package moe.antimony.hoshi.navigation

import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import moe.antimony.hoshi.R
import moe.antimony.hoshi.content.ContentLanguageProfile
import moe.antimony.hoshi.features.reader.ReaderLoadingPage
import moe.antimony.hoshi.features.reader.ReaderSettings
import moe.antimony.hoshi.features.reader.ReaderWebView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import moe.antimony.hoshi.LocalHoshiUiDependencies
import moe.antimony.hoshi.epub.BookEntry
import moe.antimony.hoshi.epub.BookMetadata
import moe.antimony.hoshi.epub.EpubBook
import moe.antimony.hoshi.epub.Bookmark
import moe.antimony.hoshi.features.reader.ReaderChapterPosition
import moe.antimony.hoshi.features.settings.collectAsLoadedSettings
import moe.antimony.hoshi.features.sync.BackendOutcome
import moe.antimony.hoshi.features.sync.ProgressSyncReport
import moe.antimony.hoshi.features.sync.SyncBackend
import moe.antimony.hoshi.features.sync.SyncOptions
import moe.antimony.hoshi.features.sync.displayName
import moe.antimony.hoshi.features.sync.toPercent
import moe.antimony.hoshi.features.wallpaper.BookCoverWallpaperViewModel
import moe.antimony.hoshi.ui.resolve

