package com.mh.librarymanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) ?: -1
        val message = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        when (status) {
            PackageInstaller.STATUS_SUCCESS ->
                Log.i(TAG, "Update installed successfully")
            else ->
                Log.e(TAG, "Update failed: status=$status message=$message")
        }
    }

    companion object {
        private const val TAG = "InstallResult"
    }
}
