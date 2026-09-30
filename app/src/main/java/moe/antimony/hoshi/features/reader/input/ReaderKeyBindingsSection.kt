package moe.antimony.hoshi.features.reader.input

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import moe.antimony.hoshi.R

/** The rows of Gestures & Shortcuts that set what keyboard keys and gamepad buttons do, one row per action. */
@Composable
internal fun ReaderKeyBindingsSection(
    bindings: ReaderKeyBindings,
    onBindingsChange: (ReaderKeyBindings) -> Unit,
) {
    var editing by remember { mutableStateOf<ReaderKeyAction?>(null) }
    var confirmingReset by remember { mutableStateOf(false) }

    SectionTitle(stringResource(R.string.gestures_section_keyboard))
    Text(
        text = stringResource(R.string.gestures_keys_help),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
    readerKeyLayersAsListed().forEach { layer ->
        Text(
            text = stringResource(layer.labelRes()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        GesturesCard {
            ReaderKeyAction.entries.filter { it.layer == layer }.forEachIndexed { index, action ->
                if (index > 0) HorizontalDivider()
                KeyActionRow(action, bindings.keys(action), onClick = { editing = action })
            }
        }
    }
    // Shown only once something differs, rather than as a button that does nothing.
    if (bindings.map.isNotEmpty()) {
        TextButton(onClick = { confirmingReset = true }, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(stringResource(R.string.gestures_keys_reset))
        }
    }

    editing?.let { action ->
        ReaderKeysDialog(
            action = action,
            bindings = bindings,
            onDismiss = { editing = null },
            onSave = { keys ->
                onBindingsChange(bindings.withKeys(action, keys))
                editing = null
            },
        )
    }
    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text(stringResource(R.string.gestures_keys_reset_title)) },
            text = { Text(stringResource(R.string.gestures_keys_reset_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBindingsChange(ReaderKeyBindings())
                        confirmingReset = false
                    },
                ) { Text(stringResource(R.string.action_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Reading first, as it is the layer that always applies; the lookup order is the reverse. */
internal fun readerKeyLayersAsListed(): List<ReaderKeyLayer> =
    listOf(ReaderKeyLayer.Reading, ReaderKeyLayer.Audiobook, ReaderKeyLayer.Popup)

@Composable
private fun KeyActionRow(action: ReaderKeyAction, keys: List<ReaderKey>, onClick: () -> Unit) {
    ListItem(
        colors = transparent(),
        headlineContent = { Text(stringResource(action.labelRes())) },
        supportingContent = {
            if (keys.isEmpty()) {
                Text(stringResource(R.string.gestures_keys_not_set))
            } else {
                KeyCaps(keys)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyCaps(keys: List<ReaderKey>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        keys.forEach { KeyCap(it) }
    }
}

/** A key drawn as the cap of a key: a label, not a button. A gamepad button carries a gamepad, to tell A from the A key. */
@Composable
private fun KeyCap(key: ReaderKey) {
    val name = KeyEvent.keyCodeToString(key.keyCode)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            if (isReaderGamepadButton(name)) {
                Icon(Icons.Rounded.SportsEsports, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(readerKeyLabel(key, name), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The keys of one action. Nothing is kept until Save, so a key taken from another action by mistake is undone with Cancel. */
@Composable
private fun ReaderKeysDialog(
    action: ReaderKeyAction,
    bindings: ReaderKeyBindings,
    onDismiss: () -> Unit,
    onSave: (List<ReaderKey>) -> Unit,
) {
    var keys by remember { mutableStateOf(bindings.keys(action)) }
    // An action with no key yet starts out waiting for one.
    var listening by remember { mutableStateOf(keys.isEmpty()) }
    // Held until the key is let go, so its release does not click whatever takes the focus next.
    var pending by remember { mutableStateOf<ReaderKey?>(null) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(keys) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        title = {
            Column {
                Text(stringResource(action.labelRes()))
                Text(
                    text = stringResource(action.layer.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (keys.isEmpty() && !listening) {
                    Text(stringResource(R.string.gestures_keys_not_set), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                keys.forEach { key ->
                    KeyRow(
                        key = key,
                        takenFrom = bindings.owner(key, action.layer)?.takeIf { it != action },
                        onRemove = { keys = keys - key },
                    )
                }
                if (listening) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = Color.Transparent,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { event ->
                                val pressed = event.nativeKeyEvent
                                when {
                                    // Back has to keep closing the dialog.
                                    pressed.keyCode == KeyEvent.KEYCODE_BACK -> false
                                    pressed.action == KeyEvent.ACTION_DOWN && pressed.repeatCount == 0 -> {
                                        readerKeyOrNull(pressed.keyCode, pressed.metaState)?.let { pending = it }
                                        true
                                    }
                                    pressed.action == KeyEvent.ACTION_UP -> {
                                        val caught = pending
                                        if (caught != null && caught.keyCode == pressed.keyCode) {
                                            if (caught !in keys) keys = keys + caught
                                            pending = null
                                            listening = false
                                        }
                                        true
                                    }
                                    else -> true
                                }
                            }
                            .focusable(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.gestures_keys_press), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = stringResource(R.string.gestures_keys_press_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    TextButton(onClick = { listening = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.gestures_keys_add))
                    }
                }
            }
        },
    )

    LaunchedEffect(listening) {
        if (listening) {
            // The box is not there to take the focus until the dialog has been laid out.
            delay(100)
            runCatching { focusRequester.requestFocus() }
        }
    }
}

@Composable
private fun KeyRow(key: ReaderKey, takenFrom: ReaderKeyAction?, onRemove: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KeyCap(key)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = null)
            }
        }
        if (takenFrom != null) {
            Text(
                text = stringResource(R.string.gestures_keys_taken_format, stringResource(takenFrom.labelRes())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

internal fun ReaderKeyLayer.labelRes(): Int = when (this) {
    ReaderKeyLayer.Popup -> R.string.gestures_keys_layer_popup
    ReaderKeyLayer.Audiobook -> R.string.gestures_keys_layer_audiobook
    ReaderKeyLayer.Reading -> R.string.gestures_keys_layer_reading
}

internal fun ReaderKeyAction.labelRes(): Int = when (this) {
    ReaderKeyAction.PageForward -> R.string.input_action_page_forward
    ReaderKeyAction.PageBackward -> R.string.input_action_page_backward
    ReaderKeyAction.ChapterForward -> R.string.input_action_chapter_forward
    ReaderKeyAction.ChapterBackward -> R.string.input_action_chapter_backward
    ReaderKeyAction.ToggleFocusMode -> R.string.input_action_toggle_focus
    ReaderKeyAction.PickWord -> R.string.input_action_pick_word
    ReaderKeyAction.WordPrevious -> R.string.input_action_word_previous
    ReaderKeyAction.WordNext -> R.string.input_action_word_next
    ReaderKeyAction.TogglePlayback -> R.string.input_action_toggle_playback
    ReaderKeyAction.SkipBackward -> R.string.input_action_skip_backward
    ReaderKeyAction.SkipForward -> R.string.input_action_skip_forward
    ReaderKeyAction.SpeedDown -> R.string.input_action_speed_down
    ReaderKeyAction.SpeedUp -> R.string.input_action_speed_up
    ReaderKeyAction.BoostWhileHeld -> R.string.input_action_boost
    ReaderKeyAction.VolumeUp -> R.string.input_action_volume_up
    ReaderKeyAction.VolumeDown -> R.string.input_action_volume_down
    ReaderKeyAction.ClosePopup -> R.string.input_action_close_popup
    ReaderKeyAction.PopupPreviousTerm -> R.string.input_action_popup_previous_term
    ReaderKeyAction.PopupNextTerm -> R.string.input_action_popup_next_term
    ReaderKeyAction.MineTerm -> R.string.dictionary_add_to_anki
    ReaderKeyAction.PlayTermAudio -> R.string.dictionary_play_audio
    ReaderKeyAction.PopupScrollUp -> R.string.input_action_popup_scroll_up
    ReaderKeyAction.PopupScrollDown -> R.string.input_action_popup_scroll_down
    ReaderKeyAction.PopupBack -> R.string.input_action_popup_back
    ReaderKeyAction.PopupForward -> R.string.input_action_popup_forward
}
