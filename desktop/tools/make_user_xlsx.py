"""Generate ready-to-use .xlsx files for the library staff:

  * sample/catalog_template.xlsx — empty template (header row + 3 example rows
    you can edit / delete / extend with your real books).
  * sample/catalog_example.xlsx  — fuller example with ~15 plausible rows,
    including a couple of deliberate duplicates so the LibraryTool review
    screen has something to show on first use.

Run:  python tools/make_user_xlsx.py
"""

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "tests"))

from _xlsx_writer import write_xlsx  # noqa: E402

HEADER = [
    "שם הספר",
    "המחבר",
    "מספר",
    "אות",
    "צבע",
    "קטגוריה",
    "תת קטגוריה",
    "ענינים",
    "הערות",
]

TEMPLATE_ROWS = [
    ["בראשית עם רש\"י", "רש\"י", "1", "ב", "אדום", "תורה", "חומש", "פרשנות על התורה", "כרך א"],
    ["משנה ברורה", "החפץ חיים", "2", "מ", "כחול", "הלכה", "אורח חיים", "הלכה יומית", ""],
    ["", "", "", "", "", "", "", "", ""],
]

EXAMPLE_ROWS = [
    ["בראשית עם רש\"י", "רש\"י", "101", "ב", "אדום", "תורה", "חומש", "פרשנות על התורה", "כרך א"],
    ["שמות עם רש\"י", "רש\"י", "102", "ש", "אדום", "תורה", "חומש", "פרשנות על התורה", "כרך ב"],
    ["ויקרא עם רש\"י", "רש\"י", "103", "ו", "אדום", "תורה", "חומש", "פרשנות על התורה", "כרך ג"],
    ["במדבר עם רש\"י", "רש\"י", "104", "ב", "אדום", "תורה", "חומש", "פרשנות על התורה", "כרך ד"],
    ["דברים עם רש\"י", "רש\"י", "105", "ד", "אדום", "תורה", "חומש", "פרשנות על התורה", "כרך ה"],
    ["משנה ברורה", "החפץ חיים", "201", "מ", "כחול", "הלכה", "אורח חיים", "הלכה יומית", ""],
    ["שולחן ערוך", "מרן הב\"י", "202", "ש", "כחול", "הלכה", "", "", ""],
    ["קיצור שולחן ערוך", "רבי שלמה גנצפריד", "203", "ק", "כחול", "הלכה", "", "הלכה בקצרה", ""],
    ["מסילת ישרים", "הרמח\"ל", "301", "מ", "ירוק", "מחשבה", "מוסר", "עבודת המידות", ""],
    ["שערי תשובה", "רבנו יונה", "302", "ש", "ירוק", "מחשבה", "מוסר", "תשובה", ""],
    ["תניא", "אדמו\"ר הזקן", "303", "ת", "ירוק", "מחשבה", "חסידות", "", ""],
    ["מורה נבוכים", "הרמב\"ם", "401", "מ", "צהוב", "מחשבה", "פילוסופיה", "", "מהדורת קאפח"],
    ["", "", "", "", "", "", "", "", ""],
    # Deliberate duplicate name+author to demonstrate the duplicate warning.
    ["בראשית עם רש\"י", "רש\"י", "106", "ב", "אדום", "תורה", "חומש", "פרשנות על התורה", "עותק נוסף"],
    # Deliberate duplicate display number (101) to demonstrate that warning too.
    ["ספר נוסף", "מחבר", "101", "ס", "ירוק", "מחשבה", "", "", ""],
]


def main() -> None:
    out_dir = os.path.join(HERE, "..", "sample")
    os.makedirs(out_dir, exist_ok=True)

    template = os.path.join(out_dir, "catalog_template.xlsx")
    write_xlsx(template, [HEADER] + TEMPLATE_ROWS)
    print(f"Wrote {os.path.abspath(template)}")

    example = os.path.join(out_dir, "catalog_example.xlsx")
    write_xlsx(example, [HEADER] + EXAMPLE_ROWS)
    print(f"Wrote {os.path.abspath(example)}")


if __name__ == "__main__":
    main()
