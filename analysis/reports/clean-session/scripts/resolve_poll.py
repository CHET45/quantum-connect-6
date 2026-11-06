"""Resolves the merge conflict in Searcher.poll: the time stream's stall sensor, read at the speed stream's clock interval."""
import re
import sys

path = sys.argv[1]
s = open(path, encoding="utf-8", newline="").read()
new = """    if ((x.nodes & CLOCK_MASK) == 0) {
      long now = Clock.nanos();
      long gap = now - x.lastPoll;
      x.lastPoll = now;
      if (gap > x.maxGap) {
        x.maxGap = gap;
        if (x.stallLimit > 0) {
          x.deadline = Math.min(x.deadline, x.start + x.stallLimit - gap);
        }
      }
      if (now > x.deadline || (x.leadingBlast && now > x.blastDeadline)) {
        x.stop = true;
      }
    }
    if (x.nodes > x.maxNodes || token != x.token) {
"""
s, n = re.subn(r"<<<<<<< HEAD\r?\n.*?>>>>>>> night-time\r?\n", new, s, flags=re.S)
assert n == 1, n
open(path, "w", encoding="utf-8", newline="").write(s)
print("resolved", n)
