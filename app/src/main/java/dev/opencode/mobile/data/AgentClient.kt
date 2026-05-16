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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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
            val workspaces = getJson<WorkspacesResponse>("$baseUrl/workspaces", tokenRequired = true, token = token)
            ConnectionResult.Success(
                upstreamHealthy = health.upstream?.healthy == true,
                upstreamError = health.upstream?.error,
                workspaceCount = workspaces.items.size,
            )
        } catch (error: Exception) {
            ConnectionResult.Failure(error.message ?: "Connection failed")
        }
    }

    suspend fun createSession(serverUrl: String, token: String): String = withContext(Dispatchers.IO) {
        val response = postJson<SessionResponse>(
            url = "${serverUrl.trim().trimEnd('/')}/opencode/session",
            token = token,
            body = "{}",
        )
        response.id
    }

    suspend fun sendMessage(serverUrl: String, token: String, sessionId: String, text: String): String = withContext(Dispatchers.IO) {
        sendMessage(serverUrl, token, sessionId, text, null)
    }

    suspend fun sendMessage(
        serverUrl: String,
        token: String,
        sessionId: String,
        text: String,
        model: ModelOption?,
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
            url = "${serverUrl.trim().trimEnd('/')}/opencode/session/$sessionId/message",
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
            url = "${serverUrl.trim().trimEnd('/')}/opencode/session/$sessionId/command",
            token = token,
            body = json.encodeToString(body),
        )
        response?.parts
            ?.filter { it.type == "text" }
            ?.joinToString(separator = "\n") { it.text.orEmpty() }
            ?.ifBlank { "Command sent: /$command" }
            ?: "Command sent: /$command"
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
}

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

sealed interface ConnectionResult {
    data class Success(
        val upstreamHealthy: Boolean,
        val upstreamError: String?,
        val workspaceCount: Int,
    ) : ConnectionResult

    data class Failure(val message: String) : ConnectionResult
}

@Serializable
private data class HealthResponse(
    val healthy: Boolean,
    val upstream: UpstreamHealth? = null,
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
