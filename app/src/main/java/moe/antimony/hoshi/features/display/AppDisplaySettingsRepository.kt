package moe.antimony.hoshi.features.display

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import moe.antimony.hoshi.features.backup.PreferencesBackup

enum class LegacyDisplayTheme {
    System,
    Light,
    Dark,
    Sepia,
    Custom,
}

data class LegacyDisplaySettingsSnapshot(
    val theme: LegacyDisplayTheme = LegacyDisplayTheme.System,
    val eInkMode: Boolean = false,
    val systemLightSepia: Boolean = false,
    val sepiaInvertInDark: Boolean = false,
    val customBackgroundColor: Long = 0xFFFFFFFFL,
    val customTextColor: Long = 0xFF000000L,
    val customInfoColor: Long = 0xFF999999L,
    /** Fork: the curated palette the Custom theme used, by its stored name; null or "manual" means hand-set colors. */
    val colorPreset: String? = null,
)

data class AppDisplayMigrationPayload(
    val activeSettings: LegacyDisplaySettingsSnapshot? = null,
)

interface AppDisplaySettingsMigrationSource {
    suspend fun loadMigrationPayload(): AppDisplayMigrationPayload
}

private val Context.appDisplaySettingsDataStore by preferencesDataStore(
    name = AppDisplaySettingsRepository.DataStoreName,
)

fun Context.appDisplaySettingsRepository(
    migrationSource: AppDisplaySettingsMigrationSource? = null,
): AppDisplaySettingsRepository = AppDisplaySettingsRepository(
    dataStore = appDisplaySettingsDataStore,
    migrationSource = migrationSource,
)

class AppDisplaySettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val migrationSource: AppDisplaySettingsMigrationSource? = null,
) {
    private val migrationMutex = Mutex()

    // Fork: the JSON settings backup carries this store like the others.
    suspend fun exportEntries(): JsonObject = PreferencesBackup.export(dataStore)

    suspend fun importEntries(entries: JsonObject) {
        PreferencesBackup.import(dataStore, entries)
    }

    val settings: Flow<AppDisplaySettings> = dataStore.data
        .onStart { ensureMigrated() }
        .map { preferences ->
            preferences.readSettings()
                ?.normalized()
                ?: defaultSettings()
        }

    suspend fun ensureMigrated() {
        migrationMutex.withLock {
            val current = dataStore.data.first().readSettings()
            if (current != null && current.migrationVersion >= CurrentMigrationVersion) return

            val migrated = migrate(migrationSource?.loadMigrationPayload())
            dataStore.edit { preferences ->
                val latest = preferences.readSettings()
                if (latest == null || latest.migrationVersion < CurrentMigrationVersion) {
                    preferences[KEY_SETTINGS] = json.encodeToString(
                        migrated.copy(migrationVersion = CurrentMigrationVersion).normalized(),
                    )
                }
            }
        }
    }

    suspend fun update(transform: (AppDisplaySettings) -> AppDisplaySettings) {
        ensureMigrated()
        dataStore.edit { preferences ->
            val current = preferences.readSettings() ?: defaultSettings()
            preferences[KEY_SETTINGS] = json.encodeToString(
                transform(current).copy(migrationVersion = CurrentMigrationVersion).normalized(),
            )
        }
    }

    suspend fun setAutoSwitch(enabled: Boolean, systemDark: Boolean) {
        update { settings -> settings.withAutoSwitch(enabled, systemDark) }
    }

    suspend fun selectPreset(slot: DisplayPaletteSlot, preset: DisplayPalettePreset) {
        update { settings -> settings.withSelectedPreset(slot, preset) }
    }

    suspend fun updateCustomPalette(
        slot: DisplayPaletteSlot,
        backgroundColor: Long,
        textColor: Long,
        infoColor: Long,
    ) {
        update { settings -> settings.withCustomPalette(slot, backgroundColor, textColor, infoColor) }
    }

    private fun Preferences.readSettings(): AppDisplaySettings? = this[KEY_SETTINGS]?.let { encoded ->
        json.decodeFromString(AppDisplaySettings.serializer(), encoded)
    }

    private fun migrate(payload: AppDisplayMigrationPayload?): AppDisplaySettings =
        payload?.activeSettings?.let(::migrateLegacy) ?: defaultSettings()

    private fun migrateLegacy(legacy: LegacyDisplaySettingsSnapshot): AppDisplaySettings {
        val custom = DisplayPaletteSelection(
            preset = DisplayPalettePreset.Custom,
            customBackgroundColor = legacy.customBackgroundColor,
            customTextColor = legacy.customTextColor,
            customInfoColor = legacy.customInfoColor,
        )
        val settings = when (legacy.theme) {
            LegacyDisplayTheme.System -> AppDisplaySettings(
                autoSwitch = true,
                lightPalette = DisplayPaletteSelection(
                    preset = if (legacy.systemLightSepia) DisplayPalettePreset.Sepia else DisplayPalettePreset.Light,
                ),
            )
            LegacyDisplayTheme.Light -> manualSettings(DisplayPaletteSelection(DisplayPalettePreset.Light))
            LegacyDisplayTheme.Dark -> manualSettings(DisplayPaletteSelection(DisplayPalettePreset.Dark))
            LegacyDisplayTheme.Sepia -> if (legacy.sepiaInvertInDark) {
                AppDisplaySettings(
                    autoSwitch = true,
                    lightPalette = DisplayPaletteSelection(DisplayPalettePreset.Sepia),
                    darkPalette = AppDisplaySettings().darkPalette.copy(preset = DisplayPalettePreset.DarkSepia),
                )
            } else {
                manualSettings(DisplayPaletteSelection(DisplayPalettePreset.Sepia))
            }
            LegacyDisplayTheme.Custom -> legacyPresetPalettes(legacy.colorPreset)?.let { (light, dark) ->
                AppDisplaySettings(
                    autoSwitch = true,
                    lightPalette = DisplayPaletteSelection(light),
                    darkPalette = AppDisplaySettings().darkPalette.copy(preset = dark),
                )
            } ?: manualSettings(custom)
        }
        return settings.copy(eInkMode = legacy.eInkMode)
    }

    /** The light and dark variant of a fork preset the Custom theme followed the system with. */
    private fun legacyPresetPalettes(colorPreset: String?): Pair<DisplayPalettePreset, DisplayPalettePreset>? = when (colorPreset) {
        "rosePine" -> DisplayPalettePreset.RosePineDawn to DisplayPalettePreset.RosePine
        "gruvbox" -> DisplayPalettePreset.GruvboxLight to DisplayPalettePreset.GruvboxDark
        "everforest" -> DisplayPalettePreset.EverforestLight to DisplayPalettePreset.EverforestDark
        else -> null
    }

    private fun manualSettings(palette: DisplayPaletteSelection): AppDisplaySettings {
        val slot = palette.legacySlot()
        val selected = AppDisplaySettings(autoSwitch = false).withSelectedPreset(slot, palette.preset)
        return if (palette.preset == DisplayPalettePreset.Custom) {
            selected.withCustomPalette(slot, palette.customBackgroundColor, palette.customTextColor, palette.customInfoColor)
        } else selected
    }

    companion object {
        const val DataStoreName = "app-display-settings"
        const val CurrentMigrationVersion = 1

        private val KEY_SETTINGS = stringPreferencesKey("settings")
        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        private fun defaultSettings(): AppDisplaySettings = AppDisplaySettings(
            migrationVersion = CurrentMigrationVersion,
        )
    }
}
