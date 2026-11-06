"""Lost turns per 1000 by own-turn band with exposure, per bot; shows whether lost turns are a warm-up effect."""
import collections
import json
import sys


def parse(moves):
    out, i = [], 0
    while i < len(moves):
        side, col = moves[i], int(moves[i + 1], 36)
        i += 2
        star = i < len(moves) and moves[i] == "*"
        i += star
        out.append((side, col, star))
    return out


BANDS = ((3, "0-2"), (10, "3-9"), (20, "10-19"), (30, "20-29"), (60, "30-59"), (10 ** 9, "60+"))


def band(t):
    for hi, name in BANDS:
        if t < hi:
            return name


games = [json.loads(l) for l in open(sys.argv[1], encoding="utf-8") if l.strip()]
lost = collections.defaultdict(collections.Counter)
exp = collections.defaultdict(collections.Counter)
for g in games:
    own = {"F": 0, "S": 0}
    prev = "S"
    name = {"F": g["players"][0]["name"], "S": g["players"][1]["name"]}
    for side, _, _ in parse(g["moves"]):
        if side == prev:
            o = "S" if side == "F" else "F"
            lost[name[o]][band(own[o])] += 1
            exp[name[o]][band(own[o])] += 1
            own[o] += 1
        exp[name[side]][band(own[side])] += 1
        own[side] += 1
        prev = side
print(f"{'bot':<24}" + "".join(f"{b:>16}" for _, b in BANDS))
for n in ("Savi beater 3.1", "Savi beater 2.2", "Savi5 beater 2.1", 'Savi9 - "The King"', 'Savi8 - "The King"', 'Savi 7 - "The Prince', 'Savi7 - "The Prince"', "Savi5", 'Savi6 - "Beat this"'):
    print(f"{n[:22]:<24}" + "".join(f"{1000 * lost[n][b] / max(1, exp[n][b]):>7.1f} {lost[n][b]:>3}/{exp[n][b]:<4}" for _, b in BANDS))
