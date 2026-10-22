package com.sainiksahayak.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Matches static/styles.css — dark ops console + accent green.
val Bg = Color(0xFF0B1118)
val AccentGreen = Color(0xFF4ADE80)
val TextSecondary = Color(0xFF8B9EB3)
val CardBg = Color(0x99121B27)
val InputBorder = Color(0x4D4ADE80)
val SurfaceGlass = Color(0x14FFFFFF)

private val Colors = darkColorScheme(
    primary = AccentGreen,
    onPrimary = Color(0xFF0B1118),
    primaryContainer = Color(0xFF163024),
    onPrimaryContainer = AccentGreen,
    secondary = TextSecondary,
    onSecondary = Color.White,
    background = Bg,
    onBackground = Color.White,
    surface = Color(0xFF121B27),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A2433),
    onSurfaceVariant = TextSecondary,
    outline = Color(0x26FFFFFF),
    error = Color(0xFFF87171),
    onError = Color(0xFF0B1118),
    errorContainer = Color(0xFF3F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    tertiary = AccentGreen,
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = 1.5.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
    ),
)

@Composable
fun SainikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = AppTypography,
        content = content,
    )
}
