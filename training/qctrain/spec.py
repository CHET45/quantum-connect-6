"""FeatureSpec written by the Java tool FeatureSpecDump: window cells, mirror classes and featMap."""
import json
from dataclasses import dataclass, replace

import numpy as np


@dataclass
class Spec:
    rows: int
    cols: int
    k: int
    codes: int
    windows: int
    classes: int
    stages: int
    cd_contexts: int
    t1: int
    t2: int
    t3: int
    spec_hash: int
    win_cells: np.ndarray  # [windows, k] cell indices in digit order
    feat_map: np.ndarray  # [windows * codes] parameter index class * codes + canonical code

    @property
    def window_params(self) -> int:
        return self.classes * self.codes


def with_bands(spec: Spec, bands: int) -> Spec:
    """The size-independent spec with another number of row bands (Java GenericSpec.windowClass with bands)."""
    codes = spec.codes
    old = spec.feat_map.reshape(spec.windows, codes)
    old_class = old[:, 0] // codes
    canon = old - old_class[:, None] * codes
    lowest = (spec.win_cells % spec.rows).min(1)
    classes = (old_class // 6) * bands + np.minimum(lowest, bands - 1)
    return replace(spec, classes=3 * bands, feat_map=(classes[:, None] * codes + canon).reshape(-1))


def generic_hash(spec: Spec, bands: int) -> int:
    """Java GenericSpec.hash(k, bands)."""
    h = 0x47454E45524943
    for x in (spec.k, bands, 3, 4, spec.cd_contexts, spec.t1, spec.t2, spec.t3):
        h = (31 * h + x) & 0xFFFFFFFFFFFFFFFF
    return h


def height_stage(cells, cols):
    """Java Features.heightStage for [N, cells] arrays (numpy)."""
    return np.minimum(3, (cells != 0).sum(1) // (2 * cols)).astype(np.int64)


def load(path: str) -> Spec:
    with open(path, encoding="utf-8") as f:
        j = json.load(f)
    k = j["k"]
    return Spec(
        rows=j["rows"], cols=j["cols"], k=k, codes=j["codes"], windows=j["windows"], classes=j["classes"],
        stages=j["stages"], cd_contexts=j["cdContexts"], t1=j["t1"], t2=j["t2"], t3=j["t3"],
        spec_hash=int(j["specHash"], 16),
        win_cells=np.asarray(j["winCells"], dtype=np.int64).reshape(-1, k),
        feat_map=np.asarray(j["featMap"], dtype=np.int64),
    )
