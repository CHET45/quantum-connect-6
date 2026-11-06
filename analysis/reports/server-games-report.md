# Server games report

234 unique games (from 468 lines in 2 export file(s), 234 unique ids, 0 dropped as same players and moves). Draws are not stored by the server, so every game here is decided and the W/L tables contain no draws. Plies are recorded moves (blasts included, skipped turns not); turns also count skipped turns.

## Notation and replay check

Replay with the engine rules (gravity, colour-blind six, 3x3 blast, a blast needs a cooldown counter of at least 10, every own turn, skipped ones included, counts toward it): **234 of 234 games consistent** (recorded winner is the replay winner, the line appears exactly at win_at, no earlier line, no disc into a full column, no blast before the cooldown).

- `*` after a token marks a blast in that column: 257 blasts in 121 games; smallest cooldown counter at a blast 10; earliest blast is the own turn #10 (0-based, own turns counted from 0); smallest gap between two blasts of one side 10 own turns. Replaying with skipped turns NOT counting toward the cooldown gives 234 of 234 consistent, so the data cannot tell the two apart.
- A skipped turn (timeout or full column) leaves no token; replay finds 116 of them as breaks in the F/S alternation. In 233 of 234 games the breaks equal the logged timeout / column-full lines in count and sides (a break is invisible when both sides skip in a row).

## Per bot

Decided by: games of this bot ending in a normal six / in a blast (either side winning). Blasts used counts every `*` token by this bot. Length is plies per game.

| Bot | Games | W/L | As F W/L | As S W/L | Six / blast games | Its wins six / blast | Blasts used | Mean plies | Median plies |
|---|---|---|---|---|---|---|---|---|---|
| Testimine-M7 | 70 | 2/68 | 2/29 | 0/39 | 70 / 0 | 2 / 0 | 33 | 25.4 | 19.0 |
| Savi5 beater 2.1 | 62 | 61/1 | 30/1 | 31/0 | 54 / 8 | 53 / 8 | 54 | 49.3 | 34.0 |
| Savi5 | 58 | 53/5 | 27/3 | 26/2 | 58 / 0 | 53 / 0 | 63 | 51.8 | 38.5 |
| Testing_v2 | 44 | 36/8 | 20/4 | 16/4 | 39 / 5 | 33 / 3 | 47 | 54.4 | 44.5 |
| AntiStrat_FINAL | 36 | 10/26 | 6/11 | 4/15 | 36 / 0 | 10 / 0 | 34 | 28.9 | 24.0 |
| BeetaSaag | 36 | 15/21 | 8/9 | 7/12 | 34 / 2 | 15 / 0 | 0 | 34.2 | 32.5 |
| \u006c\u0061\u006c\2 | 36 | 17/19 | 10/8 | 7/11 | 35 / 1 | 17 / 0 | 0 | 40.6 | 19.0 |
| Strange Quark 1 | 35 | 8/27 | 5/13 | 3/14 | 35 / 0 | 8 / 0 | 0 | 19.5 | 18.0 |
| Hurt me plenty | 34 | 13/21 | 6/10 | 7/11 | 33 / 1 | 13 / 0 | 0 | 36.4 | 35.0 |
| sleep tight | 33 | 9/24 | 8/10 | 1/14 | 33 / 0 | 9 / 0 | 18 | 22.9 | 18.0 |
| good night | 24 | 10/14 | 6/8 | 4/6 | 19 / 5 | 10 / 0 | 8 | 37.0 | 28.0 |

## Game length

All games: plies min/median/p90/max = 11/29/83/136, mean 37.5; turns (skips included) 11/29/83/136. Games ending by a blast: 11 of 234 (4.7%). Games over 200/300/400 plies: 0/0/0 (over 200/300/400 turns: 0/0/0).

Plies in 20-ply buckets (all games):

| Plies | Games |  |
|---|---|---|
| 1-20 | 85 | ############################## |
| 21-40 | 74 | ########################## |
| 41-60 | 38 | ############# |
| 61-80 | 13 | ##### |
| 81-100 | 11 | #### |
| 101-120 | 6 | ### |
| 121-140 | 7 | ### |

