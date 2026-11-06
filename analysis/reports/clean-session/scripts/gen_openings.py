"""Short random central openings for a tall narrow board, one per line, in the format of suites/openings-*.txt."""
import random
import sys

random.seed(11)
seen = set()
with open(sys.argv[1], "w", encoding="ascii") as f:
    while len(seen) < 400:
        n = random.choice((4, 6, 8))
        line = tuple(random.choice((2, 3, 4, 5, 5, 6, 7, 8)) for _ in range(n))
        if line not in seen:
            seen.add(line)
            f.write(sys.argv[2].join(map(str, line)) + "\n")
print("written", len(seen))
