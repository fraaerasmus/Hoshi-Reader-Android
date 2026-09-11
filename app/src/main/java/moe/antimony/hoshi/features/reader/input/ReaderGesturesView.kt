package moe.antimony.hoshi.features.reader.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import moe.antimony.hoshi.LocalHoshiUiDependencies
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.reader.ReaderSettings
import moe.antimony.hoshi.features.settings.SettingsDetailScaffold
import moe.antimony.hoshi.features.settings.collectAsLoadedSettings

/** Advanced › Gestures & shortcuts: where the playback controls live and what each gesture and key does. */
@Composable
fun ReaderGesturesView(
    readerSettings: ReaderSettings,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContainer = LocalHoshiUiDependencies.current
    val gesturesRepository = appContainer.readerGesturesRepository
    val sasayakiRepository = appContainer.sasayakiSettingsRepository
    val scope = rememberCoroutineScope()
    val gestures = gesturesRepository.settings.collectAsLoadedSettings() ?: return
    val sasayaki = sasayakiRepository.settings.collectAsLoadedSettings() ?: return

    fun bind(source: ReaderInputSource, action: ReaderInputAction) {
        scope.launch { gesturesRepository.update { it.copy(bindings = it.bindings.with(source, action)) } }
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.gestures_title),
        onClose = onClose,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) {
            item {
                SectionTitle(stringResource(R.string.gestures_section_controls))
                GesturesCard {
                    ChoiceRow(
                        label = stringResource(R.string.gestures_controls_placement),
                        options = SasayakiControlsPlacement.entries,
                        selected = gestures.controlsPlacement,
                        labelOf = { stringResource(it.labelRes()) },
                        onSelected = { placement -> scope.launch { gesturesRepository.update { it.copy(controlsPlacement = placement) } } },
                    )
                    if (gestures.controlsPlacement != SasayakiControlsPlacement.Bottom) {
                        HorizontalDivider()
                        ListItem(
                            colors = transparent(),
                            headlineContent = { Text(stringResource(R.string.gestures_dock_reset)) },
                            supportingContent = { Text(stringResource(R.string.gestures_dock_help)) },
                            modifier = Modifier.clickable {
                                scope.launch { gesturesRepository.update { it.copy(dockOffsetFraction = ReaderGestureSettings.DefaultDockOffsetFraction) } }
                            },
                        )
                        HorizontalDivider()
                        ListItem(
                            colors = transparent(),
                            headlineContent = { Text(stringResource(R.string.gestures_dock_compact)) },
                            supportingContent = { Text(stringResource(R.string.gestures_dock_compact_help)) },
                            trailingContent = {
                                Switch(
                                    checked = gestures.dockCompact,
                                    onCheckedChange = { checked -> scope.launch { gesturesRepository.update { it.copy(dockCompact = checked) } } },
                                )
                            },
                        )
                    }
                    HorizontalDivider()
                    SwitchRow(stringResource(R.string.sasayaki_double_tap_playback_controls_to_toggle), sasayaki.doubleTapPlaybackControlsToToggle) { checked ->
                        scope.launch { sasayakiRepository.update { it.copy(doubleTapPlaybackControlsToToggle = checked) } }
                    }
                    HorizontalDivider()
                    SwitchRow(stringResource(R.string.sasayaki_hold_playback_controls_to_boost), sasayaki.holdPlaybackControlsToBoost) { checked ->
                        scope.launch { sasayakiRepository.update { it.copy(holdPlaybackControlsToBoost = checked) } }
                    }
                    HorizontalDivider()
                    SwitchRow(stringResource(R.string.sasayaki_drag_playback_controls_to_scrub), sasayaki.dragPlaybackControlsToScrub) { checked ->
                        scope.launch { sasayakiRepository.update { it.copy(dragPlaybackControlsToScrub = checked) } }
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.gestures_section_edges))
                GesturesCard {
                    EdgeZoneWidthRow(
                        widthDp = gestures.edgeZoneWidthDp,
                        onWidthChange = { width -> scope.launch { gesturesRepository.update { it.copy(edgeZoneWidthDp = width) } } },
                    )
                    readerEdgeSources().forEach { source ->
                        HorizontalDivider()
                        BindingRow(source, gestures.bindings.action(source), ::bind)
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.gestures_section_volume))
                GesturesCard {
                    SwitchRow(stringResource(R.string.reader_behavior_volume_keys_turn_pages), readerSettings.volumeKeysTurnPages) {
                        onReaderSettingsChange(readerSettings.copy(volumeKeysTurnPages = it))
                    }
                    HorizontalDivider()
                    SwitchRow(stringResource(R.string.reader_behavior_volume_keys_navigate_popup_terms), readerSettings.volumeKeysNavigatePopupTerms) {
                        onReaderSettingsChange(readerSettings.copy(volumeKeysNavigatePopupTerms = it))
                    }
                    HorizontalDivider()
                    SwitchRow(stringResource(R.string.reader_behavior_volume_keys_seek_sasayaki), readerSettings.volumeKeysSeekSasayaki) {
                        onReaderSettingsChange(readerSettings.copy(volumeKeysSeekSasayaki = it))
                    }
                    HorizontalDivider()
                    SwitchRow(stringResource(R.string.reader_behavior_reverse_volume_key_direction), readerSettings.reverseVolumeKeyDirection) {
                        onReaderSettingsChange(readerSettings.copy(reverseVolumeKeyDirection = it))
                    }
                    HorizontalDivider()
                    BindingRow(ReaderInputSource.VolumeKeyHold, gestures.bindings.action(ReaderInputSource.VolumeKeyHold), ::bind)
                }
            }
            item {
                SectionTitle(stringResource(R.string.gestures_section_mouse))
                GesturesCard {
                    ListItem(
                        colors = transparent(),
                        headlineContent = { Text(stringResource(R.string.gestures_mouse_selection_colors)) },
                        supportingContent = { Text(stringResource(R.string.gestures_mouse_selection_colors_help)) },
                        trailingContent = {
                            Switch(
                                checked = gestures.mouseSelectionShowsHighlightColors,
                                onCheckedChange = { checked -> scope.launch { gesturesRepository.update { it.copy(mouseSelectionShowsHighlightColors = checked) } } },
                            )
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        colors = transparent(),
                        headlineContent = { Text(stringResource(R.string.gestures_mouse_side_click)) },
                        supportingContent = { Text(stringResource(R.string.gestures_mouse_side_click_help)) },
                        trailingContent = {
                            Switch(
                                checked = gestures.mouseSideClickTurnsPages,
                                onCheckedChange = { checked -> scope.launch { gesturesRepository.update { it.copy(mouseSideClickTurnsPages = checked) } } },
                            )
                        },
                    )
                }
            }
            item {
                SectionTitle(stringResource(R.string.gestures_section_keyboard))
                GesturesCard {
                    ListItem(
                        colors = transparent(),
                        headlineContent = { Text(stringResource(R.string.gestures_keyboard_help)) },
                    )
                }
            }
        }
    }
}

