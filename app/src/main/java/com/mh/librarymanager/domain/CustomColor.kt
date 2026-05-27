package com.mh.librarymanager.domain

/**
 * A user-defined book color: a Hebrew (or any) display name plus the ARGB
 * value the chip should be painted with. Stored separately from books so the
 * palette is global and stable across imports.
 */
data class CustomColor(
    val name: String,
    /** 0xAARRGGBB packed into a Long so JSON storage stays trivial. */
    val argb: Long,
)
