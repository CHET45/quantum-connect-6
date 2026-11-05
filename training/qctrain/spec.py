"""FeatureSpec written by the Java tool FeatureSpecDump: window cells, mirror classes and featMap."""
import json
from dataclasses import dataclass

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
