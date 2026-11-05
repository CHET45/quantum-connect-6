"""Fits the pattern tables to self-play records on the GPU (design 4.3, 4.4) and exports the .qcw blob.

python -m qctrain.fit --spec spec/feature-spec-8x12.json --data ../data/gen000 --out ../data/models/gen001 --lam 0.5

Target y = lam * sigmoid(value / K) + (1 - lam) * y_z with y_z in {0.95, 0.5, 0.05}; proven results 0.99 / 0.01.
K is fitted to the game outcomes (Texel's scale tuning) unless given: the hand-table search reports values about
four times too confident for 1/256 logit (K = 1024 fits its outcomes best), and mixing an over-confident search
target with the outcome target makes them pull against each other.
Binary cross-entropy on logits, Adam with an exponential learning-rate decay, decoupled weight decay (light on the
base table, stronger on the stage and cooldown deltas so rare contexts stay near the shared value), every weight
clamped to [-8, 8] logits, early stopping on a validation set of 5 percent of the games.
With --no-stage the window tables do not depend on the game stage (one stage in the blob): large boards, where
per-stage tables would cost the bot too much memory.
"""
import argparse
import dataclasses
import json
import math
import os
import time
import zlib

import numpy as np
import torch

from . import data, export, model as model_mod, spec as spec_mod


def fit_scale(recs):
    """Scale K for which sigmoid(value / K) best predicts the game outcomes (unproven positions only)."""
    m = recs["proven"] == 0
    v = recs["value"][m].astype(np.float64)
    y = (recs["z"][m].astype(np.float64) + 1) / 2
    best = None
    for k in np.geomspace(64, 4096, 49):
        p = np.clip(1.0 / (1.0 + np.exp(-v / k)), 1e-6, 1 - 1e-6)
        loss = -(y * np.log(p) + (1 - y) * np.log(1 - p)).mean()
        if best is None or loss < best[1]:
            best = (float(k), float(loss))
    return best


def targets(recs, lam, k):
    v = recs["value"].astype(np.float64)
    y_search = 1.0 / (1.0 + np.exp(-v / k))
    z = recs["z"]
    y_z = np.where(z > 0, 0.95, np.where(z < 0, 0.05, 0.5))
    y = lam * y_search + (1 - lam) * y_z
    proven = recs["proven"]
    return np.where(proven > 0, 0.99, np.where(proven < 0, 0.01, y)).astype(np.float32)


def to_device(recs, spec, y, device):
    cells = data.unpack_cells(recs["cells"], spec.rows * spec.cols)
    stage = recs["stage"].astype(np.int64) if spec.stages > 1 else np.zeros(len(recs), dtype=np.int64)
    return {
        "cells": torch.as_tensor(cells, device=device),
        "stm": torch.as_tensor(recs["stm"].astype(np.int64) + 1, device=device),
        "stage": torch.as_tensor(stage, device=device),
        "cd": torch.as_tensor(recs["cd_ctx"].astype(np.int64), device=device),
        "t1": torch.as_tensor(recs["t1"].astype(np.int64), device=device),
        "t2": torch.as_tensor(recs["t2"].astype(np.int64), device=device),
        "t3": torch.as_tensor(recs["t3"].astype(np.int64), device=device),
        "y": torch.as_tensor(y, device=device),
        "proven": torch.as_tensor(recs["proven"].astype(np.int64), device=device),
    }


def forward(m, d, sel):
    return m(d["cells"][sel], d["stm"][sel], d["stage"][sel], d["cd"][sel], d["t1"][sel], d["t2"][sel], d["t3"][sel])


@torch.no_grad()
def evaluate(m, d, batch=65536):
    n = d["y"].shape[0]
    loss = 0.0
    qs = []
    for i in range(0, n, batch):
        sel = torch.arange(i, min(n, i + batch), device=d["y"].device)
        q = forward(m, d, sel)
        loss += torch.nn.functional.binary_cross_entropy_with_logits(q, d["y"][sel], reduction="sum").item()
        qs.append(q)
    return loss / n, torch.cat(qs)