Per bot (plies; the over-N columns count games with more than N plies):

| Bot | Games | min/median/p90/max | > 200 | > 300 | > 400 |
|---|---|---|---|---|---|
| Testimine-M7 | 70 | 11/19/48/71 | 0 | 0 | 0 |
| Savi5 beater 2.1 | 62 | 13/34/104/136 | 0 | 0 | 0 |
| Savi5 | 58 | 13/38.5/123/136 | 0 | 0 | 0 |
| Testing_v2 | 44 | 12/44.5/117/130 | 0 | 0 | 0 |
| AntiStrat_FINAL | 36 | 11/24/53/67 | 0 | 0 | 0 |
| BeetaSaag | 36 | 11/32.5/57/96 | 0 | 0 | 0 |
| \u006c\u0061\u006c\2 | 36 | 11/19/100/112 | 0 | 0 | 0 |
| Strange Quark 1 | 35 | 11/18/33/35 | 0 | 0 | 0 |
| Hurt me plenty | 34 | 13/35/51/90 | 0 | 0 | 0 |
| sleep tight | 33 | 11/18/35/71 | 0 | 0 | 0 |
| good night | 24 | 11/28/72/96 | 0 | 0 | 0 |

Our team (No beaches?): 62 games, plies min/median/p90/max = 13/34/104/136, over 200/300/400 plies: 0/0/0.

## First-player advantage

First player (F) won 128 of 234 (54.7%, normal-approximation 95% interval 48.3-61.1%). By a six: F 123, S 100; by a blast: F 5, S 6. In games without any timeout or full-column event: F 103 of 184 (56.0%). Bots differ in strength, so this mixes colour with pairing; the pairings are colour-balanced only if each ordered pair is played equally often.

## Head to head

Cell = wins of the row bot over the column bot, as wins-losses (all colours).

| # | Bot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Testimine-M7 | . | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 2-5 | 0-7 |
| 2 | Savi5 beater 2.1 | 7-0 | . | 4-1 | 4-0 | 7-0 | 7-0 | 6-0 | 7-0 | 6-0 | 6-0 | 7-0 |
| 3 | Savi5 | 7-0 | 1-4 | . | 4-1 | 7-0 | 7-0 | 7-0 | 6-0 | 7-0 | 7-0 |  |
| 4 | Testing_v2 | 7-0 | 0-4 | 1-4 | . | 5-0 | 4-0 | 4-0 | 3-0 | 4-0 | 3-0 | 5-0 |
| 5 | AntiStrat_FINAL | 7-0 | 0-7 | 0-7 | 0-5 | . | 0-2 | 0-2 | 2-0 | 0-2 | 1-1 |  |
| 6 | BeetaSaag | 7-0 | 0-7 | 0-7 | 0-4 | 2-0 | . | 1-1 | 2-0 | 1-1 | 1-1 | 1-0 |
| 7 | \u006c\u0061\u006c\2 | 7-0 | 0-6 | 0-7 | 0-4 | 2-0 | 1-1 | . | 2-0 | 2-0 | 2-0 | 1-1 |
| 8 | Strange Quark 1 | 7-0 | 0-7 | 0-6 | 0-3 | 0-2 | 0-2 | 0-2 | . | 0-2 | 1-1 | 0-2 |
| 9 | Hurt me plenty | 7-0 | 0-6 | 0-7 | 0-4 | 2-0 | 1-1 | 0-2 | 2-0 | . | 1-1 |  |
| 10 | sleep tight | 5-2 | 0-6 | 0-7 | 0-3 | 1-1 | 1-1 | 0-2 | 1-1 | 1-1 | . |  |
| 11 | good night | 7-0 | 0-7 |  | 0-5 |  | 0-1 | 1-1 | 2-0 |  |  | . |

## Our team: No beaches?

