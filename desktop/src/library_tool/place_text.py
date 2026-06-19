"""Free-text book place — mirrors ``BookPlaceText`` on the tablet."""

from __future__ import annotations

import re

OTZAR_LABEL = "אוצר הספרים"
BEIS_MIDRASH_LABEL = "בית מדרש"

_COLLAPSE_SPACES = re.compile(r"\s+")


def normalize(raw: str) -> str:
    return _COLLAPSE_SPACES.sub(" ", (raw or "").strip())


def from_stored(stored: str | None) -> str:
    if not stored or not stored.strip():
        return ""
    s = stored.strip()
    if s in ("otzar",):
        return OTZAR_LABEL
    if s in ("beis_midrash",):
        return BEIS_MIDRASH_LABEL
    if s == "other":
        return ""
    return normalize(s)


def is_blank(place: str) -> bool:
    return not normalize(place)
