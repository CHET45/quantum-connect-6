# Server data and thinking time: independent analysis

Written 2026-11-04 by a second session working separately from the session that owns `qc-bot-safe`. Nothing in that worktree was changed; experiments ran on a frozen copy of its classes.

## Data

The upload `65a9be3c-data.zip` holds the same files as `export-06-8a0e07e7.zip` (recursive diff: identical; the zip hashes differ). The last snapshot is 21:21 UTC, so versions 2.3 and 3.0 are not in it. All 1054 games replay to a six by the recorded winner on their true board sizes. Draws are not exported.

## Results by board (decided games only)

| Board | Bot | W-L | Against the Savi line |
|---|---|---|---|
| 8x12 | 2.1 | 76-3 | Savi5 4-1, Savi6 1-2 |
| 15x22 | 2.1 | 78-2 | Savi7 5-2, Savi6 "Beat This" 7-0 |
| 15x11 | 2.1 | 65-5 | Savi 7 1-5 |
| 15x11 | 2.2 | 58-9 | Savi8 0-7; also 5-2 against lalalalabot |

The large deficit is on 15x11 against the two newest Savi builds: 1-12 over 13 games, 5 of them with a lost turn of ours.

## Time per move

Method: when a bot times out on consecutive turns, the gap between its log lines is its lost turn plus the opponent's whole move. The lost-turn baseline comes from fast opponents: about 101 ms for Testing_v2, about 1 ms for "I'm too young to die". Samples come from early turns and n is small.

| Bot | Median whole-turn time | Lost turns per 1000 own turns |
|---|---|---|
| Savi8, 15x11 | 96-99 ms | 3.1 |
| Savi 7, 15x11 | 90 ms | 4.9 |
| Savi7, 15x22 | 98 ms | 4.4 |
| 2.1, 15x11 | 46-65 ms | 4.5 |
| 2.1, 15x22 | 74 ms | 10.9 |
| 2.2, 15x11 | 50 ms | 7.9 |

The data cannot split a Savi turn between `accelerateQuark` and `useSpecialMove`. Savi8's first turn took about 1 ms in the 4 samples at own turn 1.

## Value of time (real-time self-play)

Hot JVM, one thread per game, loaded CPU, against the current search (45 ms, EBF stop 3). Lost turns are not modelled.

| Board | A | Pairs | Score of A | Elo | ms/call A vs B | Depth A vs B |
|---|---|---|---|---|---|---|
| 15x11 | 90 ms, no EBF stop | 40 | 65.0% ± 10.2 | +108 | 85.6 / 29.8 | 6.51 / 5.69 |
| 15x22 | 90 ms, no EBF stop | 30 | 63.3% ± 12.2 | +95 | 89.8 / 31.3 | 4.62 / 4.05 |
| 8x12 (gen003) | 90 ms, no EBF stop | 40 | 55.0% ± 8.2 | +35 (not significant) | 81.4 / 27.0 | 8.94 / 7.70 |
| 15x11 | 45 ms, no EBF stop | 40 | 51.2% ± 10.4 | +9 (not significant) | 43.9 / 29.6 | 6.27 / 6.03 |
| 15x11 | skip `blastWins` at depth ≤ 0 | 40 | 54.4% ± 10.4 | +30 (not significant) | 29.4 / 29.4 | 6.50 / 5.92 |
| 15x11 | same, plus blast reduction 2 | 40 | 49.4% ± 10.3 | -4 (not significant) | 28.8 / 29.7 | 6.62 / 6.02 |

Self-play odds usually overstate the gain against other bots. Even the full +108 would not close a 1-12 record.

## Latency and JIT

Probe: one JVM, a fresh class loader per game, `-XX:ActiveProcessorCount=2`, shipped 3.0 jar. It assumes the server reloads the jar per game, which is not confirmed.

- No timeouts in 14.8k calls. With the 45 ms stop the slowest call took about 75 ms inside the bot and 79 ms around the call, so the server's lost turns were not reproduced and their cause stays unproven.
- Cold against hot differs by about 1.5-2x in mid-game nodes. A 2 s warm-up closes most of that gap but mid-game speed stays far below the opening's, so JIT is not the main loss.
- Node counts of aborted searches are multiples of 256, the poll interval. At cold speeds of 60-80 µs per node, inferred from calls such as 768 nodes in about 65 ms, a poll can come 15-20 ms late.

## Other checks

- Memory lines: at most one per game, usually before the game's timeouts; 457 of the 640 games with one have no timeout. The rate follows the time window rather than the bot. No harm shown.
- Top games are slow: the winning cell stood as a six-cell for a median of 28 plies on 15x11 (n=10) and 20 on 8x12 (n=8), against 2 in games with weak bots. On 15x22 the "strong" group is mostly games against a weak Savi build, so that row says little.
- Threat parity did not beat the simpler "one side alone has a floating threat" as a predictor; samples are small. At 70% of the game on 15x11 that rule was right in 9 of 10 cases.
- Openings repeat: Savi 8 and Savi 7 repeat their reply in 93-94% of positions seen twice in the first 40 plies, we in 87-90%. As first player on 15x11 we played column 3 at ply 13 in 5 games against the Savi line and lost all 5; the one win played column 5 (n=1).

## Recommendation

1. Poll the clock more often, so a late poll cannot cost much.
2. Raise the budget in steps, for example to 70 ms, and compare lost turns per 1000 on the server with Savi's 3-5 before going higher.
3. Keep the learned evaluation for 15x11 as the other half of the work. Time alone cannot explain a 1-12 record. That the hand evaluation is the rest of it is a hypothesis: the same hand evaluation went 5-2 against an older Savi7 on 15x22.

Not measured: the cost of a lost turn, which is what decides how far the budget can safely go.

## Files

`scripts/` holds the analysis scripts (`replay.py`, `skips_mem.py`, `losses.py`, `zugzwang.py`, `think_time.py`, `think_fit.py`, `time_table.py`, `cold_report.py`, `cold2_report.py`), the runners and `patch_searcher.py`. `harness/` holds `ColdProbe.java`, `NodeOdds.java` and `TimeMatch.java`, which compile against `qc-bot-safe` classes after `patch_searcher.py` is applied to a copy of `Searcher.java`. `match1.txt` and `match2.txt` are the raw match outputs.
