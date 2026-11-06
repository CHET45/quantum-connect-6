"""Games new in this export: results of 3.1 and the newest Savi, lost turns, when they happen, and our losses in detail."""
import collections
import json
import statistics
import sys

new_path, old_path = sys.argv[1], sys.argv[2]
TEAM = "No beaches?"


def parse(moves):
    out, i = [], 0
    while i < len(moves):
        side, col = moves[i], int(moves[i + 1], 36)
        i += 2
        star = i < len(moves) and moves[i] == "*"
        i += star
        out.append((side, col, star))
    return out


def lost_turns(toks):
    """(side, own turn index) of each turn that left no token."""
    out = []
    own = {"F": 0, "S": 0}
    prev = "S"
    for side, _, _ in toks:
        if side == prev:
            o = "S" if side == "F" else "F"
            out.append((o, own[o]))
            own[o] += 1
        own[side] += 1
        prev = side
    return out, own


old = {json.loads(l)["id"] for l in open(old_path, encoding="utf-8") if l.strip()}
all_games = [json.loads(l) for l in open(new_path, encoding="utf-8") if l.strip()]
games = [g for g in all_games if g["id"] not in old]
size_of = lambda g: f"{g['board']['rows']}x{g['board']['cols']}"
print("new games", len(games), collections.Counter(size_of(g) for g in games), "fetched", min(g["fetchedAt"] for g in games)[:16], "to", max(g["fetchedAt"] for g in games)[:16])
print("board sources", collections.Counter(g["board"].get("source", "?") for g in games))

tab = collections.defaultdict(lambda: [0, 0])
h2h = collections.defaultdict(lambda: [0, 0])
for g in games:
    w = g["players"]["FS".index(g["winner"])]["name"]
    l = g["players"][1 - "FS".index(g["winner"])]["name"]
    tab[w][0] += 1
    tab[l][1] += 1
    h2h[(w, l)][0] += 1
print("\nnew games, W-L per bot:")
for n, v in sorted(tab.items(), key=lambda x: -x[1][0] / max(1, sum(x[1]))):
    print(f"  {n:<26}{v[0]:>4}-{v[1]:<4} {100 * v[0] / sum(v):5.1f}%")


def bot_table(name):
    print(f"\n{name}: by opponent, W-L, own lost turns, plies median")
    per = collections.defaultdict(lambda: [0, 0, 0, []])
    turns = collections.Counter()
    totals = [0, 0]
    gaps = []
    for g in games:
        for i, p in enumerate(g["players"]):
            if p["name"] != name:
                continue
            side = "FS"[i]
            toks = parse(g["moves"])
            lt, own = lost_turns(toks)
            e = per[g["players"][1 - i]["name"]]
            e[0 if g["winner"] == side else 1] += 1
            mine = [t for s, t in lt if s == side]
            e[2] += len(mine)
            e[3].append(len(toks))
            totals[0] += len(mine)
            totals[1] += own[side]
            for t in mine:
                b = "0" if t == 0 else "1-2" if t <= 2 else "3-9" if t <= 9 else "10-29" if t <= 29 else "30-59" if t <= 59 else "60+"
                turns[b] += 1
    for k, v in sorted(per.items(), key=lambda x: -x[1][1]):
        print(f"  {k:<26}{v[0]:>3}-{v[1]:<3} lost {v[2]:>3}  plies {statistics.median(v[3]):>5.0f}")
    print(f"  lost turns per 1000 own turns: {1000 * totals[0] / max(1, totals[1]):.1f} ({totals[0]}/{totals[1]}); by own turn index: {dict(turns)}")


names = sorted({p["name"] for g in games for p in g["players"] if p["team"] == TEAM or p["name"].startswith("Savi")})
print("\nours and Savi-named bots in new games:", names)
for n in names:
    bot_table(n)

print("\nlost turns per 1000 own turns, all bots in new games")
c = collections.Counter()
o = collections.Counter()
for g in games:
    lt, own = lost_turns(parse(g["moves"]))
    for i, p in enumerate(g["players"]):
        c[p["name"]] += sum(1 for s, _ in lt if s == "FS"[i])
        o[p["name"]] += own["FS"[i]]
for n in sorted(o, key=lambda n: -c[n] / max(1, o[n])):
    print(f"  {n:<26}{1000 * c[n] / max(1, o[n]):7.1f}  ({c[n]}/{o[n]})")

print("\nour losses in new games")
for g in sorted(games, key=lambda g: g["fetchedAt"]):
    i = next((k for k, p in enumerate(g["players"]) if p["team"] == TEAM), None)
    if i is None or g["winner"] == "FS"[i]:
        continue
    toks = parse(g["moves"])
    lt, _ = lost_turns(toks)
    mine = [t for s, t in lt if s == "FS"[i]]
    theirs = [t for s, t in lt if s != "FS"[i]]
    bl = collections.Counter(s for s, _, st in toks if st)
    txt = lambda ts: "".join(s + format(c, "x") + ("*" if st else "") for s, c, st in ts)
    print(f"  {size_of(g)} {g['players'][i]['name']} as {'FS'[i]} vs {g['players'][1 - i]['name']}: plies {len(toks)}, our lost turns {mine}, theirs {theirs}, blasts F{bl['F']} S{bl['S']}, last {txt(toks[-1:])}, id {g['id'][:8]}")
    print(f"     {txt(toks[:26])}")
print("\nour wins over Savi-named bots in new games")
for g in games:
    i = next((k for k, p in enumerate(g["players"]) if p["team"] == TEAM), None)
    if i is None or g["winner"] != "FS"[i] or not g["players"][1 - i]["name"].startswith("Savi"):
        continue
    toks = parse(g["moves"])
    lt, _ = lost_turns(toks)
    txt = lambda ts: "".join(s + format(c, "x") + ("*" if st else "") for s, c, st in ts)
    print(f"  {size_of(g)} {g['players'][i]['name']} as {'FS'[i]} vs {g['players'][1 - i]['name']}: plies {len(toks)}, their lost turns {[t for s, t in lt if s != 'FS'[i]]}, ours {[t for s, t in lt if s == 'FS'[i]]}; {txt(toks[:26])}")
