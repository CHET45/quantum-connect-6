"""How the context features behave across board sizes: python -m qctrain.featcheck DIR [DIR ...]

For each data directory (one size per directory): share of records per stage, per cooldown context, and how often the
capped threat counts of the T1/T2/T3 tables sit at their cap, plus the share of windows of each row band. A feature
that sits at its cap on large boards tells the evaluation nothing there.
"""
import glob
import os
import sys

import numpy as np

from . import data


def digits(x, n, base=3):
    out = []
    for _ in range(n):
        out.append(x % base)
        x = x // base
    return out[::-1]


def main():
    print(f"{'dir':>12} {'records':>8} {'plies':>6} {'stage 0/1/2/3 %':>18} {'cd 0/1/2/3 %':>16} "
          f"imm>=2 one>=2 nat>=2 unn>=2 valley>=2 (%)")
    for d in sys.argv[1:]:
        parts = []
        shards = glob.glob(os.path.join(d, "shard-*.qcp"))
        for path in shards:
            if os.path.getsize(path) > data.HEADER:
                recs, meta = data.read_shard(path)
                parts.append(recs)
        if not parts:
            continue
        r = np.concatenate(parts)
        stage = np.bincount(r["stage"], minlength=4) / len(r) * 100
        cd = np.bincount(r["cd_ctx"], minlength=4) / len(r) * 100
        t1 = digits(r["t1"].astype(np.int64), 4)  # imm me, imm opp, one me, one opp
        t2 = digits(r["t2"].astype(np.int64), 6)  # stk me, stk opp, nat me, unn me, nat opp, unn opp
        valley = r["t3"].astype(np.int64) % 3
        imm = ((t1[0] == 2) | (t1[1] == 2)).mean() * 100
        one = ((t1[2] == 2) | (t1[3] == 2)).mean() * 100
        nat = ((t2[2] == 2) | (t2[4] == 2)).mean() * 100
        unn = ((t2[3] == 2) | (t2[5] == 2)).mean() * 100
        val = (valley == 2).mean() * 100
        plies = np.where(r["plies_to_end"] != 0xFFFF, r["turn"].astype(np.int64) + r["plies_to_end"], 0)
        name = os.path.basename(os.path.normpath(d))
        print(f"{name:>12} {len(r):>8} {plies.mean():>6.0f} {' '.join(f'{x:4.0f}' for x in stage):>18} "
              f"{' '.join(f'{x:3.0f}' for x in cd):>16} {imm:6.1f} {one:6.1f} {nat:6.1f} {unn:6.1f} {val:8.1f}")


if __name__ == "__main__":
    main()