62 games, 61 wins, 1 losses. Opponent column shows our bot's version in brackets; blasts are ours/theirs; our skipped turns are numbered by turn (skipped turns included, 1-based) and, when the log pairs up, their cause; the last column is the OOM line of the game, if any (not tagged with a bot).

| # | Opponent (our version) | Side | Res | Plies | Turns | Decided by | Blasts | Our skipped turns | Opp skips | OOM line |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Testing_v2 (Savi5 beater 2.1) | S | W | 99 | 100 | blast | 3/3 | - | 1 | 312 MB |
| 2 | Testing_v2 (Savi5 beater 2.1) | F | W | 117 | 119 | six | 3/4 | - | 2 | 305 MB |
| 3 | Testing_v2 (Savi5 beater 2.1) | S | W | 84 | 88 | six | 0/1 | - | 4 | 309 MB |
| 4 | Hurt me plenty (Savi5 beater 2.1) | F | W | 34 | 37 | six | 0/0 | turn 17 (timeout) | 2 | 311 MB |
| 5 | AntiStrat_FINAL (Savi5 beater 2.1) | S | W | 58 | 58 | six | 1/2 | - | 0 | - |
| 6 | AntiStrat_FINAL (Savi5 beater 2.1) | S | W | 46 | 46 | six | 1/2 | - | 0 | - |
| 7 | AntiStrat_FINAL (Savi5 beater 2.1) | F | W | 29 | 29 | six | 1/1 | - | 0 | - |
| 8 | Savi5 (Savi5 beater 2.1) | S | W | 104 | 104 | six | 1/2 | - | 0 | - |
| 9 | Savi5 (Savi5 beater 2.1) | S | W | 128 | 128 | six | 2/4 | - | 0 | 201 MB |
| 10 | Savi5 (Savi5 beater 2.1) | S | W | 132 | 132 | six | 3/4 | - | 0 | 200 MB |
| 11 | Savi5 (Savi5 beater 2.1) | F | L | 136 | 136 | six | 3/4 | - | 0 | 200 MB |
| 12 | BeetaSaag (Savi5 beater 2.1) | F | W | 31 | 31 | six | 0/0 | - | 0 | 201 MB |
| 13 | BeetaSaag (Savi5 beater 2.1) | S | W | 48 | 48 | six | 2/0 | - | 0 | 208 MB |
| 14 | BeetaSaag (Savi5 beater 2.1) | F | W | 63 | 63 | six | 2/0 | - | 0 | 200 MB |
| 15 | BeetaSaag (Savi5 beater 2.1) | S | W | 50 | 50 | six | 1/0 | - | 0 | - |
| 16 | BeetaSaag (Savi5 beater 2.1) | S | W | 26 | 26 | blast | 1/0 | - | 0 | - |
| 17 | BeetaSaag (Savi5 beater 2.1) | F | W | 37 | 37 | six | 1/0 | - | 0 | 209 MB |
| 18 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 34 | 34 | six | 1/0 | - | 0 | - |
| 19 | Strange Quark 1 (Savi5 beater 2.1) | F | W | 17 | 17 | six | 0/0 | - | 0 | 209 MB |
| 20 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 20 | 20 | six | 0/0 | - | 0 | 230 MB |
| 21 | Strange Quark 1 (Savi5 beater 2.1) | F | W | 17 | 17 | six | 0/0 | - | 0 | 206 MB |
| 22 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 18 | 18 | six | 0/0 | - | 0 | 223 MB |
| 23 | Strange Quark 1 (Savi5 beater 2.1) | F | W | 15 | 15 | six | 0/0 | - | 0 | 205 MB |
| 24 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 20 | 20 | six | 0/0 | - | 0 | 213 MB |
| 25 | Testing_v2 (Savi5 beater 2.1) | S | W | 129 | 132 | blast | 4/3 | - | 3 | 285 MB |
| 26 | \u006c\u0061\u006c\2 (Savi5 beater 2.1) | S | W | 18 | 18 | six | 0/0 | - | 0 | 215 MB |
| 27 | sleep tight (Savi5 beater 2.1) | S | W | 16 | 16 | six | 0/0 | - | 0 | 294 MB |
| 28 | sleep tight (Savi5 beater 2.1) | F | W | 15 | 15 | six | 0/0 | - | 0 | 263 MB |
| 29 | sleep tight (Savi5 beater 2.1) | F | W | 27 | 27 | six | 0/1 | - | 0 | 255 MB |
| 30 | sleep tight (Savi5 beater 2.1) | S | W | 30 | 30 | six | 0/1 | - | 0 | 292 MB |
| 31 | sleep tight (Savi5 beater 2.1) | F | W | 13 | 13 | six | 0/0 | - | 0 | 255 MB |
| 32 | sleep tight (Savi5 beater 2.1) | S | W | 28 | 28 | six | 1/1 | - | 0 | 285 MB |
| 33 | \u006c\u0061\u006c\2 (Savi5 beater 2.1) | F | W | 43 | 43 | blast | 1/0 | - | 0 | 249 MB |
| 34 | \u006c\u0061\u006c\2 (Savi5 beater 2.1) | F | W | 83 | 83 | six | 1/0 | - | 0 | 247 MB |
| 35 | \u006c\u0061\u006c\2 (Savi5 beater 2.1) | F | W | 99 | 99 | six | 2/0 | - | 0 | 269 MB |
| 36 | \u006c\u0061\u006c\2 (Savi5 beater 2.1) | S | W | 18 | 18 | six | 0/0 | - | 0 | 261 MB |
| 37 | \u006c\u0061\u006c\2 (Savi5 beater 2.1) | F | W | 93 | 93 | six | 3/0 | - | 0 | 216 MB |
| 38 | Hurt me plenty (Savi5 beater 2.1) | F | W | 31 | 31 | blast | 1/0 | - | 0 | 294 MB |
| 39 | Hurt me plenty (Savi5 beater 2.1) | S | W | 45 | 48 | six | 0/0 | - | 3 | 331 MB |
| 40 | Hurt me plenty (Savi5 beater 2.1) | F | W | 34 | 35 | six | 0/0 | - | 1 | 297 MB |
| 41 | Hurt me plenty (Savi5 beater 2.1) | S | W | 32 | 32 | six | 0/0 | - | 0 | 335 MB |
| 42 | Hurt me plenty (Savi5 beater 2.1) | S | W | 46 | 48 | six | 0/0 | - | 2 | 336 MB |
| 43 | Testimine-M7 (Savi5 beater 2.1) | F | W | 13 | 13 | six | 0/0 | - | 0 | - |
| 44 | Testimine-M7 (Savi5 beater 2.1) | S | W | 28 | 28 | six | 0/1 | - | 0 | - |
| 45 | Testimine-M7 (Savi5 beater 2.1) | F | W | 35 | 35 | six | 0/1 | - | 0 | - |
| 46 | Testimine-M7 (Savi5 beater 2.1) | S | W | 48 | 48 | six | 1/1 | - | 0 | - |
| 47 | Testimine-M7 (Savi5 beater 2.1) | F | W | 27 | 27 | six | 0/0 | - | 0 | - |
| 48 | Testimine-M7 (Savi5 beater 2.1) | S | W | 20 | 20 | six | 0/0 | - | 0 | - |
| 49 | Testimine-M7 (Savi5 beater 2.1) | F | W | 19 | 19 | six | 0/0 | - | 0 | - |
| 50 | AntiStrat_FINAL (Savi5 beater 2.1) | F | W | 33 | 33 | six | 1/1 | - | 0 | - |
| 51 | AntiStrat_FINAL (Savi5 beater 2.1) | S | W | 44 | 44 | six | 1/2 | - | 0 | - |
| 52 | AntiStrat_FINAL (Savi5 beater 2.1) | F | W | 23 | 23 | six | 0/1 | - | 0 | - |
| 53 | AntiStrat_FINAL (Savi5 beater 2.1) | F | W | 67 | 67 | six | 2/3 | - | 0 | - |
| 54 | Savi5 (Savi5 beater 2.1) | F | W | 105 | 105 | six | 1/3 | - | 0 | 200 MB |
| 55 | BeetaSaag (Savi5 beater 2.1) | F | W | 45 | 45 | six | 1/0 | - | 0 | 206 MB |
| 56 | good night (Savi5 beater 2.1) | S | W | 63 | 64 | six | 2/1 | turn 28 (timeout) | 0 | 306 MB |
| 57 | good night (Savi5 beater 2.1) | F | W | 29 | 29 | blast | 1/0 | - | 0 | - |
| 58 | good night (Savi5 beater 2.1) | S | W | 72 | 72 | six | 1/1 | - | 0 | - |
| 59 | good night (Savi5 beater 2.1) | F | W | 29 | 29 | blast | 1/0 | - | 0 | - |
| 60 | good night (Savi5 beater 2.1) | S | W | 72 | 72 | six | 1/1 | - | 0 | - |
| 61 | good night (Savi5 beater 2.1) | F | W | 27 | 27 | blast | 1/0 | - | 0 | - |
| 62 | good night (Savi5 beater 2.1) | S | W | 94 | 94 | six | 1/1 | - | 0 | - |

