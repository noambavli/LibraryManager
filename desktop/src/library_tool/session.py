"""The application session: the single source of truth the GUI drives."""

from __future__ import annotations

import os
import threading
import zipfile
from dataclasses import dataclass, field
from typing import Callable, List, Optional

from . import adb_transfer, backups, validation
from . import strings_he as S
from .export_counter import (
    peek_next_batch_number,
    peek_next_beis_batch_number,
    peek_next_matchings_batch_number,
)
from .converter import ConvertResult, convert_rows
from .beis_converter import BeisConvertResult, convert_beis_rows
from .matchings_converter import MatchingsConvertResult, rows_to_matchings
from .model import Book, Matching
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
            raise AbortError(S.ABORT_BY_USER)


ProgressFn = Callable[[str, float], None]


def _noop_progress(message: str, fraction: float) -> None:
    pass


@dataclass
class ImportOutcome:
    convert: ConvertResult
    report: validation.ValidationReport
    restore_point: Optional[backups.BackupEntry]


@dataclass
class MatchingsImportOutcome:
    convert: MatchingsConvertResult
    source_path: str


@dataclass
class BeisImportOutcome:
    convert: BeisConvertResult
    source_path: str


def _read_workbook(path: str) -> list:
    """Read xlsx with user-facing Hebrew errors."""
    if not os.path.isfile(path):
        raise ValueError(S.ERR_FILE_NOT_FOUND)
    try:
        return read_first_sheet(path)
    except zipfile.BadZipFile as exc:
        raise ValueError(S.ERR_NOT_XLSX) from exc
    except PermissionError as exc:
        raise ValueError(S.ERR_FILE_LOCKED) from exc
    except OSError as exc:
        if getattr(exc, "errno", None) in (13, 32):
            raise ValueError(S.ERR_FILE_LOCKED) from exc
        raise ValueError(S.ERR_READ_XLSX.format(detail=str(exc))) from exc
    except ValueError as exc:
        raise ValueError(S.ERR_READ_XLSX.format(detail=str(exc))) from exc
    except Exception as exc:
        raise ValueError(S.ERR_READ_XLSX.format(detail=str(exc))) from exc


