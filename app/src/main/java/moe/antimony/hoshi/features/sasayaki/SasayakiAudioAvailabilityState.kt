package moe.antimony.hoshi.features.sasayaki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import moe.antimony.hoshi.R
import moe.antimony.hoshi.ui.UiText

class SasayakiAudioAvailabilityState(
    initialHasAudio: Boolean = false,
) {
    var errorMessage by mutableStateOf<UiText?>(null)
        private set

    var hasAudio by mutableStateOf(initialHasAudio)
        private set

    fun markRestoreFailed(error: Throwable) {
        errorMessage = sasayakiRestoreFailureMessage(error)?.let(UiText::Literal)
            ?: UiText.Resource(R.string.sasayaki_import_audiobook_failed)
        hasAudio = false
    }

    fun markRestoreSucceeded() {
        markAudioAvailable()
    }

    fun markAudioAvailable() {
        hasAudio = true
        errorMessage = null
    }

    fun markAudioCleared() {
        hasAudio = false
        errorMessage = null
    }

    fun markAudioUnavailable() {
        hasAudio = false
    }
}

/** Media3 reports "Source error" for everything; append the root cause so a 66 h m4b OOM is identifiable. */
internal fun sasayakiRestoreFailureMessage(error: Throwable): String? {
    val message = error.localizedMessage
    val root = generateSequence(error.cause) { it.cause }.lastOrNull() ?: return message
    val rootText = "${root::class.java.simpleName}: ${root.localizedMessage ?: ""}".trimEnd(':', ' ')
    return if (message == null) rootText else if (message.contains(rootText)) message else "$message — $rootText"
}
