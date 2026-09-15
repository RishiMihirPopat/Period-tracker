package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun cycleColorScheme(
    darkTheme: Boolean,
    accent: AppAccent = AppAccent.BROWN
): ColorScheme {
    val accentColor = accent.color(darkTheme)
    val onAccent = Color.White

    return if (darkTheme) {
        darkColorScheme(
            primary = accentColor,
            onPrimary = onAccent,
            primaryContainer = DarkSecondary,
            onPrimaryContainer = accentColor,
            secondary = DarkSecondary,
            onSecondary = DarkSecondaryText,
            secondaryContainer = DarkSecondary,
            onSecondaryContainer = DarkSecondaryText,
            tertiary = accentColor,
            onTertiary = onAccent,
            background = DarkBackground,
            onBackground = DarkForeground,
            surface = DarkCard,
            onSurface = DarkForeground,
            surfaceVariant = DarkSecondary,
            onSurfaceVariant = DarkMutedText,
            surfaceContainer = DarkSecondary,
            surfaceContainerHigh = DarkSecondary,
            surfaceContainerHighest = DarkSecondary,
            outline = DarkBorder,
            outlineVariant = DarkBorder
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            onPrimary = onAccent,
            primaryContainer = LightSecondary,
            onPrimaryContainer = accentColor,
            secondary = LightSecondary,
            onSecondary = LightSecondaryText,
            secondaryContainer = LightSecondary,
            onSecondaryContainer = LightSecondaryText,
            tertiary = accentColor,
            onTertiary = onAccent,
            background = LightBackground,
            onBackground = LightForeground,
            surface = LightCard,
            onSurface = LightForeground,
            surfaceVariant = LightSecondary,
            onSurfaceVariant = LightMutedText,
            surfaceContainer = LightSecondary,
            surfaceContainerHigh = LightSecondary,
            surfaceContainerHighest = LightSecondary,
            outline = LightBorder,
            outlineVariant = LightBorder
        )
    }
}

@Composable
fun CycleTheme(
    accentColor: String? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val accent = AppAccent.fromId(accentColor)
    val colorScheme = cycleColorScheme(darkTheme = darkTheme, accent = accent)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun AuraCycleTheme(
    accentColor: String? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CycleTheme(accentColor = accentColor, darkTheme = darkTheme, content = content)
}
