"""Copies a .qcw blob with every table multiplied by a factor, which multiplies the bot's evaluation by it.

python -m qctrain.rescale IN.qcw OUT.qcw 1.5

Order within one context does not change much (rounding aside), but the search compares evaluations with fixed margins
(razoring), and tables fitted with a learned output scale are smaller than calibrated logits by that scale.
"""
import struct
import sys
import zlib

import numpy as np

from . import export


def main():
    src, dst, factor = sys.argv[1], sys.argv[2], float(sys.argv[3])
    raw = zlib.decompress(open(src, "rb").read())
    start = 4 + struct.calcsize(export.HEADER)
    tables = np.frombuffer(raw[start:], dtype=">i2").astype(np.float64) * factor
    if np.abs(tables).max() > 32767:
        raise SystemExit("a table value leaves int16 after scaling")
    body = np.rint(tables).astype(">i2").tobytes()
    with open(dst, "wb") as f:
        f.write(zlib.compress(raw[:start] + body, 9))


if __name__ == "__main__":
    main()
