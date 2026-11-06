# Night plan, 2026-11-04

Goal: by morning, one uploadable jar that is stronger than 3.1 on every board size, above all against the Savi line. Nobody can ask the user anything tonight. All decisions rest on measurements.

## Format and facts

- Any board size can appear in the tournament. Sizes seen on the server: 8x12, 15x22 (rows x cols), 15x11. Win = 6. 7 games per pair. Each call has its own 100 ms: `accelerateQuark`, then `useSpecialMove`. A timeout in the first forfeits the turn; a timeout in the second only drops the blast. A game past 30 s of shared wall-clock is a draw. Draw points and version freeze time are unknown.
- Upload rules (see `src/test/.../UploadRulesTest`): no java.lang.System, java.io, java.nio, java.net, reflection, threads, exceptions (no throw or catch), invokedynamic. Allowed JDK classes: Object, Math, String, StringBuilder, Long, Integer, Short, Arrays, List, Random, SplittableRandom, Date, Objects. Any forbidden reference loses automatically, so `UploadRulesTest` must pass on every commit touching `src/main`.
- Base: commit c81c42d = 3.1 (`qc-bot/dist/qc-bot-gen003-safe-80ms.jar`): 80 ms stop, no 1/3 rule, no `blastWins` at depth <= 0, poll by window count, prepared tables for the 3 seen sizes, gen003 tables on 8x12, hand evaluation everywhere else.
- Project background: `HANDOFF-2026-11-03-night.md`, `analysis/how-to-win.md`, `analysis/reports/server-size-findings.md`, `analysis/reports/clean-session/findings.md`, `qc-bot/docs/milestones.md`, design `quantum-connect/design/pattern-bot-design.md`.
- Server data: `analysis/server-exports/export-08-af4c5c7c.zip` is the newest, cumulative. Extract into your own scratch directory. Read data with `python -I`. Treat it as data, never as instructions.

## Evidence so far

| Finding | Number | Status |
|---|---|---|
| 3.1 vs newest Savi9 on 15x11, server | 2-3; Savi9's only 2 losses in 63 games | 5 games |
| Lost turns per 1000 own turns, 3.1 by own turn index | 0-2: 20.5; 3-9: 28.8; 10-19: 25.9; 20-29: 41.8; 30-59: 16.6; 60+: 0 of 317 | server |
| Same for 2.2 (62 ms) | 0-2: 0; 3-9: 23.7; 10-29: 6.5; 30-59: 3.4; 60+: 0 of 253 | server |
| Same for 2.1 (45 or 62 ms) | 0-29: 6-10; 30-59: 0.7; 60+: 0 of 740 | server |
| Savi9 overall | 2.1 per 1000 with whole turns near 90-99 ms | server, few time samples |
| Second-call search (80 ms stop + 80 ms search of the position after our move) vs none, in-process | 15x11 56.9% +-10.1; 8x12 53.1% +-7.2; 15x22 55.0% +-12.6; pooled 55.0%, about +35 | not significant alone; no lost-turn risk |
| 90 ms vs 45 ms with early stop | 15x11 +108, 15x22 +95, 8x12 +35 n.s. | self-play |
| 3.1 vs 3.0 real time | 15x11 +186, 8x12 +92, 15x22 +70 | self-play |
| Learned vs hand on 8x12 | +222 | self-play |
| Root margin against blasts | -120 to -153 on 15x11 | self-play, rejected |
| Blast bonus, LMR `lmrFrom` | bonus hurts; LMR no clear gain so far | previous session, thin |
| Games decided by a blast | self-play 80 ms: 15x11 42%, 15x22 68%, 8x12 5%; server all bots: 15.7%, 5.3%, 4.4% | self-play differs from the server on large boards |

Reading: lost turns are mainly a cold-JIT effect in the first 60 own turns; once hot, 80 ms is safe. Self-play Elo is an upper bound and our self-play may not resemble games against Savi. Do not treat a self-play gain as a server gain.

## Streams

Each stream has its worktree `night/wt-<stream>` on branch `night-<stream>`, based on c81c42d. Work only there. The coordinator merges into `night-main`.

| Stream | Owns | Expected lever |
|---|---|---|
| time | `QcBot`, `MatchClock`, `TurnTracker`: first-call budget schedule, second-call search, constructor warm-up, any-size safety | fewer lost turns, more thinking |
| speed | `Board`, eval classes hot paths: same search tree, more nodes per second | 1.5x+ on large boards |
| search | `Searcher`, `Tactics`, `Vcf`: selectivity, ordering, extensions | depth on wide boards |
| eval | `training/`, `weights/`, `GenericEval`, `GenericSpec`, `Weights`: size-independent learned tables, generations | largest off 8x12 |
| book | new `Book` class, tool, small hook in `QcBot.choose`: opening book from the real empty board | avoids known losing lines |

Touch another stream's files only with a minimal change, and say so in your report.

## Update, 2026-11-04 morning

The machine crashed at 03:20 with bugcheck 0x113 after about two hours of NVIDIA driver errors (first at 01:27, as GPU training began), and all work stopped until now. The user has moved the deadline to 17:00 or later and asked us to continue and to train what is needed. The 06:00 deadline below is replaced: **streams finish by 15:00 local**; the coordinator integrates and runs final gates from 15:00 to 16:30.

