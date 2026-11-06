"""Size-aware replay of Quantum Connect server games (matches.jsonl exports).

Rules mirrored from qc-bot-safe/src/main/java/ch/qc/bot/rules/Board.java and the turn rules of
tools/ServerGames.java:
  * gravity, columns are stacks of at most `rows` discs, board is rows x cols;
  * a normal drop wins if it completes 6 in a row (any direction) of the mover's colour;
  * a blast ("*") in column c is played at the landing cell (c, h), h = height of c (a full column is illegal):
    the placed disc vanishes, every disc in rows h-1..h+1 of columns c-1..c+1 is removed (in column c that is only
    the top disc at h-1) and the discs above fall down; afterwards any 6-line of EITHER colour touching those
    three columns wins for the mover;
  * a blast needs a per-side cooldown counter >= 10; every own turn counts (skipped ones too, by default),
    a blast resets the counter, every played move adds 1;
  * a turn that left no token (timeout / full column) shows as a break in the F/S alternation.
Move strings: side letter F/S, ONE base-36 column digit, optional '*'.
"""
import json
import re

SIZES = [(8, 12), (15, 11), (15, 22)]  # (rows, cols), tried in this order when the recorded size does not replay
COOLDOWN = 10
TOKEN = re.compile(r"([FS])([0-9a-z])(\*?)")


def load(path):
    games = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.strip():
                games.append(json.loads(line))
    return games


def tokens(moves):
    """List of (side 1/2, column, star) or None if the string does not parse completely."""
    out = []
    p = 0
    while p < len(moves):
        m = TOKEN.match(moves, p)
        if not m:
            return None
        out.append((1 if m.group(1) == "F" else 2, int(m.group(2), 36), m.group(3) == "*"))
        p = m.end()
    return out


DIRS = ((1, 0), (0, 1), (1, 1), (1, -1))


def run_through(cols, ncols, c, y, colour, extra=None):
    """True if a run of >= 6 cells of `colour` passes through (c, y). `extra` = (c, y) cell treated as colour."""
    def at(x, yy):
        if x < 0 or x >= ncols or yy < 0:
            return 0
        if extra is not None and (x, yy) == extra:
            return colour
        col = cols[x]
        return col[yy] if yy < len(col) else 0

    for dx, dy in DIRS:
        n = 1
        x, yy = c + dx, y + dy
        while at(x, yy) == colour:
            n += 1
            x += dx
            yy += dy
        x, yy = c - dx, y - dy
        while at(x, yy) == colour:
            n += 1
            x -= dx
            yy -= dy
        if n >= 6:
            return True
    return False


def win_dirs(cols, ncols, c, y, colour):
    """Directions (0 H, 1 V, 2 diag up, 3 diag down) in which a run >= 6 passes through (c, y)."""
    res = []
    for i, (dx, dy) in enumerate(DIRS):
        n = 1
        x, yy = c + dx, y + dy
        while 0 <= x < ncols and 0 <= yy < len(cols[x]) and cols[x][yy] == colour:
            n += 1
            x += dx
            yy += dy
        x, yy = c - dx, y - dy
        while 0 <= x < ncols and 0 <= yy < len(cols[x]) and cols[x][yy] == colour:
            n += 1
            x -= dx
            yy -= dy
        if n >= 6:
            res.append(i)
    return res


def winning_columns(cols, rows, ncols, colour):
    """Columns where dropping a disc of `colour` completes a six (immediate winning drops)."""
    res = []
    for c in range(ncols):
        h = len(cols[c])
        if h < rows and run_through(cols, ncols, c, h, colour, (c, h)):
            res.append(c)
    return res


def blast(cols, rows, ncols, c):
    """In place. Returns False for a full column (illegal), else True."""
    h = len(cols[c])
    if h >= rows:
        return False
    lo = max(h - 1, 0)
    for n in range(max(c - 1, 0), min(c + 1, ncols - 1) + 1):
        top = len(cols[n])
        hi = min(h + 1, top - 1)
        if lo > hi:
            continue
        del cols[n][lo:hi + 1]
    return True


def blast_wins(cols, ncols, c):
    for n in range(max(c - 1, 0), min(c + 1, ncols - 1) + 1):
        for y in range(len(cols[n])):
            colour = cols[n][y]
            if run_through(cols, ncols, n, y, colour):
                return True
    return False


