package com.mh.librarymanager.ui.management

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.domain.PublicRequest
import com.mh.librarymanager.domain.RequestStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * View-model for the management "public requests" screen.
 *
 * Exposes the full request list filtered by an optional [RequestStatus], plus
 * per-status counts for the filter chips, and the mutating actions (change
 * status / delete).
 */
class RequestsManagementViewModel(app: Application) : AndroidViewModel(app) {

    private val container = LibraryApp.from(app)

    private val all: StateFlow<List<PublicRequest>> =
        container.requestStore.requests
            .onStart { container.requestStore.loadFromDisk() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _filter = MutableStateFlow<RequestStatus?>(null)
    val filter: StateFlow<RequestStatus?> = _filter.asStateFlow()

    val requests: StateFlow<List<PublicRequest>> =
        combine(all, _filter) { list, active ->
            list.filter { active == null || it.status == active }
                .sortedByDescending { it.createdAt }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Count per status, plus the total under the `null` key (used by "all"). */
    val counts: StateFlow<Map<RequestStatus?, Int>> =
        all.map { list ->
            buildMap<RequestStatus?, Int> {
                put(null, list.size)
                RequestStatus.entries.forEach { status ->
                    put(status, list.count { it.status == status })
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun setFilter(status: RequestStatus?) {
        _filter.value = status
    }

    fun updateStatus(id: String, status: RequestStatus) {
        viewModelScope.launch(Dispatchers.IO) {
            container.requestStore.updateStatus(id, status)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.requestStore.remove(id)
        }
    }
}
