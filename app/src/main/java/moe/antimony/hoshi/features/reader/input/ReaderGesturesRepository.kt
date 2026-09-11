package moe.antimony.hoshi.features.reader.input

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
) {
    companion object {
        const val DefaultDockOffsetFraction = 0.6f
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
    val settings: Flow<ReaderGestureSettings> = dataStore.data.map { preferences ->
        ReaderGestureSettings(
            bindings = preferences[KEY_BINDINGS]?.let(ReaderInputBindings::decode)
                ?: ReaderInputBindings.fromLegacy(legacyEdgeSwipeControls()),
            controlsPlacement = preferences[KEY_PLACEMENT]?.let { name ->
                SasayakiControlsPlacement.entries.firstOrNull { it.name == name }
            } ?: SasayakiControlsPlacement.Bottom,
            dockOffsetFraction = (preferences[KEY_DOCK_OFFSET] ?: ReaderGestureSettings.DefaultDockOffsetFraction).coerceIn(0f, 1f),
        )
    }

    suspend fun update(transform: (ReaderGestureSettings) -> ReaderGestureSettings) {
        val legacy = legacyEdgeSwipeControls()
        dataStore.edit { preferences ->
            val current = ReaderGestureSettings(
                bindings = preferences[KEY_BINDINGS]?.let(ReaderInputBindings::decode) ?: ReaderInputBindings.fromLegacy(legacy),
                controlsPlacement = preferences[KEY_PLACEMENT]?.let { name ->
                    SasayakiControlsPlacement.entries.firstOrNull { it.name == name }
                } ?: SasayakiControlsPlacement.Bottom,
                dockOffsetFraction = preferences[KEY_DOCK_OFFSET] ?: ReaderGestureSettings.DefaultDockOffsetFraction,
            )
            val next = transform(current)
            preferences[KEY_BINDINGS] = next.bindings.encode()
            preferences[KEY_PLACEMENT] = next.controlsPlacement.name
            preferences[KEY_DOCK_OFFSET] = next.dockOffsetFraction.coerceIn(0f, 1f)
        }
    }

    suspend fun exportEntries(): JsonObject = PreferencesBackup.export(dataStore)

    suspend fun importEntries(entries: JsonObject) {
        PreferencesBackup.import(dataStore, entries)
    }

    companion object {
        const val DataStoreName = "reader-gestures"
        private val KEY_BINDINGS = stringPreferencesKey("readerInputBindings")
        private val KEY_PLACEMENT = stringPreferencesKey("sasayakiControlsPlacement")
        private val KEY_DOCK_OFFSET = floatPreferencesKey("sasayakiDockOffsetFraction")
    }
}
