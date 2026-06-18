package com.mh.librarymanager.ui.management

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.R
import com.mh.librarymanager.data.backup.BackupManager
import com.mh.librarymanager.data.backup.BackupState
import com.mh.librarymanager.data.backup.BackupTrigger
import com.mh.librarymanager.data.export.DownloadsWriter
import com.mh.librarymanager.data.excel.ExcelImportIO
import com.mh.librarymanager.data.excel.MatchingsImportIO
import com.mh.librarymanager.data.store.SearchMatchingStore
import com.mh.librarymanager.data.xlsx.CatalogImporter
import com.mh.librarymanager.data.xlsx.WindowsToolCodec
import com.mh.librarymanager.data.xlsx.XlsxReader
import com.mh.librarymanager.data.xlsx.XlsxWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drives the "Windows Tool" management screen: dated full backups plus
 * xlsx import/export of books and matchings (shortcuts only inside full zip backup).
 *
 * Every import first writes a complete backup (so the pre-import state is
 * always recoverable) and only then applies the change. All work runs off the
 * main thread and every failure is mapped to a typed [OpStatus] — the UI never
 * sees an exception.
 */
class WindowsToolViewModel(app: Application) : AndroidViewModel(app) {

    private val container = LibraryApp.from(app)
    private val backup: BackupManager get() = container.backupManager

    /** Hard cap on a picked spreadsheet so a bad file can't OOM the kiosk. */
    private val maxImportBytes = 64L * 1024L * 1024L

    val backupState: StateFlow<BackupState> = backup.state

    private val _opStatus = MutableStateFlow<OpStatus>(OpStatus.Idle)
    val opStatus: StateFlow<OpStatus> = _opStatus.asStateFlow()

    private val _lastBackup = MutableStateFlow(BackupInfo(backup.lastBackupAt(), backup.lastBackupName()))
    val lastBackup: StateFlow<BackupInfo> = _lastBackup.asStateFlow()

    data class BackupInfo(val at: Long, val name: String)

    sealed interface OpStatus {
        data object Idle : OpStatus
        data class Working(val message: String) : OpStatus
        data class Success(val message: String) : OpStatus
        data class Error(val message: String) : OpStatus
    }

    fun dismissStatus() {
        if (_opStatus.value !is OpStatus.Working) _opStatus.value = OpStatus.Idle
    }

    fun dismissBackup() = backup.dismiss()

    fun cancelBackup() = backup.cancel()

    fun refreshLastBackup() {
        _lastBackup.value = BackupInfo(backup.lastBackupAt(), backup.lastBackupName())
    }

    fun createBackupNow() {
        backup.launchBackup(BackupTrigger.Manual)
    }

    // ---- Exports ----------------------------------------------------------

    fun exportBooks() = export(
        working = "מייצא ספרים…",
        baseName = "books",
        sheet = "books",
    ) {
        container.catalogStore.loadFromDisk()
        WindowsToolCodec.booksToRows(container.catalogStore.books.value.filter { it.isLatest })
    }

    fun exportMatchings() = export(
        working = "מייצא התאמות…",
        baseName = "matchings",
        sheet = "matchings",
    ) {
        container.matchingStore.loadFromDisk()
        WindowsToolCodec.matchingsToRows(container.matchingStore.matchings.value)
    }

