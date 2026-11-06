"""Each regular bot's score on the old shared engine (last 15x30 and 15x100 games, site labels) vs after the update;
rc8's record by opponent before and after."""
import collections
from lib import *
g = load()
newb = {p["name"] for x in g if era(x) == "new" for p in x["players"]}
def score(games):
    c = collections.defaultdict(collections.Counter)
    for x in games:
        for i, p in enumerate(x["players"]):
            lab = x["labels"].get(p["lb"])
            c[p["name"]]["G"] += 1; c[p["name"]]["W"] += lab == "Won"
    return c
old = [x for x in g if era(x) == "old" and x["fetchedAt"] >= "2026-11-04T10"]
o30 = score([x for x in old if x["board"]["cols"] == 30]); o100 = score([x for x in old if x["board"]["cols"] == 100])
nw = score([x for x in g if era(x) == "new"])
print("| bot | old 15x30 W/G | old 15x100 W/G | new 15x30 W/G |")
print("|---|---|---|---|")
for n in sorted(newb, key=lambda n: -nw[n]["W"] / max(1, nw[n]["G"])):
    f = lambda c: f"{c[n]['W']}/{c[n]['G']} ({c[n]['W']/c[n]['G']:.0%})" if c[n]["G"] else "-"
    print(f"| {n} | {f(o30)} | {f(o100)} | {f(nw)} |")
# rc8 by opponent, old (any size) vs new
print("\nrc8 by opponent: old engine (all sizes) | new")
by = collections.defaultdict(collections.Counter)
for x in g:
    s = side_of(x)
    if not s: continue
    i = "FS".index(s); o = x["players"][1 - i]["name"]
    k = "new" if era(x) == "new" else "old"
    by[o][k + "G"] += 1; by[o][k + "W"] += x["labels"].get(x["players"][i]["lb"]) == "Won"
for o, c in sorted(by.items(), key=lambda t: -(t[1]["oldG"] + t[1]["newG"])):
    print(f"  {o[:24]:24s} old {c['oldW']}/{c['oldG']}  new {c['newW']}/{c['newG']}")
