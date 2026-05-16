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
import dev.opencode.mobile.data.ModelOption
import dev.opencode.mobile.data.OpenCodeCommand
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
    var showNavi by remember { mutableStateOf(false) }
    var commands by remember { mutableStateOf<List<OpenCodeCommand>>(emptyList()) }
    var models by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
    var selectedModel by remember { mutableStateOf<ModelOption?>(null) }

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

        runCatching { agentClient.listCommands(connection.serverUrl, connection.token) }
            .onSuccess { commands = it }
        runCatching { agentClient.listModels(connection.serverUrl, connection.token) }
            .onSuccess { availableModels ->
                models = availableModels
                selectedModel = availableModels.firstOrNull { it.providerId == "rakuraku" && it.modelId == "gpt-5.5" }
                    ?: availableModels.firstOrNull()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.session_title))
                        Text(
                            text = selectedModel?.label ?: stringResource(R.string.model_default),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                        val activeSession = sessionId ?: return@Button
                        input = ""
                        messages += ChatMessage(ChatRole.User, text)
                        busy = true
                        scope.launch {
                            runCatching { agentClient.sendMessage(connection.serverUrl, connection.token, activeSession, text, selectedModel) }
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
                val activeSession = sessionId ?: return@NaviSheet
                showNavi = false
                busy = true
                messages += ChatMessage(ChatRole.User, "/${command.name}")
                scope.launch {
                    runCatching {
                        agentClient.executeCommand(
                            serverUrl = connection.serverUrl,
                            token = connection.token,
                            sessionId = activeSession,
                            command = command.name,
                            arguments = "",
                            model = selectedModel,
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
