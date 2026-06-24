package moe.antimony.hoshi.features.sasayaki

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

/** Identity of the book whose audio is the active "now playing" item (drives the mini-player). */
data class SasayakiNowPlaying(
    val bookId: String,
    val title: String,
    val coverFile: File?,
)

/** Live transport state, refreshed on every playback tick. */
data class SasayakiPlaybackSnapshot(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
)

/**
 * Process-scoped live transport state, written by the playback tick
 * ([SasayakiMediaSessionPublishingCoordinator]) and read via [SasayakiPlaybackHolder]. A simple
 * holder is enough: single process, one active book.
 */
internal object SasayakiPlaybackStatePublisher {
    val snapshot = MutableStateFlow(SasayakiPlaybackSnapshot())
}
