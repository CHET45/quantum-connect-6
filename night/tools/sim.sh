#!/usr/bin/env bash
# Server imitation (ServerSim) of one jar against another, under the real-time lock, on two pinned logical CPUs.
# Usage: sim.sh LABEL JAR_A JAR_B ROWS COLS GAMES [LIMIT_MS]
set -u
export MSYS2_ARG_CONV_EXCL="*"
R=C:/Users/Chtean45m/Documents/quantum-connect
M=$R/night/wt-main
L=$R/night/logs/sim
mkdir -p "$L"
J=$(cygpath -m "$(ls -d ~/.gradle/jdks/*25*/bin | head -1)")
SLF=C:/Users/Chtean45m/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.0.17/d9e58ac9c7779ba3bf8142aff6c830617a7fe60f/slf4j-api-2.0.17.jar
CP="$M/build/classes/java/tools;$M/build/classes/java/main;$M/libs/core-2.0-SNAPSHOT.jar;$SLF"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
C:/Users/Chtean45m/miniconda3/python.exe "$R/night/tools/rtrun.py" --log "$L/$1.log" -- "$J/java.exe" -Xmx512m \
  -XX:-UseThreadPriorities -XX:ActiveProcessorCount=2 -cp "$CP" ch.qc.tools.ServerSim \
  --a "jar:$2" --b "jar:$3" --rows "$4" --cols "$5" --games "$6" --affinity 14 --limit "${7:-100}" --out "$L/$1.csv" --label "$1"
tail -n 4 "$L/$1.log" | cut -c1-200
C:/Users/Chtean45m/miniconda3/python.exe -I "$M/src/tools/python/simreport.py" "$L/$1.csv" | cut -c1-200
