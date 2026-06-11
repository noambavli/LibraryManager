package com.mh.librarymanager.ui.management

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.BookOrderIssue
import com.mh.librarymanager.domain.BookOrderIssues
import com.mh.librarymanager.domain.CustomColor
import com.mh.librarymanager.domain.OutOfOrderBook
import com.mh.librarymanager.domain.OutOfOrderFilter
import com.mh.librarymanager.search.SearchEngine
import com.mh.librarymanager.search.SearchQuery
import com.mh.librarymanager.ui.search.KeyAction
import com.mh.librarymanager.ui.search.SearchField
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * View-model for the management books screen.
 *
 * Mirrors [com.mh.librarymanager.ui.search.SearchViewModel] for the search /
 * keyboard plumbing (deliberately duplicated rather than abstracted — the two
 * screens have meaningfully different result-lifecycle semantics and tying
 * them together makes both harder to change).
 *
 * On top of search it owns CRUD: [save], [delete] and the custom
 * color palette upsert. Everything is persisted through [BookRepository] so a
 * background screen change immediately sees fresh data.
 */
@OptIn(FlowPreview::class)
class BooksManagementViewModel(app: Application) : AndroidViewModel(app) {

    private val container = LibraryApp.from(app)

    private val _fieldValues: MutableStateFlow<Map<SearchField, TextFieldValue>> =
        MutableStateFlow(SearchField.entries.associateWith { TextFieldValue("") })
    val fieldValues: StateFlow<Map<SearchField, TextFieldValue>> = _fieldValues.asStateFlow()

    private val _focusedField = MutableStateFlow(SearchField.GENERAL)
    val focusedField: StateFlow<SearchField> = _focusedField.asStateFlow()

    val customColors: StateFlow<List<CustomColor>> = container.repository.observeColors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val catalog: StateFlow<List<Book>> = container.repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val engine: StateFlow<SearchEngine> = catalog
        .map { books -> SearchEngine(books) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchEngine(emptyList()))

    val parentNameLookup: StateFlow<Map<String, String>> = catalog
        .map { books -> books.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val booksById: StateFlow<Map<String, Book>> = catalog
        .map { books -> books.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val catalogSize: StateFlow<Int> = engine.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _outOfOrderFilter = MutableStateFlow(OutOfOrderFilter.ALL)
    val outOfOrderFilter: StateFlow<OutOfOrderFilter> = _outOfOrderFilter.asStateFlow()

    private val _outOfOrderIssueFilter = MutableStateFlow<BookOrderIssue?>(null)
    val outOfOrderIssueFilter: StateFlow<BookOrderIssue?> = _outOfOrderIssueFilter.asStateFlow()

    val outOfOrderBooks: StateFlow<List<OutOfOrderBook>> = catalog
        .map { books -> BookOrderIssues.findOutOfOrder(books) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val outOfOrderCount: StateFlow<Int> = outOfOrderBooks
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val filteredOutOfOrderBooks: StateFlow<List<OutOfOrderBook>> = combine(
        outOfOrderBooks,
        _outOfOrderFilter,
        _outOfOrderIssueFilter,
    ) { entries, filter, issueFilter ->
        BookOrderIssues.filterEntries(entries, filter, issueFilter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val outOfOrderFilterCounts: StateFlow<Map<OutOfOrderFilter, Int>> = outOfOrderBooks
        .map { BookOrderIssues.countByFilter(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val outOfOrderIssueCounts: StateFlow<Map<BookOrderIssue, Int>> = outOfOrderBooks
        .map { BookOrderIssues.countByIssue(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setOutOfOrderFilter(filter: OutOfOrderFilter) {
        _outOfOrderFilter.value = filter
        _outOfOrderIssueFilter.value = null
    }

    fun setOutOfOrderIssueFilter(issue: BookOrderIssue?) {
        _outOfOrderIssueFilter.value = issue
    }

    val results: StateFlow<List<Book>> = combine(
        engine,
        _fieldValues.debounce(60),
    ) { eng, values -> eng.search(values.toQuery()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setValue(field: SearchField, value: TextFieldValue) {
        _fieldValues.value = _fieldValues.value.toMutableMap().also { it[field] = value }
    }

    fun setFocused(field: SearchField) {
        _focusedField.value = field
    }

    fun handleKey(key: KeyAction) {
        val field = _focusedField.value
        val current = _fieldValues.value[field] ?: TextFieldValue("")
        val next = when (key) {
            is KeyAction.Insert -> current.insertAt(key.text)
            KeyAction.Backspace -> current.deleteBack()
            KeyAction.ClearField -> TextFieldValue("")
            KeyAction.ClearAll -> { clearAll(); return }
        }
        setValue(field, next)
    }

    fun clearAll() {
        _fieldValues.value = SearchField.entries.associateWith { TextFieldValue("") }
    }

    fun findById(id: String): Book? = catalog.value.firstOrNull { it.id == id }

    fun save(book: Book) {
        viewModelScope.launch(Dispatchers.IO) {
            container.repository.upsert(book.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    /** Suspends until the book is on disk — use before navigating away. */
    suspend fun saveAwait(book: Book) {
        withContext(Dispatchers.IO) {
            container.repository.upsert(book.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) { container.repository.delete(id) }
    }

    fun upsertColor(color: CustomColor) {
        viewModelScope.launch(Dispatchers.IO) {
            container.repository.upsertColor(color)
        }
    }

    /** Computes the next system-visible book number (max+1, padded) for new books. */
    fun suggestNextBookNumber(): String {
        val current = catalog.value
            .mapNotNull { it.bookNumber.toIntOrNull() }
            .maxOrNull() ?: 0
        return (current + 1).toString().padStart(4, '0')
    }
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

private fun TextFieldValue.insertAt(insertion: String): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    val newText = buildString {
        append(text, 0, start)
        append(insertion)
        append(text, end, text.length)
    }
    val cursor = start + insertion.length
    return TextFieldValue(text = newText, selection = TextRange(cursor))
}

private fun TextFieldValue.deleteBack(): TextFieldValue {
    if (text.isEmpty()) return this
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    if (start != end) {
        val newText = buildString {
            append(text, 0, start)
            append(text, end, text.length)
        }
        return TextFieldValue(text = newText, selection = TextRange(start))
    }
    if (start == 0) return this
    val newText = buildString {
        append(text, 0, start - 1)
        append(text, start, text.length)
    }
    return TextFieldValue(text = newText, selection = TextRange(start - 1))
}
