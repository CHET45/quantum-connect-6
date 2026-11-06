param([string]$sp, [string]$jar)
$java = (Get-ChildItem "$env:USERPROFILE\.gradle\jdks" -Directory | Where-Object { $_.Name -like '*25*' } | Select-Object -First 1).FullName + '\bin\java.exe'
$cp = "$sp\frozen\out;$sp\frozen\main;$sp\frozen\tools;$sp\frozen\core-2.0-SNAPSHOT.jar;$sp\frozen\slf4j-api.jar"
$csv = "$sp\cold.csv"
if (Test-Path $csv) { Remove-Item $csv }
foreach ($cfg in @(@('8','12'), @('15','11'), @('15','22'))) {
  foreach ($warm in @(0, 300)) {
    $label = "w$warm"
    $a = @('-XX:ActiveProcessorCount=2', '-Xmx512m', '-cp', $cp, 'ch.qc.tools.ColdProbe', $jar, $cfg[0], $cfg[1], '9', '60', "$warm", $label, $csv)
    $p = Start-Process -FilePath $java -ArgumentList $a -PassThru -NoNewWindow
    try { $p.PriorityClass = 'High' } catch { }
    $p.WaitForExit()
    "{0}x{1} warm {2}: exit {3}" -f $cfg[0], $cfg[1], $warm, $p.ExitCode
  }
}
