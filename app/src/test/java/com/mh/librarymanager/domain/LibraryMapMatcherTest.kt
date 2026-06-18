package com.mh.librarymanager.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HebrewShelfOrderTest {
    @Test
    fun range_includes_start_middle_and_end() {
        val from = ShelfSlot("א", 1)
        val to = ShelfSlot("ח", 2)
        assertTrue(HebrewShelfOrder.isInRange(ShelfSlot("א", 1), from, to))
        assertTrue(HebrewShelfOrder.isInRange(ShelfSlot("ב", 2), from, to))
        assertTrue(HebrewShelfOrder.isInRange(ShelfSlot("ח", 2), from, to))
    }

    @Test
    fun range_excludes_before_and_after() {
        val from = ShelfSlot("א", 1)
        val to = ShelfSlot("ח", 2)
        assertFalse(HebrewShelfOrder.isInRange(ShelfSlot("ח", 3), from, to))
        assertFalse(HebrewShelfOrder.isInRange(ShelfSlot("י", 1), from, to))
    }
}

class LibraryMapMatcherTest {
    private val map = LibraryMap(
        mapId = "beis_midrash",
        place = BookPlace.BEIS_MIDRASH,
        frameWidth = 1024,
        frameHeight = 715,
        imageResId = 0,
        sections = listOf(
            LibraryMapSection(
                id = "orange_1",
                label = "א-1 - ח-2",
                color = "כתום",
                from = ShelfSlot("א", 1),
                to = ShelfSlot("ח", 2),
                hotspot = MapHotspot(282f, 75f, 135f, 68f),
            ),
        ),
    )

    @Test
    fun matches_orange_section_by_color_letter_number() {
        val book = sampleBook(letter = "ג", displayNumber = "4", color = "כתום")
        val section = LibraryMapMatcher.findSection(map, book)
        assertTrue(section?.id == "orange_1")
    }

    @Test
    fun english_color_alias_matches() {
        val book = sampleBook(letter = "ב", displayNumber = "2", color = "orange")
        val section = LibraryMapMatcher.findSection(map, book)
        assertTrue(section?.id == "orange_1")
    }

    @Test
    fun wrong_color_does_not_match() {
        val book = sampleBook(letter = "ב", displayNumber = "2", color = "כחול")
        assertTrue(LibraryMapMatcher.findSection(map, book) == null)
    }

    @Test
    fun wrong_place_does_not_match() {
        val book = sampleBook(letter = "ב", displayNumber = "2", color = "כתום", place = BookPlace.OTZAR)
        assertTrue(LibraryMapMatcher.findSection(map, book) == null)
    }

    @Test
    fun otzar_green_nun1_pins_leftmost_halacha_cell() {
        val otzar = LibraryMap(
            mapId = "otzar",
            place = BookPlace.OTZAR,
            frameWidth = 1920,
            frameHeight = 1200,
            imageResId = 0,
            sections = listOf(
                LibraryMapSection(
                    id = "green_1",
                    label = "י-4 - ל-1",
                    color = "ירוק",
                    from = ShelfSlot("י", 4),
                    to = ShelfSlot("ל", 1),
                    hotspot = MapHotspot(691f, 653f, 129f, 62f),
                ),
                LibraryMapSection(
                    id = "green_4",
                    label = "נ-1 - ע-2",
                    color = "ירוק",
                    from = ShelfSlot("נ", 1),
                    to = ShelfSlot("ע", 2),
                    hotspot = MapHotspot(286f, 653f, 129f, 62f),
                ),
            ),
        )
        val book = sampleBook(letter = "נ", displayNumber = "1", color = "ירוק", place = BookPlace.OTZAR)
        val section = LibraryMapMatcher.findSection(otzar, book)
        assertTrue(section?.id == "green_4")
        assertTrue(section?.hotspot?.x == 286f)
    }

