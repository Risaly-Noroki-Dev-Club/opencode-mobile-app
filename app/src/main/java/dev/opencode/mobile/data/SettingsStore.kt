package dev.opencode.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

data class SavedConnection(
    val serverUrl: String = "",
    val token: String = "",
)

enum class ThemeMode { System, Light, Dark }

@Serializable
data class ServerHistoryEntry(
    val serverUrl: String,
    val token: String,
)

class SettingsStore(private val context: Context) {
    private val serverUrlKey = stringPreferencesKey("server_url")
    private val tokenKey = stringPreferencesKey("token")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val serverHistoryKey = stringPreferencesKey("server_history")
    private val json = Json { ignoreUnknownKeys = true }

    val connection: Flow<SavedConnection> = context.dataStore.data.map { preferences ->
        SavedConnection(
            serverUrl = preferences[serverUrlKey].orEmpty(),
            token = preferences[tokenKey].orEmpty(),
        )
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        when (preferences[themeModeKey]) {
            "light" -> ThemeMode.Light
            "dark" -> ThemeMode.Dark
            else -> ThemeMode.System
        }
    }

    suspend fun saveConnection(serverUrl: String, token: String) {
        context.dataStore.edit { preferences ->
            preferences[serverUrlKey] = serverUrl
            preferences[tokenKey] = token
        }
        addToHistory(serverUrl, token)
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[themeModeKey] = when (mode) {
                ThemeMode.System -> "system"
                ThemeMode.Light -> "light"
                ThemeMode.Dark -> "dark"
            }
        }
    }

    suspend fun loadHistory(): List<ServerHistoryEntry> {
        val raw = context.dataStore.data.first()[serverHistoryKey].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<ServerHistoryEntry>>(raw) }.getOrDefault(emptyList())
    }

    private suspend fun addToHistory(serverUrl: String, token: String) {
        val history = loadHistory().toMutableList()
        history.removeAll { it.serverUrl == serverUrl }
        history.add(0, ServerHistoryEntry(serverUrl, token))
        val trimmed = history.take(10)
        context.dataStore.edit { preferences ->
            preferences[serverHistoryKey] = json.encodeToString(trimmed)
        }
    }
}
