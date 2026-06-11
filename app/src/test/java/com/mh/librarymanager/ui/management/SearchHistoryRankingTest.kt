package com.mh.librarymanager.ui.management

import com.mh.librarymanager.domain.SearchHistoryEntry
import com.mh.librarymanager.search.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryRankingTest {

    @Test
    fun rankPopularSearches_ordersByCountDescending() {
        val entries = listOf(
            entry(query = SearchQuery(writer = "רש\"י"), at = 1),
            entry(query = SearchQuery(writer = "רמב\"ם"), at = 2),
            entry(query = SearchQuery(writer = "רש\"י"), at = 3),
            entry(query = SearchQuery(writer = "רש\"י"), at = 4),
            entry(query = SearchQuery(name = "ברכות"), at = 5),
            entry(query = SearchQuery(name = "ברכות"), at = 6),
        )
        val ranked = rankPopularSearches(entries)
        assertEquals(3, ranked.size)
        assertEquals("רש\"י", ranked[0].query.writer)
        assertEquals(3, ranked[0].searchCount)
        assertEquals(1, ranked[0].rank)
        assertEquals("ברכות", ranked[1].query.name)
        assertEquals(2, ranked[1].searchCount)
        assertEquals(2, ranked[1].rank)
        assertEquals("רמב\"ם", ranked[2].query.writer)
        assertEquals(1, ranked[2].searchCount)
        assertEquals(3, ranked[2].rank)
    }

    @Test
    fun rankPopularSearches_skipsEmptyQueries() {
        val ranked = rankPopularSearches(listOf(entry(query = SearchQuery(), at = 1)))
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun rankPopularSearches_respectsLimit() {
        val entries = (1..5).map { entry(query = SearchQuery(general = "q$it"), at = it.toLong()) }
        assertEquals(3, rankPopularSearches(entries, limit = 3).size)
    }

    private fun entry(query: SearchQuery, at: Long) = SearchHistoryEntry(
        id = "id-$at",
        searchedAt = at,
        query = query,
        resultCount = 0,
    )
}
