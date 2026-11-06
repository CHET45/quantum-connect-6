"""How Savi9 lost its two games: last moves, blast use, lost turns, age of the winning cell, winning replies available to the loser."""
import json
import sys


def parse(moves):
    out, i = [], 0
    while i < len(moves):
        side, col = moves[i], int(moves[i + 1], 36)
        i += 2
        star = i < len(moves) and moves[i] == "*"
        i += star
        out.append((side, col, star))
    return out


def at(b, c, y):
    return b[c][y] if 0 <= c < len(b) and 0 <= y < len(b[c]) else None


def lines(b):
    out = []
    for c in range(len(b)):
        for y in range(len(b[c])):
            v = b[c][y]
            for dc, dy, n in ((1, 0, "H"), (0, 1, "V"), (1, 1, "D+"), (1, -1, "D-")):
                if at(b, c - dc, y - dy) == v:
                    continue
                k = 1
                while at(b, c + dc * k, y + dy * k) == v:
                    k += 1
                if k >= 6:
                    out.append((v, n, k, c, y))
    return out


def would_six(b, c, y, side):
    for dc, dy in ((1, 0), (1, 1), (1, -1), (0, 1)):
        k = 1
        j = 1
        while at(b, c + dc * j, y + dy * j) == side:
            k += 1
            j += 1
        j = 1
        while at(b, c - dc * j, y - dy * j) == side:
            k += 1
            j += 1
        if k >= 6:
            return True
    return False


def apply(b, side, col, star, rows):
    if star and len(b[col]) >= rows:
        return
    y = len(b[col])
    b[col].append(side)
    if star:
        for c in range(max(0, col - 1), min(len(b), col + 2)):
            for r in sorted((x for x in (y - 1, y, y + 1) if 0 <= x < len(b[c])), reverse=True):
                del b[c][r]


for l in open(sys.argv[1], encoding="utf-8"):
    if not l.strip():
        continue
    g = json.loads(l)
    names = [p["name"] for p in g["players"]]
    if not any(n.startswith("Savi9") for n in names):
        continue
    i = 0 if names[0].startswith("Savi9") else 1
    if g["winner"] == "FS"[i]:
        continue
    toks = parse(g["moves"])
    rows, cols = g["board"]["rows"], g["board"]["cols"]
    b = [[] for _ in range(cols)]
    hist = []
    prev = "S"
    lost = {"F": [], "S": []}
    for n, (s, c, st) in enumerate(toks):
        if s == prev:
            lost["S" if s == "F" else "F"].append(n)
        hist.append([list(x) for x in b])
        apply(b, s, c, st, rows)
        prev = s
    w = g["winner"]
    last = toks[-1]
    pre = hist[-1]
    age = None
    if not last[2]:
        y = len(pre[last[1]])
        age = 0
        for h in reversed(hist):
            if y >= len(h[last[1]]) and would_six(h, last[1], y, w):
                age += 1
            else:
                break
    txt = lambda ts: "".join(s + format(c, "x") + ("*" if st else "") for s, c, st in ts)
    bl = {s: sum(1 for t in toks if t[0] == s and t[2]) for s in "FS"}
    print(f"{rows}x{cols} {names[0]} (F) vs {names[1]} (S), winner {names['FS'.index(w)]}, plies {len(toks)}, id {g['id'][:8]}")
    print(f"  lost turns at ply F {lost['F']} S {lost['S']}; blasts F{bl['F']} S{bl['S']}; last move {txt([last])}; six {[(v, n, k) for v, n, k, _, _ in lines(b)]}; winning cell stood {age} plies; open columns {sum(len(x) < rows for x in pre)}")
    print(f"  last 16: {txt(toks[-16:])}")
    for r in range(max(map(len, b)) - 1, -1, -1):
        print("   ", "".join(x[r] if r < len(x) else "." for x in b).replace("F", "x").replace("S", "o"))
