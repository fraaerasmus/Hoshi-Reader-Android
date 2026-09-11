package moe.antimony.hoshi.features.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.sasayaki.formatDuration
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Fork feature: drag horizontally on the Sasayaki playback row to scrub by skip-button steps.
 * Converts net horizontal travel into a signed step count; allocation-free in the move path.
 */
internal class ReaderSasayakiScrubGestureTracker(private val stepPx: Float) {
    var steps: Int = 0
        private set

    private var travelPx = 0f

    fun reset() {
        steps = 0
        travelPx = 0f
    }

    /** Accumulates [dxPx]; returns true when [steps] changed. */
    fun onDrag(dxPx: Float): Boolean {
        travelPx += dxPx
        val next = (travelPx / stepPx).toInt()
        if (next == steps) return false
        steps = next
        return true
    }
}

/** Signed steps in audio time (+ forward) for a drag of [dragSteps] (+ rightward) on the row. */
internal fun readerSasayakiScrubSignedSteps(
    dragSteps: Int,
    actions: ReaderSasayakiBottomSkipButtonActions,
): Int {
    val toward = if (dragSteps >= 0) actions.right else actions.left
    return when (toward) {
        ReaderSasayakiBottomSkipButtonAction.Forward -> abs(dragSteps)
        ReaderSasayakiBottomSkipButtonAction.Backward -> -abs(dragSteps)
    }
}

/** [steps] are signed in audio time; [secondsPerStep] is null when stepping by sentence cue. Times in seconds. */
internal data class ReaderSasayakiScrubHudState(
    val steps: Int,
    val secondsPerStep: Int?,
    val cueText: String?,
    val currentTime: Double,
    val targetTime: Double,
    val duration: Double,
)

/**
 * Preview of where a playback-row scrub will land: direction and step count, the time jump, a track
 * with the current and target positions, and the sentence at the target. Fades out once [state] is null.
 */
@Composable
internal fun ReaderSasayakiScrubHud(
    state: ReaderSasayakiScrubHudState?,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    ReaderSasayakiHudOverlay(state = state, bottomPadding = bottomPadding, modifier = modifier) { current ->
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        current.steps < 0 -> Icons.Rounded.FastRewind
                        current.steps > 0 -> Icons.Rounded.FastForward
                        else -> Icons.Rounded.SwapHoriz
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = readerSasayakiScrubLabel(current),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (current.steps == 0) {
                        formatDuration(current.currentTime)
                    } else {
                        "${formatDuration(current.currentTime)} → ${formatDuration(current.targetTime)}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (current.duration > 0.0) {
                Spacer(Modifier.height(10.dp))
                ReaderSasayakiScrubTrack(
                    currentFraction = (current.currentTime / current.duration).toFloat().coerceIn(0f, 1f),
                    targetFraction = (current.targetTime / current.duration).toFloat().coerceIn(0f, 1f),
                )
            }
            if (current.cueText != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = current.cueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Thin track: the span between the current and target positions is highlighted, the target gets the dot. */
@Composable
private fun ReaderSasayakiScrubTrack(currentFraction: Float, targetFraction: Float) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val spanColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
    ) {
        val trackHeight = 4.dp.toPx()
        val top = (size.height - trackHeight) / 2f
        val radius = CornerRadius(trackHeight / 2f)
        drawRoundRect(trackColor, topLeft = Offset(0f, top), size = Size(size.width, trackHeight), cornerRadius = radius)
        val from = min(currentFraction, targetFraction) * size.width
        val to = max(currentFraction, targetFraction) * size.width
        drawRoundRect(spanColor, topLeft = Offset(from, top), size = Size(max(to - from, trackHeight), trackHeight), cornerRadius = radius)
        drawCircle(markerColor.copy(alpha = 0.45f), radius = 3.dp.toPx(), center = Offset(currentFraction * size.width, size.height / 2f))
        drawCircle(markerColor, radius = 5.dp.toPx(), center = Offset(targetFraction * size.width, size.height / 2f))
    }
}

@Composable
private fun readerSasayakiScrubLabel(state: ReaderSasayakiScrubHudState): String {
    if (state.steps == 0) return stringResource(R.string.sasayaki_scrub_hint)
    val sign = if (state.steps < 0) "−" else "+"
    val seconds = state.secondsPerStep
    return if (seconds == null) {
        val count = abs(state.steps)
        pluralStringResource(R.plurals.sasayaki_scrub_sentences, count, "$sign$count")
    } else {
        stringResource(R.string.sasayaki_scrub_seconds, "$sign${abs(state.steps) * seconds}")
    }
}
