# Export-18: Data Collector (rc5) next to The Emperor (rc7)

2026-11-05. Zip `analysis/server-exports/export-18-f7052383.zip`, unpacked in `analysis/server-exports/export-18/`; 4559 unique games. Same scripts as export-17 (`analysis/reports/server/export17/README.md`).

## Data Collector (lb 51500332, team "No beaches?") = rc5 (the team)

- 79 games, all 15x100, 06:18-06:33 UTC: **56-23 (71%)**. The Emperor (rc7), 06:00-06:16: 65-18 (78%). Both under the same server load; the 7-point gap is within noise for 80 games each (about ±7 points), but it points the same way as the local gates (rc7 +60..+100 Elo over rc5).
- By opponent: FunkyMelodyBot3 2-5, HWGAbot10.1 2-5, Strange Quark 1 **1-5**, lalalalalalala 3-4, HumanBot 5-2, BeetaSaag 5-1, test123 6-1; 7-0 against Savi21, MimimiTest, SPMoveDestroyer3.
- Own lost turns 40 per 1000 own turns (Emperor 65), but 101 per 1000 in own turns 0-2 (Emperor 48). 11 of 23 losses have an own lost turn in the last 6 plies.
- The five Strange Quark 1 losses last 8-9 plies: Strange Quark drops in column 0 every turn, and we lose 2-4 of our first 4-5 turns. Replayed locally, both rc5 and rc7 play `49` while the stack is short and block in column 0 in time (60 ms per call); the losses are lost turns only.

## Crop rebuilds and lost turns

Python replica of `Crop.refit` over every own turn of the Emperor and Data Collector: turns where the crop is rebuilt lose 126-130 per 1000, other turns 45-70. Rebuild turns are only 3% of turns (23 of about 350 lost turns). A rebuild itself costs 1-5 ms (new Geometry, evaluation tables, Searcher; measured warm and cold), so it does not explain the timeouts by its own cost.

## Model width against wide players (simulated on export-18 games, every 10 plies)

| opponent | contiguous crop (now) | gaps, 6 columns around discs | gaps, small far groups (< 3 same-colour discs) left out |
|---|---|---|---|
| HumanBot | 97 | 82 | 52 |
| FunkyMelodyBot3 | 83 | 69 | 68 |
| Strange Quark 1 | 63 | 25 | 20 |
| Savi21 | 43 | 28 | 26 |
| HWGAbot9.0 | 49 | 35 | — |

Leaving out empty stretches narrows the model a lot against Strange Quark and the Savi bots, little against HumanBot and Funky, who spread discs over the whole width.

## HumanBot as a weapon

HumanBot on 15x100 lost 7-0 to HWGAbot9.0 and to FunkyMelodyBot3, 6-1 to Savi20 and Savi21, 5-2 to HWGAbot10.1. Its wins came from us (4-3 against King beater and the Emperor) and from weak bots. Playing like it would not hurt the top bots; it hurts bots with our crop weakness, and WileyQuarks' bots do not show it.

## Time plan under an imitated CPU limit (ServerSim, 15x100)

`night/tools/simcfs.sh`: ServerSim on 2 logical CPUs with a container CPU limit imitated by `cfsthrottle.py` (quota per 100 ms period, the whole process frozen once it is used up) and one busy thread. Calibration rc7 vs rc7, 4 games each: quota 200 ms → 2-5 engine timeouts per 1000 own turns, 150 → 0-2, **120 → calls over 100 ms 52-61 per 1000** (close to the server's 40-65 lost turns per 1000), engine timeouts 2-5 (the process frozen 38% of the time). In the 16-game runs below, at the same 40% frozen time, engine timeouts came out at 30-48 per 1000: within one run both bots face the same freezes, so only paired comparisons count.

Variants (cand jar with a timing spec) against rc7 at quota 120, one busy thread, 16 games each:

| variant | score | calls over 100 ms per 1000 (A / rc7) | engine timeouts per 1000 (A / rc7) |
|---|---|---|---|
| p=0 (no second-call search) | 5-10-1, 34.4% | 86.9 / 82.7 | 30.1 / 39.9 |
| s=45 (first-call stop 45 ms on every turn) | 7-8-1, 46.9% | 88.6 / 103.0 | 47.9 / 45.2 |
| s=45,p=0 | 7-8-1, 46.9% | 82.3 / 83.5 | 48.2 / 41.5 |

A shorter stop does not reduce engine timeouts in this model: a frozen period swallows a call however short the bot planned it. Under a CPU limit the lost turns are the container's, shared by both bots, and a bot can only lower them by using less CPU in total, which also helps the opponent. The engine-crash path (blast asked after a timed-out first call) appeared 1-2 times per 16 games here; no sign of it in the server logs (only "Column is full" of weak bots and two server-side match failures).
