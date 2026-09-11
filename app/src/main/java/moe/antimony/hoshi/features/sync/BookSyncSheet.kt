package moe.antimony.hoshi.features.sync

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.antimony.hoshi.LocalHoshiUiDependencies
import moe.antimony.hoshi.R
import moe.antimony.hoshi.epub.BookEntry
import moe.antimony.hoshi.epub.BookInfo
import moe.antimony.hoshi.epub.Bookmark
import moe.antimony.hoshi.epub.EpubBook
import moe.antimony.hoshi.epub.PositionTrailEntry
import moe.antimony.hoshi.features.reader.ReaderChapterPosition
import moe.antimony.hoshi.ui.hoshiOutlinedTextFieldColors
import moe.antimony.hoshi.ui.resolve

/**
 * Everything one book's sync backends know, in one panel: where the local position is, how each backend compares,
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
    val scope = rememberCoroutineScope()
    val state = remember(entry.metadata.id) { BookSyncSheetState(entry, deps) }
    var editingDocumentId by remember { mutableStateOf(false) }
    LaunchedEffect(state) { state.refresh() }

    /** A backend wrote a new bookmark; in the reader that has to become an actual jump. */
    fun presentApplied(report: ProgressSyncReport) {
        val bookmark = report.applied?.bookmark ?: return
        if (onApplyLocalPosition != null) {
            onApplyLocalPosition(ReaderChapterPosition(bookmark.chapterIndex, bookmark.progress))
        } else {
            onLocalPositionChanged()
        }
    }

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BookSyncHeader(state)
        state.backends.forEach { backend ->
            HorizontalDivider()
            BookSyncBackendRow(
                backend = backend,
                status = state.statuses.firstOrNull { it.backend == backend },
                busy = state.busy != null,
                onPull = {
                    scope.launch {
                        presentApplied(
                            state.perform(backend) { options ->
                                deps.progressSyncCoordinator.pull(entry, book, options, manual = true, backends = setOf(backend))
                            },
                        )
                    }
                },
                onPush = {
                    scope.launch {
                        state.perform(backend) { options ->
                            deps.progressSyncCoordinator.push(entry, book, options, manual = true, backends = setOf(backend))
                        }
                    }
                },
            )
        }
        if (state.backends.isNotEmpty()) {
            HorizontalDivider()
            TextButton(
                enabled = state.busy == null,
                onClick = {
                    scope.launch {
                        presentApplied(
                            state.perform(null) { options ->
                                deps.progressSyncCoordinator.syncAll(entry, book, options, direction = null)
                            },
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.sync_sheet_sync_all))
            }
        }
        if (state.hasAudioMatch) {
            HorizontalDivider()
            Text(stringResource(R.string.sync_sheet_audiobook), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { scope.launch { state.alignAudioToText() } }) {
                    Text(stringResource(R.string.sync_sheet_move_audio_to_text))
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            state.readerPositionAtAudio()?.let { (chapterIndex, progress) ->
                                applyPosition(chapterIndex, progress, PositionTrailEntry.SourceAudio)
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.sync_sheet_move_text_to_audio))
                }
            }
        }
        if (state.trail.isNotEmpty()) {
            HorizontalDivider()
            Text(stringResource(R.string.sync_sheet_recent_positions), style = MaterialTheme.typography.titleSmall)
            state.trail.asReversed().forEach { trailEntry ->
                Text(
                    text = stringResource(
                        R.string.sync_sheet_trail_entry_format,
                        state.percentOf(trailEntry.characterCount),
                        stringResource(positionTrailSourceLabel(trailEntry.source)),
                        relativeTime(trailEntry.recordedAt),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            applyPosition(trailEntry.chapterIndex, trailEntry.progress, PositionTrailEntry.SourceUndo)
                        }
                        .padding(vertical = 6.dp),
                )
            }
        }
        if (state.kosyncEnabled) {
            HorizontalDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sync_sheet_document_id), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = state.documentId.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { editingDocumentId = true }) {
                    Text(stringResource(R.string.action_edit))
                }
            }
        }
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
                TextButton(
                    onClick = {
                        editingDocumentId = false
                        scope.launch { state.setDocumentIdOverride(null) }
                    },
                ) {
                    Text(stringResource(R.string.action_clear))
                }
            },
        )
    }
}

