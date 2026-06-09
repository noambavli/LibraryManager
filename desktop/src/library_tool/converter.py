"""Convert imported xlsx rows into tablet ``Book`` records.

This is a faithful port of ``data/xlsx/CatalogImporter.kt``:

  * Mapping is driven by the header row, not column position, so the upstream
    sheet may reorder columns freely.
  * Hebrew headers are matched after normalisation (nikud / final letters /
    punctuation forgiven). Unknown columns are ignored.
  * Each row becomes version 1 of a new logical book. IDs are deterministic
    (``book-000001`` ...) and the system ``bookNumber`` is the 4-digit row index,
    matching the tablet so re-imports stay stable and diffable.

The ``now`` timestamp is injected so conversions are reproducible in tests and so
a single import shares one creation time across all rows (matching the tablet).
"""

from __future__ import annotations

import time
from dataclasses import dataclass
from typing import Dict, List, Optional

from .hebrew import normalize
from .model import Book, BookPlace, BookState

# Header field keys.
NAME = "name"
TOPICS = "topics"
WRITER = "writer"
NUMBER = "number"
LETTER = "letter"
COLOR = "color"
CATEGORY = "category"
SUBCATEGORY = "subcategory"
NOTES = "notes"

# Aliases mirror CatalogImporter.HeaderMap.ALIASES.
_ALIASES: Dict[str, List[str]] = {
    NAME: ["שם הספר", "שם", "name"],
    TOPICS: ["ענינים", "עניינים", "topics"],
    WRITER: ["המחבר", "מחבר", "writer", "author"],
    NUMBER: ["מספר", "number"],
    LETTER: ["אות", "letter"],
    COLOR: ["צבע", "color"],
    CATEGORY: ["קטגוריה", "category"],
    SUBCATEGORY: ["תת קטגוריה", "תת-קטגוריה", "subcategory", "subcategories"],
    NOTES: ["הערות", "הערה", "notes", "note"],
}


@dataclass
class HeaderMap:
    columns: Dict[str, int]

    def get(self, row: List[str], key: str) -> str:
        idx = self.columns.get(key)
        if idx is None:
            return ""
        if idx < 0 or idx >= len(row):
            return ""
        return (row[idx] or "").strip()

    @property
    def recognised_fields(self) -> List[str]:
        return list(self.columns.keys())

    @classmethod
    def from_header(cls, header: List[str]) -> "HeaderMap":
        normalised_header = [normalize((h or "").strip()) for h in header]
        columns: Dict[str, int] = {}
        for key, aliases in _ALIASES.items():
            norm_aliases = {normalize(a) for a in aliases}
            for idx, h in enumerate(normalised_header):
                if h in norm_aliases:
                    columns[key] = idx
                    break
        return cls(columns)


@dataclass
class ConvertResult:
    books: List[Book]
    imported: int
    skipped: int
    header_map: HeaderMap
    missing_fields: List[str]

    @property
    def recognised_any(self) -> bool:
        return bool(self.header_map.columns)


def convert_rows(rows: List[List[str]], now_ms: Optional[int] = None) -> ConvertResult:
    """Turn parsed xlsx rows into ``Book`` objects, mirroring the tablet importer."""
    if now_ms is None:
        now_ms = int(time.time() * 1000)

    if not rows:
        return ConvertResult([], 0, 0, HeaderMap({}), list(_ALIASES.keys()))

    header = rows[0]
    header_map = HeaderMap.from_header(header)

    books: List[Book] = []
    skipped = 0

    for i in range(1, len(rows)):
        row = rows[i]
        name = header_map.get(row, NAME)
        topics = header_map.get(row, TOPICS)
        writer = header_map.get(row, WRITER)
        number = header_map.get(row, NUMBER)
        letter = header_map.get(row, LETTER)
        color = header_map.get(row, COLOR)
        category = header_map.get(row, CATEGORY)
        subcategory = header_map.get(row, SUBCATEGORY)
        notes = header_map.get(row, NOTES)

        # Same skip rule as the tablet: a row with no name/topics/writer/number
        # is treated as blank padding.
        if not name and not topics and not writer and not number:
            skipped += 1
            continue

        book_id = "book-{:06d}".format(i)
        system_number = "{:04d}".format(i)
        books.append(
            Book(
                id=book_id,
                logicalBookId=book_id,
                version=1,
                isLatest=True,
                name=name,
                topics=topics,
                writer=writer,
                bookNumber=system_number,
                displayNumber=number,
                letter=letter,
                color=color,
                category=category,
                subcategories=[] if not subcategory else [subcategory],
                notes=notes,
                place=BookPlace.OTZAR,
                state=BookState.AVAILABLE,
                parentBookId=None,
                relations=[],
                createdAt=now_ms,
                updatedAt=now_ms,
            )
        )

    missing = [k for k in _ALIASES.keys() if k not in header_map.columns]
    return ConvertResult(
        books=books,
        imported=len(books),
        skipped=skipped,
        header_map=header_map,
        missing_fields=missing,
    )
