package moe.antimony.hoshi.features.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.antimony.hoshi.R
import kotlin.math.abs

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

/** [steps] are signed in audio time; [secondsPerStep] is null when stepping by sentence cue. */
internal data class ReaderSasayakiScrubHudState(
    val steps: Int,
    val secondsPerStep: Int?,
    val cueText: String?,
)

/** Centered overlay previewing where a playback-row scrub will land; fades out once [state] is null. */
@Composable
internal fun ReaderSasayakiScrubHud(
    state: ReaderSasayakiScrubHudState?,
    modifier: Modifier = Modifier,
) {
    // Plain holder (not snapshot state) so the last preview stays on screen through the fade-out.
    val shown = remember { arrayOfNulls<ReaderSasayakiScrubHudState>(1) }
    if (state != null) shown[0] = state
    val alpha by animateFloatAsState(
        targetValue = if (state != null) 1f else 0f,
        animationSpec = tween(durationMillis = HUD_FADE_MS),
        label = "sasayakiScrubHudAlpha",
    )
    val current = shown[0] ?: return
    if (alpha == 0f) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        val scrim = MaterialTheme.colorScheme.surface
        Column(
            modifier = Modifier
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(scrim.copy(alpha = 0.6f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.width * 0.7f,
                        ),
                    )
                }
                .padding(horizontal = 32.dp, vertical = 20.dp)
                .widthIn(max = 320.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (current.steps < 0) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = readerSasayakiScrubLabel(current),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (current.cueText != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = current.cueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun readerSasayakiScrubLabel(state: ReaderSasayakiScrubHudState): String {
    val sign = if (state.steps < 0) "−" else "+"
    val seconds = state.secondsPerStep
    return if (seconds == null) {
        val count = abs(state.steps)
        pluralStringResource(R.plurals.sasayaki_scrub_sentences, count, "$sign$count")
    } else {
        stringResource(R.string.sasayaki_scrub_seconds, "$sign${abs(state.steps) * seconds}")
    }
}

private const val HUD_FADE_MS = 200
