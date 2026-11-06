"""Server-game analysis by board size: replay check, W/L matrices, first-player edge, game length, blasts,
opening habits, how games end, our losses and timeouts.

Usage: python -I qc_analysis.py MATCHES_JSONL OUT_DIR

Writes server-analysis-by-size.md and our-losses-by-size.md into OUT_DIR. Games are de-duplicated on
(first player, second player, move string): the site replays identical deterministic games under new ids.
All rules come from qc_replay.py (mirror of Board.java).
"""
import collections
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qc_replay as q  # noqa: E402

OURS_TEAM = "No beaches?"
SAVI_TEAM = "Savi"
SIZE_ORDER = ["8x12", "15x22", "15x11"]
CENTRE = {"8x12": (5, 6), "15x22": (10, 11), "15x11": (5, 5)}
COLNAME = "0123456789abcdefghijklmnopqrstuvwxyz"

SHORT = {
    'Savi5 beater 2.1': 'ours 2.1',
    'Savi beater 2.2': 'ours 2.2',
    'Savi5': 'Savi5',
    'Savi6 - "Beat this"': 'Savi6',
    'Savi6 - "Beat This"': 'Savi6b',
    'Savi7 - "The Prince"': 'Savi7',
    'Savi 7 - "The Prince': 'Savi7b',
    'Savi8 - "The King"': 'Savi8',
    'FunkyMelodyBot': 'Funky',
    'Hurt me plenty': 'HurtMe',
    'Strange Quark 1': 'StrangeQ',
    'specialmovedestroyer': 'SpecialMD',
    'HereWeGoAgain': 'HereWeGoAg',
    'lalalalabot': 'lalalala',
    'lalalabot': 'lalala',
    'Testimine-M7': 'TestimineM7',
    'Back To Basics': 'BackToBas',
    'Testing_v2': 'Testing_v2',
    'BeetaSaag': 'BeetaSaag',
    'AntiStrat_FINAL': 'AntiStrat',
    "I'm too young to die": 'TooYoung',
    'sleep tight': 'sleepT',
    'good night': 'goodN',
}


def sh(name):
    return SHORT.get(name, name if len(name) < 14 else name[:12] + "..")


def b36(c):
    return COLNAME[c]


def pct(a, b):
    return "-" if b == 0 else "%.1f%%" % (100.0 * a / b)


def pctn(a, b):
    return "-" if b == 0 else "%.0f%%" % (100.0 * a / b)


