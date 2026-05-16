package dev.opencode.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class SavedConnection(
    val serverUrl: String = "",
    val token: String = "",
)

class SettingsStore(private val context: Context) {
    private val serverUrlKey = stringPreferencesKey("server_url")
    private val tokenKey = stringPreferencesKey("token")

    val connection: Flow<SavedConnection> = context.dataStore.data.map { preferences ->
        SavedConnection(
            serverUrl = preferences[serverUrlKey].orEmpty(),
            token = preferences[tokenKey].orEmpty(),
        )
    }

    suspend fun saveConnection(serverUrl: String, token: String) {
        context.dataStore.edit { preferences ->
            preferences[serverUrlKey] = serverUrl
            preferences[tokenKey] = token
        }
    }
}
