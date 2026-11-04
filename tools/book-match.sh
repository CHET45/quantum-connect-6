#!/usr/bin/env bash
# Real-protocol games from the empty board under the machine-wide real-time lock:
#   tools/book-match.sh JAR_A JAR_B SIZE PAIRS OUT.jsonl [THREADS]
# Takes a private copy of the classes, so a later Gradle run cannot change them under a running match.
set -e
A=$1; B=$2; SIZE=$3; PAIRS=$4; OUT=$5; THREADS=${6:-5}
cd "$(dirname "$0")/.."
JB=$(ls -d ~/.gradle/jdks/*25*/bin | head -1)
RUN=build/book-match-$$
mkdir -p "$RUN"
cp -r build/classes/java/main build/classes/java/tools "$RUN/"
trap 'rm -rf "$RUN"' EXIT
LB=$(find ~/.gradle/caches/modules-2/files-2.1 \( -name "logback-classic-1.5.18.jar" -o -name "logback-core-1.5.18.jar" -o -name "slf4j-api-2.0.17.jar" \) | while read -r f; do cygpath -w "$f"; done | tr '\n' ';')
CP="$RUN/main;$RUN/tools;libs/core-2.0-SNAPSHOT.jar;$LB"
LOG=../logs/book-$(basename "$OUT" .jsonl).log
/c/Users/Chtean45m/miniconda3/python.exe ../tools/rtrun.py --log "$LOG" -- "$(cygpath -w "$JB/java.exe")" -Xmx4g -XX:+UseSerialGC -cp "$CP" \
  ch.qc.tools.BookMatch --a "jar:$A" --b "jar:$B" --pairs "$PAIRS" --threads "$THREADS" --size "$SIZE" --out "$OUT"
grep -v "Operation timed out\|Invalid column\|Picked up" "$LOG" | tail -2 | cut -c1-300
