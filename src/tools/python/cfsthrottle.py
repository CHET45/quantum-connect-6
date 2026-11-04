"""User-space imitation of a container CPU limit (Linux CFS bandwidth) for one Windows process.

Every period the process may burn the quota of CPU time over all its threads. Once it has, all its threads are
suspended until the next period starts. Usage: cfsthrottle.py PID QUOTA_MS PERIOD_MS CPU; runs until the process ends.
It spins on logical CPU number CPU at high priority, so that CPU is lost to the experiment.
"""
import ctypes
import sys
import time
from ctypes import wintypes

k32 = ctypes.WinDLL("kernel32", use_last_error=True)
nt = ctypes.WinDLL("ntdll")
k32.OpenProcess.restype = wintypes.HANDLE
k32.GetCurrentProcess.restype = wintypes.HANDLE
k32.GetCurrentThread.restype = wintypes.HANDLE
PROCESS_SUSPEND_RESUME = 0x0800
PROCESS_QUERY_INFORMATION = 0x0400
SYNCHRONIZE = 0x00100000
HIGH_PRIORITY_CLASS = 0x80


def main():
    pid, quota_ms, period_ms, cpu = int(sys.argv[1]), float(sys.argv[2]), float(sys.argv[3]), int(sys.argv[4])
    me = k32.GetCurrentProcess()
    k32.SetPriorityClass(me, HIGH_PRIORITY_CLASS)
    k32.SetProcessAffinityMask(me, ctypes.c_size_t(1 << cpu))
    h = k32.OpenProcess(PROCESS_SUSPEND_RESUME | PROCESS_QUERY_INFORMATION | SYNCHRONIZE, False, pid)
    if not h:
        sys.exit("cannot open process")
    query = k32.QueryProcessCycleTime
    query.argtypes = [wintypes.HANDLE, ctypes.POINTER(ctypes.c_ulonglong)]
    thread_query = k32.QueryThreadCycleTime
    thread_query.argtypes = [wintypes.HANDLE, ctypes.POINTER(ctypes.c_ulonglong)]
    # cycles per second of this CPU's cycle counter, from spinning for a moment
    c0, c1 = ctypes.c_ulonglong(), ctypes.c_ulonglong()
    me_thread = k32.GetCurrentThread()
    thread_query(me_thread, ctypes.byref(c0))
    t0 = time.perf_counter()
    while time.perf_counter() - t0 < 0.2:
        pass
    thread_query(me_thread, ctypes.byref(c1))
    rate = (c1.value - c0.value) / (time.perf_counter() - t0)
    quota = quota_ms / 1000.0 * rate
    period = period_ms / 1000.0
    now = ctypes.c_ulonglong()
    query(h, ctypes.byref(now))
    used_at = now.value
    next_period = time.perf_counter() + period
    freezes = 0
    frozen = 0.0
    while True:
        if k32.WaitForSingleObject(h, 0) == 0:
            break
        t = time.perf_counter()
        if t >= next_period:
            next_period += period * int((t - next_period) / period + 1)
            query(h, ctypes.byref(now))
            used_at = now.value
            continue
        query(h, ctypes.byref(now))
        if now.value - used_at >= quota:
            nt.NtSuspendProcess(h)
            start = time.perf_counter()
            freezes += 1
            while time.perf_counter() < next_period:
                pass
            nt.NtResumeProcess(h)
            frozen += time.perf_counter() - start
            next_period += period
            query(h, ctypes.byref(now))
            used_at = now.value
    print(f"throttle: {freezes} freezes, {frozen:.1f} s frozen, cycle rate {rate / 1e9:.2f} GHz")


main()
