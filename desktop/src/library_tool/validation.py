"""Pre-flight validation: duplicate detection and future-problem warnings.

Nothing here mutates data — it only inspects a list of ``Book`` records and
produces human-readable findings so the user can make an informed decision
*before* an irreversible export. Findings are graded:

  * ERROR   — will almost certainly cause wrong behaviour on the tablet
              (e.g. duplicate stable IDs collapse two books into one).
  * WARNING — likely a mistake worth a second look (e.g. two books with the
              same name + author, or a row missing a name).
  * INFO    — harmless but worth surfacing (e.g. a row that was skipped).
"""

from __future__ import annotations

from collections import Counter, defaultdict
from dataclasses import dataclass, field
from typing import Dict, List

from .hebrew import normalize
from .model import Book

ERROR = "ERROR"
WARNING = "WARNING"
INFO = "INFO"

_SEVERITY_ORDER = {ERROR: 0, WARNING: 1, INFO: 2}


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
            if f.code in ("dup_id", "dup_name_author", "dup_display_number"):
                total += f.count
        return total

    def summary_line(self) -> str:
        return (
            f"{self.total_books} books · "
            f"{len(self.errors)} errors · "
            f"{len(self.warnings)} warnings · "
            f"{len(self.infos)} notes"
        )


def _trunc(values: List[str], limit: int = 5) -> List[str]:
    shown = [v for v in values[:limit]]
    if len(values) > limit:
        shown.append(f"... (+{len(values) - limit} more)")
    return shown


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
                f"{involved} books share {len(dup_ids)} duplicate internal IDs. "
                "On the tablet these would overwrite each other — only one "
                "survives. Fix the source sheet before exporting.",
                count=involved,
                examples=_trunc(sorted(dup_ids.keys())),
            )
        )

    # --- WARNING: same normalised name + author. Very likely an accidental
    #     double-entry of the same physical book.
    by_name_author: Dict[tuple, List[Book]] = defaultdict(list)
    for b in books:
        key = (normalize(b.name), normalize(b.writer))
        if key[0] or key[1]:
            by_name_author[key].append(b)
    dup_na = {k: v for k, v in by_name_author.items() if len(v) > 1}
    if dup_na:
        involved = sum(len(v) for v in dup_na.values())
        examples = []
        for (n, w), group in list(dup_na.items())[:5]:
            label = group[0].name or "(no name)"
            if group[0].writer:
                label += f" — {group[0].writer}"
            examples.append(f"{label} ×{len(group)}")
        findings.append(
            Finding(
                WARNING,
                "dup_name_author",
                f"{involved} books look like duplicates (same name + author "
                f"across {len(dup_na)} groups). Check whether these are real "
                "copies or accidental double rows.",
                count=involved,
                examples=examples,
            )
        )

    # --- WARNING: duplicate display numbers. Not fatal, but staff use these to
    #     find books, so collisions cause real-world confusion.
    display_counts = Counter(
        b.displayNumber.strip() for b in books if b.displayNumber.strip()
    )
    dup_disp = {k: v for k, v in display_counts.items() if v > 1}
    if dup_disp:
        involved = sum(dup_disp.values())
        findings.append(
            Finding(
                WARNING,
                "dup_display_number",
                f"{involved} books reuse {len(dup_disp)} catalog numbers "
                "(the 'מספר' column). Staff searching by number will get "
                "multiple hits.",
                count=involved,
                examples=_trunc([f"#{k} ×{v}" for k, v in sorted(dup_disp.items())]),
            )
        )

    # --- WARNING: rows with no name at all. The tablet keeps them but they're
    #     unsearchable by title.
    nameless = [b for b in books if not b.name.strip()]
    if nameless:
        findings.append(
            Finding(
                WARNING,
                "missing_name",
                f"{len(nameless)} books have no name. They will be hard to find "
                "on the tablet.",
                count=len(nameless),
                examples=_trunc(
                    [b.displayNumber or b.id for b in nameless]
                ),
            )
        )

    # --- WARNING: rows with only a name and nothing else (likely incomplete).
    bare = [
        b
        for b in books
        if b.name.strip()
        and not b.writer.strip()
        and not b.topics.strip()
        and not b.category.strip()
    ]
    if bare:
        findings.append(
            Finding(
                WARNING,
                "sparse_row",
                f"{len(bare)} books have a name but no author, topics, or "
                "category. They may be incomplete entries.",
                count=len(bare),
                examples=_trunc([b.name for b in bare]),
            )
        )

    # --- INFO: skipped blank rows during import.
    if skipped:
        findings.append(
            Finding(
                INFO,
                "skipped_rows",
                f"{skipped} blank rows in the sheet were skipped during import.",
                count=skipped,
            )
        )

    if not books:
        findings.append(
            Finding(
                ERROR,
                "empty",
                "No books were found. Check that the sheet has a header row with "
                "recognised column names (e.g. שם הספר, המחבר, מספר).",
            )
        )

    findings.sort(key=lambda f: _SEVERITY_ORDER.get(f.severity, 9))
    return ValidationReport(findings=findings, total_books=len(books))


def compare_for_duplicates(new_books: List[Book], existing_books: List[Book]) -> Finding | None:
    """Cross-check a new catalog against what's already on the tablet, by
    normalised name+author. Used before an *export* to warn that the export will
    introduce books that already exist (the tablet import replaces everything, so
    this is informational, but it surfaces drift between PC and tablet)."""
    if not existing_books:
        return None
    existing_keys = {
        (normalize(b.name), normalize(b.writer)) for b in existing_books
    }
    overlap = [
        b
        for b in new_books
        if (normalize(b.name), normalize(b.writer)) in existing_keys
    ]
    if not overlap:
        return None
    return Finding(
        INFO,
        "overlap_with_tablet",
        f"{len(overlap)} of the {len(new_books)} books already exist on the "
        f"tablet (matched by name + author). Exporting replaces the entire "
        "tablet catalog, so nothing is duplicated — but verify the counts look "
        "right.",
        count=len(overlap),
        examples=_trunc([b.name for b in overlap]),
    )
