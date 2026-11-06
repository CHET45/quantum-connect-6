#!/usr/bin/env bash
# Real-protocol match of two bots on one size, under the real-time lock, pinned to the P-cores.
# Usage: huge.sh LABEL SPEC_A SPEC_B SIZE PAIRS   (a spec is jar:PATH or jar:PATH@TIMING)
set -u
Q="C:/Users/Chtean45m/Documents/quantum-connect"
M="$Q/night/wt-huge"
PY=/c/Users/Chtean45m/miniconda3/python.exe
L="$Q/night/logs/huge"
mkdir -p "$L"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
cd "$M" || exit 1
J=$(ls -d ~/.gradle/jdks/*25*/bin | head -1)
SLF=C:/Users/Chtean45m/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.0.17/d9e58ac9c7779ba3bf8142aff6c830617a7fe60f/slf4j-api-2.0.17.jar
CP="$M/build/classes/java/main;$M/build/classes/java/tools;$M/libs/core-2.0-SNAPSHOT.jar;$SLF"
"$PY" "$Q/night/tools/rtrun.py" --log "$L/$1.log" -- "$J/java" -Xmx6g -cp "$CP" ch.qc.tools.MatchMain \
  --a "$2" --b "$3" --pairs "$5" --threads 4 --sizes "$4" --opening 2 --seed ${SEED:-71} --affinity FFF --out "$L/$1.jsonl"
grep -E "games, A|pairs [0-9]+ \[|decided by|^  A: timeouts|^  B: timeouts|wall time" "$L/$1.log" | tail -n 6 | cut -c1-210
"$PY" -I -c "
import json,sys
g=[json.loads(l) for l in open(sys.argv[1]) if l.strip()]
r={}
for x in g: r[x['reason']]=r.get(x['reason'],0)+1
print('  reasons',r,'median plies',sorted(x['plies'] for x in g)[len(g)//2] if g else 0)" "$L/$1.jsonl"