class Session:
    def __init__(self) -> None:
        self.books: List[Book] = []
        self.source_path: Optional[str] = None
        self.last_sent_batch: Optional[int] = None
        self.dirty: bool = False
        self.last_import_restore: Optional[backups.BackupEntry] = None
        self.last_report: Optional[validation.ValidationReport] = None
        self.matchings: List[Matching] = []
        self.matchings_source_path: Optional[str] = None
        self.last_sent_matchings_batch: Optional[int] = None
        self.beis_books: List[Book] = []
        self.beis_source_path: Optional[str] = None
        self.last_sent_beis_batch: Optional[int] = None

    def import_xlsx(
        self,
        path: str,
        progress: ProgressFn = _noop_progress,
        abort: Optional[AbortFlag] = None,
    ) -> ImportOutcome:
        abort = abort or AbortFlag()

        progress(S.PROGRESS_SNAPSHOT, 0.05)
        restore_point = None
        if self.books:
            restore_point = backups.create_backup(
                self.books, backups.KIND_IMPORT, source=self.source_path or ""
            )
        abort.check()

        progress(S.PROGRESS_READ_WORKBOOK, 0.25)
        rows = _read_workbook(path)
        abort.check()

        progress(S.PROGRESS_CONVERT, 0.55)
        result = convert_rows(rows)
        abort.check()

        progress(S.PROGRESS_VALIDATE, 0.8)
        report = validation.validate(result.books, skipped=result.skipped)
        abort.check()

        self.books = result.books
        self.source_path = path
        self.dirty = False
        self.last_import_restore = restore_point
        self.last_report = report

        progress(S.PROGRESS_DONE, 1.0)
        return ImportOutcome(result, report, restore_point)

    def import_matchings_xlsx(
        self,
        path: str,
        progress: ProgressFn = _noop_progress,
        abort: Optional[AbortFlag] = None,
    ) -> MatchingsImportOutcome:
        abort = abort or AbortFlag()

        progress(S.PROGRESS_READ_MATCHINGS, 0.35)
        rows = _read_workbook(path)
        abort.check()

        progress(S.PROGRESS_CONVERT, 0.7)
        result = rows_to_matchings(rows)
        abort.check()

        self.matchings = result.matchings
        self.matchings_source_path = path

        progress(S.PROGRESS_DONE, 1.0)
        return MatchingsImportOutcome(result, path)

    def next_matchings_send_batch(self) -> int:
        return peek_next_matchings_batch_number()

    def matchings_tablet_pick_hint(self) -> str:
        if self.last_sent_matchings_batch is not None:
            batch = self.last_sent_matchings_batch
            return S.HINT_LAST_MATCHINGS.format(batch=batch)
        if self.matchings:
            n = self.next_matchings_send_batch()
            return S.HINT_AFTER_MATCHINGS.format(n=n)
        return ""

    def send_matchings_to_tablet(
        self,
        progress: ProgressFn = _noop_progress,
        abort: Optional[AbortFlag] = None,
    ) -> adb_transfer.AdbSendResult:
        abort = abort or AbortFlag()
        progress(S.PROGRESS_FIND_TABLET, 0.1)
        abort.check()
        batch = peek_next_matchings_batch_number()
        progress(S.PROGRESS_SEND_MATCHINGS, 0.4)
        progress(S.PROGRESS_WAIT_CONFIRM, 0.55)
        result = adb_transfer.send_matchings(
            self.matchings,
            source_file=self.matchings_source_path or "",
            batch_number=batch,
        )
        abort.check()
        line = result.result_line or ""
        if line.startswith("OK:"):
            self.last_sent_matchings_batch = batch
            progress(S.PROGRESS_CONFIRMED_MATCHINGS, 1.0)
        elif line.startswith("ERR:cancelled"):
            progress(S.PROGRESS_CANCELLED_TABLET, 1.0)
        elif line.startswith("ERR:confirm_timeout"):
            progress(S.PROGRESS_CONFIRM_TIMEOUT, 1.0)
        else:
            progress(S.PROGRESS_SEND_FINISHED, 1.0)
        return result

    def import_beis_xlsx(
        self,
        path: str,
        progress: ProgressFn = _noop_progress,
        abort: Optional[AbortFlag] = None,
    ) -> BeisImportOutcome:
        abort = abort or AbortFlag()

        progress(S.PROGRESS_READ_WORKBOOK, 0.35)
        rows = _read_workbook(path)
        abort.check()

        progress(S.PROGRESS_CONVERT, 0.7)
        result = convert_beis_rows(rows)
        abort.check()

        self.beis_books = result.books
        self.beis_source_path = path

        progress(S.PROGRESS_DONE, 1.0)
        return BeisImportOutcome(result, path)

    def next_beis_send_batch(self) -> int:
        return peek_next_beis_batch_number()

    def beis_tablet_pick_hint(self) -> str:
        if self.last_sent_beis_batch is not None:
            return S.HINT_LAST_BEIS.format(batch=self.last_sent_beis_batch)
        if self.beis_books:
            return S.HINT_AFTER_BEIS.format(n=self.next_beis_send_batch())
        return ""

    def send_beis_to_tablet(
        self,
        progress: ProgressFn = _noop_progress,
        abort: Optional[AbortFlag] = None,
    ) -> adb_transfer.AdbSendResult:
        abort = abort or AbortFlag()
        progress(S.PROGRESS_FIND_TABLET, 0.1)
        abort.check()
        batch = peek_next_beis_batch_number()
        progress(S.PROGRESS_SEND_BEIS, 0.4)
        progress(S.PROGRESS_WAIT_CONFIRM, 0.55)
        result = adb_transfer.send_beis(
            self.beis_books,
            source_file=self.beis_source_path or "",
            batch_number=batch,
        )
        abort.check()
        line = result.result_line or ""
        if line.startswith("OK:"):
            self.last_sent_beis_batch = batch
            progress(S.PROGRESS_CONFIRMED_BEIS, 1.0)
        elif line.startswith("ERR:cancelled"):
            progress(S.PROGRESS_CANCELLED_TABLET, 1.0)
        elif line.startswith("ERR:confirm_timeout"):
            progress(S.PROGRESS_CONFIRM_TIMEOUT, 1.0)
        else:
            progress(S.PROGRESS_SEND_FINISHED, 1.0)
        return result

    def can_restore_import(self) -> bool:
        return self.last_import_restore is not None and not self.dirty

    def restore_import(self) -> int:
        if self.last_import_restore is None:
            raise ValueError(S.NO_IMPORT_TO_RESTORE)
        self.books = backups.restore_backup(self.last_import_restore)
        self.source_path = self.last_import_restore.source or None
        self.dirty = False
        self.last_report = validation.validate(self.books)
        return len(self.books)

    def restore_from_backup(self, entry: backups.BackupEntry) -> int:
        self.books = backups.restore_backup(entry)
        self.source_path = entry.source or None
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
            return S.HINT_LAST_SEND.format(batch=batch)
        if self.books:
            n = self.next_send_batch()
            return S.HINT_AFTER_SEND.format(n=n)
        return S.HINT_STEPS

    def send_to_tablet(
        self,
        progress: ProgressFn = _noop_progress,
        abort: Optional[AbortFlag] = None,
    ) -> adb_transfer.AdbSendResult:
        abort = abort or AbortFlag()
        progress(S.PROGRESS_FIND_TABLET, 0.1)
        abort.check()
        batch = peek_next_batch_number()
        progress(S.PROGRESS_SEND_EXCEL, 0.4)
        progress(S.PROGRESS_WAIT_CONFIRM, 0.55)
        result = adb_transfer.send_books(
            self.books,
            source_file=self.source_path or "",
            batch_number=batch,
        )
        abort.check()
        line = result.result_line or ""
        if line.startswith("OK:"):
            self.last_sent_batch = batch
            progress(S.PROGRESS_CONFIRMED_BOOKS, 1.0)
        elif line.startswith("ERR:cancelled"):
            progress(S.PROGRESS_CANCELLED_TABLET, 1.0)
        elif line.startswith("ERR:confirm_timeout"):
            progress(S.PROGRESS_CONFIRM_TIMEOUT, 1.0)
        else:
            progress(S.PROGRESS_SEND_FINISHED, 1.0)
        return result
