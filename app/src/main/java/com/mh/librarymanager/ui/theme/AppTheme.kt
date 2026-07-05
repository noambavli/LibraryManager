package com.mh.librarymanager.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * A full colour palette for the app. Every colour the UI paints — backgrounds,
 * panels, text, accents, hero gradients — is sourced from the active palette so
 * a single selection re-skins the whole app. `AppColors` (in the components
 * layer) reads these values, and [LibraryManagerTheme] derives the Material
 * [ColorScheme] from them, so both design systems follow the chosen theme.
 */
data class AppPalette(
    val bgTop: Color,
    val bgBottom: Color,
    val panel: Color,
    val panelElevated: Color,
    val border: Color,
    val borderLight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentMuted: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val heroSubtitle: Color,
    val divider: Color,
    val warning: Color,
) {
    /** Builds a Material 3 light colour scheme anchored on this palette. */
    fun toColorScheme(): ColorScheme = lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = lerp(accent, Color.White, 0.80f),
        onPrimaryContainer = textPrimary,
        secondary = heroStart,
        onSecondary = Color.White,
        secondaryContainer = lerp(accent, Color.White, 0.85f),
        onSecondaryContainer = textPrimary,
        tertiary = accent,
        onTertiary = Color.White,
        tertiaryContainer = lerp(accent, Color.White, 0.82f),
        onTertiaryContainer = textPrimary,
        background = bgTop,
        onBackground = textPrimary,
        surface = panelElevated,
        onSurface = textPrimary,
        surfaceVariant = panel,
        onSurfaceVariant = textSecondary,
        outline = border,
        outlineVariant = borderLight,
        error = Color(0xFFB3261E),
        onError = Color.White,
        inverseSurface = textPrimary,
        inverseOnSurface = panelElevated,
    )
}

/** A named, selectable theme shown in the management dashboard. */
data class AppThemeOption(
    val id: String,
    val name: String,
    val palette: AppPalette,
)

object AppTheme {

    const val DEFAULT_ID = "blue"

    private val Blue = AppPalette(
        bgTop = Color(0xFFDDE3EC),
        bgBottom = Color(0xFFD0D8E4),
        panel = Color(0xFFF4F6F9),
        panelElevated = Color.White,
        border = Color(0xFFC8D0DC),
        borderLight = Color(0xFFD8DEE8),
        textPrimary = Color(0xFF1C2838),
        textSecondary = Color(0xFF5A6578),
        textMuted = Color(0xFF6B7789),
        accent = Color(0xFF4A7BB7),
        accentMuted = Color(0xFF9AA8BA),
        heroStart = Color(0xFF1A3354),
        heroEnd = Color(0xFF243F66),
        heroSubtitle = Color(0xFFB8C9DE),
        divider = Color(0xFFDCE1E8),
        warning = Color(0xFFB45309),
    )

    private val Yellow = AppPalette(
        bgTop = Color(0xFFFBF4DB),
        bgBottom = Color(0xFFF4E7BE),
        panel = Color(0xFFFFFBF0),
        panelElevated = Color.White,
        border = Color(0xFFE7D9AC),
        borderLight = Color(0xFFF0E7C9),
        textPrimary = Color(0xFF3A2F0B),
        textSecondary = Color(0xFF6E5D2C),
        textMuted = Color(0xFF877441),
        accent = Color(0xFFBE9114),
        accentMuted = Color(0xFFCBBE8E),
        heroStart = Color(0xFF6E5410),
        heroEnd = Color(0xFF937518),
        heroSubtitle = Color(0xFFEBDCA6),
        divider = Color(0xFFEDE3C6),
        warning = Color(0xFFB45309),
    )

    private val Green = AppPalette(
        bgTop = Color(0xFFDDEDE0),
        bgBottom = Color(0xFFCCE2D2),
        panel = Color(0xFFF1F8F3),
        panelElevated = Color.White,
        border = Color(0xFFBFD8C6),
        borderLight = Color(0xFFD5E7DB),
        textPrimary = Color(0xFF16281C),
        textSecondary = Color(0xFF48604F),
        textMuted = Color(0xFF5C7263),
        accent = Color(0xFF2E8B57),
        accentMuted = Color(0xFF9DBCA8),
        heroStart = Color(0xFF15412A),
        heroEnd = Color(0xFF1E5537),
        heroSubtitle = Color(0xFFB9D8C4),
        divider = Color(0xFFD8E6DC),
        warning = Color(0xFFB45309),
    )

    private val Terracotta = AppPalette(
        bgTop = Color(0xFFF3E4DC),
        bgBottom = Color(0xFFEAD3C6),
        panel = Color(0xFFFCF5F0),
        panelElevated = Color.White,
        border = Color(0xFFDEC5B5),
        borderLight = Color(0xFFECDBCF),
        textPrimary = Color(0xFF33221A),
        textSecondary = Color(0xFF6B4E40),
        textMuted = Color(0xFF836253),
        accent = Color(0xFFB2603A),
        accentMuted = Color(0xFFC9AA9B),
        heroStart = Color(0xFF5C2E1A),
        heroEnd = Color(0xFF7A3D22),
        heroSubtitle = Color(0xFFE4C7B8),
        divider = Color(0xFFEBD9CE),
        warning = Color(0xFFB45309),
    )

    private val Graphite = AppPalette(
        bgTop = Color(0xFFE9ECEF),
        bgBottom = Color(0xFFDDE1E6),
        panel = Color(0xFFF6F7F9),
        panelElevated = Color.White,
        border = Color(0xFFCED3DA),
        borderLight = Color(0xFFDFE3E8),
        textPrimary = Color(0xFF20262E),
        textSecondary = Color(0xFF565E68),
        textMuted = Color(0xFF6B747E),
        accent = Color(0xFF5B6B7B),
        accentMuted = Color(0xFFAAB2BB),
        heroStart = Color(0xFF2C333B),
        heroEnd = Color(0xFF3D454E),
        heroSubtitle = Color(0xFFC4CBD3),
        divider = Color(0xFFDFE3E8),
        warning = Color(0xFFB45309),
    )

    /** Order here is the order shown in the dashboard. */
    val themes: List<AppThemeOption> = listOf(
        AppThemeOption("blue", "כחול קלאסי", Blue),
        AppThemeOption("yellow", "צהוב חמים", Yellow),
        AppThemeOption("green", "ירוק רגוע", Green),
        AppThemeOption("terracotta", "חום אדמה", Terracotta),
        AppThemeOption("graphite", "אפור נקי", Graphite),
    )

    fun byId(id: String?): AppThemeOption =
        themes.firstOrNull { it.id == id } ?: themes.first()

    fun paletteFor(id: String?): AppPalette = byId(id).palette
}

/**
 * Process-global, snapshot-backed holder of the active palette. Because the
 * backing is a [mutableStateOf], any composable that reads a colour (directly or
 * through `AppColors`) is re-invoked the instant the palette changes — a theme
 * switch re-skins the whole app with no restart and no stale colours.
 */
object AppThemeState {
    var palette: AppPalette by mutableStateOf(AppTheme.byId(AppTheme.DEFAULT_ID).palette)
}
