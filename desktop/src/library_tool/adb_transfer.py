"""Automatic tablet transfer via adb — no drag-and-drop, no MTP folder picker.

Flow (same pattern as the app's APK update):
  1. Detect adb + a connected tablet
  2. Best-effort enable USB file-transfer mode on the device
  3. Push books-import.xlsx to /data/local/tmp/books-import.xlsx
  4. Broadcast IMPORT_EXCEL to wake the tablet app
  5. Read catalog-import-result.txt — PENDING while waiting for on-tablet
     confirmation, then OK (merged) or ERR (cancelled / failed)

Requires USB debugging enabled on the tablet (done automatically for
device-owner/kiosk tablets). Works even when the tablet does not appear
as a drive letter in Windows Explorer.
"""

from __future__ import annotations

import hashlib
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from typing import List, Optional, Sequence, Tuple

from . import strings_he as S

REMOTE_XLSX_TMP = "/data/local/tmp/books-import.xlsx"
REMOTE_XLSX_DOWNLOAD = "/sdcard/Download/books-import.xlsx"
REMOTE_RESULT_TMP = "/data/local/tmp/catalog-import-result.txt"
REMOTE_RESULT = "/sdcard/Download/catalog-import-result.txt"
REMOTE_RESULT_APP = (
    "/sdcard/Android/data/com.mh.librarymanager/files/catalog-import-result.txt"
)
# App-private path first (always fresh); Downloads last (MediaStore can lag).
REMOTE_RESULTS = [REMOTE_RESULT_APP, REMOTE_RESULT_TMP, REMOTE_RESULT]
RESULT_PROGRESS = "RUNNING"
CONFIRM_TIMEOUT_SEC = 600
IMPORT_ACTION = "com.mh.librarymanager.IMPORT_EXCEL"
IMPORT_RECEIVER = "com.mh.librarymanager/.ExcelImportReceiver"
PACKAGE = "com.mh.librarymanager"

MATCHINGS_REMOTE_XLSX_TMP = "/data/local/tmp/matchings-import.xlsx"
MATCHINGS_REMOTE_RESULT_TMP = "/data/local/tmp/matchings-import-result.txt"
MATCHINGS_REMOTE_RESULT = "/sdcard/Download/matchings-import-result.txt"
MATCHINGS_REMOTE_RESULT_APP = (
    "/sdcard/Android/data/com.mh.librarymanager/files/matchings-import-result.txt"
)
MATCHINGS_REMOTE_RESULTS = [
    MATCHINGS_REMOTE_RESULT_APP,
    MATCHINGS_REMOTE_RESULT_TMP,
    MATCHINGS_REMOTE_RESULT,
]
MATCHINGS_IMPORT_ACTION = "com.mh.librarymanager.IMPORT_MATCHINGS_EXCEL"
MATCHINGS_IMPORT_RECEIVER = "com.mh.librarymanager/.MatchingsImportReceiver"
MATCHINGS_RESULT_FILE = "matchings-import-result.txt"


@dataclass(frozen=True)
class ImportChannel:
    remote_xlsx_tmp: str
    remote_results: Tuple[str, ...]
    remote_result_tmp: str
    import_action: str
    import_receiver: str
    logcat_tag: str
    mediastore_name: str
    merged_re: re.Pattern
    awaiting_re: re.Pattern
    pending_prefix: str  # "PENDING:added={added}:skipped={other}" or updated=
    ok_skipped_key: str  # "skipped" or "updated"


BOOKS_CHANNEL = ImportChannel(
    remote_xlsx_tmp=REMOTE_XLSX_TMP,
    remote_results=tuple(REMOTE_RESULTS),
    remote_result_tmp=REMOTE_RESULT_TMP,
    import_action=IMPORT_ACTION,
    import_receiver=IMPORT_RECEIVER,
    logcat_tag="ExcelImport",
    mediastore_name="catalog-import-result.txt",
    merged_re=re.compile(r"Merged catalog: \+(\d+) added, (\d+) skipped, total (\d+)"),
    awaiting_re=re.compile(r"Awaiting confirmation: \+(\d+) to add, (\d+) skipped"),
    pending_prefix="PENDING:added={added}:skipped={other}",
    ok_skipped_key="skipped",
)

