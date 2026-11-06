"""Savi behavioural fingerprint from game logs. Single process, writes tables.md next to this file."""
import collections
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from qclib import *  # noqa

HERE = os.path.dirname(os.path.abspath(__file__))
G = load(os.path.join(HERE, "..", "games.jsonl"))
G.sort(key=lambda g: (g["fetchedAt"], g["id"]))
VERS = ["Savi5", "Savi6", "Savi7", "Savi8", "Savi9", "ours2.1", "ours2.2", "ours3.1"]
BOARDS = ["8x12", "15x11", "15x22"]
OUT = []


def bname(g):
    return f"{g['board']['rows']}x{g['board']['cols']}"


def phase(n):
    return 0 if n <= 20 else (1 if n <= 40 else 2)


PH = ["0-20", "21-40", "41+"]
READY = 9  # counter before the move; min observed counter at a blast is 9 (= the 10th own turn)

# ---------- opponents, weak bots ----------
pair = collections.defaultdict(lambda: [0, 0])  # (a,b) -> [wins of a, losses of a]
tot = collections.defaultdict(lambda: [0, 0])
team_of = {}
for g in G:
    f, s = g["players"]
    team_of[f["name"]] = f["team"]
    team_of[s["name"]] = s["team"]
    w, l = (f, s) if g["winner"] == "F" else (s, f)
    pair[(w["name"], l["name"])][0] += 1
    pair[(l["name"], w["name"])][1] += 1
    tot[w["name"]][0] += 1
    tot[l["name"]][1] += 1
weak = set()
near = set()
weak_rows = []
for nme, (w, l) in tot.items():
    if w + l < 10:
        continue
    worst = 0.0
    ok = True
    for (a, b), (ww, ll) in pair.items():
        if a == nme and ww + ll >= 3 and ww / (ww + ll) > 0.2:
            ok = False
    if w / (w + l) <= 0.2 and ok:
        weak.add(nme)
    elif w / (w + l) <= 0.2:
        near.add(nme)
    weak_rows.append((nme, team_of[nme], w, l, ok))


def opp_class(name):
    t = team_of[name]
    if t == "No beaches?":
        return "ours"
    if t == "Savi":
        return "Savi"
    if name in weak:
        return "weak"
    if name in near:
        return "near-weak"
    return "other"


# ---------- window features ----------
def windows_through(cols, rows, ncols, c, r, me, opp):
    """Yield (mine, opp) counts for every 6-cell in-board segment through (c, r)."""
    for dc, dr in DIRS:
        for s in range(-5, 1):
            x0, y0 = c + dc * s, r + dr * s
            x1, y1 = x0 + dc * 5, y0 + dr * 5
            if not (0 <= x0 < ncols and 0 <= x1 < ncols and 0 <= y0 < rows and 0 <= y1 < rows):
                continue
            m = o = 0
            for i in range(6):
                x, y = x0 + dc * i, y0 + dr * i
                if y < len(cols[x]):
                    if cols[x][y] == me:
                        m += 1
                    else:
                        o += 1
            yield m, o


def canon_pos(cols, side, ready, rows):
    a = ",".join("".join(c) for c in cols)
    b = ",".join("".join(c) for c in reversed(cols))
    return (a if a <= b else b, a > b, a == b, side, ready, rows)


# ---------- main replay ----------
class Acc(collections.defaultdict):
    pass


def new_acc():
    return {
        "games": set(), "moves": 0, "first": collections.Counter(), "dist": [[0, 0.0] for _ in range(3)],
        "stack_opp": [0, 0], "stack_own": [0, 0], "mk": [[0, 0] for _ in range(3)], "kl": [[0, 0] for _ in range(3)],
        "win_av": 0, "win_take": 0, "win_av_drop": 0, "win_av_blast": 0, "win_fail": [],
        "blk_av": 0, "blk_drop": 0, "blk_blast": 0, "blk_other": 0, "blk_fail": [], "blk_multi": 0,
        "ready": 0, "blasts": 0, "cnt_at": collections.Counter(), "blast_ply": collections.Counter(),
        "b_win": 0, "b_save": 0, "b_neither": 0, "b_rm_own": 0, "b_rm_opp": 0, "b_neither_won": 0,
        "b_illegal": 0, "b_early": 0, "pg_blasts": collections.Counter(), "lost": 0, "own_turns": 0,
        "oppcls_moves": collections.Counter(), "oppcls_games": collections.Counter(),
    }


