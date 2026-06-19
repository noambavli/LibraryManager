package com.mh.librarymanager.ui.management

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.domain.BookOrderIssues
import com.mh.librarymanager.domain.ManagementBadgeSection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManagementDashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val container = LibraryApp.from(app)
    private val seenStore get() = container.managementSeenStore

    val badgeCounts: StateFlow<Map<ManagementBadgeSection, Int>> = combine(
        container.requestStore.requests,
        container.techSupportStore.requests,
        container.catalogStore.books,
        seenStore.state,
    ) { requests, techSupport, books, _ ->
        val outOfOrderCount = BookOrderIssues
            .findOutOfOrder(books.filter { it.isLatest })
            .size
        seenStore.badgeCounts(requests, techSupport, outOfOrderCount)
    }
        .onStart {
            viewModelScope.launch {
                container.requestStore.loadFromDisk()
                container.techSupportStore.loadFromDisk()
                container.catalogStore.loadFromDisk()
                seenStore.loadFromDisk()
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyMap(),
        )

    fun markRequestsSeen() {
        viewModelScope.launch { seenStore.markRequestsSeen() }
    }

    fun markTechSupportSeen() {
        viewModelScope.launch { seenStore.markTechSupportSeen() }
    }

    fun markOutOfOrderSeen(currentCount: Int) {
        viewModelScope.launch { seenStore.markOutOfOrderSeen(currentCount) }
    }
}
