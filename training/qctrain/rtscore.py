"""Score of a MatchMain output file: python -m qctrain.rtscore FILE.jsonl ... (score of A, 95 percent CI over pairs, timeouts)."""
import json
import sys

from .gate import elo, stats


def main():
    for path in sys.argv[1:]:
        by_pair = {}
        reasons = {}
        turns_lost = [0, 0]
        games = 0
        with open(path, encoding="utf-8") as f:
            for line in f:
                r = json.loads(line)
                by_pair.setdefault(r["pair"], []).append(r["scoreA"])
                reasons[r["reason"]] = reasons.get(r["reason"], 0) + 1
                turns_lost[0] += r["timeoutsA"]
                turns_lost[1] += r["timeoutsB"]
                games += 1
        full = [sum(v) / len(v) for v in by_pair.values() if len(v) == 2]
        m, ci = stats(full)
        print(f"{path}: {games} games, {len(full)} full pairs, A {100 * m:.1f}% +-{100 * ci:.1f} ({elo(m):+.0f} Elo), "
              f"{reasons}, timeouts A {turns_lost[0]} B {turns_lost[1]}")


if __name__ == "__main__":
    main()
