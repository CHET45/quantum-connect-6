#!/bin/bash
# usage: simrun.sh LABEL JVMFLAGS -- ServerSim args ; runs under the real-time lock from the worktree
export MSYS2_ARG_CONV_EXCL="*"
R=C:/Users/Chtean45m/Documents/quantum-connect
J=$(cygpath -m "$(ls -d ~/.gradle/jdks/*25*/bin | head -1)")
LABEL=$1; shift
FLAGS=()
while [ "$1" != "--" ]; do FLAGS+=("$1"); shift; done
shift
CP="$R/qc-bot/data/night/time/bin/toolsl6;$R/qc-bot/data/night/time/bin/engine.jar;."
cd $R/night/wt-time
C:/Users/Chtean45m/miniconda3/python.exe $R/night/tools/rtrun.py --log $R/qc-bot/data/night/time/$LABEL.log -- "$J/java.exe" "${FLAGS[@]}" -cp "$CP" ch.qc.tools.ServerSim --label $LABEL --out "$R/qc-bot/data/night/time/$LABEL.csv" "$@"
