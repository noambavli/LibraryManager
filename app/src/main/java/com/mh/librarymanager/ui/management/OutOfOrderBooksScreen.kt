package com.mh.librarymanager.ui.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mh.librarymanager.ui.text.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.BookOrderIssue
import com.mh.librarymanager.domain.BookOrderIssues
import com.mh.librarymanager.domain.BookPlaceText
import com.mh.librarymanager.domain.CustomColor
import com.mh.librarymanager.domain.OutOfOrderBook
import com.mh.librarymanager.domain.OutOfOrderFilter
import com.mh.librarymanager.domain.linkedParent
import com.mh.librarymanager.ui.components.AppLoadingContent
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.ManagementHeader
import com.mh.librarymanager.ui.components.BookCard
import com.mh.librarymanager.ui.components.ChipPill

@Composable
fun OutOfOrderBooksScreen(
    viewModel: BooksManagementViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenEditor: (bookId: String) -> Unit,
    onOpenBookLocation: (String) -> Unit,
) {
    val allEntries by viewModel.outOfOrderBooks.collectAsStateWithLifecycle()
    val entries by viewModel.filteredOutOfOrderBooks.collectAsStateWithLifecycle()
    val catalogLoaded by viewModel.catalogLoaded.collectAsStateWithLifecycle()
    val activeFilter by viewModel.outOfOrderFilter.collectAsStateWithLifecycle()
    val activeIssueFilter by viewModel.outOfOrderIssueFilter.collectAsStateWithLifecycle()
    val filterCounts by viewModel.outOfOrderFilterCounts.collectAsStateWithLifecycle()
    val issueCounts by viewModel.outOfOrderIssueCounts.collectAsStateWithLifecycle()
    val customColors by viewModel.customColors.collectAsStateWithLifecycle()
    val booksById by viewModel.booksById.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ManagementHeader(
            title = stringResource(R.string.out_of_order_title),
            onBack = onBack,
            onLogout = onLogout,
        )

        SummaryBanner(
            totalCount = allEntries.size,
            shownCount = entries.size,
            filter = activeFilter,
            catalogLoaded = catalogLoaded,
        )

        if (catalogLoaded && allEntries.isNotEmpty()) {
            FilterRow(
                active = activeFilter,
                counts = filterCounts,
                onSelect = viewModel::setOutOfOrderFilter,
            )
            IssueFilterRow(
                groupFilter = activeFilter,
                groupCount = filterCounts[activeFilter] ?: allEntries.size,
                activeIssue = activeIssueFilter,
                counts = issueCounts,
                onSelect = viewModel::setOutOfOrderIssueFilter,
            )
            HorizontalDivider(color = cs.outlineVariant)
        }

        when {
            !catalogLoaded -> AppLoadingContent()
            allEntries.isEmpty() -> EmptyAllGood()
            entries.isEmpty() -> EmptyFilter(activeFilter, activeIssueFilter)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { it.book.id }) { entry ->
                    OutOfOrderRow(
                        entry = entry,
                        parentBook = entry.book.linkedParent(booksById),
                        customColors = customColors,
                        onEdit = { onOpenEditor(entry.book.id) },
                        onOpenBookLocation = { onOpenBookLocation(entry.book.id) },
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun SummaryBanner(
    totalCount: Int,
    shownCount: Int,
    filter: OutOfOrderFilter,
    catalogLoaded: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = when {
            !catalogLoaded -> cs.surfaceVariant.copy(alpha = 0.35f)
            totalCount > 0 -> Color(0xFFFFF8E1)
            else -> cs.primaryContainer.copy(alpha = 0.35f)
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
            Text(
                text = when {
                    !catalogLoaded -> stringResource(R.string.results_loading)
                    totalCount == 0 -> stringResource(R.string.out_of_order_summary_ok)
                    filter == OutOfOrderFilter.ALL ->
                        stringResource(R.string.out_of_order_summary_count, totalCount)
                    else -> stringResource(
                        R.string.out_of_order_summary_filtered,
                        shownCount,
                        totalCount,
                    )
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    !catalogLoaded -> cs.onSurfaceVariant
                    totalCount > 0 -> Color(0xFF92400E)
                    else -> cs.onPrimaryContainer
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.out_of_order_summary_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    !catalogLoaded -> cs.onSurfaceVariant
                    totalCount > 0 -> Color(0xFFB45309)
                    else -> cs.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun FilterRow(
    active: OutOfOrderFilter,
    counts: Map<OutOfOrderFilter, Int>,
    onSelect: (OutOfOrderFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutOfOrderFilter.entries.forEach { filter ->
            FilterChip(
                label = filterLabel(filter),
                count = counts[filter] ?: 0,
                selected = active == filter,
                onClick = { onSelect(filter) },
            )
        }
    }
}

@Composable
private fun IssueFilterRow(
    groupFilter: OutOfOrderFilter,
    groupCount: Int,
    activeIssue: BookOrderIssue?,
    counts: Map<BookOrderIssue, Int>,
    onSelect: (BookOrderIssue?) -> Unit,
) {
    val issues = BookOrderIssues.issuesForGroup(groupFilter)
        .filter { (counts[it] ?: 0) > 0 }
    if (issues.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.out_of_order_issue_filter_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IssueFilterChip(
                label = stringResource(R.string.out_of_order_issue_filter_all),
                count = groupCount,
                selected = activeIssue == null,
                colors = filterGroupChipColors(groupFilter),
                onClick = { onSelect(null) },
            )
            issues.forEach { issue ->
                IssueFilterChip(
                    label = issueLabel(issue),
                    count = counts[issue] ?: 0,
                    selected = activeIssue == issue,
                    colors = filterGroupChipColors(issue.filterGroup),
                    onClick = { onSelect(issue) },
                )
            }
        }
    }
}

@Composable
private fun IssueFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    colors: Triple<Color, Color, Color>,
    onClick: () -> Unit,
) {
    val (bg, fg, border) = colors
    Surface(
        color = if (selected) bg else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) fg else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) border else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = "$label ($count)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = if (selected) cs.primary else cs.surface,
        contentColor = if (selected) cs.onPrimary else cs.onSurface,
        shape = RoundedCornerShape(20.dp),
        border = if (selected) null else BorderStroke(1.dp, cs.outlineVariant),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = "$label ($count)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EmptyAllGood() {
    val cs = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.displayMedium,
                color = cs.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.out_of_order_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.out_of_order_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyFilter(filter: OutOfOrderFilter, issueFilter: BookOrderIssue?) {
    val cs = MaterialTheme.colorScheme
    val label = if (issueFilter != null) issueLabel(issueFilter) else filterLabel(filter)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.out_of_order_filter_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.out_of_order_filter_empty_body, label),
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OutOfOrderRow(
    entry: OutOfOrderBook,
    parentBook: Book?,
    customColors: List<CustomColor>,
    onEdit: () -> Unit,
    onOpenBookLocation: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, Color(0xFFFFE082)),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            BookCard(
                book = entry.book,
                parentBook = parentBook,
                customColors = customColors,
                onClick = onEdit,
                onOpenLocation = onOpenBookLocation,
                trailing = {
                    FieldSnapshot(book = entry.book)
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                entry.issues.forEach { issue ->
                    val colors = filterGroupChipColors(issue.filterGroup)
                    ChipPill(
                        label = issueLabel(issue),
                        containerColor = colors.first,
                        contentColor = colors.second,
                        border = BorderStroke(1.dp, colors.third),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onEdit) {
                    Text(stringResource(R.string.out_of_order_fix))
                }
            }
        }
    }
}

@Composable
private fun FieldSnapshot(book: Book) {
    Column(horizontalAlignment = Alignment.End) {
        SnapshotLine(stringResource(R.string.field_name), book.name, book.name.isBlank())
        SnapshotLine(stringResource(R.string.field_writer), book.writer, book.writer.isBlank())
        SnapshotLine(stringResource(R.string.field_letter), book.letter, book.letter.isBlank())
        SnapshotLine(
            stringResource(R.string.field_display_number),
            book.displayNumber,
            book.displayNumber.isBlank(),
        )
        SnapshotLine(
            stringResource(R.string.field_book_number),
            book.bookNumber,
            book.bookNumber.isBlank(),
        )
        if (book.category.isBlank()) {
            SnapshotLine(stringResource(R.string.field_category), "", missing = true)
        }
        if (BookPlaceText.isBlank(book.place)) {
            SnapshotLine(
                stringResource(R.string.field_place),
                stringResource(R.string.book_place_unspecified),
                missing = true,
            )
        }
    }
}

@Composable
private fun SnapshotLine(label: String, value: String, missing: Boolean) {
    val cs = MaterialTheme.colorScheme
    val display = value.ifBlank { "—" }
    Text(
        text = "$label: $display",
        style = MaterialTheme.typography.labelSmall,
        color = if (missing) cs.error else cs.onSurfaceVariant,
        fontWeight = if (missing) FontWeight.SemiBold else FontWeight.Normal,
    )
}

private fun filterGroupChipColors(group: OutOfOrderFilter): Triple<Color, Color, Color> = when (group) {
    OutOfOrderFilter.MISSING -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Color(0xFFFFCDD2))
    OutOfOrderFilter.SWAPPED -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), Color(0xFFFFCC80))
    OutOfOrderFilter.DUPLICATE -> Triple(Color(0xFFF3E5F5), Color(0xFF6A1B9A), Color(0xFFE1BEE7))
    OutOfOrderFilter.OTHER -> Triple(Color(0xFFECEFF1), Color(0xFF455A64), Color(0xFFCFD8DC))
    OutOfOrderFilter.ALL -> Triple(Color(0xFFECEFF1), Color(0xFF455A64), Color(0xFFCFD8DC))
}

@Composable
private fun filterLabel(filter: OutOfOrderFilter): String = when (filter) {
    OutOfOrderFilter.ALL -> stringResource(R.string.out_of_order_filter_all)
    OutOfOrderFilter.MISSING -> stringResource(R.string.out_of_order_filter_missing)
    OutOfOrderFilter.SWAPPED -> stringResource(R.string.out_of_order_filter_swapped)
    OutOfOrderFilter.DUPLICATE -> stringResource(R.string.out_of_order_filter_duplicate)
    OutOfOrderFilter.OTHER -> stringResource(R.string.out_of_order_filter_other)
}

@Composable
private fun issueLabel(issue: BookOrderIssue): String = when (issue) {
    BookOrderIssue.MISSING_NAME -> stringResource(R.string.out_of_order_issue_missing_name)
    BookOrderIssue.MISSING_WRITER -> stringResource(R.string.out_of_order_issue_missing_writer)
    BookOrderIssue.MISSING_LETTER -> stringResource(R.string.out_of_order_issue_missing_letter)
    BookOrderIssue.MISSING_DISPLAY_NUMBER -> stringResource(R.string.out_of_order_issue_missing_display)
    BookOrderIssue.MISSING_SYSTEM_NUMBER -> stringResource(R.string.out_of_order_issue_missing_system)
    BookOrderIssue.MISSING_CATEGORY -> stringResource(R.string.out_of_order_issue_missing_category)
    BookOrderIssue.NUMBER_IN_LETTER_FIELD -> stringResource(R.string.out_of_order_issue_number_in_letter)
    BookOrderIssue.LETTER_IN_DISPLAY_NUMBER -> stringResource(R.string.out_of_order_issue_letter_in_display)
    BookOrderIssue.LETTER_IN_SYSTEM_NUMBER -> stringResource(R.string.out_of_order_issue_letter_in_system)
    BookOrderIssue.INVALID_SYSTEM_NUMBER -> stringResource(R.string.out_of_order_issue_invalid_system)
    BookOrderIssue.DUPLICATE_SYSTEM_NUMBER -> stringResource(R.string.out_of_order_issue_dup_system)
    BookOrderIssue.DUPLICATE_RECORD -> stringResource(R.string.out_of_order_issue_duplicate_record)
    BookOrderIssue.UNKNOWN_PARENT -> stringResource(R.string.out_of_order_issue_unknown_parent)
    BookOrderIssue.SELF_PARENT -> stringResource(R.string.out_of_order_issue_self_parent)
    BookOrderIssue.PLACE_NOT_SET -> stringResource(R.string.out_of_order_issue_place_unset)
}
