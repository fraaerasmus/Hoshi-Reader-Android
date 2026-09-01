package moe.antimony.hoshi.features.kosync

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import moe.antimony.hoshi.di.IoDispatcher
import moe.antimony.hoshi.epub.BookEntry
import moe.antimony.hoshi.epub.BookRepository
import moe.antimony.hoshi.epub.Bookmark
import moe.antimony.hoshi.epub.EpubBook
import moe.antimony.hoshi.epub.EpubBookParser
import moe.antimony.hoshi.features.sync.TtuSyncRules
import moe.antimony.hoshi.features.sync.resolveTtuCharacterPosition

/**
 * Reading-position sync against a KOReader kosync server, independent of the ッツ/Drive sync.
 * Pull applies a newer remote position (paragraph-exact when its XPointer resolves, percentage
 * otherwise); push sends the bookmark as a crengine XPointer plus character percentage.
 */
@Singleton
class KosyncManager private constructor(
    private val bookRepository: BookRepository,
    private val api: KosyncApi,
    private val settingsProvider: suspend () -> KosyncSettings,
    private val credentialsProvider: suspend () -> KosyncCredentials?,
    private val deviceIdProvider: () -> String,
    private val bookLoader: suspend (BookEntry) -> EpubBook?,
    private val ioDispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        bookRepository: BookRepository,
        client: KosyncClient,
        settingsRepository: KosyncSettingsRepository,
        epubBookParser: EpubBookParser,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        bookRepository = bookRepository,
        api = client,
        settingsProvider = { settingsRepository.settings.first() },
        credentialsProvider = settingsRepository::credentials,
        deviceIdProvider = { settingsRepository.deviceId },
        bookLoader = { entry ->
            runCatching {
                epubBookParser.parse(entry.root, cachedBookInfo = bookRepository.loadBookInfo(entry.root))
            }.getOrNull()
        },
        ioDispatcher = ioDispatcher,
    )

    constructor(
        bookRepository: BookRepository,
        api: KosyncApi,
        settings: KosyncSettings,
        credentials: KosyncCredentials?,
        deviceId: String,
        bookLoader: suspend (BookEntry) -> EpubBook?,
        ioDispatcher: CoroutineDispatcher,
    ) : this(
        bookRepository = bookRepository,
        api = api,
        settingsProvider = { settings },
        credentialsProvider = { credentials },
        deviceIdProvider = { deviceId },
        bookLoader = bookLoader,
        ioDispatcher = ioDispatcher,
    )

    suspend fun testConnection() {
        val credentials = credentialsProvider() ?: throw KosyncException("Enter the server, username and password first.")
        api.authorize(credentials)
    }

    suspend fun pull(entry: BookEntry, book: EpubBook? = null): KosyncResult {
        val settings = settingsProvider()
        if (!settings.enabled) return KosyncResult.Skipped
        val credentials = credentialsProvider() ?: return KosyncResult.Skipped
        val document = documentId(entry) ?: return KosyncResult.Skipped
        val title = entry.displayTitle
        val remote = api.getProgress(credentials, document) ?: return KosyncResult.Skipped
        val percentage = remote.percentage ?: return KosyncResult.Skipped
        if (remote.deviceId == deviceIdProvider()) return KosyncResult.UpToDate(title)
        val local = bookRepository.loadBookmark(entry.root)
        val remoteTimestamp = remote.timestamp
        val localSeconds = local?.lastModified?.let { TtuSyncRules.appleReferenceSecondsToUnixMillis(it) / 1_000 }
        if (local != null && (remoteTimestamp == null || localSeconds == null || remoteTimestamp <= localSeconds)) {
            return KosyncResult.UpToDate(title)
        }
        val bookmark = remoteBookmark(entry, book, remote.progress, percentage, remoteTimestamp)
            ?: return KosyncResult.Skipped
        bookRepository.saveBookmark(entry.root, bookmark)
        saveState(entry, KosyncBookState(lastSyncedCharacterCount = bookmark.characterCount, lastServerTimestamp = remoteTimestamp))
        return KosyncResult.Pulled(title, percentage)
    }

    suspend fun push(entry: BookEntry, book: EpubBook? = null): KosyncResult {
        val settings = settingsProvider()
        if (!settings.enabled || !settings.pushEnabled) return KosyncResult.Skipped
        val credentials = credentialsProvider() ?: return KosyncResult.Skipped
        val title = entry.displayTitle
        val bookmark = bookRepository.loadBookmark(entry.root) ?: return KosyncResult.Skipped
        if (loadState(entry).lastSyncedCharacterCount == bookmark.characterCount) return KosyncResult.UpToDate(title)
        val document = documentId(entry) ?: return KosyncResult.Skipped
        val bookInfo = bookRepository.loadBookInfo(entry.root) ?: return KosyncResult.Skipped
        val percentage = if (bookInfo.characterCount > 0) {
            (bookmark.characterCount.toDouble() / bookInfo.characterCount).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val loadedBook = book ?: bookLoader(entry)
        val chapter = loadedBook?.chapters?.getOrNull(bookmark.chapterIndex)
        val spineIndex = chapter?.spineIndex ?: bookmark.chapterIndex
        val xpointer = withContext(ioDispatcher) {
            val body = if (loadedBook != null && chapter != null) loadedBook.chapterBody(chapter.href) else null
            body?.let { KosyncXPointer.forProgress(spineIndex, it, bookmark.progress) }
                ?: KosyncXPointer.chapterStart(spineIndex)
        }
        val timestamp = api.putProgress(
            credentials = credentials,
            document = document,
            progress = xpointer,
            percentage = percentage,
            device = DeviceName,
            deviceId = deviceIdProvider(),
        )
        saveState(entry, KosyncBookState(lastSyncedCharacterCount = bookmark.characterCount, lastServerTimestamp = timestamp))
        return KosyncResult.Pushed(title, percentage)
    }

    private suspend fun remoteBookmark(
        entry: BookEntry,
        book: EpubBook?,
        xpointer: String?,
        percentage: Double,
        timestampSeconds: Long?,
    ): Bookmark? {
        val bookInfo = bookRepository.loadBookInfo(entry.root) ?: return null
        val lastModified = timestampSeconds?.let { TtuSyncRules.unixMillisToAppleReferenceSeconds(it * 1_000) }
            ?: bookRepository.currentAppleReferenceDateSeconds()
        val targetCharacter = (percentage.coerceIn(0.0, 1.0) * bookInfo.characterCount).roundToInt()
        val spineIndex = xpointer?.let(KosyncXPointer::spineIndex)
        val loadedBook = if (spineIndex != null) book ?: bookLoader(entry) else null
        val chapterIndex = loadedBook?.chapters?.indexOfFirst { it.spineIndex == spineIndex }?.takeIf { it >= 0 }
        if (loadedBook != null && chapterIndex != null && xpointer != null) {
            val chapter = loadedBook.chapters[chapterIndex]
            val info = loadedBook.bookInfo.chapterInfo[chapter.href]
            val progress = withContext(ioDispatcher) {
                loadedBook.chapterBody(chapter.href)?.let { body -> KosyncXPointer.resolveProgress(xpointer, body) }
            } ?: info?.takeIf { it.chapterCount > 0 }?.let { chapterInfo ->
                ((targetCharacter - chapterInfo.currentTotal).toDouble() / chapterInfo.chapterCount).coerceIn(0.0, 1.0)
            } ?: 0.0
            return Bookmark(
                chapterIndex = chapterIndex,
                progress = progress,
                characterCount = loadedBook.characterCountAt(chapterIndex, progress),
                lastModified = lastModified,
            )
        }
        val resolved = bookInfo.resolveTtuCharacterPosition(targetCharacter)
        return Bookmark(
            chapterIndex = resolved?.spineIndex ?: 0,
            progress = resolved?.progress ?: 0.0,
            characterCount = targetCharacter.coerceIn(0, bookInfo.characterCount),
            lastModified = lastModified,
        )
    }

    private fun EpubBook.chapterBody(href: String) =
        readResource(href)?.let { KosyncChapterDom.parseBody(it.toString(Charsets.UTF_8)) }

    private suspend fun documentId(entry: BookEntry): String? = withContext(ioDispatcher) {
        bookRepository.epubFile(entry)?.takeIf { it.isFile }?.let(KosyncDocumentId::partialMd5)
    }

    private suspend fun loadState(entry: BookEntry): KosyncBookState = withContext(ioDispatcher) {
        val file = stateFile(entry)
        if (!file.isFile) return@withContext KosyncBookState()
        runCatching { json.decodeFromString(KosyncBookState.serializer(), file.readText()) }.getOrDefault(KosyncBookState())
    }

    private suspend fun saveState(entry: BookEntry, state: KosyncBookState) = withContext(ioDispatcher) {
        stateFile(entry).writeText(json.encodeToString(KosyncBookState.serializer(), state))
    }

    private fun stateFile(entry: BookEntry): File = entry.root.resolve(StateFileName)

    companion object {
        const val DeviceName = "Hoshi Custom"
        private const val StateFileName = "kosync.json"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
