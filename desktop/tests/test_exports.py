import os

from library_tool import civ
from library_tool.exports import exports_dir, list_archives, path_for_batch


def _isolate_exports(tmp_path, monkeypatch):
    tool_dir = tmp_path / ".librarytool"
    tool_dir.mkdir()
    monkeypatch.setattr("library_tool.export_counter._counter_dir", lambda: str(tool_dir))


def test_path_for_batch_uses_numbered_name(tmp_path, monkeypatch):
    _isolate_exports(tmp_path, monkeypatch)
    assert path_for_batch(3) == os.path.join(exports_dir(), "3.civ")


def test_list_archives_sorted(tmp_path, monkeypatch):
    _isolate_exports(tmp_path, monkeypatch)
    root = exports_dir()
    civ.write_file(os.path.join(root, "2.civ"), [], batch_number=2, consume_counter=False)
    civ.write_file(os.path.join(root, "1.civ"), [], batch_number=1, consume_counter=False)

    assert list_archives() == [
        (1, os.path.join(root, "1.civ")),
        (2, os.path.join(root, "2.civ")),
    ]
