package moe.antimony.hoshi.features.backup

import kotlinx.serialization.json.JsonObject

/**
 * Fork-owned seam for the settings backup feature. Concrete settings repositories implement
 * this so [SettingsBackupRepository] can read and restore their DataStore contents without
 * adding backup methods to upstream-owned interfaces. Keeping the hook off upstream interfaces
 * means upstream's test fakes (which implement those interfaces) keep compiling across merges.
 *
 * Most settings repositories are concrete classes and can expose [exportEntries]/[importEntries]
 * directly; route any that are upstream-owned *interfaces* (e.g. AnkiSettingsRepository) through
 * this marker on their concrete implementation instead.
 */
interface PreferenceBackupStore {
    suspend fun exportEntries(): JsonObject
    suspend fun importEntries(entries: JsonObject)
}
