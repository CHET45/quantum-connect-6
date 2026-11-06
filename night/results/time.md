# Time stream results

Branch `night-time`, worktree `night/wt-time`. Started 2026-11-04 01:20.

## Server log facts found (export-08, read with python -I)

- Log lines read `ERROR 1 c.c.q.w.m.adapter.jars.BotMatchRunner`: pid 1, a Spring Boot web app in a container, so one long JVM.
- Games run one at a time: 0 of 1514 adjacent games overlap in their log spans. Other games are not a load source.
- The server has a memory monitor that wakes every 100 ms and logs used heap from about 160 MB up. It works as a stall sensor. 4% of its ticks are 20 ms or more late.
- Heap climbs about 5 MB/s during a game, so a young GC is rare (about one a minute).

## Harness: `ServerSim` (src/tools/java/ch/qc/tools/ServerSim.java)

One JVM, games one after another, a fresh class loader per bot per game, `Experiment.executeWithTimeout` for both calls, a 100 ms sleeping monitor. Report with `src/tools/python/simreport.py`.

What did not reproduce the server: `ActiveProcessorCount=1` or 2 with CPU affinity on Windows defaults. On Windows, HotSpot runs its compiler threads at raised native priority, so they starve the search thread: about 130 lost turns per 1000 at all turns, and second calls time out too. Linux has no such priorities.

What did reproduce it: `-XX:-UseThreadPriorities -XX:ActiveProcessorCount=2`, process affinity on two logical CPUs (mask 0x14), `-Xmx512m`, self-play on 15x11 from the empty board. All threads get a fair share, as on Linux.

Calibration, lost turns per 1000 own turns:

| Jar, stop | Own turn | ServerSim (lost/own turns) | Server |
|---|---|---|---|
| 3.1, 80 ms | 0-2 | 0 / 48 | 20.5 |
| | 3-9 | 45 (5/112) | 28.8 |
| | 10-19 | 31 (5/160) | 25.9 |
| | 20-29 | 12.5 (2/160) | 41.8 |
| | 30-59 | 17 (8/472) | 16.6 |
| | 60+ | 0 (0/515) | 0 of 317 |
| 2.2, 62 ms | 0-2 | 0 / 72 | 0 |
| | 3-9 | 18 (3/168) | 23.7 |
| | 10-29 | 2.1 (1/480) | 6.5 |
| | 30-59 | 1.5 (1/669) | 3.4 |
| | 60+ | 0 / 944 | 0 of 253 |
| 2.1, 45 ms | 0-29 | 0 / 720 | 6-10 |
| | 30-59 | 0 / 692 | 0.7 |
| | 60+ | 0 / 908 | 0 of 740 |

Reading: shape and size match at 62 and 80 ms, from a first small sample (8 to 12 games each). The harness does not produce the server's 6-10 per 1000 at 45 ms in early turns. So there is a floor of stalls on the server that no budget here can show, and the harness judges only the budget-dependent part. The server's monitor is also late more often (4% of ticks by 20 ms or more, here 1%). In this harness the mechanism is that the search thread loses its CPU to the compiler threads for a scheduler slice, so calls run 15-30 ms past the stop. The server's mechanism may differ even though the numbers agree.

More runs of the 80 ms stop in the same conditions (t3 8 games, c31 10 games of the 3.1 jar, e80 10 games of the new code with `s=80`), pooled, lost turns per 1000:

| Own turn | ServerSim | Server 3.1 |
|---|---|---|
| 0-2 | 12 (2/168) | 20.5 |
| 3-9 | 33 (13/392) | 28.8 |
| 10-19 | 21 (12/560) | 25.9 |
| 20-29 | 37.5 (21/560) | 41.8 |
| 30-59 | 12.6 (21/1669) | 16.6 |
| 60+ | 3.5 (9/2578) | 0 of 317 |

