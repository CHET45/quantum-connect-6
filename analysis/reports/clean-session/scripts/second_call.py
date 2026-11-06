"""Timed-out lines per bot split into first-call (paired with an invalid column line) and second-call timeouts; whole-turn times of the newest bots."""
import collections
import datetime as dt
import json
import re
import statistics
import sys

TS = re.compile(r"^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d+)Z")
games = [json.loads(l) for l in open(sys.argv[1] + "/matches.jsonl", encoding="utf-8") if l.strip()]

tab = collections.defaultdict(lambda: [0, 0, 0])
spans = collections.defaultdict(list)
for g in games:
    for i, side in enumerate(("FIRST_PLAYER", "SECOND_PLAYER")):
        name = g["players"][i]["name"]
        to, inv = [], []
        for e in g["errors"]:
            m = TS.match(e)
            if not m or not e.endswith(side):
                continue
            t = dt.datetime.fromisoformat(m.group(1)).timestamp() * 1000
            if "timed out" in e:
                to.append(t)
            elif "Invalid column" in e:
                inv.append(t)
        tab[name][0] += len(to)
        tab[name][1] += len(inv)
        tab[name][2] += 1
print("bot: timed-out lines, invalid-column lines, difference (second-call timeouts), games")
for n, v in sorted(tab.items(), key=lambda x: -(x[1][0] - x[1][1])):
    if v[0]:
        print(f"  {n:<28}{v[0]:>6}{v[1]:>6}{v[0] - v[1]:>6}{v[2]:>6}")

# Timed-out lines in one game of the top bots, to see the shape of a second-call timeout.
for name in ('Savi 7 - "The Prince', 'Savi9 - "The King"', "Savi beater 3.1", "Savi5 beater 2.1"):
    shown = 0
    for g in games:
        names = [p["name"] for p in g["players"]]
        if name not in names:
            continue
        side = ("FIRST_PLAYER", "SECOND_PLAYER")[names.index(name)]
        lines = [e for e in g["errors"] if e.endswith(side) and TS.match(e)]
        to = sum("timed out" in e for e in lines)
        inv = sum("Invalid" in e for e in lines)
        if to > inv and shown < 2:
            shown += 1
            print(f"\n{name} vs {names[1 - names.index(name)]}, game {g['id'][:8]}: {to} timed out, {inv} invalid")
            for e in lines[:8]:
                print("   ", e[11:23], e.split(" : ")[-1][:70])
