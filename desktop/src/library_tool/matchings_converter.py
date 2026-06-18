"""Spreadsheet ↔ search-matchings conversion — mirrors ``WindowsToolCodec.kt``."""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import List

from .hebrew import normalize
from .model import Matching, MatchingDirection

MATCHING_HEADERS = ["קיצור", "מילים", "כיוון"]


@dataclass
class MatchingsConvertResult:
    matchings: List[Matching]
    invalid: int


def matchings_to_rows(matchings: List[Matching]) -> List[List[str]]:
    rows: List[List[str]] = [MATCHING_HEADERS]
    for m in matchings:
        rows.append([
            m.shortcut,
            ", ".join(m.words),
            _direction_label(m.direction),
        ])
    return rows


def rows_to_matchings(rows: List[List[str]]) -> MatchingsConvertResult:
    if not rows:
        return MatchingsConvertResult([], 0)

    header = [normalize(c) for c in rows[0]]
    shortcut_col = _col_index(header, {"קיצור", "shortcut"})
    words_col = _col_index(header, {"מילים", "מילה", "words", "word"})
    direction_col = _col_index(header, {"כיוון", "direction"})

    has_header = shortcut_col >= 0 or words_col >= 0
    s_col = shortcut_col if shortcut_col >= 0 else 0
    w_col = words_col if words_col >= 0 else 1
    d_col = direction_col
    start = 1 if has_header else 0

    by_key: dict[str, Matching] = {}
    invalid = 0
    for i in range(start, len(rows)):
        row = rows[i]
        shortcut = _cell(row, s_col).strip()
        words_raw = _cell(row, w_col)
        words = _split_words(words_raw)
        if not shortcut or not words:
            invalid += 1
            continue
        direction_raw = _cell(row, d_col) if d_col >= 0 else ""
        by_key[shortcut.lower()] = Matching(
            shortcut=shortcut,
            words=words,
            direction=_parse_direction(direction_raw),
        )

    return MatchingsConvertResult(list(by_key.values()), invalid)


def _col_index(header: List[str], aliases: set[str]) -> int:
    norm_aliases = {normalize(a) for a in aliases}
    for idx, cell in enumerate(header):
        if cell in norm_aliases:
            return idx
    return -1


def _cell(row: List[str], idx: int) -> str:
    if idx < 0 or idx >= len(row):
        return ""
    return row[idx] or ""


def _split_words(raw: str) -> List[str]:
    parts = re.split(r"[,;|/\n\r\t]+", raw or "")
    return [p.strip() for p in parts if p.strip()]


def _parse_direction(raw: str) -> str:
    n = normalize(raw)
    if "חד" in n or "one" in n or "word" in n:
        return MatchingDirection.WORDS_TO_SHORTCUT
    return MatchingDirection.BIDIRECTIONAL


def _direction_label(direction: str) -> str:
    if direction == MatchingDirection.WORDS_TO_SHORTCUT:
        return "חד-כיווני"
    return "דו-כיווני"
