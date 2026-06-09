package com.mh.librarymanager.ui.search

import androidx.annotation.StringRes
import com.mh.librarymanager.R

/**
 * Identity for every search field on screen. Used as the key for stored
 * [androidx.compose.ui.text.input.TextFieldValue]s and to route on-screen
 * keyboard input to the currently focused field.
 */
enum class SearchField(@StringRes val labelRes: Int) {
    GENERAL(R.string.search_field_general),
    NAME(R.string.search_field_name),
    TOPICS(R.string.search_field_topics),
    WRITER(R.string.search_field_writer),
    LETTER(R.string.search_field_letter),
    COLOR(R.string.search_field_color),
    CATEGORY(R.string.search_field_category),
    SUBCATEGORY(R.string.search_field_subcategory),
    DISPLAY_NUMBER(R.string.search_field_display_number),
    BOOK_NUMBER(R.string.search_field_book_number),
    NOTES(R.string.search_field_notes),
}
