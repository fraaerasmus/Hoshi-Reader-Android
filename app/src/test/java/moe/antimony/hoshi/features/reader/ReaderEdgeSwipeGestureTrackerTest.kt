package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderEdgeSwipeGestureTrackerTest {
    // density = 1f so dp == px. Edge zone = min(1000*0.08, 64) = 64px; activation = 16px.
    private val width = 1000
    private val height = 2000

    @Test
    fun leftEdgeUpwardDragActivatesBrightnessWithPositiveFraction() {
        val tracker = ReaderEdgeSwipeGestureTracker()
        tracker.onDown(30f, 1000f, width, height, density = 1f)
        assertEquals(ReaderEdgeSwipeGestureTracker.Edge.Left, tracker.edge)

        assertTrue(tracker.onMove(34f, 900f)) // activates (dy = -100, vertical-dominant)
        assertTrue(tracker.isActive)
        assertTrue(tracker.onMove(34f, 800f)) // further up
        assertTrue(tracker.fraction > 0f)
    }

    @Test
    fun rightEdgeDownwardDragActivatesVolumeWithNegativeFraction() {
        val tracker = ReaderEdgeSwipeGestureTracker()
        tracker.onDown(980f, 1000f, width, height, density = 1f)
        assertEquals(ReaderEdgeSwipeGestureTracker.Edge.Right, tracker.edge)

        assertTrue(tracker.onMove(976f, 900f)) // activates
        assertTrue(tracker.onMove(976f, 1100f)) // drag down past the activation point
        assertTrue(tracker.fraction < 0f)
    }

    @Test
    fun centerTouchIsNotAnEdgeGesture() {
        val tracker = ReaderEdgeSwipeGestureTracker()
        tracker.onDown(500f, 1000f, width, height, density = 1f)
        assertEquals(ReaderEdgeSwipeGestureTracker.Edge.None, tracker.edge)

        assertFalse(tracker.onMove(500f, 800f))
        assertFalse(tracker.isActive)
    }

    @Test
    fun horizontalDragInEdgeZoneDoesNotActivate() {
        val tracker = ReaderEdgeSwipeGestureTracker()
        tracker.onDown(30f, 1000f, width, height, density = 1f)

        assertFalse(tracker.onMove(220f, 1006f)) // mostly horizontal
        assertFalse(tracker.isActive)
    }

    @Test
    fun smallVerticalMovementBelowThresholdDoesNotActivate() {
        val tracker = ReaderEdgeSwipeGestureTracker()
        tracker.onDown(30f, 1000f, width, height, density = 1f)

        assertFalse(tracker.onMove(30f, 990f)) // dy = -10, below 16px activation
        assertFalse(tracker.isActive)
    }

    @Test
    fun cancelResetsState() {
        val tracker = ReaderEdgeSwipeGestureTracker()
        tracker.onDown(30f, 1000f, width, height, density = 1f)
        assertTrue(tracker.onMove(34f, 900f))
        assertTrue(tracker.isActive)

        tracker.onCancel()
        assertFalse(tracker.isActive)
        assertEquals(ReaderEdgeSwipeGestureTracker.Edge.None, tracker.edge)
    }

    @Test
    fun zeroSizedViewIsIgnored() {
        val tracker = ReaderEdgeSwipeGestureTracker()
        tracker.onDown(0f, 0f, viewWidthPx = 0, viewHeightPx = 0, density = 1f)
        assertEquals(ReaderEdgeSwipeGestureTracker.Edge.None, tracker.edge)
        assertFalse(tracker.onMove(0f, 100f))
    }
}
