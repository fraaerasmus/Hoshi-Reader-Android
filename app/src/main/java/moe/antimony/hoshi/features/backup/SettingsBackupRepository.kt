package moe.antimony.hoshi.features.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import moe.antimony.hoshi.BuildConfig
import moe.antimony.hoshi.di.IoDispatcher
import moe.antimony.hoshi.features.audio.AudioSettingsRepository
import moe.antimony.hoshi.features.bookshelf.BookshelfSettingsRepository
import moe.antimony.hoshi.features.dictionary.DictionarySettingsRepository
import moe.antimony.hoshi.features.reader.ReaderSettingsRepository
import moe.antimony.hoshi.features.sasayaki.SasayakiSettingsRepository
import moe.antimony.hoshi.features.sync.DeviceCodeDriveAuthorizer
import moe.antimony.hoshi.features.kosync.KosyncSettingsRepository
import moe.antimony.hoshi.features.sync.SyncSettingsRepository
import moe.antimony.hoshi.features.update.UpdateSettingsRepository
import moe.antimony.hoshi.profiles.ProfileRepository

/**
 * Exports and restores app settings as a single JSON file. Settings live across several
 * DataStore Preferences stores plus the Google Drive credential SharedPreferences; this
 * composes them into one versioned envelope. The export deliberately includes credentials
 * (Drive client id/secret/tokens, AnkiConnect URL) so a fresh install can fully take over,
 * so the resulting file is sensitive and should be kept private.
 */
