package com.mh.librarymanager.data.store

import android.content.Context
import com.mh.librarymanager.domain.BookLocationPressEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Persists anonymous map-button presses for management popularity stats.
 * Entries older than [RETENTION_MS] are dropped on load and append.
 */
class BookLocationPressStore(private val context: Context) {

    companion object {
        const val FORMAT_VERSION = 1
        /** ~6 months. */
        const val RETENTION_MS: Long = 183L * 24L * 60L * 60L * 1000L
        const val MAX_ENTRIES = 50_000
    }

    private val file: File by lazy { File(context.filesDir, "book_location_presses.json") }

    private val _entries = MutableStateFlow<List<BookLocationPressEntry>>(emptyList())
    val entries: StateFlow<List<BookLocationPressEntry>> = _entries.asStateFlow()

    @Volatile private var loaded = false

    suspend fun loadFromDisk() {
        loadFromDiskOnce(loadedFlag = { loaded }, lock = this) {
            val raw = if (file.exists()) readFile(file) else emptyList()
            val pruned = prune(raw)
            if (pruned.size != raw.size) {
                writeFile(file, pruned)
            }
            _entries.value = pruned
            loaded = true
        }
    }

    suspend fun recordPress(bookId: String) {
        if (bookId.isBlank()) return
        loadFromDisk()
        val entry = BookLocationPressEntry(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            pressedAt = System.currentTimeMillis(),
        )
        val next = prune(_entries.value + entry)
        writeFile(file, next)
        _entries.value = next
    }

    private fun prune(list: List<BookLocationPressEntry>, now: Long = System.currentTimeMillis()): List<BookLocationPressEntry> {
        val cutoff = now - RETENTION_MS
        return list
            .filter { it.pressedAt >= cutoff }
            .takeLast(MAX_ENTRIES)
    }

    private fun readFile(file: File): List<BookLocationPressEntry> {
        val text = file.readText(Charsets.UTF_8)
        if (text.isBlank()) return emptyList()
        val root = JSONObject(text)
        if (root.optInt("version", 0) < FORMAT_VERSION) return emptyList()
        val arr = root.optJSONArray("presses") ?: return emptyList()
        val out = ArrayList<BookLocationPressEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += BookLocationPressEntry(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                bookId = o.optString("bookId"),
                pressedAt = o.optLong("pressedAt"),
            )
        }
        return out
    }

    private fun writeFile(target: File, entries: List<BookLocationPressEntry>) {
        val arr = JSONArray()
        for (e in entries) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("bookId", e.bookId)
            o.put("pressedAt", e.pressedAt)
            arr.put(o)
        }
        val root = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("presses", arr)
        atomicWriteText(target, root.toString())
    }
}
