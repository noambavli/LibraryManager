package com.mh.librarymanager.data.civ

import android.content.Context
import android.net.Uri
import com.mh.librarymanager.data.BookRepository
import com.mh.librarymanager.data.store.CatalogStore
import com.mh.librarymanager.data.store.atomicWriteText
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.BookPlace
import com.mh.librarymanager.domain.BookState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/**
 * Reads `.civ` catalogs produced by the desktop LibraryTool and **merges** new
 * books into the in-app catalog (existing rows are kept; matching IDs are skipped).
 *
 * A `.civ` document is intentionally **byte-for-byte the same JSON shape** as
 * the tablet's own [CatalogStore] writes to `filesDir/catalog.json` — so the
 * desktop side never needs to know about anything beyond a single, stable
 * format, and the import here is a small JSON parse + a merge.
 *
 * Safety guarantees:
 *   * The current catalog is snapshotted to `filesDir/catalog-import-backup.civ`
 *     **before** we replace anything, so a one-tap undo is always possible
 *     ("undo" reads the snapshot back and re-imports it).
 *   * Format version must match exactly: an older file is rejected (the tablet
 *     would drop fields) and a newer file is rejected (we may not understand
 *     new fields), both with a clear message.
 *   * Stream reading is bounded by a max size so a corrupt huge file can't OOM.
 *   * Every destructive operation is wrapped — disk-full / permission /
 *     concurrent-modification errors surface as a typed [ImportResult.Invalid]
 *     instead of an uncaught exception that would freeze the UI.
 *   * A [Mutex] makes the IO class safe even if the UI accidentally fires
 *     concurrent import + undo.
 */
