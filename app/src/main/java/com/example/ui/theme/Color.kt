package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// MOTO ODO — SWISS / MINIMALIST AUTOMOTIVE COLOR SYSTEM
// Primary Visual Identity: WHITE, RED, GREEN, BLUE
// ============================================================================

// LIGHT MODE PALETTE
val LightBackground = Color(0xFFF6F7F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFF1F3F7)
val LightTextPrimary = Color(0xFF171A21)
val LightTextSecondary = Color(0xFF737B88)
val LightBorder = Color(0xFFE7EAF0)

val LightGreen = Color(0xFF16A36A) // Tracking & Success
val LightRed = Color(0xFFE5484D)   // Warning & Important/Destructive Actions
val LightBlue = Color(0xFF3978D5)  // Information & Statistics

// DARK MODE PALETTE
val DarkBackground = Color(0xFF101216)
val DarkSurface = Color(0xFF191E26)
val DarkSurfaceElevated = Color(0xFF202733)
val DarkTextPrimary = Color(0xFFF5F7FA)
val DarkTextSecondary = Color(0xFFA0A8B5)
val DarkBorder = Color(0xFF2A3340)

val DarkGreen = Color(0xFF20B978)  // Tracking & Success
val DarkRed = Color(0xFFF16B70)    // Warning & Important/Destructive Actions
val DarkBlue = Color(0xFF65A0F0)   // Information & Statistics

// Compatibility tokens mapped to the clean palette
val CarbonBlack = DarkBackground
val CockpitDarkBg = DarkBackground
val PanelDarkGray = DarkSurface
val PanelElevatedGray = DarkSurfaceElevated
val PanelBorderGray = DarkBorder
val NeonGreen = DarkGreen
val CyberCyan = DarkBlue
val AmberWarning = LightRed
val CrimsonRed = DarkRed
val TextPrimaryWhite = DarkTextPrimary
val TextSecondarySilver = DarkTextSecondary
val TextMutedSlate = LightTextSecondary
