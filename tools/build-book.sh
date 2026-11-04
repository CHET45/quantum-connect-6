#!/usr/bin/env bash
# Rebuilds the opening book for given weights.
#
#   tools/build-book.sh WEIGHTS MINUTES THREADS OUT [BookBuilder options...]
#
# WEIGHTS  comma list of .qcw files or directories holding eval*.qcw, or "hand". Per board size it picks as the bot
#          does: a table trained for the size, then a generic one, then the hand evaluation. "weights" (the directory)
#          means whatever the jar will embed, which is what the final book should be built with.
# MINUTES  wall-clock cap for all three sizes.
# THREADS  worker threads. Above 2 the process drops itself to Idle priority (night rule).
# OUT      book file. weights/book.qcb is what `gradlew jar` embeds; OUT.txt is a readable listing.
#
# It compiles first, then runs without Gradle. The JVM takes 64 MB of table per thread plus the evaluation.
# Positions already analysed with the same weights and search code are reused from OUT's book-cache directory,
# so a run that is cut short can simply be started again; add --fresh to ignore them.
set -e
if [ $# -lt 4 ]; then
  sed -n 2,15p "$0"
  exit 2
fi
WEIGHTS=$1; MINUTES=$2; THREADS=$3; OUT=$4; shift 4
cd "$(dirname "$0")/.."
ROOT=$(pwd)
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
./gradlew.bat -q toolsClasses 2>&1 | grep -v "Picked up" || true
JB=$(ls -d ~/.gradle/jdks/*25*/bin | head -1)
# a private copy of the classes, so a later Gradle run cannot change them under a long build
RUN=build/book-run-$$
mkdir -p "$RUN"
cp -r build/classes/java/main build/classes/java/tools "$RUN/"
trap 'rm -rf "$RUN"' EXIT
CP="$RUN/main;$RUN/tools;libs/core-2.0-SNAPSHOT.jar"
"$JB/java.exe" -Xmx3g -cp "$CP" ch.qc.tools.BookBuilder --weights "$WEIGHTS" --minutes "$MINUTES" --threads "$THREADS" \
  --out "$OUT" "$@" 2>&1 | grep --line-buffered -v "Picked up" &
if [ "$THREADS" -gt 2 ]; then
  sleep 3
  for P in $(powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'BookBuilder' -and \$_.CommandLine -match [regex]::Escape('$RUN') } | ForEach-Object { \$_.ProcessId }" | tr -d ''); do
    powershell -NoProfile -Command "(Get-Process -Id $P).PriorityClass='Idle'" || true
    echo "java process $P set to Idle priority"
  done
fi
wait
ls -l "$OUT"
