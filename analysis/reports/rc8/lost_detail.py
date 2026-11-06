"""Our games with lost turns: opponent, play time (from error lines), our lost count, timeout lines, result."""
import collections, json, re, sys
sys.path.insert(0, __file__.rsplit("/", 1)[0].rsplit("\\", 1)[0])
TEAM = "No beaches?"
TOK = re.compile(r"([FS])(\[\d+\]|[0-9a-z])(\*?)")
new_path, old_path = sys.argv[1], sys.argv[2]
old = {json.loads(l)["id"] for l in open(old_path, encoding="utf-8") if l.strip()}
games = [json.loads(l) for l in open(new_path, encoding="utf-8") if l.strip() and json.loads(l)["id"] not in old]
byopp = collections.defaultdict(collections.Counter)
rows = []
for g in games:
    p = g["players"]; us = [i for i in (0, 1) if p[i]["team"] == TEAM]
    if len(us) != 1: continue
    i = us[0]; me = "FS"[i]; opp = p[1 - i]["name"]; role = ["FIRST_PLAYER", "SECOND_PLAYER"][i]
    ts = TOK.findall(g["moves"]); prev = "S"; own = {"F": 0, "S": 0}; lost = []
    for s, _, _ in ts:
        if s == prev:
            o = "S" if s == "F" else "F"; lost.append((o, own[o])); own[o] += 1
        own[s] += 1; prev = s
    mine = [t for o, t in lost if o == me]; theirs = [t for o, t in lost if o != me]
    err = g.get("errors") or []
    t_me = sum(1 for e in err if "timed out" in e and e.rstrip().endswith(role))
    t_first = sum(1 for e in err if "Operation timed out" in e and role in e)
    when = err[0][:16] if err else "?"
    c = byopp[opp]; c["g"] += 1; c["W"] += g["winner"] == me; c["mine"] += len(mine); c["theirs"] += len(theirs)
    c["myT"] += own[me]; c["gl"] += bool(mine)
    if mine: rows.append((when, opp, len(mine), mine[:6], t_me, own[me], "W" if g["winner"] == me else "L", len(err)))
print("| opponent | games | W | our lost | games with our lost | their lost |")
print("|---|---|---|---|---|---|")
for o, c in sorted(byopp.items(), key=lambda x: -x[1]["g"]):
    print(f"| {o} | {c['g']} | {c['W']} | {c['mine']} | {c['gl']} | {c['theirs']} |")
print("\nwhen opp ourLost firstTurns ourTimeoutLines ownTurns result errLines")
for r in sorted(rows): print(*r)
