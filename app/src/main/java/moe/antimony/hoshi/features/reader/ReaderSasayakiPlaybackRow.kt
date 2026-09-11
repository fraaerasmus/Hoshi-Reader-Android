package moe.antimony.hoshi.features.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.antimony.hoshi.R

private const val SASAYAKI_SKIP_HOLD_INITIAL_DELAY_MS = 350L
private const val SASAYAKI_SKIP_HOLD_REPEAT_INTERVAL_MS = 150L
private const val SASAYAKI_SCRUB_STEP_DP = 40

/**
 * Fork feature: the reader's bottom Sasayaki playback row. Kept in its own file so an upstream
 * rewrite of [ReaderBottomSafeProgress] conflicts on one call site, not this block. Emits nothing
 * when [controls] are hidden.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderSasayakiPlaybackRow(
    controls: ReaderSasayakiBottomPlaybackControls,
    colors: ReaderChromeColors,
    sasayakiPlaying: Boolean,
    onTapSafeArea: () -> Unit,
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
    doubleTapEnabled: Boolean,
) {
    if (!controls.visible) return
    val onLongClick = onHoldStart.takeIf { holdEnabled }
    // Only the empty area: the play button already toggles on a single tap.
    val onDoubleClick = onTogglePlayback.takeIf { doubleTapEnabled }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(controls.rowHeightDp.dp)
            .then(
                if (scrubEnabled) {
                    Modifier.sasayakiScrub(
                        onSteps = onScrubSteps,
                        onEnd = onScrubEnd,
                        onCancel = onScrubCancel,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .sasayakiHoldRelease(onHoldEnd)
                .combinedClickable(onClick = onTapSafeArea, onLongClick = onLongClick, onDoubleClick = onDoubleClick),
        )
        Row(
            modifier = Modifier
                .align(if (controls.centered) Alignment.Center else Alignment.CenterStart)
                .padding(
                    start = if (controls.centered) {
                        0.dp
                    } else {
                        controls.horizontalPaddingDp.dp
                    },
                )
                .height(controls.rowHeightDp.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderSasayakiPlaybackButton(
                controls = controls,
                colors = colors,
                icon = Icons.Rounded.FastRewind,
                contentDescription = stringResource(R.string.sasayaki_rewind),
                onClick = onSkipBackward,
                holdRepeat = true,
            )
            ReaderSasayakiPlaybackButton(
                controls = controls,
                colors = colors,
                icon = if (sasayakiPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (sasayakiPlaying) {
                    stringResource(R.string.sasayaki_pause)
                } else {
                    stringResource(R.string.sasayaki_play)
                },
                onClick = onTogglePlayback,
                onLongClick = onLongClick,
                onRelease = onHoldEnd,
            )
            ReaderSasayakiPlaybackButton(
                controls = controls,
                colors = colors,
                icon = Icons.Rounded.FastForward,
                contentDescription = stringResource(R.string.sasayaki_fast_forward),
                onClick = onSkipForward,
                holdRepeat = true,
            )
        }
    }
}

/**
 * Horizontal drag anywhere on the row (buttons included) scrubs by steps. Consuming the drag
 * cancels the child button press/hold-repeat and the safe-area tap, so a plain tap is unchanged.
 * [onSteps] gets the drag-direction step count (+ rightward); [onEnd] fires once on release.
 */
@Composable
internal fun Modifier.sasayakiScrub(
    onSteps: (Int) -> Unit,
    onEnd: (Int) -> Unit,
    onCancel: () -> Unit,
    vertical: Boolean = false,
): Modifier {
    val stepPx = with(LocalDensity.current) { SASAYAKI_SCRUB_STEP_DP.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val currentOnSteps = rememberUpdatedState(onSteps)
    val currentOnEnd = rememberUpdatedState(onEnd)
    val currentOnCancel = rememberUpdatedState(onCancel)
    return pointerInput(stepPx, vertical) {
        val tracker = ReaderSasayakiScrubGestureTracker(stepPx)
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            tracker.reset()
            // Vertical drags scrub upward = forward, so the sign flips.
            val slop = if (vertical) {
                awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                    change.consume()
                    tracker.onDrag(-overSlop)
                }
            } else {
                awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                    change.consume()
                    tracker.onDrag(overSlop)
                }
            }
            val start = slop ?: return@awaitEachGesture
            currentOnSteps.value(tracker.steps)
            val onChange: (PointerInputChange) -> Unit = { change ->
                // Read the delta before consuming: a consumed change reports zero movement.
                val delta = if (vertical) -change.positionChange().y else change.positionChange().x
                change.consume()
                if (tracker.onDrag(delta)) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    currentOnSteps.value(tracker.steps)
                }
            }
            val completed = if (vertical) verticalDrag(start.id, onChange) else horizontalDrag(start.id, onChange)
            if (completed) currentOnEnd.value(tracker.steps) else currentOnCancel.value()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderSasayakiPlaybackButton(
    controls: ReaderSasayakiBottomPlaybackControls,
    colors: ReaderChromeColors,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    holdRepeat: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .width(controls.buttonWidthDp.dp)
            .height(controls.rowHeightDp.dp)
            .then(
                if (holdRepeat) {
                    Modifier.pointerInput(onClick) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val repeat = scope.launch {
                                delay(SASAYAKI_SKIP_HOLD_INITIAL_DELAY_MS)
                                while (true) {
                                    onClick()
                                    delay(SASAYAKI_SKIP_HOLD_REPEAT_INTERVAL_MS)
                                }
                            }
                            try {
                                waitForUpOrCancellation()
                            } finally {
                                repeat.cancel()
                            }
                        }
                    }
                } else {
                    Modifier
                },
            )
            .then(if (onRelease != null) Modifier.sasayakiHoldRelease(onRelease) else Modifier)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color(colors.infoText),
            modifier = Modifier.size(controls.iconSizeDp.dp),
        )
    }
}
