package com.mh.librarymanager.domain

import com.mh.librarymanager.search.HebrewText

/**
 * Catalog quality problems — missing fields, swapped letter/number, duplicates,
 * broken parent links, and other quirks that make books hard to find.
 */
enum class BookOrderIssue {
    MISSING_NAME,
    MISSING_WRITER,
    MISSING_LETTER,
    MISSING_DISPLAY_NUMBER,
    MISSING_SYSTEM_NUMBER,
    MISSING_CATEGORY,
    NUMBER_IN_LETTER_FIELD,
    LETTER_IN_DISPLAY_NUMBER,
    LETTER_IN_SYSTEM_NUMBER,
    INVALID_SYSTEM_NUMBER,
    DUPLICATE_SYSTEM_NUMBER,
    /** Another row matches on every compared field (any difference = not a duplicate). */
    DUPLICATE_RECORD,
    UNKNOWN_PARENT,
    SELF_PARENT,
    PLACE_NOT_SET,
    ;

    val filterGroup: OutOfOrderFilter
        get() = when (this) {
            MISSING_NAME, MISSING_WRITER, MISSING_LETTER,
            MISSING_DISPLAY_NUMBER, MISSING_SYSTEM_NUMBER, MISSING_CATEGORY,
            -> OutOfOrderFilter.MISSING

            NUMBER_IN_LETTER_FIELD, LETTER_IN_DISPLAY_NUMBER, LETTER_IN_SYSTEM_NUMBER,
            -> OutOfOrderFilter.SWAPPED

            DUPLICATE_SYSTEM_NUMBER, DUPLICATE_RECORD,
            -> OutOfOrderFilter.DUPLICATE

            INVALID_SYSTEM_NUMBER, UNKNOWN_PARENT, SELF_PARENT, PLACE_NOT_SET,
            -> OutOfOrderFilter.OTHER
        }
}

enum class OutOfOrderFilter {
    ALL,
    MISSING,
    SWAPPED,
    DUPLICATE,
    OTHER,
}

data class OutOfOrderBook(
    val book: Book,
    val issues: List<BookOrderIssue>,
)

object BookOrderIssues {

    private val HEBREW = Regex("""[\u0590-\u05FF\u05F0-\u05F4]""")

    fun findOutOfOrder(books: List<Book>): List<OutOfOrderBook> {
        // Build the catalog context ONCE — not per book — or this is O(n^2) over
        // the whole catalog (8k+ books with Hebrew normalization = minutes of CPU).
        val ctx = CatalogContext.build(books)
        return books
            .mapNotNull { book ->
                val issues = issuesFor(book, ctx)
                if (issues.isEmpty()) null else OutOfOrderBook(book, issues)
            }
            .sortedWith(
                compareBy<OutOfOrderBook> { it.issues.size }.reversed()
                    .thenBy { it.book.name.ifBlank { it.book.bookNumber } },
            )
    }

    fun filterEntries(
        entries: List<OutOfOrderBook>,
        filter: OutOfOrderFilter,
        issueFilter: BookOrderIssue? = null,
    ): List<OutOfOrderBook> {
        val byGroup = when (filter) {
            OutOfOrderFilter.ALL -> entries
            else -> entries.filter { entry ->
                entry.issues.any { it.filterGroup == filter }
            }
        }
        if (issueFilter == null) return byGroup
        return byGroup.filter { issueFilter in it.issues }
    }

    fun countByFilter(entries: List<OutOfOrderBook>): Map<OutOfOrderFilter, Int> =
        OutOfOrderFilter.entries.associateWith { filter ->
            if (filter == OutOfOrderFilter.ALL) {
                entries.size
            } else {
                entries.count { e -> e.issues.any { it.filterGroup == filter } }
            }
        }

    fun countByIssue(entries: List<OutOfOrderBook>): Map<BookOrderIssue, Int> =
        BookOrderIssue.entries.associateWith { issue ->
            entries.count { issue in it.issues }
        }

    /** Issue types that belong to [filter]; all types when [OutOfOrderFilter.ALL]. */
    fun issuesForGroup(filter: OutOfOrderFilter): List<BookOrderIssue> = when (filter) {
        OutOfOrderFilter.ALL -> BookOrderIssue.entries
        else -> BookOrderIssue.entries.filter { it.filterGroup == filter }
    }

    fun issuesFor(book: Book, allBooks: List<Book>): List<BookOrderIssue> =
        issuesFor(book, CatalogContext.build(allBooks))

