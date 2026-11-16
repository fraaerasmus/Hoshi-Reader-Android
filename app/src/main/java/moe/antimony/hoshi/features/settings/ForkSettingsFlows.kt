package moe.antimony.hoshi.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow

/** Fork screens read settings as nullable-until-loaded; upstream screens use [collectAsSettingsLoadState]. */
@Composable
internal fun <T> Flow<T>.collectAsLoadedSettings(): T? {
    val value by collectAsStateWithLifecycle(initialValue = null)
    return value
}
