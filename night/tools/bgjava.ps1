# Runs a wt-pass tool main below normal priority on the E-cores, one run per "LABEL|MAIN|ARGS" item, in order.
param([string[]]$Runs)
$Q = "C:\Users\Chtean45m\Documents\quantum-connect"
$W = "$Q\night\wt-pass"
$J = (Get-ChildItem "$env:USERPROFILE\.gradle\jdks" -Directory | Where-Object Name -match '25' | Select-Object -First 1).FullName + "\bin\java.exe"
$SLF = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1" -Recurse -Filter 'slf4j-api*.jar' | Where-Object Name -notmatch 'sources' | Select-Object -First 1).FullName
$C = "$Q\night\logs\threats\classes"
New-Item -ItemType Directory -Force $C | Out-Null
Copy-Item -Recurse -Force "$W\build\classes\java\main" "$C\"
Copy-Item -Recurse -Force "$W\build\classes\java\tools" "$C\"
$CP = "$C\main;$C\tools;$W\libs\core-2.0-SNAPSHOT.jar;$SLF"
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
foreach ($r in $Runs) {
  $label, $main, $a = $r.Split('|', 3)
  $log = "$Q\night\logs\threats\$label.log"
  $p = Start-Process -FilePath $J -ArgumentList "-Xmx4g -cp `"$CP`" $main $a" -WorkingDirectory $W -NoNewWindow -PassThru `
    -RedirectStandardOutput $log -RedirectStandardError "$log.err"
  Start-Sleep -Milliseconds 300
  $p.PriorityClass = 'BelowNormal'
  $p.ProcessorAffinity = [IntPtr]0xFF000
  $p.WaitForExit()
  "=== $label $(Get-Date -Format HH:mm)"
}
