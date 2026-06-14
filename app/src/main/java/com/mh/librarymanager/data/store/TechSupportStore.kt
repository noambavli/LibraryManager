package com.mh.librarymanager.data.store

import android.content.Context
import com.mh.librarymanager.domain.TechSupportRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Local persistence for public technical-support reports.
 * Stored as `filesDir/tech_support.json`.
 */
class TechSupportStore(private val context: Context) {

    companion object {
        const val FORMAT_VERSION = 1
        const val MAX_ENTRIES = 500
    }

    private val file: File by lazy { File(context.filesDir, "tech_support.json") }

    private val _requests = MutableStateFlow<List<TechSupportRequest>>(emptyList())
    val requests: StateFlow<List<TechSupportRequest>> = _requests.asStateFlow()

    private val loadState = StoreLoadState()
    val loaded: StateFlow<Boolean> = loadState.loaded

    suspend fun loadFromDisk() {
        loadFromDiskOnce(loadedFlag = loadState::isLoaded, lock = this) {
            _requests.value = if (file.exists()) readFile(file) else emptyList()
            loadState.markLoaded()
        }
    }

    suspend fun add(request: TechSupportRequest) {
        loadFromDisk()
        val next = (_requests.value + request).takeLast(MAX_ENTRIES)
        writeFile(file, next)
        _requests.value = next
    }

    suspend fun remove(id: String) {
        loadFromDisk()
        val next = _requests.value.filterNot { it.id == id }
        if (next.size == _requests.value.size) return
        writeFile(file, next)
        _requests.value = next
    }

    private fun readFile(file: File): List<TechSupportRequest> {
        val text = file.readText(Charsets.UTF_8)
        if (text.isBlank()) return emptyList()
        val root = JSONObject(text)
        if (root.optInt("version", 0) < FORMAT_VERSION) return emptyList()
        val arr = root.optJSONArray("requests") ?: return emptyList()
        val out = ArrayList<TechSupportRequest>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += TechSupportRequest(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                reporterName = o.optString("reporterName"),
                problem = o.optString("problem"),
                createdAt = o.optLong("createdAt"),
            )
        }
        return out
    }

    private fun writeFile(target: File, requests: List<TechSupportRequest>) {
        val arr = JSONArray()
        for (r in requests) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("reporterName", r.reporterName)
            o.put("problem", r.problem)
            o.put("createdAt", r.createdAt)
            arr.put(o)
        }
        val root = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("requests", arr)
        atomicWriteText(target, root.toString())
    }
}
