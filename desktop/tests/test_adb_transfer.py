from library_tool.adb_transfer import (
    _explain_devices,
    parse_devices_output,
)


def test_parse_devices_output_ready():
    out = "List of devices attached\nR52M801ABC\tdevice usb:1-2 product:gta2lte model:SM_T380 device:gta2xx transport_id:1\n"
    parsed = parse_devices_output(out)
    assert len(parsed) == 1
    assert parsed[0].serial == "R52M801ABC"
    assert parsed[0].state == "device"
    assert parsed[0].model == "SM_T380"


def test_parse_devices_output_unauthorized():
    out = "List of devices attached\nR52M801ABC\tunauthorized\n"
    parsed = parse_devices_output(out)
    assert parsed[0].state == "unauthorized"
    msg = _explain_devices(parsed, r"C:\pkg\adb\adb.exe", out)
    assert "not authorized" in msg.lower()
    assert "authorize_tablet.bat" in msg


def test_parse_import_count_merge_format():
    from library_tool.adb_transfer import _parse_import_count

    assert _parse_import_count("OK:added=5:skipped=2:total=120") == 5
    assert _parse_import_count("OK:42") == 42


def test_is_final_result_rejects_stale_garbage():
    from library_tool.adb_transfer import _is_final_result

    assert _is_final_result("OK:added=1:skipped=0:total=10")
    assert _is_final_result("ERR:empty")
    assert not _is_final_result("")
    assert not _is_final_result("still working...")
    assert not _is_final_result("importing")


def test_parse_devices_output_empty():
    out = "List of devices attached\n"
    parsed = parse_devices_output(out)
    msg = _explain_devices(parsed, r"C:\pkg\adb\adb.exe", out)
    assert "No USB device seen" in msg
