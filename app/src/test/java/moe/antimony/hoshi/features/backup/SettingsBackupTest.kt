package moe.antimony.hoshi.features.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import moe.antimony.hoshi.features.dictionary.DictionaryLanguage
import moe.antimony.hoshi.features.dictionary.DictionarySettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsBackupTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun preferencesBackupRoundTripsAllValueTypes() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            val source = dataStore(scope, "source.preferences_pb")
            source.edit { preferences ->
                preferences[booleanPreferencesKey("flag")] = true
                preferences[intPreferencesKey("count")] = 42
                preferences[longPreferencesKey("timestamp")] = 9_000_000_000L
                preferences[floatPreferencesKey("ratio")] = 1.5f
                preferences[doublePreferencesKey("precise")] = 2.5
                preferences[stringPreferencesKey("name")] = "ja"
                preferences[stringSetPreferencesKey("enabled")] = setOf("a", "b")
            }

            val exported = PreferencesBackup.export(source)

            val restored = dataStore(scope, "restored.preferences_pb")
            PreferencesBackup.import(restored, exported)
            val preferences = restored.data.first()

            assertEquals(true, preferences[booleanPreferencesKey("flag")])
            assertEquals(42, preferences[intPreferencesKey("count")])
            assertEquals(9_000_000_000L, preferences[longPreferencesKey("timestamp")])
            assertEquals(1.5f, preferences[floatPreferencesKey("ratio")])
            assertEquals(2.5, preferences[doublePreferencesKey("precise")])
            assertEquals("ja", preferences[stringPreferencesKey("name")])
            assertEquals(setOf("a", "b"), preferences[stringSetPreferencesKey("enabled")])
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun dictionarySettingsRoundTripPreservesLookupLanguage() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            val source = DictionarySettingsRepository(dataStore(scope, "dictionary-source.preferences_pb"))
            source.update { it.copy(lookupLanguage = DictionaryLanguage.French) }

            val exported = source.exportEntries()

            val target = DictionarySettingsRepository(dataStore(scope, "dictionary-target.preferences_pb"))
            target.importEntries(exported)

            assertEquals(DictionaryLanguage.French, target.settings.first().lookupLanguage)
        } finally {
            scope.cancel()
        }
    }

    private fun dataStore(scope: CoroutineScope, fileName: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile(fileName) },
        )
}
