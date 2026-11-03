"""Report on ServerSim CSV files: lost turns and slow calls per 1000 own turns, by own turn bucket, per label and bot.

Usage: python -I simreport.py FILE [FILE...] [--side A]
Columns: label,size,game,side,ownTurn,ply,inMs,outerMs,timedOut,spInMs,spOuterMs,spTimedOut,nodes,depth,gcCount,gcMs,compMs,ctorMs
"""
import collections
import sys

BUCKETS = [(0, 2), (3, 9), (10, 19), (20, 29), (30, 59), (60, 10_000)]


def name(lo, hi):
    return f"{lo}-{hi}" if hi < 10_000 else f"{lo}+"


def main():
    files = [a for a in sys.argv[1:] if not a.startswith("--")]
    only = sys.argv[sys.argv.index("--side") + 1] if "--side" in sys.argv else None
    if only:
        files = [f for f in files if f != only]
    data = collections.defaultdict(lambda: collections.defaultdict(list))
    ticks = collections.defaultdict(collections.Counter)
    for path in files:
        for line in open(path, encoding="utf-8"):
            p = line.rstrip("\n").split(",")
            if p[0] == "tick":
                ticks[p[1]][int(p[2])] += int(p[3])
                continue
            if len(p) < 18:
                continue
            if only and p[3] != only:
                continue
            t = int(p[4])
            for lo, hi in BUCKETS:
                if lo <= t <= hi:
                    data[(p[0], p[1], p[3])][(lo, hi)].append(
                        (float(p[6]), float(p[7]), int(p[8]), float(p[9]), float(p[10]), int(p[11]), int(p[12]),
                         int(p[13])))
                    break
    print("label,size,bot | own turn | calls | >90ms | >100ms | engine timeouts | per 1000: >100, timeout | in-bot mean, p99, max | "
          "outer max | nodes mean | depth mean | 2nd call max, timeouts")
    for key in sorted(data):
        total = collections.Counter()
        for b in BUCKETS:
            rows = data[key].get(b)
            if not rows:
                continue
            n = len(rows)
            o90 = sum(r[1] > 90 for r in rows)
            o100 = sum(r[1] > 100 for r in rows)
            to = sum(r[2] for r in rows)
            ins = sorted(r[0] for r in rows)
            nodes = [r[6] for r in rows if r[6] >= 0]
            depths = [r[7] for r in rows if r[7] >= 0]
            sp = max(r[4] for r in rows)
            spto = sum(r[5] for r in rows)
            total["n"] += n
            total["o90"] += o90
            total["o100"] += o100
            total["to"] += to
            print(f"{','.join(key):<28}{name(*b):>8}{n:>7}{o90:>6}{o100:>6}{to:>5}{1000 * o100 / n:>8.1f}{1000 * to / n:>7.1f}"
                  f"{sum(ins) / n:>8.1f}{ins[int(0.99 * (n - 1))]:>7.1f}{ins[-1]:>8.1f}{max(r[1] for r in rows):>8.1f}"
                  f"{(sum(nodes) / len(nodes) if nodes else 0):>10.0f}{(sum(depths) / len(depths) if depths else 0):>6.1f}"
                  f"{sp:>7.1f}{spto:>4}")
        n = total["n"]
        if n:
            print(f"{','.join(key):<28}{'all':>8}{n:>7}{total['o90']:>6}{total['o100']:>6}{total['to']:>5}"
                  f"{1000 * total['o100'] / n:>8.1f}{1000 * total['to'] / n:>7.1f}")
    for label, c in sorted(ticks.items()):
        n = sum(c.values())
        print(f"monitor {label}: ticks {n}, late >=20 ms {100 * sum(v for k, v in c.items() if k >= 20) / max(n, 1):.1f}%, "
              + " ".join(f"{k}:{v}" for k, v in sorted(c.items()) if k >= 10))


main()
