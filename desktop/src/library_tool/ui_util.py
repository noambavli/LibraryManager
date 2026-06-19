"""UI helpers and modern theme for the Hebrew Tkinter desktop app."""

from __future__ import annotations

import tkinter as tk
from tkinter import ttk
from typing import Callable, Optional

# Windows Hebrew-friendly stack (Segoe UI ships with Windows 10+).
FONT_DISPLAY = ("Segoe UI", 20, "bold")
FONT_TITLE = ("Segoe UI", 13, "bold")
FONT_SECTION = ("Segoe UI", 11, "bold")
FONT_STATUS = ("Segoe UI", 10, "bold")
FONT_BODY = ("Segoe UI", 10)
FONT_BODY_BOLD = ("Segoe UI", 10, "bold")
FONT_HINT = ("Segoe UI", 9)
FONT_CAPTION = ("Segoe UI", 9)
FONT_MONO = ("Consolas", 9)

# Right-to-left mark — helps Windows render Hebrew labels correctly in LTR widgets.
RLM = "\u200f"

COLORS = {
    "bg": "#eef1f6",
    "surface": "#ffffff",
    "surface_alt": "#f8fafc",
    "border": "#d8dee9",
    "border_soft": "#e8edf4",
    "text": "#0f172a",
    "text_secondary": "#475569",
    "text_muted": "#64748b",
    "primary": "#2563eb",
    "primary_hover": "#1d4ed8",
    "primary_soft": "#dbeafe",
    "success": "#15803d",
    "success_soft": "#dcfce7",
    "warning": "#b45309",
    "warning_soft": "#fef3c7",
    "error": "#b91c1c",
    "error_soft": "#fee2e2",
    "info": "#1d4ed8",
    "info_soft": "#dbeafe",
    "accent_bar": "#1e40af",
    "footer": "#f1f5f9",
    "tree_alt": "#f8fafc",
    "tree_select": "#dbeafe",
}


def rtl(text: str) -> str:
    """Prefix with RLM when the string contains Hebrew."""
    if not text:
        return text
    if any("\u0590" <= ch <= "\u05ff" for ch in text):
        return RLM + text
    return text


def user_error(exc: BaseException) -> str:
    """Map exceptions to short Hebrew text for message boxes."""
    from . import strings_he as S

    if isinstance(exc, ValueError):
        msg = str(exc).strip()
        if msg:
            return msg
        return S.ERR_GENERIC
    if isinstance(exc, PermissionError):
        return S.ERR_FILE_LOCKED
    if isinstance(exc, FileNotFoundError):
        return S.ERR_FILE_NOT_FOUND
    if isinstance(exc, OSError) and getattr(exc, "errno", None) in (13, 32):
        return S.ERR_FILE_LOCKED
    raw = str(exc).strip()
    return raw if raw else S.ERR_GENERIC


def apply_app_theme(root: tk.Tk) -> ttk.Style:
    """Configure clam theme with a clean, modern palette."""
    root.configure(bg=COLORS["bg"])
    style = ttk.Style(root)
    try:
        style.theme_use("clam")
    except tk.TclError:
        pass

    style.configure(".", background=COLORS["bg"], foreground=COLORS["text"], font=FONT_BODY)
    style.configure("TFrame", background=COLORS["bg"])
    style.configure("Card.TFrame", background=COLORS["surface"])
    style.configure("CardInner.TFrame", background=COLORS["surface"])
    style.configure("Footer.TFrame", background=COLORS["footer"])
    style.configure("Dock.TFrame", background=COLORS["surface"])

    style.configure(
        "Title.TLabel",
        background=COLORS["surface"],
        foreground=COLORS["text"],
        font=FONT_DISPLAY,
    )
    style.configure(
        "Sub.TLabel",
        background=COLORS["surface"],
        foreground=COLORS["text_secondary"],
        font=FONT_BODY,
    )
    style.configure(
        "Caption.TLabel",
        background=COLORS["surface"],
        foreground=COLORS["text_muted"],
        font=FONT_CAPTION,
    )
    style.configure(
        "Section.TLabel",
        background=COLORS["surface"],
        foreground=COLORS["text"],
        font=FONT_SECTION,
    )
    style.configure(
        "Status.TLabel",
        background=COLORS["surface"],
        foreground=COLORS["text"],
        font=FONT_STATUS,
    )
    style.configure(
        "Footer.TLabel",
        background=COLORS["footer"],
        foreground=COLORS["text_muted"],
        font=FONT_CAPTION,
    )
    style.configure(
        "Dock.TLabel",
        background=COLORS["surface"],
        foreground=COLORS["text_secondary"],
        font=FONT_BODY,
    )

    _button_style(style, "TButton", COLORS["surface_alt"], COLORS["text"], COLORS["border"])
    _button_style(style, "Primary.TButton", COLORS["primary"], "#ffffff", COLORS["primary"])
    _button_style(style, "Secondary.TButton", COLORS["surface"], COLORS["text"], COLORS["border"])
    _button_style(style, "Danger.TButton", COLORS["error_soft"], COLORS["error"], COLORS["error"])

    style.configure(
        "Modern.Horizontal.TProgressbar",
        troughcolor=COLORS["border_soft"],
        background=COLORS["primary"],
        bordercolor=COLORS["border_soft"],
        lightcolor=COLORS["primary"],
        darkcolor=COLORS["primary"],
        thickness=8,
    )

    style.configure(
        "Modern.Treeview",
        background=COLORS["surface"],
        fieldbackground=COLORS["surface"],
        foreground=COLORS["text"],
        rowheight=30,
        font=FONT_BODY,
        bordercolor=COLORS["border"],
        relief="flat",
    )
    style.configure(
        "Modern.Treeview.Heading",
        background=COLORS["surface_alt"],
        foreground=COLORS["text_secondary"],
        font=FONT_SECTION,
        relief="flat",
        padding=(10, 8),
    )
    style.map(
        "Modern.Treeview",
        background=[("selected", COLORS["tree_select"])],
        foreground=[("selected", COLORS["text"])],
    )

    return style


