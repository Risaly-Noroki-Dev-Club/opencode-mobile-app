package dev.opencode.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.opencode.mobile.data.ThemeMode

val LocalAdventureColors = staticCompositionLocalOf { AdventureColors.light() }

data class AdventureColors(
    val surface2: Color,
    val surface3: Color,
    val textMedium: Color,
    val textDisabled: Color,
    val divider: Color,
    val accent: Color,
    val successContainer: Color,
    val infoContainer: Color,
    val cardContainer: Color,
    val topBar: Color,
) {
    companion object {
        fun light() = AdventureColors(
            surface2 = LightSurface2,
            surface3 = LightSurface3,
            textMedium = Color(0x99000000),
            textDisabled = Color(0x61000000),
            divider = Color(0x1F000000),
            accent = AdventureAccent,
            successContainer = AdventureSecondary.copy(alpha = 0.12f),
            infoContainer = AdventurePrimary.copy(alpha = 0.10f),
            cardContainer = Color.White,
            topBar = Color.White,
        )

        fun dark() = AdventureColors(
            surface2 = DarkSurface2,
            surface3 = DarkSurface3,
            textMedium = Color(0xADFFFFFF),
            textDisabled = Color(0x6BFFFFFF),
            divider = Color(0x24FFFFFF),
            accent = AdventureDarkAccent,
            successContainer = AdventureDarkSecondary.copy(alpha = 0.16f),
            infoContainer = AdventureDarkPrimary.copy(alpha = 0.14f),
            cardContainer = DarkSurface2,
            topBar = Color(0xFF191B20),
        )
    }
}

val MaterialTheme.adventure: AdventureColors
    @Composable get() = LocalAdventureColors.current

@Composable
fun OpenCodeMobileTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val adventureColors = if (darkTheme) AdventureColors.dark() else AdventureColors.light()
    MaterialTheme(
        colors = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
    ) {
        CompositionLocalProvider(
            LocalAdventureColors provides adventureColors,
            LocalContentColor provides MaterialTheme.colors.onSurface,
        ) {
            content()
        }
    }
}
