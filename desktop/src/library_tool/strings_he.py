"""Hebrew UI strings for the Windows desktop tool (ExcelTool)."""

from __future__ import annotations

# --- Window & header ---------------------------------------------------------

def app_title(version: str) -> str:
    return f"ExcelTool {version} — עדכון קטלוג בטאבלט"


APP_HEADLINE = "עדכון קטלוג הספרים בטאבלט"
APP_SUBTITLE = (
    "מעלים קובץ אקסל מהמחשב → שולחים לטאבלט בכבל USB → מאשרים בטאבלט"
)
NO_FILE_CHOSEN = "עדיין לא נבחר קובץ — התחילו ב״בחר קובץ אקסל״"
CHOSEN_BOOKS = "קובץ נבחר: {src}  ·  {n} ספרים מוכנים לשליחה"
WORKING_CATALOG = "נטענו {n} ספרים{dirty}"
UNSAVED_CHANGES = " · יש שינויים שלא נשמרו"

TABLET_USB_LABEL = "מצב חיבור:"
TABLET_CHECKING = "בודק אם הטאבלט מחובר…"
TABLET_ADB_MISSING = (
    "חסר כלי החיבור — ודאו שתיקיית adb נמצאת ליד ExcelTool.exe"
)
TABLET_CONNECTED = "הטאבלט מחובר ומוכן · {model}"
TABLET_UNAUTHORIZED = (
    "הטאבלט מזוהה אך המחשב לא מאושר — "
    "הטכנאי צריך להריץ authorize_tablet.bat פעם אחת"
)
TABLET_NOT_READY = "הטאבלט מזוהה אך לא מוכן — נתקו את הכבל, חברו מחדש, ונסו שוב"
TABLET_NONE = "הטאבלט לא מחובר — חברו כבל USB למחשב"

# --- Steps (catalog) ---------------------------------------------------------

STEPS_FRAME = "עדכון ספרים בטאבלט"
STEPS_HELP = (
    "① בחרו קובץ אקסל  →  ② בדקו את הטבלה למטה  →  ③ שלחו לטאבלט.\n"
    "רק ספרים חדשים יתווספו — ספרים שכבר בטאבלט לא יימחקו ולא יוחלפו."
)
BTN_IMPORT_CATALOG = "① בחר קובץ אקסל…"
BTN_SAVE_LOCAL = "שמור עותק במחשב…"
BTN_SEND_TABLET = "② שלח לטאבלט…"
BTN_RESTORE_IMPORT = "בטל ייבוא אחרון"
BTN_RESTORE_BACKUP = "שחזר מגיבוי…"

# --- Matchings ---------------------------------------------------------------

MATCHINGS_FRAME = "עדכון התאמות חיפוש (קיצורים)"
MATCHINGS_DESC = (
    "קיצורי חיפוש ומילים נרדפות — למשל קיצור ״רש״י״ שמחפש גם ״רashi״.\n"
    "עמודות בקובץ: קיצור · מילים · כיוון. חדשים מתווספים, קיימים מתעדכנים."
)
NO_MATCHINGS_CHOSEN = "עדיין לא נבחר קובץ התאמות"
CHOSEN_MATCHINGS = "קובץ נבחר: {src}  ·  {n} התאמות מוכנות לשליחה"
BTN_IMPORT_MATCHINGS = "בחר קובץ התאמות…"
BTN_SAVE_MATCHINGS = "שמור עותק במחשב…"
BTN_SEND_MATCHINGS = "שלח התאמות לטאבלט…"

# --- Beis Midrash books (separate library) ----------------------------------

BEIS_FRAME = "עדכון ספרי בית מדרש"
BEIS_DESC = (
    "ספרי בית המדרש הם ספרייה נפרדת מאוצר הספרים (מיקום לפי עמודה + צבע).\n"
    "עמודות בקובץ: שם הספר · ענינים · המחבר · עמודה · מדף · צבע · הערות.\n"
    "חדשים מתווספים, קיימים לא יוחלפו. הספרים ישויכו אוטומטית לבית המדרש."
)
NO_BEIS_CHOSEN = "עדיין לא נבחר קובץ ספרי בית מדרש"
CHOSEN_BEIS = "קובץ נבחר: {src}  ·  {n} ספרי בית מדרש מוכנים לשליחה"
BTN_IMPORT_BEIS = "בחר קובץ בית מדרש…"
BTN_SAVE_BEIS = "שמור עותק במחשב…"
BTN_SEND_BEIS = "שלח בית מדרש לטאבלט…"

