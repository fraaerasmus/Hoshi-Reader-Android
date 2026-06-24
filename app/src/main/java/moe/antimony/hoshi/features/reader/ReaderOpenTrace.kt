package moe.antimony.hoshi.features.reader

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.Toast

/**
 * TEMPORARY book-open latency tracing (pre-release diagnostics — remove before stable).
 * Marks the tap, route-load, WebView creation, loadUrl, and text-visible milestones, then surfaces
 * the per-phase breakdown as a Toast + logcat (tag "HoshiOpen") so we can find the real bottleneck.
 */
object ReaderOpenTrace {
    @Volatile private var clickAt = 0L
    @Volatile private var readyAt = 0L
    @Volatile private var webViewAt = 0L
    @Volatile private var loadUrlAt = 0L

    fun markClick() {
        clickAt = SystemClock.elapsedRealtime()
        readyAt = 0L
        webViewAt = 0L
        loadUrlAt = 0L
    }

    fun markReady() {
        if (clickAt != 0L && readyAt == 0L) readyAt = SystemClock.elapsedRealtime()
    }

    fun markWebViewCreated() {
        if (clickAt != 0L && webViewAt == 0L) webViewAt = SystemClock.elapsedRealtime()
    }

    fun markLoadUrl() {
        if (clickAt != 0L && loadUrlAt == 0L) loadUrlAt = SystemClock.elapsedRealtime()
    }

    fun markVisible(context: Context) {
        val start = clickAt
        if (start == 0L) return
        val now = SystemClock.elapsedRealtime()
        fun delta(end: Long, begin: Long) = if (end > 0L && begin > 0L) "${end - begin}" else "?"
        val msg = "open ${now - start}ms: route=${delta(readyAt, start)} " +
            "create=${delta(webViewAt, readyAt)} vp=${delta(loadUrlAt, webViewAt)} " +
            "render=${if (loadUrlAt > 0L) "${now - loadUrlAt}" else "?"}"
        Log.d("HoshiOpen", msg)
        Toast.makeText(context.applicationContext, msg, Toast.LENGTH_LONG).show()
        clickAt = 0L
    }
}
