import json
import os

import pytest

from library_tool import CATALOG_FORMAT_VERSION, civ
from library_tool.converter import convert_rows
from library_tool.model import Book, BookPlace, BookState
from library_tool.validation import validate


def _book(i, name="n", writer="w", display="", **kw):
    base = dict(
        id=f"book-{i:06d}", logicalBookId=f"book-{i:06d}", version=1, isLatest=True,
        name=name, topics="", writer=writer, bookNumber=f"{i:04d}", displayNumber=display,
        letter="", color="", category="", subcategories=[], notes="",
        place=BookPlace.OTZAR, state=BookState.AVAILABLE, parentBookId=None,
        relations=[], createdAt=1, updatedAt=1,
    )
    base.update(kw)
    return Book(**base)


def test_civ_roundtrip_preserves_books(tmp_path):
    books = [_book(1, "בראשית", "רש\"י", "12"), _book(2, "שמות", "ראב\"ע", "13")]
    path = os.path.join(tmp_path, "catalog.civ")
    civ.write_file(path, books)
    doc = civ.read_file(path)
    assert doc.version == CATALOG_FORMAT_VERSION
    assert [b.name for b in doc.books] == ["בראשית", "שמות"]
    assert doc.books[0].place == BookPlace.OTZAR


def test_civ_matches_tablet_json_shape(tmp_path):
    path = os.path.join(tmp_path, "catalog.civ")
    civ.write_file(path, [_book(1)])
    with open(path, encoding="utf-8") as fh:
        root = json.load(fh)
    assert root["version"] == CATALOG_FORMAT_VERSION
    keys = set(root["books"][0].keys())
    expected = {
        "id", "logicalBookId", "version", "isLatest", "name", "topics", "writer",
        "bookNumber", "displayNumber", "letter", "color", "category",
        "subcategories", "notes", "place", "state", "parentBookId", "relations",
        "createdAt", "updatedAt",
    }
    assert keys == expected
    # No-parent serialises as empty string, never null, like the tablet.
    assert root["books"][0]["parentBookId"] == ""


def test_civ_rejects_old_version(tmp_path):
    path = os.path.join(tmp_path, "old.civ")
    with open(path, "w", encoding="utf-8") as fh:
        json.dump({"version": 1, "books": []}, fh)
    with pytest.raises(ValueError):
        civ.read_file(path)


def test_atomic_write_leaves_no_temp_files(tmp_path):
    path = os.path.join(tmp_path, "catalog.civ")
    civ.write_file(path, [_book(1)])
    leftovers = [f for f in os.listdir(tmp_path) if f.endswith(".tmp")]
    assert leftovers == []


def test_write_falls_back_when_rename_unsupported(tmp_path, monkeypatch):
    # Simulate an MTP/USB destination where os.replace is not supported.
    import os as _os

    def boom(src, dst):
        raise OSError("rename not supported on this device")

    monkeypatch.setattr(civ.os, "replace", boom)
    path = os.path.join(tmp_path, "catalog.civ")
    digest = civ.write_file(path, [_book(1, "בראשית")])

    # File is correct and complete despite the failed rename...
    assert civ.file_sha256(path) == digest
    doc = civ.read_file(path)
    assert doc.books[0].name == "בראשית"
    # ...and no temp litter is left behind.
    leftovers = [f for f in os.listdir(tmp_path) if f.endswith(".tmp")]
    assert leftovers == []


def test_validation_detects_duplicate_ids():
    books = [_book(1), _book(1)]  # same id twice
    report = validate(books)
    assert report.has_errors
    assert any(f.code == "dup_id" for f in report.errors)


def test_validation_flags_duplicate_name_author():
    books = [_book(1, "בראשית", "רש\"י"), _book(2, "בראשית", "רש\"י")]
    report = validate(books)
    assert any(f.code == "dup_name_author" for f in report.warnings)
    assert report.duplicate_count >= 2


def test_validation_flags_duplicate_display_number():
    books = [_book(1, display="5"), _book(2, display="5")]
    report = validate(books)
    assert any(f.code == "dup_display_number" for f in report.warnings)


def test_validation_empty_is_error():
    report = validate([])
    assert report.has_errors
    assert any(f.code == "empty" for f in report.errors)


def test_end_to_end_convert_then_civ(tmp_path):
    header = ["שם הספר", "המחבר", "מספר"]
    rows = [header, ["ספר א", "מחבר א", "1"], ["ספר ב", "מחבר ב", "2"]]
    result = convert_rows(rows, now_ms=42)
    path = os.path.join(tmp_path, "out.civ")
    civ.write_file(path, result.books)
    doc = civ.read_file(path)
    assert doc.count == 2
    assert doc.books[1].name == "ספר ב"


# --- Tablet compatibility ---------------------------------------------------
#
# The .civ format must match the tablet's CatalogStore JSON schema EXACTLY.
# These tests pin every aspect the tablet's Kotlin parser depends on, so any
# accidental drift breaks the build, not a real user's catalog.


