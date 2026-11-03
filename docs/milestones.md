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
