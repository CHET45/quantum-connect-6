#!/usr/bin/env bash
# Fixed-node experiments on the first moves, any board size: tools/first-moves.sh THREADS OUT FirstMoves-options...
set -e
THREADS=$1; OUT=$2; shift 2
cd "$(dirname "$0")/.."
JB=$(ls -d ~/.gradle/jdks/*25*/bin | head -1)
RUN=build/first-moves-$$
mkdir -p "$RUN"
cp -r build/classes/java/main build/classes/java/tools "$RUN/"
trap 'rm -rf "$RUN"' EXIT
CP="$RUN/main;$RUN/tools;libs/core-2.0-SNAPSHOT.jar"
"$JB/java.exe" -Xmx2g -cp "$CP" ch.qc.tools.FirstMoves --threads "$THREADS" --out "$OUT" "$@" 2>&1 | grep --line-buffered -v "Picked up" &
if [ "$THREADS" -gt 2 ] || [ -n "$IDLE" ]; then
  sleep 3
  for P in $(powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'FirstMoves' -and \$_.CommandLine -match [regex]::Escape('$RUN') } | ForEach-Object { \$_.ProcessId }" | tr -d '\r'); do
    powershell -NoProfile -Command "(Get-Process -Id $P).PriorityClass='Idle'" || true
  done
fi
wait