MATCHINGS_CHANNEL = ImportChannel(
    remote_xlsx_tmp=MATCHINGS_REMOTE_XLSX_TMP,
    remote_results=tuple(MATCHINGS_REMOTE_RESULTS),
    remote_result_tmp=MATCHINGS_REMOTE_RESULT_TMP,
    import_action=MATCHINGS_IMPORT_ACTION,
    import_receiver=MATCHINGS_IMPORT_RECEIVER,
    logcat_tag="MatchingsImport",
    mediastore_name=MATCHINGS_RESULT_FILE,
    merged_re=re.compile(
        r"Merged matchings: \+(\d+) added, (\d+) updated, total (\d+)"
    ),
    awaiting_re=re.compile(
        r"Awaiting confirmation: \+(\d+) to add, (\d+) to update"
    ),
    pending_prefix="PENDING:added={added}:updated={other}",
    ok_skipped_key="updated",
)


@dataclass
class DeviceInfo:
    serial: str
    model: str


@dataclass
class RawDevice:
    serial: str
    state: str
    model: str = ""


@dataclass
class AdbSendResult:
    device: DeviceInfo
    local_path: str
    remote_path: str
    sha256: str
    imported_count: Optional[int]
    result_line: str


@dataclass
class AdbDiagnosis:
    adb_path: Optional[str]
    devices_raw: str
    devices: List[RawDevice]
    ready: List[DeviceInfo]
    error: Optional[str] = None


def _app_dir() -> str:
    """Directory containing LibraryTool.exe (PyInstaller) or the source tree."""
    if getattr(sys, "frozen", False):
        return os.path.dirname(sys.executable)
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def find_adb() -> Optional[str]:
    """Locate adb.exe on Windows (bundled next to the exe first)."""
    candidates: List[str] = []
    base = _app_dir()
    candidates += [
        os.path.join(base, "adb", "adb.exe"),
        os.path.join(base, "adb.exe"),
    ]
    if os.name == "nt":
        local = os.environ.get("LOCALAPPDATA", "")
        if local:
            candidates.append(
                os.path.join(local, "Android", "Sdk", "platform-tools", "adb.exe")
            )
    which = _which("adb")
    if which:
        candidates.append(which)
    if os.name == "nt":
        which_exe = _which("adb.exe")
        if which_exe:
            candidates.append(which_exe)

    for path in candidates:
        if path and os.path.isfile(path):
            return path
    return None


def _which(cmd: str) -> Optional[str]:
    for folder in os.environ.get("PATH", "").split(os.pathsep):
        full = os.path.join(folder, cmd)
        if os.path.isfile(full):
            return full
    return None


def _adb_env(adb: str) -> dict:
    """Use the bundled adb/.android keys so every PC shares one identity."""
    env = os.environ.copy()
    adb_dir = os.path.dirname(os.path.abspath(adb))
    env["ANDROID_SDK_HOME"] = adb_dir
    return env


def _run(adb: str, args: List[str], timeout: int = 120) -> Tuple[int, str, str]:
    adb_dir = os.path.dirname(os.path.abspath(adb))
    kwargs = {
        "capture_output": True,
        "text": True,
        "timeout": timeout,
        "encoding": "utf-8",
        "errors": "replace",
        "env": _adb_env(adb),
        "cwd": adb_dir,
    }
    if os.name == "nt":
        kwargs["creationflags"] = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    try:
        proc = subprocess.run([adb] + args, **kwargs)
        return proc.returncode, proc.stdout.strip(), proc.stderr.strip()
    except subprocess.TimeoutExpired:
        return -1, "", "Timed out"
    except FileNotFoundError:
        return -1, "", "adb not found"
    except Exception as e:
        return -1, "", str(e)


def _ensure_server(adb: str) -> None:
    """Restart adb — helps on Windows after cable plug or driver install."""
    _run(adb, ["kill-server"], timeout=15)
    _run(adb, ["start-server"], timeout=30)


def parse_devices_output(out: str) -> List[RawDevice]:
    devices: List[RawDevice] = []
    for line in out.splitlines():
        line = line.strip()
        if not line or line.startswith("List of"):
            continue
        parts = line.split()
        if len(parts) < 2:
            continue
        serial, state = parts[0], parts[1]
        model = ""
        for p in parts[2:]:
            if p.startswith("model:"):
                model = p.split(":", 1)[1]
        devices.append(RawDevice(serial=serial, state=state, model=model))
    return devices


