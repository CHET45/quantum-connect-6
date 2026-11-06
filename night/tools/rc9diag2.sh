#!/bin/bash
# Harness check: is side A always slower? A=rc8 vs B=rc8 copy, then A=rc8 vs B=rc9 (slot 2 lane).
Q=C:/Users/Chtean45m/Documents/quantum-connect
L=$Q/night/logs/rc9
J=C:/Users/Chtean45m/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2/bin/java
CP="$Q/night/logs/ponder/classes/main;$Q/night/logs/ponder/classes/tools;$Q/night/wt-pass/libs/core-2.0-SNAPSHOT.jar;C:/Users/Chtean45m/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.0.17/d9e58ac9c7779ba3bf8142aff6c830617a7fe60f/slf4j-api-2.0.17.jar"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
run() { # label a b
  python $Q/night/tools/rtrun.py --slot 2 --log $L/$1.log -- $J -Xmx3g -cp "$CP" ch.qc.tools.MatchMain --a jar:$2 --b jar:$3 \
    --pairs 30 --threads 2 --sizes 15x30 --openings $Q/night/wt-eval/suites/openings-15x30.txt --seed 77 --out $L/$1.jsonl
  echo "=== $1 $(date +%H:%M)"; grep -E "score|  A:|  B:" $L/$1.log | cut -c1-200
}
run d-same $Q/qc-bot/dist/night/qc-bot-4.0-rc8.jar $Q/qc-bot/dist/night/exp/rc8-copy.jar
run d-swap $Q/qc-bot/dist/night/qc-bot-4.0-rc8.jar $Q/qc-bot/dist/night/exp/rc9-g8s.jar
