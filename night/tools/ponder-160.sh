#!/usr/bin/env bash
# ps=160 vs rc8 on the current wide server board (15x100) and on the small board (8x12), P-cores, 2 threads.
set -u
Q="C:/Users/Chtean45m/Documents/quantum-connect"
W="$Q/night/wt-pass"; M="$Q/night/wt-main"
L="$Q/night/logs/ponder"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
J=$(ls -d ~/.gradle/jdks/*25*/bin | head -1)
SLF=$(cygpath -m "$(find ~/.gradle/caches/modules-2/files-2.1 -name 'slf4j-api*.jar' -not -name '*sources*' | head -1)")
CP="$L/classes/main;$L/classes/tools;$W/libs/core-2.0-SNAPSHOT.jar;$SLF"
A="$Q/qc-bot/dist/night/exp/rc8-ponder.jar"; B="$Q/qc-bot/dist/night/qc-bot-4.0-rc8.jar"
for item in "15x100:40:$M/suites/openings-15x100.txt" "8x12:50:$M/suites/openings-8x12.txt"; do
  size=${item%%:*}; rest=${item#*:}; pairs=${rest%%:*}; open=${rest#*:}
  /c/Users/Chtean45m/miniconda3/python.exe "$Q/night/tools/rtrun.py" --slot 2 --log "$L/$size-ps_160.log" -- "$J/java" -Xmx3g -cp "$CP" \
    ch.qc.tools.MatchMain --a "jar:$A@ps=160" --b "jar:$B" --pairs "$pairs" --threads 2 --sizes "$size" --openings "$open" --seed 73 --out "$L/$size-ps_160.jsonl"
  echo "=== $size ps=160 $(date +%H:%M)"
  grep -E "pairs [0-9]+ \[|A: timeouts|B: timeouts|crashes" "$L/$size-ps_160.log" | tail -4 | cut -c1-180
done
date
