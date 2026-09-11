package moe.antimony.hoshi.features.reader

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

internal abstract class SwipePageTouchListener(
    swipeDistance: Float = DEFAULT_SWIPE_DISTANCE,
) : View.OnTouchListener {
    private val tracker = ReaderSwipeGestureTracker(minDistance = swipeDistance)
    private val edgeTracker = ReaderEdgeSwipeGestureTracker()
    private var tapHoldTracker: ReaderEdgeTapHoldTracker? = null
    private var holdView: View? = null
    private val holdTimeout = Runnable { onEdgeHoldTimeout() }
    private var pendingTap: Runnable? = null

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (shouldIgnoreReaderGesture(event)) {
            tracker.suppressCurrentGesture()
            edgeTracker.onCancel()
            cancelEdgeHold(view)
            return false
        }
        // A mouse drag selects text: no swipe, no edge gesture, and never an ACTION_CANCEL into the WebView.
        val isMouse = event.isMouse()
        if (!isMouse) {
            if (handleEdgeTapHold(view, event)) {
                tracker.suppressCurrentGesture()
                edgeTracker.onCancel()
                return true
            }
            if (handleEdgeSwipe(view, event)) {
                tracker.suppressCurrentGesture()
                cancelEdgeHold(view)
                return true
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> tracker.onDown(event.x, event.y, event.eventTime, allowSwipe = !isMouse)
            MotionEvent.ACTION_POINTER_DOWN -> tracker.onAdditionalPointerDown()
            MotionEvent.ACTION_MOVE -> dispatch(tracker.onMove(event.x, event.y, event.eventTime))
            MotionEvent.ACTION_UP -> dispatch(tracker.onUp(event.x, event.y, event.eventTime), view, isMouse)
            MotionEvent.ACTION_CANCEL -> tracker.onCancel()
        }
        return false
    }

    /**
     * Hold and double-tap in the edge zones. The WebView keeps receiving the touch until a hold starts,
     * at which point its in-flight gesture is cancelled so no text selection appears under the finger.
     */
    private fun handleEdgeTapHold(view: View, event: MotionEvent): Boolean {
        val tapHold = tapHoldTracker ?: ReaderEdgeTapHoldTracker(
            slopPx = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat(),
            doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong(),
        ).also { tapHoldTracker = it }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val edge = readerEdgeForTouch(event.x, view.width, view.resources.displayMetrics.density, edgeZoneWidthDp())
                val schedule = tapHold.onDown(
                    edge = edge,
                    x = event.x,
                    y = event.y,
                    holdEnabled = isEdgeHoldEnabled(edge),
                    doubleTapEnabled = isEdgeDoubleTapEnabled(edge),
                )
                if (schedule) {
                    holdView = view
                    view.postDelayed(holdTimeout, EDGE_HOLD_TIMEOUT_MS)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> cancelEdgeHold(view)
            MotionEvent.ACTION_MOVE -> {
                if (tapHold.isHolding) return true
                if (tapHold.onMove(event.x, event.y)) view.removeCallbacks(holdTimeout)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.removeCallbacks(holdTimeout)
                val result = if (event.actionMasked == MotionEvent.ACTION_UP) tapHold.onUp(event.eventTime) else tapHold.onCancel().let { null }
                when (result) {
                    ReaderEdgeTapHoldTracker.Result.HoldEnd -> {
                        onEdgeHoldEnd()
                        return true
                    }
                    is ReaderEdgeTapHoldTracker.Result.DoubleTap -> {
                        pendingTap?.let(view::removeCallbacks)
                        pendingTap = null
                        tracker.suppressCurrentGesture()
                        // The WebView saw this touch go down; without a cancel its long-press would select the nearest word.
                        cancelWebViewGesture(view, event)
                        onEdgeDoubleTap(result.edge)
                        return true
                    }
                    else -> Unit
                }
            }
        }
        return false
    }

    private fun onEdgeHoldTimeout() {
        val view = holdView ?: return
        val tapHold = tapHoldTracker ?: return
        if (!tapHold.onHoldTimeout()) return
        tracker.suppressCurrentGesture()
        edgeTracker.onCancel()
        cancelWebViewGesture(view, null)
        onEdgeHoldStart(tapHold.edge)
    }

    private fun cancelEdgeHold(view: View) {
        view.removeCallbacks(holdTimeout)
        tapHoldTracker?.onCancel()
    }

    /** Returns true when the event is consumed by an active edge-zone drag. */
    private fun handleEdgeSwipe(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> edgeTracker.onDown(
                x = event.x,
                y = event.y,
                viewWidthPx = view.width,
                viewHeightPx = view.height,
                density = view.resources.displayMetrics.density,
                zoneWidthDp = edgeZoneWidthDp(),
            )
            MotionEvent.ACTION_MOVE -> {
                val wasActive = edgeTracker.isActive
                // A page turn already won this gesture; don't also start an edge adjustment.
                if (!wasActive && tracker.didDispatchSwipe) return false
                if (!wasActive && !isEdgeDragEnabled(edgeTracker.edge)) return false
                if (edgeTracker.onMove(event.x, event.y)) {
                    // First take-over: cancel the WebView's in-flight long-press/scroll so it does
                    // not leave a stray text selection or half-applied scroll behind.
                    if (!wasActive) cancelWebViewGesture(view, event)
                    onEdgeDrag(edgeTracker.edge, edgeTracker.fraction)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasActive = edgeTracker.isActive
                edgeTracker.onCancel()
                if (wasActive) {
                    onEdgeDragEnd()
                    return true
                }
            }
        }
        return false
    }

    private fun cancelWebViewGesture(view: View, event: MotionEvent?) {
        val now = android.os.SystemClock.uptimeMillis()
        val cancel = if (event != null) MotionEvent.obtain(event) else MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        cancel.action = MotionEvent.ACTION_CANCEL
        // Deliver straight to the View's own handler, bypassing this OnTouchListener.
        view.onTouchEvent(cancel)
        cancel.recycle()
    }

    open fun onLeftSwipe() = Unit
    open fun onRightSwipe() = Unit
    open fun onTap(x: Float, y: Float, isMouse: Boolean = false) = Unit
    open fun shouldIgnoreReaderGesture(event: MotionEvent): Boolean = false

    open fun edgeZoneWidthDp(): Float = READER_DEFAULT_EDGE_ZONE_DP
    open fun isEdgeHoldEnabled(edge: ReaderEdgeSwipeGestureTracker.Edge): Boolean = false
    open fun isEdgeDoubleTapEnabled(edge: ReaderEdgeSwipeGestureTracker.Edge): Boolean = false
    open fun isEdgeDragEnabled(edge: ReaderEdgeSwipeGestureTracker.Edge): Boolean = false
    open fun onEdgeHoldStart(edge: ReaderEdgeSwipeGestureTracker.Edge) = Unit
    open fun onEdgeHoldEnd() = Unit
    open fun onEdgeDoubleTap(edge: ReaderEdgeSwipeGestureTracker.Edge) = Unit
    open fun onEdgeDrag(edge: ReaderEdgeSwipeGestureTracker.Edge, fraction: Float) = Unit
    open fun onEdgeDragEnd() = Unit

    private fun dispatch(result: ReaderSwipeGestureTracker.Result, view: View? = null, isMouse: Boolean = false) {
        when (result) {
            ReaderSwipeGestureTracker.Result.LeftSwipe -> onLeftSwipe()
            ReaderSwipeGestureTracker.Result.RightSwipe -> onRightSwipe()
            is ReaderSwipeGestureTracker.Result.Tap -> {
                // A tap in an edge zone with a double-tap bound waits out the double-tap window first.
                val edge = view?.let { readerEdgeForTouch(result.x, it.width, it.resources.displayMetrics.density, edgeZoneWidthDp()) }
                if (view != null && edge != null && !isMouse && isEdgeDoubleTapEnabled(edge)) {
                    pendingTap?.let(view::removeCallbacks)
                    val runnable = Runnable {
                        pendingTap = null
                        onTap(result.x, result.y)
                    }
                    pendingTap = runnable
                    view.postDelayed(runnable, ViewConfiguration.getDoubleTapTimeout().toLong())
                } else {
                    onTap(result.x, result.y, isMouse)
                }
            }
            ReaderSwipeGestureTracker.Result.None -> Unit
        }
    }

    private companion object {
        const val DEFAULT_SWIPE_DISTANCE = 72f
        /** Shorter than the WebView's own long-press so the hold wins the race and text selection never starts. */
        const val EDGE_HOLD_TIMEOUT_MS = 320L
    }
}

