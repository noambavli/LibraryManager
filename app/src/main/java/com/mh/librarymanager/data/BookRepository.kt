package com.mh.librarymanager.data

import com.mh.librarymanager.data.store.AuditStore
import com.mh.librarymanager.data.store.CatalogStore
import com.mh.librarymanager.domain.AuditEvent
import com.mh.librarymanager.domain.Book
import com.mh.librarymanager.domain.CustomColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.UUID

/**
 * Single point of access to the persisted catalog. The store is JSON today;
 * if we later swap to Room or DataStore, callers stay unchanged.
 *
 * Every individual mutation (add / update / delete / restore) records an
 * [AuditEvent] via the [AuditStore]. Bulk replacements (xlsx import) record
 * a single [AuditEvent.Imported] instead of one entry per book so the
 * history stays readable.
 */
class BookRepository(
    private val store: CatalogStore,
    private val auditStore: AuditStore,
) {

    fun observeAll(): Flow<List<Book>> = store.books
        .map { books -> books.filter { it.isLatest } }
        .onStart { store.loadFromDisk() }

    /** Includes non-latest revisions; only the management layer needs this. */
    fun observeAllIncludingHistory(): Flow<List<Book>> = store.books
        .onStart { store.loadFromDisk() }

    fun observeColors(): Flow<List<CustomColor>> = store.colors
        .onStart { store.loadFromDisk() }

    fun observeAudit(): Flow<List<AuditEvent>> = auditStore.events
        .onStart { auditStore.loadFromDisk() }

    suspend fun count(): Int = store.count()

    /**
     * In-memory snapshot of the current catalog (all rows, including history).
     * Used by callers (e.g. .civ import) that need to back up the existing
     * state synchronously before doing a destructive replace.
     */
    suspend fun snapshotForBackup(): List<Book> {
        store.loadFromDisk()
        return store.books.value.toList()
    }

    suspend fun replaceAll(books: List<Book>) {
        store.replaceAll(books)
        auditStore.append(
            AuditEvent.Imported(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                importedCount = books.size,
            )
        )
    }

    data class MergeImportResult(
        val added: Int,
        val skipped: Int,
        val totalAfter: Int,
    )

    /**
     * Add books from a PC export without removing existing rows.
     * A row is skipped when its [Book.id] already exists on the tablet.
     */
    suspend fun mergeImport(incoming: List<Book>): MergeImportResult {
        store.loadFromDisk()
        val existing = store.books.value
        val existingIds = existing.map { it.id }.toSet()
        val toAdd = incoming.filter { it.id !in existingIds }
        if (toAdd.isEmpty()) {
            return MergeImportResult(added = 0, skipped = incoming.size, totalAfter = existing.size)
        }
        val merged = existing + toAdd
        store.replaceAll(merged)
        auditStore.append(
            AuditEvent.Imported(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                importedCount = toAdd.size,
            )
        )
        return MergeImportResult(
            added = toAdd.size,
            skipped = incoming.size - toAdd.size,
            totalAfter = merged.size,
        )
    }

    /** Count how many incoming rows would be added vs skipped (no writes). */
    suspend fun previewMerge(incoming: List<Book>): MergeImportResult {
        store.loadFromDisk()
        val existingIds = store.books.value.map { it.id }.toSet()
        val toAdd = incoming.count { it.id !in existingIds }
        val total = store.books.value.size
        return MergeImportResult(
            added = toAdd,
            skipped = incoming.size - toAdd,
            totalAfter = total + toAdd,
        )
    }

    /**
     * Insert or update a book and audit the change. When [recordAudit] is
     * false the caller is responsible for emitting the right event (e.g.
     * the restore helpers below do their own bookkeeping).
     */
    suspend fun upsert(book: Book, recordAudit: Boolean = true) {
        val existing = store.books.value.firstOrNull { it.id == book.id }
        store.upsert(book)
        if (!recordAudit) return
        val now = System.currentTimeMillis()
        val event: AuditEvent = if (existing == null) {
            AuditEvent.Added(
                id = UUID.randomUUID().toString(),
                timestamp = now,
                bookId = book.id,
                bookName = book.name,
                snapshot = book,
            )
        } else if (existing != book) {
            AuditEvent.Updated(
                id = UUID.randomUUID().toString(),
                timestamp = now,
                bookId = book.id,
                bookName = book.name,
                before = existing,
                after = book,
            )
        } else {
            return
        }
        auditStore.append(event)
    }

    suspend fun delete(id: String, recordAudit: Boolean = true) {
        val existing = store.books.value.firstOrNull { it.id == id }
        store.delete(id)
        if (!recordAudit || existing == null) return
        auditStore.append(
            AuditEvent.Deleted(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                bookId = existing.id,
                bookName = existing.name,
                snapshot = existing,
            )
        )
    }

    suspend fun upsertColor(color: CustomColor) {
        store.upsertColor(color)
    }

    /**
     * Restore a previously-deleted book.
     *
     * Returns true on success. Fails (returns false) if the slot is already
     * taken by another book — the History view-model checks this up front so
     * users see a disabled button instead of a silent failure, but we
     * re-check here to keep the data layer safe under races.
     */
    suspend fun restoreDeleted(event: AuditEvent.Deleted): Boolean {
        if (store.books.value.any { it.id == event.snapshot.id }) return false
        // Treat the restored row as a fresh "Added" event so the history
        // explains where it came from while the original Deleted entry stays
        // around for the audit trail (with restore now disabled).
        upsert(event.snapshot, recordAudit = false)
        auditStore.append(
            AuditEvent.Added(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                bookId = event.snapshot.id,
                bookName = event.snapshot.name,
                snapshot = event.snapshot,
            )
        )
        return true
    }

    /**
     * Roll back an update. Only valid if the catalog row currently matches
     * [event.after] (i.e. nothing changed it again since). Returns true on
     * success.
     */
    suspend fun restoreUpdate(event: AuditEvent.Updated): Boolean {
        val current = store.books.value.firstOrNull { it.id == event.after.id } ?: return false
        if (!current.fieldsEqualIgnoringTimestamps(event.after)) return false
        val restored = event.before.copy(updatedAt = System.currentTimeMillis())
        upsert(restored, recordAudit = false)
        auditStore.append(
            AuditEvent.Updated(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                bookId = restored.id,
                bookName = restored.name,
                before = current,
                after = restored,
            )
        )
        return true
    }
}

/**
 * Equality ignoring [Book.updatedAt] (and other auto-managed timestamps).
 * Used by the restore check so a heartbeat-style touch on `updatedAt`
 * doesn't lock users out of restoring an otherwise unchanged book.
 */
private fun Book.fieldsEqualIgnoringTimestamps(other: Book): Boolean =
    this.copy(updatedAt = 0L, createdAt = 0L) ==
        other.copy(updatedAt = 0L, createdAt = 0L)
