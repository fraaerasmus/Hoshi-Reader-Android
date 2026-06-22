package moe.antimony.hoshi.dictionary

import de.manhhao.hoshi.LookupResult

/**
 * Sinks "non-lemma" form-of entries (whose only content is a "form of <lemma>" pointer, e.g.
 * détestait -> détester in wiktionary-derived dictionaries) below entries that carry a real
 * definition, so the lemma leads. Stable, so existing relevance/match ordering is preserved.
 */
object LemmaOrdering {
    fun lemmaFirst(results: List<LookupResult>): List<LookupResult> =
        results.sortedBy { if (it.isFormOfOnly()) 1 else 0 }

    private fun LookupResult.isFormOfOnly(): Boolean =
        term.glossaries.isNotEmpty() &&
            term.glossaries.all { "non-lemma" in it.definitionTags.split(' ') }
}
