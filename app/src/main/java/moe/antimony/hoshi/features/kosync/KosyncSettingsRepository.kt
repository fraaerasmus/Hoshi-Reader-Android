package moe.antimony.hoshi.features.kosync

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import moe.antimony.hoshi.features.backup.PreferencesBackup

private val Context.kosyncSettingsDataStore by preferencesDataStore(name = KosyncSettingsRepository.DataStoreName)

fun Context.kosyncSettingsRepository(): KosyncSettingsRepository =
    KosyncSettingsRepository(
        dataStore = kosyncSettingsDataStore,
        credentialPreferences = applicationContext.getSharedPreferences(KosyncSettingsRepository.CredentialsName, Context.MODE_PRIVATE),
    )

/**
 * kosync toggles live in their own DataStore; the hashed password (KOReader's `userkey`, md5 of the
 * password) and this install's device id live in a private SharedPreferences, exported with the
 * settings backup like the Drive credentials.
 */
class KosyncSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val credentialPreferences: SharedPreferences,
) {
    val settings: Flow<KosyncSettings> = dataStore.data.map { it.toKosyncSettings() }

    suspend fun update(transform: (KosyncSettings) -> KosyncSettings) {
        dataStore.edit { preferences ->
            preferences.writeKosyncSettings(transform(preferences.toKosyncSettings()))
        }
    }

    suspend fun saveLogin(serverUrl: String, username: String, password: String) {
        credentialPreferences.edit().putString(UserKeyKey, md5(password)).apply()
        update { it.copy(serverUrl = serverUrl.trim(), username = username.trim()) }
    }

    fun hasUserKey(): Boolean = !credentialPreferences.getString(UserKeyKey, null).isNullOrBlank()

    suspend fun credentials(): KosyncCredentials? {
        val current = settings.first()
        val userKey = credentialPreferences.getString(UserKeyKey, null)?.takeIf { it.isNotBlank() } ?: return null
        if (!current.isConfigured) return null
        return KosyncCredentials(serverUrl = current.serverUrl, username = current.username, userKey = userKey)
    }

    val deviceId: String
        get() = credentialPreferences.getString(DeviceIdKey, null)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().replace("-", "").uppercase().also { generated ->
                credentialPreferences.edit().putString(DeviceIdKey, generated).apply()
            }

    suspend fun exportEntries(): JsonObject = PreferencesBackup.export(dataStore)

    suspend fun importEntries(entries: JsonObject) {
        PreferencesBackup.import(dataStore, entries)
    }

    fun exportCredentials(): JsonObject = buildJsonObject {
        credentialPreferences.getString(UserKeyKey, null)?.let { put(UserKeyKey, it) }
        credentialPreferences.getString(DeviceIdKey, null)?.let { put(DeviceIdKey, it) }
    }

    fun importCredentials(credentials: JsonObject) {
        val editor = credentialPreferences.edit()
        credentials[UserKeyKey]?.jsonPrimitive?.contentOrNull?.let { editor.putString(UserKeyKey, it) }
        credentials[DeviceIdKey]?.jsonPrimitive?.contentOrNull?.let { editor.putString(DeviceIdKey, it) }
        editor.apply()
    }

    private fun Preferences.toKosyncSettings(): KosyncSettings =
        KosyncSettings(
            enabled = this[KEY_ENABLED] ?: false,
            serverUrl = this[KEY_SERVER_URL].orEmpty(),
            username = this[KEY_USERNAME].orEmpty(),
            autoSyncEnabled = this[KEY_AUTO_SYNC] ?: true,
            pushEnabled = this[KEY_PUSH] ?: true,
        )

    private fun MutablePreferences.writeKosyncSettings(settings: KosyncSettings) {
        this[KEY_ENABLED] = settings.enabled
        this[KEY_SERVER_URL] = settings.serverUrl
        this[KEY_USERNAME] = settings.username
        this[KEY_AUTO_SYNC] = settings.autoSyncEnabled
        this[KEY_PUSH] = settings.pushEnabled
    }

    companion object {
        const val DataStoreName = "kosync-settings"
        const val CredentialsName = "kosync-credentials"
        private const val UserKeyKey = "userKey"
        private const val DeviceIdKey = "deviceId"

        private val KEY_ENABLED = booleanPreferencesKey("kosyncEnabled")
        private val KEY_SERVER_URL = stringPreferencesKey("kosyncServerUrl")
        private val KEY_USERNAME = stringPreferencesKey("kosyncUsername")
        private val KEY_AUTO_SYNC = booleanPreferencesKey("kosyncAutoSyncEnabled")
        private val KEY_PUSH = booleanPreferencesKey("kosyncPushEnabled")

        fun md5(text: String): String =
            MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