Our own timeout lines and the OOM line of the same game:

- Game #4 vs Hurt me plenty: our timeout line at turn 17; OOM line 311 MB, 696 ms before the timeout.
- Game #56 vs good night: our timeout line at turn 28; OOM line 306 MB, 747 ms before the timeout.

### Loss: Savi5 beater 2.1 (F) vs Savi5 (S)

Match id `e9582887-0ab6-4476-9d51-79d16f87415b`. Decided at ply 136 (turn 136, last token `S7`, by a six, win_at 135). Skipped turns: none.

Moves:

```
F3S5F5S5F5S5F5S5F3S3F3S3F3S3F4S4F4S4F5S3F4S4F4S7F4S8F7S1F8S1FaSaF8S7*F1S1F7S7F8S8F7S8F7S7F8S7F1S1F8S1F7SaF2S2F2S2F8SaF8SaFaS1FaSaF6S2*F3S2F3S2F3S6F2S6F9*S9F6S6F6SaFaS9F8S8FbS0*FbSbFbS0F0S0F0S0F0S1F1S0F1S0F1S2F2S1F9S9F9S9F9*S8*F7S8FaS8FaSaF9SbFbSbF9SbF9S9F8S8F7S7F6*S6F5S6F5S5F6S7
```

Position after the deciding move (top row first, F = X, S = O):

