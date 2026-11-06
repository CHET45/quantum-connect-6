# Final day (2026-11-06): set the announced rules and times, build the jar, run every test, play a sanity match under
# the announced rules, and stage the jar with its sha256. -Deliver also copies it to OneDrive qc-bot.
#
#   & night\tools\final-build.ps1 -K 5 -Blast 3x3 -Cooldown 10 -LimitMs 100 -Rows 15 -Cols 30
#   & night\tools\final-build.ps1 ... -Deliver
#
# Defaults are the core-2.0 rules: with them the jar plays exactly like final-base / rc8.
# Blast is the full block, WxH, both odd (3x3, 5x5, 1x1, 3x5 ...). Rows/Cols are the announced board, for the match only
# (the bot reads the board size from the engine). The real-time gate (night/tools/finalgate.sh) uses the core-2.0
# engine: it is valid only for k 6, 3x3, cooldown 10, 100 ms.
param(
  [int]$K = 6,
  [string]$Blast = "3x3",
  [int]$Cooldown = 10,
  [int]$LimitMs = 100,
  [int]$MatchSec = 30,
  [int]$WarmupMs = 1000,
  [int]$Rows = 15,
  [int]$Cols = 30,
  [int]$Pairs = 50,
  [int]$Nodes = 20000,
  [string]$Tag = "",
  # tables for other k from wt-dyn\weights-k: "all" (universal jar), "none", or a list like "5" or "4,5"; k=6 is always in
  [string]$Tables = "all",
  [switch]$NoMatch,
  [switch]$Deliver
)
$ErrorActionPreference = "Stop"
$Q = "C:\Users\Chtean45m\Documents\quantum-connect"
$W = "$Q\night\wt-dyn"
$OUT = "$Q\night\logs\final\build"
$DIST = "$Q\qc-bot\dist\night\final"
$ONEDRIVE = "C:\Users\Chtean45m\OneDrive\qc-bot"
New-Item -ItemType Directory -Force $OUT, $DIST | Out-Null

if ($Blast -notmatch '^(\d+)x(\d+)$') { throw "Blast must look like 3x3, got $Blast" }
$bw = [int]$Matches[1]; $bh = [int]$Matches[2]
if ($bw % 2 -ne 1 -or $bh % 2 -ne 1) { throw "Blast sides must be odd (3x3, 5x5, 1x1), got $Blast" }
$halfW = ($bw - 1) / 2; $halfH = ($bh - 1) / 2
if ($Tag -eq "") { $Tag = "k$K-$Blast-cd$Cooldown-${LimitMs}ms" }
"== final-build $Tag  (k=$K blast=$Blast half=$halfW/$halfH cooldown=$Cooldown limit=${LimitMs}ms match=${MatchSec}s warmup=${WarmupMs}ms)"

# 1. switches
$f = "$W\src\main\java\ch\qc\bot\Announced.java"
$src = [IO.File]::ReadAllText($f)
$set = [ordered]@{ "int K" = $K; "int BLAST_W" = $halfW; "int BLAST_H" = $halfH; "int COOLDOWN" = $Cooldown;
  "long CALL_LIMIT_MS" = $LimitMs; "long MATCH_SECONDS" = $MatchSec; "long WARMUP_MS" = $WarmupMs }
foreach ($key in $set.Keys) {
  $pat = "static final $key = \d+;"
  if ($src -notmatch $pat) { throw "Announced.java: '$key' not found" }
  $src = [regex]::Replace($src, $pat, "static final $key = $($set[$key]);")
}
[IO.File]::WriteAllText($f, $src)
cmd /c "git -C `"$W`" diff --stat -- src/main/java/ch/qc/bot/Announced.java 2>nul"

# 1b. tables for other k (weights\eval-generic.qcw, k=6, is always embedded)
Get-ChildItem "$W\weights\eval-generic-k*.qcw" -ErrorAction SilentlyContinue | Remove-Item -Force
$avail = Get-ChildItem "$W\weights-k\eval-generic-k*.qcw" -ErrorAction SilentlyContinue
$picked = @()
foreach ($t in $avail) {
  $tk = [int]($t.BaseName -replace '^eval-generic-k', '')
  if ($Tables -eq "all" -or ($Tables -ne "none" -and ($Tables.Split(',') | ForEach-Object { [int]$_ }) -contains $tk)) {
    Copy-Item $t.FullName "$W\weights\" -Force
    $picked += "k$tk"
  }
}
"tables: k6" + $(if ($picked.Count -gt 0) { ", " + ($picked -join ", ") } else { "" })

# 2. build + all tests (UploadRulesTest, ConformanceTest, AnnouncedTest, ...)
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
Push-Location $W
cmd /c ".\gradlew.bat --no-daemon jar toolsClasses test -q > `"$OUT\$Tag-gradle.log`" 2>&1"
$rc = $LASTEXITCODE
Pop-Location
$tests = 0; $fail = 0; $skip = 0; $skipped = @()
foreach ($x in Get-ChildItem "$W\build\test-results\test\*.xml") {
  [xml]$r = Get-Content $x.FullName
  $s = $r.testsuite
  $tests += [int]$s.tests; $fail += [int]$s.failures + [int]$s.errors; $skip += [int]$s.skipped
  foreach ($c in $s.testcase) { if ($c.skipped -ne $null) { $skipped += "$($s.name).$($c.name)" } }
}
"tests $tests, failed $fail, skipped $skip"
if ($skipped.Count -gt 0) { "  skipped: " + ($skipped -join ", ") }
if ($rc -ne 0 -or $fail -gt 0) {
  Get-Content "$OUT\$Tag-gradle.log" | Select-String -Pattern "FAILED|error:|Exception" | Select-Object -First 20
  throw "BUILD OR TESTS FAILED (log $OUT\$Tag-gradle.log) - do not ship"
}

