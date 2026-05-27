package com.mh.librarymanager.search

import com.mh.librarymanager.domain.Book

/**
 * In-memory, normalised, version-aware full-text search.
 *
 * The catalog is treated as effectively static: every time the source list is
 * updated we rebuild an immutable index of pre-normalised strings for each
 * field. Searching is then a simple scan with substring contains + ranking,
 * which on a few thousand rows finishes in well under a millisecond.
 *
 * Properties:
 * - Hebrew text is normalised on both sides (nikud, final letters, geresh,
 *   punctuation) so "אבן" matches "אבן עזרא", "אבן־עזרא", "אבן׳ עזרא", etc.
 * - Half-word / substring matching is the default ("אבן" matches "ראבן").
 * - Word-start matches (after space or Hebrew prefix letter ב/ל/מ/ה/ש/ו/כ/ת)
 *   rank higher than mid-word matches, so the obvious result floats to top.
 * - Per-field constraints AND together. The general field also ANDs, but each
 *   of its tokens may match in any indexed field.
 * - Versioning-safe: only books with `isLatest = true` are indexed; old
 *   versions are completely invisible to search.
 *
 * The class is thread-safe to read after construction; replace the engine
 * (rather than mutate it) when the source data changes.
 */
class SearchEngine(books: List<Book>) {

    private val indexed: List<IndexedBook> = books
        .asSequence()
        .filter { it.isLatest }
        .map { IndexedBook.from(it) }
        .toList()

    val size: Int get() = indexed.size

    fun search(query: SearchQuery, limit: Int = 500): List<Book> {
        if (query.isEmpty) return indexed.take(limit).map { it.book }

        val generalTokens = HebrewText.tokens(query.general)
        val nameTokens = HebrewText.tokens(query.name)
        val topicsTokens = HebrewText.tokens(query.topics)
        val writerTokens = HebrewText.tokens(query.writer)
        val letterTokens = HebrewText.tokens(query.letter)
        val colorTokens = HebrewText.tokens(query.color)
        val categoryTokens = HebrewText.tokens(query.category)
        val subcategoryTokens = HebrewText.tokens(query.subcategory)
        val notesTokens = HebrewText.tokens(query.notes)

        data class Scored(val book: Book, val score: Int, val tieName: String)

        val results = ArrayList<Scored>()

        for (book in indexed) {
            if (!book.matchesField(nameTokens, book.name)) continue
            if (!book.matchesField(topicsTokens, book.topics)) continue
            if (!book.matchesField(writerTokens, book.writer)) continue
            if (!book.matchesField(letterTokens, book.letter)) continue
            if (!book.matchesField(colorTokens, book.color)) continue
            if (!book.matchesField(categoryTokens, book.category)) continue
            if (!book.matchesField(subcategoryTokens, book.subcategory)) continue
            if (!book.matchesField(notesTokens, book.notes)) continue

            if (generalTokens.isNotEmpty() && !book.matchesGeneral(generalTokens)) continue

            val score = book.score(
                generalTokens = generalTokens,
                nameTokens = nameTokens,
                topicsTokens = topicsTokens,
                writerTokens = writerTokens,
                categoryTokens = categoryTokens,
                subcategoryTokens = subcategoryTokens,
                notesTokens = notesTokens,
            )
            results += Scored(book.book, score, book.name)
        }

        results.sortWith(
            compareByDescending<Scored> { it.score }
                .thenBy { it.tieName },
        )

        if (results.size <= limit) return results.map { it.book }
        return results.asSequence().take(limit).map { it.book }.toList()
    }

    private class IndexedBook(
        val book: Book,
        val name: String,
        val topics: String,
        val writer: String,
        val letter: String,
        val color: String,
        val category: String,
        val subcategory: String,
        val notes: String,
    ) {

        private val searchable: List<String> = listOf(
            name, topics, writer, letter, color, category, subcategory, notes,
        )

        fun matchesField(tokens: List<String>, field: String): Boolean {
            if (tokens.isEmpty()) return true
            for (t in tokens) if (!field.contains(t)) return false
            return true
        }

        fun matchesGeneral(tokens: List<String>): Boolean {
            for (t in tokens) {
                var found = false
                for (field in searchable) {
                    if (field.contains(t)) { found = true; break }
                }
                if (!found) return false
            }
            return true
        }

        fun score(
            generalTokens: List<String>,
            nameTokens: List<String>,
            topicsTokens: List<String>,
            writerTokens: List<String>,
            categoryTokens: List<String>,
            subcategoryTokens: List<String>,
            notesTokens: List<String>,
        ): Int {
            var s = 0
            s += scoreField(nameTokens, name, weight = 50)
            s += scoreField(topicsTokens, topics, weight = 25)
            s += scoreField(writerTokens, writer, weight = 30)
            s += scoreField(categoryTokens, category, weight = 15)
            s += scoreField(subcategoryTokens, subcategory, weight = 12)
            s += scoreField(notesTokens, notes, weight = 8)

            for (t in generalTokens) {
                s += scoreToken(t, name, weight = 40)
                s += scoreToken(t, topics, weight = 18)
                s += scoreToken(t, writer, weight = 22)
                s += scoreToken(t, category, weight = 10)
                s += scoreToken(t, subcategory, weight = 8)
                s += scoreToken(t, notes, weight = 5)
                s += scoreToken(t, letter, weight = 3)
                s += scoreToken(t, color, weight = 3)
            }
            return s
        }

        private fun scoreField(tokens: List<String>, field: String, weight: Int): Int {
            if (tokens.isEmpty() || field.isEmpty()) return 0
            var s = 0
            for (t in tokens) s += scoreToken(t, field, weight)
            return s
        }

        private fun scoreToken(token: String, field: String, weight: Int): Int {
            if (token.isEmpty() || field.isEmpty()) return 0
            val idx = field.indexOf(token)
            if (idx < 0) return 0
            val isStartOfField = idx == 0
            val isWordStart = isStartOfField || isHebrewWordBoundary(field[idx - 1])
            val base = when {
                field == token -> weight * 4
                isStartOfField -> weight * 3
                isWordStart -> weight * 2
                else -> weight
            }
            return base + (token.length / 2)
        }

        /**
         * Returns true if the previous character is a boundary that should make
         * the next token "feel like" the start of a word. In Hebrew this means
         * whitespace **or** one of the inseparable prefix letters that grammar
         * commonly glues onto a word (ה,ו,ב,כ,ל,מ,ש,ת).
         */
        private fun isHebrewWordBoundary(c: Char): Boolean {
            if (c.isWhitespace()) return true
            return c == 'ה' || c == 'ו' || c == 'ב' || c == 'כ' ||
                c == 'ל' || c == 'מ' || c == 'ש' || c == 'ת'
        }

        companion object {
            fun from(book: Book): IndexedBook = IndexedBook(
                book = book,
                name = HebrewText.normalize(book.name),
                topics = HebrewText.normalize(book.topics),
                writer = HebrewText.normalize(book.writer),
                letter = HebrewText.normalize(book.letter),
                color = HebrewText.normalize(book.color),
                category = HebrewText.normalize(book.category),
                subcategory = HebrewText.normalize(book.subcategories.joinToString(" ")),
                notes = HebrewText.normalize(book.notes),
            )
        }
    }
}
