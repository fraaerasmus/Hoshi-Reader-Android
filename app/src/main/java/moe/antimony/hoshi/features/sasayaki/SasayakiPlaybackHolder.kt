package moe.antimony.hoshi.features.sasayaki

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val visibleChapter = SasayakiVisibleChapterTracker()
    private var currentIdentity: SasayakiNowPlaying? = null
    private val _nowPlaying = MutableStateFlow<SasayakiNowPlaying?>(null)
    private var lastCueChapter: Int = 0
    private var sleepTimerJob: Job? = null
    private var sleepUntilChapterChangesFrom: Int? = null
    private val _sleepTimer = MutableStateFlow(SasayakiSleepTimerState())

    /** The book whose audio is currently engaged (played at least once); null otherwise. */
    val nowPlaying: StateFlow<SasayakiNowPlaying?> = _nowPlaying.asStateFlow()

    /** Live transport state (play/pause, position) of the active player. */
    val snapshot: StateFlow<SasayakiPlaybackSnapshot> = SasayakiPlaybackStatePublisher.snapshot

    /** Active sleep timer, counting down on the application scope so it survives backgrounding. */
    val sleepTimer: StateFlow<SasayakiSleepTimerState> = _sleepTimer.asStateFlow()

    init {
        // Surface "now playing" only once playback actually engages, so the mini-player doesn't
        // appear for a book you merely opened to read.
        appScope.launch {
            SasayakiPlaybackStatePublisher.snapshot.collect { snapshot ->
                if (snapshot.isPlaying) currentIdentity?.let { _nowPlaying.value = it }
            }
        }
    }

    /** Build the player for [bookRoot], or return the live one if it is already loaded. */
    fun loadBook(
        bookId: String,
        bookRoot: File,
        playbackRepository: SasayakiPlaybackRepository,
        bookTitle: String?,
        bookCoverFile: File?,
        matchData: SasayakiMatchData?,
        initialPlayback: SasayakiPlaybackData?,
    ): SasayakiPlayer {
        currentPlayer?.let { if (currentBookKey == bookRoot) return it }
        currentPlayer?.release()
        // New book: hold its identity but keep the mini-player hidden until it is played.
        currentIdentity = SasayakiNowPlaying(bookId, bookTitle ?: bookRoot.name, bookCoverFile)
        _nowPlaying.value = null
        SasayakiPlaybackStatePublisher.snapshot.value = SasayakiPlaybackSnapshot()
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
                visibleChapter.resolve(binding?.getCurrentChapterIndex?.invoke())
            },
            onCue = { cue, reveal ->
                onCuePlayed(cue.chapterIndex)
                binding?.onCue?.invoke(cue, reveal)
            },
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

    fun togglePlayback() {
        currentPlayer?.togglePlayback()
    }

    /** Skip forward/back honoring the Skip Action setting, like the reader's own controls. */
    fun skipForward() {
        currentPlayer?.nextCue()
    }

    fun skipBackward() {
        currentPlayer?.previousCue()
    }

    fun cycleSpeed() {
        currentPlayer?.cycleSpeed()
    }

    fun setSleepTimer(option: SasayakiSleepTimerOption) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepUntilChapterChangesFrom = null
        val minutes = option.minutes
        when {
            option == SasayakiSleepTimerOption.Off -> _sleepTimer.value = SasayakiSleepTimerState()
            option == SasayakiSleepTimerOption.EndOfChapter -> {
                sleepUntilChapterChangesFrom = lastCueChapter
                _sleepTimer.value = SasayakiSleepTimerState(option = option)
            }
            minutes != null -> {
                sleepTimerJob = appScope.launch {
                    var remaining = minutes * 60
                    _sleepTimer.value = SasayakiSleepTimerState(option, remaining)
                    while (remaining > 0) {
                        delay(1000L)
                        remaining -= 1
                        _sleepTimer.value = SasayakiSleepTimerState(option, remaining)
                    }
                    currentPlayer?.pausePlayback()
                    _sleepTimer.value = SasayakiSleepTimerState()
                }
            }
        }
    }

    private fun onCuePlayed(chapterIndex: Int) {
        lastCueChapter = chapterIndex
        val from = sleepUntilChapterChangesFrom ?: return
        if (chapterIndex != from) {
            sleepUntilChapterChangesFrom = null
            currentPlayer?.pausePlayback()
            _sleepTimer.value = SasayakiSleepTimerState()
        }
    }

    /** Stop and tear down the active player (no book / shutdown). */
    fun releaseForBook() {
        binding = null
        currentPlayer?.release()
        currentPlayer = null
        currentBookKey = null
        currentIdentity = null
        _nowPlaying.value = null
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepUntilChapterChangesFrom = null
        _sleepTimer.value = SasayakiSleepTimerState()
        SasayakiPlaybackStatePublisher.snapshot.value = SasayakiPlaybackSnapshot()
    }
}
