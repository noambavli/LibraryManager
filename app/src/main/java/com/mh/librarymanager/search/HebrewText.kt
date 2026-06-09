package com.mh.librarymanager.search

/**
 * Normalises Hebrew text so that searches behave intuitively:
 *
 * - Strips nikud / cantillation marks (U+0591..U+05C7).
 * - Folds final letters to their base form (ך→כ, ם→מ, ן→נ, ף→פ, ץ→צ).
 * - Removes geresh / gershayim and ASCII quotes (’ ‘ ׳ ״ " ').
 * - Removes common punctuation and collapses whitespace.
 * - Lower-cases ASCII so mixed Hebrew/English fields work.
 *
 * The output keeps Hebrew letters, digits and single ASCII spaces, which is the
 * canonical form the search engine indexes on both sides of every comparison.
 */
object HebrewText {

    private val finalLetters = mapOf(
        'ך' to 'כ',
        'ם' to 'מ',
        'ן' to 'נ',
        'ף' to 'פ',
        'ץ' to 'צ',
    )

    fun normalize(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        val out = StringBuilder(input.length)
        var lastWasSpace = true
        for (raw in input) {
            val c = raw.lowercaseChar()
            val code = c.code

            // U+05BE (maqaf ־) lives inside the nikud block but is word punctuation.
            if (code in 0x0591..0x05C7 && code != 0x05BE) continue
            if (code in 0x200E..0x200F) continue
            if (code == 0x05F3 || code == 0x05F4) continue
            if (c == '\'' || c == '"' || c == '’' || c == '‘' || c == '“' || c == '”') continue

            val folded = finalLetters[c] ?: c

            val isHebrew = code in 0x05D0..0x05EA
            val isDigit = folded.isDigit()
            val isAsciiLetter = folded in 'a'..'z'

            if (isHebrew || isDigit || isAsciiLetter) {
                out.append(folded)
                lastWasSpace = false
            } else if (folded.isWhitespace() || folded == '-' || code == 0x05BE || folded == '.' || folded == ',' ||
                folded == '(' || folded == ')' || folded == '[' || folded == ']' ||
                folded == '/' || folded == '\\' || folded == ':' || folded == ';' ||
                folded == '!' || folded == '?'
            ) {
                if (!lastWasSpace) {
                    out.append(' ')
                    lastWasSpace = true
                }
            }
        }
        while (out.isNotEmpty() && out.last() == ' ') out.deleteCharAt(out.length - 1)
        return out.toString()
    }

    /** Normalised whitespace-separated tokens. Empty result for empty input. */
    fun tokens(input: String?): List<String> {
        val n = normalize(input)
        if (n.isEmpty()) return emptyList()
        return n.split(' ').filter { it.isNotEmpty() }
    }

    /**
     * Canonical form for shelf/system numbers. Pure digits strip leading zeros
     * (`05` → `5`); everything else goes through [normalize].
     */
    fun normalizeNumberKey(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        if (trimmed.all { it.isDigit() }) {
            val stripped = trimmed.trimStart('0')
            return stripped.ifEmpty { "0" }
        }
        return normalize(trimmed)
    }

    /** Single token for a dedicated number search field. */
    fun numberTokens(input: String?): List<String> {
        val key = normalizeNumberKey(input)
        return if (key.isEmpty()) emptyList() else listOf(key)
    }
}
