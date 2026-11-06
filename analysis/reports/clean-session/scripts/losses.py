"""Our losses move by move, blast use per bot and size, opening habits of the Savi line, and timeouts vs memory lines."""
import collections
import json
import sys

TEAM = "No beaches?"
WIN = 6


def parse(moves):
    out, i = [], 0
    while i < len(moves):
        side, col = moves[i], int(moves[i + 1], 36)
        i += 2
        star = i < len(moves) and moves[i] == "*"
        i += star
        out.append((side, col, star))
    return out


def lines(cols, ncols):
    found = []
    get = lambda c, r: cols[c][r] if 0 <= c < ncols and 0 <= r < len(cols[c]) else None
    for c in range(ncols):
        for r in range(len(cols[c])):
            v = cols[c][r]
            for name, dc, dr in (("H", 1, 0), ("V", 0, 1), ("D+", 1, 1), ("D-", 1, -1)):
                if get(c - dc, r - dr) == v:
                    continue
                n = 1
                while get(c + dc * n, r + dr * n) == v:
                    n += 1
                if n >= WIN:
                    found.append((v, name, n, c, r))
    return found


def play(g):
    ncols = g["board"]["cols"]
    cols = [[] for _ in range(ncols)]
    for side, col, star in parse(g["moves"]):
        if star and len(cols[col]) >= g["board"]["rows"]:
            continue
        r = len(cols[col])
        cols[col].append(side)
        if star:
            for c in range(max(0, col - 1), min(ncols, col + 2)):
                for rr in sorted((x for x in (r - 1, r, r + 1) if 0 <= x < len(cols[c])), reverse=True):
                    del cols[c][rr]
    return cols


def picture(cols, rows):
    top = max(len(c) for c in cols)
    out = []
    for r in range(min(top, rows) - 1, -1, -1):
        out.append("   " + "".join(c[r] if r < len(c) else "." for c in cols).replace("F", "x").replace("S", "o"))
    return out


games = [json.loads(l) for l in open(sys.argv[1] + "/matches.jsonl", encoding="utf-8") if l.strip()]
size_of = lambda g: f"{g['board']['rows']}x{g['board']['cols']}"

print("=== our losses ===")
for g in sorted(games, key=lambda g: (size_of(g), g["fetchedAt"])):
    i_us = next((i for i, p in enumerate(g["players"]) if p["team"] == TEAM), None)
    if i_us is None or g["winner"] == "FS"[i_us]:
        continue
    toks = parse(g["moves"])
    cols = play(g)
    ls = lines(cols, g["board"]["cols"])
    me, opp = g["players"][i_us], g["players"][1 - i_us]
    bl = collections.Counter(s for s, _, st in toks if st)
    print(f"\n{size_of(g)} {me['name']} as {'FS'[i_us]} vs {opp['name']}: plies {len(toks)}, last {toks[-1]}, blasts F{bl['F']} S{bl['S']}, "
          f"six {[(l[0], l[1], l[2]) for l in ls]}, fill {sum(map(len, cols))}, max height {max(map(len, cols))}, id {g['id'][:8]}")
    print("   first 24:", "".join(f"{s}{np_}" for s, np_ in ((t[0], format(t[1], 'x') + ('*' if t[2] else '')) for t in toks[:24])))
    print("   last  16:", "".join(f"{s}{np_}" for s, np_ in ((t[0], format(t[1], 'x') + ('*' if t[2] else '')) for t in toks[-16:])))
    if size_of(g) == "15x11":
        print("\n".join(picture(cols, g["board"]["rows"])))

print("\n=== blasts per game and winning blasts, per bot and size ===")
tab = collections.defaultdict(lambda: [0, 0, 0, 0])
for g in games:
    toks = parse(g["moves"])
    for i, p in enumerate(g["players"]):
        e = tab[(size_of(g), p["name"])]
        e[0] += 1
        e[1] += sum(1 for s, _, st in toks if st and s == "FS"[i])
        e[2] += len(toks)
        e[3] += toks[-1][2] and toks[-1][0] == "FS"[i]
for size in ("8x12", "15x22", "15x11"):
    rows = sorted(((n, v) for (s, n), v in tab.items() if s == size), key=lambda x: -x[1][1] / x[1][0])
    print(f"  {size}: " + ", ".join(f"{n} {v[1] / v[0]:.2f}/g win-blasts {v[3]}" for n, v in rows))

print("\n=== opening habits (first own move, first reply), top bots on 15x11 and 15x22 ===")
for size in ("15x11", "15x22", "8x12"):
    for name in ('Savi8 - "The King"', 'Savi 7 - "The Prince', 'Savi7 - "The Prince"', "Savi5", 'Savi6 - "Beat this"', "Savi5 beater 2.1", "Savi beater 2.2"):
        firsts = collections.Counter()
        replies = collections.Counter()
        for g in games:
            if size_of(g) != size:
                continue
            toks = [t for t in parse(g["moves"])]
            for i, p in enumerate(g["players"]):
                if p["name"] != name:
                    continue
                own = [t for t in toks if t[0] == "FS"[i]]
                if not own:
                    continue
                if i == 0:
                    firsts[format(own[0][1], "x")] += 1
                else:
                    replies[f"{format(toks[0][1], 'x')}->{format(own[0][1], 'x')}"] += 1
        if firsts or replies:
            print(f"  {size} {name}: as F {dict(firsts.most_common(4))}; as S {dict(replies.most_common(6))}")

print("\n=== our lost turns vs memory line in the game, and vs game clock ===")
for name in ("Savi5 beater 2.1", "Savi beater 2.2", 'Savi8 - "The King"', 'Savi 7 - "The Prince', 'Savi7 - "The Prince"'):
    t = collections.Counter()
    for g in games:
        for i, p in enumerate(g["players"]):
            if p["name"] != name:
                continue
            side = "FIRST_PLAYER" if i == 0 else "SECOND_PLAYER"
            lost = sum(1 for e in g["errors"] if "timed out" in e and e.endswith(side))
            t[(size_of(g), bool(g["log"]["oom"]), lost > 0)] += 1
    print(" ", name)
    for size in ("8x12", "15x22", "15x11"):
        a, b, c, d = t[(size, True, True)], t[(size, True, False)], t[(size, False, True)], t[(size, False, False)]
        if a + b + c + d:
            print(f"    {size}: with memory line {a}/{a + b} games have own timeout; without {c}/{c + d}")

print("\n=== game length of decided games: plies between the Savi line and us, and in general ===")
for size in ("8x12", "15x22", "15x11"):
    v = sorted(len(parse(g["moves"])) for g in games if size_of(g) == size and all(p["name"].startswith("Savi") for p in g["players"]))
    a = sorted(len(parse(g["moves"])) for g in games if size_of(g) == size)
    if v:
        print(f"  {size}: both Savi-named n={len(v)} median {v[len(v) // 2]} max {v[-1]}; all n={len(a)} median {a[len(a) // 2]} max {a[-1]}")
