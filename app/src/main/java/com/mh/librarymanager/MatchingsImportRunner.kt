package com.mh.librarymanager

import android.content.Context
import android.util.Log
import com.mh.librarymanager.data.excel.MatchingsImportIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stages a PC-pushed matchings xlsx and surfaces a preview on the tablet for confirmation. */
object MatchingsImportRunner {

    private const val TAG = "MatchingsImport"

    suspend fun run(context: Context): MatchingsImportIO.ImportResult = withContext(Dispatchers.IO) {
        KioskPolicyManager.applyPolicies(context)
        UsbMaintenance.applyUsbDefaults(context)
        val io = LibraryApp.from(context).matchingsImportIo
        io.writeImportAck()
        val result = io.stageIncomingFile()
        io.writeImportResult(result)
        when (result) {
            is MatchingsImportIO.ImportResult.AwaitingConfirmation ->
                Log.i(
                    TAG,
                    "Awaiting confirmation: +${result.preview.addedCount} to add, " +
                        "${result.preview.updatedCount} to update",
                )
            is MatchingsImportIO.ImportResult.Ok ->
                Log.i(
                    TAG,
                    "Merged matchings: +${result.addedCount} added, " +
                        "${result.updatedCount} updated, total ${result.totalAfter}",
                )
            else -> Log.e(TAG, "Import failed: $result")
        }
        result
    }
}
