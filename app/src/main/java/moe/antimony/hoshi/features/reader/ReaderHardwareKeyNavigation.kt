package moe.antimony.hoshi.features.reader

import android.view.KeyEvent

internal enum class PopupTermNavigationDirection {
    Previous,
    Next,
}

internal sealed interface ReaderHardwareKeyAction {
    data class ReaderNavigation(val direction: ReaderNavigationDirection) : ReaderHardwareKeyAction
    data object SasayakiTogglePlayback : ReaderHardwareKeyAction
    data class PopupTermNavigation(val direction: PopupTermNavigationDirection) : ReaderHardwareKeyAction
    data object SasayakiSeekForward : ReaderHardwareKeyAction
    data object SasayakiSeekBackward : ReaderHardwareKeyAction
}

internal data class ReaderHardwareKeyEventResult(
    val consumed: Boolean,
    val action: ReaderHardwareKeyAction? = null,
)

internal fun readerNavigationDirectionForKeyEvent(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    settings: ReaderSettings,
): ReaderNavigationDirection? =
    (readerHardwareKeyEventForKeyEvent(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        settings = settings,
        sasayakiEnabled = false,
        hasSasayakiAudio = false,
    ).action as? ReaderHardwareKeyAction.ReaderNavigation)?.direction

internal fun readerHardwareKeyActionForKeyEvent(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    settings: ReaderSettings,
    sasayakiEnabled: Boolean,
    hasSasayakiAudio: Boolean,
    textEditorFocused: Boolean = false,
    hasLookupPopup: Boolean = false,
): ReaderHardwareKeyAction? =
    readerHardwareKeyEventForKeyEvent(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        settings = settings,
        sasayakiEnabled = sasayakiEnabled,
        hasSasayakiAudio = hasSasayakiAudio,
        textEditorFocused = textEditorFocused,
        hasLookupPopup = hasLookupPopup,
    ).action

internal fun readerHardwareKeyEventForKeyEvent(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    settings: ReaderSettings,
    sasayakiEnabled: Boolean,
    hasSasayakiAudio: Boolean,
    textEditorFocused: Boolean = false,
    hasLookupPopup: Boolean = false,
): ReaderHardwareKeyEventResult {
    return when (keyCode) {
        KeyEvent.KEYCODE_PAGE_DOWN -> pageKeyResult(
            action = action,
            repeatCount = repeatCount,
            direction = ReaderNavigationDirection.Forward,
        )
        KeyEvent.KEYCODE_PAGE_UP -> pageKeyResult(
            action = action,
            repeatCount = repeatCount,
            direction = ReaderNavigationDirection.Backward,
        )
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_UP,
        -> volumeKeyResult(
            keyCode = keyCode,
            action = action,
            settings = settings,
            sasayakiEnabled = sasayakiEnabled,
            hasSasayakiAudio = hasSasayakiAudio,
            hasLookupPopup = hasLookupPopup,
        )
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_K,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_J,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_L,
        -> sasayakiKeyboardResult(
            keyCode = keyCode,
            action = action,
            repeatCount = repeatCount,
            sasayakiEnabled = sasayakiEnabled,
            hasSasayakiAudio = hasSasayakiAudio,
            textEditorFocused = textEditorFocused,
        )
        else -> ReaderHardwareKeyEventResult(consumed = false)
    }
}

private fun sasayakiKeyboardResult(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    sasayakiEnabled: Boolean,
    hasSasayakiAudio: Boolean,
    textEditorFocused: Boolean,
): ReaderHardwareKeyEventResult {
    if (textEditorFocused || !sasayakiEnabled || !hasSasayakiAudio) {
        return ReaderHardwareKeyEventResult(consumed = false)
    }
    val keyAction = when (keyCode) {
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_K -> ReaderHardwareKeyAction.SasayakiTogglePlayback
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_J -> ReaderHardwareKeyAction.SasayakiSeekBackward
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_L -> ReaderHardwareKeyAction.SasayakiSeekForward
        else -> return ReaderHardwareKeyEventResult(consumed = false)
    }
    // Play/pause fires once per press; seeks repeat while a key is held.
    val fires = action == KeyEvent.ACTION_DOWN &&
        (keyAction != ReaderHardwareKeyAction.SasayakiTogglePlayback || repeatCount == 0)
    return ReaderHardwareKeyEventResult(consumed = true, action = keyAction.takeIf { fires })
}

