package com.mh.librarymanager.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import com.mh.librarymanager.ui.home.HomeScreen
import com.mh.librarymanager.ui.management.BookEditorScreen
import com.mh.librarymanager.ui.management.BooksManagementScreen
import com.mh.librarymanager.ui.management.BooksManagementViewModel
import com.mh.librarymanager.ui.management.InactivityScope
import com.mh.librarymanager.ui.management.ManagementDashboardScreen
import com.mh.librarymanager.ui.management.ManagementSession
import com.mh.librarymanager.ui.management.PasswordScreen
import com.mh.librarymanager.ui.navigation.AppNavController
import com.mh.librarymanager.ui.navigation.AppScreen
import com.mh.librarymanager.ui.navigation.rememberAppNavController
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
    managementSession: ManagementSession,
    onRegisterBackHandler: (handler: (() -> Boolean)) -> Unit,
) {
    val nav: AppNavController = rememberAppNavController(AppScreen.Home)
    val searchVm: SearchViewModel = searchViewModel
    val managementVm: BooksManagementViewModel = managementViewModel
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
        val onPublic = nav.current is AppScreen.Home || nav.current is AppScreen.Search
        if (onPublic && session.isAuthenticated) {
            session.logout()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val current = nav.current) {
            AppScreen.Home -> HomeScreen(
                onOpenSearch = { nav.push(AppScreen.Search) },
                onOpenManagement = { nav.push(AppScreen.ManagementGate) },
            )

            AppScreen.Search -> SearchScreen(
                viewModel = searchVm,
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
