package com.mh.librarymanager.data.civ

import android.content.Context
import android.net.Uri
import com.mh.librarymanager.data.BookRepository
import com.mh.librarymanager.data.store.CatalogStore
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
 * Reads `.civ` catalogs produced by the desktop LibraryTool and replaces the
 * in-app catalog with their contents.
 *
 * A `.civ` document is intentionally **byte-for-byte the same JSON shape** as
 * the tablet's own [CatalogStore] writes to `filesDir/catalog.json` — so the
 * desktop side never needs to know about anything beyond a single, stable
 * format, and the import here is a small JSON parse + a `replaceAll`.
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
    }

    sealed interface ImportResult {
        data class Ok(
            val importedCount: Int,
            val previousCount: Int,
            val backupAvailable: Boolean,
        ) : ImportResult

        data class WrongVersion(val found: Int, val expected: Int) : ImportResult
        data class NewerVersion(val found: Int, val expected: Int) : ImportResult
        data object Empty : ImportResult
        data class Invalid(val reason: String) : ImportResult
        data class TooLarge(val sizeBytes: Long) : ImportResult
        data class IoFailure(val reason: String) : ImportResult
    }

    private val backupFile: File by lazy { File(context.filesDir, BACKUP_FILE_NAME) }
    private val mutex = Mutex()

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

    suspend fun importFromText(text: String): ImportResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return@withContext ImportResult.Empty

            val root = try {
                JSONObject(trimmed)
            } catch (_: Exception) {
                return@withContext ImportResult.Invalid("Not a valid .civ file (JSON parse failed).")
            }

            val version = root.optInt("version", 0)
            when {
                version < CatalogStore.CATALOG_FORMAT_VERSION ->
                    return@withContext ImportResult.WrongVersion(
                        version, CatalogStore.CATALOG_FORMAT_VERSION,
                    )
                version > CatalogStore.CATALOG_FORMAT_VERSION ->
                    return@withContext ImportResult.NewerVersion(
                        version, CatalogStore.CATALOG_FORMAT_VERSION,
                    )
            }

            val arr = root.optJSONArray("books")
                ?: return@withContext ImportResult.Invalid("Missing 'books' array.")

            val books = ArrayList<Book>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                o.toBook()?.let { books += it }
            }

            // Destructive section — any failure here MUST surface as a typed
            // result so the UI never gets stuck on "Importing…".
            try {
                val previous = repository.snapshotForBackup()
                writeBackup(previous)
                repository.replaceAll(books)
                ImportResult.Ok(
                    importedCount = books.size,
                    previousCount = previous.size,
                    backupAvailable = true,
                )
            } catch (e: Exception) {
                ImportResult.IoFailure(e.message ?: "Storage error while importing.")
            }
        }
    }

    /** True iff a one-tap undo of the most recent import is available. */
    fun hasBackup(): Boolean = backupFile.exists() && backupFile.length() > 0

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
            books.size
        }
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
        val tmp = File(backupFile.parentFile, BACKUP_FILE_NAME + ".tmp")
        tmp.writeText(root.toString(), Charsets.UTF_8)
        if (backupFile.exists()) backupFile.delete()
        if (!tmp.renameTo(backupFile)) {
            // Fall back to a copy if rename failed (e.g. weird filesystem) so
            // we never end up with NO backup at all.
            backupFile.writeText(root.toString(), Charsets.UTF_8)
            tmp.delete()
        }
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
