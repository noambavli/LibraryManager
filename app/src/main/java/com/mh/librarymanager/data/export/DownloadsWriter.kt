package com.mh.librarymanager.data.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

/**
 * Writes binary documents (xlsx, zip, …) where a connected PC can reach them.
 *
 * Prefers a direct write into the public Download folder (most reliable over USB
 * / MTP on kiosk tablets), then MediaStore, then legacy public paths.
 * App-private folders are never used — those are hard to find from a PC.
 */
object DownloadsWriter {

    private const val TAG = "DownloadsWriter"

    const val MIME_XLSX =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val MIME_ZIP = "application/zip"

    private const val LOCATION_DOWNLOAD = "Download"

    private val DIRECT_DOWNLOAD_DIRS = listOf(
        "/sdcard/Download",
        "/storage/emulated/0/Download",
    )

    sealed interface Result {
        data class Ok(
            val displayName: String,
            /** Short location label kept for older callers (e.g. backup). */
            val location: String,
            val absolutePath: String = "",
            /** Human-readable hint for staff opening the file on a PC. */
            val pcHint: String = "",
            val bytesWritten: Long = 0L,
        ) : Result

        data class Failed(val message: String) : Result
    }

    fun write(
        context: Context,
        displayName: String,
        mimeType: String,
        writeBody: (OutputStream) -> Unit,
    ): Result {
        val payload = materialize(writeBody) ?: return Result.Failed("הקובץ ריק — הייצוא נכשל.")
        var lastError = "לא ניתן לשמור את הקובץ בתיקיית Download של הטאבלט."

        for (dir in directDownloadDirs()) {
            when (val outcome = writeDirect(dir, displayName, mimeType, context, payload)) {
                is Result.Ok -> return outcome
                is Result.Failed -> lastError = outcome.message
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (val outcome = writeViaMediaStore(context, displayName, mimeType, payload)) {
                is Result.Ok -> return outcome
                is Result.Failed -> lastError = outcome.message
            }
        }

        @Suppress("DEPRECATION")
        runCatching {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            when (val outcome = writeDirect(dir, displayName, mimeType, context, payload)) {
                is Result.Ok -> return outcome
                is Result.Failed -> lastError = outcome.message
            }
        }.onFailure {
            lastError = it.message ?: lastError
            Log.w(TAG, "Legacy Downloads write failed", it)
        }

        return Result.Failed(lastError)
    }

    /** Build bytes once so every target gets an identical, verified payload. */
    private fun materialize(writeBody: (OutputStream) -> Unit): ByteArray? {
        return try {
            val buffer = ByteArrayOutputStream()
            writeBody(buffer)
            buffer.toByteArray().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to build export payload", e)
            null
        }
    }

    private fun writeDirect(
        dir: File,
        displayName: String,
        mimeType: String,
        context: Context,
        payload: ByteArray,
    ): Result {
        return try {
            if (!dir.exists() && !dir.mkdirs()) {
                return Result.Failed("לא ניתן ליצור את תיקיית Download.")
            }
            if (!dir.canWrite()) {
                return Result.Failed("אין הרשאת כתיבה לתיקיית Download.")
            }
            val target = File(dir, displayName)
            val temp = File(dir, ".$displayName.part")
            if (temp.exists()) temp.delete()
            temp.writeBytes(payload)
            if (target.exists() && !target.delete()) {
                temp.delete()
                return Result.Failed("לא ניתן להחליף קובץ קיים.")
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            if (!target.isFile || target.length() != payload.size.toLong()) {
                target.delete()
                return Result.Failed("הקובץ לא נשמר במלואו — נסו שוב.")
            }
            scan(context, target.absolutePath, mimeType)
            Log.i(TAG, "Wrote $displayName to ${target.absolutePath}")
            Result.Ok(
                displayName = displayName,
                location = target.absolutePath,
                absolutePath = target.absolutePath,
                pcHint = pcHint(displayName),
                bytesWritten = payload.size.toLong(),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Direct write failed for ${dir.absolutePath}/$displayName", e)
            Result.Failed(e.message ?: "שגיאה בשמירה ל-Download")
        }
    }

    private fun writeViaMediaStore(
        context: Context,
        displayName: String,
        mimeType: String,
        payload: ByteArray,
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
            ?: return Result.Failed("תיקיית Download אינה זמינה.")
        try {
            val out = resolver.openOutputStream(uri, "w")
                ?: return Result.Failed("לא ניתן לפתוח את Download לכתיבה.")
            out.use { it.write(payload) }
            val ready = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, ready, null, null)
            val absolutePath = resolvedPath(displayName).orEmpty()
            if (absolutePath.isNotBlank()) {
                scan(context, absolutePath, mimeType)
            }
            Log.i(TAG, "Wrote $displayName via MediaStore (${payload.size} bytes)")
            return Result.Ok(
                displayName = displayName,
                location = LOCATION_DOWNLOAD,
                absolutePath = absolutePath,
                pcHint = pcHint(displayName),
                bytesWritten = payload.size.toLong(),
            )
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            Log.w(TAG, "MediaStore write failed for $displayName", e)
            return Result.Failed(e.message ?: "שגיאה בשמירה ל-Download")
        }
    }

    private fun directDownloadDirs(): List<File> {
        val out = ArrayList<File>(DIRECT_DOWNLOAD_DIRS.size)
        for (path in DIRECT_DOWNLOAD_DIRS) out += File(path)
        return out
    }

    private fun resolvedPath(displayName: String): String? {
        for (dir in DIRECT_DOWNLOAD_DIRS) {
            val file = File(dir, displayName)
            if (file.isFile && file.length() > 0L) return file.absolutePath
        }
        return null
    }

    private fun pcHint(displayName: String): String =
        "מחשב ← הטאבלט (USB) ← Download ← $displayName"

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
            Log.w(TAG, "Could not delete existing Download entry", it)
        }
        for (dir in DIRECT_DOWNLOAD_DIRS) {
            runCatching {
                File(dir, displayName).takeIf { it.isFile }?.delete()
            }
        }
    }

    private fun scan(context: Context, path: String, mimeType: String) {
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mimeType), null)
        }
    }
}