def spread(v):
    if not v:
        return "-"
    s = sorted(v)
    n = len(s)
    med = s[n // 2] if n % 2 else (s[n // 2 - 1] + s[n // 2]) / 2
    p90 = s[min(n - 1, int(0.9 * n + 0.999999) - 1)]
    return "%d/%s/%d/%d" % (s[0], ("%g" % med), p90, s[-1])


def mean(v):
    return sum(v) / len(v) if v else 0.0


def median(v):
    if not v:
        return 0
    s = sorted(v)
    n = len(s)
    return s[n // 2] if n % 2 else (s[n // 2 - 1] + s[n // 2]) / 2


def table(head, rows):
    out = ["| " + " | ".join(head) + " |", "|" + "---|" * len(head)]
    for r in rows:
        out.append("| " + " | ".join(str(x) for x in r) + " |")
    return "\n".join(out) + "\n"


# ------------------------------------------------------------------ per game facts

class G:
    pass


def analyse_game(game):
    """Replay with a hook that collects threat facts. Returns G."""
    g = G()
    g.raw = game
    g.id = game["id"]
    g.names = [p["name"] for p in game["players"]]
    g.teams = [p["team"] for p in game["players"]]
    g.winner = 1 if game["winner"] == "F" else 2
    g.timeouts = game["log"]["timeouts"]
    g.full = game["log"]["full"]
    info = {}

    def hook(phase, rec, cols, res):
        side = rec["side"]
        near_end = rec["ply"] > res.total - 30
        if phase == "before" and (rec["star"] or near_end):
            rows, ncols = res.size
            rec["wm"] = q.winning_columns(cols, rows, ncols, side)
            rec["wo"] = q.winning_columns(cols, rows, ncols, 3 - side)
        if phase == "before" and rec["star"]:
            rec["pre"] = three_col_counts(cols, rec["col"])
        if phase == "after" and rec["star"]:
            post = three_col_counts(cols, rec["col"])
            rec["rm_own"] = rec["pre"][side] - post[side]
            rec["rm_opp"] = rec["pre"][3 - side] - post[3 - side]
            rows, ncols = res.size
            rec["wm2"] = q.winning_columns(cols, rows, ncols, side)
            rec["wo2"] = q.winning_columns(cols, rows, ncols, 3 - side)
            if rec["win"]:
                rec["colours"] = q.blast_line_colours(cols, ncols, rec["col"])
        if phase == "after" and rec["win"] and not rec["star"]:
            info["height"] = len(cols[rec["col"]]) - 1
            info["maxh"] = max(len(c) for c in cols)
        if phase == "after" and rec["ply"] == 30:
            c0, c1 = CENTRE["%dx%d" % res.size]
            prof = {}
            for colour in (1, 2):
                tot = cen = floor = low = 0
                for x, col in enumerate(cols):
                    for y, v in enumerate(col):
                        if v == colour:
                            tot += 1
                            cen += 1 if c0 - 1 <= x <= c1 + 1 else 0
                            floor += 1 if y == 0 else 0
                            low += 1 if y <= 1 else 0
                prof[colour] = (tot, cen, floor, low)
            info["p30"] = prof

    res, rec_ok = q.replay_any(game, True, hook)
    g.res = res
    g.ok = res.ok
    g.size = q.size_label(res.size)
    g.rec_ok = rec_ok
    g.info = info
    g.plies = res.plies
    g.reason = res.reason
    return g


def three_col_counts(cols, c):
    cnt = {1: 0, 2: 0}
    for n in range(max(c - 1, 0), min(c + 1, len(cols) - 1) + 1):
        for v in cols[n]:
            cnt[v] += 1
    return cnt


def load_games(path):
    raw = q.load(path)
    seen = {}
    dup = 0
    for r in raw:
        key = (r["players"][0]["name"], r["players"][1]["name"], r["moves"])
        if key in seen:
            dup += 1
            continue
        seen[key] = r
    games = [analyse_game(r) for r in seen.values()]
    return raw, games, dup


# ------------------------------------------------------------------ bot statistics

def team_of(g, side):
    return g.teams[side - 1]


def role(name, team):
    if team == OURS_TEAM:
        return "ours"
    if team == SAVI_TEAM:
        return "savi"
    return "other"


class Bot:
    def __init__(self, name, team):
        self.name = name
        self.team = team
        self.w = self.l = 0
        self.fw = self.fl = self.sw = self.sl = 0
        self.six_w = self.blast_w = 0
        self.plies_w = []
        self.plies_l = []
        self.plies = []
        self.blasts = 0
        self.blast_avail = 0
        self.blast_at_counter = collections.Counter()
        self.blast_own = []
        self.first_blast_own = []
        self.blast_wins = 0
        self.blast_threat = 0
        self.blast_defence = 0
        self.blast_kill_threat_only = 0
        self.blast_colours = collections.Counter()
        self.timeouts = 0
        self.timeout_games = 0
        self.rm_own = self.rm_opp = self.rm_n = self.rm_opp_more = self.rm_big = 0
        self.p30 = [0, 0, 0, 0, 0]
        self.to_per_game = []
        self.own_turns = 0
        self.games_with_blast = 0
        self.vs = collections.defaultdict(lambda: [0, 0])


def collect(games, key_size=None):
    bots = {}
    for g in games:
        if key_size and g.size != key_size:
            continue
        for side in (1, 2):
            name = g.names[side - 1]
            b = bots.setdefault(name, Bot(name, g.teams[side - 1]))
            won = g.winner == side
            opp = g.names[2 - side]
            b.vs[opp][0 if won else 1] += 1
            if won:
                b.w += 1
                if side == 1:
                    b.fw += 1
                else:
                    b.sw += 1
                if g.reason == "blast":
                    b.blast_w += 1
                else:
                    b.six_w += 1
                b.plies_w.append(g.plies)
            else:
                b.l += 1
                if side == 1:
                    b.fl += 1
                else:
                    b.sl += 1
                b.plies_l.append(g.plies)
            b.plies.append(g.plies)
            to = g.timeouts.count("FS"[side - 1])
            b.timeouts += to
            b.timeout_games += 1 if to else 0
            b.to_per_game.append(to)
            p30 = g.info.get("p30")
            if p30:
                t, c, f, lo = p30[side]
                b.p30[0] += 1
                b.p30[1] += t
                b.p30[2] += c
                b.p30[3] += f
                b.p30[4] += lo
            b.own_turns += g.res.own[side]
            mine = [m for m in g.res.moves if m["side"] == side and m["star"]]
            b.blasts += len(mine)
            n_own = g.res.own[side]
            b.blast_avail += 0 if n_own < 11 else 1 + (n_own - 11) // 10
            b.games_with_blast += 1 if mine else 0
            first = True
            for m in mine:
                b.rm_n += 1
                b.rm_own += m["rm_own"]
                b.rm_opp += m["rm_opp"]
                b.rm_opp_more += 1 if m["rm_opp"] > m["rm_own"] else 0
                b.rm_big += 1 if m["rm_opp"] + m["rm_own"] >= 6 else 0
                b.blast_at_counter[m["counter"]] += 1
                b.blast_own.append(m["own"])
                if first:
                    b.first_blast_own.append(m["own"])
                    first = False
                if m["win"]:
                    b.blast_wins += 1
                    cols = m.get("colours", set())
                    b.blast_colours["own" if cols == {side} else "opp" if cols == {3 - side} else "both"] += 1
                else:
                    if m.get("wm2"):
                        b.blast_threat += 1
                    if m.get("wo") and not m.get("wo2"):
                        b.blast_defence += 1
                    elif m.get("wo") and m.get("wo2") is not None and len(m["wo2"]) < len(m["wo"]):
                        b.blast_kill_threat_only += 1
    return bots


def order_bots(bots, min_games=1):
    return sorted((b for b in bots.values() if b.w + b.l >= min_games), key=lambda b: (-(b.w + b.l), b.name))


# ------------------------------------------------------------------ report sections

def section_replay(raw, games, dup):
    out = ["## 1. Replay check by board size\n"]
    out.append("Rules mirrored from `Board.java`: gravity; a normal drop wins with a six of the mover's colour through the "
               "new disc; a blast (`*`) is played at the landing cell (column height h), the placed disc vanishes, rows "
               "h-1..h+1 of columns c-1..c+1 are cleared (in column c only the top disc at h-1) and the discs above fall; "
               "any six of EITHER colour touching those three columns then wins for the mover. A blast needs a per-side "
               "cooldown counter >= 10 (every own turn counts, skipped ones included).\n")
    out.append("Blast centre: the 3x3 block is centred on the cell where the blasting disc would have landed (just above "
               "the column's top disc), not on the top disc. Column c therefore loses exactly its top disc, the "
               "neighbouring columns lose their discs in rows h-1, h, h+1.\n")
    bys = collections.OrderedDict((s, [0, 0, 0, 0, 0, 0]) for s in SIZE_ORDER)
    for g in games:
        c = bys[g.size]
        c[0] += 1
        c[1] += 1 if g.ok else 0
        c[2] += 1 if g.rec_ok and g.ok else 0
        c[3] += 1 if g.raw["board"].get("source") == "page" else 0
    allraw = collections.Counter("%dx%d" % (r["board"]["rows"], r["board"]["cols"]) for r in raw)
    rows = []
    for s in SIZE_ORDER:
        c = bys[s]
        rows.append([s, allraw[s], c[0], c[1], c[2], c[3], c[0] - c[3]])
    tot = [sum(allraw.values()), len(games), sum(1 for g in games if g.ok), sum(1 for g in games if g.ok and g.rec_ok),
           sum(1 for g in games if g.raw["board"].get("source") == "page"), 0]
    rows.append(["all", tot[0], tot[1], tot[2], tot[3], tot[4], tot[1] - tot[4]])
    out.append(table(["Size (rows x cols)", "Games in file (ids)", "Unique games", "Replay consistent",
                      "On the recorded size", "Size read from the page", "Size inferred by exporter"], rows))
    out.append("%d of %d unique games replay consistently on their recorded board (recorded winner = replay winner, line "
               "exactly at `win_at`, no earlier line, no disc into a full column, no blast before the cooldown); %d further "
               "ids are identical replays (same two bots, same moves) and were dropped. The old hex/8x12-only tool "
               "reported 410 of these 1020 unique games as inconsistent.\n" % (tot[2], tot[1], dup))
    return "\n".join(out)


def section_size(size, games, bots_all):
    gs = [g for g in games if g.size == size]
    out = ["### %s\n" % size]
    n = len(gs)
    fw = sum(1 for g in gs if g.winner == 1)
    clean = [g for g in gs if not g.timeouts and not g.full]
    cfw = sum(1 for g in clean if g.winner == 1)
    blast = sum(1 for g in gs if g.reason == "blast")
    plies = [g.plies for g in gs]
    out.append("%d unique games. First player (F) won %d (%s); in the %d games with no timeout or full-column event F won "
               "%d (%s). Decided by a blast: %d (%s), by a normal six: %d. Plies min/median/p90/max = %s, mean %.1f.\n" % (
                   n, fw, pct(fw, n), len(clean), cfw, pct(cfw, len(clean)), blast, pct(blast, n), n - blast, spread(plies), mean(plies)))
    # length buckets
    buckets = collections.Counter()
    for p in plies:
        buckets[min(p // 40, 8)] += 1
    rows = []
    for k in range(9):
        lo = k * 40
        label = "%d-%d" % (lo + 1, lo + 40) if k < 8 else "321+"
        rows.append([label, buckets[k], pct(buckets[k], n), "#" * int(round(40.0 * buckets[k] / max(1, n)))])
    out.append("Game length (plies):\n")
    out.append(table(["Plies", "Games", "Share", ""], rows))
    # F win rate by colour pairing of roles
    rr = collections.defaultdict(lambda: [0, 0])
    for g in gs:
        r1 = role(g.names[0], g.teams[0])
        r2 = role(g.names[1], g.teams[1])
        rr[(r1, r2)][0 if g.winner == 1 else 1] += 1
    rows = [[a + " (F) vs " + b + " (S)", w + l, w, pct(w, w + l)] for (a, b), (w, l) in sorted(rr.items())]
    out.append("First-player win rate by pairing of team roles (savi = team Savi, ours = No beaches?, other):\n")
    out.append(table(["Pairing", "Games", "F won", "F win rate"], rows))
    # per bot
    bots = collect(games, size)
    rows = []
    for b in order_bots(bots):
        tot = b.w + b.l
        rows.append([sh(b.name), tot, "%d/%d" % (b.w, b.l), pct(b.w, tot), "%d/%d" % (b.fw, b.fl), "%d/%d" % (b.sw, b.sl),
                     "%d/%d" % (b.six_w, b.blast_w), spread(b.plies_w), spread(b.plies_l) if b.plies_l else "-",
                     b.timeouts])
    out.append("Per bot (W/L; its wins by six/blast; plies of its wins and its losses as min/median/p90/max; own timeouts):\n")
    out.append(table(["Bot", "Games", "W/L", "Win %", "As F W/L", "As S W/L", "Wins six/blast", "Plies in wins",
                      "Plies in losses", "Own timeouts"], rows))
    # matrix among bots with >= 12 games in this size
    mb = [b for b in order_bots(bots) if b.w + b.l >= 12]
    rows = []
    for i, a in enumerate(mb):
        row = [i + 1, sh(a.name)]
        for j, c in enumerate(mb):
            if i == j:
                row.append(".")
            else:
                w, l = a.vs[c.name]
                row.append("" if w + l == 0 else "%d-%d" % (w, l))
        rows.append(row)
    out.append("Head to head, row bot wins-losses against column bot (both colours; bots with >= 12 games here):\n")
    out.append(table(["#", "Bot"] + [str(i + 1) for i in range(len(mb))], rows))
    return "\n".join(out)


def section_blasts(games):
    out = ["## 3. Blast usage\n"]
    out.append("A blast is available from the 11th own turn on (cooldown counter 10; skipped turns count) and again 10 own "
               "turns after each blast, so n own turns allow at most 1 + floor((n-11)/10) blasts (0 if n < 11). 'Used/avail' "
               "compares the blasts played with that maximum. 'At counter 10' = share of blasts played the first moment they were "
               "allowed. 'Wins' = blasts that ended the game; 'threat' = non-winning blasts after which the blaster has an "
               "immediate winning drop; 'defence' = blasts after which the opponent's immediate winning drops are gone "
               "(opponent had one before). Colour of the winning line: own = only the blaster's colour, opp = only the "
               "opponent's (a colour-blind win), both.\n")
    for size in SIZE_ORDER:
        bots = collect(games, size)
        rows = []
        for b in order_bots(bots):
            if b.w + b.l < 12:
                continue
            tot = b.w + b.l
            at10 = b.blast_at_counter.get(10, 0)
            rows.append([sh(b.name), tot, b.blasts, "%.2f" % (b.blasts / tot), "%s" % pctn(b.blasts, b.blast_avail),
                         pctn(at10, b.blasts), ("%g" % median(b.first_blast_own)) if b.first_blast_own else "-",
                         ("%g" % median(b.blast_own)) if b.blast_own else "-", b.blast_wins, b.blast_threat, b.blast_defence,
                         "%d/%d/%d" % (b.blast_colours["own"], b.blast_colours["opp"], b.blast_colours["both"]),
                         "%.1f / %.1f" % (b.rm_own / b.rm_n, b.rm_opp / b.rm_n) if b.rm_n else "-",
                         pctn(b.rm_opp_more, b.rm_n), pctn(b.rm_big, b.rm_n), pctn(b.blast_wins + b.blast_threat, b.blasts)])
        out.append("### %s\n" % size)
        out.append(table(["Bot", "Games", "Blasts", "Per game", "Used/avail", "At counter 10", "Median own turn of 1st blast",
                          "Median own turn (all)", "Wins", "Threat after", "Defence", "Win colour own/opp/both",
                          "Discs removed own / opp per blast", "Blasts removing more opp than own",
                          "Blasts removing >= 6 discs", "Blasts that won or left a threat"], rows))
    return "\n".join(out)


def opening_stats(games, size, name, side, k=3):
    """Distribution of the column of own move 1..k as side."""
    cnt = [collections.Counter() for _ in range(k)]
    n = 0
    for g in games:
        if g.size != size or g.names[side - 1] != name:
            continue
        n += 1
        own = [m for m in g.res.moves if m["side"] == side][:k]
        for i, m in enumerate(own):
            cnt[i][b36(m["col"]) + ("*" if m["star"] else "")] += 1
    return n, cnt


def fmt_dist(c, n, top=3):
    return ", ".join("%s %s" % (k, pctn(v, n)) for k, v in c.most_common(top))


def section_openings(games):
    out = ["## 4. Openings and habits of the Savi line and the other leading bots\n"]
    out.append("Columns are base-36 digits; centre columns: 8x12 = 5/6, 15x22 = a/b (10/11), 15x11 = 5. 'Own move i' = the "
               "column of the bot's i-th own move (as F these are plies 1, 3, 5, as S plies 2, 4, 6).\n")
    leaders = ['Savi8 - "The King"', 'Savi7 - "The Prince"', 'Savi 7 - "The Prince', 'Savi6 - "Beat this"', 'Savi5',
               'Savi5 beater 2.1', 'Savi beater 2.2', 'Testing_v2', 'lalalalabot', 'specialmovedestroyer']
    for size in SIZE_ORDER:
        rows = []
        for name in leaders:
            for side in (1, 2):
                n, cnt = opening_stats(games, size, name, side)
                if n < 4:
                    continue
                rows.append([sh(name), "F" if side == 1 else "S", n, fmt_dist(cnt[0], n), fmt_dist(cnt[1], n), fmt_dist(cnt[2], n)])
        out.append("### %s\n" % size)
        out.append(table(["Bot", "Side", "Games", "Own move 1", "Own move 2", "Own move 3"], rows))
    # stacking habit: share of own moves (first 24 plies) played in the same column as the opponent's previous move
    out.append("### Stacking and centre habits (first 24 plies)\n")
    out.append("'Same col as opp' = share of the bot's own moves that go into the column the opponent just played in; "
               "'centre' = share of own moves within one column of the centre; 'mean height' = average stack height "
               "at the end over all columns of the decisive position (fill of the board).\n")
    rows = []
    for size in SIZE_ORDER:
        for name in leaders:
            same = tot = cen = 0
            for g in games:
                if g.size != size:
                    continue
                for side in (1, 2):
                    if g.names[side - 1] != name:
                        continue
                    mv = g.res.moves[:24]
                    for i, m in enumerate(mv):
                        if m["side"] != side or m["star"]:
                            continue
                        tot += 1
                        if i > 0 and mv[i - 1]["side"] != side and mv[i - 1]["col"] == m["col"]:
                            same += 1
                        c0, c1 = CENTRE[size]
                        if c0 - 1 <= m["col"] <= c1 + 1:
                            cen += 1
            if tot >= 40:
                rows.append([size, sh(name), tot, pctn(same, tot), pctn(cen, tot)])
    out.append(table(["Size", "Bot", "Own moves", "Same col as opp's last move", "Within 1 of centre"], rows))
    out.append("### Board shape at ply 30\n")
    out.append("Discs of each bot after 30 plies (games that lasted that long): 'central' = discs within one column of the "
               "centre, 'floor' = discs on row 0, 'rows 0-1' = discs on the two lowest rows, per bot-game (blasts have already "
               "thinned the board).\n")
    rows = []
    for size in SIZE_ORDER:
        bots = collect(games, size)
        for b in order_bots(bots):
            if b.p30[0] >= 12 and (b.team in (SAVI_TEAM, OURS_TEAM) or b.p30[0] >= 25):
                n = b.p30[0]
                rows.append([size, sh(b.name), n, "%.1f" % (b.p30[1] / n), pctn(b.p30[2], b.p30[1]), pctn(b.p30[3], b.p30[1]),
                             pctn(b.p30[4], b.p30[1])])
    out.append(table(["Size", "Bot", "Bot-games", "Discs", "Central", "Floor", "Rows 0-1"], rows))
    return "\n".join(out)


def section_branch(games):
    out = ["### Common 12-ply start on 15x11 and where it branches\n"]
    out.append("Many strong 15x11 games begin with the centre column filled alternately (F5 S5 six times, 12 plies). "
               "The table groups the games that began that way by the next pair of tokens (plies 13-14) and shows the "
               "result for the bot group shown (W-L of that group in that colour).\n")
    res = collections.defaultdict(lambda: [0, 0])
    for g in games:
        if g.size != "15x11" or plies_13_14(g) == "-":
            continue
        nxt = plies_13_14(g)
        for side in (1, 2):
            grp = role(g.names[side - 1], g.teams[side - 1])
            if grp == "other":
                continue
            res[(grp, "F" if side == 1 else "S", nxt)][0 if g.winner == side else 1] += 1
    rows = []
    for (grp, side, nxt), (w, l) in sorted(res.items()):
        rows.append([grp, side, nxt, w, l])
    out.append(table(["Group", "Colour", "Plies 13-14", "Wins", "Losses"], rows))
    return "\n".join(out)


def classify_finish(g):
    """How the game ended, from the loser's last position. Returns (category, detail)."""
    res = g.res
    mv = res.moves
    last = mv[-1]
    w = last["side"]
    if last["star"]:
        cols = last.get("colours", set())
        kind = "own colour" if cols == {w} else "opponent colour only" if cols == {3 - w} else "both colours"
        return "blast (%s)" % kind, ""
    if len(mv) < 2:
        return "immediate", ""
    prev = mv[-2]
    if prev["side"] == w:
        return "loser skipped its last turn", ""
    # position facing the loser before its last move
    wo = prev.get("wo")
    if wo is None:
        return "?", ""
    if prev["star"]:
        return "after loser's blast", ""
    if len(wo) >= 2:
        return "double threat (>=2 winning columns)", ",".join(b36(c) for c in wo)
    if len(wo) == 1:
        if prev["col"] == wo[0]:
            return "blocked the single threat, cell above won", b36(wo[0])
        return "single threat not blocked", b36(wo[0])
    return "loser's move opened the winning cell", b36(prev["col"])


def section_finish(games):
    out = ["## 5. How games end and how fast the Savi bots win\n"]
    out.append("Finish categories are read from the position the loser faced on its last move: 'double threat' = the winner "
               "had >= 2 distinct immediately winning columns; 'blocked the single threat, cell above won' = the loser "
               "dropped on the only winning column and the cell above it won (stacked threat); 'single threat not "
               "blocked'; 'loser's move opened the winning cell' = no immediate threat existed, the loser's drop gave "
               "the cell above to the winner; 'loser skipped its last turn' = the winner moved twice in a row "
               "(timeout/full column).\n")
    for size in SIZE_ORDER:
        gs = [g for g in games if g.size == size]
        out.append("### %s\n" % size)
        rows = []
        for grp_name, cond in [("Savi winners", lambda g: team_of(g, g.winner) == SAVI_TEAM),
                               ("Our winners", lambda g: team_of(g, g.winner) == OURS_TEAM),
                               ("Other winners", lambda g: team_of(g, g.winner) not in (SAVI_TEAM, OURS_TEAM))]:
            sel = [g for g in gs if cond(g)]
            cats = collections.Counter(classify_finish(g)[0] for g in sel)
            n = len(sel)
            for cat, v in cats.most_common():
                rows.append([grp_name, n, cat, v, pct(v, n)])
        out.append(table(["Winner group", "Games won", "How it ended", "Games", "Share"], rows))
        # line direction and height for six wins
        rows = []
        for grp_name, cond in [("Savi", lambda g: team_of(g, g.winner) == SAVI_TEAM),
                               ("Ours", lambda g: team_of(g, g.winner) == OURS_TEAM),
                               ("Other", lambda g: team_of(g, g.winner) not in (SAVI_TEAM, OURS_TEAM))]:
            sel = [g for g in gs if cond(g) and g.reason == "six"]
            d = collections.Counter()
            hs = []
            for g in sel:
                dirs = g.res.last_dirs
                d["V" if 1 in dirs and len(dirs) == 1 else "H" if 0 in dirs and len(dirs) == 1 else "diag" if (2 in dirs or 3 in dirs) and 0 not in dirs and 1 not in dirs else "mixed"] += 1
                hs.append(g.info.get("height", 0))
            n = len(sel)
            rows.append([grp_name, n, "%s" % pct(d["H"], n), pct(d["V"], n), pct(d["diag"], n), pct(d["mixed"], n),
                         "%.1f" % mean(hs), "%g" % median(hs)])
        out.append("Winning six by direction (H horizontal, V vertical, diag) and height of the last disc (row index, 0 = floor):\n")
        out.append(table(["Winner", "Six wins", "H", "V", "diag", "mixed (2+ lines)", "Mean row of last disc",
                          "Median row"], rows))
        # blasts shortly before a six
        rows = []
        for grp_name, cond in [("Savi", lambda g: team_of(g, g.winner) == SAVI_TEAM),
                               ("Ours", lambda g: team_of(g, g.winner) == OURS_TEAM),
                               ("Other", lambda g: team_of(g, g.winner) not in (SAVI_TEAM, OURS_TEAM))]:
            sel = [g for g in gs if cond(g) and g.reason == "six"]
            wb = lb = both = none = wthr = 0
            for g in sel:
                last = g.res.moves[-8:]
                w = g.winner
                mine = [m for m in last if m["star"] and m["side"] == w]
                theirs = [m for m in last if m["star"] and m["side"] != w]
                wb += 1 if mine else 0
                lb += 1 if theirs else 0
                both += 1 if mine and theirs else 0
                none += 0 if mine or theirs else 1
                wthr += 1 if any(m.get("wm2") for m in mine) else 0
            n = len(sel)
            rows.append([grp_name, n, pct(wb, n), pct(wthr, n), pct(lb, n), pct(none, n)])
        out.append("Blasts in the last 8 plies of games decided by a normal six ('threat' = the winner's blast left it an "
                   "immediate winning drop):\n")
        out.append(table(["Winner", "Six wins", "Winner blasted", "of which blast left a threat", "Loser blasted",
                          "No blast in last 8 plies"], rows))
    # speed
    out.append("### Speed of the Savi bots (plies of their wins, min/median/p90/max)\n")
    rows = []
    for size in SIZE_ORDER:
        for name in ['Savi8 - "The King"', 'Savi7 - "The Prince"', 'Savi 7 - "The Prince', 'Savi6 - "Beat this"', 'Savi5']:
            for opp_role, label in [("other", "vs other bots"), ("ours", "vs ours"), ("savi", "vs Savi line")]:
                sel = [g for g in games if g.size == size and name in g.names]
                p = []
                pc = []
                lp = []
                for g in sel:
                    side = g.names.index(name) + 1
                    orole = role(g.names[2 - side], g.teams[2 - side])
                    if orole != opp_role:
                        continue
                    if g.winner == side:
                        p.append(g.plies)
                        if not g.timeouts and not g.full:
                            pc.append(g.plies)
                    else:
                        lp.append(g.plies)
                if len(p) + len(lp) >= 3:
                    rows.append([size, sh(name), label, "%d/%d" % (len(p), len(lp)), spread(p), "%.0f" % mean(p) if p else "-",
                                 "%d: %s" % (len(pc), spread(pc)) if pc else "-", spread(lp) if lp else "-"])
    out.append("'Clean wins' = wins in games with no timeout or full-column event at all (a bot that times out hands the "
               "game over in a few plies).\n")
    out.append(table(["Size", "Bot", "Opponents", "W/L", "Plies in wins", "Mean", "Clean wins: n and plies", "Plies in losses"], rows))
    return "\n".join(out)


def section_timeouts(games):
    out = ["## 6. Timeouts\n"]
    out.append("Own timeouts = letters of the game's server log for that side (a timed-out turn leaves no move token and the "
               "bot is skipped). Games with a timeout = games where the bot timed out at least once; 'max in a game' shows "
               "the server-stall games (a turn after a stall times out again and again). A rate per own turn is not "
               "given: skipped turns of both sides in a row leave no trace in the move string.\n")
    for size in SIZE_ORDER:
        bots = collect(games, size)
        rows = []
        for b in order_bots(bots):
            if b.w + b.l < 8:
                continue
            tot = b.w + b.l
            rows.append([b.name, b.team, tot, b.timeouts, b.timeout_games, pct(b.timeout_games, tot),
                         "%.2f" % (b.timeouts / tot), max(b.to_per_game)])
        out.append("### %s\n" % size)
        out.append(table(["Bot", "Team", "Games", "Own timeouts", "Games with a timeout", "Share of games",
                          "Timeouts per game", "Max in a game"], rows))
    out.append("### Our versions, timeouts and results by size\n")
    out.append("'Stall games' = games in which BOTH bots timed out (server stall rather than a slow bot). The next column "
               "splits the games with a timeout by how many we had: 1 / 2-3 / 4 or more.\n")
    rows = []
    for name in ['Savi5 beater 2.1', 'Savi beater 2.2']:
        for size in SIZE_ORDER:
            sel = []
            for g in games:
                if g.size == size and name in g.names:
                    sel.append((g, g.names.index(name) + 1))
            if not sel:
                continue
            tot = len(sel)
            tos = [g.timeouts.count("FS"[side - 1]) for g, side in sel]
            stall = sum(1 for (g, side), t in zip(sel, tos) if t and g.timeouts.count("FS"[2 - side]))
            lost_to = sum(1 for (g, side), t in zip(sel, tos) if t and g.winner != side)
            lost = sum(1 for g, side in sel if g.winner != side)
            b1 = sum(1 for t in tos if t == 1)
            b2 = sum(1 for t in tos if 2 <= t <= 3)
            b3 = sum(1 for t in tos if t >= 4)
            rows.append([name, size, tot, sum(tos), sum(1 for t in tos if t), pct(sum(1 for t in tos if t), tot),
                         "%d / %d / %d" % (b1, b2, b3), stall, "%d of %d" % (lost_to, lost), max(tos)])
    out.append(table(["Version", "Size", "Games", "Own timeouts", "Games with a timeout", "Share", "1 / 2-3 / 4+ per game",
                      "Stall games", "Lost games with our timeout", "Max in a game"], rows))
    out.append("Visible skipped turns of ours by game phase (replay breaks in the F/S alternation, turn index counting "
               "skipped turns):\n")
    rows = []
    for name in ['Savi5 beater 2.1', 'Savi beater 2.2']:
        for size in SIZE_ORDER:
            bucket = collections.Counter()
            for g in games:
                if g.size == size and name in g.names:
                    side = g.names.index(name) + 1
                    for sd, turn in g.res.skips:
                        if sd == side:
                            k = 0 if turn < 6 else 1 if turn < 13 else 2 if turn < 19 else 3 if turn < 30 else 4 if turn < 60 else 5
                            bucket[k] += 1
            if sum(bucket.values()):
                rows.append([name, size, sum(bucket.values())] + [bucket[k] for k in range(6)])
    out.append(table(["Version", "Size", "All", "turn 0-5", "6-12", "13-18", "19-29", "30-59", "60+"], rows))
    out.append("Both versions on 15x11 time out most at turns 13-18, i.e. right after the 12-ply stack of the centre column "
               "in which most of these games begin (the first move off the stack is turn 12/13). A turn index of 14 is "
               "the first player's 8th move.\n")
    return "\n".join(out)


def winning_cell(g):
    last = g.res.moves[-1]
    if last["star"]:
        return "blast in %s" % b36(last["col"])
    names = {0: "H", 1: "V", 2: "diag up", 3: "diag down"}
    return "%s at %s,%d" % ("+".join(names[d] for d in g.res.last_dirs), b36(last["col"]), g.info.get("height", -1))


def plies_13_14(g):
    mv = g.res.moves
    if len(mv) < 14 or "".join("%s%s" % ("FS"[m["side"] - 1], b36(m["col"])) for m in mv[:12]) != "F5S5" * 6:
        return "-"
    return "".join("%s%s%s" % ("FS"[m["side"] - 1], b36(m["col"]), "*" if m["star"] else "") for m in mv[12:14])


def section_losses(games):
    out = ["# Our losses by board size\n"]
    out.append("All losses of team `%s` (versions Savi5 beater 2.1 = 'ours 2.1', Savi beater 2.2 = 'ours 2.2') in "
               "matches.jsonl, de-duplicated. 'Our TO' = our timeouts in the game, 'Opp TO' = the opponent's. Finish "
               "category: see the definitions in server-analysis-by-size.md section 5.\n" % OURS_TEAM)
    ls = []
    for g in games:
        us = 1 if g.teams[0] == OURS_TEAM else 2 if g.teams[1] == OURS_TEAM else 0
        if us and g.winner != us:
            ls.append((g, us))
    ls.sort(key=lambda t: (SIZE_ORDER.index(t[0].size), t[0].names[2 - t[1]], t[0].plies))
    rows = []
    for g, us in ls:
        cat, det = classify_finish(g)
        rows.append([g.size, sh(g.names[2 - us]), "F" if us == 1 else "S", sh(g.names[us - 1]), g.plies, g.reason,
                     g.timeouts.count("FS"[us - 1]), g.timeouts.count("FS"[2 - us]),
                     g.res.blasts and sum(1 for m in g.res.moves if m["star"] and m["side"] == us) or 0,
                     sum(1 for m in g.res.moves if m["star"] and m["side"] != us),
                     cat + (" [%s]" % det if det else ""), winning_cell(g), plies_13_14(g), g.id[:8]])
    out.append("%d losses.\n" % len(rows))
    out.append("'Winning cell' = direction of the six (H, V, up/down diagonal) with the column and row (0 = floor) of the "
               "last disc; '13-14' = the tokens of plies 13 and 14, i.e. the first moves after a 12-ply stack of the centre "
               "column (F5S5 six times) if the game began that way.\n")
    out.append(table(["Size", "Opponent", "Our side", "Our version", "Plies", "Decided by", "Our TO", "Opp TO", "Our blasts",
                      "Their blasts", "Finish", "Winning cell", "13-14", "Game id"], rows))
    by = collections.Counter((g.size, g.names[2 - us]) for g, us in ls)
    games_by = collections.Counter()
    for g in games:
        for side in (1, 2):
            if g.teams[side - 1] == OURS_TEAM:
                games_by[(g.size, g.names[2 - side])] += 1
    rows = [[s, sh(o), v, games_by[(s, o)], pct(v, games_by[(s, o)])] for (s, o), v in sorted(by.items(), key=lambda t: (SIZE_ORDER.index(t[0][0]), -t[1]))]
    out.append("Losses per size and opponent:\n")
    out.append(table(["Size", "Opponent", "Our losses", "Our games vs it", "Loss rate"], rows))
    # detailed endings for Savi8 / Savi7
    out.append("## Last 14 plies of the losses to Savi8 and Savi7 (15x11 and 15x22)\n")
    out.append("Per ply: mover, column played (`*` = blast), and the immediate winning columns each side had before the "
               "move (`W` = the mover's, `T` = the opponent's, base-36 columns). A non-empty `T` list that the mover "
               "ignores is a lost game; two or more columns in `T` cannot be blocked.\n")
    for g, us in ls:
        if g.size == "8x12" or not ("Savi8" in g.names[2 - us] or "Prince" in g.names[2 - us]):
            continue
        out.append("### %s %s (%s, %s), %d plies, %s - game %s\n" % (g.size, sh(g.names[us - 1]), "F" if us == 1 else "S",
                                                                  sh(g.names[2 - us]), g.plies, g.reason, g.id))
        rows = []
        for m in g.res.moves[-14:]:
            who = "us" if m["side"] == us else "them"
            rows.append([m["ply"], who, b36(m["col"]) + ("*" if m["star"] else ""),
                         ",".join(b36(c) for c in m.get("wm", [])) or "-", ",".join(b36(c) for c in m.get("wo", [])) or "-"])
        out.append(table(["Ply", "Mover", "Move", "W (mover wins at)", "T (opponent wins at)"], rows))
        out.append("Final position (top row first, X = F, O = S):\n\n```\n%s```\n" % ascii_board(g))
    return "\n".join(out)


def ascii_board(g):
    cols = g.res.final_cols
    rows, ncols = g.res.size
    lines = []
    for y in range(rows - 1, -1, -1):
        lines.append("".join(("." if y >= len(cols[c]) else "X" if cols[c][y] == 1 else "O") for c in range(ncols)))
    lines.append("".join(b36(c) for c in range(ncols)))
    return "\n".join(lines) + "\n"


def main():
    path, outdir = sys.argv[1], sys.argv[2]
    raw, games, dup = load_games(path)
    bad = [g for g in games if not g.ok]
    assert not bad, "inconsistent games: %s" % [g.id for g in bad[:5]]
    parts = ["# Server games by board size (export 06, replayed with qc_replay.py)\n"]
    parts.append("Source: `analysis/server-exports/export-06-8a0e07e7.zip` (`data/matches.jsonl`, %d ids, %d unique after "
                 "removing identical replays). Draws are not exported, so all W/L figures exclude draws. Plies = move "
                 "tokens (blasts count, skipped turns do not). Team Savi = Savi5, Savi6 (two ids), Savi7 (two ids), Savi8; "
                 "ours = Savi5 beater 2.1 / Savi beater 2.2. Short names: Savi6b = Savi6 \"Beat This\", Savi7b = \"Savi 7 - "
                 "The Prince\" (second id).\n" % (len(raw), len(games)))
    parts.append(section_replay(raw, games, dup))
    parts.append("## 2. Results by board size\n")
    for size in SIZE_ORDER:
        parts.append(section_size(size, games, None))
    parts.append(section_blasts(games))
    parts.append(section_openings(games))
    parts.append(section_branch(games))
    parts.append(section_finish(games))
    parts.append(section_timeouts(games))
    with open(os.path.join(outdir, "server-analysis-by-size.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(parts))
    with open(os.path.join(outdir, "our-losses-by-size.md"), "w", encoding="utf-8") as f:
        f.write(section_losses(games))
    print("games", len(games), "written")


if __name__ == "__main__":
    main()
