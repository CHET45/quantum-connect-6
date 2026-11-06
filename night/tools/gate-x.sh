#!/usr/bin/env bash
# Real-protocol gate of jar A against jar B on several sizes, each size under the real-time lock.
# Usage: gate.sh LABEL JAR_A JAR_B "SIZE:PAIRS SIZE:PAIRS ..."
set -u
Q="C:/Users/Chtean45m/Documents/quantum-connect"
M="$Q/night/wt-main"
PY=/c/Users/Chtean45m/miniconda3/python.exe
L="$Q/night/logs/$1"
A="$2"; B="$3"; SPEC="$4"
mkdir -p "$L"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
cd "$M" || exit 1
J=$(ls -d ~/.gradle/jdks/*25*/bin | head -1)
SLF=$(cygpath -m "$(find ~/.gradle/caches/modules-2/files-2.1 -name 'slf4j-api*.jar' -not -name '*sources*' | head -1)")
mkdir -p "$L/classes"; cp -r build/classes/java/main build/classes/java/tools "$L/classes/" 2>/dev/null
CP="$L/classes/main;$L/classes/tools;libs/core-2.0-SNAPSHOT.jar;$SLF"
for item in $SPEC; do
  size=${item%%:*}; pairs=${item##*:}
  open="--opening 4"; [ -f "suites/openings-$size.txt" ] && open="--openings suites/openings-$size.txt"
  "$PY" "$Q/night/tools/rtrun.py" --slot ${SLOT:-2} --log "$L/gate-$size.log" -- "$J/java" -Xmx3g -cp "$CP" ch.qc.tools.MatchMain \
    --a "jar:$A" --b "jar:$B" --pairs "$pairs" --threads ${THREADS:-5} --sizes "$size" $open --seed ${SEED:-53} --affinity ${AFF:-FFF} --out "$L/gate-$size.jsonl"
  echo "=== $size $(date +%H:%M)"
  grep -E "pairs [0-9]+ \[|decided by|^  A: timeouts|^  B: timeouts|wall time" "$L/gate-$size.log" | tail -n 5 | cut -c1-200
  /c/Users/Chtean45m/miniconda3/python.exe -I -c "
import json,sys
g=[json.loads(l) for l in open(sys.argv[1]) if l.strip()]
print('  crashes:',sum(x['reason']=='crash' for x in g),'of',len(g))" "$L/gate-$size.jsonl"
done
date
