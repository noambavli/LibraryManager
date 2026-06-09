package com.mh.librarymanager.ui.management

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.data.civ.CivCatalogIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the "Import catalog from PC" screen.
 *
 * Holds:
 *  - the live book count (so the dashboard / screen can show "Catalog: N books"),
 *  - a transient status (importing / result / error) for the in-screen UI,
 *  - whether a one-tap undo of the last import is available.
 */
class CatalogTransferViewModel(app: Application) : AndroidViewModel(app) {

    private val container = LibraryApp.from(app)
    private val io = container.civCatalogIo

    sealed interface Status {
        data object Idle : Status
        data object Importing : Status
        data class Imported(val count: Int, val previousCount: Int) : Status
        data class Restored(val count: Int) : Status
        data class Error(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _hasBackup = MutableStateFlow(io.hasBackup())
    val hasBackup: StateFlow<Boolean> = _hasBackup.asStateFlow()

    val bookCount: StateFlow<Int> = container.repository
        .observeAll()
        .combine(_status) { books, _ -> books.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun importFromUri(uri: Uri) {
        _status.value = Status.Importing
        viewModelScope.launch {
            val result = runCatching { io.importFromUri(uri) }
                .getOrElse { CivCatalogIO.ImportResult.IoFailure(it.message ?: "Unknown error") }
            _status.value = when (result) {
                is CivCatalogIO.ImportResult.Ok ->
                    Status.Imported(result.importedCount, result.previousCount)
                is CivCatalogIO.ImportResult.WrongVersion ->
                    Status.Error(
                        "הקובץ בגרסה ישנה (${result.found}). דרושה גרסה ${result.expected}. " +
                            "ייצאו מחדש מהמחשב באמצעות LibraryTool העדכני.",
                    )
                is CivCatalogIO.ImportResult.NewerVersion ->
                    Status.Error(
                        "הקובץ בגרסה חדשה (${result.found}) שאינה נתמכת על ידי אפליקציה זו " +
                            "(גרסה ${result.expected}). יש לעדכן את האפליקציה בטאבלט.",
                    )
                is CivCatalogIO.ImportResult.Empty ->
                    Status.Error("הקובץ ריק.")
                is CivCatalogIO.ImportResult.Invalid ->
                    Status.Error("הקובץ אינו קובץ ‎.civ תקין: ${result.reason}")
                is CivCatalogIO.ImportResult.TooLarge ->
                    Status.Error(
                        "הקובץ גדול מהמותר (${result.sizeBytes / (1024 * 1024)} MB).",
                    )
                is CivCatalogIO.ImportResult.IoFailure ->
                    Status.Error("בעיית אחסון: ${result.reason}. הקטלוג לא שונה.")
            }
            _hasBackup.value = io.hasBackup()
        }
    }

    fun undoLastImport() {
        _status.value = Status.Importing
        viewModelScope.launch {
            val restored = runCatching { io.restoreLastImport() }.getOrDefault(-1)
            _status.value = if (restored < 0) {
                Status.Error("לא ניתן לשחזר — אין גיבוי תקין.")
            } else {
                Status.Restored(restored)
            }
            _hasBackup.value = io.hasBackup()
        }
    }

    fun dismissStatus() {
        if (_status.value !is Status.Importing) _status.value = Status.Idle
    }
}
