package com.mh.librarymanager.ui.management

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.mh.librarymanager.BuildConfig
import java.security.MessageDigest

/**
 * Tracks management-mode authentication and idle-logout state for the whole
 * app. Lives on the activity scope so navigating between management screens
 * keeps the session alive but going home (or auto-logout) clears it cleanly.
 *
 * Passwords are NEVER hardcoded here:
 *  - The management password defaults to [BuildConfig.MANAGEMENT_DEFAULT_PASSWORD]
 *    (injected from secrets.properties, ships as "1111") and can be changed or
 *    reset at runtime from the developer dashboard. The current value is
 *    persisted in private SharedPreferences so it survives restarts.
 *  - The developer key lives only in secrets.properties; the app embeds just a
 *    salted SHA-256 hash of it ([BuildConfig.DEV_KEY_HASH]), so decompiling the
 *    APK cannot reveal the key. It is fixed and gates the developer dashboard
 *    (and doubles as a private master unlock for management).
 *
 * Timer policy (matches product spec):
 *  - 30 seconds with no interaction → warning sheet appears.
 *  - +30 seconds with no interaction → forced logout to the home screen.
 *  - Any interaction (tap anywhere inside the management area) resets the
 *    clock; while the warning is visible the user must tap "stay" explicitly.
 */
class ManagementSession(app: Application) : AndroidViewModel(app) {

    companion object {
        const val IDLE_BEFORE_WARNING_MS = 30_000L
        const val WARNING_DURATION_MS = 30_000L

        private const val PREFS = "management_credentials"
        private const val KEY_MANAGEMENT_PASSWORD = "management_password"

        /**
         * Factory default the management password resets to. Sourced from
         * secrets.properties via BuildConfig — ships as "1111".
         */
        val DEFAULT_MANAGEMENT_PASSWORD: String
            get() = BuildConfig.MANAGEMENT_DEFAULT_PASSWORD

        /**
         * Max digits the on-screen keypad accepts for the management password
         * and the developer key. Derived so it is ALWAYS at least as long as the
         * configured developer key and default password — that guarantees every
         * valid code can actually be typed (you can never configure or set a code
         * that the keypad refuses to enter). Minimum 12 for comfortable headroom.
         */
        val CODE_MAX_LEN: Int = maxOf(
            12,
            BuildConfig.DEV_KEY_LENGTH,
            BuildConfig.MANAGEMENT_DEFAULT_PASSWORD.length,
        )

        private fun sha256Hex(input: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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

    /** The management password currently in effect (stored value or default). */
    fun currentManagementPassword(): String =
        prefs.getString(KEY_MANAGEMENT_PASSWORD, null) ?: DEFAULT_MANAGEMENT_PASSWORD

    /**
     * Persists a new management password. Blank input is rejected so the gate
     * can never become impossible to satisfy.
     */
    fun setManagementPassword(newPassword: String): Boolean {
        val trimmed = newPassword.trim()
        if (trimmed.isEmpty()) return false
        prefs.edit().putString(KEY_MANAGEMENT_PASSWORD, trimmed).apply()
        return true
    }

    /** Restores the management password to the factory default (e.g. "1111"). */
    fun resetManagementPassword() {
        prefs.edit().remove(KEY_MANAGEMENT_PASSWORD).apply()
    }

    fun tryUnlock(code: String): Boolean {
        // The management password OR the developer key opens management. The
        // developer key is a private master override (undocumented for staff):
        // it always works even if the management password was changed/forgotten.
        if (code == currentManagementPassword() || isDeveloperKey(code)) {
            isAuthenticated = true
            recordInteraction()
            return true
        }
        return false
    }

    /**
     * True when [code] matches the developer key. The key is never stored in the
     * app in plaintext — only a salted SHA-256 hash is embedded, and we compare
     * hashes here. An empty configured hash (missing secrets.properties) or an
     * empty [code] never matches, so the developer dashboard stays locked out.
     */
    fun isDeveloperKey(code: String): Boolean {
        val expectedHash = BuildConfig.DEV_KEY_HASH
        if (expectedHash.isEmpty() || code.isEmpty()) return false
        return sha256Hex(BuildConfig.DEV_KEY_SALT + code) == expectedHash
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
