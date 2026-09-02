package moe.antimony.hoshi.features.sasayaki

import moe.antimony.hoshi.epub.BookInfo
import moe.antimony.hoshi.epub.Bookmark
import moe.antimony.hoshi.epub.SasayakiMatch
import moe.antimony.hoshi.epub.SasayakiMatchData

/**
 * Text position <-> audio time through the subtitle match. Cue offsets and bookmark character
 * counts share the reader's character alphabet, so `characterCount = currentTotal + cue.start`
 * converts exactly; granularity is one cue.
 */
internal object SasayakiPositionBridge {
    fun chapterInfo(bookInfo: BookInfo, chapterIndex: Int): BookInfo.ChapterInfo? =
        bookInfo.chapterInfo.values.firstOrNull { it.spineIndex == chapterIndex }

    /** The cue containing the bookmark, else the nearest earlier cue (offsets between cues are common). */
    fun cueForBookmark(match: SasayakiMatchData, bookInfo: BookInfo, bookmark: Bookmark): SasayakiMatch? {
        val info = chapterInfo(bookInfo, bookmark.chapterIndex) ?: return null
        val offset = (bookmark.characterCount - info.currentTotal).coerceIn(0, info.chapterCount)
        return match.matches.lastOrNull { cue ->
            cue.chapterIndex < bookmark.chapterIndex ||
                (cue.chapterIndex == bookmark.chapterIndex && cue.start <= offset)
        }
    }

    fun audioTimeForBookmark(match: SasayakiMatchData, bookInfo: BookInfo, bookmark: Bookmark, delay: Double): Double? =
        cueForBookmark(match, bookInfo, bookmark)?.let { it.startTime + delay }

    /** The cue playing at [time] (audio clock), else the nearest earlier cue. */
    fun cueAtAudioTime(match: SasayakiMatchData, time: Double, delay: Double): SasayakiMatch? {
        val cues = match.matches
        val target = time - delay
        var low = 0
        var high = cues.size
        while (low < high) {
            val mid = (low + high) / 2
            if (cues[mid].startTime <= target) low = mid + 1 else high = mid
        }
        return cues.getOrNull(low - 1)
    }

    /** Reader position (chapter index, in-chapter progress) at the start of [cue]. */
    fun readerPositionForCue(cue: SasayakiMatch, bookInfo: BookInfo): Pair<Int, Double>? {
        val info = chapterInfo(bookInfo, cue.chapterIndex) ?: return null
        if (info.chapterCount <= 0) return null
        // +0.5 so characterCountAt() truncates back to exactly cue.start.
        val progress = ((cue.start + 0.5) / info.chapterCount).coerceIn(0.0, 1.0)
        return cue.chapterIndex to progress
    }
}
