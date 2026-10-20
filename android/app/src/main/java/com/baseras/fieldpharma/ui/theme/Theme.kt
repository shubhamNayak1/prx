package com.baseras.fieldpharma.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Day — clinical teal on near-white slate, subtle warm orange for calls to action.
private val LightColors = lightColorScheme(
    primary          = Teal700,
    onPrimary        = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Teal900,

    secondary        = Orange500,
    onSecondary      = Color.White,
    secondaryContainer = Orange100,
    onSecondaryContainer = Orange600,

    tertiary         = Teal600,
    onTertiary       = Color.White,

    background       = Slate50,
    onBackground     = Slate900,
    surface          = Color.White,
    onSurface        = Slate900,
    surfaceVariant   = Slate100,
    onSurfaceVariant = Slate600,
    surfaceContainer = Slate100,
    surfaceContainerHigh = Color.White,

    outline          = Slate200,
    outlineVariant   = Slate100,

    error            = Error600,
    onError          = Color.White,
    errorContainer   = Error50,
    onErrorContainer = Error700,
)

// Night — flipped lightness, slightly desaturated for OLED comfort.
private val DarkColors = darkColorScheme(
    primary          = Teal400,
    onPrimary        = Teal900,
    primaryContainer = Teal800,
    onPrimaryContainer = Teal100,

    secondary        = Orange500,
    onSecondary      = Color.White,
    secondaryContainer = Color(0xFF422006),
    onSecondaryContainer = Orange100,

    tertiary         = Teal400,
    onTertiary       = Teal900,

    background       = Slate950,
    onBackground     = Slate100,
    surface          = Slate900,
    onSurface        = Slate100,
    surfaceVariant   = Slate800,
    onSurfaceVariant = Slate300,
    surfaceContainer = Slate800,
    surfaceContainerHigh = Slate700,

    outline          = Slate700,
    outlineVariant   = Slate800,

    error            = Error600,
    onError          = Color.White,
    errorContainer   = Color(0xFF450A0A),
    onErrorContainer = Error50,
)

@Composable
fun FieldPharmaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
