package com.glimmer.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class NeumorphicShadows(
    val lightShadow: Color,
    val darkShadow: Color
)

// The app is dark-only (dynamicColor is always false below) and these two colors never change at
// runtime, so this is a fixed constant, not per-composition state — allocating a fresh
// NeumorphicShadows on every MyApplicationTheme recomposition (as the old `val shadows = ...`
// inside the composable did) was pure churn. Hoisting it here also lets LocalNeumorphicShadows use
// staticCompositionLocalOf: compositionLocalOf sets up per-read invalidation tracking for a value
// that Compose otherwise has no reason to think can change, which is wasted overhead on every one
// of the many `.neumorphic(...)` call sites across the app that read it (PERF-01).
private val AppNeumorphicShadows = NeumorphicShadows(Color(0x26FFFFFF), Color(0x99000000))

val LocalNeumorphicShadows = staticCompositionLocalOf { AppNeumorphicShadows }

private val DarkColorScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceContainerHighestDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
)

@Composable
fun MyApplicationTheme(
    // Dynamic color is disabled to enforce Glimmer theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    // Status bar styling is NOT done here via window.statusBarColor — that setter is deprecated
    // and silently ignored once targetSdk reaches 35 (edge-to-edge is enforced app-wide from
    // API 35, and enableEdgeToEdge() is already called in MainActivity). The equivalent, working
    // approach is to pass the intended SystemBarStyle to enableEdgeToEdge() itself, in
    // MainActivity.onCreate, before setContent — see MainActivity.kt.

    CompositionLocalProvider(
        LocalNeumorphicShadows provides AppNeumorphicShadows
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
