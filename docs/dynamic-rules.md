# Rules that change per match

Status 2026-11-05, branch `rc9-dynrules` (cut from `rc9-g8s`). The organisers announced that the line length that
wins (k, today 6) and the special move (today a 3x3 blast with a cooldown of 10 own turns) may change from match to
match. The call limit stays 100 ms. The new engine is not published yet.

## What the bot does now

One object, `ch.qc.bot.rules.Rules` (k, blast half width `blastW`, blast half height `blastH`, cooldown), is built once
per match and reaches everything through `Geometry.rules`. `Rules.DEFAULT` is core-2.0: k = 6, 3x3 (1, 1), cooldown 10.

| Place | Before | Now |
|---|---|---|
| `QcBot` | `new Geometry(rows, cols)` = k 6 | `new Geometry(rows, cols, rules)`; `noteRules` reads the rules at every call and drops the model when they change (rebuilt like after a new board size) |
| `EngineRules.read(field)` | - | the single hook for the new engine API; returns `Rules.DEFAULT` until the API exists |
| `Board.blast` | 3x3 | (2 blastW + 1) x (2 blastH + 1) |
| `Board.blastWins / anyBlastWins` | exact tests for 3x3 only | 3x3 keeps the fast tests; other sizes use `blastWinsGeneric` (walks the falling discs, counts lines on the board as the blast would leave it, copies nothing) |
| `Board.blastMayWin`, `anyBlastWinsNear` | neighbour 3 taller, reach k | a column within blastW at least blastH + 2 taller, reach k - 1 + blastW |
| `TurnTracker.COOLDOWN`, `Searcher`, `Vcf`, `Tactics` | constant 10, counter 1 after a blast | `rules.cooldown`, `rules.cdAfterBlast()`; Zobrist keys for counters above 10 are drawn after the old ones, so default keys did not change |
| Evaluation counters | 0..10 | the search maps counters onto the 0..10 scale the tables were trained on (`Rules.evalCounter`: turns left until ready); identity for 10 |
| `Features` valley count | neighbour 3 taller | `blastMayWin` for other blast sizes |
| `HandEval` window values | `VAL[min(discs, 5)]` | by discs still missing (k - 1 discs score like five of six); same table for k = 6 |
| `Tactics` window weights | table for k = 6 | 1, 3, 9 ... up to k - 1 discs |
| `Tables.live3` (leaf threat-search prefilter) | at least 3 discs | at least k - 3 discs (3 for k = 6) |
| `Crop` (boards above 64 columns) | margin 6, grow 12 | margin max(6, k), grow max(12, 2 margin) |
| Learned tables | k = 6 only | still k = 6 only: `Weights.forGeometry` returns null for another k and the bot uses `HandEval` |
| `Geometry` Zobrist seed | fixed | salted with the rules for non-default rules, so a shared table never mixes positions of different rules |
| `WarmUp` | default rules | unchanged (it only compiles the code) |

Limits: k is clamped to 2..9 (window codes are 16-bit; k = 10 needs `int` codes, a mechanical change), blast half sizes
to 0..8, cooldown to 0..1000. Clamping, not refusing: the bot may not throw. A blast of radius 0 removes only the
placed disc, so it is a pass move; the search handles it as such.

## Proof that k = 6 play did not change

* Fixed-node self-play, old build (`b571b55`) against new build, same seed: 50 pairs each on 8x12, 15x11, 6x7 and
  15x22 at 20,000 nodes with the shipped generic tables. All 400 games identical (winner, reason, plies), compared
  game by game (`night/logs/dyn/base` and `night/logs/dyn/new`).
* The whole test suite passes (see the summary at the end of this file).

## Tests added

* `BoardDifferentialTest.boardMatchesNaiveRulesForOtherLinesAndBlasts`: k 4-8 and blast half sizes 0-2 (square every
  round, rectangles now and then) against `NaiveRules`, the literal engine re-implementation now with a blast size:
  cells, heights, win flags after drops and blasts, incremental state, `blastWins`, `blastWinsGeneric`,
  `anyBlastWins`, `anyBlastWinsNear`, prefilter soundness. The 3x3 test now also checks `blastWinsGeneric` against
  the fast test.
* `DynamicRulesTest`: under forced rules of four the bot sees and plays the win in one that does not exist under six;
  switching the rules between games rebuilds the model.

## Harness

