package com.mh.librarymanager.data.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.OutputStream

/**
 * Writes arbitrary binary documents (xlsx, zip, …) where a connected PC can
 * reach them — public Downloads over MTP first, then the same direct paths the
 * catalog sync tool already uses on kiosk hardware.
 */
object DownloadsWriter {

    private const val TAG = "DownloadsWriter"

    const val MIME_XLSX =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val MIME_ZIP = "application/zip"

    /** Same pattern as [com.mh.librarymanager.data.civ.CivCatalogIO] result paths. */
    private val DIRECT_DOWNLOAD_DIRS = listOf(
        "/sdcard/Download",
        "/storage/emulated/0/Download",
    )

    sealed interface Result {
        data class Ok(val displayName: String, val location: String) : Result
        data class Failed(val message: String) : Result
    }

    fun write(
        context: Context,
        displayName: String,
        mimeType: String,
        writeBody: (OutputStream) -> Unit,
    ): Result {
        var lastError = "לא ניתן לכתוב ל-Download"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return writeViaMediaStore(context, displayName, mimeType, writeBody)
            } catch (e: Exception) {
                lastError = e.message ?: lastError
                Log.w(TAG, "MediaStore write failed for $displayName", e)
            }
        }

        for (dir in directDownloadDirs(context)) {
            try {
                val file = File(dir, displayName)
                file.parentFile?.mkdirs()
                file.outputStream().buffered().use { writeBody(it) }
                scan(context, file.absolutePath, mimeType)
                Log.i(TAG, "Wrote $displayName to ${file.absolutePath}")
                return Result.Ok(displayName, file.absolutePath)
            } catch (e: Exception) {
                lastError = e.message ?: lastError
                Log.w(TAG, "Direct write failed for $dir/$displayName", e)
            }
        }

        @Suppress("DEPRECATION")
        try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = File(dir, displayName)
            file.outputStream().buffered().use { writeBody(it) }
            scan(context, file.absolutePath, mimeType)
            Log.i(TAG, "Wrote $displayName to ${file.absolutePath}")
            return Result.Ok(displayName, file.absolutePath)
        } catch (e: Exception) {
            lastError = e.message ?: lastError
            Log.w(TAG, "Legacy Downloads write failed", e)
        }

        return Result.Failed(lastError)
    }

    private fun directDownloadDirs(context: Context): List<File> {
        val out = ArrayList<File>(DIRECT_DOWNLOAD_DIRS.size + 1)
        for (path in DIRECT_DOWNLOAD_DIRS) out += File(path)
        context.getExternalFilesDir(null)?.let { out += it }
        return out
    }

    private fun writeViaMediaStore(
        context: Context,
        displayName: String,
        mimeType: String,
        writeBody: (OutputStream) -> Unit,
    ): Result {
        val resolver = context.contentResolver
        deleteExisting(context, displayName)
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            }
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending)
            ?: throw IllegalStateException("Downloads is not available.")
        try {
            val out = resolver.openOutputStream(uri, "w")
                ?: throw IllegalStateException("Could not open Downloads for writing.")
            out.use { writeBody(it) }
            val ready = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, ready, null, null)
            Log.i(TAG, "Wrote $displayName via MediaStore")
            return Result.Ok(displayName, "Download")
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun deleteExisting(context: Context, displayName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val resolver = context.contentResolver
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
                        collection.buildUpon().appendPath(id.toString()).build(),
                        null,
                        null,
                    )
                }
            }
        }.onFailure {
            // Non-fatal — a leftover row just means we may get a numbered suffix.
            Log.w(TAG, "Could not delete existing Download entry", it)
        }
    }

    private fun scan(context: Context, path: String, mimeType: String) {
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mimeType), null)
        }
    }
}
