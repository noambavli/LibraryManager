import os

from _xlsx_writer import write_xlsx

from library_tool.converter import convert_rows
from library_tool.place_text import from_stored


def test_place_column_imported(tmp_path):
    header = ["שם הספר", "מקום"]
    path = os.path.join(tmp_path, "p.xlsx")
    write_xlsx(path, [header, ["ספר", "אוצר הספרים"], ["שני", "otzar"]])
    from library_tool.xlsx_reader import read_first_sheet

    result = convert_rows(read_first_sheet(path), now_ms=1)
    assert result.imported == 2
    assert result.books[0].place == "אוצר הספרים"
    assert result.books[1].place == "אוצר הספרים"


def test_from_stored_legacy():
    assert from_stored("beis_midrash") == "בית מדרש"
    assert from_stored("") == ""
