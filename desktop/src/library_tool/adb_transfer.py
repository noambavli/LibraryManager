"""Automatic tablet transfer via adb — no drag-and-drop, no MTP folder picker.

Flow (same pattern as the app's APK update):
  1. Detect adb + a connected tablet
  2. Best-effort enable USB file-transfer mode on the device
  3. Push catalog.civ to /data/local/tmp/catalog.civ
  4. Broadcast IMPORT_CATALOG to wake the tablet app
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
from typing import List, Optional, Tuple

REMOTE_CIV_TMP = "/data/local/tmp/catalog.civ"
REMOTE_CIV_DOWNLOAD = "/sdcard/Download/catalog.civ"
REMOTE_RESULT_TMP = "/data/local/tmp/catalog-import-result.txt"
REMOTE_RESULT = "/sdcard/Download/catalog-import-result.txt"
REMOTE_RESULT_APP = (
    "/sdcard/Android/data/com.mh.librarymanager/files/catalog-import-result.txt"
)
# Downloads first — that path worked in the original "fix file import" flow.
REMOTE_RESULTS = [REMOTE_RESULT, REMOTE_RESULT_TMP, REMOTE_RESULT_APP]
RESULT_PROGRESS = "RUNNING"
CONFIRM_TIMEOUT_SEC = 600
IMPORT_ACTION = "com.mh.librarymanager.IMPORT_CATALOG"
IMPORT_RECEIVER = "com.mh.librarymanager/.CatalogImportReceiver"
PACKAGE = "com.mh.librarymanager"


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
            error=(
                "adb not found.\n"
                "Keep the whole package folder together:\n"
                "  LibraryTool.exe\n"
                "  adb\\adb.exe  (+ AdbWinApi.dll, AdbWinUsbApi.dll)\n"
                "  adb\\.android\\adbkey"
            ),
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
        lines = [
            "No USB device seen by adb.",
            "",
            "On Windows, try in order:",
            "  1. Unplug and replug the USB cable (direct port, not a hub)",
            "  2. Install the Samsung / Android USB driver for the tablet",
            "  3. Keep LibraryTool.exe and the adb\\ folder in the same place",
            "  4. Ask your technician to run authorize_tablet.bat once",
            "",
            f"adb used: {adb}",
        ]
        if raw.strip():
            lines.append(f"adb devices: {raw.strip()}")
        return "\n".join(lines)

    unauthorized = [d for d in parsed if d.state == "unauthorized"]
    if unauthorized:
        serials = ", ".join(d.serial for d in unauthorized)
        return (
            f"Tablet found ({serials}) but this PC is not authorized yet.\n\n"
            "Technician — run once per tablet:\n"
            "  authorize_tablet.bat   (in the same folder as LibraryTool.exe)\n\n"
            "Or from a Mac that already works with the tablet:\n"
            "  authorize_from_mac.sh\n\n"
            "Daily staff do not need to touch the tablet."
        )

    offline = [d for d in parsed if d.state == "offline"]
    if offline:
        return (
            "Tablet is offline. Unplug the USB cable, wait 3 seconds, plug back in, "
            "then try again."
        )

    other = ", ".join(f"{d.serial} ({d.state})" for d in parsed)
    return f"Tablet found but not ready: {other}"


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

    raise IOError(
        "Push verification failed: the file on the tablet does not match the PC copy."
    )


def _is_final_result(line: str) -> bool:
    stripped = line.strip()
    return stripped.startswith("OK:") or stripped.startswith("ERR:")


def _is_progress_result(line: str) -> bool:
    stripped = line.strip()
    return stripped == RESULT_PROGRESS or stripped.startswith("PENDING:")


def _import_timeout_sec(local_civ: str) -> int:
    """Scale wait time with catalog size — large merges can take over a minute."""
    size = os.path.getsize(local_civ)
    return min(300, 90 + (size // 102_400) * 10)


def _read_result_file(adb: str, serial: str, remote: str) -> str:
    code, out, _ = _run(adb, ["-s", serial, "shell", "cat", remote], timeout=10)
    if code == 0 and out.strip():
        return out.strip()
    return ""


_LOGCAT_MERGED = re.compile(
    r"Merged catalog: \+(\d+) added, (\d+) skipped, total (\d+)"
)
_LOGCAT_AWAITING = re.compile(
    r"Awaiting confirmation: \+(\d+) to add, (\d+) skipped"
)


def _parse_logcat_import(logcat: str) -> str:
    """Build a result line from CatalogImport log output (file write fallback)."""
    lines = logcat.splitlines()
    for line in reversed(lines):
        match = _LOGCAT_MERGED.search(line)
        if match:
            added, skipped, total = match.groups()
            return f"OK:added={added}:skipped={skipped}:total={total}"
    for line in reversed(lines):
        match = _LOGCAT_AWAITING.search(line)
        if match:
            added, skipped = match.groups()
            return f"PENDING:added={added}:skipped={skipped}:current=0:total=0"
    for line in reversed(lines):
        if "Import failed:" in line:
            detail = line.split("Import failed:", 1)[1].strip()
            return f"ERR:{detail}"
        if "Import crashed" in line:
            return "ERR:Import crashed"
    return ""


def _read_result_logcat(adb: str, serial: str, *, since: str = "") -> str:
    code, out, _ = _run(
        adb,
        ["-s", serial, "logcat", "-d", "-s", "CatalogImport:I", "CatalogImport:E"],
        timeout=15,
    )
    if code != 0 or not out.strip():
        return ""
    if since:
        since_lines = set(since.splitlines())
        fresh = [ln for ln in out.splitlines() if ln not in since_lines]
        if not fresh:
            return ""
        return _parse_logcat_import("\n".join(fresh))
    return _parse_logcat_import(out)


def _read_result_mediastore(adb: str, serial: str) -> str:
    """Fallback when Downloads was written via MediaStore (Android 10+)."""
    for where in (
        "display_name='catalog-import-result.txt'",
        "_display_name='catalog-import-result.txt'",
        "title='catalog-import-result.txt'",
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


def _trigger_import(adb: str, serial: str) -> None:
    """Broadcast import (proven path) and wake the app on newer builds."""
    code, out, err = _run(
        adb,
        [
            "-s", serial, "shell", "am", "broadcast",
            "-a", IMPORT_ACTION,
            "-n", IMPORT_RECEIVER,
            "--include-stopped-packages",
        ],
        timeout=30,
    )
    if code != 0:
        raise IOError(f"adb broadcast failed: {err or out or 'unknown error'}")
    # Extra wake-up for newer APKs; harmless no-op on older builds.
    _run(
        adb,
        [
            "-s", serial, "shell", "am", "start",
            "-n", f"{PACKAGE}/.MainActivity",
            "-a", IMPORT_ACTION,
            "--include-stopped-packages",
        ],
        timeout=30,
    )


def push_and_import(
    adb: str,
    serial: str,
    local_civ: str,
    *,
    download_name: str = "catalog.civ",
) -> AdbSendResult:
    """Push catalog to tmp for import and copy a numbered archive into Download."""
    prepare_usb(adb, serial)

    for path in REMOTE_RESULTS:
        _run(adb, ["-s", serial, "shell", "rm", "-f", path], timeout=15)

    code, _, err = _run(
        adb, ["-s", serial, "push", local_civ, REMOTE_CIV_TMP], timeout=180,
    )
    if code != 0:
        raise IOError(f"adb push failed: {err or 'unknown error'}")

    _verify_remote_file(adb, serial, local_civ, REMOTE_CIV_TMP)

    # Best-effort copy into public Download under the batch name (1.civ, 2.civ …).
    remote_download = f"/sdcard/Download/{download_name}"
    _run(
        adb,
        ["-s", serial, "shell", "cp", REMOTE_CIV_TMP, remote_download],
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

    digest = _local_sha256(local_civ)

    # PC can write to tmp via shell — seed RUNNING so we know adb can read this path.
    _run(
        adb,
        [
            "-s", serial, "shell", "sh", "-c",
            f"echo {RESULT_PROGRESS} > {REMOTE_RESULT_TMP}",
        ],
        timeout=10,
    )

    # Clear log so we only see output from this import attempt.
    _run(adb, ["-s", serial, "logcat", "-c"], timeout=10)
    _, logcat_baseline, _ = _run(
        adb,
        ["-s", serial, "logcat", "-d", "-s", "CatalogImport:I", "CatalogImport:E"],
        timeout=15,
    )

    _trigger_import(adb, serial)

    timeout_sec = _import_timeout_sec(local_civ)
    result_line = _wait_for_result(
        adb, serial, timeout_sec=timeout_sec, logcat_baseline=logcat_baseline,
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
        local_path=local_civ,
        remote_path=REMOTE_CIV_TMP,
        sha256=digest,
        imported_count=imported,
        result_line=result_line,
    )


def _wait_for_result(
    adb: str,
    serial: str,
    timeout_sec: int = 120,
    logcat_baseline: str = "",
) -> str:
    deadline = time.time() + timeout_sec
    saw_pending = False
    while time.time() < deadline:
        for remote in REMOTE_RESULTS:
            line = _read_result_file(adb, serial, remote)
            if not line:
                continue
            if _is_final_result(line):
                return line
            if _is_progress_result(line):
                if line.strip().startswith("PENDING:"):
                    saw_pending = True
                    deadline = max(deadline, time.time() + CONFIRM_TIMEOUT_SEC)
                elif deadline - time.time() < 45:
                    deadline = time.time() + 60
        line = _read_result_mediastore(adb, serial)
        if line:
            if _is_final_result(line):
                return line
            if _is_progress_result(line):
                if line.strip().startswith("PENDING:"):
                    saw_pending = True
                    deadline = max(deadline, time.time() + CONFIRM_TIMEOUT_SEC)
                elif deadline - time.time() < 45:
                    deadline = time.time() + 60
        line = _read_result_logcat(adb, serial, since=logcat_baseline)
        if line:
            if _is_final_result(line):
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
    write_fn,
    books,
    source_file: str = "",
    batch_number: Optional[int] = None,
) -> AdbSendResult:
    """High-level: find adb + device, archive .civ, push, wait for tablet confirm."""
    from . import civ as civ_mod
    from .export_counter import commit_batch_number, peek_next_batch_number
    from .exports import path_for_batch

    # Avoid kill-server on every send — that was not in the working "fix file import" flow
    # and can disrupt a stable USB session mid-transfer.
    diag = diagnose(restart_server=False)
    if not diag.adb_path:
        raise FileNotFoundError(diag.error or "adb not found")
    if not diag.ready:
        raise ConnectionError(diag.error or "No tablet detected")

    if len(diag.ready) > 1:
        raise ConnectionError(
            f"Multiple tablets connected ({len(diag.ready)}). Unplug extras and try again."
        )

    device = diag.ready[0]
    adb = diag.adb_path
    batch = batch_number if batch_number is not None else peek_next_batch_number()
    archive_path = path_for_batch(batch)
    if os.path.exists(archive_path):
        raise IOError(
            f"Archive {civ_mod.export_filename(batch)} already exists on this PC. "
            "The batch counter may be out of sync — check .librarytool/export_counter.txt."
        )
    write_fn(
        archive_path,
        books,
        source_file,
        batch_number=batch,
        consume_counter=False,
    )
    download_name = civ_mod.export_filename(batch)
    result = push_and_import(
        adb, device.serial, archive_path, download_name=download_name,
    )
    result.device = device
    if result.result_line.startswith("ERR:"):
        raise IOError(
            f"Tablet rejected the catalog: {result.result_line}. "
            "The file was pushed but import failed."
        )
    commit_batch_number(batch)
    return result
