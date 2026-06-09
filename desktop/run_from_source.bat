@echo off
REM Run LibraryTool directly from source without building an .exe.
REM Handy for trying it out: needs only Python 3.9+ (with tkinter, which ships
REM with the standard python.org installer).
setlocal
cd /d "%~dp0"
set PYTHONPATH=src
python -m library_tool