A = collections.defaultdict(new_acc)  # (ver, board)
lines = collections.defaultdict(list)  # (ver, board) -> list of (seat, normalised tokens str, game id, opp)
posrec = collections.defaultdict(lambda: collections.defaultdict(list))  # (ver,board) -> key -> [(reply, opp, gid, ply)]
early_pos = collections.defaultdict(lambda: collections.defaultdict(collections.Counter))  # (ver,board)-> key->Counter(reply)
prefix8 = collections.defaultdict(set)
oppres = collections.defaultdict(lambda: collections.defaultdict(lambda: [0, 0]))  # ver -> opp -> [w,l]
oppres_b = collections.defaultdict(lambda: collections.defaultdict(lambda: [0, 0]))

replay_fail = 0
for g in G:
    rows, ncols = g["board"]["rows"], g["board"]["cols"]
    bn = bname(g)
    toks = parse(g["moves"])
    ok, why, last = check_game(toks, rows, ncols)
    if not (ok and last == g["winner"]):
        replay_fail += 1
        continue
    names = {"F": g["players"][0]["name"], "S": g["players"][1]["name"]}
    teams = {"F": g["players"][0]["team"], "S": g["players"][1]["team"]}
    vers = {x: version_of(names[x], teams[x]) for x in "FS"}
    for x in "FS":
        if vers[x]:
            o = "S" if x == "F" else "F"
            r = oppres[vers[x]][names[o]]
            r[0 if g["winner"] == x else 1] += 1
            r = oppres_b[(vers[x], bn)][names[o]]
            r[0 if g["winner"] == x else 1] += 1
            a = A[(vers[x], bn)]
            a["games"].add(g["id"])
            a["oppcls_games"][opp_class(names[o])] += 1
    cols = [[] for _ in range(ncols)]
    cnt = {"F": 0, "S": 0}
    expected = "F"
    last_col = {"F": None, "S": None}
    ownidx = {"F": 0, "S": 0}
    pg_b = {"F": 0, "S": 0}
    centre = (ncols - 1) / 2
    for n, (s, c, star) in enumerate(toks):
        while expected != s:
            cnt[expected] += 1
            ownidx[expected] += 1
            if vers[expected]:
                A[(vers[expected], bn)]["lost"] += 1
            expected = "S" if expected == "F" else "F"
        o = "S" if s == "F" else "F"
        ver = vers[s]
        ready = cnt[s] >= READY
        a = A[(ver, bn)] if ver else None
        if ver:
            a["own_turns"] += 1
            a["moves"] += 1
            cls = opp_class(names[o])
            a["oppcls_moves"][cls] += 1
            if ownidx[s] == 0 or (n <= 1):
                a["first"][(s, min(c, ncols - 1 - c), star)] += 0  # placeholder, real count below
            ph = phase(n)
            a["dist"][ph][0] += 1
            a["dist"][ph][1] += abs(c - centre)
            if last_col[o] is not None:
                a["stack_opp"][1] += 1
                a["stack_opp"][0] += c == last_col[o]
            if last_col[s] is not None:
                a["stack_own"][1] += 1
                a["stack_own"][0] += c == last_col[s]
            # position before move
            my_drops = [x for x in range(ncols) if drop_wins(cols, rows, ncols, x, s)]
            my_blasts = [x for x in range(ncols) if ready and blast_wins(cols, rows, ncols, x)]
            opp_drops = [x for x in range(ncols) if drop_wins(cols, rows, ncols, x, o)]
            key, mirrored, sym, _, _, _ = canon_pos(cols, s, ready, rows)
            rep = (min(c, ncols - 1 - c) if sym else (ncols - 1 - c if mirrored else c), star)
            posrec[(ver, bn)][key].append((rep, names[o], g["id"], n, s))
            if n <= 20:
                early_pos[(ver, bn)][key][rep] += 1
            if ready:
                a["ready"] += 1
        # apply
        removed = []
        if star:
            okb, removed, win = blast_apply(cols, rows, ncols, c, s)
            if not okb:
                if ver:
                    a["b_illegal"] += 1
                win = False
        else:
            cols[c].append(s)
            win = six_through(cols, ncols, c, len(cols[c]) - 1)
            r_new = len(cols[c]) - 1
        if ver:
            if star:
                a["blasts"] += 1
                pg_b[s] += 1
                a["cnt_at"][cnt[s]] += 1
                a["blast_ply"][min(n // 20, 5)] += 1
                if not ready:
                    a["b_early"] += 1
                a["b_rm_own"] += sum(1 for d in removed if d == s)
                a["b_rm_opp"] += sum(1 for d in removed if d != s)
                if win:
                    a["b_win"] += 1
                else:
                    after = [x for x in range(ncols) if drop_wins(cols, rows, ncols, x, o)]
                    if opp_drops and not after:
                        a["b_save"] += 1
                    else:
                        a["b_neither"] += 1
                        a["b_neither_won"] += g["winner"] == s
            else:
                for k_i, k in enumerate((3, 4, 5)):
                    a["mk"][k_i][1] += 1
                    a["kl"][k_i][1] += 1
                best_m = best_k = 0
                for m, oo in windows_through(cols, rows, ncols, c, r_new, s, o):
                    if oo == 0:
                        best_m = max(best_m, m)
                    if m == 1:
                        best_k = max(best_k, oo)
                for k_i, k in enumerate((3, 4, 5)):
                    a["mk"][k_i][0] += best_m >= k
                    a["kl"][k_i][0] += best_k >= k
            # tactics
            av = bool(my_drops or my_blasts)
            if av:
                a["win_av"] += 1
                a["win_av_drop"] += bool(my_drops)
                a["win_av_blast"] += bool(my_blasts) and not my_drops
                if win:
                    a["win_take"] += 1
                else:
                    a["win_fail"].append(f"{g['id'][:8]}@{n}")
            elif len(opp_drops) == 1:
                a["blk_av"] += 1
                after = [x for x in range(ncols) if drop_wins(cols, rows, ncols, x, o)]
                if not after:
                    if not star and c == opp_drops[0]:
                        a["blk_drop"] += 1
                    elif star:
                        a["blk_blast"] += 1
                    else:
                        a["blk_other"] += 1
                else:
                    a["blk_fail"].append(f"{g['id'][:8]}@{n}")
            elif len(opp_drops) > 1:
                a["blk_multi"] += 1
            a["_dummy"] = 0
        # bookkeeping
        if star:
            cnt[s] = 0
        else:
            cnt[s] += 1
        ownidx[s] += 1
        last_col[s] = c
        expected = o
        if win:
            break
    for x in "FS":
        if vers[x]:
            a = A[(vers[x], bn)]
            a["pg_blasts"][min(pg_b[x], 4)] += 1
    # first moves / lines
    for x in "FS":
        if vers[x]:
            a = A[(vers[x], bn)]
            xs = [t for t in toks if t[0] == x]
            if xs:
                a["first"][(x, min(xs[0][1], ncols - 1 - xs[0][1]), xs[0][2])] += 1
            pre = toks[:20]
            fa = [(t[0], t[1], t[2]) for t in pre]
            fb = [(t[0], ncols - 1 - t[1], t[2]) for t in pre]
            sa = " ".join(f"{t[0]}{t[1]:x}{'*' if t[2] else ''}" for t in fa)
            sb = " ".join(f"{t[0]}{t[1]:x}{'*' if t[2] else ''}" for t in fb)
            nm = sa if (sa, ) <= (sb, ) else sb
            lines[(vers[x], bn)].append((x, nm, g["id"], names["S" if x == "F" else "F"]))
            prefix8[(vers[x], bn, x)].add(" ".join(nm.split()[:8]))

# remove placeholder zero counters
for a in A.values():
    for k in [k for k, v in a["first"].items() if v == 0]:
        del a["first"][k]


# ---------- output helpers ----------
def pct(a, b):
    return "-" if b == 0 else f"{100 * a / b:.0f}%"


def table(title, header, rows_):
    OUT.append(f"\n### {title}\n")
    OUT.append("| " + " | ".join(header) + " |")
    OUT.append("|" + "---|" * len(header))
    for r in rows_:
        OUT.append("| " + " | ".join(str(x) for x in r) + " |")


def each():
    for b in BOARDS:
        for v in VERS:
            if (v, b) in A and A[(v, b)]["moves"]:
                yield v, b, A[(v, b)]


# 0 coverage
table("Coverage", ["version", "board", "games", "own moves", "lost turns"],
      [(v, b, len(a["games"]), a["moves"], a["lost"]) for v, b, a in each()])

# 1 openings
rows_ = []
for v, b, a in each():
    for seat in "FS":
        c = collections.Counter({k[1:]: n for k, n in a["first"].items() if k[0] == seat})
        tot_ = sum(c.values())
        if tot_:
            rows_.append((v, b, seat, tot_, ", ".join(f"{col}{'*' if st else ''}:{n}" for (col, st), n in c.most_common(5))))
table("First own move per seat (column folded to distance from the nearer edge, 0 = edge; count after colon)",
      ["version", "board", "seat", "n", "top first moves"], rows_)

rows_ = []
for v, b, a in each():
    ls = lines[(v, b)]
    for L in (6, 10, 20):
        pass
    n = len(ls)
    d6 = len({" ".join(x[1].split()[:6]) for x in ls})
    d10 = len({" ".join(x[1].split()[:10]) for x in ls})
    d20 = len({x[1] for x in ls})
    cm = collections.Counter(x[1] for x in ls).most_common(2)
    rows_.append((v, b, n, d6, d10, d20, "; ".join(f"{k} (x{m})" for k, m in cm if m > 1) or "no repeat"))
table("Distinct opening lines (mirror-folded, both seats' moves, first 20 plies; 'n' = Savi seat-games)",
      ["version", "board", "n", "distinct 6 plies", "distinct 10", "distinct 20", "repeated 20-ply lines"], rows_)

# repeat positions
rows_ = []
for v, b, a in each():
    pr = posrec[(v, b)]
    st = [[0, 0, 0, 0, 0, 0, 0] for _ in range(3)]  # moves, seen_before, same_first, same_any, same_opp_n, same_opp_same, diff_opp_n -> extended below
    stt = [collections.Counter() for _ in range(3)]
    for key, lst in pr.items():
        for i, (rep, opp, gid, n, s) in enumerate(lst):
            ph = phase(n)
            stt[ph]["moves"] += 1
            prev = [p for p in lst[:i] if p[2] != gid]
            if not prev:
                continue
            stt[ph]["seen"] += 1
            stt[ph]["same_first"] += rep == prev[0][0]
            stt[ph]["same_any"] += any(rep == p[0] for p in prev)
            po = [p for p in prev if p[1] == opp]
            pd = [p for p in prev if p[1] != opp]
            if po:
                stt[ph]["so_n"] += 1
                stt[ph]["so_same"] += rep == po[-1][0]
            elif pd:
                stt[ph]["do_n"] += 1
                stt[ph]["do_same"] += rep == pd[-1][0]
    cells = []
    for ph in range(3):
        c = stt[ph]
        cells.append(f"{c['seen']}/{c['moves']} seen; first-reply same {pct(c['same_first'], c['seen'])}; any-prev same {pct(c['same_any'], c['seen'])}; "
                     f"same-opp {pct(c['so_same'], c['so_n'])} (n={c['so_n']}), only-other-opp {pct(c['do_same'], c['do_n'])} (n={c['do_n']})")
    rows_.append((v, b, *cells))
table("Repeated positions: same reply as an earlier game (position = board + side to move + blast-ready, mirror-folded, within version and board)",
      ["version", "board", "plies 0-20", "21-40", "41+"], rows_)

# carry-over
rows_ = []
order = ["Savi5", "Savi6", "Savi7", "Savi8", "Savi9"]
for b in BOARDS:
    for i in range(len(order)):
        for j in range(i + 1, len(order)):
            X, Y = order[i], order[j]
            if j - i > 1 and not (X == "Savi5" and Y == "Savi9"):
                continue
            ex, ey = early_pos.get((X, b)), early_pos.get((Y, b))
            if not ex or not ey:
                continue
            tot_y = sum(sum(c.values()) for c in ey.values())
            hit = same = 0
            for key, c in ey.items():
                if key in ex:
                    nm = sum(c.values())
                    hit += nm
                    same += sum(n for r, n in c.items() if r in ex[key])
            p8 = sum(1 for seat in "FS" for l in prefix8.get((Y, b, seat), ()) if l in prefix8.get((X, b, seat), ()))
            p8n = sum(len(prefix8.get((Y, b, seat), ())) for seat in "FS")
            rows_.append((b, f"{X} -> {Y}", tot_y, f"{hit} ({pct(hit, tot_y)})", f"{same} ({pct(same, hit)})", f"{p8}/{p8n}"))
table("Carry-over of early (ply<=20) positions from older version X to newer Y (Y position seen in X; reply is one X also used)",
      ["board", "pair", "Y early moves", "position also in X", "reply also used by X", "distinct 8-ply lines of Y also in X"], rows_)

# 2 blasts
rows_ = []
for v, b, a in each():
    ng = len(a["games"]) * 1
    pgs = sum(a["pg_blasts"].values())
    cnts = ", ".join(f"{k}:{n}" for k, n in sorted(a["cnt_at"].items())[:8])
    bp = ", ".join(f"{k * 20}-{k * 20 + 19}:{n}" if k < 5 else f"100+:{n}" for k, n in sorted(a["blast_ply"].items()))
    nb = a["blasts"]
    rows_.append((v, b, pgs, nb, f"{nb / pgs:.2f}" if pgs else "-", f"{pct(sum(n for k, n in a['pg_blasts'].items() if k > 0), pgs)}",
                  a["ready"], pct(nb, a["ready"]), cnts, bp))
table("Blasts: counts, own-turn counter when blasting, readiness use, ply of blast (starting ply of 20-ply bucket)",
      ["version", "board", "player-games", "blasts", "per game", "games with >=1", "ready turns", "blast share of ready", "counter at blast", "ply buckets"], rows_)
rows_ = []
for v, b, a in each():
    nb = a["blasts"]
    nw = nb - a["b_win"]
    rows_.append((v, b, nb, a["b_win"], a["b_save"], a["b_neither"], f"{a['b_rm_own'] / nb:.1f}" if nb else "-", f"{a['b_rm_opp'] / nb:.1f}" if nb else "-",
                  f"{a['b_neither_won']}/{a['b_neither']} ({pct(a['b_neither_won'], a['b_neither'])})", a["b_early"], a["b_illegal"]))
table("Blast purpose: wins at once / removes the opponent's immediate drop win / neither; mean discs removed per blast (own, opponent); game won after a 'neither' blast",
      ["version", "board", "blasts", "win now", "removes opp win", "neither", "own removed", "opp removed", "'neither' blasts then won game", "blast with counter<9", "blast in full column"], rows_)

# 3 shape
rows_ = []
for v, b, a in each():
    d = [f"{a['dist'][i][1] / a['dist'][i][0]:.2f} (n={a['dist'][i][0]})" if a["dist"][i][0] else "-" for i in range(3)]
    rows_.append((v, b, *d, f"{pct(*a['stack_opp'])} (n={a['stack_opp'][1]})", f"{pct(*a['stack_own'])} (n={a['stack_own'][1]})"))
table("Column distance from centre by ply phase (mean, columns), and stacking share (same column as opponent's last / own last move)",
      ["version", "board", "0-20", "21-40", "41+", "on opp last", "on own last"], rows_)
rows_ = []
for v, b, a in each():
    n = a["mk"][0][1]
    rows_.append((v, b, n, *[pct(*a["mk"][i]) for i in range(3)], *[pct(*a["kl"][i]) for i in range(3)]))
table("Drop moves only: share making a window (6 cells, no opponent disc, through the new disc) with >=3/4/5 own discs; share killing an opponent window (no own disc before) with >=3/4/5 opponent discs",
      ["version", "board", "drops", "make>=3", "make>=4", "make>=5", "kill>=3", "kill>=4", "kill>=5"], rows_)

# 4 tactics
rows_ = []
for v, b, a in each():
    rows_.append((v, b, a["win_av"], f"{a['win_av_drop']}/{a['win_av_blast']}", f"{a['win_take']} ({pct(a['win_take'], a['win_av'])})",
                  ", ".join(a["win_fail"][:6]) or "-"))
table("Immediate win available (drop / blast-only): taken?",
      ["version", "board", "positions", "drop/blast-only", "taken", "missed (game@ply)"], rows_)
rows_ = []
for v, b, a in each():
    rows_.append((v, b, a["blk_av"], f"{a['blk_drop']}/{a['blk_blast']}/{a['blk_other']}",
                  f"{a['blk_av'] - len(a['blk_fail'])} ({pct(a['blk_av'] - len(a['blk_fail']), a['blk_av'])})", a["blk_multi"],
                  ", ".join(a["blk_fail"][:6]) or "-"))
table("Opponent has exactly one immediate drop win and mover has none: dealt with? (drop in that column / blast / other)",
      ["version", "board", "positions", "drop/blast/other", "threat gone after move", "positions with 2+ opp wins (not scored)", "failures (game@ply)"], rows_)

# 5 opponents
rows_ = []
for v in VERS:
    if v.startswith("Savi") or v.startswith("ours"):
        d = oppres[v]
        w = sum(x[0] for x in d.values())
        l = sum(x[1] for x in d.values())
        top = sorted(d.items(), key=lambda kv: -(kv[1][0] + kv[1][1]))
        rows_.append((v, f"{w}-{l}", "; ".join(f"{k} {x[0]}-{x[1]}" for k, x in top)))
table("Opponents per version (W-L of that version, all boards)", ["version", "W-L", "opponents (W-L)"], rows_)
rows_ = []
for v in VERS:
    for b in BOARDS:
        a = A.get((v, b))
        if a and a["moves"]:
            c = a["oppcls_moves"]
            t = sum(c.values())
            rows_.append((v, b, t, *[f"{c[k]} ({pct(c[k], t)})" for k in ("ours", "Savi", "weak", "near-weak", "other")]))
table("Own moves by opponent class", ["version", "board", "moves", "vs our team", "vs Savi line", "vs weak", "vs near-weak", "vs other"], rows_)
table("Weak-bot test: all bots with >=10 games, overall win rate and whether every pairing with >=3 games has <=20% wins (weak = overall <=20% and every such pairing <=20%)",
      ["bot", "team", "W", "L", "win%", "every pairing<=20%", "weak"],
      sorted([(n, t, w, l, pct(w, w + l), ok, n in weak) for n, t, w, l, ok in weak_rows], key=lambda r: int(r[4].rstrip('%')) if r[4] != '-' else 0))

# win rates by version and board
rows_ = []
for v in VERS:
    for b in BOARDS:
        d = oppres_b.get((v, b))
        if d:
            w = sum(x[0] for x in d.values())
            l = sum(x[1] for x in d.values())
            rows_.append((v, b, w + l, f"{w}-{l}"))
table("Results per version and board", ["version", "board", "games", "W-L"], rows_)

OUT.insert(0, "Replay check: %d games, failures: %d.  Weak: %s.  Near-weak: %s.  Ready = counter >= 9 before the move." % (len(G), replay_fail, sorted(weak), sorted(near)))
with open(os.path.join(HERE, "tables.md"), "w", encoding="utf-8") as f:
    f.write("\n".join(OUT))
print("done", replay_fail, sorted(weak))
