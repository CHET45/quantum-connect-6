param([string]$sp, [string]$out, [string[]]$specs)
# Each spec: rows|cols|A|B|pairs|threads|weights|openings
$java = (Get-ChildItem "$env:USERPROFILE\.gradle\jdks" -Directory | Where-Object { $_.Name -like '*25*' } | Select-Object -First 1).FullName + '\bin\java.exe'
$cp = "$sp\frozen\out;$sp\frozen\main;$sp\frozen\tools;$sp\frozen\core-2.0-SNAPSHOT.jar;$sp\frozen\slf4j-api.jar"
foreach ($spec in $specs) {
  $f = $spec.Split('|')
  $tmp = "$out.tmp"
  $a = @('-Xmx3g', '-cp', $cp, 'ch.qc.tools.TimeMatch') + $f
  $p = Start-Process -FilePath $java -ArgumentList $a -PassThru -NoNewWindow -RedirectStandardOutput $tmp -RedirectStandardError "$out.err"
  try { $p.PriorityClass = 'High' } catch { }
  $p.WaitForExit()
  Get-Content $tmp | Select-Object -Last 4 | Add-Content $out
  Get-Content $tmp | Select-Object -Last 4
}
