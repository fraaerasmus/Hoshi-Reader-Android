package moe.antimony.hoshi.dictionary

/**
 * Pre-lookup text replacement for elision languages (Yomitan's textReplacements). The engine
 * matches by prefix, so an elided form like "l'homme" never reaches "homme" on its own.
 */
object ElisionTextReplacement {
    // Standard French elisions: le/la→l', de→d', je→j', me→m', te→t', se→s', ne→n', ce→c', que→qu'.
    // Matches a straight ' or typographic ’; case-insensitive so sentence-start "L'…" strips too.
    private val FRENCH = Regex("^(?:l|d|j|m|t|s|n|c|qu)['’]", RegexOption.IGNORE_CASE)

    /** The text with a leading French elision prefix removed, or null if none applies. */
    fun stripElision(text: String, languageId: String?): String? {
        if (languageId != "fr") return null
        val match = FRENCH.find(text) ?: return null
        return text.substring(match.value.length).takeIf { it.isNotEmpty() }
    }
}
