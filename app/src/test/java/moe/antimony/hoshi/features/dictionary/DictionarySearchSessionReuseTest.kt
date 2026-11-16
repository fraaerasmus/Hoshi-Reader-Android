package moe.antimony.hoshi.features.dictionary

import de.manhhao.hoshi.DictionaryStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import moe.antimony.hoshi.dictionary.*
import moe.antimony.hoshi.features.audio.AudioSettingsRepository
import moe.antimony.hoshi.profiles.ProfileRepository
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DictionarySearchSessionReuseTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun repeatedSearchesReuseTheSessionAndStylesUntilTheProfileChanges() = runBlocking {
        val root = temporary.newFolder()
        val profiles = ProfileRepository(root, ioDispatcher = Dispatchers.Unconfined)
        val bridge = object : DictionaryNativeBridge {
            val languages = mutableListOf<String>()
            var styleReads = 0
            override fun importDictionary(zipPath: String, outputDir: String, lowRam: Boolean): NativeDictionaryImportResult = error("unused")
            private var sessions = 0L
            override fun createLookupObject(): Long = ++sessions
            override fun setLookupLanguage(session: Long, language: String) {
                languages += language
            }
            override fun getStyles(session: Long): List<DictionaryStyle> {
                styleReads++
                return listOf(DictionaryStyle("Fixture", "session-$session"))
            }
        }
        val repository = DictionaryRepository(root, DictionaryStorageDataSource(root, profiles),
            DictionaryImportDataSource(bridge), DictionaryLookupQueryService(bridge), profileRepository = profiles)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        try {
            val model = DictionarySearchViewModel(
                AndroidDictionarySearchRepository(repository, DictionarySettingsRepository(HistoryMemoryStore()),
                    AudioSettingsRepository(HistoryMemoryStore())),
                DictionaryLookupHistoryRepository(HistoryMemoryStore(), scope, Dispatchers.Unconfined),
                scope, Dispatchers.Unconfined,
            )
            for (text in listOf("猫", "犬", "食べた")) {
                model.updateQuery(text)
                model.runLookup()
                assertEquals(text, model.uiState.value.lastQuery)
                assertNull(model.uiState.value.errorMessage)
            }
            assertEquals(listOf("ja"), bridge.languages)
            assertEquals(1, bridge.styleReads)
            assertEquals(mapOf("Fixture" to "session-1"), model.uiState.value.dictionaryStyles)
            val english = profiles.createProfile("English", "en")
            profiles.activateGlobal(english.id)
            model.onEffectiveProfileChanged(english.id)
            model.applyExternalLookup("reading")
            assertEquals(listOf("ja", "en"), bridge.languages)
            assertEquals(2, bridge.styleReads)
            assertEquals(mapOf("Fixture" to "session-2"), model.uiState.value.dictionaryStyles)
            repository.rebuildLookupQuery()
            model.applyExternalLookup("read")
            assertEquals(listOf("ja", "en", "en"), bridge.languages)
            assertEquals(3, bridge.styleReads)
            assertEquals(mapOf("Fixture" to "session-3"), model.uiState.value.dictionaryStyles)
        } finally {
            scope.cancel()
        }
    }
}
