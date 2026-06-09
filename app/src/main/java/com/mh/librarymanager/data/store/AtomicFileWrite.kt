package com.mh.librarymanager.data.store

import java.io.File
import java.nio.charset.Charset

/**
 * Persists [content] to [target] without a delete-then-rename window that can
 * leave no file at all if [File.renameTo] fails.
 *
 * 1. Write a complete copy to a same-directory ``*.tmp`` file.
 * 2. Try an atomic rename over [target] (no prior delete).
 * 3. If rename fails, overwrite [target] in place, then remove the temp file.
 */
internal fun atomicWriteText(
    target: File,
    content: String,
    charset: Charset = Charsets.UTF_8,
) {
    val parent = target.parentFile
        ?: error("Cannot atomically write file without a parent directory: ${target.absolutePath}")
    val tmp = File(parent, "${target.name}.tmp")
    tmp.writeText(content, charset)
    if (tmp.renameTo(target)) {
        return
    }
    target.writeText(content, charset)
    tmp.delete()
}
