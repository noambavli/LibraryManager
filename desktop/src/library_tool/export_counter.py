"""Simple incrementing batch numbers for .civ exports: 1.civ, 2.civ, 3.civ …"""

from __future__ import annotations

import os
import sys


def _counter_dir() -> str:
    if getattr(sys, "frozen", False):
        base = os.path.dirname(sys.executable)
    else:
        base = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    path = os.path.join(base, ".librarytool")
    os.makedirs(path, exist_ok=True)
    return path


def _counter_file() -> str:
    return os.path.join(_counter_dir(), "export_counter.txt")


def _read_last_assigned() -> int:
    try:
        with open(_counter_file(), "r", encoding="utf-8") as fh:
            return max(0, int(fh.read().strip() or "0"))
    except (OSError, ValueError):
        return 0


def _write_last_assigned(n: int) -> None:
    path = _counter_file()
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        fh.write(str(n))
        fh.flush()
        os.fsync(fh.fileno())
    try:
        os.replace(tmp, path)
    except OSError:
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(str(n))
        try:
            os.remove(tmp)
        except OSError:
            pass


def peek_next_batch_number() -> int:
    """Next number that will be used (does not consume)."""
    return _read_last_assigned() + 1


def next_batch_number() -> int:
    """Reserve and persist the next batch number."""
    n = peek_next_batch_number()
    _write_last_assigned(n)
    return n


def commit_batch_number(n: int) -> None:
    """Mark batch n as successfully sent (idempotent)."""
    _write_last_assigned(max(_read_last_assigned(), n))


def _matchings_counter_file() -> str:
    return os.path.join(_counter_dir(), "matchings_export_counter.txt")


def _read_matchings_last_assigned() -> int:
    try:
        with open(_matchings_counter_file(), "r", encoding="utf-8") as fh:
            return max(0, int(fh.read().strip() or "0"))
    except (OSError, ValueError):
        return 0


def _write_matchings_last_assigned(n: int) -> None:
    path = _matchings_counter_file()
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        fh.write(str(n))
        fh.flush()
        os.fsync(fh.fileno())
    try:
        os.replace(tmp, path)
    except OSError:
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(str(n))
        try:
            os.remove(tmp)
        except OSError:
            pass


def peek_next_matchings_batch_number() -> int:
    return _read_matchings_last_assigned() + 1


def commit_matchings_batch_number(n: int) -> None:
    _write_matchings_last_assigned(max(_read_matchings_last_assigned(), n))
