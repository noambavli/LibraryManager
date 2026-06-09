"""Hebrew-aware text normalisation, ported from ``search/HebrewText.kt``.

Used only for header matching during import, so that the upstream sheet may use
nikud, final-letter forms, or punctuation in its column titles and still map to
the right field — exactly like the tablet does.
"""

from __future__ import annotations

# Final (sofit) letters folded to their normal form, mirroring HebrewText.kt.
_FINAL_LETTERS = {
    "\u05da": "\u05db",  # ך -> כ
    "\u05dd": "\u05de",  # ם -> מ
    "\u05df": "\u05e0",  # ן -> נ
    "\u05e3": "\u05e4",  # ף -> פ
    "\u05e5": "\u05e6",  # ץ -> צ
}

_DROP_QUOTES = {"'", '"', "\u2019", "\u2018", "\u201c", "\u201d"}

_SPACE_PUNCT = set(" \t\n\r-.,()[]/\\:;!?\u05be")  # include Hebrew maqaf ־


def normalize(text: str | None) -> str:
    """Lower-case, strip nikud/cantillation, fold final letters, and collapse
    punctuation to single spaces. Matches HebrewText.normalize byte-for-byte for
    the inputs we care about (header strings)."""
    if not text:
        return ""
    out: list[str] = []
    last_was_space = True
    for raw in text:
        c = raw.lower()
        code = ord(c)

        # Hebrew nikud / cantillation marks (maqaf U+05BE is punctuation, not nikud).
        if 0x0591 <= code <= 0x05C7 and code != 0x05BE:
            continue
        # LRM / RLM directional marks.
        if 0x200E <= code <= 0x200F:
            continue
        # Hebrew geresh / gershayim.
        if code in (0x05F3, 0x05F4):
            continue
        if c in _DROP_QUOTES:
            continue

        folded = _FINAL_LETTERS.get(c, c)
        fcode = ord(folded)

        is_hebrew = 0x05D0 <= fcode <= 0x05EA
        is_digit = folded.isdigit()
        is_ascii_letter = "a" <= folded <= "z"

        if is_hebrew or is_digit or is_ascii_letter:
            out.append(folded)
            last_was_space = False
        elif folded.isspace() or folded in _SPACE_PUNCT:
            if not last_was_space:
                out.append(" ")
                last_was_space = True
        # else: drop anything else entirely.

    result = "".join(out)
    return result.strip()


def normalize_number_key(value: str | None) -> str:
    """Mirror ``HebrewText.normalizeNumberKey`` on the tablet."""
    trimmed = (value or "").strip()
    if not trimmed:
        return ""
    if trimmed.isdigit():
        stripped = trimmed.lstrip("0")
        return stripped or "0"
    return normalize(trimmed)
