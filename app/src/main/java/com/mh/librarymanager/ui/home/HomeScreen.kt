package com.mh.librarymanager.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.Announcement
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.CustomColor
import com.mh.librarymanager.ui.announcements.AnnouncementsHomeSection
import com.mh.librarymanager.ui.components.resolveBookColorStyle

/**
 * Tablet-friendly landing screen. Two large action tiles — public search and
 * gated management — sit on a soft branded background. A "What's New" strip
 * above them highlights up to three books that were added in the past 30
 * days, so frequent visitors notice fresh content at a glance.
 */
@Composable
fun HomeScreen(
    recentlyAdded: List<Book>,
    customColors: List<CustomColor>,
    announcements: List<Announcement>,
    onOpenSearch: () -> Unit,
    onOpenManagement: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenTechSupport: () -> Unit,
    onOpenAnnouncement: (String) -> Unit,
    onOpenAllAnnouncements: () -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 40.dp),
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

            if (announcements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                AnnouncementsHomeSection(
                    announcements = announcements,
                    onOpenAnnouncement = onOpenAnnouncement,
                    onOpenAll = onOpenAllAnnouncements,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            WhatsNewSection(
                books = recentlyAdded,
                customColors = customColors,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            TilesArea(
                onOpenSearch = onOpenSearch,
                onOpenRequests = onOpenRequests,
                onOpenTechSupport = onOpenTechSupport,
                onOpenManagement = onOpenManagement,
            )
        }
    }
}

@Composable
private fun TilesArea(
    onOpenSearch: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenTechSupport: () -> Unit,
    onOpenManagement: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wide = maxWidth > 720.dp
        val tileHeight = if (wide) 240.dp else 168.dp
        val tiles = listOf(
            TileSpec(
                title = stringResource(R.string.home_search),
                subtitle = stringResource(R.string.home_search_subtitle),
                iconText = "\u05E1", // ס
                accent = cs.primary,
                onClick = onOpenSearch,
            ),
            TileSpec(
                title = stringResource(R.string.home_requests),
                subtitle = stringResource(R.string.home_requests_subtitle),
                iconText = "\u05D1", // ב
                accent = cs.secondary,
                onClick = onOpenRequests,
            ),
            TileSpec(
                title = stringResource(R.string.home_tech_support),
                subtitle = stringResource(R.string.home_tech_support_subtitle),
                iconText = "\u05EA", // ת
                accent = cs.tertiary,
                onClick = onOpenTechSupport,
            ),
            TileSpec(
                title = stringResource(R.string.home_management),
                subtitle = stringResource(R.string.home_management_subtitle),
                iconText = "\u05E0", // נ
                accent = cs.primary,
                onClick = onOpenManagement,
            ),
        )

        if (wide) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                tiles.chunked(2).forEach { rowTiles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        rowTiles.forEach { tile ->
                            HomeTile(
                                modifier = Modifier.weight(1f).height(tileHeight),
                                title = tile.title,
                                subtitle = tile.subtitle,
                                iconText = tile.iconText,
                                accent = tile.accent,
                                onClick = tile.onClick,
                            )
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                tiles.forEach { tile ->
                    HomeTile(
                        modifier = Modifier.fillMaxWidth().height(tileHeight),
                        title = tile.title,
                        subtitle = tile.subtitle,
                        iconText = tile.iconText,
                        accent = tile.accent,
                        onClick = tile.onClick,
                    )
                }
            }
        }
    }
}

private data class TileSpec(
    val title: String,
    val subtitle: String,
    val iconText: String,
    val accent: Color,
    val onClick: () -> Unit,
)

@Composable
private fun WhatsNewSection(
    books: List<Book>,
    customColors: List<CustomColor>,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(cs.primary),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_whats_new),
                style = MaterialTheme.typography.titleMedium,
                color = cs.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (books.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = cs.surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, cs.outlineVariant),
                shadowElevation = 0.dp,
            ) {
                Text(
                    text = stringResource(R.string.home_whats_new_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                books.forEach { book ->
                    RecentBookCard(
                        modifier = Modifier.weight(1f),
                        book = book,
                        customColors = customColors,
                    )
                }
                // Pad with empty weight slots so cards stay sized consistently
                // even when there are fewer than 3 recent books.
                repeat(3 - books.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RecentBookCard(
    modifier: Modifier,
    book: Book,
    customColors: List<CustomColor>,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = cs.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, cs.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (book.color.isNotBlank()) {
                    val style = resolveBookColorStyle(
                        colorName = book.color,
                        customColors = customColors,
                        cardSurface = cs.surface,
                        fallbackBackground = cs.primaryContainer,
                        fallbackForeground = cs.onPrimaryContainer,
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(style.background),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = recencyLabel(book.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = book.name.ifBlank { "—" },
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.writer.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.writer,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun recencyLabel(createdAt: Long): String {
    val daysAgo = ((System.currentTimeMillis() - createdAt) / (24L * 60L * 60L * 1000L))
        .toInt()
        .coerceAtLeast(0)
    return when (daysAgo) {
        0 -> stringResource(R.string.home_added_today)
        1 -> stringResource(R.string.home_added_yesterday)
        else -> stringResource(R.string.home_added_days_ago, daysAgo)
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
