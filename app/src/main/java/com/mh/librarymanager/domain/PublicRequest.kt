package com.mh.librarymanager.domain

/**
 * A request submitted by a member of the public from the kiosk home screen
 * (e.g. "please add book X" or a note about an existing book).
 *
 * Requests are write-only for the public and managed by staff in the
 * management section, where each one carries a handling [status].
 */
data class PublicRequest(
    val id: String,
    /** Free-text name. Blank means the submitter chose to stay anonymous. */
    val requesterName: String,
    val bookName: String,
    val details: String,
    val status: RequestStatus,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Handling state of a [PublicRequest], shown and editable by staff. */
enum class RequestStatus(val storedValue: String) {
    /** נקלט — received, not yet handled. */
    RECEIVED("received"),

    /** בתהליך — currently being worked on. */
    IN_PROGRESS("in_progress"),

    /** הושלם — done. */
    COMPLETED("completed");

    companion object {
        fun fromStored(value: String?): RequestStatus =
            entries.firstOrNull { it.storedValue == value } ?: RECEIVED
    }
}
