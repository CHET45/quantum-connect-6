# Reproducing Savi from server logs

Spike, 2026-11-04. All code here is throwaway. Nothing in `night/`, `qc-bot/` or `qc-bot-safe/` was changed, and nothing is committed.

## Answer

No. Savi cannot be copied from its game logs with what we have.

- **The fit failed.** I set the bar at +10 points of repeated moves from fitting an evaluation to Savi's moves. It gave 0 to -6.6 points at real search.
- **The best copy is our own engine plus a book of Savi's moves.** It repeats 69-70% of Savi's moves on held-out games. Our bots repeat 75-79% of their own moves through their own engines, so that is the practical ceiling, not the 93% I first stated.
- **Outside the book the copy repeats about 60-62%.** Savi's evaluation stays unknown: its agreement curve has no peak at any budget up to 2.4M nodes, while each of our bots has a clear one.

**For the 17:00 jar: this gives no reason to change the choice.** Ship nothing fitted. The useful by-product is a way to measure how much search a jar really gets on the server (see Calibration).

## Hold-out table

Savi8 and Savi9 on 15x11. 22 of 134 games held out, 691 moves. The share of moves repeated:

| Engine | Nodes | No book | With book |
|---|---|---:|---:|
| hand | 3k | 55.0 | 62.1 |
| hand | 30k | 63.2 | 67.7 |
| hand | 150k | 64.7 | 69.8 |
| v-wd | 3k | 60.8 | 66.6 |
| v-wd | 30k | 64.5 | 68.2 |
| v-wd | 150k | 65.4 | 69.0 |
| g3 | 3k | 62.8 | 68.3 |
| g3 | 30k | 62.7 | 68.2 |
| g3 | 150k | 63.8 | 69.2 |
| fit on hand | 3k | 49.5 | |
| fit on hand | 30k | 58.8 | |
| fit on hand | 150k | 61.5 | |
| fit on g3 | 3k | 56.2 | 64.3 |
| fit on g3 | 30k | 62.7 | 68.5 |
| fit on g3 | 150k | 63.2 | 69.0 |

How far to trust these:

- A single figure has a binomial error of about ±3.5, and more in practice, because the moves come from only 22 games. Differences among the 66-70 entries mean nothing.
- Repeating a run moved a figure by up to 0.6 in this table, and up to 1.4 against earlier runs (hand 30k: 61.8, 63.0, 63.2). The search is not fully reproducible, because threads share a transposition table across positions.
- The book comes from the other games, folded by mirror. It covers 207 of 691 moves (30%) and is right on 87.9% of them, and it adds 4-7 points to every engine. Moves outside it: hand 150k 62.0%, g3 150k 61.2%, v-wd 150k 61.0%, v-wd 30k 59.7%, v-wd 3k 57.4%.
- The ceiling figures below include their openings, where moves are easier to repeat, so the fair gap outside the opening may be smaller than it looks.

## Ceiling: our bots against themselves

Each real jar replayed its own server moves at fixed node budgets. Its evaluation on these boards is the hand one. The peak marks the search it really got. Full curves are in `out/calib-real.txt`.

| Jar | Board | Peak | At budget | Mean nodes | Mean depth |
|---|---|---:|---:|---:|---:|
| 2.1 | 15x11 | 75.0 | 3k | 1.9k | 4.2 |
| 2.2 | 15x11 | 76.6 | 3k | 1.9k | 4.2 |
| 3.1 | 15x11 | 78.3 | 50k | 28.7k | 6.5 |
| 2.1 | 15x22 | 79.0 | 3k | 2.2k | 3.2 |

The curves are sharp. 2.2 falls from 76.6 at 3k to 59.5 at 50k, so the same evaluation at a budget about 16 times too large looks about as far from the bot as Savi looks from us. **So 60-65% alone does not show that Savi's evaluation is very different.** The missing peak is the better evidence.

Savi's curves stay flat. All moves, plain hand evaluation: Savi8 61.5, 61.2, 63.7 at 150k, 600k, 2.4M, and Savi9 59.9, 61.5, 58.5. By depth there is a jump at depth 6 and then a plateau (Savi8 d5 53.5, d6 62.5, d9 61.1), which suggests it sees at least that far. Its budget cannot be pinned from logs.

## Why the fit failed

The fit used 3392 positions from Savi8 and Savi9, 164 linear features over a base evaluation, depth 4, and 655 held-out positions.

