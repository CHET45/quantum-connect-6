"""Turns server matches.jsonl into the opening lines the book builder reads.

Usage: python -I make-book-lines.py matches.jsonl > suites/book-lines.txt
One line per game with at least one strong bot:
  ROWSxCOLS STRONG_F STRONG_S WINNER F_name|S_name columns P_F
columns: base-36 digits up to the first blast or skipped turn. P_F: chance that F wins expected from the two bots' ratings
alone (per board size, a regularised logistic fit over all games), so a line's record can be read net of who played it.
"""
import json
import math
import re
import sys
from collections import defaultdict

MAX_PLIES = 40
PRIOR_SIGMA = 2.0
TOKEN = re.compile(r"([FS])([0-9a-z])(\*?)")


def strong(name):
    n = name.lower()
    return "savi" in n or n in ("testing_v2", "lalalalabot", "specialmovedestroyer")


def fit(games):
    """Logit ratings by name with a Gaussian prior; games are (F name, S name, 1 if F won)."""
    names = {n for g in games for n in g[:2]}
    r = {n: 0.0 for n in names}
    first = 0.0
    for _ in range(400):
        grad = defaultdict(float)
        gfirst = 0.0
        for f, s, y in games:
            p = 1 / (1 + math.exp(-(r[f] - r[s] + first)))
            grad[f] += y - p
            grad[s] -= y - p
            gfirst += y - p
        for n in names:
            r[n] += 0.05 * (grad[n] - r[n] / PRIOR_SIGMA ** 2)
        first += 0.002 * gfirst
    return r, first


def main():
    print("# rows x cols, F strong, S strong, winner, names, clean prefix (no blast, no skipped turn), expected P(F wins)")
    rows = []
    seen = set()
    for raw in open(sys.argv[1], encoding="utf-8"):
        if raw.strip():
            g = json.loads(raw)
            if g["id"] not in seen:
                seen.add(g["id"])
                rows.append(g)
    by_size = defaultdict(list)
    for g in rows:
        if g["winner"] in ("F", "S"):
            b = g["board"]
            by_size[(b["rows"], b["cols"])].append((g["players"][0]["name"], g["players"][1]["name"], int(g["winner"] == "F")))
    fits = {k: fit(v) for k, v in by_size.items()}
    for g in rows:
        names = [p["name"].replace("|", "/").replace(" ", "_") for p in g["players"]]
        sf, ss = strong(g["players"][0]["name"]), strong(g["players"][1]["name"])
        if not (sf or ss):
            continue
        prefix = []
        for i, (side, col, blast) in enumerate(TOKEN.findall(g["moves"])):
            if i >= MAX_PLIES or blast or side != ("F" if i % 2 == 0 else "S"):
                break
            prefix.append(col)
        if not prefix:
            continue
        b = g["board"]
        r, first = fits.get((b["rows"], b["cols"]), ({}, 0.0))
        d = r.get(g["players"][0]["name"], 0.0) - r.get(g["players"][1]["name"], 0.0) + first
        p = 1 / (1 + math.exp(-d))
        print(f"{b['rows']}x{b['cols']} {int(sf)} {int(ss)} {g['winner'] or 'D'} {names[0]}|{names[1]} {''.join(prefix)} {p:.3f}")
    for k, (r, first) in sorted(fits.items()):
        top = sorted(r.items(), key=lambda x: -x[1])[:8]
        print(f"size {k}: first-move logit {first:+.2f}; " + ", ".join(f"{n} {v:+.2f}" for n, v in top), file=sys.stderr)


main()
