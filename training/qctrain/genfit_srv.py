"""genfit with an extra source: labelled server positions in ROOT/<server dir>/<size> (ServerQcp records).

Usage: python -m qctrain.genfit_srv NAME [--server server1] [--server-weight 3.0] [--list] [fit_generic args...]
--server-weight 0 leaves the server data out (the control fit). Everything else is as in genfit.
"""
import argparse
import glob
import json
import math
import os
import subprocess
import sys

from qctrain import genfit


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("name")
    ap.add_argument("--server", default="server1")
    ap.add_argument("--server-weight", type=float, default=3.0)
    ap.add_argument("--k-std", type=float, default=1.8)
    ap.add_argument("--list", action="store_true")
    args, rest = ap.parse_known_args()
    root = genfit.ROOT
    with open(os.path.join(root, "current.json"), encoding="utf-8") as f:
        cur = json.load(f)
    found = genfit.collect(set(cur["holdout"]))
    if args.server_weight > 0:
        for d in sorted(glob.glob(os.path.join(root, args.server, "*x*"))):
            if os.path.isdir(d) and glob.glob(os.path.join(d, "shard-*.qcp")):
                found.setdefault(genfit.size_of(d), {})["server"] = [d]
    train_dir = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    sets = []
    total = 0
    for size in sorted(found):
        sources = found[size]
        counts = {k: genfit.records(v) for k, v in sources.items()}
        sources = {k: v for k, v in sources.items() if counts[k] >= genfit.MIN_SET}
        counts = {k: c for k, c in counts.items() if c >= genfit.MIN_SET}
        if not sources:
            continue
        n = sum(counts.values())
        total += n
        share = (2.0 if size in genfit.SEEN else 1.0) * min(1.0, math.sqrt(n / 60000))
        newest = max((k for k in sources if isinstance(k, int)), default=None)
        weight = {k: c * (0.3 if k == "hand" else args.server_weight if k == "server" else 2.0 if k == newest else 1.0)
                  for k, c in counts.items()}
        for source, dirs in sources.items():
            part = share * weight[source] / sum(weight.values())
            k = "256" if source == "hand" else ""
            sets.append((size, source, dirs, k, part, counts[source]))
    for size, source, dirs, k, part, c in sets:
        if source in ("server",) or size in ("15x30", "8x12", "15x100"):
            print(f"{size:>6} {str(source):>6} {c:>8} records, share {part:.3f}", flush=True)
    print(f"{len(sets)} sets, {total} records", flush=True)
    if args.list:
        return 0
    fit_args = []
    for size, source, dirs, k, part, c in sets:
        rel = ",".join(os.path.relpath(d, train_dir).replace(os.sep, "/") for d in dirs)
        fit_args += ["--set", f"spec/generic-{size}.json:{rel}:{k}:{part:.4f}"]
    fit_args += ["--k-std", str(args.k_std), "--balance", "--temp", "--wd-base", "1", "--wd-delta", "10", "--lr", "1e-3",
            "--lr-end", "1e-5", "--epochs", "20", "--patience", "6", "--out", os.path.join(root, "models", args.name)] + rest
    argfile = os.path.join(root, "logs", args.name + ".args")
    with open(argfile, "w", encoding="utf-8") as f:
        f.write("\n".join(fit_args) + "\n")
    cmd = [sys.executable, "-m", "qctrain.argfile_run", "qctrain.fit_generic", argfile]
    log = os.path.join(root, "logs", args.name + ".log")
    os.chdir(train_dir)
    with open(log, "w", encoding="utf-8") as f:
        wrapped = [sys.executable, "-m", "qctrain.budget", os.path.join(root, "gpu.lock")] + cmd
        return subprocess.call(wrapped, stdout=f, stderr=subprocess.STDOUT)


if __name__ == "__main__":
    sys.exit(main())
