"""PyInstaller entry point — uses absolute imports so the bundled app starts."""

from library_tool.app import main

if __name__ == "__main__":
    main()
