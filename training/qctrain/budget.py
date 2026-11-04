"""Machine-wide limits of the night run: threads of Java tool jobs, and one GPU process at a time.

threads_in_use(): threads of running harness tools at BelowNormal priority or above, from their --threads argument (1 if
they have none); Idle jobs do not count. The rule is at most LIMIT in total, so at least 2 logical CPUs stay free.

python -m qctrain.budget LOCKDIR command args...  runs a command while holding the GPU lock. If the driver watchdog's stop
flag exists the command runs on the CPU instead, because the GPU must not be used then.
"""
import os
import subprocess
import sys
import time

LIMIT = 18
GPU_STOP = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "logs", "gpu-stop")

_PS = (
    "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -match 'ch\\.qc\\.tools\\.' } "
    "| ForEach-Object { $p = Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue; "
    "if ($p -and $p.PriorityClass -ne 'Idle') { $m = [regex]::Match($_.CommandLine, '--threads (\\d+)'); "
    "if ($m.Success) { $m.Groups[1].Value } else { '1' } } }"
)


def threads_in_use():
    p = subprocess.run(["powershell", "-NoProfile", "-Command", _PS], capture_output=True, text=True, timeout=120)
    return sum(int(x) for x in p.stdout.split() if x.isdigit())


def free_threads(wanted, least=2):
    """How many threads a new job may use now: wanted if they fit under the limit, but always at least `least`."""
    return max(least, min(wanted, LIMIT - threads_in_use()))


def run_with_gpu_lock(lock, cmd):
    while True:
        try:
            os.mkdir(lock)
            break
        except FileExistsError:
            time.sleep(5)
    try:
        env = dict(os.environ)
        if os.path.exists(GPU_STOP):
            print("gpu-stop exists: running on the CPU", flush=True)
            env["CUDA_VISIBLE_DEVICES"] = ""
        return subprocess.call(cmd, env=env)
    finally:
        os.rmdir(lock)


if __name__ == "__main__":
    sys.exit(run_with_gpu_lock(sys.argv[1], sys.argv[2:]))
