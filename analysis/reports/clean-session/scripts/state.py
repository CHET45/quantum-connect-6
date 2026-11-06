"""Lists the project's running processes, GPU users and each worktree's uncommitted files after the agents stopped."""
import json
import re
import subprocess

ps = "Get-CimInstance Win32_Process -Filter \"Name='java.exe' or Name='python.exe' or Name='powershell.exe'\" | Select-Object ProcessId,Name,CommandLine,@{n='t';e={$_.CreationDate.ToString('HH:mm')}} | ConvertTo-Json -Compress"
out = subprocess.run(["powershell", "-NoProfile", "-Command", ps], capture_output=True, text=True).stdout
rows = json.loads(out)
for r in sorted(rows, key=lambda r: r["t"]):
    c = r["CommandLine"] or ""
    if not re.search(r"ch\.qc|qctrain|rtrun|gpu-watch|night[/\\]|qc-bot[/\\]data", c):
        continue
    m = re.search(r"(ch\.qc\.\S+|qctrain\.\S+|rtrun\.py|gpu-watch\.ps1|\S+\.py)", c)
    t = re.search(r"--threads\s+(\d+)", c)
    print(r["t"], r["ProcessId"], r["Name"][:6], m.group(1)[-34:] if m else c[:40], ("threads " + t.group(1)) if t else "")
print("GPU:", subprocess.run(["nvidia-smi", "--query-compute-apps=pid,process_name", "--format=csv,noheader"], capture_output=True, text=True).stdout.strip() or "none")
base = "C:/Users/Chtean45m/Documents/quantum-connect/night/wt-"
for s in ("main", "time", "speed", "search", "eval", "book"):
    head = subprocess.run(["git", "-C", base + s, "log", "-1", "--format=%h %ad %s", "--date=format:%H:%M"], capture_output=True, text=True).stdout.strip()[:75]
    st = subprocess.run(["git", "-C", base + s, "status", "--short"], capture_output=True, text=True).stdout.strip().splitlines()
    print(s, "|", head, "| uncommitted:", [x[:60] for x in st][:6])
