package moe.antimony.hoshi.features.sasayaki

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.content.IntentCompat
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.antimony.hoshi.di.ApplicationScope
import moe.antimony.hoshi.di.IoDispatcher
import moe.antimony.hoshi.MainActivity
import moe.antimony.hoshi.R
import moe.antimony.hoshi.epub.SasayakiMatch
import moe.antimony.hoshi.epub.SasayakiMatchData
import moe.antimony.hoshi.epub.SasayakiPlaybackData
import java.io.File
import java.util.concurrent.Executor

internal const val SasayakiPlaybackReturnAction = "moe.antimony.hoshi.action.RETURN_TO_SASAYAKI_READER"
internal const val SasayakiPlaybackReturnBookIdExtra = "moe.antimony.hoshi.extra.SASAYAKI_BOOK_ID"
internal const val SasayakiCycleSpeedAction = "moe.antimony.hoshi.sasayaki.CYCLE_SPEED"

internal val sasayakiCycleSpeedCommand: SessionCommand
    get() = SessionCommand(SasayakiCycleSpeedAction, Bundle.EMPTY)

/** Discrete speed steps for the cycle button (notification/lock screen + mini-player chip). */
internal fun sasayakiNextCycleSpeed(rate: Float): Float {
    val speeds = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
    return speeds.firstOrNull { it > rate + 0.01f } ?: speeds.first()
}

/**
 * Media3 speed glyph for the current rate (glyphs exist up to 2.0x; above that
 * falls back to the generic speed icon).
 */
@OptIn(UnstableApi::class)
internal fun sasayakiSpeedButtonIconFor(rate: Float): Int = when {
    rate < 1.1f -> CommandButton.ICON_PLAYBACK_SPEED_1_0
    rate < 1.35f -> CommandButton.ICON_PLAYBACK_SPEED_1_2
    rate < 1.65f -> CommandButton.ICON_PLAYBACK_SPEED_1_5
    rate < 1.9f -> CommandButton.ICON_PLAYBACK_SPEED_1_8
    rate < 2.25f -> CommandButton.ICON_PLAYBACK_SPEED_2_0
    else -> CommandButton.ICON_PLAYBACK_SPEED
}

internal data class SasayakiPlaybackRuntimeLoadRequest(
    val bookId: String,
    val bookRoot: File,
    val playbackRepository: SasayakiPlaybackRepository,
    val bookTitle: String?,
    val bookCoverFile: File?,
    val matchData: SasayakiMatchData?,
    val initialPlayback: SasayakiPlaybackData?,
)

internal interface SasayakiPlaybackRuntime {
    fun load(
        request: SasayakiPlaybackRuntimeLoadRequest,
        getCurrentChapterIndex: () -> Int,
        onCue: (SasayakiMatch, Boolean, SasayakiCueRevealSource) -> Unit,
        onClearCue: () -> Unit,
    ): SasayakiPlaybackControllerContract

    fun detachReader()
    fun stopPlayback()
}

