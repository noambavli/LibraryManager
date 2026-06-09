package com.mh.librarymanager.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.ui.home.HomeScreen
import com.mh.librarymanager.ui.management.BookEditorScreen
import com.mh.librarymanager.ui.management.BooksManagementScreen
import com.mh.librarymanager.ui.management.BooksManagementViewModel
import com.mh.librarymanager.ui.management.HistoryScreen
import com.mh.librarymanager.ui.management.HistoryViewModel
import com.mh.librarymanager.ui.management.InactivityScope
import com.mh.librarymanager.ui.announcements.AllAnnouncementsScreen
import com.mh.librarymanager.ui.announcements.AnnouncementDetailScreen
import com.mh.librarymanager.ui.announcements.AnnouncementsViewModel
import com.mh.librarymanager.ui.management.AnnouncementEditorScreen
import com.mh.librarymanager.ui.management.AnnouncementsManagementScreen
import com.mh.librarymanager.ui.management.AnnouncementsManagementViewModel
import com.mh.librarymanager.ui.management.ManagementDashboardScreen
import com.mh.librarymanager.ui.management.ManagementSession
import com.mh.librarymanager.ui.management.PasswordScreen
import com.mh.librarymanager.ui.management.RequestsManagementScreen
import com.mh.librarymanager.ui.management.RequestsManagementViewModel
import com.mh.librarymanager.ui.management.ShortcutsManagementScreen
import com.mh.librarymanager.ui.management.ShortcutsManagementViewModel
import com.mh.librarymanager.ui.management.TechSupportManagementScreen
import com.mh.librarymanager.ui.management.TechSupportManagementViewModel
import com.mh.librarymanager.ui.requests.PublicRequestScreen
import com.mh.librarymanager.ui.requests.PublicRequestViewModel
import com.mh.librarymanager.ui.support.TechSupportScreen
import com.mh.librarymanager.ui.support.TechSupportViewModel
import com.mh.librarymanager.ui.navigation.AppNavController
import com.mh.librarymanager.ui.navigation.AppScreen
import com.mh.librarymanager.ui.navigation.rememberAppNavController
import com.mh.librarymanager.ui.search.NoSystemKeyboard
import com.mh.librarymanager.ui.search.SearchScreen
import com.mh.librarymanager.ui.search.SearchViewModel

/**
 * Single top-level composable for the kiosk. Owns the nav stack, the
 * management session, and the two ViewModels backing search + management.
 *
 * The [onRegisterBackHandler] hook lets [MainActivity] route the activity's
 * back press into our hand-rolled navigator without re-grabbing focus.
 */
