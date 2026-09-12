package moe.antimony.hoshi.features.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.reader.input.SasayakiControlsPlacement

private const val DOCK_TAB_THICKNESS_DP = 18
private const val DOCK_TAB_LENGTH_DP = 56
private const val DOCK_SLIDE_MS = 200
private const val DOCK_SNAP_BAND_DP = 72

/** The edge a dock placement sits on; the classic bottom row is not a dock. */
private fun SasayakiControlsPlacement.dockEdge(): DockEdge? = when (this) {
    SasayakiControlsPlacement.Left -> DockEdge.Left
    SasayakiControlsPlacement.Right -> DockEdge.Right
    SasayakiControlsPlacement.BottomDock -> DockEdge.Bottom
    SasayakiControlsPlacement.Bottom -> null
}

private enum class DockEdge(val horizontal: Boolean) {
    Left(horizontal = false),
    Right(horizontal = false),
    Bottom(horizontal = true),
}

/** Position along an edge of an item of [itemLengthDp] whose resting point is [fraction] of an [edgeLengthDp] edge. */
internal fun readerDockTopDp(fraction: Float, edgeLengthDp: Int, itemLengthDp: Int): Int {
    val travel = (edgeLengthDp - itemLengthDp).coerceAtLeast(0)
    return (travel * fraction.coerceIn(0f, 1f)).toInt()
}

/** The inverse of [readerDockTopDp]: where a tab dragged to [topDp] should rest. */
internal fun readerDockFraction(topDp: Float, edgeLengthDp: Int, itemLengthDp: Int): Float {
    val travel = (edgeLengthDp - itemLengthDp).coerceAtLeast(1)
    return (topDp / travel).coerceIn(0f, 1f)
}

/** Where the cluster sits along the edge so that it is centred on the tab yet stays on screen. */
internal fun readerDockClusterTopDp(tabTopDp: Int, edgeLengthDp: Int, clusterLengthDp: Int): Int {
    val centred = tabTopDp + DOCK_TAB_LENGTH_DP / 2 - clusterLengthDp / 2
    return centred.coerceIn(0, (edgeLengthDp - clusterLengthDp).coerceAtLeast(0))
}

internal data class ReaderDockDrop(val placement: SasayakiControlsPlacement, val fraction: Float)

/**
 * Which edge a tab dragged to ([x], [y]) would dock on, and where along it: the nearest of left, right
 * and bottom when within [snapBandPx] of it, else null (the tab springs back). [tabLengthPx] turns the
 * finger position into the tab's resting fraction.
 */
