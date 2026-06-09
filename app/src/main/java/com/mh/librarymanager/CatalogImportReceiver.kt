package com.mh.librarymanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mh.librarymanager.data.civ.CivCatalogIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Silent catalog import triggered by the PC tool over adb:
 *
 *   adb push catalog.civ /data/local/tmp/catalog.civ
 *   adb shell am broadcast -a com.mh.librarymanager.IMPORT_CATALOG \
 *     -n com.mh.librarymanager/.CatalogImportReceiver
 *
 * Stages the pushed file for on-tablet confirmation (does not merge until the
 * user approves) and writes progress to [CivCatalogIO.RESULT_PATH].
 */
class CatalogImportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = LibraryApp.from(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                CatalogImportRunner.run(context)
            } catch (e: Exception) {
                Log.e(TAG, "Import crashed", e)
                try {
                    app.civCatalogIo.writeImportResult(
                        CivCatalogIO.ImportResult.IoFailure(
                            e.message ?: "Import crashed",
                        ),
                    )
                } catch (_: Exception) {
                    // Best effort — PC will time out if this also fails.
                }
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
