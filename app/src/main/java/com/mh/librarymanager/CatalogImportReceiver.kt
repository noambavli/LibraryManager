package com.mh.librarymanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Silent catalog import triggered by the PC tool over adb:
 *
 *   adb push catalog.civ /sdcard/Download/catalog.civ
 *   adb shell am broadcast -a com.mh.librarymanager.IMPORT_CATALOG \
 *     -n com.mh.librarymanager/.CatalogImportReceiver
 *
 * Reads the pushed file, imports it (with automatic backup), and writes a
 * one-line result to [CivCatalogIO.RESULT_PATH] so the PC can confirm success.
 */
class CatalogImportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = LibraryApp.from(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                KioskPolicyManager.applyPolicies(context)
                UsbMaintenance.applyUsbDefaults(context)
                val io = app.civCatalogIo
                val result = io.importFromIncomingFile()
                io.writeImportResult(result)
                when (result) {
                    is com.mh.librarymanager.data.civ.CivCatalogIO.ImportResult.Ok ->
                        Log.i(
                            TAG,
                            "Merged catalog: +${result.addedCount} added, " +
                                "${result.skippedCount} skipped, total ${result.totalAfter}",
                        )
                    else ->
                        Log.e(TAG, "Import failed: $result")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Import crashed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.mh.librarymanager.IMPORT_CATALOG"
        private const val TAG = "CatalogImport"
    }
}
