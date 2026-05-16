package dev.opencode.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class AgentClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
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

    private inline fun <reified T> getJson(url: String, tokenRequired: Boolean, token: String): T {
        val builder = Request.Builder().url(url)
        if (tokenRequired) builder.header("Authorization", "Bearer $token")
        httpClient.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $body")
            return json.decodeFromString<T>(body)
        }
    }
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
