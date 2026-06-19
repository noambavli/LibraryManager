"""Pre-flight validation: duplicate detection and future-problem warnings.

Nothing here mutates data — it only inspects a list of ``Book`` records and
produces human-readable findings so the user can make an informed decision
*before* an irreversible export. Findings are graded:

  * ERROR   — will almost certainly cause wrong behaviour on the tablet
              (e.g. duplicate stable IDs collapse two books into one).
  * WARNING — likely a mistake worth a second look (e.g. two books with the
              same name + author, or a row missing a name).
  * INFO    — harmless but worth surfacing (e.g. a row that was skipped).

Issue detection mirrors ``BookOrderIssues.kt`` on the tablet so staff see the
same problems in LibraryTool and in the out-of-order management screen.
"""

from __future__ import annotations

import re
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from typing import Dict, List, Set

from .hebrew import normalize, normalize_number_key
from .model import Book, BookPlace
from . import strings_he as S

ERROR = "ERROR"
WARNING = "WARNING"
INFO = "INFO"

_SEVERITY_ORDER = {ERROR: 0, WARNING: 1, INFO: 2}

_HEBREW = re.compile(r"[\u0590-\u05FF\u05F0-\u05F4]")

_DUPLICATE_CODES = frozenset({
    "dup_id", "dup_record", "dup_system_number",
})

_ISSUE_MESSAGES: Dict[str, str] = S.ISSUE_MESSAGES


@dataclass
class Finding:
    severity: str
    code: str
    message: str
    count: int = 0
    examples: List[str] = field(default_factory=list)


@dataclass
class ValidationReport:
    findings: List[Finding]
    total_books: int

    @property
    def errors(self) -> List[Finding]:
        return [f for f in self.findings if f.severity == ERROR]

    @property
    def warnings(self) -> List[Finding]:
        return [f for f in self.findings if f.severity == WARNING]

    @property
    def infos(self) -> List[Finding]:
        return [f for f in self.findings if f.severity == INFO]

    @property
    def has_errors(self) -> bool:
        return bool(self.errors)

    @property
    def duplicate_count(self) -> int:
        """Total number of books involved in any duplicate finding."""
        total = 0
        for f in self.findings:
            if f.code in _DUPLICATE_CODES:
                total += f.count
        return total

    def summary_line(self) -> str:
        return S.VALIDATION_SUMMARY.format(
            total=self.total_books,
            errors=len(self.errors),
            warnings=len(self.warnings),
            infos=len(self.infos),
        )


@dataclass
class _CatalogContext:
    known_ids: Set[str]
    system_number_counts: Dict[str, int]
    fingerprint_counts: Dict[str, int]

    @classmethod
    def build(cls, books: List[Book]) -> "_CatalogContext":
        system_number_counts: Dict[str, int] = {}
        fingerprint_counts: Dict[str, int] = {}
        for book in books:
            sys_key = _system_number_key(book)
            if sys_key:
                system_number_counts[sys_key] = system_number_counts.get(sys_key, 0) + 1
            fp = _record_fingerprint(book)
            if fp:
                fingerprint_counts[fp] = fingerprint_counts.get(fp, 0) + 1
        return cls(
            known_ids={b.id for b in books},
            system_number_counts=system_number_counts,
            fingerprint_counts=fingerprint_counts,
        )


def _trunc(values: List[str], limit: int = 5) -> List[str]:
    shown = [v for v in values[:limit]]
    if len(values) > limit:
        shown.append(S.TRUNC_MORE.format(n=len(values) - limit))
    return shown


def _record_fingerprint(book: Book) -> str | None:
    """Full-row fingerprint — any differing field means not a duplicate."""
    if not book.name.strip():
        return None
    parts = [
        normalize(book.name),
        normalize(book.writer),
        normalize(book.letter),
        normalize_number_key(book.displayNumber),
        normalize_number_key(book.bookNumber),
        normalize(book.category),
        normalize(book.topics),
        normalize(book.color),
        book.place,
        book.state,
        book.parentBookId or "",
        normalize(book.parentBookName),
        "|".join(sorted(normalize(s) for s in book.subcategories)),
        "|".join(sorted(normalize(r) for r in book.relations)),
        normalize(book.notes),
    ]
    return "\0".join(parts)


def _system_number_key(book: Book) -> str | None:
    key = normalize_number_key(book.bookNumber)
    return key or None


def _field_looks_like_letter(value: str) -> bool:
    trimmed = value.strip()
    if not trimmed:
        return False
    if any(c.isdigit() for c in trimmed):
        return False
    return bool(_HEBREW.search(trimmed)) or trimmed.isalpha()


def _looks_like_system_number(value: str) -> bool:
    trimmed = value.strip()
    if not trimmed:
        return False
    if _HEBREW.search(trimmed):
        return False
    return trimmed.isdigit()


def _book_label(book: Book) -> str:
    label = book.name.strip() or book.id
    if book.writer.strip():
        label += f" — {book.writer.strip()}"
    return label