@OptIn(UnstableApi::class)
@Singleton
internal class SasayakiPlaybackServiceRuntime @Inject constructor(
    @ApplicationContext context: Context,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SasayakiPlaybackRuntime {
    private val appContext = context.applicationContext
    private var player: ExoPlayerSasayakiPlayerHandle? = null
    private var session: MediaSession? = null
    private var playbackServiceConnection: ListenableFuture<MediaController>? = null
    private var activeKey: ActivePlaybackKey? = null
    private var activeBookId: String? = null
    private var activeController: SasayakiPlaybackControllerContract? = null
    private var foregroundPlaybackRequested = false
    private val readerAttachment = SasayakiReaderAttachment()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }

    // App-scoped playback hub for the cross-tab mini-player + sleep timer. Identity is captured on
    // load(); nowPlaying stays null until the first playing snapshot so the bar hides for books you
    // only open to read.
    private var pendingIdentity: SasayakiNowPlaying? = null
    private var sleepTimerJob: Job? = null
    private var sleepTargetSeconds: Double? = null
    private var currentSpeedIcon = CommandButton.ICON_PLAYBACK_SPEED_1_0
    private val _nowPlaying = MutableStateFlow<SasayakiNowPlaying?>(null)
    val nowPlaying: StateFlow<SasayakiNowPlaying?> = _nowPlaying.asStateFlow()
    private val _snapshot = MutableStateFlow(SasayakiPlaybackSnapshot())
    val snapshot: StateFlow<SasayakiPlaybackSnapshot> = _snapshot.asStateFlow()
    private val _sleepTimer = MutableStateFlow(SasayakiSleepTimerState())
    val sleepTimer: StateFlow<SasayakiSleepTimerState> = _sleepTimer.asStateFlow()

    fun createServiceSession(serviceContext: Context): MediaSession {
        session?.let { return it }

        val createdPlayer = ExoPlayerSasayakiPlayerHandle(
            ExoPlayer.Builder(serviceContext)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build(),
        ).apply {
            setAudioAttributes(sasayakiMedia3AudioAttributes(), true)
        }
        val sessionPlayer = SasayakiServiceSessionPlayer(
            player = createdPlayer.player,
            onPlay = ::playFromSession,
            onPause = ::pauseFromSession,
            onSkipToPrevious = ::previousFromSession,
            onSkipToNext = ::nextFromSession,
            onSeekTo = ::seekToFromSession,
        )
        val createdSession = MediaSession.Builder(serviceContext, sessionPlayer)
            .setId(SasayakiPlaybackService.SessionId)
            .setMediaButtonPreferences(currentMediaButtons())
            .setSessionActivity(sasayakiPlaybackReturnPendingIntent(appContext, activeBookId))
            .setCallback(SasayakiPlaybackServiceSessionCallback(runtime = this))
            .build()

        player = createdPlayer
        session = createdSession
        return createdSession
    }

    fun currentSession(): MediaSession? =
        session

    fun activePlaybackBookId(): String? =
        activeBookId.takeIf { activeController?.hasAudio == true }

    fun playbackReturnPendingIntent(): PendingIntent =
        sasayakiPlaybackReturnPendingIntent(appContext, activeBookId)

    fun isForegroundPlaybackRequested(): Boolean =
        foregroundPlaybackRequested

    fun shouldRunPlaybackServiceInForeground(player: Player): Boolean =
        sasayakiShouldRunPlaybackServiceInForeground(
            foregroundPlaybackRequested = foregroundPlaybackRequested,
            playWhenReady = player.playWhenReady,
            playbackState = player.playbackState,
        )

    override fun load(
        request: SasayakiPlaybackRuntimeLoadRequest,
        getCurrentChapterIndex: () -> Int,
        onCue: (SasayakiMatch, Boolean, SasayakiCueRevealSource) -> Unit,
        onClearCue: () -> Unit,
    ): SasayakiPlaybackControllerContract {
        val requestedKey = ActivePlaybackKey(
            bookRoot = request.bookRoot.stableIdentity(),
        )
        activeController?.let { controller ->
            if (activeKey == requestedKey) {
                activeBookId = request.bookId
                session?.setSessionActivity(sasayakiPlaybackReturnPendingIntent(appContext, request.bookId))
                readerAttachment.attach(
                    getCurrentChapterIndex = getCurrentChapterIndex,
                    onCue = onCue,
                    onClearCue = onClearCue,
                )
                controller.updateMatchData(request.matchData)
                return controller
            }
        }

        releaseActiveController(clearBookId = false)
        readerAttachment.attach(
            getCurrentChapterIndex = getCurrentChapterIndex,
            onCue = onCue,
            onClearCue = onClearCue,
        )
        activeBookId = request.bookId
        pendingIdentity = SasayakiNowPlaying(
            bookId = request.bookId,
            title = request.bookTitle ?: request.bookRoot.name,
            coverFile = request.bookCoverFile,
        )
        session?.setSessionActivity(sasayakiPlaybackReturnPendingIntent(appContext, request.bookId))
        val controller = SasayakiPlaybackController(
            context = appContext,
            bookRoot = request.bookRoot,
            playbackRepository = request.playbackRepository,
            bookTitle = request.bookTitle,
            bookCoverFile = request.bookCoverFile,
            matchData = request.matchData,
            initialPlayback = request.initialPlayback,
            persistenceScope = appScope,
            persistenceDispatcher = ioDispatcher,
            getCurrentChapterIndex = readerAttachment::currentChapterIndex,
            onCue = readerAttachment::cue,
            onClearCue = readerAttachment::clearCue,
            playbackPreparer = ServiceOwnedSasayakiPlaybackPreparer(
                playerProvider = ::requirePlayer,
            ),
            onPlaybackStartRequested = ::ensurePlaybackServiceReady,
            onForegroundPlaybackRequestedChanged = ::setForegroundPlaybackRequested,
            onPlaybackSnapshot = ::handleSnapshot,
            restoreAudioOnCreate = false,
        )
        activeKey = requestedKey
        activeController = controller
        return controller
    }

    override fun detachReader() {
        readerAttachment.detach()
    }

    override fun stopPlayback() {
        setForegroundPlaybackRequested(false)
        releaseActiveController()
        readerAttachment.detach()
        releasePlaybackServiceConnection()
    }

    fun previousFromSession() {
        activeController?.previousCue()
    }

    fun playFromSession(): Boolean {
        val controller = activeController ?: return false
        if (!controller.isPlaying) {
            controller.togglePlayback()
        }
        return true
    }

    fun pauseFromSession(): Boolean {
        val controller = activeController ?: return false
        controller.pausePlayback(restoreTemporaryPosition = true)
        return true
    }

    fun nextFromSession() {
        activeController?.nextCue()
    }

    fun seekToFromSession(positionMs: Long) {
        activeController?.seekTo(positionMs.coerceAtLeast(0L) / 1000.0)
    }

    // Mini-player transport.
    fun togglePlayback() {
        activeController?.togglePlayback()
    }

    fun skipForward() {
        activeController?.nextCue()
    }

    fun skipBackward() {
        activeController?.previousCue()
    }

    /** Steps the playback rate through the discrete cycle (1.0 -> ... -> 3.0 -> 1.0). */
    fun cycleSpeed() {
        val controller = activeController ?: return
        controller.setRate(sasayakiNextCycleSpeed(controller.rate))
    }

    internal fun currentMediaButtons(): List<CommandButton> =
        sasayakiServiceMediaButtons(appContext, currentSpeedIcon)

    /**
     * Arm the sleep timer. [endOfChapterSeconds] is the audio position (seconds) at which the current
     * chapter ends, supplied by the sheet for [SasayakiSleepTimerOption.EndOfChapter]; minute options
     * count down on [appScope] and pause when they elapse.
     */
    fun setSleepTimer(option: SasayakiSleepTimerOption, endOfChapterSeconds: Double? = null) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTargetSeconds = null
        when {
            option == SasayakiSleepTimerOption.Off -> {
                _sleepTimer.value = SasayakiSleepTimerState()
            }
            option == SasayakiSleepTimerOption.EndOfChapter -> {
                sleepTargetSeconds = endOfChapterSeconds
                _sleepTimer.value = SasayakiSleepTimerState(option = SasayakiSleepTimerOption.EndOfChapter)
            }
            option.minutes != null -> {
                val totalSeconds = option.minutes * 60
                _sleepTimer.value = SasayakiSleepTimerState(option = option, remainingSeconds = totalSeconds)
                sleepTimerJob = appScope.launch {
                    var remaining = totalSeconds
                    while (remaining > 0) {
                        delay(1000L)
                        remaining -= 1
                        _sleepTimer.value = SasayakiSleepTimerState(option = option, remainingSeconds = remaining)
                    }
                    activeController?.pausePlayback(restoreTemporaryPosition = true)
                    _sleepTimer.value = SasayakiSleepTimerState()
                }
            }
        }
    }

    private fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTargetSeconds = null
        _sleepTimer.value = SasayakiSleepTimerState()
    }

    private fun handleSnapshot(snapshot: SasayakiPlaybackSnapshot) {
        _snapshot.value = snapshot
        if (_nowPlaying.value == null && snapshot.isPlaying) {
            _nowPlaying.value = pendingIdentity
        }
        val speedIcon = sasayakiSpeedButtonIconFor(snapshot.speed)
        if (speedIcon != currentSpeedIcon) {
            currentSpeedIcon = speedIcon
            session?.setMediaButtonPreferences(currentMediaButtons())
        }
        val target = sleepTargetSeconds
        if (target != null && snapshot.isPlaying && snapshot.positionMs / 1000.0 >= target) {
            sleepTargetSeconds = null
            activeController?.pausePlayback(restoreTemporaryPosition = true)
            _sleepTimer.value = SasayakiSleepTimerState()
        }
    }

    fun release() {
        setForegroundPlaybackRequested(false)
        releaseActiveController()
        readerAttachment.detach()
        releasePlaybackServiceConnection()
        session?.release()
        session = null
        player?.release()
        player = null
    }

    private fun requirePlayer(): Media3SasayakiPlayerHandle {
        return requireNotNull(player) {
            "SasayakiPlaybackService must create the player before audio can be restored."
        }
    }

    private fun ensurePlaybackServiceConnection(): ListenableFuture<MediaController> {
        playbackServiceConnection?.let { return it }
        // Reader still calls this in-process runtime; the service connection enters the MediaSessionService lifecycle.
        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, SasayakiPlaybackService::class.java),
        )
        return MediaController.Builder(appContext, sessionToken).buildAsync().also { future ->
            playbackServiceConnection = future
        }
    }

    internal fun releasePlaybackServiceConnection() {
        playbackServiceConnection?.let(MediaController::releaseFuture)
        playbackServiceConnection = null
    }

    private fun setForegroundPlaybackRequested(requested: Boolean) {
        foregroundPlaybackRequested = requested
    }

    private fun ensurePlaybackServiceReady(onReady: () -> Unit) {
        val serviceConnection = ensurePlaybackServiceConnection()
        serviceConnection.addListener(
            {
                runCatching { Futures.getDone(serviceConnection) }.getOrNull() ?: return@addListener
                onReady()
            },
            mainExecutor,
        )
    }

    private fun releaseActiveController(clearBookId: Boolean = true) {
        setForegroundPlaybackRequested(false)
        activeController?.release()
        activeController = null
        activeKey = null
        cancelSleepTimer()
        _nowPlaying.value = null
        _snapshot.value = SasayakiPlaybackSnapshot()
        pendingIdentity = null
        if (clearBookId) {
            activeBookId = null
        }
    }

    private data class ActivePlaybackKey(
        val bookRoot: File,
    )

    private fun File.stableIdentity(): File =
        runCatching { canonicalFile }.getOrElse { absoluteFile }
}