```
OOXXXOX.OOOO
OXOXXXO.XXXO
XXXXOXOOOXXX
OXOOXOOXOXXO
XOOOOXXXXOXX
OOXOXOOOXXOO
XOOXOXOXOOOX
OXXXXOXOOOOX
0123456789ab
```

## Timeouts and out-of-memory

Timeouts are the per-side letters of the log (an own timeout also gives an 'Invalid column selected: -1' line). OOM lines (`Memory limit exceeded: N MB used`, then an uncaught SecurityException) are not tagged with a side or bot, at most one per game. Warn = games with memory-warning lines in the log (both bots).

| Bot | Games | Own timeouts | Games with own timeout | Own full-column turns | Games with an OOM line | OOM MB min/median/p90/max | Games with warn lines |
|---|---|---|---|---|---|---|---|
| Testimine-M7 | 70 | 0 | 0 | 0 | 25 (35.7%) | 202/255/305/331 | 14 (max 199 MB) |
| Savi5 beater 2.1 | 62 | 2 | 2 | 0 | 38 (61.3%) | 200/252/312/336 | 19 (max 199 MB) |
| Savi5 | 58 | 0 | 0 | 0 | 30 (51.7%) | 200/206.5/264/302 | 24 (max 199 MB) |
| Testing_v2 | 44 | 38 | 25 | 0 | 27 (61.4%) | 200/233/309/323 | 10 (max 199 MB) |
| AntiStrat_FINAL | 36 | 0 | 0 | 0 | 11 (30.6%) | 200/227/286/295 | 14 (max 198 MB) |
| BeetaSaag | 36 | 0 | 0 | 7 | 15 (41.7%) | 200/211/271/281 | 15 (max 198 MB) |
| \u006c\u0061\u006c\2 | 36 | 0 | 0 | 0 | 19 (52.8%) | 200/216/269/271 | 13 (max 199 MB) |
| Strange Quark 1 | 35 | 0 | 0 | 0 | 18 (51.4%) | 201/219/249/258 | 16 (max 199 MB) |
| Hurt me plenty | 34 | 65 | 23 | 6 | 16 (47.1%) | 201/265.5/335/336 | 7 (max 194 MB) |
| sleep tight | 33 | 0 | 0 | 0 | 33 (100.0%) | 223/265/294/302 | 0 |
| good night | 24 | 0 | 0 | 0 | 16 (66.7%) | 258/281.5/323/331 | 0 |

