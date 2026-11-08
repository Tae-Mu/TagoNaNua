package com.gatita.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5E8C),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFDCE9F5),
    onSecondaryContainer = Color(0xFF0F2F47),
    errorContainer = Color(0xFFFBE0DC),
    onErrorContainer = Color(0xFF5C1109),
    tertiaryContainer = Color(0xFFFFF0C2),
    onTertiaryContainer = Color(0xFF4A3900),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC6F0),
    onPrimary = Color(0xFF00324F),
    secondaryContainer = Color(0xFF28465F),
    onSecondaryContainer = Color(0xFFDCE9F5),
    errorContainer = Color(0xFF7A2A21),
    onErrorContainer = Color(0xFFFFDAD4),
    tertiaryContainer = Color(0xFF574500),
    onTertiaryContainer = Color(0xFFFFE9A6),
)

@Composable
fun GatitaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
