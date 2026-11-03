"""Generation loop of design 4.7: self-play with the current engine, fit on the last three data sets with a warm
start, gate the candidate, keep or reject it. Run from the repository root (qc-bot):

python training/qctrain/loop.py --iterations 4 --positions 3000000

State lives in data/: models/genNNN (accepted generations), itNN (self-play data per iteration), loop.jsonl (one
line per iteration). The gate is a fixed-node SPRT [0, 10] against the current generation on all cores, then, only
if that passes, the same SPRT at the real 100 ms between jars built with the weights embedded (ladder/genNNN.jar).
A candidate is accepted only if the real-time SPRT accepts H1; the loop stops after two rejections in a row.
"""
import argparse
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PY = sys.executable
VERDICT = re.compile(r"Elo ([+-][\d.]+) \[([+-][\d.]+), ([+-][\d.]+)\], LLR ([-\d.]+) .*: (H0|H1|CONTINUE)")


def java_exe():
    jdks = sorted(glob.glob(os.path.expanduser("~/.gradle/jdks/*25*/bin/java.exe"))) + \
        sorted(glob.glob(os.path.expanduser("~/.gradle/jdks/*25*/bin/java")))
    if not jdks:
        raise SystemExit("no JDK 25 under ~/.gradle/jdks")
    return jdks[0]


def run(cmd, log, env=None, cwd=ROOT):
    t0 = time.time()
    with open(log, "w", encoding="utf-8") as f:
        f.write(" ".join(cmd) + "\n")
        f.flush()
        r = subprocess.run(cmd, cwd=cwd, stdout=f, stderr=subprocess.STDOUT, env=env)
    if r.returncode != 0:
        raise RuntimeError(f"command failed ({r.returncode}), see {log}")
    return time.time() - t0


