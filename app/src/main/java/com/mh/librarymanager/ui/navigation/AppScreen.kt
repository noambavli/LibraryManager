package com.mh.librarymanager.ui.navigation

/**
 * Closed set of screens the kiosk shell can display. Kept here rather than
 * pulling in Navigation-Compose because the topology is intentionally tiny
 * and the back-stack semantics are bespoke (kiosk swallows system back).
 */
sealed interface AppScreen {
    data object Home : AppScreen
    data object Search : AppScreen

    /** Public "request a book" form, reachable from the home page. */
    data object PublicRequests : AppScreen

    /** Public technical-support report form, reachable from the home help button. */
    data object TechSupport : AppScreen

    /** Public full view of a single announcement. */
    data class AnnouncementDetail(val announcementId: String) : AppScreen

    /** Public list of all active announcements, rendered in full. */
    data object AllAnnouncements : AppScreen

    /** Password gate guarding the management section. */
    data object ManagementGate : AppScreen

    /** Blue standby screen — logo, announcements, swipe up to open. */
    data object Attract : AppScreen

    data object ManagementHome : AppScreen
    data object BooksManagement : AppScreen
    data object OutOfOrderBooks : AppScreen
    data object ManagementHistory : AppScreen
    data object ManagementSearchHistory : AppScreen
    data object ManagementPopularBooks : AppScreen
    data object ManagementRequests : AppScreen
    data object ManagementAnnouncements : AppScreen
    data object AnnouncementEditor : AppScreen
    data object ManagementShortcuts : AppScreen
    data object ManagementMatchings : AppScreen
    data object ManagementTechSupport : AppScreen

    /** Windows Tool — backup + xlsx import/export on tablet + PC adb push. */
    data object ManagementWindowsTool : AppScreen

    /** Book editor — `bookId == null` means "create a brand new book". */
    data class BookEditor(val bookId: String?) : AppScreen

    /** Library map view for a single book (placeholder until map assets are wired). */
    data class BookLocation(val bookId: String) : AppScreen
}
