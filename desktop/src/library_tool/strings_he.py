"""Hebrew UI strings for the Windows desktop tool (ExcelTool)."""

from __future__ import annotations

# --- Window & header ---------------------------------------------------------

def app_title(version: str) -> str:
    return f"ExcelTool {version} — אקסל ↔ טאבלט"


APP_HEADLINE = "מנהל קטלוג הספרייה"
APP_SUBTITLE = (
    "לא מקוון · ייבוא אקסל → שליחת xlsx לטאבלט ב-USB-C → אישור בטאבלט"
)
NO_FILE_CHOSEN = "טרם נבחר קובץ אקסל — לחצו על שלב 1."
CHOSEN_BOOKS = "נבחר: {src}  —  {n} ספרים מוכנים לשליחה"
WORKING_CATALOG = "קטלוג עבודה: {n} ספרים{dirty}"
UNSAVED_CHANGES = " · שינויים שלא נשמרו"

TABLET_USB_LABEL = "USB טאבלט:"
TABLET_CHECKING = "בודק חיבור…"
TABLET_ADB_MISSING = "adb לא נמצא — השאירו את תיקיית adb\\ ליד LibraryTool.exe"
TABLET_CONNECTED = "מחובר ומאושר — {model}"
TABLET_UNAUTHORIZED = "הטאבלט זוהה אך לא מאושר — הריצו authorize_tablet.bat פעם אחת"
TABLET_NOT_READY = "הטאבלט זוהה אך לא מוכן — נתקו וחברו מחדש את כבל ה-USB"
TABLET_NONE = "אין טאבלט — חברו כבל USB-C"

# --- Steps (catalog) ---------------------------------------------------------

STEPS_FRAME = "שלבים"
BTN_IMPORT_CATALOG = "1 · ייבוא קטלוג (.xlsx)…"
BTN_SAVE_LOCAL = "שמירת xlsx במחשב…"
BTN_SEND_TABLET = "2 · שליחה לטאבלט…"
BTN_RESTORE_IMPORT = "שחזור ייבוא אחרון"
BTN_RESTORE_BACKUP = "שחזור מגיבוי…"

# --- Matchings ---------------------------------------------------------------

MATCHINGS_FRAME = "התאמות חיפוש (מילים נרדפות / קיצורים)"
MATCHINGS_DESC = (
    "עמודות: קיצור · מילים · כיוון. "
    "ייבוא מוסיף קיצורים חדשים ומעדכן קיימים."
)
NO_MATCHINGS_CHOSEN = "טרם נבחר קובץ התאמות — לחצו על ייבוא התאמות."
CHOSEN_MATCHINGS = "נבחר: {src}  —  {n} התאמות מוכנות לשליחה"
BTN_IMPORT_MATCHINGS = "ייבוא התאמות (.xlsx)…"
BTN_SAVE_MATCHINGS = "שמירת התאמות במחשב…"
BTN_SEND_MATCHINGS = "שליחת התאמות לטאבלט…"

# --- Review table ------------------------------------------------------------

REVIEW_FRAME = "בדיקה — כפילויות ובעיות אפשריות"
NO_CATALOG_LOADED = "טרם נטען קטלוג."
COL_LEVEL = "רמה"
COL_FINDING = "ממצא"
COL_BOOKS = "ספרים"
EXAMPLES_PREFIX = "דוגמאות: "

SEVERITY_ERROR = "שגיאה"
SEVERITY_WARNING = "אזהרה"
SEVERITY_INFO = "מידע"

# --- Progress & footer -------------------------------------------------------

BTN_ABORT = "ביטול"
FOOTER_READY = "מוכן."
FOOTER_OFFLINE = "ללא אינטרנט · עובד במלואו offline"
FOOTER_ABORTED = "הפעולה בוטלה — לא בוצעו שינויים."
FOOTER_ERROR = "שגיאה."
WORKING = "עובד…"
ABORTING = "מבטל בנקודה הבטוחה הבאה…"

# --- Dialogs: import ---------------------------------------------------------

