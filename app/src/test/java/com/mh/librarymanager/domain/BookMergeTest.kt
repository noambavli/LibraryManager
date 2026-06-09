package com.mh.librarymanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookMergeTest {

    @Test
    fun newBooksAreAdded_evenWhenRowIndexIdsCollide() {
        // Existing tablet rows came from a previous sheet (row-index ids).
        val previous = listOf(
            sample(id = "book-000001", name = "ספר א", writer = "מחבר א"),
            sample(id = "book-000002", name = "ספר ב", writer = "מחבר ב"),
        )
        // A new sheet of ONLY new books — but its row-index ids collide.
        val incoming = listOf(
            sample(id = "book-000001", name = "ספר חדש", writer = "מחבר חדש"),
            sample(id = "book-000002", name = "עוד ספר", writer = "מחבר נוסף"),
        )
        val plan = BookMerge.plan(previous, incoming, newId = counter())
        assertEquals(2, plan.toAdd.size)
        assertEquals(0, plan.skipped)
        assertEquals(setOf("ספר חדש", "עוד ספר"), plan.toAdd.map { it.name }.toSet())
    }

    @Test
    fun sameContentIsSkipped_idempotentReimport() {
        val previous = listOf(
            sample(id = "book-000001", name = "ספר א", writer = "מחבר א", letter = "א"),
        )
        val incoming = listOf(
            sample(id = "book-000999", name = "ספר א", writer = "מחבר א", letter = "א"),
        )
        val plan = BookMerge.plan(previous, incoming, newId = counter())
        assertEquals(0, plan.toAdd.size)
        assertEquals(1, plan.skipped)
    }

    @Test
    fun mixedSheet_addsOnlyNewContent() {
        val previous = listOf(
            sample(id = "book-000001", name = "קיים", writer = "w"),
        )
        val incoming = listOf(
            sample(id = "book-000001", name = "קיים", writer = "w"), // dup content
            sample(id = "book-000002", name = "חדש", writer = "w"),  // new
        )
        val plan = BookMerge.plan(previous, incoming, newId = counter())
        assertEquals(1, plan.toAdd.size)
        assertEquals("חדש", plan.toAdd[0].name)
        assertEquals(2, plan.totalAfter)
    }

    @Test
    fun addedBooksGetUniqueIds_notCollidingWithExisting() {
        val previous = listOf(sample(id = "book-000001", name = "א", writer = "w"))
        val incoming = listOf(sample(id = "book-000001", name = "ב", writer = "w"))
        val plan = BookMerge.plan(previous, incoming, newId = counter())
        val added = plan.toAdd.single()
        assertFalse(added.id == "book-000001")
        assertEquals(added.id, added.logicalBookId)
    }

    @Test
    fun addedBooksGetUniqueSequentialSystemNumbers() {
        val previous = listOf(
            sample(id = "book-000001", name = "א", writer = "w", system = "0007"),
        )
        val incoming = listOf(
            sample(id = "book-000001", name = "ב", writer = "w", system = "0001"),
            sample(id = "book-000002", name = "ג", writer = "w", system = "0002"),
        )
        val plan = BookMerge.plan(previous, incoming, newId = counter())
        assertEquals(listOf("0008", "0009"), plan.toAdd.map { it.bookNumber })
    }

    @Test
    fun intraFileDuplicateContent_isDeduped() {
        val incoming = listOf(
            sample(id = "book-000001", name = "כפול", writer = "w"),
            sample(id = "book-000002", name = "כפול", writer = "w"),
        )
        val plan = BookMerge.plan(emptyList(), incoming, newId = counter())
        assertEquals(1, plan.toAdd.size)
    }

    @Test
    fun blankRowsAreSkipped() {
        val incoming = listOf(
            sample(id = "book-000001", name = "", writer = "", display = "", topics = ""),
            sample(id = "book-000002", name = "ספר", writer = "w"),
        )
        val plan = BookMerge.plan(emptyList(), incoming, newId = counter())
        assertEquals(1, plan.toAdd.size)
        assertEquals("ספר", plan.toAdd[0].name)
    }

    @Test
    fun differentWriterIsNotDuplicate() {
        val previous = listOf(sample(id = "a", name = "בראשית", writer = "רשי"))
        val incoming = listOf(sample(id = "a", name = "בראשית", writer = "אחר"))
        val plan = BookMerge.plan(previous, incoming, newId = counter())
        assertEquals(1, plan.toAdd.size)
    }

    private fun counter(): () -> String {
        var n = 0
        return { "new-${n++}" }
    }

    private fun sample(
        id: String,
        name: String = "ספר",
        writer: String = "מחבר",
        letter: String = "",
        display: String = "",
        system: String = "0001",
        category: String = "",
        topics: String = "",
    ): Book = Book(
        id = id,
        logicalBookId = id,
        version = 1,
        isLatest = true,
        name = name,
        topics = topics,
        writer = writer,
        bookNumber = system,
        displayNumber = display,
        letter = letter,
        color = "",
        category = category,
        subcategories = emptyList(),
        notes = "",
        place = BookPlace.OTZAR,
        state = BookState.AVAILABLE,
        parentBookId = null,
        relations = emptyList(),
        createdAt = 1L,
        updatedAt = 1L,
    )
}
