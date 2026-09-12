package moe.antimony.hoshi.features.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.reader.input.SasayakiControlsPlacement

private const val DOCK_TAB_WIDTH_DP = 18
private const val DOCK_TAB_HEIGHT_DP = 56
private const val DOCK_SLIDE_MS = 200

/** Top offset of a dock item of [itemHeightDp] whose resting point is [fraction] along a [containerHeightDp] edge. */
internal fun readerDockTopDp(fraction: Float, containerHeightDp: Int, itemHeightDp: Int): Int {
    val travel = (containerHeightDp - itemHeightDp).coerceAtLeast(0)
    return (travel * fraction.coerceIn(0f, 1f)).toInt()
}

/** The inverse of [readerDockTopDp]: where a tab dragged to [topDp] should rest. */
internal fun readerDockFraction(topDp: Float, containerHeightDp: Int, itemHeightDp: Int): Float {
    val travel = (containerHeightDp - itemHeightDp).coerceAtLeast(1)
    return (topDp / travel).coerceIn(0f, 1f)
}

/** Where the cluster sits so that it is centred on the tab yet stays on screen. */
internal fun readerDockClusterTopDp(tabTopDp: Int, containerHeightDp: Int, clusterHeightDp: Int): Int {
    val centred = tabTopDp + DOCK_TAB_HEIGHT_DP / 2 - clusterHeightDp / 2
    return centred.coerceIn(0, (containerHeightDp - clusterHeightDp).coerceAtLeast(0))
}

