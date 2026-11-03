# Milestone log

Measurements per milestone, newest last. Definitions of done are in `pattern-bot-design.md` section 6.

## M0 Safe baseline (2026-11-03)

Machine: Windows 11, JDK 25.0.4 (Temurin, Gradle toolchain), engine `libs/core-2.0-SNAPSHOT.jar`.

What exists: `Geometry`, `Tables`, `Board` (window codes, threat cells, live-3 counts, Zobrist, make/unmake,
blast per R5-R7, valley prefilter), `Snapshot`, `TurnTracker`, `Decision`, `QcBot` (centre-first, never requests a
special, guarded two-call protocol, safe-column fallback), test suites, `MatchMain`, `PackagingProbe`.

| Check | Command | Result |
|---|---|---|
| Conformance against the official engine | `test -Pqc.conf.games=5000` | 5,007 scripted games (1x1 to 40x40, both orders) match the engine at every call, in history, final field and result; all 21 required rule situations exercised (forfeits by range, full column, exception, timeout; refused, honoured, wasted specials; turn 11 and +10; blast wins with own, opponent and both colours; draws by full row and at 1,000 turns; win on turn 1,000; win that fills the board) |
| Differential Board vs naive rule copy | `test -Pqc.diff.positions=10000000` | 10,000,000 positions, 97.8 M blasts simulated, 126,227 winning blasts, all of them passed the valley prefilter; incremental state equals a rebuild at every position |
| TurnTracker vs engine counters | `test` | exact own and opponent cooldown counters and ply index against `SpecialMoveBot`, `LinearBot`, `CheatingBot` (forfeits), a special-using bot, one instance reused across games and colours; with a history-clearing opponent own counter exact, opponent counter never below the engine's |
| Robustness | `test -Pqc.robust.calls=100000` | 100,165 QcBot calls over 15 board sizes (1x1 ... 40x40): 0 timeouts, 0 invalid columns, 0 invalid specials; `accelerateQuark` p50 0.01 ms, p99 0.25 ms, p99.99 1.87 ms, max 3.14 ms |
| Packaging | `build probe` | jar 1,003 KB, `Bot-Class: ch.qc.bot.QcBot`, no tool or test classes; loads through an isolated `URLClassLoader`, constructor 0.3 ms, plays a game against the jar's own engine |

Baseline matches (`match --pairs 500`, 8x12, 4 random opening plies, colour-swapped pairs, official protocol):

| Opponent | Score | W / D / L | Notes |
|---|---|---|---|
| `ch.qc.starter.RandomBot` | 96.1 % | 961 / 0 / 39 | all games decided by a normal six |
| `ch.qc.starter.SpecialMoveBot` | 97.0 % | 970 / 0 / 30 | one game decided by a blast |
| `ch.cern.quantumconnect.bots.LinearBot` | 82.0 % | 820 / 0 / 180 | |

QcBot had 0 timeouts, 0 invalid columns and 0 invalid specials in all 3,000 games.

Corrections to the design found while building M0:

- Section 2.2: on 8x12 a cell lies in at most 15 windows (6 horizontal, 3 vertical, 3 + 3 diagonal), not 21.
- Section 2.7: "reset when the board is empty and the history is empty" misses a reused instance that moves
  second (it sees one disc). The tracker resets when at most one disc is on the board, the history has at most
  one entry and the history did not grow since the last call. A cleared history is detected by record identity
  (`getMoveHistory` copies the list, not the `Move` records).
- The starter ships without `gradlew.bat` and without a toolchain repository; `settings.gradle` now applies the
  foojay resolver so JDK 25 is provisioned automatically.

## M1 Tactical root (2026-11-03)

What exists: `search/Tactics` at the root of `QcBot`. Order: immediate win by a drop, immediate win by a blast
(valley prefilter); otherwise every normal move is classified by a three-ply check (loses at once / lets the
opponent force a win / neutral / forces our win), cooldown readiness of both sides tracked through the plies;
inside a class a window-potential score, then the most central column. A blast is used only to win at once or
when every normal move loses at once. The three-ply check stops 25 ms after the call starts and is skipped on
boards wider than 16 columns.

Deviation from design section 6: the design's M1 is strictly one-ply. With one ply the bot lost 3 of 10,000 games
to `RandomBot` (unstoppable double threats) and scored only 75.7 % against M0 (486 of 1,000 games drawn by a full
board), so both definition-of-done targets failed. The three-ply check and the window-potential tie-break fixed
both; they are a stopgap until the M2 search replaces them.

