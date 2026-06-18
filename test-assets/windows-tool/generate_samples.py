#!/usr/bin/env python3
"""Generate sample xlsx files for Windows Tool import testing."""

from pathlib import Path

from openpyxl import Workbook

OUT = Path(__file__).resolve().parent

BOOK_HEADERS = [
    "שם הספר",
    "ענינים",
    "המחבר",
    "מספר",
    "אות",
    "צבע",
    "קטגוריה",
    "תת קטגוריה",
    "הערות",
]

BOOKS = [
    ["בראשית", "בריאת העולם, אבות", 'רש"י', "1", "א", "אדום", "חומש", "תורה", "דוגמה לייבוא"],
    ["שמות", "יציאת מצרים", 'רש"י', "2", "א", "אדום", "חומש", "תורה", ""],
    ["ויקרא", "קרבנות וטהרה", 'רש"י', "3", "א", "אדום", "חומש", "תורה", ""],
    ["במדבר", "מסעות בני ישראל", 'רש"י', "4", "א", "אדום", "חומש", "תורה", ""],
    ["דברים", "מצוות ונאום", 'רש"י', "5", "א", "אדום", "חומש", "תורה", ""],
    ["תהילים", "תפילה ושירה", "דוד", "1", "ב", "כחול", "כתבי קודש", "כתובים", ""],
    ["משלי", "חכמה ומוסר", "שלמה", "2", "ב", "כחול", "כתבי קודש", "כתובים", ""],
    ["שולחן ערוך", "הלכה", "ר' יוסף קארו", "1", "ג", "ירוק", "הלכה", "שולחן ערוך", ""],
    ["משנה ברורה", "פסקי הלכה", "חפץ חיים", "10", "ג", "ירוק", "הלכה", "אחרונים", ""],
    ["ספר הזהר", "קבלה", 'רשב"י', "1", "ד", "סגול", "קבלה", "זוהר", ""],
]

BOOKS_EXTRA = [
    ["איוב", "ייסורים וגבורה", "איוב", "3", "ב", "כחול", "כתבי קודש", "כתובים", "ספר חדש לבדיקת merge"],
    ["רות", "גאולה וחסד", "", "4", "ב", "כחול", "כתבי קודש", "כתובים", ""],
    ["בראשית", "כפילות מכוונת", 'רש"י', "99", "א", "אדום", "חומש", "תורה", "שורה כפולה — אמורה להידלג"],
]

SHORTCUTS = [
    ["חומש"],
    ["תהילים"],
    ["הלכה"],
    ["גמרא"],
    ["משנה"],
    ["קבלה"],
    ["שולחן ערוך"],
]

MATCHING_HEADERS = ["קיצור", "מילים", "כיוון"]
MATCHINGS = [
    ["חומש", "בראשית, שמות, ויקרא", "דו-כיווני"],
    ["הלכה", "שולחן, הלכה", "דו-כיווני"],
    ["גמרא", "בבלי, ירושלמי", "חד-כיווני"],
]


def write_sheet(path: Path, headers: list[str], rows: list[list[str]], sheet_name: str = "Sheet1") -> None:
    wb = Workbook()
    ws = wb.active
    ws.title = sheet_name
    ws.append(headers)
    for row in rows:
        ws.append(row)
    wb.save(path)
    print(f"Wrote {path.name} ({len(rows)} rows)")


def main() -> None:
    write_sheet(OUT / "books_sample.xlsx", BOOK_HEADERS, BOOKS, "books")
    write_sheet(OUT / "books_merge_extra.xlsx", BOOK_HEADERS, BOOKS_EXTRA, "books")
    write_sheet(OUT / "shortcuts_sample.xlsx", ["קיצור"], SHORTCUTS, "shortcuts")
    write_sheet(OUT / "matchings_sample.xlsx", MATCHING_HEADERS, MATCHINGS, "matchings")

    readme = OUT / "README.txt"
    readme.write_text(
        """קבצי בדיקה לכלי Windows
======================

books_sample.xlsx
  10 ספרים לדוגמה. ייבא ראשון (מיזוג — מוסיף חדשים).

books_merge_extra.xlsx
  3 שורות: 2 ספרים חדשים + 1 כפילות של בראשית (לבדיקת "כבר קיים").

shortcuts_sample.xlsx
  7 קיצורי חיפוש. ייבוא מחליף את כל הקיצורים הקיימים.

matchings_sample.xlsx
  3 התאמות חיפוש (אופציונלי). ייבוא מחליף את כל ההתאמות.

איך לבדוק:
1. העתק xlsx לטאבלט (Downloads או USB).
2. ניהול → כלי Windows → ייבוא ספרים / קיצורים.
3. חפש "בראשית", "תהילים", או לחץ על קיצור "חומש".
4. ייבא books_merge_extra אחרי books_sample — איוב ורות יתווספו, בראשית יידלג.
""",
        encoding="utf-8",
    )
    print(f"Wrote {readme.name}")


if __name__ == "__main__":
    main()
