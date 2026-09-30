package moe.antimony.hoshi.features.reader

import android.os.Handler
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration

/**
 * Which D-pad keys a controller's hat and left stick stand for. Pure, so the
 * transitions can be tested without a MotionEvent.
 */
internal class ReaderJoystickDpadState {
    var pressed: Set<Int> = emptySet()
        private set

    /** The keys that went down and up since the last call, given the axes now. */
    fun update(x: Float, y: Float): Pair<List<Int>, List<Int>> {
        val now = buildSet {
            if (x <= -THRESHOLD) add(KeyEvent.KEYCODE_DPAD_LEFT)
            if (x >= THRESHOLD) add(KeyEvent.KEYCODE_DPAD_RIGHT)
            if (y <= -THRESHOLD) add(KeyEvent.KEYCODE_DPAD_UP)
            if (y >= THRESHOLD) add(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        val down = (now - pressed).toList()
        val up = (pressed - now).toList()
        pressed = now
        return down to up
    }

    fun clear(): List<Int> = pressed.toList().also { pressed = emptySet() }

    companion object {
        const val THRESHOLD = 0.5f
    }
}

/**
 * Turns a gamepad's D-pad into key events the reader handles. Android only does this itself while no
 * view has focus; once one does, the joystick motion goes to that view and the D-pad goes dead, so
 * the reader does the conversion up front. Held directions repeat like a held key.
 */
internal class ReaderJoystickDpad(
    private val handler: Handler,
    private val dispatch: (KeyEvent) -> Unit,
) {
    private val state = ReaderJoystickDpadState()
    private val repeats = mutableMapOf<Int, Runnable>()
    private val downTimes = mutableMapOf<Int, Long>()

    /** True when [event] was the D-pad or left stick and has been turned into key events. */
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE) return false
        val x = event.getAxisValue(MotionEvent.AXIS_HAT_X).takeIf { it != 0f } ?: event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y).takeIf { it != 0f } ?: event.getAxisValue(MotionEvent.AXIS_Y)
        val (down, up) = state.update(x, y)
        up.forEach(::release)
        down.forEach { press(it, event.eventTime) }
        return true
    }

    fun clear() {
        state.clear().forEach(::release)
    }

    private fun press(keyCode: Int, time: Long) {
        downTimes[keyCode] = time
        dispatch(keyEvent(keyCode, KeyEvent.ACTION_DOWN, repeat = 0, time))
        var repeat = 0
        val runnable = object : Runnable {
            override fun run() {
                repeat += 1
                dispatch(keyEvent(keyCode, KeyEvent.ACTION_DOWN, repeat, SystemClock.uptimeMillis()))
                handler.postDelayed(this, ViewConfiguration.getKeyRepeatDelay().toLong())
            }
        }
        repeats[keyCode] = runnable
        handler.postDelayed(runnable, ViewConfiguration.getKeyRepeatTimeout().toLong())
    }

    private fun release(keyCode: Int) {
        repeats.remove(keyCode)?.let(handler::removeCallbacks)
        val event = keyEvent(keyCode, KeyEvent.ACTION_UP, repeat = 0, SystemClock.uptimeMillis())
        downTimes.remove(keyCode)
        dispatch(event)
    }

    private fun keyEvent(keyCode: Int, action: Int, repeat: Int, time: Long): KeyEvent =
        KeyEvent(downTimes[keyCode] ?: time, time, action, keyCode, repeat, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_DPAD)
}