So the harness reproduces the early pattern of the server at 80 ms well enough to compare budgets. Runs differ strongly from each other (own turns 20-29: 2, 19 and 0 lost in 160-200 turns), so a single run of 10 games says little about one bucket; compare totals below turn 60.

Same conditions but self-play on one CPU or with Windows native thread priorities on: far too many lost turns (130 per 1000 and more), not used.

## What the JIT does in a cold game (`-XX:+PrintCompilation`, `-Xlog:deoptimization=debug`, two 3.1-like bots, 15x11)

- About 5.7 s of C2 time per bot in its first two games' class copies; compiler threads are busy until own turn 35-40.
- `Searcher.search` is compiled by C2 about 13 times per class copy and `HandEval.evaluate` about 11 times. No method is near the 8000-bytecode limit (`search` 1584).
- Nearly all deoptimizations are `unstable_if`: a branch never taken so far, then taken, so the code is thrown away and compiled again.
- Own turns 10-19 search about half the nodes of turns 3-9 (7.5k against 13k), which is the blast paths appearing at own turn 11.
- 1 s of warm-up in the constructor halves JIT time per game (3.8 s against 8.1 s by own turn 60, both bots) and raises nodes per call in own turns 0-29 from about 12-15k to about 30k.

## What a lost turn costs

`FastMatch --forfeitA 0.02 --forfeitTurns 60`: same bot on both sides, A forfeits 2% of its own turns below 60, fixed nodes.

| Size | Pairs | Score of A | Elo | 95% CI |
|---|---|---|---|---|
| 15x11 | 200 | 43.9% | -43 | -72 to -14 |

So about -21 Elo per 1% of lost turns on 15x11. Against that, the plan's value of time: +108 Elo per doubling on 15x11.

## Facts about blasts and the stop

- A first call that timed out leaves `col = -1`, and a special requested in the second call then crashes the engine (design R10, R15). The bot cannot see whether it timed out, so a blast is refused when the first call took over 85 ms. So a stop above about 83 ms would refuse nearly all blasts. Hot stops stay at or below that.
- In 3.1 a refused blast still dropped a disc in the blast's column, a move the search never looked at. Now it plays the best drop of the search.

Lost-turn cost, other sizes (same setup; 8x12 with gen003, 15x22 at 10000 nodes):

| Size | Pairs | Score of A | Elo | 95% CI |
|---|---|---|---|---|
| 8x12 | 200 | 43.8% | -44 | -66 to -21 |
| 15x22 | 100 | 48.0% | -14 | -57 to +29 |

The coordinator asked to merge `night-speed` (3-7x faster, blast code separate), so everything below this line was measured on the merged code, commit 54e21e7 or later, unless it says otherwise. On the merged code the JIT picture stays: one game of two bots still shows 67 deoptimizations (`unstable_if` 54, `profile_predicate` 11), and about 7 s of JIT time for both bots by own turn 30, so the compiler threads stay busy for the same stretch of the game. Nodes per call are 2-4x higher.

## Crash path closed (commit ac22ca9, requested by the coordinator)

A special after a timed-out first call crashes the engine, seen once in 240 games of the eval stream's real-time match on 12x16. Fixes in `QcBot`, usable by any branch:

- `useSpecialMove` refuses a blast, and skips its search, when it starts 90 ms or more after `accelerateQuark` began. A timed-out first call makes the second start at 100 ms or later, less however late the first thread started. The 90 keeps a margin for that and for Date's 1 ms steps.
- A blast is asked for only if the first call took at most 75 ms (was 85), which tolerates a thread start up to 25 ms late.
- A search whose current choice is a blast stops at 70 ms (`Searcher.setBlastStop`), so it stays inside those 75 ms. A blast that comes later plays the best drop of the search (`Searcher.lastBestDrop`).
- As a result, stops above 85 ms no longer rule out blasts.