# --- Review table ------------------------------------------------------------

REVIEW_FRAME = "בדיקה לפני שליחה"
REVIEW_HELP = (
    "שגיאות (אדום) — תקנו באקסל לפני השליחה. "
    "אזהרות (צהוב) — מומלץ לבדוק, אפשר לשלוח."
)
NO_CATALOG_LOADED = "טענו קובץ אקסל כדי לראות כאן בעיות וכפילויות"
COL_LEVEL = "חומרה"
COL_FINDING = "מה מצאנו"
COL_BOOKS = "כמות"
EXAMPLES_PREFIX = "דוגמאות: "

SEVERITY_ERROR = "שגיאה"
SEVERITY_WARNING = "אזהרה"
SEVERITY_INFO = "מידע"

# --- Progress & footer -------------------------------------------------------

BTN_ABORT = "עצור"
FOOTER_READY = "מוכן לעבודה"
FOOTER_OFFLINE = "לא נדרש אינטרנט"
FOOTER_ABORTED = "הפעולה נעצרה — שום דבר לא השתנה"
FOOTER_ERROR = "אירעה שגיאה"
WORKING = "עובד…"
ABORTING = "עוצר…"

# --- Dialogs: import ---------------------------------------------------------

DLG_CHOOSE_CATALOG = "בחירת קובץ קטלוג הספרים"
DLG_REPLACE_CATALOG_TITLE = "יש שינויים שלא נשמרו"
DLG_REPLACE_CATALOG_BODY = (
    "בחירת קובץ אקסל אחר תחליף רק את מה שמוצג כאן ב-ExcelTool על המחשב.\n"
    "הספרים בטאבלט לא נמחקים ולא משתנים — שליחה לטאבלט מוסיפה רק ספרים חדשים.\n"
    "לפני כן נשמר גיבוי אוטומטי.\n\n"
    "להמשיך בייבוא?"
)
DLG_IMPORT_PROBLEMS_TITLE = "הקובץ נטען — יש שגיאות"
DLG_IMPORT_PROBLEMS_BODY = (
    "הקובץ עלה בהצלחה, אבל יש שגיאות שכדאי לתקן באקסל לפני שליחה לטאבלט.\n\n"
    "ראו את הטבלה למטה (שורות באדום)."
)
PROGRESS_IMPORTING = "טוען קובץ אקסל…"
FOOTER_IMPORTED = "נטענו {n} ספרים מקובץ {src}"

ERR_GENERIC = "משהו השתבש. נסו שוב."
ERR_FILE_NOT_FOUND = "הקובץ לא נמצא — ייתכן שהועבר או נמחק."
ERR_NOT_XLSX = "זה לא קובץ Excel (.xlsx) — בחרו את קובץ הקטלוג הנכון."
ERR_FILE_LOCKED = "לא ניתן לפתוח את הקובץ — סגרו אותו ב-Excel ונסו שוב."
ERR_READ_XLSX = "לא הצלחנו לקרוא את הקובץ:\n{detail}"

