# The Dark Prince

quantum connect bot (java). 6 in a row, gravity, 3x3 blast every 10 own turns.

- search: pvs + tt, iterative deepening, root vcf (gravity threats), 45 ms hard stop
- time: match clock (30 s per game shared, 3 s reserve), no System calls
- eval: learned pattern tables (`weights/eval.qcw`, 8x12), hand tables on other sizes and as fallback
- weights/eval.qcw - first fit from ~1M self-play positions, lam 0.5 (see weights/fit-report.json)
- upload-safe now: no System, io/nio, reflection, threads, exceptions (UploadRulesTest checks it)

build:

```
# put core-2.0-SNAPSHOT.jar from the starter kit into libs/ first
./gradlew test
./gradlew jar      # build/libs/the-dark-prince-1.0-SNAPSHOT.jar, Bot-Class ch.qc.bot.DarkPrince
./gradlew run      # emulator window vs SpecialMoveBot
```

tables (self-play -> fit -> weights/eval.qcw):

```
./gradlew openings -Pargs="--count 2000 --ms 250"           # suites/openings-8x12.txt
./gradlew selfplay -Pargs="--out data/gen000 --positions 1500000 --nodes 12000 --threads 16"
./gradlew featurespec                                         # training/spec/feature-spec-8x12.json
cd training && pip install -r requirements.txt
python -m qctrain.fit --data ../data/gen000 --out ../data/models/gen001
cp ../data/models/gen001/eval.qcw ../weights/ && cd .. && ./gradlew jar
./gradlew fastmatch -Pargs="--a @data/models/gen001/eval.qcw --b @hand --nodes 20000 --sprt 0,10"
```

notes for me:
- 1.5M positions at 12k nodes is a few hours on 16 threads, fit wants cuda
- next: more self-play generations for the tables (needs GPU)
