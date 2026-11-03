"""Turns server matches.jsonl into the opening lines the book builder reads.

Usage: python -I make-book-lines.py matches.jsonl > suites/book-lines.txt
One line per game with at least one strong bot:
  ROWSxCOLS STRONG_F STRONG_S WINNER "F name"|"S name" columns   (base-36 digits, up to the first blast or skipped turn)
"""
import json
import re
import sys

MAX_PLIES = 40
TOKEN = re.compile(r"([FS])([0-9a-z])(\*?)")


def strong(name):
    n = name.lower()
    return "savi" in n or n in ("testing_v2", "lalalalabot", "specialmovedestroyer")


def main():
    print("# rows x cols, F strong, S strong, winner, names, clean prefix (no blast, no skipped turn)")
    seen = set()
    for raw in open(sys.argv[1], encoding="utf-8"):
        if not raw.strip():
            continue
        g = json.loads(raw)
        if g["id"] in seen:
            continue
        seen.add(g["id"])
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
        print(f"{b['rows']}x{b['cols']} {int(sf)} {int(ss)} {g['winner'] or 'D'} {names[0]}|{names[1]} {''.join(prefix)}")


main()
