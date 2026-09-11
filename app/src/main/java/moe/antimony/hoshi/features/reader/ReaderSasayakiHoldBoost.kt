package moe.antimony.hoshi.features.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
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

/** Pill above the playback row showing the boosted speed while a hold is active; fades out once [rate] is null. */
@Composable
internal fun ReaderSasayakiBoostHud(
    rate: Float?,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    ReaderSasayakiHudOverlay(state = rate, bottomPadding = bottomPadding, modifier = modifier) { current ->
        Text(
            text = sasayakiSpeedLabel(current),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}
