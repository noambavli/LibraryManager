package com.mh.librarymanager.data.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a one-time disk load off the main thread. [loadedFlag] is checked and
 * set inside [block] while holding [lock].
 */
internal suspend fun loadFromDiskOnce(
    loadedFlag: () -> Boolean,
    lock: Any,
    block: () -> Unit,
) {
    if (loadedFlag()) return
    withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (loadedFlag()) return@withContext
            block()
        }
    }
}
