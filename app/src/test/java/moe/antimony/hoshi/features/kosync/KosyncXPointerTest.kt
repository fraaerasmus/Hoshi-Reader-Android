package moe.antimony.hoshi.features.kosync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KosyncXPointerTest {
    private val chapter = """
        <?xml version="1.0" encoding="utf-8"?>
        <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
        <html xmlns="http://www.w3.org/1999/xhtml"><head><title>c</title></head>
        <body>
          <div class="main">
            <h1>第一章</h1>
            <p>あいうえお、かきくけこ。</p>
            <p><ruby>漢字<rt>かんじ</rt></ruby>を書く&nbsp;test one two</p>
            <div><span>さしすせそ</span><p>たちつてと</p></div>
            <table><tr><td>なにぬねの</td></tr></table>
          </div>
        </body></html>
    """.trimIndent()

    private val body get() = checkNotNull(KosyncChapterDom.parseBody(chapter))

    @Test
    fun countsCharactersLikeTheReader() {
        // Only ttu alphabet characters count: punctuation, nbsp and furigana are skipped.
        val runs = KosyncChapterDom.textNodes(body)
        assertEquals(3 + 10 + (2 + 3 + 10) + 5 + 5 + 5, runs.sumOf { it.chars })
    }

    @Test
    fun chapterStartForZeroProgress() {
        assertEquals("/body/DocFragment[3]/body", KosyncXPointer.forProgress(2, body, 0.0))
    }

    @Test
    fun anchorsOnTheParagraphContainingTheTargetCharacter() {
        val total = 43.0
        // Character 13 (0-based) is the first char of the second paragraph (3 + 10 before it).
        assertEquals("/body/DocFragment[1]/body/div[1]/p[1]", KosyncXPointer.forProgress(0, body, 8.0 / total))
        assertEquals("/body/DocFragment[1]/body/div[1]/p[2]", KosyncXPointer.forProgress(0, body, 14.0 / total))
        // Inline span under a div anchors on the div; nested p is a leaf.
        assertEquals("/body/DocFragment[1]/body/div[1]/div[1]", KosyncXPointer.forProgress(0, body, 28.0 / total))
        assertEquals("/body/DocFragment[1]/body/div[1]/div[1]/p[1]", KosyncXPointer.forProgress(0, body, 33.0 / total))
        // Table structure is uncertain in crengine: fall back to the enclosing div.
        assertEquals("/body/DocFragment[1]/body/div[1]", KosyncXPointer.forProgress(0, body, 40.0 / total))
        assertEquals("/body/DocFragment[1]/body/div[1]", KosyncXPointer.forProgress(0, body, 1.0))
    }

    @Test
    fun resolvesElementAndTextPointers() {
        val total = 43.0
        assertEquals(0.0, checkNotNull(KosyncXPointer.resolveProgress("/body/DocFragment[1]/body", body)), 1e-9)
        assertEquals(13.0 / total, checkNotNull(KosyncXPointer.resolveProgress("/body/DocFragment[1]/body/div/p[2]", body)), 1e-9)
        // text()[1] is the first direct text child of p[2], i.e. the run after the ruby element.
        assertEquals(15.0 / total, checkNotNull(KosyncXPointer.resolveProgress("/body/DocFragment[1]/body/div[1]/p[2]/text()[1].0", body)), 1e-9)
        // Offset 3 code points into the first paragraph's text ("あいう" before it).
        assertEquals(6.0 / total, checkNotNull(KosyncXPointer.resolveProgress("/body/DocFragment[1]/body/div[1]/p[1]/text().3", body)), 1e-9)
        assertNull(KosyncXPointer.resolveProgress("/body/DocFragment[1]/body/div[1]/p[9]", body))
        assertNull(KosyncXPointer.resolveProgress("/body/DocFragment[2]/body/section[1]", body))
        assertNull(KosyncXPointer.resolveProgress("42", body))
    }

    @Test
    fun roundTripsThroughResolve() {
        val pointer = KosyncXPointer.forProgress(4, body, 0.5)
        assertEquals("/body/DocFragment[5]/body/div[1]/p[2]", pointer)
        val progress = checkNotNull(KosyncXPointer.resolveProgress(pointer, body))
        assertEquals(pointer, KosyncXPointer.forProgress(4, body, progress))
    }

    @Test
    fun parsesSpineIndexFromDocFragment() {
        assertEquals(10, KosyncXPointer.spineIndex("/body/DocFragment[11]/body/div/p[5]/text().123"))
        assertEquals(0, KosyncXPointer.spineIndex("/body/DocFragment/body"))
        assertNull(KosyncXPointer.spineIndex("12"))
        assertNotNull(KosyncChapterDom.parseBody("<html><body><p>x</p></body></html>"))
        assertNull(KosyncChapterDom.parseBody("<html><body><p>x</body></html>"))
    }
}
