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
class AdbSendResult:
    device: DeviceInfo
    local_path: str
    remote_path: str
    sha256: str
    imported_count: Optional[int]
    result_line: str


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


def _run(adb: str, args: List[str], timeout: int = 120) -> Tuple[int, str, str]:
    try:
        proc = subprocess.run(
            [adb] + args,
            capture_output=True,
            text=True,
            timeout=timeout,
            encoding="utf-8",
            errors="replace",
        )
        return proc.returncode, proc.stdout.strip(), proc.stderr.strip()
    except subprocess.TimeoutExpired:
        return -1, "", "Timed out"
    except FileNotFoundError:
        return -1, "", "adb not found"
    except Exception as e:
        return -1, "", str(e)


def list_devices(adb: str) -> List[DeviceInfo]:
    code, out, _ = _run(adb, ["devices", "-l"])
    if code != 0:
        return []
    devices: List[DeviceInfo] = []
    for line in out.splitlines():
        line = line.strip()
        if not line or line.startswith("List of"):
            continue
        parts = line.split()
        if len(parts) < 2 or parts[1] != "device":
            continue
        serial = parts[0]
        model = "tablet"
        for p in parts[2:]:
            if p.startswith("model:"):
                model = p.split(":", 1)[1]
        devices.append(DeviceInfo(serial=serial, model=model))
    return devices


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
    if line.startswith("OK:"):
        try:
            return int(line[3:])
        except ValueError:
            return None
    return None


def write_temp_civ(serialize_fn, books) -> str:
    """Write books to a temp .civ file; caller deletes when done."""
    fd, path = tempfile.mkstemp(prefix="catalog-", suffix=".civ")
    os.close(fd)
    serialize_fn(path, books)
    return path


def send_books(serialize_fn, books) -> AdbSendResult:
    """High-level: find adb + device, write .civ, push, import, verify."""
    adb = find_adb()
    if not adb:
        raise FileNotFoundError(
            "adb not found. Place adb.exe in an 'adb' folder next to LibraryTool.exe "
            "(included in the download zip from GitHub Actions)."
        )

    devices = list_devices(adb)
    if not devices:
        raise ConnectionError(
            "No tablet detected. Plug in the USB cable and make sure the tablet "
            "was set up as device owner (USB debugging is enabled automatically)."
        )
    if len(devices) > 1:
        raise ConnectionError(
            f"Multiple tablets connected ({len(devices)}). Unplug extras and try again."
        )

    device = devices[0]
    local = write_temp_civ(serialize_fn, books)
    try:
        result = push_and_import(adb, device.serial, local)
        result.device = device
        if result.result_line.startswith("ERR:"):
            raise IOError(
                f"Tablet rejected the catalog: {result.result_line}. "
                "The file was pushed but import failed."
            )
        if result.imported_count is None:
            # Push + broadcast succeeded; result file not written (older app build).
            pass
        return result
    finally:
        try:
            os.remove(local)
        except OSError:
            pass
