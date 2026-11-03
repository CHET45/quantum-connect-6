"""Folds the trained components and writes the .qcw blob the Java WeightBlob reads (design 3.7, 4.5).

zlib-deflated, big-endian: "QCW1", i32 version, rows, cols, k, i64 specHash, i32 scale, stages, cdContexts, classes,
codes, t1, t2, t3, then int16 W[stage][cd][class * codes + code], T1[cd][t1], T2[cd][t2], T3[t3].
"""
import struct
import zlib

import numpy as np

SCALE = 256
HEADER = ">iiiiqiiiiiiii"


def fold(model, scale=SCALE):
    """Integer tables (numpy int16) from the model's components: W [S, C, P], T1 [C, t1], T2 [C, t2], T3 [t3]."""
    v = {k: t.detach().double().cpu().numpy() for k, t in model.component_views().items()}
    w = v["base"][None, None, :] + v["stage"][:, None, :] + v["cd"][None, :, :]

    def q(x):
        r = np.rint(x * scale)
        if np.abs(r).max() > 32767:
            raise ValueError("weight outside int16 after scaling")
        return r.astype(np.int16)

    return {"W": q(w), "T1": q(v["t1"]), "T2": q(v["t2"]), "T3": q(v["t3"])}


def signed64(x):
    return x - (1 << 64) if x >= 1 << 63 else x


def write(path, spec, tables, scale=SCALE):
    head = b"QCW1" + struct.pack(HEADER, 1, spec.rows, spec.cols, spec.k, signed64(spec.spec_hash), scale,
                                 spec.stages, spec.cd_contexts, spec.classes, spec.codes, spec.t1, spec.t2, spec.t3)
    body = b"".join(tables[k].astype(">i2").tobytes() for k in ("W", "T1", "T2", "T3"))
    with open(path, "wb") as f:
        f.write(zlib.compress(head + body, 9))
