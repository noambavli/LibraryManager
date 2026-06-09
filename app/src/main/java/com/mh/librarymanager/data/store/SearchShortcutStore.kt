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

    @Volatile private var loaded = false

    suspend fun loadFromDisk() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _shortcuts.value = if (file.exists()) readFile(file) else emptyList()
            loaded = true
        }
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
        atomicWrite(target, root.toString())
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(content, Charsets.UTF_8)
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }
}
