package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.model.ThemeMode

// Primary = GREEN (Tracking & Success)
// Secondary = BLUE (Information, Trip, & Statistics)
// Error / Tertiary = RED (Warnings, GPS Error, Important/Destructive Actions)
private val MotoOdoLightColorScheme = lightColorScheme(
    primary = LightGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F6EF),
    onPrimaryContainer = Color(0xFF0D6842),
    secondary = LightBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBF2FC),
    onSecondaryContainer = Color(0xFF1E4E96),
    tertiary = LightRed,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = LightRed,
    onError = Color.White
)

private val MotoOdoDarkColorScheme = darkColorScheme(
    primary = DarkGreen,
    onPrimary = Color(0xFF07291A),
    primaryContainer = Color(0xFF143527),
    onPrimaryContainer = DarkGreen,
    secondary = DarkBlue,
    onSecondary = Color(0xFF0A2246),
    secondaryContainer = Color(0xFF1B2D48),
    onSecondaryContainer = DarkBlue,
    tertiary = DarkRed,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = DarkRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) MotoOdoDarkColorScheme else MotoOdoLightColorScheme,
        typography = Typography,
        content = content
    )
}
