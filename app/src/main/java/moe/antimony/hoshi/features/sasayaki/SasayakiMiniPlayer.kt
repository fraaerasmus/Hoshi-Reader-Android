package moe.antimony.hoshi.features.sasayaki

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.LocalHoshiUiDependencies
import moe.antimony.hoshi.R

/**
 * Persistent "now playing" bar shown on the top-level tab screens while Sasayaki audio is engaged.
 * It works headless (playback outlives the reader via the service runtime), and tapping it reopens
 * the playing book. Speed control lives in the playback sheet, not here.
 */
@Composable
internal fun SasayakiMiniPlayer(
    onOpenReader: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val runtime = LocalHoshiUiDependencies.current.sasayakiPlaybackServiceRuntime
    val nowPlaying by runtime.nowPlaying.collectAsStateWithLifecycle()
    val playing = nowPlaying ?: return
    val snapshot by runtime.snapshot.collectAsStateWithLifecycle()

    val cover by produceState<Bitmap?>(initialValue = null, playing.coverFile) {
        value = withContext(Dispatchers.IO) {
            playing.coverFile?.let { decodeSampledSasayakiCoverBitmap(it) }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(modifier = Modifier.clickable { onOpenReader(playing.bookId) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = cover
                    if (art != null) {
                        Image(
                            bitmap = art.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp),
                        )
                    } else {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = playing.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { runtime.skipBackward() }) {
                    Icon(Icons.Rounded.FastRewind, contentDescription = stringResource(R.string.sasayaki_previous_cue))
                }
                IconButton(onClick = { runtime.togglePlayback() }) {
                    Icon(
                        imageVector = if (snapshot.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.sasayaki_title),
                    )
                }
                IconButton(onClick = { runtime.skipForward() }) {
                    Icon(Icons.Rounded.FastForward, contentDescription = stringResource(R.string.sasayaki_next_cue))
                }
            }
            val durationMs = snapshot.durationMs
            if (durationMs > 0L) {
                LinearProgressIndicator(
                    progress = { (snapshot.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(2.dp),
                )
            }
        }
    }
}
