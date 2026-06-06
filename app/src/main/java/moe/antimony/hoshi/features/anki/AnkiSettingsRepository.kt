package moe.antimony.hoshi.features.anki

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import moe.antimony.hoshi.features.backup.PreferencesBackup

interface AnkiSettingsRepository {
    val settings: Flow<AnkiSettings>
    suspend fun update(transform: (AnkiSettings) -> AnkiSettings)
    suspend fun exportEntries(): JsonObject
    suspend fun importEntries(entries: JsonObject)
}

class DataStoreAnkiSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : AnkiSettingsRepository {
    override val settings: Flow<AnkiSettings> = dataStore.data.map { preferences ->
        preferences[KEY_SETTINGS]?.let { raw ->
            runCatching { json.decodeFromString<AnkiSettings>(raw) }.getOrNull()
        } ?: AnkiSettings()
    }

    override suspend fun update(transform: (AnkiSettings) -> AnkiSettings) {
        dataStore.edit { preferences ->
            val current = preferences[KEY_SETTINGS]?.let { raw ->
                runCatching { json.decodeFromString<AnkiSettings>(raw) }.getOrNull()
            } ?: AnkiSettings()
            preferences[KEY_SETTINGS] = json.encodeToString(transform(current))
        }
    }

    override suspend fun exportEntries(): JsonObject = PreferencesBackup.export(dataStore)

    override suspend fun importEntries(entries: JsonObject) {
        PreferencesBackup.import(dataStore, entries)
    }

    private companion object {
        val KEY_SETTINGS = stringPreferencesKey("ankiSettings")
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

private val Context.ankiDataStore by preferencesDataStore(name = "anki_settings")

fun Context.ankiSettingsRepository(): AnkiSettingsRepository =
    DataStoreAnkiSettingsRepository(ankiDataStore)
