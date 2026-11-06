#!/usr/bin/env bash
# Does the 15x30 regression come from the span length? ps=160 and ps=0 (= old ponder) vs rc8, P-cores, 2 threads.
set -u
Q="C:/Users/Chtean45m/Documents/quantum-connect"
W="$Q/night/wt-pass"
L="$Q/night/logs/ponder"; mkdir -p "$L/classes"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
J=$(ls -d ~/.gradle/jdks/*25*/bin | head -1)
SLF=$(cygpath -m "$(find ~/.gradle/caches/modules-2/files-2.1 -name 'slf4j-api*.jar' -not -name '*sources*' | head -1)")
CP="$L/classes/main;$L/classes/tools;$W/libs/core-2.0-SNAPSHOT.jar;$SLF"
A="$Q/qc-bot/dist/night/exp/rc8-ponder.jar"; B="$Q/qc-bot/dist/night/qc-bot-4.0-rc8.jar"
OPEN="$W/../wt-eval/suites/openings-15x30.txt"
for spec in ps=160 ps=0; do
  tag=${spec/=/_}
  /c/Users/Chtean45m/miniconda3/python.exe "$Q/night/tools/rtrun.py" --slot 2 --log "$L/15x30-$tag.log" -- "$J/java" -Xmx2g -cp "$CP" \
    ch.qc.tools.MatchMain --a "jar:$A@$spec" --b "jar:$B" --pairs 60 --threads 2 --sizes 15x30 --openings "$OPEN" --seed 71 --out "$L/15x30-$tag.jsonl"
  echo "=== $tag $(date +%H:%M)"
  grep -E "pairs [0-9]+ \[|A: timeouts|B: timeouts|crashes" "$L/15x30-$tag.log" | tail -4 | cut -c1-200
done
date
