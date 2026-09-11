package moe.antimony.hoshi.features.sync

import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import moe.antimony.hoshi.R
import moe.antimony.hoshi.epub.BookEntry
import moe.antimony.hoshi.epub.BookInfo
import moe.antimony.hoshi.epub.BookMetadata
import moe.antimony.hoshi.epub.BookRepository
import moe.antimony.hoshi.epub.Bookmark
import moe.antimony.hoshi.epub.PositionTrailEntry
import moe.antimony.hoshi.features.kosync.FakeKosyncApi
import moe.antimony.hoshi.features.kosync.KosyncCredentials
import moe.antimony.hoshi.features.kosync.KosyncManager
import moe.antimony.hoshi.features.kosync.KosyncRemoteProgress
import moe.antimony.hoshi.features.kosync.KosyncSettings
import moe.antimony.hoshi.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProgressSyncCoordinatorTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val options = SyncOptions(syncStats = false, statsSyncMode = StatisticsSyncMode.Merge, syncAudioBook = false)
    private val kosyncOn = KosyncSettings(enabled = true, serverUrl = "http://kosync.test", username = "reader")
    private val credentials = KosyncCredentials("http://kosync.test", "reader", "key")

    @Test
    fun pullAppliesDriveThenKosyncRecordsTrailAndAlignsAudio() = runBlocking {
        val repository = BookRepository(tempFolder.root)
        val entry = repository.createEntry()
        val previous = Bookmark(0, 0.1, 10, TtuSyncRules.unixMillisToAppleReferenceSeconds(1_000))
        repository.saveBookmark(entry.root, previous)
        val drive = FakeDriveSyncDataSource(progress = TtuProgress(7, 150, 0.75, 2_000))
        val api = FakeKosyncApi(remote = null)
        var aligned = 0
        val statuses = mutableListOf<Pair<SyncBackend, String?>>()
        val coordinator = coordinator(repository, drive, api, alignAudio = { aligned++ }, recordStatus = { b, e -> statuses += b to e })

        val report = coordinator.pull(entry, options = options, manual = false)

        val applied = checkNotNull(report.applied)
        assertEquals(SyncBackend.Ttu, applied.backend)
        assertEquals(previous, applied.previous)
        assertEquals(0.75, applied.percentage, 1e-9)
        assertTrue(report.outcomes[1] is BackendOutcome.Skipped)
        assertEquals(1, aligned)
        assertEquals(listOf(SyncBackend.Ttu to null), statuses)
        val trail = repository.loadPositionTrail(entry.root)
        assertEquals(listOf(PositionTrailEntry.SourceDrive), trail.entries.map { it.source })
        assertEquals(10, trail.entries.single().characterCount)
    }

    @Test
    fun failuresAreReportedPerBackendWithMappedErrors() = runBlocking {
        val repository = BookRepository(tempFolder.root)
        val entry = repository.createEntry()
        repository.saveBookmark(entry.root, Bookmark(0, 0.1, 10, 1.0))
        val api = FakeKosyncApi(remote = null, failure = SocketTimeoutException("connect timed out"))
        val statuses = mutableListOf<Pair<SyncBackend, String?>>()
        val coordinator = coordinator(repository, FakeDriveSyncDataSource(), api, syncSettings = SyncSettings(enabled = false), recordStatus = { b, e -> statuses += b to e })

        val report = coordinator.pull(entry, options = options, manual = true)

        val failure = report.failures.single()
        assertEquals(SyncBackend.Kosync, failure.backend)
        assertEquals(UiText.Resource(R.string.sync_error_server_timeout), failure.error)
        assertEquals(listOf(SyncBackend.Kosync to "connect timed out"), statuses)
        assertNull(report.applied)
        assertEquals(UiText.Multi(listOf(UiText.Multi(listOf(UiText.Literal("KOReader: "), failure.error), separator = ""))), report.toUiText())
        assertEquals(UiText.Resource(R.string.sync_error_tailscale_off), NetworkUnavailableException(NetworkUnavailableException.Reason.VpnRequired).toSyncErrorText())
        assertEquals(UiText.Resource(R.string.sync_error_offline), GoogleDriveApiException(GoogleDriveApiException.NoInternetConnectionMessage).toSyncErrorText())
    }

    @Test
    fun autoSyncSettingsGateBackendsUnlessManual() = runBlocking {
        val repository = BookRepository(tempFolder.root)
        val coordinator = coordinator(
            repository,
            FakeDriveSyncDataSource(),
            FakeKosyncApi(remote = null),
            syncSettings = SyncSettings(enabled = true, autoSyncEnabled = false),
            kosyncSettings = kosyncOn.copy(autoSyncEnabled = false),
        )

        assertEquals(emptySet<SyncBackend>(), coordinator.enabledBackends(manual = false))
        assertEquals(setOf(SyncBackend.Ttu, SyncBackend.Kosync), coordinator.enabledBackends(manual = true))
        assertTrue(coordinator.pull(repository.createEntry(), options = options, manual = false).isEmpty)
    }

    @Test
    fun restorePositionStampsLocalNewerAndRecordsUndo() = runBlocking {
        val repository = BookRepository(tempFolder.root)
        val entry = repository.createEntry()
        repository.saveBookmark(entry.root, Bookmark(1, 0.5, 150, 5.0))
        val coordinator = coordinator(repository, FakeDriveSyncDataSource(), FakeKosyncApi(remote = null))
        val target = PositionTrailEntry(chapterIndex = 0, progress = 0.2, characterCount = 20, lastModified = 1.0, source = "kosync", recordedAt = 2.0)

        coordinator.restorePosition(entry, target)

        val restored = checkNotNull(repository.loadBookmark(entry.root))
        assertEquals(20, restored.characterCount)
        assertTrue(checkNotNull(restored.lastModified) > 5.0)
        assertEquals(listOf(PositionTrailEntry.SourceUndo), repository.loadPositionTrail(entry.root).entries.map { it.source })
        assertEquals(150, repository.loadPositionTrail(entry.root).entries.single().characterCount)
    }

    private fun coordinator(
        repository: BookRepository,
        drive: FakeDriveSyncDataSource,
        api: FakeKosyncApi,
        syncSettings: SyncSettings = SyncSettings(enabled = true, autoSyncEnabled = true),
        kosyncSettings: KosyncSettings = kosyncOn,
        alignAudio: suspend (BookEntry) -> Unit = {},
        recordStatus: suspend (SyncBackend, String?) -> Unit = { _, _ -> },
    ) = ProgressSyncCoordinator(
        bookRepository = repository,
        syncManager = SyncManager(repository, drive, nowUnixMillis = { 9_999 }),
        kosyncManager = KosyncManager(
            bookRepository = repository,
            api = api,
            settings = kosyncSettings,
            credentials = credentials,
            deviceId = "me",
            bookLoader = { null },
            ioDispatcher = Dispatchers.Unconfined,
        ),
        syncSettings = syncSettings,
        kosyncSettings = kosyncSettings,
        alignAudio = alignAudio,
        recordStatus = recordStatus,
    )

    private suspend fun BookRepository.createEntry(): BookEntry {
        val root = createBookDirectory("book-${System.nanoTime()}")
        val metadata = BookMetadata(id = root.name, title = "Title", cover = null, folder = root.name, lastAccess = 0.0)
        saveMetadata(root, metadata)
        saveBookInfo(
            root,
            BookInfo(
                characterCount = 200,
                chapterInfo = mapOf(
                    "c0" to BookInfo.ChapterInfo(spineIndex = 0, currentTotal = 0, chapterCount = 100),
                    "c1" to BookInfo.ChapterInfo(spineIndex = 1, currentTotal = 100, chapterCount = 100),
                ),
            ),
        )
        root.resolve("${root.name}.epub").writeBytes(ByteArray(4096) { it.toByte() })
        return BookEntry(root, metadata)
    }
}
