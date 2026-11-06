"""Call time, nodes and depth by own turn band for the cold-start probe; game 0 of each JVM is dropped."""
import collections
import csv
import statistics
import sys

rows = []
with open(sys.argv[1], encoding="utf-8") as f:
    for r in csv.reader(f):
        rows.append(dict(label=r[0], size=r[1], game=int(r[2]), side=int(r[3]), own=int(r[4]), ply=int(r[5]), inside=float(r[6]),
                         outer=float(r[7]), to=r[8] == "true", nodes=int(r[9]), depth=int(r[10]), ctor=int(r[11])))
band = lambda t: "1" if t == 1 else "2" if t == 2 else "3-5" if t <= 5 else "6-10" if t <= 10 else "11-20" if t <= 20 else "21+"
order = ["1", "2", "3-5", "6-10", "11-20", "21+"]
keep = [r for r in rows if r["game"] > 0]
print("calls", len(rows), "kept", len(keep), "timeouts", sum(r["to"] for r in keep))
print(f"{'size':<7}{'warm':<6}{'turns':<7}{'n':>5}{'ms p50':>8}{'ms p90':>8}{'ms max':>8}{'outer max':>10}{'>60ms':>7}{'>100':>6}{'nodes p50':>11}{'knps p50':>10}{'depth':>7}")
for size in ("8x12", "15x11", "15x22"):
    for label in ("w0", "w300"):
        for b in order:
            v = [r for r in keep if r["size"] == size and r["label"] == label and band(r["own"]) == b]
            if not v:
                continue
            ins = sorted(r["inside"] for r in v)
            n = len(ins)
            good = [r for r in v if r["nodes"] > 0]
            nodes = statistics.median(r["nodes"] for r in good) if good else 0
            knps = statistics.median(r["nodes"] / max(r["inside"], 0.01) for r in good) if good else 0
            dep = statistics.median(r["depth"] for r in good) if good else 0
            print(f"{size:<7}{label:<6}{b:<7}{n:>5}{ins[n // 2]:>8.1f}{ins[9 * n // 10]:>8.1f}{ins[-1]:>8.1f}{max(r['outer'] for r in v):>10.1f}"
                  f"{sum(r['inside'] > 60 for r in v):>7}{sum(r['to'] for r in v):>6}{nodes:>11.0f}{knps:>10.0f}{dep:>7.1f}")
        print()
print("slowest calls (size, warm, game, own turn, inside ms, outer ms, nodes, depth)")
for r in sorted(keep, key=lambda r: -r["inside"])[:15]:
    print(" ", r["size"], r["label"], r["game"], r["own"], r["inside"], r["outer"], r["nodes"], r["depth"])
print("\nconstructor time ms (median) by size and warm:", {(s, l): statistics.median(r["ctor"] / 1000 for r in keep if r["size"] == s and r["label"] == l and r["own"] == 1)
                                                       for s in ("8x12", "15x11", "15x22") for l in ("w0", "w300")})
