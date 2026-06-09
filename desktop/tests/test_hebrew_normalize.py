import json
from pathlib import Path

from library_tool.hebrew import normalize, normalize_number_key

VECTORS = Path(__file__).with_name("hebrew_normalize_vectors.json")


def _load_vectors():
    with open(VECTORS, encoding="utf-8") as fh:
        return json.load(fh)


def test_normalize_matches_golden_vectors():
    data = _load_vectors()
    for case in data["normalize"]:
        assert normalize(case["input"]) == case["expected"], case["input"]


def test_normalize_number_key_matches_golden_vectors():
    data = _load_vectors()
    for case in data["number_key"]:
        assert normalize_number_key(case["input"]) == case["expected"], case["input"]
