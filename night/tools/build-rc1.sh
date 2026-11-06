#!/usr/bin/env bash
# Integrates speed, time and the generic tables into night-main, builds a release candidate and gates it against 3.1.
# CPU only: nothing here touches the GPU.
set -u
Q="C:/Users/Chtean45m/Documents/quantum-connect"
M="$Q/night/wt-main"
L="$Q/night/logs/rc1"
BASE="$Q/qc-bot/dist/qc-bot-gen003-safe-80ms.jar"
RC="$Q/qc-bot/dist/night/qc-bot-4.0-rc1.jar"
mkdir -p "$L"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"
cd "$M" || exit 1

git add weights/eval-generic.qcw
git diff --cached --quiet || git commit -q -m "Generic tables v-wd

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
for b in night-speed night-time; do
  if ! git merge -q --no-edit "$b"; then
    echo "MERGE CONFLICT with $b:"
    git diff --name-only --diff-filter=U
    git diff | head -60
    exit 2
  fi
done
git log --oneline -1
grep -n "CONSTRUCTOR_WARMUP_NANOS =" src/main/java/ch/qc/bot/Timing.java

if ! ./gradlew.bat -q jar test --tests '*UploadRulesTest' --tests '*TimePlanTest' --tests '*GenericEvalTest' --tests '*TurnTrackerTest' --tests '*WindowSumTest' --tests '*BoardDifferentialTest' > "$L/build.log" 2>&1; then
  echo "BUILD OR TESTS FAILED"
  tail -n 30 "$L/build.log" | cut -c1-180
  exit 3
fi
cp build/libs/quantumconnect-starter-1.0-SNAPSHOT.jar "$RC"
ls -la "$RC" | awk '{print "jar bytes:", $5}'

gate() { # size pairs openings-option
  ./gradlew.bat -q match -Pargs="--a jar:$RC --b jar:$BASE --pairs $2 --threads 5 --sizes $1 $3 --seed 41 --out $L/gate-$1.jsonl" > "$L/gate-$1.log" 2>&1
  echo "=== $1"
  tail -n 14 "$L/gate-$1.log" | cut -c1-190
}
gate 15x11 50 "--openings suites/openings-15x11.txt"
gate 15x22 40 "--openings suites/openings-15x22.txt"
gate 8x12 50 "--openings suites/openings-8x12.txt"
gate 12x16 30 "--opening 4"
date