def _issues_for_book(book: Book, ctx: _CatalogContext) -> Set[str]:
    """Mirror ``BookOrderIssues.issuesFor`` — same rules, same codes."""
    out: Set[str] = set()
    name = book.name.strip()

    if not name:
        out.add("missing_name")

    if name:
        if not book.writer.strip():
            out.add("missing_writer")
        if not book.letter.strip():
            out.add("missing_letter")
        if not book.displayNumber.strip():
            out.add("missing_display_number")
        if not book.bookNumber.strip():
            out.add("missing_system_number")
        if not book.category.strip():
            out.add("missing_category")
        if book.letter.strip() and any(c.isdigit() for c in book.letter):
            out.add("number_in_letter_field")
        if _field_looks_like_letter(book.displayNumber):
            out.add("letter_in_display_number")

    if _field_looks_like_letter(book.bookNumber):
        out.add("letter_in_system_number")
    elif book.bookNumber.strip() and not _looks_like_system_number(book.bookNumber):
        out.add("invalid_system_number")

    # Shelf-position duplicates (same letter + display number) are allowed:
    # multiple volumes legitimately share a shelf slot.
    sys_key = _system_number_key(book)
    if sys_key and ctx.system_number_counts.get(sys_key, 0) > 1:
        out.add("dup_system_number")

    fp = _record_fingerprint(book)
    if fp and ctx.fingerprint_counts.get(fp, 0) > 1:
        out.add("dup_record")

    parent_id = book.parentBookId
    if parent_id == book.id:
        out.add("self_parent")
    elif parent_id and parent_id not in ctx.known_ids:
        out.add("unknown_parent")

    if book.place == BookPlace.UNSPECIFIED and name:
        out.add("place_not_set")

    return out


def _aggregate_issue_findings(books: List[Book]) -> List[Finding]:
    ctx = _CatalogContext.build(books)
    by_code: Dict[str, List[Book]] = defaultdict(list)
    for book in books:
        for code in _issues_for_book(book, ctx):
            by_code[code].append(book)

    findings: List[Finding] = []
    for code, group in sorted(by_code.items()):
        if code == "missing_name":
            continue  # handled separately for clearer messaging
        count = len(group)
        hint = _ISSUE_MESSAGES.get(code, code)
        examples = _trunc([_book_label(b) for b in group])
        if code.startswith("dup_"):
            if code == "dup_system_number":
                examples = []
                seen: Set[str] = set()
                for b in group:
                    key = _system_number_key(b)
                    if key and key not in seen:
                        seen.add(key)
                        n = ctx.system_number_counts[key]
                        examples.append(f"#{key} ×{n}")
                examples = _trunc(examples)
            elif code == "dup_record":
                examples = []
                seen_fp: Set[str] = set()
                for b in group:
                    fp = _record_fingerprint(b)
                    if fp and fp not in seen_fp:
                        seen_fp.add(fp)
                        n = ctx.fingerprint_counts[fp]
                        examples.append(f"{_book_label(b)} ×{n}")
                examples = _trunc(examples)
        findings.append(
            Finding(
                WARNING,
                code,
                S.FINDING_ISSUE_COUNT.format(count=count, hint=hint),
                count=count,
                examples=examples,
            )
        )
    return findings


def validate(books: List[Book], skipped: int = 0) -> ValidationReport:
    findings: List[Finding] = []

    # --- ERROR: duplicate stable ids. These would collapse books on the tablet.
    id_counts = Counter(b.id for b in books)
    dup_ids = {k: v for k, v in id_counts.items() if v > 1}
    if dup_ids:
        involved = sum(dup_ids.values())
        findings.append(
            Finding(
                ERROR,
                "dup_id",
                S.FINDING_DUP_ID.format(involved=involved, groups=len(dup_ids)),
                count=involved,
                examples=_trunc(sorted(dup_ids.keys())),
            )
        )

    # --- WARNING: per-book quality issues (mirrors tablet BookOrderIssues).
    findings.extend(_aggregate_issue_findings(books))

    # --- WARNING: rows with no name (clearer standalone message).
    nameless = [b for b in books if not b.name.strip()]
    if nameless:
        findings.append(
            Finding(
                WARNING,
                "missing_name",
                S.FINDING_MISSING_NAME.format(count=len(nameless)),
                count=len(nameless),
                examples=_trunc(
                    [b.displayNumber or b.id for b in nameless]
                ),
            )
        )

    # --- INFO: skipped blank rows during import.
    if skipped:
        findings.append(
            Finding(
                INFO,
                "skipped_rows",
                S.FINDING_SKIPPED_ROWS.format(count=skipped),
                count=skipped,
            )
        )

    if not books:
        findings.append(
            Finding(
                ERROR,
                "empty",
                S.FINDING_EMPTY,
            )
        )

    findings.sort(key=lambda f: _SEVERITY_ORDER.get(f.severity, 9))
    return ValidationReport(findings=findings, total_books=len(books))


def compare_for_duplicates(new_books: List[Book], existing_books: List[Book]) -> Finding | None:
    """Cross-check a new catalog against what's already on the tablet.

    The tablet merges by stable book ID (existing rows are skipped, new IDs are
    added), so this reports how many incoming rows would be skipped vs added."""
    if not existing_books:
        return None
    existing_ids = {b.id for b in existing_books}
    overlap = [b for b in new_books if b.id in existing_ids]
    if not overlap:
        return None
    new_count = len(new_books) - len(overlap)
    return Finding(
        INFO,
        "overlap_with_tablet",
        S.FINDING_OVERLAP.format(
            overlap=len(overlap),
            total=len(new_books),
            new=new_count,
        ),
        count=len(overlap),
        examples=_trunc([b.name or b.id for b in overlap]),
    )
