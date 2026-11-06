"""Games new in an export: our bots' results, lost turns (ours and opponents'), by name, size and hour."""
import collections, json, re, sys

new_path, old_path = sys.argv[1], sys.argv[2]
TEAM = "No beaches?"
TOK = re.compile(r"([FS])(\[\d+\]|[0-9a-z])(\*?)")


def toks(m):
    return [(s, int(c[1:-1]) if c[0] == "[" else int(c, 36), bool(st)) for s, c, st in TOK.findall(m)]


def lost(ts):
    out, own, prev = [], {"F": 0, "S": 0}, "S"
    for side, _, _ in ts:
        if side == prev:
            o = "S" if side == "F" else "F"
            out.append(o); own[o] += 1
        own[side] += 1; prev = side
    return out, own


old = {json.loads(l)["id"] for l in open(old_path, encoding="utf-8") if l.strip()}
games = [json.loads(l) for l in open(new_path, encoding="utf-8") if l.strip()]
games = [g for g in games if g["id"] not in old]
print(len(games), "new games;", "fetched", min(g["fetchedAt"] for g in games), "..", max(g["fetchedAt"] for g in games))
stats = collections.defaultdict(lambda: collections.Counter())
hours = collections.defaultdict(lambda: collections.Counter())
for g in games:
    p = g["players"]
    us = [i for i in (0, 1) if p[i]["team"] == TEAM]
    if not us or len(us) == 2:
        continue
    i = us[0]; me = "FS"[i]; size = f"{g['board']['rows']}x{g['board']['cols']}"
    ts = toks(g["moves"]); ls, own = lost(ts)
    key = (p[i]["name"], size)
    c = stats[key]
    c["games"] += 1; c["W" if g["winner"] == me else "L"] += 1
    c["ownT"] += own[me]; c["oppT"] += own["S" if me == "F" else "F"]
    c["ownLost"] += ls.count(me); c["oppLost"] += len(ls) - ls.count(me)
    c["gamesOwnLost"] += ls.count(me) > 0
    h = g["fetchedAt"][:13]
    hours[(p[i]["name"], h)]["games"] += 1; hours[(p[i]["name"], h)]["W"] += g["winner"] == me
    hours[(p[i]["name"], h)]["ownLost"] += ls.count(me); hours[(p[i]["name"], h)]["ownT"] += own[me]
print("\n| bot | size | games | W | L | score | own lost /1000 | games with own lost | opp lost /1000 |")
print("|---|---|---|---|---|---|---|---|---|")
for (n, s), c in sorted(stats.items()):
    print(f"| {n} | {s} | {c['games']} | {c['W']} | {c['L']} | {c['W']/c['games']:.0%} | {1000*c['ownLost']/max(1,c['ownT']):.1f} | {c['gamesOwnLost']} | {1000*c['oppLost']/max(1,c['oppT']):.1f} |")
print("\nby fetch hour (UTC):")
for (n, h), c in sorted(hours.items(), key=lambda x: (x[0][1], x[0][0])):
    print(f"  {h} {n:28s} games {c['games']:4d} W {c['W']:4d} own lost/1000 {1000*c['ownLost']/max(1,c['ownT']):5.1f}")
