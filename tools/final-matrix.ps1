# Builds the set of final jars: one per assumed k (all of them with every trained table and the rules inference),
# each through final-build.ps1 (all tests + a sanity match under its rules). Blast, cooldown and times are the core-2.0
# values unless given. -Deliver copies every jar plus SHA256SUMS.txt to OneDrive qc-bot\final.
#   & night\tools\final-matrix.ps1 [-Ks 6,4,5,7,8] [-Rows 15 -Cols 30] [-Deliver]
param(
  [int[]]$Ks = @(6, 4, 5, 7, 8),
  [string]$Blast = "3x3",
  [int]$Cooldown = 10,
  [int]$LimitMs = 100,
  [int]$MatchSec = 30,
  [int]$Rows = 15,
  [int]$Cols = 30,
  [string]$Suffix = "",
  [switch]$Deliver
)
$ErrorActionPreference = "Stop"
$Q = "C:\Users\Chtean45m\Documents\quantum-connect"
$fb = "$Q\night\tools\final-build.ps1"
$DIST = "$Q\qc-bot\dist\night\final"
$built = @()
foreach ($k in $Ks) {
  $tag = "k$k" + $(if ($Blast -ne "3x3") { "-$Blast" } else { "" }) + $(if ($Cooldown -ne 10) { "-cd$Cooldown" } else { "" }) +
    $(if ($LimitMs -ne 100) { "-${LimitMs}ms" } else { "" }) + $Suffix
  $out = & $fb -K $k -Blast $Blast -Cooldown $Cooldown -LimitMs $LimitMs -MatchSec $MatchSec -Rows $Rows -Cols $Cols -Tag $tag -Tables all
  $out
  $built += "$DIST\qc-bot-final-$tag.jar"
}
# leave the source at the core values (the build directory keeps the last jar's classes)
$f = "$Q\night\wt-dyn\src\main\java\ch\qc\bot\Announced.java"
$src = [IO.File]::ReadAllText($f)
foreach ($p in @(@("int K", 6), @("int BLAST_W", 1), @("int BLAST_H", 1), @("int COOLDOWN", 10), @("long CALL_LIMIT_MS", 100),
    @("long MATCH_SECONDS", 30), @("long WARMUP_MS", 1000))) {
  $src = [regex]::Replace($src, "static final $($p[0]) = \d+;", "static final $($p[0]) = $($p[1]);")
}
[IO.File]::WriteAllText($f, $src)
$sums = foreach ($j in $built) { "$((Get-FileHash $j -Algorithm SHA256).Hash.ToLower())  $(Split-Path $j -Leaf)  $([math]::Round((Get-Item $j).Length / 1MB, 2)) MB" }
$sums
if ($Deliver) {
  $dst = "C:\Users\Chtean45m\OneDrive\qc-bot\final"
  New-Item -ItemType Directory -Force $dst | Out-Null
  foreach ($j in $built) { Copy-Item $j $dst -Force }
  $sums | Set-Content "$dst\SHA256SUMS.txt" -Encoding utf8
  "delivered to $dst"
}
