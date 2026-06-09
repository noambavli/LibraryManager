package com.mh.librarymanager

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Keeps USB ready for PC maintenance: debugging on and default mode file
 * transfer where the device owner is allowed to set it.
 *
 * Called on boot and before adb-triggered imports so staff never have to
 * pick "File transfer" on the tablet.
 */
object UsbMaintenance {

    private const val TAG = "UsbMaintenance"

    fun applyUsbDefaults(context: Context) {
        if (!KioskPolicyManager.isDeviceOwner(context)) return
        try {
            // Keep USB debugging available for the PC tool (adb push + broadcast).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                Settings.Global.putInt(
                    context.contentResolver,
                    Settings.Global.ADB_ENABLED,
                    1,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not enable adb", e)
        }

        // Best-effort: default new USB connections to MTP (file transfer).
        // Exact key/value varies by OEM; shell fallback helps on Samsung etc.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                Settings.Global.putString(
                    context.contentResolver,
                    "usb_default_functions",
                    "mtp",
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set usb_default_functions", e)
        }

        try {
            Runtime.getRuntime().exec(arrayOf("cmd", "usb", "setFunctions", "mtp"))
        } catch (e: Exception) {
            Log.w(TAG, "cmd usb setFunctions mtp failed", e)
        }
    }
}
