package dev.opencode.mobile.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.opencode.mobile.ui.connect.ConnectScreen

@Composable
fun OpenCodeMobileApp() {
    Surface(modifier = Modifier.fillMaxSize()) {
        ConnectScreen()
    }
}
