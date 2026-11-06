# Speed stream results

Branch `night-speed`, worktree `night/wt-speed`. Goal: more nodes per second with the same search tree.
Status lines at the top are updated as runs finish.

## Status

- Final commit below in the final report; `night-speed` now sits on night-main 8f31c96 (fast-forward) plus the any-size fixes. All signatures identical to base at fixed nodes.
- Numbers in this section were measured in the night on the first complete build (jar `qc-bot/dist/night/speed-86a3446.jar`, on base's time plan, without other streams).
- Real time against the base jar (`rtrun.py`, 5 threads, colour-swapped pairs, suite openings), A = this build:

| Size | Games | Score of A | 95% CI | Elo (CI) | W-D-L | Lost turns A / B | Engine crashes A / B | A knps, median depth |
|---|---|---|---|---|---|---|---|---|
| 15x11 | 120 | 67.1% | 58.9-75.3 | +124 (+62..+193) | 79-3-38 | 443 / 381 | 13 / 9 | 1931, 8 |
| 15x22 | 120 | 75.8% | 68.7-83.0 | +199 (+136..+276) | 91-0-29 | 75 / 73 | 4 / 3 | 1881, 6 |
| 8x12 | 80 | 64.4% | 57.4-71.4 | +103 (+52..+159) | 29-45-6 | 8 / 25 | 0 / 0 | 1892, 8 |

Crash = the official engine threw at `isSpaceEmpty(row, -1)`, see the last section; `MatchMain` here scores it as a loss for the mover, so the score above is pessimistic or optimistic only through those games. Without them on 15x11 A has 71.5 of 98 (73%), on 15x22 A has 88 of 113 (78%). In the brief's numbers the same bot at 80 ms under load searched 580, 450 and 360 knps, depth 4.8 on 15x22.

Commands: `python night/tools/rtrun.py --log F -- java -Xmx3g -cp tools;main;core;logback ch.qc.tools.MatchMain --a ch.qc.bot.QcBot --b jar:qc-bot/dist/qc-bot-gen003-safe-80ms.jar --pairs 60 --threads 5 --sizes S --openings suites/openings-S.txt --seed 1 --out F`. Data: `qc-bot/data/night/speed/v1-*.jsonl`.

## Hot nodes per second, clean table

Each pair of builds ran at the same time in separate JVMs, each pinned to its own P core, cores swapped between runs, one thread, 40000 nodes per position, best round (median in brackets). Measured 2026-11-04 09:50-10:20 after the restart, at lower load than the night tables. Before = base c81c42d, after = 5c45056 for the first two groups. "Same" means every position's move, score, depth and node count are equal to base, and for the threat search its move, nodes, proofs and refutations too.

| Size | Eval | Before knps | After knps | Gain | Signature |
|---|---|---|---|---|---|
| 8x12 | gen003 | 576 (517) | 1940 (1787) | 3.4x | same |
| 15x11 | hand | 352 (324) | 1941 (1818) | 5.5x | same |
| 15x22 | hand | 203 (194) | 1856 (1584) | 9.1x | same |
| 12x16 | hand | 372 (365) | 2291 (2175) | 6.2x | same |
| 20x20 | hand | 201 (191) | 2034 (1981) | 10.1x | same |
| 10x10 | hand | 371 (350) | 2259 (2187) | 6.1x | same |
| 7x9 | hand | 698 (682) | 2560 (2146) | 3.7x | same |
| 8x12 | generic-p1 | 535 (484) | 1797 (1652) | 3.4x | same |
| 15x11 | generic-p1 | 263 (242) | 1559 (1526) | 5.9x | same |
| 15x22 | generic-p1 | 152 (132) | 1389 (1340) | 9.1x | same |

Extreme sizes, base against the merged code plus the size fixes (8f895f3), hand tables, one run; medians are noisy here, the machine got busier:

| Size | Windows | Before knps | After knps | Gain | Signature |
|---|---|---|---|---|---|
| 6x7 | 23 | 1624 | 3075 | 1.9x | same |
| 9x20 | 335 | 444 | 2073 | 4.7x | same |
| 20x9 | 335 | 256 | 2129 | 8.3x | same |
| 25x25 | 1800 | 133 | 1662 | 12.5x | same |
| 40x40 | 5250 | 91 | 1749 | 19x | same |
| 64x64 | 14514 | 34 | 1355 | 40x | same |
| 8x130 | 2140 | 166 | 1604 | 9.7x | same |
| 130x8 | 2140 | 35 | 1559 | 45x | same |

The merged search is still the same tree as base at fixed nodes: on the merged code with hand tables all 15 sizes above give base's signature. So this table also says the time stream's `Searcher` edits changed nothing at fixed nodes.

Shipped configuration, merged code (night-main 8f31c96 plus the size fixes), learned tables as embedded (gen003 on 8x12, generic v-wd elsewhere), one run:

| Size | knps | | Size | knps |
|---|---|---|---|---|
| 8x12 | 1769 | | 25x25 | 1211 |
| 15x11 | 1490 | | 40x40 | 1307 |
| 15x22 | 1258 | | 64x64 | 983 |
| 6x7 | 2652 | | 8x130 | 1159 |
| 9x20 | 1617 | | 130x8 | 1212 |
| 20x9 | 1597 | | | |

Signatures of night-main and of night-main plus my size fixes are identical on these 11 sizes.

## Cold nodes per second, clean table

Fresh JVM per run, median of 5, knps in slices of cumulative search time, 2000 nodes per position, fresh tables per position, default JVM flags, no warm-up. Before = c81c42d, after = 5c45056.

| Size | Build | 0-80 ms | 80-160 | 160-320 | 320-640 | 640-1280 | 1280-2560 |
|---|---|---|---|---|---|---|---|
| 15x22 | before | 90 | 26 | 58 | 160 | 207 | 228 |
| 15x22 | after | 357 | 389 | 412 | 1447 | 1537 | 1556 |
| 15x11 | before | 90 | 37 | 116 | 136 | 258 | 293 |
| 15x11 | after | 427 | 516 | 388 | 769 | 1570 | 1631 |
| 8x12 | before | 115 | 186 | 300 | 240 | 492 | 524 |
| 8x12 | after | 393 | 761 | 1074 | 671 | 1533 | 1645 |

Same, but on two pinned logical CPUs with `-XX:-UseThreadPriorities -XX:ActiveProcessorCount=2 -Xmx512m` (the time stream's imitation of the server's CPU), run under the lock:

| Size | Build | 0-80 ms | 80-160 | 160-320 | 320-640 | 640-1280 | 1280-2560 |
|---|---|---|---|---|---|---|---|
| 15x22 | before | 52 | 37 | 42 | 50 | 49 | 171 |
| 15x22 | after | 200 | 276 | 371 | 445 | 881 | 1484 |
| 15x11 | before | 107 | 89 | 126 | 112 | 156 | 301 |
| 15x11 | after | 392 | 614 | 546 | 464 | 1114 | 1777 |
| 8x12 | before | 172 | 188 | 194 | 255 | 613 | 644 |
| 8x12 | after | 569 | 736 | 518 | 370 | 1038 | 1846 |

These are without the constructor warm-up of the merged bot, so they compare the code itself.

JIT work over 12 s of a cold run on 15x11 (`-XX:+CITime -XX:+PrintCompilation`, JFR `jdk.Compilation`): C2 time base 3.2-3.5 s, new 2.5 s; C2 compiles of `Searcher.search` base 6-7, new 4 (about 225 ms each); of `HandEval.evaluate` base 26-30, new 11-12. No hot method is near the 8000-bytecode limit (`search` 1573).

## Why it got faster

Base profile (JFR, hot, 15x22): 65% in `Board.blast`, of which 54% of all time inside `Searcher.blastWins` (copy plus full blast per candidate column at every node with a ready blast); 18% `HandEval.evaluate`; 13% `Board.account`. So the winning-blast scan, not the evaluation, was the main cost on every size.

| Commit | Change | Effect (knps 8x12, 15x11, 15x22; load differs between rows) |
|---|---|---|
| c81c42d | base | 537, 342, 203 |
| ac2ae03 | `Board.blastWins`: exact test without a copy | 881, 777, 434 |
| 95c1653 | `blast` updates only windows of changed cells; one packed accounting table | 1506, 1398, 905 |
| e703ccf | window sums kept per drop level on the board (`WindowSumEval`), threat scans skip columns without threats | 1483, 1405, 1164 |
| d5baad7 | `blastWins` checks only lines a fallen disc can complete; column scan at the top of `search` skips empty columns; one table for the ordering potential | 1686, 1795, 1529 |
| 04af256 | threat-column bit sets, one blast scan per node, cached geometry arrays | hot: no gain beyond noise; first 80 ms cold: +30-40%, kept |
| 8d5819d | `blastWins` prunes discs too high for a line; clock read every 64 nodes | side by side: 8x12 0%, 15x11 +6%, 15x22 +8%; allocation falls |
| 86a3446 | live-window count on demand (only the leaf VCF asks), accounting only near a threat | side by side: +4-6% |

Tried and dropped: writing a drop's window codes only when needed (most drops are taken back at leaves). Side by side 5-10% slower: only 60-70% of nodes reach an evaluation, so the many interior nodes pay for it twice.

Remaining profile on 15x22 is flat: `search` itself 19%, make and unmake 22%, evaluation 16%, blast children 13%, blast scan 8%, ordering 7%. I found no further item worth more than a few percent that stays out of the search stream's code.

## Real gain left for the shipped configuration?

Profile of the merged code with generic v-wd tables (JFR, 2 ms, 15x11; 15x22 is alike): `search` itself 22%, evaluation 25% (window sum 15%, of which `dropChange` 10% and `fullSum` 3%; threat features 9%), make and unmake 19%, blast scan 8%, blast children 8%, ordering 5%, TT probe 4%. Candidates and what each could give at most:

| Candidate | Upper bound | Verdict |
|---|---|---|
| Canonical tables with the colour swap built in, so `dropChange` skips `swap12` | 1-2% | not done |
| Counting valleys in make and unmake | below 1%, moves the cost into make | not done |
| Base sum of a blast child from the parent's sum and the changed windows | 2-3%, needs a board link and a new path | not done |
| TT as one array instead of two | under 1% | not done |

Generic at 0.82 of hand comes from these same items; nothing large is hiding there. `Searcher` itself is the largest item but its lazy move scoring (+15-18%) is already in the search stream, and other streams edit that file now. I stop here: no further hot change. A negative result for this question.

## Any board size

Fixes (8f895f3):

- Clock reads follow the window count: `Geometry.clockMask` in `Vcf`, 64 nodes up to 1000 windows, else 16. In `Searcher` the time stream made the same change while I did (`clockMask`, and `pollMask` 3 above 3000 windows); I took its lines as they are, so the two branches merge cleanly. The seen sizes keep 64, so allocation stays where it was. With that `pollMask`, a node budget on boards above 3000 windows stops a few nodes earlier than base's; the tree is the same. My signature tables for 40x40 and 64x64 are from before I took those lines.
- The drop stack and its log have a fixed size (128 drops); past that the stack restarts from the position and the next evaluation takes a full sum. A `new Board` per call costs 12 kB on 15x22 (was 18), 26 kB on 40x40 (was 104), 54 kB on 64x64 (was 280). A board's memory still grows with cells and windows through `wc`, `cell` and `thr`.
- `Vcf` column scans visit only columns with threats (1266670): threat-search nodes per second 8x130 20k to 54k, 6x255 60k to 254k, 15x22 231k to 289k, signature same. Search nps unchanged. On wide boards the threat search used to take a large part of a short call.
- `BoardSizesTest` (new): second word of the threat-column bit set (8x64, 8x65, 8x128, 8x129, 2x255; 2636 threat columns past the first word in the run), very tall and very wide boards against `NaiveRules` and against a rebuild, 400+ drops past the stack's size up and down with evaluations equal to a fresh board, window sums at +-32767 per window on 64x64 (no wrap, clamped result right), search and tactics legal on 24 sizes from 1x1 to 4096x1, and the bot itself on boards past its limits (2x256, 1x300, 65x65, 1x5000, 5000x1). All pass.

Audit of the rest, no change needed:

| Limit | Finding |
|---|---|
| Threat-column bit set | `long[]` sized by columns; `nextThreatColumn` crosses words; tested |
| `thr` as bytes | at most 4 x 6 = 24 windows through a cell, fine |
| `wc`, log as shorts | codes below 3^6 = 729; the engine's K is the constant 6 (`CONNECTED_QUARKS_REQUIRED_TO_WIN`) |
| Window sums | `int`; 14514 windows x 32767 = 476 M at 64x64, the worst board with a model; tested |
| Search above 255 columns, or above 4096 cells | no search (tactics) or no model (`safeColumn`); legal moves tested. Weak but safe. Field for columns would allow 510, I left it at 255 |
| `MAX_PLY` 96, `MAX_VPLY` 64 | depth-bound, not size-bound |
| 1000-turn cap in search and threat search | engine constant, independent of size; boards above 1000 cells end by it |
| `s.rows * s.cols` in `QcBot` | int overflow only above 46340 x 46340, where `Snapshot` could not allocate anyway. Not handled |

Table build cost on an unprepared size (`SpeedBench --build`):

| Size | Windows | Total ms, fresh JVM, hand tables | Total ms after the bot's constructor | Geometry kB | Eval kB |
|---|---|---|---|---|---|
| 6x7 | 23 | 13 | 1.2 | 13 | 9 |
| 9x20 | 335 | 14 | 1.5 | 61 | 12 |
| 20x9 | 335 | 13 | 1.3 | 60 | 12 |
| 25x25 | 1800 | 15 | 2.1 | 280 | 23 |
| 40x40 | 5250 | 21 | 3.3 | 795 | 50 |
| 64x64 | 14514 | 27 | 6.9 | 2175 | 122 |
| 8x130 | 2140 | 17 | 1.8 | 345 | 26 |

The first `Weights` use in a fresh JVM adds about 150 ms and 17.6 MB, but `QcBot`'s constructor does that (`Weights.preload`). The tables of an unprepared size are built in the second call, so these costs are not at risk of losing a move.

Latency at a 25 ms stop (`SpeedBench --limit 25 --calls 300`, root VCF on, table kept, generic v-wd or gen003, under the lock, one JVM going through the sizes in this order). Overrun = time past the stop, in ms; the clock has 1 ms steps, so about 1 ms of it is the clock. Gap = longest time between clock reads in the search, ms.

| Size | First 3 calls: max overrun, max gap | Calls 3-99: max overrun, max gap | Calls 100+: p99 overrun, max overrun, max gap, knodes per call | Threat search past a 1 ms deadline, max ms |
|---|---|---|---|---|
| 6x7 | 4.1, 2 | 2.0, 2 | 2.0, 2.1, 2, 75 | 0 |
| 9x20 | 1.5, 2 | 1.9, 2 | 2.2, 2.4, 2, 51 | 0 |
| 20x9 | 0.9, 2 | 2.1, 2 | 1.8, 1.9, 2, 47 | 0 |
| 25x25 | 0.6, 2 | 1.6, 2 | 2.0, 2.0, 2, 38 | 0 |
| 40x40 | 0.9, 2 | 1.8, 4 | 2.0, 2.1, 2, 50 | 0 |
| 64x64 | 1.3, 2 | 2.1, 2 | 1.6, 1.7, 2, 33 | 0 |
| 8x130 | 1.5, 2 | 1.7, 2 | 1.8, 1.9, 2, 29 | 1.4 |
| 130x8 | 0.9, 2 | 1.8, 2 | 2.2, 2.3, 2, 35 | 0 |
| 6x255 | 0.9, 2 | 2.1, 2 | 2.0, 2.2, 2, 27 | 1.5 |
| 255x6 | 1.0, 2 | 2.0, 2 | 2.0, 2.0, 2, 33 | 0 |
| 15x22 | 1.2, 2 | 2.0, 2 | 1.9, 2.0, 2, 37 | 0 |
| 15x11 | 1.2, 2 | 2.0, 2 | 1.9, 2.1, 2, 40 | 0 |
| 8x12 | 1.6, 2 | 1.8, 2 | 2.1, 2.1, 2, 42 | 0 |

Each size in a fresh JVM without any warm-up, the worst case (not under the lock, machine busy): first calls run up to 3.6 ms over and gaps reach 9 ms on 64x64, 4 ms on 40x40 and 8x130; before the size fix 34, 8 and 10 ms. After `new QcBot()` (its 1 s warm-up) the first calls on the unprepared sizes 25x25, 40x40, 64x64, 6x255 ran at most 1.7 ms over with gaps of at most 3 ms. So polling is safe at 25 ms on these sizes; a call limit near 25 ms still needs the stop to leave those few ms.

Real protocol on extreme sizes (`MatchMain`, under the lock, 5 threads, 4 random opening plies, data `qc-bot/data/night/speed/extreme.jsonl`): the bot at 8f895f3-level code on the merged time plan and tables against the base jar, 40 games, 8 per size. Lost turns 0 for both bots, no invalid column, no invalid special, no engine crash, longest first call 94 ms. Score of A 85.0% (Elo +301, +196..+491): 6x7 4.5 of 8, 9x20 6.5, 20x9 8, 25x25 7, 40x40 8. This compares more than speed (time plan, tables); it is a safety check first.

A second run on the most extreme sizes (commit 1266670 code, 20 games, 4 per size, `extreme2.jsonl`): no invalid column, no invalid special, no engine crash. Lost turns A / B: 64x64 6 / 12, 130x8 2 / 2, 8x130 0 / 2, 6x255 0 / 6, 1x1 0 / 0, nearly all in the first 40 s of the match JVM, the cold phase with 5 games at once. Longest finished first call 84 ms for A, 108 ms for B. Score of A 60% in 20 games (Elo +70, -60..+226): 64x64 2 of 4, 8x130 1, 130x8 4, 6x255 3, 1x1 2. Median depth 3, 992 knodes/s. Too few games to say anything but that the bot plays safely there.

Allocation per call on boards above 3000 windows: p99 255 KB, max 593 KB in that run. With `pollMask` 3 the search reads the clock every 4 nodes there, and each reading allocates a `Date` unless the JIT removes it. The clock has 1 ms steps and a node takes about 1 us, so that is far more reads than can matter: at `pollMask` 15 the longest gaps were the same (2 ms hot, under 4 ms after the warm-up). I suggest `g.windows <= 3000 ? 15 : 3` become plain 15. It is the time stream's line, so I left it.

Not changed, for the owners of `Searcher` and the time plan: the root threat search budgets `ROOT_VCF_NANOS` 10 ms and `OPP_VCF_NANOS` 5 ms are literals. At a 25 ms stop they may take 15 of the 25 ms when the threat search does not finish on its own, mostly on very wide boards. `hard / 8` and `hard / 16` would equal them at an 80 ms stop. That changes behaviour at other stops, so it is not in this stream.

## Checks

- `SpeedBench` signatures, per position: all sizes in the tables above, identical to base (hand or gen003) and, with generic tables, between builds.
- Whole-game differential (`FastMatch`, 3000 nodes, sorted result lines equal between base and new classes, on 5c45056): 15x11, 8x12, 15x22 with 30 games each with root VCF off and 20 games each with default options; 12x16, 10x10, 7x9, 20x20 with 20 games each. All identical.
- `BoardDifferentialTest` also checks `blastWins`, `anyBlastWins`, `nextThreatColumn` against copy plus blast (300000 positions in the suite; one longer run with 4 million positions, 39 million blasts, 50823 winning blasts, passed). `WindowSumTest`: 183000 evaluations on random play equal a fresh board. `BoardSizesTest`: see above.
- All other test classes pass on de9425e, `UploadRulesTest` included. TESTS

## How to reproduce

From the worktree, `CP="build/classes/java/main;build/classes/java/tools;libs/core-2.0-SNAPSHOT.jar"`. For the base build use `qc-bot/dist/qc-bot-gen003-safe-80ms.jar` in place of the first entry (its classes are c81c42d).

- Hot and signature: `java -cp "$CP" ch.qc.tools.SpeedBench --sizes 8x12,15x11,15x22 --nodes 40000 --warm 2 --rounds 6 --eval hand --sig sig.txt`; `--eval bot` for the embedded tables, `--eval FILE.qcw` for others. Compare `sig.txt` with `diff`.
- Cold: `python src/tools/python/coldcurve.py JAVA CP_A CP_B suites/bench-positions.txt 5 6 2000 15x22 15x11 8x12`.
- Build cost: `java -cp "$CP" ch.qc.tools.SpeedBench --build --sizes 40x40 --eval bot [--after-bot]`, one JVM per size for the cold number.
- Latency: `java -cp "$CP" ch.qc.tools.SpeedBench --limit 25 --calls 300 --sizes 40x40 --eval bot [--after-bot]`, through `rtrun.py`.
- New positions: `--extract OUT --unseen SIZES` (short self-play), `--fill OUT --unseen SIZES` (tall random boards). `suites/bench-positions.txt` holds all of them.

## Things other streams should know

- `Board.blast` and `Board.blastWins` assume the position holds no line yet, which every searched position satisfies; on a position with an old line in an untouched window `blast` no longer reports it.
- `Board.live3` is now a scan over all windows. Only the leaf VCF (off) uses it.
- An evaluator with a new window family must implement `fullSum` and `dropChange` of `WindowSumEval`. `GenericEval` tables are untouched; only how the sum is computed changed (the eval stream may change tables freely).
- `Searcher` edits: `blastWins` replaced by `Board.anyBlastWins`, column scan via `nextThreatColumn`, `potential` via one `gain` table (identical values), `poll` reads the clock every 64 nodes. `Vcf` and `Tactics`: only the blast test.
- Allocation per call, p99 in the match logs: 15x22 90 KB, 15x11 67 KB, 8x12 46 KB (base 43 KB on 15x22). It was 122 KB at 04af256 before the clock mask.
- Real-time harness: the official engine crashes when a bot requests a special after a first call that the engine has already timed out: `col` is still -1 and the engine indexes column -1. In these matches that ended 22 of 120 games on 15x11, 7 of 120 on 15x22, 0 of 80 on 8x12, both builds alike (A 17, B 12), under the machine load of tonight. The bot's own clock had said under 85 ms: the call thread started late, or stalled after the clock read, so `SAFE_FINISH_NANOS` does not catch it. The bot-side cause is in `QcBot` (time stream) and is not changed here. What the server does on this exception is not known; export 08 shows no such error text in 1218 games. `MatchMain` in this branch survives it and scores a loss for the mover (`reason: crash`), so long runs no longer die.
