package com.mh.librarymanager.ui.management

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.LibraryApp
import com.mh.librarymanager.domain.AuditEvent
import com.mh.librarymanager.domain.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * View-model for the management History screen.
 *
 * Combines the raw audit log with the live catalog into a stream of
 * [HistoryRow]s, where each row carries both the audit event and a
 * pre-computed [RestoreState] explaining whether (and why not) the change
 * can be restored.
 */
class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val container = LibraryApp.from(app)

    private val audit: StateFlow<List<AuditEvent>> =
        container.repository.observeAudit()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val catalog: StateFlow<List<Book>> =
        container.repository.observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val rows: StateFlow<List<HistoryRow>> = combine(audit, catalog) { events, books ->
        val byId = books.associateBy { it.id }
        events
            .asReversed() // newest first
            .map { ev -> HistoryRow(event = ev, restore = restoreStateFor(ev, byId)) }
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun restoreDeleted(event: AuditEvent.Deleted): RestoreOutcome =
        withContext(Dispatchers.IO) {
            val current = container.repository
            val ok = current.restoreDeleted(event)
            if (ok) RestoreOutcome.Ok else RestoreOutcome.Conflict
        }

    suspend fun restoreUpdate(event: AuditEvent.Updated): RestoreOutcome =
        withContext(Dispatchers.IO) {
            val ok = container.repository.restoreUpdate(event)
            if (ok) RestoreOutcome.Ok else RestoreOutcome.Conflict
        }

    private fun restoreStateFor(
        event: AuditEvent,
        currentById: Map<String, Book>,
    ): RestoreState = when (event) {
        is AuditEvent.Deleted -> {
            if (currentById.containsKey(event.snapshot.id)) {
                RestoreState.Unavailable(RestoreBlock.IdInUse)
            } else {
                RestoreState.Available
            }
        }
        is AuditEvent.Updated -> {
            val current = currentById[event.after.id]
            when {
                current == null -> RestoreState.Unavailable(RestoreBlock.BookMissing)
                !current.matchesIgnoringTimestamps(event.after) ->
                    RestoreState.Unavailable(RestoreBlock.ChangedSince)
                else -> RestoreState.Available
            }
        }
        is AuditEvent.Added, is AuditEvent.Imported ->
            RestoreState.NotApplicable
    }
}

data class HistoryRow(
    val event: AuditEvent,
    val restore: RestoreState,
)

sealed interface RestoreState {
    data object Available : RestoreState
    data object NotApplicable : RestoreState
    data class Unavailable(val reason: RestoreBlock) : RestoreState
}

enum class RestoreBlock { IdInUse, BookMissing, ChangedSince }

enum class RestoreOutcome { Ok, Conflict }

private fun Book.matchesIgnoringTimestamps(other: Book): Boolean =
    this.copy(updatedAt = 0L, createdAt = 0L) ==
        other.copy(updatedAt = 0L, createdAt = 0L)
