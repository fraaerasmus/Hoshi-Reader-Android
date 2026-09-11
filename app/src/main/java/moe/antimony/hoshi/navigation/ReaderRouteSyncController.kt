package moe.antimony.hoshi.navigation

import moe.antimony.hoshi.epub.Bookmark
import moe.antimony.hoshi.features.reader.ReaderChapterPosition
import moe.antimony.hoshi.features.sync.BackendOutcome

/** Where a sync wants the reader to go, and where it came from so the move can be undone. */
data class ReaderSyncJump(
    val target: ReaderChapterPosition,
    val origin: ReaderChapterPosition?,
    /** The reader already opened at [target]; only the jump history needs [origin] pushed onto it. */
    val seedOnly: Boolean,
)

internal sealed interface ReaderSyncPlan {
    data object None : ReaderSyncPlan

    data class Apply(val jump: ReaderSyncJump) : ReaderSyncPlan

    /** The reader moved after the sync started: keep the reader's position and re-save it so it wins the next round. */
    data class Ignore(val reSave: ReaderChapterPosition) : ReaderSyncPlan
}

/**
 * Decides what to do with a remote position that arrived after the reader was already open.
 *
 * [bookmarkAtOpen] is the bookmark `load()` read, [lastReaderSave] the last position the reader itself saved
 * (null when the user has not moved) and [openPosition] where the reader was opened.
 */
internal fun planReaderSync(
    applied: BackendOutcome.Applied?,
    bookmarkAtOpen: Bookmark?,
    lastReaderSave: ReaderChapterPosition?,
    openPosition: ReaderChapterPosition,
): ReaderSyncPlan {
    if (applied == null) return ReaderSyncPlan.None
    if (lastReaderSave != null && lastReaderSave != openPosition) return ReaderSyncPlan.Ignore(lastReaderSave)
    val target = applied.bookmark.toReaderPosition()
    val origin = (applied.previous ?: bookmarkAtOpen)?.toReaderPosition()
    // The pull can land before load() reads the bookmark, in which case the reader is already there.
    val alreadyOpenAtTarget = target == openPosition
    if (alreadyOpenAtTarget) {
        if (origin == null || origin == openPosition) return ReaderSyncPlan.None
        return ReaderSyncPlan.Apply(ReaderSyncJump(target = target, origin = origin, seedOnly = true))
    }
    return ReaderSyncPlan.Apply(ReaderSyncJump(target = target, origin = origin, seedOnly = false))
}

internal fun Bookmark.toReaderPosition(): ReaderChapterPosition =
    ReaderChapterPosition(index = chapterIndex, progress = progress)
