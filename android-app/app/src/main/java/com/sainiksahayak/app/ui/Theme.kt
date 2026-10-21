package com.sainiksahayak.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Army olive + brass. Deliberately not the default purple M3 palette.
private val Olive = Color(0xFF1B3A2F)
private val Brass = Color(0xFFF2C14E)

private val LightColors = lightColorScheme(
    primary = Olive,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9DF),
    onPrimaryContainer = Color(0xFF0B231A),
    secondary = Color(0xFF5C6350),
    background = Color(0xFFF7F8F4),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6E9E0),
    onSurface = Color(0xFF1A1C19),
    onSurfaceVariant = Color(0xFF44483F),
    tertiary = Brass,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC7A6),
    onPrimary = Color(0xFF10281D),
    primaryContainer = Color(0xFF2A4A38),
    onPrimaryContainer = Color(0xFFD6EBDC),
    secondary = Color(0xFFC4C8B6),
    background = Color(0xFF11140F),
    surface = Color(0xFF1A1E18),
    surfaceVariant = Color(0xFF3F443B),
    onSurface = Color(0xFFE3E4DC),
    onSurfaceVariant = Color(0xFFC4C8BB),
    tertiary = Brass,
)

@Composable
fun SainikTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
