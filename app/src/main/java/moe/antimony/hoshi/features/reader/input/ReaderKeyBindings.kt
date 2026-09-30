package moe.antimony.hoshi.features.reader.input

import android.view.KeyEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** When an action can run. In priority order: a key does the action of the first active layer that holds it. */
enum class ReaderKeyLayer { Popup, Audiobook, Reading }

enum class ReaderKeyAction(val layer: ReaderKeyLayer, val repeats: Boolean = false) {
    PageForward(ReaderKeyLayer.Reading),
    PageBackward(ReaderKeyLayer.Reading),
    ChapterForward(ReaderKeyLayer.Reading),
    ChapterBackward(ReaderKeyLayer.Reading),
    ToggleFocusMode(ReaderKeyLayer.Reading),
    TogglePlayback(ReaderKeyLayer.Audiobook),
    SkipBackward(ReaderKeyLayer.Audiobook, repeats = true),
    SkipForward(ReaderKeyLayer.Audiobook, repeats = true),
    SpeedDown(ReaderKeyLayer.Audiobook, repeats = true),
    SpeedUp(ReaderKeyLayer.Audiobook, repeats = true),
    BoostWhileHeld(ReaderKeyLayer.Audiobook),
    ClosePopup(ReaderKeyLayer.Popup),
    PopupPreviousTerm(ReaderKeyLayer.Popup, repeats = true),
    PopupNextTerm(ReaderKeyLayer.Popup, repeats = true),
}

/** A keyboard key or gamepad button, alone or with Ctrl, Alt or Shift. */
@Serializable
data class ReaderKey(val keyCode: Int, val modifiers: Int = 0)

const val READER_KEY_MODIFIER_MASK = KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON or KeyEvent.META_CTRL_ON

// Spelled out because KeyEvent.isModifierKey() is not there in JVM unit tests.
private val ModifierKeys = setOf(
    KeyEvent.KEYCODE_SHIFT_LEFT,
    KeyEvent.KEYCODE_SHIFT_RIGHT,
    KeyEvent.KEYCODE_ALT_LEFT,
    KeyEvent.KEYCODE_ALT_RIGHT,
    KeyEvent.KEYCODE_CTRL_LEFT,
    KeyEvent.KEYCODE_CTRL_RIGHT,
    KeyEvent.KEYCODE_META_LEFT,
    KeyEvent.KEYCODE_META_RIGHT,
    KeyEvent.KEYCODE_SYM,
    KeyEvent.KEYCODE_NUM,
    KeyEvent.KEYCODE_FUNCTION,
    KeyEvent.KEYCODE_CAPS_LOCK,
    KeyEvent.KEYCODE_NUM_LOCK,
    KeyEvent.KEYCODE_SCROLL_LOCK,
)

private val VolumeKeys = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)

fun isReaderVolumeKey(keyCode: Int): Boolean = keyCode in VolumeKeys

/** Null for what cannot be bound: a modifier on its own and Back. */
fun readerKeyOrNull(keyCode: Int, metaState: Int): ReaderKey? =
    if (keyCode == KeyEvent.KEYCODE_UNKNOWN || keyCode == KeyEvent.KEYCODE_BACK || keyCode in ModifierKeys) {
        null
    } else {
        ReaderKey(keyCode, metaState and READER_KEY_MODIFIER_MASK)
    }

/** Only edited rows are stored, so an action added later brings its default keys to people who already changed others. */
data class ReaderKeyBindings(val map: Map<ReaderKeyAction, List<ReaderKey>> = emptyMap()) {
    fun keys(action: ReaderKeyAction): List<ReaderKey> =
        map[action] ?: Defaults[action].orEmpty().filter { key -> savedOwner(key, action.layer) == null }

    /** What [key] does in [layer], if anything. */
    fun owner(key: ReaderKey, layer: ReaderKeyLayer): ReaderKeyAction? =
        ReaderKeyAction.entries.firstOrNull { it.layer == layer && key in keys(it) }

    fun actionFor(key: ReaderKey, popupOpen: Boolean, audioLoaded: Boolean): ReaderKeyAction? =
        ReaderKeyLayer.entries.firstNotNullOfOrNull { layer ->
            val active = when (layer) {
                ReaderKeyLayer.Popup -> popupOpen
                ReaderKeyLayer.Audiobook -> audioLoaded
                ReaderKeyLayer.Reading -> true
            }
            if (active) owner(key, layer) else null
        }

    /** [action] gets [newKeys]; a key that did something else in the same layer now does this. */
    fun withKeys(action: ReaderKeyAction, newKeys: List<ReaderKey>): ReaderKeyBindings {
        val next = map.toMutableMap()
        ReaderKeyAction.entries
            .filter { it != action && it.layer == action.layer }
            .forEach { other ->
                val kept = keys(other)
                if (kept.any { it in newKeys }) next[other] = kept - newKeys.toSet()
            }
        next[action] = newKeys.distinct()
        return ReaderKeyBindings(next.filter { (saved, savedKeys) -> savedKeys != Defaults[saved].orEmpty() })
    }

    fun encode(): String = json.encodeToString(serializer, map.mapKeys { it.key.name })

    private fun savedOwner(key: ReaderKey, layer: ReaderKeyLayer): ReaderKeyAction? =
        map.entries.firstOrNull { (action, keys) -> action.layer == layer && key in keys }?.key

