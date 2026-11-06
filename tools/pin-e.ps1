# Keeps training tools (self-play, FastMatch gates, CPU fits of ktrain) on the E-cores (logical 12-19) so real-time
# gates on the P-cores measure clean times. Runs until the given time.
param([string]$until = "09:00")
$end = [datetime]::Today.Add([timespan]::Parse($until))
if ($end -lt (Get-Date)) { $end = $end.AddDays(1) }
while ((Get-Date) -lt $end) {
  Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='python.exe'" |
    Where-Object { $_.CommandLine -match 'SelfPlayMain|ch\.qc\.tools\.FastMatch|FeatureSpecDump' -and $_.CommandLine -notmatch '/gen[2-9]/|k\d-g[2-9]' } |
    ForEach-Object {
      $p = Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue
      if ($p -and $p.ProcessorAffinity -ne [IntPtr]0xFF000) {
        try { $p.ProcessorAffinity = [IntPtr]0xFF000 } catch {}
      }
    }
  Start-Sleep -Seconds 5
}
