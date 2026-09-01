package moe.antimony.hoshi.features.kosync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.antimony.hoshi.LocalHoshiUiDependencies
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.settings.SettingsDetailScaffold
import moe.antimony.hoshi.features.settings.collectAsLoadedSettings
import moe.antimony.hoshi.ui.hoshiOutlinedTextFieldColors

@Composable
fun KosyncSettingsView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContainer = LocalHoshiUiDependencies.current
    val repository = appContainer.kosyncSettingsRepository
    val manager = appContainer.kosyncManager
    val scope = rememberCoroutineScope()
    val settings = repository.settings.collectAsLoadedSettings() ?: return
    var editingAccount by remember { mutableStateOf(false) }
    var serverInput by remember { mutableStateOf("") }
    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var connectionMessage by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    val connectedLabel = stringResource(R.string.sync_status_connected)
    val failedFormat = stringResource(R.string.kosync_login_failed_format)

    fun save(next: KosyncSettings) {
        scope.launch { repository.update { next } }
    }

    fun connect() {
        isConnecting = true
        scope.launch {
            connectionMessage = runCatching { manager.testConnection() }
                .fold(onSuccess = { connectedLabel }, onFailure = { failedFormat.format(it.message ?: it::class.java.simpleName) })
            isConnecting = false
        }
    }

    if (editingAccount) {
        AlertDialog(
            onDismissRequest = { editingAccount = false },
            title = { Text(stringResource(R.string.kosync_section_account)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = serverInput,
                        onValueChange = { serverInput = it },
                        label = { Text(stringResource(R.string.kosync_server)) },
                        placeholder = { Text("http://100.98.70.32:7200") },
                        singleLine = true,
                        colors = hoshiOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text(stringResource(R.string.kosync_username)) },
                        singleLine = true,
                        colors = hoshiOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.kosync_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
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
                            if (passwordInput.isNotEmpty()) {
                                repository.saveLogin(serverInput, usernameInput, passwordInput)
                            } else {
                                repository.update { it.copy(serverUrl = serverInput.trim(), username = usernameInput.trim()) }
                            }
                            passwordInput = ""
                            connect()
                        }
                    },
                ) {
                    Text(stringResource(R.string.kosync_login))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingAccount = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.kosync_title),
        onClose = onClose,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) {
            item {
                KosyncCard {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        headlineContent = { Text(stringResource(R.string.action_enable)) },
                        supportingContent = { Text(stringResource(R.string.kosync_description)) },
                        trailingContent = {
                            Switch(
                                checked = settings.enabled,
                                onCheckedChange = { save(settings.copy(enabled = it)) },
                            )
                        },
                    )
                }
            }
            if (settings.enabled) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    KosyncCard {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            headlineContent = { Text(stringResource(R.string.kosync_section_account)) },
                            supportingContent = {
                                val noneLabel = stringResource(R.string.none)
                                Column {
                                    Text(settings.serverUrl.ifBlank { noneLabel })
                                    Text(settings.username.ifBlank { noneLabel })
                                }
                            },
                            trailingContent = {
                                TextButton(
                                    onClick = {
                                        serverInput = settings.serverUrl
                                        usernameInput = settings.username
                                        passwordInput = ""
                                        editingAccount = true
                                    },
                                ) {
                                    Text(stringResource(R.string.action_edit))
                                }
                            },
                        )
                        HorizontalDivider()
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            headlineContent = { Text(stringResource(R.string.anki_connect_connection)) },
                            supportingContent = {
                                Text(connectionMessage ?: stringResource(R.string.sync_status_not_connected))
                            },
                            trailingContent = {
                                TextButton(
                                    onClick = ::connect,
                                    enabled = !isConnecting && settings.isConfigured && repository.hasUserKey(),
                                ) {
                                    Text(
                                        if (isConnecting) {
                                            stringResource(R.string.anki_connect_connecting)
                                        } else {
                                            stringResource(R.string.action_connect)
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    KosyncCard {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            headlineContent = { Text(stringResource(R.string.sync_auto_sync)) },
                            supportingContent = { Text(stringResource(R.string.kosync_auto_sync_description)) },
                            trailingContent = {
                                Switch(
                                    checked = settings.autoSyncEnabled,
                                    onCheckedChange = { save(settings.copy(autoSyncEnabled = it)) },
                                )
                            },
                        )
                        HorizontalDivider()
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            headlineContent = { Text(stringResource(R.string.kosync_push)) },
                            supportingContent = { Text(stringResource(R.string.kosync_push_description)) },
                            trailingContent = {
                                Switch(
                                    checked = settings.pushEnabled,
                                    onCheckedChange = { save(settings.copy(pushEnabled = it)) },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KosyncCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}
