package com.mh.librarymanager.ui.management

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Tracks management-mode authentication and idle-logout state for the whole
 * app. Lives on the activity scope so navigating between management screens
 * keeps the session alive but going home (or auto-logout) clears it cleanly.
 *
 * Timer policy (matches product spec):
 *  - 30 seconds with no interaction → warning sheet appears.
 *  - +30 seconds with no interaction → forced logout to the home screen.
 *  - Any interaction (tap anywhere inside the management area) resets the
 *    clock; while the warning is visible the user must tap "stay" explicitly.
 */
class ManagementSession : ViewModel() {

    companion object {
        const val PASSWORD = "1111"
        const val IDLE_BEFORE_WARNING_MS = 30_000L
        const val WARNING_DURATION_MS = 30_000L
    }

    var isAuthenticated by mutableStateOf(false)
        private set

    /** When non-null, the warning sheet is showing and counting down. */
    var warningStartedAt by mutableStateOf<Long?>(null)
        private set

    private var lastInteractionAt by mutableLongStateOf(SystemClock.elapsedRealtime())

    /** While > 0, idle auto-logout is paused (e.g. system file picker is open). */
    private var externalTaskDepth = 0

    fun beginExternalTask() {
        externalTaskDepth++
        recordInteraction()
    }

    fun endExternalTask() {
        externalTaskDepth = (externalTaskDepth - 1).coerceAtLeast(0)
        recordInteraction()
    }

    fun isExternalTaskActive(): Boolean = externalTaskDepth > 0

    fun tryUnlock(code: String): Boolean {
        if (code == PASSWORD) {
            isAuthenticated = true
            recordInteraction()
            return true
        }
        return false
    }

    fun logout() {
        isAuthenticated = false
        warningStartedAt = null
    }

    fun recordInteraction() {
        lastInteractionAt = SystemClock.elapsedRealtime()
        warningStartedAt = null
    }

    fun lastInteractionAt(): Long = lastInteractionAt

    fun showWarningNow() {
        if (warningStartedAt == null) {
            warningStartedAt = SystemClock.elapsedRealtime()
        }
    }

    fun dismissWarning() {
        warningStartedAt = null
        lastInteractionAt = SystemClock.elapsedRealtime()
    }
}
