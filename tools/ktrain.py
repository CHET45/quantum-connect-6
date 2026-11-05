"""Night 2026-11-05/06: generic eval tables for other line lengths k (the bot has tables only for k = 6).

Per k: spec JSONs (FeatureSpecDump --k), self-play with the hand evaluation (SelfPlayMain --k, gen0), one GPU fit
(fit_generic on the CPU: no GPU work tonight after the driver error of 14:38), then a fixed-node gate of the new table against
the hand evaluation (FastMatch --k). The CPU lane runs self-play for the next k while the GPU lane fits the previous one.
Every stage is skipped when its output exists, so the script can be restarted. Optional second round (--gen1): self-play
with the gen0 table and a refit on both generations.

python -I night/tools/ktrain.py [--ks 5,4,7,8] [--positions 250000] [--gen1]
"""
import argparse
import glob
import json
import os
import subprocess
import sys
import threading
import time

Q = "C:/Users/Chtean45m/Documents/quantum-connect"
TRAIN = Q + "/night/wt-eval/training"
ROOTK = Q + "/qc-bot/data/night/evalk"
BIN = ROOTK + "/bin"
LOCK = Q + "/qc-bot/data/night/eval/gpu.lock"
LOGS = Q + "/night/logs/ktrain"
PY = "C:/Users/Chtean45m/miniconda3/python.exe"
JAVA = "C:/Users/Chtean45m/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2/bin/java.exe"
SLF = ("C:/Users/Chtean45m/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.0.17/"
       "d9e58ac9c7779ba3bf8142aff6c830617a7fe60f/slf4j-api-2.0.17.jar")
CP = ";".join([BIN + "/main", BIN + "/tools", BIN + "/resources", BIN + "/core-2.0-SNAPSHOT.jar", SLF])
SUITES = [Q + "/night/wt-dyn/suites", Q + "/night/wt-eval/suites", Q + "/night/wt-pass/suites", Q + "/night/wt-main/suites"]

SHORT = ["8x12", "10x10", "15x11", "12x12", "15x22", "15x30"]
LONG = ["10x10", "12x12", "15x11", "12x16", "15x22", "15x30"]
GATE = {4: ["8x12", "15x22"], 5: ["8x12", "15x22"], 7: ["15x11", "15x22"], 8: ["15x11", "15x22"]}
BELOW_NORMAL = 0x00004000


def log(msg):
    line = time.strftime("%H:%M:%S ") + msg
    print(line, flush=True)
    with open(LOGS + "/ktrain.log", "a", encoding="utf-8") as f:
        f.write(line + "\n")


def run(cmd, out, cwd=None, env=None):
    with open(out, "w", encoding="utf-8") as f:
        p = subprocess.Popen(cmd, stdout=f, stderr=subprocess.STDOUT, cwd=cwd, env=env, creationflags=BELOW_NORMAL)
        return p.wait()


def sizes(k):
    return SHORT if k <= 6 else LONG


def spec(k, size):
    rel = "spec/k%d/generic-%s.json" % (k, size)
    path = TRAIN + "/" + rel
    if not os.path.exists(path):
        r, c = size.split("x")
        rc = run([JAVA, "-Xmx3g", "-cp", CP, "ch.qc.tools.FeatureSpecDump", "--generic", "--k", str(k), r, c, path],
                 "%s/spec-k%d-%s.log" % (LOGS, k, size))
        if rc != 0:
            raise RuntimeError("spec k%d %s failed" % (k, size))
    return rel


def selfplay(k, gen, positions, threads, weights):
    for size in sizes(k):
        spec(k, size)
        out = "%s/k%d/gen%d/%s" % (ROOTK, k, gen, size)
        if os.path.exists(out + "/manifest.json"):
            continue
        r, c = size.split("x")
        t0 = time.time()
        rc = run([JAVA, "-Xmx4g", "-cp", CP, "ch.qc.tools.SelfPlayMain", "--out", out, "--positions", str(positions),
                  "--nodes", "20000", "--threads", str(threads), "--seed", str(100 * k + 10 * gen + 1),
                  "--weights", weights, "--prev", "hand", "--rows", r, "--cols", c, "--k", str(k),
                  "--generation", str(gen)], "%s/selfplay-k%d-g%d-%s.log" % (LOGS, k, gen, size))
        log("self-play k%d gen%d %s rc %d, %.1f min" % (k, gen, size, rc, (time.time() - t0) / 60))
        if rc != 0:
            raise RuntimeError("self-play k%d %s failed" % (k, size))


