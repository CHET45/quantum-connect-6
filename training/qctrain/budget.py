"""Machine-wide limits of the run: threads of Java tool jobs, and how a fit may use the GPU.

threads_in_use(): threads of running harness tools at BelowNormal priority or above, from their --threads argument (1 if
they have none), plus those of a running CPU fit; Idle jobs do not count. The rule is at most LIMIT in total, so at least 2
logical CPUs stay free.

python -m qctrain.budget LOCKDIR command args...  runs a fit while holding the lock, so only one of ours runs at a time.
The GPU is used only if the driver watchdog's stop flag is absent and nvidia-smi lists no compute program at all;
otherwise the fit runs on the CPU with CPU_FIT_THREADS threads at Below Normal priority. A fit that dies (the watchdog
ends a GPU fit if another program starts computing or the driver reports an error) is started again, on whatever is
allowed then, and resumes from its checkpoint.
"""
import os
import subprocess
import sys
import time

LIMIT = 18
CPU_FIT_THREADS = int(os.environ.get("QC_FIT_THREADS", "8"))
RETRIES = 6
BELOW_NORMAL = 0x00004000
GPU_STOP = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "logs", "gpu-stop")
CPU_MARK = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..", "qc-bot", "data",
                                        "night", "eval", "cpu-fit.txt"))

_PS = (
    "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -match 'ch\\.qc\\.tools\\.' } "
    "| ForEach-Object { $p = Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue; "
    "if ($p -and $p.PriorityClass -ne 'Idle') { $m = [regex]::Match($_.CommandLine, '--threads (\\d+)'); "
    "if ($m.Success) { $m.Groups[1].Value } else { '1' } } }"
)


def threads_in_use():
    p = subprocess.run(["powershell", "-NoProfile", "-Command", _PS], capture_output=True, text=True, timeout=120)
    n = sum(int(x) for x in p.stdout.split() if x.isdigit())
    return n + (CPU_FIT_THREADS if os.path.exists(CPU_MARK) else 0)


def free_threads(wanted, least=2):
    """How many threads a new job may use now: wanted if they fit under the limit, but always at least `least`."""
    return max(least, min(wanted, LIMIT - threads_in_use()))


def gpu_allowed():
    """(allowed, reason): no stop flag and no compute program on the GPU, ours included."""
    if os.environ.get("QC_FIT_DEVICE") == "cpu":
        return False, "QC_FIT_DEVICE=cpu"
    if os.path.exists(GPU_STOP):
        return False, "gpu-stop exists"
    try:
        p = subprocess.run(["nvidia-smi", "--query-compute-apps=process_name", "--format=csv,noheader"],
                           capture_output=True, text=True, timeout=60)
    except (OSError, subprocess.SubprocessError) as e:
        return False, f"nvidia-smi failed: {e}"
    if p.returncode != 0:
        return False, f"nvidia-smi exit {p.returncode}"
    others = p.stdout.strip()
    return (False, f"on the GPU: {others}") if others else (True, "free")


def run_with_gpu_lock(lock, cmd):
    while True:
        try:
            os.mkdir(lock)
            break
        except FileExistsError:
            time.sleep(5)
    try:
        for attempt in range(RETRIES):
            allowed, reason = gpu_allowed()
            env = dict(os.environ)
            flags = 0
            if not allowed:
                # the fit reads QC_FIT_DEVICE; -1 also hides the GPU from the driver library (an empty value does not on
                # Windows, where an empty variable counts as unset)
                env["QC_FIT_DEVICE"] = "cpu"
                env["CUDA_VISIBLE_DEVICES"] = "-1"
                env["OMP_NUM_THREADS"] = str(CPU_FIT_THREADS)
                env["MKL_NUM_THREADS"] = str(CPU_FIT_THREADS)
                flags = BELOW_NORMAL
                with open(CPU_MARK, "w", encoding="utf-8") as f:
                    f.write(str(CPU_FIT_THREADS))
            print(f"{time.strftime('%H:%M:%S')} attempt {attempt}: {'GPU' if allowed else 'CPU'} ({reason})", flush=True)
            try:
                rc = subprocess.call(cmd, env=env, creationflags=flags)
            finally:
                if os.path.exists(CPU_MARK):
                    os.remove(CPU_MARK)
            if rc == 0:
                return 0
            print(f"{time.strftime('%H:%M:%S')} fit ended with exit {rc}", flush=True)
            time.sleep(10)
        return 1
    finally:
        os.rmdir(lock)


if __name__ == "__main__":
    sys.exit(run_with_gpu_lock(sys.argv[1], sys.argv[2:]))
