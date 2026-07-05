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
 * Management view-model for the app-texts dashboard. Exposes the current
 * overrides and lets staff replace or reset the visible text of any catalogued
 * string. Overrides are keyed by the string's stable resource entry name.
 */
class TextManagementViewModel(app: Application) : AndroidViewModel(app) {

    private val store = LibraryApp.from(app).textOverrideStore

    val overrides: StateFlow<Map<String, String>> =
        store.overrides
            .onStart { store.loadFromDisk() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val loaded: StateFlow<Boolean> =
        store.loaded
            .onStart { store.loadFromDisk() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Store a custom value for [key]; a blank value resets it to the default. */
    fun save(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) { store.set(key, value) }
    }

    fun reset(key: String) {
        viewModelScope.launch(Dispatchers.IO) { store.reset(key) }
    }

    fun resetAll() {
        viewModelScope.launch(Dispatchers.IO) { store.resetAll() }
    }
}
