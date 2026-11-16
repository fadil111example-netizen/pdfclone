package com.lumina.pdfai.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    primary = Violet400,
    onPrimary = Color(0xFF150E33),
    primaryContainer = Violet600,
    onPrimaryContainer = Color(0xFFEDE9FF),
    secondary = Ember400,
    onSecondary = Color(0xFF33150A),
    secondaryContainer = Color(0xFF7A3A1C),
    onSecondaryContainer = Color(0xFFFFE6D8),
    tertiary = Mint400,
    onTertiary = Color(0xFF04241C),
    background = Ink900,
    onBackground = Color(0xFFE9EDFA),
    surface = Ink850,
    onSurface = Color(0xFFE9EDFA),
    surfaceVariant = Ink700,
    onSurfaceVariant = Color(0xFFB6BFDB),
    surfaceContainer = Ink800,
    surfaceContainerHigh = Ink700,
    surfaceContainerHighest = Ink600,
    outline = Color(0xFF46527A),
    outlineVariant = Color(0xFF2C3555),
    error = Rose400,
    onError = Color(0xFF3A0716)
)

private val LightScheme = lightColorScheme(
    primary = Violet600,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E0FF),
    onPrimaryContainer = Color(0xFF1B0C56),
    secondary = Color(0xFFD9571F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2D3),
    onSecondaryContainer = Color(0xFF3A1405),
    tertiary = Color(0xFF00896A),
    onTertiary = Color.White,
    background = Mist50,
    onBackground = Color(0xFF111528),
    surface = Color.White,
    onSurface = Color(0xFF111528),
    surfaceVariant = Mist100,
    onSurfaceVariant = Slate500,
    surfaceContainer = Color(0xFFF2F4FB),
    surfaceContainerHigh = Color(0xFFEAEDF7),
    surfaceContainerHighest = Mist200,
    outline = Color(0xFFBFC6DA),
    outlineVariant = Mist200,
    error = Color(0xFFD2294A),
    onError = Color.White
)

@Composable
fun LuminaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = LuminaTypography,
        content = content
    )
}
