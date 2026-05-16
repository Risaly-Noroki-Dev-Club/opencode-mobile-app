package dev.opencode.mobile.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.R
import dev.opencode.mobile.data.AgentClient
import dev.opencode.mobile.data.AgentProject
import dev.opencode.mobile.data.AgentSession
import dev.opencode.mobile.ui.ActiveSession
import dev.opencode.mobile.ui.ServerConnection
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    connection: ServerConnection,
    project: AgentProject,
    onBack: () -> Unit,
    onSession: (ActiveSession) -> Unit,
) {
    val agentClient = remember { AgentClient() }
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<AgentSession>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        loading = true
        error = null
        scope.launch {
            runCatching {
                sessions = agentClient.listSessions(
                    serverUrl = connection.serverUrl,
                    token = connection.token,
                    projectId = project.id,
                    directory = project.worktree,
                )
            }.onFailure { error = it.message ?: "Failed to load sessions" }
            loading = false
        }
    }

    fun createSession() {
        creating = true
        error = null
        scope.launch {
            runCatching { agentClient.createSession(connection.serverUrl, connection.token, project.worktree) }
                .onSuccess { id ->
                    onSession(
                        ActiveSession(
                            id = id,
                            projectId = project.id,
                            directory = project.worktree,
                            title = null,
                        ),
                    )
                }
                .onFailure { error = it.message ?: "Failed to create session" }
            creating = false
        }
    }

    LaunchedEffect(connection, project.id) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(project.name)
                        Text(project.worktree, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back_button)) } },
                actions = { TextButton(onClick = ::refresh, enabled = !loading) { Text(stringResource(R.string.refresh_button)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = ::createSession, enabled = !creating, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Text(if (creating) stringResource(R.string.creating_session_button) else stringResource(R.string.new_session_button))
            }
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            error?.let { ErrorCard(it) }
            if (!loading && sessions.isEmpty() && error == null) EmptyCard(stringResource(R.string.sessions_empty))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sessions) { session ->
                    SessionCard(session = session) {
                        onSession(
                            ActiveSession(
                                id = session.id,
                                projectId = session.projectId,
                                directory = session.directory,
                                title = session.title,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: AgentSession, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = session.title.ifBlank { stringResource(R.string.untitled_session) }, style = MaterialTheme.typography.titleMedium)
                Text(text = formatTime(session.lastActive), style = MaterialTheme.typography.labelMedium)
            }
            Text(text = session.directory, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(20.dp)) {
        Text(text = message, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(20.dp)) {
        Text(text = message, modifier = Modifier.padding(16.dp))
    }
}

private fun formatTime(value: Long): String {
    if (value <= 0L) return "-"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
}
