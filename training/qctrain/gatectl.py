"""Runs one gate at Below Normal with the threads that fit under the machine limit; see gate.py for the output.

python -m qctrain.gatectl TAG A B SIZES [--seed S] [--threads N] [--pause]

A and B are names under models/, .qcw files, or "hand". With --pause the self-play daemon is held for the whole gate
(its running chunk is ended; what it wrote stays valid), which gives the gate all the threads self-play had.
"""
import argparse
import os
import subprocess
import sys
import time

from . import budget, gate

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..", "qc-bot", "data", "night", "eval"))

_KILL = ("Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -match 'SelfPlayMain' "
         "-and $_.CommandLine -match 'night/eval/' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }")


def resolve(name):
    if name == "hand" or name.endswith(".qcw"):
        return name
    return os.path.join(ROOT, "models", name, "eval.qcw")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("tag")
    ap.add_argument("a")
    ap.add_argument("b")
    ap.add_argument("sizes")
    ap.add_argument("--seed", type=int, default=301)
    ap.add_argument("--threads", type=int, default=10)
    ap.add_argument("--pause", action="store_true")
    ap.add_argument("--openings", default=None, help="file prefix of the opening suites, the size and .txt are appended")
    ap.add_argument("--bin", default=os.path.join(ROOT, "bin-main"))
    args = ap.parse_args()
    pause = os.path.join(ROOT, "pause")
    cp = ";".join(os.path.join(args.bin, x) for x in ("main", "tools", "core-2.0-SNAPSHOT.jar"))
    if args.pause:
        with open(pause, "w", encoding="utf-8") as f:
            f.write(args.tag)
        subprocess.run(["powershell", "-NoProfile", "-Command", _KILL], capture_output=True, timeout=120)
        time.sleep(2)
    try:
        ns = argparse.Namespace(a=resolve(args.a), b=resolve(args.b), out=os.path.join(ROOT, "gates"), cp=cp, cwd=".",
                                idle=False, below=True, threads=args.threads, openings=args.openings)
        pooled = []
        results = []
        for item in args.sizes.split(","):
            size, nodes, pairs = item.split(":")
            ns.threads = budget.free_threads(args.threads)
            r, ps = gate.run_size(ns, size, int(nodes), int(pairs), args.seed, args.tag)
            pooled += ps
            results.append(r)
            print(f"{args.tag} {size} {nodes}n: {r['pairs']} pairs, score {100 * r['score']:.1f}% +-{100 * r['ci']:.1f} "
                  f"({r['elo']:+.0f} Elo), {r['reasons']}, plies {r['plies']:.0f}, {ns.threads} threads, "
                  f"{r['minutes']:.1f} min", flush=True)
        m, ci = gate.stats(pooled)
        print(f"{args.tag} pooled: {len(pooled)} pairs, score {100 * m:.1f}% +-{100 * ci:.1f} ({gate.elo(m):+.0f} Elo)", flush=True)
    finally:
        if args.pause:
            os.remove(pause)


if __name__ == "__main__":
    sys.exit(main())
