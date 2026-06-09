from library_tool.adb_transfer import (
    RESULT_PROGRESS,
    _import_timeout_sec,
    _is_final_result,
    _is_progress_result,
    _parse_import_count,
    _parse_logcat_import,
)


def test_is_final_result():
    assert _is_final_result("OK:added=3:skipped=1:total=10")
    assert _is_final_result("ERR:empty")
    assert not _is_final_result(RESULT_PROGRESS)
    assert not _is_final_result("")


def test_is_progress_result():
    assert _is_progress_result(RESULT_PROGRESS)
    assert _is_progress_result("PENDING:added=3:skipped=1:current=100:total=103")
    assert not _is_progress_result("OK:added=1:skipped=0:total=1")


def test_import_timeout_scales_with_size(tmp_path):
    small = tmp_path / "small.civ"
    small.write_text("{}", encoding="utf-8")
    assert _import_timeout_sec(str(small)) == 90

    large = tmp_path / "large.civ"
    large.write_bytes(b"x" * 512_000)
    assert _import_timeout_sec(str(large)) == 140


def test_parse_import_count_new_format():
    assert _parse_import_count("OK:added=5:skipped=2:total=120") == 5


def test_parse_import_count_legacy_format():
    assert _parse_import_count("OK:123") == 123


def test_parse_logcat_import_merged():
    logcat = """
06-09 23:50:19.262 12784 12858 I CatalogImport: Merged catalog: +0 added, 3 skipped, total 8448
"""
    assert _parse_logcat_import(logcat) == "OK:added=0:skipped=3:total=8448"


def test_parse_logcat_import_failure():
    logcat = "06-09 23:50:19.262 12784 12858 E CatalogImport: Import failed: WrongVersion"
    assert _parse_logcat_import(logcat) == "ERR:WrongVersion"


def test_parse_logcat_import_awaiting_confirmation():
    logcat = "06-09 23:50:19.262 12784 12858 I CatalogImport: Awaiting confirmation: +2 to add, 1 skipped"
    assert _parse_logcat_import(logcat) == "PENDING:added=2:skipped=1:current=0:total=0"
