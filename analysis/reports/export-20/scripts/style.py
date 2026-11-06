"""Style of each bot after the update, from clean (unmerged) records: opening, edge play, blasts, game length, how wins
end; plus draws per pair from the site's history (a pair with fewer than 7 history entries had draws, not exported).
"""
import collections
import glob
import json
import statistics

from lib import EXPORT, Board, era, load, opp, toks
from merged import is_merged


def edge(c, ncols):
    return c <= 2 or c >= ncols - 3


def main():
    g = load()
    new = [x for x in g if era(x) == "new" and not is_merged(x)]
    st = collections.defaultdict(collections.Counter)
    first = collections.defaultdict(collections.Counter)
    reply = collections.defaultdict(collections.Counter)
    lens = collections.defaultdict(lambda: {"W": [], "L": []})
    firstblast = collections.defaultdict(list)
    for x in new:
        ts = toks(x["moves"])
        if not ts:
            continue
        C = x["board"]["cols"]
        for i, side in enumerate("FS"):
            n = x["players"][i]["name"]
            won = x["labels"].get(x["players"][i]["lb"]) == "Won"
            mine = [(k, t) for k, t in enumerate(ts) if t[0] == side]
            c = st[n]
            c["games"] += 1; c["turns"] += len(mine)
            c["edge"] += sum(edge(t[1], C) for _, t in mine)
            c["edge10"] += sum(edge(t[1], C) for _, t in mine[:10]); c["t10"] += min(10, len(mine))
            c["blasts"] += sum(t[2] for _, t in mine)
            c["far"] += sum(abs(t[1] - C // 2) >= 8 for _, t in mine)
            if mine:
                (first if side == "F" else reply)[n][mine[0][1][1]] += 1
            fb = [j for j, (_, t) in enumerate(mine) if t[2]]
            if fb:
                firstblast[n].append(fb[0])
            lens[n]["W" if won else "L"].append(len(ts))
            if won:
                c["W"] += 1
                last = ts[-1]
                if last[0] == side:
                    c["win_by_blast"] += last[2]
                    c["win_edge"] += edge(last[1], C)
                    if len(ts) <= 30:
                        c["win_short"] += 1
    print("| bot | games | W | turns | blasts /100 turns | first blast at own turn (median) | edge share (cols 0-2, 27-29) | edge share first 10 turns | far from centre (>=8) | wins ending with a blast | wins in <=30 plies | median plies won / lost | first move as F | first reply as S |")
    print("|---" * 14 + "|")
    for n in sorted(st, key=lambda n: -st[n]["games"]):
        c = st[n]
        if c["games"] < 15:
            continue
        fb = statistics.median(firstblast[n]) if firstblast[n] else "-"
        mw = statistics.median(lens[n]["W"]) if lens[n]["W"] else "-"
        ml = statistics.median(lens[n]["L"]) if lens[n]["L"] else "-"
        f = ", ".join(f"{k}:{v}" for k, v in first[n].most_common(3))
        r = ", ".join(f"{k}:{v}" for k, v in reply[n].most_common(3))
        print(f"| {n} | {c['games']} | {c['W']} | {c['turns']} | {100*c['blasts']/max(1,c['turns']):.1f} | {fb} | "
              f"{c['edge']/max(1,c['turns']):.0%} | {c['edge10']/max(1,c['t10']):.0%} | {c['far']/max(1,c['turns']):.0%} | "
              f"{c['win_by_blast']}/{c['W']} | {c['win_short']} | {mw} / {ml} | {f} | {r} |")

    # draws per pair from the latest snapshot history
    snap = json.load(open(sorted(glob.glob(EXPORT + "/export-20/data/snapshots/*"))[-1], encoding="utf-8"))
    print("\npairs with fewer than 7 games in the site history (the rest are draws or not yet played)")
    for b in snap["bots"]:
        cnt = collections.Counter()
        for h in b["history"]:
            cnt[h["second"] if h["first"] == b["name"] else h["first"]] += 1
        others = [o["name"] for o in snap["bots"] if o["name"] != b["name"]]
        short = {o: cnt[o] for o in others if cnt[o] < 7}
        if b["wld"][2] or short:
            print(f"  {b['name']} wld {b['wld']}: " + ", ".join(f"{o} {v}" for o, v in short.items()))


if __name__ == "__main__":
    main()