def _button_style(
    style: ttk.Style,
    name: str,
    bg: str,
    fg: str,
    border: str,
) -> None:
    style.configure(
        name,
        background=bg,
        foreground=fg,
        bordercolor=border,
        focusthickness=0,
        focuscolor=bg,
        padding=(18, 10),
        font=FONT_BODY_BOLD if "Primary" in name or "Danger" in name else FONT_BODY,
    )
    style.map(
        name,
        background=[
            ("active", _shade(bg, -12) if bg != COLORS["primary"] else COLORS["primary_hover"]),
            ("disabled", COLORS["border_soft"]),
        ],
        foreground=[("disabled", COLORS["text_muted"])],
        bordercolor=[("disabled", COLORS["border_soft"])],
    )


def _shade(hex_color: str, delta: int) -> str:
    r = int(hex_color[1:3], 16)
    g = int(hex_color[3:5], 16)
    b = int(hex_color[5:7], 16)
    r = max(0, min(255, r + delta))
    g = max(0, min(255, g + delta))
    b = max(0, min(255, b + delta))
    return f"#{r:02x}{g:02x}{b:02x}"


class ScrollableFrame(ttk.Frame):
    """Vertically scrollable container — keeps all sections reachable on small screens."""

    def __init__(self, parent: tk.Misc, **kwargs) -> None:
        super().__init__(parent, **kwargs)
        self.canvas = tk.Canvas(
            self,
            highlightthickness=0,
            borderwidth=0,
            bg=COLORS["bg"],
        )
        self.scrollbar = ttk.Scrollbar(self, orient="vertical", command=self.canvas.yview)
        self.inner = ttk.Frame(self.canvas)
        self._window = self.canvas.create_window((0, 0), window=self.inner, anchor="nw")

        self.canvas.configure(yscrollcommand=self.scrollbar.set)
        self.scrollbar.pack(side="right", fill="y")
        self.canvas.pack(side="left", fill="both", expand=True)

        self.inner.bind("<Configure>", self._on_inner_configure)
        self.canvas.bind("<Configure>", self._on_canvas_configure)
        self.canvas.bind_all("<MouseWheel>", self._on_mousewheel, add="+")

    def _on_inner_configure(self, _event=None) -> None:
        self.canvas.configure(scrollregion=self.canvas.bbox("all"))

    def _on_canvas_configure(self, event) -> None:
        self.canvas.itemconfigure(self._window, width=event.width)

    def _on_mousewheel(self, event) -> None:
        if not self.winfo_ismapped():
            return
        delta = event.delta
        if delta == 0:
            return
        steps = -1 if delta > 0 else 1
        self.canvas.yview_scroll(steps, "units")


class Card(ttk.Frame):
    """White card with subtle border and optional section title."""

    def __init__(
        self,
        parent: tk.Misc,
        *,
        title: str = "",
        subtitle: str = "",
        padding: int = 20,
        pady: tuple[int, int] = (0, 14),
        fill: str = "x",
        expand: bool = False,
        **kwargs,
    ) -> None:
        super().__init__(parent, style="Card.TFrame", **kwargs)
        # Cards add themselves to their parent so callers only build content.
        self.pack(fill=fill, expand=expand, pady=pady)
        shell = tk.Frame(
            self,
            bg=COLORS["surface"],
            highlightbackground=COLORS["border"],
            highlightthickness=1,
        )
        shell.pack(fill="both", expand=True)
        self.body = ttk.Frame(shell, style="CardInner.TFrame", padding=padding)
        self.body.pack(fill="both", expand=True)

        if title:
            ttk.Label(self.body, text=rtl(title), style="Section.TLabel").pack(
                anchor="e", fill="x",
            )
        if subtitle:
            label(
                self.body,
                subtitle,
                font=FONT_HINT,
                color=COLORS["text_secondary"],
                bg=COLORS["surface"],
                wrap=860,
            ).pack(anchor="e", fill="x", pady=(4 if title else 0, 0))

    def content(self) -> ttk.Frame:
        return self.body


