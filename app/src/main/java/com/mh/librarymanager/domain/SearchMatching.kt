package com.mh.librarymanager.domain

import java.util.UUID

/**
 * Direction of a search expansion between a [SearchMatching.shortcut] and its
 * ordered [SearchMatching.words].
 *
 * - [Bidirectional] (default): typing the shortcut also finds the words **and**
 *   typing any of the words also finds the shortcut.
 * - [WordsToShortcut]: one-way only — typing any of the words also finds the
 *   shortcut, but typing the shortcut does **not** pull in the words.
 */
enum class MatchingDirection {
    Bidirectional,
    WordsToShortcut;

    fun toggled(): MatchingDirection =
        if (this == Bidirectional) WordsToShortcut else Bidirectional
}

/**
 * A staff-managed search synonym rule.
 *
 * A [shortcut] (e.g. "פי׳" or "רמב״ם") maps to an ordered list of [words]
 * (e.g. ["פירוש", "פירושי", "פירושים"]). The order is meaningful: earlier
 * words are treated as higher-priority matches, so they surface above later
 * ones in results. Matching is direction-aware, see [MatchingDirection].
 *
 * Persisted in `filesDir/matchings.json`; never embedded in code.
 */
data class SearchMatching(
    val id: String = UUID.randomUUID().toString(),
    val shortcut: String,
    val words: List<String>,
    val direction: MatchingDirection = MatchingDirection.Bidirectional,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
