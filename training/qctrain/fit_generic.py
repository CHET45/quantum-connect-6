"""Fits one set of size-independent tables (Java GenericSpec) to self-play records of several board sizes.

python -m qctrain.fit_generic --set spec/generic-8x12.json:../data/it02 --set spec/generic-15x11.json:../data/x11/gen000
    --out ../data/models/generic001 --lam 0.9 --k 8x12=256

Each --set is a generic spec JSON (FeatureSpecDump --generic) and its data directories (comma-separated), optionally
followed by :K and :WEIGHT for this set alone (labels of different generations have different value scales, so one
board size may appear in several sets; each set has its own learned output scale and its own batches). Every board
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


def spearman(a, b):
    """Rank correlation of two 1-d numpy arrays (no tie correction, the values are nearly all distinct)."""
    if len(a) < 3:
        return float("nan")
    ra = np.argsort(np.argsort(a)).astype(np.float64)
    rb = np.argsort(np.argsort(b)).astype(np.float64)
    return float(np.corrcoef(ra, rb)[0, 1])


def batch_of(args, s):
    """Batch of a set: a position of a large board holds many windows, so fewer of them fit in memory and in one step."""
    return max(1024, int(args.batch * min(1.0, 800 / s["spec"].windows)))


def load_set(arg, ks, lam, device, cap=0, k_std=None):
    spec_path, dirs, *extra = arg.split(":")
    with open(spec_path, encoding="utf-8") as f:
        raw = json.load(f)
    if not raw.get("generic"):
        raise SystemExit(f"{spec_path} is not a generic spec")
    spec = spec_mod.load(spec_path)
    generic_hash = int(raw["genericHash"], 16)
    recs, keys, meta = data.read_dirs(dirs.split(","))
    if cap and len(recs) > cap:
        # whole games, so the held-out split stays clean
        uniq = np.unique(keys)
        hashed = np.array([zlib.crc32(int(k).to_bytes(8, "little") + b"cap") for k in uniq], dtype=np.int64)
        keep = uniq[hashed % 1000 < 1000 * cap / len(recs)]
        m = np.isin(keys, keep)
        recs, keys = recs[m], keys[m]
    if meta["spec_hash"] != spec.spec_hash or (meta["rows"], meta["cols"]) != (spec.rows, spec.cols):
        raise SystemExit(f"{dirs}: data and spec {spec_path} disagree")
    games = np.unique(keys)
    hashed = np.array([zlib.crc32(int(k).to_bytes(8, "little")) for k in games], dtype=np.int64)
    is_val = np.isin(keys, games[hashed % 20 == 0])
    size = f"{spec.rows}x{spec.cols}"
    if extra and extra[0]:
        k = float(extra[0])
    elif size in ks:
        k = ks[size]
    elif k_std:
        k = float(recs["value"][~is_val & (recs["proven"] == 0)].astype(np.float64).std() / k_std)
        print(f"{size}: K = {k:.0f} from the spread of the labels", flush=True)
    else:
        k, k_loss = fit.fit_scale(recs[~is_val])
        print(f"{size}: target scale K = {k:.0f} (outcome BCE {k_loss:.4f})", flush=True)
    y = fit.targets(recs, lam, k)
    train = fit.to_device(recs[~is_val], spec, y[~is_val], device)
    val = fit.to_device(recs[is_val], spec, y[is_val], device)
    print(f"{size}: {len(recs)} records, {train['y'].shape[0]} train, {val['y'].shape[0]} validation", flush=True)
    return {"size": size, "share": float(extra[1]) if len(extra) > 1 else None, "spec": spec, "hash": generic_hash,
            "k": k, "train": train, "val": val, "dirs": dirs,
            "val_value": recs["value"][is_val].astype(np.float64), "val_stage": recs["stage"][is_val].copy(),
            "val_proven": recs["proven"][is_val].copy()}


def evaluate_scaled(s, tau):
    """Validation BCE and logits with the size's output scale."""
    v, q = fit.evaluate(s["model"], s["val"], max(1024, int(16384 * min(1.0, 800 / s["spec"].windows))))
    if tau.item() == 0.0:
        return v, q
    q = q * tau.exp()
    bce = torch.nn.functional.binary_cross_entropy_with_logits(q, s["val"]["y"]).item()
    return bce, q


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--set", action="append", required=True, help="SPEC.json:DIR[,DIR...]")
    ap.add_argument("--out", required=True)
    ap.add_argument("--lam", type=float, default=0.9)
    ap.add_argument("--k", action="append", default=[], help="ROWSxCOLS=K, else fitted to that size's outcomes")
    ap.add_argument("--k-std", type=float, default=None, help="K from the labels: standard deviation of the target logit")
    ap.add_argument("--epochs", type=int, default=30)
    ap.add_argument("--batch", type=int, default=8192)
    ap.add_argument("--lr", type=float, default=1e-2)
    ap.add_argument("--lr-end", type=float, default=1e-4)
    ap.add_argument("--wd-base", type=float, default=1e-4)
    ap.add_argument("--wd-delta", type=float, default=1e-3)
    ap.add_argument("--patience", type=int, default=3)
    ap.add_argument("--warm", default=None, help="theta.pt of an earlier generic model")
    ap.add_argument("--no-stage", action="store_true", help="one window table for all game stages")
    ap.add_argument("--scale", type=int, default=1024, help="units per logit in the blob, 256 or 1024")
    ap.add_argument("--balance", action="store_true", help="every size gets the same number of batches per epoch")
    ap.add_argument("--weight", action="append", default=[], help="ROWSxCOLS=W, share of batches under --balance")
    ap.add_argument("--max-steps", type=int, default=0, help="at most this many batches per epoch under --balance")
    ap.add_argument("--temp", action="store_true", help="learn one output scale per size; the tables stay unscaled")
    ap.add_argument("--cap", type=int, default=0, help="at most about this many records per size (whole games)")
    ap.add_argument("--mask-empty", action="store_true", help="the all-empty window code contributes exactly zero")
    ap.add_argument("--mask-mixed", action="store_true", help="windows holding both colours contribute exactly zero")
    ap.add_argument("--sym", type=float, default=None, help="colour-even share of the window tables, 0 = antisymmetric")
    ap.add_argument("--bands", type=int, default=6, help="row bands of the window classes")
    ap.add_argument("--height-stage", action="store_true", help="stages by average pile height, not by board fill")
    args = ap.parse_args()
    torch.manual_seed(1)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    ks = {kv.split("=")[0]: float(kv.split("=")[1]) for kv in args.k}
    sets = [load_set(a, ks, args.lam, device, args.cap, args.k_std) for a in args.set]
    for i, s in enumerate(sets):
        s["name"] = f'{s["size"]}.{i}'
    if len({s["hash"] for s in sets}) != 1:
        raise SystemExit("the specs belong to different generic families")
    for s in sets:
        if args.no_stage:
            s["spec"] = dataclasses.replace(s["spec"], stages=1)
            for part in ("train", "val"):
                s[part]["stage"].zero_()
        if args.height_stage:
            for part in ("train", "val"):
                d = s[part]
                d["stage"] = ((d["cells"] != 0).sum(1) // (2 * s["spec"].cols)).clamp(max=3)
            s["val_stage"] = s["val"]["stage"].cpu().numpy()
        if args.bands != 6:
            s["spec"] = spec_mod.with_bands(s["spec"], args.bands)
        s["model"] = model_mod.Model(s["spec"], device, args.sym)
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
    empty_mask = None
    if args.mask_empty or args.mask_mixed:
        spec0 = sets[0]["spec"]
        code_mask = np.zeros(spec0.codes, dtype=bool)
        for c in range(spec0.codes):
            digits = {(c // 3 ** i) % 3 for i in range(spec0.k)}
            code_mask[c] = (args.mask_empty and digits == {0}) or (args.mask_mixed and 1 in digits and 2 in digits)
        window_mask = torch.as_tensor(np.tile(code_mask, spec0.classes), device=device)
        empty_mask = torch.zeros(lay.size, dtype=torch.bool, device=device)
        for block, count in ((lay.base, 1), (lay.stage, spec0.stages), (lay.cd, spec0.cd_contexts)):
            for i in range(count):
                empty_mask[block + i * lay.p:block + (i + 1) * lay.p] = window_mask
        theta.data[empty_mask] = 0.0
    tau = torch.nn.Parameter(torch.zeros(len(sets), device=device))
    params = [{"params": [theta]}]
    if args.temp:
        params.append({"params": [tau]})
    opt = torch.optim.Adam(params, lr=args.lr)
    n_total = sum(s["train"]["y"].shape[0] for s in sets)
    batches = [math.ceil(s["train"]["y"].shape[0] / batch_of(args, s)) for s in sets]
    steps_per_epoch = sum(batches)
    if args.balance:
        shares = {kv.split("=")[0]: float(kv.split("=")[1]) for kv in args.weight}
        w = [s["share"] if s["share"] is not None else shares.get(s["size"], 1.0) for s in sets]
        total = min(steps_per_epoch, args.max_steps) if args.max_steps else steps_per_epoch
        batches = [max(1, int(total * x / sum(w))) for x in w]
        steps_per_epoch = sum(batches)
    gamma = (args.lr_end / args.lr) ** (1.0 / (args.epochs * steps_per_epoch))
    sched = torch.optim.lr_scheduler.ExponentialLR(opt, gamma)
    best = (float("inf"), None, 0, None)
    history = []
    t0 = time.time()
    rng = np.random.default_rng(1)
    for epoch in range(args.epochs):
        plan = []
        for i, s in enumerate(sets):
            n = s["train"]["y"].shape[0]
            size_i = batch_of(args, s)
            need = batches[i] * size_i
            perm = torch.cat([torch.randperm(n, device=device) for _ in range(max(1, math.ceil(need / n)))])[:need]
            plan += [(i, perm[j:j + size_i]) for j in range(0, need, size_i)]
        order = rng.permutation(len(plan))
        total = 0.0
        n_seen = 0
        for o in order:
            i, sel = plan[o]
            s = sets[i]
            q = fit.forward(s["model"], s["train"], sel) * tau[i].exp()
            loss = torch.nn.functional.binary_cross_entropy_with_logits(q, s["train"]["y"][sel])
            opt.zero_grad(set_to_none=True)
            loss.backward()
            opt.step()
            with torch.no_grad():
                if empty_mask is not None:
                    theta[empty_mask] = 0.0
                lr = opt.param_groups[0]["lr"]
                theta[base_slice] *= 1 - lr * args.wd_base
                theta[delta_slice] *= 1 - lr * args.wd_delta
                theta.clamp_(-8.0, 8.0)
            sched.step()
            total += loss.item() * sel.shape[0]
            n_seen += sel.shape[0]
        vals = {}
        weighted = 0.0
        for i, s in enumerate(sets):
            v, _ = evaluate_scaled(s, tau[i])
            vals[s["name"]] = v
            weighted += v * (1.0 if args.balance else s["val"]["y"].shape[0])
        weighted /= len(sets) if args.balance else sum(s["val"]["y"].shape[0] for s in sets)
        history.append({"epoch": epoch, "train": total / n_seen, "val": vals, "val_mean": weighted})
        print(f"epoch {epoch:2d}: train {total / n_seen:.5f} val {weighted:.5f} "
              + " ".join(f"{k} {v:.5f}" for k, v in vals.items()) + f" {time.time() - t0:.0f}s", flush=True)
        if weighted < best[0] - 1e-6:
            best = (weighted, theta.detach().clone(), epoch, tau.detach().clone())
        elif epoch - best[2] >= args.patience:
            break
    theta.data.copy_(best[1])
    tau.data.copy_(best[3])
    report = {"lam": args.lam, "best_epoch": best[2], "val_bce": best[0], "stages": sets[0]["spec"].stages,
              "history": history, "generic_hash": hex(sets[0]["hash"]), "sets": []}
    for i, s in enumerate(sets):
        v, qv = evaluate_scaled(s, tau[i])
        entry = {"size": s["size"], "data": s["dirs"], "k": s["k"], "val_bce": v, "temp": math.exp(tau[i].item()),
                 "constant_bce": fit.bce_of_constant(s["val"]["y"]), "records": int(s["train"]["y"].shape[0])}
        qn = qv.detach().cpu().numpy()
        unproven = s["val_proven"] == 0
        entry["spearman_value"] = spearman(qn[unproven], s["val_value"][unproven])
        entry["spearman_by_stage"] = {int(st): spearman(qn[unproven & (s["val_stage"] == st)],
                                                       s["val_value"][unproven & (s["val_stage"] == st)])
                                      for st in np.unique(s["val_stage"])}
        pv = s["val"]["proven"] != 0
        if pv.any():
            entry["proven_sign_accuracy"] = ((qv[pv] > 0) == (s["val"]["proven"][pv] > 0)).float().mean().item()
        report["sets"].append(entry)
    os.makedirs(args.out, exist_ok=True)
    torch.save(theta.detach().cpu(), os.path.join(args.out, "theta.pt"))
    tables = export.fold(sets[0]["model"], args.scale)
    blob_hash = sets[0]["hash"] if args.bands == 6 else spec_mod.generic_hash(sets[0]["spec"], args.bands)
    blob_spec = dataclasses.replace(sets[0]["spec"], rows=0, cols=0, spec_hash=blob_hash)
    export.write(os.path.join(args.out, "eval.qcw"), blob_spec, tables, args.scale, 2 if args.height_stage else 1)
    with open(os.path.join(args.out, "fit-report.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, indent=1)
    print(json.dumps({k: v for k, v in report.items() if k != "history"}), flush=True)


if __name__ == "__main__":
    main()
