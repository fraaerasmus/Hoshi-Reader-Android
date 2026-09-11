package moe.antimony.hoshi.features.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** A request to show the edge strips: which edge (null = both) and a token so repeats retrigger. */
internal data class ReaderEdgeZoneFlashRequest(val edge: ReaderEdgeSwipeGestureTracker.Edge?, val token: Int)

/**
 * Fork feature: tints the edge gesture strips for a moment — both when their width changes, the one that
 * was hit when an edge hold or double-tap fires — so the invisible zones have a visible answer to "where?".
 */
@Composable
internal fun ReaderEdgeZoneFlash(
    request: ReaderEdgeZoneFlashRequest?,
    zoneWidthDp: Float,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        visible = true
        delay(FLASH_HOLD_MS)
        visible = false
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(FLASH_FADE_MS), label = "edgeZoneFlashAlpha")
    if (alpha == 0f || request == null) return
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    Box(modifier = modifier.fillMaxSize().alpha(alpha)) {
        if (request.edge != ReaderEdgeSwipeGestureTracker.Edge.Right) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(zoneWidthDp.dp).background(tint))
        }
        if (request.edge != ReaderEdgeSwipeGestureTracker.Edge.Left) {
            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(zoneWidthDp.dp).background(tint))
        }
    }
}

private const val FLASH_HOLD_MS = 1_200L
private const val FLASH_FADE_MS = 250
