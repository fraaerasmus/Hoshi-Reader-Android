package moe.antimony.hoshi.features.opds

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpdsClient(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun fetchFeed(catalog: OpdsCatalog, url: String): OpdsFeed = withContext(ioDispatcher) {
        OpdsFeedParser.parseFeed(url, get(catalog, url).readBytesAndClose())
    }

    /** Resolves the feed's search template, fetching the OpenSearch description if needed. */
    suspend fun searchTemplate(catalog: OpdsCatalog, feed: OpdsFeed): String? = withContext(ioDispatcher) {
        feed.searchTemplate ?: feed.searchDescriptionHref?.let { href ->
            runCatching {
                OpdsFeedParser.parseOpenSearchDescription(href, get(catalog, href).readBytesAndClose())
            }.getOrNull()
        }
    }

    data class Download(val file: File, val fileName: String)

    /** Streams [url] to a temp file in [directory] untouched; the import path copies it verbatim. */
    suspend fun download(
        catalog: OpdsCatalog,
        url: String,
        directory: File,
        fallbackName: String,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): Download = withContext(ioDispatcher) {
        val connection = get(catalog, url)
        val target = File(directory, "opds-${System.nanoTime()}.epub")
        try {
            val total = connection.contentLengthLong.takeIf { it > 0 }
            var downloaded = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
            val name = (fileNameFromDisposition(connection.getHeaderField("Content-Disposition"))
                ?: url.substringBefore('?').substringAfterLast('/').takeIf { it.endsWith(".epub", ignoreCase = true) }
                ?: fallbackName)
                .replace('/', '_')
                .let { if (it.endsWith(".epub", ignoreCase = true)) it else "$it.epub" }
            Download(target, name)
        } catch (error: Exception) {
            target.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun get(catalog: OpdsCatalog, url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = ConnectTimeoutMillis
        connection.readTimeout = ReadTimeoutMillis
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/atom+xml, application/epub+zip, */*")
        if (catalog.username.isNotBlank()) {
            val token = Base64.getEncoder().encodeToString("${catalog.username}:${catalog.password}".toByteArray(Charsets.UTF_8))
            connection.setRequestProperty("Authorization", "Basic $token")
        }
        val status = connection.responseCode
        if (status !in 200..299) {
            connection.disconnect()
            throw OpdsException(
                when (status) {
                    401, 403 -> "Authentication failed (HTTP $status)."
                    404 -> "Not found (HTTP 404)."
                    else -> "Server returned HTTP $status."
                },
                status,
            )
        }
        return connection
    }

    private fun HttpURLConnection.readBytesAndClose(): ByteArray =
        try {
            inputStream.use { it.readBytes() }
        } finally {
            disconnect()
        }

    companion object {
        private const val ConnectTimeoutMillis = 15_000
        private const val ReadTimeoutMillis = 60_000

        fun fileNameFromDisposition(header: String?): String? {
            if (header.isNullOrBlank()) return null
            Regex("filename\\*\\s*=\\s*(?:UTF-8|utf-8)''([^;]+)").find(header)?.let { match ->
                return runCatching { URLDecoder.decode(match.groupValues[1].trim(), "UTF-8") }.getOrNull()
            }
            Regex("filename\\s*=\\s*\"([^\"]+)\"").find(header)?.let { return it.groupValues[1] }
            Regex("filename\\s*=\\s*([^;]+)").find(header)?.let { return it.groupValues[1].trim() }
            return null
        }
    }
}
