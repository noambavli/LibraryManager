package com.mh.librarymanager.data.storage

import android.content.Context
import android.os.Environment
import com.mh.librarymanager.data.excel.ExcelImportIO
import com.mh.librarymanager.data.excel.MatchingsImportIO
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sandboxed file browser roots for management. Paths are validated on every
 * navigation so the UI cannot escape into system storage.
 */
enum class StorageZone {
    DOWNLOADS,
    APP_EXTERNAL,
    APP_CACHE,
    APP_DATA,
}

data class StorageEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val deletable: Boolean,
    val protectedReason: String? = null,
)

data class ZoneInfo(
    val zone: StorageZone,
    val label: String,
    val description: String,
    val available: Boolean,
)

class SandboxStorage(private val context: Context) {

    fun listZones(): List<ZoneInfo> = StorageZone.entries.map { zone ->
        ZoneInfo(
            zone = zone,
            label = zoneLabel(zone),
            description = zoneDescription(zone),
            available = zoneRoot(zone) != null,
        )
    }

    fun listEntries(zone: StorageZone, relativePath: String): List<StorageEntry> {
        val dir = resolveDirectory(zone, relativePath) ?: return emptyList()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
            ?.map { file ->
                val deletable = canDelete(zone, relativePath, file.name)
                StorageEntry(
                    name = file.name,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isDirectory) 0L else file.length(),
                    modifiedAt = file.lastModified(),
                    deletable = deletable.allowed,
                    protectedReason = deletable.reason,
                )
            }
            .orEmpty()
    }

    fun deleteEntry(zone: StorageZone, relativePath: String, name: String): DeleteResult {
        val parent = resolveDirectory(zone, relativePath)
            ?: return DeleteResult.Error("הנתיב אינו זמין.")
        val target = File(parent, name)
        val deletable = canDelete(zone, relativePath, name)
        if (!deletable.allowed) {
            return DeleteResult.Error(deletable.reason ?: "לא ניתן למחוק קובץ זה.")
        }
        if (!target.exists()) return DeleteResult.Error("הקובץ כבר לא קיים.")
        return try {
            val ok = if (target.isDirectory) target.deleteRecursively() else target.delete()
            if (ok) DeleteResult.Ok else DeleteResult.Error("המחיקה נכשלה.")
        } catch (e: Exception) {
            DeleteResult.Error(e.message ?: "שגיאה במחיקה.")
        }
    }

    fun breadcrumb(zone: StorageZone, relativePath: String): String {
        val rootLabel = zoneLabel(zone)
        if (relativePath.isBlank()) return rootLabel
        return "$rootLabel / ${relativePath.replace(File.separatorChar.toString(), " / ")}"
    }

    fun zoneRoot(zone: StorageZone): File? = when (zone) {
        StorageZone.DOWNLOADS -> resolveDownloadsDir()
        StorageZone.APP_EXTERNAL -> context.getExternalFilesDir(null)?.takeIf { it.exists() || it.mkdirs() }
        StorageZone.APP_CACHE -> context.cacheDir.takeIf { it.exists() || it.mkdirs() }
        StorageZone.APP_DATA -> context.filesDir.takeIf { it.exists() }
    }

    fun resolveDirectory(zone: StorageZone, relativePath: String): File? {
        val root = zoneRoot(zone) ?: return null
        if (relativePath.isBlank()) return root
        val target = File(root, relativePath)
        return target.takeIf { isInsideRoot(root, it) && it.isDirectory }
    }

    fun resolveChildDirectory(zone: StorageZone, relativePath: String, name: String): String? {
        val next = if (relativePath.isBlank()) name else "$relativePath${File.separator}$name"
        val dir = resolveDirectory(zone, next) ?: return null
        return if (dir.isDirectory) next else null
    }

    private fun isInsideRoot(root: File, target: File): Boolean {
        return try {
            val rootPath = root.canonicalFile.path
            val targetPath = target.canonicalFile.path
            targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
        } catch (_: Exception) {
            false
        }
    }

    private fun canDelete(zone: StorageZone, relativePath: String, name: String): DeletionPolicy {
        val parent = resolveDirectory(zone, relativePath) ?: return DeletionPolicy(false, "הנתיב אינו זמין.")
        val file = File(parent, name)
        if (!isInsideRoot(zoneRoot(zone)!!, file)) {
            return DeletionPolicy(false, "גישה חסומה.")
        }

        return when (zone) {
            StorageZone.APP_DATA ->
                DeletionPolicy(false, "קבצי נתוני האפליקציה מוגנים ולא ניתן למחוק אותם מכאן.")
            StorageZone.APP_CACHE,
            StorageZone.APP_EXTERNAL ->
                DeletionPolicy(true, null)
            StorageZone.DOWNLOADS ->
                downloadsDeletionPolicy(name, file)
        }
    }

    private fun downloadsDeletionPolicy(name: String, file: File): DeletionPolicy {
        if (file.isDirectory) {
            return DeletionPolicy(false, "לא ניתן למחוק תיקיות מתיקיית Download.")
        }
        if (PROTECTED_DOWNLOAD_NAMES.contains(name)) {
            return DeletionPolicy(false, "קובץ זה נדרש לפעולת האפליקציה ולא ניתן למחוק אותו.")
        }
        if (DELETABLE_DOWNLOAD_SUFFIXES.any { name.endsWith(it, ignoreCase = true) } &&
            DELETABLE_DOWNLOAD_PATTERNS.any { it.matches(name) }
        ) {
            return DeletionPolicy(true, null)
        }
        return DeletionPolicy(false, "ניתן למחוק רק קבצי ייצוא, גיבוי ודוחות ייבוא ישנים של הספרייה.")
    }

    private fun resolveDownloadsDir(): File? {
        val candidates = listOf(
            File("/sdcard/Download"),
            File("/storage/emulated/0/Download"),
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory && it.canRead() }
    }

    private fun zoneLabel(zone: StorageZone): String = when (zone) {
        StorageZone.DOWNLOADS -> "Download"
        StorageZone.APP_EXTERNAL -> "קבצי אפליקציה (חיצוני)"
        StorageZone.APP_CACHE -> "מטמון"
        StorageZone.APP_DATA -> "נתוני האפליקציה"
    }

    private fun zoneDescription(zone: StorageZone): String = when (zone) {
        StorageZone.DOWNLOADS -> "ייצוא, גיבויים ודוחות — מחיקה רק לקבצים שאינם בשימוש"
        StorageZone.APP_EXTERNAL -> "קבצים זמניים של האפליקציה — ניתן למחוק בבטחה"
        StorageZone.APP_CACHE -> "קבצי מטמון — ניתן למחוק בבטחה"
        StorageZone.APP_DATA -> "נתוני הקטלוג והגדרות — צפייה בלבד"
    }

    sealed interface DeleteResult {
        data object Ok : DeleteResult
        data class Error(val message: String) : DeleteResult
    }

    private data class DeletionPolicy(val allowed: Boolean, val reason: String?)

    companion object {
        val PROTECTED_APP_DATA_FILES = setOf(
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
            ExcelImportIO.PENDING_FILE_NAME,
            ExcelImportIO.BEIS_PENDING_FILE_NAME,
            MatchingsImportIO.PENDING_FILE_NAME,
        )

        private val PROTECTED_DOWNLOAD_NAMES = setOf(
            ExcelImportIO.INCOMING_CANONICAL_NAME,
            ExcelImportIO.BEIS_INCOMING_CANONICAL_NAME,
            MatchingsImportIO.INCOMING_CANONICAL_NAME,
            ExcelImportIO.PENDING_FILE_NAME,
            ExcelImportIO.BEIS_PENDING_FILE_NAME,
            MatchingsImportIO.PENDING_FILE_NAME,
        )

        private val DELETABLE_DOWNLOAD_SUFFIXES = listOf(".zip", ".xlsx", ".txt", ".civ")

        private val DELETABLE_DOWNLOAD_PATTERNS = listOf(
            Regex("^LibraryBackup_.*\\.zip$", RegexOption.IGNORE_CASE),
            Regex("^books_.*\\.xlsx$", RegexOption.IGNORE_CASE),
            Regex("^beis_.*\\.xlsx$", RegexOption.IGNORE_CASE),
            Regex("^beis-\\d+\\.xlsx$", RegexOption.IGNORE_CASE),
            Regex("^matchings_.*\\.xlsx$", RegexOption.IGNORE_CASE),
            Regex(".*-import-result\\.txt$", RegexOption.IGNORE_CASE),
            Regex(".*\\.civ$", RegexOption.IGNORE_CASE),
        )

        private val STAMP: SimpleDateFormat by lazy {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("he"))
        }

        fun formatSize(bytes: Long): String = when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
            else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }

        fun formatDate(ms: Long): String = if (ms > 0L) STAMP.format(Date(ms)) else "—"
    }
}
