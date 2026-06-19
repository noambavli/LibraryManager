package com.mh.librarymanager.data.homemap

import android.content.Context
import com.mh.librarymanager.domain.HomeOverviewMapKind
import java.io.File

/**
 * Persists full overview map images uploaded via Windows Tool.
 * Stored under `filesDir/home_overview_maps/` — never touches the bundled
 * book-location map assets used by [com.mh.librarymanager.data.librarymap.LibraryMapLoader].
 */
class HomeOverviewMapStore(private val context: Context) {

    private val dir: File by lazy {
        File(context.filesDir, DIR_NAME).also { it.mkdirs() }
    }

    fun mapFile(kind: HomeOverviewMapKind): File = File(dir, kind.fileName)

    fun hasCustomMap(kind: HomeOverviewMapKind): Boolean {
        val file = mapFile(kind)
        return file.exists() && file.length() > 0L
    }

    fun saveMap(kind: HomeOverviewMapKind, pngBytes: ByteArray) {
        val target = mapFile(kind)
        val tmp = File(dir, "${kind.fileName}.tmp")
        tmp.writeBytes(pngBytes)
        if (target.exists() && !target.delete()) {
            tmp.delete()
            error("לא ניתן להחליף את המפה הקיימת.")
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            error("לא ניתן לשמור את המפה.")
        }
    }

    fun deleteMap(kind: HomeOverviewMapKind) {
        mapFile(kind).delete()
    }

    fun listExistingFiles(): List<String> =
        dir.listFiles()?.mapNotNull { f ->
            f.takeIf { it.isFile && it.length() > 0L }?.name
        }.orEmpty()

    companion object {
        const val DIR_NAME = "home_overview_maps"

        fun from(context: Context): HomeOverviewMapStore =
            HomeOverviewMapStore(context.applicationContext)
    }
}
