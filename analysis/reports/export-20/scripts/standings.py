"""Post-update standings from the site's labels (Won/Lost), head-to-head matrix, and how often the export's winner field disagrees."""
import collections
from lib import *

g = load()
new = [x for x in g if era(x) == "new"]
res = collections.defaultdict(collections.Counter)   # bot -> W/L
h2h = collections.defaultdict(collections.Counter)   # (a,b) -> a's W
mis = collections.Counter()
for x in new:
    for i, p in enumerate(x["players"]):
        lab = x["labels"].get(p["lb"])
        o = x["players"][1 - i]["name"]
        res[p["name"]]["G"] += 1
        res[p["name"]][str(lab)] += 1
        h2h[(p["name"], o)]["G"] += 1
        h2h[(p["name"], o)]["W"] += lab == "Won"
        if (lab == "Won") != (x["winner"] == "FS"[i]) and lab is not None:
            mis[p["name"]] += 1
bots = sorted(res, key=lambda b: -res[b]["Won"] / res[b]["G"])
print("| bot | games in export | W | L | none | score | winner field wrong |")
print("|---|---|---|---|---|---|---|")
for b in bots:
    c = res[b]
    print(f"| {b} | {c['G']} | {c['Won']} | {c['Lost']} | {c['None']} | {c['Won']/c['G']:.0%} | {mis[b]} |")
short = {b: b.replace('savi22 - "The King"', "savi22").replace("Cod Published CHET45", "rc8")[:10] for b in bots}
print("\nrow bot's wins / games against column bot (site labels)")
print("| | " + " | ".join(short[b] for b in bots) + " |")
print("|---" * (len(bots) + 1) + "|")
for a in bots:
    cells = []
    for b in bots:
        c = h2h.get((a, b))
        cells.append(f"{c['W']}/{c['G']}" if c else "")
    print(f"| {short[a]} | " + " | ".join(cells) + " |")
