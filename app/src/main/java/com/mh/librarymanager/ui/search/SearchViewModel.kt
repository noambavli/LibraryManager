package com.mh.librarymanager.ui.search

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.CustomColor
import com.mh.librarymanager.domain.SearchHistoryEntry
import com.mh.librarymanager.search.SearchEngine
import com.mh.librarymanager.search.SearchQuery
import com.mh.librarymanager.search.SearchSynonyms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Owns search UI state. The catalog flow is observed once, an in-memory
 * [SearchEngine] is rebuilt whenever the data changes, and results are
 * recomputed on a short debounce against the engine + current query.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        /** 30 days, matching the "What's new" home-screen recency window. */
        const val RECENT_WINDOW_MS: Long = 30L * 24L * 60L * 60L * 1000L
        const val RECENTLY_ADDED_LIMIT: Int = 3
    }

    private val container = LibraryApp.from(app)

    /** Prevents logging the same query twice until the visitor edits the fields. */
    private var lastCommittedFingerprint: String? = null

    private val _fieldValues: MutableStateFlow<Map<SearchField, TextFieldValue>> =
        MutableStateFlow(SearchField.entries.associateWith { TextFieldValue("") })
    val fieldValues: StateFlow<Map<SearchField, TextFieldValue>> = _fieldValues.asStateFlow()

    private val _focusedField = MutableStateFlow(SearchField.GENERAL)
    val focusedField: StateFlow<SearchField> = _focusedField.asStateFlow()

    private val catalog: StateFlow<List<Book>> = container.repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val catalogLoaded: StateFlow<Boolean> = container.repository.observeCatalogLoaded()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val engine: StateFlow<SearchEngine> = combine(
        catalog,
        container.matchingStore.matchings.onStart { container.matchingStore.loadFromDisk() },
    ) { books, rules ->
        SearchEngine(books, SearchSynonyms.from(rules))
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchEngine(emptyList()))

    val catalogSize: StateFlow<Int> = engine.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val customColors: StateFlow<List<CustomColor>> = container.repository.observeColors()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Quick search shortcuts shown as tags above the general field. */
    val shortcuts: StateFlow<List<String>> = container.shortcutStore.shortcuts
        .onStart { container.shortcutStore.loadFromDisk() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val parentNameLookup: StateFlow<Map<String, String>> = catalog
        .map { books -> books.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val booksById: StateFlow<Map<String, Book>> = catalog
        .map { books -> books.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * Up to [RECENTLY_ADDED_LIMIT] books added in the last [RECENT_WINDOW_MS]. Empty if nothing
     * was added in the window — the home screen treats that as "no news"
     * rather than showing stale entries.
     */
    val recentlyAdded: StateFlow<List<Book>> = catalog
        .map { books ->
            val cutoff = System.currentTimeMillis() - RECENT_WINDOW_MS
            books
                .filter { it.createdAt >= cutoff }
                .sortedByDescending { it.createdAt }
                .take(RECENTLY_ADDED_LIMIT)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val results: StateFlow<List<Book>> = combine(
        engine,
        _fieldValues.debounce(60),
    ) { eng, values ->
        // The public search screen stays on its idle hint until the visitor
        // actually types something — an empty query must not list the catalog.
        val query = values.toQuery()
        if (query.isEmpty) emptyList() else eng.search(query)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        ensureCatalogLoaded()
        ensureMatchingsLoaded()
    }

    fun setValue(field: SearchField, value: TextFieldValue) {
        _fieldValues.value = _fieldValues.value.toMutableMap().also { it[field] = value }
    }

    fun setFocused(field: SearchField) {
        _focusedField.value = field
    }

    /** Fills the general field with a shortcut word and focuses it. */
    fun applyShortcut(word: String) {
        setValue(SearchField.GENERAL, TextFieldValue(word, TextRange(word.length)))
        _focusedField.value = SearchField.GENERAL
    }

    /** Keyboard taps modify only the currently focused field. */
    fun handleKey(key: KeyAction) {
        val field = _focusedField.value
        val current = _fieldValues.value[field] ?: TextFieldValue("")
        val next = when (key) {
            is KeyAction.Insert -> current.insert(key.text)
            KeyAction.Backspace -> current.backspace()
            KeyAction.ClearField -> TextFieldValue("")
            KeyAction.ClearAll -> {
                clearAll()
                return
            }
        }
        setValue(field, next)
    }

    /** Records a public search once the visitor interacts outside the Hebrew keyboard. */
    fun commitSearchToHistory(resultCount: Int = results.value.size) {
        val query = _fieldValues.value.toQuery()
        if (query.isEmpty) return
        val fingerprint = query.fingerprint()
        if (fingerprint == lastCommittedFingerprint) return
        lastCommittedFingerprint = fingerprint
        viewModelScope.launch(Dispatchers.IO) {
            container.searchHistoryStore.append(
                SearchHistoryEntry(
                    id = UUID.randomUUID().toString(),
                    searchedAt = System.currentTimeMillis(),
                    query = query,
                    resultCount = resultCount,
                ),
            )
        }
    }

    /** Wipes every field so the next kiosk visitor starts with a blank search. */
    fun clearAll() {
        _fieldValues.value = SearchField.entries.associateWith { TextFieldValue("") }
        _focusedField.value = SearchField.GENERAL
        lastCommittedFingerprint = null
    }

    /** Called before [clearAll] when the public search screen closes. */
    fun finalizePublicSearchSession() {
        commitSearchToHistory()
        clearAll()
    }

    /** Loads the on-device catalog only — production data comes from PC .civ sync. */
    private fun ensureCatalogLoaded() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.repository.count() }
        }
    }

    private fun ensureMatchingsLoaded() {
        viewModelScope.launch(Dispatchers.IO) {
            container.matchingStore.loadFromDisk()
        }
    }
}

