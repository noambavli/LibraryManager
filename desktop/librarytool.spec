# PyInstaller spec — builds a single-file Windows .exe.
# CI: GitHub Actions workflow build-windows-exe.yml
# Local fallback: build_windows.bat

block_cipher = None

a = Analysis(
    ["launcher.py"],
    pathex=["src"],
    binaries=[],
    datas=[],
    hiddenimports=[
        "library_tool",
        "library_tool.app",
        "library_tool.session",
        "library_tool.civ",
        "library_tool.converter",
        "library_tool.validation",
        "library_tool.backups",
        "library_tool.transfer",
        "library_tool.adb_transfer",
        "library_tool.xlsx_reader",
        "library_tool.hebrew",
        "library_tool.model",
        "tkinter",
        "tkinter.ttk",
        "tkinter.filedialog",
        "tkinter.messagebox",
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[
        "numpy", "pandas", "matplotlib", "PIL", "pytest", "PyQt5", "PySide6",
    ],
    noarchive=False,
    optimize=0,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name="LibraryTool",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
