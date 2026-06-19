package com.mh.librarymanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookPlaceTextTest {

    @Test
    fun normalize_collapsesSpaces() {
        assertEquals("אוצר הספרים", BookPlaceText.normalize("  אוצר   הספרים  "))
    }

    @Test
    fun toMapPlace_recognizesOtzarWithExtraSpaces() {
        assertEquals(BookPlace.OTZAR, BookPlaceText.toMapPlace("אוצר  הספרים   "))
    }

    @Test
    fun toMapPlace_recognizesBeisMidrashVariants() {
        assertEquals(BookPlace.BEIS_MIDRASH, BookPlaceText.toMapPlace("בית מדרש"))
        assertEquals(BookPlace.BEIS_MIDRASH, BookPlaceText.toMapPlace("בית  המדרש"))
    }

    @Test
    fun toMapPlace_unknownPlaceReturnsNull() {
        assertNull(BookPlaceText.toMapPlace("מחסן"))
        assertNull(BookPlaceText.toMapPlace(""))
    }

    @Test
    fun fromStored_migratesLegacyKeys() {
        assertEquals(BookPlaceText.OTZAR_LABEL, BookPlaceText.fromStored("otzar"))
        assertEquals(BookPlaceText.BEIS_MIDRASH_LABEL, BookPlaceText.fromStored("beis_midrash"))
    }
}
