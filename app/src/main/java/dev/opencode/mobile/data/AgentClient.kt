package dev.opencode.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class AgentClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun checkConnection(serverUrl: String, token: String): ConnectionResult = withContext(Dispatchers.IO) {
        val baseUrl = serverUrl.trim().trimEnd('/')
        if (baseUrl.isEmpty()) return@withContext ConnectionResult.Failure("Server URL is required")
        if (token.isBlank()) return@withContext ConnectionResult.Failure("Token is required")

        try {
            val health = getJson<HealthResponse>("$baseUrl/health", tokenRequired = false, token = token)
            val projects = getJson<ProjectsResponse>("$baseUrl/projects", tokenRequired = true, token = token)
            ConnectionResult.Success(
                upstreamHealthy = health.opencode?.healthy ?: health.upstream?.healthy == true,
                upstreamError = health.opencode?.error ?: health.upstream?.error,
                projectCount = projects.items.size,
                projectSource = health.projects?.source,
            )
        } catch (error: Exception) {
            ConnectionResult.Failure(error.message ?: "Connection failed")
        }
    }

    suspend fun getHealth(serverUrl: String): AgentHealth = withContext(Dispatchers.IO) {
        val health = getJson<HealthResponse>(
            url = "${serverUrl.trim().trimEnd('/')}/health",
            tokenRequired = false,
            token = "",
        )
        AgentHealth(
            agentVersion = health.agent?.version,
            opencodeHealthy = health.opencode?.healthy ?: health.upstream?.healthy == true,
            opencodeVersion = health.opencode?.version ?: health.upstream?.version,
            opencodeError = health.opencode?.error ?: health.upstream?.error,
            projectSource = health.projects?.source,
            projectCount = health.projects?.count ?: 0,
        )
    }

    suspend fun listProjects(serverUrl: String, token: String): List<AgentProject> = withContext(Dispatchers.IO) {
        getJson<ProjectsResponse>(
            url = "${serverUrl.trim().trimEnd('/')}/projects",
            tokenRequired = true,
            token = token,
        ).items
    }

    suspend fun listSessions(
        serverUrl: String,
        token: String,
        projectId: String? = null,
        directory: String? = null,
    ): List<AgentSession> = withContext(Dispatchers.IO) {
        val url = "${serverUrl.trim().trimEnd('/')}/sessions".toHttpUrl().newBuilder().apply {
            if (!projectId.isNullOrBlank()) addQueryParameter("projectId", projectId)
            if (!directory.isNullOrBlank()) addQueryParameter("directory", directory)
        }.build().toString()
        getJson<SessionsResponse>(url = url, tokenRequired = true, token = token).items
    }

    suspend fun createSession(serverUrl: String, token: String, directory: String? = null): String = withContext(Dispatchers.IO) {
        val response = postJson<SessionResponse>(
            url = withDirectoryQuery("${serverUrl.trim().trimEnd('/')}/opencode/session", directory),
            token = token,
            body = "{}",
        )
        response.id
    }

    suspend fun deleteSession(serverUrl: String, token: String, sessionId: String, directory: String? = null): Unit = withContext(Dispatchers.IO) {
        delete(
            url = withDirectoryQuery("${serverUrl.trim().trimEnd('/')}/opencode/session/$sessionId", directory),
            token = token,
        )
    }

    suspend fun sendMessage(serverUrl: String, token: String, sessionId: String, text: String): String = withContext(Dispatchers.IO) {
        sendMessage(serverUrl, token, sessionId, text, null, null)
    }

    suspend fun sendMessage(
        serverUrl: String,
        token: String,
        sessionId: String,
        text: String,
        model: ModelOption?,
        directory: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            if (model != null) {
                put("model", buildJsonObject {
                    put("providerID", model.providerId)
                    put("modelID", model.modelId)
                })
            }
            put("parts", JsonArray(listOf(buildJsonObject {
                put("type", "text")
                put("text", text)
            })))
        }
        val response = postJson<MessageResponse>(
            url = withDirectoryQuery("${serverUrl.trim().trimEnd('/')}/opencode/session/$sessionId/message", directory),
            token = token,
            body = json.encodeToString(body),
        )
        response.parts
            .filter { it.type == "text" }
            .joinToString(separator = "\n") { it.text.orEmpty() }
            .ifBlank { "(No text response)" }
    }

    suspend fun executeCommand(
        serverUrl: String,
        token: String,
        sessionId: String,
        command: String,
        arguments: String,
        model: ModelOption?,
        directory: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("command", command)
            put("arguments", arguments)
            if (model != null) {
                put("model", buildJsonObject {
                    put("providerID", model.providerId)
                    put("modelID", model.modelId)
                })
            }
        }
        val response = postJsonOrNull<MessageResponse>(
            url = withDirectoryQuery("${serverUrl.trim().trimEnd('/')}/opencode/session/$sessionId/command", directory),
            token = token,
            body = json.encodeToString(body),
        )
        response?.parts
            ?.filter { it.type == "text" }
            ?.joinToString(separator = "\n") { it.text.orEmpty() }
            ?.ifBlank { "Command sent: /$command" }
            ?: "Command sent: /$command"
    }

    suspend fun promptAsync(
        serverUrl: String,
        token: String,
        sessionId: String,
        text: String,
        model: ModelOption?,
        directory: String? = null,
    ) = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            if (model != null) {
                put("model", buildJsonObject {
                    put("providerID", model.providerId)
                    put("modelID", model.modelId)
                })
            }
            put("parts", JsonArray(listOf(buildJsonObject {
                put("type", "text")
                put("text", text)
            })))
        }
        postEmpty(
            url = withDirectoryQuery("${serverUrl.trim().trimEnd('/')}/opencode/session/$sessionId/prompt_async", directory),
            token = token,
            body = json.encodeToString(body),
        )
    }

    suspend fun streamEvents(
        serverUrl: String,
        token: String,
        onOpen: () -> Unit = {},
        onEvent: (OpenCodeStreamEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${serverUrl.trim().trimEnd('/')}/opencode/event")
            .header("Authorization", "Bearer $token")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${response.body?.string().orEmpty()}")
            onOpen()
            val source = response.body?.source() ?: throw IOException("Missing event stream body")
            var eventName: String? = null
            val data = StringBuilder()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
                    line.isBlank() -> {
                        val payload = data.toString()
                        if (payload.isNotBlank()) parseStreamEvent(eventName, payload)?.let(onEvent)
                        eventName = null
                        data.clear()
                    }
                }
            }
        }
    }

    suspend fun listCommands(serverUrl: String, token: String): List<OpenCodeCommand> = withContext(Dispatchers.IO) {
        getJson<List<OpenCodeCommand>>("${serverUrl.trim().trimEnd('/')}/opencode/command", tokenRequired = true, token = token)
    }

    suspend fun listModels(serverUrl: String, token: String): List<ModelOption> = withContext(Dispatchers.IO) {
        val root = getJson<JsonElement>("${serverUrl.trim().trimEnd('/')}/opencode/provider", tokenRequired = true, token = token)
        val providers = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["all"] ?: root["providers"]) as? JsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }

        providers.flatMap { providerElement ->
            val provider = providerElement.jsonObject
            val providerId = provider["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val providerName = provider["name"]?.jsonPrimitive?.contentOrNull ?: providerId
            val models = provider["models"]?.jsonObject ?: return@flatMap emptyList()
            models.values.mapNotNull { modelElement ->
                val model = modelElement.jsonObject
                val modelId = model["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                ModelOption(
                    providerId = providerId,
                    providerName = providerName,
                    modelId = modelId,
                    modelName = model["name"]?.jsonPrimitive?.contentOrNull ?: modelId,
                )
            }
        }.sortedWith(compareBy<ModelOption> { it.providerName }.thenBy { it.modelName })
    }

    suspend fun listMessages(serverUrl: String, token: String, sessionId: String, directory: String? = null): List<OpenCodeMessage> = withContext(Dispatchers.IO) {
        getJson<List<MessageHistoryItem>>(
            url = withDirectoryQuery("${serverUrl.trim().trimEnd('/')}/opencode/session/$sessionId/message", directory),
            tokenRequired = true,
            token = token,
        ).mapNotNull { item ->
            val text = item.parts
                .filter { it.type == "text" }
                .joinToString(separator = "\n") { it.text.orEmpty() }
                .trim()
            if (text.isBlank()) return@mapNotNull null
            OpenCodeMessage(role = item.info.role, text = text)
        }
    }

    suspend fun getSessionDiff(serverUrl: String, token: String, sessionId: String, directory: String? = null): List<DiffFile> = withContext(Dispatchers.IO) {
        val root = getJson<JsonElement>(
            url = withDirectoryQuery("${serverUrl.trim().trimEnd('/')}/opencode/session/$sessionId/diff", directory),
            tokenRequired = true,
            token = token,
        )
        root.jsonArrayOrEmpty().mapIndexed { index, element ->
            val obj = element as? JsonObject
            val path = obj?.get("path")?.jsonPrimitive?.contentOrNull
                ?: obj?.get("file")?.jsonPrimitive?.contentOrNull
                ?: obj?.get("name")?.jsonPrimitive?.contentOrNull
                ?: "diff-${index + 1}"
            DiffFile(
                path = path,
                text = obj?.get("diff")?.jsonPrimitive?.contentOrNull
                    ?: obj?.get("patch")?.jsonPrimitive?.contentOrNull
                    ?: obj?.get("content")?.jsonPrimitive?.contentOrNull
                    ?: obj?.get("text")?.jsonPrimitive?.contentOrNull
                    ?: json.encodeToString(JsonElement.serializer(), element),
            )
        }
    }

    suspend fun replyPermission(serverUrl: String, token: String, requestId: String, reply: PermissionReply) = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("reply", reply.wireValue)
        }
        postJson<Boolean>(
            url = "${serverUrl.trim().trimEnd('/')}/opencode/permission/$requestId/reply",
            token = token,
            body = json.encodeToString(payload),
        )
    }

    suspend fun addProvider(
        serverUrl: String,
        token: String,
        id: String,
        baseURL: String,
        apiKey: String,
        models: Map<String, String> = emptyMap(),
    ): Unit = withContext(Dispatchers.IO) {
        val modelsObj = buildJsonObject {
            models.forEach { (mid, name) ->
                put(mid, buildJsonObject {
                    put("name", name)
                    put("context", 128000)
                    put("output", 32000)
                })
            }
        }
        val body = buildJsonObject {
            put("id", id)
            put("baseURL", baseURL)
            put("apiKey", apiKey)
            put("models", modelsObj)
        }
        postJson<OkResponse>(
            url = "${serverUrl.trim().trimEnd('/')}/providers",
            token = token,
            body = json.encodeToString(body),
        )
    }

    suspend fun addModel(
        serverUrl: String,
        token: String,
        providerId: String,
        modelId: String,
        modelName: String,
    ): Unit = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("modelId", modelId)
            put("name", modelName)
        }
        postJson<OkResponse>(
            url = "${serverUrl.trim().trimEnd('/')}/providers/$providerId/models",
            token = token,
            body = json.encodeToString(body),
        )
    }

    suspend fun loginProvider(
        serverUrl: String,
        token: String,
        providerId: String,
        apiKey: String,
    ): Unit = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("apiKey", apiKey)
        }
        postJson<OkResponse>(
            url = "${serverUrl.trim().trimEnd('/')}/providers/$providerId/auth",
            token = token,
            body = json.encodeToString(body),
        )
    }

    private inline fun <reified T> getJson(url: String, tokenRequired: Boolean, token: String): T {
        val builder = Request.Builder().url(url)
        if (tokenRequired) builder.header("Authorization", "Bearer $token")
        httpClient.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $body")
            return json.decodeFromString<T>(body)
        }
    }

    private inline fun <reified T> postJson(url: String, token: String, body: String): T {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $responseBody")
            return json.decodeFromString<T>(responseBody)
        }
    }

    private inline fun <reified T> postJsonOrNull(url: String, token: String, body: String): T? {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $responseBody")
            if (responseBody.isBlank()) return null
            return json.decodeFromString<T>(responseBody)
        }
    }

    private fun postEmpty(url: String, token: String, body: String) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $responseBody")
        }
    }

    private fun delete(url: String, token: String) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $responseBody")
        }
    }

    private fun withDirectoryQuery(url: String, directory: String?): String {
        if (directory.isNullOrBlank()) return url
        return url.toHttpUrl().newBuilder()
            .addQueryParameter("directory", directory)
            .build()
            .toString()
    }

    private fun parseStreamEvent(eventName: String?, payload: String): OpenCodeStreamEvent? {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        val type = root["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val name = root["name"]?.jsonPrimitive?.contentOrNull ?: type
        val properties = root["properties"]?.jsonObject ?: root["data"]?.jsonObject ?: root
        val sessionId = properties["sessionID"]?.jsonPrimitive?.contentOrNull

        return when (name) {
            "session.next.text.delta", "session.next.text.delta.1", "message.part.delta" -> {
                val delta = properties["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (sessionId == null || delta.isEmpty()) null else OpenCodeStreamEvent.TextDelta(sessionId, delta)
            }
            "session.next.text.ended", "session.next.text.ended.1" -> {
                val text = properties["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (sessionId == null) null else OpenCodeStreamEvent.TextEnded(sessionId, text)
            }
            "session.next.tool.called", "session.next.tool.called.1" -> {
                val tool = properties["tool"]?.jsonPrimitive?.contentOrNull ?: "tool"
                if (sessionId == null) null else OpenCodeStreamEvent.Tool(sessionId, tool)
            }
            "session.next.shell.started", "session.next.shell.started.1" -> {
                val command = properties["command"]?.jsonPrimitive?.contentOrNull ?: "shell"
                if (sessionId == null) null else OpenCodeStreamEvent.Tool(sessionId, command)
            }
            "session.next.tool.failed", "session.next.tool.failed.1", "session.error" -> {
                val message = properties["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                    ?: properties["message"]?.jsonPrimitive?.contentOrNull
                    ?: eventName
                    ?: name
                if (sessionId == null) null else OpenCodeStreamEvent.Error(sessionId, message)
            }
            "session.idle", "session.status" -> {
                if (sessionId == null) null else OpenCodeStreamEvent.Idle(sessionId)
            }
            "permission.asked" -> {
                val requestId = properties["id"]?.jsonPrimitive?.contentOrNull ?: root["id"]?.jsonPrimitive?.contentOrNull
                val permissionSessionId = properties["sessionID"]?.jsonPrimitive?.contentOrNull
                val permission = properties["permission"]?.jsonPrimitive?.contentOrNull ?: "permission"
                val patterns = properties["patterns"].jsonArrayOrEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
                if (requestId == null || permissionSessionId == null) null else OpenCodeStreamEvent.Permission(
                    sessionId = permissionSessionId,
                    requestId = requestId,
                    title = permission,
                    details = patterns.joinToString("\n").ifBlank { permission },
                )
            }
            else -> null
        }
    }
}

private fun JsonElement?.jsonArrayOrEmpty(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())

@Serializable
data class OpenCodeCommand(
    val name: String,
    val description: String? = null,
)

data class ModelOption(
    val providerId: String,
    val providerName: String,
    val modelId: String,
    val modelName: String,
) {
    val label: String = "$providerName / $modelName"
}

data class OpenCodeMessage(
    val role: String,
    val text: String,
)

data class DiffFile(
    val path: String,
    val text: String,
)

@Serializable
data class AgentProject(
    val id: String,
    val name: String,
    val worktree: String,
    val vcs: String,
    val lastActive: Long,
)

@Serializable
data class AgentSession(
    val id: String,
    val projectId: String,
    val directory: String,
    val title: String,
    val lastActive: Long,
)

data class AgentHealth(
    val agentVersion: String?,
    val opencodeHealthy: Boolean,
    val opencodeVersion: String?,
    val opencodeError: String?,
    val projectSource: String?,
    val projectCount: Int,
)

enum class PermissionReply(val wireValue: String) {
    Once("once"),
    Always("always"),
    Reject("reject"),
}

sealed interface OpenCodeStreamEvent {
    val sessionId: String

    data class TextDelta(override val sessionId: String, val delta: String) : OpenCodeStreamEvent
    data class TextEnded(override val sessionId: String, val text: String) : OpenCodeStreamEvent
    data class Tool(override val sessionId: String, val title: String) : OpenCodeStreamEvent
    data class Error(override val sessionId: String, val message: String) : OpenCodeStreamEvent
    data class Idle(override val sessionId: String) : OpenCodeStreamEvent
    data class Permission(
        override val sessionId: String,
        val requestId: String,
        val title: String,
        val details: String,
    ) : OpenCodeStreamEvent
}

sealed interface ConnectionResult {
    data class Success(
        val upstreamHealthy: Boolean,
        val upstreamError: String?,
        val projectCount: Int,
        val projectSource: String?,
    ) : ConnectionResult

    data class Failure(val message: String) : ConnectionResult
}

@Serializable
private data class HealthResponse(
    val healthy: Boolean,
    val agent: AgentInfo? = null,
    val opencode: OpenCodeHealth? = null,
    val projects: ProjectsInfo? = null,
    val upstream: UpstreamHealth? = null,
)

@Serializable
private data class AgentInfo(
    val version: String? = null,
)

@Serializable
private data class OpenCodeHealth(
    val healthy: Boolean? = null,
    val version: String? = null,
    val error: String? = null,
)

@Serializable
private data class ProjectsInfo(
    val source: String? = null,
    val count: Int = 0,
)

@Serializable
private data class UpstreamHealth(
    val healthy: Boolean? = null,
    val version: String? = null,
    val error: String? = null,
)

@Serializable
private data class WorkspacesResponse(
    val items: List<WorkspaceItem> = emptyList(),
)

@Serializable
private data class ProjectsResponse(
    val items: List<AgentProject> = emptyList(),
)

@Serializable
private data class SessionsResponse(
    val items: List<AgentSession> = emptyList(),
)

@Serializable
private data class WorkspaceItem(
    val id: String,
    val name: String,
    val path: String,
)

@Serializable
private data class SessionResponse(
    val id: String,
)

@Serializable
private data class MessageResponse(
    val parts: List<MessagePart> = emptyList(),
)

@Serializable
private data class MessagePart(
    val type: String,
    val text: String? = null,
)

@Serializable
private data class MessageHistoryItem(
    val info: MessageInfo,
    val parts: List<MessagePart> = emptyList(),
)

@Serializable
private data class MessageInfo(
    val role: String,
)

@Serializable
private data class OkResponse(
    val ok: Boolean = false,
)
