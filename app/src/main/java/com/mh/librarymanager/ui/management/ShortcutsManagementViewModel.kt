package com.mh.librarymanager.ui.management

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.data.store.SearchShortcutStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Management view-model for the public-search shortcuts (tags). Add (up to
 * [SearchShortcutStore.MAX_SHORTCUTS]) and delete.
 */
class ShortcutsManagementViewModel(app: Application) : AndroidViewModel(app) {

    private val container = LibraryApp.from(app)

    val maxShortcuts: Int = SearchShortcutStore.MAX_SHORTCUTS

    val shortcuts: StateFlow<List<String>> =
        container.shortcutStore.shortcuts
            .onStart { container.shortcutStore.loadFromDisk() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val loaded: StateFlow<Boolean> =
        container.shortcutStore.loaded
            .onStart { container.shortcutStore.loadFromDisk() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    suspend fun add(word: String): SearchShortcutStore.AddResult =
        withContext(Dispatchers.IO) { container.shortcutStore.add(word) }

    fun delete(word: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.shortcutStore.remove(word)
        }
    }
}
