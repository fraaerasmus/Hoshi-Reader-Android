package moe.antimony.hoshi.features.sasayaki

import kotlinx.coroutines.runBlocking
import moe.antimony.hoshi.epub.BookInfo
import moe.antimony.hoshi.epub.SasayakiMatch
import moe.antimony.hoshi.epub.SasayakiMatchData
import moe.antimony.hoshi.epub.SasayakiPlaybackData
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkSyncingSasayakiPlaybackRepositoryTest {
    private val bookInfo = BookInfo(
        characterCount = 100,
        chapterInfo = mapOf("c0" to BookInfo.ChapterInfo(spineIndex = 0, currentTotal = 0, chapterCount = 100)),
    )
    private val match = SasayakiMatchData(
        matches = listOf(
            SasayakiMatch("a", 0.0, 5.0, "a", chapterIndex = 0, start = 0, length = 10),
            SasayakiMatch("b", 5.0, 10.0, "b", chapterIndex = 0, start = 10, length = 10),
            SasayakiMatch("c", 10.0, 15.0, "c", chapterIndex = 0, start = 20, length = 10),
        ),
        unmatched = 0,
    )

    @Test
    fun forwardsCueChangesToTheReaderAtMostEveryInterval() = runBlocking {
        val saved = mutableListOf<SasayakiPlaybackData>()
        val positions = mutableListOf<Pair<Int, Int>>()
        var now = 0L
        val repository = BookmarkSyncingSasayakiPlaybackRepository(
            delegate = object : SasayakiPlaybackRepository {
                override suspend fun load() = null
                override suspend fun save(playback: SasayakiPlaybackData) { saved += playback }
            },
            bookInfo = bookInfo,
            matchProvider = { match },
            onReaderPosition = { chapter, progress -> positions += chapter to (100 * progress).toInt() },
            minIntervalMillis = 10_000L,
            nowMillis = { now },
        )

        repository.save(SasayakiPlaybackData(lastPosition = 1.0))
        repository.save(SasayakiPlaybackData(lastPosition = 2.0))
        now = 3_000L
        repository.save(SasayakiPlaybackData(lastPosition = 6.0))
        repository.save(SasayakiPlaybackData(lastPosition = 6.0, rate = 1.5f))
        now = 12_000L
        repository.save(SasayakiPlaybackData(lastPosition = 7.0))
        now = 30_000L
        repository.save(SasayakiPlaybackData(lastPosition = 12.5, delay = 2.0))

        assertEquals(6, saved.size)
        assertEquals(listOf(0 to 0, 0 to 10, 0 to 20), positions)
    }

    @Test
    fun staysQuietWithoutAMatch() = runBlocking {
        val positions = mutableListOf<Pair<Int, Double>>()
        val repository = BookmarkSyncingSasayakiPlaybackRepository(
            delegate = object : SasayakiPlaybackRepository {
                override suspend fun load() = null
                override suspend fun save(playback: SasayakiPlaybackData) = Unit
            },
            bookInfo = bookInfo,
            matchProvider = { null },
            onReaderPosition = { chapter, progress -> positions += chapter to progress },
        )
        repository.save(SasayakiPlaybackData(lastPosition = 3.0))
        assertEquals(emptyList<Pair<Int, Double>>(), positions)
    }
}
