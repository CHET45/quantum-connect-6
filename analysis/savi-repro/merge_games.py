"""Merge all server exports into one file of unique games (by id, first record that has a board wins)."""
import glob, json, zipfile, collections
games = {}
for p in sorted(glob.glob("../server-exports/*.zip")):
    z = zipfile.ZipFile(p)
    for n in z.namelist():
        if n.endswith("matches.jsonl"):
            for l in z.read(n).decode("utf8").splitlines():
                if l.strip():
                    r = json.loads(l)
                    if r["id"] not in games or (r.get("board") and not games[r["id"]].get("board")) or (r.get("winner") and not games[r["id"]].get("winner")):
                        games[r["id"]] = r
with open("games.jsonl", "w", encoding="utf8", newline="\n") as f:
    for r in games.values():
        f.write(json.dumps(r, ensure_ascii=False) + "\n")
names = collections.Counter(p["name"] for r in games.values() for p in r["players"])
print(len(games), "games")
for n, c in names.most_common():
    print(c, n)