@Composable
internal fun ReaderRouteDestination(
    bookId: String,
    stateHolder: ReaderRouteStateHolder,
    readerSettings: ReaderSettings,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    onReaderKeyEventHandlerChange: (((KeyEvent) -> Boolean)?) -> Unit,
    onReaderGenericMotionHandlerChange: (((MotionEvent) -> Boolean)?) -> Unit,
    onBookmarkSaved: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContainer = LocalHoshiUiDependencies.current
    val bookCoverWallpaperViewModel: BookCoverWallpaperViewModel = hiltViewModel()
    val readerSnackbarHostState = remember { SnackbarHostState() }
    val bookCoverPublishFailedMessage = stringResource(R.string.book_cover_wallpaper_publish_failed)
    val iReaderNotSelectedMessage =
        stringResource(R.string.book_cover_wallpaper_ireader_not_selected_error)
    val syncSettings = appContainer.syncSettingsRepository.settings.collectAsLoadedSettings()
    val sasayakiSettings = appContainer.sasayakiSettingsRepository.settings.collectAsLoadedSettings()
    val kosyncSettings = appContainer.kosyncSettingsRepository.settings.collectAsLoadedSettings()
    val kosyncAutoSync = kosyncSettings?.let { it.enabled && it.autoSyncEnabled } == true
    val autoSyncState = ReaderRouteAutoSyncState(
        syncSettings = syncSettings,
        sasayakiSettings = sasayakiSettings,
    )
    val resources = LocalResources.current
    val bookmarkScope = rememberCoroutineScope()
    var reloadKey by remember(bookId) { mutableIntStateOf(0) }
    var pendingSyncJump by remember(bookId) { mutableStateOf<ReaderSyncJump?>(null) }
    var syncNotice by remember(bookId) { mutableStateOf<ReaderSyncNotice?>(null) }
    var lastReaderSave by remember(bookId) { mutableStateOf<ReaderChapterPosition?>(null) }
    var pendingSyncRequest by remember(bookId) { mutableStateOf<ReaderSyncRequest?>(null) }
    val autoSyncExportController = remember(bookId, appContainer) {
        ReaderAutoSyncExportController(appContainer.appScope)
    }
    val systemDarkTheme = isSystemInDarkTheme()
    val readerLoadingBackground = Modifier.background(
        Color(readerSettings.backgroundColor(systemDarkTheme)),
    )
    val routeState by produceState<ReaderRouteRenderState>(
        ReaderRouteRenderState.Loading,
        bookId,
        stateHolder,
        reloadKey,
    ) {
        val loaded = stateHolder.load(bookId) { entry ->
            val initialAutoSyncState = ReaderRouteAutoSyncState(
                syncSettings = syncSettings ?: appContainer.syncSettingsRepository.settings.first(),
                sasayakiSettings = sasayakiSettings ?: appContainer.sasayakiSettingsRepository.settings.first(),
            )
            val options = SyncOptions(
                syncStats = readerSettings.statisticsSyncEnabled,
                statsSyncMode = readerSettings.statisticsSyncMode,
                syncAudioBook = initialAutoSyncState.shouldSyncAudioBook,
            )
            // Drive import stays on the open path: it can replace the whole book folder, and its preflight fails fast.
            if (initialAutoSyncState.shouldSyncOnOpen) {
                runCatching {
                    appContainer.progressSyncCoordinator.pull(entry, options = options, manual = false, backends = setOf(SyncBackend.Ttu))
                }
            }
            // The audiobook keeps its own position: opening a book never moves it. A sync that applies a
            // remote position realigns it (in the coordinator), and the sync sheet moves it on request.
            val initialKosync = kosyncSettings ?: appContainer.kosyncSettingsRepository.settings.first()
            if (initialKosync.enabled && initialKosync.autoSyncEnabled) {
                // Never hold the book open on the kosync server; the Ready branch picks the outcome up.
                pendingSyncRequest = ReaderSyncRequest(
                    report = appContainer.appScope.async {
                        val kosync = setOf(SyncBackend.Kosync)
                        val pulled = appContainer.progressSyncCoordinator.pull(entry, options = options, manual = false, backends = kosync)
                        val pushed = appContainer.progressSyncCoordinator.push(entry, options = options, manual = false, backends = kosync)
                        ProgressSyncReport(pulled.outcomes + pushed.outcomes)
                    },
                )
            }
        }
        value = loaded.activateProfileAndPrepareRender(
            activateForBook = { metadata ->
                appContainer.profileActivationService.activateForBook(metadata).let { profile ->
                    ContentLanguageProfile.fromDictionaryLanguageId(profile.dictionaryLanguageId)
                        ?: ContentLanguageProfile.Default
                }
            },
            clearLoadedProfile = appContainer.profileActivationService::clearLoadedProfile,
            loadReaderSettings = { appContainer.readerSettingsRepository.settings.first() },
            loadGeneration = reloadKey,
        )
    }
    val bookCoverPublicationCoordinator = remember { ReaderBookCoverPublicationCoordinator() }
    val bookCoverPublicationEvent = when (val state = routeState) {
        ReaderRouteRenderState.Loading,
        is ReaderRouteRenderState.Error,
        -> ReaderBookCoverPublicationEvent.NotReady
        is ReaderRouteRenderState.Ready -> ReaderBookCoverPublicationEvent.Ready(
            bookId = state.loadState.entry.metadata.id,
            coverPath = state.loadState.bookCoverFile?.absolutePath,
            loadGeneration = state.loadGeneration,
        )
    }
    LaunchedEffect(bookCoverPublicationEvent) {
        val readyState = (routeState as? ReaderRouteRenderState.Ready)?.loadState
        if (readyState != null && bookCoverPublicationCoordinator.shouldPublish(bookCoverPublicationEvent)) {
            val result = bookCoverWallpaperViewModel.publishCurrentCover(readyState.bookCoverFile)
            if (result.hasFailures) {
                val message = when (bookCoverPublishFailureMessageRes(result)) {
                    R.string.book_cover_wallpaper_ireader_not_selected_error ->
                        iReaderNotSelectedMessage
                    else -> bookCoverPublishFailedMessage
                }
                readerSnackbarHostState.showSnackbar(message)
            }
        } else if (readyState == null) {
            bookCoverPublicationCoordinator.shouldPublish(ReaderBookCoverPublicationEvent.NotReady)
        }
    }

    val routeSyncOptions = SyncOptions(
        syncStats = readerSettings.statisticsSyncEnabled,
        statsSyncMode = readerSettings.statisticsSyncMode,
        syncAudioBook = autoSyncState.shouldSyncAudioBook,
    )

    suspend fun exportBook(entry: BookEntry, book: EpubBook) {
        val report = appContainer.progressSyncCoordinator.push(entry, book, routeSyncOptions, manual = false)
        report.outcomes.forEach { outcome ->
            if (outcome is BackendOutcome.Failed) {
                Log.w(ReaderAutoSyncLogTag, "Reader auto export failed for ${outcome.backend}.", outcome.cause)
            } else {
                Log.d(ReaderAutoSyncLogTag, "Reader auto export: ${outcome::class.java.simpleName} (${outcome.backend})")
            }
        }
    }

    val anyAutoSyncEnabled = autoSyncState.isReaderAutoSyncEnabled || kosyncAutoSync

    fun scheduleExport(entry: BookEntry, book: EpubBook) {
        autoSyncExportController.scheduleExport(anyAutoSyncEnabled) {
            exportBook(entry, book)
        }
    }

    fun flushExport() {
        autoSyncExportController.flushExport(anyAutoSyncEnabled)
    }

    /** Turns a finished background sync into reader feedback: a notice card, a jump with Undo, or a re-save when the reader moved on. */
    suspend fun presentReport(
        report: ProgressSyncReport,
        readyState: ReaderRouteLoadState.Ready,
        openPosition: ReaderChapterPosition,
        positionAtSyncStart: ReaderChapterPosition,
    ) {
        val applied = report.applied
        if (applied == null) {
            report.failures.firstOrNull()?.let { failure ->
                syncNotice = ReaderSyncNotice(
                    message = resources.getString(
                        R.string.reader_sync_failed_format,
                        failure.backend.displayName,
                        failure.error.resolve(resources),
                    ),
                    isError = true,
                )
            }
            return
        }
        val message = resources.getString(
            R.string.reader_synced_from_format,
            applied.backend.displayName,
            applied.percentage.toPercent(),
        )
        if (applied.backend == SyncBackend.Ttu) {
            // A Drive import rewrites statistics and sidecars too, so only a reload picks everything up.
            reloadKey += 1
            syncNotice = ReaderSyncNotice(message, isError = false)
            return
        }
        when (val plan = planReaderSync(applied, readyState.bookmark, lastReaderSave, openPosition, positionAtSyncStart)) {
            ReaderSyncPlan.None -> Unit
            is ReaderSyncPlan.Ignore -> stateHolder.saveBookmark(
                state = readyState,
                chapterIndex = plan.reSave.index,
                progress = plan.reSave.progress,
                onBookmarkSaved = onBookmarkSaved,
            )
            is ReaderSyncPlan.Apply -> {
                pendingSyncJump = plan.jump
                syncNotice = ReaderSyncNotice(message, isError = false, undo = plan.jump.origin)
            }
        }
    }

    fun importOnForeground(entry: BookEntry, position: ReaderChapterPosition) {
        if (!anyAutoSyncEnabled) return
        pendingSyncRequest = ReaderSyncRequest(
            positionAtStart = position,
            report = bookmarkScope.async {
                appContainer.progressSyncCoordinator.pull(entry, options = routeSyncOptions, manual = false)
            },
        )
    }

    when (val state = routeState) {
        ReaderRouteRenderState.Loading -> ReaderLoadingPage(
            backgroundColor = Color(readerSettings.backgroundColor(systemDarkTheme)),
            modifier = modifier.fillMaxSize(),
        )
        is ReaderRouteRenderState.Error -> Box(
            modifier = modifier
                .fillMaxSize()
                .then(readerLoadingBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(state.message)
        }
        is ReaderRouteRenderState.Ready -> {
            val readyState = state.loadState
            var routeReaderSettings by remember(readyState.entry.metadata.id, state.readerSettings) {
                mutableStateOf(state.readerSettings)
            }
            LaunchedEffect(appContainer.readerSettingsRepository, readyState.entry.metadata.id, state.readerSettings) {
                appContainer.readerSettingsRepository.settings.collect { settings ->
                    routeReaderSettings = settings
                }
            }
            val openPosition = ReaderChapterPosition(
                index = readyState.bookmark?.chapterIndex ?: 0,
                progress = readyState.bookmark?.progress ?: 0.0,
            )
            LaunchedEffect(readyState.entry.metadata.id, pendingSyncRequest) {
                val request = pendingSyncRequest ?: return@LaunchedEffect
                try {
                    val report = try {
                        request.report.await()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        return@LaunchedEffect
                    }
                    presentReport(report, readyState, openPosition, request.positionAtStart ?: openPosition)
                } finally {
                    if (pendingSyncRequest === request) pendingSyncRequest = null
                }
            }
            Box(modifier = modifier.fillMaxSize()) {
                ReaderWebView(
                    bookId = bookId,
                    book = readyState.book,
                    bookEntry = readyState.entry,
                    bookRoot = readyState.bookRoot,
                    bookCoverFile = readyState.bookCoverFile,
                    initialChapterIndex = readyState.bookmark?.chapterIndex ?: 0,
                    initialProgress = readyState.bookmark?.progress ?: 0.0,
                    readerSettings = routeReaderSettings,
                    onReaderSettingsChange = { settings ->
                        routeReaderSettings = settings
                        onReaderSettingsChange(settings)
                    },
                    onReaderKeyEventHandlerChange = onReaderKeyEventHandlerChange,
                    onReaderGenericMotionHandlerChange = onReaderGenericMotionHandlerChange,
                    onSaveBookmark = { chapterIndex, progress, statistics ->
                        lastReaderSave = ReaderChapterPosition(chapterIndex, progress)
                        autoSyncExportController.launchSave {
                            stateHolder.saveBookmark(
                                state = readyState,
                                chapterIndex = chapterIndex,
                                progress = progress,
                                statistics = statistics,
                                onBookmarkSaved = onBookmarkSaved,
                            )
                        }
                        scheduleExport(readyState.entry, readyState.book)
                    },
                    onFlushAutoSyncExport = ::flushExport,
                    onForegroundAutoSyncImport = { importOnForeground(readyState.entry, lastReaderSave ?: openPosition) },
                    pendingSyncJump = pendingSyncJump,
                    onPendingSyncJumpConsumed = { pendingSyncJump = null },
                    syncNotice = syncNotice,
                    onSyncNoticeUndo = {
                        syncNotice?.undo?.let { pendingSyncJump = ReaderSyncJump(target = it, origin = null, seedOnly = false) }
                        syncNotice = null
                    },
                    onSyncNoticeDismissed = { syncNotice = null },
                    onPositionDisplaced = { displaced, source ->
                        bookmarkScope.launch {
                            appContainer.progressSyncCoordinator.recordDisplacement(
                                readyState.entry,
                                Bookmark(displaced.index, displaced.progress, readyState.book.characterCountAt(displaced.index, displaced.progress)),
                                source,
                            )
                        }
                    },
                    contentLanguageProfile = state.contentLanguageProfile,
                    onClose = onClose,
                    modifier = Modifier.fillMaxSize(),
                )
                SnackbarHost(
                    hostState = readerSnackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

internal sealed interface ReaderRouteRenderState {
    data object Loading : ReaderRouteRenderState

    data class Ready(
        val loadState: ReaderRouteLoadState.Ready,
        val readerSettings: ReaderSettings,
        val contentLanguageProfile: ContentLanguageProfile,
        val loadGeneration: Int = 0,
    ) : ReaderRouteRenderState

    data class Error(
        val message: String,
    ) : ReaderRouteRenderState
}

internal suspend fun ReaderRouteLoadState.activateProfileAndPrepareRender(
    activateForBook: (BookMetadata) -> ContentLanguageProfile,
    clearLoadedProfile: () -> Unit,
    loadReaderSettings: suspend () -> ReaderSettings,
    loadGeneration: Int = 0,
): ReaderRouteRenderState =
    when (this) {
        is ReaderRouteLoadState.Ready -> {
            val contentLanguageProfile = activateForBook(entry.metadata)
            ReaderRouteRenderState.Ready(
                loadState = this,
                readerSettings = loadReaderSettings(),
                contentLanguageProfile = contentLanguageProfile,
                loadGeneration = loadGeneration,
            )
        }
        is ReaderRouteLoadState.Error -> {
            clearLoadedProfile()
            ReaderRouteRenderState.Error(message)
        }
        ReaderRouteLoadState.Loading -> ReaderRouteRenderState.Loading
    }

private const val ReaderAutoSyncLogTag = "HoshiReaderSync"
