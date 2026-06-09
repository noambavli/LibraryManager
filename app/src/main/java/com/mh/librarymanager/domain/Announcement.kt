package com.mh.librarymanager.domain

/**
 * A staff-authored announcement shown to the public on the home screen.
 *
 * An announcement is visible for [durationDays] days after [createdAt]; after
 * that it expires and drops off the public surfaces (but staff can still see
 * and delete it in management). It can optionally reference catalog books via
 * [linkedBookIds], which are rendered as cards on the full-announcement view.
 */
data class Announcement(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    /** Number of days the announcement stays visible to the public (>= 1). */
    val durationDays: Int,
    val linkedBookIds: List<String>,
) {
    fun expiresAt(): Long = createdAt + durationDays.toLong() * DAY_MS

    fun isActive(now: Long = System.currentTimeMillis()): Boolean =
        now < expiresAt()

    companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val DEFAULT_DURATION_DAYS = 7
        const val MAX_DURATION_DAYS = 365
    }
}
