package moe.antimony.hoshi.features.backup

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import moe.antimony.hoshi.di.ApplicationScope
import moe.antimony.hoshi.di.IoDispatcher
import moe.antimony.hoshi.epub.BookEntry
import moe.antimony.hoshi.epub.BookRepository
import moe.antimony.hoshi.features.kosync.KosyncDocumentId
import moe.antimony.hoshi.features.sync.SyncBackend
import moe.antimony.hoshi.features.sync.SyncBackoff
import moe.antimony.hoshi.features.sync.SyncStatusRepository

@Serializable
data class RemoteBackupIndex(
    val devices: List<Device> = emptyList(),
    val books: List<Book> = emptyList(),
) {
    @Serializable
    data class Device(val name: String, val updatedAtMillis: Long, val appVersion: String? = null)

    @Serializable
    data class Book(val key: String, val title: String, val updatedAtMillis: Long)
}

/** Per-book manifest next to the sidecars: which files are there and how fresh they are. */
@Serializable
data class RemoteBookState(
    val title: String,
    val updatedAtMillis: Long,
    val files: Map<String, Long>,
)

data class RemoteBackupResult(val settingsUploaded: Boolean, val booksUploaded: Int)

/** The slice of [SettingsBackupRepository] the manager needs; tests substitute a fake envelope. */
interface SettingsBackupSnapshot {
    suspend fun exportEnvelope(): JsonObject
    fun encode(envelope: JsonObject): String
    suspend fun importSettingsJson(text: String)
}

/**
 * Backs the settings JSON and each book's small sidecars up to the user's WebDAV share, keyed by the
 * book's content hash so the same EPUB on a new install gets its highlights and statistics back.
 */
