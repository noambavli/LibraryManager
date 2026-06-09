"""The Tkinter desktop GUI.

A single window organised as a guided flow:

    1. Import a catalog .xlsx        →  loads + converts + validates
    2. Review warnings / duplicates  →  a findings table with counts
    3. Export to the tablet (USB-C)  →  verified .civ write
    plus a safety panel: restore last import, restore any backup,
    and a guarded "delete all books" flow.

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

from . import __version__, backups, transfer
from . import adb_transfer, civ
from .session import AbortError, AbortFlag, Session
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

        root.title(f"LibraryTool {__version__} — Catalog ↔ Tablet")
        root.geometry("960x680")
        root.minsize(820, 600)

        self._build_styles()
        self._build_header()
        self._build_actions()
        self._build_findings()
        self._build_progress()
        self._build_footer()

        self._refresh_status()
        self._poll_tablet_connection()
        self.root.after(80, self._drain_events)

    # ------------------------------------------------------------------ UI

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
        style.configure("Danger.TButton", foreground="#b00020")

    def _build_header(self) -> None:
        top = ttk.Frame(self.root, padding=(16, 12, 16, 4))
        top.pack(fill="x")
        ttk.Label(top, text="Library Catalog Manager", style="Title.TLabel").pack(anchor="w")
        ttk.Label(
            top,
            text="Offline · imports .xlsx → writes the tablet's .civ catalog · safe export over USB-C",
            style="Sub.TLabel",
        ).pack(anchor="w")

        self.chosen_var = tk.StringVar(value="No Excel file chosen yet — click step 1.")
        ttk.Label(top, textvariable=self.chosen_var, style="Sub.TLabel").pack(anchor="w", pady=(6, 0))

        self.status_var = tk.StringVar()
        status = ttk.Label(top, textvariable=self.status_var, style="Status.TLabel")
        status.pack(anchor="w", pady=(4, 0))

        tablet_row = ttk.Frame(top)
        tablet_row.pack(anchor="w", pady=(6, 0))
        ttk.Label(tablet_row, text="Tablet USB:", style="Sub.TLabel").pack(side="left")
        self.tablet_status_var = tk.StringVar(value="Checking connection…")
        self.tablet_status_label = tk.Label(
            tablet_row,
            textvariable=self.tablet_status_var,
            font=("Segoe UI", 10, "bold"),
            fg="#666666",
        )
        self.tablet_status_label.pack(side="left", padx=(6, 0))

        self.tablet_pick_var = tk.StringVar(value=self.session.tablet_pick_hint())
        self.tablet_pick_label = tk.Label(
            top,
            textvariable=self.tablet_pick_var,
            font=("Segoe UI", 11, "bold"),
            fg="#1a4a8a",
            wraplength=900,
            justify="left",
        )
        self.tablet_pick_label.pack(anchor="w", pady=(8, 0))

    def _build_actions(self) -> None:
        frame = ttk.LabelFrame(self.root, text="Steps", padding=12, style="Step.TLabelframe")
        frame.pack(fill="x", padx=16, pady=8)

        row1 = ttk.Frame(frame)
        row1.pack(fill="x")
        self.btn_import = ttk.Button(
            row1, text="1 · Import catalog (.xlsx)…", style="Accent.TButton",
            command=self.on_import,
        )
        self.btn_import.pack(side="left")

        self.btn_load_civ = ttk.Button(
            row1, text="Open existing .civ…", command=self.on_load_civ,
        )
        self.btn_load_civ.pack(side="left", padx=(8, 0))

        self.btn_export = ttk.Button(
            row1, text="2 · Send to tablet (automatic)…", style="Accent.TButton",
            command=self.on_send_to_tablet, state="disabled",
        )
        self.btn_export.pack(side="right")

        self.btn_save = ttk.Button(
            row1, text="Save .civ locally…", command=self.on_save_local, state="disabled",
        )
        self.btn_save.pack(side="right", padx=(0, 8))

        # Safety row.
        row2 = ttk.Frame(frame)
        row2.pack(fill="x", pady=(10, 0))
        self.btn_restore_import = ttk.Button(
            row2, text="Restore last import", command=self.on_restore_import, state="disabled",
        )
        self.btn_restore_import.pack(side="left")

        self.btn_restore_backup = ttk.Button(
            row2, text="Restore from backup…", command=self.on_restore_backup,
        )
        self.btn_restore_backup.pack(side="left", padx=(8, 0))

        self.btn_delete_all = ttk.Button(
            row2, text="Delete ALL books…", style="Danger.TButton",
            command=self.on_delete_all, state="disabled",
        )
        self.btn_delete_all.pack(side="right")

    def _build_findings(self) -> None:
        frame = ttk.LabelFrame(self.root, text="Review — duplicates & potential problems",
                               padding=10, style="Step.TLabelframe")
        frame.pack(fill="both", expand=True, padx=16, pady=4)

        self.summary_var = tk.StringVar(value="No catalog loaded yet.")
        ttk.Label(frame, textvariable=self.summary_var, style="Sub.TLabel").pack(anchor="w")

        cols = ("severity", "message", "count")
        tree = ttk.Treeview(frame, columns=cols, show="headings", height=8)
        tree.heading("severity", text="Level")
        tree.heading("message", text="Finding")
        tree.heading("count", text="Books")
        tree.column("severity", width=90, anchor="w", stretch=False)
        tree.column("message", width=680, anchor="w")
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
                  wraplength=900, justify="left").pack(anchor="w", pady=(6, 0))

    def _build_progress(self) -> None:
        frame = ttk.Frame(self.root, padding=(16, 4))
        frame.pack(fill="x")
        self.progress = ttk.Progressbar(frame, mode="determinate", maximum=1.0)
        self.progress.pack(side="left", fill="x", expand=True)
        self.btn_abort = ttk.Button(frame, text="Abort", command=self.on_abort, state="disabled")
        self.btn_abort.pack(side="left", padx=(8, 0))
        self.progress_var = tk.StringVar(value="")
        ttk.Label(self.root, textvariable=self.progress_var, style="Sub.TLabel",
                  padding=(16, 0)).pack(anchor="w")

    def _build_footer(self) -> None:
        bar = ttk.Frame(self.root, padding=(16, 6))
        bar.pack(fill="x", side="bottom")
        self.footer_var = tk.StringVar(value="Ready.")
        ttk.Label(bar, textvariable=self.footer_var, style="Sub.TLabel").pack(side="left")
        ttk.Label(bar, text="Fully offline · no internet required", style="Sub.TLabel").pack(side="right")

    # ------------------------------------------------------------- helpers

    def _refresh_status(self) -> None:
        s = self.session
        n = len(s.books)
        if s.source_path:
            src = os.path.basename(s.source_path)
            self.chosen_var.set(f"Chosen: {src}  —  {n} books ready to send")
        else:
            self.chosen_var.set("No Excel file chosen yet — click step 1.")
        dirty = " · unsaved changes" if s.dirty else ""
        self.status_var.set(f"Working catalog: {n} books{dirty}")
        self.tablet_pick_var.set(s.tablet_pick_hint())

        has_books = n > 0 and not self._busy
        can_send = has_books and self._tablet_ready
        self.btn_export.config(state="normal" if can_send else "disabled")
        self.btn_save.config(state="normal" if has_books else "disabled")
        self.btn_delete_all.config(state="normal" if has_books else "disabled")
        self.btn_restore_import.config(
            state="normal" if (s.can_restore_import() and not self._busy) else "disabled"
        )

    def _set_busy(self, busy: bool) -> None:
        self._busy = busy
        state = "disabled" if busy else "normal"
        for b in (self.btn_import, self.btn_load_civ, self.btn_restore_backup):
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
            self.tree.insert("", "end", values=(f.severity, f.message, count), tags=(tag,))
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
                self.detail_var.set("Examples: " + " | ".join(f.examples))
            else:
                self.detail_var.set("")

    # ------------------------------------------- worker-thread plumbing

    def _run_worker(self, fn: Callable, on_done: Callable, on_error: Optional[Callable] = None,
                    progress_label: str = "Working…") -> None:
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
                    self.footer_var.set("Operation aborted — no changes were committed.")
                    messagebox.showinfo("Aborted", "The operation was aborted safely. "
                                                    "Nothing was changed.")
                elif kind == "error":
                    on_error, exc = payload
                    self._set_busy(False)
                    self.progress.config(value=0.0)
                    self.progress_var.set("")
                    if on_error:
                        on_error(exc)
                    else:
                        self.footer_var.set("Error.")
                        messagebox.showerror("Error", str(exc))
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
            text = "adb not found — keep adb\\ folder next to LibraryTool.exe"
            color = "#b00020"
        elif diag.ready:
            d = diag.ready[0]
            text = f"Connected & authorized — {d.model}"
            color = "#1b7a3d"
        elif any(d.state == "unauthorized" for d in diag.devices):
            text = "Tablet found but NOT authorized — run authorize_tablet.bat once"
            color = "#9a6700"
        elif diag.devices:
            text = "Tablet detected but not ready — replug USB cable"
            color = "#9a6700"
        else:
            text = "No tablet — plug in USB-C cable"
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
            self.progress_var.set("Aborting at the next safe point…")

    def on_import(self) -> None:
        path = filedialog.askopenfilename(
            title="Choose the catalog workbook",
            filetypes=[("Excel workbook", "*.xlsx"), ("All files", "*.*")],
        )
        if not path:
            return
        if self.session.books and self.session.dirty:
            if not messagebox.askyesno(
                "Replace current catalog?",
                "You have unsaved changes. Importing replaces the whole working "
                "catalog (a backup is taken first). Continue?",
            ):
                return

        def work(progress, abort):
            return self.session.import_xlsx(path, progress, abort)

        def done(outcome):
            self._show_report(outcome.report)
            rep = outcome.report
            src = os.path.basename(path)
            self.footer_var.set(
                f"Chosen: {src} — {outcome.convert.imported} books loaded."
            )
            if rep.has_errors:
                messagebox.showwarning(
                    "Import finished with problems",
                    "The catalog imported, but there are ERRORS that should be "
                    "fixed in the source sheet before exporting to the tablet. "
                    "See the review table.",
                )
            self._refresh_status()

        self._run_worker(work, done, progress_label="Importing…")

    def on_load_civ(self) -> None:
        path = filedialog.askopenfilename(
            title="Open a .civ catalog (e.g. read back from the tablet)",
            filetypes=[("Tablet catalog", "*.civ"), ("All files", "*.*")],
        )
        if not path:
            return
        try:
            n = self.session.load_civ(path)
        except Exception as exc:
            messagebox.showerror("Could not open .civ", str(exc))
            return
        self._show_report(self.session.last_report)
        self.footer_var.set(f"Loaded {n} books from {os.path.basename(path)}.")
        self._refresh_status()

    def on_save_local(self) -> None:
        path = filedialog.asksaveasfilename(
            title="Save tablet catalog",
            defaultextension=".civ",
            initialfile=self.session.suggested_export_filename(),
            filetypes=[("Tablet catalog", "*.civ")],
        )
        if not path:
            return
        try:
            self.session.save_civ(path)
        except Exception as exc:
            messagebox.showerror("Save failed", str(exc))
            return
        fname = os.path.basename(path)
        self.footer_var.set(f"Saved {len(self.session.books)} books → {fname}")
        messagebox.showinfo(
            "Saved",
            f"Saved {len(self.session.books)} books to:\n{path}\n\n"
            f"Copy {fname} to the tablet Download folder.\n"
            f"On the tablet: Management → Sync → choose {fname}",
        )

    def on_send_to_tablet(self) -> None:
        report = self.session.validate_current()
        self._show_report(report)
        if report.has_errors:
            if not messagebox.askyesno(
                "Errors present — send anyway?",
                f"There are {len(report.errors)} ERROR-level problems "
                "(see the review table). Sending them to the tablet may cause "
                "wrong behaviour.\n\nSend anyway?",
            ):
                return

        n = len(self.session.books)
        batch = self.session.next_send_batch()
        src = os.path.basename(self.session.source_path or "") or "catalog"
        if not messagebox.askyesno(
            "Send to tablet",
            f"Connect the tablet with USB-C, then click Yes.\n\n"
            f"You chose: {src}\n"
            f"This send creates file: {batch}.civ\n"
            f"Books to send: {n}\n\n"
            f"Automatic: the tablet imports by itself.\n"
            f"Manual on tablet: open Download → choose {batch}.civ\n\n"
            "Continue?",
        ):
            return

        def work(progress, abort):
            return self.session.send_to_tablet(progress, abort)

        def done(result):
            batch = self.session.last_sent_batch or self.session.next_send_batch() - 1
            count = result.imported_count
            if count is not None:
                msg = (
                    f"Done. Sent {batch}.civ — {count} new books merged "
                    "(existing books were kept)."
                )
            else:
                msg = f"Sent {batch}.civ and triggered import on the tablet."
            self.footer_var.set(msg)
            self._refresh_status()
            src = os.path.basename(self.session.source_path or "") or "catalog"
            messagebox.showinfo(
                "Tablet updated",
                f"{msg}\n\n"
                f"You chose on PC: {src}\n"
                f"File on tablet Download: {batch}.civ\n"
                f"Device: {result.device.model} ({result.device.serial})\n\n"
                f"If importing manually on the tablet:\n"
                f"  Management → Sync → pick file {batch}.civ from Download\n\n"
                "Automatic send already imported — check import summary on tablet.",
            )

        def error(exc):
            diag = adb_transfer.diagnose()
            extra = ""
            if diag.devices_raw:
                extra = f"\n\nadb devices -l:\n{diag.devices_raw}"
            messagebox.showerror(
                "Could not send to tablet",
                f"{exc}{extra}",
            )

        self._run_worker(work, done, on_error=error, progress_label="Sending to tablet…")

    def _choose_destination(self) -> Optional[str]:
        dests = transfer.detect_destinations()
        win = tk.Toplevel(self.root)
        win.title("Choose export destination")
        win.geometry("520x360")
        win.transient(self.root)
        win.grab_set()

        ttk.Label(win, text="Where should the catalog file be saved?",
                  style="Status.TLabel", padding=12).pack(anchor="w")
        ttk.Label(
            win,
            text="Tip: the tablet itself usually does NOT appear here — that's "
                 "normal. Save to the Desktop or a USB drive; the next screen "
                 "shows how to drag the file onto the tablet. Or pick a USB "
                 "drive below / Browse to any folder.",
            style="Sub.TLabel", wraplength=480, justify="left", padding=(12, 0),
        ).pack(anchor="w")

        lb = tk.Listbox(win, height=8)
        for d in dests:
            lb.insert("end", f"{d.label}    [{d.path}]")
        lb.pack(fill="both", expand=True, padx=12, pady=8)

        chosen = {"path": None}

        def use_selected():
            sel = lb.curselection()
            if sel:
                chosen["path"] = dests[sel[0]].path
                win.destroy()

        def browse():
            folder = filedialog.askdirectory(title="Choose destination folder", parent=win)
            if folder:
                chosen["path"] = folder
                win.destroy()

        def use_desktop():
            desktop = os.path.join(os.path.expanduser("~"), "Desktop")
            chosen["path"] = desktop if os.path.isdir(desktop) else os.path.expanduser("~")
            win.destroy()

        btns = ttk.Frame(win, padding=12)
        btns.pack(fill="x")
        ttk.Button(btns, text="Save to Desktop", style="Accent.TButton",
                   command=use_desktop).pack(side="left")
        ttk.Button(btns, text="Browse folder…", command=browse).pack(side="left", padx=(8, 0))
        ttk.Button(btns, text="Use selected", command=use_selected).pack(side="right")
        ttk.Button(btns, text="Cancel", command=win.destroy).pack(side="right", padx=(0, 8))

        self.root.wait_window(win)
        return chosen["path"]

    def on_restore_import(self) -> None:
        if not self.session.can_restore_import():
            return
        if not messagebox.askyesno(
            "Restore last import",
            "Revert to the catalog that was loaded before the most recent "
            "import? This is safe because nothing has changed since.",
        ):
            return
        try:
            n = self.session.restore_import()
        except Exception as exc:
            messagebox.showerror("Restore failed", str(exc))
            return
        self._show_report(self.session.last_report)
        self.footer_var.set(f"Restored {n} books from before the last import.")
        self._refresh_status()

    def on_restore_backup(self) -> None:
        entries = backups.list_backups()
        if not entries:
            messagebox.showinfo("No backups", "There are no backups yet.")
            return

        win = tk.Toplevel(self.root)
        win.title("Restore from backup")
        win.geometry("620x400")
        win.transient(self.root)
        win.grab_set()
        ttk.Label(win, text="Pick a restore point", style="Status.TLabel",
                  padding=12).pack(anchor="w")
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
            self.footer_var.set(f"Restored {n} books from backup ({entry.when}).")
            self._refresh_status()

        btns = ttk.Frame(win, padding=12)
        btns.pack(fill="x")
        ttk.Button(btns, text="Restore selected", style="Accent.TButton",
                   command=do_restore).pack(side="right")
        ttk.Button(btns, text="Cancel", command=win.destroy).pack(side="right", padx=(0, 8))
        self.root.wait_window(win)

    def on_delete_all(self) -> None:
        n = len(self.session.books)
        if n == 0:
            return
        # Step 1: intent.
        if not messagebox.askyesno(
            "Delete ALL books? (1 of 3)",
            f"This will remove all {n} books from the working catalog.\n\n"
            "A restorable backup is taken first. Continue?",
            icon="warning",
        ):
            return
        # Step 2: typed confirmation.
        confirm = _ask_text(
            self.root,
            "Delete ALL books? (2 of 3)",
            f'Type DELETE (in capitals) to confirm removing all {n} books:',
        )
        if confirm != "DELETE":
            if confirm is not None:
                messagebox.showinfo("Cancelled", "Text did not match. Nothing was deleted.")
            return
        # Step 3: final irreversible-looking confirm (still reversible via backup).
        if not messagebox.askyesno(
            "Final confirmation (3 of 3)",
            f"Last chance. Delete all {n} books now?\n\n"
            "You can restore them afterwards from 'Restore from backup'.",
            icon="warning",
        ):
            return

        snapshot = self.session.delete_all()
        self._show_report(self.session.last_report)
        self._refresh_status()
        self.footer_var.set(f"Deleted all books. Backup saved ({snapshot.when}).")
        if messagebox.askyesno(
            "Deleted — undo now?",
            "All books were deleted (a backup was saved). Undo immediately?",
        ):
            self.session.restore_from_backup(snapshot)
            self._show_report(self.session.last_report)
            self._refresh_status()
            self.footer_var.set("Undo complete — books restored.")


def _ask_text(parent: tk.Tk, title: str, prompt: str) -> Optional[str]:
    """A small modal text-entry dialog (avoids importing simpledialog styling)."""
    win = tk.Toplevel(parent)
    win.title(title)
    win.geometry("440x170")
    win.transient(parent)
    win.grab_set()
    ttk.Label(win, text=prompt, wraplength=400, padding=14, justify="left").pack(anchor="w")
    var = tk.StringVar()
    entry = ttk.Entry(win, textvariable=var, width=30)
    entry.pack(padx=14, pady=4)
    entry.focus_set()
    result = {"value": None}

    def ok():
        result["value"] = var.get()
        win.destroy()

    def cancel():
        result["value"] = None
        win.destroy()

    btns = ttk.Frame(win, padding=12)
    btns.pack(fill="x")
    ttk.Button(btns, text="Confirm", command=ok).pack(side="right")
    ttk.Button(btns, text="Cancel", command=cancel).pack(side="right", padx=(0, 8))
    entry.bind("<Return>", lambda _e: ok())
    parent.wait_window(win)
    return result["value"]


def main() -> None:
    root = tk.Tk()
    LibraryToolApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