private fun pageKeyResult(
    action: Int,
    repeatCount: Int,
    direction: ReaderNavigationDirection,
): ReaderHardwareKeyEventResult {
    if (action != KeyEvent.ACTION_DOWN || repeatCount != 0) {
        return ReaderHardwareKeyEventResult(consumed = false)
    }
    return ReaderHardwareKeyEventResult(
        consumed = true,
        action = ReaderHardwareKeyAction.ReaderNavigation(direction),
    )
}

private fun volumeKeyResult(
    keyCode: Int,
    action: Int,
    settings: ReaderSettings,
    sasayakiEnabled: Boolean,
    hasSasayakiAudio: Boolean,
    hasLookupPopup: Boolean,
): ReaderHardwareKeyEventResult {
    val keyAction = readerVolumeKeyAction(
        keyCode = keyCode,
        settings = settings,
        sasayakiEnabled = sasayakiEnabled,
        hasSasayakiAudio = hasSasayakiAudio,
        hasLookupPopup = hasLookupPopup,
    ) ?: return ReaderHardwareKeyEventResult(consumed = false)
    return ReaderHardwareKeyEventResult(
        consumed = true,
        action = keyAction.takeIf { action == KeyEvent.ACTION_DOWN },
    )
}

private fun readerVolumeKeyAction(
    keyCode: Int,
    settings: ReaderSettings,
    sasayakiEnabled: Boolean,
    hasSasayakiAudio: Boolean,
    hasLookupPopup: Boolean,
): ReaderHardwareKeyAction? {
    if (settings.volumeKeysNavigatePopupTerms && hasLookupPopup) {
        return ReaderHardwareKeyAction.PopupTermNavigation(
            popupTermNavigationDirectionForVolumeKey(
                keyCode = keyCode,
                reverseDirection = settings.reverseVolumeKeyDirection,
            ),
        )
    }
    if (settings.volumeKeysSeekSasayaki && sasayakiEnabled && hasSasayakiAudio) {
        return sasayakiSeekActionForVolumeKey(
            keyCode = keyCode,
            reverseDirection = settings.reverseVolumeKeyDirection,
        )
    }
    if (!settings.volumeKeysTurnPages) return null
    return ReaderHardwareKeyAction.ReaderNavigation(
        volumePageTurnDirectionForKey(
            keyCode = keyCode,
            reverseDirection = settings.reverseVolumeKeyDirection,
        ),
    )
}

private fun popupTermNavigationDirectionForVolumeKey(
    keyCode: Int,
    reverseDirection: Boolean,
): PopupTermNavigationDirection =
    when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> if (reverseDirection) {
            PopupTermNavigationDirection.Next
        } else {
            PopupTermNavigationDirection.Previous
        }
        KeyEvent.KEYCODE_VOLUME_DOWN -> if (reverseDirection) {
            PopupTermNavigationDirection.Previous
        } else {
            PopupTermNavigationDirection.Next
        }
        else -> error("Unsupported volume key: $keyCode")
    }

private fun sasayakiSeekActionForVolumeKey(
    keyCode: Int,
    reverseDirection: Boolean,
): ReaderHardwareKeyAction =
    when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> if (reverseDirection) {
            ReaderHardwareKeyAction.SasayakiSeekForward
        } else {
            ReaderHardwareKeyAction.SasayakiSeekBackward
        }
        KeyEvent.KEYCODE_VOLUME_DOWN -> if (reverseDirection) {
            ReaderHardwareKeyAction.SasayakiSeekBackward
        } else {
            ReaderHardwareKeyAction.SasayakiSeekForward
        }
        else -> error("Unsupported volume key: $keyCode")
    }

private fun volumePageTurnDirectionForKey(
    keyCode: Int,
    reverseDirection: Boolean,
): ReaderNavigationDirection =
    when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_DOWN -> if (reverseDirection) {
            ReaderNavigationDirection.Backward
        } else {
            ReaderNavigationDirection.Forward
        }
        KeyEvent.KEYCODE_VOLUME_UP -> if (reverseDirection) {
            ReaderNavigationDirection.Forward
        } else {
            ReaderNavigationDirection.Backward
        }
        else -> error("Unsupported volume key: $keyCode")
    }