@Singleton
class RemoteBackupManager private constructor(
    private val bookRepository: BookRepository,
    private val settingsBackup: SettingsBackupSnapshot,
    private val store: RemoteBackupStore,
    private val settings: suspend () -> RemoteBackupSettings,
    private val credentials: suspend () -> RemoteBackupCredentials?,
    private val bookkeeping: RemoteBackupBookkeeping,
    private val recordStatus: suspend (String?) -> Unit,
    private val appVersion: String?,
    private val nowMillis: () -> Long,
    private val ioDispatcher: CoroutineDispatcher,
    private val backoff: SyncBackoff = SyncBackoff(),
) {
    @Inject
    constructor(
        bookRepository: BookRepository,
        settingsBackup: SettingsBackupRepository,
        client: WebDavClient,
        settingsRepository: RemoteBackupSettingsRepository,
        syncStatusRepository: SyncStatusRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        bookRepository = bookRepository,
        settingsBackup = settingsBackup,
        store = client,
        settings = { settingsRepository.settings.first() },
        credentials = settingsRepository::credentials,
        bookkeeping = settingsRepository,
        recordStatus = { error -> syncStatusRepository.record(SyncBackend.RemoteBackup, error) },
        appVersion = moe.antimony.hoshi.BuildConfig.VERSION_NAME,
        nowMillis = System::currentTimeMillis,
        ioDispatcher = ioDispatcher,
    )

    constructor(
        bookRepository: BookRepository,
        settingsBackup: SettingsBackupSnapshot,
        store: RemoteBackupStore,
        settings: RemoteBackupSettings,
        credentials: RemoteBackupCredentials?,
        bookkeeping: RemoteBackupBookkeeping,
        recordStatus: suspend (String?) -> Unit = {},
        nowMillis: () -> Long = System::currentTimeMillis,
        ioDispatcher: CoroutineDispatcher,
    ) : this(
        bookRepository = bookRepository,
        settingsBackup = settingsBackup,
        store = store,
        settings = { settings },
        credentials = { credentials },
        bookkeeping = bookkeeping,
        recordStatus = recordStatus,
        appVersion = null,
        nowMillis = nowMillis,
        ioDispatcher = ioDispatcher,
    )

    private val mutex = Mutex()

    /** Settings when their fingerprint changed (or [force]), then every book whose sidecars changed. */
    suspend fun backupAll(force: Boolean = false): RemoteBackupResult = guarded {
        val settings = settings()
        val credentials = requireCredentials()
        val settingsUploaded = uploadSettings(credentials, settings, force)
        var booksUploaded = 0
        if (settings.backupBookState) {
            bookRepository.loadBookEntries().forEach { entry ->
                if (uploadBookState(credentials, entry, force)) booksUploaded++
            }
        }
        RemoteBackupResult(settingsUploaded, booksUploaded)
    }

    suspend fun backupBookState(entry: BookEntry, force: Boolean = false): Boolean = guarded {
        uploadBookState(requireCredentials(), entry, force)
    }

    suspend fun listIndex(): RemoteBackupIndex = guarded { readIndex(requireCredentials()) }

    suspend fun restoreSettings(deviceName: String) = guarded {
        val bytes = store.get(requireCredentials(), settingsPath(deviceName))
            ?: throw RemoteBackupException("No settings backup for $deviceName on the server.")
        settingsBackup.importSettingsJson(bytes.decodeToString())
    }

    /** Writes the server's sidecars over the local ones; false when the server has nothing for this book. */
    suspend fun restoreBookState(entry: BookEntry, onlyIfLocalEmpty: Boolean = false): Boolean = guarded {
        val credentials = requireCredentials()
        val key = bookKey(entry) ?: return@guarded false
        val state = store.get(credentials, bookPath(key, MetaFileName))?.let { json.decodeFromString(RemoteBookState.serializer(), it.decodeToString()) }
            ?: return@guarded false
        if (onlyIfLocalEmpty && hasLocalReadingData(entry)) return@guarded false
        withContext(ioDispatcher) {
            state.files.keys.filter { it in SidecarFileNames }.forEach { name ->
                store.get(credentials, bookPath(key, name))?.let { entry.root.resolve(name).writeBytes(it) }
            }
        }
        bookkeeping.setBookStamp(entry.root.name, localStamp(entry))
        true
    }

    /** After an import: quietly bring back reading data for a book the server already knows, if enabled. */
    suspend fun restoreBookStateAfterImport(entry: BookEntry): Boolean {
        val settings = settings()
        if (!settings.enabled || !settings.backupBookState) return false
        return runCatching { restoreBookState(entry, onlyIfLocalEmpty = true) }.getOrDefault(false)
    }

    private suspend fun uploadSettings(credentials: RemoteBackupCredentials, settings: RemoteBackupSettings, force: Boolean): Boolean {
        val envelope = settingsBackup.exportEnvelope()
        val fingerprint = fingerprint(envelope)
        if (!force && fingerprint == bookkeeping.settingsFingerprint) return false
        val bytes = settingsBackup.encode(envelope).toByteArray()
        val device = settings.deviceName.ifBlank { "device" }
        // Keep exactly one older copy as a rollback; the share never grows past two files per device.
        store.get(credentials, settingsPath(device))?.let { store.put(credentials, previousSettingsPath(device), it) }
        store.put(credentials, settingsPath(device), bytes)
        updateIndex(credentials) { index ->
            index.copy(devices = index.devices.filterNot { it.name == device } + RemoteBackupIndex.Device(device, nowMillis(), appVersion))
        }
        bookkeeping.settingsFingerprint = fingerprint
        return true
    }

    private suspend fun uploadBookState(credentials: RemoteBackupCredentials, entry: BookEntry, force: Boolean): Boolean {
        val stamp = localStamp(entry)
        if (stamp == 0L) return false
        if (!force && stamp <= (bookkeeping.bookStamp(entry.root.name) ?: -1L)) return false
        val key = bookKey(entry) ?: return false
        val files = withContext(ioDispatcher) {
            SidecarFileNames.mapNotNull { name ->
                val file = entry.root.resolve(name)
                if (file.isFile) name to file else null
            }
        }
        files.forEach { (name, file) -> store.put(credentials, bookPath(key, name), withContext(ioDispatcher) { file.readBytes() }) }
        val state = RemoteBookState(
            title = entry.displayTitle,
            updatedAtMillis = nowMillis(),
            files = files.associate { (name, file) -> name to file.lastModified() },
        )
        store.put(credentials, bookPath(key, MetaFileName), json.encodeToString(RemoteBookState.serializer(), state).toByteArray())
        updateIndex(credentials) { index ->
            index.copy(books = index.books.filterNot { it.key == key } + RemoteBackupIndex.Book(key, entry.displayTitle, state.updatedAtMillis))
        }
        bookkeeping.setBookStamp(entry.root.name, stamp)
        return true
    }

    private suspend fun readIndex(credentials: RemoteBackupCredentials): RemoteBackupIndex =
        store.get(credentials, IndexFileName)
            ?.let { runCatching { json.decodeFromString(RemoteBackupIndex.serializer(), it.decodeToString()) }.getOrNull() }
            ?: RemoteBackupIndex()

    private suspend fun updateIndex(credentials: RemoteBackupCredentials, transform: (RemoteBackupIndex) -> RemoteBackupIndex) {
        val next = transform(readIndex(credentials))
        store.put(credentials, IndexFileName, json.encodeToString(RemoteBackupIndex.serializer(), next).toByteArray())
    }

    private suspend fun bookKey(entry: BookEntry): String? = withContext(ioDispatcher) {
        bookRepository.epubFile(entry)?.takeIf { it.isFile }?.let(KosyncDocumentId::partialMd5)
    }

    private suspend fun localStamp(entry: BookEntry): Long = withContext(ioDispatcher) {
        SidecarFileNames.maxOf { entry.root.resolve(it).lastModified() }
    }

    private suspend fun hasLocalReadingData(entry: BookEntry): Boolean = withContext(ioDispatcher) {
        entry.root.resolve("statistics.json").isFile || entry.root.resolve("highlights.json").isFile
    }

    private suspend fun requireCredentials(): RemoteBackupCredentials =
        credentials() ?: throw RemoteBackupException("Set the backup server address first.")

    private suspend fun <T> guarded(block: suspend () -> T): T = mutex.withLock {
        try {
            block().also { backoff.recordSuccess(); recordStatus(null) }
        } catch (error: Exception) {
            backoff.recordFailure()
            recordStatus(error.localizedMessage ?: error::class.java.simpleName)
            throw error
        }
    }

    /** Runs the automatic backup unless a recent attempt failed. */
    suspend fun backupAllIfDue() {
        val settings = settings()
        if (!settings.enabled || !settings.autoBackup || backoff.isCoolingDown()) return
        runCatching { backupAll() }
    }

    companion object {
        const val IndexFileName = "index.json"
        const val DevicesDir = "devices"
        const val BooksDir = "books"
        const val MetaFileName = "meta.json"

        /** The per-book files worth keeping; the EPUB, cover and audio come back by other means. */
        val SidecarFileNames = listOf(
            "bookmark.json",
            "statistics.json",
            "highlights.json",
            "sasayaki_match.json",
            "sasayaki_playback.json",
            "positions.json",
            "kosync.json",
        )

        fun settingsPath(deviceName: String) = "$DevicesDir/$deviceName/settings.json"
        fun previousSettingsPath(deviceName: String) = "$DevicesDir/$deviceName/settings.previous.json"
        fun bookPath(key: String, fileName: String) = "$BooksDir/$key/$fileName"

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** The envelope without the per-export stamps, so an unchanged configuration is not re-uploaded. */
        fun fingerprint(envelope: JsonObject): String {
            val stable = JsonObject(envelope.filterKeys { it != "exportedAt" && it != "appVersionName" })
            val digest = MessageDigest.getInstance("SHA-256").digest(stable.toString().toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

/** Uploads changed settings and book data whenever the app leaves the foreground. */
@Singleton
class RemoteBackupScheduler @Inject constructor(
    private val manager: dagger.Lazy<RemoteBackupManager>,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    fun registerProcessBackgroundBackups(lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle) {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    appScope.launch(ioDispatcher) { manager.get().backupAllIfDue() }
                }
            },
        )
    }
}
