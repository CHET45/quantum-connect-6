"""Score difference of two logs against the same opponent (book on, book off), with a 95% CI.

Usage: python -I tools/book-compare.py on.jsonl off.jsonl
"""
import json
import math
import sys


def scores(path):
    out = []
    for line in open(path, encoding="utf-8"):
        if line.strip():
            g = json.loads(line)
            if g["reason"] != "crash":
                out.append(g)
    return out


def stats(games):
    x = [g["scoreA"] for g in games]
    n = len(x)
    m = sum(x) / n
    v = sum((a - m) ** 2 for a in x) / (n - 1)
    return n, m, v


def main():
    a = scores(sys.argv[1])
    b = scores(sys.argv[2])
    na, ma, va = stats(a)
    nb, mb, vb = stats(b)
    d = ma - mb
    h = 1.96 * math.sqrt(va / na + vb / nb)
    print(f"on {100 * ma:.1f}% of {na}, off {100 * mb:.1f}% of {nb}, difference {100 * d:+.1f} +-{100 * h:.1f} points")
    for side, name in ((True, "as F"), (False, "as S")):
        na, ma, va = stats([g for g in a if g["aFirst"] == side])
        nb, mb, vb = stats([g for g in b if g["aFirst"] == side])
        h = 1.96 * math.sqrt(va / na + vb / nb)
        print(f"  {name}: on {100 * ma:.1f}%, off {100 * mb:.1f}%, difference {100 * (ma - mb):+.1f} +-{100 * h:.1f}")


main()