124 of 234 games (53.0%) contain an OOM line.

Which side timed out in games with / without an OOM line, seen from each bot (own = only this bot's side, opp = only its opponent's, both, none):

| Bot | OOM games: own / opp / both / none | Other games: own / opp / both / none |
|---|---|---|
| Testimine-M7 | 0 / 2 / 0 / 23 | 0 / 4 / 0 / 41 |
| Savi5 beater 2.1 | 1 / 7 / 1 / 29 | 0 / 0 / 0 / 24 |
| Savi5 | 0 / 3 / 0 / 27 | 0 / 7 / 0 / 21 |
| Testing_v2 | 16 / 1 / 0 / 10 | 9 / 1 / 0 / 7 |
| AntiStrat_FINAL | 0 / 2 / 0 / 9 | 0 / 3 / 0 / 22 |
| BeetaSaag | 0 / 2 / 0 / 13 | 0 / 3 / 0 / 18 |
| \u006c\u0061\u006c\2 | 0 / 2 / 0 / 17 | 0 / 1 / 0 / 16 |
| Strange Quark 1 | 0 / 1 / 0 / 17 | 0 / 0 / 0 / 17 |
| Hurt me plenty | 10 / 0 / 1 / 5 | 12 / 2 / 0 / 4 |
| sleep tight | 0 / 3 / 0 / 30 | 0 / 0 / 0 / 0 |
| good night | 0 / 4 / 0 / 12 | 0 / 0 / 0 / 8 |

First timeout or full-column line at or after the OOM line, by whose side it hit (own / opp / no later event):

| Bot | own / opp / none |
|---|---|
| Testimine-M7 | 0 / 2 / 23 |
| Savi5 beater 2.1 | 2 / 7 / 29 |
| Savi5 | 0 / 0 / 30 |
| Testing_v2 | 13 / 1 / 13 |
| AntiStrat_FINAL | 0 / 2 / 9 |
| BeetaSaag | 1 / 2 / 12 |
| \u006c\u0061\u006c\2 | 0 / 1 / 18 |
| Strange Quark 1 | 0 / 1 / 17 |
| Hurt me plenty | 9 / 1 / 6 |
| sleep tight | 0 / 3 / 30 |
| good night | 0 / 5 / 11 |

Games with an OOM line followed by a later timeout line: 25 of 124; delay from the OOM line to it, ms min/median/p90/max = 7/300/961/1049.

Games where a timeout line precedes the OOM line: 4.

OOM games / games for every pair of bots (both colours); a bot that exhausts memory shows up as a column and row of high shares:

