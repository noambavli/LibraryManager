package com.mh.librarymanager.ui.management

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.domain.SearchHistoryEntry
import com.mh.librarymanager.search.SearchQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

data class SearchHistoryDayGroup(
    val dayLabel: String,
    val dayStartMs: Long,
    val entries: List<SearchHistoryEntry>,
)

data class PopularSearchRating(
    val rank: Int,
    val query: SearchQuery,
    val searchCount: Int,
)

class SearchHistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val container = LibraryApp.from(app)

    private val entriesFlow = container.searchHistoryStore.entries
        .onStart { container.searchHistoryStore.loadFromDisk() }

    val dayGroups: StateFlow<List<SearchHistoryDayGroup>> =
        entriesFlow
            .map { entries -> groupAndShuffleByDay(entries) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val popularSearches: StateFlow<List<PopularSearchRating>> =
        entriesFlow
            .map { entries -> rankPopularSearches(entries) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            container.searchHistoryStore.remove(id)
        }
    }
}

/** Rank distinct queries by how often they were searched (newest 6-month window is enforced by the store). */
internal fun rankPopularSearches(
    entries: List<SearchHistoryEntry>,
    limit: Int = 100,
): List<PopularSearchRating> {
    if (entries.isEmpty() || limit <= 0) return emptyList()
    val counts = LinkedHashMap<String, Pair<SearchQuery, Int>>()
    for (entry in entries) {
        if (entry.query.isEmpty) continue
        val key = entry.query.fingerprint()
        val existing = counts[key]
        counts[key] = entry.query to ((existing?.second ?: 0) + 1)
    }
    return counts.values
        .sortedWith(compareByDescending<Pair<SearchQuery, Int>> { it.second }.thenBy { it.first.fingerprint() })
        .take(limit)
        .mapIndexed { index, (query, count) ->
            PopularSearchRating(rank = index + 1, query = query, searchCount = count)
        }
}

/** Newest days first; within each day entries are shuffled for visitor privacy. */
internal fun groupAndShuffleByDay(entries: List<SearchHistoryEntry>): List<SearchHistoryDayGroup> {
    if (entries.isEmpty()) return emptyList()
    val calendar = Calendar.getInstance()
    val byDay = LinkedHashMap<Long, MutableList<SearchHistoryEntry>>()
    for (entry in entries.sortedByDescending { it.searchedAt }) {
        val dayStart = startOfDay(calendar, entry.searchedAt)
        byDay.getOrPut(dayStart) { ArrayList() }.add(entry)
    }
    return byDay
        .map { (dayStart, dayEntries) ->
            SearchHistoryDayGroup(
                dayLabel = formatSearchHistoryDay(dayStart),
                dayStartMs = dayStart,
                entries = dayEntries.shuffled(Random(dayStart)),
            )
        }
        .sortedByDescending { it.dayStartMs }
}

private fun startOfDay(calendar: Calendar, timestamp: Long): Long {
    calendar.timeInMillis = timestamp
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

private val SEARCH_HISTORY_DAY_FMT: SimpleDateFormat by lazy {
    SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("he")).apply {
        timeZone = TimeZone.getDefault()
    }
}

private fun formatSearchHistoryDay(dayStartMs: Long): String =
    SEARCH_HISTORY_DAY_FMT.format(Date(dayStartMs))
