package com.mh.librarymanager.ui.management

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.domain.Announcement
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.CustomColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Management view-model for announcements: list (newest first, with active
 * flag), create, and delete. Also exposes the catalog so the editor's book
 * picker can attach books.
 */
class AnnouncementsManagementViewModel(app: Application) : AndroidViewModel(app) {

    private val container = LibraryApp.from(app)

    val announcements: StateFlow<List<Announcement>> =
        container.announcementStore.announcements
            .onStart { container.announcementStore.loadFromDisk() }
            .map { list -> list.sortedByDescending { it.createdAt } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val announcementsLoaded: StateFlow<Boolean> =
        container.announcementStore.loaded
            .onStart { container.announcementStore.loadFromDisk() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val catalog: StateFlow<List<Book>> =
        container.repository.observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val catalogLoaded: StateFlow<Boolean> = container.repository.observeCatalogLoaded()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val booksById: StateFlow<Map<String, Book>> =
        catalog.map { books -> books.associateBy { it.id } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val customColors: StateFlow<List<CustomColor>> =
        container.repository.observeColors()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun addAwait(
        title: String,
        description: String,
        durationDays: Int,
        linkedBookIds: List<String>,
    ) {
        val now = System.currentTimeMillis()
        val announcement = Announcement(
            id = "ann-${UUID.randomUUID()}",
            title = title.trim(),
            description = description.trim(),
            createdAt = now,
            durationDays = durationDays.coerceIn(1, Announcement.MAX_DURATION_DAYS),
            linkedBookIds = linkedBookIds,
        )
        withContext(Dispatchers.IO) {
            container.announcementStore.add(announcement)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.announcementStore.remove(id)
        }
    }
}
