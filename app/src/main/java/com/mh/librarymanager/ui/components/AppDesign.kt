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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R

/** Shared palette for the refreshed tablet UI. */
object AppColors {
    val BgTop = Color(0xFFDDE3EC)
    val BgBottom = Color(0xFFD0D8E4)
    val Panel = Color(0xFFF4F6F9)
    val PanelElevated = Color.White
    val Border = Color(0xFFC8D0DC)
    val BorderLight = Color(0xFFD8DEE8)
    val TextPrimary = Color(0xFF1C2838)
    val TextSecondary = Color(0xFF5A6578)
    val TextMuted = Color(0xFF6B7789)
    val Accent = Color(0xFF4A7BB7)
    val AccentMuted = Color(0xFF9AA8BA)
    val HeroStart = Color(0xFF1A3354)
    val HeroEnd = Color(0xFF243F66)
    val HeroSubtitle = Color(0xFFB8C9DE)
    val Divider = Color(0xFFDCE1E8)
    val Warning = Color(0xFFB45309)
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                    color = Color(0xFF8FAFD4),
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
) {
    AppActionTile(
        title = title,
        subtitle = subtitle,
        accent = accent,
        onClick = onClick,
        modifier = modifier.heightIn(min = 88.dp),
    )
}
