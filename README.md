# The Dark Prince

quantum connect bot (java). 6 in a row, gravity, 3x3 blast every 10 own turns.

- search: pvs + tt, iterative deepening, root vcf (gravity threats), 62 ms hard stop
- eval: learned pattern tables (`weights/eval.qcw`), hand tables as fallback
- weights/eval.qcw - first fit from ~1M self-play positions, lam 0.5 (see weights/fit-report.json)

build:

```
# put core-2.0-SNAPSHOT.jar from the starter kit into libs/ first
./gradlew test
./gradlew jar      # build/libs/the-dark-prince-1.0-SNAPSHOT.jar, Bot-Class ch.qc.bot.DarkPrince
./gradlew run      # emulator window vs SpecialMoveBot
```

notes for me:
- NOT upload-safe yet: server bans System, exceptions, reflection, threads. port Clock + abort flag first
- `-Dqc.weights=FILE` loads other tables without rebuilding
- next: more self-play generations for the tables (needs GPU)
