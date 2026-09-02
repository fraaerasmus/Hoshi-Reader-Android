package moe.antimony.hoshi.features.sasayaki

import moe.antimony.hoshi.epub.BookInfo
import moe.antimony.hoshi.epub.Bookmark
import moe.antimony.hoshi.epub.SasayakiMatch
import moe.antimony.hoshi.epub.SasayakiMatchData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SasayakiPositionBridgeTest {
    private val bookInfo = BookInfo(
        characterCount = 300,
        chapterInfo = mapOf(
            "c0" to BookInfo.ChapterInfo(spineIndex = 0, currentTotal = 0, chapterCount = 100),
            "c1" to BookInfo.ChapterInfo(spineIndex = 1, currentTotal = 100, chapterCount = 200),
        ),
    )
    private val match = SasayakiMatchData(
        matches = listOf(
            cue("a", 10.0, 14.0, chapter = 0, start = 0, length = 20),
            cue("b", 14.0, 20.0, chapter = 0, start = 20, length = 30),
            cue("c", 30.0, 36.0, chapter = 1, start = 10, length = 40),
            cue("d", 36.0, 41.0, chapter = 1, start = 60, length = 30),
        ),
        unmatched = 0,
    )

    @Test
    fun bookmarkInsideACueResolvesToThatCue() {
        assertEquals("b", cueFor(chapter = 0, characterCount = 25)?.id)
        assertEquals("c", cueFor(chapter = 1, characterCount = 100 + 30)?.id)
    }

    @Test
    fun bookmarkBetweenCuesFallsBackToTheNearestEarlierCue() {
        assertEquals("b", cueFor(chapter = 0, characterCount = 80)?.id)
        assertEquals("b", cueFor(chapter = 1, characterCount = 100 + 5)?.id)
        assertEquals("d", cueFor(chapter = 1, characterCount = 100 + 150)?.id)
        assertEquals("a", cueFor(chapter = 0, characterCount = 0)?.id)
        assertNull(SasayakiPositionBridge.cueForBookmark(match, bookInfo, Bookmark(chapterIndex = 7, progress = 0.0, characterCount = 0)))
    }

    @Test
    fun audioTimeAddsTheSubtitleDelay() {
        val time = SasayakiPositionBridge.audioTimeForBookmark(match, bookInfo, Bookmark(1, 0.3, 160), delay = 1.5)
        assertEquals(36.0 + 1.5, checkNotNull(time), 1e-9)
    }

    @Test
    fun audioTimeMapsToTheCueBeingHeardOrTheOneBefore() {
        assertEquals("a", SasayakiPositionBridge.cueAtAudioTime(match, 12.0, 0.0)?.id)
        assertEquals("b", SasayakiPositionBridge.cueAtAudioTime(match, 25.0, 0.0)?.id)
        assertEquals("c", SasayakiPositionBridge.cueAtAudioTime(match, 31.0, 1.0)?.id)
        assertNull(SasayakiPositionBridge.cueAtAudioTime(match, 5.0, 0.0))
    }

    @Test
    fun readerPositionForCueTruncatesBackToTheCueStart() {
        val (chapterIndex, progress) = checkNotNull(SasayakiPositionBridge.readerPositionForCue(match.matches[3], bookInfo))
        assertEquals(1, chapterIndex)
        assertEquals(60, (200 * progress).toInt())
        assertNull(SasayakiPositionBridge.readerPositionForCue(cue("x", 0.0, 1.0, chapter = 9, start = 0, length = 1), bookInfo))
    }

    private fun cueFor(chapter: Int, characterCount: Int): SasayakiMatch? =
        SasayakiPositionBridge.cueForBookmark(match, bookInfo, Bookmark(chapterIndex = chapter, progress = 0.0, characterCount = characterCount))

    private fun cue(id: String, startTime: Double, endTime: Double, chapter: Int, start: Int, length: Int) =
        SasayakiMatch(id = id, startTime = startTime, endTime = endTime, text = id, chapterIndex = chapter, start = start, length = length)
}