    private fun issuesFor(book: Book, ctx: CatalogContext): List<BookOrderIssue> {
        val out = linkedSetOf<BookOrderIssue>()

        if (book.name.isBlank()) out += BookOrderIssue.MISSING_NAME

        // Named rows only — blank placeholder rows already flag MISSING_NAME.
        if (book.name.isNotBlank()) {
            if (book.writer.isBlank()) out += BookOrderIssue.MISSING_WRITER
            if (book.letter.isBlank()) out += BookOrderIssue.MISSING_LETTER
            if (book.displayNumber.isBlank()) out += BookOrderIssue.MISSING_DISPLAY_NUMBER
            if (book.bookNumber.isBlank()) out += BookOrderIssue.MISSING_SYSTEM_NUMBER
            if (book.category.isBlank()) out += BookOrderIssue.MISSING_CATEGORY

            if (book.letter.isNotBlank() && book.letter.any { it.isDigit() }) {
                out += BookOrderIssue.NUMBER_IN_LETTER_FIELD
            }
            if (fieldLooksLikeLetter(book.displayNumber)) {
                out += BookOrderIssue.LETTER_IN_DISPLAY_NUMBER
            }
        }

        val systemLooksLikeLetter = fieldLooksLikeLetter(book.bookNumber)
        if (systemLooksLikeLetter) {
            out += BookOrderIssue.LETTER_IN_SYSTEM_NUMBER
        } else if (book.bookNumber.isNotBlank() && !looksLikeSystemNumber(book.bookNumber)) {
            out += BookOrderIssue.INVALID_SYSTEM_NUMBER
        }

        // Shelf-position duplicates (same letter + display number) are allowed:
        // multiple volumes legitimately share a shelf slot, so we don't flag them.
        systemNumberKey(book)?.let { key ->
            if ((ctx.systemNumberCounts[key] ?: 0) > 1) {
                out += BookOrderIssue.DUPLICATE_SYSTEM_NUMBER
            }
        }
        duplicateFingerprint(book)?.let { fp ->
            if ((ctx.fingerprintCounts[fp] ?: 0) > 1) {
                out += BookOrderIssue.DUPLICATE_RECORD
            }
        }

        val parentId = book.parentBookId
        when {
            parentId == book.id -> out += BookOrderIssue.SELF_PARENT
            !parentId.isNullOrBlank() && parentId !in ctx.knownIds ->
                out += BookOrderIssue.UNKNOWN_PARENT
        }

        if (book.place == BookPlace.UNSPECIFIED && book.name.isNotBlank()) {
            out += BookOrderIssue.PLACE_NOT_SET
        }

        return out.toList()
    }

    /**
     * Full-row duplicate fingerprint. Any differing field (even one) means the
     * books are not duplicates. Missing values are part of the fingerprint but
     * never treated as duplicate *keys* for shelf/system checks.
     */
    internal fun duplicateFingerprint(book: Book): String? {
        if (book.name.isBlank()) return null
        val parts = listOf(
            HebrewText.normalize(book.name),
            HebrewText.normalize(book.writer),
            HebrewText.normalize(book.letter),
            normalizeNumberKey(book.displayNumber),
            normalizeNumberKey(book.bookNumber),
            HebrewText.normalize(book.category),
            HebrewText.normalize(book.topics),
            HebrewText.normalize(book.color),
            book.place.storedValue,
            book.state.storedValue,
            book.parentBookId.orEmpty(),
            book.parentBookName,
            book.subcategories.map { HebrewText.normalize(it) }.sorted().joinToString("|"),
            book.relations.map { HebrewText.normalize(it) }.sorted().joinToString("|"),
            HebrewText.normalize(book.notes),
        )
        return parts.joinToString("\u0000")
    }

    internal fun systemNumberKey(book: Book): String? {
        val key = normalizeNumberKey(book.bookNumber)
        return key.ifEmpty { null }
    }

    internal fun normalizeNumberKey(value: String): String =
        HebrewText.normalizeNumberKey(value)

    /** Non-empty value with letters but no digits — likely belongs in [Book.letter]. */
    internal fun fieldLooksLikeLetter(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.any { it.isDigit() }) return false
        return HEBREW.containsMatchIn(trimmed) || trimmed.all { it.isLetter() }
    }

    /** System numbers are expected to be numeric (often zero-padded). */
    internal fun looksLikeSystemNumber(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        if (HEBREW.containsMatchIn(trimmed)) return false
        return trimmed.all { it.isDigit() }
    }

    private data class CatalogContext(
        val knownIds: Set<String>,
        val systemNumberCounts: Map<String, Int>,
        val fingerprintCounts: Map<String, Int>,
    ) {
        companion object {
            fun build(books: List<Book>): CatalogContext {
                val systemNumberCounts = mutableMapOf<String, Int>()
                val fingerprintCounts = mutableMapOf<String, Int>()
                for (book in books) {
                    systemNumberKey(book)?.let { key ->
                        systemNumberCounts[key] = (systemNumberCounts[key] ?: 0) + 1
                    }
                    duplicateFingerprint(book)?.let { fp ->
                        fingerprintCounts[fp] = (fingerprintCounts[fp] ?: 0) + 1
                    }
                }
                return CatalogContext(
                    knownIds = books.map { it.id }.toSet(),
                    systemNumberCounts = systemNumberCounts,
                    fingerprintCounts = fingerprintCounts,
                )
            }
        }
    }
}