def verdict(log):
    text = open(log, encoding="utf-8").read()
    m = list(VERDICT.finditer(text))
    if not m:
        raise RuntimeError(f"no SPRT summary in {log}")
    e, lo, hi, llr, v = m[-1].groups()
    return {"elo": float(e), "low": float(lo), "high": float(hi), "llr": float(llr), "verdict": v}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--iterations", type=int, default=4)
    ap.add_argument("--positions", type=int, default=3_000_000)
    ap.add_argument("--nodes", type=int, default=20_000)
    ap.add_argument("--threads", type=int, default=17)
    ap.add_argument("--gate-pairs", type=int, default=1000)
    ap.add_argument("--force-realtime", action="store_true", help="testing: run the real-time gate even if the fast one fails")
    args = ap.parse_args()

    data = os.path.join(ROOT, "data")
    models = os.path.join(data, "models")
    logs = os.path.join(data, "loop-logs")
    os.makedirs(logs, exist_ok=True)
    state_file = os.path.join(data, "loop-state.json")
    if os.path.exists(state_file):
        state = json.load(open(state_file, encoding="utf-8"))
    else:
        state = {"current": 1, "previous": "hand", "data": ["gen000"], "iteration": 0, "rejections": 0}
    java = java_exe()
    bin_dir = os.path.join(data, "bin-loop")
    # tools run from a private copy of the classes, so rebuilding the jar between steps cannot change them
    shutil.rmtree(bin_dir, ignore_errors=True)
    shutil.copytree(os.path.join(ROOT, "build", "classes", "java", "main"), bin_dir)
    shutil.copytree(os.path.join(ROOT, "build", "classes", "java", "tools"), bin_dir, dirs_exist_ok=True)
    genv = dict(os.environ, JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=" + os.path.expanduser("~/.jtmp").replace("\\", "/"))
    gradle = [os.path.join(ROOT, "gradlew.bat")] if os.path.exists(os.path.join(ROOT, "gradlew.bat")) else ["bash", "./gradlew"]

    for _ in range(args.iterations):
        if state["rejections"] >= 2:
            print("two rejections in a row: plateau, stopping", flush=True)
            break
        state["iteration"] += 1
        it = state["iteration"]
        cur = state["current"]
        cur_dir = os.path.join(models, f"gen{cur:03d}")
        cand = cur + 1
        cand_dir = os.path.join(models, f"cand{cand:03d}-it{it:02d}")
        rec = {"iteration": it, "current": cur, "candidate": cand, "started": time.strftime("%Y-%m-%d %H:%M")}
        prev = state["previous"]
        prev_w = "hand" if prev == "hand" else os.path.join(models, f"gen{prev:03d}", "eval.qcw")

        out = os.path.join(data, f"it{it:02d}")
        rec["selfplay_s"] = run([java, "-Xmx8g", "-cp", bin_dir, "ch.qc.tools.SelfPlayMain", "--out", out,
                                 "--positions", str(args.positions), "--nodes", str(args.nodes), "--threads",
                                 str(args.threads), "--seed", str(1000 + it), "--weights",
                                 os.path.join(cur_dir, "eval.qcw"), "--prev", prev_w, "--mix", "0.6,0.2,0.2",
                                 "--generation", str(cur)], os.path.join(logs, f"it{it:02d}-selfplay.log"))
        rec["selfplay"] = json.load(open(os.path.join(out, "manifest.json"), encoding="utf-8"))
        window = (state["data"] + [f"it{it:02d}"])[-3:]
        rec["window"] = window
        rec["fit_s"] = run([PY, "-m", "qctrain.fit", "--data"] + [os.path.join("..", "data", d) for d in window]
                           + ["--out", cand_dir, "--warm", os.path.join(cur_dir, "theta.pt"), "--lam", "0.9", "--k",
                              "256", "--epochs", "40", "--lr", "1e-3", "--lr-end", "1e-5", "--wd-base", "1e-2",
                              "--wd-delta", "1e-1"], os.path.join(logs, f"it{it:02d}-fit.log"),
                           cwd=os.path.join(ROOT, "training"))
        rep = json.load(open(os.path.join(cand_dir, "fit-report.json"), encoding="utf-8"))
        rec["fit"] = {k: rep[k] for k in ("records", "best_epoch", "val_bce", "proven_sign_accuracy") if k in rep}

        fast_log = os.path.join(logs, f"it{it:02d}-gate-fast.log")
        run([java, "-Xmx6g", "-cp", bin_dir, "ch.qc.tools.FastMatch", "--a", "@" + os.path.join(cand_dir, "eval.qcw"),
             "--b", "@" + os.path.join(cur_dir, "eval.qcw"), "--nodes", str(args.nodes), "--pairs",
             str(args.gate_pairs), "--sprt", "0,10", "--threads", "18", "--seed", str(2000 + it), "--out",
             os.path.join(ROOT, "results", f"loop-it{it:02d}-fast.jsonl")], fast_log)
        rec["gate_fast"] = verdict(fast_log)
        anchor_log = os.path.join(logs, f"it{it:02d}-anchor.log")
        run([java, "-Xmx6g", "-cp", bin_dir, "ch.qc.tools.FastMatch", "--a", "@" + os.path.join(cand_dir, "eval.qcw"),
             "--b", "@hand", "--nodes", str(args.nodes), "--pairs", "300", "--threads", "18", "--seed",
             str(3000 + it)], anchor_log)
        rec["anchor_vs_hand"] = verdict(anchor_log)

        accepted = False
        if rec["gate_fast"]["verdict"] == "H1" or args.force_realtime:
            weights = os.path.join(ROOT, "weights", "eval.qcw")
            shutil.copy(os.path.join(cand_dir, "eval.qcw"), weights)
            run(gradle + ["--no-daemon", "-q", "-x", "test", "build"], os.path.join(logs, f"it{it:02d}-build.log"),
                env=genv)
            jar = os.path.join(ROOT, "ladder", f"gen{cand:03d}.jar")
            shutil.copy(os.path.join(ROOT, "build", "libs", "quantumconnect-starter-1.0-SNAPSHOT.jar"), jar)
            rt_log = os.path.join(logs, f"it{it:02d}-gate-realtime.log")
            run(gradle + ["--no-daemon", "-q", "match", "-Pargs=--a jar:ladder/gen{:03d}.jar --b jar:ladder/gen{:03d}.jar "
                          "--sprt 0,10 --pairs {} --threads 8 --openings suites/openings-8x12.txt --seed {} --out "
                          "results/loop-it{:02d}-realtime.jsonl".format(cand, cur, args.gate_pairs, 4000 + it, it)],
                rt_log, env=genv)
            rec["gate_realtime"] = verdict(rt_log)
            accepted = rec["gate_realtime"]["verdict"] == "H1"
            if not accepted:
                shutil.copy(os.path.join(cur_dir, "eval.qcw"), weights)
        rec["accepted"] = accepted
        state["data"] = window
        if accepted:
            shutil.copytree(cand_dir, os.path.join(models, f"gen{cand:03d}"), dirs_exist_ok=True)
            state["previous"] = cur
            state["current"] = cand
            state["rejections"] = 0
        else:
            state["rejections"] += 1
        rec["finished"] = time.strftime("%Y-%m-%d %H:%M")
        with open(os.path.join(data, "loop.jsonl"), "a", encoding="utf-8") as f:
            f.write(json.dumps(rec) + "\n")
        json.dump(state, open(state_file, "w", encoding="utf-8"), indent=1)
        print(json.dumps({k: rec[k] for k in rec if k not in ("selfplay",)}), flush=True)


if __name__ == "__main__":
    main()
