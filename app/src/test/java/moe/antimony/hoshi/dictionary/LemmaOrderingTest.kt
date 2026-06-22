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
        val lemma = result("détester", "v")
        assertEquals(
            listOf("détester", "détestait"),
            LemmaOrdering.lemmaFirst(listOf(nonLemma, lemma)).map { it.term.expression },
        )
    }

    @Test
    fun keepsEntriesThatHaveAtLeastOneRealDefinition() {
        val mixed = result("avait", "non-lemma", "v") // one form-of glossary, one real definition
        val nonLemma = result("avaient", "non-lemma")
        assertEquals(
            listOf("avait", "avaient"),
            LemmaOrdering.lemmaFirst(listOf(mixed, nonLemma)).map { it.term.expression },
        )
    }

    @Test
    fun leavesOrderUntouchedWithoutFormOfEntries() {
        val a = result("chat", "n")
        val b = result("chien", "") // empty tags must not count as non-lemma
        assertEquals(
            listOf("chat", "chien"),
            LemmaOrdering.lemmaFirst(listOf(a, b)).map { it.term.expression },
        )
    }

    private fun result(expression: String, vararg definitionTags: String): LookupResult =
        LookupResult(
            matched = expression,
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
