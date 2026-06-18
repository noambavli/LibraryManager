from library_tool import exports


def test_path_for_batch():
    assert exports.path_for_batch(3).endswith("3.xlsx")


def test_list_archives_sorted(tmp_path, monkeypatch):
    root = tmp_path / "exports"
    root.mkdir()
    monkeypatch.setattr(exports, "exports_dir", lambda: str(root))
    from _xlsx_writer import write_xlsx

    write_xlsx(str(root / "2.xlsx"), [["a"], ["b"]])
    write_xlsx(str(root / "1.xlsx"), [["a"], ["b"]])
    listed = exports.list_archives()
    assert listed == [(1, str(root / "1.xlsx")), (2, str(root / "2.xlsx"))]
