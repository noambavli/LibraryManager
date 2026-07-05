package com.mh.librarymanager.data.store

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Persists the id of the management-selected app theme (`filesDir/theme.json`).
 *
 * The store is intentionally pure data — it holds only a string id and never
 * references any UI/Color type. Mapping an id to an actual palette happens in
 * the UI layer, keeping the data layer decoupled. A missing/corrupt file simply
 * yields the default id, so theming can never fail to load.
 */
class ThemeStore(private val context: Context) {

    companion object {
        const val FORMAT_VERSION = 1
        const val FILE_NAME = "theme.json"
        const val DEFAULT_ID = "blue"
    }

    private val file: File by lazy { File(context.filesDir, FILE_NAME) }

    private val _selectedId = MutableStateFlow(DEFAULT_ID)
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    private val loadState = StoreLoadState()
    val loaded: StateFlow<Boolean> = loadState.loaded

    /**
     * Synchronous read for cold start, so the very first frame already paints in
     * the saved theme (no flash of the default). Reads a tiny file on the
     * calling thread, exactly as SharedPreferences would.
     */
    fun peekSelectedId(): String = synchronized(this) {
        val id = if (file.exists()) readFile(file) else DEFAULT_ID
        _selectedId.value = id
        loadState.markLoaded()
        id
    }

    suspend fun loadFromDisk() {
        loadFromDiskOnce(loadedFlag = loadState::isLoaded, lock = this) {
            _selectedId.value = if (file.exists()) readFile(file) else DEFAULT_ID
            loadState.markLoaded()
        }
    }

    /** Force a re-read from disk, e.g. after a backup restore overwrote the file. */
    suspend fun reloadFromDisk() {
        withContext(Dispatchers.IO) {
            synchronized(this@ThemeStore) {
                _selectedId.value = if (file.exists()) readFile(file) else DEFAULT_ID
                loadState.markLoaded()
            }
        }
    }

    suspend fun setTheme(id: String) {
        val clean = id.trim().ifEmpty { DEFAULT_ID }
        withContext(Dispatchers.IO) {
            synchronized(this@ThemeStore) {
                if (_selectedId.value == clean && loadState.isLoaded()) return@withContext
                writeFile(clean)
                _selectedId.value = clean
                loadState.markLoaded()
            }
        }
    }

    private fun readFile(file: File): String {
        return try {
            val text = file.readText(Charsets.UTF_8)
            if (text.isBlank()) return DEFAULT_ID
            val root = JSONObject(text)
            if (root.optInt("version", 0) < FORMAT_VERSION) return DEFAULT_ID
            root.optString("themeId", DEFAULT_ID).ifBlank { DEFAULT_ID }
        } catch (_: Exception) {
            DEFAULT_ID
        }
    }

    private fun writeFile(id: String) {
        val root = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("themeId", id)
        atomicWriteText(file, root.toString())
    }
}
