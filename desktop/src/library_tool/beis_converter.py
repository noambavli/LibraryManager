"""Convert Beis-Midrash xlsx rows into tablet ``Book`` records.

Mirrors the tablet's ``data/xlsx/CatalogImporter.kt`` Beis-Midrash schema and
``WindowsToolCodec.BEIS_HEADERS``:

  * Placeless sheet — the library is decided by the upload, so every row is
    stamped ``place = beis_midrash``.
  * Address columns are עמודה (column / pillar) + מדף (shelf). No letter /
    display-number / category here.
  * Header-driven, forgiving of column order and Hebrew normalisation.

Kept a faithful port so a beis sheet re-imports on the tablet without surprises.
"""

from __future__ import annotations

import time
from dataclasses import dataclass
from typing import Dict, List, Optional

from .hebrew import normalize
from .model import Book, BookPlace, BookState

NAME = "name"
TOPICS = "topics"
WRITER = "writer"
COLUMN = "column"
SHELF = "shelf"
COLOR = "color"
NOTES = "notes"

# Aliases mirror CatalogImporter.HeaderMap.ALIASES (beis subset).
_ALIASES: Dict[str, List[str]] = {
    NAME: ["שם הספר", "שם", "name"],
    TOPICS: ["ענינים", "עניינים", "topics"],
    WRITER: ["המחבר", "מחבר", "writer", "author"],
    COLUMN: ["עמודה", "עמוד", "column", "pillar"],
    SHELF: ["מדף", "shelf"],
    COLOR: ["צבע", "color"],
    NOTES: ["הערות", "הערה", "notes", "note"],
}

BEIS_HEADERS = ["שם הספר", "ענינים", "המחבר", "עמודה", "מדף", "צבע", "הערות"]


@dataclass
class HeaderMap:
    columns: Dict[str, int]

    def get(self, row: List[str], key: str) -> str:
        idx = self.columns.get(key)
        if idx is None or idx < 0 or idx >= len(row):
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
class BeisConvertResult:
    books: List[Book]
    imported: int
    skipped: int
    header_map: HeaderMap

    @property
    def recognised_any(self) -> bool:
        return bool(self.header_map.columns)


def convert_beis_rows(rows: List[List[str]], now_ms: Optional[int] = None) -> BeisConvertResult:
    """Turn parsed beis xlsx rows into ``Book`` objects (place = beis_midrash)."""
    if now_ms is None:
        now_ms = int(time.time() * 1000)

    if not rows:
        return BeisConvertResult([], 0, 0, HeaderMap({}))

    header_map = HeaderMap.from_header(rows[0])
    books: List[Book] = []
    skipped = 0

    for i in range(1, len(rows)):
        row = rows[i]
        name = header_map.get(row, NAME)
        topics = header_map.get(row, TOPICS)
        writer = header_map.get(row, WRITER)
        column = header_map.get(row, COLUMN)
        shelf = header_map.get(row, SHELF)
        color = header_map.get(row, COLOR)
        notes = header_map.get(row, NOTES)

        # Blank rule mirrors the tablet: name/topics/writer/column all empty.
        if not name and not topics and not writer and not column:
            skipped += 1
            continue

        book_id = "book-{:06d}".format(i)
        books.append(
            Book(
                id=book_id,
                logicalBookId=book_id,
                version=1,
                isLatest=True,
                name=name,
                topics=topics,
                writer=writer,
                bookNumber="{:04d}".format(i),
                displayNumber="",
                letter="",
                color=color,
                category="",
                subcategories=[],
                notes=notes,
                place=BookPlace.BEIS_MIDRASH,
                state=BookState.AVAILABLE,
                parentBookId=None,
                relations=[],
                createdAt=now_ms,
                updatedAt=now_ms,
                column=column,
                shelf=shelf,
            )
        )

    return BeisConvertResult(
        books=books,
        imported=len(books),
        skipped=skipped,
        header_map=header_map,
    )


def beis_books_to_rows(books: List[Book]) -> List[List[str]]:
    rows = [BEIS_HEADERS]
    for book in books:
        rows.append([
            book.name,
            book.topics,
            book.writer,
            book.column,
            book.shelf,
            book.color,
            book.notes,
        ])
    return rows
