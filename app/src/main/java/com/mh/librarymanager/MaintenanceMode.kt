package com.mh.librarymanager

import android.content.Context
import android.content.Intent

/**
 * Short kiosk pause so a technician can approve a new PC's USB-debugging key.
 * Triggered from Mac (already authorized) before moving the cable to Windows.
 */
object MaintenanceMode {

    private const val PREFS = "maintenance_mode"
    private const val KEY_UNTIL = "until_ms"
    const val ACTION_PREPARE_PC_AUTHORIZE = "com.mh.librarymanager.PREPARE_PC_AUTHORIZE"
    const val EXTRA_STOP_LOCK_TASK = "stop_lock_task"
    const val WINDOW_MINUTES = 5

    fun enter(context: Context, minutes: Int = WINDOW_MINUTES) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_UNTIL, until)
            .apply()
        KioskPolicyManager.suspendKioskForMaintenance(context)
        UsbMaintenance.applyUsbDefaults(context)

        val unlock = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_STOP_LOCK_TASK, true)
        }
        context.startActivity(unlock)

        val screen = Intent(context, MaintenanceAuthorizeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(screen)
    }

    fun isActive(context: Context): Boolean {
        val until = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_UNTIL, 0L)
        return System.currentTimeMillis() < until
    }

    fun remainingMs(context: Context): Long {
        val until = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_UNTIL, 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun exit(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_UNTIL)
            .apply()
        KioskPolicyManager.applyPolicies(context)
    }
}
