"""Entry point so the package can be launched with ``python -m library_tool``."""

if __name__ == "__main__":
    try:
        from .app import main
    except ImportError:
        # PyInstaller and some direct-script launches have no package context.
        from library_tool.app import main
    main()
