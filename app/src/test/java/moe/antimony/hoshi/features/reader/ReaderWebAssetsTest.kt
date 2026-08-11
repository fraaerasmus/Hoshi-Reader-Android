package moe.antimony.hoshi.features.reader

import java.io.File
import moe.antimony.hoshi.content.ContentLanguageProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderWebAssetsTest {
    @Test
    fun selectionSupportJsUsesWordBoundaryPolicyForNonJapaneseLanguages() {
        val assets = ReaderWebAssets(
            languageJapaneseJs = "LANG_JA",
            selectionJapaneseJs = "SEL_JA",
            selectionEnglishJs = "SEL_EN",
            selectionJs = "",
            readerPaginatedJs = "",
            readerContinuousJs = "",
            readerVisualNovelJs = "",
            highlightsJs = "",
            readerCss = "",
        )
        val french = requireNotNull(ContentLanguageProfile.fromDictionaryLanguageId("fr"))

        assertTrue(assets.selectionSupportJs(french).contains("SEL_EN"))
        assertFalse(assets.selectionSupportJs(french).contains("SEL_JA"))
        assertTrue(assets.selectionSupportJs(ContentLanguageProfile.English).contains("SEL_EN"))
        assertTrue(assets.selectionSupportJs(ContentLanguageProfile.Japanese).contains("SEL_JA"))
        assertFalse(assets.selectionSupportJs(ContentLanguageProfile.Japanese).contains("SEL_EN"))
    }

    @Test
    fun readerWebAssetsExistInNeutralAssetTree() {
        val assets = listOf(
            "hoshi-web/shared/language-ja.js",
            "hoshi-web/shared/selection-ja.js",
            "hoshi-web/shared/selection-en.js",
            "hoshi-web/shared/selection.js",
            "hoshi-web/reader/reader-paginated.js",
            "hoshi-web/reader/reader-continuous.js",
            "hoshi-web/reader/reader-visual-novel.js",
            "hoshi-web/reader/reader-text-semantics.js",
            "hoshi-web/reader/reader-dom-text.js",
            "hoshi-web/reader/reader-media-semantics.js",
            "hoshi-web/reader/reader-vn-content-stream.js",
            "hoshi-web/reader/reader-vn-range-map.js",
            "hoshi-web/reader/reader-vn-selection-projection.js",
            "hoshi-web/reader/highlights.js",
            "hoshi-web/reader/reader.css",
            "hoshi-web/popup/popup.js",
            "hoshi-web/popup/popup.css",
            "hoshi-web/popup/iframe.html",
            "hoshi-web/popup/reader-popup-host.js",
            "hoshi-web/popup/icons/close.svg",
        )

        assets.forEach { path ->
            val file = listOf(
                File("app/src/main/assets/$path"),
                File("src/main/assets/$path"),
            ).firstOrNull(File::isFile)
                ?: File("app/src/main/assets/$path")
            assertTrue("$path should exist", file.isFile)
            assertTrue("$path should not be empty", file.length() > 0)
        }
    }

    @Test
    fun generatedReaderCssDoesNotExposeTemplatePlaceholders() {
        val css = ReaderContentStyles.css()

        assertFalse(css.contains("__HOSHI_"))
    }
}
