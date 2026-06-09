"""Permanent numbered .civ archives for each successful send (1.civ, 2.civ …).

Each send writes a new file — never overwrites an existing batch. If someone
wipes the tablet catalog they can re-import these files one by one (oldest
first) to rebuild the library.
"""

from __future__ import annotations

import os
from typing import List, Tuple

from . import civ
from .export_counter import _counter_dir


def exports_dir() -> str:
    path = os.path.join(_counter_dir(), "exports")
    os.makedirs(path, exist_ok=True)
    return path


def path_for_batch(batch_number: int) -> str:
    return os.path.join(exports_dir(), civ.export_filename(batch_number))


def list_archives() -> List[Tuple[int, str]]:
    """Return (batch_number, absolute_path) sorted by batch."""
    out: List[Tuple[int, str]] = []
    root = exports_dir()
    for name in os.listdir(root):
        if not name.endswith(civ.CIV_EXTENSION):
            continue
        stem = name[: -len(civ.CIV_EXTENSION)]
        if not stem.isdigit():
            continue
        out.append((int(stem), os.path.join(root, name)))
    out.sort(key=lambda t: t[0])
    return out
