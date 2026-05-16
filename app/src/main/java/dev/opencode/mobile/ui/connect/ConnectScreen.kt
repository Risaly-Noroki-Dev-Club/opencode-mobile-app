package dev.opencode.mobile.ui.connect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.R
import dev.opencode.mobile.data.AgentClient
import dev.opencode.mobile.data.ConnectionResult
import dev.opencode.mobile.data.ServerHistoryEntry
import dev.opencode.mobile.data.SettingsStore
import dev.opencode.mobile.data.ThemeMode
import dev.opencode.mobile.ui.ServerConnection
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(onConnected: (ServerConnection) -> Unit) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val savedConnection by settingsStore.connection.collectAsState(initial = null)
    val serverUrlPlaceholder = stringResource(R.string.server_url_placeholder)
    val connectionFailed = stringResource(R.string.connection_failed)
    var serverUrl by remember { mutableStateOf(serverUrlPlaceholder) }
    var token by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ConnectionResult?>(null) }
    val agentClient = remember { AgentClient() }
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf<List<ServerHistoryEntry>>(emptyList()) }
    val themeMode by settingsStore.themeMode.collectAsState(initial = ThemeMode.System)

    LaunchedEffect(Unit) { history = settingsStore.loadHistory() }

    LaunchedEffect(savedConnection) {
        val saved = savedConnection ?: return@LaunchedEffect
        if (saved.serverUrl.isNotBlank()) serverUrl = saved.serverUrl
        if (saved.token.isNotBlank()) token = saved.token
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = {
                        val next = when (themeMode) {
                            ThemeMode.System -> ThemeMode.Light
                            ThemeMode.Light -> ThemeMode.Dark
                            ThemeMode.Dark -> ThemeMode.System
                        }
                        scope.launch { settingsStore.saveThemeMode(next) }
                    }) {
                        Text(
                            when (themeMode) {
                                ThemeMode.System -> stringResource(R.string.theme_system)
                                ThemeMode.Light -> stringResource(R.string.theme_light)
                                ThemeMode.Dark -> stringResource(R.string.theme_dark)
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.connect_title),
                style = MaterialTheme.typography.h5,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.connect_description),
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(32.dp))
            Card(
                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.medium,
                elevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.server_url_label)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.token_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Button(
                        onClick = {
                            isConnecting = true
                            result = null
                            scope.launch {
                                val connectionResult = agentClient.checkConnection(serverUrl, token)
                                result = connectionResult
                                if (connectionResult is ConnectionResult.Success) {
                                    val normalizedServerUrl = serverUrl.trim().trimEnd('/')
                                    settingsStore.saveConnection(normalizedServerUrl, token)
                                    onConnected(ServerConnection(normalizedServerUrl, token))
                                }
                                isConnecting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isConnecting,
                    ) {
                        Text(
                            if (isConnecting) {
                                stringResource(R.string.connecting_button)
                            } else {
                                stringResource(R.string.connect_button)
                            },
                        )
                    }
                    if (isConnecting) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    result?.let { connectionResult ->
                        ConnectionResultCard(
                            result = connectionResult,
                            connectionFailed = connectionFailed,
                        )
                    }
                }
            }
            if (history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.server_history_title),
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                history.forEach { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                serverUrl = entry.serverUrl
                                token = entry.token
                            },
                        backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.05f),
                        shape = MaterialTheme.shapes.medium,
                        elevation = 0.dp,
                    ) {
                        Text(
                            text = entry.serverUrl,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.body2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionResultCard(result: ConnectionResult, connectionFailed: String) {
    val containerColor = when (result) {
        is ConnectionResult.Success -> MaterialTheme.colors.primary.copy(alpha = 0.12f)
        is ConnectionResult.Failure -> MaterialTheme.colors.error.copy(alpha = 0.12f)
    }
    val contentColor = when (result) {
        is ConnectionResult.Success -> MaterialTheme.colors.primary
        is ConnectionResult.Failure -> MaterialTheme.colors.error
    }
    val text = when (result) {
        is ConnectionResult.Success -> {
            val upstream = if (result.upstreamHealthy) {
                stringResource(R.string.upstream_ok)
            } else {
                stringResource(R.string.upstream_unavailable, result.upstreamError ?: "unknown")
            }
            stringResource(R.string.connection_success, result.projectCount, result.projectSource ?: "unknown", upstream)
        }
        is ConnectionResult.Failure -> "$connectionFailed: ${result.message}"
    }

    Card(
        backgroundColor = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.medium,
        elevation = 1.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.body2,
        )
    }
}