internal fun readerEdgeSources(): List<ReaderInputSource> = listOf(
    ReaderInputSource.EdgeLeftHold,
    ReaderInputSource.EdgeRightHold,
    ReaderInputSource.EdgeLeftDoubleTap,
    ReaderInputSource.EdgeRightDoubleTap,
    ReaderInputSource.EdgeLeftDrag,
    ReaderInputSource.EdgeRightDrag,
)

@Composable
private fun BindingRow(source: ReaderInputSource, action: ReaderInputAction, onBind: (ReaderInputSource, ReaderInputAction) -> Unit) {
    ChoiceRow(
        label = stringResource(source.labelRes()),
        options = ReaderInputBindings.compatibleActions(source),
        selected = action,
        labelOf = { stringResource(it.labelRes()) },
        onSelected = { onBind(source, it) },
    )
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        colors = transparent(),
        headlineContent = { Text(label) },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) { Text(labelOf(selected)) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(labelOf(option)) },
                            onClick = {
                                expanded = false
                                onSelected(option)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun EdgeZoneWidthRow(widthDp: Int, onWidthChange: (Int) -> Unit) {
    val min = ReaderGestureSettings.MinEdgeZoneWidthDp
    val max = ReaderGestureSettings.MaxEdgeZoneWidthDp
    var sliderValue by remember(widthDp) { mutableStateOf(widthDp.toFloat()) }
    ListItem(
        colors = transparent(),
        headlineContent = { Text(stringResource(R.string.gestures_edge_zone_width)) },
        supportingContent = {
            Column {
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onWidthChange(sliderValue.roundToInt().coerceIn(min, max)) },
                    valueRange = min.toFloat()..max.toFloat(),
                    steps = (max - min) / 4 - 1,
                )
                EdgeZonePreview(zoneWidthDp = sliderValue)
            }
        },
        trailingContent = { Text(stringResource(R.string.gestures_edge_zone_width_value_format, sliderValue.roundToInt())) },
    )
}

