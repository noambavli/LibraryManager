package com.mh.librarymanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mh.librarymanager.data.excel.MatchingsImportIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Silent matchings xlsx import triggered by the PC Excel tool over adb:
 *
 *   adb push matchings-import.xlsx /data/local/tmp/matchings-import.xlsx
 *   adb shell am broadcast -a com.mh.librarymanager.IMPORT_MATCHINGS_EXCEL \
 *     -n com.mh.librarymanager/.MatchingsImportReceiver
 */
class MatchingsImportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = LibraryApp.from(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MatchingsImportRunner.run(context)
            } catch (e: Exception) {
                Log.e(TAG, "Import crashed", e)
                try {
                    app.matchingsImportIo.writeImportResult(
                        MatchingsImportIO.ImportResult.IoFailure(e.message ?: "Import crashed"),
                    )
                } catch (_: Exception) {
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.mh.librarymanager.IMPORT_MATCHINGS_EXCEL"
        private const val TAG = "MatchingsImport"
    }
}
