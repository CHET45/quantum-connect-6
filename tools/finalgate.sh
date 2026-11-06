#!/bin/bash
# Real-time gate final-base (rc8 + dynrules + g6f) vs rc8. Usage: finalgate.sh SLOT SIZE PAIRS OPENINGS SEED TAG [A_JAR]
Q=C:/Users/Chtean45m/Documents/quantum-connect
L=$Q/night/logs/final/rt; mkdir -p $L
J=C:/Users/Chtean45m/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2/bin/java
CP="$Q/night/logs/ponder/classes/main;$Q/night/logs/ponder/classes/tools;$Q/night/wt-pass/libs/core-2.0-SNAPSHOT.jar;C:/Users/Chtean45m/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.0.17/d9e58ac9c7779ba3bf8142aff6c830617a7fe60f/slf4j-api-2.0.17.jar"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
python $Q/night/tools/rtrun.py --slot $1 --log $L/$2$6.log -- $J -Xmx3g -cp "$CP" ch.qc.tools.MatchMain \
  --a jar:${7:-$Q/qc-bot/dist/night/exp/final-base.jar} --b jar:$Q/qc-bot/dist/night/qc-bot-4.0-rc8.jar \
  --pairs $3 --threads 2 --sizes $2 --openings $4 --seed $5 --affinity FFF --out $L/$2$6.jsonl
echo "=== $2 $(date +%H:%M)"; grep -E "pairs|  A:|  B:" $L/$2$6.log | cut -c1-200
