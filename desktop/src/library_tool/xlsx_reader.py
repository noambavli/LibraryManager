"""A minimal, dependency-free reader for ``.xlsx`` (Office Open XML) workbooks.

Mirrors ``data/xlsx/XlsxReader.kt`` on the tablet: an xlsx file is a zip of XML;
we only need the de-duplicated string table (``xl/sharedStrings.xml``) and the
first worksheet grid (``xl/worksheets/sheet1.xml``). Reading them ourselves keeps
the tool free of Apache-POI-style heavyweight dependencies, so packaging into a
single offline .exe stays trivial.

Robustness notes:
  * The first worksheet is resolved via the workbook relationships when possible,
    falling back to ``sheet1.xml`` (which is what the tablet assumes).
  * Inline strings, shared strings, booleans, and raw numbers are all handled.
  * Cells are placed by their column reference (``B3``), so blank cells and
    out-of-order cells never shift data into the wrong column.
"""

from __future__ import annotations

import re
import xml.etree.ElementTree as ET
import zipfile
from typing import List, Optional

_CELL_REF_RE = re.compile(r"^([A-Za-z]+)")


def _strip_ns(tag: str) -> str:
    """``{namespace}row`` -> ``row``."""
    return tag.rsplit("}", 1)[-1]


def column_index(ref: Optional[str]) -> int:
    """Convert a cell reference like ``B3`` to a zero-based column index (1)."""
    if not ref:
        return -1
    m = _CELL_REF_RE.match(ref)
    if not m:
        return -1
    idx = 0
    for ch in m.group(1):
        idx = idx * 26 + (ord(ch.upper()) - ord("A") + 1)
    return idx - 1


def read_first_sheet(path: str) -> List[List[str]]:
    """Read the first worksheet and return rows of trimmed cell strings."""
    with zipfile.ZipFile(path) as zf:
        names = set(zf.namelist())
        shared = _parse_shared_strings(zf) if "xl/sharedStrings.xml" in names else []
        sheet_name = _first_sheet_path(zf, names)
        with zf.open(sheet_name) as fh:
            return _parse_sheet(fh.read(), shared)


def _first_sheet_path(zf: zipfile.ZipFile, names: set) -> str:
    """Resolve the path of the first worksheet. Falls back to sheet1.xml."""
    default = "xl/worksheets/sheet1.xml"
    if "xl/workbook.xml" not in names:
        if default in names:
            return default
        # Last resort: any worksheet xml.
        for n in sorted(names):
            if n.startswith("xl/worksheets/") and n.endswith(".xml"):
                return n
        raise ValueError("xlsx has no worksheets")

    try:
        wb = ET.fromstring(zf.read("xl/workbook.xml"))
        first = None
        for el in wb.iter():
            if _strip_ns(el.tag) == "sheet":
                first = el
                break
        if first is None:
            raise KeyError
        rid = None
        for k, v in first.attrib.items():
            if _strip_ns(k) == "id":
                rid = v
                break
        rels = ET.fromstring(zf.read("xl/_rels/workbook.xml.rels"))
        for rel in rels:
            if rel.attrib.get("Id") == rid:
                target = rel.attrib.get("Target", "")
                target = target.lstrip("/")
                if not target.startswith("xl/"):
                    target = "xl/" + target
                if target in names:
                    return target
    except Exception:
        pass

    if default in names:
        return default
    raise ValueError("could not resolve first worksheet")


def _parse_shared_strings(zf: zipfile.ZipFile) -> List[str]:
    result: List[str] = []
    root = ET.fromstring(zf.read("xl/sharedStrings.xml"))
    for si in root:
        if _strip_ns(si.tag) != "si":
            continue
        # A shared string item may be a single <t> or a run of <r><t> pieces.
        parts: List[str] = []
        for t in si.iter():
            if _strip_ns(t.tag) == "t" and t.text:
                parts.append(t.text)
        result.append("".join(parts))
    return result


def _parse_sheet(data: bytes, shared: List[str]) -> List[List[str]]:
    root = ET.fromstring(data)
    rows: List[List[str]] = []

    for el in root.iter():
        if _strip_ns(el.tag) != "row":
            continue
        cells: dict[int, str] = {}
        max_col = -1
        for c in el:
            if _strip_ns(c.tag) != "c":
                continue
            ref = c.attrib.get("r")
            ctype = c.attrib.get("t")
            value = _cell_value(c, ctype, shared)
            col = column_index(ref)
            if col >= 0:
                cells[col] = value
                if col > max_col:
                    max_col = col
        if cells:
            rows.append([cells.get(i, "") for i in range(max_col + 1)])
    return rows


def _cell_value(c: ET.Element, ctype: Optional[str], shared: List[str]) -> str:
    if ctype == "inlineStr":
        parts = [t.text for t in c.iter() if _strip_ns(t.tag) == "t" and t.text]
        return "".join(parts).strip()

    raw = ""
    for child in c:
        if _strip_ns(child.tag) == "v":
            raw = child.text or ""
            break

    if ctype == "s":
        try:
            return (shared[int(raw)] if raw != "" else "").strip()
        except (ValueError, IndexError):
            return ""
    if ctype == "b":
        return "TRUE" if raw == "1" else "FALSE"
    return raw.strip()
