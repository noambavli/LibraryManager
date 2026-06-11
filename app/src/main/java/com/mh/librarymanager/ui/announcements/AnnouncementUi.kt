package com.mh.librarymanager.ui.announcements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
    compact: Boolean = false,
) {
    if (announcements.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val visible = announcements.take(maxVisible)

    val panelModifier = modifier.fillMaxWidth()

    Surface(
        modifier = panelModifier,
        color = if (compact) AppColors.Panel else cs.surface,
        shape = RoundedCornerShape(if (compact) 14.dp else 0.dp),
        border = if (compact) BorderStroke(1.dp, AppColors.Border) else null,
        shadowElevation = if (compact) 1.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 14.dp else 0.dp,
                vertical = if (compact) 12.dp else 0.dp,
            ),
        ) {
            Text(
                text = stringResource(R.string.announcements_home_title),
                style = MaterialTheme.typography.titleSmall,
                color = if (compact) AppColors.TextSecondary else cs.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.forEach { announcement ->
                    AnnouncementCompactCard(
                        announcement = announcement,
                        onOpenFull = { onOpenAnnouncement(announcement.id) },
                        compact = compact,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenAll) {
                    Text(
                        text = stringResource(R.string.announcements_see_all),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (compact) AppColors.Accent else cs.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnouncementCompactCard(
    announcement: Announcement,
    onOpenFull: () -> Unit,
    compact: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (compact) Color.White else cs.surface,
        shape = RoundedCornerShape(if (compact) 10.dp else 14.dp),
        border = BorderStroke(1.dp, if (compact) AppColors.BorderLight else cs.outlineVariant),
        shadowElevation = if (compact) 0.dp else 1.dp,
    ) {
        Column(modifier = Modifier.padding(if (compact) 10.dp else 16.dp)) {
            Text(
                text = announcement.title.ifBlank { "—" },
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
                color = if (compact) AppColors.TextPrimary else cs.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (announcement.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(if (compact) 3.dp else 6.dp))
                Text(
                    text = announcement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (compact) AppColors.TextMuted else cs.onSurfaceVariant,
                    maxLines = if (compact) 2 else 3,
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
                        color = if (compact) AppColors.Accent else cs.primary,
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
