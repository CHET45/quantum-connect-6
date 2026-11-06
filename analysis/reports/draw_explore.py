"""How predictable are draws in self-play data from cheap global features? Prints rates and AUCs."""
import sys

import numpy as np

sys.path.insert(0, "C:/Users/Chtean45m/Documents/quantum-connect/qc-bot-safe/training")
from qctrain import data, spec as specmod  # noqa: E402

DATA = "C:/Users/Chtean45m/Documents/quantum-connect/qc-bot/data/"
recs, keys, meta = data.read_dirs([DATA + "it01"])
rng = np.random.default_rng(1)
sel = rng.choice(len(recs), size=min(600_000, len(recs)), replace=False)
r = recs[sel]
print("records", len(recs), "sample", len(r))
z = r["z"].astype(int)
adj = (r["flags"] & 8) != 0
print("z values", {int(v): int((z == v).sum()) for v in np.unique(z)}, "adjudicated", int(adj.sum()))
draw = (z == 0)
print("draw share (records)", draw.mean().round(4))

rows, cols = meta["rows"], meta["cols"]
cells = data.unpack_cells(r["cells"], rows * cols)
empty = (cells == 0).sum(1)
sp = specmod.load("C:/Users/Chtean45m/Documents/quantum-connect/qc-bot-safe/data/spec-8x12.json") \
    if False else None


def windows(rows, cols, k=6):
    out = []
    for c in range(cols):
        for y in range(rows):
            for dc, dy in ((1, 0), (0, 1), (1, 1), (1, -1)):
                cc = [c + i * dc for i in range(k)]
                yy = [y + i * dy for i in range(k)]
                if all(0 <= a < cols for a in cc) and all(0 <= b < rows for b in yy):
                    out.append([a * rows + b for a, b in zip(cc, yy)])
    return np.array(out)


W = windows(rows, cols)
wc = cells[:, W]  # [N, W, 6]
n1 = (wc == 1).sum(2)
n2 = (wc == 2).sum(2)
live1 = (n2 == 0)
live2 = (n1 == 0)
feat = {
    "empty": empty,
    "live_first": live1.sum(1),
    "live_second": live2.sum(1),
    "live_any": (live1 | live2).sum(1),
    "live_both_ge3": ((live1 & (n1 >= 3)) | (live2 & (n2 >= 3))).sum(1),
    "turn": r["turn"].astype(int),
    "cd_mover": r["cd_mover"].astype(int),
    "cd_other": r["cd_other"].astype(int),
    "abs_value": np.abs(r["value"].astype(int)),
}


def auc(score, y):
    order = np.argsort(score, kind="mergesort")
    ranks = np.empty(len(score))
    ranks[order] = np.arange(1, len(score) + 1)
    # average ties
    s = score[order]
    i = 0
    while i < len(s):
        j = i
        while j + 1 < len(s) and s[j + 1] == s[i]:
            j += 1
        if j > i:
            ranks[order[i:j + 1]] = (i + j + 2) / 2
        i = j + 1
    pos = y.sum()
    neg = len(y) - pos
    return (ranks[y].sum() - pos * (pos + 1) / 2) / (pos * neg)


for name, f in feat.items():
    a = auc(f.astype(float), draw)
    print(f"{name:14s} mean {f.mean():8.2f}  AUC(draw) {max(a, 1 - a):.3f} ({'+' if a >= 0.5 else '-'})")

# logistic regression on all features
X = np.column_stack([v.astype(float) for v in feat.values()])
X = (X - X.mean(0)) / (X.std(0) + 1e-9)
X = np.column_stack([X, X ** 2, np.ones(len(X))])
n = len(X)
tr = np.arange(n) < n * 0.8
w = np.zeros(X.shape[1])
for it in range(400):
    p = 1 / (1 + np.exp(-X[tr] @ w))
    g = X[tr].T @ (p - draw[tr]) / tr.sum()
    h = (X[tr] * (p * (1 - p))[:, None]).T @ X[tr] / tr.sum() + 1e-4 * np.eye(len(w))
    w -= np.linalg.solve(h, g)
pv = 1 / (1 + np.exp(-X[~tr] @ w))
print("logistic AUC (held out)", round(auc(pv, draw[~tr]), 3),
      "BCE", round(float(-np.mean(draw[~tr] * np.log(pv + 1e-9) + (~draw[~tr]) * np.log(1 - pv + 1e-9))), 4),
      "base BCE", round(float(-(draw.mean() * np.log(draw.mean()) + (1 - draw.mean()) * np.log(1 - draw.mean()))), 4))
# draw share by empty cells bucket
for lo in range(0, 97, 12):
    m = (empty >= lo) & (empty < lo + 12)
    if m.sum():
        print(f"empty {lo:3d}-{lo + 11:3d}: n {m.sum():7d} draw {draw[m].mean():.3f} |value|<64 share "
              f"{(feat['abs_value'][m] < 64).mean():.3f} draw|value|<64 {draw[m & (feat['abs_value'] < 64)].mean():.3f}")
