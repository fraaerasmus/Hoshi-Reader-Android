package moe.antimony.hoshi.features.sasayaki

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import moe.antimony.hoshi.di.ApplicationScope
import moe.antimony.hoshi.epub.SasayakiMatchData
import moe.antimony.hoshi.epub.SasayakiPlaybackData

/**
 * App-lifetime owner of the active Sasayaki player. Owning it here (instead of in the reader
 * Composition) is what lets audio keep playing after the reader screen is gone: the reader
 * attaches a [SasayakiReaderBinding] while visible and detaches on dispose, but the player
 * outlives the binding. Persistence runs on the application scope so saves never stop when the
 * reader backgrounds.
 */
@Singleton
class SasayakiPlaybackHolder @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {
    private var currentPlayer: SasayakiPlayer? = null
    private var currentBookKey: File? = null
    private var binding: SasayakiReaderBinding? = null
    private var lastVisibleChapter: Int = 0

    /** Build the player for [bookRoot], or return the live one if it is already loaded. */
    fun loadBook(
        bookRoot: File,
        playbackRepository: SasayakiPlaybackRepository,
        bookTitle: String?,
        bookCoverFile: File?,
        matchData: SasayakiMatchData?,
        initialPlayback: SasayakiPlaybackData?,
    ): SasayakiPlayer {
        currentPlayer?.let { if (currentBookKey == bookRoot) return it }
        currentPlayer?.release()
        return SasayakiPlayer(
            context = appContext,
            bookRoot = bookRoot,
            playbackRepository = playbackRepository,
            bookTitle = bookTitle,
            bookCoverFile = bookCoverFile,
            matchData = matchData,
            initialPlayback = initialPlayback,
            persistenceScope = appScope,
            // Stable forwarding lambdas: they read the current binding at call time, so the live
            // reader can be swapped (or removed) without rebuilding the controller.
            getCurrentChapterIndex = {
                binding?.getCurrentChapterIndex?.invoke()?.also { lastVisibleChapter = it }
                    ?: lastVisibleChapter
            },
            onCue = { cue, reveal -> binding?.onCue?.invoke(cue, reveal) },
            onClearCue = { binding?.onClearCue?.invoke() },
            onLoadChapter = { index -> binding?.onLoadChapter?.invoke(index) },
        ).also {
            currentPlayer = it
            currentBookKey = bookRoot
        }
    }

    /** The live player only if it is the one loaded for [bookRoot] (else null). */
    fun playerFor(bookRoot: File?): SasayakiPlayer? =
        currentPlayer?.takeIf { bookRoot != null && currentBookKey == bookRoot }

    /** Point playback at a visible reader and replay the current highlight onto its WebView. */
    fun attachReader(binding: SasayakiReaderBinding) {
        this.binding = binding
        currentPlayer?.redisplayCue()
    }

    /** Reader gone — keep playing headless; callbacks no-op until a reader re-attaches. */
    fun detachReader() {
        binding = null
    }

    /** Stop and tear down the active player (no book / shutdown). */
    fun releaseForBook() {
        binding = null
        currentPlayer?.release()
        currentPlayer = null
        currentBookKey = null
    }
}
