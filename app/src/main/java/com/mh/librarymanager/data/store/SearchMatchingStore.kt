package com.mh.librarymanager.data.store

import android.content.Context
import com.mh.librarymanager.domain.MatchingDirection
import com.mh.librarymanager.domain.SearchMatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Local persistence for staff-managed search synonyms (`filesDir/matchings.json`).
 *
 * Same dependency-free JSON + atomic-write approach as the other stores. Each
 * entry pairs a shortcut with an ordered list of words and a direction. The
 * list is bounded by [MAX_ENTRIES]; entries are kept in insertion order so the
 * management screen can reorder freely.
 */
class SearchMatchingStore(private val context: Context) {

    companion object {
        const val MATCHINGS_FORMAT_VERSION = 1
        const val MAX_ENTRIES = 500
        const val MAX_WORDS = 50
    }

    private val file: File by lazy { File(context.filesDir, "matchings.json") }

    private val _matchings = MutableStateFlow<List<SearchMatching>>(emptyList())
    val matchings: StateFlow<List<SearchMatching>> = _matchings.asStateFlow()

    private val loadState = StoreLoadState()
    val loaded: StateFlow<Boolean> = loadState.loaded

    suspend fun loadFromDisk() {
        loadFromDiskOnce(loadedFlag = loadState::isLoaded, lock = this) {
            _matchings.value = if (file.exists()) readFile(file) else emptyList()
            loadState.markLoaded()
        }
    }

