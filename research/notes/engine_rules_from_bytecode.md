# Quantum Connect engine rules, reverse-engineered from bytecode

Source: `core-2.0-SNAPSHOT.jar` (classes version 69 = Java 25 target; `javap` 21.0.12 read them fine, no refusal). Method: `javap -c -p -constants` only; no code from the jar was run, nothing was downloaded. Jar contents: `Main`, `bots.{CheatingBot,LinearBot,RandomBot,SpecialMoveBot}`, `core.{Algorithm,Experiment,ExperimentStatus,GameConstants,Move,QuantumField,QuantumFieldPainter(+$1),SpaceOwnership,WinningPosition}`. Manifest Main-Class = `ch.cern.quantumconnect.Main`.

Certainty: everything below is read directly from bytecode unless marked **(uncertain)**.

## 0. Constants and API

- `GameConstants`: `MAX_ITERATIONS=1000`, `TIMEOUT_MS=100`, `DEFAULT_ROWS=8`, `DEFAULT_COLUMNS=12`.
- `QuantumField.CONNECTED_QUARKS_REQUIRED_TO_WIN = 6` (package-private static final int). The literal `bipush 6` is also inlined in `checkDirection`.
- `Experiment.MOVES_BETWEEN_SPECIAL_MOVES = 10` (public static final). The loop also inlines `bipush 10`.
- `Algorithm` interface: `int accelerateQuark(QuantumField)` (returns the column index) and `default boolean useSpecialMove(QuantumField)` which returns `false` (`iconst_0; ireturn`).
- `SpaceOwnership` = `EMPTY, FIRST_PLAYER, SECOND_PLAYER`. `ExperimentStatus` = `INCOMPLETE, COLLAPSED (someone won), UNCERTAIN (draw)`.
- `Move` record = `(boolean isSpecialMove, int column, SpaceOwnership player)`. `WinningPosition` record = `(int row, int column)`.

## 1. Board and normal move

- Defaults 8 rows x 12 columns. Experiment constructors:
  - `Experiment(a,b)` -> `(a,b, paint=true, print=true, 8, 12)`.
  - `Experiment(a,b,paint,print)` -> 8x12.
  - `Experiment(a,b,paint,print,rows,columns)` is the full form.
  - The shipped `Main` uses `new Experiment(RandomBot, SpecialMoveBot, true, false, 25, 25)`, i.e. **a 25x25 board**. Board size is a constructor parameter, so a bot must read `getNumberOfRows()/getNumberOfColumns()` and not hardcode 8x12.
- Storage is `SpaceOwnership[rows][columns]`, all `EMPTY` after `initializeField`.
- **Row 0 is the TOP, row `rows-1` is the BOTTOM.** `produceCollision(col)` scans `r = rows-1` downward to 0 (`isub 1; ... iinc 2,-1`) and puts the disc in the first `EMPTY` cell found, which is the lowest empty cell. Gravity falls toward higher row indices.
- Disc owner = `FIRST_PLAYER` if `currentPlayer == firstPlayer` (reference compare), else `SECOND_PLAYER`.
- A successful normal move appends `new Move(false, col, ownership)` to `moveHistory`.
- Invalid column (`col<0 || col>=columns`): `log.error("Invalid column selected: {} - {}")` and `return`. **No disc, no exception, no history entry.**
- Full column (no EMPTY found in the scan): `log.error("Column is full - {}")` and `return`. Same effect: the turn is silently forfeited.
- `isColumnFull(c)` tests only `field[0][c] != EMPTY` (the top cell).

## 2. Special move (`produceSpecialCollision(col)`)

