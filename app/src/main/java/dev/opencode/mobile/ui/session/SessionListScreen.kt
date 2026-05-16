package dev.opencode.mobile.ui.session

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.R
import dev.opencode.mobile.data.AgentClient
import dev.opencode.mobile.data.AgentProject
import dev.opencode.mobile.data.AgentSession
import dev.opencode.mobile.data.ChatCacheStore
import dev.opencode.mobile.ui.ActiveSession
import dev.opencode.mobile.ui.ServerConnection
import dev.opencode.mobile.ui.theme.AdventureBackground
import dev.opencode.mobile.ui.theme.AdventureCard
import dev.opencode.mobile.ui.theme.AdventureFilledButton
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
fun SessionListScreen(
    connection: ServerConnection,
    project: AgentProject,
    onBack: () -> Unit,
    onSession: (ActiveSession) -> Unit,
) {
    val agentClient = remember { AgentClient() }
    val context = LocalContext.current
    val cacheStore = remember(context) { ChatCacheStore(context) }
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<AgentSession>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var recentSessionId by remember { mutableStateOf("") }

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

    LaunchedEffect(connection, project.id) {
        recentSessionId = cacheStore.loadRecentSession(project.id)
        refresh()
    }

    val visibleSessions = sessions.filter { session ->
        val value = query.trim()
        value.isBlank() || session.title.contains(value, ignoreCase = true) ||
            session.directory.contains(value, ignoreCase = true) || session.id.contains(value, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            AdventureTopAppBar(
                title = {
                    Column {
                        Text(project.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            project.worktree,
                            style = MaterialTheme.typography.overline,
                            color = MaterialTheme.colors.onPrimary.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = { AdventureTextButton(onClick = onBack) { Text(stringResource(R.string.back_button)) } },
                actions = { AdventureTextButton(onClick = ::refresh, enabled = !loading) { Text(stringResource(R.string.refresh_button)) } },
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
                        title = project.name,
                        subtitle = project.worktree,
                        meta = "${visibleSessions.size}/${sessions.size} sessions",
                    ) {
                        AdventureFilledButton(
                            onClick = ::createSession,
                            enabled = !creating,
                            backgroundColor = MaterialTheme.adventure.accent,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (creating) stringResource(R.string.creating_session_button) else stringResource(R.string.new_session_button))
                        }
                    }
                }
                item {
                    AdventureOutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.session_search_label)) },
                        singleLine = true,
                    )
                }
                if (loading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                error?.let { item { ErrorCard(it) } }
                if (!loading && visibleSessions.isEmpty() && error == null) item { EmptyCard(stringResource(R.string.sessions_empty)) }
                item { AdventureSectionLabel("Sessions") }
                items(visibleSessions) { session ->
                    SessionCard(
                        session = session,
                        isRecent = session.id == recentSessionId,
                        onDelete = {
                            scope.launch {
                                runCatching { agentClient.deleteSession(connection.serverUrl, connection.token, session.id, session.directory) }
                                    .onSuccess {
                                        cacheStore.clearSession(session.id)
                                        refresh()
                                    }
                                    .onFailure { error = it.message ?: "Failed to delete session" }
                            }
                        },
                    ) {
                        scope.launch { cacheStore.saveRecentSession(project.id, session.id) }
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
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun SessionCard(session: AgentSession, isRecent: Boolean, onDelete: () -> Unit, onClick: () -> Unit) {
    AdventureCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        backgroundColor = if (isRecent) MaterialTheme.adventure.infoContainer else MaterialTheme.adventure.cardContainer,
        elevation = if (isRecent) 3.dp else 2.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = session.title.ifBlank { stringResource(R.string.untitled_session) },
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isRecent) {
                    AdventurePill(
                        text = stringResource(R.string.recent_session_badge),
                        backgroundColor = MaterialTheme.adventure.accent.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.adventure.accent,
                    )
                }
            }
            Text(
                text = session.directory,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.adventure.textMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            AdventureMetaRow {
                AdventurePill(text = formatTime(session.lastActive), backgroundColor = MaterialTheme.adventure.surface2)
                AdventureTextButton(onClick = onDelete) { Text(stringResource(R.string.delete_button)) }
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
            Text(text = "Create a new session or refresh this workspace.", color = MaterialTheme.adventure.textMedium)
        }
    }
}

private fun formatTime(value: Long): String {
    if (value <= 0L) return "No recent activity"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
}
