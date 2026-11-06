"""Speed and depth by own turn for server-like cold games, a warmed-up variant and a hot control."""
import collections
import csv
import statistics
import sys

rows = []
with open(sys.argv[1], encoding="utf-8") as f:
    for r in csv.reader(f):
        rows.append(dict(label=r[0], size=r[1], game=int(r[2]), side=int(r[3]), own=int(r[4]), ply=int(r[5]), inside=float(r[6]),
                         outer=float(r[7]), to=r[8] == "true", nodes=int(r[9]), depth=int(r[10]), ctor=int(r[11])))


def band(t):
    for hi, name in ((5, "1-5"), (10, "6-10"), (20, "11-20"), (40, "21-40"), (60, "41-60")):
        if t <= hi:
            return name
    return "61+"


order = ["1-5", "6-10", "11-20", "21-40", "41-60", "61+"]
print(f"{'size':<7}{'mode':<7}{'own turns':<10}{'n':>5}{'ms p50':>8}{'ms max':>8}{'nodes p50':>11}{'nodes p90':>11}{'knps':>7}{'depth p50':>10}{'>60ms':>7}{'t/o':>5}")
for size in ("15x11", "8x12", "15x22"):
    for label in ("cold", "w2000", "hot"):
        # The hot control's first games warm it up; use only its later games.
        v0 = [r for r in rows if r["size"] == size and r["label"] == label and (r["game"] > 0 if label != "hot" else r["game"] >= 8)]
        for b in order:
            v = [r for r in v0 if band(r["own"]) == b and r["nodes"] >= 0]
            if len(v) < 4:
                continue
            ins = sorted(r["inside"] for r in v)
            nd = sorted(r["nodes"] for r in v)
            n = len(v)
            print(f"{size:<7}{label:<7}{b:<10}{n:>5}{ins[n // 2]:>8.1f}{ins[-1]:>8.1f}{nd[n // 2]:>11}{nd[9 * n // 10]:>11}"
                  f"{statistics.median(r['nodes'] / max(r['inside'], 0.01) for r in v):>7.0f}{statistics.median(r['depth'] for r in v):>10.1f}"
                  f"{sum(r['inside'] > 60 for r in v):>7}{sum(r['to'] for r in v):>5}")
        print()
