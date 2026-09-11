package moe.antimony.hoshi.features.sync

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.antimony.hoshi.R
import moe.antimony.hoshi.epub.BookEntry
import moe.antimony.hoshi.epub.BookRepository
import moe.antimony.hoshi.epub.Bookmark
import moe.antimony.hoshi.epub.EpubBook
import moe.antimony.hoshi.epub.PositionTrailEntry
import moe.antimony.hoshi.features.kosync.KosyncManager
import moe.antimony.hoshi.features.kosync.KosyncResult
import moe.antimony.hoshi.features.kosync.KosyncSettings
import moe.antimony.hoshi.features.kosync.KosyncSettingsRepository
import moe.antimony.hoshi.features.sasayaki.SasayakiPositionSync
import moe.antimony.hoshi.ui.UiText

data class SyncOptions(
    val syncStats: Boolean,
    val statsSyncMode: StatisticsSyncMode,
    val syncAudioBook: Boolean,
)

sealed interface BackendOutcome {
    val backend: SyncBackend

    /** A remote position was written to the local bookmark. */
    data class Applied(override val backend: SyncBackend, val bookmark: Bookmark, val previous: Bookmark?, val percentage: Double) : BackendOutcome
    data class Sent(override val backend: SyncBackend, val percentage: Double) : BackendOutcome
    data class UpToDate(override val backend: SyncBackend) : BackendOutcome
    data class Skipped(override val backend: SyncBackend) : BackendOutcome
    data class Failed(override val backend: SyncBackend, val error: UiText, val cause: Throwable) : BackendOutcome
}

data class ProgressSyncReport(val outcomes: List<BackendOutcome>) {
    val applied: BackendOutcome.Applied? get() = outcomes.filterIsInstance<BackendOutcome.Applied>().lastOrNull()
    val failures: List<BackendOutcome.Failed> get() = outcomes.filterIsInstance<BackendOutcome.Failed>()
    val isEmpty: Boolean get() = outcomes.all { it is BackendOutcome.Skipped }

    companion object {
        val Empty = ProgressSyncReport(emptyList())
    }
}

data class BackendStatus(val backend: SyncBackend, val status: RemoteProgressStatus?, val error: UiText?)

val SyncBackend.displayName: String
    get() = when (this) {
        SyncBackend.Ttu -> "ッツ"
        SyncBackend.Kosync -> "KOReader"
    }

