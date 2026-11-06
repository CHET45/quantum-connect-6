#!/usr/bin/env bash
# Draw-bot lane: ServerSim (30 s game clock, real protocol) of JAR_A against JAR_B on two pinned logical CPUs.
# Usage: draw-sim.sh LABEL MASK JAR_A JAR_B ROWS COLS GAMES
# Classes from night/logs/draw/classes (frozen copy of wt-draw's tools). No lock: several lanes run side by side.
set -u
export MSYS2_ARG_CONV_EXCL="*"
R=C:/Users/Chtean45m/Documents/quantum-connect
L=$R/night/logs/draw
J=$(cygpath -m "$(ls -d ~/.gradle/jdks/*25*/bin | head -1)")
SLF=C:/Users/Chtean45m/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.0.17/d9e58ac9c7779ba3bf8142aff6c830617a7fe60f/slf4j-api-2.0.17.jar
CP="$L/classes/tools;$L/classes/main;$R/night/wt-draw/libs/core-2.0-SNAPSHOT.jar;$SLF"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
"$J/java.exe" -Xmx512m -XX:-UseThreadPriorities -XX:ActiveProcessorCount=2 -cp "$CP" ch.qc.tools.ServerSim \
  --a "jar:$3" --b "jar:$4" --rows "$5" --cols "$6" --games "$7" --affinity "$2" --clock 30 \
  --out "$L/$1.csv" --label "$1" >> "$L/$1.log" 2>&1
echo "done $1 $(date +%H:%M)" >> "$L/$1.log"