| Check | Command | Result |
|---|---|---|
| Suites T1, T3, T5 (graded by brute force with the naive engine copy) | `test -Pqc.suite.positions=30000` | 30,000 positions, all correct: T1 win in 1 11,194/11,194, forced block 10,091/10,091, under an opponent threat cell 2,520/2,520; T3 blast win 712/712, blast negative set 8,873/8,873; T5 blast danger 372/372 |
| Suite T6 (cooldown edge cases) | `TurnTrackerTest` | unchanged from M0, exact |
| All M0 tests | `test` | pass (the robustness test now also replays the bot's cooldown counter and asserts every special request has it at 10 or more) |

Matches (`match`, 8x12, 4 random opening plies, colour-swapped pairs, official protocol, final build):

| Opponent | Games | Score | W / D / L | Decided by blast |
|---|---|---|---|---|
| `ch.qc.starter.RandomBot` | 10,000 | 100 % | 10,000 / 0 / 0 | 24 |
| `ch.qc.starter.SpecialMoveBot` | 10,000 | 100 % | 10,000 / 0 / 0 | 33 |
| `ch.qc.tools.bots.M0Bot` (frozen M0) | 1,000 | 100 % | 1,000 / 0 / 0 | 0 |

QcBot: 0 timeouts, 0 invalid columns, 0 invalid specials. `accelerateQuark` latency against `RandomBot`:
p50 0.04 ms, p99 2.77 ms, p99.99 28.8 ms, max 31.2 ms (before the 25 ms cut-off the maximum was 63 ms).

## M2 Alpha-beta search with hand evaluation (2026-11-03)

What exists: `search/Searcher` (fail-soft negamax PVS, iterative deepening, two-entry lockless transposition table
keyed by cells, side to move, both cooldown counters and the distance to the 1,000-turn cap; ordering TT move,
two killers, history, window potential, centre, blasts last and a penalty for playing under an opponent threat
cell; mate-distance scores; a forced reply to a single playable threat costs no depth; blasts generated at depth
3 or more or when defending), `search/TranspositionTable` (2^18-2^21 entries from the heap size), `eval/HandEval`
(design 3.9 window values plus the threat family: d=1 threats, stacked threats, waiting-game parity, cooldown
bias). Time: hard stop 62 ms from the call entry (40 ms on an instance's first call), clock and interrupt polled
every 256 nodes, no new iteration after a third of the budget, per-call context and a token so a late search
aborts, a fresh root board per call. `QcBot` falls back to the M1 tactics if the search cannot run. Null move,
LMR, aspiration and futility are off, as the design specifies for M2.

Ladder: `MatchMain` and the tests accept `jar:PATH` bots, loaded child-first for `ch.qc.*`, so frozen builds from
the git tags play the current code in one JVM (`ladder/m1.jar` built from tag `m1`).

| Check | Command | Result |
|---|---|---|
| T1, T3, T4, T5 for the search at 5 ms per position | `test` (`TacticsSuiteTest.search`) | 6,000 positions, all correct: win in 1 2,312, forced block 2,085, under threat cell 470, blast win 63, blast negative 1,745, only a blast saves 96, blast danger 22 |
| T1, T3, T4, T5 for the root tactics (fallback) | `TacticsSuiteTest.rootTactics` | 6,000 positions, all correct |
| T2 forced wins in 3 and 5 plies, proven by an exhaustive solver | `MateSuiteTest` | 302/302 found (248 in 3, 54 in 5) within 80 ms; slowest 2.6 ms |
| T6, conformance, differential, robustness | `test` | pass |

Matches (`match`, 8x12, 4 random opening plies, colour-swapped pairs, official protocol):

| Opponent | Threads | Games | Score | W / D / L | Blast-decided |
|---|---|---|---|---|---|
| frozen M1 (`jar:ladder/m1.jar`) | 6 | 1,200 | 88.3 % (Elo +352) | 948 / 224 / 28 | 101 |
| frozen M1, latency run | 2 | 300 | 89.2 % | 241 / 53 / 6 | 25 |
| `ch.qc.starter.SpecialMoveBot` | 6 | 600 | 100 % | 600 / 0 / 0 | 20 |

Search and latency (2-thread run, 15,529 calls, measured inside the bot call): median completed depth 7 plies,
1.08 M nodes/s; `accelerateQuark` p50 35.2 ms, p99 63.0 ms, p99.99 64.3 ms, max 66.4 ms (design 5.5 targets 70 /
75 / 80 / 90 ms are met); `useSpecialMove` max 0.06 ms. Allocation per call p99 91 KB; the maximum, 209 KB, is the
first call of a game, which builds the geometry, evaluation and search tables (target 200 KB). 0 timeouts,
0 invalid columns, 0 invalid specials in all runs.

Notes:

- With 6 parallel games an early run showed calls up to 88 ms while the search itself ran past its deadline; one
  game at a time stayed at 65 ms and the GC log showed pauses under 2 ms, so the tail came from CPU contention on
  this machine, not from the bot. Clock polling was tightened from 1,024 to 256 nodes. Latency targets are
  therefore measured on at most 2 threads.
- Thread-creation overhead outside the bot call (engine side) is not measured yet; that is the M4 latency probe.

## M3 Gravity threat search (2026-11-03)

What exists: `search/Vcf`, a gravity VCF prover. The attacker plays only drops that leave it a playable threat
cell; the defender's replies are enumerated in full (its own immediate win, the block of a single threat, every
blast while its special is ready), so a returned win is a proof; drops are made and taken back in place, blasts
use copies; own lockless result table (2^17 entries, allocated in the `QcBot` constructor) keyed by cells, attacker,
both cooldown counters and, near the cap, the turns left; node and time limits, clock checked at every node; an
optional tentative mode (H1) that skips defender blasts and uses separate keys. `Searcher.Options` switches the
uses, and `MatchMain` accepts `qcbot:+razor,-leafVcf`-style specs to play variants against each other.

Default configuration after the ablations: own VCF at the root (12 forcing drops, 20k nodes, 10 ms) and the
opponent's VCF check at the root (8 drops; if the opponent wins by VCF after a pass, keep only root moves after
which its VCF is no longer found); leaf VCF and razoring off. Clock polling is every 16 nodes on boards above
400 cells (a 40x40 call had reached 101 ms with 256).

