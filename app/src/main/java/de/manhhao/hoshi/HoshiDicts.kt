package de.manhhao.hoshi

class ImportResult(
    val success: Boolean,
    val title: String,
    val termCount: Long,
    val metaCount: Long,
    val freqCount: Long,
    val pitchCount: Long,
    val mediaCount: Long,
)

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

// NOTE: constructed by the kaihouguide JNI engine; keep the constructor (dictName,
// pitchPositions) exactly. `transcriptions` is a computed adapter so upstream's
// app code compiles — the kaihouguide engine has no IPA transcriptions.
class PitchEntry(
    val dictName: String,
    val pitchPositions: IntArray,
) {
    val transcriptions: Array<String> get() = emptyArray()
}

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

    external fun setLookupLanguage(session: Long, language: String)
    external fun lookup(session: Long, text: String, maxResults: Int, scanLength: Int): Array<LookupResult>
    external fun getStyles(session: Long): Array<DictionaryStyle>
    external fun getMediaFile(session: Long, dictName: String, mediaPath: String): ByteArray?
}
