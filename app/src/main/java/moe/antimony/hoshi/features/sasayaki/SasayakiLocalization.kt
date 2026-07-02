package moe.antimony.hoshi.features.sasayaki

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import moe.antimony.hoshi.R

@Composable
internal fun SasayakiReaderSkipButtonAction.labelText(): String =
    seconds?.let { stringResource(R.string.sasayaki_skip_seconds_format, it) }
        ?: stringResource(R.string.sasayaki_skip_cue)

@Composable
internal fun sasayakiImageHoldText(seconds: Float): String {
    val normalized = normalizeSasayakiImageHoldSeconds(seconds)
    return if (normalized <= 0f) {
        stringResource(R.string.sasayaki_image_hold_off)
    } else {
        stringResource(R.string.sasayaki_seconds_decimal_format, normalized)
    }
}
