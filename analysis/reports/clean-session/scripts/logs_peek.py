"""Log formats: templates in errors.csv/occurrences.csv and what a snapshot's outputLog/errorLog hold."""
import collections, csv, glob, json, re, sys
d = sys.argv[1]
for name in ("errors.csv", "occurrences.csv"):
    with open(f"{d}/{name}", encoding="utf-8", newline="") as f:
        r = csv.reader(f)
        head = next(r)
        rows = list(r)
    print(name, head, len(rows))
    for row in rows[:3]:
        print("  ", [c[:110] for c in row])
    idx = next((i for i, h in enumerate(head) if h.lower() in ("message", "error", "text", "line", "msg")), len(head) - 1)
    t = collections.Counter()
    for row in rows:
        s = row[idx] if idx < len(row) else ""
        s = re.sub(r"\d{4}-\d\d-\d\dT[\d:.]+Z", "<ts>", s)
        s = re.sub(r"[0-9a-f]{8}-[0-9a-f-]{27}", "<id>", s)
        s = re.sub(r"\d+(\.\d+)?", "#", s)
        t[s[:130]] += 1
    print("  templates:", len(t))
    for s, n in t.most_common(30):
        print(f"  {n:7d}  {s}")
paths = sorted(glob.glob(f"{d}/snapshots/*.json"))
snap = json.load(open(paths[-1], encoding="utf-8"))
m = snap["matches"][0]
for k in ("outputLog", "errorLog"):
    v = m.get(k)
    print(k, type(v).__name__, (len(v) if v is not None else 0))
    print(repr(v)[:1200])
print(json.dumps(snap["bots"][0])[:700])