internal class ReaderSwipeGestureTracker(
    private val minDistance: Float,
) {
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var hasDown = false
    private var swipeDispatched = false

    /** True once a page swipe has fired in the current gesture (until the next down/cancel). */
    val didDispatchSwipe: Boolean
        get() = swipeDispatched

    private var swipeAllowed = true

    /** [allowSwipe] is false for a mouse: movement is a text selection, only a click without movement is a tap. */
    fun onDown(x: Float, y: Float, eventTime: Long, allowSwipe: Boolean = true) {
        downX = x
        downY = y
        downTime = eventTime
        hasDown = true
        swipeDispatched = false
        swipeAllowed = allowSwipe
    }

    fun onMove(x: Float, y: Float, eventTime: Long): Result {
        if (!hasDown || !swipeAllowed || swipeDispatched || minDistance <= 0f) return Result.None
        val dx = x - downX
        val dy = y - downY
        val elapsedMs = (eventTime - downTime).coerceAtLeast(1L)
        val velocityX = abs(dx) * 1_000f / elapsedMs
        val hasPageDistance = abs(dx) >= minDistance
        val hasFastFlickDistance = abs(dx) >= minDistance / 2f &&
            velocityX >= MIN_FAST_FLICK_VELOCITY_PX_PER_SECOND
        if (
            !hasPageDistance && !hasFastFlickDistance ||
            elapsedMs > MAX_EARLY_SWIPE_DURATION_MS ||
            velocityX < MIN_EARLY_SWIPE_VELOCITY_PX_PER_SECOND
        ) {
            return Result.None
        }
        swipeDispatched = true
        return if (dx < 0f) Result.LeftSwipe else Result.RightSwipe
    }

    fun onUp(x: Float, y: Float, eventTime: Long): Result {
        if (!hasDown) return Result.None
        val dx = x - downX
        val dy = y - downY
        val elapsedMs = eventTime - downTime
        val wasSwipeDispatched = swipeDispatched
        onCancel()
        return if (
            !wasSwipeDispatched &&
            elapsedMs <= MAX_TAP_DURATION_MS &&
            abs(dx) < TAP_SLOP &&
            abs(dy) < TAP_SLOP
        ) {
            Result.Tap(x, y)
        } else {
            Result.None
        }
    }

    fun onCancel() {
        hasDown = false
        swipeDispatched = false
    }

    fun suppressCurrentGesture() {
        onCancel()
    }

    fun onAdditionalPointerDown() {
        suppressCurrentGesture()
    }

    sealed class Result {
        data object None : Result()
        data object LeftSwipe : Result()
        data object RightSwipe : Result()
        data class Tap(val x: Float, val y: Float) : Result()
    }

    private companion object {
        const val TAP_SLOP = 72f
        const val MIN_FAST_FLICK_VELOCITY_PX_PER_SECOND = 900f
        const val MAX_EARLY_SWIPE_DURATION_MS = 300L
        const val MAX_TAP_DURATION_MS = 500L
        const val MIN_EARLY_SWIPE_VELOCITY_PX_PER_SECOND = 360f
    }
}