What remains: a first-call thread that starts more than 25 ms late, with a blast chosen and a prompt finish, can still crash. Tests: `TimePlanTest.secondCallThatStartsLateNeverAsksForABlast`, `aChosenBlastIsDroppedIfTheFirstCallMayHaveTimedOut`. `ServerSim` now counts this path per game (in `simreport.py`: engine crash path).

## Merged code, first results in ServerSim (15x11 self-play, 10 games, 2 CPUs)

| Run | Spec | Lost per 1000, own turns below 60 | 95% CI | Own turn 3-9 | Nodes per call, own turns below 30 |
|---|---|---|---|---|---|
| m80 | s=80,f=30 | 28.3 | 20.3-39.3 | 121 | 71k |
| mw1000 | s=80,f=50,c=1,w=1000 | 3.3 | 1.3-8.5 | 21 | 132k |
| mg83 | s=83,f=50,c=1,g=96@85@100 | 19.2 | 12.9-28.7 | 79 | 94k |

The merge moved lost turns earlier (own turns 3-9). The 1 s warm-up in the constructor removes most of them and almost doubles nodes per call early on, the largest effect so far.

## Provisional defaults, published for a checkpoint (commit 554a51a)

`Timing.DEFAULT`, chosen by the switch `Timing.CONSTRUCTOR_WARMUP_NANOS`:

| | With warm-up (`qc-bot/dist/night/qc-bot-time-warm1000.jar`) | Without (`qc-bot/dist/night/qc-bot-time-nowarm.jar`) |
|---|---|---|
| Constructor warm-up | 1000 ms of self-play on prepared sizes | none |
| First-call stop by own turn | 70 ms below 10, then 80 | 50 below 10, 60 below 20, 70 below 30, 75 below 60, then 80 |
| First call of an instance | 50 ms at most | 30 ms at most |
| Empty board | centre, no search | same |
| Stall sensor | limit 96 ms, memory 85% per turn | same |
| Second-call search | 85 ms from own turn 10 | 85 ms from own turn 30 |

These are provisional: they were not gated yet. The numbers behind them, 15x11 self-play in ServerSim, 10 games each: warm-up 1000 with 80 ms stop 3.3 lost per 1000 own turns below 60 [1.3, 8.5]; warm-up 500 with 70@10/83 and second call from turn 10: 8.6 [4.7, 15.8]; no warm-up with 50@10/60@20/70@30/83: 6.8 [3.3, 13.9]; 80 ms and nothing else: 28.3 [20.3, 39.3]. Runs differ a lot from each other, so treat small differences as noise.

`Searcher.poll` was merged with the newest `night-speed` as the coordinator resolved it: the clock block runs every 64 nodes, the node limit and token checks at every poll.

Second-call search at fixed nodes (`FastMatch --ponderA N`: N nodes on the move, N more on the position after it, same bot on both sides):

| Size | Nodes | Pairs | Score | Elo | 95% CI |
|---|---|---|---|---|---|
| 15x11 | 20000 | 200 | 55.5% | +38 | +8 to +69 |

## After the machine crash (03:20, driver bugcheck, not related to this stream's work)

Resumed 09:45 from `night-main` 8f31c96, which holds the speed work, generic tables and the provisional defaults. The merged tree builds a jar byte-identical to `qc-bot/dist/night/qc-bot-4.0-rc1.jar`. Fallback built from the same tree with the one-line switch at 0: `qc-bot/dist/night/qc-bot-time-rc1-nowarm.jar`; `UploadRulesTest` passes on both. Runs in progress when the crash came are lost, and those measured on older code are kept above only as history.

## Morning: second-call bug, measured limit, any size

**Bug found by the coordinator, fixed in 5549e62.** My crash fix (75 ms for a blast) also blocked the second-call search whenever the first call used its 80 ms stop, so the search almost never ran in runs `pw`, `pn` and in rc1. Only the blast is gated on that limit now.

