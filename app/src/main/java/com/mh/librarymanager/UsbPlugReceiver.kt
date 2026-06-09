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
    }

    companion object {
        private const val TAG = "UsbPlugReceiver"
        private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
        private const val EXTRA_USB_CONNECTED = "connected"
    }
}
