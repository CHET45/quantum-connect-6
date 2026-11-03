"""Reader for the .qcp shards of SelfPlayMain (see the Java class Qcp for the layout)."""
import glob
import os
import struct
import zlib

import numpy as np

HEADER = 32


def record_dtype(cell_bytes: int) -> np.dtype:
    return np.dtype([
        ("cells", np.uint8, (cell_bytes,)), ("stm", np.uint8), ("cd_mover", np.uint8), ("cd_other", np.uint8),
        ("stage", np.uint8), ("cd_ctx", np.uint8), ("parity", np.uint8), ("turn", "<u2"), ("value", "<i2"),
        ("depth", np.uint8), ("proven", np.int8), ("plies_to_end", "<u2"), ("z", np.int8), ("flags", np.uint8),
        ("t1", "<u2"), ("t2", "<u2"), ("t3", "<u2"), ("game", "<u2"),
    ])


def read_shard(path: str):
    with open(path, "rb") as f:
        head = f.read(HEADER)
        if head[:4] != b"QCP1":
            raise ValueError(f"{path}: not a QCP1 file")
        rows, cols, k, size = struct.unpack_from("<4H", head, 4)
        spec_hash, generation = struct.unpack_from("<qi", head, 12)
        cell_bytes = (rows * cols + 3) // 4
        dt = record_dtype(cell_bytes)
        if dt.itemsize != size:
            raise ValueError(f"{path}: record size {size}, expected {dt.itemsize}")
        raw = f.read()
    # a shard that is still being written may end in a partial record
    recs = np.frombuffer(raw[:len(raw) // dt.itemsize * dt.itemsize], dtype=dt)
    meta = {"rows": rows, "cols": cols, "k": k, "spec_hash": spec_hash & 0xFFFFFFFFFFFFFFFF,
            "generation": generation, "shard": os.path.basename(path)}
    return recs, meta


def read_dirs(dirs):
    """All records of the shards in the given generation directories, with a shard-unique game key."""
    parts = []
    keys = []
    meta0 = None
    for d in dirs:
        for i, path in enumerate(sorted(glob.glob(os.path.join(d, "shard-*.qcp")))):
            recs, meta = read_shard(path)
            if meta0 is None:
                meta0 = meta
            elif (meta["rows"], meta["cols"], meta["spec_hash"]) != (meta0["rows"], meta0["cols"], meta0["spec_hash"]):
                raise ValueError(f"{path}: spec differs from the first shard")
            parts.append(recs)
            # game ids are global per run but stored mod 65536; the generation dir and shard keep keys distinct
            salt = zlib.crc32(f"{os.path.basename(os.path.normpath(d))}/{i}".encode()) & 0xFFFF
            keys.append(recs["game"].astype(np.int64) + salt * 65536)
    if not parts:
        raise ValueError(f"no shards in {dirs}")
    return np.concatenate(parts), np.concatenate(keys), meta0


def unpack_cells(packed: np.ndarray, cells: int) -> np.ndarray:
    """[N, cells/4] 2-bit packed -> [N, cells] uint8 (0 empty, 1 first, 2 second)."""
    n = packed.shape[0]
    out = np.empty((n, packed.shape[1] * 4), dtype=np.uint8)
    for j in range(4):
        out[:, j::4] = (packed >> (2 * j)) & 3
    return out[:, :cells]
