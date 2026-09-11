package moe.antimony.hoshi.navigation

import moe.antimony.hoshi.epub.Bookmark
import moe.antimony.hoshi.features.reader.ReaderChapterPosition
import moe.antimony.hoshi.features.sync.BackendOutcome
import moe.antimony.hoshi.features.sync.SyncBackend
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderRouteSyncControllerTest {
    @Test
    fun noAppliedPositionLeavesTheReaderAlone() {
        assertEquals(
            ReaderSyncPlan.None,
            planReaderSync(
                applied = null,
                bookmarkAtOpen = bookmark(0, 0.1),
                lastReaderSave = null,
                openPosition = ReaderChapterPosition(0, 0.1),
            ),
        )
    }

    @Test
    fun aRemotePositionJumpsWithTheOpenPositionAsTheUndoTarget() {
        val plan = planReaderSync(
            applied = applied(bookmark(4, 0.5), previous = bookmark(0, 0.1)),
            bookmarkAtOpen = bookmark(0, 0.1),
            lastReaderSave = null,
            openPosition = ReaderChapterPosition(0, 0.1),
        )

        assertEquals(
            ReaderSyncPlan.Apply(
                ReaderSyncJump(
                    target = ReaderChapterPosition(4, 0.5),
                    origin = ReaderChapterPosition(0, 0.1),
                    seedOnly = false,
                ),
            ),
            plan,
        )
    }

    @Test
    fun aPullThatLandedBeforeTheBookmarkReadOnlySeedsTheJumpHistory() {
        val plan = planReaderSync(
            applied = applied(bookmark(4, 0.5), previous = bookmark(0, 0.1)),
            bookmarkAtOpen = bookmark(4, 0.5),
            lastReaderSave = null,
            openPosition = ReaderChapterPosition(4, 0.5),
        )

        assertEquals(
            ReaderSyncPlan.Apply(
                ReaderSyncJump(
                    target = ReaderChapterPosition(4, 0.5),
                    origin = ReaderChapterPosition(0, 0.1),
                    seedOnly = true,
                ),
            ),
            plan,
        )
    }

    @Test
    fun aReaderThatAlreadyMovedKeepsItsPositionAndReSavesIt() {
        val plan = planReaderSync(
            applied = applied(bookmark(4, 0.5), previous = bookmark(0, 0.1)),
            bookmarkAtOpen = bookmark(0, 0.1),
            lastReaderSave = ReaderChapterPosition(0, 0.6),
            openPosition = ReaderChapterPosition(0, 0.1),
        )

        assertEquals(ReaderSyncPlan.Ignore(ReaderChapterPosition(0, 0.6)), plan)
    }

    @Test
    fun aSaveAtTheOpenPositionDoesNotCountAsUserMovement() {
        val plan = planReaderSync(
            applied = applied(bookmark(4, 0.5), previous = bookmark(0, 0.1)),
            bookmarkAtOpen = bookmark(0, 0.1),
            lastReaderSave = ReaderChapterPosition(0, 0.1),
            openPosition = ReaderChapterPosition(0, 0.1),
        )

        assertEquals(
            ReaderSyncPlan.Apply(
                ReaderSyncJump(ReaderChapterPosition(4, 0.5), ReaderChapterPosition(0, 0.1), seedOnly = false),
            ),
            plan,
        )
    }

    private fun bookmark(chapterIndex: Int, progress: Double) =
        Bookmark(chapterIndex = chapterIndex, progress = progress, characterCount = 0)

    private fun applied(bookmark: Bookmark, previous: Bookmark?) =
        BackendOutcome.Applied(SyncBackend.Kosync, bookmark = bookmark, previous = previous, percentage = 0.5)
}
