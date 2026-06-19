"""Backups and restore points for the working Excel catalog."""

from __future__ import annotations

import json
import os
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from typing import List, Optional

from . import APP_NAME
from . import strings_he as S
from .model import Book

MAX_BACKUPS = 50
BACKUP_EXTENSION = ".json"

KIND_IMPORT = "import"
KIND_EXPORT = "export"
KIND_DELETE_ALL = "delete_all"
KIND_MANUAL = "manual"


def app_data_dir() -> str:
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
            KIND_IMPORT: S.BACKUP_KIND_IMPORT,
            KIND_EXPORT: S.BACKUP_KIND_EXPORT,
            KIND_DELETE_ALL: S.BACKUP_KIND_DELETE,
            KIND_MANUAL: S.BACKUP_KIND_MANUAL,
        }.get(self.kind, self.kind)
        return S.BACKUP_LABEL.format(when=self.when, kind=nice_kind, n=self.book_count)


def _write_backup_file(path: str, books: List[Book]) -> None:
    payload = {"books": [book.to_json() for book in books]}
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=2)


def _read_backup_file(path: str) -> List[Book]:
    with open(path, "r", encoding="utf-8") as fh:
        payload = json.load(fh)
    return [Book.from_json(item) for item in payload.get("books", [])]


def create_backup(books: List[Book], kind: str, source: str = "") -> BackupEntry:
    ts = int(time.time() * 1000)
    stamp = datetime.fromtimestamp(ts / 1000).strftime("%Y%m%d-%H%M%S")
    fname = f"{stamp}-{kind}{BACKUP_EXTENSION}"
    target = os.path.join(backups_dir(), fname)
    n = 1
    while os.path.exists(target):
        target = os.path.join(backups_dir(), f"{stamp}-{kind}-{n}{BACKUP_EXTENSION}")
        n += 1

    _write_backup_file(target, books)
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
    out: List[BackupEntry] = []
    d = backups_dir()
    for name in os.listdir(d):
        if not name.endswith(BACKUP_EXTENSION):
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


def restore_backup(entry: BackupEntry) -> List[Book]:
    return _read_backup_file(entry.path)


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
