package com.mh.librarymanager.search

import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.BookPlace
import com.mh.librarymanager.domain.BookState
import com.mh.librarymanager.domain.MatchingDirection
import com.mh.librarymanager.domain.SearchMatching
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

    @Test
    fun synonym_shortcutFindsWords_bidirectional() {
        val books = listOf(sample(name = "פירוש", id = "b1"))
        val engine = SearchEngine(books, synonymsOf(
            SearchMatching(shortcut = "פי׳", words = listOf("פירוש", "פירושי", "פירושים")),
        ))
        // Searching the shortcut finds a book whose name is one of the words.
        assertEquals(1, engine.search(SearchQuery(general = "פי")).size)
    }

    @Test
    fun synonym_wordsFindShortcut_phrase() {
        // Book carries the abbreviation; searching the full phrase must find it.
        val books = listOf(sample(name = "ספר", writer = "רמב״ם", id = "b1"))
        val engine = SearchEngine(books, synonymsOf(
            SearchMatching(shortcut = "רמב״ם", words = listOf("רבי משה בן מימון")),
        ))
        assertEquals(1, engine.search(SearchQuery(general = "רבי משה בן מימון")).size)
    }

    @Test
    fun synonym_shortcutFindsPhrase_reverse() {
        val books = listOf(sample(name = "ספר", writer = "רבי משה בן מימון", id = "b1"))
        val engine = SearchEngine(books, synonymsOf(
            SearchMatching(shortcut = "רמב״ם", words = listOf("רבי משה בן מימון")),
        ))
        assertEquals(1, engine.search(SearchQuery(general = "רמבם")).size)
    }

    @Test
    fun synonym_oneDirectional_doesNotExpandShortcut() {
        val books = listOf(sample(name = "ספר", writer = "רבי משה בן מימון", id = "b1"))
        val engine = SearchEngine(books, synonymsOf(
            SearchMatching(
                shortcut = "רמב״ם",
                words = listOf("רבי משה בן מימון"),
                direction = MatchingDirection.WordsToShortcut,
            ),
        ))
        // One-directional: shortcut must NOT pull in the words.
        assertTrue(engine.search(SearchQuery(general = "רמבם")).isEmpty())
        // ...but the words still find the shortcut.
        val withShortcut = listOf(sample(name = "ספר", writer = "רמב״ם", id = "b2"))
        val engine2 = SearchEngine(withShortcut, synonymsOf(
            SearchMatching(
                shortcut = "רמב״ם",
                words = listOf("רבי משה בן מימון"),
                direction = MatchingDirection.WordsToShortcut,
            ),
        ))
        assertEquals(1, engine2.search(SearchQuery(general = "רבי משה בן מימון")).size)
    }

    @Test
    fun synonym_wordOrderRanksEarlierWordsHigher() {
        // Words share no substring with the shortcut, so only the expansion matches.
        val books = listOf(
            sample(name = "גמרא", id = "later"),
            sample(name = "ברייתא", id = "earlier"),
        )
        val engine = SearchEngine(books, synonymsOf(
            SearchMatching(shortcut = "צצ", words = listOf("ברייתא", "גמרא")),
        ))
        val results = engine.search(SearchQuery(general = "צצ"))
        assertEquals(2, results.size)
        assertEquals("earlier", results.first().id)
    }

    @Test
    fun synonym_partialShortcutPrefix_expandsToWords() {
        val books = listOf(sample(name = "ספר", writer = "רבי משה בן מימון", id = "b1"))
        val engine = SearchEngine(books, synonymsOf(
            SearchMatching(shortcut = "רמב״ם", words = listOf("רבי משה בן מימון")),
        ))
        assertEquals(1, engine.search(SearchQuery(general = "רמב")).size)
    }

    @Test
    fun synonym_indexAugmentation_findsShortcutInAnyField() {
        val books = listOf(
            sample(name = "משנה תורה", writer = "רמב״ם", id = "b1"),
            sample(name = "ספר אחר", writer = "מחבר אחר", id = "b2"),
        )
        val engine = SearchEngine(books, synonymsOf(
            SearchMatching(shortcut = "רמב״ם", words = listOf("רבי משה בן מימון")),
        ))
        val results = engine.search(SearchQuery(general = "רבי משה בן מימון"))
        assertEquals(1, results.size)
        assertEquals("b1", results.first().id)
    }

    private fun synonymsOf(vararg matchings: SearchMatching): SearchSynonyms =
        SearchSynonyms.from(matchings.toList())

    private fun sample(
        name: String = "ספר",
        display: String = "",
        system: String = "0001",
        writer: String = "מחבר",
        id: String = "book-1",
    ): Book = Book(
        id = id,
        logicalBookId = id,
        version = 1,
        isLatest = true,
        name = name,
        topics = "",
        writer = writer,
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
