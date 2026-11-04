"""Self-play that keeps running over all generations, one chunk per board size in turn.

python -m qctrain.selfplayd --root DIR --cp CLASSPATH --threads 10

DIR/current.json names the tables to play with and is read again before every chunk, so a newly accepted generation takes
over at the next chunk:
  {"generation": 2, "weights": "...qcw", "prev": "...qcw", "sized": {"8x12": "...qcw"}, "plan": ["15x11:40000:100000", ...]}
A size in "sized" plays with those tables instead, and its data goes to DIR/sized-<name of its directory>/ so the 8x12 line
keeps its own generations. Data goes to DIR/gen<generation>/<size>-<number>/ in new directories only. While DIR/pause
exists no new chunk starts, which frees the threads for gates; DIR/stop ends the program after the running chunk. Threads
per chunk respect the machine limit (budget.py); Below Normal priority.
"""
import argparse
import json
import os
import subprocess
import time

from . import budget
from .gate import BELOW_NORMAL, JAVA


def next_dir(parent, name):
    n = 0
    while True:
        d = os.path.join(parent, f"{name}-{n:03d}")
        if not os.path.exists(d):
            return d
        n += 1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", required=True)
    ap.add_argument("--cp", required=True)
    ap.add_argument("--threads", type=int, default=10)
    ap.add_argument("--seed", type=int, default=5000)
    ap.add_argument("--xmx", default="4g")
    ap.add_argument("--reserve", type=int, default=2, help="threads of the limit left for gates and fits of this stream")
    args = ap.parse_args()
    seed = args.seed + int(time.time()) % 100000
    index = 0
    while True:
        if os.path.exists(os.path.join(args.root, "stop")):
            print("stop", flush=True)
            return
        if os.path.exists(os.path.join(args.root, "pause")):
            time.sleep(5)
            continue
        with open(os.path.join(args.root, "current.json"), encoding="utf-8") as f:
            cur = json.load(f)
        plan = cur["plan"]
        item = plan[index % len(plan)]
        index += 1
        size, nodes, positions = item.split(":")
        rows, cols = size.split("x")
        sized = cur.get("sized", {}).get(size)
        if sized:
            parent = os.path.join(args.root, "sized-" + os.path.basename(os.path.dirname(sized)))
            weights, prev, generation = sized, None, 100
        else:
            parent = os.path.join(args.root, f"gen{cur['generation']}")
            weights, prev, generation = cur["weights"], cur.get("prev"), cur["generation"]
        os.makedirs(parent, exist_ok=True)
        d = next_dir(parent, size)
        os.makedirs(d)
        threads = budget.free_threads(args.threads + args.reserve, least=4 + args.reserve) - args.reserve
        seed += 1
        cmd = [JAVA, f"-Xmx{args.xmx}", "-cp", args.cp, "ch.qc.tools.SelfPlayMain", "--out", d, "--positions", positions,
               "--nodes", nodes, "--threads", str(threads), "--seed", str(seed), "--rows", rows, "--cols", cols,
               "--generation", str(generation), "--weights", weights]
        if prev:
            cmd += ["--prev", prev, "--mix", "0.7,0.2,0.1"]
        t0 = time.time()
        with open(d + ".log", "w", encoding="utf-8") as log:
            rc = subprocess.run(cmd, stdout=log, stderr=subprocess.STDOUT, creationflags=BELOW_NORMAL).returncode
        if rc == 0:
            with open(os.path.join(d, "done.txt"), "w", encoding="utf-8") as f:
                f.write(f"{positions} positions, {threads} threads, {(time.time() - t0) / 60:.1f} min\n")
        print(f"{time.strftime('%H:%M')} {d} rc={rc} {threads} threads {(time.time() - t0) / 60:.1f} min", flush=True)
        if rc != 0:
            time.sleep(30)


if __name__ == "__main__":
    main()