def list_devices(adb: str, *, restart_server: bool = False) -> List[DeviceInfo]:
    if restart_server:
        _ensure_server(adb)
    code, out, err = _run(adb, ["devices", "-l"])
    if code != 0:
        return []
    return [
        DeviceInfo(serial=d.serial, model=d.model or "tablet")
        for d in parse_devices_output(out or err)
        if d.state == "device"
    ]


def diagnose(*, restart_server: bool = True) -> AdbDiagnosis:
    """Full connection check with actionable error text for the GUI."""
    adb = find_adb()
    if not adb:
        return AdbDiagnosis(
            adb_path=None,
            devices_raw="",
            devices=[],
            ready=[],
            error=S.ADB_NOT_FOUND,
        )

    if restart_server:
        _ensure_server(adb)
    code, out, err = _run(adb, ["devices", "-l"], timeout=20)
    raw = out if out else err
    parsed = parse_devices_output(raw)
    ready = [
        DeviceInfo(serial=d.serial, model=d.model or "tablet")
        for d in parsed
        if d.state == "device"
    ]
    error = None if ready else _explain_devices(parsed, adb, raw)
    return AdbDiagnosis(
        adb_path=adb,
        devices_raw=raw,
        devices=parsed,
        ready=ready,
        error=error,
    )


def _explain_devices(parsed: List[RawDevice], adb: str, raw: str) -> str:
    if not parsed:
        text = S.ADB_NO_DEVICE.format(adb=adb)
        if raw.strip():
            text += "\n" + S.ADB_DEVICES_LINE.format(raw=raw.strip())
        return text

    unauthorized = [d for d in parsed if d.state == "unauthorized"]
    if unauthorized:
        serials = ", ".join(d.serial for d in unauthorized)
        return S.ADB_UNAUTHORIZED.format(serials=serials)

    offline = [d for d in parsed if d.state == "offline"]
    if offline:
        return S.ADB_OFFLINE

    other = ", ".join(f"{d.serial} ({d.state})" for d in parsed)
    return S.ADB_NOT_READY.format(details=other)


def prepare_usb(adb: str, serial: str) -> None:
    """Best-effort: default USB to file transfer (helps some Samsung tablets)."""
    for args in (
        ["-s", serial, "shell", "settings", "put", "global", "usb_default_functions", "mtp"],
        ["-s", serial, "shell", "cmd", "usb", "setFunctions", "mtp"],
        ["-s", serial, "shell", "svc", "usb", "setFunctions", "mtp"],
    ):
        _run(adb, args, timeout=15)


def _local_sha256(path: str) -> str:
    with open(path, "rb") as fh:
        return hashlib.sha256(fh.read()).hexdigest()


def _verify_remote_file(adb: str, serial: str, local_path: str, remote_path: str) -> None:
    """Confirm the pushed file on the tablet matches the local copy."""
    expected = _local_sha256(local_path)
    code, out, _ = _run(
        adb, ["-s", serial, "shell", "sha256sum", remote_path], timeout=45,
    )
    if code == 0 and out.strip():
        got = out.split()[0].strip()
        if got == expected:
            return

    local_size = os.path.getsize(local_path)
    code, out, _ = _run(
        adb, ["-s", serial, "shell", "stat", "-c", "%s", remote_path], timeout=15,
    )
    if code != 0:
        code, out, _ = _run(
            adb, ["-s", serial, "shell", "wc", "-c", remote_path], timeout=15,
        )
    if code == 0 and out.strip():
        try:
            remote_size = int(out.split()[0])
            if remote_size == local_size:
                return
        except ValueError:
            pass

    raise IOError(S.ADB_PUSH_VERIFY_FAILED)


def _is_final_result(line: str) -> bool:
    stripped = line.strip()
    return stripped.startswith("OK:") or stripped.startswith("ERR:")


def _is_progress_result(line: str) -> bool:
    stripped = line.strip()
    return stripped == RESULT_PROGRESS or stripped.startswith("PENDING:")


