package com.mh.librarymanager.domain

import com.mh.librarymanager.search.HebrewText
import java.util.Locale

/** Map asset keys — only אוצר הספרים and בית מדרש have shelf maps. */
enum class BookPlace(val storedValue: String) {
    OTZAR("otzar"),
    BEIS_MIDRASH("beis_midrash"),
    ;

    companion object {
        fun fromStored(value: String?): BookPlace? =
            entries.firstOrNull { it.storedValue == (value ?: "") }
    }
}

/** Free-text book location with normalization and map-place resolution. */
object BookPlaceText {
    const val OTZAR_LABEL = "אוצר הספרים"
    const val BEIS_MIDRASH_LABEL = "בית מדרש"

    fun normalize(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ")

    fun normalizeKey(raw: String): String =
        HebrewText.normalize(normalize(raw)).lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    /** Read from catalog JSON / legacy enum storage. */
    fun fromStored(stored: String?): String {
        if (stored.isNullOrBlank()) return ""
        return when (stored.trim()) {
            BookPlace.OTZAR.storedValue, "otzar" -> OTZAR_LABEL
            BookPlace.BEIS_MIDRASH.storedValue, "beis_midrash" -> BEIS_MIDRASH_LABEL
            "other" -> ""
            else -> normalize(stored)
        }
    }

    fun toMapPlace(place: String): BookPlace? {
        if (normalize(place).isEmpty()) return null
        return when (normalizeKey(place)) {
            normalizeKey(OTZAR_LABEL), "otzar" -> BookPlace.OTZAR
            normalizeKey(BEIS_MIDRASH_LABEL),
            normalizeKey("בית המדרש"),
            "beis_midrash",
            -> BookPlace.BEIS_MIDRASH
            else -> null
        }
    }

    fun displayLabel(place: String): String? =
        normalize(place).takeIf { it.isNotBlank() }

    fun isBlank(place: String): Boolean =
        normalize(place).isEmpty()
}

fun Book.mapPlace(): BookPlace? = BookPlaceText.toMapPlace(place)

fun Book.displayPlace(): String? = BookPlaceText.displayLabel(place)
