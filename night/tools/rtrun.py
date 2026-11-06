"""Runs a real-time experiment under a machine-wide lock and at high priority, so real-time tests do not overlap.

Usage: python rtrun.py [--slot 2] [--log FILE] -- command args...
A lock is a directory; a lock older than MAX_AGE seconds is treated as stale and taken over. Slot 2 is a second lane
the coordinator uses for integration gates, so they need not wait behind the whole queue of slot 1.
"""
import os
import subprocess
import sys
import time

LOGS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "logs")
MAX_AGE = 45 * 60
POLL = 15


def acquire(lock):
    while True:
        try:
            os.mkdir(lock)
            return
        except FileExistsError:
            try:
                if time.time() - os.path.getmtime(lock) > MAX_AGE:
                    os.rmdir(lock)
                    continue
            except OSError:
                pass
            time.sleep(POLL)


def main():
    args = sys.argv[1:]
    log = None
    lock = os.path.join(LOGS, "rt.lock")
    while args and args[0] in ("--log", "--slot"):
        if args[0] == "--log":
            log = args[1]
        elif args[1] != "1":
            lock = os.path.join(LOGS, "rt.lock" + args[1])
        args = args[2:]
    if args and args[0] == "--":
        args = args[1:]
    if not args:
        sys.exit("usage: rtrun.py [--slot N] [--log FILE] -- command args...")
    acquire(lock)
    try:
        out = open(log, "a", encoding="utf-8") if log else None
        code = subprocess.call(args, stdout=out, stderr=subprocess.STDOUT if out else None,
                               creationflags=subprocess.HIGH_PRIORITY_CLASS)
        if out:
            out.close()
    finally:
        try:
            os.rmdir(lock)
        except OSError:
            pass
    sys.exit(code)


main()
