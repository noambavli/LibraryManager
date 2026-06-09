package com.mh.librarymanager

import android.content.Context
import android.content.Intent
import android.util.Log
import com.mh.librarymanager.data.civ.CivCatalogIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared adb-triggered catalog staging used by [CatalogImportReceiver] and
 * [MainActivity] so the PC tool can wake the app via `am start` (more reliable
 * than a background broadcast on recent Android versions).
 *
 * Stages the pushed file and surfaces a preview on the tablet — the catalog is
 * merged only after the user confirms in the UI ([CivCatalogIO.commitPendingImport]).
 */
object CatalogImportRunner {

    private const val TAG = "CatalogImport"

    suspend fun run(context: Context): CivCatalogIO.ImportResult = withContext(Dispatchers.IO) {
        KioskPolicyManager.applyPolicies(context)
        UsbMaintenance.applyUsbDefaults(context)
        val io = LibraryApp.from(context).civCatalogIo
        io.writeImportAck()
        val result = io.stageIncomingFile()
        io.writeImportResult(result)
        when (result) {
            is CivCatalogIO.ImportResult.AwaitingConfirmation -> {
                Log.i(
                    TAG,
                    "Awaiting confirmation: +${result.preview.addedCount} to add, " +
                        "${result.preview.skippedCount} skipped",
                )
                wakeForConfirmation(context)
            }
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

    /** Bring the app forward so the confirmation dialog is visible on the tablet. */
    private fun wakeForConfirmation(context: Context) {
        val launch = Intent(context, MainActivity::class.java).apply {
            action = CatalogImportReceiver.ACTION
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { context.startActivity(launch) }
    }
}
