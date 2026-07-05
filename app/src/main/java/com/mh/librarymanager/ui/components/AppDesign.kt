package com.mh.librarymanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.mh.librarymanager.ui.text.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R
import com.mh.librarymanager.ui.theme.AppThemeState

/**
 * Shared palette for the tablet UI. Every colour is sourced from the active
 * [AppThemeState] palette, so reading `AppColors.X` inside a composable makes
 * that composable follow the selected theme automatically (the palette is
 * snapshot-backed, so a theme change recomposes every reader). The values are
 * plain getters, so non-composable callers keep working too.
 */
object AppColors {
    private inline val p get() = AppThemeState.palette

    val BgTop: Color get() = p.bgTop
    val BgBottom: Color get() = p.bgBottom
    val Panel: Color get() = p.panel
    val PanelElevated: Color get() = p.panelElevated
    val Border: Color get() = p.border
    val BorderLight: Color get() = p.borderLight
    val TextPrimary: Color get() = p.textPrimary
    val TextSecondary: Color get() = p.textSecondary
    val TextMuted: Color get() = p.textMuted
    val Accent: Color get() = p.accent
    val AccentMuted: Color get() = p.accentMuted
    val HeroStart: Color get() = p.heroStart
    val HeroEnd: Color get() = p.heroEnd
    val HeroSubtitle: Color get() = p.heroSubtitle
    val Divider: Color get() = p.divider
    val Warning: Color get() = p.warning
}

@Composable
fun AppScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to AppColors.BgTop,
                    1f to AppColors.BgBottom,
                ),
            ),
    ) {
        content()
    }
}

@Composable
fun AppLoadingContent(
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.results_loading),
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextMuted,
            )
        }
    }
}

/** Library emblem used across public and management screens. */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    Image(
        painter = painterResource(R.drawable.hm_logo),
        contentDescription = stringResource(R.string.app_logo_content_description),
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

/** Title block with the library logo beside the heading. */
@Composable
fun AppBrandHeader(
    title: String,
    subtitle: String? = null,
    logoSize: Dp = 80.dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo(size = logoSize)
        Spacer(modifier = Modifier.width(24.dp))
        AppScreenTitle(
            text = title,
            subtitle = subtitle,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Top bar for public screens (search, requests, announcements). */
@Composable
fun PublicBackBar(
    onBack: () -> Unit,
    title: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppColors.Panel,
        shadowElevation = 1.dp,
        border = BorderStroke(0.dp, Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "‹  " + stringResource(R.string.back),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (!title.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(20.dp))
            AppLogo(size = 40.dp)
        }
    }
}

/** Header for gated management screens. */
@Composable
fun ManagementHeader(
    title: String,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    primaryAction: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.Panel,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "‹  " + stringResource(R.string.back),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (primaryAction != null && onPrimaryAction != null) {
                Button(
                    onClick = onPrimaryAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.HeroStart,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("+  $primaryAction", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            OutlinedButton(
                onClick = onLogout,
                border = BorderStroke(1.dp, AppColors.Border),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    stringResource(R.string.logout),
                    color = AppColors.TextSecondary,
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            AppLogo(size = 40.dp)
        }
    }
}

/** Dashboard / home compact navigation tile. */
@Composable
fun AppActionTile(
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = AppColors.Panel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AppColors.Border),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .background(accent.copy(alpha = 0.85f), RoundedCornerShape(2.dp)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (badgeCount > 0) {
                        NotificationBadge(count = badgeCount)
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "‹",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.AccentMuted,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun NotificationBadge(count: Int) {
    val label = if (count > 99) "99+" else count.toString()
    Surface(
        color = MaterialTheme.colorScheme.error,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onError,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Primary hero CTA (search on home). */
@Composable
fun AppHeroButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        0f to AppColors.HeroStart,
                        1f to AppColors.HeroEnd,
                    ),
                )
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(44.dp)
                        .background(AppColors.Accent, RoundedCornerShape(2.dp)),
                )
                Spacer(modifier = Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.HeroSubtitle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "‹",
                    style = MaterialTheme.typography.headlineMedium,
                    color = AppColors.HeroSubtitle,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun AppSectionPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppColors.Panel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppColors.Border),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun AppPaneDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(1.dp)
            .fillMaxSize()
            .background(AppColors.Divider),
    )
}

@Composable
fun AppHorizontalDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = AppColors.Divider)
}

@Composable
fun AppContentCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = AppColors.PanelElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AppColors.BorderLight),
        shadowElevation = 1.dp,
    ) {
        content()
    }
}

@Composable
fun AppDashboardTopBar(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo(size = 44.dp)
        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onLogout,
            border = BorderStroke(1.dp, AppColors.Border),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                stringResource(R.string.logout),
                color = AppColors.TextSecondary,
            )
        }
    }
}

@Composable
fun AppScreenTitle(
    text: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineLarge,
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun AppManagementTile(
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
) {
    AppActionTile(
        title = title,
        subtitle = subtitle,
        accent = accent,
        onClick = onClick,
        modifier = modifier.heightIn(min = 88.dp),
        badgeCount = badgeCount,
    )
}
