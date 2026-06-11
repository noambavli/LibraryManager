package com.mh.librarymanager.domain

/** A single tap on a book card's map button. */
data class BookLocationPressEntry(
    val id: String,
    val bookId: String,
    val pressedAt: Long,
)
