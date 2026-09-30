package moe.antimony.hoshi.features.reader.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderKeyBindingsTest {
    private val left = ReaderKey(KeyEvent.KEYCODE_DPAD_LEFT)
    private val space = ReaderKey(KeyEvent.KEYCODE_SPACE)

    @Test
    fun defaultsNeverRepeatAKeyWithinALayer() {
        ReaderKeyLayer.entries.forEach { layer ->
            val keys = ReaderKeyBindings.Defaults.filterKeys { it.layer == layer }.values.flatten()
            assertEquals(keys.distinct(), keys)
        }
    }

    @Test
    fun defaultsOnlyHoldKeysThatCanBeBound() {
        ReaderKeyBindings.Defaults.values.flatten().forEach { key ->
            assertEquals(key, readerKeyOrNull(key.keyCode, key.modifiers))
        }
    }

    @Test
    fun theFirstActiveLayerHoldingTheKeyWins() {
        val bindings = ReaderKeyBindings()
            .withKeys(ReaderKeyAction.PageForward, listOf(left))
            .withKeys(ReaderKeyAction.ClosePopup, listOf(left))

        assertEquals(ReaderKeyAction.ClosePopup, bindings.actionFor(left, popupOpen = true, audioLoaded = true))
        assertEquals(ReaderKeyAction.SkipBackward, bindings.actionFor(left, popupOpen = false, audioLoaded = true))
        assertEquals(ReaderKeyAction.PageForward, bindings.actionFor(left, popupOpen = false, audioLoaded = false))
    }

    @Test
    fun aKeyWithNoActionInAnActiveLayerIsUnbound() {
        val bindings = ReaderKeyBindings()

        assertNull(bindings.actionFor(space, popupOpen = false, audioLoaded = false))
        assertNull(bindings.actionFor(ReaderKey(KeyEvent.KEYCODE_ESCAPE), popupOpen = false, audioLoaded = true))
        assertNull(bindings.actionFor(ReaderKey(KeyEvent.KEYCODE_Q), popupOpen = true, audioLoaded = true))
    }

    @Test
    fun modifiersHaveToMatch() {
        val ctrlRight = ReaderKey(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_CTRL_ON)
        val bindings = ReaderKeyBindings().withKeys(ReaderKeyAction.ChapterForward, listOf(ctrlRight))

        assertEquals(ReaderKeyAction.ChapterForward, bindings.actionFor(ctrlRight, popupOpen = false, audioLoaded = true))
        assertEquals(
            ReaderKeyAction.SkipForward,
            bindings.actionFor(ReaderKey(KeyEvent.KEYCODE_DPAD_RIGHT), popupOpen = false, audioLoaded = true),
        )
        assertNull(bindings.actionFor(ReaderKey(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON), popupOpen = false, audioLoaded = true))
    }

    @Test
    fun lessThanAndGreaterThanStillChangeTheSpeed() {
        val bindings = ReaderKeyBindings()
        val less = ReaderKey(KeyEvent.KEYCODE_COMMA, KeyEvent.META_SHIFT_ON)
        val greater = ReaderKey(KeyEvent.KEYCODE_PERIOD, KeyEvent.META_SHIFT_ON)

        assertEquals(ReaderKeyAction.SpeedDown, bindings.actionFor(less, popupOpen = false, audioLoaded = true))
        assertEquals(ReaderKeyAction.SpeedUp, bindings.actionFor(greater, popupOpen = false, audioLoaded = true))
    }

    @Test
    fun aKeyIsTakenFromTheActionThatHadItInTheSameLayerOnly() {
        val bindings = ReaderKeyBindings().withKeys(ReaderKeyAction.TogglePlayback, listOf(space, left))

        assertEquals(listOf(space, left), bindings.keys(ReaderKeyAction.TogglePlayback))
        assertEquals(listOf(ReaderKey(KeyEvent.KEYCODE_J)), bindings.keys(ReaderKeyAction.SkipBackward))

        val otherLayer = ReaderKeyBindings().withKeys(ReaderKeyAction.PageBackward, listOf(left))
        assertEquals(listOf(left), otherLayer.keys(ReaderKeyAction.PageBackward))
        assertTrue(left in otherLayer.keys(ReaderKeyAction.SkipBackward))
    }

    @Test
    fun aTakenKeyDoesNotGoBackWhenItIsLetGoOfAgain() {
        val bindings = ReaderKeyBindings()
            .withKeys(ReaderKeyAction.TogglePlayback, listOf(space, left))
            .withKeys(ReaderKeyAction.TogglePlayback, listOf(space))

        assertFalse(left in bindings.keys(ReaderKeyAction.SkipBackward))
        assertNull(bindings.actionFor(left, popupOpen = false, audioLoaded = true))
    }

    @Test
    fun anActionCanBeLeftWithNoKeys() {
        val bindings = ReaderKeyBindings().withKeys(ReaderKeyAction.PageForward, emptyList())

        assertEquals(emptyList<ReaderKey>(), bindings.keys(ReaderKeyAction.PageForward))
        assertNull(bindings.actionFor(ReaderKey(KeyEvent.KEYCODE_PAGE_DOWN), popupOpen = false, audioLoaded = false))
    }

    @Test
    fun aRowSavedAsItsDefaultIsNotStored() {
        val defaults = ReaderKeyBindings.Defaults.getValue(ReaderKeyAction.TogglePlayback)
        val changed = ReaderKeyBindings().withKeys(ReaderKeyAction.TogglePlayback, listOf(space))

        assertFalse(changed.map.isEmpty())
        assertEquals(ReaderKeyBindings(), changed.withKeys(ReaderKeyAction.TogglePlayback, defaults))
        assertEquals(ReaderKeyBindings(), ReaderKeyBindings().withKeys(ReaderKeyAction.ChapterForward, emptyList()))
    }

    @Test
    fun aDefaultKeyGivesWayToASavedOne() {
        // As when a later build gives an action a default key that someone already uses for something else.
        val bindings = ReaderKeyBindings(mapOf(ReaderKeyAction.SpeedUp to listOf(space)))

        assertEquals(ReaderKeyAction.SpeedUp, bindings.actionFor(space, popupOpen = false, audioLoaded = true))
        assertFalse(space in bindings.keys(ReaderKeyAction.TogglePlayback))
        assertTrue(ReaderKey(KeyEvent.KEYCODE_K) in bindings.keys(ReaderKeyAction.TogglePlayback))
    }

    @Test
    fun encodeDecodeRoundTripsAndDropsGarbage() {
        val bindings = ReaderKeyBindings()
            .withKeys(ReaderKeyAction.ChapterForward, listOf(ReaderKey(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_CTRL_ON)))
            .withKeys(ReaderKeyAction.PageForward, emptyList())
        assertEquals(bindings, ReaderKeyBindings.decode(bindings.encode()))

        val messy = ReaderKeyBindings.decode(
            """{"Unknown":[{"keyCode":62}],"PageForward":[{"keyCode":24},{"keyCode":4},{"keyCode":62,"extra":1}]}""",
        )
        assertEquals(mapOf(ReaderKeyAction.PageForward to listOf(space)), messy.map)
        assertEquals(ReaderKeyBindings(), ReaderKeyBindings.decode("not json"))
    }

    @Test
    fun modifiersBackAndVolumeKeysCannotBeBound() {
        assertNull(readerKeyOrNull(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_ON))
        assertNull(readerKeyOrNull(KeyEvent.KEYCODE_BACK, 0))
        assertNull(readerKeyOrNull(KeyEvent.KEYCODE_VOLUME_UP, 0))
        assertNull(readerKeyOrNull(KeyEvent.KEYCODE_VOLUME_DOWN, 0))
        assertEquals(ReaderKey(KeyEvent.KEYCODE_BUTTON_B), readerKeyOrNull(KeyEvent.KEYCODE_BUTTON_B, 0))
    }

    @Test
    fun lockKeysDoNotCountAsModifiers() {
        assertEquals(
            ReaderKey(KeyEvent.KEYCODE_J, KeyEvent.META_SHIFT_ON),
            readerKeyOrNull(
                KeyEvent.KEYCODE_J,
                KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_CAPS_LOCK_ON or KeyEvent.META_NUM_LOCK_ON,
            ),
        )
    }

    @Test
    fun keysAreNamedForAKeyCap() {
        assertEquals("←", readerKeyCodeLabel("KEYCODE_DPAD_LEFT"))
        assertEquals("Esc", readerKeyCodeLabel("KEYCODE_ESCAPE"))
        assertEquals("Page Down", readerKeyCodeLabel("KEYCODE_PAGE_DOWN"))
        assertEquals("R1", readerKeyCodeLabel("KEYCODE_BUTTON_R1"))
        assertEquals("Select", readerKeyCodeLabel("KEYCODE_BUTTON_SELECT"))
        assertEquals("D-pad Center", readerKeyCodeLabel("KEYCODE_DPAD_CENTER"))
        assertEquals(
            "Ctrl + Shift + ,",
            readerKeyLabel(ReaderKey(KeyEvent.KEYCODE_COMMA, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON), "KEYCODE_COMMA"),
        )
        assertTrue(isReaderGamepadButton("KEYCODE_BUTTON_A"))
        assertFalse(isReaderGamepadButton("KEYCODE_A"))
    }
}
