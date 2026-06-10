"""The book data model, kept in lock-step with the tablet's Kotlin model.

Source of truth on the tablet:
  * ``domain/Book.kt``         — the book record
  * ``domain/BookPlace.kt``    — stored place values
  * ``domain/BookState.kt``    — stored state values
  * ``data/store/CatalogStore.kt`` — JSON serialisation (catalog.json v4)

Any change here must mirror those files or the tablet will silently drop fields.
"""

from __future__ import annotations

from dataclasses import dataclass, field, replace
from typing import List, Optional


class BookPlace:
    """Mirrors ``domain/BookPlace.kt`` stored values."""

    UNSPECIFIED = ""
    OTZAR = "otzar"
    BEIS_MIDRASH = "beis_midrash"
    OTHER = "other"

    ALL = (UNSPECIFIED, OTZAR, BEIS_MIDRASH, OTHER)

    @classmethod
    def from_stored(cls, value: Optional[str]) -> str:
        v = value or ""
        return v if v in cls.ALL else cls.UNSPECIFIED


class BookState:
    """Mirrors ``domain/BookState.kt`` stored values."""

    AVAILABLE = "available"
    UNAVAILABLE = "unavailable"
    IN_REPAIR = "in_repair"

    ALL = (AVAILABLE, UNAVAILABLE, IN_REPAIR)

    @classmethod
    def from_stored(cls, value: Optional[str]) -> str:
        return value if value in cls.ALL else cls.AVAILABLE


@dataclass
class Book:
    """A single catalog row. Field names match the tablet JSON keys exactly."""

    id: str
    logicalBookId: str
    version: int
    isLatest: bool

    name: str
    topics: str
    writer: str
    bookNumber: str
    displayNumber: str
    letter: str
    color: str
    category: str
    subcategories: List[str]
    notes: str

    place: str
    state: str
    parentBookId: Optional[str]
    parentBookName: str = ""
    relations: List[str]

    createdAt: int
    updatedAt: int

    def to_json(self) -> dict:
        """Serialise to the exact shape CatalogStore.writeBooks produces."""
        return {
            "id": self.id,
            "logicalBookId": self.logicalBookId,
            "version": self.version,
            "isLatest": self.isLatest,
            "name": self.name,
            "topics": self.topics,
            "writer": self.writer,
            "bookNumber": self.bookNumber,
            "displayNumber": self.displayNumber,
            "letter": self.letter,
            "color": self.color,
            "category": self.category,
            "subcategories": list(self.subcategories),
            "notes": self.notes,
            "place": self.place,
            "state": self.state,
            # The tablet stores an empty string (never null) for "no parent".
            "parentBookId": self.parentBookId or "",
            "parentBookName": self.parentBookName,
            "relations": list(self.relations),
            "createdAt": self.createdAt,
            "updatedAt": self.updatedAt,
        }

    @classmethod
    def from_json(cls, o: dict) -> "Book":
        """Parse the shape CatalogStore.readBooks expects, forgiving missing keys
        exactly the way the tablet's ``optString``/``optInt`` helpers do."""
        book_id = str(o.get("id"))
        parent = o.get("parentBookId") or ""
        return cls(
            id=book_id,
            logicalBookId=str(o.get("logicalBookId") or book_id),
            version=int(o.get("version", 1) or 1),
            isLatest=bool(o.get("isLatest", True)),
            name=str(o.get("name") or ""),
            topics=str(o.get("topics") or ""),
            writer=str(o.get("writer") or ""),
            bookNumber=str(o.get("bookNumber") or ""),
            displayNumber=str(o.get("displayNumber") or ""),
            letter=str(o.get("letter") or ""),
            color=str(o.get("color") or ""),
            category=str(o.get("category") or ""),
            subcategories=[str(x) for x in (o.get("subcategories") or [])],
            notes=str(o.get("notes") or ""),
            place=BookPlace.from_stored(o.get("place")),
            state=BookState.from_stored(o.get("state")),
            parentBookId=parent or None,
            parentBookName=str(o.get("parentBookName") or ""),
            relations=[str(x) for x in (o.get("relations") or [])],
            createdAt=int(o.get("createdAt", 0) or 0),
            updatedAt=int(o.get("updatedAt", 0) or 0),
        )

    def copy(self, **changes) -> "Book":
        return replace(self, **changes)
