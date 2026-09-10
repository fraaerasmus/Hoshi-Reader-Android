package moe.antimony.hoshi.features.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import moe.antimony.hoshi.features.sasayaki.sasayakiSpeedLabel

/**
 * Fork feature: hold-to-boost on the Sasayaki playback row. Calls [onRelease] once every pointer
 * lifts, observed on the Initial pass so consumption by clickable/long-press handlers on the same
 * node cannot end the gesture early.
 */
internal fun Modifier.sasayakiHoldRelease(onRelease: () -> Unit): Modifier =
    pointerInput(onRelease) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
            onRelease()
        }
    }

/** Centered pill showing the boosted speed while a hold is active; fades out once [rate] is null. */
@Composable
internal fun ReaderSasayakiBoostHud(
    rate: Float?,
    modifier: Modifier = Modifier,
) {
    val shown = remember { arrayOfNulls<Float>(1) }
    if (rate != null) shown[0] = rate
    val alpha by animateFloatAsState(
        targetValue = if (rate != null) 1f else 0f,
        animationSpec = tween(durationMillis = HUD_FADE_MS),
        label = "sasayakiBoostHudAlpha",
    )
    val current = shown[0] ?: return
    if (alpha == 0f) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = sasayakiSpeedLabel(current),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}

private const val HUD_FADE_MS = 200
