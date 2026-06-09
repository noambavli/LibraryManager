"""Exporting the catalog to the tablet over USB-C, safely.

Reality check on Android + USB-C: a tablet exposes its storage over **MTP**, not
as a normal drive letter, and an app's *private* ``catalog.json`` is not writable
over USB at all. So "transfer" here means: write a verified ``.civ`` file to a
destination the user can reach — the tablet's MTP storage (visible in Explorer),
a USB stick, or the tablet's Download/Documents folder — from where the tablet
app imports it.

Every export:
  1. writes atomically to a temp name in the destination, then renames;
  2. reads the written bytes back and compares SHA-256 with what we intended;
  3. refuses to report success unless the hashes match.

This guarantees a yanked cable or a flaky MTP bridge can never silently leave a
truncated catalog behind that the tablet would then load.
"""

from __future__ import annotations

import os
import string
from dataclasses import dataclass
from typing import List, Optional

from . import civ
from .model import Book


@dataclass
class Destination:
    path: str
    label: str
    kind: str  # "removable", "fixed", "mtp", "folder"


def detect_destinations() -> List[Destination]:
    """Best-effort list of plausible export targets.

    On Windows this enumerates drive letters and flags removable ones. MTP
    devices don't get drive letters, so for those the user picks the folder via
    the GUI's folder browser (which can navigate into the tablet under
    "This PC"). On non-Windows hosts we list mounted volumes for parity in
    testing.
    """
    out: List[Destination] = []
    if os.name == "nt":
        import ctypes

        bitmask = ctypes.windll.kernel32.GetLogicalDrives()
        DRIVE_REMOVABLE = 2
        DRIVE_FIXED = 3
        DRIVE_REMOTE = 4
        for i, letter in enumerate(string.ascii_uppercase):
            if not (bitmask >> i) & 1:
                continue
            root = f"{letter}:\\"
            try:
                dtype = ctypes.windll.kernel32.GetDriveTypeW(ctypes.c_wchar_p(root))
            except Exception:
                dtype = 0
            if dtype == DRIVE_REMOVABLE:
                out.append(Destination(root, f"{letter}: (removable / USB)", "removable"))
            elif dtype == DRIVE_FIXED:
                out.append(Destination(root, f"{letter}: (local disk)", "fixed"))
            elif dtype == DRIVE_REMOTE:
                out.append(Destination(root, f"{letter}: (network)", "fixed"))
    else:
        for base in ("/Volumes", "/media", "/mnt", "/run/media"):
            if os.path.isdir(base):
                try:
                    for name in sorted(os.listdir(base)):
                        full = os.path.join(base, name)
                        if os.path.isdir(full):
                            out.append(Destination(full, full, "removable"))
                except OSError:
                    pass
    return out


@dataclass
class ExportResult:
    path: str
    book_count: int
    sha256: str
    verified: bool


def export_to(books: List[Book], dest_path: str, filename: str = "catalog.civ") -> ExportResult:
    """Write ``books`` to ``dest_path`` (a folder or a full file path) and verify.

    Raises on any I/O problem or a hash mismatch; the caller should treat an
    exception as "the tablet did NOT receive a valid file".
    """
    if os.path.isdir(dest_path):
        target = os.path.join(dest_path, filename)
    else:
        target = dest_path
        if not target.lower().endswith(civ.CIV_EXTENSION):
            target += civ.CIV_EXTENSION

    intended_hash = civ.write_file(target, books)

    # Read back and verify — this is the whole point of "safe" transfer.
    actual_hash = civ.file_sha256(target)
    verified = actual_hash == intended_hash
    if not verified:
        raise IOError(
            "Export verification failed: the file on the destination does not "
            "match what was written (the transfer may have been interrupted). "
            "Do NOT import this file on the tablet — try again."
        )

    return ExportResult(
        path=target,
        book_count=len(books),
        sha256=actual_hash,
        verified=verified,
    )
