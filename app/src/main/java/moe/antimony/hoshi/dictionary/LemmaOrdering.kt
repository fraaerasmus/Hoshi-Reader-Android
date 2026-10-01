package moe.antimony.hoshi.dictionary

import de.manhhao.hoshi.LookupResult

/**
 * Longest matched text first; among entries matching the same length, sinks "non-lemma" form-of
 * entries (whose only content is a "form of <lemma>" pointer, e.g. détestait -> détester in
 * wiktionary-derived dictionaries) below entries that carry a real definition, so the lemma leads
 * and the form's own entry still precedes shorter matches. Stable otherwise.
 */
object LemmaOrdering {
    fun lemmaFirst(results: List<LookupResult>): List<LookupResult> =
        results.sortedWith(
            compareByDescending<LookupResult> { it.matched.codePointCount(0, it.matched.length) }
                .thenBy { it.isFormOfOnly() },
        )

    private fun LookupResult.isFormOfOnly(): Boolean =
        term.glossaries.isNotEmpty() &&
            term.glossaries.all { "non-lemma" in it.definitionTags.split(' ') }
}
