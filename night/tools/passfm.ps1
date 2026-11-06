# Fixed-node pass-trap experiments (FastMatch of wt-pass), below normal priority on the E-cores.
# Usage: passfm.ps1 LABEL "fastmatch args" [more "LABEL|args" pairs...]
param([string[]]$Runs)
$Q = "C:\Users\Chtean45m\Documents\quantum-connect"
$W = "$Q\night\wt-pass"
$J = (Get-ChildItem "$env:USERPROFILE\.gradle\jdks" -Directory | Where-Object Name -match '25' | Select-Object -First 1).FullName + "\bin\java.exe"
$SLF = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1" -Recurse -Filter 'slf4j-api*.jar' | Where-Object Name -notmatch 'sources' | Select-Object -First 1).FullName
$CP = "$W\build\classes\java\main;$W\build\classes\java\tools;$W\libs\core-2.0-SNAPSHOT.jar;$SLF"
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
foreach ($r in $Runs) {
  $label, $a = $r.Split('|', 2)
  $log = "$Q\night\logs\pass\$label.log"
  $argList = "-Xmx3g -cp `"$CP`" ch.qc.tools.FastMatch $a --out `"$Q\night\logs\pass\$label.jsonl`""
  $p = Start-Process -FilePath $J -ArgumentList $argList -WorkingDirectory $W -NoNewWindow -PassThru `
    -RedirectStandardOutput $log -RedirectStandardError "$log.err"
  Start-Sleep -Milliseconds 300
  $p.PriorityClass = 'BelowNormal'
  $p.ProcessorAffinity = [IntPtr]0xFF000
  $p.WaitForExit()
  "=== $label $(Get-Date -Format HH:mm)"
  Get-Content $log | Select-Object -Last 3
}