/** One entry point for "sync this book" across every enabled backend; owns outcomes, errors, the position trail and the audio follow-up. */
@Singleton
internal class ProgressSyncCoordinator private constructor(
    private val bookRepository: BookRepository,
    private val syncManager: SyncManager,
    private val kosyncManager: KosyncManager,
    private val syncSettings: suspend () -> SyncSettings,
    private val kosyncSettings: suspend () -> KosyncSettings,
    private val alignAudio: suspend (BookEntry) -> Unit,
    private val recordStatus: suspend (SyncBackend, String?) -> Unit,
) {
    @Inject
    constructor(
        bookRepository: BookRepository,
        syncManager: SyncManager,
        kosyncManager: KosyncManager,
        syncSettingsRepository: SyncSettingsRepository,
        kosyncSettingsRepository: KosyncSettingsRepository,
        sasayakiPositionSync: SasayakiPositionSync,
        syncStatusRepository: SyncStatusRepository,
    ) : this(
        bookRepository = bookRepository,
        syncManager = syncManager,
        kosyncManager = kosyncManager,
        syncSettings = { syncSettingsRepository.settings.first() },
        kosyncSettings = { kosyncSettingsRepository.settings.first() },
        alignAudio = { entry -> sasayakiPositionSync.alignAudioToBookmark(entry) },
        recordStatus = syncStatusRepository::record,
    )

    constructor(
        bookRepository: BookRepository,
        syncManager: SyncManager,
        kosyncManager: KosyncManager,
        syncSettings: SyncSettings,
        kosyncSettings: KosyncSettings,
        alignAudio: suspend (BookEntry) -> Unit = {},
        recordStatus: suspend (SyncBackend, String?) -> Unit = { _, _ -> },
    ) : this(
        bookRepository = bookRepository,
        syncManager = syncManager,
        kosyncManager = kosyncManager,
        syncSettings = { syncSettings },
        kosyncSettings = { kosyncSettings },
        alignAudio = alignAudio,
        recordStatus = recordStatus,
    )

    suspend fun enabledBackends(manual: Boolean): Set<SyncBackend> = buildSet {
        syncSettings().let { if (it.enabled && (manual || it.autoSyncEnabled)) add(SyncBackend.Ttu) }
        kosyncSettings().let { if (it.enabled && (manual || it.autoSyncEnabled)) add(SyncBackend.Kosync) }
    }

    suspend fun pull(
        entry: BookEntry,
        book: EpubBook? = null,
        options: SyncOptions,
        manual: Boolean,
        backends: Set<SyncBackend>? = null,
        force: Boolean = manual,
    ): ProgressSyncReport {
        val targets = backends ?: enabledBackends(manual)
        val previous = bookRepository.loadBookmark(entry.root)
        val outcomes = mutableListOf<BackendOutcome>()
        if (SyncBackend.Ttu in targets) {
            outcomes += attempt(SyncBackend.Ttu) {
                // A forced pull takes the Drive record even when the local bookmark is newer.
                val direction = if (force) SyncDirection.ImportFromTtu else null
                driveOutcome(entry, syncManager.syncBook(entry, direction, options.syncStats, options.statsSyncMode, options.syncAudioBook, importOnly = true), previous)
            }
        }
        if (SyncBackend.Kosync in targets) {
            outcomes += attempt(SyncBackend.Kosync) { kosyncOutcome(kosyncManager.pull(entry, book, force = force)) }
        }
        return finish(entry, outcomes)
    }

    suspend fun push(
        entry: BookEntry,
        book: EpubBook? = null,
        options: SyncOptions,
        manual: Boolean,
        backends: Set<SyncBackend>? = null,
        force: Boolean = manual,
    ): ProgressSyncReport {
        val targets = backends ?: enabledBackends(manual)
        val outcomes = mutableListOf<BackendOutcome>()
        if (SyncBackend.Ttu in targets) {
            outcomes += attempt(SyncBackend.Ttu) {
                val result = syncManager.syncBook(
                    entry, SyncDirection.ExportToTtu, options.syncStats, options.statsSyncMode, options.syncAudioBook,
                    syncBookData = syncSettings().uploadBooks,
                )
                driveOutcome(entry, result, previous = null)
            }
        }
        if (SyncBackend.Kosync in targets && (manual || kosyncSettings().pushEnabled)) {
            outcomes += attempt(SyncBackend.Kosync) { kosyncOutcome(kosyncManager.push(entry, book, force = force)) }
        }
        return finish(entry, outcomes)
    }

    /** The bookshelf's "Sync": let each backend decide, or force a direction. Always manual. */
    suspend fun syncAll(entry: BookEntry, book: EpubBook? = null, options: SyncOptions, direction: SyncDirection?): ProgressSyncReport {
        val backends = enabledBackends(manual = true)
        val previous = bookRepository.loadBookmark(entry.root)
        val outcomes = mutableListOf<BackendOutcome>()
        if (SyncBackend.Ttu in backends) {
            outcomes += attempt(SyncBackend.Ttu) {
                val result = syncManager.syncBook(
                    entry, direction, options.syncStats, options.statsSyncMode, options.syncAudioBook,
                    syncBookData = syncSettings().uploadBooks,
                )
                driveOutcome(entry, result, previous)
            }
        }
        if (SyncBackend.Kosync in backends) {
            if (direction != SyncDirection.ExportToTtu) {
                outcomes += attempt(SyncBackend.Kosync) { kosyncOutcome(kosyncManager.pull(entry, book, force = true)) }
            }
            if (direction != SyncDirection.ImportFromTtu) {
                outcomes += attempt(SyncBackend.Kosync) { kosyncOutcome(kosyncManager.push(entry, book, force = true)) }
            }
        }
        return finish(entry, outcomes)
    }

    suspend fun status(entry: BookEntry): List<BackendStatus> = coroutineScope {
        enabledBackends(manual = true).map { backend ->
            async {
                runCatching {
                    when (backend) {
                        SyncBackend.Ttu -> syncManager.status(entry)
                        SyncBackend.Kosync -> kosyncManager.status(entry)
                    }
                }.fold(
                    onSuccess = { BackendStatus(backend, it, null) },
                    onFailure = { BackendStatus(backend, null, it.toSyncErrorText()) },
                )
            }
        }.map { it.await() }
    }

    /** Puts the reader back at [target]; stamped as local-newer so the next sync pushes it back out. */
    suspend fun restorePosition(entry: BookEntry, target: PositionTrailEntry, source: String = PositionTrailEntry.SourceUndo) {
        val now = bookRepository.currentAppleReferenceDateSeconds()
        bookRepository.loadBookmark(entry.root)?.let { recordDisplacement(entry, it, source) }
        bookRepository.saveBookmark(entry.root, target.toBookmark(lastModified = now))
        alignAudio(entry)
    }

    suspend fun recordDisplacement(entry: BookEntry, displaced: Bookmark, source: String) {
        val entryToPush = PositionTrailEntry.of(displaced, source, bookRepository.currentAppleReferenceDateSeconds())
        trailMutex.withLock {
            val trail = bookRepository.loadPositionTrail(entry.root)
            val pushed = trail.pushed(entryToPush)
            if (pushed != trail) bookRepository.savePositionTrail(entry.root, pushed)
        }
    }

    private val trailMutex = Mutex()

    private suspend fun attempt(backend: SyncBackend, block: suspend () -> BackendOutcome): BackendOutcome {
        val outcome = try {
            block()
        } catch (error: Exception) {
            BackendOutcome.Failed(backend, error.toSyncErrorText(), error)
        }
        when (outcome) {
            is BackendOutcome.Skipped -> Unit
            is BackendOutcome.Failed -> recordStatus(backend, outcome.cause.localizedMessage ?: outcome.cause::class.java.simpleName)
            else -> recordStatus(backend, null)
        }
        return outcome
    }

    private suspend fun finish(entry: BookEntry, outcomes: List<BackendOutcome>): ProgressSyncReport {
        val applied = outcomes.filterIsInstance<BackendOutcome.Applied>()
        applied.forEach { outcome ->
            outcome.previous?.let { recordDisplacement(entry, it, outcome.backend.trailSource) }
        }
        if (applied.isNotEmpty()) alignAudio(entry)
        return ProgressSyncReport(outcomes)
    }

    private suspend fun driveOutcome(entry: BookEntry, result: SyncResult, previous: Bookmark?): BackendOutcome =
        when (result) {
            is SyncResult.Imported -> {
                val bookmark = bookRepository.loadBookmark(entry.root)
                if (bookmark == null) BackendOutcome.UpToDate(SyncBackend.Ttu) else BackendOutcome.Applied(SyncBackend.Ttu, bookmark, previous, percentageOf(entry, result.characterCount))
            }
            is SyncResult.Exported -> BackendOutcome.Sent(SyncBackend.Ttu, percentageOf(entry, result.characterCount))
            is SyncResult.Synced -> BackendOutcome.UpToDate(SyncBackend.Ttu)
            SyncResult.Skipped -> BackendOutcome.Skipped(SyncBackend.Ttu)
        }

    private fun kosyncOutcome(result: KosyncResult): BackendOutcome =
        when (result) {
            is KosyncResult.Pulled -> BackendOutcome.Applied(SyncBackend.Kosync, result.bookmark, result.previous, result.percentage)
            is KosyncResult.Pushed -> BackendOutcome.Sent(SyncBackend.Kosync, result.percentage)
            is KosyncResult.UpToDate -> BackendOutcome.UpToDate(SyncBackend.Kosync)
            KosyncResult.Skipped -> BackendOutcome.Skipped(SyncBackend.Kosync)
        }

    private suspend fun percentageOf(entry: BookEntry, characterCount: Int): Double {
        val total = bookRepository.loadBookInfo(entry.root)?.characterCount ?: return 0.0
        return if (total > 0) (characterCount.toDouble() / total).coerceIn(0.0, 1.0) else 0.0
    }

    private val SyncBackend.trailSource: String
        get() = when (this) {
            SyncBackend.Ttu -> PositionTrailEntry.SourceDrive
            SyncBackend.Kosync -> PositionTrailEntry.SourceKosync
        }
}

