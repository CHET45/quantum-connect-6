"""How decided games end (forced entry under a standing threat vs tactics) and whether threat parity predicts the winner."""
import collections
import json
import sys

WIN = 6
DIRS = ((1, 0), (1, 1), (1, -1))
STRONG = ("Savi", "Testing_v2")


def parse(moves):
    out, i = [], 0
    while i < len(moves):
        side, col = moves[i], int(moves[i + 1], 36)
        i += 2
        star = i < len(moves) and moves[i] == "*"
        i += star
        out.append((side, col, star))
    return out


class B:
    def __init__(self, rows, cols):
        self.R, self.C = rows, cols
        self.c = [[] for _ in range(cols)]

    def at(self, c, y):
        return self.c[c][y] if 0 <= c < self.C and 0 <= y < len(self.c[c]) else None

    def copy(self):
        o = B(self.R, self.C)
        o.c = [list(x) for x in self.c]
        return o

    def discs(self):
        return sum(len(x) for x in self.c)

    def would_six(self, c, y, side):
        """Disc of side at the empty cell (c,y) completes a six, using only the discs on the board."""
        for dc, dy in DIRS:
            n = 1
            k = 1
            while self.at(c + dc * k, y + dy * k) == side:
                n += 1
                k += 1
            k = 1
            while self.at(c - dc * k, y - dy * k) == side:
                n += 1
                k += 1
            if n >= WIN:
                return True
        if y == len(self.c[c]) and y >= WIN - 1 and all(self.at(c, y - k) == side for k in range(1, WIN)):
            return True
        return False

    def any_six(self):
        for c in range(self.C):
            for y in range(len(self.c[c])):
                v = self.c[c][y]
                for dc, dy in ((1, 0), (0, 1), (1, 1), (1, -1)):
                    if self.at(c - dc, y - dy) == v:
                        continue
                    n = 1
                    while self.at(c + dc * n, y + dy * n) == v:
                        n += 1
                    if n >= WIN:
                        return True
        return False

    def apply(self, side, col, star):
        if star and len(self.c[col]) >= self.R:
            return
        y = len(self.c[col])
        self.c[col].append(side)
        if star:
            for c in range(max(0, col - 1), min(self.C, col + 2)):
                for rr in sorted((x for x in (y - 1, y, y + 1) if 0 <= x < len(self.c[c])), reverse=True):
                    del self.c[c][rr]

    def open_cols(self):
        return [c for c in range(self.C) if len(self.c[c]) < self.R]

    def wins_now(self, side, blast_ready):
        """Columns where side wins at once by a disc, or by a blast when it has one ready."""
        out = []
        for c in self.open_cols():
            if self.would_six(c, len(self.c[c]), side):
                out.append((c, False))
            elif blast_ready:
                t = self.copy()
                t.apply(side, c, True)
                if t.any_six():
                    out.append((c, True))
        return out


def classify(g):
    """Replays the game; returns the facts about its end and threat tables at checkpoints."""
    R, C = g["board"]["rows"], g["board"]["cols"]
    b = B(R, C)
    toks = parse(g["moves"])
    winner = g["winner"]
    loser = "S" if winner == "F" else "F"
    cd = {"F": 0, "S": 0}
    prev = "S"
    history = []
    for n, (side, col, star) in enumerate(toks):
        if side == prev:
            cd["S" if side == "F" else "F"] += 1
        history.append((b.copy(), side, dict(cd)))
        b.apply(side, col, star)
        cd[side] = 0 if star else cd[side] + 1
        prev = side
    wcol, wstar = toks[-1][1], toks[-1][2]
    res = {"plies": len(toks), "win_blast": wstar}
    pre, _, cd_end = history[-1]
    wy = len(pre.c[wcol])
    res["win_row"] = wy
    # Age: how long the winning cell has been a standing six-cell for the winner (disc wins only).
    age = None
    if not wstar:
        age = 0
        for h, _, _ in reversed(history):
            if wy >= len(h.c[wcol]) and h.would_six(wcol, wy, winner) if wy < h.R else False:
                age += 1
            else:
                break
    res["age"] = age
    # The loser's last decision: its moves, how many avoid an immediate loss.
    idx = next((i for i in range(len(toks) - 1, -1, -1) if toks[i][0] == loser), None)
    res["open"] = len(pre.open_cols())
    if idx is None:
        res["kind"] = "no loser move"
        return res, toks, history
    pos, _, cds = history[idx]
    moves = [(c, False) for c in pos.open_cols()]
    if cds[loser] >= 10:
        moves += [(c, True) for c in pos.open_cols()]
    safe = []
    wins = []
    winner_ready = cds[winner] >= 10
    for c, st in moves:
        t = pos.copy()
        t.apply(loser, c, st)
        if t.any_six():
            wins.append((c, st))
            continue
        if not t.wins_now(winner, winner_ready):
            safe.append((c, st))
    res["loser_open"] = len(pos.open_cols())
    res["loser_safe"] = len(safe)
    res["loser_moves"] = len(moves)
    res["same_col"] = idx == len(toks) - 2 and toks[idx][1] == wcol and not wstar
    threats_before = pos.wins_now(winner, winner_ready)
    res["threats_before"] = len(threats_before)
    if wins:
        res["kind"] = "missed own win"
    elif not safe:
        res["kind"] = "double/zugzwang" if threats_before else "zugzwang"
        # A forced entry: nothing immediate was on before the loser moved, and every move opens a win.
    else:
        res["kind"] = "had a safe move"
    return res, toks, history


