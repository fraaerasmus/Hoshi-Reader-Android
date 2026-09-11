package moe.antimony.hoshi.features.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import moe.antimony.hoshi.epub.BookEntry
import moe.antimony.hoshi.epub.BookInfo
import moe.antimony.hoshi.epub.BookMetadata
import moe.antimony.hoshi.epub.BookRepository
import moe.antimony.hoshi.epub.Bookmark
import moe.antimony.hoshi.epub.ReadingStatistics
import moe.antimony.hoshi.features.kosync.KosyncDocumentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RemoteBackupManagerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val credentials = RemoteBackupCredentials("http://backup.test:8090", "reader", "secret")
    private val settings = RemoteBackupSettings(enabled = true, serverUrl = credentials.serverUrl, username = "reader", deviceName = "Pixel")

    @Test
    fun backupAllUploadsSettingsAndChangedBooksOnce() = runBlocking {
        val repository = BookRepository(tempFolder.root)
        val entry = repository.createEntry()
        repository.saveBookmark(entry.root, Bookmark(1, 0.5, 150, 5.0))
        val store = InMemoryStore()
        val snapshot = FakeSnapshot()
        var now = 1_000L
        val manager = manager(repository, store, snapshot, nowMillis = { now })

        val first = manager.backupAll()

        assertEquals(RemoteBackupResult(settingsUploaded = true, booksUploaded = 1), first)
        val key = KosyncDocumentId.partialMd5(checkNotNull(repository.epubFile(entry)))
        assertEquals(snapshot.encoded, store.text("Hoshi/devices/Pixel/settings.json"))
        assertNull(store.text("Hoshi/devices/Pixel/settings.previous.json"))
        assertTrue(store.text("Hoshi/books/$key/bookmark.json")!!.contains("150"))
        assertNull(store.text("Hoshi/books/$key/statistics.json"))
        val index = manager.listIndex()
        assertEquals(listOf("Hoshi", "Hoshi/devices", "Hoshi/devices/Pixel", "Hoshi/books", "Hoshi/books/$key"), store.folders)
        assertEquals(listOf("Pixel"), index.devices.map { it.name })
        assertEquals(listOf(key to "Title"), index.books.map { it.key to it.title })

        now = 2_000L
        assertEquals(RemoteBackupResult(settingsUploaded = false, booksUploaded = 0), manager.backupAll())

        snapshot.value = "changed"
        assertEquals(RemoteBackupResult(settingsUploaded = true, booksUploaded = 0), manager.backupAll())
        assertEquals(snapshot.encoded, store.text("Hoshi/devices/Pixel/settings.json"))
        assertTrue(store.text("Hoshi/devices/Pixel/settings.previous.json")!!.contains("\"first\""))
        assertEquals(1, manager.listIndex().devices.size)
    }

    @Test
    fun restoreBookStateWritesServerSidecarsUnlessLocalDataExists() = runBlocking {
        val repository = BookRepository(tempFolder.root)
        val source = repository.createEntry()
        repository.saveBookmark(source.root, Bookmark(1, 0.5, 150, 5.0))
        repository.saveStatistics(source.root, listOf(ReadingStatistics(title = "Title", dateKey = "2026-09-11", charactersRead = 10, lastStatisticModified = 1)))
        val store = InMemoryStore()
        manager(repository, store, FakeSnapshot()).backupBookState(source)

        val fresh = repository.createEntry()
        val manager = manager(repository, store, FakeSnapshot())
        assertTrue(manager.restoreBookState(fresh, onlyIfLocalEmpty = true))
        assertEquals(Bookmark(1, 0.5, 150, 5.0), repository.loadBookmark(fresh.root))
        assertEquals(10, repository.loadStatistics(fresh.root).single().charactersRead)

        repository.saveBookmark(fresh.root, Bookmark(0, 0.0, 0, 9.0))
        assertFalse(manager.restoreBookState(fresh, onlyIfLocalEmpty = true))
        assertEquals(0, checkNotNull(repository.loadBookmark(fresh.root)).characterCount)
        assertTrue(manager.restoreBookState(fresh))
        assertEquals(150, checkNotNull(repository.loadBookmark(fresh.root)).characterCount)

        val unknown = repository.createEntry(epubBytes = ByteArray(2048) { 7 })
        assertFalse(manager.restoreBookState(unknown))
    }

    @Test
    fun restoreSettingsImportsTheDeviceEnvelopeAndFailuresAreRecorded() = runBlocking {
        val repository = BookRepository(tempFolder.root)
        val store = InMemoryStore()
        val snapshot = FakeSnapshot()
        val statuses = mutableListOf<String?>()
        val manager = manager(repository, store, snapshot, recordStatus = { statuses += it })

        manager.backupAll()
        manager.restoreSettings("Pixel")
        assertEquals(snapshot.encoded, snapshot.imported)

        val failure = runCatching { manager.restoreSettings("Tablet") }.exceptionOrNull()
        assertTrue(failure is RemoteBackupException)
        assertEquals(listOf(null, null, "No settings backup for Tablet on the server."), statuses)
    }

    @Test
    fun webDavUrlNormalizesSchemeAndEncodesSegments() {
        assertEquals("http://host:8090/devices/My%20Phone/settings.json", WebDavClient.webDavUrl("host:8090/", "/devices/My Phone/settings.json"))
        assertEquals("https://h/index.json", WebDavClient.webDavUrl("https://h", "index.json"))
    }

    private fun manager(
        repository: BookRepository,
        store: RemoteBackupStore,
        snapshot: SettingsBackupSnapshot,
        nowMillis: () -> Long = { 1_000L },
        recordStatus: suspend (String?) -> Unit = {},
    ) = RemoteBackupManager(
        bookRepository = repository,
        settingsBackup = snapshot,
        store = store,
        settings = settings,
        credentials = credentials,
        bookkeeping = InMemoryBookkeeping(),
        recordStatus = recordStatus,
        nowMillis = nowMillis,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private class InMemoryStore : RemoteBackupStore {
        val files = mutableMapOf<String, ByteArray>()

        override suspend fun put(credentials: RemoteBackupCredentials, path: String, bytes: ByteArray) {
            files[path] = bytes
        }

        override suspend fun get(credentials: RemoteBackupCredentials, path: String): ByteArray? = files[path]

        val folders = mutableListOf<String>()

        override suspend fun mkcol(credentials: RemoteBackupCredentials, path: String) {
            // Like rclone: the parent must exist already.
            val parent = path.substringBeforeLast('/', "")
            check(parent.isEmpty() || parent in folders) { "parent missing for $path" }
            folders += path
        }

        fun text(path: String): String? = files[path]?.decodeToString()
    }

    private class InMemoryBookkeeping : RemoteBackupBookkeeping {
        override var settingsFingerprint: String? = null
        private val stamps = mutableMapOf<String, Long>()
        override fun bookStamp(key: String): Long? = stamps[key]
        override fun setBookStamp(key: String, stamp: Long) {
            stamps[key] = stamp
        }
    }

    private class FakeSnapshot : SettingsBackupSnapshot {
        var value = "first"
        var imported: String? = null
        val encoded: String get() = encode(envelope())

        private fun envelope(): JsonObject = buildJsonObject {
            put("exportedAt", "now-${System.nanoTime()}")
            put("stores", buildJsonObject { put("reader", value) })
        }

        override suspend fun exportEnvelope(): JsonObject = envelope()
        override fun encode(envelope: JsonObject): String = envelope["stores"].toString()
        override suspend fun importSettingsJson(text: String) {
            imported = text
        }
    }

    private suspend fun BookRepository.createEntry(epubBytes: ByteArray = ByteArray(4096) { it.toByte() }): BookEntry {
        val root = createBookDirectory("book-${System.nanoTime()}")
        val metadata = BookMetadata(id = root.name, title = "Title", cover = null, folder = root.name, lastAccess = 0.0)
        saveMetadata(root, metadata)
        saveBookInfo(root, BookInfo(characterCount = 200, chapterInfo = emptyMap()))
        root.resolve("${root.name}.epub").writeBytes(epubBytes)
        return BookEntry(root, metadata)
    }
}
