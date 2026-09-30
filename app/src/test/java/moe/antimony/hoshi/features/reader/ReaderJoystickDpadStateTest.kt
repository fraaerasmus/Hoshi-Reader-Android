package moe.antimony.hoshi.features.reader

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderJoystickDpadStateTest {
    @Test
    fun aDirectionGoesDownOnceAndUpWhenTheHatCentres() {
        val state = ReaderJoystickDpadState()

        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_LEFT) to emptyList<Int>(), state.update(-1f, 0f))
        assertEquals(emptyList<Int>() to emptyList<Int>(), state.update(-1f, 0f))
        assertEquals(emptyList<Int>() to listOf(KeyEvent.KEYCODE_DPAD_LEFT), state.update(0f, 0f))
    }

    @Test
    fun aDiagonalHoldsTwoKeysAndRollingOffSwapsThem() {
        val state = ReaderJoystickDpadState()

        val (down, up) = state.update(1f, 1f)
        assertEquals(setOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN), down.toSet())
        assertEquals(emptyList<Int>(), up)
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_UP) to listOf(KeyEvent.KEYCODE_DPAD_DOWN), state.update(1f, -1f))
    }

    @Test
    fun aStickInsideTheDeadZoneIsCentred() {
        val state = ReaderJoystickDpadState()

        assertEquals(emptyList<Int>() to emptyList<Int>(), state.update(0.3f, -0.49f))
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_UP) to emptyList<Int>(), state.update(0.3f, -0.5f))
    }

    @Test
    fun clearReleasesWhatIsHeld() {
        val state = ReaderJoystickDpadState()
        state.update(-1f, 1f)

        assertEquals(setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN), state.clear().toSet())
        assertEquals(emptySet<Int>(), state.pressed)
    }
}
