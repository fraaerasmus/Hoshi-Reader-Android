package moe.antimony.hoshi.features.backup

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.di.IoDispatcher
import moe.antimony.hoshi.features.sync.NetworkPreflight

/** The three verbs the backup needs; a WebDAV share is one implementation, tests use a map. */
interface RemoteBackupStore {
    suspend fun put(credentials: RemoteBackupCredentials, path: String, bytes: ByteArray)

    /** null when the file does not exist. */
    suspend fun get(credentials: RemoteBackupCredentials, path: String): ByteArray?

    /** Creates one folder whose parent already exists; a folder that is already there is not an error. */
    suspend fun mkcol(credentials: RemoteBackupCredentials, path: String)
}

class RemoteBackupException(message: String, val statusCode: Int? = null) : IOException(message)

/**
 * PUT/GET/MKCOL over `HttpURLConnection`. WebDAV servers (rclone included) refuse a PUT whose parent
 * folder is missing, so folders are created with MKCOL first; `HttpURLConnection` only knows the classic
 * verbs, so MKCOL is set through the protected `method` field. The app keeps its own index, so PROPFIND
 * is never needed.
 */
@Singleton
class WebDavClient @Inject constructor(
    private val preflight: NetworkPreflight,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RemoteBackupStore {
    override suspend fun put(credentials: RemoteBackupCredentials, path: String, bytes: ByteArray) {
        request(credentials, "PUT", path, bytes)
    }

    override suspend fun get(credentials: RemoteBackupCredentials, path: String): ByteArray? =
        request(credentials, "GET", path, null)

    override suspend fun mkcol(credentials: RemoteBackupCredentials, path: String) {
        request(credentials, "MKCOL", path, null)
    }

    private suspend fun request(credentials: RemoteBackupCredentials, method: String, path: String, body: ByteArray?): ByteArray? =
        withContext(ioDispatcher) {
            val url = URL(webDavUrl(credentials.serverUrl, path))
            preflight.check(url.host)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.setMethodIncludingWebDav(method)
                connection.connectTimeout = ConnectTimeoutMillis
                connection.readTimeout = ReadTimeoutMillis
                connection.instanceFollowRedirects = false
                if (credentials.username.isNotBlank()) {
                    val token = Base64.getEncoder().encodeToString("${credentials.username}:${credentials.password}".toByteArray(Charsets.UTF_8))
                    connection.setRequestProperty("Authorization", "Basic $token")
                }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/octet-stream")
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { it.write(body) }
                }
                val status = connection.responseCode
                when {
                    status in 200..299 -> if (method == "GET") connection.inputStream.use { it.readBytes() } else ByteArray(0)
                    status == 404 && method == "GET" -> null
                    // 405 is "collection already exists" on most WebDAV servers (rclone answers 201 either way).
                    status == 405 && method == "MKCOL" -> ByteArray(0)
                    status == 401 || status == 403 -> throw RemoteBackupException("Authentication failed (HTTP $status).", status)
                    else -> throw RemoteBackupException("Server returned HTTP $status.", status)
                }
            } finally {
                connection.disconnect()
            }
        }

    private fun HttpURLConnection.setMethodIncludingWebDav(method: String) {
        try {
            requestMethod = method
        } catch (error: java.net.ProtocolException) {
            // MKCOL is not in HttpURLConnection's fixed verb list; the base class stores the verb in a protected field.
            runCatching {
                HttpURLConnection::class.java.getDeclaredField("method").apply { isAccessible = true }.set(this, method)
            }.getOrElse { throw RemoteBackupException("This device cannot create folders on the server ($method unsupported).") }
        }
    }

    companion object {
        private const val ConnectTimeoutMillis = 3_000
        private const val ReadTimeoutMillis = 20_000

        /** `serverUrl` may or may not carry a scheme or trailing slash; `path` is share-relative. */
        fun webDavUrl(serverUrl: String, path: String): String {
            val base = serverUrl.trim().trimEnd('/').let { if (it.contains("://")) it else "http://$it" }
            val encoded = path.trimStart('/').split('/').joinToString("/") { segment ->
                java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
            return "$base/$encoded"
        }
    }
}
