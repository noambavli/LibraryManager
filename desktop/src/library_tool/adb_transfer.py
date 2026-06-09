"""Automatic tablet transfer via adb — no drag-and-drop, no MTP folder picker.

Flow (same pattern as the app's APK update):
  1. Detect adb + a connected tablet
  2. Best-effort enable USB file-transfer mode on the device
  3. Push catalog.civ to /sdcard/Download/catalog.civ
  4. Broadcast IMPORT_CATALOG to the tablet app
  5. Read catalog-import-result.txt to confirm the tablet imported it

Requires USB debugging enabled on the tablet (done automatically for
device-owner/kiosk tablets). Works even when the tablet does not appear
as a drive letter in Windows Explorer.
"""

from __future__ import annotations

import hashlib
import os
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass
from typing import List, Optional, Tuple

REMOTE_CIV = "/sdcard/Download/catalog.civ"
REMOTE_RESULT = "/sdcard/Download/catalog-import-result.txt"
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


def list_devices(adb: str) -> List[DeviceInfo]:
    _ensure_server(adb)
    code, out, err = _run(adb, ["devices", "-l"])
    if code != 0:
        return []
    return [
        DeviceInfo(serial=d.serial, model=d.model or "tablet")
        for d in parse_devices_output(out or err)
        if d.state == "device"
    ]


def diagnose() -> AdbDiagnosis:
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


def push_and_import(adb: str, serial: str, local_civ: str) -> AdbSendResult:
    """Push catalog.civ and trigger silent import on the tablet."""
    prepare_usb(adb, serial)

    # Remove stale result so we don't read an old success.
    _run(adb, ["-s", serial, "shell", "rm", "-f", REMOTE_RESULT], timeout=15)

    code, _, err = _run(
        adb, ["-s", serial, "push", local_civ, REMOTE_CIV], timeout=180,
    )
    if code != 0:
        raise IOError(f"adb push failed: {err or 'unknown error'}")

    with open(local_civ, "rb") as fh:
        digest = hashlib.sha256(fh.read()).hexdigest()

    code, out, err = _run(
        adb,
        [
            "-s", serial, "shell", "am", "broadcast",
            "-a", IMPORT_ACTION,
            "-n", IMPORT_RECEIVER,
        ],
        timeout=30,
    )
    if code != 0:
        raise IOError(f"adb broadcast failed: {err or out or 'unknown error'}")

    result_line = _wait_for_result(adb, serial, timeout_sec=45)
    imported = _parse_import_count(result_line)

    return AdbSendResult(
        device=DeviceInfo(serial=serial, model=""),
        local_path=local_civ,
        remote_path=REMOTE_CIV,
        sha256=digest,
        imported_count=imported,
        result_line=result_line,
    )


def _wait_for_result(adb: str, serial: str, timeout_sec: int = 45) -> str:
    deadline = time.time() + timeout_sec
    while time.time() < deadline:
        code, out, _ = _run(
            adb, ["-s", serial, "shell", "cat", REMOTE_RESULT], timeout=10,
        )
        if code == 0 and out.strip():
            return out.strip()
        time.sleep(0.8)
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


def write_temp_civ(write_fn, books, source_file: str = "") -> str:
    """Write books to a temp .civ file; caller deletes when done."""
    fd, path = tempfile.mkstemp(prefix="catalog-", suffix=".civ")
    os.close(fd)
    write_fn(path, books, source_file)
    return path


def send_books(write_fn, books, source_file: str = "") -> AdbSendResult:
    """High-level: find adb + device, write .civ, push, import, verify."""
    diag = diagnose()
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
    local = write_temp_civ(write_fn, books, source_file)
    try:
        result = push_and_import(adb, device.serial, local)
        result.device = device
        if result.result_line.startswith("ERR:"):
            raise IOError(
                f"Tablet rejected the catalog: {result.result_line}. "
                "The file was pushed but import failed."
            )
        return result
    finally:
        try:
            os.remove(local)
        except OSError:
            pass
