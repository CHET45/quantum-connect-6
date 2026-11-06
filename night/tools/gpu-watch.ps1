# GPU watchdog. Ends our GPU fits at the first new NVIDIA driver error (writes logs\gpu-stop and exits, which notifies
# the coordinator) and whenever another program starts computing on the GPU during a fit (logs it and keeps watching).
# Both driver-error episodes on 2026-11-04 happened with two GPU compute programs at once: parallel fits at night,
# a fit plus an Ollama model at 11:12. That link is inferred from timing, not proven.
param([string]$until = "18:00")
$logs = "C:\Users\Chtean45m\Documents\quantum-connect\night\logs"
$flag = "$logs\gpu-stop"
$end = [datetime]::Today.Add([timespan]::Parse($until))
$start = Get-Date

function Stop-GpuFits([string]$why) {
  $gpuPids = & nvidia-smi --query-compute-apps=pid --format=csv,noheader 2>$null | ForEach-Object { $_.Trim() }
  Get-CimInstance Win32_Process -Filter "Name='python.exe'" | Where-Object { $_.CommandLine -match 'qctrain' -and $gpuPids -contains "$($_.ProcessId)" } | ForEach-Object {
    $line = "$(Get-Date -Format HH:mm:ss) stopping GPU fit, pid $($_.ProcessId): $why"
    $line
    Add-Content -Path "$logs\gpu-watch.log" -Value $line -Encoding utf8
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
  }
}

"watching from $start until $end"
while ((Get-Date) -lt $end) {
  Start-Sleep -Seconds 10
  $ev = Get-WinEvent -FilterHashtable @{LogName='System'; ProviderName='nvlddmkm'; StartTime=$start} -ErrorAction SilentlyContinue
  if ($ev) {
    $first = $ev | Sort-Object TimeCreated | Select-Object -First 1
    $msg = "NVIDIA driver error at $($first.TimeCreated), id $($first.Id), count $(@($ev).Count)"
    $msg
    Set-Content -Path $flag -Value $msg -Encoding utf8
    Stop-GpuFits "driver error"
    exit 0
  }
  # Windows desktop programs may be listed too; they are not compute jobs
  $others = & nvidia-smi --query-compute-apps=process_name --format=csv,noheader 2>$null | Where-Object { $_ -and $_ -notmatch 'python' -and $_ -notmatch '\\(dwm|LogonUI|explorer|TextInputHost|ShellExperienceHost)\.exe$' }
  if ($others) { Stop-GpuFits "other GPU program: $($others -join ', ')" }
}
"no NVIDIA driver errors until $end"
