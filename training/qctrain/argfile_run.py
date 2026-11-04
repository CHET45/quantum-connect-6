"""Runs a module's main() with its arguments read from a file, one per line (Windows command lines are limited).

Usage: python -m qctrain.argfile_run MODULE ARGFILE
"""
import importlib
import sys

if __name__ == "__main__":
    module = sys.argv[1]
    with open(sys.argv[2], encoding="utf-8") as f:
        args = [line.rstrip("\n") for line in f if line.strip()]
    sys.argv = [module] + args
    sys.exit(importlib.import_module(module).main())
