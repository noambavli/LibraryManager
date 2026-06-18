package com.mh.librarymanager.domain

import com.mh.librarymanager.search.HebrewText

/** A shelf address: Hebrew letter + numeric position on that letter's run. */
data class ShelfSlot(val letter: String, val number: Int) {
    fun isValid(): Boolean = HebrewShelfOrder.letterIndex(letter) >= 0 && number > 0
}

object HebrewShelfOrder {
  /** Standard Hebrew alphabet shelf order (א through ת). */
    private const val ORDER = "אבגדהוזחטיכלמנסעפצקרשת"

    fun letterIndex(letter: String): Int {
        val normalized = HebrewText.normalize(letter).trim()
        if (normalized.isEmpty()) return -1
        return ORDER.indexOf(normalized.first())
    }

    /** Monotonic key for range comparisons: letter order first, then number. */
    fun slotKey(letter: String, number: String): Long {
        val index = letterIndex(letter)
        val num = HebrewText.normalizeNumberKey(number).toIntOrNull()
        if (index < 0 || num == null || num <= 0) return Long.MIN_VALUE
        return index.toLong() * 100_000L + num
    }

    fun slotKey(slot: ShelfSlot): Long = slotKey(slot.letter, slot.number.toString())

    fun isInRange(slot: ShelfSlot, from: ShelfSlot, to: ShelfSlot): Boolean {
        if (!slot.isValid() || !from.isValid() || !to.isValid()) return false
        val key = slotKey(slot)
        val fromKey = slotKey(from)
        val toKey = slotKey(to)
        return key in fromKey..toKey
    }
}

object MapColorLabels {
    private val aliases = mapOf(
        "orange" to "כתום",
        "כתום" to "כתום",
        "blue" to "כחול",
        "כחול" to "כחול",
        "green" to "ירוק",
        "ירוק" to "ירוק",
        "yellow" to "צהוב",
        "צהוב" to "צהוב",
        "purple" to "סגול",
        "סגול" to "סגול",
        "red" to "אדום",
        "אדום" to "אדום",
        "pink" to "ורוד",
        "ורוד" to "ורוד",
        "brown" to "חום",
        "חום" to "חום",
        "gray" to "אפור",
        "grey" to "אפור",
        "אפור" to "אפור",
        "white" to "לבן",
        "לבן" to "לבן",
    )

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        aliases[trimmed.lowercase()]?.let { return it }
        val out = StringBuilder()
        for (ch in trimmed) {
            if (ch.code in 0x0591..0x05C7) continue
            if (ch.code in 0x200E..0x200F) continue
            out.append(ch)
        }
        return out.toString()
    }

    fun matches(storedBookColor: String, sectionColor: String): Boolean {
        val book = normalize(storedBookColor)
        val section = normalize(sectionColor)
        return book.isNotEmpty() && book == section
    }
}
