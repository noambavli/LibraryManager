package com.mh.librarymanager.search

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden vectors must stay in sync with
 * `desktop/tests/hebrew_normalize_vectors.json`.
 */
class HebrewTextTest {

    @Test
    fun normalizeShortcut_preservesGershayim() {
        assertEquals("שמו״ת", HebrewText.normalizeShortcut("שמו״ת"))
        assertEquals("מס׳", HebrewText.normalizeShortcut("מס׳"))
        assertEquals("שמות", HebrewText.normalize("שמו״ת"))
        assertEquals("מס", HebrewText.normalize("מס׳"))
    }

    @Test
    fun normalize_matchesGoldenVectors() {
        NORMALIZE_VECTORS.forEach { (input, expected) ->
            assertEquals("normalize($input)", expected, HebrewText.normalize(input))
        }
    }

    @Test
    fun normalizeNumberKey_matchesGoldenVectors() {
        NUMBER_KEY_VECTORS.forEach { (input, expected) ->
            assertEquals("normalizeNumberKey($input)", expected, HebrewText.normalizeNumberKey(input))
        }
    }

    private companion object {
        val NORMALIZE_VECTORS = listOf(
            "" to "",
            "בראשית" to "בראשית",
            "עֲבֹדָה" to "עבדה",
            "ךְלִי" to "כלי",
            "אבן׳ עזרא" to "אבנ עזרא",
            "אבן-עזרא" to "אבנ עזרא",
            "  Hello  World  " to "hello world",
            "שֵׁם הספר" to "שמ הספר",
            "תת־קטגוריה" to "תת קטגוריה",
            "רמב\"ן" to "רמבנ",
            "מספר 12" to "מספר 12",
        )

        val NUMBER_KEY_VECTORS = listOf(
            "" to "",
            "   " to "",
            "5" to "5",
            "05" to "5",
            "0042" to "42",
            "0" to "0",
            "000" to "0",
            " 12 " to "12",
        )
    }
}