    private fun export(
        working: String,
        baseName: String,
        sheet: String,
        buildRows: suspend () -> List<List<String>>,
    ) {
        if (isBusy()) {
            _opStatus.value = OpStatus.Error(
                getApplication<Application>().getString(R.string.windows_tool_busy),
            )
            return
        }
        _opStatus.value = OpStatus.Working(working)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val rows = buildRows()
                    val bytes = XlsxWriter.toBytes(rows, sheet)
                    val name = "${baseName}_${stamp()}.xlsx"
                    DownloadsWriter.write(
                        getApplication(),
                        name,
                        DownloadsWriter.MIME_XLSX,
                    ) { out -> out.write(bytes) } to (rows.size - 1).coerceAtLeast(0)
                }
            }
            _opStatus.value = result.fold(
                onSuccess = { (outcome, count) ->
                    when (outcome) {
                        is DownloadsWriter.Result.Ok ->
                            OpStatus.Success(exportSuccessMessage(count, outcome))
                        is DownloadsWriter.Result.Failed ->
                            OpStatus.Error(outcome.message)
                    }
                },
                onFailure = { OpStatus.Error(it.message ?: "שגיאה בייצוא") },
            )
        }
    }

    private fun exportSuccessMessage(count: Int, outcome: DownloadsWriter.Result.Ok): String {
        val app = getApplication<Application>()
        return when {
            count <= 0 ->
                app.getString(R.string.windows_tool_export_empty, outcome.displayName)
            outcome.location == "Download" ->
                app.getString(R.string.windows_tool_export_success, count, outcome.displayName)
            else ->
                app.getString(R.string.windows_tool_export_success_path, count, outcome.location)
        }
    }

    // ---- Imports (always backup first) ------------------------------------

    fun importBooks(uri: Uri) = importWithBackup(uri) {
        val stream = openBounded(uri) ?: return@importWithBackup OpStatus.Error("לא ניתן לפתוח את הקובץ.")
        val result = stream.use {
            CatalogImporter(getApplication(), container.repository).mergeFromStream(it)
        }
        OpStatus.Success(booksImportMessage(result))
    }

    private fun booksImportMessage(result: CatalogImporter.ImportResult): String {
        val dup = result.duplicates
        val blank = result.blankRows
        return when {
            result.added > 0 && dup == 0 && blank == 0 ->
                "נוספו ${result.added} ספרים חדשים. סה״כ ${result.totalAfter} בקטלוג."
            result.added > 0 ->
                buildString {
                    append("נוספו ${result.added} ספרים חדשים.")
                    if (dup > 0) append(" $dup שורות דולגו — ספר זהה כבר קיים בטאבלט.")
                    if (blank > 0) append(" $blank שורות ריקות דולגו.")
                    append(" סה״כ ${result.totalAfter} בקטלוג.")
                }
            dup > 0 ->
                "לא נוסף אף ספר חדש — כל $dup השורות בקובץ כבר קיימות בטאבלט " +
                    "(שם, מחבר, אות, מספר ושאר השדות תואמים). " +
                    "ייבוא מוסיף רק מה שעדיין לא שם — לא מחליף ולא מייבא מחדש את אותו ספר."
            blank > 0 ->
                "לא נוסף אף ספר — $blank שורות ריקות בקובץ (אין שם/מחבר/מספר)."
            else ->
                "הקובץ ריק — לא נוסף אף ספר."
        }
    }

    fun importMatchings(uri: Uri) = importWithBackup(uri) {
        val rows = readSheet(uri) ?: return@importWithBackup OpStatus.Error("לא ניתן לקרוא את הקובץ.")
        val parsed = WindowsToolCodec.rowsToMatchings(rows)
        if (parsed.isEmpty()) return@importWithBackup OpStatus.Error("לא נמצאו התאמות תקינות בקובץ.")
        val result = container.matchingStore.mergeImport(parsed)
        OpStatus.Success(matchingsImportMessage(result))
    }

    private fun matchingsImportMessage(result: SearchMatchingStore.MergeImportResult): String {
        val added = result.added
        val updated = result.updated
        val unchanged = result.unchanged
        val invalid = result.invalid
        val atLimit = result.skippedAtLimit

        if (added == 0 && updated == 0 && unchanged == 0 && invalid == 0 && atLimit == 0) {
            return "הקובץ ריק — לא נוספה אף התאמה."
        }

        return buildString {
            if (added > 0) append("נוספו $added התאמות חדשות.")
            if (updated > 0) {
                if (isNotEmpty()) append(' ')
                append("עודכנו $updated התאמות קיימות (מילים או כיוון).")
            }
            if (unchanged > 0) {
                if (isNotEmpty()) append(' ')
                append("$unchanged שורות ללא שינוי.")
            }
            if (invalid > 0) {
                if (isNotEmpty()) append(' ')
                append("$invalid שורות לא תקינות דולגו.")
            }
            if (atLimit > 0) {
                if (isNotEmpty()) append(' ')
                append(
                    "$atLimit שורות דולגו — הגעתם למגבלת " +
                        "${SearchMatchingStore.MAX_ENTRIES} התאמות.",
                )
            }
            when {
                added > 0 || updated > 0 -> append(" סה״כ ${result.totalAfter}.")
                unchanged > 0 && invalid == 0 && atLimit == 0 ->
                    append(" לא נדרש עדכון — הכל כבר מעודכן.")
                invalid > 0 && unchanged == 0 && atLimit == 0 ->
                    append(" לא נוספה אף התאמה.")
            }
        }
    }

    private fun importWithBackup(uri: Uri, doImport: suspend () -> OpStatus) {
        if (isBusy()) {
            _opStatus.value = OpStatus.Error(
                getApplication<Application>().getString(R.string.windows_tool_busy),
            )
            return
        }
        _opStatus.value = OpStatus.Working("יוצר גיבוי מלא לפני הייבוא…")
        viewModelScope.launch {
            val status = runCatching {
                withContext(Dispatchers.IO) {
                    ensurePreChangeBackup()?.let { return@withContext it }
                    _opStatus.value = OpStatus.Working("מייבא מהקובץ…")
                    doImport()
                }
            }.getOrElse { OpStatus.Error(it.message ?: "שגיאה בייבוא") }
            _opStatus.value = status
            refreshLastBackup()
        }
    }

    // ---- PC adb push (Excel import) ---------------------------------------

    private val excelIo get() = container.excelImportIo

    private val _adbPending = MutableStateFlow<ExcelImportIO.ImportPreview?>(null)
    val adbPending: StateFlow<ExcelImportIO.ImportPreview?> = _adbPending.asStateFlow()

    private val _adbConfirming = MutableStateFlow(false)
    val adbConfirming: StateFlow<Boolean> = _adbConfirming.asStateFlow()

    fun refreshAdbPending() {
        viewModelScope.launch {
            when (val outcome = excelIo.loadPendingPreview()) {
                is ExcelImportIO.PreviewOutcome.Ready -> _adbPending.value = outcome.preview
                is ExcelImportIO.PreviewOutcome.Failed -> _adbPending.value = null
            }
        }
    }

    fun onAdbImportStaged() = refreshAdbPending()

    fun confirmAdbPending() {
        if (_adbConfirming.value) return
        _adbConfirming.value = true
        _adbPending.value = null
        _opStatus.value = OpStatus.Working("יוצר גיבוי מלא לפני הייבוא…")
        viewModelScope.launch {
            try {
                val backupError = withContext(Dispatchers.IO) {
                    ensurePreChangeBackup()
                }
                if (backupError != null) {
                    excelIo.writeImportResult(
                        ExcelImportIO.ImportResult.IoFailure(backupError.message),
                    )
                    refreshAdbPending()
                    _opStatus.value = backupError
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    excelIo.commitPendingImport()
                }
                excelIo.writeImportResult(result)
                _opStatus.value = when (result) {
                    is ExcelImportIO.ImportResult.Ok ->
                        OpStatus.Success(booksImportMessage(
                            CatalogImporter.ImportResult(
                                added = result.addedCount,
                                duplicates = result.skippedCount,
                                blankRows = 0,
                                totalAfter = result.totalAfter,
                            ),
                        ))
                    is ExcelImportIO.ImportResult.Invalid ->
                        OpStatus.Error(result.reason)
                    is ExcelImportIO.ImportResult.IoFailure ->
                        OpStatus.Error(result.reason)
                    else -> OpStatus.Idle
                }
                refreshLastBackup()
            } finally {
                _adbConfirming.value = false
            }
        }
    }

    fun cancelAdbPending() {
        if (_adbConfirming.value) return
        _adbPending.value = null
        excelIo.discardPendingImport()
    }

    // ---- PC adb push (matchings import) -----------------------------------

    private val matchingsIo get() = container.matchingsImportIo

    private val _adbMatchingsPending = MutableStateFlow<MatchingsImportIO.ImportPreview?>(null)
    val adbMatchingsPending: StateFlow<MatchingsImportIO.ImportPreview?> =
        _adbMatchingsPending.asStateFlow()

    private val _adbMatchingsConfirming = MutableStateFlow(false)
    val adbMatchingsConfirming: StateFlow<Boolean> = _adbMatchingsConfirming.asStateFlow()

    fun refreshAdbMatchingsPending() {
        viewModelScope.launch {
            when (val outcome = matchingsIo.loadPendingPreview()) {
                is MatchingsImportIO.PreviewOutcome.Ready ->
                    _adbMatchingsPending.value = outcome.preview
                is MatchingsImportIO.PreviewOutcome.Failed ->
                    _adbMatchingsPending.value = null
            }
        }
    }

    fun onAdbMatchingsImportStaged() = refreshAdbMatchingsPending()

    fun confirmAdbMatchingsPending() {
        if (_adbMatchingsConfirming.value) return
        _adbMatchingsConfirming.value = true
        _adbMatchingsPending.value = null
        _opStatus.value = OpStatus.Working("יוצר גיבוי מלא לפני הייבוא…")
        viewModelScope.launch {
            try {
                val backupError = withContext(Dispatchers.IO) {
                    ensurePreChangeBackup()
                }
                if (backupError != null) {
                    matchingsIo.writeImportResult(
                        MatchingsImportIO.ImportResult.IoFailure(backupError.message),
                    )
                    refreshAdbMatchingsPending()
                    _opStatus.value = backupError
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    matchingsIo.commitPendingImport()
                }
                matchingsIo.writeImportResult(result)
                _opStatus.value = when (result) {
                    is MatchingsImportIO.ImportResult.Ok ->
                        OpStatus.Success(
                            matchingsImportMessage(
                                SearchMatchingStore.MergeImportResult(
                                    added = result.addedCount,
                                    updated = result.updatedCount,
                                    unchanged = result.unchangedCount,
                                    invalid = 0,
                                    skippedAtLimit = 0,
                                    totalAfter = result.totalAfter,
                                ),
                            ),
                        )
                    is MatchingsImportIO.ImportResult.Invalid ->
                        OpStatus.Error(result.reason)
                    is MatchingsImportIO.ImportResult.IoFailure ->
                        OpStatus.Error(result.reason)
                    else -> OpStatus.Idle
                }
                refreshLastBackup()
            } finally {
                _adbMatchingsConfirming.value = false
            }
        }
    }

    fun cancelAdbMatchingsPending() {
        if (_adbMatchingsConfirming.value) return
        _adbMatchingsPending.value = null
        matchingsIo.discardPendingImport()
    }

    // ---- Restore ----------------------------------------------------------

    fun restoreFromZip(uri: Uri) {
        if (isBusy()) {
            _opStatus.value = OpStatus.Error(
                getApplication<Application>().getString(R.string.windows_tool_busy),
            )
            return
        }
        _opStatus.value = OpStatus.Working("משחזר נתונים מהגיבוי…")
        viewModelScope.launch {
            val status = runCatching {
                when (val r = backup.restoreFromZip(uri)) {
                    is BackupManager.RestoreResult.Ok ->
                        OpStatus.Success(
                            buildString {
                                append("השחזור הושלם — ${r.books} ספרים")
                                if (r.createdAtText.isNotBlank()) append(" (גיבוי מ-${r.createdAtText})")
                                append(".")
                            },
                        )
                    is BackupManager.RestoreResult.Failed -> OpStatus.Error(r.message)
                }
            }.getOrElse { OpStatus.Error(it.message ?: "שגיאה בשחזור") }
            _opStatus.value = status
            refreshLastBackup()
        }
    }

    // ---- Destructive deletes (backup first + typed confirm in UI) ---------

    fun deleteAllBooks() = deleteWithBackup(
        backupWorking = getApplication<Application>().getString(R.string.windows_tool_delete_working_backup),
        deleteWorking = getApplication<Application>().getString(R.string.windows_tool_delete_books_working),
    ) {
        val app = getApplication<Application>()
        val count = container.repository.clearCatalog()
        if (count <= 0) {
            OpStatus.Success(app.getString(R.string.windows_tool_delete_books_empty))
        } else {
            OpStatus.Success(app.getString(R.string.windows_tool_delete_books_done, count))
        }
    }

    fun deleteAllShortcuts() = deleteWithBackup(
        backupWorking = getApplication<Application>().getString(R.string.windows_tool_delete_working_backup),
        deleteWorking = getApplication<Application>().getString(R.string.windows_tool_delete_shortcuts_working),
    ) {
        val app = getApplication<Application>()
        container.shortcutStore.loadFromDisk()
        val count = container.shortcutStore.shortcuts.value.size
        container.shortcutStore.replaceAll(emptyList())
        if (count <= 0) {
            OpStatus.Success(app.getString(R.string.windows_tool_delete_shortcuts_empty))
        } else {
            OpStatus.Success(app.getString(R.string.windows_tool_delete_shortcuts_done, count))
        }
    }

    private fun deleteWithBackup(
        backupWorking: String,
        deleteWorking: String,
        doDelete: suspend () -> OpStatus,
    ) {
        if (isBusy()) {
            _opStatus.value = OpStatus.Error(
                getApplication<Application>().getString(R.string.windows_tool_busy),
            )
            return
        }
        _opStatus.value = OpStatus.Working(backupWorking)
        viewModelScope.launch {
            val status = runCatching {
                withContext(Dispatchers.IO) {
                    ensurePreChangeBackup()?.let { return@withContext it }
                    _opStatus.value = OpStatus.Working(deleteWorking)
                    doDelete()
                }
            }.getOrElse { OpStatus.Error(it.message ?: "שגיאה במחיקה") }
            _opStatus.value = status
            refreshLastBackup()
        }
    }

    // ---- Helpers ----------------------------------------------------------

    /** Returns an error status when pre-change backup failed; null means safe to proceed. */
    private suspend fun ensurePreChangeBackup(): OpStatus.Error? {
        if (backup.backupBeforeImportBlocking()) return null
        val app = getApplication<Application>()
        val detail = when (val state = backup.state.value) {
            is BackupState.Failed -> state.message
            is BackupState.Cancelled ->
                app.getString(R.string.windows_tool_backup_cancelled_before_op)
            else -> app.getString(R.string.windows_tool_backup_unknown_failure)
        }
        return OpStatus.Error(app.getString(R.string.windows_tool_backup_required_failed, detail))
    }

    private fun isBusy(): Boolean =
        _opStatus.value is OpStatus.Working || backupState.value is BackupState.Running

    private fun openBounded(uri: Uri): java.io.InputStream? {
        val resolver = getApplication<Application>().contentResolver
        val size = try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (_: Exception) { -1L }
        if (size > maxImportBytes) return null
        return try { resolver.openInputStream(uri) } catch (_: Exception) { null }
    }

    private fun readSheet(uri: Uri): List<List<String>>? {
        val stream = openBounded(uri) ?: return null
        return try {
            stream.use { XlsxReader.readFirstSheet(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun stamp(): String = STAMP.format(Date())

    companion object {
        private val STAMP: SimpleDateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
        }
    }
}
