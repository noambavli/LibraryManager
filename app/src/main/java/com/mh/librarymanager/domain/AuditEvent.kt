package com.mh.librarymanager.domain

/**
 * One entry in the management history log.
 *
 * Every mutation flowing through `BookRepository` records an [AuditEvent].
 * Bulk imports record a single [Imported] event; individual edits record
 * [Added], [Updated] or [Deleted]. Each entry carries the data needed to
 * either describe or reverse the change, so the History screen can render
 * a human-readable timeline and offer a Restore action when safe.
 *
 * "Safe to restore" rules live in the History view-model — not on the
 * event itself — because they depend on the current catalog state.
 */
sealed interface AuditEvent {
    val id: String
    val timestamp: Long
    val bookId: String
    val bookName: String
    val kind: AuditKind

    data class Added(
        override val id: String,
        override val timestamp: Long,
        override val bookId: String,
        override val bookName: String,
        /** Full snapshot of the book at the moment it was added. */
        val snapshot: Book,
    ) : AuditEvent {
        override val kind: AuditKind get() = AuditKind.ADDED
    }

    data class Updated(
        override val id: String,
        override val timestamp: Long,
        override val bookId: String,
        override val bookName: String,
        val before: Book,
        val after: Book,
    ) : AuditEvent {
        override val kind: AuditKind get() = AuditKind.UPDATED
    }

    data class Deleted(
        override val id: String,
        override val timestamp: Long,
        override val bookId: String,
        override val bookName: String,
        /** Full snapshot of the book that was removed; required for restore. */
        val snapshot: Book,
    ) : AuditEvent {
        override val kind: AuditKind get() = AuditKind.DELETED
    }

    /**
     * Bulk catalog replacement (xlsx import or a future "reset to file" tool).
     * Not individually restorable but worth showing in the timeline.
     */
    data class Imported(
        override val id: String,
        override val timestamp: Long,
        val importedCount: Int,
    ) : AuditEvent {
        override val bookId: String get() = ""
        override val bookName: String get() = ""
        override val kind: AuditKind get() = AuditKind.IMPORTED
    }
}

enum class AuditKind { ADDED, UPDATED, DELETED, IMPORTED }
