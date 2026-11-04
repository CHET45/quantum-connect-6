"""Fits a candidate generation from everything under the eval data directory, keeping the hold-out sizes out of it.

python -m qctrain.genfit NAME [--warm MODEL] [--k-std 1.8] [fit_generic options...]

It walks gen*/, hand0/, sized-*/ and the older hand-labelled directories, makes one set per (board size, label source) and
runs fit_generic through the fit lock. Hold-out sizes come from current.json and are never used. A set's share of the
batches: the size's share (seen sizes 2, all others 1, less for a size with few records) split between its sources by
record count, a record of the newest generation counting 2, of an older one 1, a hand-labelled one 0.3. Nothing here feeds
the size into the blob: K and the per-set output scale exist only during the fit.
"""
import argparse
import glob
import json
import math
import os
import subprocess
import sys

from . import budget, data

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..", "qc-bot", "data", "night", "eval"))
DATA = os.path.abspath(os.path.join(ROOT, "..", ".."))
SEEN = {"15x11", "15x22", "8x12"}
HAND = {"15x11": ["x11/gen000"], "15x22": ["x15/gen000", "x15/gen000b"]}
OLD_8X12 = ["it03-partial"]
MIN_SET = 2000


def records(dirs):
    n = 0
    for d in dirs:
        for path in glob.glob(os.path.join(d, "shard-*.qcp")):
            size = os.path.getsize(path)
            if size > data.HEADER:
                with open(path, "rb") as f:
                    head = f.read(data.HEADER)
                n += (size - data.HEADER) // int.from_bytes(head[10:12], "little")
    return n


def size_of(dirname):
    return os.path.basename(dirname).split("-")[0]


def collect(holdout, only_generations=None):
    """{size: {source: [dirs]}}, source = 'hand', or an integer generation; 8x12 tables of gen003 count as 'sized'."""
    found = {}

    def add(size, source, d):
        if size not in holdout and glob.glob(os.path.join(d, "shard-*.qcp")):
            found.setdefault(size, {}).setdefault(source, []).append(d)

    for gen_dir in sorted(glob.glob(os.path.join(ROOT, "gen[0-9]*"))):
        g = int(os.path.basename(gen_dir)[3:])
        if only_generations and g not in only_generations:
            continue
        for d in sorted(glob.glob(os.path.join(gen_dir, "*x*"))):
            if os.path.isdir(d):
                add(size_of(d), g, d)
    for sized_dir in sorted(glob.glob(os.path.join(ROOT, "sized-*"))):
        for d in sorted(glob.glob(os.path.join(sized_dir, "*x*"))):
            if os.path.isdir(d):
                add(size_of(d), "sized", d)
    for d in sorted(glob.glob(os.path.join(ROOT, "hand0", "*x*"))):
        if os.path.isdir(d):
            add(size_of(d), "hand", d)
    for size, dirs in HAND.items():
        for rel in dirs:
            add(size, "hand", os.path.join(DATA, rel))
    for rel in OLD_8X12:
        add("8x12", "sized", os.path.join(DATA, rel))
    return found


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("name")
    ap.add_argument("--no-hand", action="store_true", help="leave out hand-labelled data")
    ap.add_argument("--k-std", type=float, default=1.8)
    ap.add_argument("--list", action="store_true", help="print the sets and stop")
    args, rest = ap.parse_known_args()
    with open(os.path.join(ROOT, "current.json"), encoding="utf-8") as f:
        cur = json.load(f)
    found = collect(set(cur["holdout"]))
    train_dir = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    sets = []
    total = 0
    for size in sorted(found):
        sources = found[size]
        if args.no_hand:
            sources = {k: v for k, v in sources.items() if k != "hand"}
        if not sources:
            continue
        counts = {k: records(v) for k, v in sources.items()}
        # a set this small is a self-play chunk that has only just started
        sources = {k: v for k, v in sources.items() if counts[k] >= MIN_SET}
        counts = {k: c for k, c in counts.items() if c >= MIN_SET}
        if not sources:
            continue
        n = sum(counts.values())
        total += n
        share = (2.0 if size in SEEN else 1.0) * min(1.0, math.sqrt(n / 60000))
        newest = max((k for k in sources if isinstance(k, int)), default=None)
        weight = {k: c * (0.3 if k == "hand" else 2.0 if k == newest else 1.0) for k, c in counts.items()}
        for source, dirs in sources.items():
            part = share * weight[source] / sum(weight.values())
            k = ""
            # the older label sets keep the scale they were shown to work with; all others use --k-std
            if source == "hand":
                k = "256"
            sets.append((size, source, dirs, k, part, counts[source]))
    for size, source, dirs, k, part, c in sets:
        print(f"{size:>6} {str(source):>6} {c:>8} records, share {part:.3f}, K {k or 'by spread'}", flush=True)
    print(f"{len(sets)} sets, {total} records", flush=True)
    if args.list:
        return 0
    cmd = [sys.executable, "-m", "qctrain.fit_generic"]
    for size, source, dirs, k, part, c in sets:
        # relative paths: a drive letter's colon would split the argument
        rel = ",".join(os.path.relpath(d, train_dir).replace(os.sep, "/") for d in dirs)
        cmd += ["--set", f"spec/generic-{size}.json:{rel}:{k}:{part:.4f}"]
    cmd += ["--k-std", str(args.k_std), "--balance", "--temp", "--wd-base", "1", "--wd-delta", "10", "--lr", "1e-3",
            "--lr-end", "1e-5", "--epochs", "20", "--patience", "6", "--out", os.path.join(ROOT, "models", args.name)] + rest
    log = os.path.join(ROOT, "logs", args.name + ".log")
    os.chdir(train_dir)
    with open(log, "w", encoding="utf-8") as f:
        wrapped = [sys.executable, "-m", "qctrain.budget", os.path.join(ROOT, "gpu.lock")] + cmd
        return subprocess.call(wrapped, stdout=f, stderr=subprocess.STDOUT)


if __name__ == "__main__":
    sys.exit(main())
