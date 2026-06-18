package com.mh.librarymanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mh.librarymanager.data.excel.ExcelImportIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Silent books xlsx import triggered by the PC Excel tool over adb:
 *
 *   adb push books-import.xlsx /data/local/tmp/books-import.xlsx
 *   adb shell am broadcast -a com.mh.librarymanager.IMPORT_EXCEL \
 *     -n com.mh.librarymanager/.ExcelImportReceiver
 */
class ExcelImportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = LibraryApp.from(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ExcelImportRunner.run(context)
            } catch (e: Exception) {
                Log.e(TAG, "Import crashed", e)
                try {
                    app.excelImportIo.writeImportResult(
                        ExcelImportIO.ImportResult.IoFailure(e.message ?: "Import crashed"),
                    )
                } catch (_: Exception) {
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.mh.librarymanager.IMPORT_EXCEL"
        private const val TAG = "ExcelImport"
    }
}