- The column is validated like a normal move (an error is logged and the method returns if it is invalid or full). On a full column nothing happens, but the cooldown is still consumed (section 3).
- Otherwise:
  1. Place the current player's disc in the lowest empty cell (r,c), same scan as a normal move.
  2. `destroySurroundingDiscs(r,c)`: for `dr` in -1..1, `dc` in -1..1 (a **full 3x3 block, centre included**) set `field[r+dr][c+dc] = EMPTY` where the cell is in bounds. There is no skip for dr=dc=0, so **the disc just placed is destroyed too**. Own discs and opponent discs are destroyed alike (no owner check).
  3. `applyGravity()` over ALL columns. For each column it runs `r` from bottom to top; for each EMPTY cell it finds the nearest non-empty cell above, moves it down into `r`, and blanks the source. The result is a fully compacted column, so discs above the hole fall down.
  4. Append `new Move(true, col, ownership)` to the history.
- Net effect: the special move places nothing. It deletes a 3x3 block centred on the landing cell, and the discs above fall. Geometry: the landing cell is the lowest empty cell of column c, so in column c itself only the cell directly below it (r+1, the top disc of that stack) is destroyed. In columns c-1 and c+1, rows r-1, r, r+1 are destroyed. Rows outside the board are skipped, so a special on an empty column destroys rows `rows-2..rows-1` of the neighbour columns only (bottom two discs).
- Because gravity is re-applied, the removal can create 6-in-a-row for either player (see section 4 for who wins).

## 3. Special move cooldown and misuse

State in `Experiment`: `movesSinceLastSpecialFirstPlayer`, `movesSinceLastSpecialSecondPlayer` (per player, independent counters, both 0 at start and at `restart()`).

Per-turn order in `startExperiment()` (bytecode offsets 62-441):
1. `cur = quantumField.getCurrentPlayer()`.
2. `col = executeWithTimeout(cur.accelerateQuark(field), 100)`. On SecurityException, `col = -1`.
3. `special = executeWithTimeout(cur.useSpecialMove(field), 100)`. **It is called on EVERY turn, always AFTER `accelerateQuark`**, with a separate 100 ms budget. On SecurityException, `special = false`.
4. `special = special && movesSince[cur] >= 10` (offsets 203-219). If the cooldown is not satisfied the request is **silently ignored and a normal move is played in `col`. No penalty.**
5. If special: `produceSpecialCollision(col)` and then `movesSince[cur] = 0`. The reset happens regardless of whether the special was effective (full or invalid column).
6. Otherwise: `produceCollision(col)`.
7. **Always** (special or not, valid or not): `movesSince[cur]++`, then `changeCurrentPlayer()`, then `iterations++`.

