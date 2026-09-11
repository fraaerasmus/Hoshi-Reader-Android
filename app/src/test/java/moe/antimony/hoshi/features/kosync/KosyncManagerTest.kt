package moe.antimony.hoshi.features.kosync

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import moe.antimony.hoshi.epub.BookEntry
import moe.antimony.hoshi.epub.BookInfo
import moe.antimony.hoshi.epub.BookMetadata
import moe.antimony.hoshi.epub.BookRepository
import moe.antimony.hoshi.epub.Bookmark
import moe.antimony.hoshi.epub.EpubBook
import moe.antimony.hoshi.epub.EpubChapter
import moe.antimony.hoshi.features.sync.SyncBackoff
import moe.antimony.hoshi.features.sync.SyncComparison
import moe.antimony.hoshi.features.sync.TtuSyncRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KosyncManagerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val credentials = KosyncCredentials("http://kosync.test", "reader", "key")
    private val bookInfo = BookInfo(
        characterCount = 200,
        chapterInfo = mapOf(
            "c0.xhtml" to BookInfo.ChapterInfo(spineIndex = 0, currentTotal = 0, chapterCount = 100),
            "c1.xhtml" to BookInfo.ChapterInfo(spineIndex = 1, currentTotal = 100, chapterCount = 100),
        ),
    )

    @Test
    fun pullAppliesNewerRemotePositionAtTheResolvedParagraph() = runBlocking {
        val repository = BookRepository(tempFolder.root)
        val entry = repository.createEntry()
        repository.saveBookmark(entry.root, Bookmark(0, 0.1, 10, TtuSyncRules.unixMillisToAppleReferenceSeconds(1_000_000)))
        val api = FakeKosyncApi(
            remote = KosyncRemoteProgress(
                document = "doc",
                progress = "/body/DocFragment[2]/body/div[1]/p[2]/text().0",
                percentage = 0.6,
                device = "Kobo",
                deviceId = "kobo-id",
                timestamp = 2_000,
            ),
        )
        val manager = manager(repository, api)

        val result = manager.pull(entry, book())

        assertTrue(result is KosyncResult.Pulled)
        val bookmark = checkNotNull(repository.loadBookmark(entry.root))
        assertEquals(1, bookmark.chapterIndex)
        // Second paragraph of chapter 1 starts after "あいうえお" (5 of 15 counted characters).
        assertEquals(5.0 / 15.0, bookmark.progress, 1e-9)
        assertEquals(100 + (100 * 5.0 / 15.0).toInt(), bookmark.characterCount)
        assertEquals(2_000_000L, TtuSyncRules.appleReferenceSecondsToUnixMillis(checkNotNull(bookmark.lastModified)))
    }

    @Test
    fun pullFallsBackToPercentageWhenPointerDoesNotResolve() = runBlocking {
        val repository = BookRepository(tempFolder.root)
        val entry = repository.createEntry()
        val api = FakeKosyncApi(
            remote = KosyncRemoteProgress("doc", "/body/DocFragment[2]/body/section[1]/p[7]", 0.75, "Kobo", "kobo-id", 2_000),
        )

        val result = manager(repository, api).pull(entry, book())

        assertTrue(result is KosyncResult.Pulled)
        val bookmark = checkNotNull(repository.loadBookmark(entry.root))
        assertEquals(1, bookmark.chapterIndex)
        assertEquals(0.5, bookmark.progress, 1e-9)
        assertEquals(150, bookmark.characterCount)
    }

    @Test
    fun pullIgnoresOwnDeviceAndOlderRemote() = runBlocking {
        val repository = BookRepository(tempFolder.root)
        val entry = repository.createEntry()
        repository.saveBookmark(entry.root, Bookmark(0, 0.1, 10, TtuSyncRules.unixMillisToAppleReferenceSeconds(5_000_000)))
        val own = FakeKosyncApi(remote = KosyncRemoteProgress("doc", "/body/DocFragment[2]/body", 0.6, "Hoshi", "me", 9_000))
        val older = FakeKosyncApi(remote = KosyncRemoteProgress("doc", "/body/DocFragment[2]/body", 0.6, "Kobo", "kobo-id", 4_000))

        assertTrue(manager(repository, own).pull(entry, book()) is KosyncResult.UpToDate)
        assertTrue(manager(repository, older).pull(entry, book()) is KosyncResult.UpToDate)
        assertEquals(10, checkNotNull(repository.loadBookmark(entry.root)).characterCount)
    }

    @Test
    fun pushSendsParagraphPointerAndSkipsUnchangedPositions() = runBlocking {
        val repository = BookRepository(tempFolder.root)
        val entry = repository.createEntry()
        repository.saveBookmark(entry.root, Bookmark(1, 0.4, 140, 1.0))
        val api = FakeKosyncApi(remote = null, putTimestamp = 3_000)
        val manager = manager(repository, api)

        val result = manager.push(entry, book())

        assertTrue(result is KosyncResult.Pushed)
        val push = checkNotNull(api.lastPush)
        assertEquals("/body/DocFragment[2]/body/div[1]/p[2]", push.progress)
        assertEquals(0.7, push.percentage, 1e-9)
        assertEquals("me", push.deviceId)
        assertEquals(KosyncManager.DeviceName, push.device)
        assertTrue(manager.push(entry, book()) is KosyncResult.UpToDate)
        assertEquals(1, api.pushCount)
    }

    @Test
    fun pushWithoutChapterBodyFallsBackToChapterStart() = runBlocking {
        val repository = BookRepository(tempFolder.root)
        val entry = repository.createEntry()
        repository.saveBookmark(entry.root, Bookmark(1, 0.4, 140, 1.0))
        val api = FakeKosyncApi(remote = null, putTimestamp = 3_000)

        manager(repository, api, bookLoader = { null }).push(entry)

        assertEquals("/body/DocFragment[2]/body", checkNotNull(api.lastPush).progress)
    }

    @Test
    fun skipsWhenDisabledOrUnconfigured() = runBlocking {
        val repository = BookRepository(tempFolder.root)
        val entry = repository.createEntry()
        repository.saveBookmark(entry.root, Bookmark(1, 0.4, 140, 1.0))
        val api = FakeKosyncApi(remote = null)

        assertTrue(manager(repository, api, settings = KosyncSettings(enabled = false)).push(entry, book()) is KosyncResult.Skipped)
        assertTrue(manager(repository, api, credentials = null).pull(entry, book()) is KosyncResult.Skipped)
        assertNull(api.lastPush)
    }

    private fun manager(
        repository: BookRepository,
        api: FakeKosyncApi,
        settings: KosyncSettings = KosyncSettings(enabled = true, serverUrl = "http://kosync.test", username = "reader"),
        credentials: KosyncCredentials? = this.credentials,
        bookLoader: suspend (BookEntry) -> EpubBook? = { book() },
        backoff: SyncBackoff = SyncBackoff(),
    ) = KosyncManager(
        bookRepository = repository,
        api = api,
        settings = settings,
        credentials = credentials,
        deviceId = "me",
        bookLoader = bookLoader,
        ioDispatcher = Dispatchers.Unconfined,
        backoff = backoff,
    )

    @Test
    fun pulledCarriesTheDisplacedBookmark() = runBlocking {
        val repository = BookRepository(tempFolder.root)
        val entry = repository.createEntry()
        val previous = Bookmark(0, 0.1, 10, TtuSyncRules.unixMillisToAppleReferenceSeconds(1_000_000))
        repository.saveBookmark(entry.root, previous)
        val api = FakeKosyncApi(remote = KosyncRemoteProgress("doc", "/body/DocFragment[2]/body", 0.6, "Kobo", "kobo-id", 2_000))

        val result = manager(repository, api).pull(entry, book()) as KosyncResult.Pulled

        assertEquals(previous, result.previous)
        assertEquals(result.bookmark, repository.loadBookmark(entry.root))
    }

    @Test
    fun failuresStartACooldownThatManualSyncBypasses() = runBlocking {
        val repository = BookRepository(tempFolder.root)
        val entry = repository.createEntry()
        repository.saveBookmark(entry.root, Bookmark(1, 0.4, 140, 1.0))
        val api = FakeKosyncApi(remote = null, failure = java.net.SocketTimeoutException("connect timed out"))
        var now = 0L
        val manager = manager(repository, api, backoff = SyncBackoff(cooldownMillis = 60_000L) { now })

        assertTrue(runCatching { manager.pull(entry, book()) }.exceptionOrNull() is java.net.SocketTimeoutException)
        assertTrue(manager.push(entry, book()) is KosyncResult.Skipped)
        assertEquals(1, api.calls)
        assertTrue(runCatching { manager.push(entry, book(), force = true) }.isFailure)
        assertEquals(2, api.calls)
        now = 61_000L
        assertTrue(runCatching { manager.pull(entry, book()) }.isFailure)
        assertEquals(3, api.calls)
    }

    @Test
    fun documentIdOverrideReplacesTheHashAndStatusComparesTimestamps() = runBlocking {
        val repository = BookRepository(tempFolder.root)
        val entry = repository.createEntry()
        repository.saveBookmark(entry.root, Bookmark(0, 0.1, 10, TtuSyncRules.unixMillisToAppleReferenceSeconds(5_000_000)))
        val api = FakeKosyncApi(remote = KosyncRemoteProgress("doc", "/body/DocFragment[2]/body", 0.6, "Kobo", "kobo-id", 4_000))
        val manager = manager(repository, api)

        val computed = checkNotNull(manager.documentId(entry))
        manager.setDocumentIdOverride(entry, " custom-id ")
        assertEquals("custom-id", manager.documentId(entry))
        manager.push(entry, book())
        assertEquals("custom-id", checkNotNull(api.lastPush).document)
        manager.setDocumentIdOverride(entry, null)
        assertEquals(computed, manager.documentId(entry))

        assertEquals(SyncComparison.LocalNewer, manager.status(entry))
        assertEquals(SyncComparison.ServerNewer, kosyncComparison(null, api.remote))
        assertEquals(SyncComparison.NoRecord, kosyncComparison(Bookmark(0, 0.0, 0, 1.0), null))
        assertEquals(SyncComparison.Synced, kosyncComparison(Bookmark(0, 0.0, 0, TtuSyncRules.unixMillisToAppleReferenceSeconds(4_000_000)), api.remote))
    }

    private fun book(): EpubBook {
        val root = File(tempFolder.root, "extracted").apply { mkdirs() }
        root.resolve("c0.xhtml").writeText("<html><body><p>あいうえお</p></body></html>")
        root.resolve("c1.xhtml").writeText("<html><body><div><p>あいうえお</p><p>かきくけこ</p><p>さしすせそ</p></div></body></html>")
        return EpubBook(
            title = "Title",
            chapters = listOf(
                EpubChapter(id = "c0", href = "c0.xhtml", mediaType = "application/xhtml+xml", html = "", spineIndex = 0),
                EpubChapter(id = "c1", href = "c1.xhtml", mediaType = "application/xhtml+xml", html = "", spineIndex = 1),
            ),
            rootDirectory = root,
            bookInfo = bookInfo,
        )
    }

    private suspend fun BookRepository.createEntry(): BookEntry {
        val root = createBookDirectory("book")
        val metadata = BookMetadata(id = "book-id", title = "Title", cover = null, folder = root.name, lastAccess = 0.0)
        saveMetadata(root, metadata)
        saveBookInfo(root, bookInfo)
        root.resolve("book.epub").writeBytes(ByteArray(4096) { it.toByte() })
        return BookEntry(root, metadata)
    }
}

internal class FakeKosyncApi(
    val remote: KosyncRemoteProgress?,
    private val putTimestamp: Long? = null,
    private val failure: Exception? = null,
) : KosyncApi {
    var calls = 0

    data class Push(val document: String, val progress: String, val percentage: Double, val device: String, val deviceId: String)

    var lastPush: Push? = null
    var pushCount = 0

    override suspend fun authorize(credentials: KosyncCredentials) = Unit

    override suspend fun getProgress(credentials: KosyncCredentials, document: String): KosyncRemoteProgress? {
        calls++
        failure?.let { throw it }
        return remote
    }

    override suspend fun putProgress(
        credentials: KosyncCredentials,
        document: String,
        progress: String,
        percentage: Double,
        device: String,
        deviceId: String,
    ): Long? {
        calls++
        failure?.let { throw it }
        lastPush = Push(document, progress, percentage, device, deviceId)
        pushCount++
        return putTimestamp
    }
}