DLG_CHOOSE_CATALOG = "בחירת קובץ קטלוג"
DLG_REPLACE_CATALOG_TITLE = "להחליף את הקטלוג הנוכחי?"
DLG_REPLACE_CATALOG_BODY = (
    "יש שינויים שלא נשמרו. ייבוא מחליף את כל קטלוג העבודה "
    "(נשמר גיבוי לפני כן). להמשיך?"
)
DLG_IMPORT_PROBLEMS_TITLE = "הייבוא הסתיים עם בעיות"
DLG_IMPORT_PROBLEMS_BODY = (
    "הקטלוג יובא, אך יש שגיאות שכדאי לתקן בגיליון המקור לפני שליחה לטאבלט. "
    "ראו את טבלת הבדיקה."
)
PROGRESS_IMPORTING = "מייבא…"
FOOTER_IMPORTED = "נבחר: {src} — {n} ספרים נטענו."

FILETYPE_XLSX = "קובץ Excel"
FILETYPE_ALL = "כל הקבצים"

# --- Dialogs: save -------------------------------------------------------------

DLG_SAVE_WORKBOOK = "שמירת קובץ Excel"
DLG_SAVE_FAILED_TITLE = "השמירה נכשלה"
DLG_SAVED_TITLE = "נשמר"
DLG_SAVED_BOOKS = (
    "נשמרו {n} ספרים ב:\n{path}\n\n"
    "לשליחה לטאבלט עדיף שלב 2 (שליחה לטאבלט)."
)
FOOTER_SAVED_BOOKS = "נשמרו {n} ספרים → {fname}"

# --- Dialogs: send books -----------------------------------------------------

DLG_ERRORS_SEND_TITLE = "יש שגיאות — לשלוח בכל זאת?"
DLG_ERRORS_SEND_BODY = (
    "יש {n} בעיות ברמת שגיאה (ראו טבלת הבדיקה). "
    "שליחה לטאבלט עלולה לגרום להתנהגות שגויה.\n\n"
    "לשלוח בכל זאת?"
)
DLG_SEND_TABLET_TITLE = "שליחה לטאבלט"
DLG_SEND_TABLET_BODY = (
    "חברו את הטאבלט ב-USB-C, ואז לחצו כן.\n\n"
    "קובץ שנבחר: {src}\n"
    "שליחה זו יוצרת: {batch}.xlsx\n"
    "ספרים לשליחה: {n}\n\n"
    "בטאבלט יופיע דיאלוג אישור — אשרו כדי להוסיף ספרים חדשים.\n"
    "(מוסיף רק ספרים חדשים; הקטלוג הקיים נשמר.)\n\n"
    "להמשיך?"
)
PROGRESS_SENDING = "שולח לטאבלט…"
SEND_CANCELLED = "השליחה בוטלה בטאבלט ({batch}.xlsx לא מוזג)."
SEND_TIMEOUT = (
    "נשלח {batch}.xlsx — הטאבלט לא אישר בזמן. "
    "פתחו את האפליקציה בטאבלט ואשרו, או השתמשו בניהול → סנכרון."
)
SEND_DONE = "הושלם. נשלח {batch}.xlsx — {count} ספרים חדשים מוזגו (ספרים קיימים נשמרו)."
SEND_PENDING = "נשלח {batch}.xlsx — אשרו בטאבלט לסיום."
DLG_TABLET_SYNC_TITLE = "סנכרון טאבלט"
DLG_TABLET_SYNC_BODY = (
    "{msg}\n\n"
    "אקסל במחשב: {src}\n"
    "קובץ אצווה: {batch}.xlsx\n"
    "ארכיון במחשב: {archive}\n"
    "מכשיר: {model} ({serial})\n\n"
    "אם פספסתם את הדיאלוג — אשרו את הייבוא כשהטאבלט מציג אותו."
)
SYNC_DEVICE_ONLY = "{msg}\n\nמכשיר: {serial}"
DLG_SEND_FAILED_TITLE = "לא ניתן לשלוח לטאבלט"
ADB_DEVICES_HEADER = "\n\nadb devices -l:\n{raw}"

# --- Dialogs: matchings ------------------------------------------------------

