package moe.antimony.hoshi.features.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

enum class ReaderEdgeAdjustKind { Brightness, Volume }

data class ReaderEdgeAdjustHudState(
    val kind: ReaderEdgeAdjustKind,
    /** Current level, 0f..1f, for display. */
    val level: Float,
    /** Increments on every update so the HUD can restart its auto-hide timer. */
    val token: Int,
)

/**
 * Applies edge-swipe brightness/volume adjustments and drives the [hud] overlay state.
 *
 * Brightness is applied to the host [Activity] window only ([WindowManager.LayoutParams.screenBrightness]),
 * so it is Reader-local and reverts to the system value on [resetBrightnessOverride] (called when the
 * Reader leaves composition). Volume adjusts the media stream that Sasayaki/word audio plays on.
 */
class ReaderEdgeAdjustController(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    var hud by mutableStateOf<ReaderEdgeAdjustHudState?>(null)
        private set

    private var activeKind: ReaderEdgeAdjustKind? = null
    private var baselineBrightness = 0f
    private var baselineVolume = 0
    private var lastAppliedBrightness8Bit = -1
    private var token = 0

    fun onBrightnessDrag(fraction: Float) {
        val window = window ?: return
        if (activeKind != ReaderEdgeAdjustKind.Brightness) {
            activeKind = ReaderEdgeAdjustKind.Brightness
            baselineBrightness = currentWindowBrightness(window)
            lastAppliedBrightness8Bit = -1
        }
        val level = (baselineBrightness + fraction).coerceIn(0f, 1f)
        // Backing brightness is 8-bit; skip redundant main-thread window writes within the same step.
        val level8Bit = (level * 255f).roundToInt()
        if (level8Bit != lastAppliedBrightness8Bit) {
            lastAppliedBrightness8Bit = level8Bit
            window.attributes = window.attributes.apply { screenBrightness = level }
        }
        publish(ReaderEdgeAdjustKind.Brightness, level)
    }

    fun onVolumeDrag(fraction: Float) {
        if (activeKind != ReaderEdgeAdjustKind.Volume) {
            activeKind = ReaderEdgeAdjustKind.Volume
            baselineVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
        val index = (baselineVolume + fraction * maxVolume).roundToInt().coerceIn(0, maxVolume)
        if (index != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
            // flags = 0: change the level without the system volume dialog (the HUD replaces it).
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
        }
        publish(ReaderEdgeAdjustKind.Volume, index.toFloat() / maxVolume)
    }

    fun onDragEnd() {
        // Force baseline re-capture on the next gesture; the HUD fades out on its own timer.
        activeKind = null
    }

    fun resetBrightnessOverride() {
        activeKind = null
        hud = null
        val window = window ?: return
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private val window: Window?
        get() = context.findActivityOrNull()?.window

    private fun currentWindowBrightness(window: Window): Float {
        val current = window.attributes.screenBrightness
        if (current >= 0f) return current
        // No app override yet: seed from system brightness so adjustment starts where the user sees it.
        val system = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(SYSTEM_BRIGHTNESS_FALLBACK)
        return (system / 255f).coerceIn(0f, 1f)
    }

    private fun publish(kind: ReaderEdgeAdjustKind, level: Float) {
        token += 1
        hud = ReaderEdgeAdjustHudState(kind = kind, level = level, token = token)
    }

    private companion object {
        const val SYSTEM_BRIGHTNESS_FALLBACK = 128
    }
}

private tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}
