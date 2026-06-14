package com.mh.librarymanager.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.R
import com.mh.librarymanager.ui.home.AttractScreen
import com.mh.librarymanager.ui.home.HomeScreen
import com.mh.librarymanager.ui.location.BookLocationScreen
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
import com.mh.librarymanager.ui.management.CatalogTransferScreen
import com.mh.librarymanager.ui.management.CatalogTransferViewModel
import com.mh.librarymanager.ui.management.ImportConfirmDialog
import com.mh.librarymanager.ui.management.ImportSummaryScreen
import com.mh.librarymanager.ui.management.ManagementDashboardScreen
import com.mh.librarymanager.ui.management.OutOfOrderBooksScreen
import com.mh.librarymanager.ui.management.ManagementSession
import com.mh.librarymanager.ui.management.PopularBooksScreen
import com.mh.librarymanager.ui.management.PopularBooksViewModel
import com.mh.librarymanager.ui.management.PasswordScreen
import com.mh.librarymanager.ui.management.RequestsManagementScreen
import com.mh.librarymanager.ui.management.RequestsManagementViewModel
import com.mh.librarymanager.ui.management.SearchHistoryScreen
import com.mh.librarymanager.ui.management.SearchHistoryViewModel
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    searchHistoryViewModel: SearchHistoryViewModel,
    popularBooksViewModel: PopularBooksViewModel,
    publicRequestViewModel: PublicRequestViewModel,
    requestsManagementViewModel: RequestsManagementViewModel,
    announcementsViewModel: AnnouncementsViewModel,
    announcementsManagementViewModel: AnnouncementsManagementViewModel,
    shortcutsManagementViewModel: ShortcutsManagementViewModel,
    techSupportViewModel: TechSupportViewModel,
    techSupportManagementViewModel: TechSupportManagementViewModel,
    catalogTransferViewModel: CatalogTransferViewModel,
    managementSession: ManagementSession,
    onRegisterBackHandler: (handler: (() -> Boolean)) -> Unit,
) {
    val nav: AppNavController = rememberAppNavController(AppScreen.Attract)
    val searchVm: SearchViewModel = searchViewModel
    val managementVm: BooksManagementViewModel = managementViewModel
    val historyVm: HistoryViewModel = historyViewModel
    val searchHistoryVm: SearchHistoryViewModel = searchHistoryViewModel
    val popularBooksVm: PopularBooksViewModel = popularBooksViewModel
    val publicRequestVm: PublicRequestViewModel = publicRequestViewModel
    val requestsManagementVm: RequestsManagementViewModel = requestsManagementViewModel
    val announcementsVm: AnnouncementsViewModel = announcementsViewModel
    val announcementsManagementVm: AnnouncementsManagementViewModel = announcementsManagementViewModel
    val shortcutsManagementVm: ShortcutsManagementViewModel = shortcutsManagementViewModel
    val techSupportVm: TechSupportViewModel = techSupportViewModel
    val techSupportManagementVm: TechSupportManagementViewModel = techSupportManagementViewModel
    val catalogTransferVm: CatalogTransferViewModel = catalogTransferViewModel
    val session: ManagementSession = managementSession
    val adbPending by catalogTransferVm.adbPending.collectAsStateWithLifecycle()
    val adbConfirming by catalogTransferVm.adbConfirming.collectAsStateWithLifecycle()
    fun returnToAttract() {
        session.logout()
        searchVm.finalizePublicSearchSession()
        nav.resetTo(AppScreen.Attract)
    }

    LaunchedEffect(Unit) {
        catalogTransferVm.refreshAdbPending()
    }

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
        // BookLocation is intentionally excluded: it is reachable from BOTH
        // public screens and management (book list / editor / announcement
        // editor). Logging out here would kick staff back to attract when they
        // open a book's map, so it is not treated as a public-only screen.
        val onPublic = nav.current is AppScreen.Attract ||
            nav.current is AppScreen.Home ||
            nav.current is AppScreen.Search ||
            nav.current is AppScreen.PublicRequests ||
            nav.current is AppScreen.TechSupport ||
            nav.current is AppScreen.AnnouncementDetail ||
            nav.current is AppScreen.AllAnnouncements
        if (onPublic && session.isAuthenticated) {
            session.logout()
        }
    }

    // Power / side-button sleep wakes back to the attract screen (unless a
    // system overlay like the file picker or an adb-import dialog is open).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, session, adbPending) {
        var wasStopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> wasStopped = true
                Lifecycle.Event.ON_RESUME -> {
                    if (wasStopped &&
                        !session.isExternalTaskActive() &&
                        adbPending == null
                    ) {
                        returnToAttract()
                    }
                    wasStopped = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val recentlyAdded by searchVm.recentlyAdded.collectAsStateWithLifecycle()
    val catalogLoaded by searchVm.catalogLoaded.collectAsStateWithLifecycle()
    val customColors by searchVm.customColors.collectAsStateWithLifecycle()
    val booksById by searchVm.booksById.collectAsStateWithLifecycle()
    val activeAnnouncements by announcementsVm.active.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as LibraryApp
    val scope = rememberCoroutineScope()
    val openBookLocation: (String) -> Unit = { bookId ->
        scope.launch {
            withContext(Dispatchers.IO) {
                app.bookLocationPressStore.recordPress(bookId)
            }
        }
        nav.push(AppScreen.BookLocation(bookId))
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        NoSystemKeyboard {
        PublicIdleScope(
            enabled = nav.current.tracksPublicIdle() &&
                nav.current !is AppScreen.Attract &&
                adbPending == null,
            onIdle = { returnToAttract() },
        ) {
        when (val current = nav.current) {
            AppScreen.Attract -> AttractScreen(
                announcements = activeAnnouncements,
                onOpenHome = { nav.resetTo(AppScreen.Home) },
            )

            AppScreen.Home -> HomeScreen(
                recentlyAdded = recentlyAdded,
                catalogLoaded = catalogLoaded,
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
                onOpenBookLocation = openBookLocation,
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
                booksById = booksById,
                customColors = customColors,
                onBack = { nav.pop() },
                onOpenBookLocation = openBookLocation,
            )

            AppScreen.AllAnnouncements -> AllAnnouncementsScreen(
                viewModel = announcementsVm,
                booksById = booksById,
                customColors = customColors,
                onBack = { nav.pop() },
                onOpenBookLocation = openBookLocation,
            )

            is AppScreen.BookLocation -> BookLocationScreen(
                book = booksById[current.bookId],
                catalogLoaded = catalogLoaded,
                onBack = { nav.pop() },
            )

            AppScreen.ManagementGate -> PasswordScreen(
                session = session,
                onBack = { nav.pop() },
                onUnlocked = { nav.replaceTop(AppScreen.ManagementHome) },
            )

            AppScreen.ManagementHome -> ManagementGuard(session = session, nav = nav) {
                val outOfOrderCount by managementVm.outOfOrderCount.collectAsStateWithLifecycle()
                val catalogLoaded by managementVm.catalogLoaded.collectAsStateWithLifecycle()
                ManagementDashboardScreen(
                    outOfOrderCount = outOfOrderCount,
                    catalogLoaded = catalogLoaded,
                    onOpenBooks = { nav.push(AppScreen.BooksManagement) },
                    onOpenOutOfOrder = { nav.push(AppScreen.OutOfOrderBooks) },
                    onOpenHistory = { nav.push(AppScreen.ManagementHistory) },
                    onOpenSearchHistory = { nav.push(AppScreen.ManagementSearchHistory) },
                    onOpenPopularBooks = { nav.push(AppScreen.ManagementPopularBooks) },
                    onOpenRequests = { nav.push(AppScreen.ManagementRequests) },
                    onOpenAnnouncements = { nav.push(AppScreen.ManagementAnnouncements) },
                    onOpenShortcuts = { nav.push(AppScreen.ManagementShortcuts) },
                    onOpenTechSupport = { nav.push(AppScreen.ManagementTechSupport) },
                    onOpenCatalogTransfer = { nav.push(AppScreen.ManagementCatalogTransfer) },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                )
            }

            AppScreen.ManagementCatalogTransfer -> ManagementGuard(session = session, nav = nav) {
                CatalogTransferScreen(
                    viewModel = catalogTransferVm,
                    session = session,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                    onOpenSummary = { nav.push(AppScreen.ManagementImportSummary) },
                )
            }

            AppScreen.ManagementImportSummary -> ManagementGuard(session = session, nav = nav) {
                ImportSummaryScreen(
                    viewModel = catalogTransferVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                )
            }

            AppScreen.ManagementRequests -> ManagementGuard(session = session, nav = nav) {
                RequestsManagementScreen(
                    viewModel = requestsManagementVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                )
            }

            AppScreen.ManagementAnnouncements -> ManagementGuard(session = session, nav = nav) {
                AnnouncementsManagementScreen(
                    viewModel = announcementsManagementVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
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
                        nav.resetTo(AppScreen.Attract)
                    },
                    onOpenBookLocation = openBookLocation,
                )
            }

            AppScreen.ManagementShortcuts -> ManagementGuard(session = session, nav = nav) {
                ShortcutsManagementScreen(
                    viewModel = shortcutsManagementVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                )
            }

            AppScreen.ManagementTechSupport -> ManagementGuard(session = session, nav = nav) {
                TechSupportManagementScreen(
                    viewModel = techSupportManagementVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                )
            }

            AppScreen.ManagementHistory -> ManagementGuard(session = session, nav = nav) {
                HistoryScreen(
                    viewModel = historyVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                )
            }

            AppScreen.ManagementSearchHistory -> ManagementGuard(session = session, nav = nav) {
                SearchHistoryScreen(
                    viewModel = searchHistoryVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                )
            }

            AppScreen.ManagementPopularBooks -> ManagementGuard(session = session, nav = nav) {
                PopularBooksScreen(
                    viewModel = popularBooksVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                )
            }

            AppScreen.BooksManagement -> ManagementGuard(session = session, nav = nav) {
                BooksManagementScreen(
                    viewModel = managementVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                    onOpenEditor = { id -> nav.push(AppScreen.BookEditor(id)) },
                    onOpenBookLocation = openBookLocation,
                )
            }

            AppScreen.OutOfOrderBooks -> ManagementGuard(session = session, nav = nav) {
                OutOfOrderBooksScreen(
                    viewModel = managementVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                    onOpenEditor = { id -> nav.push(AppScreen.BookEditor(id)) },
                    onOpenBookLocation = openBookLocation,
                )
            }

            is AppScreen.BookEditor -> ManagementGuard(session = session, nav = nav) {
                BookEditorScreen(
                    viewModel = managementVm,
                    bookId = current.bookId,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                )
            }
        }
        }
        }

        adbPending?.let { preview ->
            ImportConfirmDialog(
                preview = preview,
                title = stringResource(R.string.catalog_transfer_adb_confirm_title),
                fileLabel = preview.meta?.fileLabel(),
                onCancel = { catalogTransferVm.cancelAdbPending() },
                onConfirm = { catalogTransferVm.confirmAdbPending() },
                confirmEnabled = !adbConfirming,
            )
        }
    }
}

private fun AppScreen.tracksPublicIdle(): Boolean = when (this) {
    AppScreen.ManagementHome,
    AppScreen.BooksManagement,
    AppScreen.OutOfOrderBooks,
    AppScreen.ManagementHistory,
    AppScreen.ManagementSearchHistory,
    AppScreen.ManagementPopularBooks,
    AppScreen.ManagementRequests,
    AppScreen.ManagementAnnouncements,
    AppScreen.AnnouncementEditor,
    AppScreen.ManagementShortcuts,
    AppScreen.ManagementTechSupport,
    AppScreen.ManagementCatalogTransfer,
    AppScreen.ManagementImportSummary,
    is AppScreen.BookEditor -> false
    else -> true
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
        if (!session.isAuthenticated) nav.resetTo(AppScreen.Attract)
    }
    if (!session.isAuthenticated) return

    InactivityScope(
        session = session,
        onAutoLogout = { nav.resetTo(AppScreen.Attract) },
    ) {
        content()
    }
}
