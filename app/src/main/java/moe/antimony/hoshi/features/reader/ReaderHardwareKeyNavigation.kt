package moe.antimony.hoshi.features.reader

import android.view.KeyEvent
import moe.antimony.hoshi.features.reader.input.ReaderKey
import moe.antimony.hoshi.features.reader.input.ReaderKeyAction
import moe.antimony.hoshi.features.reader.input.ReaderKeyBindings
import moe.antimony.hoshi.features.reader.input.READER_KEY_MODIFIER_MASK
import moe.antimony.hoshi.features.reader.input.isReaderVolumeKey

internal enum class PopupTermNavigationDirection {
    Previous,
    Next,
}

internal sealed interface ReaderHardwareKeyAction {
    data class ReaderNavigation(val direction: ReaderNavigationDirection) : ReaderHardwareKeyAction
    data class ChapterNavigation(val direction: ReaderNavigationDirection) : ReaderHardwareKeyAction
    data object ToggleFocusMode : ReaderHardwareKeyAction
    data object CloseLookupPopup : ReaderHardwareKeyAction
    data object PickWord : ReaderHardwareKeyAction
    data class WordStep(val forward: Boolean) : ReaderHardwareKeyAction
    data object MineTopTerm : ReaderHardwareKeyAction
    data object PlayTopTermAudio : ReaderHardwareKeyAction
    /** Scroll the top popup by most of a viewport. */
    data class PopupScroll(val down: Boolean) : ReaderHardwareKeyAction
    /** Back or forward through the top popup's lookup history. */
    data class PopupHistory(val forward: Boolean) : ReaderHardwareKeyAction
    data object SasayakiTogglePlayback : ReaderHardwareKeyAction
    data class PopupTermNavigation(val direction: PopupTermNavigationDirection) : ReaderHardwareKeyAction
    data object SasayakiSeekForward : ReaderHardwareKeyAction
    data object SasayakiSeekBackward : ReaderHardwareKeyAction
    /** `>` / `<`: step the saved playback rate, like YouTube. */
    data object SasayakiSpeedUp : ReaderHardwareKeyAction
    data object SasayakiSpeedDown : ReaderHardwareKeyAction
    /** Play key auto-repeat began, or a boost key went down: hold-to-boost should start. */
    data object SasayakiHoldBoostStart : ReaderHardwareKeyAction
    /** Play key released: end a running boost, else toggle playback. */
    data object SasayakiPlayKeyReleased : ReaderHardwareKeyAction
    /** Volume or boost key released: end a running boost; a volume key's tap action already fired on key-down. */
    data object SasayakiVolumeKeyReleased : ReaderHardwareKeyAction
}

internal data class ReaderHardwareKeyEventResult(
    val consumed: Boolean,
    val action: ReaderHardwareKeyAction? = null,
)

/** What each held key was bound to when it went down, so one press keeps one action even if a popup closes under it. */
internal class ReaderKeyPresses {
    private val held = mutableMapOf<Int, ReaderKeyAction>()

    /** [resolve] runs only when a press begins; its repeats and release get the same answer. */
    fun action(keyCode: Int, action: Int, repeatCount: Int, resolve: () -> ReaderKeyAction?): ReaderKeyAction? {
        if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
            val bound = resolve()
            if (bound == null) held.remove(keyCode) else held[keyCode] = bound
            return bound
        }
        return if (action == KeyEvent.ACTION_UP) held.remove(keyCode) else held[keyCode]
    }
}

internal fun readerHardwareKeyEventForKeyEvent(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    sasayakiEnabled: Boolean,
    hasSasayakiAudio: Boolean,
    textEditorFocused: Boolean = false,
    hasLookupPopup: Boolean = false,
    sasayakiHoldToBoost: Boolean = false,
    volumeKeysHoldToBoost: Boolean = false,
    metaState: Int = 0,
    keyBindings: ReaderKeyBindings = ReaderKeyBindings(),
    /** Null resolves every event on its own, which is what the tests of single events want. */
    presses: ReaderKeyPresses? = null,
): ReaderHardwareKeyEventResult {
    val audioLoaded = sasayakiEnabled && hasSasayakiAudio
    // Nothing is bound while typing, since any key can be.
    val resolve = {
        if (textEditorFocused) {
            null
        } else {
            keyBindings.actionFor(
                key = ReaderKey(keyCode, metaState and READER_KEY_MODIFIER_MASK),
                popupOpen = hasLookupPopup,
                audioLoaded = audioLoaded,
            )
        }
    }
    val bound = if (presses == null) resolve() else presses.action(keyCode, action, repeatCount, resolve)
    if (isReaderVolumeKey(keyCode) && volumeKeysHoldToBoost && audioLoaded) {
        // The tap action fires on the first key-down as before; auto-repeat becomes the boost instead of repeating it.
        val holdAction = when {
            action == KeyEvent.ACTION_UP -> ReaderHardwareKeyAction.SasayakiVolumeKeyReleased
            action == KeyEvent.ACTION_DOWN && repeatCount == 0 -> bound?.hardwareAction()
            action == KeyEvent.ACTION_DOWN && repeatCount == 1 -> ReaderHardwareKeyAction.SasayakiHoldBoostStart
            else -> null
        }
        return ReaderHardwareKeyEventResult(consumed = true, action = holdAction)
    }
    return boundKeyResult(
        bound = bound,
        action = action,
        repeatCount = repeatCount,
        holdToBoost = sasayakiHoldToBoost,
    )
}

