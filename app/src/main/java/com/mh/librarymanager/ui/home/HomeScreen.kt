package com.mh.librarymanager.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.Announcement
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.CustomColor
import com.mh.librarymanager.ui.announcements.AnnouncementsHomeSection
import com.mh.librarymanager.ui.components.AppLoadingContent
import com.mh.librarymanager.ui.components.AppActionTile
import com.mh.librarymanager.ui.components.AppBrandHeader
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppHeroButton
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.resolveBookColorStyle

/**
 * Tablet landing screen: search is the primary action at the top; announcements
 * and recently added books live in compact vertical panels below the secondary
 * shortcuts.
 */
@Composable
fun HomeScreen(
    recentlyAdded: List<Book>,
    catalogLoaded: Boolean,
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
    AppScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 920.dp),
            ) {
                AppBrandHeader(
                    title = stringResource(R.string.home_title),
                    subtitle = stringResource(R.string.home_subtitle),
                )

                Spacer(modifier = Modifier.height(28.dp))

                AppHeroButton(
                    title = stringResource(R.string.home_search),
                    subtitle = stringResource(R.string.home_search_subtitle),
                    onClick = onOpenSearch,
                )

                Spacer(modifier = Modifier.height(20.dp))

                SecondaryActionsRow(
                    onOpenRequests = onOpenRequests,
                    onOpenTechSupport = onOpenTechSupport,
                    onOpenManagement = onOpenManagement,
                )

                if (!catalogLoaded) {
                    Spacer(modifier = Modifier.height(24.dp))
                    HomeFeedPanel(title = stringResource(R.string.home_whats_new)) {
                        AppLoadingContent(modifier = Modifier.fillMaxSize())
                    }
                } else if (announcements.isNotEmpty() || recentlyAdded.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    FeedPanelsRow(
                        announcements = announcements,
                        recentlyAdded = recentlyAdded,
                        customColors = customColors,
                        onOpenAnnouncement = onOpenAnnouncement,
                        onOpenAllAnnouncements = onOpenAllAnnouncements,
                    )
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                    WhatsNewSection(
                        books = recentlyAdded,
                        customColors = customColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SecondaryActionsRow(
    onOpenRequests: () -> Unit,
    onOpenTechSupport: () -> Unit,
    onOpenManagement: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wide = maxWidth > 640.dp
        val actions = listOf(
            Triple(stringResource(R.string.home_requests), stringResource(R.string.home_requests_subtitle), onOpenRequests),
            Triple(stringResource(R.string.home_tech_support), stringResource(R.string.home_tech_support_subtitle), onOpenTechSupport),
            Triple(stringResource(R.string.home_management), stringResource(R.string.home_management_subtitle), onOpenManagement),
        )
        val accents = listOf(cs.secondary, cs.tertiary, cs.primary)

        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                actions.forEachIndexed { i, (title, subtitle, onClick) ->
                    AppActionTile(
                        title = title,
                        subtitle = subtitle,
                        accent = accents[i],
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.forEachIndexed { i, (title, subtitle, onClick) ->
                    AppActionTile(
                        title = title,
                        subtitle = subtitle,
                        accent = accents[i],
                        onClick = onClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedPanelsRow(
    announcements: List<Announcement>,
    recentlyAdded: List<Book>,
    customColors: List<CustomColor>,
    onOpenAnnouncement: (String) -> Unit,
    onOpenAllAnnouncements: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val sideBySide = maxWidth > 720.dp
        if (sideBySide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (announcements.isNotEmpty()) {
                    AnnouncementsHomeSection(
                        announcements = announcements,
                        onOpenAnnouncement = onOpenAnnouncement,
                        onOpenAll = onOpenAllAnnouncements,
                        modifier = Modifier.weight(1f),
                    )
                }
                WhatsNewSection(
                    books = recentlyAdded,
                    customColors = customColors,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (announcements.isNotEmpty()) {
                    AnnouncementsHomeSection(
                        announcements = announcements,
                        onOpenAnnouncement = onOpenAnnouncement,
                        onOpenAll = onOpenAllAnnouncements,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                WhatsNewSection(
                    books = recentlyAdded,
                    customColors = customColors,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WhatsNewSection(
    books: List<Book>,
    customColors: List<CustomColor>,
    modifier: Modifier = Modifier,
) {
    val visibleBooks = books.take(HomeFeedLayout.MaxBooks)

    HomeFeedPanel(
        title = stringResource(R.string.home_whats_new),
        modifier = modifier,
    ) {
        if (visibleBooks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.home_whats_new_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
            ) {
                visibleBooks.forEach { book ->
                    RecentBookRow(book = book, customColors = customColors)
                }
            }
        }
    }
}

@Composable
private fun RecentBookRow(
    book: Book,
    customColors: List<CustomColor>,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.PanelElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AppColors.BorderLight),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(style.background),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.name.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.writer.isNotBlank()) {
                    Text(
                        text = book.writer,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = recencyLabel(book.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.AccentMuted,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp),
            )
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
