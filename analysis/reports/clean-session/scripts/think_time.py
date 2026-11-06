"""Per-move thinking time of a bot, from the gap between an opponent's consecutive timeout lines; and opening determinism."""
import collections
import datetime as dt
import json
import re
import statistics
import sys

TS = re.compile(r"^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d+)Z")
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


def stamp(s):
    return dt.datetime.fromisoformat(s).timestamp() * 1000


games = [json.loads(l) for l in open(sys.argv[1] + "/matches.jsonl", encoding="utf-8") if l.strip()]
size_of = lambda g: f"{g['board']['rows']}x{g['board']['cols']}"

# Gap between two timeout lines of the same side, when the tokens show exactly one move of the other side between them.
samples = collections.defaultdict(list)
for g in games:
    toks = parse(g["moves"])
    for t_idx, side_name in ((0, "FIRST_PLAYER"), (1, "SECOND_PLAYER")):
        side = "FS"[t_idx]
        other = "FS"[1 - t_idx]
        times = []
        for e in g["errors"]:
            m = TS.match(e)
            if m and "timed out" in e and e.endswith(side_name):
                times.append(stamp(m.group(1)))
        times.sort()
        if len(times) < 2:
            continue
        # Lost turns of this side in order: a break in the alternation where `other` plays twice in a row, or at the start.
        lost_at = []
        prev = "S"
        for n, (s, _, _) in enumerate(toks):
            if s == prev and s == other:
                lost_at.append(n)
            elif n == 0 and s == other:
                lost_at.append(n)
            prev = s
        if len(lost_at) != len(times):
            continue
        name = g["players"][1 - t_idx]["name"]
        timeouter = g["players"][t_idx]["name"]
        for a in range(len(times) - 1):
            # Consecutive lost turns: tokens between them are all of `other`, exactly one token.
            if lost_at[a + 1] - lost_at[a] == 1:
                gap = times[a + 1] - times[a]
                ply = lost_at[a]
                star = toks[lost_at[a]][2]
                samples[(name, size_of(g))].append((gap, ply, timeouter, star))

print("gap between two consecutive lost turns of the opponent = opponent's timed-out turn + this bot's whole move, in ms")
print(f"{'bot':<26}{'size':<7}{'n':>5}{'p10':>7}{'p50':>7}{'p90':>7}{'max':>7}   by opponent (median, n)")
for (name, size), v in sorted(samples.items(), key=lambda x: (x[0][1], -statistics.median(a[0] for a in x[1]))):
    if len(v) < 5:
        continue
    g = sorted(a[0] for a in v)
    n = len(g)
    per = collections.defaultdict(list)
    for a in v:
        per[a[2]].append(a[0])
    print(f"{name:<26}{size:<7}{n:>5}{g[n // 10]:>7.0f}{g[n // 2]:>7.0f}{g[9 * n // 10]:>7.0f}{g[-1]:>7.0f}   " +
          ", ".join(f"{k[:14]} {statistics.median(x):.0f} ({len(x)})" for k, x in per.items()))

print("\nthe same gap by game phase (median ms), bots with enough samples")
for (name, size), v in sorted(samples.items(), key=lambda x: (x[0][1], x[0][0])):
    if len(v) < 40:
        continue
    bands = collections.defaultdict(list)
    for gap, ply, _, _ in v:
        bands["ply<10" if ply < 10 else "10-19" if ply < 20 else "20-39" if ply < 40 else "40+"].append(gap)
    print(f"  {name:<26}{size:<7}" + ", ".join(f"{k}: {statistics.median(b):.0f} (n={len(b)})" for k, b in sorted(bands.items())))

# Opening determinism: for each bot and each exact prefix seen at least twice with it to move, how often does it repeat its move?
print("\nopening determinism: for positions reached >= 2 times, share of repeats of the most common reply")
for size in ("8x12", "15x11", "15x22"):
    stat = collections.defaultdict(lambda: collections.defaultdict(collections.Counter))
    for g in games:
        if size_of(g) != size:
            continue
        toks = parse(g["moves"])
        pre = ""
        for n, (s, c, st) in enumerate(toks[:40]):
            name = g["players"]["FS".index(s)]["name"]
            mv = format(c, "x") + ("*" if st else "")
            stat[name][pre][mv] += 1
            pre += s + mv
    print(f"  {size}:")
    rows = []
    for name, tab in stat.items():
        seen = [c for c in tab.values() if sum(c.values()) >= 2]
        if len(seen) < 10:
            continue
        total = sum(sum(c.values()) for c in seen)
        same = sum(c.most_common(1)[0][1] for c in seen)
        single = sum(1 for c in seen if len(c) == 1)
        rows.append((same / total, name, len(seen), single, total))
    for r in sorted(rows, reverse=True):
        print(f"    {r[1]:<26} repeat {r[0]:.3f}; positions {r[2]}, always same reply {r[3]}, visits {r[4]}")

# Length of the common prefix among games of the same pair with the same colours.
print("\ncommon prefix of games of one pair with the same colours (median plies of the longest shared prefix with an earlier game), top pairs")
pairs = collections.defaultdict(list)
for g in games:
    pairs[(size_of(g), g["players"][0]["name"], g["players"][1]["name"])].append(parse(g["moves"]))
rows = []
for k, gs in pairs.items():
    if len(gs) < 3:
        continue
    lens = []
    for i in range(1, len(gs)):
        best = 0
        for j in range(i):
            n = 0
            for a, b in zip(gs[i], gs[j]):
                if a != b:
                    break
                n += 1
            best = max(best, n)
        lens.append(best)
    rows.append((k, len(gs), statistics.median(lens), max(lens), statistics.median(len(g) for g in gs)))
for r in sorted(rows, key=lambda x: -x[2]):
    if any(n.startswith("Savi") for n in r[0][1:]):
        print(f"  {r[0][0]:<6}{r[0][1][:24]:<26}{r[0][2][:24]:<26} games {r[1]} shared median {r[2]:.0f} max {r[3]} of median length {r[4]:.0f}")

# Our games against the Savi line: the tree of openings with results.
print("\nour games against the Savi line on 15x11: opening tree (first 30 plies), result in our view")
for g in sorted(games, key=lambda g: g["moves"]):
    if size_of(g) != "15x11":
        continue
    i = next((i for i, p in enumerate(g["players"]) if p["team"] == TEAM), None)
    if i is None or not g["players"][1 - i]["name"].startswith("Savi"):
        continue
    toks = parse(g["moves"])
    print(f"  {g['players'][i]['name'][-3:]} as {'FS'[i]} vs {g['players'][1 - i]['name'][:7]} {'W' if g['winner'] == 'FS'[i] else 'L'} {len(toks):>3}  " +
          "".join(s + format(c, "x") + ("*" if st else "") for s, c, st in toks[:30]))
