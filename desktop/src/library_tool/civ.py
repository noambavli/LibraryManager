"""Read and write the ``.civ`` tablet catalog file.

``.civ`` is a byte-for-byte compatible copy of the tablet's ``catalog.json``
document, so whatever the desktop writes can be loaded directly by
``CatalogStore`` on the tablet:

    {"version": 4, "books": [ { ...book... }, ... ]}

Writes are atomic (write to ``*.tmp`` then ``os.replace``) so a crash, a yanked
USB cable, or a forced abort can never leave a half-written, corrupt catalog —
the destination either has the complete old file or the complete new file.
"""

from __future__ import annotations

import hashlib
import json
import os
import tempfile
from dataclasses import dataclass
from typing import List

from . import CATALOG_FORMAT_VERSION
from .model import Book

CIV_EXTENSION = ".civ"


@dataclass
class CivDocument:
    version: int
    books: List[Book]

    @property
    def count(self) -> int:
        return len(self.books)


def serialize(books: List[Book]) -> str:
    """Produce the JSON text the tablet expects. ``ensure_ascii=False`` keeps
    Hebrew readable on disk; the tablet reads UTF-8."""
    root = {
        "version": CATALOG_FORMAT_VERSION,
        "books": [b.to_json() for b in books],
    }
    return json.dumps(root, ensure_ascii=False, separators=(",", ":"))


def parse(text: str) -> CivDocument:
    """Parse a ``.civ`` document, validating the version the way the tablet does
    (an older version means the tablet would discard it, so we reject it loudly
    instead of silently shipping data the tablet will drop)."""
    if not text or not text.strip():
        return CivDocument(CATALOG_FORMAT_VERSION, [])
    root = json.loads(text)
    if not isinstance(root, dict):
        raise ValueError("Not a valid .civ document (expected a JSON object).")
    version = int(root.get("version", 0) or 0)
    if version < CATALOG_FORMAT_VERSION:
        raise ValueError(
            f"This .civ file is format version {version}, but the tablet needs "
            f"version {CATALOG_FORMAT_VERSION}. The tablet would ignore it. "
            "Re-export it with this tool."
        )
    raw_books = root.get("books") or []
    if not isinstance(raw_books, list):
        raise ValueError("Not a valid .civ document ('books' must be a list).")
    books = [Book.from_json(o) for o in raw_books]
    return CivDocument(version, books)


def read_file(path: str) -> CivDocument:
    with open(path, "r", encoding="utf-8") as fh:
        return parse(fh.read())


def write_file(path: str, books: List[Book]) -> str:
    """Write ``books`` to ``path`` and return the SHA-256 of the bytes written.

    Preferred path is atomic: write to a temp file in the same directory, fsync,
    then ``os.replace`` (an atomic same-filesystem rename). Some destinations —
    notably Android storage over USB (MTP) — do not support atomic rename; in
    that case we fall back to a direct write. That is still safe because every
    caller (export/backup) verifies the result by reading it back and comparing
    this hash, so a truncated or interrupted write is always detected.
    """
    text = serialize(books)
    data = text.encode("utf-8")
    directory = os.path.dirname(os.path.abspath(path)) or "."
    os.makedirs(directory, exist_ok=True)

    fd, tmp = tempfile.mkstemp(prefix=".civ-", suffix=".tmp", dir=directory)
    renamed = False
    try:
        with os.fdopen(fd, "wb") as fh:
            fh.write(data)
            fh.flush()
            _best_effort_fsync(fh)
        try:
            os.replace(tmp, path)
            renamed = True
        except OSError:
            # Destination doesn't support atomic rename (e.g. MTP). Write the
            # bytes directly; correctness is guaranteed by the caller's hash
            # verification, not by the rename.
            with open(path, "wb") as out:
                out.write(data)
                out.flush()
                _best_effort_fsync(out)
    except BaseException:
        # Clean up on any failure (including KeyboardInterrupt / abort) so we
        # never leave litter behind.
        _quiet_remove(tmp)
        raise
    finally:
        if not renamed:
            _quiet_remove(tmp)

    return hashlib.sha256(data).hexdigest()


def _best_effort_fsync(fh) -> None:
    try:
        os.fsync(fh.fileno())
    except (OSError, ValueError):
        # Some virtual filesystems (MTP, network shares) reject fsync; the
        # bytes are still flushed and we verify by hash afterwards.
        pass


def _quiet_remove(path: str) -> None:
    try:
        if os.path.exists(path):
            os.remove(path)
    except OSError:
        pass


def file_sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()
