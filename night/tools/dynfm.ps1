# FastMatch runs for the dynamic-rules work (wt-dyn), below normal priority on the E-cores, one at a time.
# Usage: dynfm.ps1 -Tree wt-dyn -Out logs\dyn "LABEL|fastmatch args" ["LABEL|args" ...]
param([string]$Tree = "wt-dyn", [string]$Out = "logs\dyn", [Parameter(ValueFromRemainingArguments = $true)][string[]]$Runs)
$Q = "C:\Users\Chtean45m\Documents\quantum-connect"
$W = "$Q\night\$Tree"
$J = (Get-ChildItem "$env:USERPROFILE\.gradle\jdks" -Directory | Where-Object Name -match '25' | Select-Object -First 1).FullName + "\bin\java.exe"
$SLF = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1" -Recurse -Filter 'slf4j-api*.jar' | Where-Object Name -notmatch 'sources' | Select-Object -First 1).FullName
$CP = "$W\build\classes\java\main;$W\build\classes\java\tools;$W\build\resources\main;$W\libs\core-2.0-SNAPSHOT.jar;$SLF"
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
$dir = "$Q\night\$Out"
New-Item -ItemType Directory -Force $dir | Out-Null
foreach ($r in $Runs) {
  $label, $a = $r.Split('|', 2)
  $log = "$dir\$label.log"
  $argList = "-Xmx2g -cp `"$CP`" ch.qc.tools.FastMatch $a --out `"$dir\$label.jsonl`""
  $p = Start-Process -FilePath $J -ArgumentList $argList -WorkingDirectory $W -NoNewWindow -PassThru `
    -RedirectStandardOutput $log -RedirectStandardError "$log.err"
  Start-Sleep -Milliseconds 300
  $p.PriorityClass = 'BelowNormal'
  $p.ProcessorAffinity = [IntPtr]0xFF000
  $p.WaitForExit()
  "=== $label $(Get-Date -Format HH:mm)"
  Get-Content $log | Select-Object -Last 3
}