`FastMatch --k K --blast R --cooldown C` (or `--blastW/--blastH`) plays any rules through `FastGame`. `@auto` weights
are what the bot uses (tables where they fit, else hand); a side spec `tactics` is the bot's three-ply fallback as a
baseline. The summary line counts per side missed wins in one and one-ply blunders (the move let the opponent win at
once although another move did not). Opening lines are cut before a move that would already make a line of k.
Runner: `night/tools/dynfm.ps1` (E-cores, below normal priority).

`MatchMain` and the real-time gates run the real core-2.0 engine, which knows only k = 6 and 3x3: they cannot test
other rules until the new jar exists.

MEASUREMENTS

## How the bot should learn k and the blast

core-2.0 offers no way:

* `QuantumField.CONNECTED_QUARKS_REQUIRED_TO_WIN = 6` is `static final` and package-private. The upload rules forbid
  reflection, and a class of ours in package `ch.cern.quantumconnect.core` would not be in the organisers' jar.
* The blast is the literal loop `-1..1` in `QuantumField.destroySurroundingDiscs`.
* `Experiment.MOVES_BETWEEN_SPECIAL_MOVES = 10` is public but a compile-time constant: javac copies the value into
  our class when we compile, so reading it always says 10, whatever the server's jar holds. `GameConstants` has only
  `TIMEOUT_MS`, `MAX_ITERATIONS`, `DEFAULT_ROWS`, `DEFAULT_COLUMNS`.
* Bots get only the `QuantumField` passed to `accelerateQuark` / `useSpecialMove`; `Experiment` is not reachable.

Candidate ways, best first:

1. **Getters on `QuantumField` (what we should ask for).** Instance methods on the field object the bot receives, for
   example `getConnectedQuarksRequiredToWin()`, `getSpecialMoveRadius()` (or width and height, or a list of offsets
   if the shape is not a rectangle) and `getMovesBetweenSpecialMoves()`. Per-match values must live in the field
   (each match builds a new one), and they must be methods or non-final fields, never `static final` constants with
   a constant initialiser, which javac inlines into our jar. Wiring: one line in `EngineRules.read`.
2. **Non-constant statics** (`public static int getWinLength()` or a static field set at match start). Works for
   reading, but breaks if the server runs two matches at once in one JVM; worse than 1.
3. **Inference from play, as a fallback if the API never comes.**
   * Blast size: compare the board before and after the first honoured special of either side in the move history
     (`Move.isSpecialMove()`); the cleared discs give a lower bound of the block, our own blast in a well-filled area
     gives it exactly. Until then assume 3x3.
   * Cooldown: the history records only honoured specials; a special we request at counter c that is not honoured
     proves cooldown > c, an honoured one proves cooldown <= c. Cheap to probe late in a game.
   * k: only a finished game shows it (the winning line), and the bot is not called after the end. If the server
     keeps our class between the games of a match (statics survive, as the warm-up flag shows), a win of ours tells
     us the line we completed; a loss tells nothing. Too weak alone; assume 6.
4. **Board size as a hint**: no; sizes already vary with k fixed.

### What we need from the organisers' new core jar

* The jar itself, as early as possible: we compile against it, rerun `ConformanceTest` (engine vs our model) and the
  differential tests with the new rules.
* The API of point 1 (getters on `QuantumField`), and its semantics:
  * k: the same in all four directions? Does a line longer than k win (today: yes, at least k)?
  * Blast: the shape (square radius, rectangle, cross, arbitrary offsets), whether the landing cell is cleared,
    whether gravity still compacts the whole field afterwards, and what happens in a full column (today: no move,
    the turn is lost but the counter still resets in our model).
  * Cooldown: the counter rule (today: honoured iff the own counter is at least 10; the counter resets to 0 on an
    honoured special and grows by one per own turn).
  * A blast that makes lines of both colours: who wins (today the engine ends the game; our model counts it a win for
    the mover)?
  * The range of values a match may use (k = 3..8? radius 0..2?), so we can size tables and tests.
* Whether rules can change between the 7 games of one pairing, or only between pairings.

## What is left

* Learned tables exist for k = 6 only. Other k play with `HandEval`; train per-k tables (k 4, 5, 7, 8) on the same
  generic feature set, or a k-generic table set, once the range of k is known.
* `WarmUp` warms only the 3x3 path; the generic blast test gets compiled during the first real searches.
* Blast shapes other than rectangles need a mask in `Board.blast`, `blastWinsGeneric`, `blastMayWin` and
  `Searcher.blastLeavesDouble`.
