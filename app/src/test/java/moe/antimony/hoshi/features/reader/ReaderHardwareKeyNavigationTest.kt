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
    private val forward = ReaderHardwareKeyAction.ReaderNavigation(ReaderNavigationDirection.Forward)
    private val backward = ReaderHardwareKeyAction.ReaderNavigation(ReaderNavigationDirection.Backward)

    /** The rows the four volume-key switches used to stand for, all on, default direction. */
    private val legacyVolumeKeys = ReaderKeyBindings.fromLegacyVolumeKeys(
        turnPages = true,
        navigatePopupTerms = true,
        seekSasayaki = true,
        reverseDirection = false,
    )

    @Test
    fun pageDownAndPageUpTurnPagesOncePerPress() {
        assertEquals(forward, boundKeyEvent(KeyEvent.KEYCODE_PAGE_DOWN).action)
        assertEquals(backward, boundKeyEvent(KeyEvent.KEYCODE_PAGE_UP).action)
        assertNull(boundKeyEvent(KeyEvent.KEYCODE_PAGE_DOWN, action = KeyEvent.ACTION_UP).action)
        assertNull(boundKeyEvent(KeyEvent.KEYCODE_PAGE_DOWN, repeatCount = 1).action)
    }

    @Test
    fun volumeKeysAreLeftToTheSystemUntilBound() {
        listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN).forEach { keyCode ->
            assertFalse(boundKeyEvent(keyCode).consumed)
            assertFalse(boundKeyEvent(keyCode, audioLoaded = true, hasLookupPopup = true).consumed)
        }
    }

    @Test
    fun legacyVolumeSwitchesBecomeRowsInTheirLayers() {
        val bindings = legacyVolumeKeys

        assertEquals(backward, boundKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, keyBindings = bindings).action)
        assertEquals(forward, boundKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN, keyBindings = bindings).action)
        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekBackward,
            boundKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, audioLoaded = true, keyBindings = bindings).action,
        )
        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekForward,
            boundKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN, audioLoaded = true, keyBindings = bindings).action,
        )
        assertEquals(
            ReaderHardwareKeyAction.PopupTermNavigation(PopupTermNavigationDirection.Previous),
            boundKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, audioLoaded = true, hasLookupPopup = true, keyBindings = bindings).action,
        )
        assertEquals(
            ReaderHardwareKeyAction.PopupTermNavigation(PopupTermNavigationDirection.Next),
            boundKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN, audioLoaded = true, hasLookupPopup = true, keyBindings = bindings).action,
        )
    }

    @Test
    fun legacyReverseDirectionIsTheSwappedRows() {
        val bindings = ReaderKeyBindings.fromLegacyVolumeKeys(
            turnPages = true,
            navigatePopupTerms = false,
            seekSasayaki = true,
            reverseDirection = true,
        )

        assertEquals(forward, boundKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, keyBindings = bindings).action)
        assertEquals(backward, boundKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN, keyBindings = bindings).action)
        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekForward,
            boundKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, audioLoaded = true, keyBindings = bindings).action,
        )
        // Popup terms were off, so with a popup open the keys fall through to the seek rows.
        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekBackward,
            boundKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN, audioLoaded = true, hasLookupPopup = true, keyBindings = bindings).action,
        )
    }

    @Test
    fun boundVolumeKeysRepeatSeeksAndTermsButNotPages() {
        val bindings = legacyVolumeKeys

        assertNull(boundKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 2, keyBindings = bindings).action)
        assertTrue(boundKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 2, keyBindings = bindings).consumed)
        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekForward,
            boundKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 2, audioLoaded = true, keyBindings = bindings).action,
        )
        assertEquals(
            ReaderHardwareKeyAction.PopupTermNavigation(PopupTermNavigationDirection.Next),
            boundKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 2, hasLookupPopup = true, keyBindings = bindings).action,
        )
        val up = boundKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN, action = KeyEvent.ACTION_UP, keyBindings = bindings)
        assertTrue(up.consumed)
        assertNull(up.action)
    }

    @Test
    fun volumeKeyHoldBoostsAfterTheTapActionFiredOnce() {
        fun event(action: Int, repeatCount: Int) = boundKeyEvent(
            KeyEvent.KEYCODE_VOLUME_DOWN,
            action = action,
            repeatCount = repeatCount,
            audioLoaded = true,
            volumeKeysHoldToBoost = true,
            keyBindings = ReaderKeyBindings.fromLegacyVolumeKeys(turnPages = true, navigatePopupTerms = false, seekSasayaki = false, reverseDirection = false),
        )

        assertEquals(forward, event(KeyEvent.ACTION_DOWN, 0).action)
        assertEquals(ReaderHardwareKeyAction.SasayakiHoldBoostStart, event(KeyEvent.ACTION_DOWN, 1).action)
        assertNull(event(KeyEvent.ACTION_DOWN, 2).action)
        assertTrue(event(KeyEvent.ACTION_DOWN, 2).consumed)
        assertEquals(ReaderHardwareKeyAction.SasayakiVolumeKeyReleased, event(KeyEvent.ACTION_UP, 0).action)

        // With no tap action bound the key is still held for boost, and nothing fires on the first press.
        val boostOnly = boundKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, audioLoaded = true, volumeKeysHoldToBoost = true)
        assertTrue(boostOnly.consumed)
        assertNull(boostOnly.action)
        // Without audio the volume key falls back to the system.
        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, volumeKeysHoldToBoost = true).consumed)
    }

    @Test
    fun sasayakiKeyboardKeysControlPlaybackWhenAudioLoaded() {
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
            assertEquals(expected, boundKeyEvent(keyCode, audioLoaded = true).action)
        }
    }

    @Test
    fun sasayakiKeyboardKeysAreIgnoredWithoutLoadedAudio() {
        sasayakiKeyboardKeyCodes().forEach { keyCode ->
            assertFalse(boundKeyEvent(keyCode).consumed)
            assertFalse(
                readerHardwareKeyEventForKeyEvent(
                    keyCode = keyCode,
                    action = KeyEvent.ACTION_DOWN,
                    repeatCount = 0,
                    sasayakiEnabled = false,
                    hasSasayakiAudio = true,
                ).consumed,
            )
        }
    }

    @Test
    fun sasayakiKeyboardKeysIgnoreKeyUpAndToggleRepeats() {
        // Key-up never triggers an action, for toggle or seek keys.
        assertNull(boundKeyEvent(KeyEvent.KEYCODE_SPACE, action = KeyEvent.ACTION_UP, audioLoaded = true).action)
        assertNull(boundKeyEvent(KeyEvent.KEYCODE_J, action = KeyEvent.ACTION_UP, audioLoaded = true).action)
        // Play/pause toggle does not auto-repeat while held (seek keys do).
        assertNull(boundKeyEvent(KeyEvent.KEYCODE_SPACE, repeatCount = 1, audioLoaded = true).action)
        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekBackward,
            boundKeyEvent(KeyEvent.KEYCODE_J, repeatCount = 1, audioLoaded = true).action,
        )
    }

    @Test
    fun playKeysDecideTapVersusHoldOnReleaseWhenHoldToBoostIsOn() {
        fun action(action: Int, repeatCount: Int) =
            boundKeyEvent(KeyEvent.KEYCODE_SPACE, action = action, repeatCount = repeatCount, audioLoaded = true, sasayakiHoldToBoost = true)

        val down = action(KeyEvent.ACTION_DOWN, 0)
        assertTrue(down.consumed)
        assertNull(down.action)
        assertEquals(ReaderHardwareKeyAction.SasayakiHoldBoostStart, action(KeyEvent.ACTION_DOWN, 1).action)
        assertNull(action(KeyEvent.ACTION_DOWN, 2).action)
        assertEquals(ReaderHardwareKeyAction.SasayakiPlayKeyReleased, action(KeyEvent.ACTION_UP, 0).action)
        // Seek keys are unaffected by the hold setting.
        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekForward,
            boundKeyEvent(KeyEvent.KEYCODE_L, audioLoaded = true, sasayakiHoldToBoost = true).action,
        )
    }

    @Test
    fun aReboundKeyFiresAndItsOldKeyGoesBackToThePage() {
        val bindings = ReaderKeyBindings().withKeys(ReaderKeyAction.PageForward, listOf(ReaderKey(KeyEvent.KEYCODE_N)))

        assertEquals(forward, boundKeyEvent(KeyEvent.KEYCODE_N, keyBindings = bindings).action)
        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_PAGE_DOWN, keyBindings = bindings).consumed)
    }

    @Test
    fun gamepadButtonsTurnPagesAndPlayByDefault() {
        assertEquals(forward, boundKeyEvent(KeyEvent.KEYCODE_BUTTON_R1).action)
        assertEquals(backward, boundKeyEvent(KeyEvent.KEYCODE_BUTTON_L1).action)
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
        assertEquals(ReaderHardwareKeyAction.CloseLookupPopup, boundKeyEvent(KeyEvent.KEYCODE_ESCAPE, hasLookupPopup = true).action)
        assertEquals(ReaderHardwareKeyAction.CloseLookupPopup, boundKeyEvent(KeyEvent.KEYCODE_BUTTON_B, hasLookupPopup = true).action)
        assertEquals(
            ReaderHardwareKeyAction.PopupTermNavigation(PopupTermNavigationDirection.Previous),
            boundKeyEvent(KeyEvent.KEYCODE_DPAD_UP, hasLookupPopup = true).action,
        )
        assertEquals(
            ReaderHardwareKeyAction.PopupTermNavigation(PopupTermNavigationDirection.Next),
            boundKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, hasLookupPopup = true, repeatCount = 3).action,
        )
        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_ESCAPE).consumed)
        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_DPAD_UP).consumed)
    }

    @Test
    fun upAndDownChangeTheVolumeWhileAudioIsLoadedAndNoPopupIsOpen() {
        assertEquals(ReaderHardwareKeyAction.AdjustVolume(up = true), boundKeyEvent(KeyEvent.KEYCODE_DPAD_UP, audioLoaded = true).action)
        assertEquals(
            ReaderHardwareKeyAction.AdjustVolume(up = false),
            boundKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, audioLoaded = true, repeatCount = 2).action,
        )
        assertEquals(
            ReaderHardwareKeyAction.PopupTermNavigation(PopupTermNavigationDirection.Previous),
            boundKeyEvent(KeyEvent.KEYCODE_DPAD_UP, audioLoaded = true, hasLookupPopup = true).action,
        )
        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN).consumed)
    }

    @Test
    fun gamepadButtonsWorkThePopupWhileItIsOpenAndTurnPagesOtherwise() {
        assertEquals(ReaderHardwareKeyAction.MineTopTerm, boundKeyEvent(KeyEvent.KEYCODE_BUTTON_X, hasLookupPopup = true).action)
        assertEquals(ReaderHardwareKeyAction.PlayTopTermAudio, boundKeyEvent(KeyEvent.KEYCODE_BUTTON_Y, hasLookupPopup = true).action)
        assertEquals(ReaderHardwareKeyAction.PopupScroll(down = false), boundKeyEvent(KeyEvent.KEYCODE_BUTTON_L1, hasLookupPopup = true).action)
        assertEquals(
            ReaderHardwareKeyAction.PopupScroll(down = true),
            boundKeyEvent(KeyEvent.KEYCODE_BUTTON_R1, hasLookupPopup = true, repeatCount = 2).action,
        )
        assertEquals(forward, boundKeyEvent(KeyEvent.KEYCODE_BUTTON_R1).action)
        assertEquals(ReaderHardwareKeyAction.PickWord, boundKeyEvent(KeyEvent.KEYCODE_BUTTON_X).action)
        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_BUTTON_Y).consumed)
    }

    @Test
    fun theArrowsWalkWordsInAPopupAndSeekOutsideOne() {
        assertEquals(ReaderHardwareKeyAction.PickWord, boundKeyEvent(KeyEvent.KEYCODE_ENTER).action)
        assertEquals(ReaderHardwareKeyAction.PickWord, boundKeyEvent(KeyEvent.KEYCODE_BUTTON_X, audioLoaded = true).action)
        assertEquals(
            ReaderHardwareKeyAction.WordStep(forward = true),
            boundKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, audioLoaded = true, hasLookupPopup = true, repeatCount = 1).action,
        )
        assertEquals(
            ReaderHardwareKeyAction.WordStep(forward = false),
            boundKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, hasLookupPopup = true).action,
        )
        assertEquals(ReaderHardwareKeyAction.SasayakiSeekForward, boundKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, audioLoaded = true).action)
        assertEquals(
            ReaderHardwareKeyAction.SentenceStep(forward = true),
            boundKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, metaState = KeyEvent.META_SHIFT_ON, hasLookupPopup = true).action,
        )
        assertEquals(ReaderHardwareKeyAction.SentenceStep(forward = false), boundKeyEvent(KeyEvent.KEYCODE_BUTTON_L2, hasLookupPopup = true).action)
        assertFalse(boundKeyEvent(KeyEvent.KEYCODE_BUTTON_L2).consumed)
    }

    @Test
    fun aKeyFallsBackToItsReadingActionWithoutAudio() {
        val bindings = ReaderKeyBindings().withKeys(ReaderKeyAction.PageForward, listOf(ReaderKey(KeyEvent.KEYCODE_DPAD_RIGHT)))

        assertEquals(
            ReaderHardwareKeyAction.SasayakiSeekForward,
            boundKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, audioLoaded = true, keyBindings = bindings).action,
        )
        assertEquals(forward, boundKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, keyBindings = bindings).action)
    }

    @Test
    fun chapterKeysFireOncePerPress() {
        val bindings = ReaderKeyBindings()
            .withKeys(ReaderKeyAction.ChapterForward, listOf(ReaderKey(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_CTRL_ON)))

        assertEquals(
            ReaderHardwareKeyAction.ChapterNavigation(ReaderNavigationDirection.Forward),
            boundKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, metaState = KeyEvent.META_CTRL_ON, keyBindings = bindings).action,
        )
        val repeat = boundKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, repeatCount = 1, metaState = KeyEvent.META_CTRL_ON, keyBindings = bindings)
        assertTrue(repeat.consumed)
        assertNull(repeat.action)
    }

    @Test
    fun nothingIsBoundWhileTyping() {
        listOf(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_VOLUME_UP)
            .forEach { keyCode ->
                assertFalse(
                    boundKeyEvent(keyCode, audioLoaded = true, hasLookupPopup = true, textEditorFocused = true, keyBindings = legacyVolumeKeys).consumed,
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
        val bindings = ReaderKeyBindings().withKeys(ReaderKeyAction.ClosePopup, listOf(ReaderKey(KeyEvent.KEYCODE_BUTTON_A)))
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

    private fun boundKeyEvent(
        keyCode: Int,
        action: Int = KeyEvent.ACTION_DOWN,
        repeatCount: Int = 0,
        metaState: Int = 0,
        audioLoaded: Boolean = false,
        hasLookupPopup: Boolean = false,
        textEditorFocused: Boolean = false,
        sasayakiHoldToBoost: Boolean = false,
        volumeKeysHoldToBoost: Boolean = false,
        keyBindings: ReaderKeyBindings = ReaderKeyBindings(),
        presses: ReaderKeyPresses? = null,
    ) = readerHardwareKeyEventForKeyEvent(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        sasayakiEnabled = audioLoaded,
        hasSasayakiAudio = audioLoaded,
        textEditorFocused = textEditorFocused,
        hasLookupPopup = hasLookupPopup,
        sasayakiHoldToBoost = sasayakiHoldToBoost,
        volumeKeysHoldToBoost = volumeKeysHoldToBoost,
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
