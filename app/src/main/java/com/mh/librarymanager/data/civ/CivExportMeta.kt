package com.mh.librarymanager.data.civ

import org.json.JSONObject

/**
 * Optional metadata block written by LibraryTool on the PC.
 * Older .civ files without ``meta`` still import fine.
 */
data class CivExportMeta(
    val tool: String,
    val toolVersion: String,
    val exportedAt: Long,
    val sourceFile: String,
    val exportKind: String,
    val origin: String,
    val batchNumber: Int = 0,
) {
    val isMergeExport: Boolean get() = exportKind.equals("merge", ignoreCase = true)

    /** Simple label staff understand: "קובץ 3" */
    fun fileLabel(): String =
        if (batchNumber > 0) batchNumber.toString() else sourceFile.ifBlank { "?" }

    fun displayLabel(): String = buildString {
        if (batchNumber > 0) {
            append("קובץ ")
            append(batchNumber)
        }
        if (sourceFile.isNotBlank()) {
            if (isNotEmpty()) append(" · ")
            append(sourceFile)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject?): CivExportMeta? {
            if (obj == null) return null
            return CivExportMeta(
                tool = obj.optString("tool", ""),
                toolVersion = obj.optString("toolVersion", ""),
                exportedAt = obj.optLong("exportedAt", 0L),
                sourceFile = obj.optString("sourceFile", ""),
                exportKind = obj.optString("exportKind", "merge"),
                origin = obj.optString("origin", "pc"),
                batchNumber = obj.optInt("batchNumber", 0),
            )
        }
    }
}
