package moe.antimony.hoshi.features.backup

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class RemoteBackupSettings(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val username: String = "",
    val deviceName: String = "",
    val autoBackup: Boolean = true,
    val backupBookState: Boolean = true,
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank()
}

data class RemoteBackupCredentials(val serverUrl: String, val username: String, val password: String)

/** What the manager remembers between runs so unchanged data is not re-uploaded; never exported. */
interface RemoteBackupBookkeeping {
    var settingsFingerprint: String?
    fun bookStamp(key: String): Long?
    fun setBookStamp(key: String, stamp: Long)
}

private val Context.remoteBackupSettingsDataStore by preferencesDataStore(name = RemoteBackupSettingsRepository.DataStoreName)

fun Context.remoteBackupSettingsRepository(): RemoteBackupSettingsRepository =
    RemoteBackupSettingsRepository(
        dataStore = remoteBackupSettingsDataStore,
        privatePreferences = applicationContext.getSharedPreferences(RemoteBackupSettingsRepository.PrivateName, Context.MODE_PRIVATE),
        defaultDeviceName = Build.MODEL,
    )

/**
 * WebDAV backup toggles live in their own DataStore (so they ride the settings export); the password and
 * the upload bookkeeping live in a private SharedPreferences, of which only the password is exported.
 */
class RemoteBackupSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val privatePreferences: SharedPreferences,
    private val defaultDeviceName: String,
) : RemoteBackupBookkeeping {
    val settings: Flow<RemoteBackupSettings> = dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (RemoteBackupSettings) -> RemoteBackupSettings) {
        dataStore.edit { preferences -> preferences.write(transform(preferences.toSettings())) }
    }

    suspend fun saveLogin(serverUrl: String, username: String, password: String?, deviceName: String) {
        if (password != null) privatePreferences.edit().putString(PasswordKey, password).apply()
        update { it.copy(serverUrl = serverUrl.trim(), username = username.trim(), deviceName = deviceName.trim()) }
    }

    suspend fun credentials(): RemoteBackupCredentials? {
        val current = settings.first()
        if (!current.isConfigured) return null
        return RemoteBackupCredentials(
            serverUrl = current.serverUrl,
            username = current.username,
            password = privatePreferences.getString(PasswordKey, null).orEmpty(),
        )
    }

    fun hasPassword(): Boolean = !privatePreferences.getString(PasswordKey, null).isNullOrEmpty()

    override var settingsFingerprint: String?
        get() = privatePreferences.getString(FingerprintKey, null)
        set(value) {
            privatePreferences.edit().putString(FingerprintKey, value).apply()
        }

    override fun bookStamp(key: String): Long? =
        privatePreferences.getLong("$BookStampPrefix$key", -1L).takeIf { it >= 0L }

    override fun setBookStamp(key: String, stamp: Long) {
        privatePreferences.edit().putLong("$BookStampPrefix$key", stamp).apply()
    }

    suspend fun exportEntries(): JsonObject = PreferencesBackup.export(dataStore)

    suspend fun importEntries(entries: JsonObject) {
        PreferencesBackup.import(dataStore, entries)
    }

    fun exportCredentials(): JsonObject = buildJsonObject {
        privatePreferences.getString(PasswordKey, null)?.let { put(PasswordKey, it) }
    }

    fun importCredentials(credentials: JsonObject) {
        credentials[PasswordKey]?.jsonPrimitive?.contentOrNull?.let {
            privatePreferences.edit().putString(PasswordKey, it).apply()
        }
    }

    private fun Preferences.toSettings(): RemoteBackupSettings =
        RemoteBackupSettings(
            enabled = this[KEY_ENABLED] ?: false,
            serverUrl = this[KEY_SERVER_URL].orEmpty(),
            username = this[KEY_USERNAME].orEmpty(),
            deviceName = this[KEY_DEVICE_NAME]?.takeIf { it.isNotBlank() } ?: defaultDeviceName,
            autoBackup = this[KEY_AUTO_BACKUP] ?: true,
            backupBookState = this[KEY_BOOK_STATE] ?: true,
        )

    private fun MutablePreferences.write(settings: RemoteBackupSettings) {
        this[KEY_ENABLED] = settings.enabled
        this[KEY_SERVER_URL] = settings.serverUrl
        this[KEY_USERNAME] = settings.username
        this[KEY_DEVICE_NAME] = settings.deviceName
        this[KEY_AUTO_BACKUP] = settings.autoBackup
        this[KEY_BOOK_STATE] = settings.backupBookState
    }

    companion object {
        const val DataStoreName = "remote-backup-settings"
        const val PrivateName = "remote-backup-private"
        private const val PasswordKey = "password"
        private const val FingerprintKey = "settingsFingerprint"
        private const val BookStampPrefix = "book."

        private val KEY_ENABLED = booleanPreferencesKey("remoteBackupEnabled")
        private val KEY_SERVER_URL = stringPreferencesKey("remoteBackupServerUrl")
        private val KEY_USERNAME = stringPreferencesKey("remoteBackupUsername")
        private val KEY_DEVICE_NAME = stringPreferencesKey("remoteBackupDeviceName")
        private val KEY_AUTO_BACKUP = booleanPreferencesKey("remoteBackupAuto")
        private val KEY_BOOK_STATE = booleanPreferencesKey("remoteBackupBookState")
    }
}
