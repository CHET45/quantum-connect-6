"""Splits a merged move record (two games written into one list) into two legal games.

Each game must alternate F,S starting with F, stay legal (no disc into a full column, column on the board) and get no
token after its first six. Depth-first search over which game takes each token; identical boards are symmetric, so only
one branch is tried there. Returns (A, B, info) with token index lists, or None.
"""
import copy
import sys

from lib import Board, opp, toks

sys.setrecursionlimit(10000)


class G:
    __slots__ = ("b", "next", "done", "idx", "six")

    def __init__(self, rows, cols):
        self.b = Board(rows, cols)
        self.next = "F"
        self.done = False
        self.idx = []
        self.six = None

    def clone(self):
        g = G.__new__(G)
        g.b = Board(self.b.rows, self.b.ncols)
        g.b.c = [list(c) for c in self.b.c]
        g.next, g.done, g.idx, g.six = self.next, self.done, list(self.idx), self.six
        return g

    def key(self):
        return (self.next, self.done, tuple(tuple(c) for c in self.b.c))


def tryput(g, t, n):
    s, col, st = t
    if g.done or g.next != s:
        return None
    h = g.clone()
    if h.b.play(s, col, st) != "ok":
        return None
    h.next = opp(s)
    h.idx.append(n)
    w = h.b.winners()
    if w:
        h.done = True
        h.six = (n, "".join(sorted(w)))
    return h


def split(moves, rows, cols, budget=200000):
    ts = toks(moves)
    nodes = [0]
    # iterative DFS with explicit stack to avoid deep recursion copies
    stack = [(0, G(rows, cols), G(rows, cols))]
    while stack:
        n, a, b = stack.pop()
        nodes[0] += 1
        if nodes[0] > budget:
            return None
        if n == len(ts):
            return a, b, {"nodes": nodes[0], "tokens": len(ts)}
        t = ts[n]
        opts = []
        ha = tryput(a, t, n)
        hb = tryput(b, t, n)
        if ha is not None:
            opts.append((n + 1, ha, b))
        if hb is not None and not (ha is not None and a.key() == b.key()):
            opts.append((n + 1, a, hb))
        # prefer the game that has fewer tokens (the two games run side by side): push it last so it pops first
        opts.sort(key=lambda o: -abs(len(o[1].idx) - len(o[2].idx)))
        stack.extend(opts)
    return None


if __name__ == "__main__":
    from lib import load, era
    games = [g for g in load() if era(g) == "new"]
    for g in games[:3]:
        print(split(g["moves"], 15, 30) is not None)
