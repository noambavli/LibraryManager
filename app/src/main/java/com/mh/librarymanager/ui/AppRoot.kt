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
import com.mh.librarymanager.ui.text.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.R
import com.mh.librarymanager.domain.HomeOverviewMapKind
import com.mh.librarymanager.ui.home.AttractScreen
import com.mh.librarymanager.ui.home.HomeOverviewMapScreen
import com.mh.librarymanager.ui.home.HomeScreen
import com.mh.librarymanager.ui.location.BookLocationScreen
import com.mh.librarymanager.ui.management.BookEditorScreen
import com.mh.librarymanager.ui.management.DeveloperDashboardScreen
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
import com.mh.librarymanager.ui.management.ImportConfirmDialog
import com.mh.librarymanager.ui.management.MatchingsImportConfirmDialog
import com.mh.librarymanager.ui.management.ManagementDashboardScreen
import com.mh.librarymanager.ui.management.OutOfOrderBooksScreen
import com.mh.librarymanager.ui.management.ManagementSession
import com.mh.librarymanager.ui.management.PopularBooksScreen
import com.mh.librarymanager.ui.management.ManagementDashboardViewModel
import com.mh.librarymanager.ui.management.PopularBooksViewModel
import com.mh.librarymanager.ui.management.PasswordScreen
import com.mh.librarymanager.ui.management.RequestsManagementScreen
import com.mh.librarymanager.ui.management.RequestsManagementViewModel
import com.mh.librarymanager.ui.management.SearchHistoryScreen
import com.mh.librarymanager.ui.management.SearchHistoryViewModel
import com.mh.librarymanager.ui.management.SearchMatchingsManagementScreen
import com.mh.librarymanager.ui.management.SearchMatchingsManagementViewModel
import com.mh.librarymanager.ui.management.ShortcutsManagementScreen
import com.mh.librarymanager.ui.management.ShortcutsManagementViewModel
import com.mh.librarymanager.ui.management.TechSupportManagementScreen
import com.mh.librarymanager.ui.management.TechSupportManagementViewModel
import com.mh.librarymanager.ui.management.StorageBrowserScreen
import com.mh.librarymanager.ui.management.StorageBrowserViewModel
import com.mh.librarymanager.ui.management.TextManagementScreen
import com.mh.librarymanager.ui.management.TextManagementViewModel
import com.mh.librarymanager.ui.management.ThemeManagementScreen
import com.mh.librarymanager.ui.management.ThemeManagementViewModel
import com.mh.librarymanager.ui.text.ProvideAppText
import com.mh.librarymanager.ui.theme.AppTheme
import com.mh.librarymanager.ui.theme.AppThemeState
import com.mh.librarymanager.ui.management.WindowsToolScreen
import com.mh.librarymanager.ui.management.WindowsToolViewModel
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
    searchMatchingsManagementViewModel: SearchMatchingsManagementViewModel,
    techSupportViewModel: TechSupportViewModel,
    techSupportManagementViewModel: TechSupportManagementViewModel,
    windowsToolViewModel: WindowsToolViewModel,
    storageBrowserViewModel: StorageBrowserViewModel,
    managementDashboardViewModel: ManagementDashboardViewModel,
    textManagementViewModel: TextManagementViewModel,
    themeManagementViewModel: ThemeManagementViewModel,
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
    val searchMatchingsManagementVm: SearchMatchingsManagementViewModel = searchMatchingsManagementViewModel
    val techSupportVm: TechSupportViewModel = techSupportViewModel
    val techSupportManagementVm: TechSupportManagementViewModel = techSupportManagementViewModel
    val windowsToolVm: WindowsToolViewModel = windowsToolViewModel
    val storageBrowserVm: StorageBrowserViewModel = storageBrowserViewModel
    val managementDashboardVm: ManagementDashboardViewModel = managementDashboardViewModel
    val textManagementVm: TextManagementViewModel = textManagementViewModel
    val themeManagementVm: ThemeManagementViewModel = themeManagementViewModel
    val session: ManagementSession = managementSession
    val libraryApp = LocalContext.current.applicationContext as LibraryApp
    val textOverrideStore = libraryApp.textOverrideStore
    val textOverrides by textOverrideStore.overrides.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { textOverrideStore.loadFromDisk() }
    // Apply the selected theme app-wide and keep it in sync with the store, so a
    // management change (or a backup restore) re-skins every screen instantly.
    val selectedThemeId by libraryApp.themeStore.selectedId.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { libraryApp.themeStore.loadFromDisk() }
    LaunchedEffect(selectedThemeId) {
        AppThemeState.palette = AppTheme.paletteFor(selectedThemeId)
    }
    val adbPending by windowsToolVm.adbPending.collectAsStateWithLifecycle()
    val adbConfirming by windowsToolVm.adbConfirming.collectAsStateWithLifecycle()
    val adbBeisPending by windowsToolVm.adbBeisPending.collectAsStateWithLifecycle()
    val adbBeisConfirming by windowsToolVm.adbBeisConfirming.collectAsStateWithLifecycle()
    val adbMatchingsPending by windowsToolVm.adbMatchingsPending.collectAsStateWithLifecycle()
    val adbMatchingsConfirming by windowsToolVm.adbMatchingsConfirming.collectAsStateWithLifecycle()
    val adbDialogOpen = adbPending != null || adbBeisPending != null || adbMatchingsPending != null
    fun returnToAttract() {
        session.logout()
        searchVm.finalizePublicSearchSession()
        nav.resetTo(AppScreen.Attract)
    }

    LaunchedEffect(Unit) {
        windowsToolVm.refreshAdbPending()
        windowsToolVm.refreshAdbBeisPending()
        windowsToolVm.refreshAdbMatchingsPending()
    }

    SideEffect {
        onRegisterBackHandler {
            // Pop within the app stack if we can; otherwise swallow the press
            // (kiosk must not escape to the launcher).
            if (nav.current is AppScreen.Search) {
                searchVm.finalizePublicSearchSession()
            }
            if (nav.current is AppScreen.ManagementStorageBrowser &&
                storageBrowserVm.navigateUp()
            ) {
                return@onRegisterBackHandler true
            }
            nav.pop()
            true
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
            nav.current is AppScreen.AllAnnouncements ||
            nav.current is AppScreen.HomeOverviewMap
        if (onPublic && session.isAuthenticated) {
            session.logout()
        }
    }

    // Power / side-button sleep wakes back to the attract screen (unless a
    // system overlay like the file picker or an adb-import dialog is open).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, session, adbDialogOpen) {
        var wasStopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> wasStopped = true
                Lifecycle.Event.ON_RESUME -> {
                    if (wasStopped &&
                        !session.isExternalTaskActive() &&
                        !adbDialogOpen
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

    ProvideAppText(textOverrides) {
    Surface(modifier = Modifier.fillMaxSize()) {
        NoSystemKeyboard {
        PublicIdleScope(
            enabled = nav.current.tracksPublicIdle() &&
                nav.current !is AppScreen.Attract &&
                !adbDialogOpen,
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
                onOpenOtzarMap = { nav.push(AppScreen.HomeOverviewMap(HomeOverviewMapKind.OTZAR)) },
                onOpenBeisMidrashMap = { nav.push(AppScreen.HomeOverviewMap(HomeOverviewMapKind.BEIS_MIDRASH)) },
                onOpenAnnouncement = { id -> nav.push(AppScreen.AnnouncementDetail(id)) },
                onOpenAllAnnouncements = { nav.push(AppScreen.AllAnnouncements) },
            )

            AppScreen.Search -> SearchScreen(
                viewModel = searchVm,
                onBack = {
                    searchVm.finalizePublicSearchSession()
                    nav.pop()
                },
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

            is AppScreen.HomeOverviewMap -> HomeOverviewMapScreen(
                kind = current.kind,
                onBack = { nav.pop() },
            )

            AppScreen.ManagementGate -> PasswordScreen(
                onBack = { nav.pop() },
                onUnlocked = { nav.replaceTop(AppScreen.ManagementHome) },
                validate = { session.tryUnlock(it) },
            )

            AppScreen.DeveloperGate -> ManagementGuard(session = session, nav = nav) {
                PasswordScreen(
                    onBack = { nav.pop() },
                    onUnlocked = { nav.replaceTop(AppScreen.DeveloperDashboard) },
                    validate = { session.isDeveloperKey(it) },
                    title = stringResource(R.string.developer_gate_title),
                    subtitle = stringResource(R.string.developer_gate_subtitle),
                    wrongMessage = stringResource(R.string.developer_gate_wrong),
                )
            }

            AppScreen.DeveloperDashboard -> ManagementGuard(session = session, nav = nav) {
                DeveloperDashboardScreen(
                    viewModel = windowsToolVm,
                    session = session,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                )
            }

            AppScreen.ManagementHome -> ManagementGuard(session = session, nav = nav) {
                val outOfOrderCount by managementVm.outOfOrderCount.collectAsStateWithLifecycle()
                val catalogLoaded by managementVm.catalogLoaded.collectAsStateWithLifecycle()
                val badgeCounts by managementDashboardVm.badgeCounts.collectAsStateWithLifecycle()
                ManagementDashboardScreen(
                    outOfOrderCount = outOfOrderCount,
                    catalogLoaded = catalogLoaded,
                    badgeCounts = badgeCounts,
                    onOpenBooks = { nav.push(AppScreen.BooksManagement) },
                    onOpenOutOfOrder = { nav.push(AppScreen.OutOfOrderBooks) },
                    onOpenHistory = { nav.push(AppScreen.ManagementHistory) },
                    onOpenSearchHistory = { nav.push(AppScreen.ManagementSearchHistory) },
                    onOpenPopularBooks = { nav.push(AppScreen.ManagementPopularBooks) },
                    onOpenRequests = { nav.push(AppScreen.ManagementRequests) },
                    onOpenAnnouncements = { nav.push(AppScreen.ManagementAnnouncements) },
                    onOpenShortcuts = { nav.push(AppScreen.ManagementShortcuts) },
                    onOpenMatchings = { nav.push(AppScreen.ManagementMatchings) },
                    onOpenTechSupport = { nav.push(AppScreen.ManagementTechSupport) },
                    onOpenWindowsTool = { nav.push(AppScreen.ManagementWindowsTool) },
                    onOpenStorageBrowser = { nav.push(AppScreen.ManagementStorageBrowser) },
                    onOpenTexts = { nav.push(AppScreen.ManagementTexts) },
                    onOpenTheme = { nav.push(AppScreen.ManagementTheme) },
                    onOpenDeveloper = { nav.push(AppScreen.DeveloperGate) },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                )
            }

            AppScreen.ManagementTexts -> ManagementGuard(session = session, nav = nav) {
                TextManagementScreen(
                    viewModel = textManagementVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                )
            }

            AppScreen.ManagementTheme -> ManagementGuard(session = session, nav = nav) {
                ThemeManagementScreen(
                    viewModel = themeManagementVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                )
            }

            AppScreen.ManagementWindowsTool -> ManagementGuard(session = session, nav = nav) {
                WindowsToolScreen(
                    viewModel = windowsToolVm,
                    session = session,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                )
            }

            AppScreen.ManagementStorageBrowser -> ManagementGuard(session = session, nav = nav) {
                StorageBrowserScreen(
                    viewModel = storageBrowserVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                )
            }

            AppScreen.ManagementRequests -> ManagementGuard(session = session, nav = nav) {
                LaunchedEffect(Unit) { managementDashboardVm.markRequestsSeen() }
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

            AppScreen.ManagementMatchings -> ManagementGuard(session = session, nav = nav) {
                SearchMatchingsManagementScreen(
                    viewModel = searchMatchingsManagementVm,
                    onBack = { nav.pop() },
                    onLogout = {
                        session.logout()
                        nav.resetTo(AppScreen.Attract)
                    },
                )
            }

            AppScreen.ManagementTechSupport -> ManagementGuard(session = session, nav = nav) {
                LaunchedEffect(Unit) { managementDashboardVm.markTechSupportSeen() }
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
                val outOfOrderCount by managementVm.outOfOrderCount.collectAsStateWithLifecycle()
                LaunchedEffect(outOfOrderCount) {
                    managementDashboardVm.markOutOfOrderSeen(outOfOrderCount)
                }
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
                title = stringResource(R.string.excel_import_adb_confirm_title),
                fileLabel = preview.fileLabel(),
                onCancel = { windowsToolVm.cancelAdbPending() },
                onConfirm = { windowsToolVm.confirmAdbPending() },
                confirmEnabled = !adbConfirming,
            )
        }

        adbBeisPending?.let { preview ->
            ImportConfirmDialog(
                preview = preview,
                title = stringResource(R.string.beis_import_adb_confirm_title),
                fileLabel = preview.fileLabel(),
                onCancel = { windowsToolVm.cancelAdbBeisPending() },
                onConfirm = { windowsToolVm.confirmAdbBeisPending() },
                confirmEnabled = !adbBeisConfirming,
            )
        }

        adbMatchingsPending?.let { preview ->
            MatchingsImportConfirmDialog(
                preview = preview,
                onCancel = { windowsToolVm.cancelAdbMatchingsPending() },
                onConfirm = { windowsToolVm.confirmAdbMatchingsPending() },
                confirmEnabled = !adbMatchingsConfirming,
            )
        }

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
    AppScreen.ManagementMatchings,
    AppScreen.ManagementTechSupport,
    AppScreen.ManagementWindowsTool,
    AppScreen.ManagementStorageBrowser,
    AppScreen.ManagementTexts,
    AppScreen.ManagementTheme,
    AppScreen.DeveloperGate,
    AppScreen.DeveloperDashboard,
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
