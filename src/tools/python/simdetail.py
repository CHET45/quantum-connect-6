"""Detail on ServerSim CSV files: JIT time and speed by own turn, and whether a late call predicts a lost turn soon after.

Usage: python -I simdetail.py FILE [FILE...]
"""
import collections
import sys


def main():
    games = collections.defaultdict(list)
    for path in sys.argv[1:]:
        for line in open(path, encoding="utf-8"):
            p = line.rstrip("\n").split(",")
            if p[0] == "tick" or len(p) < 18:
                continue
            games[(path, p[0], p[1], int(p[2]), p[3])].append(
                dict(turn=int(p[4]), inms=float(p[6]), outer=float(p[7]), to=int(p[8]), sp=float(p[9]), spo=float(p[10]),
                     spto=int(p[11]), nodes=int(p[12]), depth=int(p[13]), gc=int(p[14]), gcms=int(p[15]), comp=int(p[16]),
                     ctor=int(p[17])))
    print("own turn: median cumulative JIT ms since game start, mean nodes, mean depth (all games pooled)")
    byturn = collections.defaultdict(list)
    for rows in games.values():
        for r in rows:
            byturn[min(r["turn"] // 5 * 5, 120)].append(r)
    for t in sorted(byturn):
        rs = byturn[t]
        comp = sorted(r["comp"] for r in rs)
        nodes = [r["nodes"] for r in rs if r["nodes"] >= 0]
        depth = [r["depth"] for r in rs if r["depth"] >= 0]
        print(f"  {t:>4}: {comp[len(comp) // 2]:>6} ms  {sum(nodes) / max(1, len(nodes)):>8.0f} nodes  "
              f"{sum(depth) / max(1, len(depth)):>5.2f} depth  n={len(rs)}")
    ctor = [rows[0]["ctor"] for rows in games.values()]
    print(f"constructor ms: median {sorted(ctor)[len(ctor) // 2]}, max {max(ctor)}, n={len(ctor)}")

    # a first call that ran late, and timeouts within the next K own turns against the base rate
    print("trouble signal: overrun of the in-bot time above the usual stop (stop = 95th percentile of the bucket is not "
          "known here, so the raw time is used)")
    for thr in (85, 88, 90, 92, 95):
        for k in (1, 3, 5, 10):
            sig = hit = base_n = base_hit = 0
            for rows in games.values():
                for i, r in enumerate(rows):
                    if r["turn"] >= 60:
                        continue
                    nxt = rows[i + 1: i + 1 + k]
                    any_to = any(x["to"] for x in nxt)
                    base_n += 1
                    base_hit += any_to
                    if r["inms"] >= thr or r["to"]:
                        sig += 1
                        hit += any_to
            if sig:
                print(f"  in-bot >= {thr} ms or timed out: next {k:>2} own turns have a timeout in {hit}/{sig} = "
                      f"{100 * hit / sig:.1f}%; base {100 * base_hit / max(1, base_n):.1f}%")


main()
