package com.mh.librarymanager.domain

enum class BookState(val storedValue: String) {
    AVAILABLE("available"),
    UNAVAILABLE("unavailable"),
    IN_REPAIR("in_repair");

    companion object {
        fun fromStored(value: String?): BookState =
            entries.firstOrNull { it.storedValue == value } ?: AVAILABLE
    }
}
