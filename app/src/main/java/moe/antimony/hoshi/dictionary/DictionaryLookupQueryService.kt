package moe.antimony.hoshi.dictionary

import de.manhhao.hoshi.DictionaryStyle
import de.manhhao.hoshi.LookupResult
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.jvm.Volatile

@Singleton
internal class DictionaryLookupQueryService @Inject constructor(
    private val nativeBridge: DictionaryNativeBridge,
) {
    private val rebuildLock = Any()
    private val queryLock = ReentrantReadWriteLock()
    private val lookupLock = Any()
    private var currentSession: Long? = null

    // Last lookup language; applied to each lookup and re-applied to a freshly rebuilt session.
    @Volatile
    private var currentLanguage: String = DEFAULT_LANGUAGE

    fun rebuild(
        termDictionaries: List<File>,
        frequencyDictionaries: List<File>,
        pitchDictionaries: List<File>,
    ) {
        synchronized(rebuildLock) {
            val nextSession = nativeBridge.createLookupObject()
            var committed = false
            try {
                nativeBridge.rebuildQuery(
                    session = nextSession,
                    termPaths = termDictionaries.toAbsolutePathArray(),
                    freqPaths = frequencyDictionaries.toAbsolutePathArray(),
                    pitchPaths = pitchDictionaries.toAbsolutePathArray(),
                )
                nativeBridge.setLookupLanguage(nextSession, currentLanguage)
                val previousSession = queryLock.write {
                    val previous = currentSession
                    currentSession = nextSession
                    committed = true
                    previous
                }
                previousSession?.let(nativeBridge::destroyLookupObject)
            } finally {
                if (!committed) {
                    nativeBridge.destroyLookupObject(nextSession)
                }
            }
        }
    }

    fun lookup(
        text: String,
        maxResults: Int = 16,
        scanLength: Int = 16,
        language: String = currentLanguage,
    ): List<LookupResult> =
        queryLock.read {
            val session = currentSession ?: return@read emptyList()
            synchronized(lookupLock) {
                currentLanguage = language
                nativeBridge.setLookupLanguage(session, language)
                nativeBridge.lookup(session, text, maxResults, scanLength)
            }
        }

    fun getStyles(): List<DictionaryStyle> =
        queryLock.read {
            currentSession?.let(nativeBridge::getStyles) ?: emptyList()
        }

    fun getMediaFile(dictionary: String, path: String): ByteArray? =
        queryLock.read {
            currentSession?.let { session ->
                nativeBridge.getMediaFile(session, dictionary, path)
            }
        }

    private fun List<File>.toAbsolutePathArray(): Array<String> =
        map { it.absolutePath }.toTypedArray()

    private companion object {
        const val DEFAULT_LANGUAGE = "ja"
    }
}
