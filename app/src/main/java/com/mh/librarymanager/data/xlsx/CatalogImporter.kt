package com.mh.librarymanager.data.xlsx

import android.content.Context
import com.mh.librarymanager.data.BookRepository
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.BookPlace
import com.mh.librarymanager.domain.BookState
import com.mh.librarymanager.search.HebrewText
import java.io.InputStream
import java.util.Locale

/**
 * Imports a catalog xlsx into the database.
 *
 * The mapping is **driven by the header row**, not column position. Two modes:
 *  * [importFromStream] — replaces the whole catalog (bundled seed xlsx only).
 *  * [mergeFromStream] — adds new books and skips rows whose content already
 *    exists on the tablet (same dedup as `.civ` sync).
 */
class CatalogImporter(
    private val context: Context,
    private val repository: BookRepository,
) {

    suspend fun importFromAsset(assetName: String): ImportResult {
        context.assets.open(assetName).use { stream ->
            return importFromStream(stream)
        }
    }

    /** Replace the entire catalog — used only for the bundled seed import. */
    suspend fun importFromStream(stream: InputStream): ImportResult {
        val parsed = parseRows(XlsxReader.readFirstSheet(stream))
        if (parsed.books.isEmpty()) {
            repository.replaceAll(emptyList())
            return ImportResult(added = 0, duplicates = 0, blankRows = parsed.blankRows, totalAfter = 0)
        }
        repository.replaceAll(parsed.books)
        return ImportResult(
            added = parsed.books.size,
            duplicates = 0,
            blankRows = parsed.blankRows,
            totalAfter = parsed.books.size,
        )
    }

    /** Merge new rows into the existing catalog; duplicates are skipped. */
    suspend fun mergeFromStream(stream: InputStream): ImportResult {
        val parsed = parseRows(XlsxReader.readFirstSheet(stream))
        if (parsed.books.isEmpty()) {
            val count = repository.count()
            return ImportResult(added = 0, duplicates = 0, blankRows = parsed.blankRows, totalAfter = count)
        }
        val outcome = repository.mergeImport(parsed.books)
        return ImportResult(
            added = outcome.result.added,
            duplicates = outcome.result.skipped,
            blankRows = parsed.blankRows,
            totalAfter = outcome.result.totalAfter,
        )
    }

    data class ImportResult(
        val added: Int,
        /** Rows whose content already matches a book on the tablet (or a duplicate row in the file). */
        val duplicates: Int,
        /** Empty / padding rows in the sheet. */
        val blankRows: Int,
        val totalAfter: Int,
    )

    private data class ParsedRows(val books: List<Book>, val blankRows: Int)

    private fun parseRows(rows: List<List<String>>): ParsedRows {
        if (rows.isEmpty()) return ParsedRows(emptyList(), 0)
        val header = rows.first()
        val map = HeaderMap.from(header)
        val now = System.currentTimeMillis()
        val books = ArrayList<Book>(rows.size - 1)
        var blankRows = 0

        for (i in 1 until rows.size) {
            val row = rows[i]
            val name = map[row, HeaderKey.NAME]
            val topics = map[row, HeaderKey.TOPICS]
            val writer = map[row, HeaderKey.WRITER]
            val number = map[row, HeaderKey.NUMBER]
            val letter = map[row, HeaderKey.LETTER]
            val color = map[row, HeaderKey.COLOR]
            val category = map[row, HeaderKey.CATEGORY]
            val subcategory = map[row, HeaderKey.SUBCATEGORY]
            val notes = map[row, HeaderKey.NOTES]

            if (name.isEmpty() && topics.isEmpty() && writer.isEmpty() && number.isEmpty()) {
                blankRows++
                continue
            }

            val id = "book-${i.toString().padStart(6, '0')}"
            books += Book(
                id = id,
                logicalBookId = id,
                version = 1,
                isLatest = true,
                name = name,
                topics = topics,
                writer = writer,
                bookNumber = i.toString().padStart(4, '0'),
                displayNumber = number,
                letter = letter,
                color = color,
                category = category,
                subcategories = if (subcategory.isEmpty()) emptyList() else listOf(subcategory),
                notes = notes,
                place = BookPlace.OTZAR,
                state = BookState.AVAILABLE,
                parentBookId = null,
                relations = emptyList(),
                createdAt = now,
                updatedAt = now,
            )
        }
        return ParsedRows(books, blankRows)
    }

    private enum class HeaderKey { NAME, TOPICS, WRITER, NUMBER, LETTER, COLOR, CATEGORY, SUBCATEGORY, NOTES }

    private class HeaderMap private constructor(
        private val columns: Map<HeaderKey, Int>,
    ) {
        operator fun get(row: List<String>, key: HeaderKey): String {
            val idx = columns[key] ?: return ""
            return row.getOrNull(idx).orEmpty().trim()
        }

        companion object {
            private val ALIASES: Map<HeaderKey, List<String>> = mapOf(
                HeaderKey.NAME to listOf("שם הספר", "שם", "name"),
                HeaderKey.TOPICS to listOf("ענינים", "עניינים", "topics"),
                HeaderKey.WRITER to listOf("המחבר", "מחבר", "writer", "author"),
                HeaderKey.NUMBER to listOf("מספר", "number"),
                HeaderKey.LETTER to listOf("אות", "letter"),
                HeaderKey.COLOR to listOf("צבע", "color"),
                HeaderKey.CATEGORY to listOf("קטגוריה", "category"),
                HeaderKey.SUBCATEGORY to listOf("תת קטגוריה", "תת-קטגוריה", "subcategory", "subcategories"),
                HeaderKey.NOTES to listOf("הערות", "הערה", "notes", "note"),
            )

            fun from(header: List<String>): HeaderMap {
                val normalisedHeader = header.map {
                    HebrewText.normalize(it.trim()).lowercase(Locale.ROOT)
                }
                val result = EnumMap()
                for ((key, aliases) in ALIASES) {
                    val normalisedAliases = aliases.map {
                        HebrewText.normalize(it).lowercase(Locale.ROOT)
                    }
                    val idx = normalisedHeader.indexOfFirst { it in normalisedAliases }
                    if (idx >= 0) result[key] = idx
                }
                return HeaderMap(result)
            }

            private fun EnumMap(): MutableMap<HeaderKey, Int> = java.util.EnumMap(HeaderKey::class.java)
        }
    }
}
