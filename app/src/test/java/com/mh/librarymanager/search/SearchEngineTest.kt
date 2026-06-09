package com.mh.librarymanager.search

import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.BookPlace
import com.mh.librarymanager.domain.BookState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    @Test
    fun searchByDisplayNumber_normalizedMatch() {
        val books = listOf(sample(name = "א", display = "05"))
        val engine = SearchEngine(books)
        val results = engine.search(SearchQuery(displayNumber = "5"))
        assertEquals(1, results.size)
    }

    @Test
    fun searchByBookNumber_normalizedMatch() {
        val books = listOf(sample(name = "א", system = "0042"))
        val engine = SearchEngine(books)
        val results = engine.search(SearchQuery(bookNumber = "42"))
        assertEquals(1, results.size)
    }

    @Test
    fun searchByDisplayNumber_noFalseMatch() {
        val books = listOf(sample(name = "א", display = "15"))
        val engine = SearchEngine(books)
        val results = engine.search(SearchQuery(displayNumber = "5"))
        assertTrue(results.isEmpty())
    }

    @Test
    fun generalSearch_findsDisplayNumber() {
        val books = listOf(sample(name = "א", display = "99"))
        val engine = SearchEngine(books)
        val results = engine.search(SearchQuery(general = "99"))
        assertEquals(1, results.size)
    }

    private fun sample(
        name: String = "ספר",
        display: String = "",
        system: String = "0001",
        id: String = "book-1",
    ): Book = Book(
        id = id,
        logicalBookId = id,
        version = 1,
        isLatest = true,
        name = name,
        topics = "",
        writer = "מחבר",
        bookNumber = system,
        displayNumber = display,
        letter = "",
        color = "",
        category = "קט",
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
