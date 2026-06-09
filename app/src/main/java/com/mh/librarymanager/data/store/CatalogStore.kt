package com.mh.librarymanager.data.store

import android.content.Context
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.BookPlace
import com.mh.librarymanager.domain.BookState
import com.mh.librarymanager.domain.CustomColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local catalog persistence.
 *
 * Books are written as a single JSON document under `filesDir/catalog.json`,
 * which is atomic (write-tmp + rename) and avoids the toolchain pain of Room
 * + KSP on current AGP previews. The public shape mirrors what a future Room
 * implementation would expose so the rest of the app does not change when we
 * swap stores.
 *
 * The whole catalog is held in a [StateFlow] so search can rebuild its index
 * cheaply when the file is replaced.
 *
 * In addition to books, a global custom-color palette is stored in
 * `filesDir/colors.json`. Books reference colors only by name; the palette
 * supplies the visual style so users can introduce new colors via the editor
 * without touching code.
 */
class CatalogStore(private val context: Context) {

    companion object {
        /** Bump when persisted book fields change so the bundled xlsx is re-imported. */
        const val CATALOG_FORMAT_VERSION = 4
        const val PALETTE_FORMAT_VERSION = 1
    }

    private val file: File by lazy { File(context.filesDir, "catalog.json") }
    private val paletteFile: File by lazy { File(context.filesDir, "colors.json") }

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _colors = MutableStateFlow<List<CustomColor>>(emptyList())
    val colors: StateFlow<List<CustomColor>> = _colors.asStateFlow()

    @Volatile private var booksLoaded = false
    @Volatile private var paletteLoaded = false

    suspend fun loadFromDisk() {
        if (!booksLoaded) {
            synchronized(this) {
                if (!booksLoaded) {
                    _books.value = if (file.exists()) readBooks(file) else emptyList()
                    booksLoaded = true
                }
            }
        }
        if (!paletteLoaded) {
            synchronized(this) {
                if (!paletteLoaded) {
                    _colors.value = if (paletteFile.exists()) readPalette(paletteFile) else emptyList()
                    paletteLoaded = true
                }
            }
        }
    }

    suspend fun count(): Int {
        loadFromDisk()
        return _books.value.size
    }

    suspend fun replaceAll(books: List<Book>) {
        writeBooks(file, books)
        _books.value = books
        booksLoaded = true
    }

    suspend fun upsert(book: Book) {
        loadFromDisk()
        val next = _books.value.toMutableList()
        val idx = next.indexOfFirst { it.id == book.id }
        if (idx >= 0) next[idx] = book else next += book
        writeBooks(file, next)
        _books.value = next
    }

    suspend fun delete(id: String) {
        loadFromDisk()
        val next = _books.value.filterNot { it.id == id }
        writeBooks(file, next)
        _books.value = next
    }

    suspend fun replacePalette(colors: List<CustomColor>) {
        writePalette(paletteFile, colors)
        _colors.value = colors
        paletteLoaded = true
    }

    suspend fun upsertColor(color: CustomColor) {
        loadFromDisk()
        val next = _colors.value.toMutableList()
        val idx = next.indexOfFirst { it.name.equals(color.name, ignoreCase = true) }
        if (idx >= 0) next[idx] = color else next += color
        writePalette(paletteFile, next)
        _colors.value = next
    }

    private fun readBooks(file: File): List<Book> {
        val text = file.readText(Charsets.UTF_8)
        if (text.isBlank()) return emptyList()
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            return emptyList()
        }
        val version = root.optInt("version", 0)
        if (version != CATALOG_FORMAT_VERSION) return emptyList()
        val arr = root.optJSONArray("books") ?: return emptyList()
        val result = ArrayList<Book>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val book = o.toBook() ?: continue
            result += book
        }
        return result
    }

    private fun JSONObject.toBook(): Book? {
        val id = safeString("id").takeIf { it.isNotBlank() } ?: return null
        val parent = safeString("parentBookId").takeIf { it.isNotBlank() }
        return Book(
            id = id,
            logicalBookId = safeString("logicalBookId").ifBlank { id },
            version = optInt("version", 1),
            isLatest = optBoolean("isLatest", true),
            name = safeString("name"),
            topics = safeString("topics"),
            writer = safeString("writer"),
            bookNumber = safeString("bookNumber"),
            displayNumber = safeString("displayNumber"),
            letter = safeString("letter"),
            color = safeString("color"),
            category = safeString("category"),
            subcategories = optJSONArray("subcategories").toStringList(),
            notes = safeString("notes"),
            place = BookPlace.fromStored(safeString("place")),
            state = BookState.fromStored(safeString("state")),
            parentBookId = parent,
            relations = optJSONArray("relations").toStringList(),
            createdAt = optLong("createdAt"),
            updatedAt = optLong("updatedAt"),
        )
    }

    private fun JSONObject.safeString(key: String): String =
        if (isNull(key)) "" else optString(key, "")

    private fun writeBooks(target: File, books: List<Book>) {
        val arr = JSONArray()
        for (b in books) {
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
            arr.put(o)
        }
        val root = JSONObject().put("version", CATALOG_FORMAT_VERSION).put("books", arr)
        atomicWriteText(target, root.toString())
    }

    private fun readPalette(file: File): List<CustomColor> {
        val text = file.readText(Charsets.UTF_8)
        if (text.isBlank()) return emptyList()
        val root = JSONObject(text)
        if (root.optInt("version", 0) < PALETTE_FORMAT_VERSION) return emptyList()
        val arr = root.optJSONArray("colors") ?: return emptyList()
        val out = ArrayList<CustomColor>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            out += CustomColor(
                name = name,
                argb = o.optLong("argb"),
            )
        }
        return out
    }

    private fun writePalette(target: File, colors: List<CustomColor>) {
        val arr = JSONArray()
        for (c in colors) {
            val o = JSONObject()
            o.put("name", c.name)
            o.put("argb", c.argb)
            arr.put(o)
        }
        val root = JSONObject().put("version", PALETTE_FORMAT_VERSION).put("colors", arr)
        atomicWriteText(target, root.toString())
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val out = ArrayList<String>(length())
        for (i in 0 until length()) {
            if (!isNull(i)) out += optString(i, "")
        }
        return out
    }
}
