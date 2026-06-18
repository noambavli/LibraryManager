# PyInstaller spec — builds a single-file Windows .exe.
# CI: GitHub Actions workflow build-windows-exe.yml

from PyInstaller.utils.hooks import collect_submodules

block_cipher = None

_library_tool_modules = collect_submodules("library_tool")

a = Analysis(
    ["launcher.py"],
    pathex=["src"],
    binaries=[],
    datas=[],
    hiddenimports=_library_tool_modules + [
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
    name="ExcelTool",
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
