package com.mh.librarymanager.data.xlsx

import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.BookPlaceText
import com.mh.librarymanager.domain.BookState
import com.mh.librarymanager.domain.MatchingDirection
import com.mh.librarymanager.domain.SearchMatching
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsToolCodecTest {

    @Test
    fun shortcuts_skipHeaderAndExtractFirstColumn() {
        val rows = listOf(
            listOf("קיצור"),
            listOf("רמבם"),
            listOf("גמרא"),
            listOf(""),
        )
        val result = WindowsToolCodec.rowsToShortcuts(rows)
        assertEquals(listOf("רמבם", "גמרא"), result)
    }

    @Test
    fun shortcuts_noHeaderKeepsFirstRow() {
        val rows = listOf(listOf("גמרא"), listOf("משנה"))
        val result = WindowsToolCodec.rowsToShortcuts(rows)
        assertEquals(listOf("גמרא", "משנה"), result)
    }

    @Test
    fun shortcuts_roundTripThroughRows() {
        val original = listOf("גמרא", "משנה", "רמבם")
        val rows = WindowsToolCodec.shortcutsToRows(original)
        assertEquals(original, WindowsToolCodec.rowsToShortcuts(rows))
    }

    @Test
    fun matchings_headerDrivenWithCommaSeparatedWords() {
        val rows = listOf(
            listOf("קיצור", "מילים", "כיוון"),
            listOf("רמבם", "רמב\"ם, משנה תורה, מיימוני", "דו-כיווני"),
            listOf("פי'", "פירוש; פירושים", "חד-כיווני"),
        )
        val result = WindowsToolCodec.rowsToMatchings(rows)
        assertEquals(2, result.size)
        assertEquals("רמבם", result[0].shortcut)
        assertEquals(listOf("רמב\"ם", "משנה תורה", "מיימוני"), result[0].words)
        assertEquals(MatchingDirection.Bidirectional, result[0].direction)
        assertEquals(MatchingDirection.WordsToShortcut, result[1].direction)
        assertEquals(listOf("פירוש", "פירושים"), result[1].words)
    }

    @Test
    fun matchings_positionalFallbackWithoutHeader() {
        val rows = listOf(
            listOf("רמבם", "רמב\"ם | משנה תורה"),
        )
        val result = WindowsToolCodec.rowsToMatchings(rows)
        assertEquals(1, result.size)
        assertEquals("רמבם", result[0].shortcut)
        assertEquals(listOf("רמב\"ם", "משנה תורה"), result[0].words)
    }

    @Test
    fun matchings_dropsRowsWithoutShortcutOrWords() {
        val rows = listOf(
            listOf("קיצור", "מילים"),
            listOf("", "משנה"),
            listOf("רמבם", ""),
            listOf("גמרא", "תלמוד"),
        )
        val result = WindowsToolCodec.rowsToMatchings(rows)
        assertEquals(1, result.size)
        assertEquals("גמרא", result[0].shortcut)
    }

    @Test
    fun matchings_roundTripThroughRows() {
        val original = listOf(
            SearchMatching(shortcut = "רמבם", words = listOf("משנה תורה", "מיימוני")),
            SearchMatching(
                shortcut = "פי",
                words = listOf("פירוש"),
                direction = MatchingDirection.WordsToShortcut,
            ),
        )
        val rows = WindowsToolCodec.matchingsToRows(original)
        val parsed = WindowsToolCodec.rowsToMatchings(rows)
        assertEquals(original.map { it.shortcut }, parsed.map { it.shortcut })
        assertEquals(original.map { it.words }, parsed.map { it.words })
        assertEquals(original.map { it.direction }, parsed.map { it.direction })
    }

    @Test
    fun books_exportHasHeaderAndMapsColumns() {
        val rows = WindowsToolCodec.booksToRows(listOf(sampleBook()))
        assertEquals(WindowsToolCodec.BOOK_HEADERS, rows.first())
        // Otzar sheet is placeless: 9 columns, no מקום column.
        assertEquals(9, rows.first().size)
        val row = rows[1]
        assertEquals("בראשית", row[0])
        assertEquals("רש\"י", row[2])
        assertEquals("12", row[3])
        assertEquals("אדום", row[5])
        assertEquals("חומש", row[6])
        assertEquals("תורה", row[7])
    }

    @Test
    fun books_onlyHeaderWhenEmpty() {
        val rows = WindowsToolCodec.booksToRows(emptyList())
        assertEquals(1, rows.size)
        assertTrue(rows.first().isNotEmpty())
    }

    @Test
    fun beis_exportHasHeaderAndMapsColumns() {
        val rows = WindowsToolCodec.beisToRows(listOf(sampleBeisBook()))
        assertEquals(WindowsToolCodec.BEIS_HEADERS, rows.first())
        assertEquals(7, rows.first().size)
        val row = rows[1]
        assertEquals("משנה ברורה", row[0])
        assertEquals("החפץ חיים", row[2])
        assertEquals("3", row[3])
        assertEquals("5", row[4])
        assertEquals("אדום", row[5])
    }

    private fun sampleBeisBook(): Book = sampleBook().copy(
        name = "משנה ברורה",
        writer = "החפץ חיים",
        displayNumber = "",
        letter = "",
        category = "",
        subcategories = emptyList(),
        column = "3",
        shelf = "5",
        color = "אדום",
        place = BookPlaceText.BEIS_MIDRASH_LABEL,
    )

    private fun sampleBook(): Book = Book(
        id = "b1",
        logicalBookId = "b1",
        version = 1,
        isLatest = true,
        name = "בראשית",
        topics = "בריאת העולם",
        writer = "רש\"י",
        bookNumber = "0001",
        displayNumber = "12",
        letter = "ב",
        color = "אדום",
        category = "חומש",
        subcategories = listOf("תורה"),
        notes = "",
        place = BookPlaceText.OTZAR_LABEL,
        state = BookState.AVAILABLE,
        parentBookId = null,
        relations = emptyList(),
        createdAt = 0L,
        updatedAt = 0L,
    )
}
