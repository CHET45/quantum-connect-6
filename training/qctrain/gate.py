"""Fixed-node gate of two evaluations over several board sizes, at Idle priority, through the Java tool FastMatch.

python -m qctrain.gate --a MODEL/eval.qcw --b hand --sizes 15x11:12000:100,10x10:12000:100 --threads 8 --out DIR

--sizes is ROWSxCOLS:NODES:PAIRS. --a and --b are .qcw files or "hand"; A's score is reported with a 95 percent
confidence interval over colour-swapped pairs, per size and pooled, and appended to --out/gates.jsonl.
"""
import argparse
import json
import math
import os
import subprocess
import sys
import time

IDLE = 0x00000040
BELOW_NORMAL = 0x00004000
JAVA = os.path.expanduser("~/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2/bin/java.exe")


def stats(pairs):
    n = len(pairs)
    if n == 0:
        return 0.5, 0.0
    m = sum(pairs) / n
    if n < 2:
        return m, 0.5
    v = sum((p - m) ** 2 for p in pairs) / (n - 1)
    return m, 1.96 * math.sqrt(v / n)


def elo(score):
    s = min(max(score, 1e-3), 1 - 1e-3)
    return -400 * math.log10(1 / s - 1)


def read_pairs(path):
    by_pair = {}
    reasons = {}
    plies = 0
    games = 0
    with open(path, encoding="utf-8") as f:
        for line in f:
            r = json.loads(line)
            by_pair.setdefault(r["pair"], []).append(r["scoreA"])
            reasons[r["reason"]] = reasons.get(r["reason"], 0) + 1
            plies += r["plies"]
            games += 1
    return [sum(v) / len(v) for v in by_pair.values()], reasons, plies / max(1, games)


def run_size(args, size, nodes, pairs, seed, tag):
    rows, cols = size.split("x")
    out = os.path.join(args.out, f"{tag}-{size}.jsonl")
    cmd = [JAVA, "-Xmx6g", "-cp", args.cp, "ch.qc.tools.FastMatch", "--a", f"contempt=0@{args.a}",
           "--b", f"contempt=0@{args.b}", "--nodes", str(nodes), "--pairs", str(pairs), "--threads", str(args.threads),
           "--seed", str(seed), "--rows", rows, "--cols", cols, "--out", out]
    t0 = time.time()
    p = subprocess.run(cmd, capture_output=True, text=True, creationflags=BELOW_NORMAL if args.below else IDLE if args.idle else 0, cwd=args.cwd)
    if p.returncode != 0:
        raise SystemExit(p.stderr[-2000:])
    ps, reasons, plies = read_pairs(out)
    m, ci = stats(ps)
    return {"size": size, "nodes": nodes, "pairs": len(ps), "score": m, "ci": ci, "elo": elo(m),
            "reasons": reasons, "plies": plies, "minutes": (time.time() - t0) / 60, "out": out}, ps


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--a", required=True)
    ap.add_argument("--b", default="hand")
    ap.add_argument("--sizes", required=True)
    ap.add_argument("--threads", type=int, default=8)
    ap.add_argument("--seed", type=int, default=77)
    ap.add_argument("--out", required=True)
    ap.add_argument("--tag", default=None)
    ap.add_argument("--cp", default="build/classes/java/main;build/classes/java/tools;libs/core-2.0-SNAPSHOT.jar")
    ap.add_argument("--cwd", default=".")
    ap.add_argument("--normal", dest="idle", action="store_false")
    ap.add_argument("--below", action="store_true", help="Below Normal priority instead of Idle")
    args = ap.parse_args()
    args.a = os.path.abspath(args.a) if args.a != "hand" else args.a
    args.b = os.path.abspath(args.b) if args.b != "hand" else args.b
    args.out = os.path.abspath(args.out)
    os.makedirs(args.out, exist_ok=True)
    tag = args.tag or time.strftime("%H%M%S")
    pooled = []
    results = []
    for item in args.sizes.split(","):
        size, nodes, pairs = item.split(":")
        r, ps = run_size(args, size, int(nodes), int(pairs), args.seed, tag)
        pooled += ps
        results.append(r)
        print(f"{tag} {size} {nodes}n: {r['pairs']} pairs, score {100 * r['score']:.1f}% +-{100 * r['ci']:.1f} "
              f"({r['elo']:+.0f} Elo), {r['reasons']}, plies {r['plies']:.0f}, {r['minutes']:.1f} min", flush=True)
    m, ci = stats(pooled)
    print(f"{tag} pooled: {len(pooled)} pairs, score {100 * m:.1f}% +-{100 * ci:.1f} ({elo(m):+.0f} Elo)", flush=True)
    with open(os.path.join(args.out, "gates.jsonl"), "a", encoding="utf-8") as f:
        f.write(json.dumps({"tag": tag, "a": args.a, "b": args.b, "results": results, "pooled": m, "pooled_ci": ci}) + "\n")


if __name__ == "__main__":
    sys.exit(main())
