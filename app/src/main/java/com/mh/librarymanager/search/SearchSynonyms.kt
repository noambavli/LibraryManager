package com.mh.librarymanager.search

import com.mh.librarymanager.domain.MatchingDirection
import com.mh.librarymanager.domain.SearchMatching

/**
 * Compiles [SearchMatching] rules into query-time expansions and index-time
 * field augmentation.
 *
 * Query time: a recognised shortcut/word is expanded into ranked alternatives
 * (e.g. "רמבם" also searches for "רבי משה בן מימון").
 *
 * Index time: each catalog field is augmented with its synonym forms so a book
 * labelled "רמב״ם" is also indexed under "רבי משה בן מימון", and the reverse.
 * This makes both directions reliable even when query expansion alone would miss.
 */
class SearchSynonyms private constructor(
    private val rules: List<CompiledRule>,
    private val expansions: List<Expansion>,
) {

    /** A normalised query token sequence plus its priority [rank] (0 = best). */
    class Candidate(val tokens: List<String>, val rank: Int)

    private class CompiledRule(
        val shortcut: String,
        val shortcutTokens: List<String>,
        val words: List<String>,
        val wordTokenLists: List<List<String>>,
        val direction: MatchingDirection,
    )

    private class Expansion(
        val source: List<String>,
        val target: List<String>,
        val rank: Int,
    )

    val isEmpty: Boolean get() = rules.isEmpty()

    /**
     * Appends synonym aliases to a normalised catalog field so cross-form
     * searches hit without relying on query expansion alone.
     */
    fun augmentField(normalizedField: String): String {
        if (normalizedField.isEmpty() || rules.isEmpty()) return normalizedField
        val extras = LinkedHashSet<String>()
        for (rule in rules) {
            // Field contains the shortcut → index all word forms so word searches
            // find this book (always, including one-directional).
            if (rule.shortcut.isNotEmpty() && normalizedField.contains(rule.shortcut)) {
                extras.addAll(rule.words)
            }
            // Field contains a word form → index the shortcut too, but only when
            // bidirectional (one-directional must not let shortcut searches hit
            // word-labelled books).
            if (rule.direction == MatchingDirection.Bidirectional) {
                for (word in rule.words) {
                    if (word.isNotEmpty() && normalizedField.contains(word)) {
                        extras += rule.shortcut
                    }
                }
            }
        }
        if (extras.isEmpty()) return normalizedField
        return buildString {
            append(normalizedField)
            for (extra in extras) {
                append(' ')
                append(extra)
            }
        }
    }

    fun expand(queryTokens: List<String>): List<Candidate> {
        if (queryTokens.isEmpty()) return emptyList()
        val out = ArrayList<Candidate>(4)
        out += Candidate(queryTokens, 0)
        if (expansions.isEmpty()) return out

        val seen = HashSet<String>()
        seen += queryTokens.joinToString(" ")
        for (e in expansions) {
            val match = findMatch(queryTokens, e.source) ?: continue
            val replaced = ArrayList<String>(queryTokens.size - match.consumed + e.target.size)
            replaced.addAll(queryTokens.subList(0, match.start))
            replaced.addAll(e.target)
            replaced.addAll(queryTokens.subList(match.start + match.consumed, queryTokens.size))
            if (!seen.add(replaced.joinToString(" "))) continue
            out += Candidate(replaced, e.rank + 1)
            if (out.size >= MAX_CANDIDATES) break
        }
        return out
    }

    companion object {
        const val MAX_CANDIDATES = 24

        val EMPTY = SearchSynonyms(emptyList(), emptyList())

        fun from(matchings: List<SearchMatching>): SearchSynonyms {
            if (matchings.isEmpty()) return EMPTY
            val compiled = ArrayList<CompiledRule>()
            val list = ArrayList<Expansion>()
            for (m in matchings) {
                val shortcutNorm = HebrewText.normalize(m.shortcut)
                val shortcutTokens = HebrewText.tokens(m.shortcut)
                if (shortcutNorm.isEmpty() || shortcutTokens.isEmpty()) continue

                val wordNorms = m.words
                    .map { HebrewText.normalize(it) }
                    .filter { it.isNotEmpty() }
                val wordTokenLists = m.words
                    .map { HebrewText.tokens(it) }
                    .filter { it.isNotEmpty() }
                if (wordNorms.isEmpty()) continue

                compiled += CompiledRule(
                    shortcut = shortcutNorm,
                    shortcutTokens = shortcutTokens,
                    words = wordNorms,
                    wordTokenLists = wordTokenLists,
                    direction = m.direction,
                )

                for (w in wordTokenLists) {
                    list += Expansion(source = w, target = shortcutTokens, rank = 0)
                }
                if (m.direction == MatchingDirection.Bidirectional) {
                    wordTokenLists.forEachIndexed { index, w ->
                        list += Expansion(source = shortcutTokens, target = w, rank = index)
                    }
                }
            }
            return if (compiled.isEmpty()) EMPTY else SearchSynonyms(compiled, list)
        }

        private fun findMatch(haystack: List<String>, needle: List<String>): Match? {
            val exact = indexOfSubsequence(haystack, needle)
            if (exact >= 0) return Match(exact, needle.size)
            if (needle.size != 1) return null
            val canonical = needle[0]
            if (canonical.length < 2) return null
            for (i in haystack.indices) {
                val token = haystack[i]
                if (token.length < 2) continue
                if (canonical.startsWith(token) || token.startsWith(canonical)) {
                    return Match(i, 1)
                }
            }
            return null
        }

        private data class Match(val start: Int, val consumed: Int)

        private fun indexOfSubsequence(haystack: List<String>, needle: List<String>): Int {
            if (needle.isEmpty() || needle.size > haystack.size) return -1
            outer@ for (i in 0..haystack.size - needle.size) {
                for (j in needle.indices) {
                    if (haystack[i + j] != needle[j]) continue@outer
                }
                return i
            }
            return -1
        }
    }
}
