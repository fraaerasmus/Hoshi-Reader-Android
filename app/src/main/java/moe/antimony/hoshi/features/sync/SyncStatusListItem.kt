package moe.antimony.hoshi.features.sync

import android.text.format.DateUtils
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import moe.antimony.hoshi.R

/** "Last sync" / "Last error" for one backend, shared by the ッツ and KOReader settings screens. */
@Composable
internal fun SyncStatusListItem(status: SyncStatus) {
    val error = status.lastError
    val text = when {
        error != null && status.lastErrorAtMillis != null ->
            stringResource(R.string.sync_last_error_format, relativeMillis(status.lastErrorAtMillis), error)
        status.lastSyncAtMillis != null ->
            stringResource(R.string.sync_last_sync_format, relativeMillis(status.lastSyncAtMillis))
        else -> return
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(text, style = MaterialTheme.typography.bodySmall) },
    )
}

private fun relativeMillis(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
