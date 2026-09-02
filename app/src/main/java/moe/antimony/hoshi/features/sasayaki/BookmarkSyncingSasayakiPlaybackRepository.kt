package moe.antimony.hoshi.features.sasayaki

import moe.antimony.hoshi.epub.BookInfo
import moe.antimony.hoshi.epub.SasayakiMatchData
import moe.antimony.hoshi.epub.SasayakiPlaybackData

/**
 * Wraps the playback sidecar writes (every second while playing, on pause/seek) and moves the
 * reading bookmark to the cue being heard, throttled to cue changes at most every
 * [minIntervalMillis]. The reader's own save path then stamps and syncs the bookmark.
 */
internal class BookmarkSyncingSasayakiPlaybackRepository(
    private val delegate: SasayakiPlaybackRepository,
    private val bookInfo: BookInfo,
    private val matchProvider: () -> SasayakiMatchData?,
    private val onReaderPosition: (chapterIndex: Int, progress: Double) -> Unit,
    private val minIntervalMillis: Long = 10_000L,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : SasayakiPlaybackRepository {
    private var lastPosition: Double? = null
    private var lastCueId: String? = null
    private var lastSyncAt: Long? = null

    override suspend fun load(): SasayakiPlaybackData? = delegate.load()

    override suspend fun save(playback: SasayakiPlaybackData) {
        delegate.save(playback)
        val position = playback.lastPosition
        if (position == lastPosition) return
        lastPosition = position
        val match = matchProvider()?.takeIf { it.matches.isNotEmpty() } ?: return
        val cue = SasayakiPositionBridge.cueAtAudioTime(match, position, playback.delay) ?: return
        if (cue.id == lastCueId) return
        val now = nowMillis()
        if (lastSyncAt?.let { now - it < minIntervalMillis } == true) return
        val (chapterIndex, progress) = SasayakiPositionBridge.readerPositionForCue(cue, bookInfo) ?: return
        lastCueId = cue.id
        lastSyncAt = now
        onReaderPosition(chapterIndex, progress)
    }
}
