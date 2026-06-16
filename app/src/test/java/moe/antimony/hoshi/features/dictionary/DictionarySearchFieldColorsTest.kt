package moe.antimony.hoshi.features.dictionary

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import moe.antimony.hoshi.content.ContentLanguageProfile
import moe.antimony.hoshi.ui.hoshiTextFieldCursorColor
import org.junit.Assert.assertEquals
import org.junit.Test

class DictionarySearchFieldColorsTest {
    @Test
    fun cursorUsesReadableSearchFieldForegroundColor() {
        val darkModeForeground = Color(0xFFECE6F0)

        assertEquals(darkModeForeground, hoshiTextFieldCursorColor(darkModeForeground))
    }

    @Test
    fun searchKeyboardHintsJapaneseInputAndShowsOnFocus() {
        val options = dictionarySearchKeyboardOptions()

        assertEquals("ja-JP", options.hintLocales?.single()?.toLanguageTag())
        assertEquals(KeyboardType.Unspecified, options.keyboardType)
        assertEquals(true, options.showKeyboardOnFocus)
    }

    @Test
    fun searchKeyboardRequestsAsciiInputForEnglishProfiles() {
        val options = dictionarySearchKeyboardOptions(ContentLanguageProfile.English)

        assertEquals("en-US", options.hintLocales?.single()?.toLanguageTag())
        assertEquals(KeyboardType.Ascii, options.keyboardType)
        assertEquals(true, options.showKeyboardOnFocus)
    }

    @Test
    fun searchTextStyleUsesFixedJapaneseContentProfile() {
        val textStyle = dictionarySearchTextStyle(
            baseStyle = TextStyle.Default,
            color = Color.Black,
        )

        assertEquals("ja-JP", textStyle.localeList?.single()?.toLanguageTag())
        assertEquals(Color.Black, textStyle.color)
    }
}
