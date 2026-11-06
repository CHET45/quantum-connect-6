"""Replay core for the Savi fingerprint. Rules copied from Board.blast (qc-bot, read only)."""
import json
import re

WIN = 6
DIRS = ((1, 0), (0, 1), (1, 1), (1, -1))


def parse(moves):
    out, i = [], 0
    while i < len(moves):
        side, col = moves[i], int(moves[i + 1], 36)
        i += 2
        star = i < len(moves) and moves[i] == "*"
        i += star
        out.append((side, col, star))
    return out


def six_through(cols, ncols, c, r):
    """True if a run of >= WIN equal discs passes through cell (c, r)."""
    v = cols[c][r]
    for dc, dr in DIRS:
        n = 1
        x, y = c + dc, r + dr
        while 0 <= x < ncols and 0 <= y < len(cols[x]) and cols[x][y] == v:
            n += 1
            x += dc
            y += dr
        x, y = c - dc, r - dr
        while 0 <= x < ncols and 0 <= y < len(cols[x]) and cols[x][y] == v:
            n += 1
            x -= dc
            y -= dr
        if n >= WIN:
            return True
    return False


def drop_wins(cols, rows, ncols, c, side):
    if len(cols[c]) >= rows:
        return False
    cols[c].append(side)
    w = six_through(cols, ncols, c, len(cols[c]) - 1)
    cols[c].pop()
    return w


def blast_apply(cols, rows, ncols, c, side):
    """Blast in column c, in place. Nothing is placed; columns c-1..c+1 lose rows h-1..h+1 (existing only),
    h = height of column c. Returns (ok, removed discs, win)."""
    h = len(cols[c])
    if h >= rows:
        return False, [], False
    lo = max(h - 1, 0)
    removed, changed = [], []
    for n in range(max(c - 1, 0), min(c + 1, ncols - 1) + 1):
        top = len(cols[n])
        hi = min(h + 1, top - 1)
        if lo > hi:
            continue
        removed.extend(cols[n][lo:hi + 1])
        del cols[n][lo:hi + 1]
        for y in range(lo, len(cols[n])):
            changed.append((n, y))
    win = any(six_through(cols, ncols, n, y) for n, y in changed)
    return True, removed, win


def blast_may_win(cols, rows, ncols, c):
    h = len(cols[c])
    if h >= rows:
        return False
    return (c > 0 and len(cols[c - 1]) >= h + 3) or (c + 1 < ncols and len(cols[c + 1]) >= h + 3)


def blast_wins(cols, rows, ncols, c):
    if not blast_may_win(cols, rows, ncols, c):
        return False
    lo = max(c - 1, 0)
    hi = min(c + 1, ncols - 1)
    saved = [list(x) for x in cols[lo:hi + 1]]
    _, _, win = blast_apply(cols, rows, ncols, c, None)
    for i, s in enumerate(saved):
        cols[lo + i][:] = s
    return win


def check_game(tokens, rows, ncols):
    cols = [[] for _ in range(ncols)]
    for n, (side, c, star) in enumerate(tokens):
        if c >= ncols:
            return False, "col outside", None
        if star:
            ok, _, win = blast_apply(cols, rows, ncols, c, side)
            if not ok:
                return False, "blast in full column", None
        else:
            if len(cols[c]) >= rows:
                return False, "full column", None
            cols[c].append(side)
            win = six_through(cols, ncols, c, len(cols[c]) - 1)
        if win:
            if n != len(tokens) - 1:
                return False, "six before end", None
            return True, "", side
    return False, "no six", None


def version_of(name, team):
    if team == "No beaches?":
        m = re.search(r"(\d\.\d)$", name)
        return "ours" + (m.group(1) if m else "?")
    if team == "Savi":
        m = re.match(r"Savi\s?(\d)", name)
        return "Savi" + m.group(1)
    return None


def load(path):
    return [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]
