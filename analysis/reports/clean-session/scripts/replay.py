"""Replay every server game with its real board size and report results, skips, memory lines and how games end."""
import collections
import csv
import json
import sys

TEAM = "No beaches?"
WIN = 6


def parse(moves):
    """Tokens (side, column, blast); columns are base-36 digits."""
    out, i = [], 0
    while i < len(moves):
        side, col = moves[i], int(moves[i + 1], 36)
        i += 2
        star = i < len(moves) and moves[i] == "*"
        i += star
        out.append((side, col, star))
    return out


def six_lines(cols, rows, ncols):
    """All maximal lines of at least WIN equal discs: (colour, direction, length, start column, start row)."""
    found = []
    get = lambda c, r: cols[c][r] if 0 <= c < ncols and 0 <= r < len(cols[c]) else None
    for c in range(ncols):
        for r in range(len(cols[c])):
            v = cols[c][r]
            for name, dc, dr in (("H", 1, 0), ("V", 0, 1), ("D+", 1, 1), ("D-", 1, -1)):
                if get(c - dc, r - dr) == v:
                    continue
                n = 1
                while get(c + dc * n, r + dr * n) == v:
                    n += 1
                if n >= WIN:
                    found.append((v, name, n, c, r))
    return found


def replay(game):
    b = game["board"]
    rows, ncols = b["rows"], b["cols"]
    cols = [[] for _ in range(ncols)]
    res = {"plies": 0, "end": None, "problem": None, "blasts": {"F": 0, "S": 0}, "lines": [], "last": None}
    for n, (side, col, star) in enumerate(parse(game["moves"])):
        res["plies"] = n + 1
        res["last"] = (side, col, star)
        if col >= ncols:
            res["problem"] = f"column {col} outside the board at ply {n + 1}"
            return res
        if len(cols[col]) >= rows:
            if star:
                res["blasts"][side] += 1
                continue
            res["problem"] = f"disc into a full column at ply {n + 1}"
            return res
        r = len(cols[col])
        cols[col].append(side)
        if star:
            res["blasts"][side] += 1
            for c in range(max(0, col - 1), min(ncols, col + 2)):
                for rr in sorted((x for x in (r - 1, r, r + 1) if 0 <= x < len(cols[c])), reverse=True):
                    del cols[c][rr]
        lines = six_lines(cols, rows, ncols)
        if lines:
            res["end"] = n
            res["lines"] = lines
            res["fill"] = sum(len(c) for c in cols)
            res["height"] = max(len(c) for c in cols)
            if n + 1 != len(parse(game["moves"])):
                res["problem"] = f"six at ply {n + 1} but game goes on"
            return res
    res["fill"] = sum(len(c) for c in cols)
    return res


def skips(tokens):
    """Turns lost per side, from breaks in the F/S alternation; the first token decides who started."""
    out = {"F": 0, "S": 0}
    prev = "S"
    for side, _, _ in tokens:
        if side == prev:
            out["S" if side == "F" else "F"] += 1
        prev = side
    return out