class Result:
    __slots__ = ("size", "ok", "problem", "plies", "turns", "reason", "winner", "skips", "blasts", "moves",
                 "min_counter", "final_cols", "last_dirs", "total", "own")

    def __init__(self):
        self.ok = False
        self.problem = None
        self.plies = 0
        self.turns = 0
        self.reason = "none"
        self.winner = 0
        self.skips = []      # (side, turn_index)
        self.blasts = []     # dicts: side, ply (1-based), own (own-turn index, skipped included), counter, col, wins
        self.moves = []      # dicts: side, col, star, ply (1-based), turn, own, counter, win
        self.min_counter = None
        self.final_cols = None
        self.last_dirs = []
        self.total = 0
        self.own = [0, 0, 0]  # own turns taken per side, skipped turns included


def blast_line_colours(cols, ncols, c):
    """Set of colours that have a six touching columns c-1..c+1 (after a blast)."""
    out = set()
    for n in range(max(c - 1, 0), min(c + 1, ncols - 1) + 1):
        for y in range(len(cols[n])):
            if cols[n][y] not in out and run_through(cols, ncols, n, y, cols[n][y]):
                out.add(cols[n][y])
    return out


def replay(game, rows, ncols, skips_count=True, hook=None):
    """Replay one matches.jsonl record on a rows x ncols board with the real rules; stops at the first problem.

    hook(phase, rec, cols, result) is called with phase 'before' (position before the move, rec has side/col/star/ply)
    and 'after' (rec also has win). The hook sees the live board and must not modify it."""
    r = Result()
    r.size = (rows, ncols)
    toks = tokens(game["moves"])
    if toks is None:
        r.problem = "unparsable moves"
        return r
    r.total = len(toks)
    cols = [[] for _ in range(ncols)]
    r.final_cols = cols
    counter = [0, 0, 0]
    own = [0, 0, 0]
    expected = 1
    turn = 0
    n = 0
    rec_winner = 1 if game["winner"] == "F" else 2
    for idx, (side, col, star) in enumerate(toks):
        if side != expected:
            r.skips.append((expected, turn))
            if skips_count:
                counter[expected] += 1
            own[expected] += 1
            turn += 1
            expected = side
        if col >= ncols:
            r.problem = "column %d outside the board at ply %d" % (col, n + 1)
            return r
        rec = {"side": side, "col": col, "star": star, "ply": n + 1, "turn": turn, "own": own[side],
               "counter": counter[side], "win": False}
        if hook is not None:
            hook("before", rec, cols, r)
        if star:
            if counter[side] < COOLDOWN:
                r.problem = "blast at ply %d with cooldown counter %d" % (n + 1, counter[side])
                return r
            if not blast(cols, rows, ncols, col):
                r.problem = "blast on a full column at ply %d" % (n + 1)
                return r
            win = blast_wins(cols, ncols, col)
            r.blasts.append(rec)
            if r.min_counter is None or counter[side] < r.min_counter:
                r.min_counter = counter[side]
            counter[side] = 0
        else:
            h = len(cols[col])
            if h >= rows:
                r.problem = "disc into a full column at ply %d" % (n + 1)
                return r
            cols[col].append(side)
            win = run_through(cols, ncols, col, h, side)
            if win:
                r.last_dirs = win_dirs(cols, ncols, col, h, side)
        counter[side] += 1
        own[side] += 1
        turn += 1
        n += 1
        expected = 3 - side
        rec["win"] = win
        r.moves.append(rec)
        r.own[side] = own[side]
        if hook is not None:
            hook("after", rec, cols, r)
        if win:
            r.reason = "blast" if star else "six"
            r.winner = side
            if idx != len(toks) - 1:
                r.problem = "line at ply %d but more moves follow" % n
                return r
    r.plies = n
    r.turns = turn
    r.own[1], r.own[2] = own[1], own[2]
    if r.winner == 0:
        r.problem = "no line after the last move (%d plies)" % n
    elif r.winner != rec_winner:
        r.problem = "replay winner %s but recorded %s" % ("FS"[r.winner - 1], game["winner"])
    elif game["win_at"] != n - 1:
        r.problem = "win at ply index %d but recorded win_at %s" % (n - 1, game["win_at"])
    r.ok = r.problem is None
    return r


def replay_any(game, skips_count=True, hook=None):
    """Replay on the recorded board size first, then on the other sizes. Returns (Result, recorded_size_ok)."""
    b = game.get("board") or {}
    rec = (b.get("rows"), b.get("cols"))
    order = []
    if rec[0] and rec[1]:
        order.append(rec)
    order += [s for s in SIZES if s not in order]
    first = None
    for s in order:
        res = replay(game, s[0], s[1], skips_count, hook if s == order[0] else None)
        if first is None:
            first = res
        if res.ok:
            return res, (s == rec)
    return first, False


def size_label(size):
    return "%dx%d" % (size[0], size[1])
