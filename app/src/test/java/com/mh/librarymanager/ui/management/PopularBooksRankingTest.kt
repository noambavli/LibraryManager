package com.mh.librarymanager.ui.management

import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.BookLocationPressEntry
import com.mh.librarymanager.domain.BookPlace
import com.mh.librarymanager.domain.BookState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PopularBooksRankingTest {

    @Test
    fun rankPopularBooks_ordersByPressCountDescending() {
        val presses = listOf(
            press("a", 1),
            press("b", 2),
            press("a", 3),
            press("a", 4),
            press("c", 5),
            press("c", 6),
        )
        val books = mapOf(
            "a" to sampleBook("a", "Alpha"),
            "b" to sampleBook("b", "Beta"),
            "c" to sampleBook("c", "Gamma"),
        )
        val ranked = rankPopularBooks(presses, books)
        assertEquals(3, ranked.size)
        assertEquals("a", ranked[0].bookId)
        assertEquals(3, ranked[0].pressCount)
        assertEquals("c", ranked[1].bookId)
        assertEquals(2, ranked[1].pressCount)
        assertEquals("b", ranked[2].bookId)
        assertEquals(1, ranked[2].pressCount)
    }

    @Test
    fun rankPopularBooks_keepsMissingBookWithNullBook() {
        val ranked = rankPopularBooks(listOf(press("gone", 1)), emptyMap())
        assertEquals(1, ranked.size)
        assertNull(ranked[0].book)
        assertEquals("gone", ranked[0].bookId)
    }

    @Test
    fun rankPopularBooks_respectsLimit() {
        val presses = listOf(press("a", 1), press("b", 2), press("c", 3))
        assertEquals(2, rankPopularBooks(presses, emptyMap(), limit = 2).size)
    }

    private fun press(bookId: String, at: Long) = BookLocationPressEntry(
        id = "id-$bookId-$at",
        bookId = bookId,
        pressedAt = at,
    )

    private fun sampleBook(id: String, name: String) = Book(
        id = id,
        logicalBookId = id,
        version = 1,
        isLatest = true,
        name = name,
        writer = "Writer",
        topics = "",
        letter = "",
        color = "",
        category = "",
        subcategories = emptyList(),
        displayNumber = "",
        bookNumber = "",
        notes = "",
        place = BookPlace.OTZAR,
        state = BookState.AVAILABLE,
        parentBookId = null,
        relations = emptyList(),
        createdAt = 0,
        updatedAt = 0,
    )
}
