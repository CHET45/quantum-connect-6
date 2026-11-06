#!/usr/bin/env bash
# Real-protocol match A vs B over many new sizes (one MatchMain per size), classes of wt-blast (rc8 tools).
# Usage: sizes2.sh LABEL SPEC_A SPEC_B "SIZE:PAIRS ..." [MASK] [THREADS] [SLOT]
set -u
Q="C:/Users/Chtean45m/Documents/quantum-connect"
M="$Q/night/wt-blast"
PY=/c/Users/Chtean45m/miniconda3/python.exe
L="$Q/night/logs/sizes2/$1"; mkdir -p "$L"
MASK=${5:-FFF}; TH=${6:-4}; SLOT=${7:-2}
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
cd "$M" || exit 1
J=$(ls -d ~/.gradle/jdks/*25*/bin | head -1)
SLF=$(cygpath -m "$(find ~/.gradle/caches/modules-2/files-2.1 -name 'slf4j-api*.jar' -not -name '*sources*' | head -1)")
CP="$M/build/classes/java/main;$M/build/classes/java/tools;$M/libs/core-2.0-SNAPSHOT.jar;$SLF"
for item in $4; do
  size=${item%%:*}; pairs=${item##*:}
  "$PY" "$Q/night/tools/rtrun.py" --slot $SLOT --log "$L/$size.log" -- "$J/java" -Xmx3g -cp "$CP" ch.qc.tools.MatchMain \
    --a "$2" --b "$3" --pairs "$pairs" --threads $TH --sizes "$size" --opening 2 --seed ${SEED:-91} --affinity $MASK --out "$L/$size.jsonl"
  echo "=== $size $(date +%H:%M)"
  grep -E "pairs [0-9]+ \[|^  A: timeouts|^  B: timeouts|wall time|Exception" "$L/$size.log" | tail -n 5 | cut -c1-200
done
date
