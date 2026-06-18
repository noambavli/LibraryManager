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
 * - Staff-managed synonyms ([SearchSynonyms]) expand the query: a recognised
 *   shortcut/word is matched against its ranked alternatives, so "רמבם" also
 *   finds "רבי משה בן מימון". Results found only via an expansion are ordered by
 *   the alternative's rank (word order in the rule); literal matches stay on top.
 * - Versioning-safe: only books with `isLatest = true` are indexed; old
 *   versions are completely invisible to search.
 *
 * The class is thread-safe to read after construction; replace the engine
 * (rather than mutate it) when the source data changes.
 */
class SearchEngine(
    books: List<Book>,
    private val synonyms: SearchSynonyms = SearchSynonyms.EMPTY,
) {

    private val indexed: List<IndexedBook> = books
        .asSequence()
        .filter { it.isLatest }
        .map { IndexedBook.from(it, synonyms) }
        .toList()

    val size: Int get() = indexed.size

    fun search(query: SearchQuery, limit: Int = 500): List<Book> {
        if (query.isEmpty) return indexed.take(limit).map { it.book }

        val generalCands = synonyms.expand(HebrewText.tokens(query.general))
        val nameCands = synonyms.expand(HebrewText.tokens(query.name))
        val topicsCands = synonyms.expand(HebrewText.tokens(query.topics))
        val writerCands = synonyms.expand(HebrewText.tokens(query.writer))
        val letterCands = synonyms.expand(HebrewText.tokens(query.letter))
        val colorCands = synonyms.expand(HebrewText.tokens(query.color))
        val categoryCands = synonyms.expand(HebrewText.tokens(query.category))
        val subcategoryCands = synonyms.expand(HebrewText.tokens(query.subcategory))
        val notesCands = synonyms.expand(HebrewText.tokens(query.notes))
        val displayNumberTokens = HebrewText.numberTokens(query.displayNumber)
        val bookNumberTokens = HebrewText.numberTokens(query.bookNumber)

        data class Scored(val book: Book, val score: Int, val tieName: String)

        val results = ArrayList<Scored>()

        for (book in indexed) {
            val nameC = book.fieldContribution(nameCands, book.name, weight = 50) ?: continue
            val topicsC = book.fieldContribution(topicsCands, book.topics, weight = 25) ?: continue
            val writerC = book.fieldContribution(writerCands, book.writer, weight = 30) ?: continue
            if (!book.matchesCandidates(letterCands, book.letter)) continue
            if (!book.matchesCandidates(colorCands, book.color)) continue
            val categoryC = book.fieldContribution(categoryCands, book.category, weight = 15) ?: continue
            val subcategoryC = book.fieldContribution(subcategoryCands, book.subcategory, weight = 12) ?: continue
            if (!book.matchesNumberField(displayNumberTokens, book.displayNumber)) continue
            if (!book.matchesNumberField(bookNumberTokens, book.bookNumber)) continue
            val notesC = book.fieldContribution(notesCands, book.notes, weight = 8) ?: continue

            val generalC = book.generalContribution(generalCands) ?: continue

            var score = nameC + topicsC + writerC + categoryC + subcategoryC + notesC + generalC
            score += book.scoreNumberField(displayNumberTokens, book.displayNumber, weight = 20)
            score += book.scoreNumberField(bookNumberTokens, book.bookNumber, weight = 18)
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
        val displayNumber: String,
        val bookNumber: String,
        val notes: String,
    ) {

        private val searchable: List<String> = listOf(
            name, topics, writer, letter, color, category, subcategory,
            displayNumber, bookNumber, notes,
        )

        /**
         * Filter-only match for a field (used where the field carries no score,
         * e.g. letter/color). True when unconstrained or any candidate matches.
         */
        fun matchesCandidates(candidates: List<SearchSynonyms.Candidate>, field: String): Boolean {
            if (candidates.isEmpty()) return true
            if (field.isEmpty()) return false
            for (c in candidates) {
                if (c.tokens.all { field.contains(it) }) return true
            }
            return false
        }

        /**
         * Best score contribution for a constrained field, or null when the
         * field is constrained but no candidate matches (book is excluded).
         * Returns 0 when there is no constraint for the field.
         */
        fun fieldContribution(
            candidates: List<SearchSynonyms.Candidate>,
            field: String,
            weight: Int,
        ): Int? {
            if (candidates.isEmpty()) return 0
            if (field.isEmpty()) return null
            var best: Int? = null
            for (c in candidates) {
                if (!c.tokens.all { field.contains(it) }) continue
                var s = 0
                for (t in c.tokens) s += scoreToken(t, field, weight)
                s -= c.rank * RANK_PENALTY
                if (best == null || s > best) best = s
            }
            return best
        }

        /**
         * Best general-field contribution. Each token may live in any field;
         * a candidate matches when every one of its tokens is found somewhere.
         */
        fun generalContribution(candidates: List<SearchSynonyms.Candidate>): Int? {
            if (candidates.isEmpty()) return 0
            var best: Int? = null
            for (c in candidates) {
                if (!allTokensSomewhere(c.tokens)) continue
                var s = 0
                for (t in c.tokens) {
                    s += scoreToken(t, name, weight = 40)
                    s += scoreToken(t, topics, weight = 18)
                    s += scoreToken(t, writer, weight = 22)
                    s += scoreToken(t, category, weight = 10)
                    s += scoreToken(t, subcategory, weight = 8)
                    s += scoreToken(t, displayNumber, weight = 12)
                    s += scoreToken(t, bookNumber, weight = 10)
                    s += scoreToken(t, notes, weight = 5)
                    s += scoreToken(t, letter, weight = 3)
                    s += scoreToken(t, color, weight = 3)
                }
                s -= c.rank * RANK_PENALTY
                if (best == null || s > best) best = s
            }
            return best
        }

        private fun allTokensSomewhere(tokens: List<String>): Boolean {
            for (t in tokens) {
                var found = false
                for (field in searchable) {
                    if (field.contains(t)) { found = true; break }
                }
                if (!found) return false
            }
            return true
        }

        fun matchesNumberField(tokens: List<String>, field: String): Boolean {
            if (tokens.isEmpty()) return true
            for (t in tokens) if (field != t) return false
            return true
        }

        fun scoreNumberField(tokens: List<String>, field: String, weight: Int): Int {
            if (tokens.isEmpty() || field.isEmpty()) return 0
            var s = 0
            for (t in tokens) if (field == t) s += weight * 4 + (t.length / 2)
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
            fun from(book: Book, synonyms: SearchSynonyms = SearchSynonyms.EMPTY): IndexedBook = IndexedBook(
                book = book,
                name = synonyms.augmentField(HebrewText.normalize(book.name)),
                topics = synonyms.augmentField(HebrewText.normalize(book.topics)),
                writer = synonyms.augmentField(HebrewText.normalize(book.writer)),
                letter = synonyms.augmentField(HebrewText.normalize(book.letter)),
                color = synonyms.augmentField(HebrewText.normalize(book.color)),
                category = synonyms.augmentField(HebrewText.normalize(book.category)),
                subcategory = synonyms.augmentField(
                    HebrewText.normalize(book.subcategories.joinToString(" ")),
                ),
                displayNumber = HebrewText.normalizeNumberKey(book.displayNumber),
                bookNumber = HebrewText.normalizeNumberKey(book.bookNumber),
                notes = synonyms.augmentField(HebrewText.normalize(book.notes)),
            )
        }
    }

    companion object {
        /**
         * Score subtracted per rank step of a synonym alternative. Small enough
         * to act as a tiebreaker (literal/earlier-word matches stay above
         * later-word matches) without overriding genuine relevance.
         */
        private const val RANK_PENALTY = 4
    }
}
