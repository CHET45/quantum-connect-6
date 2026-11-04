"""Self-play over several board sizes, one after the other, at Idle priority, each into its own new directory.

python -m qctrain.multiplay --root DIR --plan 10x10:8000:100000,7x9:8000:60000 --threads 6 --seed 100 [--weights W.qcw]

--plan is ROWSxCOLS:NODES:POSITIONS[:TAG]. A finished size (marker file done.txt) is skipped, so the script can be restarted;
an unfinished one is not resumed: its directory gets a new seed suffix so nothing is ever overwritten.
"""
import argparse
import os
import subprocess
import time

from .gate import BELOW_NORMAL, IDLE, JAVA


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", required=True)
    ap.add_argument("--plan", required=True)
    ap.add_argument("--threads", type=int, default=6)
    ap.add_argument("--seed", type=int, default=100)
    ap.add_argument("--weights", default=None)
    ap.add_argument("--prev", default=None)
    ap.add_argument("--mix", default=None)
    ap.add_argument("--generation", type=int, default=0)
    ap.add_argument("--cp", required=True)
    ap.add_argument("--xmx", default="4g")
    ap.add_argument("--below", action="store_true", help="Below Normal priority instead of Idle")
    args = ap.parse_args()
    for i, item in enumerate(args.plan.split(",")):
        size, nodes, positions, *tag = item.split(":")
        rows, cols = size.split("x")
        base = os.path.join(args.root, size + ("-" + tag[0] if tag else ""))
        d = base
        if os.path.exists(os.path.join(d, "done.txt")):
            continue
        n = 0
        while os.path.exists(d):
            n += 1
            d = f"{base}-r{n}"
        os.makedirs(d)
        cmd = [JAVA, f"-Xmx{args.xmx}", "-cp", args.cp, "ch.qc.tools.SelfPlayMain", "--out", d, "--positions", positions,
               "--nodes", nodes, "--threads", str(args.threads), "--seed", str(args.seed + 100 * i + n),
               "--rows", rows, "--cols", cols, "--generation", str(args.generation)]
        if args.weights:
            cmd += ["--weights", args.weights]
        if args.prev:
            cmd += ["--prev", args.prev]
        if args.mix:
            cmd += ["--mix", args.mix]
        t0 = time.time()
        with open(d + ".log", "w", encoding="utf-8") as log:
            p = subprocess.run(cmd, stdout=log, stderr=subprocess.STDOUT, creationflags=BELOW_NORMAL if args.below else IDLE)
        if p.returncode == 0:
            with open(os.path.join(d, "done.txt"), "w", encoding="utf-8") as f:
                f.write(f"{positions} positions in {(time.time() - t0) / 60:.1f} min\n")
        print(f"{size} rc={p.returncode} {(time.time() - t0) / 60:.1f} min -> {d}", flush=True)


if __name__ == "__main__":
    main()
