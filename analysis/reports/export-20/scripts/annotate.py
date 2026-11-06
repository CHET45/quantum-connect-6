"""Replays one clean game and prints, near the end, each side's immediate wins after every ply.

Immediate win of side X = a drop that makes a six for X, or (blast ready) a blast in some column after which any six
exists (a six of either colour made by a blast counts for the blaster). Blast readiness: 10 own turns since the start (or 9 after
the last blast), lost turns included (the engine counts turns, not moves).
Usage: python annotate.py <match-id prefix> [last N plies]
"""
import sys

from lib import Board, load, opp, toks


def copy(b):
    n = Board(b.rows, b.ncols)
    n.c = [list(x) for x in b.c]
    return n


def drop_wins(b, side):
    return b.wins_for(side)


def blast_wins(b, side):
    out = []
    for col in range(b.ncols):
        if len(b.c[col]) >= b.rows:
            continue
        n = copy(b)
        n.play(side, col, True)
        if n.winners():
            out.append(col)
    return out


def timeline(g):
    """Yields (ply, side, col, star, board after, counters, lost-before) for each token; counters = own turns incl. lost."""
    b = Board(g["board"]["rows"], g["board"]["cols"])
    cnt = {"F": 0, "S": 0}
    since = {"F": 0, "S": 0}
    expect = "F"
    for n, (s, c, st) in enumerate(toks(g["moves"])):
        lost = []
        while s != expect:
            lost.append(expect); cnt[expect] += 1; since[expect] += 1; expect = opp(expect)
        b.play(s, c, st)
        cnt[s] += 1
        since[s] = 1 if st else since[s] + 1
        expect = opp(s)
        yield n, s, c, st, b, dict(cnt), dict(since), lost


def col36(c):
    return "0123456789abcdefghijklmnopqrstuvwxyz"[c] if c < 36 else f"[{c}]"


def annotate(g, last=40):
    ts = toks(g["moves"])
    rows = []
    for n, s, c, st, b, cnt, since, lost in timeline(g):
        o = opp(s)
        ready_o = since[o] >= 10
        dw = drop_wins(b, o)
        bw = blast_wins(b, o) if ready_o else []
        mine = drop_wins(b, s)
        rows.append((n, s, col36(c) + ("*" if st else ""), cnt[s], "lost:" + "".join(lost) if lost else "",
                     f"{o} wins now: drop {''.join(col36(x) for x in dw) or '-'} blast {''.join(col36(x) for x in bw) or ('-' if ready_o else 'n/a')}",
                     f"{s} threats {''.join(col36(x) for x in mine) or '-'}"))
    for r in rows[-last:]:
        print(*r)


if __name__ == "__main__":
    pre = sys.argv[1]
    last = int(sys.argv[2]) if len(sys.argv) > 2 else 40
    for g in load():
        if g["id"].startswith(pre):
            print(g["id"], [p["name"] for p in g["players"]], "labels", list(g["labels"].values()))
            annotate(g, last)
            break