@OptIn(UnstableApi::class)
private class SasayakiPlaybackServiceSessionCallback(
    private val runtime: SasayakiPlaybackServiceRuntime,
) : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val playerCommands = Player.Commands.Builder()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_BACK)
            .add(Player.COMMAND_SEEK_FORWARD)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_TIMELINE)
            .add(Player.COMMAND_GET_METADATA)
            .add(Player.COMMAND_GET_AUDIO_ATTRIBUTES)
            .add(Player.COMMAND_RELEASE)
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailablePlayerCommands(playerCommands)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(sasayakiCycleSpeedCommand)
                    .build(),
            )
            .setMediaButtonPreferences(runtime.currentMediaButtons())
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (customCommand.customAction == SasayakiCycleSpeedAction) {
            runtime.cycleSpeed()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        return super.onCustomCommand(session, controller, customCommand, args)
    }

    // A single-item player doesn't advertise SEEK_TO_NEXT/PREVIOUS, so the default
    // routing drops headset next/previous keys. Intercept the transport keys here
    // (before that gate) and reuse the skip callbacks, which honor Skip Action.
    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent,
    ): Boolean {
        val keyEvent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            ?: return false
        val isDown = keyEvent.action == KeyEvent.ACTION_DOWN
        return when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            -> {
                if (isDown) runtime.nextFromSession()
                true
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            -> {
                if (isDown) runtime.previousFromSession()
                true
            }

            else -> false
        }
    }
}