/**
 * Fork feature: the playback row docked on a screen side for one-handed use, as a drawer with no timers.
 * A translucent tab rests where the user last dragged it and never moves on its own; tapping it slides the
 * rewind / play-pause / forward cluster out from behind it, and the drawer closes on the tab, on any touch
 * on the page (reported by the reader through [closeRequests]), or on a push back toward the edge. The cluster carries the
 * bottom row's hold and drag-to-scrub gestures (drag is vertical here).
 *
 * [compact] drops the drawer: the tab is the control — tap play/pause, slide up or down to scrub, hold
 * (with a haptic) and then drag to move it. Holding any button of the open drawer speeds playback up.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderSasayakiSideDock(
    placement: SasayakiControlsPlacement,
    offsetFraction: Float,
    onOffsetFractionChange: (Float) -> Unit,
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
    if (!controls.visible || placement == SasayakiControlsPlacement.Bottom) return
    val density = LocalDensity.current
    val isLeft = placement == SasayakiControlsPlacement.Left
    val currentOffsetChange = rememberUpdatedState(onOffsetFractionChange)
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(closeRequests) {
        if (closeRequests > 0) expanded = false
    }
    val clusterWidthDp = if (compact) 0 else controls.buttonWidthDp
    val clusterHeightDp = if (compact) DOCK_TAB_HEIGHT_DP else controls.rowHeightDp * 3
    // The tab slides out with the cluster so the two read as one drawer.
    val tabShiftDp by animateDpAsState(
        targetValue = if (expanded) clusterWidthDp.dp else 0.dp,
        animationSpec = tween(DOCK_SLIDE_MS),
        label = "sasayakiDockTabShift",
    )
    // No pointer input on this full-size box: a Compose node that listens would sit above the WebView and steal its touches.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerHeightDp = with(density) { constraints.maxHeight.toDp() }.value.toInt()
        val tabTopDp = readerDockTopDp(offsetFraction, containerHeightDp, DOCK_TAB_HEIGHT_DP)
        val clusterTopDp = readerDockClusterTopDp(tabTopDp, containerHeightDp, clusterHeightDp)
        var dragTopDp by remember(tabTopDp) { mutableStateOf(tabTopDp.toFloat()) }
        fun moveTab(dragAmountPx: Float) {
            dragTopDp = (dragTopDp + dragAmountPx / density.density)
                .coerceIn(0f, (containerHeightDp - DOCK_TAB_HEIGHT_DP).coerceAtLeast(0).toFloat())
            currentOffsetChange.value(readerDockFraction(dragTopDp, containerHeightDp, DOCK_TAB_HEIGHT_DP))
        }
        fun commitTabPosition() = currentOffsetChange.value(readerDockFraction(dragTopDp, containerHeightDp, DOCK_TAB_HEIGHT_DP))
        Box(
            modifier = Modifier
                .align(if (isLeft) Alignment.TopStart else Alignment.TopEnd)
                .offset(y = clusterTopDp.dp)
                .width((clusterWidthDp + DOCK_TAB_WIDTH_DP).dp)
                .height(clusterHeightDp.dp),
        ) {
            if (!compact) AnimatedVisibility(
                visible = expanded,
                enter = slideInHorizontally(tween(DOCK_SLIDE_MS)) { if (isLeft) -it else it } + fadeIn(tween(DOCK_SLIDE_MS)),
                exit = slideOutHorizontally(tween(DOCK_SLIDE_MS)) { if (isLeft) -it else it } + fadeOut(tween(DOCK_SLIDE_MS)),
                modifier = Modifier.align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd),
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(colors.buttonContainer).copy(alpha = 0.85f))
                        // A push back toward the edge closes the drawer; vertical drags still scrub below.
                        .pointerInput(isLeft) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var travel = 0f
                                val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                                    travel = overSlop
                                    change.consume()
                                }
                                if (drag != null && (if (isLeft) travel < 0f else travel > 0f)) expanded = false
                            }
                        }
                        .then(
                            if (scrubEnabled) {
                                Modifier.sasayakiScrub(onSteps = onScrubSteps, onEnd = onScrubEnd, onCancel = onScrubCancel, vertical = true)
                            } else {
                                Modifier
                            },
                        )
                        .sasayakiHoldRelease(onHoldEnd),
                ) {
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
            }
            // The tab: anchored to its own persisted spot, shifted sideways only while the drawer is open.
            Box(
                modifier = Modifier
                    .align(if (isLeft) Alignment.TopStart else Alignment.TopEnd)
                    .offset(x = if (isLeft) tabShiftDp else -tabShiftDp, y = (tabTopDp - clusterTopDp).dp)
                    .width(DOCK_TAB_WIDTH_DP.dp)
                    .height(DOCK_TAB_HEIGHT_DP.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(colors.infoText).copy(alpha = 0.35f))
                    .then(
                        when {
                            compact -> Modifier.compactDockTabGestures(
                                scrubEnabled = scrubEnabled,
                                onTap = onTogglePlayback,
                                onScrubSteps = onScrubSteps,
                                onScrubEnd = onScrubEnd,
                                onScrubCancel = onScrubCancel,
                                onMove = ::moveTab,
                                onMoveEnd = ::commitTabPosition,
                            )
                            expanded -> Modifier.pointerInput(Unit) { detectTapGestures { expanded = false } }
                            else -> Modifier
                                .pointerInput(containerHeightDp) {
                                    detectVerticalDragGestures(onDragEnd = ::commitTabPosition) { change, dragAmount ->
                                        change.consume()
                                        moveTab(dragAmount)
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

/**
 * The compact tab: tap toggles playback, a vertical drag scrubs (up = forward), and a hold — answered with a
 * haptic — turns the rest of the gesture into moving the tab along the edge. One handler, so the three
 * never race each other.
 */
@Composable
private fun Modifier.compactDockTabGestures(
    scrubEnabled: Boolean,
    onTap: () -> Unit,
    onScrubSteps: (Int) -> Unit,
    onScrubEnd: (Int) -> Unit,
    onScrubCancel: () -> Unit,
    onMove: (Float) -> Unit,
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
    return pointerInput(scrubEnabled, stepPx) {
        val tracker = ReaderSasayakiScrubGestureTracker(stepPx)
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var overSlop = Offset.Zero
            var released = false
            // null = the hold timed out before the finger moved; Unit = it lifted (a tap) or was taken over.
            val slop = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                    overSlop = Offset(0f, over)
                    change.consume()
                } ?: run { released = true }
            }
            when {
                slop == null -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    drag(down.id) { change ->
                        val dy = change.positionChange().y
                        change.consume()
                        currentOnMove.value(dy)
                    }
                    currentOnMoveEnd.value()
                }
                released -> if (currentEvent.changes.none { it.pressed }) currentOnTap.value()
                scrubEnabled -> {
                    tracker.reset()
                    tracker.onDrag(-overSlop.y)
                    currentOnScrubSteps.value(tracker.steps)
                    val completed = drag(down.id) { change ->
                        val dy = change.positionChange().y
                        change.consume()
                        if (tracker.onDrag(-dy)) {
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
