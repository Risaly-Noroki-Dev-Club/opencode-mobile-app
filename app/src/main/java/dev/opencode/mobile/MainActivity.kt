package dev.opencode.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.opencode.mobile.ui.OpenCodeMobileApp
import dev.opencode.mobile.ui.theme.OpenCodeMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenCodeMobileTheme {
                OpenCodeMobileApp()
            }
        }
    }
}
