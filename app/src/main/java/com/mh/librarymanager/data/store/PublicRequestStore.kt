package com.mh.librarymanager.data.store

import android.content.Context
import com.mh.librarymanager.domain.PublicRequest
import com.mh.librarymanager.domain.RequestStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Local persistence for public book requests.
 *
 * Stored as `filesDir/requests.json`. Mirrors the lightweight, dependency-free
 * JSON approach used by [AuditStore] / [CatalogStore]. The log is bounded by
 * [MAX_ENTRIES] so the kiosk never accumulates an unbounded file.
 */
class PublicRequestStore(private val context: Context) {

    companion object {
        const val REQUESTS_FORMAT_VERSION = 1
        const val MAX_ENTRIES = 1000
    }

    private val file: File by lazy { File(context.filesDir, "requests.json") }

    private val _requests = MutableStateFlow<List<PublicRequest>>(emptyList())
    val requests: StateFlow<List<PublicRequest>> = _requests.asStateFlow()

    @Volatile private var loaded = false

    suspend fun loadFromDisk() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _requests.value = if (file.exists()) readFile(file) else emptyList()
            loaded = true
        }
    }

    suspend fun add(request: PublicRequest) {
        loadFromDisk()
        val next = (_requests.value + request).takeLast(MAX_ENTRIES)
        writeFile(file, next)
        _requests.value = next
    }

    suspend fun updateStatus(id: String, status: RequestStatus) {
        loadFromDisk()
        val now = System.currentTimeMillis()
        var changed = false
        val next = _requests.value.map { req ->
            if (req.id == id && req.status != status) {
                changed = true
                req.copy(status = status, updatedAt = now)
            } else {
                req
            }
        }
        if (!changed) return
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

    private fun readFile(file: File): List<PublicRequest> {
        val text = file.readText(Charsets.UTF_8)
        if (text.isBlank()) return emptyList()
        val root = JSONObject(text)
        if (root.optInt("version", 0) < REQUESTS_FORMAT_VERSION) return emptyList()
        val arr = root.optJSONArray("requests") ?: return emptyList()
        val out = ArrayList<PublicRequest>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val created = o.optLong("createdAt")
            out += PublicRequest(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                requesterName = o.optString("requesterName"),
                bookName = o.optString("bookName"),
                details = o.optString("details"),
                status = RequestStatus.fromStored(o.optString("status")),
                createdAt = created,
                updatedAt = o.optLong("updatedAt", created),
            )
        }
        return out
    }

    private fun writeFile(target: File, requests: List<PublicRequest>) {
        val arr = JSONArray()
        for (r in requests) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("requesterName", r.requesterName)
            o.put("bookName", r.bookName)
            o.put("details", r.details)
            o.put("status", r.status.storedValue)
            o.put("createdAt", r.createdAt)
            o.put("updatedAt", r.updatedAt)
            arr.put(o)
        }
        val root = JSONObject()
            .put("version", REQUESTS_FORMAT_VERSION)
            .put("requests", arr)
        atomicWrite(target, root.toString())
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(content, Charsets.UTF_8)
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }
}
