"""Permanent numbered .xlsx archives for each successful send (1.xlsx, 2.xlsx …)."""

from __future__ import annotations

import os
from typing import List, Tuple

from .export_counter import _counter_dir

XLSX_EXTENSION = ".xlsx"


def exports_dir() -> str:
    path = os.path.join(_counter_dir(), "exports")
    os.makedirs(path, exist_ok=True)
    return path


def export_filename(batch_number: int) -> str:
    return f"{batch_number}{XLSX_EXTENSION}"


def path_for_batch(batch_number: int) -> str:
    return os.path.join(exports_dir(), export_filename(batch_number))


def list_archives() -> List[Tuple[int, str]]:
    out: List[Tuple[int, str]] = []
    root = exports_dir()
    for name in os.listdir(root):
        if not name.endswith(XLSX_EXTENSION):
            continue
        stem = name[: -len(XLSX_EXTENSION)]
        if not stem.isdigit():
            continue
        out.append((int(stem), os.path.join(root, name)))
    out.sort(key=lambda t: t[0])
    return out
