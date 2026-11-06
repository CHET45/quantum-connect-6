"""Whole-turn time of each bot on the OLD engine (timestamped lines only), from gaps between the opponent's consecutive
timeout lines when exactly one move of this bot lies between them (gap = opponent's timed-out turn + this bot's move).
The new machines print no timestamps, so the method does not work there.
"""
import collections
import datetime as dt
import re
import statistics

from lib import era, load, opp, toks

TS = re.compile(r"^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d+)Z")
WANT = None  # all bots


def stamp(s):
    return dt.datetime.fromisoformat(s).timestamp() * 1000


def main():
    g = load()
    games = [x for x in g if era(x) == "old" and x["board"]["cols"] in (30, 100)]
    samples = collections.defaultdict(list)
    for x in games:
        ts = toks(x["moves"])
        for t_idx, role in ((0, "FIRST_PLAYER"), (1, "SECOND_PLAYER")):
            side = "FS"[t_idx]; other = opp(side)
            times = sorted(stamp(TS.match(e).group(1)) for e in x["errors"]
                           if TS.match(e) and "timed out" in e and e.rstrip().endswith(role))
            if len(times) < 2:
                continue
            lost_at = []
            prev = "S"
            for n, (s, _, _) in enumerate(ts):
                if (s == prev and s == other) or (n == 0 and s == other):
                    lost_at.append(n)
                prev = s
            if len(lost_at) != len(times):
                continue
            name = x["players"][1 - t_idx]["name"]
            size = f"{x['board']['rows']}x{x['board']['cols']}"
            for a in range(len(times) - 1):
                if lost_at[a + 1] - lost_at[a] == 1:
                    samples[(name, size)].append(times[a + 1] - times[a])
    print("| bot | size | n | p10 | median | p90 |")
    print("|---|---|---|---|---|---|")
    for (name, size), v in sorted(samples.items(), key=lambda t: (t[0][1], -statistics.median(t[1]))):
        if len(v) < 5:
            continue
        v = sorted(v); n = len(v)
        print(f"| {name} | {size} | {n} | {v[n // 10]:.0f} | {statistics.median(v):.0f} | {v[9 * n // 10]:.0f} |")


if __name__ == "__main__":
    main()
