package dev.opencode.mobile.ui.project

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
import dev.opencode.mobile.data.AgentHealth
import dev.opencode.mobile.data.AgentProject
import dev.opencode.mobile.ui.ServerConnection
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    connection: ServerConnection,
    onProject: (AgentProject) -> Unit,
    onDisconnect: () -> Unit,
) {
    val agentClient = remember { AgentClient() }
    val scope = rememberCoroutineScope()
    var health by remember { mutableStateOf<AgentHealth?>(null) }
    var projects by remember { mutableStateOf<List<AgentProject>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        loading = true
        error = null
        scope.launch {
            runCatching {
                health = agentClient.getHealth(connection.serverUrl)
                projects = agentClient.listProjects(connection.serverUrl, connection.token)
            }.onFailure { error = it.message ?: "Failed to load projects" }
            loading = false
        }
    }

    LaunchedEffect(connection) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.projects_title)) },
                actions = {
                    TextButton(onClick = ::refresh, enabled = !loading) { Text(stringResource(R.string.refresh_button)) }
                    TextButton(onClick = onDisconnect) { Text(stringResource(R.string.disconnect_button)) }
                },
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
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            health?.let { HealthCard(it) }
            error?.let { ErrorCard(it) }
            if (!loading && projects.isEmpty() && error == null) {
                EmptyCard(stringResource(R.string.projects_empty))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(projects) { project ->
                    ProjectCard(project = project, onClick = { onProject(project) })
                }
            }
        }
    }
}

@Composable
private fun HealthCard(health: AgentHealth) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(R.string.agent_status_title), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.agent_status_projects, health.projectCount, health.projectSource ?: "unknown"))
            Text(
                text = if (health.opencodeHealthy) {
                    stringResource(R.string.upstream_ok)
                } else {
                    stringResource(R.string.upstream_unavailable, health.opencodeError ?: "unknown")
                },
            )
            health.opencodeVersion?.let { Text(text = stringResource(R.string.opencode_version, it)) }
        }
    }
}

@Composable
private fun ProjectCard(project: AgentProject, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = project.name, style = MaterialTheme.typography.titleLarge)
                Text(text = project.vcs, style = MaterialTheme.typography.labelMedium)
            }
            Text(text = project.worktree, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = formatTime(project.lastActive), style = MaterialTheme.typography.labelMedium)
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
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = message)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun formatTime(value: Long): String {
    if (value <= 0L) return "-"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
}