DLG_CHOOSE_MATCHINGS = "בחירת קובץ התאמות"
PROGRESS_IMPORT_MATCHINGS = "מייבא התאמות…"
FOOTER_MATCHINGS_LOADED = "נבחר: {src} — {n} התאמות נטענו."
FOOTER_MATCHINGS_INVALID = " ({invalid} שורות לא תקינות דולגו.)"
DLG_NO_MATCHINGS_TITLE = "אין התאמות"
DLG_NO_MATCHINGS_BODY = (
    "בקובץ לא נמצאו שורות התאמות תקינות "
    "(נדרש קיצור + מילים בכל שורה)."
)
DLG_SAVE_MATCHINGS = "שמירת קובץ התאמות"
DLG_SAVED_MATCHINGS = "נשמרו {n} התאמות ב:\n{path}"
FOOTER_SAVED_MATCHINGS = "נשמרו {n} התאמות → {fname}"
DLG_SEND_MATCHINGS_TITLE = "שליחת התאמות לטאבלט"
DLG_SEND_MATCHINGS_BODY = (
    "חברו את הטאבלט ב-USB-C, ואז לחצו כן.\n\n"
    "קובץ שנבחר: {src}\n"
    "שליחה זו יוצרת: matchings-{batch}.xlsx\n"
    "התאמות לשליחה: {n}\n\n"
    "בטאבלט יופיע דיאלוג אישור.\n"
    "קיצורים חדשים יתווספו; לקיימים יעודכנו מילים/כיוון.\n\n"
    "להמשיך?"
)
PROGRESS_SEND_MATCHINGS = "שולח התאמות לטאבלט…"
MATCHINGS_SEND_CANCELLED = "השליחה בוטלה בטאבלט (matchings-{batch}.xlsx לא מוזג)."
MATCHINGS_SEND_TIMEOUT = (
    "נשלח matchings-{batch}.xlsx — הטאבלט לא אישר בזמן. "
    "פתחו את האפליקציה בטאבלט ואשרו את הדיאלוג."
)
MATCHINGS_SEND_DONE = "הושלם. נשלח matchings-{batch}.xlsx — {count} התאמות חדשות מוזגו."
MATCHINGS_SEND_PENDING = "נשלח matchings-{batch}.xlsx — אשרו בטאבלט לסיום."
MATCHINGS_SYNC_BODY = (
    "{msg}\n\n"
    "אקסל במחשב: {src}\n"
    "קובץ אצווה: matchings-{batch}.xlsx\n"
    "ארכיון במחשב: {archive}\n"
    "מכשיר: {model} ({serial})"
)
DLG_SEND_MATCHINGS_FAILED = "לא ניתן לשלוח התאמות לטאבלט"

# --- Dialogs: restore --------------------------------------------------------

DLG_ABORTED_TITLE = "בוטל"
DLG_ABORTED_BODY = "הפעולה בוטלה בבטחה. לא בוצע שינוי."
DLG_ERROR_TITLE = "שגיאה"

DLG_RESTORE_IMPORT_TITLE = "שחזור ייבוא אחרון"
DLG_RESTORE_IMPORT_BODY = (
    "לחזור לקטלוג שהיה לפני הייבוא האחרון? "
    "פעולה בטוחה — לא בוצעו שינויים מאז."
)
DLG_RESTORE_FAILED = "השחזור נכשל"
FOOTER_RESTORED_IMPORT = "שוחזרו {n} ספרים מלפני הייבוא האחרון."

DLG_NO_BACKUPS_TITLE = "אין גיבויים"
DLG_NO_BACKUPS_BODY = "עדיין לא נוצרו גיבויים."
DLG_RESTORE_BACKUP_TITLE = "שחזור מגיבוי"
DLG_RESTORE_BACKUP_PICK = "בחרו נקודת שחזור"
BTN_RESTORE_SELECTED = "שחזור נבחר"
BTN_CANCEL = "ביטול"
FOOTER_RESTORED_BACKUP = "שוחזרו {n} ספרים מגיבוי ({when})."

# --- Session progress --------------------------------------------------------

