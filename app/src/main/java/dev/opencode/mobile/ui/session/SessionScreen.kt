package dev.opencode.mobile.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FilterChip
import androidx.compose.material.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.R
import dev.opencode.mobile.data.AgentClient
import dev.opencode.mobile.data.CachedChatMessage
import dev.opencode.mobile.data.ChatCacheStore
import dev.opencode.mobile.data.DiffFile
import dev.opencode.mobile.data.ModelOption
import dev.opencode.mobile.data.OpenCodeMessage
import dev.opencode.mobile.data.OpenCodeCommand
import dev.opencode.mobile.data.OpenCodeStreamEvent
import dev.opencode.mobile.data.PermissionReply
import dev.opencode.mobile.ui.ActiveSession
import dev.opencode.mobile.ui.ServerConnection
import dev.opencode.mobile.ui.theme.AdventureBackground
import dev.opencode.mobile.ui.theme.AdventureCard
import dev.opencode.mobile.ui.theme.AdventureFilledButton
import dev.opencode.mobile.ui.theme.AdventureOutlinedTextField
import dev.opencode.mobile.ui.theme.AdventurePill
import dev.opencode.mobile.ui.theme.AdventureSectionLabel
import dev.opencode.mobile.ui.theme.AdventureTextButton
import dev.opencode.mobile.ui.theme.AdventureTopAppBar
import dev.opencode.mobile.ui.theme.adventure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ChatMessage(
    val role: ChatRole,
    val text: String,
)

private enum class ChatRole { User, Assistant, System, Tool, Working }

private enum class StreamStatus { Connecting, Connected, Reconnecting, Disconnected, Error }

