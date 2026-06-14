package com.mh.librarymanager.data.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Tracks whether a JSON store has finished its first disk read. UI uses
 * [loaded] to distinguish "still loading" from a genuine empty list.
 */
class StoreLoadState {
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    @Volatile
    private var flag: Boolean = false

    fun isLoaded(): Boolean = flag

    fun markLoaded() {
        if (flag) return
        flag = true
        _loaded.value = true
    }
}

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
