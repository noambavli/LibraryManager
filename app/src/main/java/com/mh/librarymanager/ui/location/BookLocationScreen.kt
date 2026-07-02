package com.mh.librarymanager.ui.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mh.librarymanager.R
import com.mh.librarymanager.data.librarymap.LibraryMapLoader
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.BookPlace
import com.mh.librarymanager.domain.LibraryMap
import com.mh.librarymanager.domain.LibraryMapMatcher
import com.mh.librarymanager.domain.displayPlace
import com.mh.librarymanager.domain.mapPlace
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppLoadingContent
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.PublicBackBar

@Composable
fun BookLocationScreen(
    book: Book?,
    catalogLoaded: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val mapPlace = remember(book?.place) { book?.mapPlace() }
    val libraryMap = remember(mapPlace) {
        mapPlace?.let { LibraryMapLoader.load(context, it) }
    }

    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            PublicBackBar(onBack = onBack)

            when {
                !catalogLoaded -> AppLoadingContent()
                book == null -> MissingBookMessage()
                else -> BookLocationContent(book = book, libraryMap = libraryMap)
            }
        }
    }
}

@Composable
private fun MissingBookMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.book_location_missing),
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextMuted,
        )
    }
}

@Composable
private fun BookLocationContent(
    book: Book,
    libraryMap: LibraryMap?,
) {
    val highlightSection = libraryMap?.let { LibraryMapMatcher.findSection(it, book) }
    val isBeis = book.mapPlace() == BookPlace.BEIS_MIDRASH
    val slotLabel = formatSlotLabel(
        book = book,
        columnPrefix = stringResource(R.string.book_location_column_prefix),
        shelfPrefix = stringResource(R.string.book_location_shelf_prefix),
    )
    // Beis-Midrash signs the column on the map; the shelf (מדף) is written
    // beside the red marker rather than moving it.
    val calloutText = if (isBeis && book.shelf.isNotBlank()) {
        stringResource(R.string.book_location_shelf_prefix) + " " + book.shelf
    } else {
        null
    }
    val hasMapPlace = book.mapPlace() != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        BookLocationHeader(
            book = book,
            slotLabel = slotLabel,
            sectionLabel = highlightSection?.label,
        )

        when {
            !hasMapPlace -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val placeLabel = book.displayPlace()
                        ?: stringResource(R.string.book_place_unspecified)
                    Text(
                        text = stringResource(R.string.book_location_no_map_for_place, placeLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextMuted,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            libraryMap == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.book_location_no_map),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextMuted,
                    )
                }
            }
            highlightSection == null -> {
                LibraryMapView(
                    map = libraryMap,
                    highlightSection = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                Text(
                    text = if (isBeis) {
                        stringResource(R.string.book_location_section_unknown_beis)
                    } else {
                        stringResource(R.string.book_location_section_unknown)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            else -> {
                LibraryMapView(
                    map = libraryMap,
                    highlightSection = highlightSection,
                    calloutText = calloutText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BookLocationHeader(
    book: Book,
    slotLabel: String,
    sectionLabel: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.name.ifBlank { "—" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (slotLabel.isNotBlank()) {
                Text(
                    text = slotLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            book.displayPlace()?.let { place ->
                Text(
                    text = place,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                )
            }
        }
        sectionLabel?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Accent,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatSlotLabel(
    book: Book,
    columnPrefix: String,
    shelfPrefix: String,
): String {
    val parts = buildList {
        if (book.mapPlace() == BookPlace.BEIS_MIDRASH) {
            if (book.column.isNotBlank()) add("$columnPrefix ${book.column}")
            if (book.shelf.isNotBlank()) add("$shelfPrefix ${book.shelf}")
        } else {
            if (book.letter.isNotBlank()) add(book.letter)
            if (book.displayNumber.isNotBlank()) add(book.displayNumber)
        }
        if (book.color.isNotBlank()) add(book.color)
    }
    return parts.joinToString(" — ")
}
