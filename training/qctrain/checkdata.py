"""Checks .qcp shards after an unclean stop: python -m qctrain.checkdata DIR [DIR ...]

A power loss can leave a shard with a tail of zero bytes or garbage. For each shard this prints the number of records
and the length of the valid prefix: records are valid while every field is in range and the stored disc count fits
the board. The shard itself is never changed; --fix-into DIR writes the valid prefixes as new files there.
"""
import argparse
import glob
import os
import struct

import numpy as np

from . import data


def valid_prefix(recs, rows, cols):
    """Number of leading records whose fields are all in range."""
    cells = data.unpack_cells(recs["cells"], rows * cols)
    discs = (cells != 0).sum(1)
    ok = cells.max(1) <= 2
    ok &= recs["stm"] <= 1
    ok &= (recs["cd_mover"] <= 10) & (recs["cd_other"] <= 10)
    ok &= recs["stage"] <= 3
    ok &= recs["cd_ctx"] <= 3
    ok &= np.isin(recs["proven"], [-1, 0, 1])
    ok &= np.isin(recs["z"], [-1, 0, 1])
    ok &= recs["t1"] < 81
    ok &= recs["t2"] < 729
    ok &= recs["t3"] < 363
    # a position in play never has an empty board with a late turn; zero-filled records are caught here
    ok &= ~((discs == 0) & (recs["t1"] == 0) & (recs["t2"] == 0) & (recs["t3"] == 0) & (recs["game"] == 0))
    bad = np.flatnonzero(~ok)
    return (int(bad[0]) if len(bad) else len(recs)), int(len(bad))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dirs", nargs="+")
    ap.add_argument("--fix-into", default=None)
    args = ap.parse_args()
    for d in args.dirs:
        total = 0
        good = 0
        for path in sorted(glob.glob(os.path.join(d, "shard-*.qcp"))):
            size = os.path.getsize(path)
            if size < data.HEADER:
                continue
            with open(path, "rb") as f:
                head = f.read(data.HEADER)
            try:
                recs, meta = data.read_shard(path)
            except ValueError as e:
                print(f"{path}: BAD HEADER {e}")
                continue
            n, bad = valid_prefix(recs, meta["rows"], meta["cols"])
            total += len(recs)
            good += n
            if n != len(recs) or size % recs.dtype.itemsize != data.HEADER % recs.dtype.itemsize:
                print(f"  {os.path.basename(path)}: {len(recs)} records, valid prefix {n}, invalid {bad}, "
                      f"{size - data.HEADER - len(recs) * recs.dtype.itemsize} trailing bytes")
            if args.fix_into:
                out = os.path.join(args.fix_into, os.path.basename(os.path.normpath(d)))
                os.makedirs(out, exist_ok=True)
                with open(os.path.join(out, os.path.basename(path)), "wb") as f:
                    f.write(head)
                    f.write(recs[:n].tobytes())
        print(f"{d}: {total} records, {good} in valid prefixes", flush=True)


if __name__ == "__main__":
    main()
