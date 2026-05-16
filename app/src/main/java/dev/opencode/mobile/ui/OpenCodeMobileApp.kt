package dev.opencode.mobile.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.opencode.mobile.data.AgentProject
import dev.opencode.mobile.ui.connect.ConnectScreen
import dev.opencode.mobile.ui.project.ProjectListScreen
import dev.opencode.mobile.ui.session.SessionListScreen
import dev.opencode.mobile.ui.session.SessionScreen

private sealed interface AppScreen {
    data object Connect : AppScreen
    data class Projects(val connection: ServerConnection) : AppScreen
    data class Sessions(val connection: ServerConnection, val project: AgentProject) : AppScreen
    data class Chat(val connection: ServerConnection, val project: AgentProject, val session: ActiveSession) : AppScreen
}

@Composable
fun OpenCodeMobileApp() {
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Connect) }
    Surface(modifier = Modifier.fillMaxSize()) {
        when (val active = screen) {
            AppScreen.Connect -> ConnectScreen(onConnected = { screen = AppScreen.Projects(it) })
            is AppScreen.Projects -> ProjectListScreen(
                connection = active.connection,
                onProject = { screen = AppScreen.Sessions(active.connection, it) },
                onDisconnect = { screen = AppScreen.Connect },
            )
            is AppScreen.Sessions -> SessionListScreen(
                connection = active.connection,
                project = active.project,
                onBack = { screen = AppScreen.Projects(active.connection) },
                onSession = { screen = AppScreen.Chat(active.connection, active.project, it) },
            )
            is AppScreen.Chat -> SessionScreen(
                connection = active.connection,
                activeSession = active.session,
                onBack = { screen = AppScreen.Sessions(active.connection, active.project) },
            )
        }
    }
}