DLG_IMPORT_OK_TITLE = "הקובץ נטען בהצלחה"
DLG_IMPORT_OK_BODY = (
    "נטענו {n} ספרים.\n\n"
    "השלב הבא: בדקו את הטבלה למטה, ואז לחצו ״שלח לטאבלט״."
)
DLG_IMPORT_EMPTY_TITLE = "לא נמצאו ספרים בקובץ"
DLG_IMPORT_EMPTY_BODY = (
    "הקובץ ריק או שאין בו שורות ספרים.\n\n"
    "ודאו שיש שורת כותרות עם: שם הספר, המחבר, מספר, אות, קטגוריה וכו׳."
)
DLG_IMPORT_NO_NAME_TITLE = "חסרה עמודת ״שם הספר״"
DLG_IMPORT_NO_NAME_BODY = (
    "בשורת הכותרות חייבת להיות עמודה בשם ״שם הספר״.\n"
    "בלי זה לא ניתן לטעון את הקטלוג."
)
DLG_REPLACE_ANY_TITLE = "לטעון קובץ אקסל אחר?"
DLG_REPLACE_ANY_BODY = (
    "ב-ExcelTool על המחשב מוצגים כרגע {n} ספרים.\n"
    "קובץ חדש יחליף רק את מה שמוצג כאן — לא את הספרים בטאבלט.\n"
    "שליחה לטאבלט מוסיפה רק ספרים חדשים; קיימים נשארים כמו שהם.\n\n"
    "להמשיך?"
)

DLG_REPLACE_MATCHINGS_TITLE = "לטעון קובץ התאמות אחר?"
DLG_REPLACE_MATCHINGS_BODY = (
    "ב-ExcelTool על המחשב טעונות כרגע {n} התאמות.\n"
    "קובץ חדש יחליף רק את מה שמוצג כאן — לא את ההתאמות בטאבלט.\n\n"
    "להמשיך?"
)

FILETYPE_XLSX = "קובץ Excel (.xlsx)"
FILETYPE_ALL = "כל הקבצים"

# --- Dialogs: save -------------------------------------------------------------

DLG_SAVE_WORKBOOK = "שמירת עותק במחשב"
DLG_SAVE_FAILED_TITLE = "השמירה נכשלה"
DLG_SAVED_TITLE = "נשמר במחשב"
DLG_SAVED_BOOKS = (
    "נשמרו {n} ספרים ב:\n{path}\n\n"
    "לעדכון הטאבלט — השתמשו ב״שלח לטאבלט״ (לא צריך לשמור ידנית)."
)
FOOTER_SAVED_BOOKS = "נשמר {fname} ({n} ספרים)"

# --- Dialogs: send books -----------------------------------------------------

DLG_ERRORS_SEND_TITLE = "יש שגיאות — לשלוח בכל זאת?"
DLG_ERRORS_SEND_BODY = (
    "נמצאו {n} שגיאות בטבלה (אדום).\n"
    "שליחה עם שגיאות עלולה לגרום לבעיות בטאבלט.\n\n"
    "מומלץ לתקן באקסל קודם.\n"
    "לשלוח בכל זאת?"
)
DLG_SEND_TABLET_TITLE = "לשלוח לטאבלט?"
DLG_SEND_TABLET_BODY = (
    "ודאו שהטאבלט מחובר ב-USB.\n\n"
    "קובץ מקור: {src}\n"
    "יישלח כ: {batch}.xlsx\n"
    "ספרים לשליחה: {n}\n\n"
    "מה יקרה:\n"
    "• בטאבלט יופיע חלון — לחצו אישור\n"
    "• רק ספרים חדשים יתווספו\n"
    "• ספרים שכבר בטאבלט — לא ישתנו\n\n"
    "לשלוח?"
)
PROGRESS_SENDING = "שולח לטאבלט — אל תנתקו את הכבל…"
SEND_CANCELLED = "בוטל בטאבלט — הקובץ {batch}.xlsx לא נוסף."
SEND_TIMEOUT = (
    "הקובץ נשלח ({batch}.xlsx) אך לא התקבל אישור בזמן.\n"
    "פתחו את אפליקציית הספרייה בטאבלט ואשרו את הייבוא."
)
SEND_DONE = "הושלם! {count} ספרים חדשים נוספו לטאבלט (קובץ {batch}.xlsx)."
SEND_PENDING = "הקובץ {batch}.xlsx נשלח — המתינו לאישור בטאבלט."
DLG_TABLET_SYNC_TITLE = "השליחה הסתיימה"
DLG_TABLET_SYNC_BODY = (
    "{msg}\n\n"
    "קובץ מקור: {src}\n"
    "קובץ שנשלח: {batch}.xlsx\n"
    "עותק שנשמר במחשב: {archive}\n"
    "טאבלט: {model}\n\n"
    "לא ראיתם חלון אישור? פתחו את האפליקציה בטאבלט — הוא אמור להופיע שם."
)
SYNC_DEVICE_ONLY = "{msg}\n\nמזהה טאבלט: {serial}"
DLG_SEND_FAILED_TITLE = "השליחה לטאבלט נכשלה"
ADB_DEVICES_HEADER = "\n\nפרטי חיבור:\n{raw}"