def test_civ_field_types_are_what_tablet_expects(tmp_path):
    path = os.path.join(tmp_path, "catalog.civ")
    civ.write_file(path, [_book(1, "א", "ב", display="7", parentBookId="book-000099",
                                subcategories=["x", "y"], relations=["z"])])
    with open(path, encoding="utf-8") as fh:
        root = json.load(fh)

    assert isinstance(root["version"], int)
    assert isinstance(root["books"], list)
    b = root["books"][0]

    # String fields the tablet reads with optString / safeString.
    for k in (
        "id", "logicalBookId", "name", "topics", "writer", "bookNumber",
        "displayNumber", "letter", "color", "category", "notes",
        "place", "state", "parentBookId",
    ):
        assert isinstance(b[k], str), f"{k} must be a string, got {type(b[k])}"

    # Number fields the tablet reads with optInt / optLong.
    assert isinstance(b["version"], int)
    assert isinstance(b["createdAt"], int)
    assert isinstance(b["updatedAt"], int)

    # Boolean field the tablet reads with optBoolean.
    assert isinstance(b["isLatest"], bool)

    # Array fields the tablet reads with optJSONArray + toStringList.
    assert isinstance(b["subcategories"], list)
    assert all(isinstance(x, str) for x in b["subcategories"])
    assert isinstance(b["relations"], list)


def test_civ_never_emits_json_null(tmp_path):
    """The tablet treats JSON-null as risky (some Android versions read 'null'
    as the literal string 'null'). The PC writer must always emit '' or [] for
    missing fields, never null."""
    path = os.path.join(tmp_path, "catalog.civ")
    civ.write_file(path, [_book(1, parentBookId=None)])
    raw = open(path, encoding="utf-8").read()
    assert ":null" not in raw, "Found a JSON null in the .civ output"
    assert ", null" not in raw


def test_civ_place_and_state_use_stored_string_values(tmp_path):
    """Tablet's BookPlace.fromStored / BookState.fromStored compare on these
    exact strings. Drift here would silently demote books to defaults."""
    path = os.path.join(tmp_path, "catalog.civ")
    civ.write_file(path, [
        _book(1, place="otzar", state="available"),
        _book(2, place="beis_midrash", state="unavailable"),
        _book(3, place="other", state="in_repair"),
        _book(4, place="", state="available"),
    ])
    with open(path, encoding="utf-8") as fh:
        root = json.load(fh)
    places = [b["place"] for b in root["books"]]
    states = [b["state"] for b in root["books"]]
    assert places == ["otzar", "beis_midrash", "other", ""]
    assert states == ["available", "unavailable", "in_repair", "available"]


def test_civ_hebrew_round_trip_byte_for_byte(tmp_path):
    """Hebrew text + final letters + nikud must survive a write+read with no
    normalisation or loss. Failure here would corrupt every catalog entry."""
    name = "בְּרֵאשִׁית עִם רש\"י — כֶּרֶךְ א'"
    notes = "סוֹף שׁוֹרָה ם ן ץ ף"
    path = os.path.join(tmp_path, "catalog.civ")
    civ.write_file(path, [_book(1, name=name, notes=notes)])
    doc = civ.read_file(path)
    assert doc.books[0].name == name
    assert doc.books[0].notes == notes


def test_zero_byte_civ_is_handled(tmp_path):
    """An empty file is a real-world scenario (user creates a placeholder).
    The PC must treat it as an empty catalog, not a parse error."""
    path = os.path.join(tmp_path, "empty.civ")
    open(path, "w").close()
    doc = civ.parse("")  # the in-memory equivalent
    assert doc.count == 0
    assert doc.version == civ.CATALOG_FORMAT_VERSION
    assert civ.read_file(path).count == 0


def test_corrupt_civ_raises_clear_error(tmp_path):
    path = os.path.join(tmp_path, "bad.civ")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("{not really json")
    with pytest.raises(Exception):
        civ.read_file(path)


def test_civ_export_creates_destination_directory(tmp_path):
    """Exporting to a nested folder that doesn't exist yet must work — users
    routinely export to Tablet/Download which Explorer shows even when it's
    technically absent on the filesystem yet."""
    nested = os.path.join(tmp_path, "tablet", "Download")
    out = os.path.join(nested, "catalog.civ")
    civ.write_file(out, [_book(1)])
    assert os.path.exists(out)


def test_repeated_writes_to_same_destination_overwrite_cleanly(tmp_path):
    path = os.path.join(tmp_path, "catalog.civ")
    civ.write_file(path, [_book(1, "א")])
    civ.write_file(path, [_book(2, "ב"), _book(3, "ג")])
    doc = civ.read_file(path)
    assert doc.count == 2
    assert doc.books[0].name == "ב"
    # No temp files left from either write.
    assert [f for f in os.listdir(tmp_path) if f.endswith(".tmp")] == []
