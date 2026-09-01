package moe.antimony.hoshi.features.kosync

import kotlinx.serialization.Serializable

data class KosyncSettings(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val username: String = "",
    val autoSyncEnabled: Boolean = true,
    val pushEnabled: Boolean = true,
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank()
}

data class KosyncCredentials(
    val serverUrl: String,
    val username: String,
    val userKey: String,
)

data class KosyncRemoteProgress(
    val document: String,
    val progress: String?,
    val percentage: Double?,
    val device: String?,
    val deviceId: String?,
    /** Server-assigned, unix seconds. */
    val timestamp: Long?,
)

sealed interface KosyncResult {
    data class Pulled(val title: String, val percentage: Double) : KosyncResult
    data class Pushed(val title: String, val percentage: Double) : KosyncResult
    data class UpToDate(val title: String) : KosyncResult
    data object Skipped : KosyncResult
}

/** Per-book kosync bookkeeping, stored as `kosync.json` beside `bookmark.json`. */
@Serializable
data class KosyncBookState(
    val lastSyncedCharacterCount: Int? = null,
    val lastServerTimestamp: Long? = null,
)

class KosyncException(message: String, val statusCode: Int? = null) : Exception(message)