def bce_of_constant(y):
    p = y.mean().clamp(1e-6, 1 - 1e-6)
    return -(y * torch.log(p) + (1 - y) * torch.log(1 - p)).mean().item()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--spec", default="spec/feature-spec-8x12.json")
    ap.add_argument("--data", nargs="+", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--lam", type=float, default=0.5)
    ap.add_argument("--epochs", type=int, default=30)
    ap.add_argument("--batch", type=int, default=16384)
    ap.add_argument("--lr", type=float, default=1e-2)
    ap.add_argument("--lr-end", type=float, default=1e-4)
    ap.add_argument("--wd-base", type=float, default=1e-4)
    ap.add_argument("--wd-delta", type=float, default=1e-3)
    ap.add_argument("--patience", type=int, default=3)
    ap.add_argument("--warm", default=None, help="theta.pt of the previous generation")
    ap.add_argument("--base-only", action="store_true", help="keep the stage and cooldown deltas at zero")
    ap.add_argument("--no-stage", action="store_true", help="one window table for all game stages")
    ap.add_argument("--k", default="auto", help="search-value scale of the target, or auto (fitted to outcomes)")
    args = ap.parse_args()
    torch.manual_seed(1)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    spec = spec_mod.load(args.spec)
    if args.no_stage:
        spec = dataclasses.replace(spec, stages=1)
    recs, keys, meta = data.read_dirs(args.data)
    if meta["spec_hash"] != spec.spec_hash or (meta["rows"], meta["cols"]) != (spec.rows, spec.cols):
        raise SystemExit("data and spec disagree")
    # hold out 5 percent of the games, by a hash of the game key (positions of one game are strongly correlated)
    games = np.unique(keys)
    hashed = np.array([zlib.crc32(int(k).to_bytes(8, "little")) for k in games], dtype=np.int64)
    val_games = games[hashed % 20 == 0]
    is_val = np.isin(keys, val_games)
    if args.k == "auto":
        k, k_loss = fit_scale(recs[~is_val])
        print(f"target scale K = {k:.0f} (outcome BCE {k_loss:.4f})", flush=True)
    else:
        k = float(args.k)
    y = targets(recs, args.lam, k)
    train = to_device(recs[~is_val], spec, y[~is_val], device)
    val = to_device(recs[is_val], spec, y[is_val], device)
    n = train["y"].shape[0]
    print(f"{len(recs)} records, {n} train, {val['y'].shape[0]} validation ({len(val_games)} games), device {device}",
          flush=True)

    m = model_mod.Model(spec, device)
    if args.warm:
        m.theta.data.copy_(torch.load(args.warm, map_location=device))
    lay = m.layout
    opt = torch.optim.Adam([m.theta], lr=args.lr, betas=(0.9, 0.999))
    steps_per_epoch = math.ceil(n / args.batch)
    gamma = (args.lr_end / args.lr) ** (1.0 / (args.epochs * steps_per_epoch))
    sched = torch.optim.lr_scheduler.ExponentialLR(opt, gamma)
    base_slice = slice(lay.base, lay.stage)
    delta_slice = slice(lay.stage, lay.t1)
    best = (float("inf"), None, 0)
    history = []
    const_val = bce_of_constant(val["y"])
    t0 = time.time()
    for epoch in range(args.epochs):
        perm = torch.randperm(n, device=device)
        total = 0.0
        for i in range(0, n, args.batch):
            sel = perm[i:i + args.batch]
            q = forward(m, train, sel)
            loss = torch.nn.functional.binary_cross_entropy_with_logits(q, train["y"][sel])
            opt.zero_grad(set_to_none=True)
            loss.backward()
            opt.step()
            with torch.no_grad():
                lr = opt.param_groups[0]["lr"]
                m.theta[base_slice] *= 1 - lr * args.wd_base
                if args.base_only:
                    m.theta[delta_slice] = 0.0
                else:
                    m.theta[delta_slice] *= 1 - lr * args.wd_delta
                m.theta.clamp_(-8.0, 8.0)
            sched.step()
            total += loss.item() * sel.shape[0]
        val_loss, _ = evaluate(m, val)
        history.append({"epoch": epoch, "train": total / n, "val": val_loss})
        print(f"epoch {epoch:2d}: train {total / n:.5f} val {val_loss:.5f} (constant {const_val:.5f}) "
              f"{time.time() - t0:.0f}s", flush=True)
        if val_loss < best[0] - 1e-6:
            best = (val_loss, m.theta.detach().clone(), epoch)
        elif epoch - best[2] >= args.patience:
            break
    m.theta.data.copy_(best[1])
    val_loss, qv = evaluate(m, val)

    report = {"records": int(len(recs)), "train": int(n), "validation": int(val["y"].shape[0]),
              "lam": args.lam, "k": k, "base_only": args.base_only, "stages": spec.stages, "best_epoch": best[2], "val_bce": val_loss, "constant_bce": const_val,
              "history": history, "spec_hash": hex(spec.spec_hash), "data": args.data}
    pv = val["proven"] != 0
    if pv.any():
        report["proven_sign_accuracy"] = ((qv[pv] > 0) == (val["proven"][pv] > 0)).float().mean().item()
    by = {}
    for s in range(spec.stages):
        for c in range(spec.cd_contexts):
            sel = (val["stage"] == s) & (val["cd"] == c)
            if sel.sum() > 100:
                loss_sc = torch.nn.functional.binary_cross_entropy_with_logits(qv[sel], val["y"][sel]).item()
                by[f"stage{s}_cd{c}"] = {"n": int(sel.sum()), "bce": loss_sc}
    report["by_context"] = by
    os.makedirs(args.out, exist_ok=True)
    torch.save(m.theta.detach().cpu(), os.path.join(args.out, "theta.pt"))
    tables = export.fold(m)
    export.write(os.path.join(args.out, "eval.qcw"), spec, tables)
    with open(os.path.join(args.out, "fit-report.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, indent=1)
    print(json.dumps({k: v for k, v in report.items() if k not in ("history", "by_context")}), flush=True)


if __name__ == "__main__":
    main()