@Singleton
class SettingsBackupRepository @Inject constructor(
    private val readerSettingsRepository: ReaderSettingsRepository,
    private val dictionarySettingsRepository: DictionarySettingsRepository,
    private val audioSettingsRepository: AudioSettingsRepository,
    private val sasayakiSettingsRepository: SasayakiSettingsRepository,
    private val ankiBackupStore: PreferenceBackupStore,
    private val bookshelfSettingsRepository: BookshelfSettingsRepository,
    private val syncSettingsRepository: SyncSettingsRepository,
    private val kosyncSettingsRepository: KosyncSettingsRepository,
    private val updateSettingsRepository: UpdateSettingsRepository,
    private val driveAuthorizer: DeviceCodeDriveAuthorizer,
    private val profileRepository: ProfileRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun exportSettings(contentResolver: ContentResolver, uri: Uri) {
        withContext(ioDispatcher) {
            val envelope = buildEnvelope()
            val bytes = JSON.encodeToString(JsonElement.serializer(), envelope).toByteArray()
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
            } ?: error("Unable to open settings backup destination.")
        }
    }

    suspend fun importSettings(contentResolver: ContentResolver, uri: Uri) {
        withContext(ioDispatcher) {
            val text = contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().decodeToString()
            } ?: error("Unable to open settings backup file.")
            applyEnvelope(JSON.parseToJsonElement(text).jsonObject)
        }
    }

    private suspend fun buildEnvelope(): JsonObject {
        // Gather suspend results before composing the (non-suspend) JSON builders.
        val reader = readerSettingsRepository.exportEntries()
        val dictionary = dictionarySettingsRepository.exportEntries()
        val audio = audioSettingsRepository.exportEntries()
        val sasayaki = sasayakiSettingsRepository.exportEntries()
        val anki = ankiBackupStore.exportEntries()
        val bookshelf = bookshelfSettingsRepository.exportEntries()
        val sync = syncSettingsRepository.exportEntries()
        val kosync = kosyncSettingsRepository.exportEntries()
        val update = updateSettingsRepository.exportEntries()
        val driveCredentials = driveAuthorizer.exportCredentials()
        val profiles = profileRepository.exportProfilesBackup()
        return buildJsonObject {
            put(KEY_SCHEMA, SCHEMA)
            put(KEY_VERSION, VERSION)
            put(KEY_APP_VERSION, BuildConfig.VERSION_NAME)
            put(KEY_EXPORTED_AT, Instant.now().toString())
            put(
                KEY_STORES,
                buildJsonObject {
                    put(STORE_READER, reader)
                    put(STORE_DICTIONARY, dictionary)
                    put(STORE_AUDIO, audio)
                    put(STORE_SASAYAKI, sasayaki)
                    put(STORE_ANKI, anki)
                    put(STORE_BOOKSHELF, bookshelf)
                    put(STORE_SYNC, sync)
                    put(STORE_KOSYNC, kosync)
                    put(STORE_UPDATE, update)
                },
            )
            put(
                KEY_CREDENTIALS,
                buildJsonObject {
                    put(CREDENTIAL_DRIVE, driveCredentials)
                    put(CREDENTIAL_KOSYNC, kosyncSettingsRepository.exportCredentials())
                },
            )
            put(KEY_PROFILES, profiles)
        }
    }

    private suspend fun applyEnvelope(envelope: JsonObject) {
        val stores = envelope[KEY_STORES]?.jsonObject ?: JsonObject(emptyMap())
        stores.store(STORE_READER)?.let { readerSettingsRepository.importEntries(it) }
        stores.store(STORE_DICTIONARY)?.let { dictionarySettingsRepository.importEntries(it) }
        stores.store(STORE_AUDIO)?.let { audioSettingsRepository.importEntries(it) }
        stores.store(STORE_SASAYAKI)?.let { sasayakiSettingsRepository.importEntries(it) }
        stores.store(STORE_ANKI)?.let { ankiBackupStore.importEntries(it) }
        stores.store(STORE_BOOKSHELF)?.let { bookshelfSettingsRepository.importEntries(it) }
        stores.store(STORE_SYNC)?.let { syncSettingsRepository.importEntries(it) }
        stores.store(STORE_KOSYNC)?.let { kosyncSettingsRepository.importEntries(it) }
        stores.store(STORE_UPDATE)?.let { updateSettingsRepository.importEntries(it) }

        envelope[KEY_CREDENTIALS]?.jsonObject?.store(CREDENTIAL_DRIVE)
            ?.let { driveAuthorizer.importCredentials(it) }
        envelope[KEY_CREDENTIALS]?.jsonObject?.store(CREDENTIAL_KOSYNC)
            ?.let { kosyncSettingsRepository.importCredentials(it) }

        envelope[KEY_PROFILES]?.jsonObject?.let { profileRepository.importProfilesBackup(it) }
    }

    private fun JsonObject.store(name: String): JsonObject? = this[name]?.jsonObject

    private companion object {
        const val SCHEMA = "hoshi-settings"
        const val VERSION = 2

        const val KEY_SCHEMA = "schema"
        const val KEY_VERSION = "version"
        const val KEY_APP_VERSION = "appVersionName"
        const val KEY_EXPORTED_AT = "exportedAt"
        const val KEY_STORES = "stores"
        const val KEY_CREDENTIALS = "credentials"
        const val KEY_PROFILES = "profiles"

        const val STORE_READER = "reader"
        const val STORE_DICTIONARY = "dictionary"
        const val STORE_AUDIO = "audio"
        const val STORE_SASAYAKI = "sasayaki"
        const val STORE_ANKI = "anki"
        const val STORE_BOOKSHELF = "bookshelf"
        const val STORE_SYNC = "sync"
        const val STORE_KOSYNC = "kosync"
        const val STORE_UPDATE = "update"
        const val CREDENTIAL_DRIVE = "drive"
        const val CREDENTIAL_KOSYNC = "kosync"

        val JSON = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/**
 * Serializes a [DataStore] of [Preferences] to a JSON object and back, preserving each
 * entry's value type. Keeps each settings repository in control of its own store while
 * sharing the type handling.
 */
object PreferencesBackup {
    private const val TYPE = "t"
    private const val VALUE = "v"

    suspend fun export(dataStore: DataStore<Preferences>): JsonObject {
        val preferences = dataStore.data.first()
        return buildJsonObject {
            preferences.asMap().forEach { (key, value) ->
                put(key.name, encode(value))
            }
        }
    }

    suspend fun import(dataStore: DataStore<Preferences>, entries: JsonObject) {
        dataStore.edit { preferences ->
            entries.forEach { (name, element) ->
                val entry = element as? JsonObject ?: return@forEach
                apply(preferences, name, entry)
            }
        }
    }

    private fun encode(value: Any?): JsonObject = when (value) {
        is Boolean -> typed("bool", JsonPrimitive(value))
        is Int -> typed("int", JsonPrimitive(value))
        is Long -> typed("long", JsonPrimitive(value))
        is Float -> typed("float", JsonPrimitive(value))
        is Double -> typed("double", JsonPrimitive(value))
        is String -> typed("string", JsonPrimitive(value))
        is Set<*> -> typed(
            "stringSet",
            buildJsonArray { value.forEach { add(JsonPrimitive(it.toString())) } },
        )
        else -> typed("string", JsonPrimitive(value.toString()))
    }

    private fun typed(type: String, value: JsonElement): JsonObject = buildJsonObject {
        put(TYPE, type)
        put(VALUE, value)
    }

    private fun apply(preferences: MutablePreferences, name: String, entry: JsonObject) {
        val type = entry[TYPE]?.jsonPrimitive?.content ?: return
        val value = entry[VALUE] ?: return
        when (type) {
            "bool" -> preferences[booleanPreferencesKey(name)] = value.jsonPrimitive.boolean
            "int" -> preferences[intPreferencesKey(name)] = value.jsonPrimitive.int
            "long" -> preferences[longPreferencesKey(name)] = value.jsonPrimitive.long
            "float" -> preferences[floatPreferencesKey(name)] = value.jsonPrimitive.float
            "double" -> preferences[doublePreferencesKey(name)] = value.jsonPrimitive.double
            "string" -> preferences[stringPreferencesKey(name)] = value.jsonPrimitive.content
            "stringSet" ->
                preferences[stringSetPreferencesKey(name)] =
                    (value as? JsonArray ?: value.jsonArray).map { it.jsonPrimitive.content }.toSet()
        }
    }
}

fun settingsBackupFileName(
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val timestamp = DateTimeFormatter
        .ofPattern("yyyy-MM-dd_HH-mm-ss")
        .withZone(zoneId)
        .format(now)
    return "Settings_$timestamp.json"
}
