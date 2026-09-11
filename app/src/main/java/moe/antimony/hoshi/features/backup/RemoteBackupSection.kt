package moe.antimony.hoshi.features.backup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import moe.antimony.hoshi.LocalHoshiUiDependencies
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.settings.collectAsLoadedSettings
import moe.antimony.hoshi.features.sync.SyncBackend
import moe.antimony.hoshi.features.sync.SyncStatus
import moe.antimony.hoshi.features.sync.SyncStatusListItem
import moe.antimony.hoshi.features.sync.relativeTimeText
import moe.antimony.hoshi.features.sync.toSyncErrorText
import moe.antimony.hoshi.ui.hoshiOutlinedTextFieldColors
import moe.antimony.hoshi.ui.resolve

/** The "Server" block of Advanced › Backup: WebDAV account, automatic backup toggles, Back up now, Restore. */
@Composable
internal fun RemoteBackupSection(
    enabled: Boolean,
    onOperation: (BackupOperation?) -> Unit,
    onMessage: (String) -> Unit,
) {
    val appContainer = LocalHoshiUiDependencies.current
    val repository = appContainer.remoteBackupSettingsRepository
    val manager = appContainer.remoteBackupManager
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val settings = repository.settings.collectAsLoadedSettings() ?: return
    val status by appContainer.syncStatusRepository.status(SyncBackend.RemoteBackup)
        .collectAsStateWithLifecycle(initialValue = SyncStatus())
    var editingAccount by remember { mutableStateOf(false) }
    var restoreIndex by remember { mutableStateOf<RemoteBackupIndex?>(null) }
    val uploadedLabel = stringResource(R.string.backup_server_settings_uploaded)
    val unchangedLabel = stringResource(R.string.backup_server_settings_unchanged)
    val resultFormat = stringResource(R.string.backup_server_result_format)
    val failedFormat = stringResource(R.string.backup_server_failed_format)
    val settingsRestored = stringResource(R.string.backup_settings_restored)

    fun save(next: RemoteBackupSettings) {
        scope.launch { repository.update { next } }
    }

    fun run(operation: BackupOperation, block: suspend () -> String) {
        onOperation(operation)
        scope.launch {
            val message = runCatching { block() }
                .getOrElse { failedFormat.format(it.toSyncErrorText().resolve(resources)) }
            onOperation(null)
            onMessage(message)
        }
    }

    Text(
        text = stringResource(R.string.backup_server),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    BackupGroupCard {
        ListItem(
            colors = transparent(),
            headlineContent = { Text(stringResource(R.string.action_enable)) },
            supportingContent = { Text(stringResource(R.string.backup_server_description)) },
            trailingContent = { Switch(checked = settings.enabled, onCheckedChange = { save(settings.copy(enabled = it)) }) },
        )
        if (settings.enabled) {
            HorizontalDivider()
            val noneLabel = stringResource(R.string.none)
            ListItem(
                colors = transparent(),
                headlineContent = { Text(stringResource(R.string.backup_server_address)) },
                supportingContent = {
                    Column {
                        Text(settings.serverUrl.ifBlank { noneLabel })
                        Text(listOf(settings.username, settings.deviceName).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { noneLabel })
                    }
                },
                trailingContent = {
                    TextButton(onClick = { editingAccount = true }) { Text(stringResource(R.string.action_edit)) }
                },
            )
            HorizontalDivider()
            ListItem(
                colors = transparent(),
                headlineContent = { Text(stringResource(R.string.backup_server_auto)) },
                supportingContent = { Text(stringResource(R.string.backup_server_auto_help)) },
                trailingContent = { Switch(checked = settings.autoBackup, onCheckedChange = { save(settings.copy(autoBackup = it)) }) },
            )
            HorizontalDivider()
            ListItem(
                colors = transparent(),
                headlineContent = { Text(stringResource(R.string.backup_server_book_state)) },
                supportingContent = { Text(stringResource(R.string.backup_server_book_state_help)) },
                trailingContent = { Switch(checked = settings.backupBookState, onCheckedChange = { save(settings.copy(backupBookState = it)) }) },
            )
            HorizontalDivider()
            ListItem(
                colors = transparent(),
                leadingContent = { Icon(Icons.Rounded.Upload, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.backup_server_backup_now)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled && settings.isConfigured) {
                        run(BackupOperation.Uploading) {
                            val result = manager.backupAll(force = true)
                            resultFormat.format(if (result.settingsUploaded) uploadedLabel else unchangedLabel, result.booksUploaded)
                        }
                    },
            )
            HorizontalDivider()
            ListItem(
                colors = transparent(),
                leadingContent = { Icon(Icons.Rounded.Download, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.backup_server_restore)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled && settings.isConfigured) {
                        onOperation(BackupOperation.Downloading)
                        scope.launch {
                            val index = runCatching { manager.listIndex() }
                            onOperation(null)
                            index.fold(
                                onSuccess = { restoreIndex = it },
                                onFailure = { onMessage(failedFormat.format(it.toSyncErrorText().resolve(resources))) },
                            )
                        }
                    },
            )
            SyncStatusListItem(status)
        }
    }
    Text(
        text = stringResource(R.string.backup_settings_credentials_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )

    if (editingAccount) {
        var serverInput by remember { mutableStateOf(settings.serverUrl) }
        var usernameInput by remember { mutableStateOf(settings.username) }
        var passwordInput by remember { mutableStateOf("") }
        var deviceInput by remember { mutableStateOf(settings.deviceName) }
        AlertDialog(
            onDismissRequest = { editingAccount = false },
            title = { Text(stringResource(R.string.backup_server)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = serverInput,
                        onValueChange = { serverInput = it },
                        label = { Text(stringResource(R.string.backup_server_address)) },
                        placeholder = { Text("http://100.98.70.32:8090") },
                        singleLine = true,
                        colors = hoshiOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text(stringResource(R.string.kosync_username)) },
                        singleLine = true,
                        colors = hoshiOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.kosync_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = hoshiOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deviceInput,
                        onValueChange = { deviceInput = it },
                        label = { Text(stringResource(R.string.backup_server_device_name)) },
                        singleLine = true,
                        colors = hoshiOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingAccount = false
                        scope.launch {
                            repository.saveLogin(serverInput, usernameInput, passwordInput.takeIf { it.isNotEmpty() }, deviceInput)
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingAccount = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    restoreIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { restoreIndex = null },
            title = { Text(stringResource(R.string.backup_server_restore_pick)) },
            text = {
                if (index.devices.isEmpty()) {
                    Text(stringResource(R.string.backup_server_index_empty))
                } else {
                    Column {
                        index.devices.sortedByDescending { it.updatedAtMillis }.forEach { device ->
                            ListItem(
                                colors = transparent(),
                                headlineContent = { Text(device.name) },
                                supportingContent = { Text(relativeTimeText(device.updatedAtMillis)) },
                                modifier = Modifier.clickable {
                                    restoreIndex = null
                                    run(BackupOperation.Downloading) {
                                        manager.restoreSettings(device.name)
                                        appContainer.dictionaryRepository.rebuildLookupQuery()
                                        settingsRestored
                                    }
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { restoreIndex = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun transparent() = ListItemDefaults.colors(containerColor = Color.Transparent)
