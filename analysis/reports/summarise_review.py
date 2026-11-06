"""Turns the raw GameReview output (loss-review-sizes-raw.md) into a readable digest.

Usage: python -I summarise_review.py RAW_MD [RAW_MD ...] OUT_MD

For every reviewed game (our moves only, hand eval): the first ply from which the deep (2000 ms) score stays at -1.00
or worse for the rest of the game, the same for the short (45 ms) search, forced-loss horizons, how often the deep and
short moves differ from the move played, and the rows around the point of no return.
"""
import re
import statistics
import sys

ROW = re.compile(r"^\| (\d+) \| ([FS]) \| (\S+) \| (\S+) \| (\S+ ?\S*) \| (\S+) \| (\S+ ?\S*) \| (\d+) \|$")
HEAD = re.compile(r"^## (.*?) \(([FS])\) vs (.*?), game (\S+), board (\S+)$")
SUMM = re.compile(r"^Summary: game of (\d+) plies, (\d+) of our moves searched(.*)\.$")


def score(txt):
    txt = txt.strip()
    if txt.startswith("loss "):
        return -10.0 - 0.001 * (100 - int(txt[5:])), int(txt[5:])
    if txt.startswith("win "):
        return 10.0, None
    return float(txt), None


def parse(path):
    games = []
    cur = None
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        m = HEAD.match(line)
        if m:
            cur = {"ours": m.group(1), "side": m.group(2), "opp": m.group(3), "id": m.group(4), "size": m.group(5),
                   "rows": [], "plies": None, "truncated": False}
            games.append(cur)
            continue
        if cur is None:
            continue
        m = ROW.match(line)
        if m:
            ply, side, played, smove, sscore, dmove, dscore, depth = m.groups()
            s, sl = score(sscore)
            d, dl = score(dscore)
            cur["rows"].append({"ply": int(ply), "played": played, "smove": smove, "sscore": s, "sloss": sl,
                                "dmove": dmove, "dscore": d, "dloss": dl, "depth": int(depth),
                                "stxt": sscore.strip(), "dtxt": dscore.strip()})
            continue
        m = SUMM.match(line)
        if m:
            cur["plies"] = int(m.group(1))
            cur["truncated"] = "TRUNCATED" in m.group(3)
    return [g for g in games if g["rows"]]


def permanent_from(rows, key, thr):
    """First ply from which every later searched move has score <= thr."""
    idx = None
    for i in range(len(rows) - 1, -1, -1):
        if rows[i][key] <= thr:
            idx = i
        else:
            break
    return idx


def first_at_or_below(rows, key, thr):
    for i, r in enumerate(rows):
        if r[key] <= thr:
            return i
    return None


def fmt(rows, i, plies):
    if i is None:
        return "never"
    return "%d (%d before end)" % (rows[i]["ply"], plies - rows[i]["ply"] + 1)


def reliable(rows):
    """Rows where a search really ran: depth 1-2 without a mate score is a forced-move shortcut."""
    return [r for r in rows if r["depth"] >= 3 or r["dloss"] is not None or r["sloss"] is not None]


def near_pnr(rows, i):
    """'k of 3' deep moves differing from the move played at the point of no return and the two searched moves before."""
    if i is None:
        return "-"
    sel = rows[max(0, i - 2):i + 1]
    diff = [r for r in sel if r["dmove"] != r["played"]]
    return "%d of %d (%s)" % (len(diff), len(sel), ", ".join("%d: %s vs %s" % (r["ply"], r["played"], r["dmove"]) for r in diff)
                              if diff else "none")


