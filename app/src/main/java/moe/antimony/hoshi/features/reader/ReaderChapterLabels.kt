package moe.antimony.hoshi.features.reader

import moe.antimony.hoshi.epub.EpubBook
import moe.antimony.hoshi.epub.EpubTocItem

internal object ReaderChapterLabels {
    fun labels(book: EpubBook): Map<Int, String> {
        val pathToSpine = book.chapters.mapIndexed { fallbackIndex, chapter ->
            chapter.href to (chapter.spineIndex ?: fallbackIndex)
        }
            .toMap()
        val labels = linkedMapOf<Int, String>()

        fun walk(items: List<EpubTocItem>, topLabel: String?) {
            items.forEach { item ->
                val label = topLabel ?: item.label
                val path = item.href?.substringBefore("#")
                val index = path?.let(pathToSpine::get)
                if (index != null && labels[index] == null) {
                    labels[index] = label
                }
                walk(item.children, label)
            }
        }

        walk(book.toc, null)
        return labels
    }

    fun sectionLabelForIndex(book: EpubBook, chapterIndex: Int): String {
        return sectionLabelForIndex(labels(book), chapterIndex)
    }

    fun sectionLabelForIndex(labels: Map<Int, String>, chapterIndex: Int): String {
        var index = chapterIndex
        while (index > 0 && labels[index] == null) {
            index -= 1
        }
        return labels[index].orEmpty()
    }

    data class ChapterPositionInfo(val number: Int, val total: Int, val label: String)

    fun chapterPositionForIndex(book: EpubBook, chapterIndex: Int): ChapterPositionInfo? {
        val pathToSpine = book.chapters.mapIndexed { fallbackIndex, chapter ->
            chapter.href to (chapter.spineIndex ?: fallbackIndex)
        }
            .toMap()
        val tops = book.toc.mapNotNull { item ->
            val path = item.href?.substringBefore("#") ?: return@mapNotNull null
            (pathToSpine[path] ?: return@mapNotNull null) to item.label
        }
            .sortedBy { it.first }
        if (tops.isEmpty()) return null
        val pos = tops.indexOfLast { it.first <= chapterIndex }.coerceAtLeast(0)
        return ChapterPositionInfo(number = pos + 1, total = tops.size, label = tops[pos].second)
    }
}
