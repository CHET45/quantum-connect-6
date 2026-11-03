# Quantum Connect pattern-table bot: design document

Status: draft v1, 2026-11-02. Scope: a Java 25, standard-library-only bot built from alpha-beta search, a gravity-aware threat search, and a learned pattern-table evaluation trained by self-play in our own engine and fitted in PyTorch.

## 0. How to read this document

Citation keys point to the research digests in `sources/digests/` and to the earlier report. Every non-obvious decision carries one.

| Key | File |
|---|---|
| `rapfi` | `rapfi.md` |
| `search` | `search_engineering.md` |
| `selfplay` | `selfplay_learning.md` |
| `mcts` | `mcts_hybrids.md` |
| `nt4` | `ntuple_td_connect4.md` |
| `ntX` | `ntuple_td_other_games.md` |
| `fit` | `pattern_fitting_tuning_nnue.md` |
| `tss` | `threat_space_search.md` |
| `c4vcf` | `connect4_threats_vcf.md` |
| `rep` | `reports/Quantum Connect bot research.md` |
| `bc` | the bytecode findings in section 1 of this document (read directly with `javap`) |

Status tags used on decisions:

- **[D]** decided; implement as written.
- **[H#]** hypothesis; implement behind a switch and test it as described in section 5 (the consolidated list is in section 8.3).
- **[E]** estimate; a number reasoned from digest figures, to be replaced by a measurement.
- **[OWN]** my own derivation from the rules; unverified until the conformance and tactical suites exist.

How the sources were used: the five digests `rapfi`, `search`, `fit`, `tss` and `c4vcf` were read directly in their design, adaptation and open-question sections. `mcts`, `ntX`, `nt4` and `selfplay` were read in full by two delegated readers who returned section-by-section extracts, so numbers quoted from those four are second-hand and should be re-checked against the digest before they are relied on. No web research was done.

## 1. Rules as implemented, and what they imply

### 1.1 Method

All rule answers below were read from the bytecode of `libs/core-2.0-SNAPSHOT.jar` with `javap -c -p -constants` (JDK 21.0.12). Nothing in the jar was executed. The jar's manifest names `ch.cern.quantumconnect.Main`; the engine classes are `core.{Algorithm, Experiment, QuantumField, Move, SpaceOwnership, ExperimentStatus, GameConstants, WinningPosition, QuantumFieldPainter}`. Offsets such as `@203-219` are bytecode offsets inside the named method and can be reproduced with:

```
javap -c -p -constants -cp libs/core-2.0-SNAPSHOT.jar ch.cern.quantumconnect.core.Experiment
```

Two caveats apply to everything in this section. First, the jar is the local emulator. The tournament harness that loads uploaded bots is not in it (no `ClassLoader`, `getResource`, `Bot-Class` or `Class.forName` appears in any engine class), so timeouts, scoring, side assignment and board size in the real tournament are unverified. Second, `Experiment` uses the literals `100L` and `bipush 10` directly; the constants `GameConstants.TIMEOUT_MS` and `Experiment.MOVES_BETWEEN_SPECIAL_MOVES` are not read by the turn loop.

### 1.2 Verified rule table

| # | Question | Answer | Bytecode evidence |
|---|---|---|---|
| R1 | Gravity and orientation | Row 0 is the top, row `rows-1` the bottom. A disc lands in the lowest EMPTY cell of the column. A column is full when `field[0][c]` is non-empty. | `QuantumField.produceCollision` @31-107 scans `r = rows-1` downward for the first EMPTY; `isColumnFull` @0-19 tests row 0 only. |
| R2 | Win | Six consecutive same-owner cells in any of four directions: vertical (1,0), horizontal (0,1), diagonals (1,1) and (1,-1). No exact-length test, so lines longer than 6 contain a 6-line and count. | `hasWinner` @43-80 calls `checkDirection` for the four direction pairs; `checkDirection` @25-124 walks `i = 0..5`, bounds-checks, compares with the start cell, returns the positions when the count is at least 6. |
| R3 | Whose line counts | Colour-blind. `hasWinner` never looks at who owns the line. | `hasWinner` returns on the first non-empty list, no owner test anywhere. |
| R4 | Who wins | Always the player who made the last move. | `Experiment.experimentCollapsed` @0-11: `changeCurrentPlayer()` then `getCurrentPlayer()`. The loop already flipped the player once after the move, so this flips back to the mover. |
| R5 | Does the placed disc vanish in a blast? | Yes. The disc is stored, then the full 3x3 block centred on it is cleared with no skip of the centre. | `produceSpecialCollision` @78-105 stores the owner, @109 calls `destroySurroundingDiscs(r,c)`. In `destroySurroundingDiscs` the loops run `dr = -1..1` (@0-4, @74-77) and `dc = -1..1` (@7-13, @68-71) with no `dr==0 && dc==0` test; it stores EMPTY at @55-67 for every in-bounds cell. Owners are not checked. |
| R6 | Gravity after a blast | Applied to every column, compacting each column fully. | `applyGravity` @0-105 loops all columns, for each EMPTY cell from the bottom finds the nearest non-empty cell above and moves it down. |
| R7 | Blast geometry in "height from bottom" terms | Let `h` be the number of discs in column `c` before the move. Cleared cells have index (from the bottom) in `[h-1, h]` for column `c` (this removes the top disc and the placed disc) and in `[h-1, h+1]` for columns `c-1` and `c+1`, clipped to the board. Columns then compact. On an empty column only the placed disc vanishes and the neighbours lose their bottom two discs. | Derived from R1, R5, R6. |
| R8 | Special move on a full column | Nothing happens: no disc, no history entry, an error line is logged. The cooldown is still consumed and the turn is spent. | `produceSpecialCollision` @146-160 logs "Column is full" and returns; `Experiment` @324-345 resets the counter after the call regardless; @356-387 increments. |
| R9 | Special move on a nearly full column (only the top cell empty) | Works. The disc lands in row 0, the block is clipped at the top edge. | Same loops; `destroySurroundingDiscs` @27-52 clips rows outside `[0, rows)`. |
| R10 | Special requested with an out-of-range column | The engine throws `ArrayIndexOutOfBoundsException`, which is not caught. It leaves `startExperiment`, skips `endExperiment`, and the experiment does not end normally. In paint mode it kills the experiment thread (`lambda$runExperiment$0` has only try/finally). | The animation scan `Experiment` @233-275 calls `QuantumField.isSpaceEmpty(r, col)` for `r = rows-1..0`. `isSpaceEmpty` @0-19 has no bounds check. The scan is not guarded by the paint flag (the flag only guards @278-316). Only `SecurityException` is caught (exception table: @90-110 and @157-178). The branch "Invalid column selected for special collision" at `produceSpecialCollision` @0-52 is therefore dead code in this flow. A special on a full but in-range column is safe (the scan simply finds no empty cell). |
| R11 | Cooldown counting | One counter per player, starts at 0, read once per turn before the call to `useSpecialMove`. A special is honoured iff `useSpecialMove` returned true and `counter >= 10`. A refused request is silently ignored and a normal move is played in the returned column. After an honoured special the counter is reset to 0, and every turn (special, normal, forfeited, invalid) then increments it. | Counter read @134-153; gate @203-219 (`iload 4; ifeq; iload_3; bipush 10; if_icmplt`); reset @324-345 only on the special branch; increment @356-387 on both branches, unconditionally. |
| R12 | Exact off-by-one | The first special is possible on a player's 11th own turn. After a special on own turn `s`, the next is possible on own turn `s+10` (nine ordinary turns between). Before own turn `k` the counter is `k-1` if no special was honoured yet, else `k-s` where `s` is the last honoured special turn. Forfeited turns count. The counter is never capped, so an unused special stays available indefinitely. | R11 plus the post-reset increment: after the special turn the counter is 1. |
| R13 | Is the cooldown consumed when the special was a no-op (full column)? | Yes (see R8). | @324-345 has no condition on the outcome. |
| R14 | Call protocol | Each turn the engine calls `accelerateQuark(field)` and then, always, `useSpecialMove(field)`, with a separate 100 ms budget each, each on a fresh single-thread executor. `useSpecialMove` is called every turn, even if the cooldown is not met and even if `accelerateQuark` failed. Both calls get the same live `QuantumField` object (`this.quantumField`), nothing changes in between. The special is applied in the column returned by `accelerateQuark`. | `lambda$startExperiment$0` and `$1` both read `this.quantumField`. `executeWithTimeout` @0-73 calls `Executors.newSingleThreadExecutor()` per call and `Future.get(100, MILLISECONDS)`. Call sites @90-110 and @157-178. |
| R15 | Timeout or exception in `accelerateQuark` | The turn is forfeited: `col` stays -1, `produceCollision(-1)` logs an error and does nothing. It is not a loss and not a random move. The forfeited turn still counts for `iterations` and for the cooldown. | `col = -1` @88-89; any `Exception` becomes `SecurityException` @46-61; handler @113-129 only logs. |
| R16 | Timeout or exception in `useSpecialMove` | Treated as `false` (the special is dropped, the normal move in `col` is played). | `special = false` @154-155; handler @181-198. |
| R17 | Zombie threads | `shutdownNow()` only interrupts. A bot that ignores interruption keeps running with the live field reference, concurrently with the next call, and (same instance) with its own later calls. | `executeWithTimeout` @36-37 and finally @64-65. |
| R18 | Terminal-check order | The loop condition is evaluated before every turn: status first, then the 1000-iteration cap. `getExperimentStatus` tests a line first (`COLLAPSED`), then `isFieldFull` (`UNCERTAIN`). So a move that wins and fills the board is a win, and a win on turn 1000 still counts. | `startExperiment` @0-26; `getExperimentStatus` @0-25; `endExperiment` @0-24 (`COLLAPSED` else `experimentUncertain`). |
| R19 | Draw | Two ways: the top row is full without a line, or 1000 turns have been played in total (both players, forfeits included). | `isFieldFull` @0-33; `sipush 1000` @23; `iterations.incrementAndGet` @437. |
| R20 | Live field or copy | The bot receives the live engine object. `getField()` returns a deep copy of the row arrays (the enum values are shared). `getMoveHistory()` returns a copy. `clearMoveHistory()` mutates the live list (the engine never reads it). | `getField` @0-42 clones each row; `getMoveHistory` @0-11 copies; `clearMoveHistory` @0-9. |
| R21 | Can the bot read the cooldown or the move number? | No. There is no public accessor. `getCurrentPlayer`, `getExperimentStatus`, `changeCurrentPlayer`, `produceCollision` and `produceSpecialCollision` are `protected` in a `final` class, and the `QuantumField` constructor is package-private. The counters live in `Experiment`, which the bot never receives. The bot must count its own calls. | `javap -p` modifiers; `Experiment` fields `movesSinceLastSpecial{First,Second}Player`. |
| R22 | What the move history contains | Only successful placements, as `Move(isSpecialMove, column, player)`. Forfeits, timeouts and wasted specials leave no entry. The `player` is the owner of the disc at the time. | `produceCollision` @84-101, `produceSpecialCollision` @116-133 (the add is after the blast). |
| R23 | Sides and repeated games | The first constructor argument of `Experiment` always moves first and owns `FIRST_PLAYER`. There is no swapping. `restart()` builds a new `QuantumField`, resets both counters and `iterations`, and reuses the same `Algorithm` instances (so bot instance state survives a restart). | `QuantumField.<init>` sets `currentPlayer = firstPlayer`; `Experiment.restart` @39-112. |
| R24 | Determinism | The engine has no randomness; all variance comes from the bots. | No `Random` reference in `Experiment`, `QuantumField`, `QuantumFieldPainter`. |
| R25 | Board size | A constructor parameter. The 2-argument constructors give 8 rows by 12 columns; the shipped `Main` runs 25x25. | `Experiment.<init>` bipush 8, 12; `Main.main` bipush 25, 25. |
| R26 | Logging cost | The engine logs through slf4j at ERROR for every invalid column and wasted special. The starter packages logback. | `produceCollision` @12-30, @114-128. |

### 1.3 Things the bytecode cannot settle

- **The real harness.** Class loading of the uploaded jar (a `Bot-Class` manifest entry is set in the starter's `build.gradle`), per-game clocks, scoring, board size, concurrency, JVM flags, heap and jar-size limits are outside the engine jar. The starter README adds two statements that differ from the bytecode: "return a column that is not full, otherwise you lose the game" and "a bot that is too slow loses the move". In this engine an invalid column is a forfeited turn, and a slow bot also forfeits the turn. Treat both as potentially fatal anyway [D].
- **Whether a leftover zombie thread has side effects** on the harness (CPU contention, JVM exit delay).
- **Whether `getResourceAsStream` works in the harness** (section 3.7). The standard-library call is valid, and the starter's `jar` task copies `sourceSets.main.output` (classes and resources) into the jar, but the class loader the harness uses is unknown.
- **Whether the constructor is timed**, and how many cores are available.
- **How a leftover timeout interacts with the first-call JIT.** A new thread per call does not reset the JIT (compiled code is shared), only the first calls in a fresh JVM are cold.

### 1.4 Strategic implications

1. **Blast-wins-any-line (R3, R4).** If, after a special move, any 6-line exists, the player who fired it wins, whatever its colour. Simultaneous wins are impossible. A normal move can only add a disc of the mover's own colour, so for normal moves this changes nothing.
2. **A blast can only lower columns.** Heights never rise in a blast (the placed disc vanishes, neighbours lose discs, gravity compacts). Every occupied cell after a blast was occupied before it. A blast therefore cannot fill an empty cell, so it cannot complete a window through a currently empty cell. It creates lines only by sliding discs of the three affected columns down by up to 3 cells until they line up with unaffected discs. Consequence [OWN]: a blast-created line must consist of cells that are occupied before the move, and every window cell outside columns `c-1..c+1` must already hold the winning colour. This gives a sound, cheap prefilter for the blast-win scan (section 2.5).
3. **Both colours are weapons.** When a side's special is available, any near-line of either colour is a potential win for that side, and also a liability for the other: a side with an available special wins by shaking the board into the opponent's line. Own five-of-six windows are therefore liabilities whenever the opponent's special is available.
4. **Blasts break parity and zugzwang plans.** A blast changes heights in up to three columns. Whether it flips which player owns odd rows depends on the parity of `R`, the number of cleared cells including the placed disc (`c4vcf` §3.4, [OWN]). Parity features are valid only while the opponent's special is unavailable for the remaining fill-up, or when the plan's discs are unexposed.
5. **Cooldown tracking is the bot's job (R21).** The own counter and the opponent's counter are both exactly derivable (section 2.8), including the opponent's from turn parity. The only invisible event is an opponent special on a full column (it resets their counter but leaves no history entry). A bot would have to choose a full column on purpose; treat it as impossible but make the opponent's availability estimate conservative (available as early as possible).
6. **No special before the 11th own turn.** The first 20 plies are special-free and can be searched with pure normal-move logic. The earliest blast of the first player is ply 21, of the second player ply 22.
7. **Timeouts are forfeits, not losses, but the special-after-timeout path crashes the engine (R10, R15, R16).** If `accelerateQuark` times out (so `col = -1`) and `useSpecialMove` still returns true in time, with the counter at 10 or more, the engine throws. The bot cannot see whether its first answer arrived in time, so it must make the second call conditional on the first call having finished well inside the budget (section 2.9).
8. **The 1000-turn draw cap (R19) is reachable** only through blasts, because a blast-free game ends by a full top row after at most `rows*columns` turns. The bot knows the exact turn index (own call count) and can steer around a draw at the cap.
9. **Real time is one hundred milliseconds per call, twice per turn (R14), on a cold, fresh thread.** The second budget exists only for the special decision and can be used for a verification search (section 2.9, [H10]).

## 2. Architecture overview

### 2.1 Component diagram

```
 ENGINE (Experiment, per turn)                  BOT (one QcBot instance per game, no static state)
 -----------------------------                  ----------------------------------------------------------
 executeWithTimeout(100 ms) ------------------> accelerateQuark(field)
   fresh thread, cold stack                       t0 = nanoTime()
                                                  Snapshot : field.getField() copy, belongsToMe probe, getMoveHistory()
                                                  TurnTracker.update()  -> TurnState{turn, iAmFirst, cdMe, cdOpp}
                                                  Board.load(snapshot)  -> cells, heights, window codes, threat counts, hash
                                                  Searcher.run(root, deadline)
                                                    |- RootMoves = normal(col) + special(col) if cdMe >= 10
                                                    |- PVS + iterative deepening + TT + ordering
                                                    |- Vcf (gravity threat search) at depth <= 0 and as razor
                                                    |- Eval : window pattern tables + threat features (or hand tables)
                                                    '- Board.make/unmake (normal), copy-make (blast)
                                                  Decision{turnId, hash, col, special, score, finishedAt}
                                                  return col   (final guard: in range and not full)
 executeWithTimeout(100 ms) ------------------> useSpecialMove(field)
                                                  not ready               -> false
                                                  no valid Decision        -> false
                                                  optional verification search of {col normal, col special}
                                                  return Decision.special
```

Two further processes share the same code but are not part of the uploaded jar: the **self-play and match harness** (section 5) and the **training pipeline** (section 4). They link the same `Board`, `Searcher` and `Eval` classes, so the rules the training data was produced under are the rules the bot plays under.

### 2.2 Board and bitboard core [D]

**Representation.** The digests propose three layouts: two-`long` Pons bitboards (`search` §1.2 option A), a byte per column with 96-bit masks (`c4vcf` §3.6) and per-window counters (`c4vcf` §3.6, `tss` §4.5). We choose **per-window base-3 codes over a flat cell array** [D], because the pattern-table evaluation needs exactly those codes, the threat sets fall out of the same transitions, and the structure is size-generic (25x25 has 1,800 windows, 8x12 has 134) without any 128-bit shift code. Bit-parallel shift tricks (`search` §1.3-1.4) stay available as a test oracle, not as production code.

```
Geometry (built once per (rows, cols), cached; rebuilt if the field size changes)
  R rows, C cols, K = 6.  cell index i = col*R + y, y = 0 is the bottom row (engine row = R-1-y).
  windows: for dir in {H (0,1), V (1,0), D1 (1,1), D2 (1,-1)} all start cells whose K cells lie on the board.
           8x12 -> 56 H + 36 V + 21 D1 + 21 D2 = 134 windows.
  CSR tables: for each cell, the list of (window id, digit position) through it  (at most 4*K = 24; 8x12: at most 21)
  POW3[0..K-1], code range 0..728.   digit 0 empty, 1 FIRST_PLAYER, 2 SECOND_PLAYER (absolute colours).

Board (all primitive arrays, no allocation after construction)
  byte  cell[R*C]        0/1/2
  int   height[C]
  short wc[nW]           window code, base-3 digits in window order
  byte  thr[2][R*C]      thr[X][cell] = number of windows w with exactly 5 X, 1 empty, 0 other whose empty cell is `cell`
  int   live3[2]         number of windows with >= 3 X and no O      (cheap global gate)
  long  hash             Zobrist of cells (cooldowns and side to move are mixed in by the node)
  int   discs
Static 729-entry tables:  THR_POS[X][code] (position of the single empty digit if the code is a five-threat for X else -1),
                          LIVE3[X][code], SIX[X][code], SWAP12[code] (exchange digits 1 and 2).
```

**Normal move** (`make`): set the cell, `height[col]++`, then for each window through the cell compute `new = old + colour*POW3[pos]`; for both colours update `thr` and `live3` from the table transitions `THR_POS[X][old] -> THR_POS[X][new]`; return `true` if `new == SIX[colour]`. `unmake` is the exact inverse. Cost about 100-150 operations after warm-up [E].

**Special move (blast)** is applied to a copy: `child = pool[ply].copyFrom(parent)` (about 700 bytes at 8x12, 5.5 KB at 25x25), then per R5-R7:

```
h = height[c]
place nothing (the placed disc and the cleared band cancel); clear index range [h-1, h] in column c (if h >= 1 the top disc, else nothing)
for n in {c-1, c+1} inside the board: clear index range [h-1, h+1] clipped to [0, height[n])  -> k_n cleared discs
for each of the up to three columns: compact (discs above the cleared band shift down by k_n), height[n] -= k_n
recompute the codes of all windows touching columns c-1..c+1; update thr/live3 by table transitions; recompute the hash for the changed cells
```

Copy-make for blasts keeps the in-place `make/unmake` fast path clean (`search` §1.5, `c4vcf` §3.6): the recursion receives a `Board` reference, and a blast child simply continues on a different `Board` object.

**Hash.** 64-bit Zobrist: `H = XOR(Z[colour][cell]) ^ ZSTM[stm] ^ ZCD[first][min(cdFirst,10)] ^ ZCD[second][min(cdSecond,10)]`, plus `ZTURN[bucket]` only when the remaining turns to the 1000 cap are fewer than the maximum search depth [D] (`search` §2.3, `rapfi` §7.4). The side to move must be hashed, because with blasts it can no longer be inferred from the disc count. A fixed seed makes runs reproducible.

### 2.3 Move representation and generation [D]

`int move = col | (special << 8)`, `NONE = -1`. Generation per node, in this order (`search` §2.4, adapted to R2-R12):

1. **Forced-reply logic first.** Let `P` be the set of distinct *playable* threat cells of the opponent (`thr[opp][col*R+height[col]] > 0`). If `|P| >= 2` and the side to move has no available special: the position is lost (return a mate-in-2 loss). If `|P| == 1`: the only candidate normal move is the block; blasts remain candidates if the special is available (R5 gives a universal defence, `tss` §4.4).
2. **Pons filter**: omit normal moves into a column `c` whose next cell `col*R+height[c]+1` is an opponent threat cell (that hands the opponent the win); keep it only if every move loses [D] (`search` §3.1).
3. All remaining normal moves (non-full columns).
4. If `cdMover >= 10`: blast moves for every non-full column, which are ordered last unless tactical (section 2.5).

Special on an empty or short column is a legal tempo move with a cooldown cost; keep it in the move set (it is the only way to pass), never forbid it [OWN].

### 2.4 Win and blast detection [D]

- **Normal win**: the flag returned by `make`; at node entry also the pre-check "some playable cell of mine has `thr[me] > 0`".
- **Blast win**: after applying the blast, check only the windows touching columns `c-1..c+1` for `SIX[1]` or `SIX[2]` (all other windows were non-six before, per the engine's loop invariant). Either colour wins for the mover (R3, R4).
- **Valley prefilter (sound)** [OWN, derived from R7 and the observation that blasts only lower columns]: a blast in column `c` can create a line only if discs fall, which requires a neighbour `n` with `height[n] >= height[c] + 3`. If no column satisfies this, no blast can win, for either side. This reduces the blast-win scan from 12 simulations per node to the few "valley" columns. On boards with `R >= 9` also test vertical lines in a neighbour column (a fall of 3 cells can close a 6-stack once `height >= 9`); a generic `rows >= K + 3` branch covers it.
- **Gate**: `live3[1] + live3[2] == 0` skips the scan before the valley test.
- **Differential test**: the prefilter and the whole blast logic are tested against a naive array implementation of R5-R7 on millions of random positions (section 5).

### 2.5 Threat search (gravity VCF) [D structure, caps are H]

Adopts the Rapfi two-function scheme (`rapfi` §5.3: attacker node with stand-pat, defender node with a single reply) with the gravity translation from `c4vcf` §3.6, `tss` §4.5 and `search` §3.3:

- **Forcing unit**: a move after which the attacker has at least one *playable* winning cell. Latent threats are not forcing; they feed the evaluation.
- **Attacker node**: immediate win? blast win? opponent has a playable threat (then the attacker is not free and must answer)? stand pat on the static evaluation; try only forcing moves (at most `C` candidates, each checked by `thr` transitions: a move is forcing iff after it `thr[me]` has a playable cell, which includes the cell it unlocks directly above).
- **Defender node**: replies = block the single playable cell, or an own immediate win, or (if the defender's special is ready) every blast. The defender node is exact only because replies are enumerated: a double playable threat or a stacked pair (a playable threat with another own threat directly above, `c4vcf` §3.3) has no normal reply.
- **Defender blasts**: when the defender's special is ready, each non-full column is simulated (about 1-2 microseconds per blast, filtered by "does this blast change any window of the threat?" only if profiling demands it). Because the blast at the threat cell's column always removes at least one disc of the threatened window (`tss` §4.4), single threats are usually refuted while the defender's special is ready; **the VCF is a weapon mainly while the defender's special is on cooldown for the whole line**, or when it finds a double threat no single blast removes. The cooldown counters are part of the VCF transposition key.
- **Where it is called**: at `depth <= 0` of the main search in place of quiescence (`rapfi` §5.2 step 1, about 17 Elo there), as razoring when `eval + margin < alpha` and `depth < 2.6` (`rapfi` §5.2 step 7, about 68 Elo there), and once at the root for a deep own VCF (iterative, attacker moves 1, 2, ... up to 12). The root also runs the "null-move VCF for the opponent" check from `tss` §4.5: if the opponent wins by VCF when we pass, restrict our moves to those that stop it.
- **Caps** (from `c4vcf` §3.6, all [E] until measured): root own VCF 12 attacker moves, 20k nodes, at most 10 ms; root opponent check 8 moves, 8k nodes, 5 ms; leaf dive 3 attacker moves, 100-300 nodes; razor 3 moves, 300 nodes. Check `System.nanoTime()` every 256 VCF nodes; expiry returns UNKNOWN, never "win".
- **Own TT** (`long[]` keys, `int[]` data, 2^18 entries) storing (result, remaining depth, cooldown bits). A proven win is valid for any depth at least the stored one.
- **Validity switch [H1]**: mode "tentative" (ignore defender blasts, return a large non-mate score) at leaves, exact mode at the root. Test both with SPRT; the exact mode costs up to 12 blast simulations per defender node.

### 2.6 Alpha-beta search [D skeleton, each selectivity item gated]

Negamax, fail-soft, PVS with a boolean `pv` argument, iterative deepening with a per-iteration abort. Depth is an `int` in **half-plies** (one ply = 2 units) so that threat-creating moves can be extended by half a ply (`search` §2.5, `rapfi` §5.1 uses a float depth for the same reason).

| Item | Decision | Source | Gate |
|---|---|---|---|
| Score scale | `MATE = 30000`, mate in n plies = `MATE - ply`; static evaluation clamped to +-10000; unit = 1/256 logit (section 3.5) | `rapfi` §2.2, `search` §2.1 | fixed |
| Terminal and mate-distance pruning | enabled; no Pons score clamp (the empty-cell count does not bound the game with blasts) | `search` §2.1 | fixed |
| Draw by turn cap | `turnIndex + ply >= 1000` returns 0; the turn index is exact (section 2.7) | R19 | fixed |
| TT | 2 `long[]` arrays, 16 bytes per entry, 2^21 entries default (32 MB), adaptive to `Runtime.maxMemory()` (cap 2^22, floor 2^18); entry = full 64-bit key XOR-verified (lockless-safe against zombie writes) with score16 bound2 depth8 move9 gen8 eval16; two-entry bucket, replace lowest `depth - age`; mate scores stored relative to the node; kept across calls and across games | `search` §2.3, `rapfi` §5.7 | fixed |
| Ordering | win-in-1, blast win, forced blocks, TT/PV move, moves creating a stacked or double playable threat, 2 killers, history with gravity update, threat-count after the move, centre-first ties; blasts last unless tactical | `search` §2.4 | fixed |
| Check-like extension | the forced reply to a playable threat costs zero depth; a move creating a playable threat extends half a ply | `rapfi` §5.2 step 13, `search` §2.5 | half-ply extension is [H8] |
| LMR | gentle: `r = floor(0.5 + ln(d)*ln(m)/2.4)`, moves 4 and later, never for TT move, killers, forced blocks, threat-creating moves, or blasts at PV nodes | `search` §2.5 | [H9], default off at M2 |
| Aspiration | window of +-64 units (0.25 logit) around the previous score from iteration 5, widen the failing side exponentially | `rapfi` §5.1 | [H9] |
| Null-move pruning | **not used** (zugzwang is the point of gravity games; Rapfi measured only about 3 Elo from it even without zugzwang) | `search` §2.5, `rapfi` §5.2 step 9 | fixed off |
| Futility, razoring to static value | off at first; razoring *into the threat search* is the safe variant | `search` §2.5, `rapfi` §5.2 steps 7-8 | [H9] |
| Singular extension, ProbCut, IIR | later, each by SPRT; ProbCut needs fitted `a, b, sigma` per stage | `search` §2.5, `fit` §1.6 | M7 |
| Even-depth-only iterations | measure, do not assume | `search` §2.2 | [H9] |
| Search threads | one; Lazy SMP only if the tournament gives extra cores (unknown) | `rapfi` §5.1 | M7 |
| Root | a root move is a `(col, special)` pair; the best pair's column is returned by the first call, its special flag is cached | `search` §3.4, §4.4 | fixed |

Move ordering of blasts deserves one more rule: a blast in a non-valley column cannot create a line (2.4) and cannot be an immediate win. It matters as a defence (it can lower the stack that supports a playable opponent threat, R7), as a parity or tempo move, or as a way to reset an opponent's near-line. Generate it at all nodes of depth at least 3 plies, order it last, and apply LMR to it; at depth 2 plies or less generate it only if it is a candidate defence [H7].

### 2.7 TurnTracker: counting turns and cooldowns [D]

The bot is called exactly once per own turn (R14), so its own call counter is the exact own turn index; the engine's iteration counter is then `2*(k-1) + (iAmFirst ? 0 : 1)` before own turn `k`. State:

```
myCalls k                    incremented at the start of accelerateQuark; reset when the board is empty AND the history is empty AND k > 0
iAmFirst                     taken from the board at the first non-empty observation: pick any occupied cell,
                             myColour = belongsToMe(r,c) ? field[r][c] : other(field[r][c]);  empty board at call 1 => assume first
lastMySpecialTurn   s_me     own turn index of the last HONOURED special, -1 if none
lastOppSpecialTurn  s_opp    opponent turn index of the last observed special, -1 if none
histSeen                     history size already scanned
```

Per call, before searching, scan the new history entries: an entry by the opponent with `isSpecialMove` sets `s_opp = oppTurnsDone` (the opponent made exactly one turn since our last call); an entry by us with `isSpecialMove` confirms `s_me = k-1`. If we returned `true` from `useSpecialMove` on the previous turn but our newest own entry is a normal move, the special was dropped (timeout) and `s_me` stays as it was. If `getMoveHistory()` is shorter than `histSeen` (someone called `clearMoveHistory`), fall back to the local belief ("we requested, the answer was delivered inside the deadline, the column was valid"). Then:

```
cdMe        = (s_me  < 0) ? k - 1 : k - s_me                       // engine counter before our turn k; special possible iff >= 10
oppDone     = iAmFirst ? k - 1 : k                                 // opponent turns completed before ours
cdOppNext   = (s_opp < 0) ? oppDone : (oppDone + 1) - s_opp        // counter before their next turn; ready iff >= 10
```

Inside the search both counters advance by one per own/opponent ply (a blast resets the mover's counter to 1 after the move, a normal move increments it). The opponent's estimate is deliberately pessimistic: a wasted opponent special (full column) is invisible and would only make the opponent's special *less* ready than we assume. Everything is a field of the `QcBot` instance and nothing is static (R23: the same instance may be reused after `restart()`).

### 2.8 Time manager [D structure, numbers E]

There is no time bank (every call has its own 100 ms), so the aim is to use the budget without ever exceeding it. The 100 ms starts before the engine's thread runs our first statement (executor creation, submit, scheduling), so we measure from method entry and reserve the rest.

| Quantity | Value | Reason |
|---|---|---|
| Hard stop, first call of the turn | 62 ms from entry [E] | 38 ms reserve for thread start, `getField()` copy, safepoints and GC; tune from the measured p99.9 of harness-observed latency (section 5.4) |
| Hard stop, first call of a fresh JVM | 40 ms | cold code |
| Soft stop | do not start iteration `d+1` when `elapsed > hard / EBF`, EBF measured, initial 3 | `search` §4.2 |
| Clock polling | every 1024 nodes in the main search, every 256 in the VCF, plus `Thread.currentThread().isInterrupted()` | `search` §4.2; interruption is the engine's `shutdownNow` (R17) |
| Abort | a preallocated exception without stack trace thrown from the polling site; the search state lives in per-ply arrays so nothing needs unwinding | `search` §4.3 |
| Early exit | a proven win or loss at the root stops after one further iteration; with a single legal non-losing root move return at once | `rapfi` §5.1, `search` §4.2 |
| Hot path | no allocation, no logging, no boxing, no lambdas, no collections; allocations (TT, per-ply stacks, move lists) happen in the constructor | `search` §4.5 |

A zombie search (one that was timed out but still runs, R17) must not corrupt the next call: the per-call search context (per-ply stacks, killers, history deltas) is allocated per call, the TT entries are XOR-verified, and the context carries a token that a new call invalidates; every poll checks the token and aborts a stale search.

### 2.9 Two-call protocol: accelerateQuark then useSpecialMove [D]

```
accelerateQuark(field):
    t0 = nanoTime(); turnId = ++calls
    try {
       snap  = Snapshot.read(field)              // getField() copy once; own colour via belongsToMe; history copy
       state = tracker.update(snap)
       board.load(snap); result = searcher.run(board, state, t0 + HARD)
       col = result.bestColumn
       decision = new Decision(turnId, board.hash(state), col, result.bestIsSpecial && inRangeAndNotFull(col),
                               result.score, finishedWithin = (nanoTime()-t0) <= SAFE_FINISH /* 85 ms */)
       publish(decision)                         // single volatile write
       return col
    } catch (Throwable) { return safeColumn(field) }   // centre-first non-full column using isColumnFull

useSpecialMove(field):
    if (!tracker.ownSpecialReady()) return false         // cheap path; called every turn
    d = latestDecision;  if (d == null || !d.special || !d.finishedWithin) return false
    if (hash(field) != d.hash || d.turnId != calls) return false   // stale or from a zombie
    if (verification enabled && time allows) d.special = verify(field, d)  // [H10]
    return d.special                                      // column is known valid; col is what the engine will use
```

Rules this enforces:

- The special flag is never raised unless the column returned by the first call is valid and non-full (R10).
- The special flag is never raised if the first call finished later than 85 ms of its 100 ms (it may have timed out without us knowing, and then `col = -1` would crash the engine, R10, R15).
- A stale `Decision` (zombie thread, restart, engine desync) is detected by `(turnId, hash)`.
- The second call is only a verification, never a source of truth: if the cache is missing it returns `false`. Cross-call state is an accelerator, not a dependency (`search` §4.4 point 4).
- **Verification search [H10]**: in the second call the column is already committed, so the TT is warm; spend up to 40 ms searching only `{(col, normal), (col, special)}` one to two plies deeper and return whichever is better. Fairness: the rules allow it (the second call has its own budget, R14), but the contest organisers have not been asked [open question 8.2].

### 2.10 Failure containment [D]

- Any `Throwable` in either call returns the safe fallback (`safeColumn`, or `false`). The engine would also swallow it, but the turn would be forfeited (R15).
- `safeColumn` scans columns centre-first with `field.isColumnFull(c)` and never returns a full column; with all columns full the game is already over.
- Out-of-memory at TT allocation falls back to a smaller table; failing to decode the weight blob falls back to the built-in hand tables (section 3.8); failing geometry setup falls back to the centre-first rule.
- The bot never writes to the console, never calls `observeQuantumField()`, and never calls `clearMoveHistory()`.

## 3. Evaluation design

### 3.1 Form [D]

The evaluation is a **sum of integer table lookups** from the side to move's view (the family with the best documented return: Logistello-2/GLEM `fit` §1.3-1.4, Rapfi's classical tables `rapfi` §2, n-tuple TD `nt4` §1). There is no multiplication and no link function at inference (the logistic link exists only in training, `fit` §1.4 and `fit` §5.1):

```
E(position, stm) = sum over 134 windows w   TW[ctx][cls(w)][ code_rel(w) ]            family W  (window patterns)
                 + TT1[ctx][t1] + TT2[ctx][t2] + TT3[t3]                                family T  (gravity, parity, cooldown)
```

`ctx = (stage, cdCtx)` is the context pair of section 3.3; `code_rel` is the window code seen from the side to move (digits 1 = mover, 2 = opponent). All terms are `short`, the sum is an `int`, the result is clamped to +-10000 and used in units of 1/256 logit (section 3.5).

### 3.2 Family W: window patterns [D]

- **Windows**: all 6-cell lines in four directions, 134 on 8x12 (56 H, 36 V, 21 D1, 21 D2). A 6-window is the natural unit for six-in-a-row (`rep`; `fit` §5.1; `ntX` §3.3). Lines of 7 or 8 cells (`3^7 = 2187`, `3^8 = 6561` states) are the first extension to test [H6].
- **Cell states**: three (empty, mover, opponent), `3^6 = 729` codes per window. **[H2]**: a fourth state "empty and playable now" (`4^6 = 4096` codes, 5.6x more parameters) injects the gravity information that plain ternary windows lack; Thill's Connect-4 n-tuples used exactly a 4-valued code and report it as the standard (`nt4` §1.2), but no ablation of P=3 versus P=4 exists (`nt4` §7 question 4), and `rapfi` §7.3 recommends keeping playability out of the line code and handling it per cell. v1 therefore uses 3 states and puts playability into family T; H2 is the first learned-model experiment after v1.
- **Mirror sharing**: the only valid symmetry is the left-right mirror (gravity breaks every rotation, `rapfi` §7.1 point 5; `ntX` §3.1). On 8x12 the 134 windows fall into **71 classes** (32 horizontal: 8 rows x 4 start pairs `{0,6},{1,5},{2,4},{3}`; 18 vertical: 6 column pairs x 3 start rows; 21 diagonal: mirror maps D1 onto D2). Digit order is chosen per window so that mirrored windows read the same relative cell at each digit position; self-mirroring windows (horizontal start 3) use `canon(code) = min(code, reverse(code))`. The class and the canonical-code table come from a `FeatureSpec` that Java writes once; Python only reads it.
- **Row identity is kept** for horizontal and diagonal windows, because on a gravity board the row matters (parity, support). Coarser sharing (by row band) is the alternative [H3], decided by held-out loss.
- **Vertical windows** are degenerate (a column is a contiguous stack, so only 127 of the 729 codes are reachable) but cost nothing and are kept for uniformity.
- **Why no Rapfi pattern classes**: Rapfi first classifies lines into 16 patterns and then combines the four directions (`rapfi` §1.3-1.4). With only 3^6 raw states per window and a joint regression, the raw window code already contains the same information, and the learned table avoids hand-designed classes (`fit` §1.3: joint fitting of overlapping patterns was the decisive improvement over independent estimates).

### 3.3 Contexts: stage, cooldown, parity [D with variants as hypotheses]

| Context | Definition | v1 | Variants |
|---|---|---|---|
| `stage` | `min(3, 4*discs / (R*C))`, i.e. 0-23, 24-47, 48-71, 72+ discs on 8x12. Not monotone after a blast, which is fine because it is only a feature | 4 stages | [H4]: 3, 6 or 8 stages; blend two adjacent stage tables linearly by disc count at inference (untested in every source: `ntX` §3.4, `fit` §5.1) |
| `cdCtx` | `moverReady | (otherReady << 1)`, where ready means the engine counter is at least 10 at that node | 4 contexts | [H5]: three buckets per side (ready, 7-9, below 7) giving 9 contexts |
| parity | the bit `discs mod 2` is carried only inside family T (section 3.4) | in T | [H3]: a second table set selected by the bit, if T does not capture enough |

Contexts multiply parameters (`ntX` §3.5: 4 contexts x 4 stages x 71 classes x 729 = 828,144 weights, 1.7 MB as `int16`). To avoid starving rare contexts, **train a factorised model and export folded tables** (the factoriser idea of `fit` §3.2, the promotion and redundant-encoding results of `ntX` §1.4-1.5):

```
training params:  Wbase[cls][code]  +  Wstage[stage][cls][code]  +  Wcd[cdCtx][cls][code]
                  (L2 on the two delta tables toward zero; base lightly regularised)
export:           TW[stage][cdCtx][cls][code] = Wbase + Wstage + Wcd     (one table lookup at runtime)
```

Parameter counts: training 71 x 729 x (1 + 4 + 4) = 465,831 (51,759 per component); exported 4 x 4 x 51,759 = 828,144. At load, the class-level tables are expanded into per-window tables (`134 x 729` per context, 3.1 MB in memory for 16 contexts [E]) so the runtime lookup is a single `short` read with no class indirection.

### 3.4 Family T: gravity, parity and cooldown features [D structure, details H]

These cannot be read from a window: they depend on which cells are playable and on parity. All are computed from `thr[X][cell]`, `height[]` and the node's cooldown counters (section 2.2), without scanning windows. `me` is the side to move. `d(cell)` is the number of empty cells below a threat cell in its column.

| Quantity (per side X in {me, opp}) | Definition | Buckets |
|---|---|---|
| `imm[X]` | distinct playable threat cells of X (`d = 0`) | 0, 1, 2+ |
| `one[X]` | threat cells of X with `d = 1` (class II: the cell below is the key, `c4vcf` §3.2) | 0, 1, 2+ |
| `stk[X]` | a column where X has threat cells at two vertically adjacent cells (`c4vcf` §3.3) | 0 none, 1 if the lower cell has `d >= 2`, 2 if `d <= 1` |
| `nat[X]`, `unn[X]` | number of columns whose lowest latent threat (`d >= 2`) of X is natural (X owns that row's parity) or unnatural | 0, 1, 2+ each |

Natural ownership uses the waiting-game rule from `c4vcf` §3.1, generalised [OWN, to be verified by exhaustive search on reduced boards]: with `E` empty cells and 1-based row `rho`, the side to move gets the cell iff `E + R + rho` is odd, assuming no blast and that both sides avoid the column. The opponent gets it iff the sum is even. A blast of odd cleared-cell count `R` flips the ownership (`c4vcf` §3.4), which is why `cdCtx` multiplies the T tables.

Three tables (all additive, so the model stays a plain linear sum):

```
t1 = (imm[me], imm[opp], one[me], one[opp])                          3^4 = 81     x cdCtx(4) = 324 entries
t2 = (stk[me], stk[opp], nat[me], unn[me], nat[opp], unn[opp])        3^6 = 729    x cdCtx(4) = 2,916 entries
t3 = (min(cdMeCounter,10), min(cdOppCounter,10), valleyBucket)        11*11*3      = 363 entries
```

`valleyBucket` = number (0, 1, 2+) of columns where a neighbour is at least 3 cells taller, i.e. where a blast could make discs fall (section 2.4). The stage is deliberately absent from T (it is thin data); the evaluation sees stage through W. Tuning of the bucket edges is part of [H3].

### 3.5 Scale, quantisation and overflow [D]

- **Training scale**: the model output `q` is a logit, `P(mover wins) = sigmoid(q)`. Draw is 0.5.
- **Export scale**: `qInt = round(256 * w)`, one `short` per weight. The engine's score unit is therefore 1/256 logit; 64 units = 0.25 logit (the aspiration width, section 2.6).
- **Clamps in training**: each component weight in [-8, 8] logits, so a folded weight is in [-24, 24] = [-6144, 6144] units, far inside `int16`. The sum of 137 terms is at most about 840k, which fits `int`; the result is clamped to +-10000 so it can never be mistaken for a mate score (`MATE = 30000`).
- **Rounding error**: worst case 137 x 0.5 = 68.5 units; typical error a few units. The Java-Python parity test (section 4.6) compares the *integer* sum exactly and the float sum within 70 units.
- **Why not int8 or fixed shifts**: tables are tiny; `int16` removes the whole class of INT8 value-head degradation that Oracle observed (`selfplay` §1.14 OR5, §4.6 pitfall 8).

### 3.6 Table sizes at a glance

| Item | Count | Bytes as `int16` |
|---|---|---|
| Class-level folded W tables, 4 stages x 4 cd | 828,144 | 1.66 MB raw; about 0.8-1.2 MB deflated [E] |
| Family T | 3,603 | 7 KB |
| In-memory expanded per-window tables | 134 x 729 x 16 | 3.1 MB |
| W for the [H2] 4-state variant | 4,096 per class | 5.6x |
| W for [H6] 7-cell lines (96 windows, about 50 mirror classes, 2187 codes) | about 110k per context | about 6 MB for 16 contexts |
| Generic spec (any other board size, section 3.10) | 12 classes x 729 x 16 | 280 KB |

### 3.7 Delivering the weights inside the jar [D]

**Blob format** (`.qcw`, big-endian, deflated as a whole): magic `QCW1`, version, `rows`, `cols`, `K`, `specHash` (64-bit hash of the FeatureSpec: window cell lists, class ids, mirror flags, canonical-code table, bucket edges), `scale` (256), `nStages`, `nCd`, `nClasses`, then the `int16` sections in the order W (stage-major, cd, class, code), T1, T2, T3. The loader rejects a blob whose `specHash` or board size does not match the running geometry and falls back to the hand tables.

**Embedding.** A generator (`EmbedWeights`, a tool in the repository) turns the blob into a Java class:

```java
final class EvalBlob {
  static final String[] CHUNKS = { "...60000 base64 characters...", "...", ... };   // each literal <= 65,535 bytes of UTF-8
  static byte[] load() { /* join, Base64.getDecoder().decode, Inflater */ }
}
```

Limits that make this work: a `CONSTANT_Utf8` entry holds up to 65,535 bytes, Base64 is ASCII (1 byte per character) so 60,000 characters per chunk is safe; a 1.2 MB deflated blob becomes about 1.6 MB of Base64 in about 27 chunks; the `<clinit>` that fills the array is small (about 8 bytes of bytecode per chunk, far from the 64 KB method limit that rules out a `static final short[]` initialiser, `selfplay` §5.6). The approach is pure standard library and cannot fail to be present, because it is inside the class file.

**`getResourceAsStream` check.** `QcBot.class.getResourceAsStream("/ch/qc/bot/eval.qcw")` is plain standard library and works under the normal application and `URLClassLoader` class loaders; the starter's `jar` task includes `sourceSets.main.output`, which contains `src/main/resources`, so the file would be at the jar root path. What cannot be verified from the engine jar is the harness's class loader (it is not in the jar, section 1.3), and a jar-size limit is unknown. Therefore: **the release path uses the compiled-in `EvalBlob`**; the resource path and a system property `qc.weights=<file>` are development conveniences for the harnesses. Loading order: property file, compiled-in blob, resource, built-in hand tables. A probe test in M0 (build a jar, load it through a `URLClassLoader` in a fresh JVM, and read both the resource and the blob) documents the behaviour on our side; the real harness cannot be probed from here [open question 8.2].

### 3.8 Incremental versus full recomputation [D v1, H11 later]

- **v1 evaluates a leaf from scratch**: 134 window reads plus the T lookups (about 140 table reads, a few hundred nanoseconds after warm-up [E]). The window codes themselves are already maintained incrementally by `make/unmake`, and after a blast the board recomputes the codes of the windows touching the three changed columns (section 2.2). The evaluator therefore needs **no special case for blasts**: it always reads the current codes.
- **Perspective**: the board stores absolute colours; the evaluator reads `code_rel = (stm == FIRST) ? code : SWAP12[code]` (one extra 729-entry read per window). Two code arrays (one per colour view) are not worth the doubled update cost.
- **Running sums [H11]**: if profiling shows the evaluation above 30 percent of node time, maintain `S[first]` and `S[second]` (the sums seen by each colour as mover) with delta updates on the touched windows, and recompute both when `ctx` changes or after a blast. This is the NNUE accumulator idea in its trivial form (`fit` §3.4, `ntX` §3.7); it matters mostly on large boards, where 1,800 windows make a full sum about 13 times more expensive.

### 3.9 Hand-written fallback and bootstrap evaluation [D]

The first self-play generation needs an evaluation that is not learned, and the bot needs one if the weights cannot be loaded. It is built as a particular setting of the same tables, so the code path is identical (`fit` §5.2 bootstrap; `rapfi` §7.8 step 1):

```
window (a = mover discs, b = opponent discs, other digits empty):
   a>0 and b>0 : 0                       (dead window)
   b == 0      : +VAL[a]                 VAL = {0, 3, 13, 50, 150, 384}   units  (1, 2, 3, 4, 5 discs, live)
   a == 0      : -VAL[b] * 0.9           (the mover's tempo is worth about 10 percent)
T family (units):  imm[opp] >= 2 : -3000      imm[me] is handled by search, not by the evaluation
                   one[me] : +60 each        one[opp] : -60 each
                   stk[me] = 2 : +3000       stk[me] = 1 : +200      symmetric for opp
                   nat[X] : +150 each        unn[X] : +30 each       multiplied by 0.4 when the opponent special is ready
                   cd bias : +40 if my special is ready, -40 if the opponent's is ready (+/-15 when 7-9)
```

All values are initial guesses to be tuned by SPSA on a handful of scalars (`fit` §2.2, `search` §5.2); the only requirement is a sensible ordering so that the first search-labelled data is better than random. The generator `HandTables.build(rows, cols)` works for any geometry, so it is also the evaluation for every board size without trained tables.

### 3.10 Board sizes other than 8x12 [D with H12]

- Geometry, window lists, the board, search and the VCF are generic (section 2.2). The built-in hand tables work on any size.
- Learned tables are specific to a `FeatureSpec`. For other sizes ship a **generic spec**: 12 classes = {H, V, D (mirror merged)} x `min(yLowest, 3)`, not tied to a column, plus the same family T and contexts. It is trained on a size mixture (8x12 primary; 6x7, 9x9, 10x14, 25x25 secondary, balanced by `EqualBoard`-style weights as in `rapfi` §2.5) [H12]. Until it exists, other sizes play with hand tables.
- Parity and `nat/unn` use `R` and `E` as written in 3.4, so they hold for any height.
- Cost: at 25x25 a full evaluation reads 1,800 windows; plan for a lower search depth there and enable [H11].

## 4. Training pipeline

### 4.1 Overview and the decision against pure TD [D]

```
  Java (CPU, N threads)                 files                         Python/PyTorch (remote PC, 8 GB GPU)          Java
  ---------------------                 -----                         -------------------------------------         ----
  SelfPlayMain(engine_g) --> gen_g/shard-*.qcp + manifest.json --> data.py -> fit.py (embedding-sum, BCE)  -->  export.py
        ^                                                                                                          |
        |                                                                  eval-g+1.qcw  <-------------------------+
        '--- EmbedWeights -> EvalBlob.java -> engine_g+1  --> MatchMain(engine_g+1 vs engine_g, SPRT) --> accept / reject
```

The sources offer two ways to get a first learned table: TD-FARL with TCL (an n-tuple net bootstrapped by 1-ply self-play on a CPU, `nt4` §2.2, `selfplay` §5.3) and expert iteration with search-value labels (Logistello, Texel, Rapfi: `fit` §5, `rapfi` §7.8). **Primary path: expert iteration from the hand tables, search-value labels, GPU regression** [D]. Reasons: (1) our deployed evaluator is searched, and labels from the same search are on the distribution the evaluator will face, whereas a 1-ply TD value is "the value if both sides keep playing the 1-ply policy" (`nt4` §6.1) and is untested as a leaf evaluator (`nt4` §7 question 6); (2) it needs no TD learner in Java, which `selfplay` §5.3 estimates at 2-3 days plus tuning; (3) Texel gained the most (+39.4 of +99.6 Elo) by keeping noisy positions rather than filtering them (`fit` §2.1). **Fallback [H13]**: if generation 1 fails its gate, port TD-FARL (`nt4` §6.6 first experiment) as the bootstrap. TD-Leaf and TreeStrap are optional fine-tuning (4.8).

### 4.2 Data generation in Java [D, numbers E]

**Engine.** The harness links the production `Board`, `Searcher` and `Eval` in a fixed-node mode (`Searcher.run(nodeLimit)`), so a game is deterministic given its seed (single thread per game, TT cleared per game). One game per worker thread; `workers = cores - 1`.

**Opening diversity** (the engine is deterministic and has no randomness, so all of it must be injected, `selfplay` §4.1; `rapfi` §7.8 step 2):

- Random opening: 2-8 uniformly random normal plies (`U{2..8}`).
- **Mid-game starts with controlled cooldowns**: in 25 percent of games, first play 20-60 random plies; each ply is a uniform random non-full column, except that when the mover's special is ready it is chosen with probability 0.15 (a uniformly random non-full column). Accept the start only if the position is non-terminal and `|search value| < 600` units (about 2.3 logits), so the game is still informative. This gives positions with every cooldown state without any special logic (the counters follow from the turns played) and covers blast structures that engine play alone would rarely produce.
- **Forced special-move exploration**: during engine play, if the mover's special is ready and a blast is among the root moves within 128 units of the best, play it with probability 0.3 (`selfplay` §4.1 inference: the destroy move is a sparse, high-impact action; `nt4` §6.3 suggests a prior for the same reason). Labels are search values, so choosing a non-best move does not corrupt them.
- **Move choice**: sample among root moves within `delta = 48` units of the best with a softmax of temperature 48 units for plies 0-15 and 8 units afterwards; with probability 0.03 per move play a uniformly random non-immediately-losing move. Mark positions reached after a random move.
- **Games per generation**: 60 percent engine_g against itself, 20 percent against engine_{g-1}, 20 percent against the hand-table anchor (a mixed pool avoids cycling and forgetting; `selfplay` §6 question 9; KnightCap's self-play-only run scored 11 percent against the version trained on diverse opposition, `selfplay` §1.15). Only positions where engine_g is the mover are labelled by it and recorded in games against other engines.
- **Length cap**: stop at 400 turns and record result 0 with a flag (draw adjudication, [H13b]); natural terminal states end earlier.

**What is recorded**: for every ply except the first four random ones and plies with a proven result within 3 plies: the position, the root search value `v` of the fixed-node search, the completed depth, a proven win or loss and its distance, and, once the game ends, the game result and plies to the end. For every root, **also the PV successor position labelled with the negated value** (Buro's cure for the side-to-move bias of positions sampled from search trees: `fit` §1.4 and §5.2 point 5).

**Record format `.qcp`** (little-endian, 48 bytes per record for 8x12; the file header carries `rows`, `cols`, `K`, record size, `specHash`, generation and the engine jar hash):

| Offset | Size | Field |
|---|---|---|
| 0 | 24 | cells, 2 bits each, cell `i = col*8 + y`, byte `i/4`, bits `2*(i%4)`; 0 empty, 1 FIRST_PLAYER, 2 SECOND_PLAYER (absolute) |
| 24 | 1 | `stm`: 0 first player to move, 1 second |
| 25 | 1 | `cdMover`: mover's engine counter before the move, clamped to 10 |
| 26 | 1 | `cdOther`: the other player's counter before its next turn, clamped to 10 |
| 27 | 1 | `stage` (0-3) |
| 28 | 1 | `cdCtx` (0-3) |
| 29 | 1 | parity `discs mod 2` |
| 30 | 2 | `turn`: iterations played before this move |
| 32 | 2 | `value` (i16): search score, mover's view, units; proven results store `+-(30000 - plies)` |
| 34 | 1 | completed depth |
| 35 | 1 | proven: 0 none, +1 mover wins, -1 mover loses |
| 36 | 2 | plies to the end of the game (0xFFFF if capped) |
| 38 | 1 | result `z` from the mover's view, -1/0/+1 |
| 39 | 1 | flags: bit0 after a random move, bit1 inside the random opening, bit2 PV successor record, bit3 adjudicated draw |
| 40 | 8 | `tf[4]` (u16): family-T table indices `t1, t2, t3` and a spare, computed by Java |

The context bytes and the T-family indices are written by Java so that the *single source of truth* for every feature that involves game logic is the production code; Python computes only the window indices (a pure gather and dot product from the FeatureSpec). This removes the largest drift risk between the two languages.

### 4.3 Labels [D with schedule H]

```
y_search = sigmoid(value / 256)
y_z      = {0.95, 0.50, 0.05} for result {+1, 0, -1}           (soft targets: Buro's 0.9/0.5/0.1 avoids non-existent optimal weights, fit §1.4)
y        = lambda_g * y_search + (1 - lambda_g) * y_z
proven (mate distance >= 4 plies): y = 0.99 or 0.01 (overrides the blend)       proven within 3 plies: record not written
lambda_g = min(0.9, 0.5 + 0.05 * (g - 1))                      (z weight 0.5 in generation 1 falling to 0.1: selfplay §5.5, OR4 / OR6 linear fall-off)
```

Rationale and sources: bootstrapped search values were more accurate than raw outcomes in AVI, Oracle and Descent (`selfplay` §3.7 point 1), while outcomes counter the horizon effect early on and a mixture or a linear fall-off from outcome to search value was best in Connect 4 (`selfplay` §1.14 OR4). With blasts, one late tactical blunder relabels every earlier position, so `z` is a minority term from generation 3 on. Proven results from the threat search or the main search are exact (`selfplay` §3.6, OR2) and override. The schedule and the targets are [H14].

### 4.4 Fitting in PyTorch [D, hyperparameters H]

- **Model**: a single 1-D parameter vector `theta` (training layout of 3.3 plus family T), exactly the Java evaluator before folding. Per sample: 134 window indices x 3 components (base, stage, cd) plus 3 T indices = 405 indices; output `q = sum(theta[idx])`. This is `torch.nn.EmbeddingBag(mode="sum")` / gather-and-sum, the analogue of NNUE's sparse feature transformer without custom kernels (`fit` §5.4, `selfplay` §5.5).
- **Index extraction on the GPU**: positions are stored on the GPU as `uint8 [N, 96]` (15 M positions = 1.4 GB); per batch gather the `[134, 6]` window cells from the FeatureSpec, compute codes (`sum digit * 3^k`), swap digits if `stm == second`, then look up `featMap[w, code]` (a 97,686-entry table from the FeatureSpec that applies mirror tying and canonical codes) and add the component offsets.
- **Loss**: binary cross-entropy of `sigmoid(q)` against the soft target `y` (the logistic-regression form of Texel/Buro; Rapfi's tuner uses BCE the same way, `rapfi` §2.5; cross-entropy trained faster and generalised better than squared error in the CPW taxonomy, `fit` §2.5). Regularisation: L2 on `Wstage` and `Wcd` ~ 1e-5 and on `Wbase` ~ 1e-7 (sweep {1e-6, 1e-5, 1e-4}), plus a per-entry back-off `l2_i = lambda / (1 + count_i / 20)` so rare entries stay near their class prior (`fit` §5.3 point 3, Logistello's count rules). Component weights clamped to [-8, 8] after each step.
- **Optimiser**: Adam (beta 0.9, 0.999), batch 16,384, learning rate 1e-2 decaying exponentially to 1e-4 over training, 30-60 epochs with early stopping on the validation loss (patience 3). These follow Rapfi's tuner defaults (Adam, lr 0.01, batch 8192, exponential schedule, `rapfi` §2.5) and the NNUE practice of a mean cross-entropy (`fit` §3.6). Dense Adam over 466k parameters is trivial; no sparse optimiser is needed.
- **Warm start**: generation g+1 starts from `theta_g` and trains on the window of the last three generations (about 15 M positions, deduplicated by position with target averaging, because duplicates are 30-50 percent of windows in self-play, `selfplay` §1.14 OR6). Generation 1 starts from the hand tables converted to logits.
- **Augmentation**: left-right mirror, applied virtually (the tying already makes the model mirror-invariant; augmentation adds the second view of every position for free).
- **Validation**: hold out 5 percent of *games* by game id hash, not random positions (positions within a game are strongly correlated, `fit` §5.2 point 7). Report validation BCE, sign accuracy on proven positions, pairwise discordance on random position pairs of different outcome (Buro's quality measure, `fit` §1.2), all split by stage and `cdCtx`; and list the entries with large magnitude and fewer than 20 samples (the Texel "cursed parameter" check, `fit` §2.1).
- **Data volume rule**: Logistello used about 7 positions per weight (`fit` §5.2 point 6); with 466k parameters that is at least 3.3 M positions; we fit on about 15 M.

### 4.5 Export to int16 tables [D]

`export.py` folds the components (`Wbase + Wstage + Wcd` per stage and cd context), multiplies by 256, rounds to `int16`, asserts `|q| <= 6144`, writes the `.qcw` blob (section 3.7) with the `specHash`, and the tool `EmbedWeights` produces `EvalBlob.java`. The hand tables use the same writer, so the very first engine already runs the production loading path.

### 4.6 Java to Python parity test [D]

1. **Exhaustive window test**: for every class and every code in 0..728, the Java FeatureSpec's parameter index equals the index Python derives from the same JSON (this also tests tying and canonical codes).
2. **Position test**: Python writes `parity-8x12.qpv`, 100,000 positions sampled from the data with Python's float logit and its *integer* sum computed from the quantised tables by an independent numpy implementation. The Java unit test builds a `Board` from the cells, calls the production `Eval`, and requires: integer equality with Python's integer sum; `|int/256 - float| <= 70/256`; equality of `tf[]` with the recorded values.
3. **Drift test**: regenerate 1,000 positions with the current Java generator, and require that the T-family indices equal Java's own recomputation.

### 4.7 Iteration loop: generation N plays generation N-1 [D]

```
g = 0 : engine_0 = M3 search + hand tables
repeat
   data_g   = SelfPlay(engine_g, 20k nodes/move, pool: 60/20/20 as in 4.2)          ~ 3-6 M new positions
   theta    = Fit(window = data_{g-2..g}, warm start theta_g, lambda_g)
   engine'  = Export(theta)
   gate     = Match(engine' vs engine_g, paired openings, colours swapped, SPRT elo0 = 0, elo1 = 10, alpha = beta = 0.05, <= 2000 pairs)
   anchor   = Match(engine' vs hand-table engine, fixed 600 pairs)     // regression detector, no decision
   accept: engine_{g+1} = engine'   | reject: retry once with lambda_g + 0.1 or with doubled data, else stop
until two consecutive rejections (plateau) or 12 generations
```

Expected number of generations: 6-12 (Oracle 40, AlphaZero.jl 15, with much slower generators, `selfplay` §1.13-1.14). Track per generation: fraction of games decided by a blast, draw rate, game length, fraction of proven labels, validation BCE, share of positions with each `cdCtx`, and redundancy (`1 - unique/total`), the diversity measure of AlphaZero.jl (`selfplay` §4.1).

### 4.8 Optional fine-tuning: TD-Leaf and TreeStrap [H15]

- **RootStrap is the default** (the label is the root search value of every recorded position). KnightCap-style TD-Leaf stores, for each root, the PV-leaf board and its static value, and trains the leaf toward the next searched value with `lambda = 0.7`, a `tanh` squash, and the rule "zero positive temporal differences unless the opponent's move was the predicted one" (`selfplay` §1.15, `ntX` §1.8). Evidence is one chess run plus indirect 2048 results, a self-play-only run scored 11 percent against a version with diverse opposition, and backgammon showed no gain (`selfplay` §3.3); so TD-Leaf is tried only after the plateau and judged head-to-head at equal time.
- TreeStrap (all searched nodes with their alpha-beta bounds) was the strongest tuner in `fit` §2.4 (2157 versus 1068 for TD-Leaf in Meep) and applies directly to a sum of table lookups, but needs bounds read from the TT. Start with a sampled variant: for 10 percent of roots dump up to 100 exact or bounded nodes with depth at least 2 and use the one-sided loss `max(0, bound - H)`.
- A Java Descent-style generator (`selfplay` §5.8) is a comparison arm for data, not part of the first pipeline.

### 4.9 Optional later NNUE [H16]

Gate: build only if the tables plateau **and** an offline teacher MLP (trained on the same records on the GPU, `selfplay` §5.9, `fit` §5.8) reaches at least 5 percent lower validation BCE than the tables (the supervised ceiling idea of OR2). Sketch: inputs 192 binary features (96 cells x mover/opponent) plus 8 one-hot context features, one accumulator of 32-64 `int16` units, `2H -> 16 -> 1`, SCReLU or clipped ReLU, QA = 255, QB = 64 scheme (`fit` §3.5). A normal move adds one feature column; a blast triggers a full refresh. Scalar Java cost per evaluation is about `2H*16` multiply-adds (1-2k), several times a table sum [E], and no source shows that a small NNUE beats a well-fitted pattern table on a game like this (`fit` §5.8, §6 question 14).

### 4.10 Compute and time estimates [E, all to be replaced by measurements]

| Item | Estimate | Basis |
|---|---|---|
| Java node rate, single thread, steady | 1-3 M nodes/s | `search` §6.4 (Pons 12 M/s x 0.6 for Java / 3-6x heavier node) |
| Fixed 20k-node move | 7-20 ms | above |
| Game of 70 plies | 0.5-1.4 s per core | above |
| 8 worker threads | 20-60k games/h, 1.4-4 M positions/h | `rapfi` §7.9 (0.5-1 M positions/h) is more pessimistic; use the lower figure for planning |
| Positions per generation | 3-6 M new, window 15 M | 4.4 |
| Training data in VRAM | 15 M x 96 B = 1.4 GB (of 8 GB) | 4.4 |
| One fit (30-60 epochs) | 3-15 minutes | 466k parameters, 405 indices per sample, tens of millions of samples per minute |
| Gate: 2,000 games at fixed nodes | 5-10 minutes on 8 cores | 1 s per game |
| Gate: 2,000 games at the real 100 ms | 20-40 minutes on 8 cores | about 70 plies x 2 calls x up to 65 ms, 8 concurrent games |
| One generation end to end | 3-6 hours, dominated by generation | sum |
| Whole campaign | 1-3 days wall clock, about 250 core-hours, under 5 GPU-hours | 8-12 generations |
| Data per generation | 150-300 MB of `.qcp` | 48 B x 3-6 M |

Cores on the remote PC are unknown; every Java-side figure scales linearly with them. The files crossing the network are small (shards and a 1-2 MB `.qcw`), so Java may run wherever cores are, provided a JDK 25 and the same engine jar are available there, and Python needs only `torch` and `numpy`.

### 4.11 Pitfalls the sources document

- Outcome labels corrupted by opponents that fail to convert (14-17 percent of Connect-4 positions in `selfplay` §1.4); we use `z` as a minority term.
- Side-to-move bias of search-sampled positions; fixed by PV-successor records (4.2).
- Horizon effect in search-value labels; the threat search runs inside the labelling search, and proven results override.
- Learning that the opponent will blunder (KnightCap); matters only for TD-Leaf.
- Non-stationary self-play distribution and forgetting; mitigated by the 60/20/20 pool, the multi-generation window and warm start.
- A deterministic engine produces narrow data; mitigated by 4.2.
- Python and Java disagreeing about feature indices; mitigated by 4.6 and by Java computing every game-logic feature.

## 5. Testing and measurement

### 5.1 Rules conformance against the official engine [D, built in M0]

Everything else rests on our `Board` reproducing R1-R25 exactly. The conformance harness (a test source set that links `libs/core-2.0-SNAPSHOT.jar`; this is the project's own contest jar and the README describes running it locally) drives the real `Experiment` headless (`paint=false, print=false`) with **scripted bots** that return predetermined `(column, special)` sequences, including deliberately illegal ones, and records `field.getField()` at the start of every call (the bot receives the live field). A `Board` replay of the same script must match the engine after every turn. Cases that must be in the script generator:

- normal drops, full columns, out-of-range columns without a special (forfeit);
- specials requested before the 11th own turn (silently ignored), exactly on turn 11, on turns 20, 21, 22 after a special on turn 11 (the next honoured turn is 21), and while the other player forfeits turns;
- specials on empty, short, nearly full and full in-range columns (the full-column case must burn the cooldown and leave the board untouched);
- blasts that create a line of the opponent only, of the mover only, of both (the mover wins every time);
- full-top-row draw, a win on the move that fills the board, a win on turn 1000, the draw at the 1000 cap;
- board sizes from 1x1 to 40x40, both player orders.

Never script an out-of-range column together with an honoured special (R10 crashes the engine). Add a **differential test of the blast and window logic** against a naive array reimplementation of R5-R7 on at least 10 million random positions, including the valley prefilter (every position where a naive scan finds a winning blast must pass the prefilter), the `thr` and `live3` incremental counters against a rebuild from the cells, and the hash against a full recompute. A 7x6 mode with `K = 4` and specials disabled (cooldown infinite) is a built-in correctness check against Pons's published test sets and scores (`search` §5.3 correctness suite 3).

### 5.2 Match harness [D]

Two modes sharing one orchestrator:

| Mode | Purpose | How |
|---|---|---|
| **In-process, fixed nodes** | thousands of games per hour for tuning and for the gate pre-check | our `Board` and `Searcher` directly, fixed node budget, start from any position with explicit counters; deterministic given the seed |
| **Official protocol, real time** | the acceptance mode: real 100 ms limits, thread-per-call, cold effects, the actual `QcBot` class | `new Experiment(a, b, false, false, rows, cols).runExperiment()` (synchronous when not painting); opponents are `Algorithm` instances; concurrency at most physical cores minus 1 (CodinGame numbers vary 20-40 percent run to run when oversubscribed, `search` §4.1) |

**Openings and colours.** The engine starts every game from the empty board and never swaps sides (R23), so the harness must do both. Colour swap is trivial (swap the constructor arguments). Openings are injected by a `ScriptedOpening` wrapper that returns scripted columns for the first `k` own turns and calls `bot.observeScriptedTurn()` so the bot's turn counter and tracker stay correct (the bot reads its turn index from its own call count, section 2.7; the wrapper must therefore be able to advance it). Opening pool [D]: 2,000 positions; 70 percent after 4-10 random normal plies, 30 percent after 20-30 plies where both specials are ready or about to be (random moves, specials excluded or sparse); keep a position only if a 1-second search says `|value| < 150` units (0.6 logit) and it is not terminal (the "balanced book" of `fit` §5.2 and `search` §5.3). Every opening is played twice with colours swapped; the pair is the unit of statistics.

**Game statistics logged per run** (the report calls them the two metrics the literature does not provide, `rep`): draw share, share of games decided by a blast, share decided by a normal six, first-player and second-player score, mean length, share reaching the 1000 cap, timeouts and illegal moves per bot (must be zero for ours), and nodes, depth, search time percentiles from the bot's own counters.

### 5.3 SPRT [D]

Pentanomial statistic over colour-swapped pairs. For each pair let `x` be the mean of the two game scores (win 1, draw 0.5, loss 0), so `x` is in {0, 0.25, 0.5, 0.75, 1}. With `N` pairs, sample mean `mu` and sample variance `sigma2` of `x`, and `s(e) = 1 / (1 + 10^(-e/400))`:

```
LLR = N * (s(elo1) - s(elo0)) * (2*mu - s(elo0) - s(elo1)) / (2 * sigma2)      (GSPRT normal approximation, as in fishtest)
accept H1 when LLR >= ln((1-beta)/alpha) = +2.944     accept H0 when LLR <= ln(beta/(1-alpha)) = -2.944       (alpha = beta = 0.05)
```

Validate the implementation once by simulation (known Elo, check the stopping distribution). Bounds follow `search` §5.1 ("all other engines": gainer `[0, 10]`, non-regression `[-10, 0]`):

| Test | Bounds | Cap |
|---|---|---|
| Gainer: a new component or a new weight generation | elo0 = 0, elo1 = 10 | 4,000 pairs; no verdict at the cap means reject |
| Non-regression: removing code, simplifying | elo0 = -10, elo1 = 0 | 4,000 pairs; no verdict means keep the simpler version only if the point estimate is above -5 |
| Parameter tuning (SPSA, `fit` §2.2, `search` §5.2) | not SPRT; a few thousand game pairs per step | tuned scalars only: eval scale, special-move ordering penalty, aspiration width, razor margin, LMR factor |

Procedure for every change: (1) fixed-node in-process SPRT as a cheap filter, (2) confirm at the real 100 ms in official-protocol mode, (3) any timeout or illegal move in the candidate is an automatic fail regardless of score. A draw-heavy result (draw share above 90 percent) lowers the power of every test; then raise the opening imbalance threshold or use the more decisive opponent pool of 4.2.

### 5.4 Benchmark positions [D, built progressively]

A text format, one position per line, produced from self-play and graded by a long offline search (10-30 s per position, or df-pn for narrow trees, `tss` §3 and `search` §3.3): `rows cols turn cdFirst cdSecond stm cellString | acceptableMoves | provenResult | mateDistance | tag`. The suites and what they test:

| Suite | Content | Pass criterion |
|---|---|---|
| T1 | win in 1 by a normal move; forced block of a single playable threat; avoid the move under an opponent threat cell | 100 percent at depth 1 |
| T2 | stacked and double playable threats (mate in 3 and 5) | solved within 80 ms |
| T3 | **win in 1 by a blast**: the line is the mover's, the opponent's, or mixed; blasts in valley columns, edge columns, on empty and nearly full columns | 100 percent, and no false positives on a negative set of 5,000 positions |
| T4 | **blast defence**: the opponent threatens a playable win and only a blast saves the position; also positions where a blast must not be used because it opens a line for the opponent | solved within 80 ms |
| T5 | **blast danger avoidance**: the opponent's special is ready and some quiet moves hand it a winning blast, others do not | the engine never plays the losing set |
| T6 | cooldown edge cases using the real `TurnTracker`: counter 9, 10, 11; a special just used; opponent forfeits; special on a full column wasted | the tracker's availability matches an independent replay |
| T7 | gravity VCF positions with a proven long sequence (attacker 4-10 moves) with the defender's special unavailable, and the same position with it available | proofs found when exact, no false proofs when the defender can blast |
| T8 | parity and waiting-game endgames on reduced boards solved exhaustively (for example 6x4 with `K = 4`, specials off) | validates the ownership rule of 3.4 and the stacked-threat claims before they are used as features |
| T9 | Pons 7x6, `K = 4`, specials off | node counts and solve rates comparable to published tables (engine sanity) |

Reported per suite: solve rate within 80 ms, time to solution, nodes, depth. The suites double as regression tests in CI and as an additional sanity check for each trained generation (a stronger average evaluation must not break T1-T7).

### 5.5 Performance budget [E targets]

| Quantity | Target | Notes |
|---|---|---|
| Steady-state search speed, 8x12, mid-game | at least 1 M nodes/s per thread (`search` §6.4 estimates 1-3 M) | measure first: `make/unmake`, blast, `hasSix`, `thr` update, eval; each micro-benchmark with JIT warm-up and dead-code protection (`search` §7.2) |
| Nodes in a 62 ms search | at least 60k, depth 8 plies or more in quiet mid-game positions | an observation to confirm, not a promise |
| Evaluation share of node time | below 30 percent, else enable the running sums [H11] | profile |
| Allocation per call | below 200 KB (snapshot and history copies); zero in the search loops | check with `ThreadMXBean.getThreadAllocatedBytes` in the harness (`search` §4.5) |
| **Latency per call as seen from outside the bot** | p50 at most 70 ms, p99 at most 75 ms, p99.99 at most 80 ms, no call above 90 ms | **never exceed about 80 ms** (requirement); the 100 ms engine limit includes thread creation, `getField()` and any GC pause |
| `useSpecialMove` | below 1 ms when no verification is needed; at most 45 ms with verification | |
| First call in a fresh JVM | below 60 ms | cold code; the constructor warm-up of 300-500 ms [H17] and the first-call hard stop of 40 ms |
| Heap | works at `-Xmx128m`; TT adapts to `Runtime.maxMemory()` | unknown limit |

**How latency is measured**: the engine's own `Experiment.executeWithTimeout(Supplier, long)` is `public static`, so the harness calls the official function with a `Supplier` that records `System.nanoTime()` inside, and takes the timestamp outside around the call; the difference between inside and outside is the thread-creation and scheduling overhead the bot must reserve for. Run at least 20,000 calls per configuration, with the default GC, with `-XX:+UseSerialGC` and `-XX:+UseParallelGC`, with GC logging on, and with background CPU load equal to the concurrency used in the tournament-like runs. If p99.99 exceeds 80 ms lower `HARD` in 2 ms steps (section 2.8).

### 5.6 Robustness tests [D]

- **Board sizes**: 1x1, 1xN, Nx1, 2x2, 3x3, 5x5 (no vertical windows), 6x6, 6x7, 7x6, 8x12, 12x8, 9x9, 10x14, 25x25 and 40x40; the bot must return a legal column every time, with and without learned tables, within the time limit. A size with no window at all is a pure fill-up game. Above a configured number of cells (default 4,096) fall back to a cheap rule to keep latency safe.
- **Never a full column**: property test over random positions, including positions where only one column is open and positions produced by blasts, 1 M calls: `!field.isColumnFull(result)` and `0 <= result < columns`.
- **Never an invalid special**: a wrapping oracle checks `useSpecialMove == true` implies the cached column is in range and non-full, the cooldown counter (from an independent replay) is at least 10, the decision matches the turn and the first call finished in time.
- **Timeout injection**: a test hook delays the first call of selected turns past 100 ms (through the official `executeWithTimeout`); the bot must then return `false` from `useSpecialMove` (R10, R15), and keep working afterwards (the zombie from the late call must not damage the next call).
- **Zombie and restart**: a test hook makes a search ignore its deadline once; the next calls must be unaffected. Reuse one bot instance across `Experiment.restart()` and across two consecutive games.
- **History tampering**: a wrapper calls `clearMoveHistory()` every turn; the tracker must fall back to its local belief and stay correct.
- **Forfeiting opponents**: `CheatingBot` (forfeits every turn), `LinearBot`, `RandomBot`, `SpecialMoveBot` (spams specials, many of them refused by the cooldown), each as first and as second player; opponent forfeits must not desynchronise the tracker.
- **Weights**: truncated, corrupted, wrong-spec and wrong-size blobs fall back to hand tables without exceptions.
- **Concurrency**: several bot instances in parallel threads of one JVM must not share mutable static state (immutable tables only); the TT is per instance.
- **Log noise**: no bot call may cause an engine ERROR log line during a normal game (each is a forfeited turn or wasted special).

## 6. Milestones

Order as agreed: M0 safe baseline, M1 tactical 1-ply, M2 alpha-beta, M3 threat search, M4 harness and SPRT, M5 learned tables v1, M6 iterative training, M7 optional experiments. One dependency to respect: M2 and M3 are accepted with a minimal fixed-N match runner (colour swap, paired openings, no SPRT) that is delivered in M0; M4 turns it into the full SPRT and regression system and re-confirms the earlier verdicts. Expected gains are estimates and hypotheses, quoted with their digest basis where one exists; the Elo figures from other games are not transferable without measurement (`fit` §4 header). Effort figures are working days for one developer [E].

| Milestone | Deliverable | Definition of done | Expected gain | Effort |
|---|---|---|---|---|
| **M0 Safe baseline** | Gradle project from the starter (Bot-Class `ch.qc.bot.QcBot`), `Geometry`, `Board` with exact rules R1-R25, `Snapshot`, `TurnTracker`, centre-first non-full-column bot that never requests a special, safe-column fallback, conformance suite against the official engine, minimal match runner, jar build, packaging probe (resource and blob read in a fresh JVM through a `URLClassLoader`) | Conformance and differential tests clean (section 5.1, 10 M random positions). 100,000 bot calls over the board-size list of 5.6 with zero timeouts, zero invalid columns and zero invalid specials. Latency p99.99 below 10 ms. Uploadable jar builds with `./gradlew build` and the manifest is correct | Removes the failure modes that decide some games outright (a timeout is a forfeited turn, an invalid special crashes the engine). Against `RandomBot` and `SpecialMoveBot` it should already win most games; **measure and record the baseline, no target** | 3-5 |
| **M1 Tactical 1-ply** | Immediate normal win; immediate **blast win** with the valley prefilter (own, opponent or mixed line); forced block of a single playable threat; Pons filter (never play under an opponent threat cell); refusal of moves that allow a winning blast when the opponent's special is ready (the same blast-win scan on the opponent, one ply deep); a blast is used only to win or when it is the only defence against two playable threats; cooldown tracker complete | Suites T1, T3, T5, T6 at 100 percent; zero losses to `RandomBot` in 10,000 games and fewer than 1 percent losses to `SpecialMoveBot`; scores at least 90 percent against M0 | The largest single step against naive opponents: all one-move tactics in both directions, including the explosion tactic that decides some games by the rules (R3, R4). [E] against M0: above 90 percent score | 4-6 |
| **M2 Alpha-beta, TT, hand evaluation** | `Searcher` (PVS, iterative deepening, TT, ordering, killers, history, mate-distance, forced-reply extension), time manager with hard stop 62 ms, hand tables (3.9), two-call protocol with cached decision, per-call search context and token | Median completed depth at least 6 plies at 62 ms in mid-game; latency table of 5.5 met; T1-T6 at 100 percent, T2 at least 80 percent; SPRT gainer (or fixed-N, at least 600 pairs) against M1 shows at least 80 percent score; allocation check passes; node rate measured and recorded | Search depth beyond tactics. Pons's ablations show node-count reductions of about 10x from centre-first ordering, about 20x from the TT and up to 100x from threat-count ordering (`rep`), which in a fixed 62 ms translates into several extra plies; in ConnectX depth-5 minimax scored 873 against 268 for a greedy bot (`rep`). [E] +300 Elo or more against M1 | 7-10 |
| **M3 Threat search** | Gravity VCF (exact mode with defender blasts), razoring into VCF, leaf dive, root own VCF and root opponent-VCF check, quick-win static rules, own VCF TT | T7 passes: every VCF win agrees with a full alpha-beta on 100,000 random positions (no false proofs); SPRT gainer [0, 10] against M2 at the real 100 ms; timeouts zero. Statistics logged: fraction of nodes in VCF, VCF proof rate per move, cases where a defender blast refuted a VCF | `rapfi` §5.2 attributes about 17 Elo (VCF at leaves) and about 68 Elo (razoring into VCF) to these two uses in Gomoku, with a strong evaluation; here the gain depends on the share of games decided by long forcing sequences, which is unknown. [E] +50 to +150 Elo, could be lower if shallow search already finds most threats | 5-8 |
| **M4 Harness and SPRT** | Opening pool of 2,000 balanced openings (4-10 random plies, and 20-30 plies with specials ready), `ScriptedOpening`, pentanomial SPRT with the LLR of 5.3 (validated by simulation), JSONL result store, nightly regression, suite runner in CI, latency report generator, game statistics (draw share, share decided by a blast, first-player score), first SPSA tuning of the scalar parameters | The SPRT implementation passes its simulation test. The M1 to M2 to M3 verdicts are reproduced from stored logs. 2,000 official-protocol games finish within 40 minutes on 8 cores with zero harness-induced timeouts. Draw share and blast-decided share of M3 against itself are reported | Measurement, not strength. The two report-level questions get answers: how drawish is six-in-a-row on 8 rows, and how often does a blast decide the game (`rep`). A modest strength gain from SPSA on the margins [E] +10 to +30 Elo | 4-6 |
| **M5 Learned tables v1** | `FeatureSpec` export, `SelfPlayMain`, `.qcp` writer, Python `fit.py` and `export.py`, `.qcw` format, `EmbedWeights`, loader with fallback, parity tests; generation 0 (M3 plus hand tables) to generation 1 | Parity tests of 4.6 pass exactly; the built jar loads the compiled-in blob in a fresh JVM and uses it; engine_1 beats engine_0 (hand tables) by SPRT gainer [0, 10] at the real time limit; latency unchanged within 2 ms; validation loss and per-stage metrics recorded | A jointly fitted pattern table replaced hand features in Othello with a gain worth about two plies, roughly tenfold speed (`fit` §1.1). KnightCap's warning that search can beat evaluation applies (`selfplay` §4.6 pitfall 12). [E] +100 to +250 Elo against hand tables, could be near zero if the hand tables are already sound and the search dominates | 7-10 |
| **M6 Iterative self-play training** | The loop of 4.7 run to a plateau; ablations of [H2] 4-state cells, [H3] sharing, [H4] stages, [H5] cooldown buckets, [H14] label schedule, each refitted on the same data (cheap) and gated by SPRT | At least three accepted generations or two consecutive rejections with diagnostics. Final candidate beats M3-with-hand-tables by SPRT, passes all suites, and is inside the latency table. Ladder of all accepted generations stored | Diminishing returns per generation are the norm; [E] cumulative +100 to +300 Elo over the hand tables including M5, with later generations adding tens of Elo (`selfplay` §4.2: performance jumps when early data leaves the window; Oracle needed about 40 generations with a slow generator) | 5-8 wall-clock days, mostly unattended |
| **M7 Optional experiments** | Each independent, each gated by SPRT or by the explicit go/no-go below | See the list below | Unknown; assume each is worth at most a few tens of Elo until measured | open |

**M7 candidates, in the order of expected value per effort** (each is [H]):

1. Tiny opening book: the first one to two plies from deep offline search (about 12 + 144 positions, under 10 KB, `search` §6.3 [OWN]); only if self-play shows the first moves matter.
2. Interpolation between adjacent stage tables ([H4]) and 7-cell lines ([H6]) if the 6-window tables plateau.
3. TD-Leaf or sampled TreeStrap fine-tuning ([H15]).
4. Generic-size tables ([H12]) if the tournament uses other board sizes.
5. Lazy SMP with 2-4 threads, only if the tournament lets a bot use extra cores.
6. ProbCut with per-stage regressions (`fit` §1.6), only with a threat-aware quiescence; the chess evidence is mixed.
7. Small NNUE ([H16]) behind the teacher-MLP gate of 4.9.
8. **MCTS-Solver with the pattern evaluation as leaf value ("UCT-EV")**, as an experiment only (`mcts` §3.4): MCTS-Solver proof backups with the leaf check, implicit minimax `alpha` swept over {0.15, 0.25, 0.4}, `C` swept over {0.3, 0.5, 0.7, 1.0}, cooldown counters in the hash. Go/no-go: at least 1,000 colour-swapped games at 100 ms against the production bot with a score of at least 50 percent (the confidence interval at 1,000 games is about +-3 percent). The prior is against it: no source reports MCTS beating alpha-beta in a Connect-4-class game at sub-second budgets, hybrids were measured only against plain MCTS, and the strongest evaluation-driven results favour alpha-beta at 1 s (`mcts` §3.2, §4). The only parts of `mcts` adopted into the main design are the decisive and anti-decisive move checks (already in M1) and the trap-density census as a diagnostic.
9. A Java Descent-style data generator as a comparison arm (`selfplay` §5.8).

Rough schedule on the above efforts: M0-M3 about 4-5 weeks, M4 overlapping the end of M3, M5 about 1.5 weeks, M6 about 1 week of wall-clock time, so a trained bot after roughly 7-9 weeks of work [E]. `selfplay` §5.10 gives 2.5-3.5 weeks for the learning pipeline alone once a search engine exists.

## 7. Repository layout

```
quantum-connect/
  design/pattern-bot-design.md            this document
  reports/, research_notes/, sources/     existing research (unchanged)
  bot/                                    Java module = the starter Gradle project (gradlew, libs/core-2.0-SNAPSHOT.jar, settings.gradle)
    build.gradle                          keep the starter's jar task; set 'Bot-Class': 'ch.qc.bot.QcBot'; add sourceSets 'tools'
    src/main/java/ch/qc/bot/
      QcBot.java                          implements Algorithm; the only class the harness sees
      Snapshot.java, TurnTracker.java, Decision.java
      rules/   Geometry.java, Board.java, Blast.java, Zobrist.java, Tables.java (THR_POS, LIVE3, SIX, SWAP12)
      search/  Searcher.java, Vcf.java, TranspositionTable.java, MoveOrdering.java, TimeManager.java, Abort.java
      eval/    Eval.java, FeatureSpec.java, WeightBlob.java, EvalBlob.java (generated), HandTables.java
    src/main/resources/                   dev-only weights (optional; the release uses EvalBlob)
    src/test/java/                        conformance (links the official jar), differential, tracker, parity, robustness, suites runner
    src/tools/java/ch/qc/tools/           SelfPlayMain, MatchMain (SPRT, ScriptedOpening), DataDump, ParityDump, EmbedWeights,
                                          SuiteGen, LatencyProbe, FeatureSpecDump
  training/                               Python (torch, numpy); runs on the remote PC
    qctrain/ features.py, data.py, model.py, fit.py, export.py, parity.py, report.py
    configs/ fit-gen1.yaml, ...
    spec/feature-spec-8x12.json           written by FeatureSpecDump, read by Python
    requirements.txt
  suites/                                 tactical and regression positions, text, versioned (T1-T9)
  data/                                   git-ignored
    gen000/ ... genNNN/  shard-0000.qcp, manifest.json (engine jar hash, specHash, seeds, node budget, pool mix)
    models/ genNNN/ theta.pt, eval.qcw, fit-report.json
  results/                                match logs (JSONL), SPRT summaries, latency histograms, nightly reports
```

**Build notes.** The starter's `jar` task copies `sourceSets.main.output` and the runtime classpath into one fat jar and sets `Main-Class: EmulatorRunner`; keep that, change only `Bot-Class`. The `tools` and `test` source sets are not part of the uploaded jar (the `jar` task copies `main` output and `runtimeClasspath` only), so JUnit and the tools may use any library. The Gradle toolchain asks for Java 25; the laptop currently has a JDK 21, so a JDK 25 must be installed (or auto-provisioned by the toolchain) on every machine that builds or runs the bot, including the remote PC if self-play runs there. The README restricts the bot to the standard library plus `libs/`; tools and tests are outside that rule.

**Data formats between Java and Python.**

| File | Writer | Reader | Content |
|---|---|---|---|
| `feature-spec-8x12.json` | `FeatureSpecDump` | `features.py`, Java `FeatureSpec` loader | version, `rows`, `cols`, `K`, per window `{id, dir, cells[6] in digit order, class, selfMirror}`, `canon[729]`, class count, stage and cooldown context definitions, T-family bucket edges and layout, parameter offsets, `specHash` |
| `shard-*.qcp` | `SelfPlayMain` | `data.py` | file header (magic, rows, cols, K, record size 48, `specHash`, generation, engine hash) and fixed-size records of 4.2 |
| `eval.qcw` | `export.py` | `WeightBlob`, `EmbedWeights` | blob of 3.7 |
| `parity-8x12.qpv` | `parity.py` | Java parity test | 100,000 records: cells, stm, counters, expected integer sum, expected float logit, `tf[]` |
| `suites/*.txt` | `SuiteGen` | tests, `MatchMain` | position line format of 5.4 |
| `results/*.jsonl` | `MatchMain` | `report.py` | one line per game: opening id, colours, result, plies, decided by (normal six, blast, draw by full row, draw at the cap), timeouts and illegal moves, per-bot latency summary |

## 8. Risks and open questions

### 8.1 Risks

| ID | Risk | Impact | Mitigation | Early signal |
|---|---|---|---|---|
| K1 | The tournament harness differs from the local engine (timeouts, invalid-column rule, scoring, side assignment, board size) | Wrong assumptions in section 1 | Treat both README claims as fatal (never invalid column, never slow); keep all constants in one `Rules` class; make board size and `K` parameters | Organisers' answers (8.2) |
| K2 | `accelerateQuark` finishes late, `useSpecialMove` returns true, engine throws (R10) | Experiment dies | The `finishedWithin` rule, `(turnId, hash)` check, timeout-injection test (5.6) | Any engine exception in the harness |
| K3 | The first calls are slow (cold JIT, class loading of a 2-3 MB class) | Forfeited early turns | Warm-up in the constructor [H17], first-call hard stop 40 ms, no heavy static initialisation in the first call | First-call latency in the probe |
| K4 | The constructor is timed or forbidden from running long | Warm-up kills the bot | Warm-up is time-boxed to 300-500 ms, can be switched off, and the bot works cold | Organisers' answer |
| K5 | GC pause or CPU contention (other games in the same JVM or on the same cores) pushes a call over 100 ms | Forfeits | 62 ms hard stop leaves 38 ms; allocation-free search; the p99.99 test under load; heap-adaptive TT | Latency percentiles (5.5) |
| K6 | Jar size limit or class-loader restrictions make the weights unusable | No learned evaluation | Compiled-in blob (about 1.6 MB of source constants); hand tables as fallback; a generic small spec if size is tight | Upload rejection |
| K7 | Six-in-a-row with gravity on 8 rows is drawish, so game outcomes carry little signal | Weak `z` labels and slow SPRTs | Search-value labels dominate; opening imbalance filter; report draw share early (M4); blast exploration raises decisive games | Draw share above 90 percent |
| K8 | The learned evaluation does not help (KnightCap: search beats evaluation; hand tables good enough) | M5-M6 give little | Hand tables are the safe baseline; gate every generation; stop at the plateau; the value of M5 is checked by one cheap experiment (generation 1) | Generation 1 fails its gate |
| K9 | Distribution mismatch: 1-ply or fixed-node labels differ from the 62 ms search | Overfitting to the labelling search | Labels come from the same search family; the gate runs at the real time control; mixed opponent pool | Gate at fixed nodes passes but real time fails |
| K10 | Parity rule `E + R + rho` or the stacked-threat claims are wrong | Harmful family-T features | Validate on reduced boards by exhaustive search (T8) before the features go into training; the model can learn to ignore them | T8 disagreement |
| K11 | Blast search cost explodes when specials are ready for both sides in hoarding games | Depth collapse | Valley prefilter, blasts ordered last and reduced, [H7] policy; measure the share of nodes with a ready special | Depth statistics by cooldown state |
| K12 | Python and Java drift (feature indices, quantisation) | Silent loss of strength | Single source of truth in Java for game-logic features, exhaustive and sampled parity tests, spec hash in every file | Parity test failure |
| K13 | Opponent clears the shared history (`clearMoveHistory`) or the harness reuses a bot instance across games | Wrong cooldown estimate | Local belief fallback, restart detection by empty board and empty history, instance state reset | Tracker tests T6 |
| K14 | Reading `getMoveHistory()` costs time on long games (a 1,000-entry copy per call) | Small but unnecessary | Read only entries after `histSeen`, via the copy once per call; measured in 5.5 | Allocation check |
| K15 | Remote PC has few CPU cores, so generation is slow | Campaign takes weeks | Scale the pool; lower node budget per move; fewer generations; use the laptop cores too | Positions/hour in generation 0 |

### 8.2 Open questions that need an answer from the organisers or the user

1. Which board sizes are played in the tournament (8x12, 25x25 as in `Main`, something else)? This decides how much effort goes into the generic spec ([H12]).
2. Is the tournament engine the same as the supplied jar (timeouts of 100 ms per call, forfeit on invalid column, the 1000-turn cap, cooldown 10)? What does the harness do if `startExperiment` throws?
3. How are results scored (win, draw, loss points), is there a total time budget per game, and are sides swapped between games?
4. Is the bot constructor (and static initialisation) time-limited? Is a warm-up there acceptable?
5. How many CPU cores does a bot get, is one JVM shared by several games or bots, what are the heap and thread limits?
6. How is the uploaded jar loaded (class loader), is there a size limit, and can resources be read?
7. Does anything count the time between the two calls, or the CPU time rather than wall time?
8. Is it acceptable to use the second call (`useSpecialMove`) for a verification search? The rules allow it (R14), but fairness is a policy question (`rep`).
9. Is the remote PC's core count enough for the data generation (section 4.10), and is JDK 25 available there?
10. May the bot keep state between games of one tournament (the TT and the history)? The design assumes it may but does not depend on it.

### 8.3 Hypothesis register

All are implemented behind switches. "Gate" is the decision rule; "SPRT" means the gainer test of 5.3 at the real 100 ms unless another test is named.

| ID | Hypothesis | Test | Source of the idea |
|---|---|---|---|
| H1 | Exact VCF (with defender blasts) at leaves beats the tentative mode that ignores them, despite its cost | SPRT between the two modes | `c4vcf` §3.6 policy table |
| H2 | A fourth cell state "empty and playable now" improves the window tables | refit on the same data, SPRT | `nt4` §1.2, `fit` §5.1 point 3; no ablation exists (`nt4` §7) |
| H3 | Finer or coarser table sharing, different T bucket edges, or a second table set by `discs mod 2`, improve held-out loss and strength | held-out BCE, then SPRT | `fit` §5.1, `rapfi` §7.3 |
| H4 | 3, 6 or 8 stages, or linear blending of adjacent stages, beat 4 stages | refit, SPRT | `ntX` §3.4 (untested in any source) |
| H5 | Nine cooldown contexts (ready, 7-9, below 7 per side) beat four | refit, SPRT | `nt4` §6.3 |
| H6 | 7-cell lines add strength beyond 6-windows | refit, SPRT | `fit` §5.1 point 2 |
| H7 | Generating every blast at every node (default) is better than limiting blasts at depth 2 plies or less to defensive ones | SPRT | `search` §3.4 |
| H8 | Extending threat-creating moves by half a ply helps | SPRT | `search` §2.5 |
| H9 | LMR, aspiration, razoring into VCF, even-depth iterations each help; margins tuned by SPSA | SPRT each, SPSA for scalars | `search` §2.2, §2.5; `rapfi` §5.2 |
| H10 | A 40 ms verification search in `useSpecialMove` improves the special decision | SPRT | `search` §4.4 |
| H11 | Running per-perspective sums remove an evaluation bottleneck (matters at large sizes) | profile, then SPRT | `fit` §3.4, `ntX` §3.7 |
| H12 | Generic tables trained on a size mixture beat hand tables on other sizes | match on each size | `rapfi` §7.7, `selfplay` §5.2 option B |
| H13 | If generation 1 fails, TD-FARL bootstraps better than hand tables; H13b: adjudicating games at 400 turns as draws is harmless | match; compare label statistics with and without the cap | `nt4` §2.2, `selfplay` §5.3 |
| H14 | The label schedule (`lambda_g`, soft `z` targets, proven overrides, back-off L2) is near-optimal | refit variants, SPRT | `selfplay` §1.14, §3.7; `fit` §1.4 |
| H15 | TD-Leaf or sampled TreeStrap fine-tuning adds strength after the plateau | head-to-head at equal time | `fit` §2.4, `selfplay` §3.3 |
| H16 | A small NNUE beats the tables enough to pay for scalar Java inference | teacher-MLP gate, then SPRT | `fit` §5.8, `selfplay` §5.9 |
| H17 | A 300-500 ms constructor warm-up removes first-call slowness without harming anything | first-call latency probe | `search` §4.5 |
| H18 | MCTS-Solver with the learned evaluation reaches at least 50 percent against the production bot at 100 ms | 1,000 colour-swapped games | `mcts` §3.4 |

### 8.4 Differences between the sources and the bytecode, so they are not copied forward

- `rapfi` §7.6 writes "a timeout = loss". In this engine a timeout is a forfeited turn (R15); the harness behaviour is open (K1).
- The starter README says an invalid or full column loses the game; the engine forfeits the turn (R8-R10, R15). Both are treated as fatal.
- `search` §8 questions 1-3 (cooldown semantics, terminal order, call protocol) and `tss` §5 questions 1-5 and `c4vcf` §4 questions 1-4 are answered by section 1 (R5, R8, R9, R11, R12, R14, R18, R19). `c4vcf` §3.4's assumption that the placed disc vanishes is confirmed (R5), so its formulas for the cleared count `R` and the parity flip stand. `tss` §4.4's "universal defence" holds because a blast on the threat's column always clears a neighbouring disc of the window; whether gravity then refills the gap must be simulated.
- The earlier report states that a special after an invalid column crashes the engine. Precisely: it crashes when the special is honoured (counter at least 10) and the column is outside `[0, columns)` (R10).
- `Main` prints a 30 percent special-move chance, the code constant is 0.2; irrelevant to the bot.
- The numbers attributed to `mcts`, `ntX`, `nt4` and `selfplay` in this document come from section-by-section extracts by two readers, not from this author's own reading, and should be spot-checked before they are used in a decision.

### 8.5 What is deliberately not in the plan

AlphaZero-style networks (inference alone exceeds the budget, board size is not fixed, `rep`); MCTS as the primary engine (`mcts` §3.2); null-move pruning (zugzwang); the Allis rule system and solver-based books (not solvable here, the blast breaks the pairing logic, `rep`, `c4vcf` §3.4); training-time MCTS (`selfplay` §1.11: worse at equal compute); relying on the move history for state (R22).

