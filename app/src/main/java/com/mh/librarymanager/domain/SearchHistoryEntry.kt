package com.mh.librarymanager.domain

import com.mh.librarymanager.search.SearchQuery

/** A single public search session recorded for management review. */
data class SearchHistoryEntry(
    val id: String,
    val searchedAt: Long,
    val query: SearchQuery,
    val resultCount: Int,
)
