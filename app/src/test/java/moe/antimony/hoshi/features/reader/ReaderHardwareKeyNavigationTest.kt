package moe.antimony.hoshi.features.reader

import android.view.KeyEvent
import moe.antimony.hoshi.features.reader.input.ReaderKey
import moe.antimony.hoshi.features.reader.input.ReaderKeyAction
import moe.antimony.hoshi.features.reader.input.ReaderKeyBindings
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
            KeyEvent.KEYCODE_COMMA to ReaderHardwareKeyAction.SasayakiSpeedDown,
            KeyEvent.KEYCODE_PERIOD to ReaderHardwareKeyAction.SasayakiSpeedUp,
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
    fun playKeysDecideTapVersusHoldOnReleaseWhenHoldToBoostIsOn() {
        val settings = ReaderSettings()
        fun action(action: Int, repeatCount: Int) = readerHardwareKeyEventForKeyEvent(
            keyCode = KeyEvent.KEYCODE_SPACE,
            action = action,
            repeatCount = repeatCount,
            settings = settings,
            sasayakiEnabled = true,
            hasSasayakiAudio = true,
            sasayakiHoldToBoost = true,
        )

        val down = action(KeyEvent.ACTION_DOWN, 0)
        assertTrue(down.consumed)
        assertNull(down.action)
        assertEquals(ReaderHardwareKeyAction.SasayakiHoldBoostStart, action(KeyEvent.ACTION_DOWN, 1).action)
        assertNull(action(KeyEvent.ACTION_DOWN, 2).action)
        assertEquals(ReaderHardwareKeyAction.SasayakiPlayKeyReleased, action(KeyEvent.ACTION_UP, 0).action)
        // Seek keys are unaffected by the hold setting.
        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekForward,
            readerHardwareKeyActionForKeyEvent(
                keyCode = KeyEvent.KEYCODE_L,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = settings,
                sasayakiEnabled = true,
                hasSasayakiAudio = true,
                sasayakiHoldToBoost = true,
            ),
        )
    }

    @Test
    fun volumeKeyHoldBoostsAfterTheTapActionFiredOnce() {
        val settings = ReaderSettings(volumeKeysTurnPages = true)
        fun event(action: Int, repeatCount: Int) = readerHardwareKeyEventForKeyEvent(
            keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
            action = action,
            repeatCount = repeatCount,
            settings = settings,
            sasayakiEnabled = true,
            hasSasayakiAudio = true,
            volumeKeysHoldToBoost = true,
        )

        assertEquals(
            ReaderHardwareKeyAction.ReaderNavigation(ReaderNavigationDirection.Forward),
            event(KeyEvent.ACTION_DOWN, 0).action,
        )
        assertEquals(ReaderHardwareKeyAction.SasayakiHoldBoostStart, event(KeyEvent.ACTION_DOWN, 1).action)
        assertNull(event(KeyEvent.ACTION_DOWN, 2).action)
        assertTrue(event(KeyEvent.ACTION_DOWN, 2).consumed)
        assertEquals(ReaderHardwareKeyAction.SasayakiVolumeKeyReleased, event(KeyEvent.ACTION_UP, 0).action)

        // With no tap action bound the key is still held for boost, and nothing fires on the first press.
        val boostOnly = readerHardwareKeyEventForKeyEvent(
            keyCode = KeyEvent.KEYCODE_VOLUME_UP,
            action = KeyEvent.ACTION_DOWN,
            repeatCount = 0,
            settings = ReaderSettings(),
            sasayakiEnabled = true,
            hasSasayakiAudio = true,
            volumeKeysHoldToBoost = true,
        )
        assertTrue(boostOnly.consumed)
        assertNull(boostOnly.action)
        // Without audio the volume key falls back to the system.
        assertFalse(
            readerHardwareKeyEventForKeyEvent(
                keyCode = KeyEvent.KEYCODE_VOLUME_UP,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                settings = ReaderSettings(),
                sasayakiEnabled = true,
                hasSasayakiAudio = false,
                volumeKeysHoldToBoost = true,
            ).consumed,
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

    @Test
    fun aReboundKeyFiresAndItsOldKeyGoesBackToThePage() {
        val bindings = ReaderKeyBindings().withKeys(ReaderKeyAction.PageForward, listOf(ReaderKey(KeyEvent.KEYCODE_N)))

        assertEquals(
            ReaderHardwareKeyAction.ReaderNavigation(ReaderNavigationDirection.Forward),
            boundKeyEvent(KeyEvent.KEYCODE_N, keyBindings = bindings).action,
        )
        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_PAGE_DOWN, keyBindings = bindings).consumed)
    }

    @Test
    fun gamepadButtonsTurnPagesAndPlayByDefault() {
        assertEquals(
            ReaderHardwareKeyAction.ReaderNavigation(ReaderNavigationDirection.Forward),
            boundKeyEvent(KeyEvent.KEYCODE_BUTTON_R1).action,
        )
        assertEquals(
            ReaderHardwareKeyAction.ReaderNavigation(ReaderNavigationDirection.Backward),
            boundKeyEvent(KeyEvent.KEYCODE_BUTTON_L1).action,
        )
        assertEquals(ReaderHardwareKeyAction.ToggleFocusMode, boundKeyEvent(KeyEvent.KEYCODE_BUTTON_SELECT).action)
        assertEquals(
            ReaderHardwareKeyAction.SasayakiTogglePlayback,
            boundKeyEvent(KeyEvent.KEYCODE_BUTTON_A, audioLoaded = true).action,
        )
        // With nothing of its own to do, a button is left to Android: A clicks, B goes back.
        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_BUTTON_A).consumed)
        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_BUTTON_B, audioLoaded = true).consumed)
    }

    @Test
    fun popupKeysOnlyWorkWhileAPopupIsOpen() {
        assertEquals(
            ReaderHardwareKeyAction.CloseLookupPopup,
            boundKeyEvent(KeyEvent.KEYCODE_ESCAPE, hasLookupPopup = true).action,
        )
        assertEquals(
            ReaderHardwareKeyAction.CloseLookupPopup,
            boundKeyEvent(KeyEvent.KEYCODE_BUTTON_B, hasLookupPopup = true).action,
        )
        assertEquals(
            ReaderHardwareKeyAction.PopupTermNavigation(PopupTermNavigationDirection.Previous),
            boundKeyEvent(KeyEvent.KEYCODE_DPAD_UP, hasLookupPopup = true).action,
        )
        assertEquals(
            ReaderHardwareKeyAction.PopupTermNavigation(PopupTermNavigationDirection.Next),
            boundKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, hasLookupPopup = true, repeatCount = 3).action,
        )
        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_ESCAPE).consumed)
        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_DPAD_UP, audioLoaded = true).consumed)
    }

    @Test
    fun aKeyFallsBackToItsReadingActionWithoutAudio() {
        val bindings = ReaderKeyBindings()
            .withKeys(ReaderKeyAction.PageForward, listOf(ReaderKey(KeyEvent.KEYCODE_DPAD_RIGHT)))

        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekForward,
            boundKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, audioLoaded = true, keyBindings = bindings).action,
        )
        assertEquals(
            ReaderHardwareKeyAction.ReaderNavigation(ReaderNavigationDirection.Forward),
            boundKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, keyBindings = bindings).action,
        )
    }

    @Test
    fun chapterKeysFireOncePerPress() {
        val bindings = ReaderKeyBindings()
            .withKeys(ReaderKeyAction.ChapterForward, listOf(ReaderKey(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_CTRL_ON)))

        assertEquals(
            ReaderHardwareKeyAction.ChapterNavigation(ReaderNavigationDirection.Forward),
            boundKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, metaState = KeyEvent.META_CTRL_ON, keyBindings = bindings).action,
        )
        val repeat = boundKeyEvent(
            KeyEvent.KEYCODE_DPAD_RIGHT,
            repeatCount = 1,
            metaState = KeyEvent.META_CTRL_ON,
            keyBindings = bindings,
        )
        assertTrue(repeat.consumed)
        assertNull(repeat.action)
    }

    @Test
    fun nothingIsBoundWhileTyping() {
        listOf(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_SPACE).forEach { keyCode ->
            assertFalse(
                boundKeyEvent(keyCode, audioLoaded = true, hasLookupPopup = true, textEditorFocused = true).consumed,
            )
        }
    }

    @Test
    fun aBoundKeyIsConsumedDownToItsRelease() {
        val up = boundKeyEvent(KeyEvent.KEYCODE_PAGE_DOWN, action = KeyEvent.ACTION_UP)

        assertTrue(up.consumed)
        assertNull(up.action)
    }

    @Test
    fun theBoostKeyBoostsFromPressToRelease() {
        val bindings = ReaderKeyBindings().withKeys(ReaderKeyAction.BoostWhileHeld, listOf(ReaderKey(KeyEvent.KEYCODE_B)))
        fun event(action: Int, repeatCount: Int) =
            boundKeyEvent(KeyEvent.KEYCODE_B, action = action, repeatCount = repeatCount, audioLoaded = true, keyBindings = bindings)

        assertEquals(ReaderHardwareKeyAction.SasayakiHoldBoostStart, event(KeyEvent.ACTION_DOWN, 0).action)
        assertNull(event(KeyEvent.ACTION_DOWN, 1).action)
        assertTrue(event(KeyEvent.ACTION_DOWN, 1).consumed)
        assertEquals(ReaderHardwareKeyAction.SasayakiVolumeKeyReleased, event(KeyEvent.ACTION_UP, 0).action)
        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_B, keyBindings = bindings).consumed)
    }

    @Test
    fun holdToBoostFollowsWhicheverKeyPlays() {
        val bindings = ReaderKeyBindings().withKeys(ReaderKeyAction.TogglePlayback, listOf(ReaderKey(KeyEvent.KEYCODE_P)))
        fun event(keyCode: Int, action: Int, repeatCount: Int) = boundKeyEvent(
            keyCode,
            action = action,
            repeatCount = repeatCount,
            audioLoaded = true,
            sasayakiHoldToBoost = true,
            keyBindings = bindings,
        )

        assertNull(event(KeyEvent.KEYCODE_P, KeyEvent.ACTION_DOWN, 0).action)
        assertEquals(ReaderHardwareKeyAction.SasayakiHoldBoostStart, event(KeyEvent.KEYCODE_P, KeyEvent.ACTION_DOWN, 1).action)
        assertEquals(ReaderHardwareKeyAction.SasayakiPlayKeyReleased, event(KeyEvent.KEYCODE_P, KeyEvent.ACTION_UP, 0).action)
        assertFalse(event(KeyEvent.KEYCODE_SPACE, KeyEvent.ACTION_DOWN, 0).consumed)
    }

    @Test
    fun aPressKeepsItsActionWhenThePopupClosesUnderIt() {
        // One key closes the popup and plays: closing the popup must not also toggle playback on release.
        val key = ReaderKey(KeyEvent.KEYCODE_BUTTON_A)
        val bindings = ReaderKeyBindings().withKeys(ReaderKeyAction.ClosePopup, listOf(key))
        val presses = ReaderKeyPresses()
        fun event(action: Int, hasLookupPopup: Boolean) = boundKeyEvent(
            KeyEvent.KEYCODE_BUTTON_A,
            action = action,
            audioLoaded = true,
            hasLookupPopup = hasLookupPopup,
            sasayakiHoldToBoost = true,
            keyBindings = bindings,
            presses = presses,
        )

        assertEquals(ReaderHardwareKeyAction.CloseLookupPopup, event(KeyEvent.ACTION_DOWN, hasLookupPopup = true).action)
        val release = event(KeyEvent.ACTION_UP, hasLookupPopup = false)
        assertTrue(release.consumed)
        assertNull(release.action)

        // The next press, with no popup, plays as usual.
        assertNull(event(KeyEvent.ACTION_DOWN, hasLookupPopup = false).action)
        assertEquals(ReaderHardwareKeyAction.SasayakiPlayKeyReleased, event(KeyEvent.ACTION_UP, hasLookupPopup = false).action)
    }

    @Test
    fun aReleaseWithNoPressBehindItGoesBackToThePage() {
        val presses = ReaderKeyPresses()

        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_PAGE_DOWN, action = KeyEvent.ACTION_UP, presses = presses).consumed)
        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_PAGE_DOWN, repeatCount = 2, presses = presses).consumed)
        assertTrue(boundKeyEvent(KeyEvent.KEYCODE_PAGE_DOWN, presses = presses).consumed)
        assertTrue(boundKeyEvent(KeyEvent.KEYCODE_PAGE_DOWN, action = KeyEvent.ACTION_UP, presses = presses).consumed)
        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_PAGE_DOWN, action = KeyEvent.ACTION_UP, presses = presses).consumed)
    }

    @Test
    fun volumeKeysKeepTheirSwitchesWhateverIsBound() {
        val bindings = ReaderKeyBindings(
            mapOf(ReaderKeyAction.PageForward to listOf(ReaderKey(KeyEvent.KEYCODE_VOLUME_DOWN))),
        )

        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN, keyBindings = bindings).consumed)
    }

    private fun boundKeyEvent(
        keyCode: Int,
        action: Int = KeyEvent.ACTION_DOWN,
        repeatCount: Int = 0,
        metaState: Int = 0,
        audioLoaded: Boolean = false,
        hasLookupPopup: Boolean = false,
        textEditorFocused: Boolean = false,
        sasayakiHoldToBoost: Boolean = false,
        keyBindings: ReaderKeyBindings = ReaderKeyBindings(),
        presses: ReaderKeyPresses? = null,
    ) = readerHardwareKeyEventForKeyEvent(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        settings = ReaderSettings(volumeKeysTurnPages = false),
        sasayakiEnabled = audioLoaded,
        hasSasayakiAudio = audioLoaded,
        textEditorFocused = textEditorFocused,
        hasLookupPopup = hasLookupPopup,
        sasayakiHoldToBoost = sasayakiHoldToBoost,
        metaState = metaState,
        keyBindings = keyBindings,
        presses = presses,
    )

    private fun sasayakiKeyboardKeyCodes() = listOf(
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_K,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_J,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_L,
        KeyEvent.KEYCODE_COMMA,
        KeyEvent.KEYCODE_PERIOD,
    )
}
