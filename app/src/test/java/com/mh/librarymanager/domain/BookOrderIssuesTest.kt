package com.mh.librarymanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookOrderIssuesTest {

    @Test
    fun sameNameDifferentWriter_isNotDuplicateRecord() {
        val books = listOf(
            sample(name = "בראשית", writer = "רש\"י", id = "a"),
            sample(name = "בראשית", writer = "אחר", id = "b"),
        )
        val issues = BookOrderIssues.issuesFor(books[0], books)
        assertFalse(issues.contains(BookOrderIssue.DUPLICATE_RECORD))
    }

    @Test
    fun identicalRows_areDuplicateRecord() {
        val books = listOf(
            sample(name = "שמות", writer = "רמב\"ן", letter = "א", display = "5", id = "a"),
            sample(name = "שמות", writer = "רמב\"ן", letter = "א", display = "5", id = "b"),
        )
        val issues = BookOrderIssues.issuesFor(books[0], books)
        assertTrue(issues.contains(BookOrderIssue.DUPLICATE_RECORD))
    }

    @Test
    fun sameLetterAndDisplay_isAllowed_notFlagged() {
        // Multiple volumes may share a shelf slot (letter + display number);
        // that is intentionally not an out-of-order issue.
        val books = listOf(
            sample(name = "א", letter = "א", display = "5", system = "0001", id = "a"),
            sample(name = "ב", letter = "א", display = "5", system = "0002", id = "b"),
        )
        val issues = BookOrderIssues.issuesFor(books[0], books)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun issueFilter_showsOnlyBooksWithThatMissingField() {
        val books = listOf(
            sample(name = "א", writer = "", letter = "א", display = "1", id = "a"),
            sample(name = "ב", writer = "מחבר", letter = "", display = "2", id = "b"),
        )
        val entries = BookOrderIssues.findOutOfOrder(books)
        val missingWriter = BookOrderIssues.filterEntries(
            entries,
            OutOfOrderFilter.MISSING,
            BookOrderIssue.MISSING_WRITER,
        )
        val missingLetter = BookOrderIssues.filterEntries(
            entries,
            OutOfOrderFilter.MISSING,
            BookOrderIssue.MISSING_LETTER,
        )
        assertEquals(1, missingWriter.size)
        assertEquals("א", missingWriter[0].book.name)
        assertEquals(1, missingLetter.size)
        assertEquals("ב", missingLetter[0].book.name)
    }

    @Test
    fun missingDisplayNumbers_stillFlagMissingDisplay() {
        val books = listOf(
            sample(name = "א", letter = "א", display = "", id = "a"),
            sample(name = "ב", letter = "ב", display = "", id = "b"),
        )
        val issues = BookOrderIssues.issuesFor(books[0], books)
        assertTrue(issues.contains(BookOrderIssue.MISSING_DISPLAY_NUMBER))
    }

    @Test
    fun normalizedSystemNumbers_matchDuplicate() {
        val books = listOf(
            sample(name = "א", system = "0042", id = "a"),
            sample(name = "ב", system = "42", id = "b"),
        )
        val issues = BookOrderIssues.issuesFor(books[0], books)
        assertTrue(issues.contains(BookOrderIssue.DUPLICATE_SYSTEM_NUMBER))
    }

    @Test
    fun blankSystemNumbers_areNotDuplicateSystem() {
        val books = listOf(
            sample(name = "א", system = "", id = "a"),
            sample(name = "ב", system = "", id = "b"),
        )
        val issues = BookOrderIssues.issuesFor(books[0], books)
        assertFalse(issues.contains(BookOrderIssue.DUPLICATE_SYSTEM_NUMBER))
        assertTrue(issues.contains(BookOrderIssue.MISSING_SYSTEM_NUMBER))
    }

    @Test
    fun hebrewInSystemNumber_flagsLetterNotInvalid() {
        val book = sample(name = "א", system = "א")
        val issues = BookOrderIssues.issuesFor(book, listOf(book))
        assertTrue(issues.contains(BookOrderIssue.LETTER_IN_SYSTEM_NUMBER))
        assertFalse(issues.contains(BookOrderIssue.INVALID_SYSTEM_NUMBER))
    }

    @Test
    fun sameNameDifferentCategory_isNotDuplicateRecord() {
        val books = listOf(
            sample(name = "בראשית", category = "תורה", id = "a"),
            sample(name = "בראשית", category = "נביאים", id = "b"),
        )
        val issues = BookOrderIssues.issuesFor(books[0], books)
        assertFalse(issues.contains(BookOrderIssue.DUPLICATE_RECORD))
    }

    @Test
    fun sameNameDifferentDisplay_isNotDuplicateRecord() {
        val books = listOf(
            sample(name = "שמות", letter = "א", display = "1", id = "a"),
            sample(name = "שמות", letter = "א", display = "2", id = "b"),
        )
        val issues = BookOrderIssues.issuesFor(books[0], books)
        assertFalse(issues.contains(BookOrderIssue.DUPLICATE_RECORD))
    }

    @Test
    fun subcategoriesDifferentOrder_isDuplicateRecord() {
        val books = listOf(
            sample(name = "ויקרא", id = "a").copy(subcategories = listOf("א", "ב")),
            sample(name = "ויקרא", id = "b").copy(subcategories = listOf("ב", "א")),
        )
        val issues = BookOrderIssues.issuesFor(books[0], books)
        assertTrue(issues.contains(BookOrderIssue.DUPLICATE_RECORD))
    }

    @Test
    fun namelessRow_onlyFlagsMissingName() {
        val book = sample(name = "", writer = "", letter = "", display = "", system = "")
        val issues = BookOrderIssues.issuesFor(book, listOf(book))
        assertTrue(issues.contains(BookOrderIssue.MISSING_NAME))
        assertFalse(issues.contains(BookOrderIssue.MISSING_WRITER))
        assertFalse(issues.contains(BookOrderIssue.MISSING_LETTER))
        assertFalse(issues.contains(BookOrderIssue.MISSING_DISPLAY_NUMBER))
        assertFalse(issues.contains(BookOrderIssue.MISSING_SYSTEM_NUMBER))
        assertFalse(issues.contains(BookOrderIssue.MISSING_CATEGORY))
    }

    private fun sample(
        name: String = "ספר",
        writer: String = "מחבר",
        letter: String = "",
        display: String = "",
        system: String = "0001",
        category: String = "קט",
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
        letter = letter,
        color = "",
        category = category,
        subcategories = emptyList(),
        notes = "",
        place = BookPlaceText.OTZAR_LABEL,
        state = BookState.AVAILABLE,
        parentBookId = null,
        relations = emptyList(),
        createdAt = 1L,
        updatedAt = 1L,
    )
}
