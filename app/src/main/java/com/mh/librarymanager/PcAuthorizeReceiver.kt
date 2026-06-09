package com.mh.librarymanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Opens a short maintenance window so a Windows PC can be authorized for adb.
 *
 * Run from a Mac that already trusts the tablet:
 *   adb shell am broadcast -a com.mh.librarymanager.PREPARE_PC_AUTHORIZE \
 *     -n com.mh.librarymanager/.PcAuthorizeReceiver
 */
class PcAuthorizeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != MaintenanceMode.ACTION_PREPARE_PC_AUTHORIZE) return
        Log.i(TAG, "Opening PC authorize maintenance window")
        MaintenanceMode.enter(context)
    }

    companion object {
        private const val TAG = "PcAuthorizeReceiver"
    }
}
