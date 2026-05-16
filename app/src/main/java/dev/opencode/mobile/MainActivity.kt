package dev.opencode.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.opencode.mobile.data.SettingsStore
import dev.opencode.mobile.data.ThemeMode
import dev.opencode.mobile.ui.OpenCodeMobileApp
import dev.opencode.mobile.ui.theme.OpenCodeMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsStore = remember { SettingsStore(this@MainActivity) }
            val themeMode by settingsStore.themeMode.collectAsState(initial = ThemeMode.System)
            OpenCodeMobileTheme(themeMode = themeMode) {
                OpenCodeMobileApp()
            }
        }
    }
}