/** A to-scale sketch of this screen with the two edge strips, so the slider's number means something. */
@Composable
private fun EdgeZonePreview(zoneWidthDp: Float) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.coerceAtLeast(1)
    val screenHeightDp = configuration.screenHeightDp.coerceAtLeast(1)
    val previewHeight = 120.dp
    val previewWidth = previewHeight * screenWidthDp / screenHeightDp
    val outline = MaterialTheme.colorScheme.outline
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Canvas(
        modifier = Modifier
            .padding(top = 4.dp)
            .width(previewWidth)
            .height(previewHeight),
    ) {
        val strip = size.width * zoneWidthDp / screenWidthDp
        drawRoundRect(outline, cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(width = 1.dp.toPx()))
        drawRect(tint, size = Size(strip, size.height))
        drawRect(tint, topLeft = Offset(size.width - strip, 0f), size = Size(strip, size.height))
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        colors = transparent(),
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun GesturesCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun transparent() = ListItemDefaults.colors(containerColor = Color.Transparent)

internal fun ReaderInputSource.labelRes(): Int = when (this) {
    ReaderInputSource.EdgeLeftHold -> R.string.gestures_edge_left_hold
    ReaderInputSource.EdgeRightHold -> R.string.gestures_edge_right_hold
    ReaderInputSource.EdgeLeftDoubleTap -> R.string.gestures_edge_left_double_tap
    ReaderInputSource.EdgeRightDoubleTap -> R.string.gestures_edge_right_double_tap
    ReaderInputSource.EdgeLeftDrag -> R.string.gestures_edge_left_drag
    ReaderInputSource.EdgeRightDrag -> R.string.gestures_edge_right_drag
    ReaderInputSource.VolumeKeyHold -> R.string.gestures_volume_hold
}

internal fun ReaderInputAction.labelRes(): Int = when (this) {
    ReaderInputAction.None -> R.string.input_action_none
    ReaderInputAction.TogglePlayback -> R.string.input_action_toggle_playback
    ReaderInputAction.SkipForward -> R.string.input_action_skip_forward
    ReaderInputAction.SkipBackward -> R.string.input_action_skip_backward
    ReaderInputAction.PageForward -> R.string.input_action_page_forward
    ReaderInputAction.PageBackward -> R.string.input_action_page_backward
    ReaderInputAction.ToggleFocusMode -> R.string.input_action_toggle_focus
    ReaderInputAction.BoostWhileHeld -> R.string.input_action_boost
    ReaderInputAction.Scrub -> R.string.input_action_scrub
    ReaderInputAction.Brightness -> R.string.input_action_brightness
    ReaderInputAction.Volume -> R.string.input_action_volume
}

internal fun SasayakiControlsPlacement.labelRes(): Int = when (this) {
    SasayakiControlsPlacement.Bottom -> R.string.gestures_placement_bottom
    SasayakiControlsPlacement.Left -> R.string.gestures_placement_left
    SasayakiControlsPlacement.Right -> R.string.gestures_placement_right
}
