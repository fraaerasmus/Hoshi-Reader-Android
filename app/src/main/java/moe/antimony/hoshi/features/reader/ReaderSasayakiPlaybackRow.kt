package moe.antimony.hoshi.features.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.antimony.hoshi.R

private const val SASAYAKI_SKIP_HOLD_INITIAL_DELAY_MS = 350L
private const val SASAYAKI_SKIP_HOLD_REPEAT_INTERVAL_MS = 150L

/**
 * Fork feature: the reader's bottom Sasayaki playback row. Kept in its own file so an upstream
 * rewrite of [ReaderBottomSafeProgress] conflicts on one call site, not this block. Emits nothing
 * when [controls] are hidden.
 */
@Composable
internal fun ReaderSasayakiPlaybackRow(
    controls: ReaderSasayakiBottomPlaybackControls,
    colors: ReaderChromeColors,
    sasayakiPlaying: Boolean,
    onTapSafeArea: () -> Unit,
    onSkipBackward: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipForward: () -> Unit,
) {
    if (!controls.visible) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(controls.rowHeightDp.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onTapSafeArea),
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

@Composable
private fun ReaderSasayakiPlaybackButton(
    controls: ReaderSasayakiBottomPlaybackControls,
    colors: ReaderChromeColors,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    holdRepeat: Boolean = false,
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
            .clickable(onClick = onClick),
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
