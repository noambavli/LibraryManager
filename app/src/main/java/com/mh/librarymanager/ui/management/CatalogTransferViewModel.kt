package com.mh.librarymanager.ui.management

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.data.civ.CivCatalogIO
import com.mh.librarymanager.data.civ.CivExportMeta
import com.mh.librarymanager.data.store.CatalogStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CatalogTransferViewModel(app: Application) : AndroidViewModel(app) {

    private val container = LibraryApp.from(app)
    private val io = container.civCatalogIo

    val formatVersion: Int = CatalogStore.CATALOG_FORMAT_VERSION

    sealed interface Status {
        data object Idle : Status
        data object Working : Status
        data class Imported(
            val added: Int,
            val skipped: Int,
            val totalAfter: Int,
            val fileLabel: String,
        ) : Status
        data class Restored(val count: Int) : Status
        data class Wiped(val count: Int) : Status
        data class Error(val message: String) : Status
    }

    data class DashboardState(
        val bookCount: Int,
        val lastImport: CivCatalogIO.LastImportSummary,
        val hasBackup: Boolean,
        val hasWipeBackup: Boolean,
        val hasSummary: Boolean,
    )

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _hasBackup = MutableStateFlow(io.hasBackup())
    val hasBackup: StateFlow<Boolean> = _hasBackup.asStateFlow()

    private val _hasWipeBackup = MutableStateFlow(io.hasWipeBackup())
    val hasWipeBackup: StateFlow<Boolean> = _hasWipeBackup.asStateFlow()

    private val _importSummary = MutableStateFlow(io.importSummary())
    val importSummary: StateFlow<CivCatalogIO.ImportSummaryDetail> = _importSummary.asStateFlow()

    private val _preview = MutableStateFlow<CivCatalogIO.ImportPreview?>(null)
    val preview: StateFlow<CivCatalogIO.ImportPreview?> = _preview.asStateFlow()

    /** PC-pushed catalog waiting for on-tablet confirmation (shown as a global overlay). */
    private val _adbPending = MutableStateFlow<CivCatalogIO.ImportPreview?>(null)
    val adbPending: StateFlow<CivCatalogIO.ImportPreview?> = _adbPending.asStateFlow()

    private val _adbConfirming = MutableStateFlow(false)
    val adbConfirming: StateFlow<Boolean> = _adbConfirming.asStateFlow()

    private val _openSummary = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openSummary = _openSummary.asSharedFlow()

    val dashboard: StateFlow<DashboardState> = container.repository
        .observeAll()
        .combine(_hasBackup) { books, backup -> books.size to backup }
        .combine(_hasWipeBackup) { (count, backup), wipeBackup -> Triple(count, backup, wipeBackup) }
        .combine(_importSummary) { (count, backup, wipeBackup), summary ->
            DashboardState(
                bookCount = count,
                lastImport = io.lastImportSummary(),
                hasBackup = backup,
                hasWipeBackup = wipeBackup,
                hasSummary = summary.hasData,
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            DashboardState(
                0,
                io.lastImportSummary(),
                io.hasBackup(),
                io.hasWipeBackup(),
                io.importSummary().hasData,
            ),
        )

    fun loadPreview(uri: Uri) {
        _status.value = Status.Working
        _preview.value = null
        viewModelScope.launch {
            when (val outcome = io.previewFromUri(uri)) {
                is CivCatalogIO.PreviewOutcome.Ready -> {
                    _preview.value = outcome.preview
                    _status.value = Status.Idle
                }
                is CivCatalogIO.PreviewOutcome.Failed ->
                    _status.value = mapError(outcome.result)
            }
        }
    }

    fun clearPreview() {
        _preview.value = null
    }

    /** Called when adb stages a file, or on startup to resume an unstaged confirmation. */
    fun refreshAdbPending() {
        viewModelScope.launch {
            when (val outcome = io.loadPendingPreview()) {
                is CivCatalogIO.PreviewOutcome.Ready -> _adbPending.value = outcome.preview
                is CivCatalogIO.PreviewOutcome.Failed -> _adbPending.value = null
            }
        }
    }

    fun onAdbImportStaged() = refreshAdbPending()

    fun confirmAdbPending() {
        if (_adbConfirming.value) return
        _adbConfirming.value = true
        _adbPending.value = null
        _status.value = Status.Working
        viewModelScope.launch {
            try {
                val result = runCatching { io.commitPendingImport() }
                    .getOrElse { CivCatalogIO.ImportResult.IoFailure(it.message ?: "Unknown error") }
                when (result) {
                    // Duplicate tap after a successful commit — don't overwrite OK.
                    is CivCatalogIO.ImportResult.Invalid -> Unit
                    else -> {
                        io.writeImportResult(result)
                        applyResult(result)
                    }
                }
            } finally {
                _adbConfirming.value = false
            }
        }
    }

    fun cancelAdbPending() {
        if (_adbConfirming.value) return
        _adbPending.value = null
        viewModelScope.launch {
            io.discardPendingImport()
            _status.value = Status.Error("הייבוא בוטל — לא נוספו ספרים.")
        }
    }

    fun importFromUri(uri: Uri) {
        _status.value = Status.Working
        viewModelScope.launch {
            val result = runCatching { io.importFromUri(uri) }
                .getOrElse { CivCatalogIO.ImportResult.IoFailure(it.message ?: "Unknown error") }
            applyResult(result)
        }
    }

    fun deleteAllBooks() {
        _status.value = Status.Working
        viewModelScope.launch {
            val removed = runCatching { io.wipeAllBooks() }.getOrDefault(-1)
            _status.value = when {
                removed < 0 -> Status.Error("לא ניתן למחוק — שגיאת אחסון.")
                removed == 0 -> Status.Error("הקטלוג כבר ריק.")
                else -> Status.Wiped(removed)
            }
            refreshMeta()
        }
    }

    fun restoreAfterWipe() {
        _status.value = Status.Working
        viewModelScope.launch {
            val restored = runCatching { io.restoreAfterWipe() }.getOrDefault(-1)
            _status.value = if (restored < 0) {
                Status.Error("לא ניתן לשחזר — אין גיבוי תקין.")
            } else {
                Status.Restored(restored)
            }
            refreshMeta()
        }
    }

    fun undoLastImport() {
        _status.value = Status.Working
        viewModelScope.launch {
            val restored = runCatching { io.restoreLastImport() }.getOrDefault(-1)
            _status.value = if (restored < 0) {
                Status.Error("לא ניתן לשחזר — אין גיבוי תקין.")
            } else {
                Status.Restored(restored)
            }
            refreshMeta()
        }
    }

    fun dismissStatus() {
        if (_status.value !is Status.Working) _status.value = Status.Idle
    }

    fun refreshSummary() {
        _importSummary.value = io.importSummary()
    }

    private fun applyResult(result: CivCatalogIO.ImportResult) {
        _status.value = when (result) {
            is CivCatalogIO.ImportResult.Ok -> {
                refreshMeta()
                if (result.addedCount > 0) {
                    _openSummary.tryEmit(Unit)
                }
                val label = result.meta?.fileLabel() ?: _importSummary.value.fileLabel
                Status.Imported(
                    added = result.addedCount,
                    skipped = result.skippedCount,
                    totalAfter = result.totalAfter,
                    fileLabel = label,
                )
            }
            is CivCatalogIO.ImportResult.WrongVersion ->
                Status.Error(
                    "הקובץ בגרסה ישנה (${result.found}). דרושה גרסה ${result.expected}.",
                )
            is CivCatalogIO.ImportResult.NewerVersion ->
                Status.Error(
                    "הקובץ בגרסה חדשה (${result.found}). עדכנו את האפליקציה.",
                )
            is CivCatalogIO.ImportResult.Empty -> Status.Error("הקובץ ריק.")
            is CivCatalogIO.ImportResult.Invalid ->
                Status.Error("הקובץ אינו תקין: ${result.reason}")
            is CivCatalogIO.ImportResult.TooLarge ->
                Status.Error("הקובץ גדול מדי (${result.sizeBytes / (1024 * 1024)} MB).")
            is CivCatalogIO.ImportResult.IoFailure ->
                Status.Error("בעיית אחסון: ${result.reason}")
            is CivCatalogIO.ImportResult.AwaitingConfirmation -> Status.Idle
        }
        if (result !is CivCatalogIO.ImportResult.Ok) refreshMeta()
    }

    private fun mapError(result: CivCatalogIO.ImportResult): Status.Error = when (result) {
        is CivCatalogIO.ImportResult.WrongVersion ->
            Status.Error("גרסת קובץ ישנה (${result.found}). דרושה v${result.expected}.")
        is CivCatalogIO.ImportResult.NewerVersion ->
            Status.Error("גרסת קובץ חדשה (${result.found}). עדכנו את האפליקציה.")
        is CivCatalogIO.ImportResult.Empty -> Status.Error("הקובץ ריק.")
        is CivCatalogIO.ImportResult.Invalid -> Status.Error(result.reason)
        is CivCatalogIO.ImportResult.TooLarge ->
            Status.Error("הקובץ גדול מדי (${result.sizeBytes / (1024 * 1024)} MB).")
        is CivCatalogIO.ImportResult.IoFailure -> Status.Error(result.reason)
        is CivCatalogIO.ImportResult.Ok -> Status.Error("שגיאה לא צפויה.")
        is CivCatalogIO.ImportResult.AwaitingConfirmation -> Status.Error("ממתין לאישור.")
    }

    private fun refreshMeta() {
        _hasBackup.value = io.hasBackup()
        _hasWipeBackup.value = io.hasWipeBackup()
        _importSummary.value = io.importSummary()
        _preview.value = null
    }
}
