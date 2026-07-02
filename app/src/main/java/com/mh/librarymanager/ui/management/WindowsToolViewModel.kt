package com.mh.librarymanager.ui.management

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.R
import com.mh.librarymanager.data.backup.BackupManager
import com.mh.librarymanager.data.backup.BackupState
import com.mh.librarymanager.data.backup.BackupTrigger
import com.mh.librarymanager.data.excel.ExcelImportIO
import com.mh.librarymanager.data.excel.MatchingsImportIO
import com.mh.librarymanager.data.homemap.HomeOverviewMapProcessor
import com.mh.librarymanager.data.homemap.HomeOverviewMapStore
import com.mh.librarymanager.data.store.SearchMatchingStore
import com.mh.librarymanager.data.xlsx.CatalogImporter
import com.mh.librarymanager.data.xlsx.WindowsToolCodec
import com.mh.librarymanager.data.xlsx.XlsxReader
import com.mh.librarymanager.data.xlsx.XlsxWriter
import com.mh.librarymanager.domain.BookPlace
import com.mh.librarymanager.domain.HomeOverviewMapKind
import com.mh.librarymanager.domain.mapPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    private val homeMapStore get() = container.homeOverviewMapStore

    private val _pendingHomeMap = MutableStateFlow<PendingHomeMapUpload?>(null)
    val pendingHomeMap: StateFlow<PendingHomeMapUpload?> = _pendingHomeMap.asStateFlow()

    private val _homeMapConfirming = MutableStateFlow(false)
    val homeMapConfirming: StateFlow<Boolean> = _homeMapConfirming.asStateFlow()

    private val _homeMapRevision = MutableStateFlow(0)
    val homeMapRevision: StateFlow<Int> = _homeMapRevision.asStateFlow()

    private val _lastExport = MutableStateFlow<LastExport?>(null)
    val lastExport: StateFlow<LastExport?> = _lastExport.asStateFlow()

    private val _pendingDownload = MutableStateFlow<PendingDownload?>(null)
    val pendingDownload: StateFlow<PendingDownload?> = _pendingDownload.asStateFlow()

    fun hasHomeMap(kind: HomeOverviewMapKind): Boolean = homeMapStore.hasCustomMap(kind)

    data class BackupInfo(val at: Long, val name: String)

    enum class ExportKind { Books, Beis, Matchings }

    data class LastExport(
        val kind: ExportKind,
        val fileName: String,
        val rowCount: Int,
        val absolutePath: String,
        val pcHint: String,
        val isEmpty: Boolean,
    )

    /** Built file waiting for the user to pick a save location (system "Save" dialog). */
    class PendingDownload(
        val kind: ExportKind,
        val fileName: String,
        val rowCount: Int,
        val isEmpty: Boolean,
        val bytes: ByteArray,
    )

    data class PendingHomeMapUpload(
        val kind: HomeOverviewMapKind,
        val preview: HomeOverviewMapProcessor.Preview,
    )

    sealed interface OpStatus {
        data object Idle : OpStatus
        data class Working(val message: String) : OpStatus
        data class Success(val message: String) : OpStatus
        data class Error(val message: String) : OpStatus
    }

    fun dismissStatus() {
        if (_opStatus.value !is OpStatus.Working) _opStatus.value = OpStatus.Idle
    }

    fun dismissExport() {
        _lastExport.value = null
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
        kind = ExportKind.Books,
        workingRes = R.string.windows_tool_export_books_working,
        baseName = "books",
        sheet = "books",
    ) {
        container.catalogStore.loadFromDisk()
        WindowsToolCodec.booksToRows(
            container.catalogStore.books.value.filter { it.isLatest && it.mapPlace() != BookPlace.BEIS_MIDRASH },
        )
    }

    fun exportBeis() = export(
        kind = ExportKind.Beis,
        workingRes = R.string.windows_tool_export_beis_working,
        baseName = "beis",
        sheet = "beis",
    ) {
        container.catalogStore.loadFromDisk()
        WindowsToolCodec.beisToRows(
            container.catalogStore.books.value.filter { it.isLatest && it.mapPlace() == BookPlace.BEIS_MIDRASH },
        )
    }

    fun exportMatchings() = export(
        kind = ExportKind.Matchings,
        workingRes = R.string.windows_tool_export_matchings_working,
        baseName = "matchings",
        sheet = "matchings",
    ) {
        container.matchingStore.loadFromDisk()
        WindowsToolCodec.matchingsToRows(container.matchingStore.matchings.value)
    }

    private fun export(
        kind: ExportKind,
        workingRes: Int,
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
        val app = getApplication<Application>()
        _opStatus.value = OpStatus.Working(app.getString(workingRes))
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val rows = buildRows()
                    val bytes = XlsxWriter.toBytes(rows, sheet)
                    val name = "${baseName}_${stamp()}.xlsx"
                    Triple(name, bytes, (rows.size - 1).coerceAtLeast(0))
                }
            }
            result.fold(
                onSuccess = { (name, bytes, count) ->
                    // Hand the built file to the system "Save" dialog (works on
                    // every Android version, no storage permission needed).
                    _pendingDownload.value = PendingDownload(
                        kind = kind,
                        fileName = name,
                        rowCount = count,
                        isEmpty = count <= 0,
                        bytes = bytes,
                    )
                    _opStatus.value = OpStatus.Idle
                },
                onFailure = {
                    _opStatus.value = OpStatus.Error(it.message ?: "שגיאה בייצוא")
                },
            )
        }
    }

    /** Writes the prepared file to the location the user picked in the save dialog. */
    fun saveExportTo(uri: Uri) {
        val pending = _pendingDownload.value ?: return
        val app = getApplication<Application>()
        _opStatus.value = OpStatus.Working(app.getString(R.string.windows_tool_export_saving))
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = app.contentResolver
                    val stream = resolver.openOutputStream(uri, "w")
                        ?: throw java.io.IOException("no output stream")
                    stream.use { it.write(pending.bytes) }
                    describeSavedLocation(uri, pending.fileName)
                }
            }
            result.fold(
                onSuccess = { location ->
                    _lastExport.value = LastExport(
                        kind = pending.kind,
                        fileName = pending.fileName,
                        rowCount = pending.rowCount,
                        absolutePath = location,
                        pcHint = "",
                        isEmpty = pending.isEmpty,
                    )
                    _pendingDownload.value = null
                    _opStatus.value = OpStatus.Idle
                },
                onFailure = {
                    _pendingDownload.value = null
                    _opStatus.value = OpStatus.Error(
                        app.getString(R.string.windows_tool_export_save_failed),
                    )
                },
            )
        }
    }

    /** User dismissed the save dialog without choosing a location. */
    fun cancelPendingDownload() {
        _pendingDownload.value = null
        if (_opStatus.value !is OpStatus.Working) _opStatus.value = OpStatus.Idle
    }

    /** Best-effort human-readable folder/name from a SAF document URI. */
    private fun describeSavedLocation(uri: Uri, fallbackName: String): String {
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            val tail = docId.substringAfter(':', "")
            if (tail.isNotBlank()) tail else fallbackName
        } catch (_: Exception) {
            fallbackName
        }
    }

    // ---- Imports (always backup first) ------------------------------------

    fun importBooks(uri: Uri) = importWithBackup(uri) {
        val stream = openBounded(uri) ?: return@importWithBackup OpStatus.Error("לא ניתן לפתוח את הקובץ.")
        val result = stream.use {
            CatalogImporter(getApplication(), container.repository).mergeFromStream(it, BookPlace.OTZAR)
        }
        OpStatus.Success(booksImportMessage(result))
    }

    fun importBeis(uri: Uri) = importWithBackup(uri) {
        val stream = openBounded(uri) ?: return@importWithBackup OpStatus.Error("לא ניתן לפתוח את הקובץ.")
        val result = stream.use {
            CatalogImporter(getApplication(), container.repository).mergeFromStream(it, BookPlace.BEIS_MIDRASH)
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
            refreshAdbPendingInternal(retries = 0)
        }
    }

    fun onAdbImportStaged() {
        viewModelScope.launch {
            refreshAdbPendingInternal(retries = 8)
        }
    }

    private suspend fun refreshAdbPendingInternal(retries: Int) {
        repeat(retries + 1) { attempt ->
            when (val outcome = excelIo.loadPendingPreview()) {
                is ExcelImportIO.PreviewOutcome.Ready -> {
                    _adbPending.value = outcome.preview
                    return
                }
                is ExcelImportIO.PreviewOutcome.Failed -> {
                    if (attempt < retries) delay(350)
                    else _adbPending.value = null
                }
            }
        }
    }

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
                    // Backup failed: drop the staged file and stop. Do NOT
                    // re-surface the confirm dialog, or every "OK" would loop
                    // back here. Write the real reason last so the PC sees it.
                    excelIo.discardPendingImport()
                    excelIo.writeImportResult(
                        ExcelImportIO.ImportResult.IoFailure(backupError.message),
                    )
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

    // ---- PC adb push (Beis-Midrash Excel import) --------------------------

    private val beisIo get() = container.beisImportIo

    private val _adbBeisPending = MutableStateFlow<ExcelImportIO.ImportPreview?>(null)
    val adbBeisPending: StateFlow<ExcelImportIO.ImportPreview?> = _adbBeisPending.asStateFlow()

    private val _adbBeisConfirming = MutableStateFlow(false)
    val adbBeisConfirming: StateFlow<Boolean> = _adbBeisConfirming.asStateFlow()

    fun refreshAdbBeisPending() {
        viewModelScope.launch { refreshAdbBeisPendingInternal(retries = 0) }
    }

    fun onAdbBeisImportStaged() {
        viewModelScope.launch { refreshAdbBeisPendingInternal(retries = 8) }
    }

    private suspend fun refreshAdbBeisPendingInternal(retries: Int) {
        repeat(retries + 1) { attempt ->
            when (val outcome = beisIo.loadPendingPreview()) {
                is ExcelImportIO.PreviewOutcome.Ready -> {
                    _adbBeisPending.value = outcome.preview
                    return
                }
                is ExcelImportIO.PreviewOutcome.Failed -> {
                    if (attempt < retries) delay(350)
                    else _adbBeisPending.value = null
                }
            }
        }
    }

    fun confirmAdbBeisPending() {
        if (_adbBeisConfirming.value) return
        _adbBeisConfirming.value = true
        _adbBeisPending.value = null
        _opStatus.value = OpStatus.Working("יוצר גיבוי מלא לפני הייבוא…")
        viewModelScope.launch {
            try {
                val backupError = withContext(Dispatchers.IO) { ensurePreChangeBackup() }
                if (backupError != null) {
                    beisIo.discardPendingImport()
                    beisIo.writeImportResult(
                        ExcelImportIO.ImportResult.IoFailure(backupError.message),
                    )
                    _opStatus.value = backupError
                    return@launch
                }
                val result = withContext(Dispatchers.IO) { beisIo.commitPendingImport() }
                beisIo.writeImportResult(result)
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
                    is ExcelImportIO.ImportResult.Invalid -> OpStatus.Error(result.reason)
                    is ExcelImportIO.ImportResult.IoFailure -> OpStatus.Error(result.reason)
                    else -> OpStatus.Idle
                }
                refreshLastBackup()
            } finally {
                _adbBeisConfirming.value = false
            }
        }
    }

    fun cancelAdbBeisPending() {
        if (_adbBeisConfirming.value) return
        _adbBeisPending.value = null
        beisIo.discardPendingImport()
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
            refreshAdbMatchingsPendingInternal(retries = 0)
        }
    }

    /** Called when PC adb push wakes the app — retry briefly if staging is still in flight. */
    fun onAdbMatchingsImportStaged() {
        viewModelScope.launch {
            refreshAdbMatchingsPendingInternal(retries = 8)
        }
    }

    private suspend fun refreshAdbMatchingsPendingInternal(retries: Int) {
        repeat(retries + 1) { attempt ->
            when (val outcome = matchingsIo.loadPendingPreview()) {
                is MatchingsImportIO.PreviewOutcome.Ready -> {
                    _adbMatchingsPending.value = outcome.preview
                    return
                }
                is MatchingsImportIO.PreviewOutcome.Failed -> {
                    if (attempt < retries) delay(350)
                    else _adbMatchingsPending.value = null
                }
            }
        }
    }

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
                    // Backup failed: drop the staged file and stop. Do NOT
                    // re-surface the confirm dialog, or every "OK" would loop
                    // back here. Write the real reason last so the PC sees it.
                    matchingsIo.discardPendingImport()
                    matchingsIo.writeImportResult(
                        MatchingsImportIO.ImportResult.IoFailure(backupError.message),
                    )
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

    // ---- Home overview maps (separate from book-location maps) -----------

    fun stageHomeMapUpload(kind: HomeOverviewMapKind, uri: Uri) {
        if (isBusy()) {
            _opStatus.value = OpStatus.Error(
                getApplication<Application>().getString(R.string.windows_tool_busy),
            )
            return
        }
        _opStatus.value = OpStatus.Working(
            getApplication<Application>().getString(R.string.windows_tool_home_map_processing),
        )
        viewModelScope.launch {
            val status = runCatching {
                withContext(Dispatchers.IO) {
                    when (val result = HomeOverviewMapProcessor.process(getApplication(), uri)) {
                        is HomeOverviewMapProcessor.Result.Ok -> {
                            _pendingHomeMap.value = PendingHomeMapUpload(kind, result.preview)
                            OpStatus.Idle
                        }
                        is HomeOverviewMapProcessor.Result.Error -> OpStatus.Error(result.message)
                    }
                }
            }.getOrElse { OpStatus.Error(it.message ?: "שגיאה בעיבוד התמונה") }
            _opStatus.value = status
        }
    }

    fun confirmHomeMapUpload() {
        val pending = _pendingHomeMap.value ?: return
        if (_homeMapConfirming.value) return
        _homeMapConfirming.value = true
        _pendingHomeMap.value = null
        _opStatus.value = OpStatus.Working("יוצר גיבוי מלא לפני שמירת המפה…")
        viewModelScope.launch {
            try {
                val status = runCatching {
                    withContext(Dispatchers.IO) {
                        ensurePreChangeBackup()?.let { return@withContext it }
                        _opStatus.value = OpStatus.Working(
                            getApplication<Application>().getString(R.string.windows_tool_home_map_saving),
                        )
                        homeMapStore.saveMap(pending.kind, pending.preview.pngBytes)
                        _homeMapRevision.value++
                        OpStatus.Success(
                            getApplication<Application>().getString(
                                R.string.windows_tool_home_map_saved,
                                getApplication<Application>().getString(pending.kind.titleRes()),
                            ),
                        )
                    }
                }.getOrElse { OpStatus.Error(it.message ?: "שגיאה בשמירת המפה") }
                _opStatus.value = status
                refreshLastBackup()
            } finally {
                _homeMapConfirming.value = false
            }
        }
    }

    fun cancelHomeMapUpload() {
        if (_homeMapConfirming.value) return
        _pendingHomeMap.value = null
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
        // The "books" library is everything that isn't Beis-Midrash (Otzar +
        // any unspecified place), so a Beis delete never touches these and
        // vice-versa.
        val count = container.repository.clearCatalogMatching { it.mapPlace() != BookPlace.BEIS_MIDRASH }
        if (count <= 0) {
            OpStatus.Success(app.getString(R.string.windows_tool_delete_books_empty))
        } else {
            OpStatus.Success(app.getString(R.string.windows_tool_delete_books_done, count))
        }
    }

    fun deleteAllBeis() = deleteWithBackup(
        backupWorking = getApplication<Application>().getString(R.string.windows_tool_delete_working_backup),
        deleteWorking = getApplication<Application>().getString(R.string.windows_tool_delete_beis_working),
    ) {
        val app = getApplication<Application>()
        val count = container.repository.clearCatalogMatching { it.mapPlace() == BookPlace.BEIS_MIDRASH }
        if (count <= 0) {
            OpStatus.Success(app.getString(R.string.windows_tool_delete_beis_empty))
        } else {
            OpStatus.Success(app.getString(R.string.windows_tool_delete_beis_done, count))
        }
    }

    fun deleteAllMatchings() = deleteWithBackup(
        backupWorking = getApplication<Application>().getString(R.string.windows_tool_delete_working_backup),
        deleteWorking = getApplication<Application>().getString(R.string.windows_tool_delete_matchings_working),
    ) {
        val app = getApplication<Application>()
        container.matchingStore.loadFromDisk()
        val count = container.matchingStore.matchings.value.size
        container.matchingStore.replaceAll(emptyList())
        if (count <= 0) {
            OpStatus.Success(app.getString(R.string.windows_tool_delete_matchings_empty))
        } else {
            OpStatus.Success(app.getString(R.string.windows_tool_delete_matchings_done, count))
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
        val raw = try { resolver.openInputStream(uri) } catch (_: Exception) { null } ?: return null
        // When the provider can't report a size up front, enforce the cap while
        // reading so a huge/garbage file can't OOM the kiosk.
        return BoundedInputStream(raw, maxImportBytes)
    }

    /** Throws once more than [limit] bytes are read from the wrapped stream. */
    private class BoundedInputStream(
        private val delegate: java.io.InputStream,
        private val limit: Long,
    ) : java.io.InputStream() {
        private var read = 0L

        private fun track(n: Int): Int {
            if (n > 0) {
                read += n
                if (read > limit) throw java.io.IOException("הקובץ גדול מדי לייבוא.")
            }
            return n
        }

        override fun read(): Int {
            val b = delegate.read()
            if (b >= 0) track(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int = track(delegate.read(b, off, len))

        override fun available(): Int = delegate.available()

        override fun close() = delegate.close()
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
