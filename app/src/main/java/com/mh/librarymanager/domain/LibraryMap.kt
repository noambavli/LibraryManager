package com.mh.librarymanager.domain

import com.mh.librarymanager.search.HebrewText

data class LibraryMap(
    val mapId: String,
    val place: BookPlace,
    val frameWidth: Int,
    val frameHeight: Int,
    val imageResId: Int,
    val sections: List<LibraryMapSection>,
)

data class LibraryMapSection(
    val id: String,
    val label: String,
    val color: String,
    val hotspot: MapHotspot,
    // ---- Otzar addressing (אות + מספר range within a color) -------------
    val from: ShelfSlot = ShelfSlot("", 0),
    val to: ShelfSlot = ShelfSlot("", 0),
    /** When true, match yellow (etc.) books that have a number but no Hebrew letter. */
    val numberOnly: Boolean = false,
    // ---- Beis-Midrash addressing (עמודה + מדף within a color/area) -------
    /** Exact עמודה this section covers (e.g. "1", "ג"). Blank = column-agnostic. */
    val column: String = "",
    /** Set of עמודה values this section covers (e.g. משנה ברורה letter columns). */
    val columns: List<String> = emptyList(),
    /** Inclusive מדף range (e.g. מוסר "1-4"/"5-9"). 0..0 = shelf-agnostic. */
    val shelfFrom: Int = 0,
    val shelfTo: Int = 0,
)

data class MapHotspot(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
) {
    fun xFraction(frameWidth: Int): Float = x / frameWidth
    fun yFraction(frameHeight: Int): Float = y / frameHeight
    fun wFraction(frameWidth: Int): Float = w / frameWidth
    fun hFraction(frameHeight: Int): Float = h / frameHeight
}

object LibraryMapMatcher {
    fun findSection(map: LibraryMap, book: Book): LibraryMapSection? {
        if (book.mapPlace() != map.place) return null
        return when (map.place) {
            BookPlace.OTZAR -> findOtzarSection(map, book)
            BookPlace.BEIS_MIDRASH -> findBeisSection(map, book)
        }
    }

    private fun findOtzarSection(map: LibraryMap, book: Book): LibraryMapSection? {
        val number = HebrewText.normalizeNumberKey(book.displayNumber).toIntOrNull() ?: return null
        if (number <= 0) return null
        return map.sections.firstOrNull { section ->
            if (!MapColorLabels.matches(book.color, section.color)) return@firstOrNull false
            if (section.numberOnly) {
                book.letter.isBlank() && number in section.from.number..section.to.number
            } else {
                val slot = ShelfSlot(letter = book.letter, number = number)
                slot.isValid() && HebrewShelfOrder.isInRange(slot, section.from, section.to)
            }
        }
    }

    /**
     * Beis-Midrash books are placed by color/area + עמודה (column), with מדף
     * (shelf) used only to disambiguate the מוסר shelf-range boxes. A section
     * matches when the color/area matches and the book's column (and, when the
     * section defines one, its shelf range) fall inside the section.
     */
    private fun findBeisSection(map: LibraryMap, book: Book): LibraryMapSection? {
        if (areaKey(book.color).isEmpty()) return null
        return map.sections.firstOrNull { section ->
            areaMatches(book.color, section.color) &&
                columnMatches(book.column, section) &&
                shelfMatches(book.shelf, section)
        }
    }

    private fun columnMatches(bookColumn: String, section: LibraryMapSection): Boolean {
        val col = areaKey(bookColumn)
        return when {
            section.column.isNotBlank() -> col.isNotEmpty() && col == areaKey(section.column)
            section.columns.isNotEmpty() -> col.isNotEmpty() && section.columns.any { col == areaKey(it) }
            else -> true
        }
    }

    private fun shelfMatches(bookShelf: String, section: LibraryMapSection): Boolean {
        if (section.shelfTo <= 0) return true
        val shelf = HebrewText.normalizeNumberKey(bookShelf).toIntOrNull() ?: return false
        return shelf in section.shelfFrom..section.shelfTo
    }

    /**
     * Beis area / column identity. Beis colors are area labels that staff type
     * by hand ("מוסר", "חום - שחור", "משנה ברורה", …), so we compare on a key
     * that ignores nikud, case, spaces and hyphens to stay forgiving.
     */
    private fun areaMatches(a: String, b: String): Boolean {
        val ka = areaKey(a)
        return ka.isNotEmpty() && ka == areaKey(b)
    }

    private fun areaKey(raw: String): String =
        MapColorLabels.normalize(raw).lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("\u05be", "") // Hebrew maqaf
}
