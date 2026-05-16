package dev.opencode.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.chatCacheDataStore by preferencesDataStore(name = "chat_cache")

@Serializable
data class CachedChatMessage(
    val role: String,
    val text: String,
)

class ChatCacheStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadMessages(sessionId: String): List<CachedChatMessage> {
        val raw = context.chatCacheDataStore.data.first()[messagesKey(sessionId)].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<CachedChatMessage>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun saveMessages(sessionId: String, messages: List<CachedChatMessage>) {
        context.chatCacheDataStore.edit { preferences ->
            preferences[messagesKey(sessionId)] = json.encodeToString(messages.takeLast(120))
        }
    }

    suspend fun loadDraft(sessionId: String): String {
        return context.chatCacheDataStore.data.first()[draftKey(sessionId)].orEmpty()
    }

    suspend fun saveDraft(sessionId: String, draft: String) {
        context.chatCacheDataStore.edit { preferences ->
            if (draft.isBlank()) {
                preferences.remove(draftKey(sessionId))
            } else {
                preferences[draftKey(sessionId)] = draft
            }
        }
    }

    suspend fun clearSession(sessionId: String) {
        context.chatCacheDataStore.edit { preferences ->
            preferences.remove(messagesKey(sessionId))
            preferences.remove(draftKey(sessionId))
        }
    }

    suspend fun saveRecentSession(projectId: String, sessionId: String) {
        context.chatCacheDataStore.edit { preferences ->
            preferences[recentSessionKey(projectId)] = sessionId
        }
    }

    suspend fun loadRecentSession(projectId: String): String {
        return context.chatCacheDataStore.data.first()[recentSessionKey(projectId)].orEmpty()
    }

    private fun messagesKey(sessionId: String) = stringPreferencesKey("messages_${sessionId.safeKey()}")
    private fun draftKey(sessionId: String) = stringPreferencesKey("draft_${sessionId.safeKey()}")
    private fun recentSessionKey(projectId: String) = stringPreferencesKey("recent_session_${projectId.safeKey()}")
}

private fun String.safeKey(): String = map { char ->
    if (char.isLetterOrDigit()) char else '_'
}.joinToString(separator = "")
