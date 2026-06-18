package com.mh.librarymanager.data.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistence for public-search quick shortcuts (`filesDir/shortcuts.json`).
 *
 * A shortcut is just a search word/phrase shown as a tag above the general
 * search field; tapping it fills that field. Capped at [MAX_SHORTCUTS].
 */
class SearchShortcutStore(private val context: Context) {

    companion object {
        const val SHORTCUTS_FORMAT_VERSION = 1
        const val MAX_SHORTCUTS = 7
    }

    private val file: File by lazy { File(context.filesDir, "shortcuts.json") }

    private val _shortcuts = MutableStateFlow<List<String>>(emptyList())
    val shortcuts: StateFlow<List<String>> = _shortcuts.asStateFlow()

    private val loadState = StoreLoadState()
    val loaded: StateFlow<Boolean> = loadState.loaded

    suspend fun loadFromDisk() {
        loadFromDiskOnce(loadedFlag = loadState::isLoaded, lock = this) {
            _shortcuts.value = if (file.exists()) readFile(file) else emptyList()
            loadState.markLoaded()
        }
    }

    /** Force a re-read from disk, e.g. after a backup restore overwrote the file. */
    suspend fun reloadFromDisk() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            synchronized(this@SearchShortcutStore) {
                _shortcuts.value = if (file.exists()) readFile(file) else emptyList()
                loadState.markLoaded()
            }
        }
    }

    /**
     * Replace the whole shortcut list (bulk xlsx import). Blank/duplicate
     * entries are dropped and the result is capped at [MAX_SHORTCUTS], so a
     * messy spreadsheet can never produce an invalid state. Returns the list
     * actually persisted.
     */
    suspend fun replaceAll(words: List<String>): List<String> {
        loadFromDisk()
        val clean = ArrayList<String>(words.size)
        for (raw in words) {
            val w = raw.trim()
            if (w.isEmpty()) continue
            if (clean.any { it.equals(w, ignoreCase = true) }) continue
            clean += w
            if (clean.size >= MAX_SHORTCUTS) break
        }
        writeFile(file, clean)
        _shortcuts.value = clean
        return clean
    }

    enum class AddResult { Ok, Blank, Duplicate, LimitReached }

    suspend fun add(word: String): AddResult {
        loadFromDisk()
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return AddResult.Blank
        val current = _shortcuts.value
        if (current.size >= MAX_SHORTCUTS) return AddResult.LimitReached
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return AddResult.Duplicate
        val next = current + trimmed
        writeFile(file, next)
        _shortcuts.value = next
        return AddResult.Ok
    }

    suspend fun remove(word: String) {
        loadFromDisk()
        val next = _shortcuts.value.filterNot { it == word }
        if (next.size == _shortcuts.value.size) return
        writeFile(file, next)
        _shortcuts.value = next
    }

    private fun readFile(file: File): List<String> {
        val text = file.readText(Charsets.UTF_8)
        if (text.isBlank()) return emptyList()
        val root = JSONObject(text)
        if (root.optInt("version", 0) < SHORTCUTS_FORMAT_VERSION) return emptyList()
        val arr = root.optJSONArray("shortcuts") ?: return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.optString(i).trim()
            if (v.isNotEmpty()) out += v
        }
        return out.take(MAX_SHORTCUTS)
    }

    private fun writeFile(target: File, shortcuts: List<String>) {
        val root = JSONObject()
            .put("version", SHORTCUTS_FORMAT_VERSION)
            .put("shortcuts", JSONArray(shortcuts))
        atomicWriteText(target, root.toString())
    }
}
