"""Summary of draw-sim.sh lanes: per label games, A wins / draws / B wins, draw reasons, plies, lost turns per 1000.

Usage: python -I draw-report.py CSV [CSV ...]
"""
import sys
from collections import defaultdict


def main():
    games = defaultdict(list)
    calls = defaultdict(lambda: {"A": [0, 0], "B": [0, 0]})
    for path in sys.argv[1:]:
        for line in open(path, encoding="utf-8"):
            f = line.strip().split(",")
            if f[0] == "game":
                # game,label,size,g,firstSide,winner,plies,reason,seconds,...
                games[(f[1], f[2])].append((f[5], int(f[6]), f[7], float(f[8])))
            elif f[0] not in ("tick",) and len(f) > 9:
                c = calls[(f[0], f[1])][f[3]]
                c[0] += 1
                c[1] += int(f[8])
    print(f"{'label':28} {'size':7} {'n':>3} {'A win':>5} {'draw':>5} {'B win':>5}  draws(time/full)  plies  sec  lostA/1k lostB/1k")
    for key in sorted(games):
        g = games[key]
        a = sum(1 for x in g if x[0] == "A")
        b = sum(1 for x in g if x[0] == "B")
        d = len(g) - a - b
        t = sum(1 for x in g if x[0] == "-" and x[2] == "TIME")
        plies = sum(x[1] for x in g) / len(g)
        sec = sum(x[3] for x in g) / len(g)
        c = calls[key]
        la = 1000 * c["A"][1] / max(1, c["A"][0])
        lb = 1000 * c["B"][1] / max(1, c["B"][0])
        print(f"{key[0]:28} {key[1]:7} {len(g):3} {a:5} {d:5} {b:5}  {t:>5}/{d - t:<5}       {plies:5.0f} {sec:4.1f}  {la:7.1f} {lb:7.1f}")


if __name__ == "__main__":
    main()
