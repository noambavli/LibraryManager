package com.mh.librarymanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.BookState
import com.mh.librarymanager.domain.CustomColor

/**
 * Card used in both the public search and the management list. The card body
 * is the same; the management screen wraps it in an action row.
 *
 * Behaviour rules driven by the product spec:
 *  - State chip is hidden when the book is [BookState.AVAILABLE]; only
 *    "unavailable" (red) and "in repair" (dark blue) surface visually.
 *  - Linked catalog parents render as a compact tag (name + shelf metadata).
 *  - Free-text [Book.parentBookName] stays as plain "בתוך …" next to the title.
 */
@Composable
fun BookCard(
    book: Book,
    parentBook: Book?,
    customColors: List<CustomColor>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppColors.PanelElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AppColors.Border),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .then(clickableModifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BookTitleRow(
                book = book,
                parentBook = parentBook,
                trailing = trailing,
            )

            if (parentBook != null) {
                Spacer(modifier = Modifier.height(6.dp))
                ParentBookReference(parentBook = parentBook, customColors = customColors)
            }

            val writer = book.writer.ifBlank { stringResource(R.string.book_unknown_writer) }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = writer,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (book.topics.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = book.topics,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (book.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = book.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            BookChipsRow(book = book, customColors = customColors)
        }
    }
}

@Composable
private fun BookTitleRow(
    book: Book,
    parentBook: Book?,
    trailing: (@Composable () -> Unit)?,
) {
    val cs = MaterialTheme.colorScheme
    val textParent = book.parentBookName.takeIf { it.isNotBlank() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = book.name.ifBlank { "—" },
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (parentBook == null && textParent != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "—",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF9AA1AC),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.book_inside_of, textParent),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B7280),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.weight(1f))
            trailing()
        } else if (parentBook == null && textParent == null) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParentBookReference(
    parentBook: Book,
    customColors: List<CustomColor>,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = stringResource(R.string.book_inside_of_label),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF6B7280),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 7.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            modifier = Modifier.weight(1f),
            color = Color(0xFFF4F6F8),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFFDCE1E8)),
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = parentBook.name.ifBlank { "—" },
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (parentBook.hasShelfMetadata) {
                    Spacer(modifier = Modifier.height(5.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (parentBook.letter.isNotBlank()) {
                            ParentMetaChip(
                                label = parentBook.letter,
                                containerColor = Color(0xFFBDBDBD),
                                contentColor = Color(0xFF212121),
                                border = BorderStroke(1.dp, Color(0xFF9E9E9E)),
                            )
                        }
                        if (parentBook.displayNumber.isNotBlank()) {
                            ParentMetaChip(
                                label = parentBook.displayNumber,
                                containerColor = cs.surfaceVariant,
                                contentColor = cs.onSurfaceVariant,
                            )
                        }
                        if (parentBook.color.isNotBlank()) {
                            val style = resolveBookColorStyle(
                                colorName = parentBook.color,
                                customColors = customColors,
                                cardSurface = cs.surface,
                                fallbackBackground = cs.primaryContainer,
                                fallbackForeground = cs.onPrimaryContainer,
                            )
                            ParentMetaChip(
                                label = parentBook.color,
                                containerColor = style.background,
                                contentColor = style.foreground,
                                border = style.border,
                            )
                        }
                        if (parentBook.topics.isNotBlank()) {
                            ParentMetaChip(
                                label = parentBook.topics,
                                containerColor = Color(0xFFECEFF3),
                                contentColor = Color(0xFF4A5568),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val Book.hasShelfMetadata: Boolean
    get() = letter.isNotBlank() || displayNumber.isNotBlank() ||
        color.isNotBlank() || topics.isNotBlank()

@Composable
private fun ParentMetaChip(
    label: String,
    containerColor: Color,
    contentColor: Color,
    border: BorderStroke? = null,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(6.dp),
        border = border,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BookChipsRow(
    book: Book,
    customColors: List<CustomColor>,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StateChip(book.state)
        if (book.letter.isNotBlank()) {
            ChipPill(
                label = book.letter,
                containerColor = Color(0xFFBDBDBD),
                contentColor = Color(0xFF212121),
                border = BorderStroke(1.dp, Color(0xFF9E9E9E)),
            )
        }
        if (book.color.isNotBlank()) {
            val style = resolveBookColorStyle(
                colorName = book.color,
                customColors = customColors,
                cardSurface = cs.surface,
                fallbackBackground = cs.primaryContainer,
                fallbackForeground = cs.onPrimaryContainer,
            )
            ChipPill(
                label = book.color,
                containerColor = style.background,
                contentColor = style.foreground,
                border = style.border,
            )
        }
        if (book.displayNumber.isNotBlank()) {
            ChipPill(
                label = book.displayNumber,
                containerColor = cs.surfaceVariant,
                contentColor = cs.onSurfaceVariant,
            )
        }
        if (book.category.isNotBlank()) ChipPill(book.category)
        book.subcategories.firstOrNull()?.takeIf { it.isNotBlank() }?.let { ChipPill(it) }
        book.place.labelRes()?.let { labelRes ->
            ChipPill(
                label = stringResource(labelRes),
                containerColor = Color.White,
                contentColor = Color(0xFF424242),
                border = BorderStroke(1.dp, Color(0xFF9E9E9E)),
            )
        }
    }
}

@Composable
private fun StateChip(state: BookState) {
    when (state) {
        BookState.AVAILABLE -> Unit
        BookState.UNAVAILABLE -> ChipPill(
            label = stringResource(R.string.book_state_unavailable),
            containerColor = Color(0xFFC62828),
            contentColor = Color.White,
        )
        BookState.IN_REPAIR -> ChipPill(
            label = stringResource(R.string.book_state_in_repair),
            containerColor = Color(0xFF0B3A6F),
            contentColor = Color.White,
        )
    }
}

@Composable
fun ChipPill(
    label: String,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    border: BorderStroke? = null,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        border = border,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
