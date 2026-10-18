package com.appprogress.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val title: String) { System("Как в системе"), Light("Светлая"), Dark("Тёмная") }

private val Light = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E2FF),
    onPrimaryContainer = Color(0xFF1E1A6B),
    secondary = Color(0xFF0E9F8E),
    background = Color(0xFFF6F6FA),
    onBackground = Color(0xFF1B1B21),
    surface = Color.White,
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFEDEDF4),
    onSurfaceVariant = Color(0xFF5D5E6B),
    outlineVariant = Color(0xFFDCDCE6),
    error = Color(0xFFD93A3A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA5A0FF),
    onPrimary = Color(0xFF221C7A),
    primaryContainer = Color(0xFF353090),
    onPrimaryContainer = Color(0xFFE4E2FF),
    secondary = Color(0xFF5ED6C4),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE5E4EC),
    surface = Color(0xFF1A1B21),
    onSurface = Color(0xFFE5E4EC),
    surfaceVariant = Color(0xFF25262E),
    onSurfaceVariant = Color(0xFFA9A9B8),
    outlineVariant = Color(0xFF34353F),
    error = Color(0xFFFF8A80),
)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
