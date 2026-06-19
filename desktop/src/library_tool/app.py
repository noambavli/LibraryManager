"""The Tkinter desktop GUI — Hebrew, scrollable, reliable import/send flow."""

from __future__ import annotations

import os
import queue
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, ttk
from typing import Callable, Optional

from . import __version__, adb_transfer, backups
from . import strings_he as S
from .converter import NAME, books_to_rows
from .session import AbortError, AbortFlag, Session
from .matchings_converter import matchings_to_rows
from .xlsx_writer import write_xlsx
from .ui_util import (
    COLORS,
    FONT_BODY,
    FONT_BODY_BOLD,
    FONT_STATUS,
    Card,
    InfoBanner,
    ScrollableFrame,
    StatusPill,
    action_row,
    apply_app_theme,
    configure_findings_tree,
    label,
    muted_caption,
    rtl,
    style_restore_dialog,
    user_error,
)
from .validation import ERROR, INFO, WARNING

_SEVERITY_TAG = {ERROR: "error", WARNING: "warning", INFO: "info"}
_WRAP = 860


class LibraryToolApp:
    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        self.session = Session()
        self.abort = AbortFlag()
        self._events: "queue.Queue" = queue.Queue()
        self._busy = False
        self._tablet_ready = False

        root.title(S.app_title(__version__))
        root.geometry("1020x820")
        root.minsize(900, 720)

        apply_app_theme(root)
        self._build_shell()

        self._refresh_status()
        self._poll_tablet_connection()
        root.after(80, self._drain_events)

    # ------------------------------------------------------------------ UI

    def _build_shell(self) -> None:
        self._build_accent_bar()
        self._build_footer()
        self._build_progress_dock()

        scroll_host = ttk.Frame(self.root, padding=0)
        scroll_host.pack(fill="both", expand=True)
        self._scroll = ScrollableFrame(scroll_host)
        self._scroll.pack(fill="both", expand=True, padx=16, pady=(12, 8))
        content = self._scroll.inner

        self._build_header(content)
        self._build_tablet_card(content)
        self._build_actions(content)
        self._build_matchings_actions(content)
        self._build_findings(content)

    def _build_accent_bar(self) -> None:
        bar = tk.Frame(self.root, bg=COLORS["accent_bar"], height=4)
        bar.pack(fill="x", side="top")

    def _build_footer(self) -> None:
        bar = ttk.Frame(self.root, style="Footer.TFrame", padding=(18, 10))
        bar.pack(fill="x", side="bottom")
        self.footer_var = tk.StringVar(value=S.FOOTER_READY)
        ttk.Label(bar, textvariable=self.footer_var, style="Footer.TLabel").pack(side="left")
        ttk.Label(bar, text=S.FOOTER_OFFLINE, style="Footer.TLabel").pack(side="right")

    def _build_progress_dock(self) -> None:
        dock = tk.Frame(
            self.root,
            bg=COLORS["surface"],
            highlightbackground=COLORS["border"],
            highlightthickness=1,
        )
        dock.pack(fill="x", side="bottom")
        inner = ttk.Frame(dock, style="Dock.TFrame", padding=(18, 12))
        inner.pack(fill="x")

        self.progress = ttk.Progressbar(
            inner,
            mode="determinate",
            maximum=1.0,
            style="Modern.Horizontal.TProgressbar",
        )
        self.progress.pack(side="left", fill="x", expand=True)
        self.progress_var = tk.StringVar(value="")
        ttk.Label(inner, textvariable=self.progress_var, style="Dock.TLabel").pack(
            side="left", padx=(14, 0),
        )
        self.btn_abort = ttk.Button(
            inner, text=S.BTN_ABORT, style="Danger.TButton", command=self.on_abort, state="disabled",
        )
        self.btn_abort.pack(side="left", padx=(12, 0))

    def _build_header(self, parent: ttk.Frame) -> None:
        card = Card(
            parent,
            title=S.APP_HEADLINE,
            subtitle=S.APP_SUBTITLE,
            padding=22,
            pady=(0, 14),
        )
        body = card.content()

        meta = ttk.Frame(body, style="CardInner.TFrame")
        meta.pack(fill="x", pady=(14, 0))

        self.chosen_var = tk.StringVar(value=S.NO_FILE_CHOSEN)
        muted_caption(meta, textvariable=self.chosen_var, wrap=_WRAP).pack(
            anchor="e", fill="x",
        )

        self.status_var = tk.StringVar()
        label(
            meta,
            textvariable=self.status_var,
            font=FONT_STATUS,
            bold=True,
            color=COLORS["primary"],
            wrap=_WRAP,
        ).pack(anchor="e", fill="x", pady=(6, 0))

    def _build_tablet_card(self, parent: ttk.Frame) -> None:
        card = Card(parent, title=S.TABLET_USB_LABEL.replace(":", ""), padding=18, pady=(0, 14))
        body = card.content()

        row = ttk.Frame(body, style="CardInner.TFrame")
        row.pack(fill="x")
        self.tablet_status_var = tk.StringVar(value=S.TABLET_CHECKING)
        self.tablet_pill = StatusPill(row, self.tablet_status_var)
        self.tablet_pill.pack(side="right")

        self.tablet_pick_var = tk.StringVar(value=self.session.tablet_pick_hint())
        InfoBanner(body, textvariable=self.tablet_pick_var, tone="info").pack(
            fill="x", pady=(12, 0),
        )

    def _build_actions(self, parent: ttk.Frame) -> None:
        card = Card(
            parent,
            title=S.STEPS_FRAME,
            subtitle=S.STEPS_HELP,
            padding=20,
            pady=(0, 14),
        )
        body = card.content()

        row1 = action_row(body, pady=(12, 0))
        self.btn_import = ttk.Button(
            row1, text=S.BTN_IMPORT_CATALOG, style="Primary.TButton", command=self.on_import,
        )
        self.btn_import.pack(side="left")
        self.btn_save = ttk.Button(
            row1, text=S.BTN_SAVE_LOCAL, style="Secondary.TButton",
            command=self.on_save_local, state="disabled",
        )
        self.btn_save.pack(side="left", padx=(10, 0))
        self.btn_export = ttk.Button(
            row1, text=S.BTN_SEND_TABLET, style="Primary.TButton",
            command=self.on_send_to_tablet, state="disabled",
        )
        self.btn_export.pack(side="right")

        row2 = action_row(body)
        self.btn_restore_import = ttk.Button(
            row2, text=S.BTN_RESTORE_IMPORT, style="Secondary.TButton",
            command=self.on_restore_import, state="disabled",
        )
        self.btn_restore_import.pack(side="left")
        self.btn_restore_backup = ttk.Button(
            row2, text=S.BTN_RESTORE_BACKUP, style="Secondary.TButton",
            command=self.on_restore_backup,
        )
        self.btn_restore_backup.pack(side="left", padx=(10, 0))

    def _build_matchings_actions(self, parent: ttk.Frame) -> None:
        card = Card(
            parent,
            title=S.MATCHINGS_FRAME,
            subtitle=S.MATCHINGS_DESC,
            padding=20,
            pady=(0, 14),
        )
        body = card.content()

        self.matchings_chosen_var = tk.StringVar(value=S.NO_MATCHINGS_CHOSEN)
        muted_caption(body, textvariable=self.matchings_chosen_var, wrap=_WRAP).pack(
            anchor="e", fill="x", pady=(10, 0),
        )

        row = action_row(body)
        self.btn_import_matchings = ttk.Button(
            row, text=S.BTN_IMPORT_MATCHINGS, style="Primary.TButton",
            command=self.on_import_matchings,
        )
        self.btn_import_matchings.pack(side="left")
        self.btn_save_matchings = ttk.Button(
            row, text=S.BTN_SAVE_MATCHINGS, style="Secondary.TButton",
            command=self.on_save_matchings_local, state="disabled",
        )
        self.btn_save_matchings.pack(side="left", padx=(10, 0))
        self.btn_send_matchings = ttk.Button(
            row, text=S.BTN_SEND_MATCHINGS, style="Primary.TButton",
            command=self.on_send_matchings_to_tablet, state="disabled",
        )
        self.btn_send_matchings.pack(side="right")

        self.matchings_hint_var = tk.StringVar(value="")
        InfoBanner(body, textvariable=self.matchings_hint_var, tone="neutral").pack(
            fill="x", pady=(12, 0),
        )

    def _build_findings(self, parent: ttk.Frame) -> None:
        card = Card(
            parent,
            title=S.REVIEW_FRAME,
            subtitle=S.REVIEW_HELP,
            padding=18,
            pady=(0, 18),
            fill="both",
            expand=True,
        )
        body = card.content()

        self.summary_var = tk.StringVar(value=S.NO_CATALOG_LOADED)
        label(
            body,
            textvariable=self.summary_var,
            font=FONT_BODY_BOLD,
            color=COLORS["text"],
            wrap=_WRAP,
        ).pack(anchor="e", fill="x", pady=(8, 0))

        tree_shell = tk.Frame(
            body,
            bg=COLORS["surface"],
            highlightbackground=COLORS["border"],
            highlightthickness=1,
        )
        tree_shell.pack(fill="both", expand=True, pady=(12, 0))
        tree_frame = ttk.Frame(tree_shell, style="CardInner.TFrame", padding=1)
        tree_frame.pack(fill="both", expand=True)

        cols = ("severity", "message", "count")
        tree = ttk.Treeview(
            tree_frame,
            columns=cols,
            show="headings",
            height=10,
            style="Modern.Treeview",
        )
        tree.heading("severity", text=S.COL_LEVEL)
        tree.heading("message", text=S.COL_FINDING)
        tree.heading("count", text=S.COL_BOOKS)
        tree.column("severity", width=92, anchor="center", stretch=False)
        tree.column("message", width=640, anchor="e")
        tree.column("count", width=72, anchor="center", stretch=False)
        configure_findings_tree(tree)
        tree.pack(side="left", fill="both", expand=True)

        sb = ttk.Scrollbar(tree_frame, orient="vertical", command=tree.yview)
        sb.pack(side="right", fill="y")
        tree.configure(yscrollcommand=sb.set)
        tree.bind("<<TreeviewSelect>>", self._on_finding_select)
        self.tree = tree

        self.detail_var = tk.StringVar(value="")
        muted_caption(body, textvariable=self.detail_var, wrap=_WRAP).pack(
            anchor="e", fill="x", pady=(10, 0),
        )

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
        self.status_var.set(rtl(S.WORKING_CATALOG.format(n=n, dirty=dirty)))
        self.tablet_pick_var.set(rtl(s.tablet_pick_hint()))

        has_books = n > 0 and not self._busy
        can_send = has_books and self._tablet_ready
        self.btn_export.config(state="normal" if can_send else "disabled")
        self.btn_save.config(state="normal" if has_books else "disabled")
        self.btn_restore_import.config(
            state="normal" if (s.can_restore_import() and not self._busy) else "disabled",
        )

        mn = len(s.matchings)
        if s.matchings_source_path:
            src = os.path.basename(s.matchings_source_path)
            self.matchings_chosen_var.set(S.CHOSEN_MATCHINGS.format(src=src, n=mn))
        else:
            self.matchings_chosen_var.set(S.NO_MATCHINGS_CHOSEN)
        self.matchings_hint_var.set(rtl(s.matchings_tablet_pick_hint()))

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
        for idx, f in enumerate(self._findings):
            tag = _SEVERITY_TAG.get(f.severity, "info")
            stripe = "odd" if idx % 2 else "even"
            count = str(f.count) if f.count else ""
            severity = S.SEVERITY_DISPLAY.get(f.severity, f.severity)
            self.tree.insert(
                "", "end",
                values=(severity, rtl(f.message), count),
                tags=(tag, stripe),
            )
        self.summary_var.set(rtl(report.summary_line()))
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
                self.detail_var.set(rtl(S.EXAMPLES_PREFIX + " | ".join(f.examples)))
            else:
                self.detail_var.set("")

    def _show_error(self, title: str, exc: BaseException) -> None:
        self.footer_var.set(S.FOOTER_ERROR)
        messagebox.showerror(title, user_error(exc))

    # ------------------------------------------- worker-thread plumbing

    def _run_worker(
        self,
        fn: Callable,
        on_done: Callable,
        *,
        on_error: Optional[Callable[[BaseException], None]] = None,
        progress_label: str = S.WORKING,
        error_title: str = S.DLG_ERROR_TITLE,
    ) -> None:
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
            except Exception as exc:
                self._events.put(("error", (on_error, exc, error_title)))

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
                    on_error, exc, error_title = payload
                    self._set_busy(False)
                    self.progress.config(value=0.0)
                    self.progress_var.set("")
                    if on_error:
                        on_error(exc)
                    else:
                        self._show_error(error_title, exc)
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
            text, tone = S.TABLET_ADB_MISSING, "error"
        elif diag.ready:
            text = S.TABLET_CONNECTED.format(model=diag.ready[0].model)
            tone = "success"
        elif any(d.state == "unauthorized" for d in diag.devices):
            text, tone = S.TABLET_UNAUTHORIZED, "warning"
        elif diag.devices:
            text, tone = S.TABLET_NOT_READY, "warning"
        else:
            text, tone = S.TABLET_NONE, "error"
        self.tablet_status_var.set(rtl(text))
        self.tablet_pill.set_tone(tone)
        self._refresh_status()

    # ----------------------------------------------------------- actions

    def on_abort(self) -> None:
        if self._busy:
            self.abort.request()
            self.progress_var.set(S.ABORTING)

    def _confirm_replace_catalog(self) -> bool:
        n = len(self.session.books)
        if n <= 0:
            return True
        if self.session.dirty:
            return messagebox.askyesno(
                S.DLG_REPLACE_CATALOG_TITLE,
                S.DLG_REPLACE_CATALOG_BODY,
            )
        return messagebox.askyesno(
            S.DLG_REPLACE_ANY_TITLE,
            S.DLG_REPLACE_ANY_BODY.format(n=n),
        )

    def on_import(self) -> None:
        path = filedialog.askopenfilename(
            title=S.DLG_CHOOSE_CATALOG,
            filetypes=[(S.FILETYPE_XLSX, "*.xlsx"), (S.FILETYPE_ALL, "*.*")],
        )
        if not path:
            return
        if not self._confirm_replace_catalog():
            return

        def work(progress, abort):
            return self.session.import_xlsx(path, progress, abort)

        def done(outcome):
            self._show_report(outcome.report)
            src = os.path.basename(path)
            n = outcome.convert.imported
            self.footer_var.set(S.FOOTER_IMPORTED.format(src=src, n=n))

            if NAME not in outcome.convert.header_map.columns:
                messagebox.showerror(
                    S.DLG_IMPORT_NO_NAME_TITLE,
                    S.DLG_IMPORT_NO_NAME_BODY,
                )
            elif n == 0:
                messagebox.showwarning(
                    S.DLG_IMPORT_EMPTY_TITLE,
                    S.DLG_IMPORT_EMPTY_BODY,
                )
            elif outcome.report.has_errors:
                messagebox.showwarning(
                    S.DLG_IMPORT_PROBLEMS_TITLE,
                    S.DLG_IMPORT_PROBLEMS_BODY,
                )
            else:
                messagebox.showinfo(
                    S.DLG_IMPORT_OK_TITLE,
                    S.DLG_IMPORT_OK_BODY.format(n=n, src=src),
                )
            self._refresh_status()

        self._run_worker(
            work, done,
            progress_label=S.PROGRESS_IMPORTING,
            error_title=S.DLG_IMPORT_PROBLEMS_TITLE,
        )

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
            self._show_error(S.DLG_SAVE_FAILED_TITLE, exc)
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
                f"{user_error(exc)}{extra}",
            )

        self._run_worker(
            work, done, on_error=error,
            progress_label=S.PROGRESS_SENDING,
            error_title=S.DLG_SEND_FAILED_TITLE,
        )

    def on_import_matchings(self) -> None:
        path = filedialog.askopenfilename(
            title=S.DLG_CHOOSE_MATCHINGS,
            filetypes=[(S.FILETYPE_XLSX, "*.xlsx"), (S.FILETYPE_ALL, "*.*")],
        )
        if not path:
            return
        if self.session.matchings and not messagebox.askyesno(
            S.DLG_REPLACE_MATCHINGS_TITLE,
            S.DLG_REPLACE_MATCHINGS_BODY.format(n=len(self.session.matchings)),
        ):
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
                messagebox.showwarning(S.DLG_NO_MATCHINGS_TITLE, S.DLG_NO_MATCHINGS_BODY)
            self._refresh_status()

        self._run_worker(
            work, done,
            progress_label=S.PROGRESS_IMPORT_MATCHINGS,
            error_title=S.DLG_NO_MATCHINGS_TITLE,
        )

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
            self._show_error(S.DLG_SAVE_FAILED_TITLE, exc)
            return
        fname = os.path.basename(path)
        self.footer_var.set(
            S.FOOTER_SAVED_MATCHINGS.format(n=len(self.session.matchings), fname=fname),
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
            messagebox.showerror(
                S.DLG_SEND_MATCHINGS_FAILED,
                f"{user_error(exc)}{extra}",
            )

        self._run_worker(
            work, done, on_error=error,
            progress_label=S.PROGRESS_SEND_MATCHINGS,
            error_title=S.DLG_SEND_MATCHINGS_FAILED,
        )

    def on_restore_import(self) -> None:
        if not self.session.can_restore_import():
            return
        if not messagebox.askyesno(S.DLG_RESTORE_IMPORT_TITLE, S.DLG_RESTORE_IMPORT_BODY):
            return
        try:
            n = self.session.restore_import()
        except Exception as exc:
            self._show_error(S.DLG_RESTORE_FAILED, exc)
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
        win.geometry("680x460")
        win.transient(self.root)
        win.grab_set()
        style_restore_dialog(win)

        shell = tk.Frame(
            win,
            bg=COLORS["surface"],
            highlightbackground=COLORS["border"],
            highlightthickness=1,
        )
        shell.pack(fill="both", expand=True, padx=16, pady=16)

        header = ttk.Frame(shell, style="CardInner.TFrame", padding=16)
        header.pack(fill="x")
        ttk.Label(header, text=S.DLG_RESTORE_BACKUP_PICK, style="Section.TLabel").pack(
            anchor="e",
        )

        list_shell = tk.Frame(
            shell,
            bg=COLORS["surface"],
            highlightbackground=COLORS["border"],
            highlightthickness=1,
        )
        list_shell.pack(fill="both", expand=True, padx=16, pady=(0, 8))
        lb = tk.Listbox(
            list_shell,
            height=12,
            font=FONT_BODY,
            justify="right",
            bg=COLORS["surface"],
            fg=COLORS["text"],
            selectbackground=COLORS["tree_select"],
            selectforeground=COLORS["text"],
            highlightthickness=0,
            borderwidth=0,
        )
        for e in entries:
            lb.insert("end", rtl(e.label()))
        lb.pack(fill="both", expand=True, padx=8, pady=8)

        def do_restore():
            sel = lb.curselection()
            if not sel:
                messagebox.showwarning(S.DLG_RESTORE_BACKUP_TITLE, S.DLG_RESTORE_BACKUP_PICK)
                return
            entry = entries[sel[0]]
            if not messagebox.askyesno(
                S.DLG_RESTORE_CONFIRM_TITLE,
                S.DLG_RESTORE_CONFIRM_BODY.format(when=entry.when),
                parent=win,
            ):
                return
            win.destroy()
            try:
                n = self.session.restore_from_backup(entry)
            except Exception as exc:
                self._show_error(S.DLG_RESTORE_FAILED, exc)
                return
            self._show_report(self.session.last_report)
            self.footer_var.set(S.FOOTER_RESTORED_BACKUP.format(n=n, when=entry.when))
            self._refresh_status()

        btns = ttk.Frame(shell, style="CardInner.TFrame", padding=16)
        btns.pack(fill="x")
        ttk.Button(
            btns, text=S.BTN_RESTORE_SELECTED, style="Primary.TButton", command=do_restore,
        ).pack(side="right")
        ttk.Button(
            btns, text=S.BTN_CANCEL, style="Secondary.TButton", command=win.destroy,
        ).pack(side="right", padx=(0, 10))
        self.root.wait_window(win)


def main() -> None:
    root = tk.Tk()
    LibraryToolApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
