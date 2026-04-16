package com.ainotes.studyassistant.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PrimaryOlive,
    secondary = AccentClay,
    background = CanvasBackground,
    surface = CardSurface,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onBackground = SoftText,
    onSurface = SoftText
)

private val DarkColors = darkColorScheme(
    primary = PrimaryOlive,
    secondary = AccentClay
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