# --- Dialogs: matchings ------------------------------------------------------

DLG_CHOOSE_MATCHINGS = "בחירת קובץ התאמות חיפוש"
PROGRESS_IMPORT_MATCHINGS = "טוען קובץ התאמות…"
FOOTER_MATCHINGS_LOADED = "נטענו {n} התאמות מקובץ {src}"
FOOTER_MATCHINGS_INVALID = " ({invalid} שורות לא תקינות — דולגו)"
DLG_NO_MATCHINGS_TITLE = "לא נמצאו התאמות"
DLG_NO_MATCHINGS_BODY = (
    "הקובץ ריק או שאין בו שורות תקינות.\n\n"
    "כל שורה צריכה: קיצור + מילים (לפחות אחת)."
)
DLG_SAVE_MATCHINGS = "שמירת קובץ התאמות"
DLG_SAVED_MATCHINGS = "נשמרו {n} התאמות ב:\n{path}"
FOOTER_SAVED_MATCHINGS = "נשמר {fname} ({n} התאמות)"
DLG_SEND_MATCHINGS_TITLE = "לשלוח התאמות לטאבלט?"
DLG_SEND_MATCHINGS_BODY = (
    "ודאו שהטאבלט מחובר ב-USB.\n\n"
    "קובץ מקור: {src}\n"
    "יישלח כ: matchings-{batch}.xlsx\n"
    "התאמות לשליחה: {n}\n\n"
    "מה יקרה:\n"
    "• בטאבלט יופיע חלון — לחצו אישור\n"
    "• קיצורים חדשים יתווספו\n"
    "• קיצורים קיימים — יעודכנו המילים והכיוון\n\n"
    "לשלוח?"
)
PROGRESS_SEND_MATCHINGS = "שולח התאמות — אל תנתקו את הכבל…"
MATCHINGS_SEND_CANCELLED = "בוטל בטאבלט — matchings-{batch}.xlsx לא נוסף."
MATCHINGS_SEND_TIMEOUT = (
    "הקובץ נשלח (matchings-{batch}.xlsx) אך לא התקבל אישור בזמן.\n"
    "פתחו את האפליקציה בטאבלט ואשרו."
)
MATCHINGS_SEND_DONE = "הושלם! {count} התאמות חדשות נוספו לטאבלט."
MATCHINGS_SEND_PENDING = "הקובץ matchings-{batch}.xlsx נשלח — המתינו לאישור בטאבלט."
MATCHINGS_SYNC_BODY = (
    "{msg}\n\n"
    "קובץ מקור: {src}\n"
    "קובץ שנשלח: matchings-{batch}.xlsx\n"
    "עותק במחשב: {archive}\n"
    "טאבלט: {model}"
)
DLG_SEND_MATCHINGS_FAILED = "שליחת ההתאמות נכשלה"

# --- Dialogs: beis midrash ---------------------------------------------------

