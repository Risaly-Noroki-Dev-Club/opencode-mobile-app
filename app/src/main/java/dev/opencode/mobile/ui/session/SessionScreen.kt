package dev.opencode.mobile.ui.session

import androidx.compose.foundation.clickable
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
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FilterChip
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.rememberModalBottomSheetState
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

private sealed interface SheetContent {
    data object None : SheetContent
    data object Navi : SheetContent
    data object Diff : SheetContent
    data class Permission(val event: OpenCodeStreamEvent.Permission) : SheetContent
    data object AddProvider : SheetContent
    data object AddModel : SheetContent
    data object LoginProvider : SheetContent
}

@OptIn(ExperimentalMaterialApi::class)
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
    var sheetContent by remember { mutableStateOf<SheetContent>(SheetContent.None) }
    var commands by remember { mutableStateOf<List<OpenCodeCommand>>(emptyList()) }
    var models by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
    var selectedModel by remember { mutableStateOf<ModelOption?>(null) }
    var streamActive by remember { mutableStateOf(false) }
    var diffFiles by remember { mutableStateOf<List<DiffFile>>(emptyList()) }
    var pendingPermission by remember { mutableStateOf<OpenCodeStreamEvent.Permission?>(null) }

    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true,
    )

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

    // Show sheet when pendingPermission is set
    LaunchedEffect(pendingPermission) {
        if (pendingPermission != null) {
            sheetContent = SheetContent.Permission(pendingPermission!!)
        }
    }

    // Animate sheet open/close based on sheetContent
    LaunchedEffect(sheetContent) {
        if (sheetContent == SheetContent.None) {
            sheetState.hide()
        } else {
            sheetState.show()
        }
    }

    // Sync back when user swipes to dismiss
    LaunchedEffect(sheetState.isVisible) {
        if (!sheetState.isVisible && sheetContent != SheetContent.None) {
            if (sheetContent is SheetContent.Permission) {
                pendingPermission = null
            }
            sheetContent = SheetContent.None
        }
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        sheetBackgroundColor = MaterialTheme.colors.surface,
        sheetContent = {
            when (val content = sheetContent) {
                SheetContent.None -> Box(Modifier.height(1.dp))
                SheetContent.Navi -> NaviSheetContent(
                    commands = commands,
                    models = models,
                    selectedModel = selectedModel,
                    onTemplate = { template ->
                        input = template
                        sheetContent = SheetContent.None
                    },
                    onCommand = { command ->
                        val activeSessionId = sessionId ?: return@NaviSheetContent
                        sheetContent = SheetContent.None
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
                        sheetContent = SheetContent.None
                    },
                    onDiff = {
                        val activeSessionId = sessionId ?: return@NaviSheetContent
                        scope.launch {
                            runCatching { agentClient.getSessionDiff(connection.serverUrl, connection.token, activeSessionId, activeSession.directory) }
                                .onSuccess {
                                    diffFiles = it
                                    sheetContent = SheetContent.Diff
                                }
                                .onFailure { messages += ChatMessage(ChatRole.System, it.message ?: "Diff failed") }
                        }
                    },
                    onAddProvider = { sheetContent = SheetContent.AddProvider },
                    onAddModel = { sheetContent = SheetContent.AddModel },
                    onLoginProvider = { sheetContent = SheetContent.LoginProvider },
                )
                SheetContent.Diff -> DiffSheetContent(
                    diffFiles = diffFiles,
                )
                is SheetContent.Permission -> PermissionSheetContent(
                    permission = content.event,
                    onReply = { reply ->
                        scope.launch {
                            runCatching { agentClient.replyPermission(connection.serverUrl, connection.token, content.event.requestId, reply) }
                                .onSuccess { messages += ChatMessage(ChatRole.System, "${content.event.title}: ${reply.wireValue}") }
                                .onFailure { messages += ChatMessage(ChatRole.System, it.message ?: "Permission reply failed") }
                            pendingPermission = null
                            sheetContent = SheetContent.None
                        }
                    },
                )
                SheetContent.AddProvider -> AddProviderSheetContent(
                    onSubmit = { id, baseURL, apiKey, modelId, modelName ->
                        sheetContent = SheetContent.None
                        scope.launch {
                            val modelMap = if (modelId.isNotBlank() && modelName.isNotBlank()) mapOf(modelId to modelName) else emptyMap()
                            runCatching { agentClient.addProvider(connection.serverUrl, connection.token, id, baseURL, apiKey, modelMap) }
                                .onSuccess {
                                    messages += ChatMessage(ChatRole.System, stringResource_providerSuccess)
                                    reloadModels(agentClient, connection, models = { models = it }, selected = { selectedModel = it })
                                }
                                .onFailure { messages += ChatMessage(ChatRole.System, it.message ?: "Failed") }
                        }
                    },
                )
                SheetContent.AddModel -> AddModelSheetContent(
                    onSubmit = { providerId, modelId, modelName ->
                        sheetContent = SheetContent.None
                        scope.launch {
                            runCatching { agentClient.addModel(connection.serverUrl, connection.token, providerId, modelId, modelName) }
                                .onSuccess {
                                    messages += ChatMessage(ChatRole.System, stringResource_providerSuccess)
                                    reloadModels(agentClient, connection, models = { models = it }, selected = { selectedModel = it })
                                }
                                .onFailure { messages += ChatMessage(ChatRole.System, it.message ?: "Failed") }
                        }
                    },
                )
                SheetContent.LoginProvider -> LoginProviderSheetContent(
                    onSubmit = { providerId, apiKey ->
                        sheetContent = SheetContent.None
                        scope.launch {
                            runCatching { agentClient.loginProvider(connection.serverUrl, connection.token, providerId, apiKey) }
                                .onSuccess {
                                    messages += ChatMessage(ChatRole.System, stringResource_providerSuccess)
                                    reloadModels(agentClient, connection, models = { models = it }, selected = { selectedModel = it })
                                }
                                .onFailure { messages += ChatMessage(ChatRole.System, it.message ?: "Failed") }
                        }
                    },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(activeSession.title?.ifBlank { stringResource(R.string.session_title) } ?: stringResource(R.string.session_title))
                            Text(
                                text = selectedModel?.label ?: activeSession.directory ?: stringResource(R.string.model_default),
                                style = MaterialTheme.typography.overline,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    },
                    navigationIcon = {
                        TextButton(onClick = onBack) { Text(stringResource(R.string.back_button)) }
                    },
                    actions = {
                        TextButton(onClick = { sheetContent = SheetContent.Navi }) {
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
                        onClick = { sheetContent = SheetContent.Navi },
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(stringResource(R.string.navi_button))
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            if (it == "/") sheetContent = SheetContent.Navi
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
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun NaviSheetContent(
    commands: List<OpenCodeCommand>,
    models: List<ModelOption>,
    selectedModel: ModelOption?,
    onTemplate: (String) -> Unit,
    onCommand: (OpenCodeCommand) -> Unit,
    onModel: (ModelOption) -> Unit,
    onDiff: () -> Unit,
    onAddProvider: () -> Unit,
    onAddModel: () -> Unit,
    onLoginProvider: () -> Unit,
) {
    val templates = promptTemplates()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.navi_title),
                style = MaterialTheme.typography.h5,
                color = MaterialTheme.colors.primary,
            )
            Text(
                text = stringResource(R.string.navi_subtitle),
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
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
                FilterChip(
                    selected = selectedModel?.providerId == model.providerId && selectedModel.modelId == model.modelId,
                    onClick = { onModel(model) },
                    modifier = Modifier.widthIn(max = 520.dp),
                ) {
                    Text(model.label)
                }
            }
        }
        item { NaviSectionTitle(stringResource(R.string.navi_section_providers)) }
        item {
            NaviActionCard(
                title = stringResource(R.string.navi_add_provider_title),
                description = stringResource(R.string.navi_add_provider_description),
                onClick = onAddProvider,
            )
        }
        item {
            NaviActionCard(
                title = stringResource(R.string.navi_add_model_title),
                description = stringResource(R.string.navi_add_model_description),
                onClick = onAddModel,
            )
        }
        item {
            NaviActionCard(
                title = stringResource(R.string.navi_login_provider_title),
                description = stringResource(R.string.navi_login_provider_description),
                onClick = onLoginProvider,
            )
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun DiffSheetContent(diffFiles: List<DiffFile>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(text = stringResource(R.string.diff_title), style = MaterialTheme.typography.h6)
            Text(
                text = if (diffFiles.isEmpty()) stringResource(R.string.diff_empty) else stringResource(R.string.diff_count, diffFiles.size),
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            )
        }
        items(diffFiles) { file ->
            Card(
                shape = MaterialTheme.shapes.large,
                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.08f),
                elevation = 2.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = file.path, style = MaterialTheme.typography.overline)
                    Spacer(modifier = Modifier.height(8.dp))
                    MarkdownText(text = "```json\n${file.text}\n```")
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun PermissionSheetContent(
    permission: OpenCodeStreamEvent.Permission,
    onReply: (PermissionReply) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = stringResource(R.string.permission_title), style = MaterialTheme.typography.h6)
        Text(text = permission.title, style = MaterialTheme.typography.subtitle2)
        Text(text = permission.details, style = MaterialTheme.typography.body2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onReply(PermissionReply.Reject) }) { Text(stringResource(R.string.permission_reject)) }
            Button(onClick = { onReply(PermissionReply.Once) }) { Text(stringResource(R.string.permission_once)) }
            Button(onClick = { onReply(PermissionReply.Always) }) { Text(stringResource(R.string.permission_always)) }
        }
    }
}

@Composable
private fun NaviSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.subtitle2,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun NaviActionCard(title: String, description: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.08f),
        shape = MaterialTheme.shapes.large,
        elevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = title, style = MaterialTheme.typography.subtitle2)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
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
    val containerColor = when (message.role) {
        ChatRole.User -> MaterialTheme.colors.primary.copy(alpha = 0.12f)
        ChatRole.Assistant -> MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
        ChatRole.System -> MaterialTheme.colors.secondary.copy(alpha = 0.12f)
        ChatRole.Tool -> MaterialTheme.colors.secondary.copy(alpha = 0.08f)
    }
    val title = when (message.role) {
        ChatRole.User -> stringResource(R.string.role_user)
        ChatRole.Assistant -> stringResource(R.string.role_assistant)
        ChatRole.System -> stringResource(R.string.role_system)
        ChatRole.Tool -> stringResource(R.string.role_tool)
    }
    Card(
        backgroundColor = containerColor,
        shape = MaterialTheme.shapes.large,
        elevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.button)
            Spacer(modifier = Modifier.height(6.dp))
            if (message.role == ChatRole.Assistant) {
                MarkdownText(text = message.text)
            } else {
                Text(text = message.text, style = MaterialTheme.typography.body2)
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

private const val stringResource_providerSuccess = "Done. Models will refresh."

private suspend fun reloadModels(
    client: AgentClient,
    connection: ServerConnection,
    models: (List<ModelOption>) -> Unit,
    selected: (ModelOption?) -> Unit,
) {
    kotlinx.coroutines.delay(3000)
    runCatching { client.listModels(connection.serverUrl, connection.token) }
        .onSuccess { list ->
            models(list)
            selected(list.firstOrNull())
        }
}

@Composable
private fun AddProviderSheetContent(onSubmit: (String, String, String, String, String) -> Unit) {
    var id by remember { mutableStateOf("") }
    var baseURL by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.navi_add_provider_title), style = MaterialTheme.typography.h6)
        OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text(stringResource(R.string.provider_id_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = baseURL, onValueChange = { baseURL = it }, label = { Text(stringResource(R.string.provider_base_url_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text(stringResource(R.string.provider_api_key_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = modelId, onValueChange = { modelId = it }, label = { Text(stringResource(R.string.provider_model_id_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = modelName, onValueChange = { modelName = it }, label = { Text(stringResource(R.string.provider_model_name_label)) }, modifier = Modifier.fillMaxWidth())
        Button(
            enabled = id.isNotBlank() && baseURL.isNotBlank() && apiKey.isNotBlank(),
            onClick = { onSubmit(id.trim(), baseURL.trim(), apiKey.trim(), modelId.trim(), modelName.trim()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.provider_submit)) }
    }
}

@Composable
private fun AddModelSheetContent(onSubmit: (String, String, String) -> Unit) {
    var providerId by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.navi_add_model_title), style = MaterialTheme.typography.h6)
        OutlinedTextField(value = providerId, onValueChange = { providerId = it }, label = { Text(stringResource(R.string.provider_id_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = modelId, onValueChange = { modelId = it }, label = { Text(stringResource(R.string.provider_model_id_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = modelName, onValueChange = { modelName = it }, label = { Text(stringResource(R.string.provider_model_name_label)) }, modifier = Modifier.fillMaxWidth())
        Button(
            enabled = providerId.isNotBlank() && modelId.isNotBlank() && modelName.isNotBlank(),
            onClick = { onSubmit(providerId.trim(), modelId.trim(), modelName.trim()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.provider_submit)) }
    }
}

@Composable
private fun LoginProviderSheetContent(onSubmit: (String, String) -> Unit) {
    var providerId by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.navi_login_provider_title), style = MaterialTheme.typography.h6)
        OutlinedTextField(value = providerId, onValueChange = { providerId = it }, label = { Text(stringResource(R.string.provider_id_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text(stringResource(R.string.provider_api_key_label)) }, modifier = Modifier.fillMaxWidth())
        Button(
            enabled = providerId.isNotBlank() && apiKey.isNotBlank(),
            onClick = { onSubmit(providerId.trim(), apiKey.trim()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.provider_submit)) }
    }
}
