package moe.antimony.hoshi.features.sasayaki

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.epub.BookEntry
import moe.antimony.hoshi.epub.BookRepository

/** Moves the audiobook resume point to the cue under the text bookmark (reader open, kosync/Drive pulls). */
@Singleton
internal class SasayakiPositionSync @Inject constructor(
    private val bookRepository: BookRepository,
    private val runtime: SasayakiPlaybackServiceRuntime,
) {
    /** Where the text sits under the audiobook's current position: the live controller if there is one, else the sidecar. */
    suspend fun readerPositionAtAudio(entry: BookEntry): Pair<Int, Double>? {
        val match = bookRepository.loadSasayakiMatch(entry.root)?.takeIf { it.matches.isNotEmpty() } ?: return null
        val bookInfo = bookRepository.loadBookInfo(entry.root) ?: return null
        val live = runtime.activePlayback(entry.metadata.id)
        val cue = if (live != null) {
            SasayakiPositionBridge.cueAtAudioTime(match, live.currentTime, live.delay)
        } else {
            val playback = bookRepository.loadSasayakiPlayback(entry.root) ?: return null
            SasayakiPositionBridge.cueAtAudioTime(match, playback.lastPosition, playback.delay)
        } ?: return null
        return SasayakiPositionBridge.readerPositionForCue(cue, bookInfo)
    }

    /** Returns true when the audio position was moved. Audio that is currently playing is left alone. */
    suspend fun alignAudioToBookmark(entry: BookEntry): Boolean {
        val bookmark = bookRepository.loadBookmark(entry.root) ?: return false
        val match = bookRepository.loadSasayakiMatch(entry.root)?.takeIf { it.matches.isNotEmpty() } ?: return false
        val bookInfo = bookRepository.loadBookInfo(entry.root) ?: return false
        val target = SasayakiPositionBridge.cueForBookmark(match, bookInfo, bookmark) ?: return false

        val live = runtime.activePlayback(entry.metadata.id)
        if (live != null) {
            if (live.isPlaying) return false
            val current = SasayakiPositionBridge.cueAtAudioTime(match, live.currentTime, live.delay)
            if (current?.id == target.id) return false
            withContext(Dispatchers.Main.immediate) { live.seekTo(target.startTime + live.delay) }
            return true
        }

        val playback = bookRepository.loadSasayakiPlayback(entry.root) ?: return false
        if (playback.audioUri == null && playback.audioFileName == null) return false
        val current = SasayakiPositionBridge.cueAtAudioTime(match, playback.lastPosition, playback.delay)
        if (current?.id == target.id) return false
        bookRepository.saveSasayakiPlayback(entry.root, playback.copy(lastPosition = target.startTime + playback.delay))
        return true
    }
}
