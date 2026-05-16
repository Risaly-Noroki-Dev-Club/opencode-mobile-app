package dev.opencode.mobile.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import dev.opencode.mobile.data.SettingsStore
import dev.opencode.mobile.ui.ServerConnection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(savedConnection) {
        val saved = savedConnection ?: return@LaunchedEffect
        if (saved.serverUrl.isNotBlank()) serverUrl = saved.serverUrl
        if (saved.token.isNotBlank()) token = saved.token
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
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
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.connect_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                shape = RoundedCornerShape(28.dp),
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
                        shape = RoundedCornerShape(18.dp),
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
        }
    }
}

@Composable
private fun ConnectionResultCard(result: ConnectionResult, connectionFailed: String) {
    val containerColor = when (result) {
        is ConnectionResult.Success -> MaterialTheme.colorScheme.primaryContainer
        is ConnectionResult.Failure -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (result) {
        is ConnectionResult.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        is ConnectionResult.Failure -> MaterialTheme.colorScheme.onErrorContainer
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
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