**Second-call search at fixed nodes on merged code with generic tables** (`FastMatch --ponderA N`, A spends N nodes on its move and N more on the position after it):

| Size | Nodes | Pairs | Score | Elo | 95% CI |
|---|---|---|---|---|---|
| 15x11 | 40000 | 150 | 56.7% | +47 | +12 to +82 |
| 8x12 | 40000 | 150 | 55.3% | +37 | +14 to +60 |

**rc1 (search effectively off) against 3.1, ServerSim, 2 CPUs, 15x11, 20 games, same games, machine under heavy load from other streams:**

| Bot | 0-2 | 3-9 | 10-19 | 20-29 | 30-59 | 60+ | Below 60 | 95% CI | Nodes below turn 30 |
|---|---|---|---|---|---|---|---|---|---|
| rc1 | 100 | 121 | 5 | 40 | 7 | 5.5 | 30.8 | 22-42 | 97k |
| 3.1 | 167 | 257 | 160 | 45 | 21 | 5.5 | 84.7 | 70-102 | 13k |

Score of rc1: 85% (16-2-2), against the older hand evaluation of 3.1. Absolute rates are far higher than overnight (3.1 self-play now 83 per 1000 below turn 60, overnight 29), because the machine is busier, while the monitor's late ticks (4-8%) now match the server's 4%. Read only the ratio between bots in the same run.

**Measured call limit, commit 058cfbf.** All budgets are now given at 100 ms and scaled by `CallLimit`:

- A budget keeps its absolute distance from the limit, because what makes a call late does not grow with the limit. Floors for short limits: 25% for the first call, 40% for the second-call search. Above 400 ms the distance grows slowly.
- The first turn never searches (limit and tables unknown; at most one disc on the board). Its second call builds the tables for this board and watches the field for 85 ms, playing warm-up games on this board meanwhile. If the engine gives up before then, the field changes: that time, rounded down to a round limit, is the limit. At 100 ms no timeout is logged.
- A limit above 100 ms cannot be told apart from 100 ms without running into a timeout at 100 ms. So that probe only starts if an opponent's whole turn is above 110% of the limit + 5 ms, or with the switch `up=1` (`Timing.probeUpward`, off by default). It logs one timeout line and costs one limit of time, once per game. It reads the field and ends as soon as the field changes, so it takes nothing from the opponent.
- 30 s stays an assumption and can't be measured in advance. A game that outlives it doubles it. `MatchClock.turnCap` keeps long limits from using up the clock before the per-ply estimate has samples.
- The list of prepared sizes is gone: every size is built the same way. Warm-up in the constructor still needs some boards, since the real one is unknown then; they only show the JIT a range of shapes.
- Clock reads follow the board in `Searcher` (`clockMask`): every 64 nodes up to 1000 windows, then more often. This is a minimal change in the search stream's file.

Not probed by design: a limit between 85 and 100 ms counts as 100, and the stop is then protected only by the sensor and lost-turn cut.

The 12x16 gate of rc1 (17 timeouts in 60 games): in-bot time there never exceeded 90 ms, so those timeouts come from outside the bot (late thread start or hand-over under load), not from the unprepared size. Being re-measured quietly.

## Blast limits and the crash path (decision: the cap)

Real protocol, same games, 100 pairs per size, 5 threads, machine under the load that produces the bursts. A: blast stop 70 ms, finish 75 ms (`b=70@75`) less the stall sensed. B: stop 55, finish 60 (`b=55@60`) less the stall sensed.

| Size | Score of A | Elo of A | 95% CI | Crashes / blast requests A | B | Timeouts A | B |
|---|---|---|---|---|---|---|---|
| 15x22 | 45.5% | -31 | -77 to +13 | 1 / 915 | 0 / 964 | 277 | 233 |
| 15x11 | 51.2% | +9 | -32 to +50 | 0 / 581 | 0 / 601 | 40 | 63 |
| 8x12 | 49.8% | -1 | see log | 4 / 438 | 0 / 461 | 205 | 213 |
| Total | 48.8% | -8 | | 5 / 1934 = 2.6 per 1000 | 0 / 2026 (95% upper bound 1.5 per 1000) | 522 | 509 |

