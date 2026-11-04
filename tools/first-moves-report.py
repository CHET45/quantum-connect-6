"""Pools FirstMoves results by offset from the centre over board sizes.

Usage: python -I tools/first-moves-report.py first.jsonl [NODES ...]   (only runs with these node budgets)
A score is F's for "first" and S's for "reply", in percent; the CI is per game, treating games as independent.
"""
import json
import math
import sys
from collections import defaultdict


def main():
    rows = {}
    for line in open(sys.argv[1], encoding="utf-8"):
        if line.strip():
            r = json.loads(line)
            if r["mode"] in ("first", "reply"):
                rows[(r["mode"], r["size"], r["col"], r.get("seed", 0), r["nodes"])] = r
    for mode in ("first", "reply"):
        pooled = defaultdict(lambda: [0, 0.0, 0])
        by_size = defaultdict(dict)
        filt = sys.argv[2:]
        for (m, size, col, seed, nodes), r in rows.items():
            if m != mode or (filt and str(nodes) not in filt):
                continue
            if mode == "reply" and int(size.split("x")[1]) % 2 == 0:
                key = f"even {col - r['centre']:+d}"
            else:
                key = f"{abs(col - r['centre'])}"
            a = pooled[key]
            a[0] += r["games"]
            a[1] += r["score"] * r["games"] / 100
            a[2] += r["draws"]
            b = by_size[size].setdefault(key, [0, 0.0])
            b[0] += r["games"]
            b[1] += r["score"] * r["games"] / 100
            by_size[size]["searched"] = r["searched"] - r["centre"]
        if not pooled:
            continue
        print(f"## {mode}: score by offset from the centre")
        keys = sorted(pooled)
        print("| size | search plays | " + " | ".join(keys) + " |")
        print("|---|---|" + "---|" * len(keys))
        for size in sorted(by_size, key=lambda s: tuple(int(x) for x in s.split("x"))):
            row = by_size[size]
            cells = [f"{100 * row[k][1] / row[k][0]:.1f} ({row[k][0]})" if k in row else "" for k in keys]
            print(f"| {size} | {row['searched']:+d} | " + " | ".join(cells) + " |")
        cells = []
        for k in keys:
            n, s, d = pooled[k]
            m = s / n
            # variance of a game's score given the draw share
            v = max(0.0, (s - d * 0.5) / n + 0.25 * d / n - m * m)
            cells.append(f"{100 * m:.1f} +-{100 * 1.96 * math.sqrt(v / n):.1f} (n={n})")
        print("| pooled | | " + " | ".join(cells) + " |")
        print()


main()
