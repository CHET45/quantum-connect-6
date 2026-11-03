"""Fits one set of size-independent tables (Java GenericSpec) to self-play records of several board sizes.

python -m qctrain.fit_generic --set spec/generic-8x12.json:../data/it02 --set spec/generic-15x11.json:../data/x11/gen000
    --out ../data/models/generic001 --lam 0.9 --k 8x12=256

Each --set is a generic spec JSON (FeatureSpecDump --generic) and its data directories (comma-separated). Every board
size has its own model view of one shared parameter vector (the layout depends only on the class count); batches
of all sizes are interleaved in proportion to their record counts. Targets as in fit.py, with the search-value
scale K fitted per size unless given (--k ROWSxCOLS=K, repeatable). The blob is written with rows = cols = 0 and the
generic hash, which the bot's WeightBlob reads as tables for any board.
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

from . import data, export, fit, model as model_mod, spec as spec_mod


def load_set(arg, ks, lam, device):
    spec_path, dirs = arg.split(":", 1)
    with open(spec_path, encoding="utf-8") as f:
        raw = json.load(f)
    if not raw.get("generic"):
        raise SystemExit(f"{spec_path} is not a generic spec")
    spec = spec_mod.load(spec_path)
    generic_hash = int(raw["genericHash"], 16)
    recs, keys, meta = data.read_dirs(dirs.split(","))
    if meta["spec_hash"] != spec.spec_hash or (meta["rows"], meta["cols"]) != (spec.rows, spec.cols):
        raise SystemExit(f"{dirs}: data and spec {spec_path} disagree")
    games = np.unique(keys)
    hashed = np.array([zlib.crc32(int(k).to_bytes(8, "little")) for k in games], dtype=np.int64)
    is_val = np.isin(keys, games[hashed % 20 == 0])
    size = f"{spec.rows}x{spec.cols}"
    if size in ks:
        k = ks[size]
    else:
        k, k_loss = fit.fit_scale(recs[~is_val])
        print(f"{size}: target scale K = {k:.0f} (outcome BCE {k_loss:.4f})", flush=True)
    y = fit.targets(recs, lam, k)
    train = fit.to_device(recs[~is_val], spec, y[~is_val], device)
    val = fit.to_device(recs[is_val], spec, y[is_val], device)
    print(f"{size}: {len(recs)} records, {train['y'].shape[0]} train, {val['y'].shape[0]} validation", flush=True)
    return {"size": size, "spec": spec, "hash": generic_hash, "k": k, "train": train, "val": val, "dirs": dirs}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--set", action="append", required=True, help="SPEC.json:DIR[,DIR...]")
    ap.add_argument("--out", required=True)
    ap.add_argument("--lam", type=float, default=0.9)
    ap.add_argument("--k", action="append", default=[], help="ROWSxCOLS=K, else fitted to that size's outcomes")
    ap.add_argument("--epochs", type=int, default=30)
    ap.add_argument("--batch", type=int, default=8192)
    ap.add_argument("--lr", type=float, default=1e-2)
    ap.add_argument("--lr-end", type=float, default=1e-4)
    ap.add_argument("--wd-base", type=float, default=1e-4)
    ap.add_argument("--wd-delta", type=float, default=1e-3)
    ap.add_argument("--patience", type=int, default=3)
    ap.add_argument("--warm", default=None, help="theta.pt of an earlier generic model")
    ap.add_argument("--no-stage", action="store_true", help="one window table for all game stages")
    args = ap.parse_args()
    torch.manual_seed(1)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    ks = {kv.split("=")[0]: float(kv.split("=")[1]) for kv in args.k}
    sets = [load_set(a, ks, args.lam, device) for a in args.set]
    if len({s["hash"] for s in sets}) != 1:
        raise SystemExit("the specs belong to different generic families")
    for s in sets:
        if args.no_stage:
            s["spec"] = dataclasses.replace(s["spec"], stages=1)
            for part in ("train", "val"):
                s[part]["stage"].zero_()
        s["model"] = model_mod.Model(s["spec"], device)
    sizes = {s["model"].layout.size for s in sets}
    if len(sizes) != 1:
        raise SystemExit(f"layouts differ: {sizes}")
    theta = sets[0]["model"].theta
    if args.warm:
        theta.data.copy_(torch.load(args.warm, map_location=device))
    for s in sets[1:]:
        s["model"].theta = theta
    lay = sets[0]["model"].layout
    base_slice = slice(lay.base, lay.stage)
    delta_slice = slice(lay.stage, lay.t1)
    opt = torch.optim.Adam([theta], lr=args.lr)
    n_total = sum(s["train"]["y"].shape[0] for s in sets)
    steps_per_epoch = sum(math.ceil(s["train"]["y"].shape[0] / args.batch) for s in sets)
    gamma = (args.lr_end / args.lr) ** (1.0 / (args.epochs * steps_per_epoch))
    sched = torch.optim.lr_scheduler.ExponentialLR(opt, gamma)
    best = (float("inf"), None, 0)
    history = []
    t0 = time.time()
    rng = np.random.default_rng(1)
    for epoch in range(args.epochs):
        plan = []
        for i, s in enumerate(sets):
            n = s["train"]["y"].shape[0]
            perm = torch.randperm(n, device=device)
            plan += [(i, perm[j:j + args.batch]) for j in range(0, n, args.batch)]
        order = rng.permutation(len(plan))
        total = 0.0
        for o in order:
            i, sel = plan[o]
            s = sets[i]
            q = fit.forward(s["model"], s["train"], sel)
            loss = torch.nn.functional.binary_cross_entropy_with_logits(q, s["train"]["y"][sel])
            opt.zero_grad(set_to_none=True)
            loss.backward()
            opt.step()
            with torch.no_grad():
                lr = opt.param_groups[0]["lr"]
                theta[base_slice] *= 1 - lr * args.wd_base
                theta[delta_slice] *= 1 - lr * args.wd_delta
                theta.clamp_(-8.0, 8.0)
            sched.step()
            total += loss.item() * sel.shape[0]
        vals = {}
        weighted = 0.0
        for s in sets:
            v, _ = fit.evaluate(s["model"], s["val"])
            vals[s["size"]] = v
            weighted += v * s["val"]["y"].shape[0]
        weighted /= sum(s["val"]["y"].shape[0] for s in sets)
        history.append({"epoch": epoch, "train": total / n_total, "val": vals, "val_mean": weighted})
        print(f"epoch {epoch:2d}: train {total / n_total:.5f} val {weighted:.5f} "
              + " ".join(f"{k} {v:.5f}" for k, v in vals.items()) + f" {time.time() - t0:.0f}s", flush=True)
        if weighted < best[0] - 1e-6:
            best = (weighted, theta.detach().clone(), epoch)
        elif epoch - best[2] >= args.patience:
            break
    theta.data.copy_(best[1])
    report = {"lam": args.lam, "best_epoch": best[2], "val_bce": best[0], "stages": sets[0]["spec"].stages,
              "history": history, "generic_hash": hex(sets[0]["hash"]), "sets": []}
    for s in sets:
        v, qv = fit.evaluate(s["model"], s["val"])
        entry = {"size": s["size"], "data": s["dirs"], "k": s["k"], "val_bce": v,
                 "constant_bce": fit.bce_of_constant(s["val"]["y"]), "records": int(s["train"]["y"].shape[0])}
        pv = s["val"]["proven"] != 0
        if pv.any():
            entry["proven_sign_accuracy"] = ((qv[pv] > 0) == (s["val"]["proven"][pv] > 0)).float().mean().item()
        report["sets"].append(entry)
    os.makedirs(args.out, exist_ok=True)
    torch.save(theta.detach().cpu(), os.path.join(args.out, "theta.pt"))
    tables = export.fold(sets[0]["model"])
    blob_spec = dataclasses.replace(sets[0]["spec"], rows=0, cols=0, spec_hash=sets[0]["hash"])
    export.write(os.path.join(args.out, "eval.qcw"), blob_spec, tables)
    with open(os.path.join(args.out, "fit-report.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, indent=1)
    print(json.dumps({k: v for k, v in report.items() if k != "history"}), flush=True)


if __name__ == "__main__":
    main()
