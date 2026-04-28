package com.gsmtrick.musicplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private fun accentSchemeDark(accent: String) = when (accent) {
    "blue" -> darkColorScheme(primary = Color(0xFF6FA8FF), secondary = Color(0xFF82CFFF), tertiary = Color(0xFFB7C5FF))
    "green" -> darkColorScheme(primary = Color(0xFF7FE0A0), secondary = Color(0xFF9FE5C2), tertiary = Color(0xFFB6F0CE))
    "orange" -> darkColorScheme(primary = Color(0xFFFFB077), secondary = Color(0xFFFFC79A), tertiary = Color(0xFFFFD8B5))
    "pink" -> darkColorScheme(primary = Color(0xFFFF8FB1), secondary = Color(0xFFFFA8C2), tertiary = Color(0xFFFFC1D2))
    else -> darkColorScheme(primary = Color(0xFFB69CFF), secondary = Color(0xFFCAB6FF), tertiary = Color(0xFFE0CFFF))
}

private fun accentSchemeLight(accent: String) = when (accent) {
    "blue" -> lightColorScheme(primary = Color(0xFF1763D6), secondary = Color(0xFF0080B5), tertiary = Color(0xFF44558F))
    "green" -> lightColorScheme(primary = Color(0xFF1F7A45), secondary = Color(0xFF21825F), tertiary = Color(0xFF3F7A5C))
    "orange" -> lightColorScheme(primary = Color(0xFFB55815), secondary = Color(0xFF8E531C), tertiary = Color(0xFF7F5A36))
    "pink" -> lightColorScheme(primary = Color(0xFFB13363), secondary = Color(0xFF8C476A), tertiary = Color(0xFF7C5267))
    else -> lightColorScheme(primary = Color(0xFF6750A4), secondary = Color(0xFF625B71), tertiary = Color(0xFF7D5260))
}

@Composable
fun ModernMusicTheme(
    themeMode: String,
    dynamicColor: Boolean,
    accent: String,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val canDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val colors = when {
        dynamicColor && canDynamic && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && canDynamic && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> accentSchemeDark(accent)
        else -> accentSchemeLight(accent)
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
