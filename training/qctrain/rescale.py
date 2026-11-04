"""Copies a .qcw blob with another header scale, which multiplies the bot's evaluation by old scale / new scale.

python -m qctrain.rescale IN.qcw OUT.qcw 256

Only 1024 (shift 2) and 256 (shift 0) are read by the bot. Used to test how much the absolute size of the evaluation
matters to the search (razoring margins), which does not change the order of positions.
"""
import struct
import sys
import zlib

from . import export


def main():
    src, dst, scale = sys.argv[1], sys.argv[2], int(sys.argv[3])
    raw = bytearray(zlib.decompress(open(src, "rb").read()))
    fields = list(struct.unpack_from(export.HEADER, raw, 4))
    fields[5] = scale
    struct.pack_into(export.HEADER, raw, 4, *fields)
    with open(dst, "wb") as f:
        f.write(zlib.compress(bytes(raw), 9))


if __name__ == "__main__":
    main()