def main(d):
    games = [json.loads(l) for l in open(f"{d}/matches.jsonl", encoding="utf-8") if l.strip()]
    print("games", len(games))
    sizes = collections.Counter(f"{g['board']['rows']}x{g['board']['cols']}" for g in games)
    print("sizes", dict(sizes))
    print("board sources", collections.Counter(g["board"].get("source", "snapshot") for g in games))

    # How games end, per size, with memory lines separated.
    endings = collections.Counter()
    problems = collections.Counter()
    samples = []
    results = {}
    for g in games:
        r = replay(g)
        results[g["id"]] = r
        size = f"{g['board']['rows']}x{g['board']['cols']}"
        oom = bool(g["log"]["oom"])
        if r["problem"]:
            kind = r["problem"].split(" at ")[0].split(" column ")[0]
            endings[(size, oom, "PROBLEM:" + kind)] += 1
            problems[(size, g["board"].get("source", "snapshot"))] += 1
            if len(samples) < 12:
                samples.append((g["id"][:8], size, g["board"].get("source", "snapshot"), r["problem"]))
        elif r["end"] is None:
            endings[(size, oom, "NO SIX")] += 1
            if len(samples) < 12:
                samples.append((g["id"][:8], size, "no six", g["winner"], len(g["moves"]), g["moves"][-20:]))
        else:
            mover_ok = r["last"][0] == g["winner"]
            endings[(size, oom, "six, winner is mover" if mover_ok else "six, winner NOT mover")] += 1
    print("\nendings (size, any OOM line, ending): count")
    for k, v in sorted(endings.items()):
        print(f"  {k}: {v}")
    print("samples:")
    for s in samples:
        print("  ", s)

    # Win tables per size, per bot, by side.
    tab = collections.defaultdict(lambda: collections.Counter())
    h2h = collections.defaultdict(lambda: collections.Counter())
    for g in games:
        size = f"{g['board']['rows']}x{g['board']['cols']}"
        f, s = g["players"]
        w = f if g["winner"] == "F" else s
        l = s if g["winner"] == "F" else f
        tab[(size, w["name"])]["W"] += 1
        tab[(size, l["name"])]["L"] += 1
        tab[(size, w["name"])]["W_" + g["winner"]] += 1
        tab[(size, l["name"])]["L_" + ("S" if g["winner"] == "F" else "F")] += 1
        h2h[(size, w["name"], l["name"])]["W"] += 1
    for size in sorted(sizes, key=lambda x: (int(x.split("x")[0]), int(x.split("x")[1]))):
        print(f"\n=== {size}: {sizes[size]} games; first-player wins "
              f"{sum(1 for g in games if f'{g['board']['rows']}x{g['board']['cols']}' == size and g['winner'] == 'F')}")
        rows = [(n, c) for (sz, n), c in tab.items() if sz == size]
        rows.sort(key=lambda x: -(x[1]["W"] / max(1, x[1]["W"] + x[1]["L"])) * 1000 - x[1]["W"] / 1000)
        print(f"  {'bot':<26}{'W':>5}{'L':>5}{'win%':>7}   W as F / L as F / W as S / L as S")
        for n, c in rows:
            t = c["W"] + c["L"]
            print(f"  {n:<26}{c['W']:>5}{c['L']:>5}{100 * c['W'] / t:>7.1f}   "
                  f"{c['W_F']} / {c['L_F']} / {c['W_S']} / {c['L_S']}")

    # Our team and the top line, head to head.
    print("\nour bots head to head, per size: opponent W-L (our wins - our losses)")
    ours = {p["name"] for g in games for p in g["players"] if p["team"] == TEAM}
    for size in sizes:
        for me in sorted(ours):
            opp = collections.defaultdict(lambda: [0, 0])
            for (sz, w, l), c in h2h.items():
                if sz != size:
                    continue
                if w == me:
                    opp[l][0] += c["W"]
                if l == me:
                    opp[w][1] += c["W"]
            if opp:
                print(f"  {size} {me}: " + ", ".join(f"{k} {v[0]}-{v[1]}" for k, v in sorted(opp.items(), key=lambda x: -x[1][1])))

    # Memory lines: which bots appear with them, and does a game that has them still end in a six?
    print("\nmemory lines per bot (games with a 'limit exceeded' line / games), per size")
    mem = collections.defaultdict(lambda: [0, 0])
    pair = collections.defaultdict(lambda: [0, 0])
    for g in games:
        size = f"{g['board']['rows']}x{g['board']['cols']}"
        o = bool(g["log"]["oom"])
        names = [p["name"] for p in g["players"]]
        for n in names:
            mem[(size, n)][0] += o
            mem[(size, n)][1] += 1
        key = tuple(sorted(names))
        pair[key][0] += o
        pair[key][1] += 1
    for size in sizes:
        rows = sorted(((n, v) for (s, n), v in mem.items() if s == size), key=lambda x: -x[1][0] / x[1][1])
        print(f"  {size}: " + ", ".join(f"{n} {v[0]}/{v[1]}" for n, v in rows))
    print("pairs with no memory line at all in >= 5 games:")
    clean = [(k, v) for k, v in pair.items() if v[0] == 0 and v[1] >= 5]
    for k, v in sorted(clean, key=lambda x: -x[1][1])[:40]:
        print(f"  {k[0]} vs {k[1]}: 0/{v[1]}")
    print("pairs with memory lines in every game, >= 5 games:")
    full = [(k, v) for k, v in pair.items() if v[0] == v[1] and v[1] >= 5]
    for k, v in sorted(full, key=lambda x: -x[1][1])[:40]:
        print(f"  {k[0]} vs {k[1]}: {v[0]}/{v[1]}")
    oom_vals = [x for g in games for x in g["log"]["oom"]]
    if oom_vals:
        oom_vals.sort()
        print("OOM MB values: n", len(oom_vals), "min", oom_vals[0], "median", oom_vals[len(oom_vals) // 2], "max", oom_vals[-1])

    # Skipped turns per bot per size vs logged timeouts.
    print("\nskipped turns per side, by bot and size (skips / games, games with >= 1 skip)")
    sk = collections.defaultdict(lambda: [0, 0, 0])
    for g in games:
        size = f"{g['board']['rows']}x{g['board']['cols']}"
        t = skips(parse(g["moves"]))
        for side, p in zip("FS", g["players"]):
            e = sk[(size, p["name"])]
            e[0] += t[side]
            e[1] += 1
            e[2] += t[side] > 0
    for size in sizes:
        rows = sorted(((n, v) for (s, n), v in sk.items() if s == size), key=lambda x: -x[1][0] / x[1][1])
        print(f"  {size}: " + ", ".join(f"{n} {v[0]}/{v[1]} ({v[2]})" for n, v in rows))

    # How decided games end.
    print("\nwinning move, per size: blast share, colour of the six, direction, median plies")
    for size in sizes:
        gs = [g for g in games if f"{g['board']['rows']}x{g['board']['cols']}" == size and results[g["id"]]["end"] is not None and not results[g["id"]]["problem"]]
        if not gs:
            continue
        blast = sum(1 for g in gs if results[g["id"]]["last"][2])
        own = sum(1 for g in gs if any(l[0] == results[g["id"]]["last"][0] for l in results[g["id"]]["lines"]))
        dirs = collections.Counter(results[g["id"]]["lines"][0][1] for g in gs)
        plies = sorted(results[g["id"]]["plies"] for g in gs)
        print(f"  {size}: n={len(gs)} blast wins {blast} ({100 * blast / len(gs):.1f}%), six of the mover's colour {own}, dirs {dict(dirs)}, plies median {plies[len(plies) // 2]} max {plies[-1]}")

    with open(sys.argv[2], "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow("id size first second winner plies end_six blast_win six_colour_mover dir fill height blasts_f blasts_s skips_f skips_s oom problem".split())
        for g in games:
            r = results[g["id"]]
            t = skips(parse(g["moves"]))
            line = r["lines"][0] if r["lines"] else None
            w.writerow([g["id"], f"{g['board']['rows']}x{g['board']['cols']}", g["players"][0]["name"], g["players"][1]["name"], g["winner"],
                        r["plies"], r["end"] is not None, bool(r["last"] and r["last"][2]),
                        any(l[0] == r["last"][0] for l in r["lines"]) if r["lines"] else "", line[1] if line else "",
                        r.get("fill", ""), r.get("height", ""), r["blasts"]["F"], r["blasts"]["S"], t["F"], t["S"], len(g["log"]["oom"]), r["problem"] or ""])


main(sys.argv[1])
