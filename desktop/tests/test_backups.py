import json
import os

import pytest

from library_tool import backups


@pytest.fixture(autouse=True)
def isolated_app_data(tmp_path, monkeypatch):
    data_dir = os.path.join(tmp_path, "appdata")
    os.makedirs(data_dir, exist_ok=True)
    monkeypatch.setattr(backups, "app_data_dir", lambda: data_dir)
    yield


def test_list_backups_excludes_meta_sidecars():
    main = os.path.join(backups.backups_dir(), "20250619-import.json")
    meta = main + ".meta.json"
    with open(main, "w", encoding="utf-8") as fh:
        json.dump({"books": [{"id": "1", "name": "A"}]}, fh)
    with open(meta, "w", encoding="utf-8") as fh:
        json.dump({"kind": "import", "book_count": 1}, fh)

    listed = backups.list_backups()
    paths = [e.path for e in listed]
    assert main in paths
    assert meta not in paths
    assert all(not p.endswith(".meta.json") for p in paths)


def test_restore_backup_rejects_empty_payload():
    empty = os.path.join(backups.backups_dir(), "empty.json")
    with open(empty, "w", encoding="utf-8") as fh:
        fh.write('{"books": []}')
    entry = backups.BackupEntry(empty, empty + ".meta.json", 0, "manual", 0, "")
    with pytest.raises(ValueError, match="ריק"):
        backups.restore_backup(entry)