PROGRESS_SNAPSHOT = "שומר עותק של הקטלוג הנוכחי…"
PROGRESS_READ_WORKBOOK = "קורא את קובץ האקסל…"
PROGRESS_CONVERT = "ממיר שורות…"
PROGRESS_VALIDATE = "בודק כפילויות ובעיות…"
PROGRESS_DONE = "הושלם."
PROGRESS_READ_MATCHINGS = "קורא קובץ התאמות…"
PROGRESS_FIND_TABLET = "מחפש טאבלט (adb)…"
PROGRESS_SEND_EXCEL = "שולח אקסל לטאבלט…"
PROGRESS_SEND_MATCHINGS = "שולח קובץ התאמות לטאבלט…"
PROGRESS_WAIT_CONFIRM = "ממתין לאישור בטאבלט…"
PROGRESS_CONFIRMED_BOOKS = "הטאבלט אישר — הספרים מוזגו."
PROGRESS_CONFIRMED_MATCHINGS = "הטאבלט אישר — ההתאמות מוזגו."
PROGRESS_CANCELLED_TABLET = "בוטל בטאבלט."
PROGRESS_CONFIRM_TIMEOUT = "הטאבלט לא אישר בזמן — אשרו בטאבלט."
PROGRESS_SEND_FINISHED = "השליחה הסתיימה."

HINT_LAST_SEND = (
    "שליחה אחרונה: {batch}.xlsx (נשמר במחשב + Download בטאבלט). "
    "אשרו בטאבלט כשמופיע הדיאלוג."
)
HINT_AFTER_SEND = (
    "לאחר שליחה: קובץ {n}.xlsx נשמר בארכיון (לא נדרס). "
    "אשרו בטאבלט. יש להשאיר USB מחובר."
)
HINT_STEPS = "שלב 1: ייבוא אקסל · שלב 2: שליחה לטאבלט (USB מחובר)"
HINT_LAST_MATCHINGS = (
    "שליחת התאמות אחרונה: matchings-{batch}.xlsx. "
    "אשרו בטאבלט כשמופיע הדיאלוג."
)
HINT_AFTER_MATCHINGS = (
    "לאחר שליחת התאמות: matchings-{n}.xlsx נשמר בארכיון. "
    "אשרו בטאבלט. יש להשאיר USB מחובר."
)

ABORT_BY_USER = "הפעולה בוטלה על ידי המשתמש."
NO_IMPORT_TO_RESTORE = "אין ייבוא לשחזור."

# --- Backups -----------------------------------------------------------------

BACKUP_KIND_IMPORT = "לפני ייבוא"
BACKUP_KIND_EXPORT = "לפני ייצוא"
BACKUP_KIND_DELETE = "לפני מחיקה"
BACKUP_KIND_MANUAL = "נקודת ביקורת ידנית"
BACKUP_LABEL = "{when} · {kind} · {n} ספרים"

# --- Validation --------------------------------------------------------------

VALIDATION_SUMMARY = (
    "{total} ספרים · {errors} שגיאות · {warnings} אזהרות · {infos} הערות"
)
TRUNC_MORE = "... (+עוד {n})"

ISSUE_MESSAGES = {
    "missing_name": "ספרים ללא שם — קשה למצוא בטאבלט",
    "missing_writer": "לספרים עם שם חסר מחבר",
    "missing_letter": "לספרים עם שם חסרה אות מדף",
    "missing_display_number": "לספרים עם שם חסר מספר תצוגה",
    "missing_system_number": "לספרים עם שם חסר מספר מערכת",
    "missing_category": "לספרים עם שם חסרה קטגוריה",
    "number_in_letter_field": "ספרים עם ספרות בשדה האות",
    "letter_in_display_number": "ספרים עם אות בשדה מספר התצוגה",
    "letter_in_system_number": "ספרים עם אות בשדה מספר המערכת",
    "invalid_system_number": "ספרים עם מספר מערכת לא מספרי",
    "dup_record": "ספרים זהים בכל השדות",
    "dup_system_number": "ספרים חולקים מספר מערכת",
    "unknown_parent": "ספרים מצביעים על ספר אב שלא קיים",
    "self_parent": "ספרים מוגדרים כהורה של עצמם",
    "place_not_set": "לספרים עם שם לא הוגדר מקום",
}

