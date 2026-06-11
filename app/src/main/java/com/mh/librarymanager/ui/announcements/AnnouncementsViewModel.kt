package com.mh.librarymanager.ui.announcements

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.domain.Announcement
import com.mh.librarymanager.domain.CustomColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Public-facing view-model for announcements (home strip, full view, "see all").
 *
 * [active] is what the home page shows; [all] includes expired entries so a
 * detail view opened from the back-stack still resolves its ID.
 */
class AnnouncementsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = LibraryApp.from(app)

    val all: StateFlow<List<Announcement>> =
        container.announcementStore.announcements
            .onStart { container.announcementStore.loadFromDisk() }
            .map { list -> list.sortedByDescending { it.createdAt } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val active: StateFlow<List<Announcement>> =
        all.map { list ->
            val now = System.currentTimeMillis()
            list.filter { it.isActive(now) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun findById(id: String): Announcement? = all.value.firstOrNull { it.id == id }
}
