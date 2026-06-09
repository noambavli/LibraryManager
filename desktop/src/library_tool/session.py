"""The application session: the single source of truth the GUI drives.

Holds the working catalog in memory and brokers every state change through the
safety layer (backups, atomic writes, validation). The GUI never touches files
directly — it calls these methods, which are deliberately small, synchronous,
and exception-safe so they can run on a background thread with the GUI showing
progress.

Safety guarantees enforced here:
  * Importing first snapshots the catalog being replaced (restore-after-import).
  * The "dirty" flag tracks whether anything changed since the last import, so
    the GUI can offer a clean restore only when it's truly safe.
  * Delete-all snapshots before wiping and is therefore always reversible.
  * An ``AbortFlag`` lets any long operation be cancelled at a safe checkpoint
    without leaving partial state.
"""

from __future__ import annotations

import threading
from dataclasses import dataclass, field
from typing import Callable, List, Optional

from . import backups, civ, transfer, validation
from .converter import ConvertResult, convert_rows
from .model import Book
from .xlsx_reader import read_first_sheet


class AbortError(Exception):
    """Raised to unwind a long-running operation that the user aborted."""


class AbortFlag:
    """A thread-safe, resettable cancellation flag."""

    def __init__(self) -> None:
        self._event = threading.Event()

    def request(self) -> None:
        self._event.set()

    def reset(self) -> None:
        self._event.clear()

    @property
    def requested(self) -> bool:
        return self._event.is_set()

    def check(self) -> None:
        if self._event.is_set():
            raise AbortError("Operation aborted by the user.")


ProgressFn = Callable[[str, float], None]


def _noop_progress(message: str, fraction: float) -> None:
    pass


@dataclass
class ImportOutcome:
    convert: ConvertResult
    report: validation.ValidationReport
    restore_point: Optional[backups.BackupEntry]


class Session:
    def __init__(self) -> None:
        self.books: List[Book] = []
        self.source_path: Optional[str] = None
        # True once the working set has been changed since the last import,
        # which is when a "clean restore to pre-import" is no longer guaranteed.
        self.dirty: bool = False
        self.last_import_restore: Optional[backups.BackupEntry] = None
        self.last_report: Optional[validation.ValidationReport] = None

    # -- Import -------------------------------------------------------------

    def import_xlsx(
        self,
        path: str,
        progress: ProgressFn = _noop_progress,
        abort: Optional[AbortFlag] = None,
    ) -> ImportOutcome:
        """Read + convert + validate an xlsx, replacing the working catalog.

        The previous catalog is snapshotted first so the user can restore it if
        the new import was a mistake (and nothing has changed since).
        """
        abort = abort or AbortFlag()

        progress("Snapshotting current catalog…", 0.05)
        restore_point = None
        if self.books:
            restore_point = backups.create_backup(
                self.books, backups.KIND_IMPORT, source=self.source_path or ""
            )
        abort.check()

        progress("Reading workbook…", 0.25)
        rows = read_first_sheet(path)
        abort.check()

        progress("Converting rows…", 0.55)
        result = convert_rows(rows)
        abort.check()

        progress("Checking for duplicates and problems…", 0.8)
        report = validation.validate(result.books, skipped=result.skipped)
        abort.check()

        # Commit to working state only after all the risky work succeeded.
        self.books = result.books
        self.source_path = path
        self.dirty = False
        self.last_import_restore = restore_point
        self.last_report = report

        progress("Done.", 1.0)
        return ImportOutcome(result, report, restore_point)

    # -- Restore ------------------------------------------------------------

    def can_restore_import(self) -> bool:
        """A clean restore to the pre-import state is offered only when we have a
        restore point and nothing has been changed since the import."""
        return self.last_import_restore is not None and not self.dirty

    def restore_import(self) -> int:
        if self.last_import_restore is None:
            raise ValueError("There is no import to restore.")
        self.books = backups.restore_backup(self.last_import_restore)
        self.dirty = False
        self.last_report = validation.validate(self.books)
        return len(self.books)

    def restore_from_backup(self, entry: backups.BackupEntry) -> int:
        self.books = backups.restore_backup(entry)
        self.dirty = True
        self.last_report = validation.validate(self.books)
        return len(self.books)

    # -- Delete all ---------------------------------------------------------

    def delete_all(self) -> backups.BackupEntry:
        """Wipe the working catalog. Always snapshots first, so it's reversible.

        Returns the backup so the caller can offer an immediate undo.
        """
        snapshot = backups.create_backup(
            self.books, backups.KIND_DELETE_ALL, source=self.source_path or ""
        )
        self.books = []
        self.dirty = True
        self.last_report = validation.validate(self.books)
        return snapshot

    # -- Load an existing .civ (e.g. read back from the tablet) -------------

    def load_civ(self, path: str) -> int:
        doc = civ.read_file(path)
        self.books = doc.books
        self.source_path = path
        self.dirty = False
        self.last_import_restore = None
        self.last_report = validation.validate(self.books)
        return len(self.books)

    # -- Export -------------------------------------------------------------

    def validate_current(self) -> validation.ValidationReport:
        report = validation.validate(self.books)
        self.last_report = report
        return report

    def export(
        self,
        dest_path: str,
        filename: str = "catalog.civ",
        progress: ProgressFn = _noop_progress,
        abort: Optional[AbortFlag] = None,
    ) -> transfer.ExportResult:
        abort = abort or AbortFlag()
        progress("Snapshotting before export…", 0.2)
        backups.create_backup(self.books, backups.KIND_EXPORT, source=dest_path)
        abort.check()
        progress("Writing and verifying .civ on the tablet…", 0.6)
        result = transfer.export_to(self.books, dest_path, filename)
        abort.check()
        progress("Verified.", 1.0)
        return result

    def save_civ(self, path: str) -> str:
        """Save the working catalog to a local .civ file (no transfer)."""
        return civ.write_file(path, self.books)