@Composable
fun AppRoot(
    searchViewModel: SearchViewModel,
    managementViewModel: BooksManagementViewModel,
    historyViewModel: HistoryViewModel,
    publicRequestViewModel: PublicRequestViewModel,
    requestsManagementViewModel: RequestsManagementViewModel,
    announcementsViewModel: AnnouncementsViewModel,
    announcementsManagementViewModel: AnnouncementsManagementViewModel,
    shortcutsManagementViewModel: ShortcutsManagementViewModel,
    techSupportViewModel: TechSupportViewModel,
    techSupportManagementViewModel: TechSupportManagementViewModel,
    managementSession: ManagementSession,
    onRegisterBackHandler: (handler: (() -> Boolean)) -> Unit,
) {
    val nav: AppNavController = rememberAppNavController(AppScreen.Home)
    val searchVm: SearchViewModel = searchViewModel
    val managementVm: BooksManagementViewModel = managementViewModel
    val historyVm: HistoryViewModel = historyViewModel
    val publicRequestVm: PublicRequestViewModel = publicRequestViewModel
    val requestsManagementVm: RequestsManagementViewModel = requestsManagementViewModel
    val announcementsVm: AnnouncementsViewModel = announcementsViewModel
    val announcementsManagementVm: AnnouncementsManagementViewModel = announcementsManagementViewModel
    val shortcutsManagementVm: ShortcutsManagementViewModel = shortcutsManagementViewModel
    val techSupportVm: TechSupportViewModel = techSupportViewModel
    val techSupportManagementVm: TechSupportManagementViewModel = techSupportManagementViewModel
    val session: ManagementSession = managementSession

    SideEffect {
        onRegisterBackHandler {
            // Pop within the app stack if we can; otherwise swallow the press
            // (kiosk must not escape to the launcher).
            nav.pop()
        }
    }

    // If the activity is recreated mid-flow (rare under kiosk) the session
    // could be authenticated while the navigator landed on Home — keep them
    // in sync by resetting auth when the user is on a public screen.
    LaunchedEffect(nav.current) {
        val onPublic = nav.current is AppScreen.Home ||
            nav.current is AppScreen.Search ||
            nav.current is AppScreen.PublicRequests ||
            nav.current is AppScreen.TechSupport ||
            nav.current is AppScreen.AnnouncementDetail ||
            nav.current is AppScreen.AllAnnouncements
        if (onPublic && session.isAuthenticated) {
            session.logout()
        }
    }

    val recentlyAdded by searchVm.recentlyAdded.collectAsStateWithLifecycle()
    val customColors by searchVm.customColors.collectAsStateWithLifecycle()
    val activeAnnouncements by announcementsVm.active.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        NoSystemKeyboard {
        when (val current = nav.current) {
            AppScreen.Home -> HomeScreen(
                recentlyAdded = recentlyAdded,
                customColors = customColors,
                announcements = activeAnnouncements,
                onOpenSearch = { nav.push(AppScreen.Search) },
                onOpenManagement = { nav.push(AppScreen.ManagementGate) },
                onOpenRequests = { nav.push(AppScreen.PublicRequests) },
                onOpenTechSupport = { nav.push(AppScreen.TechSupport) },
                onOpenAnnouncement = { id -> nav.push(AppScreen.AnnouncementDetail(id)) },
                onOpenAllAnnouncements = { nav.push(AppScreen.AllAnnouncements) },
            )

            AppScreen.Search -> SearchScreen(
                viewModel = searchVm,
                onBack = { nav.pop() },
            )

            AppScreen.PublicRequests -> PublicRequestScreen(
                viewModel = publicRequestVm,
                onBack = { nav.pop() },
            )

            AppScreen.TechSupport -> TechSupportScreen(
                viewModel = techSupportVm,
                onBack = { nav.pop() },
            )

            is AppScreen.AnnouncementDetail -> AnnouncementDetailScreen(
                viewModel = announcementsVm,
                announcementId = current.announcementId,
                onBack = { nav.pop() },
            )

            AppScreen.AllAnnouncements -> AllAnnouncementsScreen(
                viewModel = announcementsVm,
                onBack = { nav.pop() },
            )

            AppScreen.ManagementGate -> PasswordScreen(
                session = session,
                onBack = { nav.pop() },
                onUnlocked = { nav.replaceTop(AppScreen.ManagementHome) },
            )

            AppScreen.ManagementHome -> ManagementGuard(session = session, nav = nav) {
                ManagementDashboardScreen(
                    onOpenBooks = { nav.push(AppScreen.BooksManagement) },
                    onOpenHistory = { nav.push(AppScreen.ManagementHistory) },
                    onOpenRequests = { nav.push(AppScreen.ManagementRequests) },
                    onOpenAnnouncements = { nav.push(AppScreen.ManagementAnnouncements) },
                    onOpenShortcuts = { nav.push(AppScreen.ManagementShortcuts) },
                    onOpenTechSupport = { nav.push(AppScreen.ManagementTechSupport) },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Home)
                    },
                )
            }

            AppScreen.ManagementRequests -> ManagementGuard(session = session, nav = nav) {
                RequestsManagementScreen(
                    viewModel = requestsManagementVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Home)
                    },
                )
            }

            AppScreen.ManagementAnnouncements -> ManagementGuard(session = session, nav = nav) {
                AnnouncementsManagementScreen(
                    viewModel = announcementsManagementVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Home)
                    },
                    onAdd = { nav.push(AppScreen.AnnouncementEditor) },
                )
            }

            AppScreen.AnnouncementEditor -> ManagementGuard(session = session, nav = nav) {
                AnnouncementEditorScreen(
                    viewModel = announcementsManagementVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Home)
                    },
                )
            }

            AppScreen.ManagementShortcuts -> ManagementGuard(session = session, nav = nav) {
                ShortcutsManagementScreen(
                    viewModel = shortcutsManagementVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Home)
                    },
                )
            }

            AppScreen.ManagementTechSupport -> ManagementGuard(session = session, nav = nav) {
                TechSupportManagementScreen(
                    viewModel = techSupportManagementVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Home)
                    },
                )
            }

            AppScreen.ManagementHistory -> ManagementGuard(session = session, nav = nav) {
                HistoryScreen(
                    viewModel = historyVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Home)
                    },
                )
            }

            AppScreen.BooksManagement -> ManagementGuard(session = session, nav = nav) {
                BooksManagementScreen(
                    viewModel = managementVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Home)
                    },
                    onOpenEditor = { id -> nav.push(AppScreen.BookEditor(id)) },
                )
            }

            is AppScreen.BookEditor -> ManagementGuard(session = session, nav = nav) {
                BookEditorScreen(
                    viewModel = managementVm,
                    bookId = current.bookId,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Home)
                    },
                    onReplaceWith = { newId ->
                        nav.replaceTop(AppScreen.BookEditor(newId))
                    },
                )
            }
        }
        }
    }
}

/**
 * Reusable wrapper that:
 *  - Forces management screens to redirect home if the session is dropped
 *    (e.g. auto-logout fires).
 *  - Wraps the content in [InactivityScope] for idle tracking.
 */
@Composable
private fun ManagementGuard(
    session: ManagementSession,
    nav: AppNavController,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(session.isAuthenticated) {
        if (!session.isAuthenticated) nav.resetTo(AppScreen.Home)
    }
    if (!session.isAuthenticated) return

    InactivityScope(
        session = session,
        onAutoLogout = { nav.resetTo(AppScreen.Home) },
    ) {
        content()
    }
}
