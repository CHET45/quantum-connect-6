"""Board sizes of the matches in the snapshots (structured moves), by time, and our results per size."""
import collections
import glob
import json
import sys

TEAM = "No beaches?"
seen = {}
for path in sorted(glob.glob(sys.argv[1] + "/snapshots/*.json")):
    snap = json.load(open(path, encoding="utf-8"))
    for m in snap.get("matches", []):
        b = m.get("board")
        seen.setdefault(m["id"], (snap["fetchedAt"], b, m))
sizes = collections.Counter()
first_big = None
for mid, (t, b, m) in seen.items():
    key = f"{b['rows']}x{b['cols']} win {b.get('win')} blast {b.get('blast')}" if b else "no board field"
    sizes[key] += 1
    if b and b["rows"] != 8 and (first_big is None or t < first_big):
        first_big = t
print("matches in snapshots:", len(seen), dict(sizes))
print("first non-8-row snapshot:", first_big)
chars = collections.Counter()
for line in open(sys.argv[1] + "/matches.jsonl", encoding="utf-8"):
    m = json.loads(line)
    s = m["moves"]
    for i, ch in enumerate(s):
        if ch not in "FS*":
            chars[ch] += 1
print("column chars in matches.jsonl moves:", "".join(sorted(chars)))
ours = collections.defaultdict(collections.Counter)
maxcol = collections.Counter()
for mid, (t, b, m) in seen.items():
    p = m["players"]
    us = 1 if p[0]["team"] == TEAM else 2 if p[1]["team"] == TEAM else 0
    size = f"{b['rows']}x{b['cols']}" if b else "?"
    mc = max((mv["column"] for mv in m["moves"]), default=-1)
    maxcol[(size, mc >= 12)] += 1
    if us:
        lab = m.get("labels", {}).get(p[us - 1]["lb"], "?") if isinstance(m.get("labels"), dict) else "?"
        ours[size][lab] += 1
print("our results by size:", {k: dict(v) for k, v in ours.items()})
print("(size, any column >= 12):", dict(maxcol))
print("sample keys:", list(next(iter(seen.values()))[2].keys()))