class StatusPill(tk.Frame):
    """Soft status chip for tablet connection."""

    _PALETTE = {
        "neutral": ("#e2e8f0", COLORS["text_muted"]),
        "success": (COLORS["success_soft"], COLORS["success"]),
        "warning": (COLORS["warning_soft"], COLORS["warning"]),
        "error": (COLORS["error_soft"], COLORS["error"]),
    }

    def __init__(self, parent: tk.Misc, textvariable: tk.StringVar, **kwargs) -> None:
        super().__init__(parent, bg=COLORS["surface"], **kwargs)
        self._wrap = tk.Frame(
            self,
            highlightbackground=COLORS["border"],
            highlightthickness=1,
            bg=COLORS["surface"],
        )
        self._wrap.pack(side="right")
        self._dot = tk.Label(self._wrap, text="●", font=("Segoe UI", 8))
        self._dot.pack(side="right", padx=(0, 6))
        self._label = tk.Label(
            self._wrap,
            textvariable=textvariable,
            font=FONT_STATUS,
            anchor="e",
            justify="right",
            padx=12,
            pady=5,
        )
        self._label.pack(side="right")
        self.set_tone("neutral")

    def set_tone(self, tone: str) -> None:
        bg, fg = self._PALETTE.get(tone, self._PALETTE["neutral"])
        self._wrap.configure(bg=bg, highlightbackground=bg)
        self._label.configure(bg=bg, fg=fg)
        self._dot.configure(bg=bg, fg=fg)


class InfoBanner(tk.Frame):
    """Soft tinted hint box."""

    def __init__(
        self,
        parent: tk.Misc,
        text: str = "",
        *,
        textvariable: Optional[tk.Variable] = None,
        tone: str = "info",
        wrap: int = 860,
        **kwargs,
    ) -> None:
        palettes = {
            "info": (COLORS["info_soft"], COLORS["info"]),
            "neutral": (COLORS["surface_alt"], COLORS["text_secondary"]),
        }
        bg, fg = palettes.get(tone, palettes["info"])
        super().__init__(parent, bg=bg, highlightbackground=bg, highlightthickness=1, **kwargs)
        kw = {
            "font": FONT_HINT,
            "anchor": "e",
            "justify": "right",
            "bg": bg,
            "fg": fg,
            "wraplength": wrap,
            "padx": 14,
            "pady": 10,
        }
        if textvariable is not None:
            kw["textvariable"] = textvariable
        else:
            kw["text"] = rtl(text)
        tk.Label(self, **kw).pack(fill="x")


def label(
    parent: tk.Misc,
    text: str = "",
    *,
    textvariable: Optional[tk.Variable] = None,
    style: Optional[str] = None,
    font: Optional[tuple] = None,
    wrap: int = 0,
    bold: bool = False,
    color: Optional[str] = None,
    bg: Optional[str] = None,
    **pack_kw,
) -> tk.Label:
    """Hebrew-friendly tk.Label (ttk.Label ignores justify on many themes)."""
    parent_bg = bg
    if parent_bg is None:
        try:
            parent_bg = parent.cget("bg")
        except tk.TclError:
            parent_bg = COLORS["surface"]

    kw: dict = {
        "font": font or (FONT_BODY_BOLD if bold else FONT_BODY),
        "anchor": "e",
        "justify": "right",
        "bg": parent_bg,
        "fg": color or COLORS["text"],
    }
    if textvariable is not None:
        kw["textvariable"] = textvariable
    else:
        kw["text"] = rtl(text)
    if wrap:
        kw["wraplength"] = wrap
    w = tk.Label(parent, **kw)
    if pack_kw:
        w.pack(**pack_kw)
    return w


def muted_caption(
    parent: tk.Misc,
    text: str = "",
    *,
    textvariable: Optional[tk.Variable] = None,
    wrap: int = 860,
    **pack_kw,
) -> tk.Label:
    return label(
        parent,
        text,
        textvariable=textvariable,
        font=FONT_CAPTION,
        color=COLORS["text_muted"],
        wrap=wrap,
        **pack_kw,
    )


def action_row(parent: tk.Misc, *, pady: tuple[int, int] = (14, 0)) -> ttk.Frame:
    row = ttk.Frame(parent, style="CardInner.TFrame")
    row.pack(fill="x", pady=pady)
    return row


def bind_dialog_rtl(win: tk.Toplevel) -> None:
    """Best-effort RTL for simple dialogs."""
    win.configure(bg=COLORS["bg"])
    try:
        win.option_add("*Label.justify", "right")
        win.option_add("*Label.anchor", "e")
    except tk.TclError:
        pass


def style_restore_dialog(win: tk.Toplevel) -> None:
    bind_dialog_rtl(win)
    win.configure(bg=COLORS["bg"])


def configure_findings_tree(tree: ttk.Treeview) -> None:
    tree.tag_configure("error", foreground=COLORS["error"])
    tree.tag_configure("warning", foreground=COLORS["warning"])
    tree.tag_configure("info", foreground=COLORS["info"])
    tree.tag_configure("odd", background=COLORS["tree_alt"])
    tree.tag_configure("even", background=COLORS["surface"])