class CivCatalogIO(
    private val context: Context,
    private val repository: BookRepository,
) {

    companion object {
        /** Hard cap on .civ size we'll ingest — generous for any real catalog. */
        const val MAX_BYTES: Long = 64L * 1024L * 1024L

        const val BACKUP_FILE_NAME = "catalog-import-backup.civ"

        /**
         * Where the PC tool (adb push) drops the catalog. First readable wins.
         * Prefer /data/local/tmp — same pattern as APK maintenance updates.
         */
        val INCOMING_PATHS = listOf(
            "/data/local/tmp/catalog.civ",
            "/sdcard/Download/catalog.civ",
        )

        const val INCOMING_CANONICAL_NAME = "catalog.civ"

        /** Written after an adb-triggered import so the PC can read the outcome. */
        const val RESULT_PATH = "/sdcard/Download/catalog-import-result.txt"
        const val RESULT_PATH_TMP = "/data/local/tmp/catalog-import-result.txt"
        val RESULT_PATHS = listOf(RESULT_PATH_TMP, RESULT_PATH)

        private const val PREF_LAST_AT = "last_at"
        private const val PREF_LAST_ADDED = "last_added"
        private const val PREF_LAST_SKIPPED = "last_skipped"
        private const val PREF_LAST_TOTAL = "last_total"
        private const val PREF_LAST_TOOL = "last_tool"
        private const val PREF_LAST_TOOL_VER = "last_tool_ver"
        private const val PREF_LAST_SOURCE = "last_source"
        private const val PREF_LAST_BATCH = "last_batch"
        private const val PREF_LAST_ADDED_BOOKS = "last_added_books"
        const val MAX_STORED_ADDED_BOOKS = 150
    }

    data class AddedBookLine(
        val name: String,
        val writer: String,
    )

    data class ImportSummaryDetail(
        val batchNumber: Int,
        val fileLabel: String,
        val sourceFile: String,
        val at: Long,
        val added: Int,
        val skipped: Int,
        val totalAfter: Int,
        val addedBooks: List<AddedBookLine>,
    ) {
        val hasData: Boolean get() = at > 0L
        val displayLimit: Int get() = addedBooks.size.coerceAtMost(100)
        val hasMoreBooks: Boolean get() = added > displayLimit
    }

    sealed interface PreviewOutcome {
        data class Ready(val preview: ImportPreview) : PreviewOutcome
        data class Failed(val result: ImportResult) : PreviewOutcome
    }

    data class ImportPreview(
        val incomingCount: Int,
        val addedCount: Int,
        val skippedCount: Int,
        val totalAfter: Int,
        val currentCount: Int,
        val meta: CivExportMeta?,
    )

    sealed interface ImportResult {
        data class Ok(
            val addedCount: Int,
            val skippedCount: Int,
            val totalAfter: Int,
            val previousCount: Int,
            val backupAvailable: Boolean,
            val meta: CivExportMeta?,
        ) : ImportResult

        data class WrongVersion(val found: Int, val expected: Int) : ImportResult
        data class NewerVersion(val found: Int, val expected: Int) : ImportResult
        data object Empty : ImportResult
        data class Invalid(val reason: String) : ImportResult
        data class TooLarge(val sizeBytes: Long) : ImportResult
        data class IoFailure(val reason: String) : ImportResult
    }

    private val backupFile: File by lazy { File(context.filesDir, BACKUP_FILE_NAME) }
    private val prefs by lazy {
        context.getSharedPreferences("civ_import_history", Context.MODE_PRIVATE)
    }
    private val mutex = Mutex()

    fun importSummary(): ImportSummaryDetail {
        val booksJson = prefs.getString(PREF_LAST_ADDED_BOOKS, "[]") ?: "[]"
        val books = parseAddedBooksJson(booksJson)
        val batch = prefs.getInt(PREF_LAST_BATCH, 0)
        val source = prefs.getString(PREF_LAST_SOURCE, "").orEmpty()
        val label = if (batch > 0) batch.toString() else source.ifBlank { "?" }
        return ImportSummaryDetail(
            batchNumber = batch,
            fileLabel = label,
            sourceFile = source,
            at = prefs.getLong(PREF_LAST_AT, 0L),
            added = prefs.getInt(PREF_LAST_ADDED, 0),
            skipped = prefs.getInt(PREF_LAST_SKIPPED, 0),
            totalAfter = prefs.getInt(PREF_LAST_TOTAL, 0),
            addedBooks = books,
        )
    }

    fun lastImportSummary(): LastImportSummary = LastImportSummary(
        at = prefs.getLong(PREF_LAST_AT, 0L),
        added = prefs.getInt(PREF_LAST_ADDED, 0),
        skipped = prefs.getInt(PREF_LAST_SKIPPED, 0),
        totalAfter = prefs.getInt(PREF_LAST_TOTAL, 0),
        tool = prefs.getString(PREF_LAST_TOOL, "").orEmpty(),
        toolVersion = prefs.getString(PREF_LAST_TOOL_VER, "").orEmpty(),
        sourceFile = prefs.getString(PREF_LAST_SOURCE, "").orEmpty(),
    )

    data class LastImportSummary(
        val at: Long,
        val added: Int,
        val skipped: Int,
        val totalAfter: Int,
        val tool: String,
        val toolVersion: String,
        val sourceFile: String,
    ) {
        val hasData: Boolean get() = at > 0L
    }

    /** Import from a path the PC pushed via adb (no SAF picker). */
    suspend fun importFromIncomingFile(): ImportResult = withContext(Dispatchers.IO) {
        val file = INCOMING_PATHS.map { File(it) }.firstOrNull { it.isFile && it.canRead() }
            ?: return@withContext ImportResult.Invalid(
                "No catalog.civ found. Expected one of: ${INCOMING_PATHS.joinToString()}",
            )
        val text = try {
            file.inputStream().use { readBoundedText(it) }
        } catch (e: Exception) {
            return@withContext when (e) {
                is IllegalStateException -> ImportResult.TooLarge(MAX_BYTES)
                else -> ImportResult.Invalid(e.message ?: "Could not read file")
            }
        }
        val result = importFromText(text)
        if (result is ImportResult.Ok) {
            publishToDownloads(text, result.meta)
        }
        result
    }

    /** Make the last received catalog visible under Downloads for manual pick / debugging. */
    private fun publishToDownloads(text: String, meta: CivExportMeta?) {
        CivDownloadPublisher.publish(context, text, INCOMING_CANONICAL_NAME)
        CivDownloadPublisher.publish(context, text, CivDownloadPublisher.archiveFilename(meta))
    }

    fun writeImportResult(result: ImportResult) {
        val line = when (result) {
            is ImportResult.Ok ->
                "OK:added=${result.addedCount}:skipped=${result.skippedCount}:total=${result.totalAfter}"
            is ImportResult.Empty -> "ERR:empty"
            is ImportResult.WrongVersion -> "ERR:version_old:${result.found}"
            is ImportResult.NewerVersion -> "ERR:version_new:${result.found}"
            is ImportResult.Invalid -> "ERR:${result.reason}"
            is ImportResult.TooLarge -> "ERR:too_large"
            is ImportResult.IoFailure -> "ERR:${result.reason}"
        }
        for (path in RESULT_PATHS) {
            try {
                File(path).writeText(line, Charsets.UTF_8)
            } catch (_: Exception) {
                // Best effort — tmp path is readable by adb on device-owner tablets.
            }
        }
    }

    suspend fun importFromUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // Reported size is best-effort — providers may return -1 for unknown.
        val size = try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (_: Exception) { -1L }

        when {
            size == 0L -> return@withContext ImportResult.Empty
            size > MAX_BYTES -> return@withContext ImportResult.TooLarge(size)
            // Negative ("unknown") and 1..MAX_BYTES both proceed; readBoundedText
            // is the real defense against runaway reads.
        }

        val text = try {
            resolver.openInputStream(uri)?.use { readBoundedText(it) }
        } catch (e: Exception) {
            return@withContext ImportResult.Invalid(e.message ?: "Could not open file")
        } ?: return@withContext ImportResult.Invalid("File could not be opened.")

        importFromText(text)
    }

    suspend fun previewFromUri(uri: Uri): PreviewOutcome = withContext(Dispatchers.IO) {
        when (val parsed = readAndParseUri(uri)) {
            is ParseOutcome.Failure -> PreviewOutcome.Failed(parsed.result)
            is ParseOutcome.Success ->
                PreviewOutcome.Ready(buildPreview(parsed.books, parsed.meta))
        }
    }

    suspend fun importFromText(text: String): ImportResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return@withContext ImportResult.Empty

            when (val parsed = parseText(trimmed)) {
                is ParseOutcome.Failure -> return@withContext parsed.result
                is ParseOutcome.Success -> commitMerge(parsed.books, parsed.meta)
            }
        }
    }

    private sealed interface ParseOutcome {
        data class Success(val books: List<Book>, val meta: CivExportMeta?) : ParseOutcome
        data class Failure(val result: ImportResult) : ParseOutcome
    }

    private suspend fun readAndParseUri(uri: Uri): ParseOutcome = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val size = try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (_: Exception) { -1L }
        when {
            size == 0L -> return@withContext ParseOutcome.Failure(ImportResult.Empty)
            size > MAX_BYTES -> return@withContext ParseOutcome.Failure(ImportResult.TooLarge(size))
        }
        val text = try {
            resolver.openInputStream(uri)?.use { readBoundedText(it) }
        } catch (e: Exception) {
            return@withContext ParseOutcome.Failure(
                ImportResult.Invalid(e.message ?: "Could not open file"),
            )
        } ?: return@withContext ParseOutcome.Failure(ImportResult.Invalid("File could not be opened."))
        parseText(text)
    }

    private fun parseText(text: String): ParseOutcome {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ParseOutcome.Failure(ImportResult.Empty)
        val root = try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            return ParseOutcome.Failure(ImportResult.Invalid("Not a valid .civ file (JSON parse failed)."))
        }
        val version = root.optInt("version", 0)
        when {
            version < CatalogStore.CATALOG_FORMAT_VERSION ->
                return ParseOutcome.Failure(
                    ImportResult.WrongVersion(version, CatalogStore.CATALOG_FORMAT_VERSION),
                )
            version > CatalogStore.CATALOG_FORMAT_VERSION ->
                return ParseOutcome.Failure(
                    ImportResult.NewerVersion(version, CatalogStore.CATALOG_FORMAT_VERSION),
                )
        }
        val arr = root.optJSONArray("books")
            ?: return ParseOutcome.Failure(ImportResult.Invalid("Missing 'books' array."))
        val books = ArrayList<Book>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.toBook()?.let { books += it }
        }
        val meta = CivExportMeta.fromJson(root.optJSONObject("meta"))
        return ParseOutcome.Success(books, meta)
    }

    private suspend fun buildPreview(books: List<Book>, meta: CivExportMeta?): ImportPreview {
        val merge = repository.previewMerge(books)
        val current = merge.totalAfter - merge.added
        return ImportPreview(
            incomingCount = books.size,
            addedCount = merge.added,
            skippedCount = merge.skipped,
            totalAfter = merge.totalAfter,
            currentCount = current,
            meta = meta,
        )
    }

    private suspend fun commitMerge(books: List<Book>, meta: CivExportMeta?): ImportResult {
        return try {
            val outcome = repository.mergeImport(books)
            val merge = outcome.result
            if (merge.added > 0) {
                writeBackup(outcome.previous)
                recordImportHistory(merge, meta, merge.addedBooks)
            } else {
                clearImportBackup()
            }
            ImportResult.Ok(
                addedCount = merge.added,
                skippedCount = merge.skipped,
                totalAfter = merge.totalAfter,
                previousCount = outcome.previous.size,
                backupAvailable = merge.added > 0 && hasBackup(),
                meta = meta,
            )
        } catch (e: Exception) {
            ImportResult.IoFailure(e.message ?: "Storage error while importing.")
        }
    }

    private fun recordImportHistory(
        merge: BookRepository.MergeImportResult,
        meta: CivExportMeta?,
        addedBooks: List<Book>,
    ) {
        val batch = meta?.batchNumber ?: 0
        val booksJson = encodeAddedBooks(addedBooks)
        prefs.edit()
            .putLong(PREF_LAST_AT, System.currentTimeMillis())
            .putInt(PREF_LAST_ADDED, merge.added)
            .putInt(PREF_LAST_SKIPPED, merge.skipped)
            .putInt(PREF_LAST_TOTAL, merge.totalAfter)
            .putInt(PREF_LAST_BATCH, batch)
            .putString(PREF_LAST_TOOL, meta?.tool.orEmpty())
            .putString(PREF_LAST_TOOL_VER, meta?.toolVersion.orEmpty())
            .putString(PREF_LAST_SOURCE, meta?.sourceFile.orEmpty())
            .putString(PREF_LAST_ADDED_BOOKS, booksJson)
            .apply()
    }

    private fun encodeAddedBooks(books: List<Book>): String {
        val arr = JSONArray()
        for (b in books.take(MAX_STORED_ADDED_BOOKS)) {
            arr.put(
                JSONObject()
                    .put("name", b.name)
                    .put("writer", b.writer),
            )
        }
        return arr.toString()
    }

    private fun parseAddedBooksJson(json: String): List<AddedBookLine> {
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        AddedBookLine(
                            name = o.optString("name", ""),
                            writer = o.optString("writer", ""),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** True iff a one-tap undo of the most recent import is available. */
    fun hasBackup(): Boolean = backupFile.exists() && backupFile.length() > 0

    private fun clearImportBackup() {
        try {
            if (backupFile.exists()) backupFile.delete()
        } catch (_: Exception) {
        }
    }

    /**
     * Restore the catalog snapshot taken before the most recent import.
     *
     * On success: returns the number of books restored AND clears the backup
     * (so the "Undo" button no longer claims there's something to undo — the
     * current state already equals the backup). On failure: returns -1.
     */
    suspend fun restoreLastImport(): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!hasBackup()) return@withContext -1
            val text = try {
                backupFile.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                return@withContext -1
            }
            val root = try { JSONObject(text) } catch (_: Exception) { return@withContext -1 }
            val arr = root.optJSONArray("books") ?: return@withContext -1
            val books = ArrayList<Book>(arr.length())
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.toBook()?.let { books += it }
            }
            try {
                repository.replaceAll(books)
            } catch (_: Exception) {
                return@withContext -1
            }
            // Clear the now-redundant backup. Best effort; if the delete fails,
            // the worst case is the Undo button stays visible.
            try { backupFile.delete() } catch (_: Exception) { }
            clearImportHistoryPrefs()
            books.size
        }
    }

    private fun clearImportHistoryPrefs() {
        prefs.edit().clear().apply()
    }

    private fun readBoundedText(input: InputStream): String {
        val out = StringBuilder()
        var totalChars = 0L
        input.reader(Charsets.UTF_8).use { reader ->
            val chars = CharArray(4 * 1024)
            while (true) {
                val n = reader.read(chars)
                if (n <= 0) break
                totalChars += n.toLong()
                if (totalChars > MAX_BYTES) {
                    throw IllegalStateException(
                        "File exceeds the ${MAX_BYTES / (1024 * 1024)} MB limit.",
                    )
                }
                out.append(chars, 0, n)
            }
        }
        return out.toString()
    }

    private fun writeBackup(books: List<Book>) {
        val arr = JSONArray()
        for (b in books) arr.put(b.toJson())
        val root = JSONObject()
            .put("version", CatalogStore.CATALOG_FORMAT_VERSION)
            .put("books", arr)
        atomicWriteText(backupFile, root.toString())
    }
}

