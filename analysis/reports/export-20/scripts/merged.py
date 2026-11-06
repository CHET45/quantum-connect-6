"""Merged records on machine 2825972: two games of the same pairing written into one move list.

Structure seen: a lockstep phase where each round is F F S S (one disc per game), then a single-game phase (F S F S)
after one game ended. Split: in the lockstep phase the first token of each pair goes to game A, the second to B (and
the reverse if that fails); in the tail all tokens go to the game still running. A split is accepted when both games are
legal, the game that stops at the switch ends with a six on its last token (or is cut), and the survivor ends with a six
on the record's last token.
"""
import collections
import sys

from lib import Board, era, load, machine, opp, replay, runs, toks


def is_merged(x):
    r = replay(x)
    return r["problem"] is not None or (r["six_at"] is not None and r["six_at"] != len(toks(x["moves"])) - 1)


def play(seq, rows, cols):
    """Replays a token list; returns (status, six_index, six_side). status: 'six-last', 'six-early', 'open', 'illegal'."""
    b = Board(rows, cols)
    for n, (s, c, st) in enumerate(seq):
        if b.play(s, c, st) != "ok":
            return "illegal", n, None
        w = b.winners()
        if w:
            return ("six-last" if n == len(seq) - 1 else "six-early"), n, "".join(sorted(w))
    return "open", None, None


def phases(ts):
    """Index where the lockstep (runs of 2) phase ends."""
    rr = runs(ts)
    pos = 0
    for k, (s, l) in enumerate(rr):
        if l != 2:
            # tolerate a final pair being cut: the lockstep phase ends at the first run of 1 that is followed only by 1s
            if all(ll == 1 for _, ll in rr[k:]):
                return pos
        pos += l
    return pos


def split(x):
    ts = toks(x["moves"])
    R, C = x["board"]["rows"], x["board"]["cols"]
    cut = phases(ts)
    head, tail = ts[:cut], ts[cut:]
    best = None
    for order in (0, 1):
        A, B = [], []
        for k in range(0, len(head), 2):
            pair = head[k:k + 2]
            if len(pair) < 2:
                A.append(pair[0]); continue
            a, b = (pair[0], pair[1]) if order == 0 else (pair[1], pair[0])
            A.append(a); B.append(b)
        for surv in ("A", "B"):
            AA, BB = (A + tail, B) if surv == "A" else (A, B + tail)
            # the survivor must alternate after the switch
            seq = AA if surv == "A" else BB
            if any(seq[i][0] == seq[i + 1][0] for i in range(len(seq) - 1)) or (seq and seq[0][0] != "F"):
                continue
            ra, rb = play(AA, R, C), play(BB, R, C)
            sv, st = (ra, rb) if surv == "A" else (rb, ra)
            ok = sv[0] == "six-last" and st[0] in ("six-last", "open")
            cand = {"order": order, "surv": surv, "A": AA, "B": BB, "ra": ra, "rb": rb, "ok": ok, "cut": cut}
            if ok and (best is None or not best["ok"]):
                best = cand
            elif best is None:
                best = cand
    return best


def main():
    g = load()
    new = [x for x in g if era(x) == "new"]
    mixed = [x for x in new if is_merged(x)]
    print(len(mixed), "merged records, machines", dict(collections.Counter(machine(x) for x in mixed)))
    stat = collections.Counter()
    out = []
    for x in mixed:
        sp = split(x)
        if sp is None:
            stat["irregular (lost turns inside lockstep)"] += 1
            out.append(([p["name"] for p in x["players"]], len(toks(x["moves"])), "irregular", "", "", "", "", "", x["winner"]))
            continue
        names = [p["name"] for p in x["players"]]
        lab = {"FS"[i]: x["labels"].get(p["lb"]) for i, p in enumerate(x["players"])}
        surv = sp["A"] if sp["surv"] == "A" else sp["B"]
        stop = sp["B"] if sp["surv"] == "A" else sp["A"]
        rs = sp["ra"] if sp["surv"] == "A" else sp["rb"]
        rt = sp["rb"] if sp["surv"] == "A" else sp["ra"]
        stat[("split ok", sp["ok"])] += 1
        stat[("stopped game ends", rt[0])] += 1
        # official label vs the two sub-games' six sides
        won = [s for s in "FS" if lab[s] == "Won"]
        won = won[0] if won else None
        stat[("label = first-finished game's six", won == rt[2])] += 1
        stat[("label = survivor's six", won == rs[2])] += 1
        out.append((names, len(toks(x["moves"])), sp["cut"], len(stop), rt, len(surv), rs, won, x["winner"]))
    for k, v in sorted(stat.items(), key=str):
        print(k, v)
    print("\nfirst / second | plies | lockstep plies | stopped game: plies, result | survivor: plies, result | site winner | export winner field")
    for o in out:
        print(o[0][0][:14], "/", o[0][1][:14], "|", o[1], "|", o[2], "|", o[3], o[4], "|", o[5], o[6], "|", o[7], "|", o[8])
    return mixed


if __name__ == "__main__":
    main()
