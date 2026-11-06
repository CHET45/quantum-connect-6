#!/usr/bin/env bash
# Second-call loophole: rc8 vs rc8, then vampire(rc8) vs rc8, whole match on two P-cores (CPU 0 and 2).
set -u
Q="C:/Users/Chtean45m/Documents/quantum-connect"
W="$Q/night/wt-pass"
L="$Q/night/logs/vamp1"; mkdir -p "$L/classes"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
J=$(ls -d ~/.gradle/jdks/*25*/bin | head -1)
SLF=$(cygpath -m "$(find ~/.gradle/caches/modules-2/files-2.1 -name 'slf4j-api*.jar' -not -name '*sources*' | head -1)")
cp -r "$W/build/classes/java/main" "$W/build/classes/java/tools" "$L/classes/"
CP="$L/classes/main;$L/classes/tools;$W/libs/core-2.0-SNAPSHOT.jar;$SLF"
R="$Q/qc-bot/dist/night/qc-bot-4.0-rc8.jar"
for A in "jar:$R" "vampire:jar:$R"; do
  tag=$([ "${A%%:*}" = vampire ] && echo vamp || echo base)
  /c/Users/Chtean45m/miniconda3/python.exe "$Q/night/tools/rtrun.py" --slot 2 --log "$L/$tag.log" -- "$J/java" -Xmx2g -cp "$CP" \
    ch.qc.tools.MatchMain --a "$A" --b "jar:$R" --pairs 10 --threads 1 --sizes 15x30 --opening 4 --seed 31 --affinity 1 --out "$L/$tag.jsonl"
  echo "=== $tag $(date +%H:%M)"; grep -E "games,|timeouts|vampire" "$L/$tag.log" | cut -c1-200
done
