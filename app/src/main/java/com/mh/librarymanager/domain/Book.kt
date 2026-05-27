package com.mh.librarymanager.domain

/**
 * Public, UI-facing book model.
 *
 * Versioning is built in from day one: a book has a stable [logicalBookId]
 * which is shared across every revision of the same title, plus a per-row
 * [version]. The active row is marked [isLatest]. Search and listing always
 * filter on `isLatest = true` so the rest of the app does not need to think
 * about the history table.
 *
 * [subcategories] and [relations] are stored as lists so the UI can already
 * render multi-value fields even though the import populates one value today.
 */
data class Book(
    val id: String,
    val logicalBookId: String,
    val version: Int,
    val isLatest: Boolean,

    val name: String,
    val topics: String,
    val writer: String,
    /** System / technical identifier shown to staff (editable). */
    val bookNumber: String,
    /** Legacy "number" column from the source xlsx — display-only, no semantic meaning. */
    val displayNumber: String,
    val letter: String,
    val color: String,
    val category: String,
    val subcategories: List<String>,
    val notes: String,

    val place: BookPlace,
    val state: BookState,
    val parentBookId: String?,
    val relations: List<String>,

    val createdAt: Long,
    val updatedAt: Long,
)
