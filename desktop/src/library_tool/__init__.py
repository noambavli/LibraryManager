"""LibraryTool — offline Windows Excel companion for the LibraryManager tablet.

Imports a library catalog from `.xlsx`, validates it, and sends it to the tablet
over USB (adb) for merge import — the same Excel format as the tablet Windows Tool.
"""

__version__ = "2.0.0"

APP_NAME = "ExcelTool"
