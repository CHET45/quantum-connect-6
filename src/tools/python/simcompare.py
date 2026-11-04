"""One line per ServerSim CSV: lost turns per 1000 own turns by own-turn bucket, nodes, constructor and second-call time.

Usage: python -I simcompare.py [--bot A] FILE [FILE...]
Lost turns are the engine's timeouts. The interval is a 95% Wilson interval for own turns below 60.
"""
import math
import sys

BUCKETS = [(0, 2), (3, 9), (10, 19), (20, 29), (30, 59), (60, 10_000)]


def wilson(k, n):
    if n == 0:
        return 0, 0
    z = 1.96
    p = k / n
    d = 1 + z * z / n
    c = (p + z * z / (2 * n)) / d
    h = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / d
    return max(0, c - h), c + h


def main():
    args = sys.argv[1:]
    bot = None
    if args and args[0] == "--bot":
        bot = args[1]
        args = args[2:]
    print(f"{'file':<14}" + "".join(f"{f'{lo}-{hi}' if hi < 10000 else f'{lo}+':>11}" for lo, hi in BUCKETS)
          + f"{'<60 /1000':>10}{'CI':>13}{'nodes<30':>9}{'nodes30+':>9}{'in p99':>7}{'2nd to':>7}{'ctor':>6}{'A%':>6}")
    for path in args:
        lost = [[0, 0] for _ in BUCKETS]
        nodes_early = []
        nodes_late = []
        ins = []
        spto = 0
        ctor = []
        res = {"A": 0, "B": 0, "-": 0}
        for line in open(path, encoding="utf-8"):
            p = line.rstrip("\n").split(",")
            if p[0] == "game":
                res[p[5]] += 1
                continue
            if p[0] == "tick" or len(p) < 18:
                continue
            if bot and p[3] != bot:
                continue
            t = int(p[4])
            for i, (lo, hi) in enumerate(BUCKETS):
                if lo <= t <= hi:
                    lost[i][0] += int(p[8])
                    lost[i][1] += 1
            n = int(p[12])
            if n >= 0:
                (nodes_early if t < 30 else nodes_late).append(n)
            ins.append(float(p[6]))
            spto += int(p[11])
            if t == 0:
                ctor.append(int(p[17]))
        name = path.replace("\\", "/").split("/")[-1].replace(".csv", "")
        k = sum(x[0] for x in lost[:-1])
        n = sum(x[1] for x in lost[:-1])
        lo, hi = wilson(k, n)
        ins.sort()
        g = sum(res.values())
        print(f"{name:<14}" + "".join(f"{(1000 * a / b if b else 0):>6.1f}/{b:<4}" for a, b in lost)
              + f"{(1000 * k / n if n else 0):>10.1f}{f'[{1000 * lo:.1f},{1000 * hi:.1f}]':>13}"
              + f"{(sum(nodes_early) / max(1, len(nodes_early))):>9.0f}{(sum(nodes_late) / max(1, len(nodes_late))):>9.0f}"
              + f"{(ins[int(0.99 * (len(ins) - 1))] if ins else 0):>7.1f}{spto:>7}"
              + f"{(sorted(ctor)[len(ctor) // 2] if ctor else 0):>6}"
              + f"{(100 * (res['A'] + res['-'] / 2) / g if g else 0):>6.1f}")


main()
