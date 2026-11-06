#!/usr/bin/env bash
# Real-protocol gate of the pondering jar against rc8, P-cores under the rt lock, 2 threads (timing feature).
set -u
Q="C:/Users/Chtean45m/Documents/quantum-connect"
W="$Q/night/wt-pass"; M="$Q/night/wt-main"
L="$Q/night/logs/ponder"; mkdir -p "$L/classes"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
J=$(ls -d ~/.gradle/jdks/*25*/bin | head -1)
SLF=$(cygpath -m "$(find ~/.gradle/caches/modules-2/files-2.1 -name 'slf4j-api*.jar' -not -name '*sources*' | head -1)")
cp -r "$W/build/classes/java/main" "$W/build/classes/java/tools" "$L/classes/" 2>/dev/null
CP="$L/classes/main;$L/classes/tools;$W/libs/core-2.0-SNAPSHOT.jar;$SLF"
A="$Q/qc-bot/dist/night/exp/rc8-ponder.jar"; B="$Q/qc-bot/dist/night/qc-bot-4.0-rc8.jar"
for item in "15x30:80:$W/../wt-eval/suites/openings-15x30.txt" "8x12:50:$M/suites/openings-8x12.txt"; do
  size=${item%%:*}; rest=${item#*:}; pairs=${rest%%:*}; open=${rest#*:}
  /c/Users/Chtean45m/miniconda3/python.exe "$Q/night/tools/rtrun.py" --slot 2 --log "$L/$size.log" -- "$J/java" -Xmx2g -cp "$CP" \
    ch.qc.tools.MatchMain --a "jar:$A" --b "jar:$B" --pairs "$pairs" --threads 2 --sizes "$size" --openings "$open" --seed 61 --out "$L/$size.jsonl"
  echo "=== $size $(date +%H:%M)"
  grep -E "games,|pairs [0-9]+ \[|A: timeouts|B: timeouts|crash|wall" "$L/$size.log" | tail -6 | cut -c1-200
done
date
