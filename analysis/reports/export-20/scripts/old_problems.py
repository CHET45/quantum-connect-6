"""Problem records on the old engine: wrong board size (columns beyond the labelled width), lockstep merges, other."""
import collections
from lib import *
from merged import is_merged
g = load()
c = collections.Counter(); pairs = collections.Counter(); when = collections.Counter()
for x in g:
    if era(x) != "old" or not is_merged(x):
        continue
    ts = toks(x["moves"])
    if max(t[1] for t in ts) >= x["board"]["cols"]:
        kind = "columns beyond board (size label wrong)"
    else:
        rr = runs(ts)
        head = rr[:20]
        kind = "lockstep merge" if sum(1 for _, l in head if l == 2) >= 16 else "other (lost turns / odd)"
    c[(kind, x["board"]["cols"], x["board"].get("source"))] += 1
    when[(kind, x["fetchedAt"][:13])] += 1
    if kind == "lockstep merge":
        pairs[tuple(sorted(p["name"][:16] for p in x["players"]))] += 1
for k, v in sorted(c.items(), key=str): print(k, v)
for k, v in sorted(when.items()): print("  ", k, v)
print("lockstep merges by pair:", dict(pairs))
