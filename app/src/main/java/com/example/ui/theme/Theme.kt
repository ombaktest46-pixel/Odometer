package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MotoOdoColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = CarbonBlack,
    primaryContainer = Color(0xFF07361C),
    onPrimaryContainer = NeonGreen,
    secondary = CyberCyan,
    onSecondary = CarbonBlack,
    secondaryContainer = Color(0xFF06313B),
    onSecondaryContainer = CyberCyan,
    tertiary = AmberWarning,
    onTertiary = CarbonBlack,
    background = CarbonBlack,
    onBackground = TextPrimaryWhite,
    surface = PanelDarkGray,
    onSurface = TextPrimaryWhite,
    surfaceVariant = PanelElevatedGray,
    onSurfaceVariant = TextSecondarySilver,
    outline = PanelBorderGray,
    error = CrimsonRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MotoOdoColorScheme,
        typography = Typography,
        content = content
    )
}