def fit(k, gens, name):
    model = "%s/models/%s" % (ROOTK, name)
    if os.path.exists(model + "/eval.qcw"):
        return model
    # CPU only: a GPU fit logged an NVIDIA driver error at 14:38 today, and a CPU epoch is about as fast here
    cmd = [PY, "-m", "qctrain.fit_generic"]
    for size in sizes(k):
        dirs = ["%s/k%d/gen%d/%s" % (ROOTK, k, g, size) for g in gens]
        dirs = [d for d in dirs if os.path.exists(d + "/manifest.json")]
        rel = ",".join(os.path.relpath(d, TRAIN).replace(os.sep, "/") for d in dirs)
        share = 2.0 if size in ("15x30", "15x22", "15x11") else 1.0
        cmd += ["--set", "%s:%s::%.2f" % (spec(k, size), rel, share)]
    cmd += ["--k-std", "1.8", "--balance", "--temp", "--wd-base", "1", "--wd-delta", "10", "--lr", "1e-3",
            "--lr-end", "1e-5", "--epochs", "12", "--patience", "4", "--out", model]
    t0 = time.time()
    env = dict(os.environ, QC_FIT_DEVICE="cpu", OMP_NUM_THREADS="10", CUDA_VISIBLE_DEVICES="")
    rc = run(cmd, "%s/fit-%s.log" % (LOGS, name), cwd=TRAIN, env=env)
    log("fit %s rc %d, %.1f min" % (name, rc, (time.time() - t0) / 60))
    if rc != 0 or not os.path.exists(model + "/eval.qcw"):
        raise RuntimeError("fit %s failed" % name)
    return model


def suite(size):
    for d in SUITES:
        p = "%s/openings-%s.txt" % (d, size)
        if os.path.exists(p):
            return p
    return None


def gate(k, model, name, against="@hand", pairs=150):
    res = []
    for size in GATE[k]:
        out = "%s/gate-%s-%s" % (LOGS, name, size)
        if not os.path.exists(out + ".log") or "fixed" not in open(out + ".log", encoding="utf-8").read():
            r, c = size.split("x")
            run([JAVA, "-Xmx3g", "-cp", CP, "ch.qc.tools.FastMatch", "--k", str(k), "--a", "@" + model + "/eval.qcw",
                 "--b", against, "--nodes", "20000", "--threads", "6", "--seed", "77", "--rows", r, "--cols", c,
                 "--pairs", str(pairs), "--openings", suite(size), "--openingPlies", "2", "--out", out + ".jsonl"],
                out + ".log")
        text = open(out + ".log", encoding="utf-8").read()
        line = [l for l in text.splitlines() if l.startswith("fixed")]
        res.append("%s: %s" % (size, line[-1][line[-1].find("score"):][:60] if line else "no result"))
    log("gate %s vs %s: %s" % (name, against, " | ".join(res)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ks", default="5,4,7,8")
    ap.add_argument("--positions", type=int, default=300000)
    ap.add_argument("--threads", type=int, default=8)
    ap.add_argument("--gen1", action="store_true")
    a = ap.parse_args()
    ks = [int(x) for x in a.ks.split(",")]
    os.makedirs(LOGS, exist_ok=True)
    ready = {k: threading.Event() for k in ks}
    failed = []

    def cpu_lane():
        for k in ks:
            try:
                selfplay(k, 0, a.positions, a.threads, "hand")
            except Exception as e:  # noqa: BLE001 - keep the other k going
                log("CPU lane k%d: %s" % (k, e))
                failed.append(k)
            ready[k].set()

    cpu = threading.Thread(target=cpu_lane)
    cpu.start()
    for k in ks:
        ready[k].wait()
        if k in failed:
            continue
        try:
            model = fit(k, [0], "k%d-g0" % k)
            gate(k, model, "k%d-g0" % k)
        except Exception as e:  # noqa: BLE001
            log("GPU lane k%d: %s" % (k, e))
    cpu.join()
    if a.gen1:
        for k in ks:
            model0 = "%s/models/k%d-g0" % (ROOTK, k)
            if not os.path.exists(model0 + "/eval.qcw"):
                continue
            try:
                selfplay(k, 1, a.positions, a.threads, model0 + "/eval.qcw")
                model = fit(k, [0, 1], "k%d-g1" % k)
                gate(k, model, "k%d-g1" % k)
                gate(k, model, "k%d-g1-vs-g0" % k, against="@" + model0 + "/eval.qcw")
            except Exception as e:  # noqa: BLE001
                log("gen1 k%d: %s" % (k, e))
    log("done")


if __name__ == "__main__":
    sys.exit(main())