| Check | Command | Result |
|---|---|---|
| T7 short proofs vs the exhaustive solver | `VcfTest` | 100,000 positions, 11,507 proofs, no false proof (the solver finds 11,753 wins within 3 plies; the 246 the VCF misses start with a blast or a quiet move) |
| T7 long proofs vs an independent checker (defender tries every legal move, including the no-op blast on a full column) | `VcfTest` | 50,000 positions, 5,879 proofs up to 5 forcing drops, no false proof; 26 need two or more drops |
| Table independence | `VcfTest` | 5,000 positions, same verdict with a shared or an empty table |
| T1-T5, T2, T6, conformance, differential, robustness | `test` | pass (T2 302/302) |

Ablations (`match`, 8x12, 600 games each, 6 threads; A against the then-default with every VCF use on):

| A | Score of A | Note |
|---|---|---|
| leaf VCF off | 71.6 % (+160 Elo) | the leaf VCF took 66 % of all nodes, 266k instead of ~1 M nodes/s, median depth 6 instead of 7 |
| tentative leaves | 47.8 % | within noise |
| razoring on | 49.6 % | within noise |
| opponent VCF check off | 50.2 % | within noise |
| leaf VCF gated to "defender special not ready", 100 nodes (against leaf VCF off) | 36.1 % (-99 Elo) | still 57 % of nodes |

Final M3 against frozen M2 (`jar:ladder/m2.jar`, 1,200 games, 6 threads): **50.9 % (+6 Elo), 325 / 571 / 304,
not significant** (about +-10 Elo at this sample). The definition of done asks for an SPRT gain over M2; it is
not met. The design anticipated this ("could be lower if shallow search already finds most threats"): the M2
search with its zero-cost forced reply already resolves short forcing lines, and a leaf VCF at this node cost
loses more depth than it wins. The root VCF and the opponent check are kept because they are almost free (0.1 %
of nodes) and turn long forced wins into immediate moves. Statistics in that run: 0.23 proofs per call, 3,377
defender-blast refutations over 79,051 calls.

Latency: 25x25 / 40x40 / 10x14 / 12x8 against `SpecialMoveBot` on 2 threads, max 67 ms (was 101 ms); 8x12 robustness
run max 63 ms. Allocation per call on 8x12 p99 91 KB; on 40x40 p99 0.8 MB (a 40x40 board copy is about 40 KB).
0 timeouts, 0 invalid columns, 0 invalid specials in all runs.

## M4 Harness and SPRT (2026-11-03)

