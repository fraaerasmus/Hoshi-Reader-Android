package moe.antimony.hoshi.features.reader

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderHardwareKeyNavigationTest {
    @Test
    fun pageDownAndPageUpAlwaysMapToReaderNavigation() {
        val settings = ReaderSettings(volumeKeysTurnPages = false, reverseVolumeKeyDirection = true)

        assertEquals(
            ReaderNavigationDirection.Forward,
            readerNavigationDirectionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_PAGE_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
            ),
        )
        assertEquals(
            ReaderNavigationDirection.Backward,
            readerNavigationDirectionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_PAGE_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
            ),
        )
    }

    @Test
    fun volumeKeysAreIgnoredUntilEnabled() {
        val settings = ReaderSettings(volumeKeysTurnPages = false)

        assertNull(
            readerNavigationDirectionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
            ),
        )
        assertNull(
            readerNavigationDirectionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
            ),
        )
    }

    @Test
    fun sasayakiSeekVolumeKeysAreIgnoredUntilEnabled() {
        val settings = ReaderSettings(
            volumeKeysTurnPages = false,
            volumeKeysSeekSasayaki = false,
        )

        assertNull(
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = true,
            ),
        )
        assertNull(
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = true,
            ),
        )
    }

    @Test
    fun sasayakiSeekVolumeKeysRequireEnabledSasayakiAndLoadedAudio() {
        val settings = ReaderSettings(volumeKeysSeekSasayaki = true)

        assertNull(
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = false,
                hasSasayakiAudio = true,
            ),
        )
        assertNull(
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = false,
            ),
        )
    }

    @Test
    fun sasayakiSeekVolumeKeysUseDefaultDirection() {
        val settings = ReaderSettings(
            volumeKeysSeekSasayaki = true,
            reverseVolumeKeyDirection = false,
        )

        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekBackward,
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = true,
            ),
        )
        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekForward,
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = true,
            ),
        )
    }

    @Test
    fun sasayakiSeekVolumeKeysCanBeReversed() {
        val settings = ReaderSettings(
            volumeKeysSeekSasayaki = true,
            reverseVolumeKeyDirection = true,
        )

        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekForward,
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = true,
            ),
        )
        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekBackward,
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = true,
            ),
        )
    }

    @Test
    fun sasayakiSeekVolumeKeysTakePriorityOverVolumePageTurnsWhenAudioIsLoaded() {
        val settings = ReaderSettings(
            volumeKeysTurnPages = true,
            volumeKeysSeekSasayaki = true,
        )

        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekBackward,
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = true,
            ),
        )
    }

    @Test
    fun popupTermNavigationTakesPriorityOverSasayakiAndPageTurns() {
        val settings = ReaderSettings(
            volumeKeysTurnPages = true,
            volumeKeysNavigatePopupTerms = true,
            volumeKeysSeekSasayaki = true,
        )

        assertEquals(
            ReaderHardwareKeyAction.PopupTermNavigation(PopupTermNavigationDirection.Previous),
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = true,
                hasLookupPopup = true,
            ),
        )
        assertEquals(
            ReaderHardwareKeyAction.PopupTermNavigation(PopupTermNavigationDirection.Next),
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = true,
                hasLookupPopup = true,
            ),
        )
    }

    @Test
    fun popupTermNavigationUsesReverseVolumeDirection() {
        val settings = ReaderSettings(
            volumeKeysNavigatePopupTerms = true,
            reverseVolumeKeyDirection = true,
        )

        assertEquals(
            ReaderHardwareKeyAction.PopupTermNavigation(PopupTermNavigationDirection.Next),
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = false,
                hasSasayakiAudio = false,
                hasLookupPopup = true,
            ),
        )
        assertEquals(
            ReaderHardwareKeyAction.PopupTermNavigation(PopupTermNavigationDirection.Previous),
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = false,
                hasSasayakiAudio = false,
                hasLookupPopup = true,
            ),
        )
    }

    @Test
    fun popupTermNavigationFallsBackWhenDisabledOrNoPopupExists() {
        val settings = ReaderSettings(
            volumeKeysTurnPages = true,
            volumeKeysNavigatePopupTerms = true,
        )

        assertEquals(
            ReaderHardwareKeyAction.ReaderNavigation(ReaderNavigationDirection.Forward),
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = false,
                hasSasayakiAudio = false,
                hasLookupPopup = false,
            ),
        )
        assertEquals(
            ReaderHardwareKeyAction.ReaderNavigation(ReaderNavigationDirection.Forward),
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings.copy(volumeKeysNavigatePopupTerms = false),
                sasayakiEnabled = false,
                hasSasayakiAudio = false,
                hasLookupPopup = true,
            ),
        )
    }

    @Test
    fun popupTermNavigationRepeatsAndConsumesKeyUpWithoutAction() {
        val settings = ReaderSettings(volumeKeysNavigatePopupTerms = true)

        assertEquals(
            ReaderHardwareKeyAction.PopupTermNavigation(PopupTermNavigationDirection.Next),
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 3,
                settings = settings,
                sasayakiEnabled = false,
                hasSasayakiAudio = false,
                hasLookupPopup = true,
            ),
        )
        val keyUp = readerHardwareKeyEventForKeyEvent(
            keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
            action = KeyEvent.ACTION_UP,
            repeatCount = 0,
            settings = settings,
            sasayakiEnabled = false,
            hasSasayakiAudio = false,
            hasLookupPopup = true,
        )
        assertTrue(keyUp.consumed)
        assertNull(keyUp.action)
    }

    @Test
    fun sasayakiSeekFallsBackToVolumePageTurnsWhenAudioIsNotLoaded() {
        val settings = ReaderSettings(
            volumeKeysTurnPages = true,
            volumeKeysSeekSasayaki = true,
        )

        assertEquals(
            ReaderHardwareKeyAction.ReaderNavigation(ReaderNavigationDirection.Backward),
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = false,
            ),
        )
    }

    @Test
    fun enabledVolumeKeysUseDefaultReaderDirection() {
        val settings = ReaderSettings(volumeKeysTurnPages = true, reverseVolumeKeyDirection = false)

        assertEquals(
            ReaderNavigationDirection.Forward,
            readerNavigationDirectionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
            ),
        )
        assertEquals(
            ReaderNavigationDirection.Backward,
            readerNavigationDirectionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
            ),
        )
    }

    @Test
    fun enabledVolumeKeysCanBeReversedWithoutChangingPageKeys() {
        val settings = ReaderSettings(volumeKeysTurnPages = true, reverseVolumeKeyDirection = true)

        assertEquals(
            ReaderNavigationDirection.Backward,
            readerNavigationDirectionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
            ),
        )
        assertEquals(
            ReaderNavigationDirection.Forward,
            readerNavigationDirectionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
            ),
        )
        assertEquals(
            ReaderNavigationDirection.Forward,
            readerNavigationDirectionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_PAGE_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
            ),
        )
    }

    @Test
    fun pageKeysIgnoreKeyUpAndRepeatedKeyDownEvents() {
        val settings = ReaderSettings(volumeKeysTurnPages = true, volumeKeysSeekSasayaki = true)

        assertNull(
            readerNavigationDirectionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_PAGE_DOWN,
                action = KeyEvent.ACTION_UP,
                repeatCount = 0,
                settings = settings,
            ),
        )
        assertNull(
            readerNavigationDirectionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_PAGE_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                settings = settings,
            ),
        )
    }

    @Test
    fun enabledVolumePageTurnKeysRepeatReaderNavigation() {
        val settings = ReaderSettings(volumeKeysTurnPages = true)

        assertEquals(
            ReaderHardwareKeyAction.ReaderNavigation(ReaderNavigationDirection.Forward),
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 3,
                settings = settings,
                sasayakiEnabled = false,
                hasSasayakiAudio = false,
            ),
        )
        assertEquals(
            ReaderHardwareKeyAction.ReaderNavigation(ReaderNavigationDirection.Backward),
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 2,
                settings = settings,
                sasayakiEnabled = false,
                hasSasayakiAudio = false,
            ),
        )
    }

    @Test
    fun enabledSasayakiVolumeSeekKeysRepeatSeekActions() {
        val settings = ReaderSettings(volumeKeysSeekSasayaki = true)

        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekBackward,
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 4,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = true,
            ),
        )
        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekForward,
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = true,
            ),
        )
    }

    @Test
    fun sasayakiSeekKeysRepeatWhileHeld() {
        val settings = ReaderSettings(volumeKeysSeekSasayaki = true)
        val expectations = mapOf(
            KeyEvent.KEYCODE_DPAD_LEFT to ReaderHardwareKeyAction.SasayakiSeekBackward,
            KeyEvent.KEYCODE_J to ReaderHardwareKeyAction.SasayakiSeekBackward,
            KeyEvent.KEYCODE_DPAD_RIGHT to ReaderHardwareKeyAction.SasayakiSeekForward,
            KeyEvent.KEYCODE_L to ReaderHardwareKeyAction.SasayakiSeekForward,
            // Volume keys (default direction): up seeks backward, down seeks forward.
            KeyEvent.KEYCODE_VOLUME_UP to ReaderHardwareKeyAction.SasayakiSeekBackward,
            KeyEvent.KEYCODE_VOLUME_DOWN to ReaderHardwareKeyAction.SasayakiSeekForward,
        )

        expectations.forEach { (keyCode, expected) ->
            assertEquals(
                expected,
                readerHardwareKeyActionForKeyEvent(
                    keyCode = keyCode,
                    action = KeyEvent.ACTION_DOWN,
                    repeatCount = 1,
                    settings = settings,
                    sasayakiEnabled = true,
                    hasSasayakiAudio = true,
                ),
            )
        }
    }

    @Test
    fun sasayakiKeyboardKeysControlPlaybackWhenAudioLoaded() {
        val settings = ReaderSettings()
        val expectations = mapOf(
            KeyEvent.KEYCODE_SPACE to ReaderHardwareKeyAction.SasayakiTogglePlayback,
            KeyEvent.KEYCODE_K to ReaderHardwareKeyAction.SasayakiTogglePlayback,
            KeyEvent.KEYCODE_DPAD_LEFT to ReaderHardwareKeyAction.SasayakiSeekBackward,
            KeyEvent.KEYCODE_J to ReaderHardwareKeyAction.SasayakiSeekBackward,
            KeyEvent.KEYCODE_DPAD_RIGHT to ReaderHardwareKeyAction.SasayakiSeekForward,
            KeyEvent.KEYCODE_L to ReaderHardwareKeyAction.SasayakiSeekForward,
        )

        expectations.forEach { (keyCode, expected) ->
            assertEquals(
                expected,
                readerHardwareKeyActionForKeyEvent(
                    keyCode = keyCode,
                    action = KeyEvent.ACTION_DOWN,
                    repeatCount = 0,
                    settings = settings,
                    sasayakiEnabled = true,
                    hasSasayakiAudio = true,
                ),
            )
        }
    }

    @Test
    fun sasayakiKeyboardKeysAreIgnoredWithoutLoadedAudio() {
        val settings = ReaderSettings()

        sasayakiKeyboardKeyCodes().forEach { keyCode ->
            assertNull(
                readerHardwareKeyActionForKeyEvent(
                    keyCode = keyCode,
                    action = KeyEvent.ACTION_DOWN,
                    repeatCount = 0,
                    settings = settings,
                    sasayakiEnabled = true,
                    hasSasayakiAudio = false,
                ),
            )
        }
    }

    @Test
    fun sasayakiKeyboardKeysAreIgnoredWhileTextEditorFocused() {
        val settings = ReaderSettings()

        sasayakiKeyboardKeyCodes().forEach { keyCode ->
            assertNull(
                readerHardwareKeyActionForKeyEvent(
                    keyCode = keyCode,
                    action = KeyEvent.ACTION_DOWN,
                    repeatCount = 0,
                    settings = settings,
                    sasayakiEnabled = true,
                    hasSasayakiAudio = true,
                    textEditorFocused = true,
                ),
            )
        }
    }

    @Test
    fun sasayakiKeyboardKeysIgnoreKeyUpAndToggleRepeats() {
        val settings = ReaderSettings()

        // Key-up never triggers an action, for toggle or seek keys.
        assertNull(
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_SPACE,
                action = KeyEvent.ACTION_UP,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = true,
            ),
        )
        assertNull(
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_J,
                action = KeyEvent.ACTION_UP,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = true,
            ),
        )
        // Play/pause toggle does not auto-repeat while held (seek keys do).
        assertNull(
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_SPACE,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = true,
            ),
        )
    }

    @Test
    fun enabledVolumeKeysConsumeKeyUpWithoutAction() {
        val settings = ReaderSettings(volumeKeysTurnPages = true)

        val result = readerHardwareKeyEventForKeyEvent(
            keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
            action = KeyEvent.ACTION_UP,
            repeatCount = 0,
            settings = settings,
            sasayakiEnabled = false,
            hasSasayakiAudio = false,
        )

        assertTrue(result.consumed)
        assertNull(result.action)
    }

    @Test
    fun disabledVolumeKeysAreNotConsumed() {
        val settings = ReaderSettings(volumeKeysTurnPages = false, volumeKeysSeekSasayaki = false)

        val result = readerHardwareKeyEventForKeyEvent(
            keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
            action = KeyEvent.ACTION_DOWN,
            repeatCount = 1,
            settings = settings,
            sasayakiEnabled = true,
            hasSasayakiAudio = true,
        )

        assertFalse(result.consumed)
        assertNull(result.action)
    }

    @Test
    fun enabledSasayakiVolumeKeysConsumeKeyUpWithoutAction() {
        val settings = ReaderSettings(volumeKeysSeekSasayaki = true)

        val result = readerHardwareKeyEventForKeyEvent(
            keyCode = KeyEvent.KEYCODE_VOLUME_UP,
            action = KeyEvent.ACTION_UP,
            repeatCount = 0,
            settings = settings,
            sasayakiEnabled = true,
            hasSasayakiAudio = true,
        )

        assertTrue(result.consumed)
        assertNull(result.action)
    }

    private fun sasayakiKeyboardKeyCodes() = listOf(
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_K,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_J,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_L,
    )
}
