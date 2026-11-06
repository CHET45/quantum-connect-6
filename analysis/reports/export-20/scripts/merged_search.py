"""Splits merged records (two runs of one match in lockstep, F F S S per round, then one run alone) into two legal games.

Search over the order inside each lockstep pair. Constraints: every disc legal, no six in a game before its last token,
the game that stops at the switch ends with a six on its last token (status 'six') or without one ('open'), the survivor
ends with a six on the record's last token. Depth-first with a node budget; tries 'stopped game ends with a six' first.
Output: per record whether a split exists, both games' lengths and six sides, versus the site's label.
"""
import collections
import sys

from lib import era, load, machine, opp, runs, toks
from merged import is_merged

ROWS, COLS = 15, 30


def six_through(cols, c, r):
    v = cols[c][r]
    for dc, dr in ((1, 0), (0, 1), (1, 1), (1, -1)):
        n = 1
        for sgn in (1, -1):
            k = 1
            while True:
                cc, rr = c + sgn * dc * k, r + sgn * dr * k
                if 0 <= cc < COLS and 0 <= rr < len(cols[cc]) and cols[cc][rr] == v:
                    n += 1; k += 1
                else:
                    break
        if n >= 6:
            return True
    return False


def any_six(cols):
    out = set()
    for c in range(COLS):
        for r in range(len(cols[c])):
            if cols[c][r] not in out and six_through(cols, c, r):
                out.add(cols[c][r])
    return out


def apply(cols, t):
    """Plays token on a copy; returns (cols, sixes) or None if illegal."""
    s, c, st = t
    if not 0 <= c < COLS:
        return None
    if len(cols[c]) >= ROWS:
        return (cols, set()) if st else None
    new = list(cols)
    new[c] = cols[c] + [s]
    r = len(cols[c])
    if not st:
        return new, ({s} if six_through(new, c, r) else set())
    for cc in range(max(0, c - 1), min(COLS, c + 2)):
        col = list(new[cc])
        for rr in sorted((x for x in (r - 1, r, r + 1) if 0 <= x < len(col)), reverse=True):
            del col[rr]
        new[cc] = col
    return new, any_six(new)


def lockstep_end(ts):
    rr = runs(ts)
    pos = 0
    for k, (s, l) in enumerate(rr):
        if all(ll == 1 for _, ll in rr[k:]):
            return pos
        if l != 2:
            return None
        pos += l
    return pos


def solve(x, budget=300000):
    ts = toks(x["moves"])
    cut = lockstep_end(ts)
    if cut is None or cut % 2:
        return {"status": "irregular"}
    pairs = [(ts[k], ts[k + 1]) for k in range(0, cut, 2)]
    tail = ts[cut:]
    empty = tuple([] for _ in range(COLS))
    nodes = 0
    results = []
    for want_stop in ("six", "open"):
        for surv in (0, 1):
            # stack: (pair index, colsA, colsB)
            stack = [(0, list(empty), list(empty))]
            seen = set()
            while stack:
                i, A, B = stack.pop()
                nodes += 1
                if nodes > budget:
                    return {"status": "budget", "nodes": nodes, "cut": cut, "plies": len(ts)}
                last = i == len(pairs) - 1
                if i == len(pairs):
                    continue
                a_tok, b_tok = pairs[i]
                for first, second in ((a_tok, b_tok), (b_tok, a_tok)):
                    if first == second and (first, second) != (a_tok, b_tok):
                        continue
                    ra = apply(A, first); rb = apply(B, second)
                    if ra is None or rb is None:
                        continue
                    (nA, sA), (nB, sB) = ra, rb
                    games = [(nA, sA), (nB, sB)]
                    stop_i = 1 - surv
                    # a six is allowed only on the stopped game's last lockstep token (if want_stop == 'six')
                    bad = False
                    for gi, (cols, sx) in enumerate(games):
                        if sx and not (gi == stop_i and last and want_stop == "six"):
                            bad = True
                    if bad:
                        continue
                    if last:
                        if want_stop == "six" and not games[stop_i][1]:
                            continue
                        # play the tail on the survivor
                        cols = games[surv][0]; ok = True; sx = set()
                        for n, t in enumerate(tail):
                            r = apply(cols, t)
                            if r is None:
                                ok = False; break
                            cols, sx = r
                            if sx and n != len(tail) - 1:
                                ok = False; break
                        if ok and (sx or not tail):
                            if not tail and not games[surv][1]:
                                continue
                            results.append({"status": "ok", "stop": want_stop, "surv": surv,
                                            "stop_six": "".join(sorted(games[stop_i][1])),
                                            "surv_six": "".join(sorted(sx)),
                                            "lens": (len(pairs) + (len(tail) if surv == 0 else 0),
                                                     len(pairs) + (len(tail) if surv == 1 else 0)),
                                            "nodes": nodes, "cut": cut, "plies": len(ts)})
                            return results[0]
                        continue
                    key = (i + 1, tuple(map(tuple, nA)), tuple(map(tuple, nB)))
                    if key in seen:
                        continue
                    seen.add(key)
                    stack.append((i + 1, nA, nB))
    return {"status": "none", "nodes": nodes, "cut": cut, "plies": len(ts)}


def main():
    budget = int(sys.argv[1]) if len(sys.argv) > 1 else 300000
    g = load()
    mixed = [x for x in g if era(x) == "new" and is_merged(x)]
    stat = collections.Counter()
    for x in mixed:
        r = solve(x, budget)
        lab = {"FS"[i]: x["labels"].get(p["lb"]) for i, p in enumerate(x["players"])}
        won = [s for s in "FS" if lab[s] == "Won"]
        won = won[0] if won else "-"
        names = " / ".join(p["name"][:14] for p in x["players"])
        stat[r["status"]] += 1
        if r["status"] == "ok":
            stat[("site label = first finished", won == r["stop_six"])] += 1
            stat[("site label = survivor", won == r["surv_six"])] += 1
            print(f"{names:32s} plies {r['plies']:3d} lockstep {r['cut']:3d} first-finished {r['stop']}:{r['stop_six'] or '-'} "
                  f"survivor six {r['surv_six']} site winner {won} export winner {x['winner']} nodes {r['nodes']}", flush=True)
        else:
            print(f"{names:32s} {r['status']} plies {r.get('plies')} lockstep {r.get('cut')}", flush=True)
    for k, v in sorted(stat.items(), key=str):
        print(k, v)


if __name__ == "__main__":
    main()
