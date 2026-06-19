"""The Tkinter desktop GUI.

A single window organised as a guided flow:

    1. Import a catalog .xlsx        →  loads + converts + validates
    2. Review warnings / duplicates  →  a findings table with counts
    3. Send Excel to the tablet (USB-C)  →  merge import on confirm
    plus a safety panel: restore last import and restore any backup.

Long operations run on a worker thread; progress and completion are marshalled
back to the Tk main loop through a queue, so the UI never freezes and an abort
can be requested mid-operation.
"""

from __future__ import annotations

import os
import queue
import subprocess
import sys
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, ttk
from typing import Callable, Optional

from . import __version__, adb_transfer, backups
from . import strings_he as S
from .session import AbortError, AbortFlag, Session
from .converter import books_to_rows
from .matchings_converter import matchings_to_rows
from .xlsx_writer import write_xlsx
from .validation import ERROR, INFO, WARNING

_SEVERITY_TAG = {ERROR: "error", WARNING: "warning", INFO: "info"}
_SEVERITY_COLOR = {
    "error": "#b00020",
    "warning": "#9a6700",
    "info": "#3a6ea5",
}


class LibraryToolApp:
    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        self.session = Session()
        self.abort = AbortFlag()
        self._events: "queue.Queue" = queue.Queue()
        self._busy = False
        self._tablet_ready = False

        root.title(S.app_title(__version__))
        root.geometry("960x760")
        root.minsize(820, 680)
        self._configure_hebrew_ui(root)

        self._build_styles()
        self._build_header()
        self._build_actions()
        self._build_matchings_actions()
        self._build_findings()
        self._build_progress()
        self._build_footer()

        self._refresh_status()
        self._poll_tablet_connection()
        self.root.after(80, self._drain_events)

    # ------------------------------------------------------------------ UI

    def _configure_hebrew_ui(self, root: tk.Tk) -> None:
        root.option_add("*Font", "Segoe UI 10")
        root.option_add("*Label.justify", "right")
        root.option_add("*Label.anchor", "e")

    def _build_styles(self) -> None:
        style = ttk.Style()
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass
        style.configure("Title.TLabel", font=("Segoe UI", 16, "bold"))
        style.configure("Sub.TLabel", font=("Segoe UI", 10))
        style.configure("Status.TLabel", font=("Segoe UI", 11, "bold"))
        style.configure("Step.TLabelframe.Label", font=("Segoe UI", 11, "bold"))
        style.configure("Accent.TButton", font=("Segoe UI", 10, "bold"))
    def _build_header(self) -> None:
        top = ttk.Frame(self.root, padding=(16, 12, 16, 4))
        top.pack(fill="x")
        ttk.Label(top, text=S.APP_HEADLINE, style="Title.TLabel").pack(anchor="e")
        ttk.Label(
            top,
            text=S.APP_SUBTITLE,
            style="Sub.TLabel",
        ).pack(anchor="e")

        self.chosen_var = tk.StringVar(value=S.NO_FILE_CHOSEN)
        ttk.Label(top, textvariable=self.chosen_var, style="Sub.TLabel").pack(anchor="e", pady=(6, 0))

        self.status_var = tk.StringVar()
        status = ttk.Label(top, textvariable=self.status_var, style="Status.TLabel")
        status.pack(anchor="e", pady=(4, 0))

        tablet_row = ttk.Frame(top)
        tablet_row.pack(anchor="e", pady=(6, 0))
        ttk.Label(tablet_row, text=S.TABLET_USB_LABEL, style="Sub.TLabel").pack(side="right")
        self.tablet_status_var = tk.StringVar(value=S.TABLET_CHECKING)
        self.tablet_status_label = tk.Label(
            tablet_row,
            textvariable=self.tablet_status_var,
            font=("Segoe UI", 10, "bold"),
            fg="#666666",
            anchor="e",
            justify="right",
        )
        self.tablet_status_label.pack(side="right", padx=(6, 0))

        self.tablet_pick_var = tk.StringVar(value=self.session.tablet_pick_hint())
        self.tablet_pick_label = tk.Label(
            top,
            textvariable=self.tablet_pick_var,
            font=("Segoe UI", 11, "bold"),
            fg="#1a4a8a",
            wraplength=900,
            justify="right",
            anchor="e",
        )
        self.tablet_pick_label.pack(anchor="e", pady=(8, 0))

    def _build_actions(self) -> None:
        frame = ttk.LabelFrame(self.root, text=S.STEPS_FRAME, padding=12, style="Step.TLabelframe")
        frame.pack(fill="x", padx=16, pady=8)

        row1 = ttk.Frame(frame)
        row1.pack(fill="x")
        self.btn_export = ttk.Button(
            row1, text=S.BTN_SEND_TABLET, style="Accent.TButton",
            command=self.on_send_to_tablet, state="disabled",
        )
        self.btn_export.pack(side="right")

        self.btn_save = ttk.Button(
            row1, text=S.BTN_SAVE_LOCAL, command=self.on_save_local, state="disabled",
        )
        self.btn_save.pack(side="right", padx=(8, 0))

        self.btn_import = ttk.Button(
            row1, text=S.BTN_IMPORT_CATALOG, style="Accent.TButton",
            command=self.on_import,
        )
        self.btn_import.pack(side="right", padx=(8, 0))

        row2 = ttk.Frame(frame)
        row2.pack(fill="x", pady=(10, 0))
        self.btn_restore_backup = ttk.Button(
            row2, text=S.BTN_RESTORE_BACKUP, command=self.on_restore_backup,
        )
        self.btn_restore_backup.pack(side="right")

        self.btn_restore_import = ttk.Button(
            row2, text=S.BTN_RESTORE_IMPORT, command=self.on_restore_import, state="disabled",
        )
        self.btn_restore_import.pack(side="right", padx=(8, 0))

    def _build_matchings_actions(self) -> None:
        frame = ttk.LabelFrame(
            self.root,
            text=S.MATCHINGS_FRAME,
            padding=12,
            style="Step.TLabelframe",
        )
        frame.pack(fill="x", padx=16, pady=(0, 8))

        ttk.Label(
            frame,
            text=S.MATCHINGS_DESC,
            style="Sub.TLabel",
            wraplength=900,
            justify="right",
        ).pack(anchor="e", pady=(0, 8))

        self.matchings_chosen_var = tk.StringVar(value=S.NO_MATCHINGS_CHOSEN)
        ttk.Label(frame, textvariable=self.matchings_chosen_var, style="Sub.TLabel").pack(
            anchor="e", pady=(0, 6),
        )

        row = ttk.Frame(frame)
        row.pack(fill="x")
        self.btn_send_matchings = ttk.Button(
            row,
            text=S.BTN_SEND_MATCHINGS,
            style="Accent.TButton",
            command=self.on_send_matchings_to_tablet,
            state="disabled",
        )
        self.btn_send_matchings.pack(side="right")

        self.btn_save_matchings = ttk.Button(
            row,
            text=S.BTN_SAVE_MATCHINGS,
            command=self.on_save_matchings_local,
            state="disabled",
        )
        self.btn_save_matchings.pack(side="right", padx=(8, 0))

        self.btn_import_matchings = ttk.Button(
            row,
            text=S.BTN_IMPORT_MATCHINGS,
            style="Accent.TButton",
            command=self.on_import_matchings,
        )
        self.btn_import_matchings.pack(side="right", padx=(8, 0))

        self.matchings_hint_var = tk.StringVar(value="")
        ttk.Label(frame, textvariable=self.matchings_hint_var, style="Sub.TLabel").pack(
            anchor="w", pady=(8, 0),
        )

    def _build_findings(self) -> None:
        frame = ttk.LabelFrame(self.root, text=S.REVIEW_FRAME,
                               padding=10, style="Step.TLabelframe")
        frame.pack(fill="both", expand=True, padx=16, pady=4)

        self.summary_var = tk.StringVar(value=S.NO_CATALOG_LOADED)
        ttk.Label(frame, textvariable=self.summary_var, style="Sub.TLabel").pack(anchor="e")

        cols = ("severity", "message", "count")
        tree = ttk.Treeview(frame, columns=cols, show="headings", height=8)
        tree.heading("severity", text=S.COL_LEVEL, anchor="e")
        tree.heading("message", text=S.COL_FINDING, anchor="e")
        tree.heading("count", text=S.COL_BOOKS, anchor="center")
        tree.column("severity", width=90, anchor="e", stretch=False)
        tree.column("message", width=680, anchor="e")
        tree.column("count", width=70, anchor="center", stretch=False)
        for tag, color in _SEVERITY_COLOR.items():
            tree.tag_configure(tag, foreground=color)
        tree.pack(side="left", fill="both", expand=True, pady=(6, 0))

        sb = ttk.Scrollbar(frame, orient="vertical", command=tree.yview)
        sb.pack(side="right", fill="y", pady=(6, 0))
        tree.configure(yscrollcommand=sb.set)
        tree.bind("<<TreeviewSelect>>", self._on_finding_select)
        self.tree = tree

        self.detail_var = tk.StringVar(value="")
        ttk.Label(frame, textvariable=self.detail_var, style="Sub.TLabel",
                  wraplength=900, justify="right").pack(anchor="e", pady=(6, 0))

    def _build_progress(self) -> None:
        frame = ttk.Frame(self.root, padding=(16, 4))
        frame.pack(fill="x")
        self.btn_abort = ttk.Button(frame, text=S.BTN_ABORT, command=self.on_abort, state="disabled")
        self.btn_abort.pack(side="right", padx=(8, 0))
        self.progress = ttk.Progressbar(frame, mode="determinate", maximum=1.0)
        self.progress.pack(side="right", fill="x", expand=True)
        self.progress_var = tk.StringVar(value="")
        ttk.Label(self.root, textvariable=self.progress_var, style="Sub.TLabel",
                  padding=(16, 0)).pack(anchor="e")

    def _build_footer(self) -> None:
        bar = ttk.Frame(self.root, padding=(16, 6))
        bar.pack(fill="x", side="bottom")
        self.footer_var = tk.StringVar(value=S.FOOTER_READY)
        ttk.Label(bar, text=S.FOOTER_OFFLINE, style="Sub.TLabel").pack(side="right")
        ttk.Label(bar, textvariable=self.footer_var, style="Sub.TLabel").pack(side="right", padx=(0, 12))

    # ------------------------------------------------------------- helpers

    def _refresh_status(self) -> None:
        s = self.session
        n = len(s.books)
        if s.source_path:
            src = os.path.basename(s.source_path)
            self.chosen_var.set(S.CHOSEN_BOOKS.format(src=src, n=n))
        else:
            self.chosen_var.set(S.NO_FILE_CHOSEN)
        dirty = S.UNSAVED_CHANGES if s.dirty else ""
        self.status_var.set(S.WORKING_CATALOG.format(n=n, dirty=dirty))
        self.tablet_pick_var.set(s.tablet_pick_hint())

        has_books = n > 0 and not self._busy
        can_send = has_books and self._tablet_ready
        self.btn_export.config(state="normal" if can_send else "disabled")
        self.btn_save.config(state="normal" if has_books else "disabled")
        self.btn_restore_import.config(
            state="normal" if (s.can_restore_import() and not self._busy) else "disabled"
        )

        mn = len(s.matchings)
        if s.matchings_source_path:
            src = os.path.basename(s.matchings_source_path)
            self.matchings_chosen_var.set(S.CHOSEN_MATCHINGS.format(src=src, n=mn))
        else:
            self.matchings_chosen_var.set(S.NO_MATCHINGS_CHOSEN)
        self.matchings_hint_var.set(s.matchings_tablet_pick_hint())

        has_matchings = mn > 0 and not self._busy
        can_send_matchings = has_matchings and self._tablet_ready
        self.btn_send_matchings.config(state="normal" if can_send_matchings else "disabled")
        self.btn_save_matchings.config(state="normal" if has_matchings else "disabled")

    def _set_busy(self, busy: bool) -> None:
        self._busy = busy
        state = "disabled" if busy else "normal"
        for b in (self.btn_import, self.btn_restore_backup, self.btn_import_matchings):
            b.config(state=state)
        self.btn_abort.config(state="normal" if busy else "disabled")
        if not busy:
            self.abort.reset()
        self._refresh_status()

    def _show_report(self, report) -> None:
        self.tree.delete(*self.tree.get_children())
        self._findings = list(report.findings)
        for f in self._findings:
            tag = _SEVERITY_TAG.get(f.severity, "info")
            count = str(f.count) if f.count else ""
            severity = S.SEVERITY_DISPLAY.get(f.severity, f.severity)
            self.tree.insert("", "end", values=(severity, f.message, count), tags=(tag,))
        self.summary_var.set(report.summary_line())
        self.detail_var.set("")

    def _on_finding_select(self, _event) -> None:
        sel = self.tree.selection()
        if not sel:
            return
        idx = self.tree.index(sel[0])
        findings = getattr(self, "_findings", [])
        if 0 <= idx < len(findings):
            f = findings[idx]
            if f.examples:
                self.detail_var.set(S.EXAMPLES_PREFIX + " | ".join(f.examples))
            else:
                self.detail_var.set("")

    # ------------------------------------------- worker-thread plumbing

    def _run_worker(self, fn: Callable, on_done: Callable, on_error: Optional[Callable] = None,
                    progress_label: str = S.WORKING) -> None:
        """Run ``fn`` on a thread; ``fn`` receives (progress, abort)."""
        self._set_busy(True)
        self.progress.config(value=0.0)
        self.progress_var.set(progress_label)

        def progress(message: str, fraction: float) -> None:
            self._events.put(("progress", (message, fraction)))

        def worker() -> None:
            try:
                result = fn(progress, self.abort)
                self._events.put(("done", (on_done, result)))
            except AbortError:
                self._events.put(("aborted", None))
            except Exception as exc:  # surfaced to the user, never swallowed
                self._events.put(("error", (on_error, exc)))

        threading.Thread(target=worker, daemon=True).start()

    def _drain_events(self) -> None:
        try:
            while True:
                kind, payload = self._events.get_nowait()
                if kind == "progress":
                    message, fraction = payload
                    self.progress.config(value=fraction)
                    self.progress_var.set(message)
                elif kind == "done":
                    on_done, result = payload
                    self._set_busy(False)
                    self.progress.config(value=1.0)
                    self.progress_var.set("")
                    on_done(result)
                elif kind == "aborted":
                    self._set_busy(False)
                    self.progress.config(value=0.0)
                    self.progress_var.set("")
                    self.footer_var.set(S.FOOTER_ABORTED)
                    messagebox.showinfo(S.DLG_ABORTED_TITLE, S.DLG_ABORTED_BODY)
                elif kind == "error":
                    on_error, exc = payload
                    self._set_busy(False)
                    self.progress.config(value=0.0)
                    self.progress_var.set("")
                    if on_error:
                        on_error(exc)
                    else:
                        self.footer_var.set(S.FOOTER_ERROR)
                        messagebox.showerror(S.DLG_ERROR_TITLE, str(exc))
                elif kind == "tablet_status":
                    self._apply_tablet_status(payload)
        except queue.Empty:
            pass
        self.root.after(80, self._drain_events)

    def _poll_tablet_connection(self) -> None:
        if self._busy:
            self.root.after(3000, self._poll_tablet_connection)
            return

        def worker() -> None:
            try:
                diag = adb_transfer.diagnose(restart_server=False)
                self._events.put(("tablet_status", diag))
            except Exception:
                pass

        threading.Thread(target=worker, daemon=True).start()
        self.root.after(3000, self._poll_tablet_connection)

    def _apply_tablet_status(self, diag: adb_transfer.AdbDiagnosis) -> None:
        self._tablet_ready = bool(diag.ready)
        if not diag.adb_path:
            text = S.TABLET_ADB_MISSING
            color = "#b00020"
        elif diag.ready:
            d = diag.ready[0]
            text = S.TABLET_CONNECTED.format(model=d.model)
            color = "#1b7a3d"
        elif any(d.state == "unauthorized" for d in diag.devices):
            text = S.TABLET_UNAUTHORIZED
            color = "#9a6700"
        elif diag.devices:
            text = S.TABLET_NOT_READY
            color = "#9a6700"
        else:
            text = S.TABLET_NONE
            color = "#b00020"
        self.tablet_status_var.set(text)
        self.tablet_status_label.config(fg=color)
        self._refresh_status()

    # ----------------------------------------------------------- actions

    def _open_in_file_manager(self, folder: str) -> None:
        """Open a folder in the OS file manager (Explorer / Finder / xdg-open).
        Best-effort: failure here must never break the export flow."""
        try:
            if os.name == "nt":
                os.startfile(folder)  # type: ignore[attr-defined]
            elif sys.platform == "darwin":
                subprocess.Popen(["open", folder])
            else:
                subprocess.Popen(["xdg-open", folder])
        except Exception:
            pass

    def on_abort(self) -> None:
        if self._busy:
            self.abort.request()
            self.progress_var.set(S.ABORTING)

    def on_import(self) -> None:
        path = filedialog.askopenfilename(
            title=S.DLG_CHOOSE_CATALOG,
            filetypes=[(S.FILETYPE_XLSX, "*.xlsx"), (S.FILETYPE_ALL, "*.*")],
        )
        if not path:
            return
        if self.session.books and self.session.dirty:
            if not messagebox.askyesno(
                S.DLG_REPLACE_CATALOG_TITLE,
                S.DLG_REPLACE_CATALOG_BODY,
            ):
                return

        def work(progress, abort):
            return self.session.import_xlsx(path, progress, abort)

        def done(outcome):
            self._show_report(outcome.report)
            rep = outcome.report
            src = os.path.basename(path)
            self.footer_var.set(
                S.FOOTER_IMPORTED.format(src=src, n=outcome.convert.imported)
            )
            if rep.has_errors:
                messagebox.showwarning(
                    S.DLG_IMPORT_PROBLEMS_TITLE,
                    S.DLG_IMPORT_PROBLEMS_BODY,
                )
            self._refresh_status()

        self._run_worker(work, done, progress_label=S.PROGRESS_IMPORTING)

    def on_save_local(self) -> None:
        initial = os.path.basename(self.session.source_path or "books.xlsx")
        path = filedialog.asksaveasfilename(
            title=S.DLG_SAVE_WORKBOOK,
            defaultextension=".xlsx",
            initialfile=initial,
            filetypes=[(S.FILETYPE_XLSX, "*.xlsx")],
        )
        if not path:
            return
        try:
            write_xlsx(path, books_to_rows(self.session.books))
        except Exception as exc:
            messagebox.showerror(S.DLG_SAVE_FAILED_TITLE, str(exc))
            return
        fname = os.path.basename(path)
        self.footer_var.set(S.FOOTER_SAVED_BOOKS.format(n=len(self.session.books), fname=fname))
        messagebox.showinfo(
            S.DLG_SAVED_TITLE,
            S.DLG_SAVED_BOOKS.format(n=len(self.session.books), path=path),
        )

    def on_send_to_tablet(self) -> None:
        report = self.session.validate_current()
        self._show_report(report)
        if report.has_errors:
            if not messagebox.askyesno(
                S.DLG_ERRORS_SEND_TITLE,
                S.DLG_ERRORS_SEND_BODY.format(n=len(report.errors)),
            ):
                return

        n = len(self.session.books)
        batch = self.session.next_send_batch()
        src = os.path.basename(self.session.source_path or "") or "catalog"
        if not messagebox.askyesno(
            S.DLG_SEND_TABLET_TITLE,
            S.DLG_SEND_TABLET_BODY.format(src=src, batch=batch, n=n),
        ):
            return

        def work(progress, abort):
            return self.session.send_to_tablet(progress, abort)

        def done(result):
            batch = self.session.last_sent_batch or self.session.next_send_batch() - 1
            count = result.imported_count
            line = result.result_line or ""
            if line.startswith("ERR:cancelled"):
                msg = S.SEND_CANCELLED.format(batch=batch)
            elif line.startswith("ERR:confirm_timeout"):
                msg = S.SEND_TIMEOUT.format(batch=batch)
            elif count is not None:
                msg = S.SEND_DONE.format(batch=batch, count=count)
            else:
                msg = S.SEND_PENDING.format(batch=batch)
            self.footer_var.set(msg)
            self._refresh_status()
            src = os.path.basename(self.session.source_path or "") or "catalog"
            if line.startswith("ERR:"):
                messagebox.showwarning(
                    S.DLG_TABLET_SYNC_TITLE,
                    S.SYNC_DEVICE_ONLY.format(msg=msg, serial=result.device.serial),
                )
            else:
                from .exports import exports_dir

                messagebox.showinfo(
                    S.DLG_TABLET_SYNC_TITLE,
                    S.DLG_TABLET_SYNC_BODY.format(
                        msg=msg,
                        src=src,
                        batch=batch,
                        archive=exports_dir(),
                        model=result.device.model,
                        serial=result.device.serial,
                    ),
                )

        def error(exc):
            diag = adb_transfer.diagnose()
            extra = ""
            if diag.devices_raw:
                extra = S.ADB_DEVICES_HEADER.format(raw=diag.devices_raw)
            messagebox.showerror(
                S.DLG_SEND_FAILED_TITLE,
                f"{exc}{extra}",
            )

        self._run_worker(work, done, on_error=error, progress_label=S.PROGRESS_SENDING)

    def on_import_matchings(self) -> None:
        path = filedialog.askopenfilename(
            title=S.DLG_CHOOSE_MATCHINGS,
            filetypes=[(S.FILETYPE_XLSX, "*.xlsx"), (S.FILETYPE_ALL, "*.*")],
        )
        if not path:
            return

        def work(progress, abort):
            return self.session.import_matchings_xlsx(path, progress, abort)

        def done(outcome):
            src = os.path.basename(path)
            n = len(outcome.convert.matchings)
            invalid = outcome.convert.invalid
            msg = S.FOOTER_MATCHINGS_LOADED.format(src=src, n=n)
            if invalid:
                msg += S.FOOTER_MATCHINGS_INVALID.format(invalid=invalid)
            self.footer_var.set(msg)
            if n == 0:
                messagebox.showwarning(
                    S.DLG_NO_MATCHINGS_TITLE,
                    S.DLG_NO_MATCHINGS_BODY,
                )
            self._refresh_status()

        self._run_worker(work, done, progress_label=S.PROGRESS_IMPORT_MATCHINGS)

    def on_save_matchings_local(self) -> None:
        initial = os.path.basename(self.session.matchings_source_path or "matchings.xlsx")
        path = filedialog.asksaveasfilename(
            title=S.DLG_SAVE_MATCHINGS,
            defaultextension=".xlsx",
            initialfile=initial,
            filetypes=[(S.FILETYPE_XLSX, "*.xlsx")],
        )
        if not path:
            return
        try:
            write_xlsx(path, matchings_to_rows(self.session.matchings))
        except Exception as exc:
            messagebox.showerror(S.DLG_SAVE_FAILED_TITLE, str(exc))
            return
        fname = os.path.basename(path)
        self.footer_var.set(
            S.FOOTER_SAVED_MATCHINGS.format(n=len(self.session.matchings), fname=fname)
        )
        messagebox.showinfo(
            S.DLG_SAVED_TITLE,
            S.DLG_SAVED_MATCHINGS.format(n=len(self.session.matchings), path=path),
        )

    def on_send_matchings_to_tablet(self) -> None:
        n = len(self.session.matchings)
        batch = self.session.next_matchings_send_batch()
        src = os.path.basename(self.session.matchings_source_path or "") or "matchings"
        if not messagebox.askyesno(
            S.DLG_SEND_MATCHINGS_TITLE,
            S.DLG_SEND_MATCHINGS_BODY.format(src=src, batch=batch, n=n),
        ):
            return

        def work(progress, abort):
            return self.session.send_matchings_to_tablet(progress, abort)

        def done(result):
            batch = (
                self.session.last_sent_matchings_batch
                or self.session.next_matchings_send_batch() - 1
            )
            count = result.imported_count
            line = result.result_line or ""
            if line.startswith("ERR:cancelled"):
                msg = S.MATCHINGS_SEND_CANCELLED.format(batch=batch)
            elif line.startswith("ERR:confirm_timeout"):
                msg = S.MATCHINGS_SEND_TIMEOUT.format(batch=batch)
            elif count is not None:
                msg = S.MATCHINGS_SEND_DONE.format(batch=batch, count=count)
            else:
                msg = S.MATCHINGS_SEND_PENDING.format(batch=batch)
            self.footer_var.set(msg)
            if line.startswith("ERR:"):
                messagebox.showwarning(
                    S.DLG_TABLET_SYNC_TITLE,
                    S.SYNC_DEVICE_ONLY.format(msg=msg, serial=result.device.serial),
                )
            else:
                from .exports import matchings_exports_dir

                messagebox.showinfo(
                    S.DLG_TABLET_SYNC_TITLE,
                    S.MATCHINGS_SYNC_BODY.format(
                        msg=msg,
                        src=src,
                        batch=batch,
                        archive=matchings_exports_dir(),
                        model=result.device.model,
                        serial=result.device.serial,
                    ),
                )
            self._refresh_status()

        def error(exc):
            diag = adb_transfer.diagnose()
            extra = ""
            if diag.devices_raw:
                extra = S.ADB_DEVICES_HEADER.format(raw=diag.devices_raw)
            messagebox.showerror(S.DLG_SEND_MATCHINGS_FAILED, f"{exc}{extra}")

        self._run_worker(
            work, done, on_error=error, progress_label=S.PROGRESS_SEND_MATCHINGS,
        )

    def on_restore_import(self) -> None:
        if not self.session.can_restore_import():
            return
        if not messagebox.askyesno(
            S.DLG_RESTORE_IMPORT_TITLE,
            S.DLG_RESTORE_IMPORT_BODY,
        ):
            return
        try:
            n = self.session.restore_import()
        except Exception as exc:
            messagebox.showerror(S.DLG_RESTORE_FAILED, str(exc))
            return
        self._show_report(self.session.last_report)
        self.footer_var.set(S.FOOTER_RESTORED_IMPORT.format(n=n))
        self._refresh_status()

    def on_restore_backup(self) -> None:
        entries = backups.list_backups()
        if not entries:
            messagebox.showinfo(S.DLG_NO_BACKUPS_TITLE, S.DLG_NO_BACKUPS_BODY)
            return

        win = tk.Toplevel(self.root)
        win.title(S.DLG_RESTORE_BACKUP_TITLE)
        win.geometry("620x400")
        win.transient(self.root)
        win.grab_set()
        ttk.Label(win, text=S.DLG_RESTORE_BACKUP_PICK, style="Status.TLabel",
                  padding=12).pack(anchor="e")
        lb = tk.Listbox(win, height=12)
        for e in entries:
            lb.insert("end", e.label())
        lb.pack(fill="both", expand=True, padx=12, pady=8)

        def do_restore():
            sel = lb.curselection()
            if not sel:
                return
            entry = entries[sel[0]]
            win.destroy()
            n = self.session.restore_from_backup(entry)
            self._show_report(self.session.last_report)
            self.footer_var.set(S.FOOTER_RESTORED_BACKUP.format(n=n, when=entry.when))
            self._refresh_status()

        btns = ttk.Frame(win, padding=12)
        btns.pack(fill="x")
        ttk.Button(btns, text=S.BTN_RESTORE_SELECTED, style="Accent.TButton",
                   command=do_restore).pack(side="right")
        ttk.Button(btns, text=S.BTN_CANCEL, command=win.destroy).pack(side="right", padx=(0, 8))
        self.root.wait_window(win)

def main() -> None:
    root = tk.Tk()
    LibraryToolApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