    /** Force a re-read from disk, e.g. after a backup restore overwrote the file. */
    suspend fun reloadFromDisk() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            synchronized(this@SearchMatchingStore) {
                _matchings.value = if (file.exists()) readFile(file) else emptyList()
                loadState.markLoaded()
            }
        }
    }

    /**
     * Replace every matching at once (backup restore / internal bulk reset).
     * Each row's words are cleaned/de-duped and rows with a blank shortcut or no
     * words are dropped; the result is capped at [MAX_ENTRIES].
     */
    suspend fun replaceAll(incoming: List<SearchMatching>): List<SearchMatching> {
        loadFromDisk()
        val now = System.currentTimeMillis()
        val out = ArrayList<SearchMatching>(incoming.size)
        val seenShortcuts = HashSet<String>()
        for (m in incoming) {
            val shortcut = m.shortcut.trim()
            if (shortcut.isEmpty()) continue
            val words = cleanWords(m.words)
            if (words.isEmpty()) continue
            // Collapse duplicate shortcuts so the visible list stays clean.
            if (!seenShortcuts.add(shortcut.lowercase())) continue
            out += m.copy(
                shortcut = shortcut,
                words = words,
                createdAt = if (m.createdAt > 0) m.createdAt else now,
                updatedAt = now,
            )
            if (out.size >= MAX_ENTRIES) break
        }
        writeFile(file, out)
        _matchings.value = out
        return out
    }

    data class MergeImportResult(
        val added: Int,
        /** Existing shortcuts whose words or direction were changed. */
        val updated: Int,
        /** Rows matching what's already stored — no write needed. */
        val unchanged: Int,
        /** Rows with a blank shortcut or no usable words. */
        val invalid: Int,
        /** Valid new shortcuts not added because [MAX_ENTRIES] was reached. */
        val skippedAtLimit: Int,
        val totalAfter: Int,
    )

    /**
     * Merge spreadsheet rows: add new shortcuts, update words/direction when the
     * shortcut already exists. Duplicate shortcuts within the file — last row wins.
     */
    suspend fun previewMergeImport(incoming: List<SearchMatching>): MergeImportResult {
        loadFromDisk()
        return planMergeImport(_matchings.value, incoming).result
    }

    suspend fun mergeImport(incoming: List<SearchMatching>): MergeImportResult {
        loadFromDisk()
        val plan = planMergeImport(_matchings.value, incoming)
        if (plan.dirty) {
            writeFile(file, plan.next)
            _matchings.value = plan.next
        }
        return plan.result
    }

    private data class MergePlan(
        val result: MergeImportResult,
        val dirty: Boolean,
        val next: List<SearchMatching>,
    )

    private fun planMergeImport(
        existing: List<SearchMatching>,
        incoming: List<SearchMatching>,
        now: Long = System.currentTimeMillis(),
    ): MergePlan {
        var invalid = 0

        val byKey = LinkedHashMap<String, SearchMatching>()
        for (m in incoming) {
            val shortcut = m.shortcut.trim()
            if (shortcut.isEmpty()) {
                invalid++
                continue
            }
            val words = cleanWords(m.words)
            if (words.isEmpty()) {
                invalid++
                continue
            }
            byKey[shortcut.lowercase()] = m.copy(shortcut = shortcut, words = words)
        }

        val current = existing.toMutableList()
        var added = 0
        var updated = 0
        var unchanged = 0
        var skippedAtLimit = 0
        var dirty = false

        for (m in byKey.values) {
            val idx = current.indexOfFirst { it.shortcut.equals(m.shortcut, ignoreCase = true) }
            if (idx >= 0) {
                val prev = current[idx]
                val next = prev.copy(
                    words = m.words,
                    direction = m.direction,
                    updatedAt = now,
                )
                if (next.words == prev.words && next.direction == prev.direction) {
                    unchanged++
                } else {
                    current[idx] = next
                    updated++
                    dirty = true
                }
            } else {
                if (current.size >= MAX_ENTRIES) {
                    skippedAtLimit++
                    continue
                }
                current += m.copy(
                    createdAt = if (m.createdAt > 0) m.createdAt else now,
                    updatedAt = now,
                )
                added++
                dirty = true
            }
        }

        return MergePlan(
            result = MergeImportResult(
                added = added,
                updated = updated,
                unchanged = unchanged,
                invalid = invalid,
                skippedAtLimit = skippedAtLimit,
                totalAfter = current.size,
            ),
            dirty = dirty,
            next = current,
        )
    }

    enum class SaveResult { Ok, BlankShortcut, NoWords, LimitReached }

    /**
     * Inserts a new matching or replaces the existing one with the same id.
     * The shortcut is trimmed; words are trimmed, blank-stripped and de-duped
     * (case-insensitively) while keeping their order. Returns a validation
     * outcome so the UI can surface a precise message.
     */
    suspend fun upsert(matching: SearchMatching): SaveResult {
        loadFromDisk()
        val shortcut = matching.shortcut.trim()
        if (shortcut.isEmpty()) return SaveResult.BlankShortcut

        val words = cleanWords(matching.words)
        if (words.isEmpty()) return SaveResult.NoWords

        val current = _matchings.value
        val existingIndex = current.indexOfFirst { it.id == matching.id }
        if (existingIndex < 0 && current.size >= MAX_ENTRIES) return SaveResult.LimitReached

        val now = System.currentTimeMillis()
        val next: List<SearchMatching>
        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            val updated = existing.copy(
                shortcut = shortcut,
                words = words,
                direction = matching.direction,
                updatedAt = now,
            )
            next = current.toMutableList().also { it[existingIndex] = updated }
        } else {
            val created = matching.copy(
                shortcut = shortcut,
                words = words,
                createdAt = if (matching.createdAt > 0) matching.createdAt else now,
                updatedAt = now,
            )
            next = current + created
        }
        writeFile(file, next)
        _matchings.value = next
        return SaveResult.Ok
    }

    suspend fun delete(id: String) {
        loadFromDisk()
        val next = _matchings.value.filterNot { it.id == id }
        if (next.size == _matchings.value.size) return
        writeFile(file, next)
        _matchings.value = next
    }

    private fun cleanWords(words: List<String>): List<String> {
        val out = ArrayList<String>(words.size)
        for (raw in words) {
            val w = raw.trim()
            if (w.isEmpty()) continue
            if (out.any { it.equals(w, ignoreCase = true) }) continue
            out += w
            if (out.size >= MAX_WORDS) break
        }
        return out
    }

    private fun readFile(file: File): List<SearchMatching> {
        val text = file.readText(Charsets.UTF_8)
        if (text.isBlank()) return emptyList()
        val root = JSONObject(text)
        if (root.optInt("version", 0) < MATCHINGS_FORMAT_VERSION) return emptyList()
        val arr = root.optJSONArray("matchings") ?: return emptyList()
        val out = ArrayList<SearchMatching>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val shortcut = o.optString("shortcut").trim()
            val words = cleanWords(o.optJSONArray("words").toStringList())
            if (shortcut.isEmpty() || words.isEmpty()) continue
            out += SearchMatching(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                shortcut = shortcut,
                words = words,
                direction = parseDirection(o.optString("direction")),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            )
        }
        return out.take(MAX_ENTRIES)
    }

    private fun writeFile(target: File, matchings: List<SearchMatching>) {
        val arr = JSONArray()
        for (m in matchings) {
            val o = JSONObject()
            o.put("id", m.id)
            o.put("shortcut", m.shortcut)
            o.put("words", JSONArray(m.words))
            o.put("direction", m.direction.name)
            o.put("createdAt", m.createdAt)
            o.put("updatedAt", m.updatedAt)
            arr.put(o)
        }
        val root = JSONObject()
            .put("version", MATCHINGS_FORMAT_VERSION)
            .put("matchings", arr)
        atomicWriteText(target, root.toString())
    }

    private fun parseDirection(raw: String): MatchingDirection =
        MatchingDirection.entries.firstOrNull { it.name == raw } ?: MatchingDirection.Bidirectional

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val out = ArrayList<String>(length())
        for (i in 0 until length()) out += optString(i)
        return out
    }
}