/** A bound key is consumed whole, down to its release, so none of it reaches the page. */
private fun boundKeyResult(
    bound: ReaderKeyAction?,
    action: Int,
    repeatCount: Int,
    holdToBoost: Boolean,
): ReaderHardwareKeyEventResult {
    bound ?: return ReaderHardwareKeyEventResult(consumed = false)
    val down = action == KeyEvent.ACTION_DOWN
    val up = action == KeyEvent.ACTION_UP
    val keyAction = when {
        bound == ReaderKeyAction.BoostWhileHeld -> when {
            up -> ReaderHardwareKeyAction.SasayakiVolumeKeyReleased
            down && repeatCount == 0 -> ReaderHardwareKeyAction.SasayakiHoldBoostStart
            else -> null
        }
        // Tap vs hold is decided on key-up; the first auto-repeat marks a hold.
        bound == ReaderKeyAction.TogglePlayback && holdToBoost -> when {
            up -> ReaderHardwareKeyAction.SasayakiPlayKeyReleased
            down && repeatCount == 1 -> ReaderHardwareKeyAction.SasayakiHoldBoostStart
            else -> null
        }
        down && (repeatCount == 0 || bound.repeats) -> bound.hardwareAction()
        else -> null
    }
    return ReaderHardwareKeyEventResult(consumed = true, action = keyAction)
}

private fun ReaderKeyAction.hardwareAction(): ReaderHardwareKeyAction? = when (this) {
    ReaderKeyAction.PageForward -> ReaderHardwareKeyAction.ReaderNavigation(ReaderNavigationDirection.Forward)
    ReaderKeyAction.PageBackward -> ReaderHardwareKeyAction.ReaderNavigation(ReaderNavigationDirection.Backward)
    ReaderKeyAction.ChapterForward -> ReaderHardwareKeyAction.ChapterNavigation(ReaderNavigationDirection.Forward)
    ReaderKeyAction.ChapterBackward -> ReaderHardwareKeyAction.ChapterNavigation(ReaderNavigationDirection.Backward)
    ReaderKeyAction.ToggleFocusMode -> ReaderHardwareKeyAction.ToggleFocusMode
    ReaderKeyAction.PickWord -> ReaderHardwareKeyAction.PickWord
    ReaderKeyAction.WordPrevious -> ReaderHardwareKeyAction.WordStep(forward = false)
    ReaderKeyAction.WordNext -> ReaderHardwareKeyAction.WordStep(forward = true)
    ReaderKeyAction.TogglePlayback -> ReaderHardwareKeyAction.SasayakiTogglePlayback
    ReaderKeyAction.SkipBackward -> ReaderHardwareKeyAction.SasayakiSeekBackward
    ReaderKeyAction.SkipForward -> ReaderHardwareKeyAction.SasayakiSeekForward
    ReaderKeyAction.SpeedDown -> ReaderHardwareKeyAction.SasayakiSpeedDown
    ReaderKeyAction.SpeedUp -> ReaderHardwareKeyAction.SasayakiSpeedUp
    ReaderKeyAction.ClosePopup -> ReaderHardwareKeyAction.CloseLookupPopup
    ReaderKeyAction.PopupPreviousTerm -> ReaderHardwareKeyAction.PopupTermNavigation(PopupTermNavigationDirection.Previous)
    ReaderKeyAction.PopupNextTerm -> ReaderHardwareKeyAction.PopupTermNavigation(PopupTermNavigationDirection.Next)
    ReaderKeyAction.MineTerm -> ReaderHardwareKeyAction.MineTopTerm
    ReaderKeyAction.PlayTermAudio -> ReaderHardwareKeyAction.PlayTopTermAudio
    ReaderKeyAction.PopupScrollUp -> ReaderHardwareKeyAction.PopupScroll(down = false)
    ReaderKeyAction.PopupScrollDown -> ReaderHardwareKeyAction.PopupScroll(down = true)
    ReaderKeyAction.PopupBack -> ReaderHardwareKeyAction.PopupHistory(forward = false)
    ReaderKeyAction.PopupForward -> ReaderHardwareKeyAction.PopupHistory(forward = true)
    ReaderKeyAction.BoostWhileHeld -> null
}
