package com.mh.librarymanager.ui.navigation

/**
 * Closed set of screens the kiosk shell can display. Kept here rather than
 * pulling in Navigation-Compose because the topology is intentionally tiny
 * and the back-stack semantics are bespoke (kiosk swallows system back).
 */
sealed interface AppScreen {
    data object Home : AppScreen
    data object Search : AppScreen

    /** Password gate guarding the management section. */
    data object ManagementGate : AppScreen
    data object ManagementHome : AppScreen
    data object BooksManagement : AppScreen

    /** Book editor — `bookId == null` means "create a brand new book". */
    data class BookEditor(val bookId: String?) : AppScreen
}
