#!/usr/bin/env bash
# Fixed-node games from the empty board (Idle priority above 2 threads):
#   tools/book-fast.sh BOOK SIZE WEIGHTS NODES PAIRS THREADS OUT [SEED] [BookFast options...]
set -e
BOOK=$1; SIZE=$2; W=$3; NODES=$4; PAIRS=$5; THREADS=$6; OUT=$7; SEED=${8:-1}
shift 7
[ $# -gt 0 ] && shift
cd "$(dirname "$0")/.."
JB=$(ls -d ~/.gradle/jdks/*25*/bin | head -1)
RUN=build/book-fast-$$
mkdir -p "$RUN"
cp -r build/classes/java/main build/classes/java/tools "$RUN/"
trap 'rm -rf "$RUN"' EXIT
CP="$RUN/main;$RUN/tools;libs/core-2.0-SNAPSHOT.jar"
"$JB/java.exe" -Xmx3g -cp "$CP" ch.qc.tools.BookFast --book "$BOOK" --size "$SIZE" --weights "$W" --nodes "$NODES" --pairs "$PAIRS" \
  --threads "$THREADS" --seed "$SEED" --out "$OUT" "$@" 2>&1 | grep --line-buffered -v "Picked up" &
if [ "$THREADS" -gt 2 ]; then
sleep 2
for P in $(powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'BookFast' -and \$_.CommandLine -match [regex]::Escape('$OUT') } | ForEach-Object { \$_.ProcessId }" | tr -d '\r'); do
  powershell -NoProfile -Command "(Get-Process -Id $P).PriorityClass='Idle'" || true
done
fi
wait
