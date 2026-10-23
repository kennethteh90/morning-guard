package com.morningguard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Forest = Color(0xFF1B4332)
private val ForestLight = Color(0xFF2D6A4F)
private val Mint = Color(0xFF95D5B2)
private val Cream = Color(0xFFF8F9FA)
private val Dawn = Color(0xFFD8F3DC)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Dawn,
    onPrimaryContainer = Forest,
    secondary = ForestLight,
    onSecondary = Color.White,
    tertiary = Mint,
    background = Cream,
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Dawn,
    onSurfaceVariant = Color(0xFF414941),
)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Forest,
    primaryContainer = ForestLight,
    onPrimaryContainer = Dawn,
    secondary = Mint,
    onSecondary = Forest,
    background = Color(0xFF101412),
    onBackground = Dawn,
    surface = Color(0xFF161A18),
    onSurface = Dawn,
    surfaceVariant = Color(0xFF243028),
    onSurfaceVariant = Color(0xFFBFC9BF),
)

@Composable
fun MorningGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = MorningGuardTypography,
        content = content,
    )
}
