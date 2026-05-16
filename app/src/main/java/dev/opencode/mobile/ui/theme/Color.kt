package dev.opencode.mobile.ui.theme

import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.ui.graphics.Color

/**
 * Adventure Island / Rakuraku design tokens.
 *
 * These values intentionally mirror the web radio frontend's CSS variables in
 * rakurakumusicstation-ng/radio-backend/frontend/src/style.css so the mobile
 * OpenCode client feels like it belongs to the same small product family.
 */
val AdventurePrimary = Color(0xFF003D99)
val AdventurePrimaryDark = Color(0xFF002A6B)
val AdventurePrimaryLight = Color(0xFF3366CC)
val AdventureSecondary = Color(0xFF00897B)
val AdventureSecondaryLight = Color(0xFF4DB6AC)
val AdventureAccent = Color(0xFFFF6F00)
val AdventureError = Color(0xFFB00020)

val AdventureDarkPrimary = Color(0xFF8AB4F8)
val AdventureDarkPrimaryVariant = Color(0xFF5E8EDB)
val AdventureDarkSecondary = Color(0xFF5FD0C3)
val AdventureDarkSecondaryVariant = Color(0xFF8FE4DC)
val AdventureDarkAccent = Color(0xFFFFB86B)
val AdventureDarkError = Color(0xFFFFB4AB)

val LightColors = lightColors(
    primary = AdventurePrimary,
    primaryVariant = AdventurePrimaryDark,
    secondary = AdventureSecondary,
    secondaryVariant = AdventureSecondaryLight,
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    error = AdventureError,
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xDE000000),
    onSurface = Color(0xDE000000),
    onError = Color(0xFFFFFFFF),
)

val DarkColors = darkColors(
    primary = AdventureDarkPrimary,
    primaryVariant = AdventureDarkPrimaryVariant,
    secondary = AdventureDarkSecondary,
    secondaryVariant = AdventureDarkSecondaryVariant,
    background = Color(0xFF101114),
    surface = Color(0xFF191B20),
    error = AdventureDarkError,
    onPrimary = Color(0xFF101114),
    onSecondary = Color(0xFF101114),
    onBackground = Color(0xEBFFFFFF),
    onSurface = Color(0xEBFFFFFF),
    onError = Color(0xFF101114),
)

val LightSurface2 = Color(0xFFF5F5F5)
val LightSurface3 = Color(0xFFEEEEEE)
val DarkSurface2 = Color(0xFF23262D)
val DarkSurface3 = Color(0xFF2D313A)
