"""Backups, restore points, and the delete-all safety net.

Every operation that changes the working catalog first snapshots the previous
state into a timestamped backup, so:

  * **Restore after import** — if the user imports a new sheet and hasn't made
    further changes, they can revert to exactly what was there before.
  * **Delete-all is reversible** — "delete everything" really writes an empty
    catalog, but the pre-delete state is snapshotted and can be restored.

Backups live in a per-user app-data folder (so they survive even if the working
folder is cleaned up) and are pruned to a sane maximum. Each backup carries a
small JSON sidecar describing what it was (kind, book count, source).
"""

from __future__ import annotations

import json
import os
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from typing import List, Optional

from . import APP_NAME, civ

MAX_BACKUPS = 50

KIND_IMPORT = "import"          # snapshot of catalog *before* an import replaced it
KIND_EXPORT = "export"          # snapshot taken right before exporting to a tablet
KIND_DELETE_ALL = "delete_all"  # snapshot taken before wiping everything
KIND_MANUAL = "manual"          # user-requested checkpoint


def app_data_dir() -> str:
    """A writable, per-user data directory that works on Windows, macOS, Linux."""
    if os.name == "nt":
        base = os.environ.get("APPDATA") or os.path.expanduser("~")
    elif sys.platform == "darwin":
        base = os.path.expanduser("~/Library/Application Support")
    else:
        base = os.environ.get("XDG_DATA_HOME") or os.path.expanduser("~/.local/share")
    path = os.path.join(base, APP_NAME)
    os.makedirs(path, exist_ok=True)
    return path


def backups_dir() -> str:
    path = os.path.join(app_data_dir(), "backups")
    os.makedirs(path, exist_ok=True)
    return path


@dataclass
class BackupEntry:
    path: str
    meta_path: str
    timestamp: int
    kind: str
    book_count: int
    source: str

    @property
    def when(self) -> str:
        return datetime.fromtimestamp(self.timestamp / 1000).strftime("%Y-%m-%d %H:%M:%S")

    def label(self) -> str:
        nice_kind = {
            KIND_IMPORT: "Before import",
            KIND_EXPORT: "Before export",
            KIND_DELETE_ALL: "Before delete-all",
            KIND_MANUAL: "Manual checkpoint",
        }.get(self.kind, self.kind)
        return f"{self.when} · {nice_kind} · {self.book_count} books"


def create_backup(books: List, kind: str, source: str = "") -> BackupEntry:
    """Snapshot ``books`` as a timestamped backup and return its entry."""
    ts = int(time.time() * 1000)
    stamp = datetime.fromtimestamp(ts / 1000).strftime("%Y%m%d-%H%M%S")
    fname = f"{stamp}-{kind}{civ.CIV_EXTENSION}"
    target = os.path.join(backups_dir(), fname)
    # Avoid clobbering if two backups land in the same second.
    n = 1
    while os.path.exists(target):
        target = os.path.join(backups_dir(), f"{stamp}-{kind}-{n}{civ.CIV_EXTENSION}")
        n += 1

    civ.write_file(target, books)
    meta = {
        "timestamp": ts,
        "kind": kind,
        "book_count": len(books),
        "source": source,
    }
    meta_path = target + ".meta.json"
    with open(meta_path, "w", encoding="utf-8") as fh:
        json.dump(meta, fh, ensure_ascii=False, indent=2)

    _prune()
    return BackupEntry(
        path=target,
        meta_path=meta_path,
        timestamp=ts,
        kind=kind,
        book_count=len(books),
        source=source,
    )


def list_backups() -> List[BackupEntry]:
    """All backups, newest first."""
    out: List[BackupEntry] = []
    d = backups_dir()
    for name in os.listdir(d):
        if not name.endswith(civ.CIV_EXTENSION):
            continue
        path = os.path.join(d, name)
        meta_path = path + ".meta.json"
        ts = int(os.path.getmtime(path) * 1000)
        kind = "unknown"
        count = 0
        source = ""
        if os.path.exists(meta_path):
            try:
                with open(meta_path, "r", encoding="utf-8") as fh:
                    meta = json.load(fh)
                ts = int(meta.get("timestamp", ts))
                kind = meta.get("kind", kind)
                count = int(meta.get("book_count", 0))
                source = meta.get("source", "")
            except (OSError, ValueError, json.JSONDecodeError):
                pass
        out.append(BackupEntry(path, meta_path, ts, kind, count, source))
    out.sort(key=lambda e: e.timestamp, reverse=True)
    return out


def restore_backup(entry: BackupEntry):
    """Load the books stored in a backup."""
    return civ.read_file(entry.path).books


def latest_backup() -> Optional[BackupEntry]:
    entries = list_backups()
    return entries[0] if entries else None


def _prune():
    entries = list_backups()
    for stale in entries[MAX_BACKUPS:]:
        for p in (stale.path, stale.meta_path):
            try:
                if os.path.exists(p):
                    os.remove(p)
            except OSError:
                pass
