"""Per-bot move time per board size: least squares over the time between consecutive timeout lines inside each game."""
import collections
import datetime as dt
import json
import re
import statistics
import sys

import numpy as np

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


def stamp(s):
    return dt.datetime.fromisoformat(s).timestamp() * 1000


games = [json.loads(l) for l in open(sys.argv[1] + "/matches.jsonl", encoding="utf-8") if l.strip()]
size_of = lambda g: f"{g['board']['rows']}x{g['board']['cols']}"

segments = []  # (ms, {param: count}, facts)
raw = collections.defaultdict(list)
dropped = kept = 0
for g in games:
    toks = parse(g["moves"])
    names = {"F": g["players"][0]["name"], "S": g["players"][1]["name"]}
    # Event list of turns in order: played tokens and lost turns, using the alternation.
    turns = []
    prev = "S"
    for s, _, st in toks:
        if s == prev:
            turns.append(("S" if s == "F" else "F", "lost"))
        turns.append((s, "play"))
        prev = s
    lines = []
    for e in g["errors"]:
        m = TS.match(e)
        if m and "timed out" in e:
            lines.append((stamp(m.group(1)), "F" if e.endswith("FIRST_PLAYER") else "S"))
    lines.sort()
    lost_idx = [i for i, t in enumerate(turns) if t[1] == "lost"]
    # The log can hold one more lost turn at the very end; only trust a game whose counts agree per side.
    log_seq = [s for _, s in lines]
    turn_seq = [turns[i][0] for i in lost_idx]
    if log_seq != turn_seq:
        # Same-millisecond lines may be out of order; compare by count, then fall back to skipping the game.
        dropped += 1
        continue
    kept += 1
    size = size_of(g)
    for a in range(len(lines) - 1):
        ms = lines[a + 1][0] - lines[a][0]
        i, j = lost_idx[a], lost_idx[a + 1]
        # From the end of lost turn i to the end of lost turn j: everything after i up to and including j.
        count = collections.Counter()
        for s, kind in turns[i + 1:j]:
            count[(names[s], size, "move")] += 1
        count[(names[turns[j][0]], "any", "lost")] += 1
        segments.append((ms, count, g["id"]))
        plays = [turns[k] for k in range(i + 1, j)]
        if len(plays) == 1:
            raw[(names[plays[0][0]], size, names[turns[j][0]])].append((round(ms), i))

print(f"games with timeout lines matching the alternation: {kept}, dropped {dropped}; segments {len(segments)}")

# A long gap holds server stalls; cut the segments where the time per turn is absurd.
params = sorted({p for _, c, _ in segments for p in c}, key=str)
index = {p: i for i, p in enumerate(params)}
A = np.zeros((len(segments), len(params)))
y = np.zeros(len(segments))
for r, (ms, c, _) in enumerate(segments):
    for p, n in c.items():
        A[r, index[p]] = n
    y[r] = ms
weights = np.ones(len(segments))
sol = None
for _ in range(6):  # iteratively reweighted against stalls
    W = np.sqrt(weights)[:, None]
    sol, *_ = np.linalg.lstsq(A * W, y * W[:, 0], rcond=None)
    res = np.abs(y - A @ sol)
    weights = 1 / np.maximum(res, 5.0)
counts = A.sum(axis=0)
print("\nestimated ms per played move (moves observed in segments), per board size")
rows = [(p, sol[index[p]], int(counts[index[p]])) for p in params if p[2] == "move"]
for size in ("8x12", "15x11", "15x22"):
    print(f"  {size}:")
    for p, v, n in sorted((r for r in rows if r[0][1] == size), key=lambda r: -r[1]):
        if n >= 20:
            print(f"    {p[0]:<26}{v:>7.1f} ms   moves {n}")
print("\nestimated ms per lost turn")
for p, v, n in sorted(((p, sol[index[p]], int(counts[index[p]])) for p in params if p[2] == "lost"), key=lambda r: -r[1]):
    if n >= 3:
        print(f"    {p[0]:<26}{v:>7.1f} ms   lost turns {n}")

print("\nraw single-move gaps (gap ms @ turn index) for top bots")
for key, v in sorted(raw.items(), key=lambda x: (x[0][1], x[0][0], x[0][2])):
    if key[0].startswith("Savi") or key[0].startswith("Testing"):
        vals = sorted(a for a, _ in v)
        print(f"  {key[0][:22]:<24}{key[1]:<7}vs {key[2][:18]:<20} n={len(v):<4} med {statistics.median(vals):>6.1f}  {vals[:24]}")
