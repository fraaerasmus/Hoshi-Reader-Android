package moe.antimony.hoshi.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PositionTrailTest {
    private fun entry(source: String, characterCount: Int) =
        PositionTrailEntry.of(Bookmark(0, 0.0, characterCount, null), source, recordedAt = characterCount.toDouble())

    @Test
    fun keepsNewestLastSkipsRepeatsKeepsFirstOfAnAudioRunAndCapsLength() {
        var trail = PositionTrail()
        trail = trail.pushed(entry(PositionTrailEntry.SourceKosync, 1))
        trail = trail.pushed(entry(PositionTrailEntry.SourceAudio, 2))
        trail = trail.pushed(entry(PositionTrailEntry.SourceAudio, 3))
        trail = trail.pushed(entry(PositionTrailEntry.SourceJump, 4))
        trail = trail.pushed(entry(PositionTrailEntry.SourceAudio, 5))

        assertEquals(listOf(1, 2, 4, 5), trail.entries.map { it.characterCount })
        assertEquals(listOf("kosync", "audio", "jump", "audio"), trail.entries.map { it.source })
        assertSame(trail, trail.pushed(entry(PositionTrailEntry.SourceUndo, 5)))

        repeat(30) { trail = trail.pushed(entry(PositionTrailEntry.SourceJump, 100 + it), max = 5) }
        assertEquals(5, trail.entries.size)
        assertEquals(129, trail.entries.last().characterCount)
        assertEquals(Bookmark(0, 0.0, 129, 7.0), trail.entries.last().toBookmark(lastModified = 7.0))
    }
}
