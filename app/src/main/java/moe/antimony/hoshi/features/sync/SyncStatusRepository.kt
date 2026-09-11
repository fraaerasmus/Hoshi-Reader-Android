package moe.antimony.hoshi.features.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SyncBackend { Ttu, Kosync, RemoteBackup }

data class SyncStatus(
    val lastSyncAtMillis: Long? = null,
    val lastErrorAtMillis: Long? = null,
    val lastError: String? = null,
)

private val Context.syncStatusDataStore by preferencesDataStore(name = "sync-status")

fun Context.syncStatusRepository(): SyncStatusRepository = SyncStatusRepository(syncStatusDataStore)

/** Last successful sync / last error per backend, for the settings screens. Not part of the settings backup. */
class SyncStatusRepository(
    private val dataStore: DataStore<Preferences>,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun status(backend: SyncBackend): Flow<SyncStatus> = dataStore.data.map { preferences ->
        SyncStatus(
            lastSyncAtMillis = preferences[backend.key("lastSyncAt")],
            lastErrorAtMillis = preferences[backend.key("lastErrorAt")],
            lastError = preferences[backend.textKey("lastError")],
        )
    }

    suspend fun record(backend: SyncBackend, error: String?) {
        dataStore.edit { preferences ->
            if (error == null) {
                preferences[backend.key("lastSyncAt")] = nowMillis()
                preferences.remove(backend.key("lastErrorAt"))
                preferences.remove(backend.textKey("lastError"))
            } else {
                preferences[backend.key("lastErrorAt")] = nowMillis()
                preferences[backend.textKey("lastError")] = error
            }
        }
    }

    private fun SyncBackend.key(name: String) = longPreferencesKey("${this.name}.$name")
    private fun SyncBackend.textKey(name: String) = stringPreferencesKey("${this.name}.$name")
}
