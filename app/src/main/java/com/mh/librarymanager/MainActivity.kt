package com.mh.librarymanager

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mh.librarymanager.ui.AppRoot
import com.mh.librarymanager.ui.announcements.AnnouncementsViewModel
import com.mh.librarymanager.ui.management.AnnouncementsManagementViewModel
import com.mh.librarymanager.ui.management.BooksManagementViewModel
import com.mh.librarymanager.ui.management.CatalogTransferViewModel
import com.mh.librarymanager.ui.management.HistoryViewModel
import com.mh.librarymanager.ui.management.ManagementSession
import com.mh.librarymanager.ui.management.RequestsManagementViewModel
import com.mh.librarymanager.ui.management.ShortcutsManagementViewModel
import com.mh.librarymanager.ui.management.TechSupportManagementViewModel
import com.mh.librarymanager.ui.requests.PublicRequestViewModel
import com.mh.librarymanager.ui.support.TechSupportViewModel
import com.mh.librarymanager.ui.search.SearchViewModel
import com.mh.librarymanager.ui.theme.LibraryManagerTheme

class MainActivity : ComponentActivity() {

    private val searchViewModel: SearchViewModel by viewModels()
    private val managementViewModel: BooksManagementViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val publicRequestViewModel: PublicRequestViewModel by viewModels()
    private val requestsManagementViewModel: RequestsManagementViewModel by viewModels()
    private val announcementsViewModel: AnnouncementsViewModel by viewModels()
    private val announcementsManagementViewModel: AnnouncementsManagementViewModel by viewModels()
    private val shortcutsManagementViewModel: ShortcutsManagementViewModel by viewModels()
    private val techSupportViewModel: TechSupportViewModel by viewModels()
    private val techSupportManagementViewModel: TechSupportManagementViewModel by viewModels()
    private val catalogTransferViewModel: CatalogTransferViewModel by viewModels()
    private val managementSession: ManagementSession by viewModels()

    /**
     * Compose-supplied back handler. AppRoot registers a navigator-aware
     * callback here; if it can't pop anything we swallow the press to keep
     * kiosk mode intact.
     */
    private var composeBackHandler: (() -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleMaintenanceIntent(intent)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    composeBackHandler?.invoke()
                }
            },
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
        )
        enableEdgeToEdge()
        setContent {
            LibraryManagerTheme {
                AppRoot(
                    searchViewModel = searchViewModel,
                    managementViewModel = managementViewModel,
                    historyViewModel = historyViewModel,
                    publicRequestViewModel = publicRequestViewModel,
                    requestsManagementViewModel = requestsManagementViewModel,
                    announcementsViewModel = announcementsViewModel,
                    announcementsManagementViewModel = announcementsManagementViewModel,
                    shortcutsManagementViewModel = shortcutsManagementViewModel,
                    techSupportViewModel = techSupportViewModel,
                    techSupportManagementViewModel = techSupportManagementViewModel,
                    catalogTransferViewModel = catalogTransferViewModel,
                    managementSession = managementSession,
                    onRegisterBackHandler = { handler ->
                        composeBackHandler = handler
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!MaintenanceMode.isActive(this)) {
            KioskPolicyManager.applyPolicies(this)
        }
        enterKioskMode()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleMaintenanceIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        enterKioskMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUi()
            enterKioskMode()
        }
    }

    private fun handleMaintenanceIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(MaintenanceMode.EXTRA_STOP_LOCK_TASK, false) != true) return
        try {
            stopLockTask()
        } catch (_: Exception) {
            // Not in lock task, or maintenance already lifted it.
        }
    }

    private fun enterKioskMode() {
        if (!KioskPolicyManager.isKioskReady(this)) return
        if (MaintenanceMode.isActive(this)) return

        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        if (activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            try {


















































                startLockTask()
            } catch (_: Exception) {
                // Lock task requires device owner provisioning.
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.hide(WindowInsetsCompat.Type.ime())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { c ->
                c.hide(WindowInsetsCompat.Type.ime())
                c.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN
        }
    }
}
