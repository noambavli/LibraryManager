package com.mh.librarymanager.ui.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mh.librarymanager.ui.text.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.SearchHistoryEntry
import com.mh.librarymanager.search.SearchQuery
import com.mh.librarymanager.ui.components.AppLoadingContent
import com.mh.librarymanager.ui.components.AppColors
import com.mh.librarymanager.ui.components.AppScreenBackground
import com.mh.librarymanager.ui.components.ManagementHeader
import com.mh.librarymanager.ui.search.SearchField
import kotlinx.coroutines.launch

private enum class SearchHistoryTab { Daily, Popular }

@Composable
fun SearchHistoryScreen(
    viewModel: SearchHistoryViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val dayGroups by viewModel.dayGroups.collectAsStateWithLifecycle()
    val popularSearches by viewModel.popularSearches.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var deleteCandidate by remember { mutableStateOf<SearchHistoryEntry?>(null) }
    var selectedTab by remember { mutableStateOf(SearchHistoryTab.Daily) }

    AppScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ManagementHeader(
                title = stringResource(R.string.search_history_title),
                onBack = onBack,
                onLogout = onLogout,
            )

            SearchHistoryTabBar(
                selected = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )

            when (selectedTab) {
                SearchHistoryTab.Daily -> DailySearchHistoryContent(
                    dayGroups = dayGroups,
                    loaded = loaded,
                    onDelete = { deleteCandidate = it },
                )
                SearchHistoryTab.Popular -> PopularSearchHistoryContent(
                    ratings = popularSearches,
                    loaded = loaded,
                )
            }
        }
    }

    deleteCandidate?.let { candidate ->
        ConfirmSearchHistoryDeleteDialog(
            entry = candidate,
            onDismiss = { deleteCandidate = null },
            onConfirm = {
                scope.launch { viewModel.delete(candidate.id) }
                deleteCandidate = null
            },
        )
    }
}

@Composable
private fun SearchHistoryTabBar(
    selected: SearchHistoryTab,
    onSelect: (SearchHistoryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SearchHistoryTabChip(
            label = stringResource(R.string.search_history_tab_daily),
            selected = selected == SearchHistoryTab.Daily,
            onClick = { onSelect(SearchHistoryTab.Daily) },
            modifier = Modifier.weight(1f),
        )
        SearchHistoryTabChip(
            label = stringResource(R.string.search_history_tab_popular),
            selected = selected == SearchHistoryTab.Popular,
            onClick = { onSelect(SearchHistoryTab.Popular) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SearchHistoryTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) AppColors.Accent else AppColors.Panel
    val textColor = if (selected) Color.White else AppColors.TextPrimary
    val borderColor = if (selected) AppColors.Accent else AppColors.Border
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = background,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun DailySearchHistoryContent(
    dayGroups: List<SearchHistoryDayGroup>,
    loaded: Boolean,
    onDelete: (SearchHistoryEntry) -> Unit,
) {
    when {
        !loaded -> AppLoadingContent()
        dayGroups.isEmpty() -> EmptySearchHistoryMessage(stringResource(R.string.search_history_empty))
        else -> {
            PrivacyNotice(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
            )

            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                dayGroups.forEach { group ->
                    item(key = "day-${group.dayStartMs}") {
                        DayHeader(group.dayLabel)
                    }
                    items(group.entries, key = { it.id }) { entry ->
                        SearchHistoryCard(
                            entry = entry,
                            onDelete = { onDelete(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PopularSearchHistoryContent(
    ratings: List<PopularSearchRating>,
    loaded: Boolean,
) {
    PopularPeriodNotice(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
    )

    when {
        !loaded -> AppLoadingContent()
        ratings.isEmpty() -> EmptySearchHistoryMessage(stringResource(R.string.search_popular_empty))
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(ratings, key = { it.query.fingerprint() }) { rating ->
                PopularSearchCard(rating = rating)
            }
        }
    }
}

@Composable
private fun EmptySearchHistoryMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextMuted,
        )
    }
}

@Composable
private fun PopularPeriodNotice(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = AppColors.Panel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AppColors.Border),
    ) {
        Text(
            text = stringResource(R.string.search_popular_period_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun PopularSearchCard(rating: PopularSearchRating) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.PanelElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppColors.BorderLight),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = stringResource(R.string.search_popular_rank, rating.rank),
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.Accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 14.dp, top = 2.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    queryLines(rating.query).forEach { line ->
                        QueryChip(text = line)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.search_popular_search_count, rating.searchCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun PrivacyNotice(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = AppColors.Panel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AppColors.Border),
    ) {
        Text(
            text = stringResource(R.string.search_history_privacy_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun DayHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = AppColors.TextSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SearchHistoryCard(
    entry: SearchHistoryEntry,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.PanelElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppColors.BorderLight),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                queryLines(entry.query).forEach { line ->
                    QueryChip(text = line)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.search_history_results_count, entry.resultCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onDelete,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun QueryChip(text: String) {
    Surface(
        color = AppColors.Panel,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, AppColors.BorderLight),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun queryLines(query: SearchQuery): List<String> {
    // Resolve field labels through the override-aware stringResource so a
    // management rename of a search field (e.g. search_field_name) shows here
    // too — not just on the search screen.
    fun line(field: SearchField, label: String, value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        return "$label: $trimmed"
    }
    return listOfNotNull(
        line(SearchField.GENERAL, stringResource(SearchField.GENERAL.labelRes), query.general),
        line(SearchField.NAME, stringResource(SearchField.NAME.labelRes), query.name),
        line(SearchField.TOPICS, stringResource(SearchField.TOPICS.labelRes), query.topics),
        line(SearchField.WRITER, stringResource(SearchField.WRITER.labelRes), query.writer),
        line(SearchField.LETTER, stringResource(SearchField.LETTER.labelRes), query.letter),
        line(SearchField.COLOR, stringResource(SearchField.COLOR.labelRes), query.color),
        line(SearchField.CATEGORY, stringResource(SearchField.CATEGORY.labelRes), query.category),
        line(SearchField.SUBCATEGORY, stringResource(SearchField.SUBCATEGORY.labelRes), query.subcategory),
        line(SearchField.DISPLAY_NUMBER, stringResource(SearchField.DISPLAY_NUMBER.labelRes), query.displayNumber),
        line(SearchField.BOOK_NUMBER, stringResource(SearchField.BOOK_NUMBER.labelRes), query.bookNumber),
        line(SearchField.NOTES, stringResource(SearchField.NOTES.labelRes), query.notes),
    )
}

@Composable
private fun ConfirmSearchHistoryDeleteDialog(
    entry: SearchHistoryEntry,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val preview = queryLines(entry.query).firstOrNull().orEmpty()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cs.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.width(420.dp),
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text(
                    text = stringResource(R.string.search_history_confirm_delete),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                )
                if (preview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.confirm_delete_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cs.error,
                            contentColor = cs.onError,
                        ),
                    ) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
}