def _import_timeout_sec(local_xlsx: str) -> int:
    """Scale wait time with workbook size — large merges can take over a minute."""
    size = os.path.getsize(local_xlsx)
    return min(300, 90 + (size // 102_400) * 10)


def _quiet_remove(path: str) -> None:
    try:
        if os.path.exists(path):
            os.remove(path)
    except OSError:
        pass


def _read_result_file(adb: str, serial: str, remote: str) -> str:
    code, out, _ = _run(adb, ["-s", serial, "shell", "cat", remote], timeout=10)
    if code == 0 and out.strip():
        return out.strip()
    return ""


_LOGCAT_MERGED = BOOKS_CHANNEL.merged_re
_LOGCAT_AWAITING = BOOKS_CHANNEL.awaiting_re


def _parse_logcat_import(logcat: str, channel: ImportChannel = BOOKS_CHANNEL) -> str:
    """Build a result line from import log output (file write fallback)."""
    lines = logcat.splitlines()
    for line in reversed(lines):
        match = channel.merged_re.search(line)
        if match:
            added, other, total = match.groups()
            if channel.ok_skipped_key == "updated":
                return f"OK:added={added}:updated={other}:unchanged=0:total={total}"
            return f"OK:added={added}:skipped={other}:total={total}"
    for line in reversed(lines):
        match = channel.awaiting_re.search(line)
        if match:
            added, other = match.groups()
            pending = channel.pending_prefix.format(added=added, other=other)
            if channel.ok_skipped_key == "updated":
                return f"{pending}:unchanged=0:current=0:total=0"
            return f"{pending}:current=0:total=0"
    for line in reversed(lines):
        if "Import failed:" in line:
            detail = line.split("Import failed:", 1)[1].strip()
            return f"ERR:{detail}"
        if "Import crashed" in line:
            return "ERR:Import crashed"
    return ""


def _read_result_logcat(
    adb: str,
    serial: str,
    *,
    since: str = "",
    channel: ImportChannel = BOOKS_CHANNEL,
) -> str:
    tag = channel.logcat_tag
    code, out, _ = _run(
        adb,
        ["-s", serial, "logcat", "-d", "-s", f"{tag}:I", f"{tag}:E"],
        timeout=15,
    )
    if code != 0 or not out.strip():
        return ""
    if since:
        since_lines = set(since.splitlines())
        fresh = [ln for ln in out.splitlines() if ln not in since_lines]
        if not fresh:
            return ""
        return _parse_logcat_import("\n".join(fresh), channel)
    return _parse_logcat_import(out, channel)


def _read_result_mediastore(
    adb: str,
    serial: str,
    *,
    channel: ImportChannel = BOOKS_CHANNEL,
) -> str:
    """Fallback when Downloads was written via MediaStore (Android 10+)."""
    name = channel.mediastore_name
    for where in (
        f"display_name='{name}'",
        f"_display_name='{name}'",
        f"title='{name}'",
    ):
        code, out, _ = _run(
            adb,
            [
                "-s", serial, "shell", "content", "query",
                "--uri", "content://media/external/downloads",
                "--projection", "_data",
                "--where", where,
            ],
            timeout=15,
        )
        if code != 0 or not out.strip():
            continue
        for line in out.splitlines():
            if "_data=" not in line:
                continue
            path = line.split("_data=", 1)[1].strip()
            content = _read_result_file(adb, serial, path)
            if content:
                return content
    return ""


def _trigger_import(
    adb: str,
    serial: str,
    *,
    channel: ImportChannel = BOOKS_CHANNEL,
) -> None:
    """Broadcast import (proven path) and wake the app on newer builds."""
    code, out, err = _run(
        adb,
        [
            "-s", serial, "shell", "am", "broadcast",
            "-a", channel.import_action,
            "-n", channel.import_receiver,
            "--include-stopped-packages",
        ],
        timeout=30,
    )
    if code != 0:
        raise IOError(S.ADB_BROADCAST_FAILED.format(detail=err or out or S.ADB_UNKNOWN_ERROR))
    _run(
        adb,
        [
            "-s", serial, "shell", "am", "start",
            "-n", f"{PACKAGE}/.MainActivity",
            "-a", channel.import_action,
            "--include-stopped-packages",
        ],
        timeout=30,
    )


def push_and_import(
    adb: str,
    serial: str,
    local_xlsx: str,
    *,
    download_name: str = "books-import.xlsx",
    channel: ImportChannel = BOOKS_CHANNEL,
) -> AdbSendResult:
    """Push workbook to tmp for import and copy a numbered archive into Download."""
    prepare_usb(adb, serial)

    for path in channel.remote_results:
        _run(adb, ["-s", serial, "shell", "rm", "-f", path], timeout=15)

    code, _, err = _run(
        adb, ["-s", serial, "push", local_xlsx, channel.remote_xlsx_tmp], timeout=180,
    )
    if code != 0:
        raise IOError(S.ADB_PUSH_FAILED.format(detail=err or S.ADB_UNKNOWN_ERROR))

    _verify_remote_file(adb, serial, local_xlsx, channel.remote_xlsx_tmp)

    remote_download = f"/sdcard/Download/{download_name}"
    _run(
        adb,
        ["-s", serial, "shell", "cp", channel.remote_xlsx_tmp, remote_download],
        timeout=30,
    )
    _run(
        adb,
        [
            "-s", serial, "shell", "am", "broadcast",
            "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
            "-d", f"file://{remote_download}",
        ],
        timeout=15,
    )

    digest = _local_sha256(local_xlsx)

    _run(
        adb,
        [
            "-s", serial, "shell", "sh", "-c",
            f"echo {RESULT_PROGRESS} > {channel.remote_result_tmp}",
        ],
        timeout=10,
    )

    tag = channel.logcat_tag
    _run(adb, ["-s", serial, "logcat", "-c"], timeout=10)
    _, logcat_baseline, _ = _run(
        adb,
        ["-s", serial, "logcat", "-d", "-s", f"{tag}:I", f"{tag}:E"],
        timeout=15,
    )

    _trigger_import(adb, serial, channel=channel)

    timeout_sec = _import_timeout_sec(local_xlsx)
    result_line = _wait_for_result(
        adb,
        serial,
        timeout_sec=timeout_sec,
        logcat_baseline=logcat_baseline,
        channel=channel,
    )
    if not result_line or not _is_final_result(result_line):
        raise IOError(
            "Tablet did not report an import result in time. "
            "The file was pushed but import may not have finished. "
            f"(waited {timeout_sec}s — update the tablet app if this keeps happening)"
        )

    imported = _parse_import_count(result_line)

    return AdbSendResult(
        device=DeviceInfo(serial=serial, model=""),
        local_path=local_xlsx,
        remote_path=channel.remote_xlsx_tmp,
        sha256=digest,
        imported_count=imported,
        result_line=result_line,
    )


def _pick_best_result(*candidates: str) -> str:
    """Prefer OK over stale ERR when multiple result paths disagree."""
    lines = [c.strip() for c in candidates if c and c.strip()]
    for line in lines:
        if line.startswith("OK:"):
            return line
    for line in lines:
        if line.startswith("ERR:cancelled"):
            return line
    for line in lines:
        if _is_final_result(line):
            return line
    for line in lines:
        if _is_progress_result(line):
            return line
    return ""


def _collect_result_candidates(
    adb: str,
    serial: str,
    *,
    channel: ImportChannel = BOOKS_CHANNEL,
) -> List[str]:
    out: List[str] = []
    for remote in channel.remote_results:
        line = _read_result_file(adb, serial, remote)
        if line:
            out.append(line)
    line = _read_result_mediastore(adb, serial, channel=channel)
    if line:
        out.append(line)
    return out


def _wait_for_result(
    adb: str,
    serial: str,
    timeout_sec: int = 120,
    logcat_baseline: str = "",
    *,
    channel: ImportChannel = BOOKS_CHANNEL,
) -> str:
    deadline = time.time() + timeout_sec
    saw_pending = False
    while time.time() < deadline:
        best = _pick_best_result(*_collect_result_candidates(adb, serial, channel=channel))
        if best.startswith("PENDING:"):
            saw_pending = True
            deadline = max(deadline, time.time() + CONFIRM_TIMEOUT_SEC)
        elif best.startswith("OK:"):
            return best
        elif best.startswith("ERR:cancelled"):
            return best
        elif saw_pending:
            pass
        elif best.startswith("ERR:"):
            return best
        elif best == RESULT_PROGRESS and deadline - time.time() < 45:
            deadline = time.time() + 60

        line = _read_result_logcat(adb, serial, since=logcat_baseline, channel=channel)
        if line:
            if line.startswith("OK:"):
                return line
            if saw_pending and line.startswith("ERR:") and not line.startswith("ERR:cancelled"):
                pass
            elif _is_final_result(line):
                return line
            if _is_progress_result(line) and line.strip().startswith("PENDING:"):
                saw_pending = True
                deadline = max(deadline, time.time() + CONFIRM_TIMEOUT_SEC)
        time.sleep(0.8)
    if saw_pending:
        return "ERR:confirm_timeout"
    return ""


def _parse_import_count(line: str) -> Optional[int]:
    if not line.startswith("OK:"):
        return None
    body = line[3:]
    # New: OK:added=5:skipped=2:total=120
    if "added=" in body:
        for part in body.split(":"):
            if part.startswith("added="):
                try:
                    return int(part[6:])
                except ValueError:
                    return None
        return None
    # Legacy: OK:123
    try:
        return int(body)
    except ValueError:
        return None


def send_books(
    books,
    source_file: str = "",
    batch_number: Optional[int] = None,
) -> AdbSendResult:
    """High-level: find adb + device, archive .xlsx, push, wait for tablet confirm."""
    from .converter import books_to_rows
    from .export_counter import commit_batch_number, peek_next_batch_number
    from .exports import export_filename, path_for_batch
    from .xlsx_writer import write_xlsx

    diag = diagnose(restart_server=False)
    if not diag.adb_path:
        raise FileNotFoundError(diag.error or S.ADB_NOT_FOUND.split("\n")[0])
    if not diag.ready:
        raise ConnectionError(diag.error or S.ADB_NO_DEVICE.split("\n")[0])

    if len(diag.ready) > 1:
        raise ConnectionError(S.ADB_MULTIPLE_TABLETS.format(n=len(diag.ready)))

    device = diag.ready[0]
    adb = diag.adb_path
    batch = batch_number if batch_number is not None else peek_next_batch_number()
    archive_path = path_for_batch(batch)
    write_xlsx(archive_path, books_to_rows(books))
    download_name = export_filename(batch)
    result = push_and_import(
        adb, device.serial, archive_path, download_name=download_name,
    )
    result.device = device

    line = result.result_line or ""
    if line.startswith("OK:"):
        commit_batch_number(batch)
        return result
    if line.startswith("ERR:cancelled"):
        # User declined on the tablet — drop the un-confirmed archive so the
        # next send cleanly reuses this number.
        _quiet_remove(archive_path)
        return result
    if line.startswith("ERR:confirm_timeout"):
        # Tablet may still confirm later; keep the archive but don't advance.
        return result
    # Hard rejection (wrong version / invalid / etc.) — discard and surface it.
    _quiet_remove(archive_path)
    raise IOError(S.ADB_TABLET_REJECTED_BOOKS.format(line=line))


def send_matchings(
    matchings,
    source_file: str = "",
    batch_number: Optional[int] = None,
) -> AdbSendResult:
    """High-level: find adb + device, archive matchings .xlsx, push, wait for confirm."""
    from .export_counter import (
        commit_matchings_batch_number,
        peek_next_matchings_batch_number,
    )
    from .exports import matchings_export_filename, matchings_path_for_batch
    from .matchings_converter import matchings_to_rows
    from .xlsx_writer import write_xlsx

    diag = diagnose(restart_server=False)
    if not diag.adb_path:
        raise FileNotFoundError(diag.error or S.ADB_NOT_FOUND.split("\n")[0])
    if not diag.ready:
        raise ConnectionError(diag.error or S.ADB_NO_DEVICE.split("\n")[0])

    if len(diag.ready) > 1:
        raise ConnectionError(S.ADB_MULTIPLE_TABLETS.format(n=len(diag.ready)))

    device = diag.ready[0]
    adb = diag.adb_path
    batch = (
        batch_number
        if batch_number is not None
        else peek_next_matchings_batch_number()
    )
    archive_path = matchings_path_for_batch(batch)
    write_xlsx(archive_path, matchings_to_rows(matchings))
    download_name = matchings_export_filename(batch)
    result = push_and_import(
        adb,
        device.serial,
        archive_path,
        download_name=download_name,
        channel=MATCHINGS_CHANNEL,
    )
    result.device = device

    line = result.result_line or ""
    if line.startswith("OK:"):
        commit_matchings_batch_number(batch)
        return result
    if line.startswith("ERR:cancelled"):
        _quiet_remove(archive_path)
        return result
    if line.startswith("ERR:confirm_timeout"):
        return result
    _quiet_remove(archive_path)
    raise IOError(S.ADB_TABLET_REJECTED_MATCHINGS.format(line=line))