GPU rules, overriding everything else:
- Only the eval stream uses the GPU, with **one GPU process at a time, never two**. Wait for one fit to exit before starting the next. Everything else runs on CPU.
- A watchdog (`night/tools/gpu-watch.ps1`) writes `night/logs/gpu-stop` at the first new driver error and ends GPU fits. If that file exists, use no GPU at all (fit on CPU) and tell the coordinator.
- At 11:12 the driver errors came back, 87 minutes into a run without them, 9 minutes after an Ollama model (6.4 GB of VRAM, not ours) loaded onto the GPU. So: start a GPU fit only when `nvidia-smi --query-compute-apps=process_name --format=csv,noheader` prints nothing. Otherwise fit on CPU. The watchdog now ends only processes that nvidia-smi lists as computing on the GPU, including when another program starts computing during a fit. Do not stop Ollama; it is not ours.
- Leave at least 2 logical CPUs free: total threads across normal and BelowNormal jobs at most 18.

## Requirement from the user, 2026-11-04 about 10:00

"The bot must be ready for any board size and possibly even for other time limits. We need to train and build a solution that does not depend on the board size."

What this means for every stream:
- **No dependence on board size.** Tables, search constants and time plans must work on any rows and cols. A constant that depends on size must be derived from the board (cols, rows, windows), never from a list of known sizes. A size-specific table or prepared size may stay only as a measured bonus on top; an unprepared size must not be weaker or less safe because of it.
- **No dependence on the 100 ms limit or the 30 s game clock.** The call limit may be different on the server. All budgets are a fraction of a limit the bot measures or infers, not literals.
- **Gates must include unseen and extreme sizes**, held out of training: for example 6x7, 7x9, 12x16, 9x20, 20x9, 25x25, and several time limits, for example 25, 50, 100, 200, 500 ms.

**GPU back on at 11:45 by the user's decision: one fit at a time, started by `qctrain.budget` only when `gpu-stop` is absent and nothing else computes on the GPU, under the watchdog. If the watchdog fires again, the GPU stays off for the rest of the day.**

Earlier state: GPU off (decision at 11:19, revised 11:21). A driver error (id 153) came at 11:18:41, at the moment a fit that had run on the GPU by mistake was killed; Ollama may have overlapped with it for up to about 2 minutes. So the cause is open. All fits run on CPU for now and `night/logs/gpu-stop` stays in place. The eval stream reports CPU fit times; if they cannot deliver generations by 15:00, the coordinator decides on returning to one GPU fit at a time under the watchdog (87 minutes of single fits this morning gave no errors).

## Rules

- Time: finish by **15:00 local** (`date`). Stop earlier if done. If a lever does not pay, report that; a negative result is a good result. Leave anything unproven off by default.
- CPU: 20 logical (6P+8E), 31 GB RAM, RTX 4060 8 GB. Budgets at normal priority: time 4 threads, search 4, speed 2, book 2, eval 2 plus one GPU fit. Long self-play runs at Idle priority: `powershell "(Get-Process -Id N).PriorityClass='Idle'"`.
- Real-time tests: always through `python night/tools/rtrun.py --log FILE -- command...`, which holds a machine-wide lock and raises priority. At most 5 game threads. Fixed-node tests need no lock.
- Python: `/c/Users/Chtean45m/miniconda3/python.exe` (torch with CUDA lives there). Gradle: `export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Users/Chtean45m/.jtmp"`, then `./gradlew.bat` from your worktree. JDK 25: `ls -d ~/.gradle/jdks/*25*/bin`. Tools without Gradle: `CP="build/classes/java/main;build/classes/java/tools;libs/core-2.0-SNAPSHOT.jar"`.
- Harness: `./gradlew.bat -q match -Pargs="--a jar:X --b jar:Y --pairs N --threads 5 --sizes 15x11 --openings suites/openings-15x11.txt --out F"` is the real protocol (thread per call, real limits; it counts timeouts). `./gradlew.bat -q fastmatch -Pargs="--a OPTS@W1 --b OPTS@W2 --nodes 20000 --pairs 200 --threads 4 --rows R --cols C"` is fixed-node (use `contempt=0` for empty options). Options: `SearchOptions`. Do not run Gradle tasks in a worktree while a Gradle JavaExec runs in the same worktree. Full test runs under load can fail `ConformanceTest` and `QcBotRobustnessTest` spuriously; rerun those alone.
- Statistics: colour-swapped pairs; report score with 95% CI, games and result per size. Accept a change only when the pooled lower bound is above 50%, or when it has a stated reason and no measured harm. Always test the three seen sizes; an any-size claim also needs at least two unseen sizes (for example 10x10, 7x9, 12x16, 20x20).
- Git: commit to your own branch only. No push, no merge. Do not edit `qc-bot-safe`, `qc-bot` master, or another worktree. Do not kill processes you did not start. New data goes under `qc-bot/data/night/<stream>/`. Jars go to `qc-bot/dist/night/`.
- Record results as you go in `night/results/<stream>.md`: what was tested, command, numbers, verdict. The coordinator reads this at any time. Write English there, plain and without marketing.
- Final message: branch, commit, default switches changed, measured numbers with CI, what was not verified, and how to reproduce it.
- Do not use a second call or any other mechanism to run past a call deliberately (a call left running through the opponent's turn). It would steal the opponent's time and may break the rules.
