package com.github.caiheyu.keybook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = KeyBookGreenDark,
    onPrimary = DarkBackground,
    primaryContainer = ColorTokens.darkTint,
    onPrimaryContainer = DarkInk,
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkBackground,
    onSurface = DarkInk,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkMuted,
    outline = DarkLine,
    error = ErrorDark,
)

private val LightColorScheme = lightColorScheme(
    primary = KeyBookGreen,
    onPrimary = LightBackground,
    primaryContainer = ColorTokens.lightTint,
    onPrimaryContainer = LightInk,
    background = LightBackground,
    onBackground = LightInk,
    surface = LightBackground,
    onSurface = LightInk,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightMuted,
    outline = LightLine,
    error = ErrorLight,
)

private object ColorTokens {
    val lightTint = androidx.compose.ui.graphics.Color(0xFFE0F0E8)
    val darkTint = androidx.compose.ui.graphics.Color(0xFF254A3D)
}

@Composable
fun KeyBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
