#!/bin/bash
# Real-time gate rc9-g8s vs rc8 through the official engine, one size after another under the rtrun lock (slot 1).
Q=C:/Users/Chtean45m/Documents/quantum-connect
L=$Q/night/logs/rc9; mkdir -p $L
J=C:/Users/Chtean45m/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2/bin/java
CP="$Q/night/logs/ponder/classes/main;$Q/night/logs/ponder/classes/tools;$Q/night/wt-pass/libs/core-2.0-SNAPSHOT.jar;C:/Users/Chtean45m/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.0.17/d9e58ac9c7779ba3bf8142aff6c830617a7fe60f/slf4j-api-2.0.17.jar"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
run() { # size pairs openings
  python $Q/night/tools/rtrun.py --log $L/$1.log -- $J -Xmx3g -cp "$CP" ch.qc.tools.MatchMain \
    --a jar:$Q/qc-bot/dist/night/exp/rc9-g8s.jar --b jar:$Q/qc-bot/dist/night/qc-bot-4.0-rc8.jar \
    --pairs $2 --threads 2 --sizes $1 --openings $3 --seed 99 --out $L/$1.jsonl
  echo "=== $1 $(date +%H:%M)"; tail -3 $L/$1.log
}
run 15x30 100 $Q/night/wt-eval/suites/openings-15x30.txt
run 15x100 60 $Q/night/wt-main/suites/openings-15x100.txt
run 15x11 100 $Q/night/wt-pass/suites/openings-15x11.txt
run 8x12 100 $Q/night/wt-pass/suites/openings-8x12.txt
