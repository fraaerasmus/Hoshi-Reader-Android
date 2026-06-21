package moe.antimony.hoshi.features.backup

import kotlinx.serialization.json.JsonObject

/**
 * Fork-owned backup seam. Concrete settings repositories implement this so SettingsBackupRepository
 * can reach their DataStore without adding backup methods to upstream-owned interfaces — which
 * would break upstream's test fakes on each merge.
 */
interface PreferenceBackupStore {
    suspend fun exportEntries(): JsonObject
    suspend fun importEntries(entries: JsonObject)
}