DLG_CHOOSE_BEIS = "בחירת קובץ ספרי בית מדרש"
PROGRESS_IMPORT_BEIS = "טוען קובץ בית מדרש…"
FOOTER_BEIS_LOADED = "נטענו {n} ספרי בית מדרש מקובץ {src}"
DLG_NO_BEIS_TITLE = "לא נמצאו ספרי בית מדרש"
DLG_NO_BEIS_BODY = (
    "הקובץ ריק או שאין בו שורות תקינות.\n\n"
    "ודאו שיש שורת כותרות: שם הספר, המחבר, עמודה, מדף, צבע."
)
DLG_REPLACE_BEIS_TITLE = "לטעון קובץ בית מדרש אחר?"
DLG_REPLACE_BEIS_BODY = (
    "ב-ExcelTool על המחשב טעונים כרגע {n} ספרי בית מדרש.\n"
    "קובץ חדש יחליף רק את מה שמוצג כאן — לא את הספרים בטאבלט.\n\n"
    "להמשיך?"
)
DLG_SAVE_BEIS = "שמירת קובץ ספרי בית מדרש"
DLG_SAVED_BEIS = "נשמרו {n} ספרי בית מדרש ב:\n{path}"
FOOTER_SAVED_BEIS = "נשמר {fname} ({n} ספרי בית מדרש)"
DLG_SEND_BEIS_TITLE = "לשלוח ספרי בית מדרש לטאבלט?"
DLG_SEND_BEIS_BODY = (
    "ודאו שהטאבלט מחובר ב-USB.\n\n"
    "קובץ מקור: {src}\n"
    "יישלח כ: beis-{batch}.xlsx\n"
    "ספרים לשליחה: {n}\n\n"
    "מה יקרה:\n"
    "• בטאבלט יופיע חלון — לחצו אישור\n"
    "• רק ספרים חדשים יתווספו לבית המדרש\n"
    "• ספרי אוצר הספרים לא יושפעו\n\n"
    "לשלוח?"
)
PROGRESS_SEND_BEIS = "מעלה ספרי בית מדרש לטאבלט…"
PROGRESS_CONFIRMED_BEIS = "אושר בטאבלט — ספרי בית המדרש נוספו."
BEIS_SEND_CANCELLED = "בוטל בטאבלט — beis-{batch}.xlsx לא נוסף."
BEIS_SEND_TIMEOUT = (
    "הקובץ נשלח (beis-{batch}.xlsx) אך לא התקבל אישור בזמן.\n"
    "פתחו את האפליקציה בטאבלט ואשרו."
)
BEIS_SEND_DONE = "הושלם! {count} ספרי בית מדרש חדשים נוספו לטאבלט."
BEIS_SEND_PENDING = "הקובץ beis-{batch}.xlsx נשלח — המתינו לאישור בטאבלט."
BEIS_SYNC_BODY = (
    "{msg}\n\n"
    "קובץ מקור: {src}\n"
    "קובץ שנשלח: beis-{batch}.xlsx\n"
    "עותק במחשב: {archive}\n"
    "טאבלט: {model}"
)
DLG_SEND_BEIS_FAILED = "שליחת ספרי בית מדרש נכשלה"

# --- Dialogs: restore --------------------------------------------------------

DLG_ABORTED_TITLE = "הופסק"
DLG_ABORTED_BODY = "הפעולה הופסקה. לא בוצע שינוי."
DLG_ERROR_TITLE = "שגיאה"

DLG_RESTORE_IMPORT_TITLE = "לבטל את הייבוא האחרון?"
DLG_RESTORE_IMPORT_BODY = (
    "יחזיר את הקטלוג למצב שלפני הייבוא האחרון.\n"
    "שימושי אם טעיתם בקובץ שבחרתם."
)
DLG_RESTORE_FAILED = "השחזור נכשל"
FOOTER_RESTORED_IMPORT = "הוחזרו {n} ספרים (לפני הייבוא האחרון)"

DLG_NO_BACKUPS_TITLE = "אין גיבויים"
DLG_NO_BACKUPS_BODY = "עדיין לא נוצרו גיבויים אוטומטיים."
DLG_RESTORE_BACKUP_TITLE = "שחזור מגיבוי"
DLG_RESTORE_BACKUP_PICK = "בחרו מתי לחזור:"
DLG_RESTORE_CONFIRM_TITLE = "לשחזר מהגיבוי?"
DLG_RESTORE_CONFIRM_BODY = (
    "פעולה זו תחליף את הספרים שמוצגים כעת ב-ExcelTool על המחשב\n"
    "בנתונים מהגיבוי שנוצר ב-{when}.\n"
    "הספרים בטאבלט לא מושפעים.\n\n"
    "להמשיך?"
)
BTN_RESTORE_SELECTED = "שחזר"
BTN_CANCEL = "ביטול"
FOOTER_RESTORED_BACKUP = "שוחזרו {n} ספרים (גיבוי מ-{when})"

