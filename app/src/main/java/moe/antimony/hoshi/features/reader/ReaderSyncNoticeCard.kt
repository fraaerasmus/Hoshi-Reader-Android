package moe.antimony.hoshi.features.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import moe.antimony.hoshi.R
import moe.antimony.hoshi.navigation.ReaderSyncNotice

/**
 * Sync feedback on the reader's HUD card: "Synced from KOReader · 14%" with Undo, or the failure in error
 * colour. Fades on its own, on tap, or when the reader is used again — Undo stays reachable in the sync sheet.
 */
@Composable
internal fun ReaderSyncNoticeCard(
    notice: ReaderSyncNotice?,
    bottomPadding: Dp,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        delay(if (notice.undo != null) SYNC_NOTICE_UNDO_MS else SYNC_NOTICE_MS)
        onDismiss()
    }
    ReaderSasayakiHudOverlay(state = notice, bottomPadding = bottomPadding, modifier = modifier) { current ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(max = 480.dp)
                .clickable(onClick = onDismiss)
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            val tint = if (current.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            Icon(
                imageVector = if (current.isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Sync,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = current.message,
                style = MaterialTheme.typography.bodyMedium,
                color = tint,
                modifier = Modifier.weight(1f),
            )
            if (current.undo != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onUndo) {
                    Text(stringResource(R.string.action_undo))
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

private const val SYNC_NOTICE_MS = 5_000L
private const val SYNC_NOTICE_UNDO_MS = 8_000L
