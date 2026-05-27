package com.mh.librarymanager

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log

/**
 * Debug-only: clear install blocks so Android Studio can deploy again.
 *
 *   adb shell am broadcast -a com.mh.librarymanager.ALLOW_DEPLOY \
 *     -n com.mh.librarymanager/.DeployHelperReceiver
 */
class DeployHelperReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!KioskPolicyManager.isDeviceOwner(context)) {
            Log.w(TAG, "Not device owner — nothing to clear")
            return
        }
        val dpm = KioskPolicyManager.devicePolicyManager(context)
        val admin = KioskPolicyManager.adminComponent(context)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
        Log.i(TAG, "Install/uninstall restrictions cleared")
    }

    companion object {
        private const val TAG = "DeployHelperReceiver"
    }
}
