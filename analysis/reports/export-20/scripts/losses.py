"""rc8 ("Cod Published CHET45") losses on 15x30, old engine and new: why each was lost.

For each lost clean game: plies, our lost turns (own-turn numbers), whether the final move was a blast, the winner's
immediate wins right before its final move (drops, blasts if ready), whether we lost a turn in the last 12 plies, and the
edge share of the winner's discs in the final six. Cause: 'lost turn' if one of ours fell in the last 12 plies;
'blast + drop' if the winner had a ready blast win and a drop win; 'double drop' if 2+ drop wins; 'blast' if only a
blast win; 'single' if exactly one drop win (a missed block); 'other' otherwise.
"""
import collections

from annotate import blast_wins, drop_wins, timeline
from lib import US, era, load, opp, side_of, toks
from merged import is_merged


def analyse(x):
    s = side_of(x)
    w = opp(s)
    ts = toks(x["moves"])
    prev = None
    ours_lost = []
    for n, side, c, st, b, cnt, since, lost in timeline(x):
        for l in lost:
            if l == s:
                ours_lost.append((n, cnt[s]))
        if n == len(ts) - 2:
            ready = since[w] >= 10
            dw = drop_wins(b, w)
            bw = blast_wins(b, w) if ready else []
            prev = (dw, bw, ready)
    last = ts[-1]
    late = [t for n, t in ours_lost if n >= len(ts) - 12]
    dw, bw, ready = prev if prev else ([], [], False)
    if late:
        cause = "lost turn"
    elif bw and dw:
        cause = "blast + drop"
    elif len(dw) >= 2:
        cause = "double drop"
    elif bw:
        cause = "blast"
    elif len(dw) == 1:
        cause = "single"
    else:
        cause = "other"
    return {"plies": len(ts), "lost": [t for _, t in ours_lost], "final_blast": last[2], "dw": dw, "bw": bw,
            "cause": cause, "final_col": last[1]}


def main():
    g = load()
    rows = []
    for x in g:
        if x["board"]["cols"] != 30 or is_merged(x):
            continue
        s = side_of(x)
        if not s:
            continue
        me = x["players"]["FS".index(s)]
        if x["labels"].get(me["lb"]) != "Lost":
            continue
        opp_name = x["players"][1 - "FS".index(s)]["name"]
        a = analyse(x)
        rows.append((era(x), x["fetchedAt"][:16], x["id"][:8], opp_name, s, a))
    c = collections.Counter((r[0], r[5]["cause"]) for r in rows)
    print("cause counts:", dict(c))
    print("\n| engine | fetched | id | opponent | we | plies | our lost turns (own turn) | final move | winner's wins before it (drop / blast) | cause |")
    print("|---|---|---|---|---|---|---|---|---|---|")
    for e, f, i, o, s, a in sorted(rows):
        print(f"| {e} | {f} | {i} | {o} | {s} | {a['plies']} | {','.join(map(str, a['lost'])) or '-'} | "
              f"{a['final_col']}{'*' if a['final_blast'] else ''} | {a['dw'] or '-'} / {a['bw'] or '-'} | {a['cause']} |")


if __name__ == "__main__":
    main()
