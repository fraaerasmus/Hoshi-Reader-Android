package moe.antimony.hoshi.features.kosync

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import moe.antimony.hoshi.di.IoDispatcher

interface KosyncApi {
    suspend fun authorize(credentials: KosyncCredentials)

    suspend fun getProgress(credentials: KosyncCredentials, document: String): KosyncRemoteProgress?

    /** Returns the server-assigned timestamp (unix seconds) when the server reports one. */
    suspend fun putProgress(
        credentials: KosyncCredentials,
        document: String,
        progress: String,
        percentage: Double,
        device: String,
        deviceId: String,
    ): Long?
}

/** KOReader progress sync protocol (`plugins/kosync.koplugin/api.json`) over plain HttpURLConnection. */
@Singleton
class KosyncClient @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : KosyncApi {
    override suspend fun authorize(credentials: KosyncCredentials) {
        request(credentials, "GET", "/users/auth")
    }

    override suspend fun getProgress(credentials: KosyncCredentials, document: String): KosyncRemoteProgress? {
        val body = request(credentials, "GET", "/syncs/progress/${URLEncoder.encode(document, "UTF-8")}", notFoundIsNull = true)
            ?: return null
        return KosyncRemoteProgress(
            document = body.string("document") ?: document,
            progress = body.string("progress"),
            percentage = body["percentage"]?.jsonPrimitive?.doubleOrNull,
            device = body.string("device"),
            deviceId = body.string("device_id"),
            timestamp = body["timestamp"]?.jsonPrimitive?.longOrNull,
        )
    }

    override suspend fun putProgress(
        credentials: KosyncCredentials,
        document: String,
        progress: String,
        percentage: Double,
        device: String,
        deviceId: String,
    ): Long? {
        val payload = buildJsonObject {
            put("document", document)
            put("progress", progress)
            put("percentage", percentage)
            put("device", device)
            put("device_id", deviceId)
        }
        return request(credentials, "PUT", "/syncs/progress", payload)
            ?.get("timestamp")?.jsonPrimitive?.longOrNull
    }

    private suspend fun request(
        credentials: KosyncCredentials,
        method: String,
        path: String,
        payload: JsonObject? = null,
        notFoundIsNull: Boolean = false,
    ): JsonObject? = withContext(ioDispatcher) {
        val connection = URL(normalizeServerUrl(credentials.serverUrl) + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = ConnectTimeoutMillis
            connection.readTimeout = ReadTimeoutMillis
            connection.setRequestProperty("Accept", "application/vnd.koreader.v1+json")
            connection.setRequestProperty("x-auth-user", credentials.username)
            connection.setRequestProperty("x-auth-key", credentials.userKey)
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                .orEmpty()
            when {
                status in 200..299 -> runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                    ?: if (text.isBlank()) JsonObject(emptyMap()) else throw KosyncException("Unexpected response from server.", status)
                status == 404 && notFoundIsNull -> null
                status == 401 -> throw KosyncException("Incorrect username or password.", status)
                status == 403 -> throw KosyncException("Unknown user.", status)
                else -> throw KosyncException("Server returned HTTP $status.", status)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        private const val ConnectTimeoutMillis = 10_000
        private const val ReadTimeoutMillis = 15_000
        private val json = Json { ignoreUnknownKeys = true }

        fun normalizeServerUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            return if (trimmed.contains("://")) trimmed else "http://$trimmed"
        }
    }
}
