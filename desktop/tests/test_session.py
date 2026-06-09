import os

import pytest

from _xlsx_writer import write_xlsx

from library_tool import backups
from library_tool.session import AbortError, AbortFlag, Session


HEADER = ["שם הספר", "המחבר", "מספר"]


@pytest.fixture(autouse=True)
def isolated_app_data(tmp_path, monkeypatch):
    # Redirect all backups into a temp dir so tests don't touch real app data.
    data_dir = os.path.join(tmp_path, "appdata")
    os.makedirs(data_dir, exist_ok=True)
    monkeypatch.setattr(backups, "app_data_dir", lambda: data_dir)
    yield


def _sheet(tmp_path, name, rows):
    path = os.path.join(tmp_path, name)
    write_xlsx(path, [HEADER] + rows)
    return path


def test_import_then_restore_last_import(tmp_path):
    s = Session()
    first = _sheet(tmp_path, "a.xlsx", [["A", "x", "1"], ["B", "y", "2"]])
    second = _sheet(tmp_path, "b.xlsx", [["C", "z", "3"]])

    s.import_xlsx(first)
    assert len(s.books) == 2
    # No restore possible yet (nothing existed before the first import).
    assert not s.can_restore_import()

    s.import_xlsx(second)
    assert len(s.books) == 1
    assert s.can_restore_import()  # a pre-import snapshot exists, nothing changed

    n = s.restore_import()
    assert n == 2
    assert [b.name for b in s.books] == ["A", "B"]


def test_delete_all_is_reversible(tmp_path):
    s = Session()
    s.import_xlsx(_sheet(tmp_path, "a.xlsx", [["A", "x", "1"], ["B", "y", "2"]]))
    snapshot = s.delete_all()
    assert s.books == []
    n = s.restore_from_backup(snapshot)
    assert n == 2


def test_export_writes_and_verifies(tmp_path):
    s = Session()
    s.import_xlsx(_sheet(tmp_path, "a.xlsx", [["A", "x", "1"]]))
    out_dir = os.path.join(tmp_path, "tablet")
    os.makedirs(out_dir)
    result = s.export(out_dir, "catalog.civ")
    assert result.verified
    assert os.path.exists(result.path)
    assert result.book_count == 1


def test_abort_during_import_does_not_commit(tmp_path):
    s = Session()
    s.import_xlsx(_sheet(tmp_path, "a.xlsx", [["A", "x", "1"]]))
    before = list(s.books)

    flag = AbortFlag()
    flag.request()  # request abort immediately
    with pytest.raises(AbortError):
        s.import_xlsx(_sheet(tmp_path, "b.xlsx", [["C", "z", "9"]]), abort=flag)
    # Working state unchanged because we never reached the commit step.
    assert s.books == before


def test_export_to_nonexistent_nested_folder(tmp_path):
    """Reproduces the common case where the user picks Tablet/Download — a
    folder that needs creating on first use."""
    s = Session()
    s.import_xlsx(_sheet(tmp_path, "a.xlsx", [["A", "x", "1"]]))
    nested = os.path.join(tmp_path, "Tablet", "Download")
    r = s.export(nested, "catalog.civ")
    assert r.verified and os.path.exists(r.path)


def test_export_file_can_be_loaded_back_identically(tmp_path):
    """Round-trip: PC writes a .civ, PC re-reads it, both states are equal.
    This is the same operation the tablet performs to ingest the file."""
    s = Session()
    s.import_xlsx(_sheet(tmp_path, "a.xlsx",
                          [["בראשית", "רש\"י", "1"], ["שמות", "ראב\"ע", "2"]]))
    out_dir = os.path.join(tmp_path, "tablet")
    os.makedirs(out_dir)
    r = s.export(out_dir, "catalog.civ")

    s2 = Session()
    n = s2.load_civ(r.path)
    assert n == 2
    assert [b.name for b in s2.books] == ["בראשית", "שמות"]
    assert [b.writer for b in s2.books] == ["רש\"י", "ראב\"ע"]


def test_delete_all_then_export_produces_empty_but_valid_civ(tmp_path):
    """An export after delete-all is a legitimate (if drastic) operation:
    the produced .civ must still be a valid v4 document the tablet accepts."""
    s = Session()
    s.import_xlsx(_sheet(tmp_path, "a.xlsx", [["A", "x", "1"]]))
    s.delete_all()
    out_dir = os.path.join(tmp_path, "tablet")
    os.makedirs(out_dir)
    r = s.export(out_dir, "catalog.civ")
    assert r.verified and r.book_count == 0

    s2 = Session()
    n = s2.load_civ(r.path)
    assert n == 0


def test_multiple_imports_keep_session_consistent(tmp_path):
    """Importing twice without intervening exports must leave the working
    catalog reflecting the latest import."""
    s = Session()
    s.import_xlsx(_sheet(tmp_path, "a.xlsx", [["A", "x", "1"], ["B", "y", "2"]]))
    s.import_xlsx(_sheet(tmp_path, "b.xlsx", [["C", "z", "3"]]))
    assert [b.name for b in s.books] == ["C"]
