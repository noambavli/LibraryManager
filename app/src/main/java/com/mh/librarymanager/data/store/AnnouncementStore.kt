package com.mh.librarymanager.data.store

import android.content.Context
import com.mh.librarymanager.domain.Announcement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Local persistence for staff announcements (`filesDir/announcements.json`).
 *
 * Same dependency-free JSON + atomic-write approach as the other stores. The
 * list is bounded by [MAX_ENTRIES]; expiry is computed on read by the domain
 * model rather than pruned here, so an announcement can be "revived" by editing
 * its duration in a future iteration without data loss.
 */
class AnnouncementStore(private val context: Context) {

    companion object {
        const val ANNOUNCEMENTS_FORMAT_VERSION = 1
        const val MAX_ENTRIES = 500
    }

    private val file: File by lazy { File(context.filesDir, "announcements.json") }

    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    val announcements: StateFlow<List<Announcement>> = _announcements.asStateFlow()

    @Volatile private var loaded = false

    suspend fun loadFromDisk() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _announcements.value = if (file.exists()) readFile(file) else emptyList()
            loaded = true
        }
    }

    suspend fun add(announcement: Announcement) {
        loadFromDisk()
        val next = (_announcements.value + announcement).takeLast(MAX_ENTRIES)
        writeFile(file, next)
        _announcements.value = next
    }

    suspend fun remove(id: String) {
        loadFromDisk()
        val next = _announcements.value.filterNot { it.id == id }
        if (next.size == _announcements.value.size) return
        writeFile(file, next)
        _announcements.value = next
    }

    private fun readFile(file: File): List<Announcement> {
        val text = file.readText(Charsets.UTF_8)
        if (text.isBlank()) return emptyList()
        val root = JSONObject(text)
        if (root.optInt("version", 0) < ANNOUNCEMENTS_FORMAT_VERSION) return emptyList()
        val arr = root.optJSONArray("announcements") ?: return emptyList()
        val out = ArrayList<Announcement>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += Announcement(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = o.optString("title"),
                description = o.optString("description"),
                createdAt = o.optLong("createdAt"),
                durationDays = o.optInt("durationDays", Announcement.DEFAULT_DURATION_DAYS),
                linkedBookIds = o.optJSONArray("linkedBookIds")?.toStringList().orEmpty(),
            )
        }
        return out
    }

    private fun writeFile(target: File, announcements: List<Announcement>) {
        val arr = JSONArray()
        for (a in announcements) {
            val o = JSONObject()
            o.put("id", a.id)
            o.put("title", a.title)
            o.put("description", a.description)
            o.put("createdAt", a.createdAt)
            o.put("durationDays", a.durationDays)
            o.put("linkedBookIds", JSONArray(a.linkedBookIds))
            arr.put(o)
        }
        val root = JSONObject()
            .put("version", ANNOUNCEMENTS_FORMAT_VERSION)
            .put("announcements", arr)
        atomicWrite(target, root.toString())
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(content, Charsets.UTF_8)
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }

    private fun JSONArray.toStringList(): List<String> {
        val out = ArrayList<String>(length())
        for (i in 0 until length()) out += getString(i)
        return out
    }
}
