"""When turns get skipped, whether log lines explain them, and where memory lines sit inside a game."""
import collections
import json
import re
import sys

TEAM = "No beaches?"
TS = re.compile(r"^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d+)Z")


def parse(moves):
    out, i = [], 0
    while i < len(moves):
        side, col = moves[i], int(moves[i + 1], 36)
        i += 2
        star = i < len(moves) and moves[i] == "*"
        i += star
        out.append((side, col, star))
    return out


def skipped(tokens):
    """(side, own turn index of the lost turn) for every break in the alternation; own turns count lost ones."""
    out = []
    own = {"F": 0, "S": 0}
    prev = "S"
    for side, _, _ in tokens:
        if side == prev:
            other = "S" if side == "F" else "F"
            out.append((other, own[other]))
            own[other] += 1
        own[side] += 1
        prev = side
    return out


games = [json.loads(l) for l in open(sys.argv[1] + "/matches.jsonl", encoding="utf-8") if l.strip()]
size_of = lambda g: f"{g['board']['rows']}x{g['board']['cols']}"

# 1. Own turn index of lost turns, per bot.
turns = collections.defaultdict(collections.Counter)
for g in games:
    for side, t in skipped(parse(g["moves"])):
        p = g["players"][0 if side == "F" else 1]
        bucket = str(t) if t < 3 else "3-9" if t < 10 else "10-29" if t < 30 else "30-59" if t < 60 else "60+"
        turns[(size_of(g), p["name"])][bucket] += 1
print("lost turns by own turn index (0 = first turn of the game)")
for k, c in sorted(turns.items(), key=lambda x: (x[0][0], -sum(x[1].values()))):
    print(f"  {k[0]:<6}{k[1]:<26}{sum(c.values()):>5}  " + ", ".join(f"{b}:{c[b]}" for b in ("0", "1", "2", "3-9", "10-29", "30-59", "60+") if c[b]))

# 2. Are lost turns explained by log lines? A lost turn: timed out or full column; compare per side per game.
gap = collections.Counter()
ex = []
for g in games:
    log_lost = {"F": 0, "S": 0}
    for e in g["errors"]:
        if "timed out" in e or "Column is full" in e:
            log_lost["F" if e.endswith("FIRST_PLAYER") else "S"] += 1
    sk = {"F": 0, "S": 0}
    for side, _ in skipped(parse(g["moves"])):
        sk[side] += 1
    for side in "FS":
        d = sk[side] - log_lost[side]
        # A lost turn at the very end leaves no later token, so the log can exceed the alternation by one.
        k = "equal" if d == 0 else "log more" if d < 0 else "skips more"
        gap[(k, bool(g["log"]["oom"]))] += 1
        if d > 0 and len(ex) < 8:
            ex.append((g["id"][:8], size_of(g), side, g["players"][0 if side == "F" else 1]["name"], sk[side], log_lost[side], len(g["log"]["oom"])))
print("\nlost turns vs log lines, per side of a game: (relation, game has memory line): count")
for k, v in sorted(gap.items()):
    print(f"  {k}: {v}")
print("  unexplained samples (id, size, side, bot, skips, log lines, oom lines):", ex)

# 3. Position of memory lines relative to timestamped lines in the same game.
pos = collections.Counter()
per_game = collections.Counter()
spans = []
for g in games:
    evs = []
    for e in g["errors"]:
        m = TS.match(e)
        if not m:
            continue
        kind = "oom" if "Memory limit exceeded:" in e else "uncaught" if "Uncaught" in e else "to" if "timed out" in e else "other"
        evs.append((m.group(1), kind))
    evs.sort()
    ooms = [i for i, (_, k) in enumerate(evs) if k == "oom"]
    per_game[min(len(ooms), 5)] += 1
    if not ooms:
        continue
    tos = [i for i, (_, k) in enumerate(evs) if k == "to"]
    if tos:
        before = sum(1 for i in tos if i < ooms[0])
        after = sum(1 for i in tos if i > ooms[-1])
        pos["timeouts before and after" if before and after else "only before oom" if before else "only after oom"] += 1
    else:
        pos["no timeouts in game"] += 1
print("\nmemory lines per game (5 = 5+):", dict(sorted(per_game.items())))
print("games with memory lines, timeouts relative to them:", dict(pos))

# 4. Our games: lost turns and memory lines by version and size.
print("\nour games: version, size, games, games with memory line, games with lost turn of ours, losses, losses with a lost turn of ours")
tab = collections.defaultdict(lambda: [0, 0, 0, 0, 0])
for g in games:
    for i, p in enumerate(g["players"]):
        if p["team"] != TEAM:
            continue
        side = "FS"[i]
        lost = sum(1 for s, _ in skipped(parse(g["moves"])) if s == side)
        e = tab[(p["name"], size_of(g))]
        e[0] += 1
        e[1] += bool(g["log"]["oom"])
        e[2] += lost > 0
        e[3] += g["winner"] != side
        e[4] += g["winner"] != side and lost > 0
for k, v in sorted(tab.items()):
    print(" ", k, v)

# 5. Memory warnings: value distribution by whether Savi8/new Savi7 is in the game.
print("\nmemory-line rate on 15x11 by time order of fetch, top line vs others (first 10 games shown by fetch time)")
g15 = sorted((g for g in games if size_of(g) == "15x11"), key=lambda g: g["fetchedAt"])
by_pair_time = collections.OrderedDict()
for g in g15:
    key = " vs ".join(sorted(p["name"] for p in g["players"]))
    e = by_pair_time.setdefault(key, [g["fetchedAt"][11:16], 0, 0])
    e[1] += bool(g["log"]["oom"])
    e[2] += 1
for k, v in by_pair_time.items():
    print(f"  {v[0]}  {v[1]}/{v[2]}  {k}")
