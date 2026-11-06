"""Shared helpers for the export-20 analysis: load games, engine/machine of a game, parse moves, replay boards."""
import json
import re

TEAM = "No beaches?"
US = "Cod Published CHET45"
WIN = 6
EXPORT = r"C:/Users/Chtean45m/Documents/quantum-connect/analysis/server-exports"
TOK = re.compile(r"([FS])(\[\d+\]|[0-9a-z])(\*?)")
MID = re.compile(r"^ERROR (\d+) ")


def load(name="export-20"):
    return [json.loads(l) for l in open(f"{EXPORT}/{name}/data/matches.jsonl", encoding="utf-8") if l.strip()]


def toks(moves):
    """[(side, column, blast)]; base-36 column, [NN] above 35, * = blast."""
    return [(s, int(c[1:-1]) if c[0] == "[" else int(c, 36), bool(st)) for s, c, st in TOK.findall(moves)]


def machine(g):
    """'2824152' / '2825972' / '2640679' for engines that print a machine id, 'old' for timestamped lines, '' if no errors."""
    for e in g["errors"]:
        m = MID.match(e)
        if m:
            return m.group(1)
    return "old" if g["errors"] else ""


def era(g):
    """new = after the update (fetched 14:00 or later and machine-id lines); old = shared engine."""
    m = machine(g)
    if m in ("2824152", "2825972"):
        return "new"
    return "old"


def side_of(g, name=US):
    for i in (0, 1):
        if g["players"][i]["name"] == name:
            return "FS"[i]
    return None


def opp(s):
    return "S" if s == "F" else "F"


class Board:
    def __init__(self, rows, cols):
        self.rows, self.ncols = rows, cols
        self.c = [[] for _ in range(cols)]

    def get(self, c, r):
        return self.c[c][r] if 0 <= c < self.ncols and 0 <= r < len(self.c[c]) else None

    def play(self, side, col, star):
        """Returns 'ok', 'full' (disc into full column), 'bad' (column off board). Blast on full column: no-op."""
        if not 0 <= col < self.ncols:
            return "bad"
        if len(self.c[col]) >= self.rows:
            return "ok" if star else "full"
        r = len(self.c[col])
        self.c[col].append(side)
        if star:
            for cc in range(max(0, col - 1), min(self.ncols, col + 2)):
                for rr in sorted((x for x in (r - 1, r, r + 1) if 0 <= x < len(self.c[cc])), reverse=True):
                    del self.c[cc][rr]
        return "ok"

    def lines(self):
        out = []
        for c in range(self.ncols):
            for r in range(len(self.c[c])):
                v = self.c[c][r]
                for dc, dr in ((1, 0), (0, 1), (1, 1), (1, -1)):
                    if self.get(c - dc, r - dr) == v:
                        continue
                    n = 1
                    while self.get(c + dc * n, r + dr * n) == v:
                        n += 1
                    if n >= WIN:
                        out.append((v, n))
        return out

    def winners(self):
        return {v for v, _ in self.lines()}

    def wins_for(self, side):
        """Columns where a plain disc of `side` makes six now (immediate threats)."""
        out = []
        for col in range(self.ncols):
            if len(self.c[col]) >= self.rows:
                continue
            self.c[col].append(side)
            if side in self.winners():
                out.append(col)
            self.c[col].pop()
        return out

    def fill(self):
        return sum(len(x) for x in self.c)


def replay(g):
    """Replays a game; returns dict with end ply (first six), winner by board, problems, per-ply info."""
    b = Board(g["board"]["rows"], g["board"]["cols"])
    ts = toks(g["moves"])
    res = {"plies": len(ts), "six_at": None, "six_side": None, "problem": None}
    for n, (s, col, st) in enumerate(ts):
        r = b.play(s, col, st)
        if r != "ok":
            res["problem"] = (n, r)
            break
        w = b.winners()
        if w:
            res["six_at"], res["six_side"] = n, "".join(sorted(w))
            break
    res["fill"] = b.fill()
    return res


def runs(ts):
    """Lengths of same-side runs, in order, with side: [(side, length)]."""
    out = []
    for s, _, _ in ts:
        if out and out[-1][0] == s:
            out[-1][1] += 1
        else:
            out.append([s, 1])
    return out
