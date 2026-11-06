# Quantum Connect bot (team "No beaches?")

Everything we built for the Quantum Connect bot tournament (November 2026): the bot, its search and learned
evaluation, the training pipeline, the test and match harness, the server-log analysis and the research behind it.
Full git history, dead ends included.

Quantum Connect is a gravity game on a board of any size: drop quarks into columns, six in a row wins, and every
10 own turns a player may fire a 3x3 blast that destroys discs and compacts the columns. If a blast leaves any line
of six on the board, the player who fired it wins, whoever owns the line. Each call gets about 100 ms.

The organisers' engine jar (`libs/core-2.0-SNAPSHOT.jar` from the starter kit) is not included and was removed from
the whole history. Put your copy into `libs/` to build and run the tests.

## Where we got

| Version | Server name | What changed | Evidence |
|---|---|---|---|
| m0-m5 (tags) | Cristi Schuszter | rules core, PVS + TT, root VCF, learned pattern tables | `docs/milestones.md` |
| 3.0 / 3.1 (tags `v3.0`, `v3.1`) | Savi beater 3.x | upload-safe code (no System, exceptions, threads), match clock, size-independent tables | `upload-safe` branch |
| rc5 (tag `rc5`) | King beater, Data Collector | Crop for giant boards, generic tables g6f, warm-up | 83-1-0 in export 10; 187-10 in export 16 |
| rc7 | The Emperor | blast-threat search, partial iterations | 65-18 on 15x100 (export 17); source in `night/patches/` |
| rc8 (tag `rc8`) | Cod Published CHET45 | gap crop for very wide boards | 56-17 on 15x100 (export 19) |
| rc9 (tag `rc9`) | - | keep a blast that is the only defence | `rc8-bomb` branch |
| final (tags `final`, `final-safer`) | final round | rc8 + rules announced on the day (k, blast size, cooldown, limits), rules inference in play, per-k tables (k4, k5, k6, k7) | `night/final/`, `docs/final-day.md` |

Numbers are game records from the organisers' server exports; our reports on them are in `analysis/reports/`.

## How the bot works

- **Rules core** (`src/main/java/ch/qc/bot/rules/`): window-coded board, make/unmake, exact blast simulation,
  threat cells, Zobrist hash. Checked against the official engine on thousands of scripted games and against a
  naive rule copy on 10 M random positions.
- **Search** (`src/main/java/ch/qc/bot/search/`): iterative-deepening PVS with a transposition table, gravity-aware
  VCF threat search at the root, blast-threat detection, late-move reductions, partial last iteration.
- **Evaluation** (`src/main/java/ch/qc/bot/eval/`): hand pattern tables plus learned tables over 6-cell windows,
  size-independent ("generic") so one table serves every board size; one table per win length k.
- **Time** (`Timing`, `MatchClock`, `CallLimit`): hard stops below the call limit, a shared match clock, measured
  call limit, constructor warm-up.
- **Giant boards** (`Crop`): boards wider than 40 columns are searched only around the discs.
- **Upload rules**: the server bans System, io/nio/net, reflection, threads and exceptions; `UploadRulesTest`
  enforces this on the bot classes.

## Training the tables

Self-play at a fixed node budget (`./gradlew selfplay`), fit in Python/torch (`training/qctrain/`), gate by
SPRT (`./gradlew fastmatch`), embed into the jar (`./gradlew embedWeights jar`). Lessons that cost us most:

- with little data a target mix of lambda 0.5 makes the tables memorise game outcomes; lambda 0.9 with K = 256 on
  the raw search value works;
- judge a table by matches and by rank correlation with the search value on held-out games, not by validation loss;
- fresh fits beat warm-started ones whenever the gap was significant.

Details: `docs/pattern-bot-design.md` (design), `docs/milestones.md` (M0-M6 results), `night/results/eval.md`.

## Repository map

| Path | What it is |
|---|---|
| `src/main` | the bot (upload jar contents), `ch.qc.bot.QcBot` |
| `src/test` | conformance, differential, robustness, upload-rules and search tests |
| `src/tools` | match runner, fixed-node matches, SPRT, self-play, opening generator, server-game replay |
| `training/` | Python training code and feature specs |
| `weights/`, `weights-k/` | the shipped learned tables |
| `suites/` | opening suites per board size |
| `docs/` | design, milestones, dynamic rules, final-day procedure, developer guide (old README with all commands) |
| `night/` | plans, status logs, gate and build scripts from the last two days, patches for rc7/rc8 |
| `analysis/` | server-export reports and scripts (raw exports not included), loss reviews, Savi reproduction attempt |
| `research/` | research report, notes on related games and engines, source digests and source lists |

## Branches

| Branch | What it holds |
|---|---|
| `main` | final-safer code plus the docs, analysis and research folders |
| `rc9-dynrules` | final code with announced-rules switches and per-k tables |
| `final-safer` | fallback final: no warm-up, no second-call search, stops at most 70 ms |
| `rc8-bomb`, `rc9-draw`, `rc9-g8s`, `pass-trap` | rc9 candidates: blast defence, draw handling, server-position tables, pondering |
| `night-main`, `rc5-blast-threat`, `rc5-nano` | rc4/rc5 line, blast-threat search (rc6/rc7 base), nanoTime clock |
| `night-eval`, `night-search`, `night-speed`, `night-time`, `night-huge`, `night-book` | parallel work streams of the night before the server opened |
| `upload-safe` | the first upload-safe builds (2.x, 3.0, 3.1) |
| `master` | milestones M0-M6 |
| `the-dark-prince` | an older, weaker snapshot (3.0-level code, a deliberately weak table) published here first |

Commits titled "WIP: uncommitted state ..." hold edits that were never committed in a working copy when the
tournament ended.

## Build

```
# put core-2.0-SNAPSHOT.jar from the starter kit into libs/ first
./gradlew test
./gradlew jar          # build/libs/*.jar, Bot-Class ch.qc.bot.QcBot
./gradlew run          # emulator window
```

JDK 25 is fetched by the Gradle toolchain. More commands: `docs/developer-guide.md`.

## How it was made

The team worked with AI coding agents (Claude, and Codex for one release). Most commits were written by agents
working in parallel git worktrees, with matches and SPRT gates deciding what shipped.

## License

MIT, see `LICENSE`. The starter-kit files (`EmulatorRunner`, `ch.qc.starter`) come from the organisers.
