package moe.antimony.hoshi.features.reader.input

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import moe.antimony.hoshi.features.backup.PreferencesBackup

data class ReaderGestureSettings(
    val bindings: ReaderInputBindings = ReaderInputBindings(),
    val controlsPlacement: SasayakiControlsPlacement = SasayakiControlsPlacement.Bottom,
    /** Where the side dock rests along its edge, 0 = top, 1 = bottom. */
    val dockOffsetFraction: Float = DefaultDockOffsetFraction,
    /** Mouse only: a click in the outer side zones turns the page instead of looking up the word there. */
    val mouseSideClickTurnsPages: Boolean = false,
    /** Mouse only: finishing a selection opens the highlight colours, which Chromium's mouse menu leaves out. */
    val mouseSelectionShowsHighlightColors: Boolean = true,
    /** Width of the touch strips on both screen edges that hold, double-tap and drag bindings listen in. */
    val edgeZoneWidthDp: Int = DefaultEdgeZoneWidthDp,
    /** Side dock only: the tab itself is the control (tap, hold, pull to scrub) and no drawer opens. */
    val dockCompact: Boolean = false,
) {
    companion object {
        const val DefaultDockOffsetFraction = 0.6f
        const val DefaultEdgeZoneWidthDp = 32
        const val MinEdgeZoneWidthDp = 16
        const val MaxEdgeZoneWidthDp = 64
    }
}

private val Context.readerGesturesDataStore by preferencesDataStore(name = ReaderGesturesRepository.DataStoreName)

fun Context.readerGesturesRepository(legacyEdgeSwipeControls: suspend () -> Boolean): ReaderGesturesRepository =
    ReaderGesturesRepository(readerGesturesDataStore, legacyEdgeSwipeControls)

/** Rebindable gestures and the playback-control placement, in their own store so they ride the settings backup. */
class ReaderGesturesRepository(
    private val dataStore: DataStore<Preferences>,
    private val legacyEdgeSwipeControls: suspend () -> Boolean,
) {
    val settings: Flow<ReaderGestureSettings> = dataStore.data.map { it.toSettings(legacyEdgeSwipeControls()) }

    suspend fun update(transform: (ReaderGestureSettings) -> ReaderGestureSettings) {
        val legacy = legacyEdgeSwipeControls()
        dataStore.edit { preferences ->
            val next = transform(preferences.toSettings(legacy))
            preferences[KEY_BINDINGS] = next.bindings.encode()
            preferences[KEY_PLACEMENT] = next.controlsPlacement.name
            preferences[KEY_DOCK_OFFSET] = next.dockOffsetFraction.coerceIn(0f, 1f)
            preferences[KEY_MOUSE_SIDE_CLICK] = next.mouseSideClickTurnsPages
            preferences[KEY_MOUSE_SELECTION_COLORS] = next.mouseSelectionShowsHighlightColors
            preferences[KEY_EDGE_ZONE_WIDTH] = next.edgeZoneWidthDp.coerceIn(ReaderGestureSettings.MinEdgeZoneWidthDp, ReaderGestureSettings.MaxEdgeZoneWidthDp)
            preferences[KEY_DOCK_COMPACT] = next.dockCompact
        }
    }

    private fun Preferences.toSettings(legacyEdgeSwipeControls: Boolean): ReaderGestureSettings =
        ReaderGestureSettings(
            bindings = this[KEY_BINDINGS]?.let(ReaderInputBindings::decode) ?: ReaderInputBindings.fromLegacy(legacyEdgeSwipeControls),
            controlsPlacement = this[KEY_PLACEMENT]?.let { name ->
                SasayakiControlsPlacement.entries.firstOrNull { it.name == name }
            } ?: SasayakiControlsPlacement.Bottom,
            dockOffsetFraction = (this[KEY_DOCK_OFFSET] ?: ReaderGestureSettings.DefaultDockOffsetFraction).coerceIn(0f, 1f),
            mouseSideClickTurnsPages = this[KEY_MOUSE_SIDE_CLICK] ?: false,
            mouseSelectionShowsHighlightColors = this[KEY_MOUSE_SELECTION_COLORS] ?: true,
            edgeZoneWidthDp = (this[KEY_EDGE_ZONE_WIDTH] ?: ReaderGestureSettings.DefaultEdgeZoneWidthDp)
                .coerceIn(ReaderGestureSettings.MinEdgeZoneWidthDp, ReaderGestureSettings.MaxEdgeZoneWidthDp),
            dockCompact = this[KEY_DOCK_COMPACT] ?: false,
        )

    suspend fun exportEntries(): JsonObject = PreferencesBackup.export(dataStore)

    suspend fun importEntries(entries: JsonObject) {
        PreferencesBackup.import(dataStore, entries)
    }

    companion object {
        const val DataStoreName = "reader-gestures"
        private val KEY_BINDINGS = stringPreferencesKey("readerInputBindings")
        private val KEY_PLACEMENT = stringPreferencesKey("sasayakiControlsPlacement")
        private val KEY_DOCK_OFFSET = floatPreferencesKey("sasayakiDockOffsetFraction")
        private val KEY_MOUSE_SIDE_CLICK = booleanPreferencesKey("mouseSideClickTurnsPages")
        private val KEY_MOUSE_SELECTION_COLORS = booleanPreferencesKey("mouseSelectionShowsHighlightColors")
        private val KEY_EDGE_ZONE_WIDTH = intPreferencesKey("edgeZoneWidthDp")
        private val KEY_DOCK_COMPACT = booleanPreferencesKey("sasayakiDockCompact")
    }
}
