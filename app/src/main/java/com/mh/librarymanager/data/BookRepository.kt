package com.mh.librarymanager.data

import com.mh.librarymanager.data.store.CatalogStore
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.CustomColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Single point of access to the persisted catalog. The store is JSON today;
 * if we later swap to Room or DataStore, callers stay unchanged.
 */
class BookRepository(private val store: CatalogStore) {

    fun observeAll(): Flow<List<Book>> = store.books
        .map { books -> books.filter { it.isLatest } }
        .onStart { store.loadFromDisk() }

    /** Includes non-latest revisions; only the management layer needs this. */
    fun observeAllIncludingHistory(): Flow<List<Book>> = store.books
        .onStart { store.loadFromDisk() }

    fun observeColors(): Flow<List<CustomColor>> = store.colors
        .onStart { store.loadFromDisk() }

    suspend fun count(): Int = store.count()

    suspend fun replaceAll(books: List<Book>) {
        store.replaceAll(books)
    }

    suspend fun upsert(book: Book) {
        store.upsert(book)
    }

    suspend fun delete(id: String) {
        store.delete(id)
    }

    suspend fun upsertColor(color: CustomColor) {
        store.upsertColor(color)
    }
}
