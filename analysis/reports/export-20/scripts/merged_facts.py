"""Facts about merged records: who, when in the pair's series, blast spacing of each bot inside them (shared instance?),
probe lines of rc8, and results (site labels) merged vs clean per bot."""
import collections, statistics
from lib import *
from merged import is_merged

g = load()
new = [x for x in g if era(x) == "new"]
res = collections.defaultdict(collections.Counter)
gaps = collections.defaultdict(list); gaps_clean = collections.defaultdict(list)
lockfrac = []
for x in new:
    m = is_merged(x)
    ts = toks(x["moves"])
    for i, s in enumerate("FS"):
        n = x["players"][i]["name"]
        won = x["labels"].get(x["players"][i]["lb"]) == "Won"
        res[n][("merged" if m else "clean", won)] += 1
        mine = [t for t in ts if t[0] == s]
        idx = [k for k, t in enumerate(mine) if t[2]]
        for a, b in zip(idx, idx[1:]):
            (gaps if m else gaps_clean)[n].append(b - a)
    if m:
        rr = runs(ts)
        lockfrac.append(sum(l for _, l in rr if l == 2) / len(ts))
print("results (site labels): clean W-L | merged W-L")
for n, c in sorted(res.items(), key=lambda t: -sum(t[1].values())):
    print(f"  {n[:24]:24s} clean {c[('clean', True)]}-{c[('clean', False)]} | merged {c[('merged', True)]}-{c[('merged', False)]}")
print("\nown tokens between a bot's consecutive blasts (in the record): median merged vs clean")
for n in sorted(set(gaps) | set(gaps_clean)):
    a, b = gaps.get(n, []), gaps_clean.get(n, [])
    if len(a) >= 3:
        print(f"  {n[:24]:24s} merged median {statistics.median(a)} (n={len(a)}, min {min(a)}) | clean median {statistics.median(b) if b else '-'} (n={len(b)})")
print("\nshare of merged-record plies inside the lockstep phase: median", round(statistics.median(lockfrac), 2))
