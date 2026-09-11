package moe.antimony.hoshi.features.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.antimony.hoshi.HoshiUiDependencies
import moe.antimony.hoshi.LocalHoshiUiDependencies
import moe.antimony.hoshi.R
import moe.antimony.hoshi.epub.BookEntry
import moe.antimony.hoshi.epub.BookInfo
import moe.antimony.hoshi.epub.Bookmark
import moe.antimony.hoshi.epub.EpubBook
import moe.antimony.hoshi.epub.PositionTrailEntry
import moe.antimony.hoshi.features.backup.RemoteBackupManager
import moe.antimony.hoshi.features.reader.ReaderChapterPosition
import moe.antimony.hoshi.features.reader.tocLabelAt
import moe.antimony.hoshi.features.sasayaki.SasayakiAudioPosition
import moe.antimony.hoshi.features.sasayaki.formatDuration
import moe.antimony.hoshi.ui.UiText
import moe.antimony.hoshi.ui.hoshiOutlinedTextFieldColors
import moe.antimony.hoshi.ui.resolve

private const val TrailPreviewCount = 5

/**
 * Everything one book's sync backends know, in one panel: where the local position is, what each server holds,
 * per-backend Pull/Push, the audiobook bridge, recent positions to jump back to, and the kosync document id.
 *
 * [onApplyLocalPosition] is non-null in the reader, where a position change is a jump rather than a bookmark write.
 */
