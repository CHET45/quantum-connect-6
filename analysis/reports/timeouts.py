"""Our timeouts per game in a server export: who, when, with which opponent, and what the moves field holds."""
import collections
import json
import sys

TEAM = "No beaches?"
path = sys.argv[1]
rows = []
for line in open(path, encoding="utf-8"):
    if not line.strip():
        continue
    m = json.loads(line)
    p = m["players"]
    us = 1 if p[0]["team"] == TEAM else 2 if p[1]["team"] == TEAM else 0
    if not us:
        continue
    side = "FIRST_PLAYER" if us == 1 else "SECOND_PLAYER"
    ours = sum(1 for e in m["errors"] if "timed out" in e and e.endswith(side))
    theirs = sum(1 for e in m["errors"] if "timed out" in e and not e.endswith(side))
    ooms = sum(1 for e in m["errors"] if "Memory limit exceeded:" in e)
    other = p[2 - us]["name"]
    first_ts = m["errors"][0][:23] if m["errors"] else ""
    rows.append((m["fetchedAt"], first_ts, other, us, ours, theirs, ooms, len(m["moves"]), m.get("win_at"),
                 m["labels"].get(p[us - 1]["lb"]), m["id"][:8]))
rows.sort(key=lambda r: r[1] or r[0])
print("games", len(rows), "our timeouts", sum(r[4] for r in rows), "games with our timeouts",
      sum(1 for r in rows if r[4]))
print("empty moves field:", sum(1 for r in rows if r[7] == 0))
by = collections.Counter()
for r in rows:
    by[r[2]] += r[4]
print("our timeouts by opponent:", dict(by.most_common()))
print("\nfirst-log-ts | fetched | opponent | side | ours | theirs | oom lines | moves chars | win_at | label | id")
for r in rows:
    if r[4] or r[7] == 0:
        print(" | ".join(str(x) for x in (r[1], r[0][:16], r[2], "F" if r[3] == 1 else "S", r[4], r[5], r[6],
                                          r[7], r[8], r[9], r[10])))
