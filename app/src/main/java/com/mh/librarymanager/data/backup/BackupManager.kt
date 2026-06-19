package com.mh.librarymanager.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.mh.librarymanager.BuildConfig
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.data.export.DownloadsWriter
import com.mh.librarymanager.data.homemap.HomeOverviewMapStore
import com.mh.librarymanager.data.store.CatalogStore
import com.mh.librarymanager.data.xlsx.WindowsToolCodec
import com.mh.librarymanager.data.xlsx.XlsxWriter
import com.mh.librarymanager.domain.Announcement
import com.mh.librarymanager.domain.PublicRequest
import com.mh.librarymanager.domain.RequestStatus
import com.mh.librarymanager.domain.TechSupportRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Creates and restores full-app backups (the "Windows Tool" safety net).
 *
 * A backup is a single dated `.zip` written to the public Downloads folder so a
 * connected PC sees it over MTP. It carries two views of the same data:
 *
 *  * `restore/` — the **exact** on-disk JSON store files plus a manifest. This
 *    is what [restoreFromZip] reads to put the app back into a precise state.
 *  * `readable/` — human-friendly `.xlsx`/`.txt` files for reading only.
 *
 * The engine is built for a kiosk: backups run on an app-lifetime scope so they
 * survive screen changes, are serialised by a [Mutex] so a USB-triggered backup
 * and a manual one never clash, are cancellable, and never throw into the UI —
 * every failure becomes a [BackupState.Failed].
 */
