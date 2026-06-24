package moe.antimony.hoshi.features.sasayaki

/**
 * Tracks the reader's currently-visible chapter for [SasayakiPlaybackHolder]. While a reader is
 * attached it reports (and remembers) the reader's chapter; while detached — when the supplied value
 * is null — it reports the last known one. This keeps the cue coordinator from comparing cues
 * against a stale/zero index while playback runs headless, and lets re-attach resync the reader to
 * the audio's chapter.
 */
internal class SasayakiVisibleChapterTracker {
    private var lastKnown = 0

    fun resolve(visibleChapter: Int?): Int {
        if (visibleChapter != null) lastKnown = visibleChapter
        return lastKnown
    }
}