/**
 * Detects a vertical drag that starts inside the left or right screen edge zone, used to map the
 * left edge to brightness and the right edge to volume. Allocation-free in the move path: callers
 * read [edge] and [fraction] fields after [onMove] returns true.
 *
 * [fraction] is the signed vertical travel since the gesture activated, normalized to the view
 * height (positive = upward = increase), so a full-height drag spans roughly the full range.
 */
internal class ReaderEdgeSwipeGestureTracker {
    enum class Edge { None, Left, Right }

    var edge: Edge = Edge.None
        private set
    var fraction: Float = 0f
        private set
    var isActive: Boolean = false
        private set

    private var downX = 0f
    private var downY = 0f
    private var activationY = 0f
    private var viewHeightPx = 1f
    private var activationPx = 0f
    private var hasDown = false

    fun onDown(x: Float, y: Float, viewWidthPx: Int, viewHeightPx: Int, density: Float, zoneWidthDp: Float = READER_DEFAULT_EDGE_ZONE_DP) {
        reset()
        if (viewWidthPx <= 0 || viewHeightPx <= 0) return
        edge = readerEdgeForTouch(x, viewWidthPx, density, zoneWidthDp)
        if (edge == Edge.None) return
        downX = x
        downY = y
        this.viewHeightPx = viewHeightPx.toFloat()
        activationPx = ACTIVATION_DP * density
        hasDown = true
    }

    /** Returns true when an active edge drag should be dispatched for this move. */
    fun onMove(x: Float, y: Float): Boolean {
        if (!hasDown) return false
        if (!isActive) {
            val dx = x - downX
            val dy = y - downY
            if (abs(dy) < activationPx || abs(dy) <= abs(dx)) return false
            isActive = true
            activationY = y
        }
        fraction = -(y - activationY) / viewHeightPx
        return true
    }

    fun onCancel() = reset()

    private fun reset() {
        edge = Edge.None
        fraction = 0f
        isActive = false
        hasDown = false
        downX = 0f
        downY = 0f
        activationY = 0f
    }

    private companion object {
        const val ACTIVATION_DP = 16f
    }
}

internal fun MotionEvent.isMouse(): Boolean = getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE
