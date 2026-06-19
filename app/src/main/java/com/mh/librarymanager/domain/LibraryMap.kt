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
    val from: ShelfSlot,
    val to: ShelfSlot,
    val hotspot: MapHotspot,
    /** When true, match yellow (etc.) books that have a number but no Hebrew letter. */
    val numberOnly: Boolean = false,
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
}