sealed interface KeyAction {
    data class Insert(val text: String) : KeyAction
    data object Backspace : KeyAction
    data object ClearField : KeyAction
    data object ClearAll : KeyAction
}

private fun Map<SearchField, TextFieldValue>.toQuery(): SearchQuery = SearchQuery(
    general = this[SearchField.GENERAL]?.text.orEmpty(),
    name = this[SearchField.NAME]?.text.orEmpty(),
    topics = this[SearchField.TOPICS]?.text.orEmpty(),
    writer = this[SearchField.WRITER]?.text.orEmpty(),
    letter = this[SearchField.LETTER]?.text.orEmpty(),
    color = this[SearchField.COLOR]?.text.orEmpty(),
    category = this[SearchField.CATEGORY]?.text.orEmpty(),
    subcategory = this[SearchField.SUBCATEGORY]?.text.orEmpty(),
    displayNumber = this[SearchField.DISPLAY_NUMBER]?.text.orEmpty(),
    bookNumber = this[SearchField.BOOK_NUMBER]?.text.orEmpty(),
    notes = this[SearchField.NOTES]?.text.orEmpty(),
)

private fun TextFieldValue.insert(insertion: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val safeStart = start.coerceIn(0, text.length)
    val safeEnd = end.coerceIn(0, text.length)
    val newText = buildString {
        append(text, 0, safeStart)
        append(insertion)
        append(text, safeEnd, text.length)
    }
    val cursor = safeStart + insertion.length
    return TextFieldValue(text = newText, selection = TextRange(cursor))
}

private fun TextFieldValue.backspace(): TextFieldValue {
    if (text.isEmpty()) return this
    val start = selection.min
    val end = selection.max
    val safeStart = start.coerceIn(0, text.length)
    val safeEnd = end.coerceIn(0, text.length)
    if (safeStart != safeEnd) {
        val newText = buildString {
            append(text, 0, safeStart)
            append(text, safeEnd, text.length)
        }
        return TextFieldValue(text = newText, selection = TextRange(safeStart))
    }
    if (safeStart == 0) return this
    val newText = buildString {
        append(text, 0, safeStart - 1)
        append(text, safeStart, text.length)
    }
    return TextFieldValue(text = newText, selection = TextRange(safeStart - 1))
}
