"""Parity file for the Java test (design 4.6): positions with the integer sum of the exported tables, computed here
by plain numpy (independent of the torch model), and the float logit of the model.

python -m qctrain.parity --spec spec/feature-spec-8x12.json --model ../data/models/gen001 --data ../data/gen000

File (little-endian): "QPV1", i32 count, i32 cells, then per record: cells (u8 each, 0/1/2, Geometry order),
u8 stm (1/2), u8 cdMover, u8 cdOther, i32 integer sum, u16 t1, u16 t2, u16 t3, f32 model logit.
"""
import argparse
import dataclasses
import os
import struct
import zlib

import numpy as np
import torch

from . import data, export, model as model_mod, spec as spec_mod


def read_qcw(path, spec):
    raw = zlib.decompress(open(path, "rb").read())
    if raw[:4] != b"QCW1":
        raise ValueError("not a QCW1 blob")
    off = 4 + struct.calcsize(export.HEADER)
    a = np.frombuffer(raw[off:], dtype=">i2").astype(np.int64)
    s, c, p = spec.stages, spec.cd_contexts, spec.window_params
    w = a[:s * c * p].reshape(s, c, p)
    o = s * c * p
    t1 = a[o:o + c * spec.t1].reshape(c, spec.t1)
    o += c * spec.t1
    t2 = a[o:o + c * spec.t2].reshape(c, spec.t2)
    o += c * spec.t2
    t3 = a[o:o + spec.t3]
    return w, t1, t2, t3


def integer_sums(spec, tables, cells, stm, stage, cd, t1, t2, t3):
    w, tab1, tab2, tab3 = tables
    c = cells.astype(np.int64)
    rel = np.where((stm[:, None] == 2) & (c > 0), 3 - c, c)
    pow3 = 3 ** np.arange(spec.k)
    codes = (rel[:, spec.win_cells] * pow3).sum(-1)  # [N, W]
    fm = spec.feat_map.reshape(spec.windows, spec.codes)
    idx = fm[np.arange(spec.windows)[None, :], codes]
    total = w[stage[:, None], cd[:, None], idx].sum(1)
    return total + tab1[cd, t1] + tab2[cd, t2] + tab3[t3]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--spec", default="spec/feature-spec-8x12.json")
    ap.add_argument("--model", required=True)
    ap.add_argument("--data", nargs="+", required=True)
    ap.add_argument("--count", type=int, default=100_000)
    args = ap.parse_args()
    spec = spec_mod.load(args.spec)
    # a blob fitted with --no-stage has one stage (see export.HEADER: version, rows, cols, k, hash, scale, stages)
    stages = struct.unpack_from(export.HEADER, zlib.decompress(open(os.path.join(args.model, "eval.qcw"), "rb").read()), 4)[6]
    if stages != spec.stages:
        spec = dataclasses.replace(spec, stages=stages)
    recs, _, _ = data.read_dirs(args.data)
    rng = np.random.default_rng(5)
    recs = recs[rng.choice(len(recs), size=min(args.count, len(recs)), replace=False)]
    cells = data.unpack_cells(recs["cells"], spec.rows * spec.cols)
    stm = recs["stm"].astype(np.int64) + 1
    stage = recs["stage"].astype(np.int64) if spec.stages > 1 else np.zeros(len(recs), dtype=np.int64)
    cd = recs["cd_ctx"].astype(np.int64)
    t1, t2, t3 = (recs[k].astype(np.int64) for k in ("t1", "t2", "t3"))
    tables = read_qcw(os.path.join(args.model, "eval.qcw"), spec)
    ints = integer_sums(spec, tables, cells, stm, stage, cd, t1, t2, t3)
    m = model_mod.Model(spec, "cpu")
    m.theta.data.copy_(torch.load(os.path.join(args.model, "theta.pt")))
    with torch.no_grad():
        q = m(torch.as_tensor(cells), torch.as_tensor(stm), torch.as_tensor(stage), torch.as_tensor(cd),
              torch.as_tensor(t1), torch.as_tensor(t2), torch.as_tensor(t3)).numpy()
    err = np.abs(ints / 256.0 - q).max()
    out = os.path.join(args.model, f"parity-{spec.rows}x{spec.cols}.qpv")
    with open(out, "wb") as f:
        f.write(b"QPV1" + struct.pack("<ii", len(recs), spec.rows * spec.cols))
        for i in range(len(recs)):
            f.write(cells[i].tobytes())
            f.write(struct.pack("<BBBiHHHf", stm[i], recs["cd_mover"][i], recs["cd_other"][i], int(ints[i]),
                                t1[i], t2[i], t3[i], float(q[i])))
    print(f"{len(recs)} parity records -> {out}; max |int/256 - float| = {err:.4f} logit ({err * 256:.1f} units)")


if __name__ == "__main__":
    main()