    @Test
    fun otzar_yellow_tefila_matches_number_only_books() {
        val otzar = LibraryMap(
            mapId = "otzar",
            place = BookPlace.OTZAR,
            frameWidth = 1920,
            frameHeight = 1200,
            imageResId = 0,
            sections = listOf(
                LibraryMapSection(
                    id = "yellow_tefila",
                    label = "1 - 4",
                    color = "צהוב",
                    from = ShelfSlot("", 1),
                    to = ShelfSlot("", 4),
                    hotspot = MapHotspot(1719f, 889f, 62f, 131f),
                    numberOnly = true,
                ),
            ),
        )
        val book = sampleBook(letter = "", displayNumber = "3", color = "צהוב", place = BookPlace.OTZAR)
        val section = LibraryMapMatcher.findSection(otzar, book)
        assertTrue(section?.id == "yellow_tefila")
        assertTrue(section?.hotspot?.x == 1719f)
    }

    @Test
    fun number_only_section_ignores_books_with_letter() {
        val map = LibraryMap(
            mapId = "otzar",
            place = BookPlace.OTZAR,
            frameWidth = 1920,
            frameHeight = 1200,
            imageResId = 0,
            sections = listOf(
                LibraryMapSection(
                    id = "yellow_tefila",
                    label = "1 - 4",
                    color = "צהוב",
                    from = ShelfSlot("", 1),
                    to = ShelfSlot("", 4),
                    hotspot = MapHotspot(1719f, 889f, 62f, 131f),
                    numberOnly = true,
                ),
            ),
        )
        val book = sampleBook(letter = "א", displayNumber = "3", color = "צהוב", place = BookPlace.OTZAR)
        assertTrue(LibraryMapMatcher.findSection(map, book) == null)
    }

    @Test
    fun otzar_white_yesod_matches_number_only_books() {
        val otzar = LibraryMap(
            mapId = "otzar",
            place = BookPlace.OTZAR,
            frameWidth = 1920,
            frameHeight = 1200,
            imageResId = 0,
            sections = listOf(
                LibraryMapSection(
                    id = "white_yesod_1",
                    label = "1 - 6",
                    color = "לבן",
                    from = ShelfSlot("", 1),
                    to = ShelfSlot("", 6),
                    hotspot = MapHotspot(941f, 83f, 126f, 58f),
                    numberOnly = true,
                ),
                LibraryMapSection(
                    id = "white_yesod_2",
                    label = "7 - 12",
                    color = "לבן",
                    from = ShelfSlot("", 7),
                    to = ShelfSlot("", 12),
                    hotspot = MapHotspot(1076f, 82f, 127f, 59f),
                    numberOnly = true,
                ),
            ),
        )
        val book = sampleBook(letter = "", displayNumber = "4", color = "לבן", place = BookPlace.OTZAR)
        assertTrue(LibraryMapMatcher.findSection(otzar, book)?.id == "white_yesod_1")
        val book2 = sampleBook(letter = "", displayNumber = "9", color = "white", place = BookPlace.OTZAR)
        assertTrue(LibraryMapMatcher.findSection(otzar, book2)?.id == "white_yesod_2")
    }

    @Test
    fun otzar_pink_moedim_matches_number_only_books() {
        val otzar = LibraryMap(
            mapId = "otzar",
            place = BookPlace.OTZAR,
            frameWidth = 1920,
            frameHeight = 1200,
            imageResId = 0,
            sections = listOf(
                LibraryMapSection(
                    id = "pink_moedim",
                    label = "1 - 7",
                    color = "ורוד",
                    from = ShelfSlot("", 1),
                    to = ShelfSlot("", 7),
                    hotspot = MapHotspot(1649f, 1109f, 129f, 60f),
                    numberOnly = true,
                ),
            ),
        )
        val book = sampleBook(letter = "", displayNumber = "5", color = "ורוד", place = BookPlace.OTZAR)
        assertTrue(LibraryMapMatcher.findSection(otzar, book)?.id == "pink_moedim")
    }

    private fun sampleBook(
        letter: String,
        displayNumber: String,
        color: String,
        place: BookPlace = BookPlace.BEIS_MIDRASH,
    ) = Book(
        id = "1",
        logicalBookId = "1",
        version = 1,
        isLatest = true,
        name = "Test",
        topics = "",
        writer = "",
        bookNumber = "1",
        displayNumber = displayNumber,
        letter = letter,
        color = color,
        category = "",
        subcategories = emptyList(),
        notes = "",
        place = place,
        state = BookState.AVAILABLE,
        parentBookId = null,
        relations = emptyList(),
        createdAt = 0L,
        updatedAt = 0L,
    )
}