internal fun Throwable.toSyncErrorText(): UiText =
    when {
        this is NetworkUnavailableException -> when (reason) {
            NetworkUnavailableException.Reason.Offline -> UiText.Resource(R.string.sync_error_offline)
            NetworkUnavailableException.Reason.VpnRequired -> UiText.Resource(R.string.sync_error_tailscale_off)
        }
        this is SocketTimeoutException || this is ConnectException -> UiText.Resource(R.string.sync_error_server_timeout)
        this is UnknownHostException -> UiText.Resource(R.string.sync_error_server_unreachable)
        this is GoogleDriveApiException && message == GoogleDriveApiException.NoInternetConnectionMessage ->
            UiText.Resource(R.string.sync_error_offline)
        else -> UiText.Literal(localizedMessage ?: this::class.java.simpleName)
    }

fun Double.toPercent(): Int = (this * 100).roundToInt()

/** One line per backend that did something; null when nothing happened. */
fun ProgressSyncReport.toUiText(): UiText? {
    val lines = outcomes.mapNotNull { outcome ->
        val name = outcome.backend.displayName
        when (outcome) {
            is BackendOutcome.Applied -> UiText.Resource(R.string.sync_outcome_applied_format, name, outcome.percentage.toPercent())
            is BackendOutcome.Sent -> UiText.Resource(R.string.sync_outcome_sent_format, name, outcome.percentage.toPercent())
            is BackendOutcome.UpToDate -> UiText.Resource(R.string.sync_outcome_up_to_date_format, name)
            is BackendOutcome.Failed -> UiText.Multi(listOf(UiText.Literal("$name: "), outcome.error), separator = "")
            is BackendOutcome.Skipped -> null
        }
    }
    return if (lines.isEmpty()) null else UiText.Multi(lines)
}
