package com.mh.librarymanager.data.xlsx

import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.MatchingDirection
import com.mh.librarymanager.domain.SearchMatching
import com.mh.librarymanager.search.HebrewText
import java.util.Locale

/**
 * Spreadsheet <-> domain conversion for the Windows Tool export/import.
 *
 * Books reuse the exact Hebrew headers the bundled catalog importer already
 * understands ([com.mh.librarymanager.data.xlsx.CatalogImporter]), so a sheet
 * exported here re-imports without surprises. Shortcuts and matchings get a
 * small, well-documented layout of their own.
 *
 * All readers are header-driven first (column order may move) and fall back to
 * a fixed positional layout when no header is recognised, so a hand-made sheet
 * still imports.
 */
object WindowsToolCodec {

    // ---- Books ------------------------------------------------------------

    val BOOK_HEADERS = listOf(
        "שם הספר", "ענינים", "המחבר", "מספר", "אות", "צבע", "קטגוריה", "תת קטגוריה", "הערות", "מקום",
    )

    fun booksToRows(books: List<Book>): List<List<String>> {
        val rows = ArrayList<List<String>>(books.size + 1)
        rows += BOOK_HEADERS
        for (b in books) {
            rows += listOf(
                b.name,
                b.topics,
                b.writer,
                b.displayNumber,
                b.letter,
                b.color,
                b.category,
                b.subcategories.firstOrNull().orEmpty(),
                b.notes,
                b.place,
            )
        }
        return rows
    }

    // ---- Shortcuts (quick search tags) ------------------------------------

    const val SHORTCUT_HEADER = "קיצור"

    fun shortcutsToRows(shortcuts: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>(shortcuts.size + 1)
        rows += listOf(SHORTCUT_HEADER)
        for (s in shortcuts) rows += listOf(s)
        return rows
    }

    /** First non-empty cell of each row becomes a shortcut; a header row is skipped. */
    fun rowsToShortcuts(rows: List<List<String>>): List<String> {
        if (rows.isEmpty()) return emptyList()
        val startIndex = if (looksLikeHeader(rows.first().firstOrNull())) 1 else 0
        val out = ArrayList<String>()
        for (i in startIndex until rows.size) {
            val value = rows[i].firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            if (value.isNotEmpty()) out += value
        }
        return out
    }

    // ---- Matchings (shortcut -> several full words) -----------------------

    val MATCHING_HEADERS = listOf("קיצור", "מילים", "כיוון")

    fun matchingsToRows(matchings: List<SearchMatching>): List<List<String>> {
        val rows = ArrayList<List<String>>(matchings.size + 1)
        rows += MATCHING_HEADERS
        for (m in matchings) {
            rows += listOf(
                m.shortcut,
                m.words.joinToString(", "),
                directionLabel(m.direction),
            )
        }
        return rows
    }

    fun rowsToMatchings(rows: List<List<String>>): List<SearchMatching> {
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { normalize(it) }
        val shortcutCol = header.indexOfFirst { it in normSet("קיצור", "shortcut") }
        val wordsCol = header.indexOfFirst { it in normSet("מילים", "מילה", "words", "word") }
        val directionCol = header.indexOfFirst { it in normSet("כיוון", "direction") }

        val hasHeader = shortcutCol >= 0 || wordsCol >= 0
        val sCol = if (shortcutCol >= 0) shortcutCol else 0
        val wCol = if (wordsCol >= 0) wordsCol else 1
        val dCol = directionCol
        val startIndex = if (hasHeader) 1 else 0

        val out = ArrayList<SearchMatching>()
        for (i in startIndex until rows.size) {
            val row = rows[i]
            val shortcut = row.getOrNull(sCol).orEmpty().trim()
            val wordsRaw = row.getOrNull(wCol).orEmpty()
            val words = splitWords(wordsRaw)
            if (shortcut.isEmpty() || words.isEmpty()) continue
            val direction = parseDirection(if (dCol >= 0) row.getOrNull(dCol).orEmpty() else "")
            out += SearchMatching(shortcut = shortcut, words = words, direction = direction)
        }
        return out
    }

    /** Words may be separated by comma, semicolon, pipe, slash or newline. */
    private fun splitWords(raw: String): List<String> =
        raw.split(',', ';', '|', '/', '\n', '\r', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun directionLabel(direction: MatchingDirection): String = when (direction) {
        MatchingDirection.Bidirectional -> "דו-כיווני"
        MatchingDirection.WordsToShortcut -> "חד-כיווני"
    }

    private fun parseDirection(raw: String): MatchingDirection {
        val n = normalize(raw)
        return if (n.contains("חד") || n.contains("one") || n.contains("word")) {
            MatchingDirection.WordsToShortcut
        } else {
            MatchingDirection.Bidirectional
        }
    }

    private fun looksLikeHeader(value: String?): Boolean {
        val n = normalize(value ?: return false)
        return n in normSet("קיצור", "מילה", "מילים", "shortcut", "word", "words", "תגית", "tag")
    }

    /** Normalises each token the same way as a header cell so comparisons line up. */
    private fun normSet(vararg tokens: String): Set<String> =
        tokens.map { normalize(it) }.toSet()

    private fun normalize(value: String): String =
        HebrewText.normalize(value.trim()).lowercase(Locale.ROOT)
}
