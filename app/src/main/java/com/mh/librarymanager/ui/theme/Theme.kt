package com.mh.librarymanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * App-wide theme. The Material colour scheme is derived from the management-
 * selected palette held in [AppThemeState]; because that palette is snapshot-
 * backed, choosing a new theme re-derives the scheme and re-skins the whole app
 * instantly. Layout direction is forced RTL for the Hebrew UI.
 */
@Composable
fun LibraryManagerTheme(
    content: @Composable () -> Unit,
) {
    val colors = AppThemeState.palette.toColorScheme()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography,
            content = content,
        )
    }
}
