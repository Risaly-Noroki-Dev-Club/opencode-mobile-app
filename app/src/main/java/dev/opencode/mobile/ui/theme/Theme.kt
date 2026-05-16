package dev.opencode.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun OpenCodeMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = dynamicM2Colors(darkTheme)
        ?: if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colors = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
