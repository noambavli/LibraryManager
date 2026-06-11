package com.mh.librarymanager

import android.app.Application
import android.content.Context
import com.mh.librarymanager.data.BookRepository
import com.mh.librarymanager.data.civ.CivCatalogIO
import com.mh.librarymanager.data.store.AnnouncementStore
import com.mh.librarymanager.data.store.AuditStore
import com.mh.librarymanager.data.store.CatalogStore
import com.mh.librarymanager.data.store.PublicRequestStore
import com.mh.librarymanager.data.store.BookLocationPressStore
import com.mh.librarymanager.data.store.SearchHistoryStore
import com.mh.librarymanager.data.store.SearchShortcutStore
import com.mh.librarymanager.data.store.TechSupportStore
/**
 * Tiny manual DI container. One layer above singletons, one layer below Hilt.
 * Sufficient for this milestone; the API is stable so swapping for Hilt later
 * touches only this file.
 */
class LibraryApp : Application() {

    val catalogStore: CatalogStore by lazy { CatalogStore(this) }
    val auditStore: AuditStore by lazy { AuditStore(this) }
    val requestStore: PublicRequestStore by lazy { PublicRequestStore(this) }
    val announcementStore: AnnouncementStore by lazy { AnnouncementStore(this) }
    val shortcutStore: SearchShortcutStore by lazy { SearchShortcutStore(this) }
    val searchHistoryStore: SearchHistoryStore by lazy { SearchHistoryStore(this) }
    val bookLocationPressStore: BookLocationPressStore by lazy { BookLocationPressStore(this) }
    val techSupportStore: TechSupportStore by lazy { TechSupportStore(this) }
    val repository: BookRepository by lazy { BookRepository(catalogStore, auditStore) }
    val civCatalogIo: CivCatalogIO by lazy { CivCatalogIO(this, repository) }

    companion object {
        fun from(context: Context): LibraryApp =
            context.applicationContext as LibraryApp
    }
}
