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
 * Process-global snapshot of the current text overrides, keyed by string
 * resource entry name (e.g. `home_title`).
 *
 * Composable code reads overrides reactively through the `LocalTextOverrides`
 * composition local. This registry exists purely so non-composable callers
 * (view-models building status strings via `Context.appString`) can resolve the
 * same overrides without threading the store everywhere. It is always written
 * by [TextOverrideStore] on the same value the flow emits, so the two never
 * diverge.
 */
object TextOverrideRegistry {
    @Volatile
    var current: Map<String, String> = emptyMap()
        internal set
}

/**
 * Persistence for management-authored text overrides (`filesDir/text_overrides.json`).
 *
 * The app ships default copy in `strings.xml`. Management may replace the visible
 * text of any catalogued string; the new value is stored here keyed by the
 * string's stable resource *entry name*. Anything without an override falls back
 * to the shipped default, so this file only ever holds the deltas — a fresh
 * install, a wiped file, or a bad JSON blob all safely degrade to "no overrides".
 */
class TextOverrideStore(private val context: Context) {

    companion object {
        const val FORMAT_VERSION = 1
        const val FILE_NAME = "text_overrides.json"
    }

    private val file: File by lazy { File(context.filesDir, FILE_NAME) }

    private val _overrides = MutableStateFlow<Map<String, String>>(emptyMap())
    val overrides: StateFlow<Map<String, String>> = _overrides.asStateFlow()

    private val loadState = StoreLoadState()
    val loaded: StateFlow<Boolean> = loadState.loaded

    private fun publish(value: Map<String, String>) {
        _overrides.value = value
        TextOverrideRegistry.current = value
    }

    suspend fun loadFromDisk() {
        loadFromDiskOnce(loadedFlag = loadState::isLoaded, lock = this) {
            publish(if (file.exists()) readFile(file) else emptyMap())
            loadState.markLoaded()
        }
    }

    /** Force a re-read from disk, e.g. after a backup restore overwrote the file. */
    suspend fun reloadFromDisk() {
        withContext(Dispatchers.IO) {
            synchronized(this@TextOverrideStore) {
                publish(if (file.exists()) readFile(file) else emptyMap())
                loadState.markLoaded()
            }
        }
    }

    /**
     * Set (or clear) the override for [key]. A blank value clears the override
     * so the shipped default takes over again — management can never blank out a
     * label into nothing by mistake.
     */
    suspend fun set(key: String, value: String) {
        val trimmedKey = key.trim()
        if (trimmedKey.isEmpty()) return
        withContext(Dispatchers.IO) {
            synchronized(this@TextOverrideStore) {
                loadIfNeededLocked()
                val next = HashMap(_overrides.value)
                if (value.isBlank()) {
                    if (next.remove(trimmedKey) == null) return@withContext
                } else {
                    if (next[trimmedKey] == value) return@withContext
                    next[trimmedKey] = value
                }
                writeFile(next)
                publish(next)
            }
        }
    }

    suspend fun reset(key: String) = set(key, "")

    suspend fun resetAll() {
        withContext(Dispatchers.IO) {
            synchronized(this@TextOverrideStore) {
                if (_overrides.value.isEmpty() && !file.exists()) return@withContext
                if (file.exists()) file.delete()
                publish(emptyMap())
                loadState.markLoaded()
            }
        }
    }

    private fun loadIfNeededLocked() {
        if (loadState.isLoaded()) return
        publish(if (file.exists()) readFile(file) else emptyMap())
        loadState.markLoaded()
    }

    private fun readFile(file: File): Map<String, String> {
        return try {
            val text = file.readText(Charsets.UTF_8)
            if (text.isBlank()) return emptyMap()
            val root = JSONObject(text)
            if (root.optInt("version", 0) < FORMAT_VERSION) return emptyMap()
            val obj = root.optJSONObject("overrides") ?: return emptyMap()
            val out = HashMap<String, String>(obj.length())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k, "")
                if (k.isNotBlank() && v.isNotEmpty()) out[k] = v
            }
            out
        } catch (_: Exception) {
            // A corrupt file must never crash the app or block startup — treat it
            // as "no overrides" and let a future write heal it.
            emptyMap()
        }
    }

    private fun writeFile(overrides: Map<String, String>) {
        val obj = JSONObject()
        for ((k, v) in overrides) obj.put(k, v)
        val root = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("overrides", obj)
        atomicWriteText(file, root.toString())
    }
}
