package moe.antimony.hoshi.content

class ContentLanguageProfile private constructor(
    val htmlLang: String,
    val composeLocaleTag: String,
    val inputLocaleTag: String,
    val webViewFontFamilyCss: String,
    val readerSerifFontFamilyCss: String,
    val readerSansSerifFontFamilyCss: String,
) {
    companion object {
        val Default: ContentLanguageProfile = ContentLanguageProfile(
            htmlLang = "ja",
            composeLocaleTag = "ja-JP",
            inputLocaleTag = "ja-JP",
            webViewFontFamilyCss = """"Noto Sans CJK JP", "NotoSansCJKJP-Regular", "SECCJKjp-Regular", sans-serif""",
            readerSerifFontFamilyCss = "'Noto Serif CJK JP', 'NotoSerifCJKjp-Regular', serif",
            readerSansSerifFontFamilyCss = "'Noto Sans CJK JP', 'NotoSansCJKJP-Regular', sans-serif",
        )
    }
}
