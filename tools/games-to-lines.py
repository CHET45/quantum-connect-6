"""Turns BookFast or BookMatch logs into lines the book builder can learn from (--self-lines).

Usage: python -I games-to-lines.py log.jsonl ... > lines.txt
Same format as make-book-lines.py, with the winner D for a draw and P_F the mean score of F over each log's games.
"""
import json
import re
import sys

TOKEN = re.compile(r"([FS])([0-9a-z])(\*?)")
MAX_PLIES = 40


def main():
    print("# rows x cols, 1, 1, winner, self|self, clean prefix (no blast, no skipped turn), mean score of F")
    for path in sys.argv[1:]:
        games = [json.loads(x) for x in open(path, encoding="utf-8") if x.strip()]
        if not games:
            continue
        f_scores = [g["scoreA"] if g["aFirst"] else 1 - g["scoreA"] for g in games]
        mean_f = sum(f_scores) / len(f_scores)
        for g, fs in zip(games, f_scores):
            prefix = []
            for i, (side, col, blast) in enumerate(TOKEN.findall(g["moves"])):
                if i >= MAX_PLIES or blast or side != ("F" if i % 2 == 0 else "S"):
                    break
                prefix.append(col)
            winner = "F" if fs == 1 else "S" if fs == 0 else "D"
            print(f"{g['rows']}x{g['cols']} 1 1 {winner} self|self {''.join(prefix)} {mean_f:.3f}")


main()
