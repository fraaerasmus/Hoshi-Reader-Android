package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderEdgeTapHoldTrackerTest {
    private val left = ReaderEdgeSwipeGestureTracker.Edge.Left

    @Test
    fun holdStartsOnTimeoutAndEndsOnUp() {
        val tracker = ReaderEdgeTapHoldTracker(slopPx = 10f, doubleTapTimeoutMs = 300)
        assertTrue(tracker.onDown(left, 5f, 500f, holdEnabled = true, doubleTapEnabled = false))
        assertFalse(tracker.onMove(8f, 504f))
        assertTrue(tracker.onHoldTimeout())
        assertTrue(tracker.isHolding)
        assertEquals(ReaderEdgeTapHoldTracker.Result.HoldEnd, tracker.onUp(1_000))
        assertFalse(tracker.isHolding)
    }

    @Test
    fun movingPastSlopCancelsTheHoldCandidate() {
        val tracker = ReaderEdgeTapHoldTracker(slopPx = 10f, doubleTapTimeoutMs = 300)
        tracker.onDown(left, 5f, 500f, holdEnabled = true, doubleTapEnabled = true)
        assertTrue(tracker.onMove(5f, 540f))
        assertFalse(tracker.onHoldTimeout())
        assertEquals(ReaderEdgeTapHoldTracker.Result.None, tracker.onUp(200))
    }

    @Test
    fun twoQuickTapsOnTheSameEdgeAreADoubleTap() {
        val tracker = ReaderEdgeTapHoldTracker(slopPx = 10f, doubleTapTimeoutMs = 300)
        tracker.onDown(left, 5f, 500f, holdEnabled = false, doubleTapEnabled = true)
        assertEquals(ReaderEdgeTapHoldTracker.Result.Tap(left), tracker.onUp(100))
        tracker.onDown(left, 6f, 502f, holdEnabled = false, doubleTapEnabled = true)
        assertEquals(ReaderEdgeTapHoldTracker.Result.DoubleTap(left), tracker.onUp(350))
        // The pair is consumed: a third tap starts over.
        tracker.onDown(left, 6f, 502f, holdEnabled = false, doubleTapEnabled = true)
        assertEquals(ReaderEdgeTapHoldTracker.Result.Tap(left), tracker.onUp(500))
    }

    @Test
    fun tapsOutsideTheWindowOrOnAnotherEdgeDoNotPair() {
        val tracker = ReaderEdgeTapHoldTracker(slopPx = 10f, doubleTapTimeoutMs = 300)
        tracker.onDown(left, 5f, 500f, holdEnabled = false, doubleTapEnabled = true)
        tracker.onUp(100)
        tracker.onDown(ReaderEdgeSwipeGestureTracker.Edge.Right, 995f, 500f, holdEnabled = false, doubleTapEnabled = true)
        assertEquals(ReaderEdgeTapHoldTracker.Result.Tap(ReaderEdgeSwipeGestureTracker.Edge.Right), tracker.onUp(200))
        tracker.onDown(ReaderEdgeSwipeGestureTracker.Edge.Right, 995f, 500f, holdEnabled = false, doubleTapEnabled = true)
        assertEquals(ReaderEdgeTapHoldTracker.Result.Tap(ReaderEdgeSwipeGestureTracker.Edge.Right), tracker.onUp(900))
    }

    @Test
    fun centreTouchesAndDisabledEdgesDoNothing() {
        val tracker = ReaderEdgeTapHoldTracker(slopPx = 10f, doubleTapTimeoutMs = 300)
        assertFalse(tracker.onDown(ReaderEdgeSwipeGestureTracker.Edge.None, 500f, 500f, holdEnabled = true, doubleTapEnabled = true))
        assertEquals(ReaderEdgeTapHoldTracker.Result.None, tracker.onUp(100))
        assertFalse(tracker.onDown(left, 5f, 500f, holdEnabled = false, doubleTapEnabled = false))
        assertFalse(tracker.onHoldTimeout())
        assertEquals(ReaderEdgeTapHoldTracker.Result.None, tracker.onUp(100))
        assertEquals(ReaderEdgeSwipeGestureTracker.Edge.Left, readerEdgeForTouch(30f, 1000, 1f))
        assertEquals(ReaderEdgeSwipeGestureTracker.Edge.None, readerEdgeForTouch(500f, 1000, 1f))
    }

    @Test
    fun dockGeometryClampsToTheEdge() {
        assertEquals(0, readerDockTopDp(0f, 800, 56))
        assertEquals(744, readerDockTopDp(1f, 800, 56))
        assertEquals(372, readerDockTopDp(0.5f, 800, 56))
        assertEquals(0, readerDockTopDp(0.5f, 40, 56))
        assertEquals(0.5f, readerDockFraction(372f, 800, 56), 0.01f)
        assertEquals(1f, readerDockFraction(9_999f, 800, 56), 0f)
        assertEquals(372, readerDockClusterTopDp(tabTopDp = 400, edgeLengthDp = 800, clusterLengthDp = 112))
        assertEquals(688, readerDockClusterTopDp(tabTopDp = 790, edgeLengthDp = 800, clusterLengthDp = 112))
    }

    @Test
    fun dockDropSnapsToTheNearestEdgeWithinTheBand() {
        fun drop(x: Float, y: Float) = readerDockDropTarget(x, y, widthPx = 1000f, heightPx = 2000f, tabLengthPx = 100f, snapBandPx = 72f)
        assertEquals(ReaderDockDrop(moe.antimony.hoshi.features.reader.input.SasayakiControlsPlacement.Left, 0.5f), drop(20f, 1000f))
        assertEquals(ReaderDockDrop(moe.antimony.hoshi.features.reader.input.SasayakiControlsPlacement.Right, 0f), drop(990f, 10f))
        assertEquals(ReaderDockDrop(moe.antimony.hoshi.features.reader.input.SasayakiControlsPlacement.BottomDock, 1f), drop(990f, 1990f))
        assertEquals(moe.antimony.hoshi.features.reader.input.SasayakiControlsPlacement.BottomDock, drop(500f, 1950f)?.placement)
        assertEquals(null, drop(500f, 1000f))
    }
}
