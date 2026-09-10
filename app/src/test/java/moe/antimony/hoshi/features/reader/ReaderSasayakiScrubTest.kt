package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSasayakiScrubTest {
    @Test
    fun stepsChangeOnlyWhenTravelCrossesAStepBoundary() {
        val tracker = ReaderSasayakiScrubGestureTracker(stepPx = 40f)

        assertFalse(tracker.onDrag(39f))
        assertEquals(0, tracker.steps)
        assertTrue(tracker.onDrag(1f))
        assertEquals(1, tracker.steps)
        assertFalse(tracker.onDrag(30f))
        assertEquals(1, tracker.steps)
        assertTrue(tracker.onDrag(-70f))
        assertEquals(0, tracker.steps)
        assertTrue(tracker.onDrag(-85f))
        assertEquals(-2, tracker.steps)
    }

    @Test
    fun resetClearsAccumulatedTravel() {
        val tracker = ReaderSasayakiScrubGestureTracker(stepPx = 40f)
        tracker.onDrag(100f)
        tracker.reset()

        assertEquals(0, tracker.steps)
        assertFalse(tracker.onDrag(39f))
    }

    @Test
    fun signedStepsFollowTheButtonUnderTheDragDirection() {
        val normal = readerSasayakiBottomSkipButtonActions(verticalWriting = false, reverseVerticalReaderSkipButtons = true)
        assertEquals(3, readerSasayakiScrubSignedSteps(3, normal))
        assertEquals(-2, readerSasayakiScrubSignedSteps(-2, normal))
        assertEquals(0, readerSasayakiScrubSignedSteps(0, normal))

        val reversed = readerSasayakiBottomSkipButtonActions(verticalWriting = true, reverseVerticalReaderSkipButtons = true)
        assertEquals(-3, readerSasayakiScrubSignedSteps(3, reversed))
        assertEquals(2, readerSasayakiScrubSignedSteps(-2, reversed))
    }
}
