"""Who loses the crash games in the gate logs: the harness scores a crash against the bot that caused it, if it can tell."""
import glob
import json
import os
import re
import sys

base = sys.argv[1]
for f in sorted(glob.glob(base + "/gate-*.jsonl")):
    g = [json.loads(l) for l in open(f) if l.strip()]
    c = [x for x in g if x["reason"] == "crash"]
    noc = [x for x in g if x["reason"] != "crash"]
    s = sum(x["scoreA"] for x in noc) / max(1, len(noc))
    print(os.path.basename(f), "games", len(g), "crashes", len(c), "A lost", sum(x["scoreA"] == 0 for x in c), "A won", sum(x["scoreA"] == 1 for x in c),
          "| score without crash games %.1f%% of %d" % (100 * s, len(noc)),
          "| timeouts in crash games A/B", sum(x["timeoutsA"] for x in c), sum(x["timeoutsB"] for x in c))
    print("   ", [(x["pair"], x["game"], x["aFirst"], x["scoreA"], x["plies"], x["timeoutsA"], x["timeoutsB"]) for x in c][:10])
    log = f[:-6] + ".log"
    if os.path.exists(log):
        t = open(log, encoding="utf-8", errors="replace").read()
        m = re.findall(r"(?m)^.*(?:crash|Crash|isSpaceEmpty|ArrayIndex).*$", t)
        print("    log lines:", len(m), m[:2] if m else "")
