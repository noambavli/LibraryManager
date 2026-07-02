package com.mh.librarymanager

import android.app.Application
import android.content.Context
import com.mh.librarymanager.data.BookRepository
import com.mh.librarymanager.data.backup.BackupManager
import com.mh.librarymanager.data.excel.ExcelImportIO
import com.mh.librarymanager.data.excel.MatchingsImportIO
import com.mh.librarymanager.data.store.AnnouncementStore
import com.mh.librarymanager.data.store.AuditStore
import com.mh.librarymanager.data.store.CatalogStore
import com.mh.librarymanager.data.store.ManagementSeenStore
import com.mh.librarymanager.data.store.PublicRequestStore
import com.mh.librarymanager.data.store.BookLocationPressStore
import com.mh.librarymanager.data.store.SearchHistoryStore
import com.mh.librarymanager.data.store.SearchMatchingStore
import com.mh.librarymanager.data.store.SearchShortcutStore
import com.mh.librarymanager.data.homemap.HomeOverviewMapStore
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
    val matchingStore: SearchMatchingStore by lazy { SearchMatchingStore(this) }
    val searchHistoryStore: SearchHistoryStore by lazy { SearchHistoryStore(this) }
    val bookLocationPressStore: BookLocationPressStore by lazy { BookLocationPressStore(this) }
    val techSupportStore: TechSupportStore by lazy { TechSupportStore(this) }
    val managementSeenStore: ManagementSeenStore by lazy { ManagementSeenStore(this) }
    val repository: BookRepository by lazy { BookRepository(catalogStore, auditStore) }
    val excelImportIo: ExcelImportIO by lazy { ExcelImportIO(this, repository) }
    val beisImportIo: ExcelImportIO by lazy { ExcelImportIO.beis(this, repository) }
    val matchingsImportIo: MatchingsImportIO by lazy { MatchingsImportIO(this, matchingStore) }
    val backupManager: BackupManager by lazy { BackupManager(this, this) }
    val homeOverviewMapStore: HomeOverviewMapStore by lazy { HomeOverviewMapStore(this) }

    companion object {
        fun from(context: Context): LibraryApp =
            context.applicationContext as LibraryApp
    }
}
