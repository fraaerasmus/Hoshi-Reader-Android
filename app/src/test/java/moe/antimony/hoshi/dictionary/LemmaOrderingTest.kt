package moe.antimony.hoshi.dictionary

import de.manhhao.hoshi.GlossaryEntry
import de.manhhao.hoshi.LookupResult
import de.manhhao.hoshi.TermResult
import org.junit.Assert.assertEquals
import org.junit.Test

class LemmaOrderingTest {
    @Test
    fun sinksFormOfEntriesBelowTheLemma() {
        val nonLemma = result("détestait", "non-lemma")
        val lemma = result("détester", "v", matched = "détestait")
        assertEquals(
            listOf("détester", "détestait"),
            LemmaOrdering.lemmaFirst(listOf(nonLemma, lemma)).map { it.term.expression },
        )
    }

    @Test
    fun keepsEntriesThatHaveAtLeastOneRealDefinition() {
        val mixed = result("avait", "non-lemma", "v") // one form-of glossary, one real definition
        val nonLemma = result("avaient", "non-lemma", matched = "avait")
        assertEquals(
            listOf("avait", "avaient"),
            LemmaOrdering.lemmaFirst(listOf(mixed, nonLemma)).map { it.term.expression },
        )
    }

    @Test
    fun leavesOrderUntouchedWithoutFormOfEntries() {
        val a = result("chat", "n")
        val b = result("chien", "", matched = "chat") // empty tags must not count as non-lemma
        assertEquals(
            listOf("chat", "chien"),
            LemmaOrdering.lemmaFirst(listOf(a, b)).map { it.term.expression },
        )
    }

    @Test
    fun keepsTheFormOfEntryAboveShorterMatches() {
        val formOf = result("grandes", "non-lemma")
        val shorter = result("grande", "adj")
        val shortest = result("grand", "adj")
        val lemma = result("grand", "adj", matched = "grandes")
        assertEquals(
            listOf("grand" to "grandes", "grandes" to "grandes", "grande" to "grande", "grand" to "grand"),
            LemmaOrdering.lemmaFirst(listOf(formOf, lemma, shorter, shortest)).map { it.term.expression to it.matched },
        )
    }

    @Test
    fun measuresMatchedLengthInCodePoints() {
        val astral = result("𠮟る", "non-lemma") // 2 code points, 3 UTF-16 units
        val longer = result("しかる", "v")
        assertEquals(
            listOf("しかる", "𠮟る"),
            LemmaOrdering.lemmaFirst(listOf(astral, longer)).map { it.term.expression },
        )
    }

    private fun result(expression: String, vararg definitionTags: String, matched: String = expression): LookupResult =
        LookupResult(
            matched = matched,
            deinflected = expression,
            process = emptyArray(),
            term = TermResult(
                expression = expression,
                reading = expression,
                rules = "",
                glossaries = definitionTags.map { tags ->
                    GlossaryEntry(
                        dictName = "Test",
                        glossary = "",
                        definitionTags = tags,
                        termTags = "",
                    )
                }.toTypedArray(),
                frequencies = emptyArray(),
                pitches = emptyArray(),
            ),
            preprocessorSteps = 0,
        )
}
