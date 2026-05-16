package dev.opencode.mobile.ui.project

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
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.R
import dev.opencode.mobile.data.AgentClient
import dev.opencode.mobile.data.AgentHealth
import dev.opencode.mobile.data.AgentProject
import dev.opencode.mobile.ui.ServerConnection
import dev.opencode.mobile.ui.theme.AdventureBackground
import dev.opencode.mobile.ui.theme.AdventureCard
import dev.opencode.mobile.ui.theme.AdventureHeroCard
import dev.opencode.mobile.ui.theme.AdventureMetaRow
import dev.opencode.mobile.ui.theme.AdventureOutlinedTextField
import dev.opencode.mobile.ui.theme.AdventurePill
import dev.opencode.mobile.ui.theme.AdventureSectionLabel
import dev.opencode.mobile.ui.theme.AdventureTextButton
import dev.opencode.mobile.ui.theme.AdventureTopAppBar
import dev.opencode.mobile.ui.theme.adventure
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

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
    var query by remember { mutableStateOf("") }

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

    val visibleProjects = projects.filter { project ->
        val value = query.trim()
        value.isBlank() || project.name.contains(value, ignoreCase = true) ||
            project.worktree.contains(value, ignoreCase = true) || project.vcs.contains(value, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            AdventureTopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.projects_title), fontWeight = FontWeight.SemiBold)
                        Text(
                            text = connection.serverUrl,
                            style = MaterialTheme.typography.overline,
                            color = MaterialTheme.adventure.textMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    AdventureTextButton(onClick = ::refresh, enabled = !loading) { Text(stringResource(R.string.refresh_button)) }
                    AdventureTextButton(onClick = onDisconnect) { Text(stringResource(R.string.disconnect_button)) }
                },
            )
        },
        backgroundColor = MaterialTheme.colors.background,
    ) { padding ->
        AdventureBackground {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    AdventureHeroCard(
                        title = "OpenCode Remote",
                        subtitle = "Choose a workspace and keep a tiny command center in your pocket.",
                        meta = "${visibleProjects.size}/${projects.size} workspaces",
                    )
                }
                if (loading) {
                    item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                }
                item {
                    health?.let { HealthCard(it) }
                }
                item {
                    AdventureOutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.project_search_label)) },
                        singleLine = true,
                    )
                }
                error?.let { item { ErrorCard(it) } }
                if (!loading && visibleProjects.isEmpty() && error == null) {
                    item { EmptyCard(stringResource(R.string.projects_empty)) }
                }
                item { AdventureSectionLabel("Workspaces") }
                items(visibleProjects) { project ->
                    ProjectCard(project = project, onClick = { onProject(project) })
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun HealthCard(health: AgentHealth) {
    AdventureCard(
        backgroundColor = MaterialTheme.adventure.surface2,
        elevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(R.string.agent_status_title), style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
                AdventurePill(
                    text = if (health.opencodeHealthy) stringResource(R.string.upstream_ok) else "Offline",
                    backgroundColor = if (health.opencodeHealthy) MaterialTheme.adventure.successContainer else MaterialTheme.colors.error.copy(alpha = 0.14f),
                    contentColor = if (health.opencodeHealthy) MaterialTheme.adventure.success else MaterialTheme.colors.error,
                )
            }
            Text(
                text = stringResource(R.string.agent_status_projects, health.projectCount, health.projectSource ?: "unknown"),
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.adventure.textMedium,
            )
            if (!health.opencodeHealthy) {
                Text(
                    text = stringResource(R.string.upstream_unavailable, health.opencodeError ?: "unknown"),
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.error,
                )
            }
            health.opencodeVersion?.let {
                Text(
                    text = stringResource(R.string.opencode_version, it),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.adventure.textMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun ProjectCard(project: AgentProject, onClick: () -> Unit) {
    AdventureCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        backgroundColor = MaterialTheme.adventure.cardContainer,
        elevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AdventurePill(text = project.vcs.ifBlank { "local" })
            }
            Text(
                text = project.worktree,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.adventure.textMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            AdventureMetaRow {
                AdventurePill(
                    text = formatTime(project.lastActive),
                    backgroundColor = MaterialTheme.adventure.surface2,
                )
                AdventurePill(
                    text = "Open",
                    backgroundColor = MaterialTheme.adventure.accent.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.adventure.accent,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    AdventureCard(backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.12f), elevation = 0.dp) {
        Text(text = message, modifier = Modifier.padding(16.dp), color = MaterialTheme.colors.error)
    }
}

@Composable
private fun EmptyCard(message: String) {
    AdventureCard(backgroundColor = MaterialTheme.adventure.surface2, elevation = 0.dp) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = message, style = MaterialTheme.typography.subtitle2)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Try refreshing or changing the filter.", color = MaterialTheme.adventure.textMedium)
        }
    }
}

private fun formatTime(value: Long): String {
    if (value <= 0L) return "No recent activity"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
}