- The held-out loss did improve (hand 1.76 to 1.38, g3 1.65 to 1.20), but top-1 did not, and at real search the fit on hand loses 3-6 points.
- Likely reason, not proven: the data is too thin to pin down the features that matter, and the fit learns the choices of a search shallower than Savi's.
- The largest weight is "me ready" (+106 in 1/256 logit), which fits Savi keeping its blast. But our 3.1 shows the same signal against its own engine (+0.148, t=7.4, against Savi's +0.080, t=10.4), so it is at least partly a habit of search, not a trait of Savi.
- No other simple feature explains the disagreements. Same-sign shares are only 54-63%.

## What is known about Savi

Details are in `fingerprint/fingerprint.md`; read the correction at its top first.

- It holds its blast. Blasts per ready turn on 15x11: Savi7 8.6%, Savi8 6.4%, Savi9 4.6%, against 2.1 20.4%, 2.2 19.8%, 3.1 11.9%.
- It opens in the centre and plays one stack line. It repeats its replies 90-93% in plies 0-20, so it is deterministic or close to it, though not a fixed table.
- It rarely steps into a proven loss within a 100k-node horizon: Savi8 3 of 2241 moves, Savi9 2 of 2340, against 3.1 12 of 2228, 2.2 23 of 2253, 2.1 31 of 2447. The counts are small and confounded, since Savi wins nearly every game and so meets less pressure.

## Matches

Fixed nodes, 15x11, server openings.

| Pair | Score | Elo | Server |
|---|---:|---:|---|
| copy (hand 150k) vs 2.2 model (hand 3k) | 98.3% ±2.3 | +708 | Savi8 7-0 |
| copy vs 3.1 model (hand 30k) | 74.4% ±8.5 | +185 | Savi9 3-2 |
| hand 600k vs 3.1 model | 87.5% ±9.1 | +338 | |
| g3 120k vs copy | 65.6% ±9.7 | +112 | |

The direction agrees with the server, on very few games. These matches prove little: the copy is our own engine at a larger budget, so it says nothing new beyond the existing gates against hand.

## Calibration: the useful by-product

Replaying a jar's server moves through its own engine shows how much search it really got. Local timeouts have not predicted the server, and this measure needs only a game export.

Warm JVM here against the server peak, same jar and same stop (`out/calib-time.txt`, machine loaded, 2 threads):

| Jar, stop | Nodes here | Depth here | Repeats here | Server peak nodes | Here / server |
|---|---:|---:|---:|---:|---:|
| 2.1, 45 ms | 13.6k | 5.5 | 68.5 | 1.9k | about 7 |
| 2.2, 62 ms | 15.3k | 5.6 | 68.1 | 1.9k | about 8 |
| 3.1, 80 ms | 48.1k | 6.5 | 77.4 | 28.7k | about 1.7 |

So 3.1 searched about 15 times more nodes than 2.x on the server, while here the gap is about 3.5 times. **I do not know why 2.x lost so much more.** One candidate is that 2.x stops early at a third of the limit and uses about 30 ms per call, so a cold JVM never gets compiled in time. Server load in those hours is another. Neither is tested, and the games differ too.

To calibrate a new jar after 17:00, rebuild `games.jsonl` with the new export using `merge_games.py`, then run from this folder:

```
java -cp BOT.jar;bin/calib;bin/all;CORE.jar ch.qc.tools.SaviCalib games.jsonl "NAME_REGEX" 15x11 5000,12000,30000,50000,80000,120000,200000 2
```

Before that, `SaviCalib.java` line 72 (`new Searcher`) has to build the jar's own evaluation. It builds `HandEval` with `Options.DEFAULT`, which is right for 2.x and 3.1, while rc4 uses the g5w tables. This has not been tried on any 4.0 jar. It needs about 30 games of the new jar on one board. The reference to beat is 3.1: about 29k nodes, depth 6.5.

## Recommendation

1. Do not change the 17:00 jar because of this work, and ship nothing fitted.
2. Stop trying to copy Savi's evaluation from logs. More of the same data will not pin it down, and each Savi version appears on one board only.
3. Keep `BookFast --b-imitates Savi` as the sparring partner. At 40k nodes it should repeat about 68% of Savi's moves (measured 68.2 at 30k and 69.0 at 150k with v-wd), about as good as anything here. Read its results as our engine in Savi's openings, not as an expected score against Savi. This does not reopen the 12:00 verdict in `night/results/book.md`: predicting Savi's moves says nothing about whether we should play them.
4. Calibrate the uploaded jar from its first export.

## Limits

- Savi8 and Savi9 appear only on 15x11, there are no Savi-vs-Savi games, drawn games are missing from the export, and the data ends at 23:05 on 2026-11-03.
- The fit was tried on 15x11 only, at one depth (4) and with one feature set.
- Calibration assumes the jar's fixed-node search behaves like its timed search. Lost turns and refused blasts are in the moves but not in the engine, which is one reason the ceiling is below 100%.
- Side finding on 3.1's missed blocks, inferred from replays: in `ecf0d7b1`, `bb907d77` and `f17e1f71` the engine picks a blast that removes the threat, and the server recorded a plain drop in that column, so the second call probably failed. `147174c3` is similar, with plain drops at plies 25 and 27 and the blast going through at ply 28. The night time stream has since added handling for this.

## Files

- Tools: `src/ch/qc/tools/` (`SaviAgree`, `SaviFit`, `SaviEval`, `SaviTact`, `SaviDiff`, `SaviMatch`, `SaviProbe`) and `src/calib/ch/qc/tools/SaviCalib.java`.
- Engine: `frozen/` is `git archive 45d9597`.
- Data: `games.jsonl`.
- Results: `out/holdout-table.txt` (the table above), `out/calib-real.txt`, `out/calib-time.txt`, `out/match-1.txt`, `out/tact-15x11.txt`, `out/depth-*.txt`, `out/nodes-savi.txt`, `out/diff-*.txt`, `out/fit-*`.