def threat_table(b, mover):
    """Standing floating threats per side, split by the natural owner of the cell under the blind filling rule."""
    disc = b.discs()
    phi = (disc + (1 if mover == "S" else 0)) % 2  # F moves when the disc count has this parity
    out = {"F": [0, 0], "S": [0, 0]}
    for c in range(b.C):
        h = len(b.c[c])
        # Cells above the lowest standing threat of the same column do not matter: keep only the lowest per side.
        seen = {"F": False, "S": False}
        for y in range(h + 1, b.R):
            count_at = b.R * (b.C - 1) + y
            owner = "F" if count_at % 2 == phi else "S"
            for side in "FS":
                if not seen[side] and b.would_six(c, y, side):
                    seen[side] = True
                    out[side][0 if owner == side else 1] += 1
    return out


games = [json.loads(l) for l in open(sys.argv[1] + "/matches.jsonl", encoding="utf-8") if l.strip()]
size_of = lambda g: f"{g['board']['rows']}x{g['board']['cols']}"
is_strong = lambda p: p["name"].startswith(STRONG) and not p["name"].startswith("Testing_v2") or p["team"] == "No beaches?"


kinds = collections.Counter()
ages = collections.defaultdict(list)
parity = collections.defaultdict(collections.Counter)
for g in games:
    res, toks, history = classify(g)
    size = size_of(g)
    strong = sum(is_strong(p) for p in g["players"])
    cls = {2: "strong-strong", 1: "strong-weak", 0: "weak-weak"}[strong]
    kinds[(size, cls, res["kind"])] += 1
    if res["age"] is not None:
        ages[(size, cls)].append(res["age"])
    if res.get("kind") in ("zugzwang", "double/zugzwang", "had a safe move"):
        kinds[(size, cls, "  -> same column entry" if res.get("same_col") else "  -> other")] += 1
    # Parity test at checkpoints: a side alone holding a threat of its own parity.
    for frac in (0.3, 0.5, 0.7, 0.85):
        if len(history) < 30:
            continue
        t = int(len(history) * frac)
        h, side, _ = history[t]
        tab = threat_table(h, side)
        fg, fb, sg, sb = tab["F"][0], tab["F"][1], tab["S"][0], tab["S"][1]
        pred = "F" if fg and not sg else "S" if sg and not fg else None
        key = (size, cls, frac)
        parity[key]["games"] += 1
        if pred:
            parity[key]["pred"] += 1
            parity[key]["hit"] += pred == g["winner"]
        any_pred = "F" if (fg + fb) and not (sg + sb) else "S" if (sg + sb) and not (fg + fb) else None
        if any_pred:
            parity[key]["any_pred"] += 1
            parity[key]["any_hit"] += any_pred == g["winner"]
        more = (fg + fb) - (sg + sb)
        if more:
            parity[key]["more_pred"] += 1
            parity[key]["more_hit"] += ("F" if more > 0 else "S") == g["winner"]

print("how decided games end: (size, class, kind): count")
for k, v in sorted(kinds.items()):
    print(f"  {k}: {v}")
print("\nage of the winning cell as a standing six-cell, in plies (disc wins)")
for k, v in sorted(ages.items()):
    v.sort()
    n = len(v)
    print(f"  {k}: n={n} median {v[n // 2]} p75 {v[3 * n // 4]} p90 {v[9 * n // 10]} share>=10 {sum(a >= 10 for a in v) / n:.2f} share>=30 {sum(a >= 30 for a in v) / n:.2f}")
print("\nthreat parity as a predictor. pred = one side alone has a good-parity floating threat; any = one side alone has any floating threat; more = more threats")
for k in sorted(parity):
    p = parity[k]
    f = lambda a, b: f"{p[a]}/{p[b]}={p[a] / p[b]:.2f}" if p[b] else "-"
    print(f"  {k}: games {p['games']}; good-parity hit {f('hit', 'pred')}; any-threat hit {f('any_hit', 'any_pred')}; more-threats hit {f('more_hit', 'more_pred')}")
