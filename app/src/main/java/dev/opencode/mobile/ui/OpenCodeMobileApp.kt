package dev.opencode.mobile.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.opencode.mobile.ui.connect.ConnectScreen
import dev.opencode.mobile.ui.session.ChatConnection
import dev.opencode.mobile.ui.session.SessionScreen

@Composable
fun OpenCodeMobileApp() {
    var connection by remember { mutableStateOf<ChatConnection?>(null) }
    Surface(modifier = Modifier.fillMaxSize()) {
        val activeConnection = connection
        if (activeConnection == null) {
            ConnectScreen(onConnected = { connection = it })
        } else {
            SessionScreen(connection = activeConnection)
        }
    }
}
