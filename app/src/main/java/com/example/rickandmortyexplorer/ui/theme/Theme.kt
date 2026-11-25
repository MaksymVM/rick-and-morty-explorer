package com.example.rickandmortyexplorer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class PortalExtraColors(
    val statusAlive: Color,
    val statusAliveDot: Color,
    val statusDead: Color,
    val statusDeadDot: Color,
    val statusUnknown: Color,
    val onStatusBadge: Color,
    val bgGlow: Color,
    val bgLime: Color,
    val heroTintAlpha: Float,
    val isDark: Boolean
)

private val LightPortalExtraColors = PortalExtraColors(
    statusAlive = PortalLightStatusAlive,
    statusAliveDot = PortalLightStatusAliveDot,
    statusDead = PortalLightStatusDead,
    statusDeadDot = PortalLightStatusDeadDot,
    statusUnknown = PortalStatusUnknown,
    onStatusBadge = PortalLightOnPrimary,
    bgGlow = PortalBackdropGlow,
    bgLime = PortalLightTertiary,
    heroTintAlpha = 0.35f,
    isDark = false
)

private val DarkPortalExtraColors = PortalExtraColors(
    statusAlive = PortalDarkStatusAlive,
    statusAliveDot = PortalDarkStatusAliveDot,
    statusDead = PortalDarkStatusDead,
    statusDeadDot = PortalDarkStatusDeadDot,
    statusUnknown = PortalStatusUnknown,
    onStatusBadge = PortalLightOnPrimary,
    bgGlow = PortalBackdropGlow,
    bgLime = PortalDarkTertiary,
    heroTintAlpha = 0.55f,
    isDark = true
)

val LocalPortalColors = staticCompositionLocalOf { LightPortalExtraColors }

private val DarkColorScheme = darkColorScheme(
    primary = PortalDarkPrimary,
    onPrimary = PortalDarkOnPrimary,
    primaryContainer = PortalDarkPrimaryContainer,
    onPrimaryContainer = PortalDarkOnPrimaryContainer,
    secondary = PortalDarkSecondary,
    onSecondary = PortalDarkOnSecondary,
    secondaryContainer = PortalDarkSecondaryContainer,
    onSecondaryContainer = PortalDarkOnSecondaryContainer,
    tertiary = PortalDarkTertiary,
    background = PortalDarkBackground,
    onBackground = PortalDarkOnSurface,
    surface = PortalDarkSurface,
    onSurface = PortalDarkOnSurface,
    surfaceVariant = PortalDarkSurfaceVariant,
    onSurfaceVariant = PortalDarkOnSurfaceVariant,
    surfaceContainerLowest = PortalDarkBackground,
    surfaceContainerLow = PortalDarkSurface,
    surfaceContainer = PortalDarkSurface,
    surfaceContainerHigh = PortalDarkSurfaceVariant,
    surfaceContainerHighest = PortalDarkSurfaceVariant,
    outline = PortalDarkOutline,
    outlineVariant = PortalDarkOutlineVariant,
    error = PortalDarkError,
    onError = PortalDarkOnPrimary,
    errorContainer = PortalDarkErrorContainer,
    onErrorContainer = PortalDarkOnErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = PortalLightPrimary,
    onPrimary = PortalLightOnPrimary,
    primaryContainer = PortalLightPrimaryContainer,
    onPrimaryContainer = PortalLightOnPrimaryContainer,
    secondary = PortalLightSecondary,
    onSecondary = PortalLightOnSecondary,
    secondaryContainer = PortalLightSecondaryContainer,
    onSecondaryContainer = PortalLightOnSecondaryContainer,
    tertiary = PortalLightTertiary,
    background = PortalLightBackground,
    onBackground = PortalLightOnSurface,
    surface = PortalLightSurface,
    onSurface = PortalLightOnSurface,
    surfaceVariant = PortalLightSurfaceVariant,
    onSurfaceVariant = PortalLightOnSurfaceVariant,
    surfaceContainerLowest = PortalLightSurface,
    surfaceContainerLow = PortalLightBackground,
    surfaceContainer = PortalLightSurface,
    surfaceContainerHigh = PortalLightSurfaceVariant,
    surfaceContainerHighest = PortalLightSurfaceVariant,
    outline = PortalLightOutline,
    outlineVariant = PortalLightOutlineVariant,
    error = PortalLightError,
    onError = PortalLightOnPrimary,
    errorContainer = PortalLightErrorContainer,
    onErrorContainer = PortalLightOnErrorContainer
)

@Composable
fun RickAndMortyExplorerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extraColors = if (darkTheme) DarkPortalExtraColors else LightPortalExtraColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalPortalColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
