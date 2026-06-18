package com.mh.librarymanager

import android.content.Context
import android.util.Log
import com.mh.librarymanager.data.excel.ExcelImportIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stages a PC-pushed xlsx and surfaces a preview on the tablet for confirmation. */
object ExcelImportRunner {

    private const val TAG = "ExcelImport"

    suspend fun run(context: Context): ExcelImportIO.ImportResult = withContext(Dispatchers.IO) {
        KioskPolicyManager.applyPolicies(context)
        UsbMaintenance.applyUsbDefaults(context)
        val io = LibraryApp.from(context).excelImportIo
        io.writeImportAck()
        val result = io.stageIncomingFile()
        io.writeImportResult(result)
        when (result) {
            is ExcelImportIO.ImportResult.AwaitingConfirmation ->
                Log.i(
                    TAG,
                    "Awaiting confirmation: +${result.preview.addedCount} to add, " +
                        "${result.preview.skippedCount} skipped",
                )
            is ExcelImportIO.ImportResult.Ok ->
                Log.i(
                    TAG,
                    "Merged catalog: +${result.addedCount} added, " +
                        "${result.skippedCount} skipped, total ${result.totalAfter}",
                )
            else -> Log.e(TAG, "Import failed: $result")
        }
        result
    }
}
