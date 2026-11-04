"""Runs a command at Idle priority, output to a file: python -m qctrain.idle LOG command args..."""
import subprocess
import sys

IDLE = 0x00000040

if __name__ == "__main__":
    with open(sys.argv[1], "w", encoding="utf-8") as log:
        sys.exit(subprocess.run(sys.argv[2:], stdout=log, stderr=subprocess.STDOUT, creationflags=IDLE).returncode)
