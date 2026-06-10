package com.mh.librarymanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3FA),
    onPrimaryContainer = BlueTertiary,
    secondary = BlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFEAF8),
    onSecondaryContainer = BlueTertiary,
    tertiary = BlueTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC9D8F1),
    onTertiaryContainer = BlueTertiary,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = Color(0xFFF4F6F9),
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = Color(0xFFC8D0DC),
    error = ErrorRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = Color(0xFF002F69),
    primaryContainer = Color(0xFF154488),
    onPrimaryContainer = Color(0xFFD7E3FA),
    secondary = BlueSecondaryDark,
    onSecondary = Color(0xFF002F69),
    secondaryContainer = Color(0xFF2A589A),
    onSecondaryContainer = Color(0xFFDFEAF8),
    tertiary = BlueTertiaryDark,
    onTertiary = Color(0xFF112648),
    tertiaryContainer = Color(0xFF294473),
    onTertiaryContainer = Color(0xFFD7E3FA),
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = Color(0xFF131C2F),
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF2B3A57),
    error = ErrorRedDark,
    onError = Color(0xFF690004),
)

@Composable
fun LibraryManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography,
            content = content,
        )
    }
}