@Composable
private fun BookSyncHeader(state: BookSyncSheetState) {
    val bookmark = state.bookmark
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.sync_sheet_local_position), style = MaterialTheme.typography.titleSmall)
        Text(
            text = "${state.percentOf(bookmark?.characterCount ?: 0)}%",
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        bookmark?.lastModified?.let {
            Text(
                text = stringResource(R.string.sync_sheet_saved_format, relativeTime(it)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BookSyncBackendRow(
    backend: SyncBackend,
    status: BackendStatus?,
    busy: Boolean,
    onPull: () -> Unit,
    onPush: () -> Unit,
) {
    val comparison = status?.comparison
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val resources = LocalResources.current
        Column(modifier = Modifier.weight(1f)) {
            Text(backend.displayName, style = MaterialTheme.typography.titleSmall)
            val chip = status?.error?.resolve(resources) ?: stringResource(syncComparisonChipLabel(comparison))
            AssistChip(onClick = {}, enabled = false, label = { Text(chip, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
        TextButton(enabled = !busy && comparison == SyncComparison.ServerNewer, onClick = onPull) {
            Text(stringResource(R.string.action_pull))
        }
        TextButton(
            enabled = !busy && (comparison == SyncComparison.LocalNewer || comparison == SyncComparison.NoRecord),
            onClick = onPush,
        ) {
            Text(stringResource(R.string.action_push))
        }
    }
}

private fun syncComparisonChipLabel(comparison: SyncComparison?): Int =
    when (comparison) {
        SyncComparison.Synced -> R.string.sync_chip_synced
        SyncComparison.ServerNewer -> R.string.sync_chip_server_newer
        SyncComparison.LocalNewer -> R.string.sync_chip_local_newer
        SyncComparison.NoRecord -> R.string.sync_chip_no_record
        null -> R.string.sync_chip_offline
    }

private fun positionTrailSourceLabel(source: String): Int =
    when (source) {
        PositionTrailEntry.SourceKosync -> R.string.sync_source_kosync
        PositionTrailEntry.SourceDrive -> R.string.sync_source_drive
        PositionTrailEntry.SourceAudio -> R.string.sync_source_audio
        PositionTrailEntry.SourceJump -> R.string.sync_source_jump
        else -> R.string.sync_source_undo
    }

private fun relativeTime(appleReferenceSeconds: Double): String =
    DateUtils.getRelativeTimeSpanString(
        TtuSyncRules.appleReferenceSecondsToUnixMillis(appleReferenceSeconds),
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()

internal class BookSyncSheetState(
    private val entry: BookEntry,
    private val deps: moe.antimony.hoshi.HoshiUiDependencies,
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
    var backends by mutableStateOf<List<SyncBackend>>(emptyList())
        private set
    var busy by mutableStateOf<SyncBackend?>(null)
        private set
    var documentId by mutableStateOf<String?>(null)
        private set
    var hasAudioMatch by mutableStateOf(false)
        private set
    var kosyncEnabled by mutableStateOf(false)
        private set

    fun percentOf(characterCount: Int): Int =
        if (characterTotal > 0) (characterCount.toDouble() / characterTotal).coerceIn(0.0, 1.0).toPercent() else 0

    suspend fun refresh() {
        bookmark = deps.bookRepository.loadBookmark(entry.root)
        val bookInfo = deps.bookRepository.loadBookInfo(entry.root)
        characterTotal = bookInfo?.characterCount ?: 0
        chapterInfo = bookInfo?.chapterInfo
        trail = deps.bookRepository.loadPositionTrail(entry.root).entries
        hasAudioMatch = deps.bookRepository.loadSasayakiMatch(entry.root)?.matches?.isNotEmpty() == true
        kosyncEnabled = deps.kosyncSettingsRepository.settings.first().enabled
        documentId = if (kosyncEnabled) deps.kosyncManager.documentId(entry) else null
        backends = deps.progressSyncCoordinator.enabledBackends(manual = true).sortedBy { it.ordinal }
        statuses = deps.progressSyncCoordinator.status(entry)
    }

    /** Runs one sync action with [backend] marked busy (null means "all backends"), then reloads the panel. */
    suspend fun perform(backend: SyncBackend?, action: suspend (SyncOptions) -> ProgressSyncReport): ProgressSyncReport {
        busy = backend ?: SyncBackend.Ttu
        val report = try {
            action(syncOptions())
        } finally {
            busy = null
        }
        refresh()
        return report
    }

    suspend fun saveLocalPosition(chapterIndex: Int, progress: Double, source: String) {
        val now = deps.bookRepository.currentAppleReferenceDateSeconds()
        val target = PositionTrailEntry(
            chapterIndex = chapterIndex,
            progress = progress,
            characterCount = characterCountAt(chapterIndex, progress),
            source = source,
            recordedAt = now,
        )
        deps.progressSyncCoordinator.restorePosition(entry, target, source)
    }

    private fun characterCountAt(chapterIndex: Int, progress: Double): Int {
        val info = chapterInfo?.values?.firstOrNull { it.spineIndex == chapterIndex } ?: return 0
        return (info.currentTotal + (info.chapterCount * progress.coerceIn(0.0, 1.0)).toInt()).coerceIn(0, characterTotal)
    }

    suspend fun alignAudioToText() {
        deps.sasayakiPositionSync.alignAudioToBookmark(entry)
    }

    suspend fun readerPositionAtAudio(): Pair<Int, Double>? = deps.sasayakiPositionSync.readerPositionAtAudio(entry)

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
