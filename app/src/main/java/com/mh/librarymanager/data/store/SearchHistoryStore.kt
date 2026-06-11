package com.mh.librarymanager.data.store

import android.content.Context
import com.mh.librarymanager.domain.SearchHistoryEntry
import com.mh.librarymanager.search.SearchQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Persists anonymous public-search history for management review.
 *
 * Entries older than [RETENTION_MS] are dropped on load and append. Only the
 * public [com.mh.librarymanager.ui.search.SearchViewModel] writes here — the
 * management books search never touches this file.
 */
class SearchHistoryStore(private val context: Context) {

    companion object {
        const val FORMAT_VERSION = 1
        /** ~6 months. */
        const val RETENTION_MS: Long = 183L * 24L * 60L * 60L * 1000L
        const val MAX_ENTRIES = 50_000
    }

    private val file: File by lazy { File(context.filesDir, "search_history.json") }

    private val _entries = MutableStateFlow<List<SearchHistoryEntry>>(emptyList())
    val entries: StateFlow<List<SearchHistoryEntry>> = _entries.asStateFlow()

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

    suspend fun append(entry: SearchHistoryEntry) {
        loadFromDisk()
        val next = prune(_entries.value + entry)
        writeFile(file, next)
        _entries.value = next
    }

    suspend fun remove(id: String) {
        loadFromDisk()
        val next = _entries.value.filterNot { it.id == id }
        if (next.size == _entries.value.size) return
        writeFile(file, next)
        _entries.value = next
    }

    private fun prune(list: List<SearchHistoryEntry>, now: Long = System.currentTimeMillis()): List<SearchHistoryEntry> {
        val cutoff = now - RETENTION_MS
        return list
            .filter { it.searchedAt >= cutoff }
            .takeLast(MAX_ENTRIES)
    }

    private fun readFile(file: File): List<SearchHistoryEntry> {
        val text = file.readText(Charsets.UTF_8)
        if (text.isBlank()) return emptyList()
        val root = JSONObject(text)
        if (root.optInt("version", 0) < FORMAT_VERSION) return emptyList()
        val arr = root.optJSONArray("searches") ?: return emptyList()
        val out = ArrayList<SearchHistoryEntry>(arr.length())
        for (i in 0 until arr.length()) {
            out += parseEntry(arr.getJSONObject(i))
        }
        return out
    }

    private fun parseEntry(o: JSONObject): SearchHistoryEntry = SearchHistoryEntry(
        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
        searchedAt = o.optLong("searchedAt"),
        query = SearchQuery(
            general = o.optString("general"),
            name = o.optString("name"),
            topics = o.optString("topics"),
            writer = o.optString("writer"),
            letter = o.optString("letter"),
            color = o.optString("color"),
            category = o.optString("category"),
            subcategory = o.optString("subcategory"),
            displayNumber = o.optString("displayNumber"),
            bookNumber = o.optString("bookNumber"),
            notes = o.optString("notes"),
        ),
        resultCount = o.optInt("resultCount"),
    )

    private fun writeFile(target: File, entries: List<SearchHistoryEntry>) {
        val arr = JSONArray()
        for (e in entries) {
            val q = e.query
            val o = JSONObject()
            o.put("id", e.id)
            o.put("searchedAt", e.searchedAt)
            o.put("general", q.general)
            o.put("name", q.name)
            o.put("topics", q.topics)
            o.put("writer", q.writer)
            o.put("letter", q.letter)
            o.put("color", q.color)
            o.put("category", q.category)
            o.put("subcategory", q.subcategory)
            o.put("displayNumber", q.displayNumber)
            o.put("bookNumber", q.bookNumber)
            o.put("notes", q.notes)
            o.put("resultCount", e.resultCount)
            arr.put(o)
        }
        val root = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("searches", arr)
        atomicWriteText(target, root.toString())
    }
}
