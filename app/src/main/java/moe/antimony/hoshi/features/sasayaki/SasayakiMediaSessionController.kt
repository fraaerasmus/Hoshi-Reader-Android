package moe.antimony.hoshi.features.sasayaki

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import java.io.File

interface SasayakiMediaSessionHandle {
    fun activate()

    fun update(
        isPlaying: Boolean,
        currentTimeMs: Long,
        durationMs: Long,
        rate: Float,
    )

    fun release()
}

class AndroidSasayakiMediaSessionHandle(
    context: Context,
    player: Player,
    title: String,
    artworkFile: File?,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onSkipToNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
) : SasayakiMediaSessionHandle {
    private val appContext = context.applicationContext
    private val session = SasayakiMediaSession(
        context = appContext,
        player = player,
        title = title,
        artwork = SasayakiMediaSession.loadCoverArt(artworkFile),
        onPlay = onPlay,
        onPause = onPause,
        onSkipToPrevious = onSkipToPrevious,
        onSkipToNext = onSkipToNext,
        onSeekTo = onSeekTo,
        onCycleSpeed = onCycleSpeed,
    )

    override fun activate() {
        // Start (or re-foreground) the playback service; it hosts the session created above so
        // audio + the media notification survive the reader Composition going away.
        ContextCompat.startForegroundService(appContext, serviceIntent())
    }

    override fun update(
        isPlaying: Boolean,
        currentTimeMs: Long,
        durationMs: Long,
        rate: Float,
    ) {
        // media3's notification provider observes play/position directly; we only need to refresh
        // the speed-button icon when the rate changes.
        session.onRateChanged(rate)
    }

    override fun release() {
        session.release()
        appContext.stopService(serviceIntent())
    }

    private fun serviceIntent(): Intent =
        Intent(appContext, SasayakiPlaybackService::class.java)
}
