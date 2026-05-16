package dev.opencode.mobile.ui.session

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.R
import dev.opencode.mobile.data.AgentClient
import kotlinx.coroutines.launch

data class ChatConnection(
    val serverUrl: String,
    val token: String,
)

private data class ChatMessage(
    val role: ChatRole,
    val text: String,
)

private enum class ChatRole { User, Assistant, System }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(connection: ChatConnection) {
    val agentClient = remember { AgentClient() }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var sessionId by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(connection) {
        busy = true
        runCatching { agentClient.createSession(connection.serverUrl, connection.token) }
            .onSuccess {
                sessionId = it
                messages += ChatMessage(ChatRole.System, "${connection.serverUrl}\n${it}")
            }
            .onFailure {
                messages += ChatMessage(ChatRole.System, it.message ?: "Failed to create session")
            }
        busy = false
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.session_title)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(messages) { message ->
                    MessageCard(message)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.message_input_label)) },
                    minLines = 1,
                    maxLines = 4,
                )
                Button(
                    enabled = !busy && sessionId != null && input.isNotBlank(),
                    onClick = {
                        val text = input.trim()
                        val activeSession = sessionId ?: return@Button
                        input = ""
                        messages += ChatMessage(ChatRole.User, text)
                        busy = true
                        scope.launch {
                            runCatching { agentClient.sendMessage(connection.serverUrl, connection.token, activeSession, text) }
                                .onSuccess { messages += ChatMessage(ChatRole.Assistant, it) }
                                .onFailure { messages += ChatMessage(ChatRole.System, it.message ?: "Send failed") }
                            busy = false
                        }
                    },
                ) {
                    Text(if (busy) stringResource(R.string.sending_button) else stringResource(R.string.send_button))
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: ChatMessage) {
    val colors = when (message.role) {
        ChatRole.User -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ChatRole.Assistant -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ChatRole.System -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    }
    val title = when (message.role) {
        ChatRole.User -> stringResource(R.string.role_user)
        ChatRole.Assistant -> stringResource(R.string.role_assistant)
        ChatRole.System -> stringResource(R.string.role_system)
    }
    Card(
        colors = colors,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = message.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
