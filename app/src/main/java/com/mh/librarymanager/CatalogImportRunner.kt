package com.mh.librarymanager

import android.content.Context
import android.util.Log
import com.mh.librarymanager.data.civ.CivCatalogIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared adb-triggered catalog import used by [CatalogImportReceiver] and
 * [MainActivity] so the PC tool can wake the app via `am start` (more reliable
 * than a background broadcast on recent Android versions).
 */
object CatalogImportRunner {

    private const val TAG = "CatalogImport"

    suspend fun run(context: Context): CivCatalogIO.ImportResult = withContext(Dispatchers.IO) {
        KioskPolicyManager.applyPolicies(context)
        UsbMaintenance.applyUsbDefaults(context)
        val io = LibraryApp.from(context).civCatalogIo
        io.writeImportAck()
        val result = io.importFromIncomingFile()
        io.writeImportResult(result)
        when (result) {
            is CivCatalogIO.ImportResult.Ok ->
                Log.i(
                    TAG,
                    "Merged catalog: +${result.addedCount} added, " +
                        "${result.skippedCount} skipped, total ${result.totalAfter}",
                )
            else ->
                Log.e(TAG, "Import failed: $result")
        }
        result
    }
}
