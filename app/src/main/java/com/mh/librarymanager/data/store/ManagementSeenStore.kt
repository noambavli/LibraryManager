package com.mh.librarymanager.data.store

import android.content.Context
import com.mh.librarymanager.domain.ManagementBadgeSection
import com.mh.librarymanager.domain.PublicRequest
import com.mh.librarymanager.domain.TechSupportRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/**
 * Tracks when staff last viewed badge-enabled management sections so the
 * dashboard can show "new since last visit" counts.
 */
class ManagementSeenStore(private val context: Context) {

    data class SeenState(
        val requestsSeenAt: Long = System.currentTimeMillis(),
        val techSupportSeenAt: Long = System.currentTimeMillis(),
        val outOfOrderSeenCount: Int = 0,
    )

    companion object {
        const val FORMAT_VERSION = 1
    }

    private val file: File by lazy { File(context.filesDir, "management_seen.json") }

    private val _state = MutableStateFlow(SeenState())
    val state: StateFlow<SeenState> = _state.asStateFlow()

    suspend fun loadFromDisk() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            synchronized(this@ManagementSeenStore) {
                _state.value = if (file.exists()) readFile(file) else SeenState()
            }
        }
    }

    suspend fun markRequestsSeen(at: Long = System.currentTimeMillis()) {
        update { copy(requestsSeenAt = at) }
    }

    suspend fun markTechSupportSeen(at: Long = System.currentTimeMillis()) {
        update { copy(techSupportSeenAt = at) }
    }

    suspend fun markOutOfOrderSeen(count: Int) {
        update { copy(outOfOrderSeenCount = count) }
    }

    fun badgeCounts(
        requests: List<PublicRequest>,
        techSupport: List<TechSupportRequest>,
        outOfOrderCount: Int,
    ): Map<ManagementBadgeSection, Int> {
        val seen = _state.value
        return mapOf(
            ManagementBadgeSection.REQUESTS to
                requests.count { it.createdAt > seen.requestsSeenAt },
            ManagementBadgeSection.TECH_SUPPORT to
                techSupport.count { it.createdAt > seen.techSupportSeenAt },
            ManagementBadgeSection.OUT_OF_ORDER to
                (outOfOrderCount - seen.outOfOrderSeenCount).coerceAtLeast(0),
        )
    }

    private suspend fun update(transform: SeenState.() -> SeenState) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            synchronized(this@ManagementSeenStore) {
                val next = _state.value.transform()
                writeFile(next)
                _state.value = next
            }
        }
    }

    private fun readFile(file: File): SeenState {
        val text = file.readText(Charsets.UTF_8)
        if (text.isBlank()) return SeenState()
        val root = JSONObject(text)
        if (root.optInt("version", 0) < FORMAT_VERSION) return SeenState()
        return SeenState(
            requestsSeenAt = root.optLong("requestsSeenAt", System.currentTimeMillis()),
            techSupportSeenAt = root.optLong("techSupportSeenAt", System.currentTimeMillis()),
            outOfOrderSeenCount = root.optInt("outOfOrderSeenCount", 0),
        )
    }

    private fun writeFile(state: SeenState) {
        val root = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("requestsSeenAt", state.requestsSeenAt)
            .put("techSupportSeenAt", state.techSupportSeenAt)
            .put("outOfOrderSeenCount", state.outOfOrderSeenCount)
        atomicWriteText(file, root.toString())
    }
}
