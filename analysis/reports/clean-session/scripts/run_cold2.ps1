param([string]$sp, [string]$jar)
$java = (Get-ChildItem "$env:USERPROFILE\.gradle\jdks" -Directory | Where-Object { $_.Name -like '*25*' } | Select-Object -First 1).FullName + '\bin\java.exe'
$cp = "$sp\frozen\out;$sp\frozen\main;$sp\frozen\tools;$sp\frozen\core-2.0-SNAPSHOT.jar;$sp\frozen\slf4j-api.jar"
$csv = "$sp\cold2.csv"
if (Test-Path $csv) { Remove-Item $csv }
foreach ($cfg in @(@('15','11'), @('8','12'), @('15','22'))) {
  foreach ($run in @(@('cold', 0, 7), @('w2000', 2000, 7), @('hot', 0, 12))) {
    $a = @('-XX:ActiveProcessorCount=2', '-Xmx512m', '-cp', $cp, 'ch.qc.tools.ColdProbe', $jar, $cfg[0], $cfg[1], "$($run[2])", '170', "$($run[1])", $run[0], $csv)
    $p = Start-Process -FilePath $java -ArgumentList $a -PassThru -NoNewWindow -RedirectStandardError "$sp\cold2.err"
    try { $p.PriorityClass = 'High' } catch { }
    $p.WaitForExit()
    "{0}x{1} {2} done {3}" -f $cfg[0], $cfg[1], $run[0], (Get-Date).ToString('HH:mm:ss')
  }
}