FINDING_ISSUE_COUNT = "{count} {hint}."
FINDING_DUP_ID = (
    "{involved} ספרים חולקים {groups} מזהים פנימיים כפולים. "
    "בטאבלט אלה ידרסו זה את זה — רק אחד יישאר. "
    "תקנו את הגיליון לפני שליחה."
)
FINDING_MISSING_NAME = (
    "ל-{count} ספרים אין שם. יהיה קשה למצוא אותם בטאבלט."
)
FINDING_SKIPPED_ROWS = "דולגו {count} שורות ריקות בגיליון במהלך הייבוא."
FINDING_EMPTY = (
    "לא נמצאו ספרים. ודאו שיש שורת כותרות עם שמות עמודות מוכרים "
    "(למשל שם הספר, המחבר, מספר)."
)
FINDING_OVERLAP = (
    "{overlap} מתוך {total} ספרים נכנסים כבר קיימים בטאבלט (לפי מזהה) "
    "וידולגו; {new} יתווספו כשורות חדשות."
)

# --- ADB errors --------------------------------------------------------------

ADB_NOT_FOUND = (
    "adb לא נמצא.\n"
    "השאירו את כל תיקיית התוכנה יחד:\n"
    "  LibraryTool.exe\n"
    "  adb\\adb.exe  (+ AdbWinApi.dll, AdbWinUsbApi.dll)\n"
    "  adb\\.android\\adbkey"
)
ADB_NO_DEVICE = (
    "adb לא רואה מכשיר USB.\n"
    "\n"
    "ב-Windows, נסו לפי הסדר:\n"
    "  1. נתקו וחברו מחדש את כבל ה-USB (ישירות למחשב, לא דרך רכזת)\n"
    "  2. התקינו את מנהל ההתקן Samsung / Android של הטאבלט\n"
    "  3. השאירו את LibraryTool.exe ותיקיית adb\\ באותה תיקייה\n"
    "  4. בקשו מהטכנאי להריץ authorize_tablet.bat פעם אחת\n"
    "\n"
    "adb בשימוש: {adb}"
)
ADB_DEVICES_LINE = "adb devices: {raw}"
ADB_UNAUTHORIZED = (
    "הטאבלט זוהה ({serials}) אך המחשב עדיין לא מאושר.\n\n"
    "טכנאי — להריץ פעם אחת לכל טאבלט:\n"
    "  authorize_tablet.bat   (באותה תיקייה כמו LibraryTool.exe)\n\n"
    "או מ-Mac שכבר עובד עם הטאבלט:\n"
    "  authorize_from_mac.sh\n\n"
    "צוות יומיומי לא צריך לגעת בטאבלט."
)
ADB_OFFLINE = (
    "הטאבלט במצב offline. נתקו את כבל ה-USB, "
    "המתינו 3 שניות, חברו מחדש ונסו שוב."
)
ADB_NOT_READY = "הטאבלט זוהה אך לא מוכן: {details}"
ADB_PUSH_VERIFY_FAILED = "אימות השליחה נכשל: הקובץ בטאבלט לא תואם לעותק במחשב."
ADB_BROADCAST_FAILED = "שידור adb נכשל: {detail}"
ADB_PUSH_FAILED = "adb push נכשל: {detail}"
ADB_UNKNOWN_ERROR = "שגיאה לא ידועה"
ADB_MULTIPLE_TABLETS = (
    "מחוברים {n} טאבלטים. נתקו את העודפים ונסו שוב."
)
ADB_TABLET_REJECTED_BOOKS = (
    "הטאבלט דחה את הקטלוג: {line}. "
    "הקובץ נשלח אך הייבוא נכשל."
)
ADB_TABLET_REJECTED_MATCHINGS = (
    "הטאבלט דחה את ייבוא ההתאמות: {line}. "
    "הקובץ נשלח אך הייבוא נכשל."
)

SEVERITY_DISPLAY = {
    "ERROR": SEVERITY_ERROR,
    "WARNING": SEVERITY_WARNING,
    "INFO": SEVERITY_INFO,
}
