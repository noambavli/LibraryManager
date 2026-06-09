package com.mh.librarymanager.ui.announcements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.TextButton
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
import com.mh.librarymanager.ui.components.BookCard

/** A description is "long" (and gets a "full announcement" link) past this length. */
private const val LONG_DESCRIPTION_CHARS = 160

fun Announcement.isLong(): Boolean =
    description.length > LONG_DESCRIPTION_CHARS || linkedBookIds.isNotEmpty()

/**
 * Home-page announcements strip: a header, up to [maxVisible] compact cards,
 * and a small "see all" button.
 */
@Composable
fun AnnouncementsHomeSection(
    announcements: List<Announcement>,
    onOpenAnnouncement: (String) -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier,
    maxVisible: Int = 3,
) {
    if (announcements.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val visible = announcements.take(maxVisible)

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(cs.secondary),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.announcements_home_title),
                style = MaterialTheme.typography.titleMedium,
                color = cs.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            visible.forEach { announcement ->
                AnnouncementCompactCard(
                    announcement = announcement,
                    onOpenFull = { onOpenAnnouncement(announcement.id) },
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onOpenAll) {
                Text(
                    text = stringResource(R.string.announcements_see_all),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun AnnouncementCompactCard(
    announcement: Announcement,
    onOpenFull: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, cs.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = announcement.title.ifBlank { "—" },
                style = MaterialTheme.typography.titleLarge,
                color = cs.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (announcement.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = announcement.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (announcement.isLong()) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onOpenFull,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(
                        text = stringResource(R.string.announcement_full),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.primary,
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
                            parentName = book.parentBookId?.let { booksById[it]?.name },
                            customColors = customColors,
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