/** Parse a single book object from a .civ document. Returns null on a missing id. */
private fun JSONObject.toBook(): Book? {
    val id = safeString("id").takeIf { it.isNotBlank() } ?: return null
    val parent = safeString("parentBookId").takeIf { it.isNotBlank() }
    return Book(
        id = id,
        logicalBookId = safeString("logicalBookId").ifBlank { id },
        version = optInt("version", 1),
        isLatest = optBoolean("isLatest", true),
        name = safeString("name"),
        topics = safeString("topics"),
        writer = safeString("writer"),
        bookNumber = safeString("bookNumber"),
        displayNumber = safeString("displayNumber"),
        letter = safeString("letter"),
        color = safeString("color"),
        category = safeString("category"),
        subcategories = optJSONArray("subcategories").toStringList(),
        notes = safeString("notes"),
        place = BookPlace.fromStored(safeString("place")),
        state = BookState.fromStored(safeString("state")),
        parentBookId = parent,
        relations = optJSONArray("relations").toStringList(),
        createdAt = optLong("createdAt"),
        updatedAt = optLong("updatedAt"),
    )
}

/**
 * Safer alternative to [JSONObject.optString]: returns "" for missing keys AND
 * for explicit JSON-null values. (Plain `optString` on JSON null returns the
 * literal string "null" on some Android versions, which would corrupt fields.)
 */
private fun JSONObject.safeString(key: String): String =
    if (isNull(key)) "" else optString(key, "")

private fun Book.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("logicalBookId", logicalBookId)
    put("version", version)
    put("isLatest", isLatest)
    put("name", name)
    put("topics", topics)
    put("writer", writer)
    put("bookNumber", bookNumber)
    put("displayNumber", displayNumber)
    put("letter", letter)
    put("color", color)
    put("category", category)
    put("subcategories", JSONArray(subcategories))
    put("notes", notes)
    put("place", place.storedValue)
    put("state", state.storedValue)
    put("parentBookId", parentBookId.orEmpty())
    put("relations", JSONArray(relations))
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        if (!isNull(i)) out += optString(i, "")
    }
    return out
}