class BackupManager(
    private val context: Context,
    private val app: LibraryApp,
) {

    companion object {
        private const val TAG = "BackupManager"
        const val BACKUP_FORMAT_VERSION = 1

        /** Reject restore archives larger than this (defends against bad input). */
        private const val MAX_RESTORE_BYTES = 256L * 1024L * 1024L

        /** Ignore repeated USB_STATE broadcasts from the same plug event. */
        private const val USB_TRIGGER_DEBOUNCE_MS = 10_000L

        /** Skip USB auto-backup when any full ZIP backup succeeded within this window. */
        private const val USB_RECENT_BACKUP_MS = 30L * 60L * 1000L

        private const val PREFS = "windows_tool_backup"
        private const val PREF_LAST_AT = "last_backup_at"
        private const val PREF_LAST_NAME = "last_backup_name"

        /** Exact store files we snapshot and can restore. Order is cosmetic. */
        private val STORE_FILES = listOf(
            "catalog.json",
            "colors.json",
            "shortcuts.json",
            "matchings.json",
            "requests.json",
            "tech_support.json",
            "announcements.json",
            "audit.json",
            "search_history.json",
            "book_location_presses.json",
            "civ_import_log.json",
        )

        /** Overview map PNGs uploaded via Windows Tool (home page only). */
        private val HOME_MAP_FILES = listOf(
            HomeOverviewMapStore.DIR_NAME + "/otzar.png",
            HomeOverviewMapStore.DIR_NAME + "/beis_midrash.png",
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    @Volatile private var currentJob: Job? = null
    @Volatile private var lastUsbTriggerAt = 0L

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    fun lastBackupAt(): Long = prefs.getLong(PREF_LAST_AT, 0L)
    fun lastBackupName(): String = prefs.getString(PREF_LAST_NAME, "").orEmpty()

    // ---- Triggers ---------------------------------------------------------

    /** Fire-and-forget backup (used by the USB receiver and the manual button). */
    fun launchBackup(trigger: BackupTrigger) {
        scope.launch { runBackup(trigger) }
    }

    /**
     * Auto-backup when the tablet is plugged into a PC. Skipped silently when a
     * full ZIP backup already succeeded within [USB_RECENT_BACKUP_MS], and when
     * there is nothing worth saving yet (a fresh tablet on its very first connect).
     */
    fun onUsbConnected() {
        val now = System.currentTimeMillis()
        if (now - lastUsbTriggerAt < USB_TRIGGER_DEBOUNCE_MS) return
        lastUsbTriggerAt = now
        if (_state.value is BackupState.Running) return

        val lastBackup = lastBackupAt()
        if (lastBackup > 0L && now - lastBackup < USB_RECENT_BACKUP_MS) {
            Log.i(
                TAG,
                "USB connected — full backup ran ${(now - lastBackup) / 60_000L} min ago, skipping",
            )
            return
        }

        scope.launch {
            if (!hasAnyData()) {
                Log.i(TAG, "USB connected but no data yet — skipping auto-backup")
                return@launch
            }
            runBackup(BackupTrigger.Usb)
        }
    }

    /**
     * Run a backup and wait for it (used before a destructive import so the
     * snapshot captures the pre-import state). Returns true if a backup file
     * was written.
     */
    suspend fun backupBeforeImportBlocking(): Boolean {
        val result = runBackup(BackupTrigger.BeforeImport)
        return result is BackupState.Done
    }

    fun cancel() {
        scope.launch { currentJob?.cancelAndJoin() }
    }

    fun dismiss() {
        if (_state.value !is BackupState.Running) _state.value = BackupState.Idle
    }

    // ---- Backup -----------------------------------------------------------

    private suspend fun runBackup(trigger: BackupTrigger): BackupState = mutex.withLock {
        val job = coroutineContext[Job]
        currentJob = job
        _state.value = BackupState.Running(trigger, BackupStep.COLLECTING)
        val result = try {
            performBackup(trigger)
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            _state.value = BackupState.Cancelled(trigger)
            throw cancel
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            BackupState.Failed(trigger, e.message ?: "שגיאה לא ידועה ביצירת הגיבוי")
        } finally {
            currentJob = null
        }
        _state.value = result
        result
    }

    private suspend fun performBackup(trigger: BackupTrigger): BackupState {
        // Make sure every store has read its file before we snapshot.
        loadAllStores()
        coroutineContext.ensureActive()

        _state.value = BackupState.Running(trigger, BackupStep.PACKING)
        val now = System.currentTimeMillis()
        val fileName = "LibraryBackup_${FILE_STAMP.format(Date(now))}.zip"
        val temp = File.createTempFile("backup", ".zip", context.cacheDir)
        try {
            ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                writeManifest(zip, now)
                writeReadme(zip)
                writeRestoreFiles(zip)
                coroutineContext.ensureActive()
                writeReadable(zip)
            }
            coroutineContext.ensureActive()

            _state.value = BackupState.Running(trigger, BackupStep.WRITING)
            val outcome = DownloadsWriter.write(
                context = context,
                displayName = fileName,
                mimeType = DownloadsWriter.MIME_ZIP,
            ) { out -> temp.inputStream().use { it.copyTo(out) } }

            return when (outcome) {
                is DownloadsWriter.Result.Ok -> {
                    prefs.edit()
                        .putLong(PREF_LAST_AT, now)
                        .putString(PREF_LAST_NAME, fileName)
                        .apply()
                    BackupState.Done(trigger, fileName, now, outcome.location)
                }
                is DownloadsWriter.Result.Failed ->
                    BackupState.Failed(trigger, outcome.message)
            }
        } finally {
            try { temp.delete() } catch (_: Exception) {}
        }
    }

    private fun writeManifest(zip: ZipOutputStream, now: Long) {
        val counts = JSONObject()
            .put("books", app.catalogStore.books.value.count { it.isLatest })
            .put("shortcuts", app.shortcutStore.shortcuts.value.size)
            .put("matchings", app.matchingStore.matchings.value.size)
            .put("requests", app.requestStore.requests.value.size)
            .put("techSupport", app.techSupportStore.requests.value.size)
            .put("announcements", app.announcementStore.announcements.value.size)
        val manifest = JSONObject()
            .put("backupFormatVersion", BACKUP_FORMAT_VERSION)
            .put("app", context.packageName)
            .put("appVersionName", BuildConfig.VERSION_NAME)
            .put("appVersionCode", BuildConfig.VERSION_CODE)
            .put("catalogFormatVersion", CatalogStore.CATALOG_FORMAT_VERSION)
            .put("createdAt", now)
            .put("createdAtText", READABLE_STAMP.format(Date(now)))
            .put("counts", counts)
        zip.putText("manifest.json", manifest.toString(2))
    }

    private fun writeReadme(zip: ZipOutputStream) {
        zip.putText("README.txt", README_TEXT)
    }

    private fun writeRestoreFiles(zip: ZipOutputStream) {
        for (name in STORE_FILES) {
            val file = File(context.filesDir, name)
            if (!file.exists() || file.length() == 0L) continue
            zip.putNextEntry(ZipEntry("restore/$name"))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        for (name in HOME_MAP_FILES) {
            val file = File(context.filesDir, name)
            if (!file.exists() || file.length() == 0L) continue
            zip.putNextEntry(ZipEntry("restore/$name"))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun writeReadable(zip: ZipOutputStream) {
        val books = app.catalogStore.books.value.filter { it.isLatest }
        zip.putBytes(
            "readable/books.xlsx",
            XlsxWriter.toBytes(WindowsToolCodec.booksToRows(books), "books"),
        )
        zip.putBytes(
            "readable/shortcuts.xlsx",
            XlsxWriter.toBytes(
                WindowsToolCodec.shortcutsToRows(app.shortcutStore.shortcuts.value),
                "shortcuts",
            ),
        )
        zip.putBytes(
            "readable/matchings.xlsx",
            XlsxWriter.toBytes(
                WindowsToolCodec.matchingsToRows(app.matchingStore.matchings.value),
                "matchings",
            ),
        )
        zip.putText("readable/requests.txt", requestsText(app.requestStore.requests.value))
        zip.putText("readable/tech_support.txt", techSupportText(app.techSupportStore.requests.value))
        zip.putText("readable/announcements.txt", announcementsText(app.announcementStore.announcements.value))
        zip.putText("readable/summary.txt", summaryText(books.size))
    }

    // ---- Restore ----------------------------------------------------------

    suspend fun restoreFromZip(uri: Uri): RestoreResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            // Safety net: snapshot current data before overwriting it, so a bad
            // restore (or wrong file) can still be undone from the backups list.
            if (hasAnyData()) {
                val snapshot = performBackup(BackupTrigger.BeforeImport)
                if (snapshot is BackupState.Failed) {
                    _state.value = BackupState.Idle
                    return@withContext RestoreResult.Failed(
                        "השחזור בוטל — לא ניתן היה לשמור גיבוי בטיחות לפני השחזור: ${snapshot.message}",
                    )
                }
            }
            _state.value = BackupState.Running(BackupTrigger.Manual, BackupStep.RESTORING)
            val result = try {
                doRestore(uri)
            } catch (e: Exception) {
                Log.e(TAG, "Restore failed", e)
                RestoreResult.Failed(e.message ?: "שגיאה לא ידועה בשחזור")
            }
            _state.value = BackupState.Idle
            result
        }
    }

    private suspend fun doRestore(uri: Uri): RestoreResult {
        val resolver = context.contentResolver
        val staged = HashMap<String, ByteArray>()
        var manifest: JSONObject? = null

        val opened = resolver.openInputStream(uri)
            ?: return RestoreResult.Failed("לא ניתן לפתוח את הקובץ.")
        var total = 0L
        opened.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        name == "manifest.json" -> {
                            val bytes = zip.readBoundedEntry { total += it; total }
                            manifest = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
                        }
                        name.startsWith("restore/") -> {
                            val leaf = name.removePrefix("restore/")
                            if (leaf in STORE_FILES || leaf in HOME_MAP_FILES) {
                                staged[leaf] = zip.readBoundedEntry { total += it; total }
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }

        manifest?.let { m ->
            val version = m.optInt("backupFormatVersion", 0)
            if (version > BACKUP_FORMAT_VERSION) {
                return RestoreResult.Failed("הגיבוי נוצר בגרסה חדשה יותר. עדכנו את האפליקציה.")
            }
        }

        if (staged.isEmpty()) {
            return RestoreResult.Failed("הקובץ אינו גיבוי תקין (לא נמצאו נתונים לשחזור).")
        }

        // Apply atomically: write everything to side files first, then swap
        // them in with rollback. A crash or failure can no longer leave a
        // half-restored mix of old and new store files.
        return applyRestoreAtomically(staged, manifest)
    }

    /**
     * Two-phase apply. Phase 1: stage every change to `<file>.restore-new` and
     * move current files aside to `<file>.restore-bak`. Phase 2: swap new files
     * into place. Any failure rolls every file back to its backed-up original.
     */
    private suspend fun applyRestoreAtomically(
        staged: Map<String, ByteArray>,
        manifest: JSONObject?,
    ): RestoreResult {
        val allNames = STORE_FILES + HOME_MAP_FILES
        val backups = ArrayList<Pair<File, File>>() // original -> .restore-bak
        val newFiles = ArrayList<Pair<File, File>>() // .restore-new -> original
        val toDelete = ArrayList<File>()

        try {
            // Phase 1 — prepare side files (no destructive change yet).
            for (name in allNames) {
                val target = File(context.filesDir, name)
                val bytes = staged[name]
                if (bytes != null) {
                    target.parentFile?.mkdirs()
                    val tmp = File(target.parentFile, target.name + ".restore-new")
                    if (tmp.exists()) tmp.delete()
                    tmp.writeBytes(bytes)
                    newFiles += tmp to target
                } else if (target.exists()) {
                    toDelete += target
                }
            }

            // Phase 2 — move current files aside, then swap new ones in.
            for ((_, target) in newFiles) {
                if (target.exists()) {
                    val bak = File(target.parentFile, target.name + ".restore-bak")
                    if (bak.exists()) bak.delete()
                    if (target.renameTo(bak)) backups += target to bak
                    else {
                        target.copyTo(bak, overwrite = true)
                        backups += target to bak
                        target.delete()
                    }
                }
            }
            for (target in toDelete) {
                val bak = File(target.parentFile, target.name + ".restore-bak")
                if (bak.exists()) bak.delete()
                if (target.renameTo(bak)) backups += target to bak
            }
            for ((tmp, target) in newFiles) {
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
            }
        } catch (e: Exception) {
            // Roll back: restore every original we moved aside, drop side files.
            for ((target, bak) in backups) {
                try {
                    if (target.exists()) target.delete()
                    bak.renameTo(target)
                } catch (_: Exception) {}
            }
            for ((tmp, _) in newFiles) {
                try { if (tmp.exists()) tmp.delete() } catch (_: Exception) {}
            }
            reloadAllStores()
            Log.e(TAG, "Restore apply failed; rolled back", e)
            return RestoreResult.Failed("השחזור נכשל — הנתונים הוחזרו למצב הקודם.")
        }

        // Success — discard the backed-up originals.
        for ((_, bak) in backups) {
            try { if (bak.exists()) bak.delete() } catch (_: Exception) {}
        }

        reloadAllStores()
        return RestoreResult.Ok(
            books = app.catalogStore.books.value.count { it.isLatest },
            createdAtText = manifest?.optString("createdAtText").orEmpty(),
        )
    }

    // ---- Helpers ----------------------------------------------------------

    private suspend fun hasAnyData(): Boolean {
        loadAllStores()
        return app.catalogStore.books.value.any { it.isLatest } ||
            app.shortcutStore.shortcuts.value.isNotEmpty() ||
            app.matchingStore.matchings.value.isNotEmpty() ||
            app.requestStore.requests.value.isNotEmpty() ||
            app.techSupportStore.requests.value.isNotEmpty() ||
            app.announcementStore.announcements.value.isNotEmpty()
    }

    private suspend fun loadAllStores() {
        app.catalogStore.loadFromDisk()
        app.shortcutStore.loadFromDisk()
        app.matchingStore.loadFromDisk()
        app.requestStore.loadFromDisk()
        app.techSupportStore.loadFromDisk()
        app.announcementStore.loadFromDisk()
        app.auditStore.loadFromDisk()
        app.searchHistoryStore.loadFromDisk()
        app.bookLocationPressStore.loadFromDisk()
    }

    private suspend fun reloadAllStores() {
        app.catalogStore.reloadFromDisk()
        app.shortcutStore.reloadFromDisk()
        app.matchingStore.reloadFromDisk()
        app.requestStore.reloadFromDisk()
        app.techSupportStore.reloadFromDisk()
        app.announcementStore.reloadFromDisk()
        app.auditStore.reloadFromDisk()
        app.searchHistoryStore.reloadFromDisk()
        app.bookLocationPressStore.reloadFromDisk()
    }

    private fun requestsText(requests: List<PublicRequest>): String = buildString {
        appendLine("בקשות הציבור (${requests.size})")
        appendLine("========================================")
        if (requests.isEmpty()) appendLine("אין בקשות.")
        requests.sortedByDescending { it.createdAt }.forEach { r ->
            appendLine()
            appendLine("שם: ${r.requesterName.ifBlank { "אנונימי" }}")
            appendLine("ספר: ${r.bookName}")
            if (r.details.isNotBlank()) appendLine("פרטים: ${r.details}")
            appendLine("סטטוס: ${statusText(r.status)}")
            appendLine("נשלח: ${formatDate(r.createdAt)}")
        }
    }

    private fun techSupportText(requests: List<TechSupportRequest>): String = buildString {
        appendLine("דיווחי תמיכה טכנית (${requests.size})")
        appendLine("========================================")
        if (requests.isEmpty()) appendLine("אין דיווחים.")
        requests.sortedByDescending { it.createdAt }.forEach { r ->
            appendLine()
            appendLine("שם: ${r.reporterName.ifBlank { "אנונימי" }}")
            appendLine("בעיה: ${r.problem}")
            appendLine("נשלח: ${formatDate(r.createdAt)}")
        }
    }

    private fun announcementsText(announcements: List<Announcement>): String = buildString {
        appendLine("הודעות המערכת (${announcements.size})")
        appendLine("========================================")
        if (announcements.isEmpty()) appendLine("אין הודעות.")
        announcements.sortedByDescending { it.createdAt }.forEach { a ->
            appendLine()
            appendLine("כותרת: ${a.title}")
            if (a.description.isNotBlank()) appendLine("תוכן: ${a.description}")
            appendLine("פורסם: ${formatDate(a.createdAt)} · מוצג ${a.durationDays} ימים")
            if (a.linkedBookIds.isNotEmpty()) appendLine("ספרים מקושרים: ${a.linkedBookIds.size}")
        }
    }

    private fun summaryText(bookCount: Int): String = buildString {
        appendLine("גיבוי ספריית בית המדרש")
        appendLine("נוצר: ${READABLE_STAMP.format(Date())}")
        appendLine("גרסת אפליקציה: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("----------------------------------------")
        appendLine("ספרים: $bookCount")
        appendLine("קיצורי חיפוש: ${app.shortcutStore.shortcuts.value.size}")
        appendLine("התאמות חיפוש: ${app.matchingStore.matchings.value.size}")
        appendLine("בקשות ציבור: ${app.requestStore.requests.value.size}")
        appendLine("דיווחי תמיכה: ${app.techSupportStore.requests.value.size}")
        appendLine("הודעות: ${app.announcementStore.announcements.value.size}")
    }

    private fun statusText(status: RequestStatus): String = when (status) {
        RequestStatus.RECEIVED -> "נקלט"
        RequestStatus.IN_PROGRESS -> "בתהליך"
        RequestStatus.COMPLETED -> "הושלם"
    }

    private fun formatDate(ms: Long): String =
        if (ms > 0L) READABLE_STAMP.format(Date(ms)) else "—"

    sealed interface RestoreResult {
        data class Ok(val books: Int, val createdAtText: String) : RestoreResult
        data class Failed(val message: String) : RestoreResult
    }

    private inline fun ZipInputStream.readBoundedEntry(onRead: (Long) -> Long): ByteArray {
        val buffer = ByteArray(16 * 1024)
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val n = read(buffer)
            if (n <= 0) break
            val total = onRead(n.toLong())
            if (total > MAX_RESTORE_BYTES) {
                throw IllegalStateException("הקובץ גדול מדי לשחזור.")
            }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.putText(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.putBytes(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }
}

enum class BackupTrigger { Usb, Manual, BeforeImport }

object BackupStep {
    const val COLLECTING = "אוסף את כל נתוני האפליקציה…"
    const val PACKING = "אורז גיבוי…"
    const val WRITING = "כותב את קובץ הגיבוי למחשב…"
    const val RESTORING = "משחזר נתונים…"
}

sealed interface BackupState {
    data object Idle : BackupState
    data class Running(val trigger: BackupTrigger, val step: String) : BackupState
    data class Done(val trigger: BackupTrigger, val fileName: String, val at: Long, val location: String = "") : BackupState
    data class Failed(val trigger: BackupTrigger, val message: String) : BackupState
    data class Cancelled(val trigger: BackupTrigger) : BackupState
}

private val FILE_STAMP: SimpleDateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
}

private val READABLE_STAMP: SimpleDateFormat by lazy {
    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("he"))
}

private val README_TEXT = """
גיבוי ספריית בית המדרש
======================

קובץ זה הוא גיבוי מלא של נתוני האפליקציה, ונוצר אוטומטית בעת חיבור הטאבלט
למחשב ולפני כל ייבוא, וכן ידנית מתוך "כלי Windows" שבמסך הניהול.

מבנה הקובץ:
  • restore/   — קבצי הנתונים המדויקים (JSON). תיקייה זו משמשת לשחזור
                 מדויק של האפליקציה למצב שבו הייתה בעת הגיבוי.
  • readable/  — קבצים קריאים לבני אדם (Excel ו-טקסט) לצפייה בלבד.
  • manifest.json — פרטי הגיבוי (תאריך, גרסה, כמויות).

כיצד לשחזר:
  פתחו באפליקציה: ניהול ← כלי Windows ← "שחזור מגיבוי", ובחרו קובץ zip זה.
  השחזור יחזיר את כל הנתונים (ספרים, קיצורים, בקשות, תמיכה, הודעות ועוד)
  למצב שנשמר בגיבוי. אין לשנות את שמות הקבצים שבתוך הארכיון.
""".trimIndent()
