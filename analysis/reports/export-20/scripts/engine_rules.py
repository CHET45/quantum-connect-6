"""New-engine log rules: per bot and game, compares 'Invalid column selected: -1' lines and 'exceeded'/'failed' lines
with visible lost turns (clean records, bot not abandoned). Also: lines per game for rc8, abandon delays, memory lines."""
import collections, re
from lib import *
from merged import is_merged
from lost_turns import lost_by_turn

g = load()
new = [x for x in g if era(x) == "new"]
cmp = collections.Counter(); per = collections.defaultdict(collections.Counter)
ours = collections.Counter(); mem = []
for x in new:
    merged = is_merged(x)
    ts = toks(x["moves"]); L, own = lost_by_turn(ts)
    for i, s in enumerate("FS"):
        r = ("FIRST_PLAYER", "SECOND_PLAYER")[i]; n = x["players"][i]["name"]
        E = x["errors"]
        exc = sum(f"Bot {r} exceeded" in e for e in E); fl = sum(f"Bot {r} failed" in e for e in E)
        ab = sum(f"Bot {r} is still" in e for e in E)
        inv = sum("Invalid column" in e and e.endswith(r) for e in E)
        if n == US:
            ours[(machine(x), "merged" if merged else "clean", exc, inv, len(L[s]))] += 1
        if merged or ab:
            continue
        cmp[("invalid lines == lost turns", inv == len(L[s]))] += 1
        cmp[("exceeded+failed - invalid (second-call skips)", exc + fl - inv)] += 1
        per[n]["first-call skips"] += inv; per[n]["second-call skips"] += exc + fl - inv; per[n]["lost"] += len(L[s]); per[n]["games"] += 1
    m = [int(re.search(r"(\d+)MB", e).group(1)) for e in x["errors"] if "Memory limit exceeded:" in e]
    mem.append((machine(x), len(m), m[0] if m else None, x["errors"].index(next(e for e in x["errors"] if "Memory" in e)) if m else None))
for k, v in sorted(cmp.items(), key=str): print(k, v)
print("\nper bot (clean, not abandoned): games, first-call skips (= lost turns), second-call skips")
for n, c in sorted(per.items(), key=lambda t: -t[1]["games"]):
    print(f"  {n[:24]:24s} games {c['games']:3d} first-call {c['first-call skips']:4d} lost {c['lost']:4d} second-call {c['second-call skips']:4d}")
print("\nrc8: (machine, record, exceeded lines, invalid lines, visible lost) -> games")
for k, v in sorted(ours.items()): print("  ", k, v)
print("\nmemory lines: per game count", collections.Counter(c for _, c, _, _ in mem), "; position of the first one in the log", collections.Counter(p for *_, p in mem))
for mm in ("2824152", "2825972"):
    v = sorted(x for m_, c, x, _ in mem if m_ == mm and x)
    print(f"  {mm}: MB min {v[0]} median {v[len(v)//2]} max {v[-1]}")