# --- Session progress --------------------------------------------------------

PROGRESS_SNAPSHOT = "שומר גיבוי לפני שינוי…"
PROGRESS_READ_WORKBOOK = "קורא קובץ אקסל…"
PROGRESS_CONVERT = "מעבד שורות…"
PROGRESS_VALIDATE = "בודק כפילויות ובעיות…"
PROGRESS_DONE = "סיום."
PROGRESS_READ_MATCHINGS = "קורא קובץ התאמות…"
PROGRESS_FIND_TABLET = "מחפש טאבלט מחובר…"
PROGRESS_SEND_EXCEL = "מעלה קובץ לטאבלט…"
PROGRESS_SEND_MATCHINGS = "מעלה התאמות לטאבלט…"
PROGRESS_WAIT_CONFIRM = "ממתין שתאשרו בטאבלט…"
PROGRESS_CONFIRMED_BOOKS = "אושר בטאבלט — הספרים נוספו."
PROGRESS_CONFIRMED_MATCHINGS = "אושר בטאבלט — ההתאמות עודכנו."
PROGRESS_CANCELLED_TABLET = "בוטל בטאבלט."
PROGRESS_CONFIRM_TIMEOUT = "לא התקבל אישור בזמן — בדקו את הטאבלט."
PROGRESS_SEND_FINISHED = "השליחה הסתיימה."

HINT_LAST_SEND = (
    "הקובץ {batch}.xlsx נשלח — אשרו בטאבלט כשמופיע החלון."
)
HINT_AFTER_SEND = (
    "לחצו ״שלח לטאבלט״ — הקובץ {n}.xlsx יישמר ויישלח. "
    "השאירו את הכבל מחובר עד שמאשרים בטאבלט."
)
HINT_STEPS = "התחילו: בחרו קובץ אקסל ← בדקו טבלה ← שלחו לטאבלט"
HINT_LAST_MATCHINGS = (
    "matchings-{batch}.xlsx נשלח — אשרו בטאבלט."
)
HINT_AFTER_MATCHINGS = (
    "לחצו ״שלח התאמות לטאבלט״ — הקובץ matchings-{n}.xlsx יישלח. "
    "השאירו USB מחובר."
)
HINT_LAST_BEIS = (
    "beis-{batch}.xlsx נשלח — אשרו בטאבלט."
)
HINT_AFTER_BEIS = (
    "לחצו ״שלח בית מדרש לטאבלט״ — הקובץ beis-{n}.xlsx יישלח. "
    "השאירו USB מחובר."
)

ABORT_BY_USER = "הפעולה הופסקה."
NO_IMPORT_TO_RESTORE = "אין ייבוא קודם לשחזור."

# --- Backups -----------------------------------------------------------------

BACKUP_KIND_IMPORT = "לפני ייבוא"
BACKUP_KIND_EXPORT = "לפני שליחה"
BACKUP_KIND_DELETE = "לפני מחיקה"
BACKUP_KIND_MANUAL = "גיבוי ידני"
BACKUP_LABEL = "{when} · {kind} · {n} ספרים"

# --- Validation --------------------------------------------------------------

VALIDATION_SUMMARY = (
    "{total} ספרים · {errors} שגיאות · {warnings} אזהרות · {infos} הערות"
)
TRUNC_MORE = "... (ועוד {n})"

ISSUE_MESSAGES = {
    "missing_name": "ספרים בלי שם — לא ניתן לחפש אותם בטאבלט",
    "missing_writer": "ספרים בלי שם מחבר",
    "missing_letter": "ספרים בלי אות מדף",
    "missing_display_number": "ספרים בלי מספר תצוגה (עמודת ״מספר״)",
    "missing_system_number": "ספרים בלי מספר מערכת",
    "missing_category": "ספרים בלי קטגוריה",
    "number_in_letter_field": "בשדה ״אות״ יש ספרות במקום אות",
    "letter_in_display_number": "בשדה ״מספר״ יש אות במקום מספר",
    "letter_in_system_number": "במספר המערכת יש אות במקום ספרות",
    "invalid_system_number": "מספר מערכת לא תקין (צריך להיות מספר)",
    "dup_record": "שורות כפולות — אותו ספר מופיע פעמיים",
    "dup_system_number": "שני ספרים עם אותו מספר מערכת",
    "unknown_parent": "ספר מצביע על ספר אב שלא קיים",
    "self_parent": "ספר מוגדר כהורה של עצמו",
    "place_not_set": "ספרים בלי מקום (עמודת ״מקום״)",
}