internal fun readerDockDropTarget(
    x: Float,
    y: Float,
    widthPx: Float,
    heightPx: Float,
    tabLengthPx: Float,
    snapBandPx: Float,
): ReaderDockDrop? {
    val toLeft = x
    val toRight = widthPx - x
    val toBottom = heightPx - y
    val nearest = minOf(toLeft, toRight, toBottom)
    if (nearest > snapBandPx) return null
    fun along(position: Float, edgeLength: Float) =
        ((position - tabLengthPx / 2f) / (edgeLength - tabLengthPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
    return when (nearest) {
        toBottom -> ReaderDockDrop(SasayakiControlsPlacement.BottomDock, along(x, widthPx))
        toLeft -> ReaderDockDrop(SasayakiControlsPlacement.Left, along(y, heightPx))
        else -> ReaderDockDrop(SasayakiControlsPlacement.Right, along(y, heightPx))
    }
}

/**
 * Fork feature: the playback controls docked on a screen edge (left, right or bottom) for one-handed use,
 * as a drawer with no timers. A translucent tab rests where the user last put it; tapping it slides the
 * rewind / play-pause / forward cluster out from behind it, and the drawer closes on the tab, on any touch
 * on the page (reported through [closeRequests]), or on a push back toward the edge. Holding any button
 * of the open drawer speeds playback up. Dragging the tab carries it anywhere: the edge it would land on
 * lights up, and letting go elsewhere springs it back.
 *
 * [compact] drops the drawer: the tab is the control — tap play/pause, slide along the edge to scrub,
 * hold (with a haptic) and then drag to carry it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderSasayakiSideDock(
    placement: SasayakiControlsPlacement,
    offsetFraction: Float,
    onDockChange: (SasayakiControlsPlacement, Float) -> Unit,
    controls: ReaderSasayakiBottomPlaybackControls,
    colors: ReaderChromeColors,
    sasayakiPlaying: Boolean,
    onSkipBackward: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipForward: () -> Unit,
    scrubEnabled: Boolean,
    onScrubSteps: (Int) -> Unit,
    onScrubEnd: (Int) -> Unit,
    onScrubCancel: () -> Unit,
    holdEnabled: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    compact: Boolean = false,
    /** Bumped by the reader on every page touch: the drawer closes, the page still gets the touch. */
    closeRequests: Int = 0,
    modifier: Modifier = Modifier,
) {
    val edge = placement.dockEdge()
    if (!controls.visible || edge == null) return
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val currentOnDockChange = rememberUpdatedState(onDockChange)
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(closeRequests) {
        if (closeRequests > 0) expanded = false
    }
    // While the tab is being carried: its offset from the resting spot and the edge it would land on.
    var dragOffset by remember { mutableStateOf<Offset?>(null) }
    var dropTarget by remember { mutableStateOf<ReaderDockDrop?>(null) }
    LaunchedEffect(dropTarget?.placement) {
        if (dropTarget != null) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
    val clusterThicknessDp = if (edge.horizontal) controls.rowHeightDp else controls.buttonWidthDp
    val clusterLengthDp = if (compact) DOCK_TAB_LENGTH_DP else (if (edge.horizontal) controls.buttonWidthDp else controls.rowHeightDp) * 3
    // The tab slides out with the cluster so the two read as one drawer.
    val tabShiftDp by animateDpAsState(
        targetValue = if (expanded && !compact) clusterThicknessDp.dp else 0.dp,
        animationSpec = tween(DOCK_SLIDE_MS),
        label = "sasayakiDockTabShift",
    )
    // No pointer input on this full-size box: a Compose node that listens would sit above the WebView and steal its touches.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val edgeLengthDp = with(density) { (if (edge.horizontal) constraints.maxWidth else constraints.maxHeight).toDp() }.value.toInt()
        val tabAlongDp = readerDockTopDp(offsetFraction, edgeLengthDp, DOCK_TAB_LENGTH_DP)
        val clusterAlongDp = readerDockClusterTopDp(tabAlongDp, edgeLengthDp, clusterLengthDp)
        val tabThicknessPx = with(density) { DOCK_TAB_THICKNESS_DP.dp.toPx() }
        val tabLengthPx = with(density) { DOCK_TAB_LENGTH_DP.dp.toPx() }
        val tabAlongPx = with(density) { tabAlongDp.dp.toPx() }
        // The tab's resting centre in root pixels, so a drag offset maps to a drop position.
        val tabCentre = when (edge) {
            DockEdge.Left -> Offset(tabThicknessPx / 2f, tabAlongPx + tabLengthPx / 2f)
            DockEdge.Right -> Offset(widthPx - tabThicknessPx / 2f, tabAlongPx + tabLengthPx / 2f)
            DockEdge.Bottom -> Offset(tabAlongPx + tabLengthPx / 2f, heightPx - tabThicknessPx / 2f)
        }
        fun carry(delta: Offset) {
            expanded = false
            val next = (dragOffset ?: Offset.Zero) + delta
            dragOffset = next
            dropTarget = readerDockDropTarget(
                x = tabCentre.x + next.x,
                y = tabCentre.y + next.y,
                widthPx = widthPx,
                heightPx = heightPx,
                tabLengthPx = tabLengthPx,
                snapBandPx = with(density) { DOCK_SNAP_BAND_DP.dp.toPx() },
            )
        }
        fun drop() {
            dropTarget?.let { currentOnDockChange.value(it.placement, it.fraction) }
            dragOffset = null
            dropTarget = null
        }

        dropTarget?.let { target -> DockEdgeHighlight(target.placement.dockEdge() ?: edge) }

        Box(
            modifier = Modifier
                .align(
                    when (edge) {
                        DockEdge.Left -> Alignment.TopStart
                        DockEdge.Right -> Alignment.TopEnd
                        DockEdge.Bottom -> Alignment.BottomStart
                    },
                )
                .offset(x = if (edge.horizontal) clusterAlongDp.dp else 0.dp, y = if (edge.horizontal) 0.dp else clusterAlongDp.dp)
                .width(if (edge.horizontal) clusterLengthDp.dp else (clusterThicknessDp + DOCK_TAB_THICKNESS_DP).dp)
                .height(if (edge.horizontal) (clusterThicknessDp + DOCK_TAB_THICKNESS_DP).dp else clusterLengthDp.dp),
        ) {
            if (!compact) AnimatedVisibility(
                visible = expanded,
                enter = when (edge) {
                    DockEdge.Left -> slideInHorizontally(tween(DOCK_SLIDE_MS)) { -it }
                    DockEdge.Right -> slideInHorizontally(tween(DOCK_SLIDE_MS)) { it }
                    DockEdge.Bottom -> slideInVertically(tween(DOCK_SLIDE_MS)) { it }
                } + fadeIn(tween(DOCK_SLIDE_MS)),
                exit = when (edge) {
                    DockEdge.Left -> slideOutHorizontally(tween(DOCK_SLIDE_MS)) { -it }
                    DockEdge.Right -> slideOutHorizontally(tween(DOCK_SLIDE_MS)) { it }
                    DockEdge.Bottom -> slideOutVertically(tween(DOCK_SLIDE_MS)) { it }
                } + fadeOut(tween(DOCK_SLIDE_MS)),
                modifier = Modifier.align(
                    when (edge) {
                        DockEdge.Left -> Alignment.CenterStart
                        DockEdge.Right -> Alignment.CenterEnd
                        DockEdge.Bottom -> Alignment.BottomCenter
                    },
                ),
            ) {
                val clusterModifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(colors.buttonContainer).copy(alpha = 0.85f))
                    // A push back toward the edge closes the drawer; drags along the edge still scrub below.
                    .pointerInput(edge) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var travel = Offset.Zero
                            val drag = awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
                                travel = overSlop
                                change.consume()
                            }
                            val towardEdge = when (edge) {
                                DockEdge.Left -> travel.x < 0f && -travel.x > abs(travel.y)
                                DockEdge.Right -> travel.x > 0f && travel.x > abs(travel.y)
                                DockEdge.Bottom -> travel.y > 0f && travel.y > abs(travel.x)
                            }
                            if (drag != null && towardEdge) expanded = false
                        }
                    }
                    .then(
                        if (scrubEnabled) {
                            Modifier.sasayakiScrub(onSteps = onScrubSteps, onEnd = onScrubEnd, onCancel = onScrubCancel, vertical = !edge.horizontal)
                        } else {
                            Modifier
                        },
                    )
                    .sasayakiHoldRelease(onHoldEnd)
                val buttons: @Composable () -> Unit = {
                    ReaderSasayakiPlaybackButton(
                        controls = controls,
                        colors = colors,
                        icon = Icons.Rounded.FastRewind,
                        contentDescription = stringResource(R.string.sasayaki_rewind),
                        onClick = onSkipBackward,
                        onLongClick = onHoldStart.takeIf { holdEnabled },
                        onRelease = onHoldEnd,
                    )
                    ReaderSasayakiPlaybackButton(
                        controls = controls,
                        colors = colors,
                        icon = if (sasayakiPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (sasayakiPlaying) stringResource(R.string.sasayaki_pause) else stringResource(R.string.sasayaki_play),
                        onClick = onTogglePlayback,
                        onLongClick = onHoldStart.takeIf { holdEnabled },
                        onRelease = onHoldEnd,
                    )
                    ReaderSasayakiPlaybackButton(
                        controls = controls,
                        colors = colors,
                        icon = Icons.Rounded.FastForward,
                        contentDescription = stringResource(R.string.sasayaki_fast_forward),
                        onClick = onSkipForward,
                        onLongClick = onHoldStart.takeIf { holdEnabled },
                        onRelease = onHoldEnd,
                    )
                }
                if (edge.horizontal) Row(modifier = clusterModifier) { buttons() } else Column(modifier = clusterModifier) { buttons() }
            }
            // The tab: anchored to its own persisted spot, shifted only while the drawer is open, and
            // carried freely by the finger while being moved.
            val carried = dragOffset
            Box(
                modifier = Modifier
                    .align(
                        when (edge) {
                            DockEdge.Left -> Alignment.TopStart
                            DockEdge.Right -> Alignment.TopEnd
                            DockEdge.Bottom -> Alignment.BottomStart
                        },
                    )
                    .offset(
                        x = when (edge) {
                            DockEdge.Left -> tabShiftDp
                            DockEdge.Right -> -tabShiftDp
                            DockEdge.Bottom -> (tabAlongDp - clusterAlongDp).dp
                        },
                        y = if (edge.horizontal) -tabShiftDp else (tabAlongDp - clusterAlongDp).dp,
                    )
                    .then(if (carried != null) Modifier.offset { IntOffset(carried.x.roundToInt(), carried.y.roundToInt()) } else Modifier)
                    .width((if (edge.horizontal) DOCK_TAB_LENGTH_DP else DOCK_TAB_THICKNESS_DP).dp)
                    .height((if (edge.horizontal) DOCK_TAB_THICKNESS_DP else DOCK_TAB_LENGTH_DP).dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(colors.infoText).copy(alpha = if (carried != null) 0.6f else 0.35f))
                    .then(
                        when {
                            compact -> Modifier.compactDockTabGestures(
                                horizontal = edge.horizontal,
                                scrubEnabled = scrubEnabled,
                                onTap = onTogglePlayback,
                                onScrubSteps = onScrubSteps,
                                onScrubEnd = onScrubEnd,
                                onScrubCancel = onScrubCancel,
                                onMove = ::carry,
                                onMoveEnd = ::drop,
                            )
                            expanded -> Modifier.pointerInput(Unit) { detectTapGestures { expanded = false } }
                            else -> Modifier
                                .pointerInput(edge) {
                                    detectDragGestures(onDragEnd = ::drop, onDragCancel = ::drop) { change, dragAmount ->
                                        change.consume()
                                        carry(dragAmount)
                                    }
                                }
                                .pointerInput(Unit) { detectTapGestures { expanded = true } }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (compact) {
                    Icon(
                        imageVector = if (sasayakiPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (sasayakiPlaying) stringResource(R.string.sasayaki_pause) else stringResource(R.string.sasayaki_play),
                        tint = Color(colors.infoText).copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

/** A tinted band on the edge the carried tab would dock on. */
@Composable
private fun BoxWithConstraintsScope.DockEdgeHighlight(edge: DockEdge) {
    val tint = Color(0xFF888888).copy(alpha = 0.18f)
    val band = DOCK_SNAP_BAND_DP.dp
    Box(
        modifier = when (edge) {
            DockEdge.Left -> Modifier.align(Alignment.CenterStart).fillMaxHeight().width(band)
            DockEdge.Right -> Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(band)
            DockEdge.Bottom -> Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(band)
        }.background(tint),
    )
}

/**
 * The compact tab: tap toggles playback, a drag along the edge scrubs (up / right = forward), and a hold —
 * answered with a haptic — turns the rest of the gesture into carrying the tab anywhere. One handler, so
 * the three never race each other.
 */
@Composable
private fun Modifier.compactDockTabGestures(
    horizontal: Boolean,
    scrubEnabled: Boolean,
    onTap: () -> Unit,
    onScrubSteps: (Int) -> Unit,
    onScrubEnd: (Int) -> Unit,
    onScrubCancel: () -> Unit,
    onMove: (Offset) -> Unit,
    onMoveEnd: () -> Unit,
): Modifier {
    val stepPx = with(LocalDensity.current) { SASAYAKI_SCRUB_STEP_DP.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val currentOnTap = rememberUpdatedState(onTap)
    val currentOnScrubSteps = rememberUpdatedState(onScrubSteps)
    val currentOnScrubEnd = rememberUpdatedState(onScrubEnd)
    val currentOnScrubCancel = rememberUpdatedState(onScrubCancel)
    val currentOnMove = rememberUpdatedState(onMove)
    val currentOnMoveEnd = rememberUpdatedState(onMoveEnd)
    return pointerInput(horizontal, scrubEnabled, stepPx) {
        val tracker = ReaderSasayakiScrubGestureTracker(stepPx)
        fun alongEdge(delta: Offset): Float = if (horizontal) delta.x else -delta.y
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var overSlop = Offset.Zero
            var released = false
            // null = the hold timed out before the finger moved; Unit = it lifted (a tap) or was taken over.
            val slop = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                awaitTouchSlopOrCancellation(down.id) { change, over ->
                    overSlop = over
                    change.consume()
                } ?: run { released = true }
            }
            when {
                slop == null -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    drag(down.id) { change ->
                        val delta = change.positionChange()
                        change.consume()
                        currentOnMove.value(delta)
                    }
                    currentOnMoveEnd.value()
                }
                released -> if (currentEvent.changes.none { it.pressed }) currentOnTap.value()
                scrubEnabled -> {
                    tracker.reset()
                    tracker.onDrag(alongEdge(overSlop))
                    currentOnScrubSteps.value(tracker.steps)
                    val completed = drag(down.id) { change ->
                        val delta = change.positionChange()
                        change.consume()
                        if (tracker.onDrag(alongEdge(delta))) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            currentOnScrubSteps.value(tracker.steps)
                        }
                    }
                    if (completed) currentOnScrubEnd.value(tracker.steps) else currentOnScrubCancel.value()
                }
            }
        }
    }
}
