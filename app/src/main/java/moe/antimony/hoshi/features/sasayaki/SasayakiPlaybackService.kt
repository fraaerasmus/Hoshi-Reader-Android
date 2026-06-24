package moe.antimony.hoshi.features.sasayaki

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import moe.antimony.hoshi.R

/**
 * Process-scoped pointer to the single active media3 session. [SasayakiMediaSession] sets this on
 * creation and clears it on release; [SasayakiPlaybackService] reads it. Single-process app with one
 * active book, so a simple holder is enough — no IPC, no binding.
 */
internal object SasayakiSessionRegistry {
    @Volatile
    var session: MediaSession? = null
}

/**
 * Thin foreground host for Sasayaki playback. It owns no player: it hosts the holder-owned
 * [MediaSession] so audio keeps playing — with OS foreground priority and the framework media
 * notification — even after the reader Composition is gone. Started on the play transition (via the
 * media-session handle) and stopped on teardown.
 */
@OptIn(UnstableApi::class)
class SasayakiPlaybackService : MediaSessionService() {
    private var addedSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(ChannelId)
                .setChannelName(R.string.sasayaki_playback)
                .setNotificationId(NotificationId)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_hoshi) },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        syncSession()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        syncSession()
        return SasayakiSessionRegistry.session
    }

    // Keep media3's notification manager observing whatever session is currently active.
    private fun syncSession() {
        val session = SasayakiSessionRegistry.session
        if (session === addedSession) return
        addedSession?.let { runCatching { removeSession(it) } }
        session?.let { addSession(it) }
        addedSession = session
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = SasayakiSessionRegistry.session?.player
        val playing = player != null && player.playWhenReady && player.playbackState != Player.STATE_IDLE
        if (!playing) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        addedSession?.let { runCatching { removeSession(it) } }
        addedSession = null
        super.onDestroy()
    }

    companion object {
        private const val ChannelId = "sasayaki_playback"
        private const val NotificationId = 2407
    }
}