| # | Bot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Testimine-M7 | . | 0/7 | 2/7 | 0/7 | 3/7 | 0/7 | 1/7 | 3/7 | 2/7 | 7/7 | 7/7 |
| 2 | Savi5 beater 2.1 | 0/7 | . | 4/5 | 4/4 | 0/7 | 5/7 | 6/6 | 6/7 | 6/6 | 6/6 | 1/7 |
| 3 | Savi5 | 2/7 | 4/5 | . | 3/5 | 3/7 | 3/7 | 3/7 | 3/6 | 2/7 | 7/7 |  |
| 4 | Testing_v2 | 0/7 | 4/4 | 3/5 | . | 2/5 | 3/4 | 3/4 | 3/3 | 1/4 | 3/3 | 5/5 |
| 5 | AntiStrat_FINAL | 3/7 | 0/7 | 3/7 | 2/5 | . | 0/2 | 0/2 | 0/2 | 1/2 | 2/2 |  |
| 6 | BeetaSaag | 0/7 | 5/7 | 3/7 | 3/4 | 0/2 | . | 1/2 | 0/2 | 0/2 | 2/2 | 1/1 |
| 7 | \u006c\u0061\u006c\2 | 1/7 | 6/6 | 3/7 | 3/4 | 0/2 | 1/2 | . | 0/2 | 1/2 | 2/2 | 2/2 |
| 8 | Strange Quark 1 | 3/7 | 6/7 | 3/6 | 3/3 | 0/2 | 0/2 | 0/2 | . | 1/2 | 2/2 | 0/2 |
| 9 | Hurt me plenty | 2/7 | 6/6 | 2/7 | 1/4 | 1/2 | 0/2 | 1/2 | 1/2 | . | 2/2 |  |
| 10 | sleep tight | 7/7 | 6/6 | 7/7 | 3/3 | 2/2 | 2/2 | 2/2 | 2/2 | 2/2 | . |  |
| 11 | good night | 7/7 | 1/7 |  | 5/5 |  | 1/1 | 2/2 | 0/2 |  |  | . |

Median OOM size in MB for every pair of bots (the figure is the memory in use when the limit tripped; abrupt jumps give large values, slow growth gives values just above 200):

| # | Bot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Testimine-M7 |  |  | 203 |  | 215 |  | 210 | 224 | 207.5 | 265 | 292 |
| 2 | Savi5 beater 2.1 |  |  | 200 | 307 |  | 206 | 248 | 211 | 321 | 274 | 306 |
| 3 | Savi5 | 203 | 200 |  | 202 | 214 | 207 | 209 | 203 | 206 | 264 |  |
| 4 | Testing_v2 |  | 307 | 202 |  | 222 | 228 | 200 | 208 | 271 | 262 | 284 |
| 5 | AntiStrat_FINAL | 215 |  | 214 | 222 |  |  |  |  | 231 | 290.5 |  |
| 6 | BeetaSaag |  | 206 | 207 | 228 |  |  | 211 |  |  | 276 | 258 |
| 7 | \u006c\u0061\u006c\2 | 210 | 248 | 209 | 200 |  | 211 |  |  | 201 | 264.5 | 263.5 |
| 8 | Strange Quark 1 | 224 | 211 | 203 | 208 |  |  |  |  | 236 | 253.5 |  |
| 9 | Hurt me plenty | 207.5 | 321 | 206 | 271 | 231 |  | 201 | 236 |  | 268 |  |
| 10 | sleep tight | 265 | 274 | 264 | 262 | 290.5 | 276 | 264.5 | 253.5 | 268 |  |  |
| 11 | good night | 292 | 306 |  | 284 |  | 258 | 263.5 |  |  |  |  |

## Wall-clock evidence

The exported log lines are only ERROR lines (OOM, timeout, invalid column, full column); they carry millisecond timestamps (471 lines) but there is no per-move or start/end line, so a game's duration can only be bracketed from its first to its last timestamped event.

28 games have two or more paired timeout lines (log events match the skipped turns found by the replay). Between consecutive such events (55 intervals, 461 turns): time per turn in ms, min/median/p90/max = 0/6/54/89, pooled mean 15 ms per turn (6.8 s over 461 turns). First-to-last event span per game, ms min/median/p90/max = 1/146/784/1151, covering turns 2/12/38/52. Consecutive timeouts can be logged only a few ms apart although each is reported as exceeding 100 ms, so the log timestamps are not a reliable clock for single moves; they give an order of magnitude only (games of this many turns are logged within about a second, far below 30 s).

