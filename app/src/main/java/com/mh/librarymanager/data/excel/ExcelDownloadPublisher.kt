package com.mh.librarymanager.data.excel

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/** Publishes a received `.xlsx` into public Downloads for visibility in pickers. */
object ExcelDownloadPublisher {

    private const val TAG = "ExcelDownloadPublisher"
    private const val MIME =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun publish(context: Context, bytes: ByteArray, displayName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishViaMediaStore(context, bytes, displayName)
            } else {
                publishViaFile(context, bytes, displayName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not publish $displayName to Downloads", e)
        }
    }

    private fun publishViaMediaStore(context: Context, bytes: ByteArray, displayName: String) {
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
            out.use { stream -> stream.write(bytes) }
            val ready = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, ready, null, null)
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
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
    private fun publishViaFile(context: Context, bytes: ByteArray, displayName: String) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = File(dir, displayName)
        file.writeBytes(bytes)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(MIME),
            null,
        )
    }
}
