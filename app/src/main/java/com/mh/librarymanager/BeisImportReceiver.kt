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
 * Silent Beis-Midrash xlsx import triggered by the PC Excel tool over adb:
 *
 *   adb push beis-import.xlsx /data/local/tmp/beis-import.xlsx
 *   adb shell am broadcast -a com.mh.librarymanager.IMPORT_BEIS_EXCEL \
 *     -n com.mh.librarymanager/.BeisImportReceiver
 */
class BeisImportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = LibraryApp.from(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                BeisImportRunner.run(context)
            } catch (e: Exception) {
                Log.e(TAG, "Import crashed", e)
                try {
                    app.beisImportIo.writeImportResult(
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
        const val ACTION = "com.mh.librarymanager.IMPORT_BEIS_EXCEL"
        private const val TAG = "BeisImport"
    }
}
