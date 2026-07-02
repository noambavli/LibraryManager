package com.mh.librarymanager.domain

import com.mh.librarymanager.search.HebrewText
import java.util.UUID

/**
 * Pure planning for a merge import (PC `.civ` / xlsx → tablet catalog).
 *
 * Why content-based identity:
 *   The desktop/tablet importer derives each book's [Book.id] and
 *   [Book.bookNumber] from its **row index** in the source sheet
 *   (`book-000001`, `0001`, …). That makes identity = position, so a second
 *   sheet — or a sheet of only-new books — reuses `book-000001` and the old
 *   id-based dedup skipped every "new" book as already existing.
 *
 *   We instead dedup on the book's **content** (name/writer/letter/… — never
 *   the synthetic id, bookNumber, or timestamps), so:
 *     * the same book re-imported is correctly recognised and skipped, and
 *     * a genuinely new book is always added.
 *
 *   Newly added books get a fresh unique id (so they never collide with an
 *   existing storage key) and a fresh sequential system number (so they aren't
 *   immediately flagged as duplicate system numbers).
 */
object BookMerge {

    data class Plan(
        val toAdd: List<Book>,
        val skipped: Int,
        val totalAfter: Int,
    )

    /**
     * Content identity used for dedup. Deliberately excludes synthetic /
     * storage-only fields: [Book.id], [Book.logicalBookId], [Book.bookNumber]
     * (row-index), [Book.version], [Book.isLatest], place/state, parent,
     * relations and timestamps.
     */
    fun contentKey(book: Book): String = listOf(
        HebrewText.normalize(book.name),
        HebrewText.normalize(book.writer),
        HebrewText.normalize(book.letter),
        HebrewText.normalizeNumberKey(book.displayNumber),
        HebrewText.normalize(book.category),
        HebrewText.normalize(book.topics),
        HebrewText.normalize(book.color),
        book.subcategories.map { HebrewText.normalize(it) }.sorted().joinToString("|"),
        HebrewText.normalize(book.notes),
        // Location identity: the same title placed in a different library (or a
        // different column/shelf) is a distinct physical copy, so include place
        // and the beis-midrash address in the dedup key.
        HebrewText.normalize(book.place),
        HebrewText.normalize(book.column),
        HebrewText.normalize(book.shelf),
    ).joinToString("\u0000")

    fun plan(
        previous: List<Book>,
        incoming: List<Book>,
        newId: () -> String = { "book-" + UUID.randomUUID().toString() },
    ): Plan {
        val existingKeys = HashSet<String>(previous.size * 2)
        val existingIds = HashSet<String>(previous.size * 2)
        var maxNumber = 0
        for (b in previous) {
            existingKeys.add(contentKey(b))
            existingIds.add(b.id)
            b.bookNumber.toIntOrNull()?.let { if (it > maxNumber) maxNumber = it }
        }

        val toAdd = ArrayList<Book>()
        for (book in incoming) {
            if (isBlank(book)) continue
            val key = contentKey(book)
            if (!existingKeys.add(key)) continue // already on tablet or earlier in this file

            var id = book.id
            if (id.isBlank() || id in existingIds) {
                do { id = newId() } while (id in existingIds)
            }
            existingIds.add(id)

            maxNumber++
            val number = maxNumber.toString().padStart(4, '0')
            toAdd.add(book.copy(id = id, logicalBookId = id, bookNumber = number))
        }

        return Plan(
            toAdd = toAdd,
            skipped = incoming.size - toAdd.size,
            totalAfter = previous.size + toAdd.size,
        )
    }

    /** A row with nothing identifying — treated as padding, never imported. */
    private fun isBlank(book: Book): Boolean =
        book.name.isBlank() && book.writer.isBlank() && book.topics.isBlank() &&
            book.displayNumber.isBlank() && book.column.isBlank()
}
