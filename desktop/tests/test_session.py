import os

import pytest

from _xlsx_writer import write_xlsx

from library_tool import backups
from library_tool.session import AbortError, AbortFlag, Session


HEADER = ["שם הספר", "המחבר", "מספר"]


@pytest.fixture(autouse=True)
def isolated_app_data(tmp_path, monkeypatch):
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
    assert not s.can_restore_import()

    s.import_xlsx(second)
    assert len(s.books) == 1
    assert s.can_restore_import()

    n = s.restore_import()
    assert n == 2
    assert [b.name for b in s.books] == ["A", "B"]



def test_abort_during_import_does_not_commit(tmp_path):
    s = Session()
    s.import_xlsx(_sheet(tmp_path, "a.xlsx", [["A", "x", "1"]]))
    before = list(s.books)

    flag = AbortFlag()
    flag.request()
    with pytest.raises(AbortError):
        s.import_xlsx(_sheet(tmp_path, "b.xlsx", [["C", "z", "9"]]), abort=flag)
    assert s.books == before


def test_multiple_imports_keep_session_consistent(tmp_path):
    s = Session()
    s.import_xlsx(_sheet(tmp_path, "a.xlsx", [["A", "x", "1"], ["B", "y", "2"]]))
    s.import_xlsx(_sheet(tmp_path, "b.xlsx", [["C", "z", "3"]]))
    assert [b.name for b in s.books] == ["C"]