    companion object {
        val Defaults: Map<ReaderKeyAction, List<ReaderKey>> = mapOf(
            ReaderKeyAction.PageForward to keysOf(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_BUTTON_R1),
            ReaderKeyAction.PageBackward to keysOf(KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_BUTTON_L1),
            ReaderKeyAction.ToggleFocusMode to keysOf(KeyEvent.KEYCODE_BUTTON_SELECT),
            ReaderKeyAction.TogglePlayback to keysOf(KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_BUTTON_A),
            ReaderKeyAction.SkipBackward to keysOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_J),
            ReaderKeyAction.SkipForward to keysOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_L),
            // With Shift too, so < and > keep working now that modifiers have to match.
            ReaderKeyAction.SpeedDown to listOf(
                ReaderKey(KeyEvent.KEYCODE_COMMA),
                ReaderKey(KeyEvent.KEYCODE_COMMA, KeyEvent.META_SHIFT_ON),
            ),
            ReaderKeyAction.SpeedUp to listOf(
                ReaderKey(KeyEvent.KEYCODE_PERIOD),
                ReaderKey(KeyEvent.KEYCODE_PERIOD, KeyEvent.META_SHIFT_ON),
            ),
            ReaderKeyAction.ClosePopup to keysOf(KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_B),
            ReaderKeyAction.PopupPreviousTerm to keysOf(KeyEvent.KEYCODE_DPAD_UP),
            ReaderKeyAction.PopupNextTerm to keysOf(KeyEvent.KEYCODE_DPAD_DOWN),
        )

        /**
         * The volume-key switches that came before bindings, as the rows they stood for: Up went back and Down
         * forward, or the other way round with the direction reversed. Nothing when every switch was off.
         */
        fun fromLegacyVolumeKeys(
            turnPages: Boolean,
            navigatePopupTerms: Boolean,
            seekSasayaki: Boolean,
            reverseDirection: Boolean,
        ): ReaderKeyBindings {
            val back = ReaderKey(if (reverseDirection) KeyEvent.KEYCODE_VOLUME_DOWN else KeyEvent.KEYCODE_VOLUME_UP)
            val forward = ReaderKey(if (reverseDirection) KeyEvent.KEYCODE_VOLUME_UP else KeyEvent.KEYCODE_VOLUME_DOWN)
            var bindings = ReaderKeyBindings()
            if (turnPages) {
                bindings = bindings
                    .withKeys(ReaderKeyAction.PageBackward, Defaults.getValue(ReaderKeyAction.PageBackward) + back)
                    .withKeys(ReaderKeyAction.PageForward, Defaults.getValue(ReaderKeyAction.PageForward) + forward)
            }
            if (navigatePopupTerms) {
                bindings = bindings
                    .withKeys(ReaderKeyAction.PopupPreviousTerm, Defaults.getValue(ReaderKeyAction.PopupPreviousTerm) + back)
                    .withKeys(ReaderKeyAction.PopupNextTerm, Defaults.getValue(ReaderKeyAction.PopupNextTerm) + forward)
            }
            if (seekSasayaki) {
                bindings = bindings
                    .withKeys(ReaderKeyAction.SkipBackward, Defaults.getValue(ReaderKeyAction.SkipBackward) + back)
                    .withKeys(ReaderKeyAction.SkipForward, Defaults.getValue(ReaderKeyAction.SkipForward) + forward)
            }
            return bindings
        }

        /** Unknown actions and unbindable keys are dropped; a broken blob yields the defaults rather than a broken reader. */
        fun decode(text: String): ReaderKeyBindings =
            runCatching { json.decodeFromString(serializer, text) }
                .getOrDefault(emptyMap())
                .mapNotNull { (name, keys) ->
                    val action = ReaderKeyAction.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
                    action to keys.mapNotNull { readerKeyOrNull(it.keyCode, it.modifiers) }.distinct()
                }
                .toMap()
                .let(::ReaderKeyBindings)

        private fun keysOf(vararg keyCodes: Int) = keyCodes.map { ReaderKey(it) }

        private val serializer = MapSerializer(String.serializer(), ListSerializer(ReaderKey.serializer()))
        private val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * A name short enough for a key cap, out of what [KeyEvent.keyCodeToString] gives, such as
 * KEYCODE_BUTTON_L1. The four directions are arrows, as a keyboard and a gamepad share them.
 */
fun readerKeyCodeLabel(keyCodeName: String): String =
    when (val name = keyCodeName.removePrefix("KEYCODE_")) {
        "DPAD_LEFT" -> "←"
        "DPAD_RIGHT" -> "→"
        "DPAD_UP" -> "↑"
        "DPAD_DOWN" -> "↓"
        "ESCAPE" -> "Esc"
        "COMMA" -> ","
        "PERIOD" -> "."
        else -> name.removePrefix("BUTTON_").split('_').joinToString(" ") { word ->
            if (word == "DPAD") "D-pad" else word.lowercase().replaceFirstChar(Char::uppercase)
        }
    }

/** Gamepad A and keyboard A are both named A, so a key cap marks the gamepad one. */
fun isReaderGamepadButton(keyCodeName: String): Boolean = keyCodeName.startsWith("KEYCODE_BUTTON_")

fun readerKeyLabel(key: ReaderKey, keyCodeName: String): String =
    listOfNotNull(
        "Ctrl".takeIf { key.modifiers and KeyEvent.META_CTRL_ON != 0 },
        "Alt".takeIf { key.modifiers and KeyEvent.META_ALT_ON != 0 },
        "Shift".takeIf { key.modifiers and KeyEvent.META_SHIFT_ON != 0 },
        readerKeyCodeLabel(keyCodeName),
    ).joinToString(" + ")
