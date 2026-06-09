#!/usr/bin/env bash
# Technician: prepare a tablet so Windows LibraryTool can connect.
# Run from a Mac where "adb devices" already shows the tablet as "device".
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
PUB="$ROOT/adb_bundle/.android/adbkey.pub"

if [[ ! -f "$PUB" ]]; then
  echo "Missing $PUB"
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found in PATH"
  exit 1
fi

echo "Checking tablet..."
adb devices -l
COUNT=$(adb devices | awk 'NR>1 && $2=="device" {c++} END {print c+0}')
if [[ "$COUNT" -eq 0 ]]; then
  echo "No authorized tablet connected on this Mac."
  exit 1
fi

echo ""
echo "Step 1/2 — Opening 5-minute maintenance window on the tablet..."
adb shell am broadcast -a com.mh.librarymanager.PREPARE_PC_AUTHORIZE \
  -n com.mh.librarymanager/.PcAuthorizeReceiver
echo "The tablet should show a yellow maintenance screen."

echo ""
echo "Step 2/2 — Trying to install the LibraryTool PC key directly..."
KEY=$(cat "$PUB")
if adb shell "grep -qF '${KEY%% *}' /data/misc/adb/adb_keys 2>/dev/null || echo '$KEY' >> /data/misc/adb/adb_keys" 2>/dev/null; then
  echo "Key installed. Windows should work immediately."
  exit 0
fi

echo "Direct key install not supported on this tablet (normal on Samsung)."
echo ""
echo "NEXT — within 5 minutes, on the Windows PC:"
echo "  1. Unplug the tablet from this Mac"
echo "  2. Plug it into the Windows PC (USB-C)"
echo "  3. Double-click authorize_tablet.bat"
echo "  4. If the tablet asks \"Allow USB debugging?\" — tap ALLOW once"
echo ""
echo "After that, LibraryTool \"Send to tablet\" works forever on that PC."
exit 0
