package com.mh.librarymanager.data.civ

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Publishes a received .civ into the public Downloads collection so staff can
 * see it in the system file picker (adb push alone is often invisible there).
 */
object CivDownloadPublisher {

    private const val TAG = "CivDownloadPublisher"
    private const val MIME = "application/octet-stream"

    fun archiveFilename(meta: CivExportMeta?): String {
        val n = meta?.batchNumber ?: 0
        if (n > 0) return "$n.civ"
        return "import.civ"
    }

    /** Write the catalog text to Downloads under [displayName] and index it. */
    fun publish(context: Context, text: String, displayName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishViaMediaStore(context, text, displayName)
            } else {
                publishViaFile(context, text, displayName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not publish $displayName to Downloads", e)
        }
    }

    private fun publishViaMediaStore(context: Context, text: String, displayName: String) {
        val resolver = context.contentResolver
        deleteExistingDownload(resolver, displayName)
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, MIME)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending) ?: return
        try {
            val out = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("Could not open output stream for $displayName")
            out.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
            }
            val ready = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, ready, null, null)
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
                // Avoid leaving a stuck pending entry in Downloads.
            }
            throw e
        }
    }

    private fun deleteExistingDownload(
        resolver: android.content.ContentResolver,
        displayName: String,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val existing = resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=?",
            arrayOf(displayName),
            null,
        ) ?: return
        existing.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                resolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI.buildUpon()
                        .appendPath(id.toString())
                        .build(),
                    null,
                    null,
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun publishViaFile(context: Context, text: String, displayName: String) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = File(dir, displayName)
        file.writeText(text, Charsets.UTF_8)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(MIME),
            null,
        )
    }
}
