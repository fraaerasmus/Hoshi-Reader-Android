package moe.antimony.hoshi.dictionary

import de.manhhao.hoshi.LookupResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Adds the entries a "form of" glossary points at. Yomitan dictionaries store that pointer as
 * `[uninflectedTerm, inflectionRule[]]`; the engine returns it as glossary text only, so a form the
 * deinflection rules cannot undo would otherwise show without its base term. One level deep.
 */
object FormOfExpansion {
    fun expand(results: List<LookupResult>, lookup: (String) -> List<LookupResult>): List<LookupResult> {
        val seen = results.mapTo(HashSet()) { it.term.expression to it.term.reading }
        val targetResults = HashMap<String, List<LookupResult>>()
        val expanded = ArrayList<LookupResult>(results.size)
        for (hit in results) {
            expanded += hit
            for (target in hit.targets()) {
                if (target == hit.term.expression) continue
                val found = targetResults.getOrPut(target) {
                    lookup(target).filter { it.matched == target && it.term.expression == target }
                }
                for (entry in found) {
                    if (!seen.add(entry.term.expression to entry.term.reading)) continue
                    expanded += LookupResult(
                        matched = hit.matched,
                        deinflected = entry.deinflected,
                        process = entry.process,
                        term = entry.term,
                        preprocessorSteps = entry.preprocessorSteps,
                    )
                }
            }
        }
        return expanded
    }

    private fun LookupResult.targets(): List<String> =
        term.glossaries.flatMap { targets(it.glossary) }.distinct()

    internal fun targets(glossary: String): List<String> {
        val text = glossary.trimStart()
        // Structured content starts with `[{`; skip it without parsing.
        if (!text.startsWith("[[") && !text.startsWith("[\"")) return emptyList()
        val items = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonArray ?: return emptyList()
        target(items)?.let { return listOf(it) }
        return items.mapNotNull(::target)
    }

    // Same shape test as isDeinflection in popup.js.
    private fun target(node: JsonElement): String? {
        if (node !is JsonArray || node.size != 2) return null
        val term = (node[0] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val rules = node[1] as? JsonArray ?: return null
        if (rules.isEmpty() || !rules.all { it is JsonPrimitive && it.isString }) return null
        return term.takeIf { it.isNotEmpty() }
    }
}
