package moe.antimony.hoshi.features.reader

import kotlin.math.abs

/** Which screen edge a touch started in; shared by the edge drag, hold and double-tap trackers. */
internal fun readerEdgeForTouch(
    x: Float,
    viewWidthPx: Int,
    density: Float,
    zoneWidthDp: Float = READER_DEFAULT_EDGE_ZONE_DP,
): ReaderEdgeSwipeGestureTracker.Edge {
    if (viewWidthPx <= 0) return ReaderEdgeSwipeGestureTracker.Edge.None
    // Never let the two strips meet in the middle on a narrow view.
    val zoneWidth = (zoneWidthDp * density).coerceAtMost(viewWidthPx * READER_MAX_EDGE_ZONE_FRACTION)
    return when {
        x <= zoneWidth -> ReaderEdgeSwipeGestureTracker.Edge.Left
        x >= viewWidthPx - zoneWidth -> ReaderEdgeSwipeGestureTracker.Edge.Right
        else -> ReaderEdgeSwipeGestureTracker.Edge.None
    }
}

internal const val READER_DEFAULT_EDGE_ZONE_DP = 32f
internal const val READER_MAX_EDGE_ZONE_FRACTION = 0.4f

/**
 * Hold and double-tap inside the screen edge zones, timer-free: the caller schedules a hold timeout
 * when [onDown] returns true and reports it through [onHoldTimeout]; it delays a single tap by the
 * double-tap window when [onUp] returns [Result.Tap] and the edge's double-tap is bound.
 */
internal class ReaderEdgeTapHoldTracker(
    private val slopPx: Float,
    private val doubleTapTimeoutMs: Long,
) {
    sealed interface Result {
        data object None : Result
        data object HoldEnd : Result
        data class Tap(val edge: ReaderEdgeSwipeGestureTracker.Edge) : Result
        data class DoubleTap(val edge: ReaderEdgeSwipeGestureTracker.Edge) : Result
    }

    var edge: ReaderEdgeSwipeGestureTracker.Edge = ReaderEdgeSwipeGestureTracker.Edge.None
        private set
    var isHolding: Boolean = false
        private set

    private var downX = 0f
    private var downY = 0f
    private var holdCandidate = false
    private var tapCandidate = false
    private var lastTapEdge = ReaderEdgeSwipeGestureTracker.Edge.None
    private var lastTapTimeMs = NO_TAP

    /** Returns true when a hold timer should be scheduled for this touch. */
    fun onDown(edge: ReaderEdgeSwipeGestureTracker.Edge, x: Float, y: Float, holdEnabled: Boolean, doubleTapEnabled: Boolean): Boolean {
        this.edge = edge
        downX = x
        downY = y
        isHolding = false
        holdCandidate = edge != ReaderEdgeSwipeGestureTracker.Edge.None && holdEnabled
        tapCandidate = edge != ReaderEdgeSwipeGestureTracker.Edge.None && doubleTapEnabled
        return holdCandidate
    }

    /** Returns true when the touch moved too far to be a hold or a tap; the caller cancels its timer. */
    fun onMove(x: Float, y: Float): Boolean {
        if (isHolding) return false
        if (!holdCandidate && !tapCandidate) return false
        if (abs(x - downX) <= slopPx && abs(y - downY) <= slopPx) return false
        holdCandidate = false
        tapCandidate = false
        return true
    }

    /** The scheduled hold timer fired; true when the hold really starts. */
    fun onHoldTimeout(): Boolean {
        if (!holdCandidate) return false
        holdCandidate = false
        tapCandidate = false
        isHolding = true
        return true
    }

    fun onUp(timeMs: Long): Result {
        val result = when {
            isHolding -> Result.HoldEnd
            tapCandidate && lastTapEdge == edge && lastTapTimeMs != NO_TAP && timeMs - lastTapTimeMs <= doubleTapTimeoutMs -> {
                lastTapTimeMs = NO_TAP
                Result.DoubleTap(edge)
            }
            tapCandidate -> {
                lastTapEdge = edge
                lastTapTimeMs = timeMs
                Result.Tap(edge)
            }
            else -> Result.None
        }
        reset()
        return result
    }

    fun onCancel() = reset()

    private fun reset() {
        edge = ReaderEdgeSwipeGestureTracker.Edge.None
        isHolding = false
        holdCandidate = false
        tapCandidate = false
    }

    private companion object {
        // A sentinel instead of Long.MIN_VALUE: subtracting MIN_VALUE overflows and made every third tap a double-tap.
        const val NO_TAP = -1L
    }
}

/** What the reader wants from each edge; the WebView only asks and reports, the bindings decide. */
internal interface ReaderEdgeGestureHandler {
    val zoneWidthDp: Float
    fun holdEnabled(edge: ReaderEdgeSwipeGestureTracker.Edge): Boolean
    fun doubleTapEnabled(edge: ReaderEdgeSwipeGestureTracker.Edge): Boolean
    fun dragEnabled(edge: ReaderEdgeSwipeGestureTracker.Edge): Boolean
    fun onHoldStart(edge: ReaderEdgeSwipeGestureTracker.Edge)
    fun onHoldEnd()
    fun onDoubleTap(edge: ReaderEdgeSwipeGestureTracker.Edge)
    fun onDrag(edge: ReaderEdgeSwipeGestureTracker.Edge, fraction: Float)
    fun onDragEnd()

    companion object {
        val None = object : ReaderEdgeGestureHandler {
            override val zoneWidthDp: Float = READER_DEFAULT_EDGE_ZONE_DP
            override fun holdEnabled(edge: ReaderEdgeSwipeGestureTracker.Edge) = false
            override fun doubleTapEnabled(edge: ReaderEdgeSwipeGestureTracker.Edge) = false
            override fun dragEnabled(edge: ReaderEdgeSwipeGestureTracker.Edge) = false
            override fun onHoldStart(edge: ReaderEdgeSwipeGestureTracker.Edge) = Unit
            override fun onHoldEnd() = Unit
            override fun onDoubleTap(edge: ReaderEdgeSwipeGestureTracker.Edge) = Unit
            override fun onDrag(edge: ReaderEdgeSwipeGestureTracker.Edge, fraction: Float) = Unit
            override fun onDragEnd() = Unit
        }
    }
}