def digest(g):
    rows = reliable(g["rows"])
    plies = g["plies"] or (rows[-1]["ply"] + 1)
    out = {}
    out["n"] = len(rows)
    out["deep_diff"] = sum(1 for r in rows if r["dmove"] != r["played"])
    out["short_diff"] = sum(1 for r in rows if r["smove"] != r["played"])
    out["sd_diff"] = sum(1 for r in rows if r["smove"] != r["dmove"])
    for key, name in (("dscore", "deep"), ("sscore", "short")):
        for thr in (-0.5, -1.0, -2.0):
            out["%s_first_%s" % (name, thr)] = first_at_or_below(rows, key, thr)
            out["%s_perm_%s" % (name, thr)] = permanent_from(rows, key, thr)
    # forced loss horizons
    out["deep_loss"] = next((i for i, r in enumerate(rows) if r["dloss"] is not None), None)
    out["short_loss"] = next((i for i, r in enumerate(rows) if r["sloss"] is not None), None)
    out["plies"] = plies
    out["from_ply"] = rows[0]["ply"] if rows else None
    return out


def main():
    outp = sys.argv[-1]
    games = []
    for raw in sys.argv[1:-1]:
        games += parse(raw)
    o = []
    o.append("# Loss review by board size: hand-eval searches at our moves\n")
    o.append("Source: `loss-review-sizes-raw.md` (GameReview, single thread, hand evaluation, short = 45 ms, deep = 2000 ms, "
             "scores in logit from OUR view, mate scores shown as `loss N` = plies to the forced loss). Only our moves "
             "were searched. 'Point of no return' = the first searched ply from which the score stays at or below "
             "-1.00 for the rest of the game; 'before end' counts plies including the searched one.\n")
    rows = []
    stats = {"deep_perm": [], "short_perm": [], "deep_loss": [], "short_loss": [], "deep_first": [], "short_first": []}
    for g in games:
        d = digest(g)
        rws = reliable(g["rows"])
        pl = d["plies"]
        rows.append([g["size"], g["opp"].replace('Savi8 - "The King"', "Savi8").replace('Savi7 - "The Prince"', "Savi7")
                     .replace('Savi 7 - "The Prince', "Savi7b"), g["side"], pl, d["n"] if not g["truncated"] else "%d (truncated)" % d["n"],
                     "%d / %d / %d" % (d["deep_diff"], d["short_diff"], d["sd_diff"]),
                     fmt(rws, d["deep_first_-1.0"], pl), fmt(rws, d["short_first_-1.0"], pl),
                     fmt(rws, d["deep_perm_-1.0"], pl), fmt(rws, d["short_perm_-1.0"], pl),
                     fmt(rws, d["deep_loss"], pl), fmt(rws, d["short_loss"], pl), near_pnr(rws, d["deep_perm_-1.0"]),
                     g["id"][:8]])
        if d["deep_perm_-1.0"] is not None and not g["truncated"]:
            stats["deep_perm"].append(pl - rws[d["deep_perm_-1.0"]]["ply"] + 1)
        if d["short_perm_-1.0"] is not None and not g["truncated"]:
            stats["short_perm"].append(pl - rws[d["short_perm_-1.0"]]["ply"] + 1)
        if d["deep_loss"] is not None and not g["truncated"]:
            stats["deep_loss"].append(pl - rws[d["deep_loss"]]["ply"] + 1)
        if d["short_loss"] is not None and not g["truncated"]:
            stats["short_loss"].append(pl - rws[d["short_loss"]]["ply"] + 1)
        if d["deep_first_-1.0"] is not None and not g["truncated"]:
            stats["deep_first"].append(pl - rws[d["deep_first_-1.0"]]["ply"] + 1)
        if d["short_first_-1.0"] is not None and not g["truncated"]:
            stats["short_first"].append(pl - rws[d["short_first_-1.0"]]["ply"] + 1)
    o.append("| Size | Opponent | Our side | Plies | Our moves searched | Moves differing from played: deep / short / deep vs short | "
             "Deep first <= -1.00 (before end) | Short first <= -1.00 | Deep point of no return | Short point of no return | "
             "Deep first forced loss | Short first forced loss | Deep move differs from played at the 3 moves up to the "
             "deep point of no return | Game |")
    o.append("|" + "---|" * 14)
    for r in rows:
        o.append("| " + " | ".join(str(x) for x in r) + " |")
    o.append("")

    def med(v):
        return "%g" % statistics.median(v) if v else "-"

    o.append("Medians over the reviewed, untruncated games (plies before the end): deep first <= -1.00: %s; short first <= -1.00: "
             "%s; deep point of no return: %s; short point of no return: %s; deep forced loss seen: %s; short forced loss "
             "seen: %s.\n" % (med(stats["deep_first"]), med(stats["short_first"]), med(stats["deep_perm"]),
                              med(stats["short_perm"]), med(stats["deep_loss"]), med(stats["short_loss"])))
    # per-game neighbourhood of the point of no return
    for g in games:
        d = digest(g)
        rws = reliable(g["rows"])
        i = d["deep_perm_-1.0"]
        o.append("## %s (%s) vs %s, game %s, %s\n" % (g["ours"], g["side"], g["opp"], g["id"], g["size"]))
        if i is None:
            o.append("Deep score never stays at or below -1.00 until the end of the searched part.\n")
            continue
        lo = max(0, i - 4)
        hi = min(len(rws), i + 4)
        o.append("Rows around the deep point of no return (ply %d). `played` = move in the game, short = 45 ms, deep = 2000 ms "
                 "(scores from our view):\n" % rws[i]["ply"])
        o.append("| ply | played | short move | short | deep move | deep | depth |")
        o.append("|---|---|---|---|---|---|---|")
        for k in range(lo, hi):
            r = rws[k]
            mark = " **<-**" if k == i else ""
            o.append("| %d%s | %s | %s | %s | %s | %s | %d |" % (r["ply"], mark, r["played"], r["smove"], r["stxt"], r["dmove"],
                                                              r["dtxt"], r["depth"]))
        o.append("")
    # optimism of the short search
    o.append("## Short search versus deep search (score gap)\n")
    o.append("Gap = short (45 ms) score minus deep (2000 ms) score at the same position, our view, positions where neither "
             "search reports a forced result and the deep search really ran (depth >= 3). Positive = the 45 ms search is more "
             "optimistic than the 2 s search.\n")
    o.append("| Game | Opponent | Rows | Mean gap, whole game | Mean gap, last 30 plies | Mean gap, last 60 plies | Deep depth median | Rows with gap >= +1.00 |")
    o.append("|---|---|---|---|---|---|---|---|")
    allgap, last30, last60 = [], [], []
    for g in games:
        rws = [r for r in reliable(g["rows"]) if r["dloss"] is None and r["sloss"] is None]
        if not rws:
            continue
        pl = g["plies"] or (rws[-1]["ply"] + 1)
        gaps = [(r["sscore"] - r["dscore"], pl - r["ply"] + 1) for r in rws]
        w = [x for x, _ in gaps]
        l30 = [x for x, k in gaps if k <= 30]
        l60 = [x for x, k in gaps if k <= 60]
        allgap += w
        last30 += l30
        last60 += l60
        o.append("| %s | %s | %d | %+.2f | %s | %s | %g | %d |" % (
            g["id"][:8], g["opp"].replace('Savi8 - "The King"', "Savi8").replace('Savi7 - "The Prince"', "Savi7")
            .replace('Savi 7 - "The Prince', "Savi7b"), len(rws), statistics.mean(w),
            ("%+.2f" % statistics.mean(l30)) if l30 else "-", ("%+.2f" % statistics.mean(l60)) if l60 else "-",
            statistics.median([r["depth"] for r in rws]), sum(1 for x in w if x >= 1.0)))
    if allgap:
        o.append("")
        o.append("All reviewed games: mean gap %+.2f over %d rows; last 60 plies %+.2f over %d rows; last 30 plies %+.2f over "
                 "%d rows.\n" % (statistics.mean(allgap), len(allgap), statistics.mean(last60) if last60 else 0, len(last60),
                                  statistics.mean(last30) if last30 else 0, len(last30)))
    # blasts played in the reviewed games, and the deep score change around our moves
    o.append("## Our blasts in the reviewed games\n")
    o.append("For every searched move of ours that was a blast (`*`): did the 2000 ms search choose the same blast, a quiet "
             "drop in the same column, or something else; and the change of the deep score from this move to our next "
             "searched move (after the opponent's reply), compared with our quiet moves. Rows with depth <= 2 and no mate "
             "(forced-move shortcut) are ignored.\n")
    stat = {"blast": [], "quiet": [], "blast, deep agrees": [], "blast, deep disagrees": [], "quiet, deep agrees": [],
            "quiet, deep disagrees": []}
    agree = {"same blast": 0, "quiet drop, same column": 0, "other": 0}
    short_agree = {"same blast": 0, "quiet drop, same column": 0, "other": 0}
    lines = []
    for g in games:
        rws = reliable(g["rows"])
        for i, r in enumerate(rws):
            kind = "blast" if r["played"].endswith("*") else "quiet"
            if i + 1 < len(rws) and rws[i + 1]["ply"] == r["ply"] + 2 and abs(r["dscore"]) < 9 and abs(rws[i + 1]["dscore"]) < 9:
                delta = rws[i + 1]["dscore"] - r["dscore"]
                stat[kind].append(delta)
                stat[kind + (", deep agrees" if r["dmove"] == r["played"] else ", deep disagrees")].append(delta)
            if kind == "blast":
                for mv, tbl in ((r["dmove"], agree), (r["smove"], short_agree)):
                    if mv == r["played"]:
                        tbl["same blast"] += 1
                    elif mv == r["played"].rstrip("*"):
                        tbl["quiet drop, same column"] += 1
                    else:
                        tbl["other"] += 1
                nxt = rws[i + 1]["dscore"] if i + 1 < len(rws) and rws[i + 1]["ply"] == r["ply"] + 2 else None
                lines.append("| %s | %s | %d | %s | %s | %s | %s | %s |" % (g["id"][:8], g["opp"].replace('Savi8 - "The King"', "Savi8")
                             .replace('Savi7 - "The Prince"', "Savi7").replace('Savi 7 - "The Prince', "Savi7b"), r["ply"],
                             r["played"], r["dmove"], r["dtxt"], r["smove"],
                             ("%+.2f" % nxt) if nxt is not None else "-"))
    n = sum(agree.values())
    o.append("Blasts searched: %d. 2000 ms search: same blast %d, quiet drop in the same column %d, other move %d. 45 ms search: "
             "same blast %d, quiet drop in the same column %d, other move %d.\n" % (
                 n, agree["same blast"], agree["quiet drop, same column"], agree["other"], short_agree["same blast"],
                 short_agree["quiet drop, same column"], short_agree["other"]))
    for kind in ("blast", "blast, deep agrees", "blast, deep disagrees", "quiet", "quiet, deep agrees",
                 "quiet, deep disagrees"):
        v = stat[kind]
        if v:
            o.append("- Deep score change from our %s move to our next searched move: mean %+.2f, median %+.2f over %d moves "
                     "(mate-range scores excluded)." % (kind, statistics.mean(v), statistics.median(v), len(v)))
    o.append("")
    o.append("| Game | Opponent | Ply | Played | Deep move | Deep score then | Short move | Deep score at our next move |")
    o.append("|---|---|---|---|---|---|---|---|")
    o.extend(lines)
    o.append("")
    # score trajectory every 10 searched plies, for the first few games
    o.append("## Deep-score trajectory (every 10th searched ply)\n")
    for g in games:
        rws = g["rows"]
        pts = ["%d:%s" % (r["ply"], r["dtxt"]) for k, r in enumerate(rws) if k % 5 == 0]
        o.append("- %s %s vs %s (%s): %s" % (g["size"], g["ours"], g["opp"], g["id"][:8], ", ".join(pts)))
    open(outp, "w", encoding="utf-8").write("\n".join(o) + "\n")
    print("games", len(games))


if __name__ == "__main__":
    main()