FINDING_ISSUE_COUNT = "{count} {hint}"
FINDING_DUP_ID = (
    "{involved} ספרים עם מזהה כפול ({groups} כפילויות) — בטאבלט רק אחד מכל קבוצה יישאר. "
    "תקנו באקסל לפני שליחה."
)
FINDING_MISSING_NAME = (
    "{count} ספרים בלי שם — לא יופיעו בחיפוש בטאבלט"
)
FINDING_SKIPPED_ROWS = "דולגו {count} שורות ריקות בקובץ"
FINDING_EMPTY = (
    "לא נמצאו ספרים. ודאו שיש שורת כותרות: שם הספר, המחבר, מספר וכו׳"
)
FINDING_OVERLAP = (
    "{overlap} ספרים כבר קיימים בטאבלט וידולגו · "
    "{new} ספרים חדשים יתווספו"
)

# --- ADB errors (shown when send fails) --------------------------------------

ADB_NOT_FOUND = (
    "חסר כלי החיבור לטאבלט.\n\n"
    "ודאו שכל התיקייה נמצאת יחד:\n"
    "  ExcelTool.exe\n"
    "  adb\\adb.exe\n"
    "  adb\\.android\\adbkey"
)
ADB_NO_DEVICE = (
    "המחשב לא רואה את הטאבלט.\n\n"
    "נסו לפי הסדר:\n"
    "  1. נתקו וחברו מחדש את כבל ה-USB\n"
    "  2. בחרו ״העברת קבצים״ / MTP בטאבלט אם נשאל\n"
    "  3. הטכנאי: הריצו authorize_tablet.bat פעם אחת\n\n"
    "נתיב כלי החיבור: {adb}"
)
ADB_DEVICES_LINE = "פרטי מכשירים: {raw}"
ADB_UNAUTHORIZED = (
    "הטאבלט מחובר ({serials}) אך המחשב לא מאושר.\n\n"
    "הטכנאי צריך להריץ פעם אחת:\n"
    "  authorize_tablet.bat\n\n"
    "אחרי זה לא צריך לגעת בזה שוב."
)
ADB_OFFLINE = (
    "החיבור נפסק. נתקו את הכבל, "
    "המתינו 3 שניות, חברו מחדש."
)
ADB_NOT_READY = "הטאבלט מזוהה אך לא מוכן: {details}"
ADB_PUSH_VERIFY_FAILED = "הקובץ לא הגיע תקין לטאבלט — נסו שוב."
ADB_BROADCAST_FAILED = "שגיאת שליחה: {detail}"
ADB_PUSH_FAILED = "שגיאת העלאה לטאבלט: {detail}"
ADB_UNKNOWN_ERROR = "שגיאה לא ידועה"
ADB_MULTIPLE_TABLETS = (
    "מחוברים {n} טאבלטים — נתקו את כולם חוץ מאחד."
)
ADB_TABLET_REJECTED_BOOKS = (
    "הטאבלט דחה את הקובץ: {line}\n"
    "נסו לפתוח את האפליקציה בטאבלט ולשלוח שוב."
)
ADB_TABLET_REJECTED_MATCHINGS = (
    "הטאבלט דחה את קובץ ההתאמות: {line}\n"
    "נסו לפתוח את האפליקציה בטאבלט ולשלוח שוב."
)
ADB_TABLET_REJECTED_BEIS = (
    "הטאבלט דחה את קובץ בית המדרש: {line}\n"
    "נסו לפתוח את האפליקציה בטאבלט ולשלוח שוב."
)

SEVERITY_DISPLAY = {
    "ERROR": SEVERITY_ERROR,
    "WARNING": SEVERITY_WARNING,
    "INFO": SEVERITY_INFO,
}
