#!/bin/bash
# In-process QcBot (search stats) with g8s or g6f tables vs the rc8 jar, 15x30 (slot 2 lane).
Q=C:/Users/Chtean45m/Documents/quantum-connect
L=$Q/night/logs/rc9
J=C:/Users/Chtean45m/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2/bin/java
SLF=C:/Users/Chtean45m/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.0.17/d9e58ac9c7779ba3bf8142aff6c830617a7fe60f/slf4j-api-2.0.17.jar
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
for t in g8s g6f; do
  CP="$L/cls-$t/main;$L/cls-$t/tools;$Q/night/wt-pass/libs/core-2.0-SNAPSHOT.jar;$SLF"
  python $Q/night/tools/rtrun.py --slot 2 --log $L/d-in-$t.log -- $J -Xmx3g -cp "$CP" ch.qc.tools.MatchMain --a ch.qc.bot.QcBot \
    --b jar:$Q/qc-bot/dist/night/qc-bot-4.0-rc8.jar --pairs 25 --threads 2 --sizes 15x30 \
    --openings $Q/night/wt-eval/suites/openings-15x30.txt --seed 55 --out $L/d-in-$t.jsonl
  echo "=== $t $(date +%H:%M)"; grep -vE "SLF4J|JAVA_TOOL" $L/d-in-$t.log | cut -c1-230
done
