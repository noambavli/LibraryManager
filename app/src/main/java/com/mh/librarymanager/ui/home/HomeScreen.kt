package com.mh.librarymanager.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R

/**
 * Tablet-friendly landing screen. Two large action tiles — public search and
 * gated management — sit on a soft branded background. The layout reflows to
 * a single column on narrow widths so it still works portrait.
 */
@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenManagement: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to cs.background,
                    1f to cs.surfaceVariant,
                )
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.displaySmall,
                color = cs.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(48.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (maxWidth > 720.dp) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HomeTile(
                            modifier = Modifier.weight(1f).fillMaxHeight(0.85f),
                            title = stringResource(R.string.home_search),
                            subtitle = stringResource(R.string.home_search_subtitle),
                            iconText = "\u05E1", // ס
                            accent = cs.primary,
                            onClick = onOpenSearch,
                        )
                        HomeTile(
                            modifier = Modifier.weight(1f).fillMaxHeight(0.85f),
                            title = stringResource(R.string.home_management),
                            subtitle = stringResource(R.string.home_management_subtitle),
                            iconText = "\u05E0", // נ
                            accent = cs.tertiary,
                            onClick = onOpenManagement,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        HomeTile(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            title = stringResource(R.string.home_search),
                            subtitle = stringResource(R.string.home_search_subtitle),
                            iconText = "\u05E1",
                            accent = cs.primary,
                            onClick = onOpenSearch,
                        )
                        HomeTile(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            title = stringResource(R.string.home_management),
                            subtitle = stringResource(R.string.home_management_subtitle),
                            iconText = "\u05E0",
                            accent = cs.tertiary,
                            onClick = onOpenManagement,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTile(
    modifier: Modifier,
    title: String,
    subtitle: String,
    iconText: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = cs.surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, cs.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onClick)
                .padding(32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = iconText,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.displayMedium,
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = cs.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(56.dp)
                        .background(accent, RoundedCornerShape(1.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "›",
                    color = accent,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
