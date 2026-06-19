package com.mh.librarymanager.domain

import androidx.annotation.StringRes
import com.mh.librarymanager.R

/** Overview maps shown from the home page — separate from book-location hotspot maps. */
enum class HomeOverviewMapKind(val fileName: String) {
    OTZAR("otzar.png"),
    BEIS_MIDRASH("beis_midrash.png"),
    ;

    @StringRes
    fun titleRes(): Int = when (this) {
        OTZAR -> R.string.home_map_otzar
        BEIS_MIDRASH -> R.string.home_map_beis_midrash
    }
}
