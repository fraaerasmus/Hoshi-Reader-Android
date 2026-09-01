package moe.antimony.hoshi.features.opds

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put

fun Context.opdsCatalogRepository(): OpdsCatalogRepository = OpdsCatalogRepository(File(filesDir, "opds_catalogs.json"))

/** Saved OPDS catalogs (URL + Basic-auth credentials) as a JSON file, included in the settings backup. */
class OpdsCatalogRepository(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val state = MutableStateFlow(load())
    val catalogs: StateFlow<List<OpdsCatalog>> = state

    suspend fun save(catalog: OpdsCatalog) {
        val id = catalog.id.ifBlank { UUID.randomUUID().toString() }
        val next = catalog.copy(id = id, name = catalog.name.trim(), url = catalog.url.trim(), username = catalog.username.trim())
        update { current -> current.filterNot { it.id == id } + next }
    }

    suspend fun delete(id: String) {
        update { current -> current.filterNot { it.id == id } }
    }

    fun exportCredentials(): JsonObject = buildJsonObject {
        put("catalogs", json.encodeToJsonElement(serializer, state.value))
    }

    suspend fun importCredentials(credentials: JsonObject) {
        val imported = credentials["catalogs"]?.jsonArray?.let { json.decodeFromJsonElement(serializer, it) } ?: return
        update { current -> current.filterNot { existing -> imported.any { it.id == existing.id } } + imported }
    }

    private suspend fun update(transform: (List<OpdsCatalog>) -> List<OpdsCatalog>) = withContext(ioDispatcher) {
        val next = transform(state.value).sortedBy { it.name.lowercase() }
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(serializer, next))
        state.value = next
    }

    private fun load(): List<OpdsCatalog> =
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(OpdsCatalog.serializer())
    }
}
