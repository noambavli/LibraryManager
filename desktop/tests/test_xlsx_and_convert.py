import os

from _xlsx_writer import write_xlsx

from library_tool.converter import convert_rows
from library_tool.model import BookState
from library_tool.xlsx_reader import column_index, read_first_sheet


HEADER = ["שם הספר", "המחבר", "מספר", "אות", "צבע", "קטגוריה", "תת קטגוריה", "ענינים", "הערות"]


def test_packaged_xlsx_writer_importable():
    """Guard: app.py imports this module — it must ship inside ExcelTool.exe."""
    from library_tool.xlsx_writer import write_xlsx as pkg_write

    assert callable(pkg_write)


def test_column_index():
    assert column_index("A1") == 0
    assert column_index("B3") == 1
    assert column_index("AA10") == 26
    assert column_index(None) == -1


def test_xlsx_roundtrip_reads_header_and_rows(tmp_path):
    path = os.path.join(tmp_path, "cat.xlsx")
    write_xlsx(path, [HEADER, ["בראשית", "רש\"י", "12", "ב", "אדום", "תורה", "חומש", "פרשנות", "כרך א"]])
    rows = read_first_sheet(path)
    assert rows[0] == HEADER
    assert rows[1][0] == "בראשית"
    assert rows[1][2] == "12"


def test_convert_maps_fields_by_header_regardless_of_order(tmp_path):
    # Deliberately reorder columns; header-driven mapping must still work.
    header = ["מספר", "שם הספר", "המחבר"]
    rows = [header, ["7", "ספר הזוהר", "רשב\"י"]]
    result = convert_rows(rows, now_ms=1000)
    assert result.imported == 1
    b = result.books[0]
    assert b.name == "ספר הזוהר"
    assert b.writer == "רשב\"י"
    assert b.displayNumber == "7"
    # Deterministic ids/numbers match the tablet importer.
    assert b.id == "book-000001"
    assert b.bookNumber == "0001"
    assert b.place == ""
    assert b.state == BookState.AVAILABLE
    assert b.createdAt == 1000 and b.updatedAt == 1000


def test_convert_skips_blank_rows():
    header = ["שם הספר", "המחבר"]
    rows = [header, ["", ""], ["שם", "מחבר"], ["", ""]]
    result = convert_rows(rows, now_ms=1)
    assert result.imported == 1
    assert result.skipped == 2


def test_convert_maqaf_in_subcategory_header():
    header = ["שם הספר", "תת־קטגוריה"]
    rows = [header, ["ספר", "חומש"]]
    result = convert_rows(rows, now_ms=1)
    assert result.imported == 1
    assert result.books[0].subcategories == ["חומש"]


def test_convert_nikud_and_final_letters_in_header():
    # Header with nikud and a final letter variant should still match aliases.
    header = ["שֵׁם הספר", "עניינים"]
    rows = [header, ["דבר", "נושא"]]
    result = convert_rows(rows, now_ms=1)
    assert result.imported == 1
    assert result.books[0].name == "דבר"
    assert result.books[0].topics == "נושא"
