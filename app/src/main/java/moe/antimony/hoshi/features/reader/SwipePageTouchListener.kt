package moe.antimony.hoshi.features.reader

import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

abstract class SwipePageTouchListener(
    swipeDistance: Float = DEFAULT_SWIPE_DISTANCE,
) : View.OnTouchListener {
    private val tracker = ReaderSwipeGestureTracker(minDistance = swipeDistance)
    private val edgeTracker = ReaderEdgeSwipeGestureTracker()

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (shouldIgnoreReaderGesture(event)) {
            tracker.suppressCurrentGesture()
            edgeTracker.onCancel()
            return false
        }
        if (isEdgeSwipeEnabled() && handleEdgeSwipe(view, event)) {
            tracker.suppressCurrentGesture()
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> tracker.onDown(event.x, event.y, event.eventTime)
            MotionEvent.ACTION_POINTER_DOWN -> tracker.onAdditionalPointerDown()
            MotionEvent.ACTION_MOVE -> dispatch(tracker.onMove(event.x, event.y, event.eventTime))
            MotionEvent.ACTION_UP -> dispatch(tracker.onUp(event.x, event.y, event.eventTime))
            MotionEvent.ACTION_CANCEL -> tracker.onCancel()
        }
        return false
    }

    /** Returns true when the event is consumed by an active edge-zone brightness/volume drag. */
    private fun handleEdgeSwipe(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> edgeTracker.onDown(
                x = event.x,
                y = event.y,
                viewWidthPx = view.width,
                viewHeightPx = view.height,
                density = view.resources.displayMetrics.density,
            )
            MotionEvent.ACTION_MOVE -> {
                val wasActive = edgeTracker.isActive
                // A page turn already won this gesture; don't also start an edge adjustment.
                if (!wasActive && tracker.didDispatchSwipe) return false
                if (edgeTracker.onMove(event.x, event.y)) {
                    // First take-over: cancel the WebView's in-flight long-press/scroll so it does
                    // not leave a stray text selection or half-applied scroll behind.
                    if (!wasActive) cancelWebViewGesture(view, event)
                    dispatchEdgeDrag()
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

    private fun cancelWebViewGesture(view: View, event: MotionEvent) {
        val cancel = MotionEvent.obtain(event)
        cancel.action = MotionEvent.ACTION_CANCEL
        // Deliver straight to the View's own handler, bypassing this OnTouchListener.
        view.onTouchEvent(cancel)
        cancel.recycle()
    }

    private fun dispatchEdgeDrag() {
        when (edgeTracker.edge) {
            ReaderEdgeSwipeGestureTracker.Edge.Left -> onEdgeBrightnessDrag(edgeTracker.fraction)
            ReaderEdgeSwipeGestureTracker.Edge.Right -> onEdgeVolumeDrag(edgeTracker.fraction)
            ReaderEdgeSwipeGestureTracker.Edge.None -> Unit
        }
    }

    open fun onLeftSwipe() = Unit
    open fun onRightSwipe() = Unit
    open fun onTap(x: Float, y: Float) = Unit
    open fun shouldIgnoreReaderGesture(event: MotionEvent): Boolean = false

    open fun isEdgeSwipeEnabled(): Boolean = false
    open fun onEdgeBrightnessDrag(fraction: Float) = Unit
    open fun onEdgeVolumeDrag(fraction: Float) = Unit
    open fun onEdgeDragEnd() = Unit

    private fun dispatch(result: ReaderSwipeGestureTracker.Result) {
        when (result) {
            ReaderSwipeGestureTracker.Result.LeftSwipe -> onLeftSwipe()
            ReaderSwipeGestureTracker.Result.RightSwipe -> onRightSwipe()
            is ReaderSwipeGestureTracker.Result.Tap -> onTap(result.x, result.y)
            ReaderSwipeGestureTracker.Result.None -> Unit
        }
    }

    private companion object {
        const val DEFAULT_SWIPE_DISTANCE = 72f
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

    fun onDown(x: Float, y: Float, eventTime: Long) {
        downX = x
        downY = y
        downTime = eventTime
        hasDown = true
        swipeDispatched = false
    }

    fun onMove(x: Float, y: Float, eventTime: Long): Result {
        if (!hasDown || swipeDispatched || minDistance <= 0f) return Result.None
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

    fun onDown(x: Float, y: Float, viewWidthPx: Int, viewHeightPx: Int, density: Float) {
        reset()
        if (viewWidthPx <= 0 || viewHeightPx <= 0) return
        val zoneWidth = (viewWidthPx * EDGE_ZONE_FRACTION)
            .coerceAtMost(MAX_EDGE_ZONE_DP * density)
            .coerceAtLeast(MIN_EDGE_ZONE_DP * density)
        edge = when {
            x <= zoneWidth -> Edge.Left
            x >= viewWidthPx - zoneWidth -> Edge.Right
            else -> Edge.None
        }
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
        const val EDGE_ZONE_FRACTION = 0.08f
        const val MIN_EDGE_ZONE_DP = 24f
        const val MAX_EDGE_ZONE_DP = 64f
        const val ACTIVATION_DP = 16f
    }
}
