package com.mh.librarymanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Re-apply USB defaults when the cable is plugged in so adb/MTP come up
 * without staff choosing a menu on the tablet.
 */
class UsbPlugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_USB_STATE) return
        val connected = intent.getBooleanExtra(EXTRA_USB_CONNECTED, false)
        if (!connected) return
        Log.i(TAG, "USB connected — applying maintenance USB defaults")
        UsbMaintenance.applyUsbDefaults(context)

        // Trigger an automatic full backup when this looks like a real data
        // link to a PC (MTP/adb/configured) rather than a dumb charger. When
        // none of those signals are present we still proceed (some OEMs omit
        // them); the BackupManager itself debounces and skips when there is
        // nothing to save.
        if (looksLikeDataConnection(intent)) {
            try {
                LibraryApp.from(context).backupManager.onUsbConnected()
            } catch (e: Exception) {
                Log.w(TAG, "Could not start auto-backup", e)
            }
        }
    }

    private fun looksLikeDataConnection(intent: Intent): Boolean {
        val dataSignals = listOf("configured", "mtp", "ptp", "adb", "host_connected")
        val present = dataSignals.filter { intent.hasExtra(it) }
        if (present.isEmpty()) return true // No info — don't block a genuine connect.
        return present.any { intent.getBooleanExtra(it, false) }
    }

    companion object {
        private const val TAG = "UsbPlugReceiver"
        private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
        private const val EXTRA_USB_CONNECTED = "connected"
    }
}
