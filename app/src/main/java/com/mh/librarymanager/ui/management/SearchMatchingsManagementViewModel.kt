package com.mh.librarymanager.ui.management

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.data.store.SearchMatchingStore
import com.mh.librarymanager.domain.SearchMatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Management view-model for search synonyms (shortcut ⇄ ordered words).
 * Add, edit, delete and reorder, persisted via [SearchMatchingStore].
 */
class SearchMatchingsManagementViewModel(app: Application) : AndroidViewModel(app) {

    private val container = LibraryApp.from(app)

    val matchings: StateFlow<List<SearchMatching>> =
        container.matchingStore.matchings
            .onStart { container.matchingStore.loadFromDisk() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val loaded: StateFlow<Boolean> =
        container.matchingStore.loaded
            .onStart { container.matchingStore.loadFromDisk() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    suspend fun save(matching: SearchMatching): SearchMatchingStore.SaveResult =
        withContext(Dispatchers.IO) { container.matchingStore.upsert(matching) }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.matchingStore.delete(id)
        }
    }
}
