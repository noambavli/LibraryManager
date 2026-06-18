package com.mh.librarymanager.data.excel

import android.content.Context
import android.util.Log
import com.mh.librarymanager.data.BookRepository
import com.mh.librarymanager.data.civ.CivDownloadPublisher
import com.mh.librarymanager.data.xlsx.CatalogImporter
import com.mh.librarymanager.domain.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Stages a PC-pushed books `.xlsx` (via adb) for on-tablet confirmation, then
 * merges through [CatalogImporter] after the user approves.
 *
 * Uses the same result-file protocol as the legacy `.civ` tool so the Windows
 * desktop exe can wait for OK / PENDING / ERR lines.
 */
class ExcelImportIO(
    private val context: Context,
    private val repository: BookRepository,
) {

    companion object {
        private const val TAG = "ExcelImportIO"
        const val MAX_BYTES: Long = 64L * 1024L * 1024L
        const val PENDING_FILE_NAME = "books-import-pending.xlsx"
        const val INCOMING_CANONICAL_NAME = "books-import.xlsx"
        val INCOMING_PATHS = listOf(
            "/data/local/tmp/$INCOMING_CANONICAL_NAME",
            "/sdcard/Download/$INCOMING_CANONICAL_NAME",
        )
        const val RESULT_FILE_NAME = "catalog-import-result.txt"
        const val RESULT_PROGRESS = "RUNNING"
        const val RESULT_PATH = "/sdcard/Download/$RESULT_FILE_NAME"
        const val RESULT_PATH_TMP = "/data/local/tmp/$RESULT_FILE_NAME"
        const val RESULT_PATH_APP_FILES =
            "/sdcard/Android/data/com.mh.librarymanager/files/$RESULT_FILE_NAME"
    }

    private val mutex = Mutex()
    private val pendingFile: File get() = File(context.filesDir, PENDING_FILE_NAME)

    data class ImportPreview(
        val addedCount: Int,
        val skippedCount: Int,
        val currentCount: Int,
        val totalAfter: Int,
        val sourceFile: String = "",
        val batchNumber: Int = 0,
    ) {
        fun fileLabel(): String? = when {
            batchNumber > 0 -> "$batchNumber.xlsx"
            sourceFile.isNotBlank() -> sourceFile
            else -> null
        }
    }

    sealed interface PreviewOutcome {
        data class Ready(val preview: ImportPreview) : PreviewOutcome
        data class Failed(val result: ImportResult) : PreviewOutcome
    }

    sealed interface ImportResult {
        data class Ok(
            val addedCount: Int,
            val skippedCount: Int,
            val totalAfter: Int,
        ) : ImportResult

        data class AwaitingConfirmation(val preview: ImportPreview) : ImportResult
        data object Empty : ImportResult
        data class Invalid(val reason: String) : ImportResult
        data class TooLarge(val maxBytes: Long) : ImportResult
        data class IoFailure(val reason: String) : ImportResult
    }

    fun hasPendingImport(): Boolean = pendingFile.exists() && pendingFile.length() > 0L

    suspend fun stageIncomingFile(): ImportResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (hasPendingImport()) {
                when (val existing = readPendingPreviewLocked()) {
                    is PreviewOutcome.Ready ->
                        return@withContext ImportResult.AwaitingConfirmation(existing.preview)
                    is PreviewOutcome.Failed ->
                        try { pendingFile.delete() } catch (_: Exception) {}
                }
            }
            val incoming = INCOMING_PATHS.map { File(it) }.firstOrNull { it.isFile && it.canRead() }
                ?: return@withContext ImportResult.Invalid(
                    "No $INCOMING_CANONICAL_NAME found. Expected one of: ${INCOMING_PATHS.joinToString()}",
                )
            if (incoming.length() > MAX_BYTES) {
                return@withContext ImportResult.TooLarge(MAX_BYTES)
            }
            try {
                incoming.inputStream().use { input ->
                    pendingFile.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                return@withContext ImportResult.IoFailure(e.message ?: "Could not stage workbook")
            }
            deleteIncomingFiles()
            publishArchive(pendingFile, archiveName(incoming.name))
            when (val preview = readPendingPreviewLocked()) {
                is PreviewOutcome.Ready ->
                    ImportResult.AwaitingConfirmation(preview.preview)
                is PreviewOutcome.Failed -> preview.result
            }
        }
    }

    suspend fun loadPendingPreview(): PreviewOutcome = mutex.withLock {
        withContext(Dispatchers.IO) { readPendingPreviewLocked() }
    }

    suspend fun commitPendingImport(): ImportResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!hasPendingImport()) {
                return@withContext ImportResult.Invalid("No pending import to apply.")
            }
            val merge = try {
                pendingFile.inputStream().use {
                    CatalogImporter(context, repository).mergeFromStream(it)
                }
            } catch (e: Exception) {
                return@withContext ImportResult.IoFailure(e.message ?: "Import failed")
            }
            try {
                pendingFile.delete()
            } catch (_: Exception) {
            }
            ImportResult.Ok(
                addedCount = merge.added,
                skippedCount = merge.duplicates,
                totalAfter = merge.totalAfter,
            )
        }
    }

    fun discardPendingImport() {
        try {
            if (pendingFile.exists()) pendingFile.delete()
        } catch (_: Exception) {
        }
        deleteIncomingFiles()
        writeResultLine("ERR:cancelled")
    }

    fun writeImportAck() = writeResultLine(RESULT_PROGRESS)

    fun writeImportResult(result: ImportResult) {
        val line = when (result) {
            is ImportResult.Ok ->
                "OK:added=${result.addedCount}:skipped=${result.skippedCount}:total=${result.totalAfter}"
            is ImportResult.AwaitingConfirmation -> {
                val p = result.preview
                "PENDING:added=${p.addedCount}:skipped=${p.skippedCount}:" +
                    "current=${p.currentCount}:total=${p.totalAfter}"
            }
            ImportResult.Empty -> "ERR:empty"
            is ImportResult.Invalid -> "ERR:${result.reason}"
            is ImportResult.TooLarge -> "ERR:too_large"
            is ImportResult.IoFailure -> "ERR:${result.reason}"
        }
        writeResultLine(line)
    }

    private suspend fun readPendingPreviewLocked(): PreviewOutcome {
        if (!hasPendingImport()) {
            return PreviewOutcome.Failed(ImportResult.Invalid("No pending import."))
        }
        return try {
            val parsed = pendingFile.inputStream().use {
                CatalogImporter(context, repository).parseBooksFromStream(it)
            }
            if (parsed.books.isEmpty()) {
                PreviewOutcome.Failed(ImportResult.Empty)
            } else {
                PreviewOutcome.Ready(buildPreview(parsed.books, pendingFile.name))
            }
        } catch (e: Exception) {
            PreviewOutcome.Failed(ImportResult.Invalid(e.message ?: "Could not read workbook"))
        }
    }

    private suspend fun buildPreview(books: List<Book>, sourceName: String): ImportPreview {
        val merge = repository.previewMerge(books)
        val batch = batchFromName(sourceName)
        return ImportPreview(
            addedCount = merge.added,
            skippedCount = merge.skipped,
            totalAfter = merge.totalAfter,
            currentCount = merge.totalAfter - merge.added,
            sourceFile = sourceName,
            batchNumber = batch,
        )
    }

    private fun batchFromName(name: String): Int {
        val base = name.substringBefore('.')
        return base.toIntOrNull()?.takeIf { it > 0 } ?: 0
    }

    private fun archiveName(incomingName: String): String {
        val batch = batchFromName(incomingName)
        return if (batch > 0) "$batch.xlsx" else incomingName.ifBlank { INCOMING_CANONICAL_NAME }
    }

    private fun publishArchive(source: File, displayName: String) {
        try {
            val bytes = source.readBytes()
            ExcelDownloadPublisher.publish(context, bytes, displayName)
        } catch (e: Exception) {
            Log.w(TAG, "Could not publish $displayName to Downloads", e)
        }
    }

    private fun deleteIncomingFiles() {
        for (path in INCOMING_PATHS) {
            try {
                val f = File(path)
                if (f.exists()) f.delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun writeResultLine(line: String) {
        var wrote = false
        runCatching {
            CivDownloadPublisher.publish(context, line, RESULT_FILE_NAME)
            wrote = true
            Log.i(TAG, "Import result published to Downloads: $line")
        }.onFailure {
            Log.w(TAG, "Could not publish import result to Downloads", it)
        }
        context.getExternalFilesDir(null)?.let { dir ->
            runCatching {
                File(dir, RESULT_FILE_NAME).writeText(line, Charsets.UTF_8)
                wrote = true
            }.onFailure {
                Log.w(TAG, "Could not write import result to app files", it)
            }
        }
        for (path in listOf(RESULT_PATH, RESULT_PATH_TMP)) {
            runCatching {
                File(path).writeText(line, Charsets.UTF_8)
                wrote = true
            }.onFailure {
                Log.w(TAG, "Could not write import result to $path", it)
            }
        }
        if (!wrote) {
            Log.e(TAG, "Import result not written to any path — PC will use logcat: $line")
        }
    }
}
