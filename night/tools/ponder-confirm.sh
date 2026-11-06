#!/usr/bin/env bash
# Confirm the shipped ps=160 jar (default, no @spec) vs rc8 on 15x100, bigger sample, P-cores, 2 threads.
set -u
Q="C:/Users/Chtean45m/Documents/quantum-connect"; W="$Q/night/wt-pass"; M="$Q/night/wt-main"; L="$Q/night/logs/ponder"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
J=$(ls -d ~/.gradle/jdks/*25*/bin | head -1)
SLF=$(cygpath -m "$(find ~/.gradle/caches/modules-2/files-2.1 -name 'slf4j-api*.jar' -not -name '*sources*' | head -1)")
CP="$L/classes/main;$L/classes/tools;$W/libs/core-2.0-SNAPSHOT.jar;$SLF"
A="$Q/qc-bot/dist/night/exp/rc8-ponder.jar"; B="$Q/qc-bot/dist/night/qc-bot-4.0-rc8.jar"
/c/Users/Chtean45m/miniconda3/python.exe "$Q/night/tools/rtrun.py" --slot 2 --log "$L/15x100-confirm.log" -- "$J/java" -Xmx3g -cp "$CP" \
  ch.qc.tools.MatchMain --a "jar:$A" --b "jar:$B" --pairs 80 --threads 2 --sizes 15x100 --openings "$M/suites/openings-15x100.txt" --seed 88 --out "$L/15x100-confirm.jsonl"
echo "=== 15x100 confirm $(date +%H:%M)"
grep -E "pairs [0-9]+ \[|A: timeouts|B: timeouts|crashes" "$L/15x100-confirm.log" | tail -4 | cut -c1-180
date
