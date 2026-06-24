package moe.antimony.hoshi.features.sasayaki

import org.junit.Assert.assertEquals
import org.junit.Test

class SasayakiVisibleChapterTrackerTest {
    @Test
    fun reportsAttachedChapterAndRemembersIt() {
        val tracker = SasayakiVisibleChapterTracker()
        assertEquals(5, tracker.resolve(5))
        assertEquals(3, tracker.resolve(3))
    }

    @Test
    fun fallsBackToLastKnownWhenDetached() {
        val tracker = SasayakiVisibleChapterTracker()
        tracker.resolve(7)
        // Detached (null) keeps reporting the last attached chapter instead of resetting.
        assertEquals(7, tracker.resolve(null))
        assertEquals(7, tracker.resolve(null))
    }

    @Test
    fun startsAtZeroBeforeAnyReaderAttaches() {
        assertEquals(0, SasayakiVisibleChapterTracker().resolve(null))
    }
}
