@echo off
REM ===========================================================================
REM  Build LibraryTool.exe on Windows  (run this on a Windows PC)
REM
REM  Requirements: Python 3.8+ installed and on PATH (python.org installer).
REM  Internet is needed ONCE to fetch PyInstaller; the built .exe runs offline.
REM
REM  Usage:  double-click this file, or run it from a cmd prompt in desktop\
REM  Output: dist\LibraryTool.exe
REM ===========================================================================
setlocal
cd /d "%~dp0"

REM Force UTF-8 everywhere so old pip's progress bar can't crash on a
REM non-Latin (e.g. Hebrew cp1255) console. This was the cause of the
REM "UnicodeEncodeError ... cp1255" failure.
chcp 65001 >nul
set PYTHONUTF8=1
set PYTHONIOENCODING=utf-8
set PIP_NO_INPUT=1
set PIP_DISABLE_PIP_VERSION_CHECK=1
REM Belt-and-suspenders: turn the progress bar off entirely.
set PIP_PROGRESS_BAR=off

echo.
echo [1/5] Creating build virtual environment...
python -m venv .buildenv || goto :error
call .buildenv\Scripts\activate.bat || goto :error

echo.
echo [2/5] Upgrading pip (old pip versions crash on non-Latin consoles)...
python -m pip install --upgrade --progress-bar off pip || goto :error

echo.
echo [3/5] Installing PyInstaller...
python -m pip install --progress-bar off "pyinstaller>=6.0" || goto :error

echo.
echo [4/5] Running tests (optional - will not block the build)...
python -m pip install --progress-bar off "pytest>=8.0"
if errorlevel 1 (
    echo    WARNING: could not install pytest - skipping tests.
) else (
    python -m pytest -q
    if errorlevel 1 (
        echo    WARNING: tests reported issues - continuing to build anyway.
    )
)

echo.
echo [5/5] Building LibraryTool.exe...
pyinstaller --noconfirm librarytool.spec || goto :error

echo.
echo ===========================================================================
echo  DONE. Your program is here:
echo     %cd%\dist\LibraryTool.exe
echo  Copy that single file anywhere. It runs fully offline.
echo ===========================================================================
echo.
pause
exit /b 0

:error
echo.
echo BUILD FAILED. See the messages above.
echo.
echo Common fixes:
echo   * Make sure you have internet for this build (PyInstaller download).
echo   * If it mentions pip/venv, try deleting the .buildenv folder and re-run.
echo   * Recommended: install a newer Python (3.11 or 3.12) from python.org.
pause
exit /b 1