# 3. stage the jar
$jar = "$DIST\qc-bot-final-$Tag.jar"
Copy-Item "$W\build\libs\quantumconnect-starter-1.0-SNAPSHOT.jar" $jar -Force
$sha = (Get-FileHash $jar -Algorithm SHA256).Hash.ToLower()
"jar $jar"
"sha256 $sha"

# 4. sanity match under the announced rules: the bot (tables if k = 6, else hand evaluation) vs its 3-ply fallback
if (-not $NoMatch) {
  $suite = $null
  foreach ($d in @("wt-dyn", "wt-eval", "wt-main", "wt-pass")) {
    $p = "$Q\night\$d\suites\openings-${Rows}x$Cols.txt"
    if (Test-Path $p) { $suite = $p; break }
  }
  if ($suite -eq $null) {
    # two-ply openings over the whole width, k-independent; enough for a sanity check
    $suite = "$OUT\openings-${Rows}x$Cols-2ply.txt"
    $rnd = New-Object System.Random 15
    $lines = @("# ${Rows}x$Cols two-ply openings for final-build sanity matches, seed 15")
    for ($i = 0; $i -lt 400; $i++) { $lines += "$($rnd.Next($Cols)),$($rnd.Next($Cols))" }
    [IO.File]::WriteAllLines($suite, $lines)
  }
  $plies = if ($K -ne 6 -or $suite -like "*2ply*") { "--openingPlies 2" } else { "" }
  $J = (Get-ChildItem "$env:USERPROFILE\.gradle\jdks" -Directory | Where-Object Name -match '25' | Select-Object -First 1).FullName + "\bin\java.exe"
  $SLF = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1" -Recurse -Filter 'slf4j-api*.jar' | Where-Object Name -notmatch 'sources' | Select-Object -First 1).FullName
  $CP = "$W\build\classes\java\main;$W\build\classes\java\tools;$W\build\resources\main;$W\libs\core-2.0-SNAPSHOT.jar;$SLF"
  $log = "$OUT\$Tag-sanity.log"
  $argList = "-Xmx2g -cp `"$CP`" ch.qc.tools.FastMatch --a @auto --b tactics --k $K --blastW $halfW --blastH $halfH --cooldown $Cooldown --rows $Rows --cols $Cols --pairs $Pairs --nodes $Nodes --threads 6 --seed 7 --openings `"$suite`" $plies --out `"$OUT\$Tag-sanity.jsonl`""
  $p = Start-Process -FilePath $J -ArgumentList $argList -WorkingDirectory $W -NoNewWindow -PassThru -RedirectStandardOutput $log -RedirectStandardError "$log.err"
  Start-Sleep -Milliseconds 300
  try { $p.PriorityClass = 'BelowNormal'; $p.ProcessorAffinity = [IntPtr]0xFF000 } catch {}
  $p.WaitForExit()
  "sanity ($suite) vs tactics:"
  Get-Content $log | Select-Object -Last 3
  $rules = Get-Content $log | Select-String "missed wins in one" | Select-Object -Last 1
  if ($rules -eq $null -or $rules.Line -notmatch "A: \d+ moves, 0 missed wins in one, 0 one-ply blunders") {
    throw "SANITY MATCH: missing summary or A missed a win / blundered - look at $log"
  }
}

# 5. deliver
if ($Deliver) {
  if (-not (Test-Path $ONEDRIVE)) { throw "OneDrive folder $ONEDRIVE not found" }
  $dst = "$ONEDRIVE\qc-bot-final-$Tag.jar"
  Copy-Item $jar $dst -Force
  $sha2 = (Get-FileHash $dst -Algorithm SHA256).Hash.ToLower()
  if ($sha2 -ne $sha) { throw "OneDrive copy differs: $sha2" }
  "delivered $dst"
}
"== OK $Tag sha256 $sha"
