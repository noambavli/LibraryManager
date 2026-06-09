@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0"

echo ============================================================
echo   Authorize this Windows PC for the library tablet
echo   (Technician only — run once per tablet)
echo ============================================================
echo.

if not exist "adb\adb.exe" (
    echo ERROR: adb\adb.exe not found next to this script.
    echo Keep the whole package folder together.
    pause
    exit /b 1
)

set "ANDROID_SDK_HOME=%~dp0adb"
set "PATH=%~dp0adb;%PATH%"

echo Restarting adb...
adb kill-server >nul 2>&1
adb start-server
echo.

echo Plug in the tablet with USB-C.
echo If you ran authorize_from_mac.sh first, the tablet shows a yellow screen.
echo.
pause
echo.

:retry
echo Checking connection...
adb devices -l
echo.

for /f "tokens=1,2" %%A in ('adb devices ^| findstr /v "List"') do (
    if /i "%%B"=="unauthorized" (
        echo.
        echo Tablet %%A is waiting for approval.
        echo On the tablet: tap ALLOW on "Allow USB debugging?"
        echo Waiting 15 seconds...
        timeout /t 15 /nobreak >nul
        goto retry
    )
    if /i "%%B"=="device" (
        echo.
        echo OK — tablet %%A is ready for LibraryTool.
        echo You can close this window and use "Send to tablet" in LibraryTool.
        pause
        exit /b 0
    )
)

echo No tablet detected yet.
echo.
echo Try:
echo   1. Run authorize_from_mac.sh on the Mac first (opens 5-min window)
echo   2. Another USB port on the PC
echo   3. Samsung / Android USB driver for Windows
echo   4. Unplug and replug the cable
echo.
pause
exit /b 1
