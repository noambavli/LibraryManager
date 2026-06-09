"""LibraryTool — offline Windows companion for the LibraryManager tablet.

Imports the library catalog from a pre-defined .xlsx workbook, converts it into
the tablet-compatible .civ format (a byte-for-byte copy of the tablet's
``catalog.json`` version 4 document), validates it for duplicates and future
problems, and exports it safely to the tablet over USB-C.

The whole package is standard-library only so it packages into a single,
fully-offline Windows .exe with no third-party runtime dependencies.
"""

__version__ = "1.0.0"

# Must match CatalogStore.CATALOG_FORMAT_VERSION on the tablet. Bump only when
# the tablet's persisted book schema changes.
CATALOG_FORMAT_VERSION = 4

APP_NAME = "LibraryTool"
