package moe.antimony.hoshi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import moe.antimony.hoshi.features.dictionary.PendingDictionaryLookupRequest
import moe.antimony.hoshi.features.reader.ReaderSettings
import moe.antimony.hoshi.features.reader.usesDarkInterface
import moe.antimony.hoshi.features.reader.usesDarkSystemBarIcons
import moe.antimony.hoshi.features.sasayaki.SasayakiPlaybackReturnAction
import moe.antimony.hoshi.features.sasayaki.SasayakiPlaybackReturnBookIdExtra
import moe.antimony.hoshi.features.update.DownloadedUpdatePrompt
import moe.antimony.hoshi.navigation.AppShell
import moe.antimony.hoshi.ui.theme.HoshiReaderTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject internal lateinit var uiDependencies: HoshiUiDependencies

    private var pendingImportUri by mutableStateOf<Uri?>(null)
    private var pendingSasayakiReaderBookId by mutableStateOf<String?>(null)
    private var pendingDictionaryLookupRequest by mutableStateOf<PendingDictionaryLookupRequest?>(null)
    private var dictionaryLookupRequestId = 0L
    private var readerKeyEventHandler: ((KeyEvent) -> Boolean)? = null
    private var readerGenericMotionHandler: ((MotionEvent) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        pendingImportUri = intent.importUri()
        pendingSasayakiReaderBookId = intent.sasayakiReaderBookIdOrActivePlayback()
        pendingDictionaryLookupRequest = intent.pendingDictionaryLookupRequest()
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val readerSettingsRepository = uiDependencies.readerSettingsRepository
            val scope = rememberCoroutineScope()
            var readerSettings by remember { mutableStateOf<ReaderSettings?>(null) }
            LaunchedEffect(readerSettingsRepository) {
                readerSettingsRepository.settings.collect { settings ->
                    readerSettings = settings
                }
            }
            val systemDark = isSystemInDarkTheme()
            val loadedReaderSettings = readerSettings
            LaunchedEffect(loadedReaderSettings?.lockCurrentOrientation) {
                val settings = loadedReaderSettings ?: return@LaunchedEffect
                requestedOrientation = requestedOrientationForLockCurrentOrientation(settings.lockCurrentOrientation)
            }
            val darkTheme = loadedReaderSettings?.usesDarkInterface(systemDark) ?: systemDark
            val useDarkSystemBarIcons = loadedReaderSettings?.usesDarkSystemBarIcons(systemDark) ?: !systemDark
            CompositionLocalProvider(LocalHoshiUiDependencies provides uiDependencies) {
                HoshiReaderTheme(
                    darkTheme = darkTheme,
                    eInkMode = loadedReaderSettings?.eInkMode ?: false,
                    useDarkSystemBarIcons = useDarkSystemBarIcons,
                ) {
                    val loadedReaderSettings = readerSettings ?: return@HoshiReaderTheme
                    AppShell(
                        pendingImportUri = pendingImportUri,
                        onPendingImportConsumed = { pendingImportUri = null },
                        pendingSasayakiReaderBookId = pendingSasayakiReaderBookId,
                        onPendingSasayakiReaderConsumed = { pendingSasayakiReaderBookId = null },
                        pendingDictionaryLookupRequest = pendingDictionaryLookupRequest,
                        onPendingDictionaryLookupConsumed = { pendingDictionaryLookupRequest = null },
                        readerSettings = loadedReaderSettings,
                        onReaderSettingsChange = { settings ->
                            readerSettings = settings
                            scope.launch {
                                readerSettingsRepository.update { settings }
                            }
                        },
                        onReaderKeyEventHandlerChange = { handler ->
                            readerKeyEventHandler = handler
                        },
                        onReaderGenericMotionHandlerChange = { handler ->
                            readerGenericMotionHandler = handler
                        }
                    )
                    DownloadedUpdatePrompt()
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (readerKeyEventHandler?.invoke(event) == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        // Like dispatchKeyEvent, runs before the WebView so reader hover survives a split-screen
        // refocus that stops DOM mousemove. Non-consuming: it only reads the cursor position.
        if (readerGenericMotionHandler?.invoke(event) == true) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    // The Sasayaki foreground playback notification needs POST_NOTIFICATIONS on API 33+. Playback
    // still runs if denied — only the notification is hidden.
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.importUri()?.let { pendingImportUri = it }
        intent.sasayakiReaderBookIdOrActivePlayback()?.let { pendingSasayakiReaderBookId = it }
        intent.pendingDictionaryLookupRequest()?.let { pendingDictionaryLookupRequest = it }
    }

    private fun Intent?.importUri(): Uri? =
        this?.data?.takeIf { action == Intent.ACTION_VIEW }

    private fun Intent?.sasayakiReaderBookId(): String? =
        this?.getStringExtra(SasayakiPlaybackReturnBookIdExtra)
            ?.takeIf { action == SasayakiPlaybackReturnAction && it.isNotBlank() }

    private fun Intent?.sasayakiReaderBookIdOrActivePlayback(): String? =
        sasayakiReaderBookId()
            ?: takeIf { it?.action == Intent.ACTION_MAIN }
                ?.let { uiDependencies.sasayakiPlaybackServiceRuntime.activePlaybackBookId() }

    private fun Intent?.pendingDictionaryLookupRequest(): PendingDictionaryLookupRequest? {
        val nextRequestId = dictionaryLookupRequestId + 1L
        return PendingDictionaryLookupRequest.fromIntent(this, nextRequestId)?.also {
            dictionaryLookupRequestId = nextRequestId
        }
    }
}

internal fun requestedOrientationForLockCurrentOrientation(lockCurrentOrientation: Boolean): Int =
    if (lockCurrentOrientation) {
        ActivityInfo.SCREEN_ORIENTATION_LOCKED
    } else {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
