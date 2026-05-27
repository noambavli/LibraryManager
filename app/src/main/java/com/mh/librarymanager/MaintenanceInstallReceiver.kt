package com.mh.librarymanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MaintenanceInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        KioskPolicyManager.applyPolicies(context)

        val apk = ApkUpdateInstaller.findUpdateApk()
        if (apk == null) {
            Log.e(TAG, "No APK found. Push to one of: ${ApkUpdateInstaller.UPDATE_PATHS}")
            return
        }
        ApkUpdateInstaller.install(context, apk)
    }

    companion object {
        private const val TAG = "MaintenanceInstall"
    }
}
