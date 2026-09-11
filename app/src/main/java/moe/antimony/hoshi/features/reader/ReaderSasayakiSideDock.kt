package moe.antimony.hoshi.features.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.reader.input.SasayakiControlsPlacement

private const val DOCK_HANDLE_WIDTH_DP = 18
private const val DOCK_HANDLE_HEIGHT_DP = 56
private const val DOCK_AUTO_COLLAPSE_MS = 4_000L

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
 * Fork feature: the playback row docked on a screen side for one-handed use. A translucent handle
 * rests where the user last dragged it; a tap expands the vertical rewind / play-pause / forward cluster,
 * which collapses again after a few idle seconds. The cluster carries the same hold and drag gestures
 * as the bottom row (drag is vertical here).
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
    val currentOffsetChange = rememberUpdatedState(onOffsetFractionChange)
    var expanded by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    LaunchedEffect(expanded, interaction) {
        if (expanded) {
            delay(DOCK_AUTO_COLLAPSE_MS)
            expanded = false
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerHeightDp = with(density) { constraints.maxHeight.toDp() }.value.toInt()
        val clusterHeightDp = controls.rowHeightDp * 3
        val itemHeightDp = if (expanded) clusterHeightDp else DOCK_HANDLE_HEIGHT_DP
        val topDp = readerDockTopDp(offsetFraction, containerHeightDp, itemHeightDp)
        val alignment = if (placement == SasayakiControlsPlacement.Left) Alignment.TopStart else Alignment.TopEnd
        Box(
            modifier = Modifier
                .align(alignment)
                .offset(y = topDp.dp),
        ) {
            if (!expanded) {
                // The handle: drag along the edge to move it, tap to open the controls.
                var dragTopDp by remember(topDp) { mutableStateOf(topDp.toFloat()) }
                Box(
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .width(DOCK_HANDLE_WIDTH_DP.dp)
                        .height(DOCK_HANDLE_HEIGHT_DP.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(colors.infoText).copy(alpha = 0.35f))
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
                        .pointerInput(Unit) { detectTapGestures { expanded = true } },
                )
            } else {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(colors.buttonContainer).copy(alpha = 0.85f))
                        .then(
                            if (scrubEnabled) {
                                Modifier.sasayakiScrub(
                                    vertical = true,
                                    onSteps = { interaction++; onScrubSteps(it) },
                                    onEnd = { interaction++; onScrubEnd(it) },
                                    onCancel = onScrubCancel,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .sasayakiHoldRelease {
                            interaction++
                            onHoldEnd()
                        },
                ) {
                    ReaderSasayakiPlaybackButton(
                        controls = controls,
                        colors = colors,
                        icon = Icons.Rounded.FastRewind,
                        contentDescription = stringResource(R.string.sasayaki_rewind),
                        onClick = { interaction++; onSkipBackward() },
                        holdRepeat = true,
                    )
                    ReaderSasayakiPlaybackButton(
                        controls = controls,
                        colors = colors,
                        icon = if (sasayakiPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (sasayakiPlaying) stringResource(R.string.sasayaki_pause) else stringResource(R.string.sasayaki_play),
                        onClick = { interaction++; onTogglePlayback() },
                        onLongClick = if (holdEnabled) ({ interaction++; onHoldStart() }) else null,
                    )
                    ReaderSasayakiPlaybackButton(
                        controls = controls,
                        colors = colors,
                        icon = Icons.Rounded.FastForward,
                        contentDescription = stringResource(R.string.sasayaki_fast_forward),
                        onClick = { interaction++; onSkipForward() },
                        holdRepeat = true,
                    )
                }
            }
        }
    }
}
