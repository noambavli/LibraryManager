# ExcelTool — Windows Excel manager for LibraryManager tablets

Offline Windows program that imports a library `.xlsx`, validates duplicates and problems, and sends the workbook to the tablet over USB (adb) for **merge import** — the same Excel format as the tablet **כלי Windows** screen.

Companion to the **LibraryManager** Android app in this repo.

---

## Build the `.exe` (GitHub Actions — recommended)

1. Push this repo to GitHub.
2. Go to **Actions** → **Build ExcelTool Windows .exe** → **Run workflow**.
3. Download **`ExcelTool-windows`** from the run's **Artifacts**.
4. Copy the whole `package/` folder to a USB stick (includes `ExcelTool.exe` + `adb/`).

---

## Build locally on Windows (optional)

```bat
build_windows.bat
```

Output: `dist\ExcelTool.exe`

---

## Customer workflow (Windows)

1. **ExcelTool** → Import `.xlsx` → Review warnings
2. **Send to tablet** (USB connected, adb authorized)
3. On tablet: confirm the import dialog when it appears (adds new books only)

Manual fallback: copy a numbered archive (`1.xlsx`, `2.xlsx`…) from the PC exports folder to the tablet Downloads, then **ניהול → כלי Windows → ייבוא ספרים**.

---

## Excel format

Same headers as the tablet Windows Tool / bundled catalog:

| Field | Headers |
|-------|---------|
| Name | שם הספר, שם, name |
| Topics | ענינים, topics |
| Author | המחבר, מחבר, writer |
| Number | מספר, number |
| Letter | אות, letter |
| Color | צבע, color |
| Category | קטגוריה, category |
| Subcategory | תת קטגוריה, subcategory |
| Notes | הערות, notes |

Import **merges** — existing identical rows are skipped.

---

## Tests

```bat
python -m pytest -q
```

22 tests cover xlsx import, validation, adb helpers, and session flow.