@OptIn(UnstableApi::class)
private class SasayakiServiceSessionPlayer(
    player: Player,
    private val onPlay: () -> Boolean,
    private val onPause: () -> Boolean,
    private val onSkipToPrevious: () -> Unit,
    private val onSkipToNext: () -> Unit,
    private val onSeekTo: (Long) -> Unit,
) : ForwardingPlayer(player) {
    override fun play() {
        onPlay()
    }

    override fun pause() {
        onPause()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) {
            play()
        } else {
            pause()
        }
    }

    override fun seekBack() {
        onSkipToPrevious()
    }

    override fun seekForward() {
        onSkipToNext()
    }

    override fun seekToPrevious() {
        onSkipToPrevious()
    }

    override fun seekToPreviousMediaItem() {
        onSkipToPrevious()
    }

    override fun seekToNext() {
        onSkipToNext()
    }

    override fun seekToNextMediaItem() {
        onSkipToNext()
    }

    override fun seekTo(positionMs: Long) {
        onSeekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        onSeekTo(positionMs)
    }
}

internal data class SasayakiServiceMediaButtonSpec(
    val icon: Int,
    val displayNameResId: Int,
    val slot: Int,
    val playerCommand: Int,
)

@OptIn(UnstableApi::class)
internal fun sasayakiServiceMediaButtonSpecs(): List<SasayakiServiceMediaButtonSpec> =
    listOf(
        SasayakiServiceMediaButtonSpec(
            icon = CommandButton.ICON_REWIND,
            displayNameResId = R.string.sasayaki_rewind,
            slot = CommandButton.SLOT_BACK,
            playerCommand = Player.COMMAND_SEEK_BACK,
        ),
        SasayakiServiceMediaButtonSpec(
            icon = CommandButton.ICON_FAST_FORWARD,
            displayNameResId = R.string.sasayaki_fast_forward,
            slot = CommandButton.SLOT_FORWARD,
            playerCommand = Player.COMMAND_SEEK_FORWARD,
        ),
    )

@OptIn(UnstableApi::class)
internal fun sasayakiServiceMediaButtons(
    context: Context,
    speedIcon: Int = CommandButton.ICON_PLAYBACK_SPEED_1_0,
): List<CommandButton> =
    sasayakiServiceMediaButtonSpecs().map { spec ->
        CommandButton.Builder(spec.icon)
            .setDisplayName(context.getString(spec.displayNameResId))
            .setPlayerCommand(spec.playerCommand)
            .setSlots(spec.slot)
            .build()
    } + CommandButton.Builder(speedIcon)
        .setDisplayName(context.getString(R.string.sasayaki_speed))
        .setSessionCommand(sasayakiCycleSpeedCommand)
        .build()

internal fun sasayakiPlaybackReturnActivityFlags(): Int =
    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT

private fun sasayakiPlaybackReturnPendingIntent(context: Context, bookId: String?): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
        .setAction(SasayakiPlaybackReturnAction)
        .addFlags(sasayakiPlaybackReturnActivityFlags())
    bookId?.let { intent.putExtra(SasayakiPlaybackReturnBookIdExtra, it) }
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
