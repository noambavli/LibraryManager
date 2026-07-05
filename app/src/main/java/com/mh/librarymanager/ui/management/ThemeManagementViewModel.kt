package com.mh.librarymanager.ui.management

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Management view-model for the appearance/theme dashboard. Exposes the current
 * theme id and lets staff switch between the predefined palettes.
 */
class ThemeManagementViewModel(app: Application) : AndroidViewModel(app) {

    private val store = LibraryApp.from(app).themeStore

    val selectedId: StateFlow<String> =
        store.selectedId
            .onStart { store.loadFromDisk() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, com.mh.librarymanager.data.store.ThemeStore.DEFAULT_ID)

    fun select(id: String) {
        viewModelScope.launch(Dispatchers.IO) { store.setTheme(id) }
    }
}
