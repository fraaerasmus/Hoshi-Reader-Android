package moe.antimony.hoshi.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentLanguageProfileTest {
    @Test
    fun defaultProfileIsFixedJapaneseAndUsesAndroidFonts() {
        val japanese = ContentLanguageProfile.Default

        assertEquals("ja", japanese.htmlLang)
        assertEquals("ja-JP", japanese.composeLocaleTag)
        assertEquals("ja-JP", japanese.inputLocaleTag)
        assertTrue(japanese.webViewFontFamilyCss.contains("Noto Sans CJK JP"))
        assertTrue(japanese.readerSerifFontFamilyCss.contains("Noto Serif CJK JP"))
        assertFalse(japanese.webViewFontFamilyCss.contains("Hira" + "gino"))
    }
}