What exists (all in `src/tools`, none of it in the uploadable jar):

- `Sprt`: pentanomial GSPRT over colour-swapped pairs with the LLR of design 5.3, no verdict before 50 pairs.
- `ReportMain` (`./gradlew report`): pair statistics, draw / blast / first-player shares and the SPRT verdict of
  stored JSONL logs (also the logs written before the decimal-comma fix).
- `MatchMain` (`./gradlew match`): pairs are tasks, results stream to the JSONL file as they finish, `--sprt E0,E1`
  stops at the verdict, `--openings FILE` uses the pool (pair p plays line (p + seed) mod N in both colours),
  pentanomial summary, draw share, blast share and first-player score in every report.
- `OpeningGen` (`./gradlew openings`): `suites/openings-8x12.txt`, 2,000 lines (1,477 of 4-10 plies, 523 of 20-30
  plies), each kept if no six, no full column and a 250 ms search scores the final position within +-150 units;
  2,208 candidates, 1 minute on 6 threads. The design asked for a 1-second search; 250 ms was used to keep it short.
- `FastGame` / `FastMatch` (`./gradlew fastmatch`): the in-process game loop with the engine's turn rules and
  fixed-node search (`Searcher.run(..., maxNodes)`), reproducible bit for bit, about 15,000 games per hour on 16
  threads at 20k nodes per move.
- `LatencyProbe` (`./gradlew latency`): games driven through the engine's own `Experiment.executeWithTimeout`,
  recording time inside the bot, around the official call, and the difference.
- `./gradlew regression`: all tests, then a non-regression SPRT [-10, 0] against the newest jar in `ladder/`.
- `ToolsFields`: harness access to the package-private `QuantumField` (tools source set only).

| Check | Result |
|---|---|
| SPRT simulation (`SprtTest`, 1,000 runs per point, 40 % draws, uncapped) | P(accept H1): 0.003 at -5 Elo, 0.044 at 0 (alpha 0.05), 0.951 at +10 (power 0.95), 1.000 at +20; with the 4,000-pair cap 0.868 at +10. Without the 50-pair minimum the false-positive rate was 0.345 |
| FastGame against the official engine (`FastGameConformanceTest`) | 300 games of position-determined engines identical in winner, reason and length (286 six, 9 blast, 5 full) |
| Fixed-node determinism | two runs of 100 games with the same seed give byte-identical logs |
| Stored verdicts reproduced (`report`, bounds [0, 10]) | M1 vs M0: H1 (500/500 pairs WW); M2 vs M1: H1, +352 Elo [+329, +377]; M3 vs M2: no verdict at 600 pairs, +6 [-8, +21] |
| 2,000 official-protocol games, M3 vs M3, pool openings, 8 threads | 20.4 minutes, 0 timeouts in 253,161 calls; score 50.3 %, draw share 49.5 %, decided by a blast 4.1 %, first player scores 50.3 % (no first-move advantage measurable) |

Latency through the engine timer (10,000 calls each, one game at a time):

| GC / load | inside the bot p99.99 / max | around the official call max | engine overhead p50 / max |
|---|---|---|---|
| G1 (default) | 64.2 / 65.5 ms | 65.8 ms | 0.21 / 2.9 ms |
| Serial | 64.4 / 64.4 ms | 64.7 ms | 0.21 / 4.5 ms |
| Parallel, 32 MB TT | 92.2 / **108.2** ms | 108.6 ms | 0.20 / 4.7 ms |
| Parallel, 8 MB TT (current) | 64.4 / 64.7 ms | 64.9 ms | 0.21 / 3.2 ms; one `useSpecialMove` call hit a 95 ms pause |
| G1 with 6 busy background threads | 64.1 / 67.4 ms | 67.8 ms | 0.23 / 4.0 ms |

The 108 ms call was a ParallelGC full collection: each bot instance allocated a 32 MB transposition table, so
games that create new instances fill the old generation. The table is now capped at 2^19 entries (8 MB) and the
VCF table at 2^16 (1 MB); against `ladder/m3.jar` (32 MB table) the current build scores 49.5 % (-3 Elo
[-24, +17], 600 games), i.e. no measurable cost. A GC pause in `useSpecialMove` only drops the special (R16).
Which GC and heap the tournament JVM uses is unknown (design 8.2).

Not done: SPSA tuning of the scalar parameters. The scalars it would tune (hand-evaluation weights, margins) are
replaced or retuned by the learned tables of M5, so it is deferred until after M5.
