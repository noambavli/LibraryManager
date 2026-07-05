package com.mh.librarymanager

import android.content.Context
import android.util.Log

/**
 * On a sealed device we deliberately do NOT enable USB debugging (adb) and do
 * NOT auto-authorize any PC. The kiosk is locked down via [KioskPolicyManager]
 * (DISALLOW_DEBUGGING_FEATURES), and app updates / imports happen on-device from
 * a USB flash drive through the password-protected management screen.
 *
 * This object is kept as a no-op so existing call sites stay valid; it must
 * never turn adb back on, or it would reopen the "PC + commands" hole.
 */
object UsbMaintenance {

    private const val TAG = "UsbMaintenance"

    fun applyUsbDefaults(context: Context) {
        // Intentionally does nothing: adb stays off on a locked device.
        Log.i(TAG, "USB maintenance is disabled on a sealed device (adb stays off)")
    }
}
