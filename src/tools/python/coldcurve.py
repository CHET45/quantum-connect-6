"""Cold speed of two builds: knps in slices of cumulative search time, median over fresh JVMs (SpeedBench --cold).

Usage: coldcurve.py JAVA CP_A CP_B POSITIONS RUNS SECONDS NODES SIZE...
"""
import statistics
import subprocess
import sys

SLICES = [0, 80, 160, 320, 640, 1280, 2560, 5120]


def one_run(java, cp, positions, size, nodes, seconds):
    out = subprocess.run([java, '-cp', cp, 'ch.qc.tools.SpeedBench', '--positions', positions, '--cold', '--sizes', size,
                          '--nodes', nodes, '--seconds', seconds], capture_output=True, text=True).stdout
    points = []
    t = 0.0
    for line in out.splitlines():
        p = line.split()
        if len(p) == 3:
            t += float(p[2])
            points.append((t, int(p[1])))
    return points


def nodes_at(points, t):
    prev = (0.0, 0)
    for p in points:
        if p[0] >= t:
            f = (t - prev[0]) / (p[0] - prev[0]) if p[0] > prev[0] else 1
            return prev[1] + f * (p[1] - prev[1])
        prev = p
    return None


def main():
    java, cp_a, cp_b, positions, runs, seconds, nodes = sys.argv[1:8]
    for size in sys.argv[8:]:
        runs_a = [one_run(java, cp_a, positions, size, nodes, seconds) for _ in range(int(runs))]
        runs_b = [one_run(java, cp_b, positions, size, nodes, seconds) for _ in range(int(runs))]
        print(size, 'knps per slice of cumulative search time (ms), median of', runs, 'fresh JVMs')
        print('  slice ' + ''.join(f'{SLICES[i]:>5}-{SLICES[i + 1]:<5}' for i in range(len(SLICES) - 1)))
        for name, results in (('A', runs_a), ('B', runs_b)):
            row = []
            for i in range(len(SLICES) - 1):
                vals = []
                for points in results:
                    a, b = nodes_at(points, SLICES[i]), nodes_at(points, SLICES[i + 1])
                    if a is not None and b is not None:
                        vals.append((b - a) / (SLICES[i + 1] - SLICES[i]))
                row.append(f'{statistics.median(vals):>10.0f} ' if vals else '         - ')
            print(f'  {name:<5} ' + ''.join(row))


main()
