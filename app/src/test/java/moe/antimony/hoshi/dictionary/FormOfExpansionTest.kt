package moe.antimony.hoshi.dictionary

import de.manhhao.hoshi.GlossaryEntry
import de.manhhao.hoshi.LookupResult
import de.manhhao.hoshi.TermResult
import org.junit.Assert.assertEquals
import org.junit.Test

class FormOfExpansionTest {
    private val formOf = """[["vendre",["past participle"]]]"""

    @Test
    fun addsThePointedAtEntryWithTheHitsMatchedText() {
        val hit = result("vendu", "vendu", formOf, "non-lemma")
        val expanded = FormOfExpansion.expand(listOf(hit)) { listOf(result("vendre", "vendre", """["to sell"]""", "v")) }
        assertEquals(listOf("vendu" to "vendu", "vendre" to "vendu"), expanded.map { it.term.expression to it.matched })
        assertEquals(listOf("vendre", "vendu"), LemmaOrdering.lemmaFirst(expanded).map { it.term.expression })
    }

    @Test
    fun skipsEntriesAlreadyInTheResults() {
        val hit = result("vendu", "vendu", formOf, "non-lemma")
        val lemma = result("vendre", "vendu", """["to sell"]""", "v")
        val expanded = FormOfExpansion.expand(listOf(lemma, hit)) { listOf(result("vendre", "vendre", """["to sell"]""", "v")) }
        assertEquals(listOf("vendre", "vendu"), expanded.map { it.term.expression })
    }

    @Test
    fun followsOnlyOneLevelAndLooksEachTargetUpOnce() {
        val lookups = mutableListOf<String>()
        val hits = listOf(
            result("vendu", "vendu", formOf, "non-lemma"),
            result("vendu", "vendu", formOf, "non-lemma", reading = "other"),
        )
        val expanded = FormOfExpansion.expand(hits) { target ->
            lookups += target
            listOf(result(target, target, """[["further",["rule"]]]""", "non-lemma"))
        }
        assertEquals(listOf("vendre"), lookups)
        assertEquals(listOf("vendu", "vendre", "vendu"), expanded.map { it.term.expression })
    }

    @Test
    fun keepsOnlyWholeTargetMatches() {
        val hit = result("vendu", "vendu", formOf, "non-lemma")
        val expanded = FormOfExpansion.expand(listOf(hit)) {
            listOf(result("vend", "vend", """["sells"]""", "v"), result("vendeur", "vendre", """["seller"]""", "n"))
        }
        assertEquals(listOf("vendu"), expanded.map { it.term.expression })
    }

    @Test
    fun readsTargetsFromGlossaryShapes() {
        assertEquals(listOf("grand"), FormOfExpansion.targets("""[["grand",["feminine","plural"]]]"""))
        assertEquals(listOf("a", "b"), FormOfExpansion.targets("""[["a",["x"]],["b",["y"]]]"""))
        assertEquals(listOf("a"), FormOfExpansion.targets("""["a",["x"]]"""))
        assertEquals(emptyList<String>(), FormOfExpansion.targets("""["a plain definition","another"]"""))
        assertEquals(emptyList<String>(), FormOfExpansion.targets("""[{"type":"structured-content","content":"x"}]"""))
        assertEquals(emptyList<String>(), FormOfExpansion.targets(""))
    }

    private fun result(
        expression: String,
        matched: String,
        glossary: String,
        definitionTags: String,
        reading: String = expression,
    ): LookupResult =
        LookupResult(
            matched = matched,
            deinflected = expression,
            process = emptyArray(),
            term = TermResult(
                expression = expression,
                reading = reading,
                rules = "",
                glossaries = arrayOf(GlossaryEntry("Test", glossary, definitionTags, "")),
                frequencies = emptyArray(),
                pitches = emptyArray(),
            ),
            preprocessorSteps = 0,
        )
}
