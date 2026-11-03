"""Reads BookMatch or BookFast logs: scores with 95% CI, unique games, per-unique-line statistics, lost turns.

Usage: python -I tools/book-report.py [--lines N] log.jsonl ...

Games between nearly deterministic bots repeat, so two figures are given: over all games (each repeat counts, the
CI is too narrow if the repeats are not independent) and over unique games (each distinct move list counts once).
"""
import json
import math
import re
import sys
from collections import Counter, defaultdict

TOKEN = re.compile(r"([FS])([0-9a-z])(\*?)")


def ci(scores):
    n = len(scores)
    if n == 0:
        return 0.0, 0.0
    m = sum(scores) / n
    if n < 2:
        return m, 1.0
    var = sum((x - m) ** 2 for x in scores) / (n - 1)
    return m, 1.96 * math.sqrt(var / n)


def lost_turns(games):
    """Lost turns of A by A's own turn index (a turn is lost when the other side moves twice in a row)."""
    lost = Counter()
    turns = Counter()
    for g in games:
        side = "F" if g["aFirst"] else "S"
        own = 0
        moves = TOKEN.findall(g["moves"])
        expected = "F"
        for mv_side, _, _ in moves:
            while mv_side != expected:
                if expected == side:
                    lost[min(own // 10, 6)] += 1
                    turns[min(own // 10, 6)] += 1
                    own += 1
                expected = "S" if expected == "F" else "F"
            if expected == side:
                turns[min(own // 10, 6)] += 1
                own += 1
            expected = "S" if expected == "F" else "F"
    return " ".join(
        f"{k * 10}-{k * 10 + 9}:{lost[k]}/{turns[k]}" for k in sorted(turns))


def report(path, top):
    games = [json.loads(x) for x in open(path, encoding="utf-8") if x.strip()]
    if not games:
        print(path, "empty")
        return
    size = f"{games[0]['rows']}x{games[0]['cols']}"
    wins = sum(g["scoreA"] == 1 for g in games)
    draws = sum(g["scoreA"] == 0.5 for g in games)
    losses = len(games) - wins - draws
    m, h = ci([g["scoreA"] for g in games])
    # colour-swapped pairs as the unit, when both games of a pair are there
    pairs = defaultdict(list)
    for g in games:
        pairs[g["pair"]].append(g["scoreA"])
    pm, ph = ci([sum(v) / len(v) for v in pairs.values()])
    print(f"## {path}: {size}, {len(games)} games, A +{wins} ={draws} -{losses}, "
          f"score {100 * m:.1f}% +-{100 * h:.1f} (per game), +-{100 * ph:.1f} (per pair)")
    timeouts_a = sum(g["timeoutsA"] for g in games)
    timeouts_b = sum(g["timeoutsB"] for g in games)
    reasons = Counter(g["reason"] for g in games)
    print(f"reasons {dict(reasons)}; timeouts A {timeouts_a}, B {timeouts_b}; A lost turns by own turn: {lost_turns(games)}")
    for side, name in ((True, "A as F"), (False, "A as S")):
        sub = [g for g in games if g["aFirst"] == side]
        by_line = defaultdict(list)
        for g in sub:
            by_line[g["moves"]].append(g["scoreA"])
        um, uh = ci([sum(v) / len(v) for v in by_line.values()])
        sm, sh = ci([g["scoreA"] for g in sub])
        print(f"{name}: {len(sub)} games, {len(by_line)} unique; score {100 * sm:.1f}% +-{100 * sh:.1f} over games, "
              f"{100 * um:.1f}% +-{100 * uh:.1f} over unique games")
        for depth in (10, 20, 30, 40):
            pre = Counter()
            res = defaultdict(float)
            for g in sub:
                key = g["moves"][: 2 * depth]
                pre[key] += 1
                res[key] += g["scoreA"]
            print(f"  first {depth} plies: {len(pre)} unique, most frequent {pre.most_common(1)[0][1]} of {len(sub)}, "
                  f"score over them {100 * res[pre.most_common(1)[0][0]] / pre.most_common(1)[0][1]:.0f}%")
        if top:
            pre = Counter()
            res = defaultdict(float)
            for g in sub:
                key = "".join(t[1] + t[2] for t in TOKEN.findall(g["moves"])[:24])
                pre[key] += 1
                res[key] += g["scoreA"]
            for key, n in pre.most_common(top):
                print(f"    {key}: {n} games, A {100 * res[key] / n:.0f}%")
    print()


def main():
    args = sys.argv[1:]
    top = 0
    if args and args[0] == "--lines":
        top = int(args[1])
        args = args[2:]
    for path in args:
        report(path, top)


main()
