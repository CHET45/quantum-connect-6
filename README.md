# QcBot

A Quantum Connect bot built from the design in [`docs/pattern-bot-design.md`](docs/pattern-bot-design.md)
(milestones M0-M7). The uploadable class is `ch.qc.bot.QcBot`; everything below `src/tools` and `src/test` is
harness code and is not packaged into the jar.

| Path | What it is |
| ---- | ---------- |
| `src/main/java/ch/qc/bot/QcBot.java` | The bot: per-call snapshot, turn tracking, guarded two-call protocol |
| `src/main/java/ch/qc/bot/TurnTracker.java` | Own turn index and both special-move cooldown counters |
| `src/main/java/ch/qc/bot/rules/` | `Geometry` (windows), `Tables` (window codes), `Board` (moves, blasts, threat cells, hash) |
| `src/test/java/` | Conformance against the official engine, differential test against a naive rule copy, tracker and robustness tests |
| `src/tools/java/ch/qc/tools/` | `MatchMain` (colour-swapped match runner), `PackagingProbe`, `BotProbe` (latency and legality checks) |

Commands (the Gradle wrapper downloads JDK 25 if needed):

```
./gradlew test                                   # all tests at their default size
./gradlew test -Pqc.diff.positions=10000000 -Pqc.robust.calls=100000 -Pqc.conf.games=5000
./gradlew match -Pargs="--b ch.qc.starter.SpecialMoveBot --pairs 200"
./gradlew build probe                            # jar, then load it through an isolated class loader
./gradlew run                                    # emulator window: QcBot vs SpecialMoveBot
./gradlew match -Pargs="--b jar:ladder/m3.jar --openings suites/openings-8x12.txt --sprt 0,10 --pairs 2000"
./gradlew fastmatch -Pargs="--a +razor --nodes 20000 --sprt 0,10"   # fixed-node, reproducible, all cores
./gradlew report -Pargs="--sprt 0,10 results/m3-vs-m2.jsonl"        # verdict of a stored log
./gradlew latency -Pargs="--calls 20000" -Pjvm="-XX:+UseSerialGC"   # latency through the engine timer
./gradlew regression                             # tests + non-regression SPRT against the newest ladder jar
```

Training the evaluation (design section 4; Python needs torch and numpy, see `training/requirements.txt`):

```
./gradlew featurespec                                                       # spec JSON for Python
./gradlew selfplay -Pargs="--out data/gen000 --positions 4000000 --nodes 20000"
cd training && python -m qctrain.fit --data ../data/gen000 --out ../data/models/gen1 --lam 0.9 --k 256 --epochs 40 --lr 1e-3 --lr-end 1e-5 --wd-base 1e-2 --wd-delta 1e-1
python -m qctrain.parity --model ../data/models/gen1 --data ../data/gen000  # then: ./gradlew test -Pqc.parity=data/models/gen1
./gradlew fastmatch -Pargs="--a @data/models/gen1/eval.qcw --b @hand --sprt 0,10"
cp data/models/gen1/eval.qcw weights/eval.qcw && ./gradlew build probe     # embeds the weights into the jar
```

Bot specs for `--a/--b`: a class name, `qcbot:+opt,-opt` (current QcBot with search switches, see
`Searcher.Options`), or `jar:PATH` (a frozen build; `ladder/mN.jar` are built from the git tags). Results and the
history of each milestone are in [`docs/milestones.md`](docs/milestones.md).

---

# Quantum Connect starter project

This is a ready-to-use project to write, test and package your own Quantum Connect bot. It already
contains the game engine (`libs/core-*.jar`), so you do not need anything else.

## Pre-requisites

- Java 25 (the Gradle toolchain downloads it for you if it is missing and your Gradle is allowed to)
- Nothing else: use the included `./gradlew` (`gradlew.bat` on Windows)

## What is in the project

| Path | What it is |
| ---- | ---------- |
| `src/main/java/ch/qc/starter/RandomBot.java` | A bot that plays a random free column. Start from here. |
| `src/main/java/ch/qc/starter/SpecialMoveBot.java` | A bot that also uses the special move now and then. |
| `src/main/java/EmulatorRunner.java` | Plays two bots against each other on your machine. |
| `libs/core-*.jar` | The game engine (`Algorithm`, `QuantumField`, `Experiment`, ...). |
| `build.gradle` | Build file. This is where you tell the game which class is your bot. |

## Writing your bot

A bot is a class that implements `ch.cern.quantumconnect.core.Algorithm`:

```java
public class MyBot implements Algorithm {

  // Required: return the column (0-based) in which to drop your quark
  @Override
  public int accelerateQuark(QuantumField quantumField) {
    return 0;
  }

  // Optional: return true to use your special move (destroys surrounding discs).
  // The game only allows it once every few moves.
  @Override
  public boolean useSpecialMove(QuantumField quantumField) {
    return false;
  }
}
```

Useful methods of `QuantumField`: `getNumberOfColumns()` and `isColumnFull(int column)`; browse the
rest of the class in your IDE (the jar contains the sources' signatures).

Rules to keep in mind:

- Your bot needs a public constructor without arguments.
- Always return a column that is not full, otherwise you lose the game.
- You only have a limited time per move; a bot that is too slow loses the move.
- Only use the Java standard library and the classes that are in `libs/`.

## Testing your bot locally

1. Create your class, for example `src/main/java/ch/qc/starter/MyBot.java`.
2. Open `src/main/java/EmulatorRunner.java` and put your bot in the experiment:

   ```java
   var exp = new Experiment(new MyBot(), new RandomBot());
   exp.runExperiment();
   ```

   Any `Algorithm` can be your opponent: the `RandomBot` and `SpecialMoveBot` of this project, a
   second version of your own bot, or the bots of `ch.cern.quantumconnect.bots` in the core jar.
3. Run it:

   ```
   ./gradlew run
   ```

   A window opens and shows the match. Run it several times: results vary because of the random
   bots. A good bot should beat `RandomBot` almost every time.

## Packaging and uploading

1. In `build.gradle`, set the fully qualified class name of your bot:

   ```
   'Bot-Class': 'ch.qc.starter.MyBot',
   ```

2. Build the jar:

   ```
   ./gradlew build
   ```

3. Upload `build/libs/quantumconnect-starter-1.0-SNAPSHOT.jar` on the "Bots" page of the game.

If the upload is rejected, check that `Bot-Class` points to a class that exists, implements
`Algorithm`, and has a public no-argument constructor.

## Updating this project

New versions of the game engine are published with a new copy of this starter project. To update an
existing bot, download the starter project again and copy your own sources (`src/main/java/...`) and
your `Bot-Class` line into it, instead of replacing `libs/` by hand.
