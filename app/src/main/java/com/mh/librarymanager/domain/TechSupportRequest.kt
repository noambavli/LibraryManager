package com.mh.librarymanager.domain

/**
 * A technical problem report submitted from the public home "help" button.
 * Staff review these in the management section; there is no public status flow.
 */
data class TechSupportRequest(
    val id: String,
    val reporterName: String,
    val problem: String,
    val createdAt: Long,
)
