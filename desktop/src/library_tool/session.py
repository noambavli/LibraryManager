"""The application session: the single source of truth the GUI drives."""

from __future__ import annotations

import threading
from dataclasses import dataclass, field
from typing import Callable, List, Optional

from . import adb_transfer, backups, validation
from .export_counter import peek_next_batch_number
from .converter import ConvertResult, convert_rows
from .model import Book
from .xlsx_reader import read_first_sheet


class AbortError(Exception):
    """Raised to unwind a long-running operation that the user aborted."""


class AbortFlag:
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
        self.last_sent_batch: Optional[int] = None
        self.dirty: bool = False
        self.last_import_restore: Optional[backups.BackupEntry] = None
        self.last_report: Optional[validation.ValidationReport] = None

    def import_xlsx(
        self,
        path: str,
        progress: ProgressFn = _noop_progress,
        abort: Optional[AbortFlag] = None,
    ) -> ImportOutcome:
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

        self.books = result.books
        self.source_path = path
        self.dirty = False
        self.last_import_restore = restore_point
        self.last_report = report

        progress("Done.", 1.0)
        return ImportOutcome(result, report, restore_point)

    def can_restore_import(self) -> bool:
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

    def validate_current(self) -> validation.ValidationReport:
        report = validation.validate(self.books)
        self.last_report = report
        return report

    def next_send_batch(self) -> int:
        return peek_next_batch_number()

    def tablet_pick_hint(self) -> str:
        if self.last_sent_batch is not None:
            batch = self.last_sent_batch
            return (
                f"Last send: {batch}.xlsx (saved on PC + tablet Download). "
                f"Confirm on the tablet when prompted."
            )
        if self.books:
            n = self.next_send_batch()
            return (
                f"After Send: file {n}.xlsx is archived (never overwritten). "
                f"Confirm on the tablet. USB must stay connected."
            )
        return "Step 1: import Excel · Step 2: Send to tablet (USB connected)"

    def send_to_tablet(
        self,
        progress: ProgressFn = _noop_progress,
        abort: Optional[AbortFlag] = None,
    ) -> adb_transfer.AdbSendResult:
        abort = abort or AbortFlag()
        progress("Looking for tablet (adb)…", 0.1)
        abort.check()
        batch = peek_next_batch_number()
        progress("Sending Excel to tablet…", 0.4)
        progress("Waiting for confirmation on the tablet…", 0.55)
        result = adb_transfer.send_books(
            self.books,
            source_file=self.source_path or "",
            batch_number=batch,
        )
        self.last_sent_batch = batch
        abort.check()
        line = result.result_line or ""
        if line.startswith("OK:"):
            progress("Tablet confirmed — books merged.", 1.0)
        elif line.startswith("ERR:cancelled"):
            progress("Cancelled on the tablet.", 1.0)
        elif line.startswith("ERR:confirm_timeout"):
            progress("Tablet did not confirm in time — approve on tablet.", 1.0)
        else:
            progress("Send finished.", 1.0)
        return result
