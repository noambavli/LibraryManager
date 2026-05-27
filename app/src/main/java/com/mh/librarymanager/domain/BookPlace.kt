package com.mh.librarymanager.domain

import androidx.annotation.StringRes
import com.mh.librarymanager.R

enum class BookPlace(val storedValue: String) {
    UNSPECIFIED(""),
    OTZAR("otzar"),
    BEIS_MIDRASH("beis_midrash"),
    OTHER("other"),
    ;

    @StringRes
    fun labelRes(): Int? = when (this) {
        OTZAR -> R.string.book_place_otzar
        BEIS_MIDRASH -> R.string.book_place_beis_midrash
        OTHER -> R.string.book_place_other
        UNSPECIFIED -> null
    }

    @StringRes
    fun editorLabelRes(): Int = when (this) {
        OTZAR -> R.string.book_place_otzar
        BEIS_MIDRASH -> R.string.book_place_beis_midrash
        OTHER -> R.string.book_place_other
        UNSPECIFIED -> R.string.book_place_unspecified
    }

    companion object {
        fun fromStored(value: String?): BookPlace =
            entries.firstOrNull { it.storedValue == (value ?: "") } ?: UNSPECIFIED
    }
}
