package com.mh.librarymanager.data.excel

import android.content.Context
import android.util.Log
import com.mh.librarymanager.data.BookRepository
import com.mh.librarymanager.data.civ.CivDownloadPublisher
import com.mh.librarymanager.data.xlsx.CatalogImporter
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.BookPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Stages a PC-pushed books `.xlsx` (via adb) for on-tablet confirmation, then
 * merges through [CatalogImporter] after the user approves.
 *
 * One instance drives one **library channel**: the Otzar catalog (default) or
 * the Beis-Midrash catalog ([beis]). The channel decides which library the rows
 * are stamped with and which incoming/result file names are used, so the Windows
 * ExcelTool can push each library on its own path without collision. The
 * result-file protocol (OK / PENDING / ERR lines) is identical for both.
 */
class ExcelImportIO(
    private val context: Context,
    private val repository: BookRepository,
    private val place: BookPlace = BookPlace.OTZAR,
    private val incomingCanonicalName: String = INCOMING_CANONICAL_NAME,
    private val pendingFileName: String = PENDING_FILE_NAME,
    private val pendingSourceFileName: String = PENDING_SOURCE_NAME,
    private val resultFileName: String = RESULT_FILE_NAME,
    private val archivePrefix: String = "",
) {

    companion object {
        private const val TAG = "ExcelImportIO"
        const val MAX_BYTES: Long = 64L * 1024L * 1024L
        const val PENDING_FILE_NAME = "books-import-pending.xlsx"
        const val PENDING_SOURCE_NAME = "books-import-pending-source.txt"
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

        // ---- Beis-Midrash channel (parallel adb path) --------------------
        const val BEIS_INCOMING_CANONICAL_NAME = "beis-import.xlsx"
        const val BEIS_PENDING_FILE_NAME = "beis-import-pending.xlsx"
        const val BEIS_PENDING_SOURCE_NAME = "beis-import-pending-source.txt"
        const val BEIS_RESULT_FILE_NAME = "beis-import-result.txt"

        /** Beis-Midrash channel: stamps books as בית מדרש and uses beis-* files. */
        fun beis(context: Context, repository: BookRepository): ExcelImportIO =
            ExcelImportIO(
                context = context,
                repository = repository,
                place = BookPlace.BEIS_MIDRASH,
                incomingCanonicalName = BEIS_INCOMING_CANONICAL_NAME,
                pendingFileName = BEIS_PENDING_FILE_NAME,
                pendingSourceFileName = BEIS_PENDING_SOURCE_NAME,
                resultFileName = BEIS_RESULT_FILE_NAME,
                archivePrefix = "beis-",
            )
    }

    private val incomingPaths = listOf(
        "/data/local/tmp/$incomingCanonicalName",
        "/sdcard/Download/$incomingCanonicalName",
    )
    private val resultPathDownload = "/sdcard/Download/$resultFileName"
    private val resultPathTmp = "/data/local/tmp/$resultFileName"

    private val mutex = Mutex()
    private val pendingFile: File get() = File(context.filesDir, pendingFileName)
    private val pendingSourceFile: File get() = File(context.filesDir, pendingSourceFileName)

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
            val incoming = incomingPaths.map { File(it) }.firstOrNull { it.isFile && it.canRead() }
            if (hasPendingImport()) {
                if (incoming == null) {
                    when (val existing = readPendingPreviewLocked()) {
                        is PreviewOutcome.Ready ->
                            return@withContext ImportResult.AwaitingConfirmation(existing.preview)
                        is PreviewOutcome.Failed -> clearPendingLocked()
                    }
                } else {
                    // New PC push while an old confirm is still pending — replace it.
                    clearPendingLocked()
                }
            }
            val file = incoming
                ?: return@withContext ImportResult.Invalid(
                    "No $incomingCanonicalName found. Expected one of: ${incomingPaths.joinToString()}",
                )
            if (file.length() > MAX_BYTES) {
                return@withContext ImportResult.TooLarge(MAX_BYTES)
            }
            try {
                file.inputStream().use { input ->
                    pendingFile.outputStream().use { output -> input.copyTo(output) }
                }
                pendingSourceFile.writeText(file.name)
            } catch (e: Exception) {
                clearPendingLocked()
                return@withContext ImportResult.IoFailure(e.message ?: "Could not stage workbook")
            }
            deleteIncomingFiles()
            publishArchive(pendingFile, archiveName(file.name))
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
                    CatalogImporter(context, repository).mergeFromStream(it, place)
                }
            } catch (e: Exception) {
                return@withContext ImportResult.IoFailure(e.message ?: "Import failed")
            }
            try {
                pendingFile.delete()
                pendingSourceFile.delete()
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
        clearPendingLocked()
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
                CatalogImporter(context, repository).parseBooksFromStream(it, place)
            }
            if (parsed.books.isEmpty()) {
                PreviewOutcome.Failed(ImportResult.Empty)
            } else {
                PreviewOutcome.Ready(buildPreview(parsed.books, pendingSourceName()))
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
        return if (batch > 0) "$archivePrefix$batch.xlsx" else incomingName.ifBlank { incomingCanonicalName }
    }

    private fun pendingSourceName(): String {
        val stored = runCatching {
            pendingSourceFile.takeIf { it.isFile }?.readText()?.trim()
        }.getOrNull()
        if (!stored.isNullOrBlank()) return stored
        return pendingFile.name
    }

    private fun clearPendingLocked() {
        try {
            if (pendingFile.exists()) pendingFile.delete()
        } catch (_: Exception) {
        }
        try {
            if (pendingSourceFile.exists()) pendingSourceFile.delete()
        } catch (_: Exception) {
        }
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
        for (path in incomingPaths) {
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
            CivDownloadPublisher.publish(context, line, resultFileName)
            wrote = true
            Log.i(TAG, "Import result published to Downloads: $line")
        }.onFailure {
            Log.w(TAG, "Could not publish import result to Downloads", it)
        }
        context.getExternalFilesDir(null)?.let { dir ->
            runCatching {
                File(dir, resultFileName).writeText(line, Charsets.UTF_8)
                wrote = true
            }.onFailure {
                Log.w(TAG, "Could not write import result to app files", it)
            }
        }
        for (path in listOf(resultPathDownload, resultPathTmp)) {
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
