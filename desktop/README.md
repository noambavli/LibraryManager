# LibraryTool — Windows catalog manager for LibraryManager tablets

Offline Windows program that imports a library `.xlsx`, converts it to the
tablet's `.civ` format (catalog.json v4), validates duplicates and problems,
and guides staff through copying the file to the tablet.

Companion to the **LibraryManager** Android app in this repo.

---

## Build the `.exe` (GitHub Actions — recommended)

1. Push this repo to GitHub.
2. Go to **Actions** → **Build LibraryTool Windows .exe** → **Run workflow**.
3. Download **`LibraryTool-windows`** from the run's **Artifacts**.
4. Copy `LibraryTool.exe` to a USB stick for customers. No Python needed to run it.

---

## Build locally on Windows (optional)

```bat
build_windows.bat
```

Output: `dist\LibraryTool.exe`

Requires Python 3.8+ with internet once (to fetch PyInstaller).

---

## Customer workflow (Windows)

See **`CUSTOMER_GUIDE_WINDOWS.txt`** on the USB stick next to `LibraryTool.exe`.

Summary:

1. **LibraryTool** → Import `.xlsx` → Review warnings
2. Export → **Save to Desktop** → `catalog.civ` is written and verified
3. Connect tablet (USB, choose **File transfer**)
4. Drag `catalog.civ` into the tablet's **Download** folder in File Explorer
5. On tablet: **ניהול** → **ייבוא קטלוג מהמחשב** → pick `catalog.civ` → confirm

---

## Excel format

Header row with these columns (Hebrew or English, any order):

| Field | Headers |
|---|---|
| Name | `שם הספר`, `שם`, `name` |
| Author | `המחבר`, `מחבר`, `writer`, `author` |
| Number | `מספר`, `number` |
| Letter | `אות`, `letter` |
| Color | `צבע`, `color` |
| Category | `קטגוריה`, `category` |
| Subcategory | `תת קטגוריה`, `subcategory` |
| Topics | `ענינים`, `עניינים`, `topics` |
| Notes | `הערות`, `הערה`, `notes` |

Generate sample workbooks:

```bat
python tools\make_user_xlsx.py
```

→ `sample\catalog_template.xlsx` (empty template) and `sample\catalog_example.xlsx`.

---

## Tests

```bat
python -m pip install pytest
python -m pytest -q
```

31 tests cover xlsx import, `.civ` format, validation, and the full session flow.

---

## Layout

```
desktop/
├── src/library_tool/     application source (stdlib only)
├── tests/                pytest suite
├── tools/make_user_xlsx.py
├── launcher.py           PyInstaller entry point
├── librarytool.spec
├── build_windows.bat     local Windows build
├── CUSTOMER_GUIDE_WINDOWS.txt
└── .github/workflows/build-windows-exe.yml  (in repo root)
```