The higher limit does not get crashes near zero, and the cap costs nothing measurable, so the cap ships. A blast is asked for only if the first call took at most 60 ms at a limit of 100 ms; a search with a blast in the lead stops at 55 ms; a later blast plays the best drop and waits a turn. Both limits keep their distance to the limit: 60 ms at 100, 20 ms at 50 (floor 40%), 10 ms at 25, 160 ms at 200.

`ServerSim` earlier: of 985 calls that ended below 60 ms inside the bot, none timed out; between 60 and 85 ms, 0.5-0.8% did.

Blast comparison in the second call (design H10), fixed nodes, same setting as the second-call search but comparing drop and blast in the chosen column on blast-ready turns: 15x11 may cancel +34 [-3, +71], may also start +40; 15x22 may cancel +63 at 25000 nodes; 8x12 may cancel +12. Against the plain second-call search on the same sizes (+47, +14, +37) nothing stands out, so it is not built.

The trigger for limits above 100 ms: at a real 200 or 500 ms limit against a fast opponent (RandomBot, 6 games each) the bot stays at its 100 ms plan: it believed 100 in all games. This is a limitation: if the limit rises and the opponent replies fast, the bot uses only the time of a 100 ms limit. That costs it nothing against such opponents. Against an opponent that uses the longer time the trigger fires and it learns the limit; details below.

## False raises of the limit (found through the coordinator's rc2b logs, fixed in 394abe4 and a9d6ce8)

rc2b showed first calls of 130-190 ms under load. Cause, read from the code and consistent with those logs, not separately reproduced: one late sample raised the limit. A stall that ended after the engine gave up made the first-turn probe, or the upward probe started by one long opponent turn, read 150-190 ms; rounding made that a limit of 150 or 200.

Fix:
- The probe for a shorter limit looks at the time first: 85 ms or more reads as the 100 ms prior, whatever the field shows.
- A limit above 100 needs three samples, each at least 130 ms and within 15% of the first. Any other sample or a lost turn closes the question for the game and goes back to 100.
- The probe upward starts only after 5 opponent turns in a row of about the same length (within 15%), each above 110% of the limit + 5 ms. A forfeited opponent turn closes that for the game.
- Two own lost turns since the last measurement measure the limit again, which also repairs a late first measurement that made the limit look too long or too short.

Test: an opponent that times out on 70% of its first calls and 20% of its second, real limit 100 ms, 15x11 and 8x12, cold, 2 CPUs, up to 3 busy threads on them:

| Busy threads | Games | Believed limit (games) | Raises above 100 | Lost per 1000 below turn 60 |
|---|---|---|---|---|
| 0 | 10 | 100 (10) | 0 | 11 |
| 2 | 10 | 100 (7), 50 (2), 30 (1) | 0 | 155 |
| 3 | 10 | 100 (9), 40 (1) | 0 | 179 |

Those games are short (about 12 own turns), because the opponent loses quickly. The monitor was late by 20 ms or more on about 90% of ticks with busy threads, far above the server's 4%.

## Warm-up against none, same games (lim6, first results)

W: warm-up 1000 ms, 60@10/80, second call from turn 10. N: no warm-up, 50@10/60@20/70@30/75@60/80, second call from turn 30. 15x11, 20 games, 2 CPUs.

| | Below 60 per 1000 | 95% CI | 60+ per 1000 | Nodes below turn 30 |
|---|---|---|---|---|
| W | 9.3 | 5.2-16.5 | 8.4 | 106k |
| N | 4.2 | 1.8-9.8 | 9.9 | 64k |

Score of W: 65% (9-3-8).
