package moe.antimony.hoshi.features.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import moe.antimony.hoshi.R
import kotlin.math.roundToInt

/**
 * Transient centered overlay showing the current brightness/volume level during an edge-swipe
 * adjustment. Fades out shortly after the last update. Only this small subtree recomposes while a
 * drag is in progress.
 */
@Composable
fun ReaderEdgeAdjustHud(
    controller: ReaderEdgeAdjustController,
    modifier: Modifier = Modifier,
) {
    // Read the state here (not at the call site) so only this subtree recomposes per drag step.
    val state = controller.hud
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state?.token) {
        if (state == null) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        delay(HUD_VISIBLE_MS)
        visible = false
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = HUD_FADE_MS),
        label = "edgeAdjustHudAlpha",
    )
    if (state == null || alpha == 0f) return

    val level = state.level.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .width(120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val (icon, contentDescription) = when (state.kind) {
                    ReaderEdgeAdjustKind.Brightness ->
                        Icons.Rounded.BrightnessHigh to stringResource(R.string.reader_edge_hud_brightness)
                    ReaderEdgeAdjustKind.Volume ->
                        Icons.AutoMirrored.Rounded.VolumeUp to stringResource(R.string.reader_edge_hud_volume)
                }
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(level)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${(level * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val HUD_VISIBLE_MS = 650L
private const val HUD_FADE_MS = 200
