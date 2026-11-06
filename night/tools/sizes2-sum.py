"""Summary of sizes2 runs: per label and size, score of A, reasons, plies, likely 30 s server draws, faults."""
import json, math, os, sys, glob
root = sys.argv[1]
SERVER_PLIES = 370  # ~81 ms per ply in self-play -> 30 s
for lab in sorted(os.listdir(root)):
    d = os.path.join(root, lab)
    if not os.path.isdir(d) or lab == "smoke": continue
    print(f"## {lab}")
    print("| size | games | A score | Elo +-95% | six/blast/full | median plies | >370 plies | to A/B | inv/crash |")
    print("|---|---|---|---|---|---|---|---|---|")
    files = sorted(glob.glob(os.path.join(d, "*.jsonl")), key=lambda f: (lambda r, c: (int(r) * int(c)))(*os.path.basename(f)[:-6].split("x")))
    for f in files:
        g = [json.loads(l) for l in open(f) if l.strip()]
        if not g: continue
        n = len(g); s = sum(x["scoreA"] for x in g) / n
        # pair-based SE
        pairs = {}
        for x in g: pairs.setdefault(x["pair"], []).append(x["scoreA"])
        ps = [sum(v) / len(v) for v in pairs.values()]
        m = sum(ps) / len(ps)
        se = math.sqrt(sum((p - m) ** 2 for p in ps) / max(1, len(ps) - 1) / len(ps)) if len(ps) > 1 else 0
        elo = lambda p: -400 * math.log10(1 / min(max(p, 1e-3), 1 - 1e-3) - 1)
        lo, hi = elo(m - 1.96 * se), elo(m + 1.96 * se)
        r = {}
        for x in g: r[x["reason"]] = r.get(x["reason"], 0) + 1
        pl = sorted(x["plies"] for x in g)
        long_ = sum(p > SERVER_PLIES for p in pl)
        ta = sum(x["timeoutsA"] for x in g); tb = sum(x["timeoutsB"] for x in g)
        inv = sum(x["invalidA"] + x["invalidB"] + x["badSpecialsA"] + x["badSpecialsB"] for x in g)
        print(f"| {os.path.basename(f)[:-6]} | {n} | {100*s:.1f}% | {elo(m):+.0f} [{lo:+.0f}, {hi:+.0f}] | "
              f"{r.get('six',0)}/{r.get('blast',0)}/{r.get('full',0)+r.get('cap',0)} | {pl[n//2]} | {long_} | {ta}/{tb} | {inv}/{r.get('crash',0)} |")
    print()
