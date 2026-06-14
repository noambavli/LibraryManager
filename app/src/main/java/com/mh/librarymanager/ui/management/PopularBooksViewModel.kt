package com.mh.librarymanager.ui.management

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.BookLocationPressEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class PopularBookRating(
    val rank: Int,
    val bookId: String,
    val book: Book?,
    val pressCount: Int,
)

class PopularBooksViewModel(app: Application) : AndroidViewModel(app) {

    private val container = LibraryApp.from(app)

    val ratings: StateFlow<List<PopularBookRating>> =
        combine(
            container.bookLocationPressStore.entries
                .onStart { container.bookLocationPressStore.loadFromDisk() },
            container.repository.observeAll()
                .map { books -> books.associateBy { it.id } },
        ) { presses, booksById ->
            rankPopularBooks(presses, booksById)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dataLoaded: StateFlow<Boolean> = combine(
        container.repository.observeCatalogLoaded(),
        container.bookLocationPressStore.loaded
            .onStart { container.bookLocationPressStore.loadFromDisk() },
    ) { catalogLoaded, pressesLoaded -> catalogLoaded && pressesLoaded }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val customColors = container.repository.observeColors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Rank books by map-button presses (6-month window is enforced by the store). */
internal fun rankPopularBooks(
    presses: List<BookLocationPressEntry>,
    booksById: Map<String, Book>,
    limit: Int = 100,
): List<PopularBookRating> {
    if (presses.isEmpty() || limit <= 0) return emptyList()
    val counts = LinkedHashMap<String, Int>()
    for (press in presses) {
        if (press.bookId.isBlank()) continue
        counts[press.bookId] = (counts[press.bookId] ?: 0) + 1
    }
    return counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .mapIndexed { index, (bookId, count) ->
            PopularBookRating(
                rank = index + 1,
                bookId = bookId,
                book = booksById[bookId],
                pressCount = count,
            )
        }
}
