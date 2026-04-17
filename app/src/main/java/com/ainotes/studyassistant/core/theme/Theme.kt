package com.ainotes.studyassistant.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PrimaryOlive,
    secondary = AccentClay,
    background = CanvasBackground,
    surface = CardSurface,
    onPrimary = Color.White,
    onBackground = SoftText,
    onSurface = SoftText,
    surfaceVariant = Color(0xFFF1EBDD)
)

private val DarkColors = darkColorScheme(
    primary = PrimaryOlive,
    secondary = AccentClay,
    background = Color(0xFF141917),
    surface = Color(0xFF1B2220),
    onBackground = Color(0xFFE7ECE8),
    onSurface = Color(0xFFE7ECE8),
    surfaceVariant = Color(0xFF27312E)
)

@Composable
fun StudyAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
