# Final jars: which one to upload (2026-11-06)

**Final set from about 07:10.** The tables were trained overnight: k4 is the 2nd round, k5 is the 6th round, k7 is the 5th round. For k=8 the table lost to the hand-written evaluation, so at k=8 the bot plays on the hand-written evaluation.

All jars are rc8 (first place on the server) plus three things:
1. The rules are set by switches: k, blast, cooldown, limit, match clock.
2. The bot refines the rules itself during the game.
3. Trained tables for k = 4, 5, 6, 7 inside.

The jars differ only in the k the bot starts the game with. Each one contains all tables, so if the bot raises k during the game, it takes the trained table of the new k.

## Choice by the 9:00 announcement

| Announced | Upload |
|---|---|
| k = 6, blast 3x3, 100 ms (as now) | `qc-bot-final-k6.jar` |
| k = 4 / 5 / 7 / 8, blast 3x3, cooldown 10, 100 ms | `qc-bot-final-k4.jar` / `-k5` / `-k7` / `-k8` |
| k changes from match to match, the engine has no getter | the jar with the **lowest** possible k (the bot raises k itself, it cannot lower it) |
| another blast, cooldown, limit or match clock | rebuild with one command (~5 minutes), see below |
| the organizers give a new engine jar with getters | one line in `EngineRules.read`, then rebuild |

Rebuild (PowerShell, from the project root):

```
& night\tools\final-build.ps1 -K 5 -Blast 5x5 -Cooldown 10 -LimitMs 100 -MatchSec 30 -Rows 15 -Cols 30 -Deliver
```

The script:
- sets the values;
- builds the jar;
- runs all tests, including the upload rules;
- plays a control match with the announced rules and stops if the bot misses a win in one move even once;
- puts the jar in the delivery folder and prints the sha256.

Full procedure: `night/wt-dyn/docs/final-day.md`.

## Checks

- **k=6 matches rc8.** The jar built from the rc8 sources is byte-identical to the rc8 on the server. At k=6 the new code played 400 of 400 games the same way as rc8 (fixed nodes, 4 sizes).
- **Real time, 15x30, 100 ms, against rc8:**
  - base: 51.5% of 200 games;
  - universal build (all tables and auto-adjustment): 52% of 200;
  - timeouts 0, move time the same as rc8.
- **Auto-adjustment in a real game.** A jar told k=5, in an engine with k=6 against rc8, scored 45% of 40 games: timeouts 0, wrong moves 0.
- **Tests.** All pass for every jar. Tests that run the bot inside the core-2.0 engine are skipped under other rules, because this engine plays only k=6.

## What the bot refines itself (anew in every game)

- **k only upward.** If there is a line of length L on the board and the game has not ended, then k > L.
- **Cooldown.** A refusal of our blast means the cooldown is larger. An opponent's blast at counter c means the cooldown is not larger than c.
- **Blast size.** After any blast, the moves are replayed for sizes from 1x1 to 7x7. The announced size stays if it matches what the engine did. Otherwise the nearest matching size is taken.

## Strength of the tables (against the hand-written evaluation, fixed 20k nodes, 300 games per size)

| k | table | 8x12 | 15x11 | 15x22 |
|---|---|---|---|---|
| 4 | 2nd round | +449 Elo | — | +335 |
| 5 | 6th round | +278 | — | +417 |
| 7 | 5th round | — | +149 (many draws) | +292 |
| 8 | none, hand-written evaluation | | | |

Each next round was kept only if it beat the previous one head to head. Example for k5, 6th round against the 5th: +47 and +51. Tables for another k do not change the game at k=6: g6f from rc8 is always taken there.

SHA256 values are in `SHA256SUMS.txt` next to the jar.

## If we get killed for memory (OOM): `qc-bot-final-k6-lean.jar`

Added on the morning of 2026-11-06. The full jars keep all k4/k5/k6/k7 tables in memory at once. In live memory this is about 15 MB more than rc8, and the peak at start is higher still. The lean jar contains only the k6 table (the same as in rc8), the rest is as in `qc-bot-final-k6.jar`.

Measurement, two bots and the engine in one JVM, 15x30:

| pair | -Xmx 40 MB | 48 MB | 56 MB |
|---|---|---|---|
| rc8 against rc8 | OOM | finished | — |
| lean against rc8 | OOM | finished | — |
| full k6 against rc8 | OOM | — | OOM at start |

In memory, lean = rc8. The downside: if the bot raises k above 6 during the game, it plays on the hand-written evaluation, not on a trained table.

Lean for another k (one command, ~5 minutes):

```
& night\tools\final-build.ps1 -K 5 -Tables 5 -Tag k5-lean -Rows 15 -Cols 30 -Deliver
```

`-Tables 5` keeps k6 and k5. `-Tables none` keeps only k6.