Cooldown arithmetic (counts the player's own turns, forfeited ones included):
- The first special is possible on the player's **11th own turn** (counter is 10 before the move).
- After a special, the counter is reset to 0 and then incremented to 1 in the same turn. The next special is possible **10 own turns later, with 9 normal turns in between**.
- Sequence of usable turns: 11, 21, 31, ... (per player).
- The column used is the one returned by `accelerateQuark` in the same turn. The bot must remember its chosen column (instance state) for `useSpecialMove`, which receives only the field. `useSpecialMove` is called after `accelerateQuark` on a different thread but sequentially (`Future.get`), so field visibility is OK.
- There is no public API to read the counter. A bot has to count its own `accelerateQuark` calls (exactly one per own turn; the same thread-safe counter in the bot instance). Do not derive it from `getMoveHistory`: a special on a full or invalid column resets the cooldown but leaves no history entry.

**Crash hazard (important).** When a special is allowed and requested, the engine first runs a loop `for r=rows-1..0: if (quantumField.isSpaceEmpty(r, col))` (offsets 236-275) for animation purposes. `isSpaceEmpty` has no bounds check. If `col` is -1 (timed-out or failed `accelerateQuark`) or `>= columns`, this throws `ArrayIndexOutOfBoundsException`. Only `SecurityException` is caught in `startExperiment`, so the exception **propagates out of `startExperiment`/`runExperiment`** and `endExperiment` is skipped. The effect on the tournament harness is **(uncertain)**; in paint mode it kills the experiment thread (the lambda only has try/finally plus InterruptedException handling). Rule: never request a special unless `accelerateQuark` returned a valid in-range column for that turn. A full column is safe (it just wastes the special).

## 4. Win, draw and end of game

- Checked at the **top of each loop iteration**, i.e. after every move, normal or special: `while (ExperimentStatus.INCOMPLETE.equals(field.getExperimentStatus()) && iterations.get() < 1000)`.
- `getExperimentStatus()`: `hasWinner()` -> `COLLAPSED`; else `isFieldFull()` -> `UNCERTAIN`; else `INCOMPLETE`. The win check takes priority, so a move that fills the board and wins is a win.
- `hasWinner()` scans every non-empty cell (row-major, from the top-left) and for each calls `checkDirection(r,c,dr,dc)` for the four directions (1,0) vertical, (0,1) horizontal, (1,1) diagonal down-right, (1,-1) diagonal down-left. `checkDirection` walks up to 6 cells and counts consecutive cells owned by the same owner as the start cell. It returns the 6 positions if the count is >= 6, else an empty list. **Win = 6 in a row, in any of the 4 line directions, with no exact-length requirement (overlines count).** Positions are saved in `winningPositions` (first found line only).
- `hasWinner()` **does not record or check WHOSE line it is.** Any 6-line of either colour gives `COLLAPSED`.
- **The winner is always the player who made the last move.** After the move, `changeCurrentPlayer()` has run. `endExperiment -> experimentCollapsed()` calls `changeCurrentPlayer()` again (flipping back to the mover) and announces `getCurrentPlayer()` as the winner. `Main` prints `getExperimentStatus()` and `Experiment.getCurrentPlayer()` as the result.
  - Consequence for a normal move: it can only create the mover's own line, so this is the normal win.
  - Consequence for a special move: gravity after the explosion can complete a line of EITHER colour. **Whoever fires the special move wins if any 6-line exists afterwards, even a pure opponent line.** If it creates lines for both, the mover wins. Simultaneous wins by both players are therefore impossible: the mover wins.
  - Before a normal move there is never an existing line (the game would have ended), so this affects specials only.
- Draw (`UNCERTAIN`):
  - (a) `isFieldFull()`: every top-row cell `field[0][c] != EMPTY`, with no winner.
  - (b) `iterations >= 1000` while still `INCOMPLETE`: the loop exits and `endExperiment` calls `experimentUncertain()` for anything that is not `COLLAPSED`. `iterations` counts all turns of both players, including forfeited turns, so 1000 total = 500 per player.
  - Specials remove discs, so a game can outlive a full 8x12 board's worth of moves, and the 1000-turn cap is reachable.

## 5. Timeouts and failures (`Experiment.executeWithTimeout`)

- Mechanism: `Executors.newSingleThreadExecutor()` per call; `submit(callable)`; `Future.get(100, MILLISECONDS)`; `finally shutdownNow()`. Wall-clock time, 100 ms for each of `accelerateQuark` and `useSpecialMove` separately (up to ~200 ms per turn). A new thread is created per call (cold JIT, no thread-locals carried over).
- Any `Exception` from the call (`TimeoutException`, `ExecutionException` wrapping anything the bot threw including Errors, `InterruptedException`) is converted to `SecurityException("...timeout...")`. The caller catches it, logs `log.error(...)` and carries on.
- Consequences:
  - Timeout or exception in `accelerateQuark`: `col = -1` -> `produceCollision(-1)` logs an error and does nothing. **The turn is forfeited (a pass). The game continues; it is NOT an automatic loss and no random fallback move is made.** The opponent just moves again after the (empty) turn. The forfeited turn still counts toward `iterations` and the special cooldown counter.
  - Timeout or exception in `useSpecialMove`: treated as `false`.
  - Returning an out-of-range or full column: same as forfeit (normal move) or wasted special (see crash hazard above for out-of-range + special).
- `shutdownNow()` only interrupts the thread. A bot that ignores interruption keeps running in the background (non-daemon executor thread) and still holds the live field reference. **(uncertain)** whether this has any side effect on the harness (CPU contention or JVM exit delay). Avoid long computations that do not poll time; implement your own deadline well below 100 ms (JIT warm-up and a fresh thread per call make the first calls slower).

## 6. Order of play, sides, repeated games

- `QuantumField` ctor sets `currentPlayer = firstPlayer`. The first constructor argument of `Experiment` moves first and owns `FIRST_PLAYER` discs. **The engine never swaps sides.** There is no per-experiment loop over multiple games.
- `Experiment.restart()` (also fired from the painter's restart button) resets both cooldown counters and `iterations`, builds a **new** `QuantumField` with the same players in the same order (first still moves first), and re-runs. The same `Algorithm` instances are reused, so bot instance state persists across restarts unless the bot resets it. Counting turns with an instance counter must be reset when a new game starts (detect an empty board).
- Paint mode (`paintQuantumField=true`) runs the experiment in a new `Thread` and `runExperiment()` returns immediately. Without painting, it runs synchronously (`startExperiment(); endExperiment()`). A GUI (Swing) is involved only when painting.
- The 2-arg `Experiment` constructor enables painting and console printing by default.
- Any tournament/side-swapping, scoring or multi-game logic is NOT in this jar; it would be in the harness.

## 7. What a bot can see and do (`QuantumField`)

Public methods (the class is `final`; `getExperimentStatus`, `getCurrentPlayer`, `changeCurrentPlayer`, `produceCollision`, `produceSpecialCollision` are `protected` or package-private in `core`, so bots in other packages cannot call them without reflection):
- `getNumberOfRows()`, `getNumberOfColumns()`.
- `getField()`: returns a deep copy (`new SpaceOwnership[n][]` with each row `.clone()`d). Safe to modify. Index `[row][col]`, row 0 = top.
- `isSpaceEmpty(r,c)`: no bounds check (can throw AIOOBE). `isColumnFull(c)`: top-cell check, no bounds check.
- `belongsToMe(r,c)`: `field[r][c] == getOwnershipOfCurrentPlayer()`, where the current player is `FIRST_PLAYER` if `currentPlayer == firstPlayer` else `SECOND_PLAYER`. Inside `accelerateQuark`/`useSpecialMove` this is the bot's own colour, because the engine calls each bot only on its turn. **(uncertain)** The identity of "me" for a timed-out leftover thread: it would see the then-current player and not its own colour.
- A bot can also learn its own colour from `getMoveHistory()` (first own move) or by checking who moves: the first bot to be called on an empty board is `FIRST_PLAYER`.
- `getMoveHistory()`: returns a **copy** (`new ArrayList(moveHistory)`) of all recorded moves, oldest first. Only successful placements are recorded (forfeits and wasted specials are absent). Entries carry the special flag, the column and the player.
- `clearMoveHistory()`: public, mutates the **live** list. The engine itself never reads `moveHistory`, so it only affects bots that rely on it. A bot (or the opponent bot, if it shares the JVM) can wipe the history; do not depend on it for state, keep your own.
- `getWinningPositions()`: returns a copy of the last found winning line (empty unless the game is over).
- `observeQuantumField()`: **no randomness and no state change.** It just prints the board to `System.out` (rows top to bottom, `.` empty, `1` first player, `2` second). It is called by the engine itself only when `printMessagesToConsole` is true. It is dead weight for a competitive bot (do not call it: slow console I/O).
- The bot receives the **live** `QuantumField` object (the same instance the engine uses), not a copy. The only mutating public method is `clearMoveHistory`. `getField()` and `getMoveHistory()` are defensive copies. Reflection could mutate anything but is outside the intended API.
- **Randomness: the engine is fully deterministic.** No `java.util.Random`, `Math.random` or `ThreadLocalRandom` in `Experiment`, `QuantumField`, `Painter`. No probabilistic collisions. The "quantum" naming is cosmetic. Only `RandomBot` and `SpecialMoveBot` use `java.util.Random`. All outcome variance comes from the bots.
- Other classes can reach `System.out`: `printMessagesToConsole` makes the engine log each mover's class name, "Using special move that destroys surrounding discs!" and the board. Not relevant to rules.

## 8. Bundled bots (what the engine guards against)

- **CheatingBot**: `accelerateQuark` first does `Thread.sleep(1000)` (catching `InterruptedException`), then picks columns round-robin skipping full ones (`column = (column+1) % columns`). It exceeds the 100 ms timeout on every call. The engine interrupts it via `shutdownNow()` and records a timeout (`col=-1`), so **it never actually places a disc; every turn is a forfeit.** It exists to test the timeout guard ("cheating" = taking more time than allowed). The leftover thread wakes after the interrupt and keeps cycling `column` in the background. It never requests specials (default `useSpecialMove=false`).
- **LinearBot**: stateful round-robin over columns 0..columns-1 (`column` field), skipping full columns (checked via `isColumnFull`), returning the current column and advancing by one modulo `columns`. Never requests specials. It would loop forever only if all columns were full, which cannot happen because the game ends first.
- **RandomBot**: uniform random among non-full columns (`isColumnFull`); throws `IllegalStateException("No valid moves available")` if none (caught by the engine as a forfeit). No special move.
- **SpecialMoveBot** (extends RandomBot): random column via `RandomBot`; `useSpecialMove` returns `random.nextDouble() < p` with `p = 0.2` by default (`Main`'s printed text says 30%, but the constant is 0.2d: a discrepancy between message and code). It requests a special whenever the dice say so, even if the cooldown prevents it (those requests are silently ignored). Since the column is random, its specials are random, and it can hit the out-of-range hazard only if `accelerateQuark` fails.

## 9. Other things a competitive bot must know

1. Board size is not fixed (8x12 default, `Main` uses 25x25). Read dimensions from the field; the win length is 6 (hard-coded).
2. Win length 6 on 8 rows: a vertical line fits in 3 vertical positions (rows 0-5, 1-6, 2-7); horizontal lines have 7 positions per row on 12 columns. Overlines (7+) still count.
3. Specials are available from your 11th turn, then every 10th turn. A special never adds a disc: it is a demolition tool. It also costs you the tempo (your turn).
4. Special-move winner rule (section 4): a special move that makes ANY 6-line appear wins for the player who fired it. Look for explosions where the compaction forms a line, in particular the opponent's own near-lines (they fall into place and still count for you).
5. Danger: when the opponent's special is ready (their own-turn count reached 11, or 10 turns after their last special), they win if the explosion makes any 6-line appear, even one made of YOUR discs. Avoid leaving 5-of-6 shapes of either colour that a 3x3 removal plus gravity could close. Track the opponent's specials via `getMoveHistory` (`isSpecialMove`) and count their turns yourself.
6. Time budget: 100 ms for `accelerateQuark` plus 100 ms for `useSpecialMove`, wall-clock, on a fresh thread each time. Exceeding it forfeits only that call (turn lost or special dropped), but lost tempo is costly. Use a self-imposed budget of ~60-70 ms and make sure no work is left running after returning.
7. Never throw out of the bot; exceptions are swallowed as a forfeit, but they cost the turn.
8. Never return a full or out-of-range column; never request a special after a failed or invalid column.
9. The game is a draw after 1000 total turns or a full top row. With specials, a stalemate-ish game can reach the cap.
10. `useSpecialMove` is called every turn, even when the special is not yet allowed, so it must be cheap and must not count cooldown turns itself unless idempotent (a call per turn).
11. No side swapping: the first argument of `Experiment` always moves first. In an A-vs-B comparison, run both orders yourself.
12. **(uncertain)** The real tournament harness may differ from this jar (rows/columns, timeouts, scoring). The jar is `2.0-SNAPSHOT`.