private sealed interface SheetContent {
    data object None : SheetContent
    data object Navi : SheetContent
    data object Diff : SheetContent
    data class Permission(val event: OpenCodeStreamEvent.Permission) : SheetContent
    data object AddProvider : SheetContent
    data object AddModel : SheetContent
    data object LoginProvider : SheetContent
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun SessionScreen(
    connection: ServerConnection,
    activeSession: ActiveSession,
    onBack: () -> Unit,
) {
    val agentClient = remember { AgentClient() }
    val context = LocalContext.current
    val cacheStore = remember(context) { ChatCacheStore(context) }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var sessionId by remember { mutableStateOf<String?>(activeSession.id) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sheetContent by remember { mutableStateOf<SheetContent>(SheetContent.None) }
    var commands by remember { mutableStateOf<List<OpenCodeCommand>>(emptyList()) }
    var models by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
    var selectedModel by remember { mutableStateOf<ModelOption?>(null) }
    var streamStatus by remember { mutableStateOf(StreamStatus.Connecting) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var awaitingResponse by remember { mutableStateOf(false) }
    var waitingSinceMs by remember { mutableStateOf<Long?>(null) }
    var diffFiles by remember { mutableStateOf<List<DiffFile>>(emptyList()) }
    var pendingPermission by remember { mutableStateOf<OpenCodeStreamEvent.Permission?>(null) }
    val listState = rememberLazyListState()

    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true,
    )

    LaunchedEffect(connection, activeSession.id) {
        busy = true
        sessionId = activeSession.id
        messages.clear()
        val cachedMessages = cacheStore.loadMessages(activeSession.id).map { it.toChatMessage() }
        if (cachedMessages.isNotEmpty()) {
            messages += cachedMessages
        } else {
            messages += ChatMessage(ChatRole.System, "${connection.serverUrl}\n${activeSession.directory.orEmpty()}\n${activeSession.id}")
        }
        input = cacheStore.loadDraft(activeSession.id)
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

    LaunchedEffect(messages.size, messages.lastOrNull()?.text, sessionId) {
        val activeSessionId = sessionId ?: return@LaunchedEffect
        cacheStore.saveMessages(activeSessionId, messages.map { it.toCachedMessage() })
    }

    LaunchedEffect(input, sessionId) {
        val activeSessionId = sessionId ?: return@LaunchedEffect
        cacheStore.saveDraft(activeSessionId, input)
    }

    LaunchedEffect(connection, sessionId) {
        val activeSession = sessionId ?: return@LaunchedEffect
        var reconnectDelayMs = 1000L
        while (true) {
            streamStatus = if (streamStatus == StreamStatus.Connected) StreamStatus.Reconnecting else StreamStatus.Connecting
            streamError = null
            runCatching {
                agentClient.streamEvents(
                    serverUrl = connection.serverUrl,
                    token = connection.token,
                    onOpen = { scope.launch { streamStatus = StreamStatus.Connected } },
                ) { event ->
                    if (event.sessionId != activeSession) return@streamEvents
                    scope.launch {
                        withContext(Dispatchers.Main) {
                            streamStatus = StreamStatus.Connected
                            streamError = null
                            reconnectDelayMs = 1000L
                            when (event) {
                                is OpenCodeStreamEvent.TextDelta -> {
                                    awaitingResponse = false
                                    waitingSinceMs = null
                                    appendAssistantDelta(messages, event.delta)
                                }
                                is OpenCodeStreamEvent.TextEnded -> {
                                    awaitingResponse = false
                                    waitingSinceMs = null
                                    finalizeAssistantText(messages, event.text)
                                }
                                is OpenCodeStreamEvent.Tool -> messages += ChatMessage(ChatRole.Tool, event.title)
                                is OpenCodeStreamEvent.Error -> {
                                    awaitingResponse = false
                                    waitingSinceMs = null
                                    messages += ChatMessage(ChatRole.System, event.message)
                                }
                                is OpenCodeStreamEvent.Idle -> {
                                    busy = false
                                    awaitingResponse = false
                                    waitingSinceMs = null
                                }
                                is OpenCodeStreamEvent.Permission -> pendingPermission = event
                            }
                        }
                    }
                }
            }.onFailure {
                streamStatus = StreamStatus.Error
                streamError = it.message ?: "Event stream failed"
            }
            delay(reconnectDelayMs)
            reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(10000L)
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text, sessionId, awaitingResponse) {
        if ((messages.isNotEmpty() || awaitingResponse) && listState.isNearBottom()) {
            val targetIndex = if (awaitingResponse) messages.size else messages.lastIndex
            listState.animateScrollToItem(targetIndex)
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
                    onClearLocalCache = {
                        val activeSessionId = sessionId ?: return@NaviSheetContent
                        scope.launch {
                            cacheStore.clearSession(activeSessionId)
                            messages.clear()
                            input = ""
                            messages += ChatMessage(ChatRole.System, stringResource_cacheCleared)
                            sheetContent = SheetContent.None
                        }
                    },
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
                AdventureTopAppBar(
                    title = {
                        Column {
                            Text(activeSession.title?.ifBlank { stringResource(R.string.session_title) } ?: stringResource(R.string.session_title))
                            Text(
                                text = "${streamStatus.label()} · ${selectedModel?.label ?: activeSession.directory ?: stringResource(R.string.model_default)}",
                                style = MaterialTheme.typography.overline,
                                color = MaterialTheme.adventure.textMedium,
                            )
                        }
                    },
                    navigationIcon = {
                        AdventureTextButton(onClick = onBack) { Text(stringResource(R.string.back_button)) }
                    },
                    actions = {
                        AdventureTextButton(onClick = { sheetContent = SheetContent.Navi }) {
                            Text(stringResource(R.string.navi_title))
                        }
                    },
                )
            },
            backgroundColor = MaterialTheme.colors.background,
        ) { padding ->
            AdventureBackground {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                ) {
                streamError?.let {
                    StatusCard(message = it)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (awaitingResponse) {
                    ThinkingIndicator(
                        streamStatus = streamStatus,
                        waitingSinceMs = waitingSinceMs,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(messages) { message ->
                        MessageCard(message = message, onRetry = { retryText -> input = retryText })
                    }
                    if (awaitingResponse) {
                        item {
                            WorkingMessageCard(
                                streamStatus = streamStatus,
                                waitingSinceMs = waitingSinceMs,
                            )
                        }
                    }
                }
                if (!listState.isNearBottom() && messages.isNotEmpty()) {
                    TextButton(
                        onClick = { scope.launch { listState.animateScrollToItem(messages.lastIndex) } },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.scroll_bottom_button)) }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (input.isNotBlank()) {
                    Text(
                        text = "${input.length} chars",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AdventureFilledButton(
                        onClick = { sheetContent = SheetContent.Navi },
                    ) {
                        Text(stringResource(R.string.navi_button))
                    }
                    AdventureOutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            if (it == "/") sheetContent = SheetContent.Navi
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.message_input_label)) },
                        minLines = 1,
                        maxLines = 6,
                    )
                    AdventureFilledButton(
                        enabled = !busy && sessionId != null && input.isNotBlank(),
                        onClick = {
                            val text = input.trim()
                            val activeSessionId = sessionId ?: return@AdventureFilledButton
                            input = ""
                            messages += ChatMessage(ChatRole.User, text)
                            busy = true
                            awaitingResponse = true
                            waitingSinceMs = System.currentTimeMillis()
                            scope.launch {
                                runCatching { agentClient.promptAsync(connection.serverUrl, connection.token, activeSessionId, text, selectedModel, activeSession.directory) }
                                    .onFailure {
                                        runCatching { agentClient.sendMessage(connection.serverUrl, connection.token, activeSessionId, text, selectedModel, activeSession.directory) }
                                            .onSuccess {
                                                awaitingResponse = false
                                                waitingSinceMs = null
                                                messages += ChatMessage(ChatRole.Assistant, it)
                                            }
                                            .onFailure { error ->
                                                awaitingResponse = false
                                                waitingSinceMs = null
                                                messages += ChatMessage(ChatRole.System, error.message ?: "Send failed")
                                            }
                                        busy = false
                                    }
                            }
                        },
                    ) {
                        Text(
                            when {
                                busy -> stringResource(R.string.sending_button)
                                else -> stringResource(R.string.send_button)
                            },
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun ThinkingIndicator(streamStatus: StreamStatus, waitingSinceMs: Long?) {
    val elapsed = waitingSinceMs?.let { ((System.currentTimeMillis() - it) / 1000).coerceAtLeast(0) }
    AdventureCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.adventure.infoContainer,
        elevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (elapsed != null) {
                    "Working… ${streamStatus.label()} · ${elapsed}s"
                } else {
                    "Working… ${streamStatus.label()}"
                },
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.78f),
            )
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
    onClearLocalCache: () -> Unit,
) {
    val templates = promptTemplates()
    var modelQuery by remember { mutableStateOf("") }
    val visibleModels = models.filter { model ->
        modelQuery.isBlank() || model.label.contains(modelQuery, ignoreCase = true) ||
            model.providerId.contains(modelQuery, ignoreCase = true) || model.modelId.contains(modelQuery, ignoreCase = true)
    }

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
            item {
                AdventureOutlinedTextField(
                    value = modelQuery,
                    onValueChange = { modelQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.model_search_label)) },
                    singleLine = true,
                )
            }
            items(visibleModels.take(64)) { model ->
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
        item { NaviSectionTitle(stringResource(R.string.navi_section_local)) }
        item {
            NaviActionCard(
                title = stringResource(R.string.clear_local_cache_title),
                description = stringResource(R.string.clear_local_cache_description),
                onClick = onClearLocalCache,
            )
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun DiffSheetContent(diffFiles: List<DiffFile>) {
    val clipboard = LocalClipboardManager.current
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
            AdventureCard(
                backgroundColor = MaterialTheme.adventure.cardContainer,
                elevation = 2.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = file.path,
                            style = MaterialTheme.typography.overline,
                            modifier = Modifier.weight(1f),
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.adventure.textMedium,
                        )
                        AdventureTextButton(onClick = { clipboard.setText(AnnotatedString(file.text)) }) { Text(stringResource(R.string.copy_button)) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    MarkdownText(text = "```diff\n${file.text}\n```")
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
        AdventureSectionLabel(stringResource(R.string.permission_title))
        AdventureCard(
            backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.10f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.error.copy(alpha = 0.25f)),
            elevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = permission.title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
                Text(
                    text = permission.details,
                    style = MaterialTheme.typography.body2,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.adventure.textMedium,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AdventureFilledButton(onClick = { onReply(PermissionReply.Reject) }, modifier = Modifier.weight(1f), backgroundColor = MaterialTheme.colors.error) { Text(stringResource(R.string.permission_reject)) }
            AdventureFilledButton(onClick = { onReply(PermissionReply.Once) }, modifier = Modifier.weight(1f), backgroundColor = MaterialTheme.adventure.secondary) { Text(stringResource(R.string.permission_once)) }
            AdventureFilledButton(onClick = { onReply(PermissionReply.Always) }, modifier = Modifier.weight(1f), backgroundColor = MaterialTheme.adventure.accent) { Text(stringResource(R.string.permission_always)) }
        }
    }
}

@Composable
private fun NaviSectionTitle(text: String) {
    AdventureSectionLabel(text)
}

@Composable
private fun NaviActionCard(title: String, description: String, onClick: () -> Unit) {
    AdventureCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        backgroundColor = MaterialTheme.adventure.cardContainer,
        elevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.adventure.textMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageCard(message: ChatMessage, onRetry: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    val containerColor = when (message.role) {
        ChatRole.User -> MaterialTheme.colors.primary.copy(alpha = 0.12f)
        ChatRole.Assistant -> MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
        ChatRole.System -> MaterialTheme.colors.secondary.copy(alpha = 0.12f)
        ChatRole.Tool -> MaterialTheme.colors.secondary.copy(alpha = 0.08f)
        ChatRole.Working -> MaterialTheme.adventure.accent.copy(alpha = 0.18f)
    }
    val title = when (message.role) {
        ChatRole.User -> stringResource(R.string.role_user)
        ChatRole.Assistant -> stringResource(R.string.role_assistant)
        ChatRole.System -> stringResource(R.string.role_system)
        ChatRole.Tool -> stringResource(R.string.role_tool)
        ChatRole.Working -> "Working"
    }
    AdventureCard(
        backgroundColor = containerColor,
        elevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { menuExpanded = true },
            ),
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdventurePill(
                    text = title,
                    backgroundColor = when (message.role) {
                        ChatRole.User -> MaterialTheme.colors.primary.copy(alpha = 0.16f)
                        ChatRole.Assistant -> MaterialTheme.adventure.secondary.copy(alpha = 0.14f)
                        ChatRole.System -> MaterialTheme.adventure.infoContainer
                        ChatRole.Tool -> MaterialTheme.adventure.surface2
                        ChatRole.Working -> MaterialTheme.adventure.accent.copy(alpha = 0.18f)
                    },
                    contentColor = when (message.role) {
                        ChatRole.User -> MaterialTheme.colors.primary
                        ChatRole.Assistant -> MaterialTheme.adventure.secondary
                        ChatRole.System -> MaterialTheme.adventure.info
                        ChatRole.Tool -> MaterialTheme.adventure.textMedium
                        ChatRole.Working -> MaterialTheme.adventure.accent
                    },
                )
                if (message.role == ChatRole.Assistant) {
                    MarkdownText(text = message.text)
                } else if (message.role == ChatRole.Tool) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.caption,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                    )
                } else {
                    Text(text = message.text, style = MaterialTheme.typography.body2)
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(onClick = {
                    clipboard.setText(AnnotatedString(message.text))
                    menuExpanded = false
                }) { Text(stringResource(R.string.copy_message_button)) }
                if (message.role == ChatRole.User) {
                    DropdownMenuItem(onClick = {
                        onRetry(message.text)
                        menuExpanded = false
                    }) { Text(stringResource(R.string.retry_prompt_button)) }
                }
            }
        }
    }
}

@Composable
private fun WorkingMessageCard(streamStatus: StreamStatus, waitingSinceMs: Long?) {
    val elapsed = waitingSinceMs?.let { ((System.currentTimeMillis() - it) / 1000).coerceAtLeast(0) }
    AdventureCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.adventure.accent.copy(alpha = 0.18f),
        contentColor = MaterialTheme.colors.onSurface,
        elevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Agent is working",
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.adventure.accent,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = if (elapsed != null) {
                    "Prompt accepted · ${streamStatus.label()} · ${elapsed}s"
                } else {
                    "Prompt accepted · ${streamStatus.label()}"
                },
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.adventure.textMedium,
            )
        }
    }
}

@Composable
private fun StatusCard(message: String) {
    AdventureCard(
        backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.12f),
        elevation = 0.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.error,
        )
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

private fun LazyListState.isNearBottom(): Boolean {
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return true
    return lastVisible >= layoutInfo.totalItemsCount - 2
}

private fun StreamStatus.label(): String = when (this) {
    StreamStatus.Connecting -> "Connecting"
    StreamStatus.Connected -> "Connected"
    StreamStatus.Reconnecting -> "Reconnecting"
    StreamStatus.Disconnected -> "Disconnected"
    StreamStatus.Error -> "Stream error"
}

private fun OpenCodeMessage.toChatMessage(): ChatMessage {
    val chatRole = when (role) {
        "user" -> ChatRole.User
        "assistant" -> ChatRole.Assistant
        else -> ChatRole.System
    }
    return ChatMessage(role = chatRole, text = text)
}

private fun CachedChatMessage.toChatMessage(): ChatMessage {
    val chatRole = when (role) {
        "user" -> ChatRole.User
        "assistant" -> ChatRole.Assistant
        "tool" -> ChatRole.Tool
        else -> ChatRole.System
    }
    return ChatMessage(role = chatRole, text = text)
}

private fun ChatMessage.toCachedMessage(): CachedChatMessage {
    val roleName = when (role) {
        ChatRole.User -> "user"
        ChatRole.Assistant -> "assistant"
        ChatRole.Tool -> "tool"
        ChatRole.Working -> "system"
        ChatRole.System -> "system"
    }
    return CachedChatMessage(role = roleName, text = text)
}

private const val stringResource_providerSuccess = "Done. Models will refresh."
private const val stringResource_cacheCleared = "Local cache cleared."

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