@Composable
internal fun BookSyncSheetContent(
    entry: BookEntry,
    book: EpubBook?,
    onApplyLocalPosition: ((ReaderChapterPosition) -> Unit)?,
    onLocalPositionChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deps = LocalHoshiUiDependencies.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val state = remember(entry.metadata.id) { BookSyncSheetState(entry, deps) }
    var editingDocumentId by remember { mutableStateOf(false) }
    var showAllPositions by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    LaunchedEffect(state) { state.refresh() }

    fun applyPosition(chapterIndex: Int, progress: Double, source: String) {
        scope.launch {
            if (onApplyLocalPosition != null) {
                onApplyLocalPosition(ReaderChapterPosition(chapterIndex, progress))
            } else {
                state.saveLocalPosition(chapterIndex, progress, source)
                onLocalPositionChanged()
                state.refresh()
            }
        }
    }

    /** A backend that wrote a new bookmark becomes a jump in the reader and a shelf refresh in the bookshelf. */
    fun runSync(busy: Set<SyncBackend>, action: suspend (SyncOptions) -> ProgressSyncReport) {
        scope.launch {
            val bookmark = state.perform(busy, action).applied?.bookmark ?: return@launch
            if (onApplyLocalPosition != null) {
                onApplyLocalPosition(ReaderChapterPosition(bookmark.chapterIndex, bookmark.progress))
            } else {
                onLocalPositionChanged()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.sync_sheet_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = state.headerLine(book),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.backends.forEach { backend ->
            BackendRow(
                backend = backend,
                status = state.statuses.firstOrNull { it.backend == backend },
                loaded = state.statusesLoaded,
                busy = backend in state.busy,
                idle = state.busy.isEmpty(),
                hasLocalBookmark = state.bookmark != null,
                onPull = {
                    runSync(setOf(backend)) { options ->
                        deps.progressSyncCoordinator.pull(entry, book, options, manual = true, backends = setOf(backend))
                    }
                },
                onPush = {
                    runSync(setOf(backend)) { options ->
                        deps.progressSyncCoordinator.push(entry, book, options, manual = true, backends = setOf(backend))
                    }
                },
            )
        }
        if (state.backends.size > 1) {
            FilledTonalButton(
                enabled = state.busy.isEmpty(),
                onClick = {
                    runSync(state.backends.toSet()) { options ->
                        deps.progressSyncCoordinator.syncAll(entry, book, options, direction = null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Rounded.Sync, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sync_sheet_sync_now))
            }
        }
        (state.lastMessage ?: state.lastReport?.toUiText())?.let { outcome ->
            Text(
                text = outcome.resolve(resources),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.lastMessage == null && !state.lastReport?.failures.isNullOrEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (state.hasAudioMatch) {
            SectionLabel(stringResource(R.string.sync_sheet_audiobook))
            ListItem(
                colors = transparentListItemColors(),
                headlineContent = { Text(stringResource(R.string.sync_sheet_move_audio_to_text)) },
                modifier = Modifier.clickable { scope.launch { state.alignAudioToText() } },
            )
            val audio = state.audioPosition
            ListItem(
                colors = transparentListItemColors(),
                headlineContent = { Text(stringResource(R.string.sync_sheet_move_text_to_audio)) },
                supportingContent = if (audio == null) {
                    null
                } else {
                    {
                        Text(
                            stringResource(
                                R.string.sync_sheet_audio_position_format,
                                formatDuration(audio.seconds),
                                state.percentOf(state.characterCountAt(audio.chapterIndex, audio.progress)),
                            ),
                        )
                    }
                },
                modifier = Modifier.clickable(enabled = audio != null) {
                    audio?.let { applyPosition(it.chapterIndex, it.progress, PositionTrailEntry.SourceAudio) }
                },
            )
        }
        if (state.trail.isNotEmpty()) {
            SectionLabel(stringResource(R.string.sync_sheet_recent_positions))
            val newestFirst = state.trail.asReversed()
            val visible = if (showAllPositions) newestFirst else newestFirst.take(TrailPreviewCount)
            visible.forEach { trailEntry ->
                val isCurrent = state.isCurrentPosition(trailEntry)
                ListItem(
                    colors = transparentListItemColors(),
                    leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
                    headlineContent = {
                        Text(
                            stringResource(
                                R.string.sync_sheet_trail_entry_format,
                                state.percentOf(trailEntry.characterCount),
                                stringResource(positionTrailSourceLabel(trailEntry.source)),
                                relativeTimeText(TtuSyncRules.appleReferenceSecondsToUnixMillis(trailEntry.recordedAt)),
                            ),
                            color = if (isCurrent) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                        )
                    },
                    modifier = Modifier.clickable(enabled = !isCurrent) {
                        applyPosition(trailEntry.chapterIndex, trailEntry.progress, PositionTrailEntry.SourceUndo)
                    },
                )
            }
            if (!showAllPositions && newestFirst.size > TrailPreviewCount) {
                TextButton(onClick = { showAllPositions = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.sync_sheet_show_all_format, newestFirst.size))
                }
            }
        }
        if (state.remoteBackupEnabled) {
            ListItem(
                colors = transparentListItemColors(),
                leadingContent = { Icon(Icons.Rounded.CloudDownload, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.sync_sheet_restore_from_server)) },
                modifier = Modifier.clickable(enabled = state.busy.isEmpty()) { confirmRestore = true },
            )
        }
        if (state.kosyncEnabled) {
            ListItem(
                colors = transparentListItemColors(),
                headlineContent = { Text(stringResource(R.string.sync_sheet_document_id)) },
                supportingContent = { Text(state.documentId.orEmpty(), style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    IconButton(onClick = { editingDocumentId = true }) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                },
            )
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.sync_sheet_restore_from_server)) },
            text = { Text(stringResource(R.string.sync_sheet_restore_from_server_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        scope.launch {
                            val restored = state.restoreFromServer(deps.remoteBackupManager)
                            val bookmark = state.bookmark
                            if (restored && bookmark != null) {
                                if (onApplyLocalPosition != null) {
                                    onApplyLocalPosition(ReaderChapterPosition(bookmark.chapterIndex, bookmark.progress))
                                } else {
                                    onLocalPositionChanged()
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (editingDocumentId) {
        var documentIdInput by remember(state.documentId) { mutableStateOf(state.documentId.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingDocumentId = false },
            title = { Text(stringResource(R.string.sync_sheet_document_id)) },
            text = {
                OutlinedTextField(
                    value = documentIdInput,
                    onValueChange = { documentIdInput = it },
                    singleLine = true,
                    colors = hoshiOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingDocumentId = false
                        scope.launch { state.setDocumentIdOverride(documentIdInput) }
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            editingDocumentId = false
                            scope.launch { state.setDocumentIdOverride(null) }
                        },
                    ) {
                        Text(stringResource(R.string.action_reset))
                    }
                    TextButton(onClick = { editingDocumentId = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            },
        )
    }
}

@Composable
private fun BackendRow(
    backend: SyncBackend,
    status: BackendStatus?,
    loaded: Boolean,
    busy: Boolean,
    idle: Boolean,
    hasLocalBookmark: Boolean,
    onPull: () -> Unit,
    onPush: () -> Unit,
) {
    val resources = LocalResources.current
    val remote = status?.status
    val error = status?.error
    val supporting = when {
        error != null -> error.resolve(resources)
        !loaded -> stringResource(R.string.sync_sheet_checking)
        remote == null -> stringResource(R.string.sync_state_unavailable)
        else -> listOfNotNull(
            remote.percentage?.let { stringResource(R.string.sync_sheet_server_position_format, it.toPercent()) },
            remote.modifiedAtMillis?.let { relativeTimeText(it) },
            stringResource(syncComparisonLabel(remote.comparison)),
        ).joinToString(" · ")
    }
    ListItem(
        colors = transparentListItemColors(),
        headlineContent = {
            Text(if (backend == SyncBackend.Ttu) stringResource(R.string.sync_title) else backend.displayName)
        },
        supportingContent = {
            Text(supporting, color = if (error != null) MaterialTheme.colorScheme.error else Color.Unspecified)
        },
        trailingContent = {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Row {
                    IconButton(onClick = onPull, enabled = idle && remote?.percentage != null) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = stringResource(R.string.action_pull))
                    }
                    IconButton(onClick = onPush, enabled = idle && hasLocalBookmark) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = stringResource(R.string.action_push))
                    }
                }
            }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun transparentListItemColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

private fun syncComparisonLabel(comparison: SyncComparison): Int =
    when (comparison) {
        SyncComparison.Synced -> R.string.sync_state_synced
        SyncComparison.ServerNewer -> R.string.sync_state_ahead
        SyncComparison.LocalNewer -> R.string.sync_state_behind
        SyncComparison.NoRecord -> R.string.sync_state_no_record
    }

private fun positionTrailSourceLabel(source: String): Int =
    when (source) {
        PositionTrailEntry.SourceKosync -> R.string.sync_source_kosync
        PositionTrailEntry.SourceDrive -> R.string.sync_source_drive
        PositionTrailEntry.SourceAudio -> R.string.sync_source_audio
        PositionTrailEntry.SourceJump -> R.string.sync_source_jump
        else -> R.string.sync_source_undo
    }

internal class BookSyncSheetState(
    private val entry: BookEntry,
    private val deps: HoshiUiDependencies,
) {
    var bookmark by mutableStateOf<Bookmark?>(null)
        private set
    var characterTotal by mutableStateOf(0)
        private set
    private var chapterInfo: Map<String, BookInfo.ChapterInfo>? = null
    var trail by mutableStateOf<List<PositionTrailEntry>>(emptyList())
        private set
    var statuses by mutableStateOf<List<BackendStatus>>(emptyList())
        private set
    var statusesLoaded by mutableStateOf(false)
        private set
    var backends by mutableStateOf<List<SyncBackend>>(emptyList())
        private set
    var busy by mutableStateOf<Set<SyncBackend>>(emptySet())
        private set
    var lastReport by mutableStateOf<ProgressSyncReport?>(null)
        private set
    var documentId by mutableStateOf<String?>(null)
        private set
    var hasAudioMatch by mutableStateOf(false)
        private set
    var audioPosition by mutableStateOf<SasayakiAudioPosition?>(null)
        private set
    var kosyncEnabled by mutableStateOf(false)
        private set
    var remoteBackupEnabled by mutableStateOf(false)
        private set
    /** A non-sync outcome (server restore) shown on the outcome line instead of [lastReport]. */
    var lastMessage by mutableStateOf<UiText?>(null)
        private set

    fun percentOf(characterCount: Int): Int =
        if (characterTotal > 0) (characterCount.toDouble() / characterTotal).coerceIn(0.0, 1.0).toPercent() else 0

    fun isCurrentPosition(trailEntry: PositionTrailEntry): Boolean =
        bookmark?.let { it.chapterIndex == trailEntry.chapterIndex && it.characterCount == trailEntry.characterCount } == true

    /** "Title · Chapter · 14% · Saved just now"; the chapter needs the parsed book, so only the reader shows it. */
    @Composable
    fun headerLine(book: EpubBook?): String {
        val bookmark = bookmark
        val chapter = if (bookmark != null && book != null) book.tocLabelAt(ReaderChapterPosition(bookmark.chapterIndex, bookmark.progress)) else null
        val saved = bookmark?.lastModified?.let {
            stringResource(R.string.sync_sheet_saved_format, relativeTimeText(TtuSyncRules.appleReferenceSecondsToUnixMillis(it)))
        }
        return listOfNotNull(entry.displayTitle, chapter, "${percentOf(bookmark?.characterCount ?: 0)}%", saved).joinToString(" · ")
    }

    suspend fun refresh() {
        bookmark = deps.bookRepository.loadBookmark(entry.root)
        val bookInfo = deps.bookRepository.loadBookInfo(entry.root)
        characterTotal = bookInfo?.characterCount ?: 0
        chapterInfo = bookInfo?.chapterInfo
        trail = deps.bookRepository.loadPositionTrail(entry.root).entries
        hasAudioMatch = deps.bookRepository.loadSasayakiMatch(entry.root)?.matches?.isNotEmpty() == true
        audioPosition = if (hasAudioMatch) deps.sasayakiPositionSync.audioPosition(entry) else null
        kosyncEnabled = deps.kosyncSettingsRepository.settings.first().enabled
        remoteBackupEnabled = deps.remoteBackupSettingsRepository.settings.first().let { it.enabled && it.backupBookState }
        documentId = if (kosyncEnabled) deps.kosyncManager.documentId(entry) else null
        backends = deps.progressSyncCoordinator.enabledBackends(manual = true).sortedBy { it.ordinal }
        statusesLoaded = false
        statuses = deps.progressSyncCoordinator.status(entry)
        statusesLoaded = true
    }

    /** Runs one sync action with [backends] marked busy, keeps its report for the outcome line, then reloads the panel. */
    suspend fun perform(backends: Set<SyncBackend>, action: suspend (SyncOptions) -> ProgressSyncReport): ProgressSyncReport {
        busy = backends
        val report = try {
            action(syncOptions())
        } finally {
            busy = emptySet()
        }
        lastReport = report
        lastMessage = null
        refresh()
        return report
    }

    suspend fun restoreFromServer(manager: RemoteBackupManager): Boolean {
        busy = SyncBackend.values().toSet()
        val restored = try {
            manager.restoreBookState(entry)
        } catch (error: Exception) {
            lastMessage = error.toSyncErrorText()
            false
        } finally {
            busy = emptySet()
        }
        if (restored) lastMessage = UiText.Resource(R.string.sync_sheet_restore_from_server_done)
        else if (lastMessage == null) lastMessage = UiText.Resource(R.string.sync_sheet_restore_from_server_none)
        refresh()
        return restored
    }

    suspend fun saveLocalPosition(chapterIndex: Int, progress: Double, source: String) {
        val target = PositionTrailEntry(
            chapterIndex = chapterIndex,
            progress = progress,
            characterCount = characterCountAt(chapterIndex, progress),
            source = source,
            recordedAt = deps.bookRepository.currentAppleReferenceDateSeconds(),
        )
        deps.progressSyncCoordinator.restorePosition(entry, target, source)
    }

    fun characterCountAt(chapterIndex: Int, progress: Double): Int {
        val info = chapterInfo?.values?.firstOrNull { it.spineIndex == chapterIndex } ?: return 0
        return (info.currentTotal + (info.chapterCount * progress.coerceIn(0.0, 1.0)).toInt()).coerceIn(0, characterTotal)
    }

    suspend fun alignAudioToText() {
        deps.sasayakiPositionSync.alignAudioToBookmark(entry)
        audioPosition = deps.sasayakiPositionSync.audioPosition(entry)
    }

    suspend fun setDocumentIdOverride(documentId: String?) {
        deps.kosyncManager.setDocumentIdOverride(entry, documentId)
        refresh()
    }

    private suspend fun syncOptions(): SyncOptions {
        val readerSettings = deps.readerSettingsRepository.settings.first()
        val sasayakiSettings = deps.sasayakiSettingsRepository.settings.first()
        return SyncOptions(
            syncStats = readerSettings.statisticsSyncEnabled,
            statsSyncMode = readerSettings.statisticsSyncMode,
            syncAudioBook = sasayakiSettings.enabled && sasayakiSettings.syncEnabled,
        )
    }
}
