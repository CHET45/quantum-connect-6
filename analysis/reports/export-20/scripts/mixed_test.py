"""Which new-engine records are illegal as written, and can each be split into two legal games?"""
import collections, time
from lib import *
from deinterleave import split
g = load()
new = [x for x in g if era(x) == "new"]
mixed = [x for x in new if replay(x)["problem"] or (replay(x)["six_at"] is not None and replay(x)["six_at"] != len(toks(x["moves"])) - 1)]
print(len(mixed), "records illegal or with a six before the end")
for x in mixed:
    t0 = time.time()
    r = split(x["moves"], x["board"]["rows"], x["board"]["cols"])
    names = [p["name"][:14] for p in x["players"]]
    if r is None:
        print("NOSPLIT", machine(x), names, len(toks(x["moves"])), round(time.time() - t0, 1)); continue
    a, b, info = r
    print("split", machine(x), names, x["winner"], len(toks(x["moves"])), "A", len(a.idx), a.six, "B", len(b.idx), b.six, "nodes", info["nodes"])
