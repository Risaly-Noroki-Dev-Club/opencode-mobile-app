package dev.opencode.mobile.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.opencode.mobile.data.DiffFile
import dev.opencode.mobile.data.ModelOption
import dev.opencode.mobile.data.OpenCodeMessage
import dev.opencode.mobile.data.OpenCodeCommand
import dev.opencode.mobile.data.OpenCodeStreamEvent
import dev.opencode.mobile.data.PermissionReply
import dev.opencode.mobile.ui.ActiveSession
import dev.opencode.mobile.ui.ServerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ChatMessage(
    val role: ChatRole,
    val text: String,
)

private enum class ChatRole { User, Assistant, System, Tool }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    connection: ServerConnection,
    activeSession: ActiveSession,
    onBack: () -> Unit,
) {
    val agentClient = remember { AgentClient() }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var sessionId by remember { mutableStateOf<String?>(activeSession.id) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showNavi by remember { mutableStateOf(false) }
    var commands by remember { mutableStateOf<List<OpenCodeCommand>>(emptyList()) }
    var models by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
    var selectedModel by remember { mutableStateOf<ModelOption?>(null) }
    var streamActive by remember { mutableStateOf(false) }
    var showDiff by remember { mutableStateOf(false) }
    var diffFiles by remember { mutableStateOf<List<DiffFile>>(emptyList()) }
    var pendingPermission by remember { mutableStateOf<OpenCodeStreamEvent.Permission?>(null) }

    LaunchedEffect(connection, activeSession.id) {
        busy = true
        sessionId = activeSession.id
        messages.clear()
        messages += ChatMessage(ChatRole.System, "${connection.serverUrl}\n${activeSession.directory.orEmpty()}\n${activeSession.id}")
        runCatching { agentClient.listMessages(connection.serverUrl, connection.token, activeSession.id, activeSession.directory) }
            .onSuccess { history ->
                if (history.isNotEmpty()) {
                    messages.clear()
                    messages += history.map { message -> message.toChatMessage() }
                }
            }
            .onFailure {
                messages += ChatMessage(ChatRole.System, it.message ?: "Failed to load session")
            }
        busy = false

        runCatching { agentClient.listCommands(connection.serverUrl, connection.token) }
            .onSuccess { commands = it }
        runCatching { agentClient.listModels(connection.serverUrl, connection.token) }
            .onSuccess { availableModels ->
                models = availableModels
                selectedModel = availableModels.firstOrNull { it.providerId == "rakuraku" && it.modelId == "gpt-5.5" }
                    ?: availableModels.firstOrNull()
            }
    }

    LaunchedEffect(connection, sessionId) {
        val activeSession = sessionId ?: return@LaunchedEffect
        runCatching {
            streamActive = true
            agentClient.streamEvents(connection.serverUrl, connection.token) { event ->
                if (event.sessionId != activeSession) return@streamEvents
                scope.launch {
                    withContext(Dispatchers.Main) {
                        when (event) {
                            is OpenCodeStreamEvent.TextDelta -> appendAssistantDelta(messages, event.delta)
                            is OpenCodeStreamEvent.TextEnded -> finalizeAssistantText(messages, event.text)
                            is OpenCodeStreamEvent.Tool -> messages += ChatMessage(ChatRole.Tool, event.title)
                            is OpenCodeStreamEvent.Error -> messages += ChatMessage(ChatRole.System, event.message)
                            is OpenCodeStreamEvent.Idle -> busy = false
                            is OpenCodeStreamEvent.Permission -> pendingPermission = event
                        }
                    }
                }
            }
        }.onFailure {
            streamActive = false
            messages += ChatMessage(ChatRole.System, it.message ?: "Event stream failed")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(activeSession.title?.ifBlank { stringResource(R.string.session_title) } ?: stringResource(R.string.session_title))
                        Text(
                            text = selectedModel?.label ?: activeSession.directory ?: stringResource(R.string.model_default),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back_button)) }
                },
                actions = {
                    TextButton(onClick = { showNavi = true }) {
                        Text(stringResource(R.string.navi_title))
                    }
                },
            )
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
                Button(
                    onClick = { showNavi = true },
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(stringResource(R.string.navi_button))
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        if (it == "/") showNavi = true
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.message_input_label)) },
                    minLines = 1,
                    maxLines = 4,
                )
                Button(
                    enabled = !busy && sessionId != null && input.isNotBlank(),
                    onClick = {
                        val text = input.trim()
                        val activeSessionId = sessionId ?: return@Button
                        input = ""
                        messages += ChatMessage(ChatRole.User, text)
                        busy = true
                        scope.launch {
                            runCatching { agentClient.promptAsync(connection.serverUrl, connection.token, activeSessionId, text, selectedModel, activeSession.directory) }
                                .onFailure {
                                    runCatching { agentClient.sendMessage(connection.serverUrl, connection.token, activeSessionId, text, selectedModel, activeSession.directory) }
                                        .onSuccess { messages += ChatMessage(ChatRole.Assistant, it) }
                                        .onFailure { error -> messages += ChatMessage(ChatRole.System, error.message ?: "Send failed") }
                                    busy = false
                                }
                        }
                    },
                ) {
                    Text(
                        when {
                            busy -> stringResource(R.string.sending_button)
                            streamActive -> stringResource(R.string.send_button)
                            else -> stringResource(R.string.send_button)
                        },
                    )
                }
            }
        }
    }

    if (showNavi) {
        NaviSheet(
            commands = commands,
            models = models,
            selectedModel = selectedModel,
            onDismiss = { showNavi = false },
            onTemplate = { template ->
                input = template
                showNavi = false
            },
            onCommand = { command ->
                val activeSessionId = sessionId ?: return@NaviSheet
                showNavi = false
                busy = true
                messages += ChatMessage(ChatRole.User, "/${command.name}")
                scope.launch {
                    runCatching {
                        agentClient.executeCommand(
                            serverUrl = connection.serverUrl,
                            token = connection.token,
                            sessionId = activeSessionId,
                            command = command.name,
                            arguments = "",
                            model = selectedModel,
                            directory = activeSession.directory,
                        )
                    }.onSuccess { messages += ChatMessage(ChatRole.Assistant, it) }
                        .onFailure { messages += ChatMessage(ChatRole.System, it.message ?: "Command failed") }
                    busy = false
                }
            },
            onModel = {
                selectedModel = it
                messages += ChatMessage(ChatRole.System, "${it.providerName} / ${it.modelName}")
                showNavi = false
            },
            onDiff = {
                val activeSessionId = sessionId ?: return@NaviSheet
                showNavi = false
                scope.launch {
                    runCatching { agentClient.getSessionDiff(connection.serverUrl, connection.token, activeSessionId, activeSession.directory) }
                        .onSuccess {
                            diffFiles = it
                            showDiff = true
                        }
                        .onFailure { messages += ChatMessage(ChatRole.System, it.message ?: "Diff failed") }
                }
            },
        )
    }

    if (showDiff) {
        DiffSheet(diffFiles = diffFiles, onDismiss = { showDiff = false })
    }

    pendingPermission?.let { permission ->
        PermissionSheet(
            permission = permission,
            onDismiss = { pendingPermission = null },
            onReply = { reply ->
                scope.launch {
                    runCatching { agentClient.replyPermission(connection.serverUrl, connection.token, permission.requestId, reply) }
                        .onSuccess { messages += ChatMessage(ChatRole.System, "${permission.title}: ${reply.wireValue}") }
                        .onFailure { messages += ChatMessage(ChatRole.System, it.message ?: "Permission reply failed") }
                    pendingPermission = null
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NaviSheet(
    commands: List<OpenCodeCommand>,
    models: List<ModelOption>,
    selectedModel: ModelOption?,
    onDismiss: () -> Unit,
    onTemplate: (String) -> Unit,
    onCommand: (OpenCodeCommand) -> Unit,
    onModel: (ModelOption) -> Unit,
    onDiff: () -> Unit,
) {
    val templates = promptTemplates()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.navi_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.navi_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { NaviSectionTitle(stringResource(R.string.navi_section_templates)) }
            item {
                NaviActionCard(
                    title = stringResource(R.string.navi_diff_title),
                    description = stringResource(R.string.navi_diff_description),
                    onClick = onDiff,
                )
            }
            items(templates) { item ->
                NaviActionCard(title = item.title, description = item.description) { onTemplate(item.prompt) }
            }
            if (commands.isNotEmpty()) {
                item { NaviSectionTitle(stringResource(R.string.navi_section_opencode)) }
                items(commands.take(12)) { command ->
                    NaviActionCard(
                        title = "/${command.name}",
                        description = command.description ?: stringResource(R.string.navi_native_command),
                    ) { onCommand(command) }
                }
            }
            if (models.isNotEmpty()) {
                item { NaviSectionTitle(stringResource(R.string.navi_section_models)) }
                items(models.take(48)) { model ->
                    ElevatedFilterChip(
                        selected = selectedModel?.providerId == model.providerId && selectedModel.modelId == model.modelId,
                        onClick = { onModel(model) },
                        label = { Text(model.label) },
                        modifier = Modifier.widthIn(max = 520.dp),
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiffSheet(diffFiles: List<DiffFile>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(text = stringResource(R.string.diff_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = if (diffFiles.isEmpty()) stringResource(R.string.diff_empty) else stringResource(R.string.diff_count, diffFiles.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(diffFiles) { file ->
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = file.path, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        MarkdownText(text = "```json\n${file.text}\n```")
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionSheet(
    permission: OpenCodeStreamEvent.Permission,
    onDismiss: () -> Unit,
    onReply: (PermissionReply) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineSmall)
            Text(text = permission.title, style = MaterialTheme.typography.titleMedium)
            Text(text = permission.details, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onReply(PermissionReply.Reject) }) { Text(stringResource(R.string.permission_reject)) }
                Button(onClick = { onReply(PermissionReply.Once) }) { Text(stringResource(R.string.permission_once)) }
                Button(onClick = { onReply(PermissionReply.Always) }) { Text(stringResource(R.string.permission_always)) }
            }
        }
    }
}

@Composable
private fun NaviSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun NaviActionCard(title: String, description: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class PromptTemplate(
    val title: String,
    val description: String,
    val prompt: String,
)

@Composable
private fun promptTemplates() = listOf(
    PromptTemplate(
        title = stringResource(R.string.template_review_title),
        description = stringResource(R.string.template_review_description),
        prompt = stringResource(R.string.template_review_prompt),
    ),
    PromptTemplate(
        title = stringResource(R.string.template_test_title),
        description = stringResource(R.string.template_test_description),
        prompt = stringResource(R.string.template_test_prompt),
    ),
    PromptTemplate(
        title = stringResource(R.string.template_diff_title),
        description = stringResource(R.string.template_diff_description),
        prompt = stringResource(R.string.template_diff_prompt),
    ),
    PromptTemplate(
        title = stringResource(R.string.template_explain_title),
        description = stringResource(R.string.template_explain_description),
        prompt = stringResource(R.string.template_explain_prompt),
    ),
)

@Composable
private fun MessageCard(message: ChatMessage) {
    val colors = when (message.role) {
        ChatRole.User -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ChatRole.Assistant -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ChatRole.System -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ChatRole.Tool -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    }
    val title = when (message.role) {
        ChatRole.User -> stringResource(R.string.role_user)
        ChatRole.Assistant -> stringResource(R.string.role_assistant)
        ChatRole.System -> stringResource(R.string.role_system)
        ChatRole.Tool -> stringResource(R.string.role_tool)
    }
    Card(
        colors = colors,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            if (message.role == ChatRole.Assistant) {
                MarkdownText(text = message.text)
            } else {
                Text(text = message.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun appendAssistantDelta(messages: MutableList<ChatMessage>, delta: String) {
    val last = messages.lastOrNull()
    if (last?.role == ChatRole.Assistant) {
        messages[messages.lastIndex] = last.copy(text = last.text + delta)
    } else {
        messages += ChatMessage(ChatRole.Assistant, delta)
    }
}

private fun finalizeAssistantText(messages: MutableList<ChatMessage>, text: String) {
    if (text.isBlank()) return
    val last = messages.lastOrNull()
    if (last?.role == ChatRole.Assistant) {
        messages[messages.lastIndex] = last.copy(text = text)
    }
}

private fun OpenCodeMessage.toChatMessage(): ChatMessage {
    val chatRole = when (role) {
        "user" -> ChatRole.User
        "assistant" -> ChatRole.Assistant
        else -> ChatRole.System
    }
    return ChatMessage(role = chatRole, text = text)
}
