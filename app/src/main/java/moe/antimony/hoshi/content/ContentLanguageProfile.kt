package moe.antimony.hoshi.content

import androidx.annotation.StringRes
import moe.antimony.hoshi.R

class ContentLanguageProfile private constructor(
    val id: String,
    @param:StringRes val displayNameRes: Int,
    val dictionaryLanguageId: String,
    val htmlLang: String,
    val composeLocaleTag: String,
    val inputLocaleTag: String,
    val webViewFontFamilyCss: String,
    val readerSerifFontFamilyCss: String,
    val readerSansSerifFontFamilyCss: String,
) {
    companion object {
        const val JapaneseLanguageId = "ja"
        const val EnglishLanguageId = "en"

        // Generic stacks for the non-Japanese languages supported by the kaihouguide
        // multilingual engine. Scripts (Korean, Greek, Arabic, Georgian, Yiddish, …)
        // resolve through the system Noto fallback; per-language font tuning is out of
        // scope for the fork.
        private const val GenericWebViewFontCss = """"Noto Sans", "Roboto", Arial, sans-serif"""
        private const val GenericSerifFontCss = "'Noto Serif', Georgia, serif"
        private const val GenericSansSerifFontCss = "'Noto Sans', 'Roboto', Arial, sans-serif"

        val Japanese: ContentLanguageProfile = ContentLanguageProfile(
            id = JapaneseLanguageId,
            displayNameRes = R.string.profile_language_japanese,
            dictionaryLanguageId = JapaneseLanguageId,
            htmlLang = "ja",
            composeLocaleTag = "ja-JP",
            inputLocaleTag = "ja-JP",
            webViewFontFamilyCss = """"Noto Sans CJK JP", "NotoSansCJKJP-Regular", "SECCJKjp-Regular", sans-serif""",
            readerSerifFontFamilyCss = "'Noto Serif CJK JP', 'NotoSerifCJKjp-Regular', serif",
            readerSansSerifFontFamilyCss = "'Noto Sans CJK JP', 'NotoSansCJKJP-Regular', sans-serif",
        )

        val English: ContentLanguageProfile = ContentLanguageProfile(
            id = EnglishLanguageId,
            displayNameRes = R.string.profile_language_english,
            dictionaryLanguageId = EnglishLanguageId,
            htmlLang = "en",
            composeLocaleTag = "en-US",
            inputLocaleTag = "en-US",
            webViewFontFamilyCss = """"Roboto", "Noto Sans", Arial, sans-serif""",
            readerSerifFontFamilyCss = "'Noto Serif', Georgia, serif",
            readerSansSerifFontFamilyCss = "'Roboto', 'Noto Sans', Arial, sans-serif",
        )

        val Default: ContentLanguageProfile = Japanese

        // Japanese and English first (matching upstream), then the remaining languages
        // the kaihouguide engine de-inflects. The language id is what reaches the native
        // setLookupLanguage(), so lookups work for all of them.
        val Supported: List<ContentLanguageProfile> = listOf(
            Japanese,
            English,
            additional("ar", R.string.dictionary_language_arabic, "ar"),
            additional("de", R.string.dictionary_language_german, "de-DE"),
            additional("el", R.string.dictionary_language_modern_greek, "el-GR"),
            additional("eo", R.string.dictionary_language_esperanto, "eo"),
            additional("es", R.string.dictionary_language_spanish, "es-ES"),
            additional("eu", R.string.dictionary_language_basque, "eu-ES"),
            additional("fr", R.string.dictionary_language_french, "fr-FR"),
            additional("ga", R.string.dictionary_language_irish, "ga-IE"),
            additional("grc", R.string.dictionary_language_ancient_greek, "grc"),
            additional("kat", R.string.dictionary_language_georgian, "ka-GE", htmlLang = "ka"),
            additional("ko", R.string.dictionary_language_korean, "ko-KR"),
            additional("la", R.string.dictionary_language_latin, "la"),
            additional("sga", R.string.dictionary_language_old_irish, "sga"),
            additional("sq", R.string.dictionary_language_albanian, "sq-AL"),
            additional("tl", R.string.dictionary_language_tagalog, "tl-PH"),
            additional("yi", R.string.dictionary_language_yiddish, "yi"),
        )

        fun fromDictionaryLanguageId(languageId: String?): ContentLanguageProfile? =
            Supported.firstOrNull { it.dictionaryLanguageId == languageId }

        private fun additional(
            id: String,
            @StringRes displayNameRes: Int,
            localeTag: String,
            htmlLang: String = id,
        ): ContentLanguageProfile = ContentLanguageProfile(
            id = id,
            displayNameRes = displayNameRes,
            dictionaryLanguageId = id,
            htmlLang = htmlLang,
            composeLocaleTag = localeTag,
            inputLocaleTag = localeTag,
            webViewFontFamilyCss = GenericWebViewFontCss,
            readerSerifFontFamilyCss = GenericSerifFontCss,
            readerSansSerifFontFamilyCss = GenericSansSerifFontCss,
        )
    }
}
