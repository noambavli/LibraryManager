package com.mh.librarymanager.ui.management

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.domain.TechSupportRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TechSupportManagementViewModel(app: Application) : AndroidViewModel(app) {

    private val container = LibraryApp.from(app)

    val requests: StateFlow<List<TechSupportRequest>> =
        container.techSupportStore.requests
            .onStart { container.techSupportStore.loadFromDisk() }
            .map { list -> list.sortedByDescending { it.createdAt } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.techSupportStore.remove(id)
        }
    }
}
