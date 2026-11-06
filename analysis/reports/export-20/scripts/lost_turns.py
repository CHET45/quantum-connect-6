"""Lost turns per bot on clean records (legal, no merge), by own-turn band, old engine vs new machines; plus abandon/failed games."""
import collections, sys
from lib import *

BANDS = [(0, 2), (3, 9), (10, 19), (20, 29), (30, 59), (60, 10**6)]


def clean(x):
    r = replay(x)
    return r["problem"] is None and (r["six_at"] is None or r["six_at"] == len(toks(x["moves"])) - 1)


def lost_by_turn(ts):
    """For each side: list of own-turn numbers that were lost (a turn of that side with the other side moving)."""
    own = {"F": 0, "S": 0}; out = {"F": [], "S": []}
    expect = "F"
    for s, _, _ in ts:
        while s != expect:          # expect lost its turn
            out[expect].append(own[expect]); own[expect] += 1; expect = opp(expect)
        own[s] += 1; expect = opp(s)
    return out, own


def band(t):
    for a, b in BANDS:
        if a <= t <= b:
            return f"{a}-{b}" if b < 10**6 else f"{a}+"


def table(games, label, abandon_cut=True, only=None):
    turns = collections.defaultdict(collections.Counter); lost = collections.defaultdict(collections.Counter)
    gl = collections.Counter(); gn = collections.Counter(); ab = collections.Counter()
    for x in games:
        ts = toks(x["moves"]); L, own = lost_by_turn(ts)
        for i, s in enumerate("FS"):
            n = x["players"][i]["name"]; r = ("FIRST_PLAYER", "SECOND_PLAYER")[i]
            aband = any(f"Bot {r} is still" in e for e in x["errors"])
            gn[n] += 1; ab[n] += aband
            if aband and abandon_cut:
                continue           # counted separately: all remaining turns are forfeited
            gl[n] += bool(L[s])
            for t in range(own[s] + len(L[s])):
                turns[n][band(t)] += 1
            for t in L[s]:
                lost[n][band(t)] += 1
    print(f"\n{label}: lost turns per 1000 own turns by own-turn band (games where the bot was abandoned left out)")
    hdr = [band(a) for a, _ in BANDS]
    print("| bot | games | abandoned | games with a lost turn | all | " + " | ".join(hdr) + " |")
    print("|---" * (5 + len(hdr)) + "|")
    for n in sorted(gn, key=lambda n: -gn[n]):
        if only and n not in only:
            continue
        T = sum(turns[n].values()); Lt = sum(lost[n].values())
        cells = [f"{1000*lost[n][h]/turns[n][h]:.0f}" if turns[n][h] else "" for h in hdr]
        print(f"| {n} | {gn[n]} | {ab[n]} | {gl[n]} | {1000*Lt/max(1,T):.1f} | " + " | ".join(cells) + " |")


if __name__ == "__main__":
    g = load()
    new = [x for x in g if era(x) == "new" and clean(x)]
    for m in ("2824152", "2825972"):
        table([x for x in new if machine(x) == m], f"machine {m}")
    names = {p["name"] for x in g if era(x) == "new" for p in x["players"]}
    old = [x for x in g if era(x) == "old" and clean(x) and {p["name"] for p in x["players"]} & names]
    old = [x for x in old if x["board"]["cols"] == 30]
    print("\nold engine 15x30 games of these bots:", len(old), "fetched", min(x["fetchedAt"] for x in old)[:16], "..", max(x["fetchedAt"] for x in old)[:16])
    table(old, "old shared engine, 15x30, same bots", only=names)
