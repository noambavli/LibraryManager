package com.mh.librarymanager.ui.announcements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mh.librarymanager.ui.text.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.Announcement
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.CustomColor
import com.mh.librarymanager.domain.linkedParent
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.BookCard
import com.mh.librarymanager.ui.home.HomeFeedLayout
import com.mh.librarymanager.ui.home.HomeFeedPanel

/** A description is "long" (and gets a "full announcement" link) past this length. */
private const val LONG_DESCRIPTION_CHARS = 160

fun Announcement.isLong(): Boolean =
    description.length > LONG_DESCRIPTION_CHARS || linkedBookIds.isNotEmpty()

/**
 * Home-page announcements panel — same fixed height as the catalog "what's new"
 * panel. Shows as many announcements as fit; long single entries are truncated.
 */
@Composable
fun AnnouncementsHomeSection(
    announcements: List<Announcement>,
    onOpenAnnouncement: (String) -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (announcements.isEmpty()) return
    val slots = remember(announcements) { HomeFeedLayout.planAnnouncements(announcements) }

    HomeFeedPanel(
        title = stringResource(R.string.announcements_home_title),
        modifier = modifier,
        footer = {
            TextButton(onClick = onOpenAll) {
                Text(
                    text = stringResource(R.string.announcements_see_all),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Accent,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Top),
        ) {
            slots.forEach { slot ->
                AnnouncementCompactCard(
                    announcement = slot.announcement,
                    descriptionMaxLines = slot.descriptionMaxLines,
                    onOpenFull = { onOpenAnnouncement(slot.announcement.id) },
                )
            }
        }
    }
}

@Composable
private fun AnnouncementCompactCard(
    announcement: Announcement,
    descriptionMaxLines: Int,
    onOpenFull: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AppColors.BorderLight),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = announcement.title.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (announcement.description.isNotBlank() && descriptionMaxLines > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = announcement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                    maxLines = descriptionMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (announcement.isLong()) {
                TextButton(
                    onClick = onOpenFull,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(
                        text = stringResource(R.string.announcement_full),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.Accent,
                    )
                }
            }
        }
    }
}

/**
 * Full rendering of a single announcement: bold title, the complete
 * description, and any linked books as read-only cards.
 */
@Composable
fun AnnouncementFullView(
    announcement: Announcement,
    booksById: Map<String, Book>,
    customColors: List<CustomColor>,
    modifier: Modifier = Modifier,
    onOpenBookLocation: (String) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val linked = announcement.linkedBookIds.mapNotNull { booksById[it] }
    val missingCount = announcement.linkedBookIds.size - linked.size

    Column(modifier = modifier) {
        Text(
            text = announcement.title.ifBlank { "—" },
            style = MaterialTheme.typography.headlineMedium,
            color = cs.onSurface,
            fontWeight = FontWeight.Bold,
        )
        if (announcement.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = announcement.description,
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
            )
        }
        if (announcement.linkedBookIds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.announcement_linked_books),
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (linked.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    linked.forEach { book ->
                        BookCard(
                            book = book,
                            parentBook = book.linkedParent(booksById),
                            customColors = customColors,
                            onOpenLocation = { onOpenBookLocation(book.id) },
                        )
                    }
                }
            }
            if (missingCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.announcement_linked_books_missing, missingCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}
