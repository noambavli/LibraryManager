package com.mh.librarymanager.search

/**
 * Inputs from every search field on screen.
 *
 * Stored as raw strings; the engine normalises them at match time. An empty
 * string means "no constraint" for that field — never "must equal empty".
 */
data class SearchQuery(
    val general: String = "",
    val name: String = "",
    val topics: String = "",
    val writer: String = "",
    val letter: String = "",
    val color: String = "",
    val category: String = "",
    val subcategory: String = "",
    val displayNumber: String = "",
    val bookNumber: String = "",
    val notes: String = "",
) {
    val isEmpty: Boolean
        get() = general.isBlank() &&
            name.isBlank() &&
            topics.isBlank() &&
            writer.isBlank() &&
            letter.isBlank() &&
            color.isBlank() &&
            category.isBlank() &&
            subcategory.isBlank() &&
            displayNumber.isBlank() &&
            bookNumber.isBlank() &&
            notes.isBlank()
}
