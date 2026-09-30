package de.manhhao.hoshi

// NOTE: constructed by the kaihouguide JNI engine; keep the constructor exactly.
// `kanjiCount` and `error` are computed adapters — the kaihouguide engine has no kanji
// dictionaries and reports no import diagnostics beyond success.
class ImportResult(
    val success: Boolean,
    val title: String,
    val termCount: Long,
    val metaCount: Long,
    val freqCount: Long,
    val pitchCount: Long,
    val mediaCount: Long,
) {
    val kanjiCount: Long get() = 0
    val error: String get() = ""
}

class DictionaryStyle(
    val dictName: String,
    val styles: String,
)

class Frequency(
    val value: Int,
    val displayValue: String,
)

class GlossaryEntry(
    val dictName: String,
    val glossary: String,
    val definitionTags: String,
    val termTags: String,
)

class FrequencyEntry(
    val dictName: String,
    val frequencies: Array<Frequency>,
)

class Pitch(
    val position: Int,
    val pattern: String,
    val nasal: IntArray,
    val devoice: IntArray,
)

// NOTE: constructed by the kaihouguide JNI engine; keep the primary constructor (dictName,
// pitchPositions) exactly. `pitches`/`transcriptions` are computed adapters so upstream's
// complete-pitch popup code compiles — the kaihouguide engine only has downstep positions
// and no IPA transcriptions. The secondary constructor exists for upstream's tests.
class PitchEntry(
    val dictName: String,
    val pitchPositions: IntArray,
) {
    private var explicitPitches: Array<Pitch>? = null
    private var explicitTranscriptions: Array<String>? = null

    constructor(
        dictName: String,
        pitches: Array<Pitch>,
        transcriptions: Array<String>,
    ) : this(dictName, IntArray(pitches.size) { pitches[it].position }) {
        explicitPitches = pitches
        explicitTranscriptions = transcriptions
    }

    val pitches: Array<Pitch>
        get() = explicitPitches
            ?: Array(pitchPositions.size) { Pitch(position = pitchPositions[it], pattern = "", nasal = IntArray(0), devoice = IntArray(0)) }

    val transcriptions: Array<String> get() = explicitTranscriptions ?: emptyArray()
}

class KanjiStat(
    val key: String,
    val value: String,
)

class KanjiEntry(
    val dictName: String,
    val onyomi: String,
    val kunyomi: String,
    val tags: String,
    val definitions: Array<String>,
    val stats: Array<KanjiStat>,
)

class KanjiResult(
    val character: String,
    val entries: Array<KanjiEntry>,
)

class TermResult(
    val expression: String,
    val reading: String,
    val rules: String,
    val glossaries: Array<GlossaryEntry>,
    val frequencies: Array<FrequencyEntry>,
    val pitches: Array<PitchEntry>,
)

class TransformGroup(
    val name: String,
    val description: String,
)

enum class TraceSource {
    ALGORITHM,
    DICTIONARY,
    BOTH,
}

class TraceCandidate(
    val deinflected: String,
    val preprocessorSteps: Int,
    val source: TraceSource,
    val trace: Array<TransformGroup>,
)

// NOTE: constructed by the kaihouguide JNI engine; keep the constructor (matched,
// deinflected, process, term, preprocessorSteps) exactly. `traceCandidates` is a
// computed adapter that synthesizes a single algorithm candidate from our fields so
// upstream's trace-candidate UI compiles against the kaihouguide engine.
class LookupResult(
    val matched: String,
    val deinflected: String,
    val process: Array<TransformGroup>,
    val term: TermResult,
    val preprocessorSteps: Int,
) {
    val traceCandidates: Array<TraceCandidate>
        get() = arrayOf(
            TraceCandidate(
                deinflected = deinflected,
                preprocessorSteps = preprocessorSteps,
                source = TraceSource.ALGORITHM,
                trace = process,
            ),
        )
}

enum class LookupFrequencyOrder(val nativeValue: Int) { Auto(0), Ascending(1), Descending(2), Disabled(3) }

data class LookupOptions(
    val frequencyOrder: LookupFrequencyOrder = LookupFrequencyOrder.Auto,
    val frequencyDictionary: String? = null,
)

object HoshiDicts {
    init {
        System.loadLibrary("hoshidicts_jni")
    }

    external fun importDictionary(zipPath: String, outputDir: String, lowRam: Boolean = false): ImportResult
    external fun createLookupObject(): Long
    external fun destroyLookupObject(session: Long)
    external fun rebuildQuery(
        session: Long,
        termPaths: Array<String>,
        freqPaths: Array<String>,
        pitchPaths: Array<String>,
    )

    // Kanji dictionaries are an upstream-engine feature; the kaihouguide bridge ignores them.
    fun rebuildQuery(
        session: Long,
        termPaths: Array<String>,
        freqPaths: Array<String>,
        pitchPaths: Array<String>,
        @Suppress("UNUSED_PARAMETER") kanjiPaths: Array<String>,
    ) = rebuildQuery(session, termPaths, freqPaths, pitchPaths)

    fun queryKanji(session: Long, kanji: String): KanjiResult = KanjiResult(kanji, emptyArray())

    external fun setLookupLanguage(session: Long, language: String)
    external fun lookup(session: Long, text: String, maxResults: Int, scanLength: Int): Array<LookupResult>

    /**
     * The kaihouguide engine orders by the first frequency dictionary, ascending, before capping at
     * [maxResults]; the other orders are applied here, on the capped results, until the engine takes
     * the options itself.
     */
    fun lookup(session: Long, text: String, maxResults: Int, scanLength: Int, options: LookupOptions): Array<LookupResult> =
        orderByFrequency(lookup(session, text, maxResults, scanLength), options)
    external fun getStyles(session: Long): Array<DictionaryStyle>
    external fun getMediaFile(session: Long, dictName: String, mediaPath: String): ByteArray?
}

/** Longest match first as the engine has it; within a length, by the chosen dictionary's frequency, entries without one last. */
internal fun orderByFrequency(results: Array<LookupResult>, options: LookupOptions): Array<LookupResult> {
    val descending = when (options.frequencyOrder) {
        LookupFrequencyOrder.Ascending -> false
        LookupFrequencyOrder.Descending -> true
        LookupFrequencyOrder.Auto, LookupFrequencyOrder.Disabled -> return results
    }
    fun rank(result: LookupResult): Int? = result.term.frequencies
        .filter { options.frequencyDictionary == null || it.dictName == options.frequencyDictionary }
        .flatMap { it.frequencies.asIterable() }
        .minOfOrNull { it.value }
    return results
        .sortedWith(
            compareByDescending<LookupResult> { it.matched.codePointCount(0, it.matched.length) }
                .thenBy { rank(it) == null }
                .thenBy { rank(it)?.let { value -> if (descending) -value else value } ?: 0 },
        )
        .toTypedArray()
}
