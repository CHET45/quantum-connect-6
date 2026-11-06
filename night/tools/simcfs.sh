#!/usr/bin/env bash
# ServerSim under an imitated container CPU limit (cfsthrottle.py) with busy threads, classes of night/wt-blast.
# Usage: simcfs.sh LABEL SPEC_A SPEC_B ROWS COLS GAMES QUOTA_MS LOAD   (SPEC = jar:PATH[@TIMING])
# Java on logical CPUs 2 and 4 (mask 14), the throttler spins on CPU 6.
set -u
export MSYS2_ARG_CONV_EXCL="*"
R=C:/Users/Chtean45m/Documents/quantum-connect
W=$R/night/wt-blast
L=$R/night/logs/sim
mkdir -p "$L"
J=$(cygpath -m "$(ls -d ~/.gradle/jdks/*25*/bin | head -1)")
PY=C:/Users/Chtean45m/miniconda3/python.exe
SLF=C:/Users/Chtean45m/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.0.17/d9e58ac9c7779ba3bf8142aff6c830617a7fe60f/slf4j-api-2.0.17.jar
# a snapshot of the harness classes, so a rebuild of wt-blast cannot change them under a running sim
K=$L/classes-$1; mkdir -p "$K"; cp -r "$W/build/classes/java/main" "$W/build/classes/java/tools" "$K/" 2>/dev/null
CP="$K/tools;$K/main;$R/night/wt-main/libs/core-2.0-SNAPSHOT.jar;$SLF"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
$PY "$R/night/tools/rtrun.py" --log "$L/$1.log" -- "$J/java.exe" -Xmx512m \
  -XX:-UseThreadPriorities -XX:ActiveProcessorCount=2 -cp "$CP" ch.qc.tools.ServerSim \
  --a "$2" --b "$3" --rows "$4" --cols "$5" --games "$6" --affinity 14 --limit 100 \
  --cfs "$PY,$W/src/tools/python/cfsthrottle.py,$7,6" --load "$8" --out "$L/$1.csv" --label "$1"
tail -n 3 "$L/$1.log" | cut -c1-200
$PY -I "$W/src/tools/python/simreport.py" "$L/$1.csv" | cut -c1-220
