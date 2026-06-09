package com.mh.librarymanager.data.store

import android.content.Context
import com.mh.librarymanager.domain.AuditEvent
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.BookPlace
import com.mh.librarymanager.domain.BookState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Local audit-log persistence.
 *
 * Each mutation that flows through `BookRepository` writes one entry to
 * `filesDir/audit.json`. The log is bounded by [MAX_ENTRIES] to keep
 * read/write cheap on the kiosk hardware — when the cap is hit the oldest
 * entries are trimmed.
 *
 * Entries are immutable. To "delete" an entry (after a restore consumes it)
 * we still keep it in the log but its restore action becomes inert thanks
 * to the safety checks in the view-model.
 */
class AuditStore(private val context: Context) {

    companion object {
        const val AUDIT_FORMAT_VERSION = 1
        const val MAX_ENTRIES = 500
    }

    private val file: File by lazy { File(context.filesDir, "audit.json") }

    private val _events = MutableStateFlow<List<AuditEvent>>(emptyList())
    val events: StateFlow<List<AuditEvent>> = _events.asStateFlow()

    @Volatile private var loaded = false

    suspend fun loadFromDisk() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _events.value = if (file.exists()) readFile(file) else emptyList()
            loaded = true
        }
    }

    suspend fun append(event: AuditEvent) {
        loadFromDisk()
        val next = (_events.value + event)
            .takeLast(MAX_ENTRIES)
        writeFile(file, next)
        _events.value = next
    }

    /** Removes the entry with [eventId], if any. Used after a restore consumes it. */
    suspend fun remove(eventId: String) {
        loadFromDisk()
        val next = _events.value.filterNot { it.id == eventId }
        if (next.size == _events.value.size) return
        writeFile(file, next)
        _events.value = next
    }

    private fun readFile(file: File): List<AuditEvent> {
        val text = file.readText(Charsets.UTF_8)
        if (text.isBlank()) return emptyList()
        val root = JSONObject(text)
        if (root.optInt("version", 0) < AUDIT_FORMAT_VERSION) return emptyList()
        val arr = root.optJSONArray("events") ?: return emptyList()
        val out = ArrayList<AuditEvent>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val kind = o.optString("kind")
            val id = o.optString("id").ifBlank { UUID.randomUUID().toString() }
            val ts = o.optLong("timestamp")
            when (kind) {
                "ADDED" -> {
                    val snap = o.optJSONObject("snapshot")?.let(::readBook) ?: continue
                    out += AuditEvent.Added(
                        id = id, timestamp = ts,
                        bookId = snap.id, bookName = snap.name,
                        snapshot = snap,
                    )
                }
                "UPDATED" -> {
                    val before = o.optJSONObject("before")?.let(::readBook) ?: continue
                    val after = o.optJSONObject("after")?.let(::readBook) ?: continue
                    out += AuditEvent.Updated(
                        id = id, timestamp = ts,
                        bookId = after.id, bookName = after.name,
                        before = before, after = after,
                    )
                }
                "DELETED" -> {
                    val snap = o.optJSONObject("snapshot")?.let(::readBook) ?: continue
                    out += AuditEvent.Deleted(
                        id = id, timestamp = ts,
                        bookId = snap.id, bookName = snap.name,
                        snapshot = snap,
                    )
                }
                "IMPORTED" -> {
                    out += AuditEvent.Imported(
                        id = id, timestamp = ts,
                        importedCount = o.optInt("count"),
                    )
                }
            }
        }
        return out
    }

    private fun writeFile(target: File, events: List<AuditEvent>) {
        val arr = JSONArray()
        for (e in events) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("timestamp", e.timestamp)
            o.put("kind", e.kind.name)
            when (e) {
                is AuditEvent.Added -> o.put("snapshot", writeBook(e.snapshot))
                is AuditEvent.Updated -> {
                    o.put("before", writeBook(e.before))
                    o.put("after", writeBook(e.after))
                }
                is AuditEvent.Deleted -> o.put("snapshot", writeBook(e.snapshot))
                is AuditEvent.Imported -> o.put("count", e.importedCount)
            }
            arr.put(o)
        }
        val root = JSONObject().put("version", AUDIT_FORMAT_VERSION).put("events", arr)
        atomicWrite(target, root.toString())
    }

    private fun readBook(o: JSONObject): Book = Book(
        id = o.optString("id"),
        logicalBookId = o.optString("logicalBookId", o.optString("id")),
        version = o.optInt("version", 1),
        isLatest = o.optBoolean("isLatest", true),
        name = o.optString("name"),
        topics = o.optString("topics"),
        writer = o.optString("writer"),
        bookNumber = o.optString("bookNumber"),
        displayNumber = o.optString("displayNumber"),
        letter = o.optString("letter"),
        color = o.optString("color"),
        category = o.optString("category"),
        subcategories = o.optJSONArray("subcategories")?.toStringList().orEmpty(),
        notes = o.optString("notes"),
        place = BookPlace.fromStored(o.optString("place")),
        state = BookState.fromStored(o.optString("state")),
        parentBookId = o.optString("parentBookId").takeIf { it.isNotBlank() },
        relations = o.optJSONArray("relations")?.toStringList().orEmpty(),
        createdAt = o.optLong("createdAt"),
        updatedAt = o.optLong("updatedAt"),
    )

    private fun writeBook(b: Book): JSONObject {
        val o = JSONObject()
        o.put("id", b.id)
        o.put("logicalBookId", b.logicalBookId)
        o.put("version", b.version)
        o.put("isLatest", b.isLatest)
        o.put("name", b.name)
        o.put("topics", b.topics)
        o.put("writer", b.writer)
        o.put("bookNumber", b.bookNumber)
        o.put("displayNumber", b.displayNumber)
        o.put("letter", b.letter)
        o.put("color", b.color)
        o.put("category", b.category)
        o.put("subcategories", JSONArray(b.subcategories))
        o.put("notes", b.notes)
        o.put("place", b.place.storedValue)
        o.put("state", b.state.storedValue)
        o.put("parentBookId", b.parentBookId.orEmpty())
        o.put("relations", JSONArray(b.relations))
        o.put("createdAt", b.createdAt)
        o.put("updatedAt", b.updatedAt)
        return o
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
