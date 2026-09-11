package moe.antimony.hoshi.features.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.reader.input.SasayakiControlsPlacement

private const val DOCK_HANDLE_WIDTH_DP = 18
private const val DOCK_HANDLE_HEIGHT_DP = 56
private const val DOCK_IDLE_COLLAPSE_MS = 2_000L
/** Long enough to see the play icon flip before the drawer goes. */
private const val DOCK_AFTER_TOGGLE_COLLAPSE_MS = 600L

/** Top offset of a dock item of [itemHeightDp] whose resting point is [fraction] along a [containerHeightDp] edge. */
internal fun readerDockTopDp(fraction: Float, containerHeightDp: Int, itemHeightDp: Int): Int {
    val travel = (containerHeightDp - itemHeightDp).coerceAtLeast(0)
    return (travel * fraction.coerceIn(0f, 1f)).toInt()
}

/** The inverse of [readerDockTopDp]: where a handle dragged to [topDp] should rest. */
internal fun readerDockFraction(topDp: Float, containerHeightDp: Int, itemHeightDp: Int): Float {
    val travel = (containerHeightDp - itemHeightDp).coerceAtLeast(1)
    return (topDp / travel).coerceIn(0f, 1f)
}

/**
 * Fork feature: the playback row docked on a screen side for one-handed use, as a drawer. A translucent
 * tab rests where the user last dragged it; a tap slides the rewind / play-pause / forward cluster out
 * with the tab still attached. It closes on the tab, on a tap anywhere else (which the page still
 * receives), on a push back toward the edge, shortly after play/pause, or after a couple of idle seconds.
 * The cluster carries the bottom row's hold and drag-to-scrub gestures (drag is vertical here).
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
    modifier: Modifier = Modifier,
) {
    if (!controls.visible || placement == SasayakiControlsPlacement.Bottom) return
    val density = LocalDensity.current
    val isLeft = placement == SasayakiControlsPlacement.Left
    val currentOffsetChange = rememberUpdatedState(onOffsetFractionChange)
    var expanded by remember { mutableStateOf(false) }
    // Every interaction restarts the collapse timer; the delay is whatever the last interaction asked for.
    var interaction by remember { mutableIntStateOf(0) }
    var collapseDelayMs by remember { mutableLongStateOf(DOCK_IDLE_COLLAPSE_MS) }
    var drawerBounds by remember { mutableStateOf(Rect.Zero) }
    fun touched(delayMs: Long = DOCK_IDLE_COLLAPSE_MS) {
        collapseDelayMs = delayMs
        interaction++
    }
    LaunchedEffect(expanded, interaction) {
        if (expanded) {
            delay(collapseDelayMs)
            expanded = false
        }
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // Observe only: a tap anywhere outside the drawer closes it and still reaches the page underneath.
            .pointerInput(expanded) {
                if (!expanded) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (!drawerBounds.contains(down.position)) expanded = false
                }
            },
    ) {
        val containerHeightDp = with(density) { constraints.maxHeight.toDp() }.value.toInt()
        val clusterHeightDp = controls.rowHeightDp * 3
        val itemHeightDp = if (expanded) clusterHeightDp else DOCK_HANDLE_HEIGHT_DP
        val topDp = readerDockTopDp(offsetFraction, containerHeightDp, itemHeightDp)
        var dragTopDp by remember(topDp, expanded) { mutableStateOf(topDp.toFloat()) }
        val tab: @Composable () -> Unit = {
            DockTab(
                colors = colors,
                modifier = if (expanded) {
                    Modifier.pointerInput(Unit) { detectTapGestures { expanded = false } }
                } else {
                    Modifier
                        .pointerInput(containerHeightDp) {
                            detectVerticalDragGestures(
                                onDragEnd = { currentOffsetChange.value(readerDockFraction(dragTopDp, containerHeightDp, DOCK_HANDLE_HEIGHT_DP)) },
                            ) { change, dragAmount ->
                                change.consume()
                                dragTopDp = (dragTopDp + dragAmount / density.density)
                                    .coerceIn(0f, (containerHeightDp - DOCK_HANDLE_HEIGHT_DP).coerceAtLeast(0).toFloat())
                                currentOffsetChange.value(readerDockFraction(dragTopDp, containerHeightDp, DOCK_HANDLE_HEIGHT_DP))
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures { touched(); expanded = true } }
                },
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(if (isLeft) Alignment.TopStart else Alignment.TopEnd)
                .offset(y = topDp.dp)
                .onGloballyPositioned { drawerBounds = it.boundsInParent() },
        ) {
            if (!isLeft) tab()
            AnimatedVisibility(
                visible = expanded,
                enter = slideInHorizontally { if (isLeft) -it else it } + fadeIn(),
                exit = slideOutHorizontally { if (isLeft) -it else it } + fadeOut(),
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
                                Modifier.sasayakiScrub(
                                    vertical = true,
                                    onSteps = { touched(); onScrubSteps(it) },
                                    onEnd = { touched(); onScrubEnd(it) },
                                    onCancel = onScrubCancel,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .sasayakiHoldRelease {
                            touched()
                            onHoldEnd()
                        },
                ) {
                    ReaderSasayakiPlaybackButton(
                        controls = controls,
                        colors = colors,
                        icon = Icons.Rounded.FastRewind,
                        contentDescription = stringResource(R.string.sasayaki_rewind),
                        onClick = { touched(); onSkipBackward() },
                        holdRepeat = true,
                    )
                    ReaderSasayakiPlaybackButton(
                        controls = controls,
                        colors = colors,
                        icon = if (sasayakiPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (sasayakiPlaying) stringResource(R.string.sasayaki_pause) else stringResource(R.string.sasayaki_play),
                        onClick = { touched(DOCK_AFTER_TOGGLE_COLLAPSE_MS); onTogglePlayback() },
                        onLongClick = if (holdEnabled) ({ touched(); onHoldStart() }) else null,
                    )
                    ReaderSasayakiPlaybackButton(
                        controls = controls,
                        colors = colors,
                        icon = Icons.Rounded.FastForward,
                        contentDescription = stringResource(R.string.sasayaki_fast_forward),
                        onClick = { touched(); onSkipForward() },
                        holdRepeat = true,
                    )
                }
            }
            if (isLeft) tab()
        }
    }
}

@Composable
private fun DockTab(colors: ReaderChromeColors, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(vertical = 2.dp)
            .width(DOCK_HANDLE_WIDTH_DP.dp)
            .height(DOCK_HANDLE_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(colors.infoText).copy(alpha = 0.35f)),
    )
}
