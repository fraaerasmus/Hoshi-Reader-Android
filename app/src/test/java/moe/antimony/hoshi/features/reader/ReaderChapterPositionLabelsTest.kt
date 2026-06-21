package moe.antimony.hoshi.features.reader

import moe.antimony.hoshi.epub.EpubBook
import moe.antimony.hoshi.epub.EpubChapter
import moe.antimony.hoshi.epub.EpubTocItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderChapterPositionLabelsTest {
    @Test
    fun flatTocCountsEachTopLevelChapter() {
        val book = flatTocBook()

        assertEquals(
            ReaderChapterLabels.ChapterPositionInfo(number = 1, total = 3, label = "The Boy Who Lived"),
            ReaderChapterLabels.chapterPositionForIndex(book, 0),
        )
        assertEquals(
            ReaderChapterLabels.ChapterPositionInfo(number = 2, total = 3, label = "The Vanishing Glass"),
            ReaderChapterLabels.chapterPositionForIndex(book, 1),
        )
        assertEquals(
            ReaderChapterLabels.ChapterPositionInfo(number = 3, total = 3, label = "The Letters from No One"),
            ReaderChapterLabels.chapterPositionForIndex(book, 2),
        )
    }

    @Test
    fun nestedTocCountsTopLevelChaptersOnly() {
        val book = nestedTocBook()

        // Two top-level chapters even though "Chapter One" has a sub-section.
        assertEquals(
            ReaderChapterLabels.ChapterPositionInfo(number = 1, total = 2, label = "Chapter One"),
            ReaderChapterLabels.chapterPositionForIndex(book, 1),
        )
        // The sub-section spine item rounds down to its top-level chapter.
        assertEquals(
            ReaderChapterLabels.ChapterPositionInfo(number = 1, total = 2, label = "Chapter One"),
            ReaderChapterLabels.chapterPositionForIndex(book, 2),
        )
        assertEquals(
            ReaderChapterLabels.ChapterPositionInfo(number = 2, total = 2, label = "Chapter Two"),
            ReaderChapterLabels.chapterPositionForIndex(book, 3),
        )
    }

    @Test
    fun frontMatterBeforeFirstChapterResolvesToFirstChapter() {
        val book = nestedTocBook()

        // Spine index 0 is front matter with no TOC anchor; clamp to the first chapter.
        assertEquals(
            ReaderChapterLabels.ChapterPositionInfo(number = 1, total = 2, label = "Chapter One"),
            ReaderChapterLabels.chapterPositionForIndex(book, 0),
        )
    }

    @Test
    fun bookWithoutTocHasNoChapterPosition() {
        val book = EpubBook(
            title = "Book",
            chapters = listOf(chapter("a"), chapter("b")),
            toc = emptyList(),
        )

        assertNull(ReaderChapterLabels.chapterPositionForIndex(book, 0))
        assertNull(ReaderChapterLabels.chapterPositionForIndex(book, 1))
    }

    private fun flatTocBook(): EpubBook =
        EpubBook(
            title = "Harry Potter",
            chapters = listOf(chapter("ch1"), chapter("ch2"), chapter("ch3")),
            toc = listOf(
                EpubTocItem(label = "The Boy Who Lived", href = "ch1.xhtml"),
                EpubTocItem(label = "The Vanishing Glass", href = "ch2.xhtml"),
                EpubTocItem(label = "The Letters from No One", href = "ch3.xhtml"),
            ),
        )

    private fun nestedTocBook(): EpubBook =
        EpubBook(
            title = "Book",
            chapters = listOf(chapter("front"), chapter("ch1"), chapter("ch1-sec2"), chapter("ch2")),
            toc = listOf(
                EpubTocItem(
                    label = "Chapter One",
                    href = "ch1.xhtml",
                    children = listOf(EpubTocItem(label = "Section 2", href = "ch1-sec2.xhtml")),
                ),
                EpubTocItem(label = "Chapter Two", href = "ch2.xhtml"),
            ),
        )

    private fun chapter(name: String): EpubChapter =
        EpubChapter(
            id = name,
            href = "$name.xhtml",
            mediaType = "application/xhtml+xml",
            html = "abcdefghij",
        )
}
