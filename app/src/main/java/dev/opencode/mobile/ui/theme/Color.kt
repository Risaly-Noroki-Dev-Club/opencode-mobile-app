package dev.opencode.mobile.ui.theme

import android.os.Build
import androidx.compose.material.Colors
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LightColors = lightColors(
    primary = Color(0xFF2C3E50),
    primaryVariant = Color(0xFF1A252F),
    secondary = Color(0xFF2980B9),
    secondaryVariant = Color(0xFF1F6391),
    background = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFC0392B),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    onError = Color(0xFFFFFFFF),
)

val DarkColors = darkColors(
    primary = Color(0xFF5DADE2),
    primaryVariant = Color(0xFF3498DB),
    secondary = Color(0xFF48C9B0),
    secondaryVariant = Color(0xFF1ABC9C),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    error = Color(0xFFE74C3C),
    onPrimary = Color(0xFF000000),
    onSecondary = Color(0xFF000000),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    onError = Color(0xFF000000),
)

@Composable
fun dynamicM2Colors(darkTheme: Boolean): Colors? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return if (darkTheme) {
        darkColors(
            primary = scheme.primary,
            primaryVariant = scheme.tertiary,
            secondary = scheme.secondary,
            secondaryVariant = scheme.secondaryContainer,
            background = scheme.background,
            surface = scheme.surface,
            error = scheme.error,
            onPrimary = scheme.onPrimary,
            onSecondary = scheme.onSecondary,
            onBackground = scheme.onBackground,
            onSurface = scheme.onSurface,
            onError = scheme.onError,
        )
    } else {
        lightColors(
            primary = scheme.primary,
            primaryVariant = scheme.tertiary,
            secondary = scheme.secondary,
            secondaryVariant = scheme.secondaryContainer,
            background = scheme.background,
            surface = scheme.surface,
            error = scheme.error,
            onPrimary = scheme.onPrimary,
            onSecondary = scheme.onSecondary,
            onBackground = scheme.onBackground,
            onSurface = scheme.onSurface,
            onError = scheme.onError,
        )
    }
}
