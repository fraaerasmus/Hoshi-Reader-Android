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

    @Test
    fun foregroundSyncAcceptsRemoteProgressAfterEarlierLocalReading() {
        val current = bookmark(2, 0.6)
        val remote = bookmark(4, 0.5)
        val plan = planReaderSync(
            applied = applied(remote, previous = current),
            bookmarkAtOpen = bookmark(0, 0.1),
            lastReaderSave = current.toReaderPosition(),
            openPosition = ReaderChapterPosition(0, 0.1),
            positionAtSyncStart = current.toReaderPosition(),
        )

        assertEquals(
            ReaderSyncPlan.Apply(
                ReaderSyncJump(remote.toReaderPosition(), current.toReaderPosition(), seedOnly = false),
            ),
            plan,
        )
    }

    @Test
    fun foregroundSyncKeepsReadingThatHappenedDuringTheRequest() {
        val plan = planReaderSync(
            applied = applied(bookmark(4, 0.5), previous = bookmark(2, 0.6)),
            bookmarkAtOpen = bookmark(0, 0.1),
            lastReaderSave = ReaderChapterPosition(2, 0.8),
            openPosition = ReaderChapterPosition(0, 0.1),
            positionAtSyncStart = ReaderChapterPosition(2, 0.6),
        )

        assertEquals(ReaderSyncPlan.Ignore(ReaderChapterPosition(2, 0.8)), plan)
    }

    @Test
    fun foregroundSyncJumpsWhenTheRemoteTargetIsTheOriginalOpenPosition() {
        val original = bookmark(0, 0.1)
        val current = bookmark(2, 0.6)
        val plan = planReaderSync(
            applied = applied(original, previous = current),
            bookmarkAtOpen = original,
            lastReaderSave = current.toReaderPosition(),
            openPosition = original.toReaderPosition(),
            positionAtSyncStart = current.toReaderPosition(),
        )

        assertEquals(
            ReaderSyncPlan.Apply(
                ReaderSyncJump(original.toReaderPosition(), current.toReaderPosition(), seedOnly = false),
            ),
            plan,
        )
    }

    @Test
    fun foregroundSyncDoesNotJumpWhenTheReaderIsAlreadyAtTheRemoteTarget() {
        val current = bookmark(2, 0.6)
        val plan = planReaderSync(
            applied = applied(current, previous = current),
            bookmarkAtOpen = bookmark(0, 0.1),
            lastReaderSave = current.toReaderPosition(),
            openPosition = ReaderChapterPosition(0, 0.1),
            positionAtSyncStart = current.toReaderPosition(),
        )

        assertEquals(ReaderSyncPlan.None, plan)
    }

    private fun bookmark(chapterIndex: Int, progress: Double) =
        Bookmark(chapterIndex = chapterIndex, progress = progress, characterCount = 0)

    private fun applied(bookmark: Bookmark, previous: Bookmark?) =
        BackendOutcome.Applied(SyncBackend.Kosync, bookmark = bookmark, previous = previous, percentage = 0.5)
}
