"""Whole-turn time histogram per bot from timeout gaps (baseline removed), and lost turns per 1000 own turns."""
import collections
import datetime as dt
import json
import re
import statistics
import sys

TS = re.compile(r"^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d+)Z")
BASE = {"Testing_v2": 101.0, "I'm too young to die": 1.0}


def parse(moves):
    out, i = [], 0
    while i < len(moves):
        side, col = moves[i], int(moves[i + 1], 36)
        i += 2
        star = i < len(moves) and moves[i] == "*"
        i += star
        out.append((side, col, star))
    return out


games = [json.loads(l) for l in open(sys.argv[1] + "/matches.jsonl", encoding="utf-8") if l.strip()]
size_of = lambda g: f"{g['board']['rows']}x{g['board']['cols']}"
samples = collections.defaultdict(list)
for g in games:
    toks = parse(g["moves"])
    names = {"F": g["players"][0]["name"], "S": g["players"][1]["name"]}
    turns = []
    prev = "S"
    for s, _, st in toks:
        if s == prev:
            turns.append(("S" if s == "F" else "F", "lost", False))
        turns.append((s, "play", st))
        prev = s
    lines = []
    for e in g["errors"]:
        m = TS.match(e)
        if m and "timed out" in e:
            lines.append((dt.datetime.fromisoformat(m.group(1)).timestamp() * 1000, "F" if e.endswith("FIRST_PLAYER") else "S"))
    lines.sort()
    lost = [i for i, t in enumerate(turns) if t[1] == "lost"]
    if [s for _, s in lines] != [turns[i][0] for i in lost]:
        continue
    for a in range(len(lines) - 1):
        i, j = lost[a], lost[a + 1]
        if j - i == 2 and turns[i + 1][1] == "play" and turns[i][0] == turns[j][0]:
            who = names[turns[i][0]]
            if who in BASE:
                me = names[turns[i + 1][0]]
                own = sum(1 for t in turns[:i + 2] if t[0] == turns[i + 1][0])
                samples[(me, size_of(g), who)].append((lines[a + 1][0] - lines[a][0] - BASE[who], own))

print("whole-turn time of a bot in ms (gap minus the opponent's lost-turn baseline), histogram by 10 ms bands; own = own turn number")
for k, v in sorted(samples.items(), key=lambda x: (x[0][1], x[0][0])):
    if not (k[0].startswith("Savi") or k[0] in ("FunkyMelodyBot", "lalalalabot")) or len(v) < 3:
        continue
    h = collections.Counter(min(int(a // 10) * 10, 200) for a, _ in v)
    print(f"  {k[0][:21]:<23}{k[1]:<6}vs {k[2][:10]:<11} n={len(v):<4} med {statistics.median(a for a, _ in v):>5.0f}  " +
          " ".join(f"{b}:{h[b]}" for b in sorted(h)))
    if k[0].startswith(("Savi8", "Savi 7", "Savi7", "Savi5 beater", "Savi beater")):
        early = sorted((own, round(a)) for a, own in v)[:14]
        print(f"      first own turns (turn, ms): {early}")

print("\nlost turns per 1000 own turns (played + lost), by bot and size, with log lines of 'timed out' per 1000")
own = collections.Counter()
lostc = collections.Counter()
for g in games:
    toks = parse(g["moves"])
    prev = "S"
    for s, _, _ in toks:
        if s == prev:
            o = "S" if s == "F" else "F"
            lostc[(g["players"]["FS".index(o)]["name"], size_of(g))] += 1
            own[(g["players"]["FS".index(o)]["name"], size_of(g))] += 1
        own[(g["players"]["FS".index(s)]["name"], size_of(g))] += 1
        prev = s
for size in ("8x12", "15x11", "15x22"):
    rows = [(k[0], lostc[k], own[k]) for k in own if k[1] == size and own[k] >= 300]
    print(f"  {size}: " + "; ".join(f"{n} {1000 * l / o:.1f} ({l}/{o})" for n, l, o in sorted(rows, key=lambda r: -r[1] / r[2]) if l or n.startswith("Savi")))
