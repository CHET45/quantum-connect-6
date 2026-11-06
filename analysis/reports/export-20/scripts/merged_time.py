"""Each bot's site history order (latest snapshot) with merged records marked: M merged, . clean, ? not exported."""
import json, glob
from lib import *
from merged import is_merged
g = {x["id"]: x for x in load() if era(x) == "new"}
snap = json.load(open(sorted(glob.glob(EXPORT + "/export-20/data/snapshots/*"))[-1], encoding="utf-8"))
for b in snap["bots"]:
    s = ""
    opp_seq = []
    for h in b["history"]:
        x = g.get(h["id"])
        s += "?" if not x else ("M" if is_merged(x) else ".")
        opp_seq.append((h["second"] if h["first"] == b["name"] else h["first"])[:6])
    print(f"{b['name'][:22]:22s} {s}")
    if "savi22" in b["name"] or b["name"] == US:
        # show opponent blocks
        blocks = []
        for o in opp_seq:
            if not blocks or blocks[-1][0] != o:
                blocks.append([o, 0])
            blocks[-1][1] += 1
        print("    opponent blocks in history order:", " ".join(f"{o}x{n}" for o, n in blocks))
