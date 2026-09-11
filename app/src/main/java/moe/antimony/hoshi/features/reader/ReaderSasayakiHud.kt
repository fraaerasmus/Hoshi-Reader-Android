package moe.antimony.hoshi.features.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fork feature: the card the Sasayaki playback-row gestures (scrub, hold-to-boost) preview into.
 * Sits just above the row, where the finger is; keeps the last [state] on screen through the fade-out.
 */
@Composable
internal fun <T : Any> ReaderSasayakiHudOverlay(
    state: T?,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    // Plain holder (not snapshot state) so the last preview stays on screen through the fade-out.
    val shown = remember { arrayOfNulls<Any>(1) }
    if (state != null) shown[0] = state
    val visible = state != null
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = HUD_FADE_MS),
        label = "sasayakiHudAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.96f,
        animationSpec = tween(durationMillis = HUD_FADE_MS),
        label = "sasayakiHudScale",
    )
    @Suppress("UNCHECKED_CAST")
    val current = shown[0] as T? ?: return
    if (alpha == 0f) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, bottom = bottomPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        ) {
            content(current)
        }
    }
}

private const val HUD_FADE_MS = 180
