# Server games report

341 unique games (from 575 lines in 2 export file(s), 341 unique ids, 0 dropped as same players and moves). Draws are not stored by the server, so every game here is decided and the W/L tables contain no draws. Plies are recorded moves (blasts included, skipped turns not); turns also count skipped turns.

## Notation and replay check

Replay with the engine rules (gravity, colour-blind six, 3x3 blast, a blast needs a cooldown counter of at least 10, every own turn, skipped ones included, counts toward it): **341 of 341 games consistent** (recorded winner is the replay winner, the line appears exactly at win_at, no earlier line, no disc into a full column, no blast before the cooldown).

- `*` after a token marks a blast in that column: 379 blasts in 176 games; smallest cooldown counter at a blast 10; earliest blast is the own turn #10 (0-based, own turns counted from 0); smallest gap between two blasts of one side 10 own turns. Replaying with skipped turns NOT counting toward the cooldown gives 341 of 341 consistent, so the data cannot tell the two apart.
- A skipped turn (timeout or full column) leaves no token; replay finds 153 of them as breaks in the F/S alternation. In 340 of 341 games the breaks equal the logged timeout / column-full lines in count and sides (a break is invisible when both sides skip in a row).

## Per bot

Decided by: games of this bot ending in a normal six / in a blast (either side winning). Blasts used counts every `*` token by this bot. Length is plies per game.

| Bot | Games | W/L | As F W/L | As S W/L | Six / blast games | Its wins six / blast | Blasts used | Mean plies | Median plies |
|---|---|---|---|---|---|---|---|---|---|
| Testimine-M7 | 91 | 2/89 | 2/38 | 0/51 | 91 / 0 | 2 / 0 | 35 | 23.9 | 19.0 |
| Savi5 beater 2.1 | 79 | 76/3 | 37/2 | 39/1 | 68 / 11 | 65 / 11 | 73 | 53.3 | 43.0 |
| Savi5 | 58 | 53/5 | 27/3 | 26/2 | 58 / 0 | 53 / 0 | 63 | 51.8 | 38.5 |
| Savi6 - "Beat this" | 57 | 55/2 | 30/0 | 25/2 | 56 / 1 | 54 / 1 | 47 | 53.2 | 44.0 |
| Testing_v2 | 57 | 45/12 | 24/5 | 21/7 | 52 / 5 | 42 / 3 | 70 | 59.5 | 48.0 |
| BeetaSaag | 46 | 17/29 | 9/12 | 8/17 | 44 / 2 | 17 / 0 | 0 | 36.3 | 33.5 |
| Strange Quark 1 | 46 | 8/38 | 5/18 | 3/20 | 46 / 0 | 8 / 0 | 0 | 20.7 | 18.5 |
| Hurt me plenty | 44 | 13/31 | 6/15 | 7/16 | 43 / 1 | 13 / 0 | 0 | 38.0 | 38.0 |
| AntiStrat_FINAL | 36 | 10/26 | 6/11 | 4/15 | 36 / 0 | 10 / 0 | 34 | 28.9 | 24.0 |
| \u006c\u0061\u006c\2 | 36 | 17/19 | 10/8 | 7/11 | 35 / 1 | 17 / 0 | 0 | 40.6 | 19.0 |
| lalalabot | 34 | 14/20 | 9/10 | 5/10 | 32 / 2 | 14 / 0 | 5 | 35.9 | 19.5 |
| good night | 33 | 11/22 | 7/11 | 4/11 | 27 / 6 | 11 / 0 | 14 | 40.2 | 33.0 |
| sleep tight | 33 | 9/24 | 8/10 | 1/14 | 33 / 0 | 9 / 0 | 18 | 22.9 | 18.0 |
| specialmovedestroyer | 32 | 11/21 | 6/12 | 5/9 | 31 / 1 | 11 / 0 | 20 | 44.2 | 46.0 |

## Game length

All games: plies min/median/p90/max = 11/31/90/136, mean 40.0; turns (skips included) 11/31/93/136. Games ending by a blast: 15 of 341 (4.4%). Games over 200/300/400 plies: 0/0/0 (over 200/300/400 turns: 0/0/0).

Plies in 20-ply buckets (all games):

| Plies | Games |  |
|---|---|---|
| 1-20 | 111 | ########################### |
| 21-40 | 102 | ######################## |
| 41-60 | 66 | ################ |
| 61-80 | 22 | ###### |
| 81-100 | 19 | ##### |
| 101-120 | 12 | ### |
| 121-140 | 9 | ### |

Per bot (plies; the over-N columns count games with more than N plies):

| Bot | Games | min/median/p90/max | > 200 | > 300 | > 400 |
|---|---|---|---|---|---|
| Testimine-M7 | 91 | 11/19/46/71 | 0 | 0 | 0 |
| Savi5 beater 2.1 | 79 | 13/43/109/136 | 0 | 0 | 0 |
| Savi5 | 58 | 13/38.5/123/136 | 0 | 0 | 0 |
| Savi6 - "Beat this" | 57 | 15/44/113/128 | 0 | 0 | 0 |
| Testing_v2 | 57 | 12/48/117/130 | 0 | 0 | 0 |
| BeetaSaag | 46 | 11/33.5/60/96 | 0 | 0 | 0 |
| Strange Quark 1 | 46 | 11/18.5/33/36 | 0 | 0 | 0 |
| Hurt me plenty | 44 | 13/38/51/93 | 0 | 0 | 0 |
| AntiStrat_FINAL | 36 | 11/24/53/67 | 0 | 0 | 0 |
| \u006c\u0061\u006c\2 | 36 | 11/19/100/112 | 0 | 0 | 0 |
| lalalabot | 34 | 11/19.5/93/112 | 0 | 0 | 0 |
| good night | 33 | 11/33/74/96 | 0 | 0 | 0 |
| sleep tight | 33 | 11/18/35/71 | 0 | 0 | 0 |
| specialmovedestroyer | 32 | 11/46/78/96 | 0 | 0 | 0 |

Our team (No beaches?): 79 games, plies min/median/p90/max = 13/43/109/136, over 200/300/400 plies: 0/0/0.

## First-player advantage

First player (F) won 186 of 341 (54.5%, normal-approximation 95% interval 49.3-59.8%). By a six: F 180, S 146; by a blast: F 6, S 9. In games without any timeout or full-column event: F 152 of 272 (55.9%). Bots differ in strength, so this mixes colour with pairing; the pairings are colour-balanced only if each ordered pair is played equally often.

## Head to head

Cell = wins of the row bot over the column bot, as wins-losses (all colours).

| # | Bot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Testimine-M7 | . | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 2-5 | 0-7 |
| 2 | Savi5 beater 2.1 | 7-0 | . | 4-1 | 1-2 | 4-0 | 7-0 | 7-0 | 6-0 | 7-0 | 6-0 | 7-0 | 7-0 | 6-0 | 7-0 |
| 3 | Savi5 | 7-0 | 1-4 | . |  | 4-1 | 7-0 | 6-0 | 7-0 | 7-0 | 7-0 |  |  | 7-0 |  |
| 4 | Savi6 - "Beat this" | 7-0 | 2-1 |  | . | 4-1 | 7-0 | 7-0 | 7-0 |  |  | 7-0 | 7-0 |  | 7-0 |
| 5 | Testing_v2 | 7-0 | 0-4 | 1-4 | 1-4 | . | 4-0 | 3-0 | 4-0 | 5-0 | 4-0 | 3-0 | 5-0 | 3-0 | 5-0 |
| 6 | BeetaSaag | 7-0 | 0-7 | 0-7 | 0-7 | 0-4 | . | 2-0 | 1-1 | 2-0 | 1-1 | 1-1 | 1-0 | 1-1 | 1-0 |
| 7 | Strange Quark 1 | 7-0 | 0-7 | 0-6 | 0-7 | 0-3 | 0-2 | . | 0-2 | 0-2 | 0-2 | 0-2 | 0-2 | 1-1 | 0-2 |
| 8 | Hurt me plenty | 7-0 | 0-6 | 0-7 | 0-7 | 0-4 | 1-1 | 2-0 | . | 2-0 | 0-2 | 0-2 |  | 1-1 | 0-1 |
| 9 | AntiStrat_FINAL | 7-0 | 0-7 | 0-7 |  | 0-5 | 0-2 | 2-0 | 0-2 | . | 0-2 |  |  | 1-1 |  |
| 10 | \u006c\u0061\u006c\2 | 7-0 | 0-6 | 0-7 |  | 0-4 | 1-1 | 2-0 | 2-0 | 2-0 | . |  | 1-1 | 2-0 |  |
| 11 | lalalabot | 7-0 | 0-7 |  | 0-7 | 0-3 | 1-1 | 2-0 | 2-0 |  |  | . | 1-1 |  | 1-1 |
| 12 | good night | 7-0 | 0-7 |  | 0-7 | 0-5 | 0-1 | 2-0 |  |  | 1-1 | 1-1 | . |  |  |
| 13 | sleep tight | 5-2 | 0-6 | 0-7 |  | 0-3 | 1-1 | 1-1 | 1-1 | 1-1 | 0-2 |  |  | . |  |
| 14 | specialmovedestroyer | 7-0 | 0-7 |  | 0-7 | 0-5 | 0-1 | 2-0 | 1-0 |  |  | 1-1 |  |  | . |

## Our team: No beaches?

79 games, 76 wins, 3 losses. Opponent column shows our bot's version in brackets; blasts are ours/theirs; our skipped turns are numbered by turn (skipped turns included, 1-based) and, when the log pairs up, their cause; the last column is the OOM line of the game, if any (not tagged with a bot).

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
| 63 | lalalabot (Savi5 beater 2.1) | S | W | 18 | 18 | six | 0/0 | - | 0 | 200 MB |
| 64 | lalalabot (Savi5 beater 2.1) | F | W | 29 | 29 | six | 0/1 | - | 0 | 230 MB |
| 65 | lalalabot (Savi5 beater 2.1) | S | W | 18 | 18 | six | 0/0 | - | 0 | 205 MB |
| 66 | lalalabot (Savi5 beater 2.1) | F | W | 95 | 95 | six | 1/1 | - | 0 | 235 MB |
| 67 | lalalabot (Savi5 beater 2.1) | S | W | 77 | 78 | blast | 1/0 | turn 20 (timeout) | 0 | 235 MB |
| 68 | lalalabot (Savi5 beater 2.1) | F | W | 69 | 69 | six | 2/0 | - | 0 | 225 MB |
| 69 | lalalabot (Savi5 beater 2.1) | S | W | 94 | 94 | blast | 3/1 | - | 0 | 278 MB |
| 70 | Savi6 - "Beat this" (Savi5 beater 2.1) | S | L | 115 | 115 | six | 2/1 | - | 0 | 200 MB |
| 71 | Savi6 - "Beat this" (Savi5 beater 2.1) | F | W | 109 | 109 | six | 1/1 | - | 0 | 205 MB |
| 72 | Savi6 - "Beat this" (Savi5 beater 2.1) | F | L | 122 | 122 | six | 2/4 | - | 0 | 244 MB |
| 73 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 48 | 48 | six | 1/1 | - | 0 | - |
| 74 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 56 | 56 | six | 1/1 | - | 0 | 201 MB |
| 75 | specialmovedestroyer (Savi5 beater 2.1) | F | W | 43 | 43 | six | 0/1 | - | 0 | - |
| 76 | specialmovedestroyer (Savi5 beater 2.1) | F | W | 55 | 55 | blast | 2/1 | - | 0 | 201 MB |
| 77 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 84 | 84 | six | 2/1 | - | 0 | 223 MB |
| 78 | specialmovedestroyer (Savi5 beater 2.1) | F | W | 41 | 41 | six | 0/1 | - | 0 | - |
| 79 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 78 | 78 | six | 1/1 | - | 0 | 203 MB |

Our own timeout lines and the OOM line of the same game:

- Game #4 vs Hurt me plenty: our timeout line at turn 17; OOM line 311 MB, 696 ms before the timeout.
- Game #56 vs good night: our timeout line at turn 28; OOM line 306 MB, 747 ms before the timeout.
- Game #67 vs lalalabot: our timeout line at turn 20; OOM line 235 MB, 495 ms before the timeout.

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

### Loss: Savi5 beater 2.1 (S) vs Savi6 - "Beat this" (F)

Match id `01c00039-a661-496c-85b3-12bda7c2d613`. Decided at ply 115 (turn 115, last token `Fa`, by a six, win_at 114). Skipped turns: none.

Moves:

```
F5S6F6S6F6S6F6S6F3S6F5S5F5S5F5S5F5S3F3S3F3S3F3S3FbS2F9S2F2S2F7SbF7S7F9S9F9S7F7S7F9S7FbS9F9S7F9S2F2S2F0S2F0S0FbS0FbS0FbSbF4S4F4SbF0S4F1S4F1S4F1S1F4S0F4S0Fa*S9F8S8F9SbFbS8F8S8*F7S7F9S9F9S7F1SaF8S8F8S8F8S1FaS1FaSaF1Sa*F9SbF9SbFaSbF9SaFa
```

Position after the deciding move (top row first, F = X, S = O):

```
OXOOXXOOXX.O
OOOXXOOOOX.O
XOXOOXXXXXXO
OXOXOOOOOXOX
OOOOOXXOXOXO
OXXXXOOOOXXX
XXOOOXXXOXXX
XXOXXXOXXOOX
0123456789ab
```

### Loss: Savi5 beater 2.1 (F) vs Savi6 - "Beat this" (S)

Match id `440af18b-3b2c-4b5d-8365-18a8e9cd29f6`. Decided at ply 122 (turn 122, last token `S6`, by a six, win_at 121). Skipped turns: none.

Moves:

```
F3S5F5S5F5S5F5S5F5S3F3S3F3S3F3S3F8S8F8S8F8S8F4S4F4S8F4SbF8S9F7S9F9S4F7S7F4S4*F4S7F5S7F7S3F9S5F5S7F6S4F6S7F3S9F3S9F9Sa*FbSaF4SaF2S2F2*S1F6SaFaSaF0SaF2SaFaS3F3S3F2S2F2S1*F6SbF2S2F2S1F2S0F2S2F9S9F9S0FbS0F1S1F0S1FbSbFbSbFbS0*F0S0F0S0F1S0F1S1F1*S1F2S6F6S6
```

Position after the deciding move (top row first, F = X, S = O):

```
...OXX.OXXXX
...XOOOOOOOO
.OXOXXXXOXOX
OXXXOOOOXXOO
XXOXXXXOOOXX
OOXOXOXOXOOX
OXXXOXXXOXOO
OOOXXOXXXXOX
0123456789ab
```

## Timeouts and out-of-memory

Timeouts are the per-side letters of the log (an own timeout also gives an 'Invalid column selected: -1' line). OOM lines (`Memory limit exceeded: N MB used`, then an uncaught SecurityException) are not tagged with a side or bot, at most one per game. Warn = games with memory-warning lines in the log (both bots).

| Bot | Games | Own timeouts | Games with own timeout | Own full-column turns | Games with an OOM line | OOM MB min/median/p90/max | Games with warn lines |
|---|---|---|---|---|---|---|---|
| Testimine-M7 | 91 | 0 | 0 | 0 | 39 (42.9%) | 200/230/300/331 | 22 (max 199 MB) |
| Savi5 beater 2.1 | 79 | 3 | 3 | 0 | 52 (65.8%) | 200/230/309/336 | 27 (max 199 MB) |
| Savi5 | 58 | 0 | 0 | 0 | 30 (51.7%) | 200/206.5/264/302 | 24 (max 199 MB) |
| Savi6 - "Beat this" | 57 | 1 | 1 | 0 | 42 (73.7%) | 200/213/240/277 | 25 (max 199 MB) |
| Testing_v2 | 57 | 50 | 34 | 0 | 37 (64.9%) | 200/225/305/323 | 19 (max 199 MB) |
| BeetaSaag | 46 | 0 | 0 | 7 | 22 (47.8%) | 200/211/258/281 | 19 (max 198 MB) |
| Strange Quark 1 | 46 | 0 | 0 | 0 | 29 (63.0%) | 201/217/258/274 | 17 (max 199 MB) |
| Hurt me plenty | 44 | 88 | 31 | 6 | 23 (52.3%) | 200/236/331/336 | 12 (max 199 MB) |
| AntiStrat_FINAL | 36 | 0 | 0 | 0 | 11 (30.6%) | 200/227/286/295 | 14 (max 198 MB) |
| \u006c\u0061\u006c\2 | 36 | 0 | 0 | 0 | 19 (52.8%) | 200/216/269/271 | 13 (max 199 MB) |
| lalalabot | 34 | 0 | 0 | 0 | 26 (76.5%) | 200/236/277/280 | 4 (max 199 MB) |
| good night | 33 | 0 | 0 | 0 | 25 (75.8%) | 200/273/318/331 | 2 (max 199 MB) |
| sleep tight | 33 | 0 | 0 | 0 | 33 (100.0%) | 223/265/294/302 | 0 |
| specialmovedestroyer | 32 | 0 | 0 | 0 | 22 (68.8%) | 200/210.5/232/250 | 16 (max 199 MB) |

205 of 341 games (60.1%) contain an OOM line.

Which side timed out in games with / without an OOM line, seen from each bot (own = only this bot's side, opp = only its opponent's, both, none):

| Bot | OOM games: own / opp / both / none | Other games: own / opp / both / none |
|---|---|---|
| Testimine-M7 | 0 / 2 / 0 / 37 | 0 / 4 / 0 / 48 |
| Savi5 beater 2.1 | 2 / 7 / 1 / 42 | 0 / 0 / 0 / 27 |
| Savi5 | 0 / 3 / 0 / 27 | 0 / 7 / 0 / 21 |
| Savi6 - "Beat this" | 1 / 7 / 0 / 34 | 0 / 3 / 0 / 12 |
| Testing_v2 | 23 / 1 / 0 / 13 | 11 / 1 / 0 / 8 |
| BeetaSaag | 0 / 2 / 0 / 20 | 0 / 3 / 0 / 21 |
| Strange Quark 1 | 0 / 1 / 0 / 28 | 0 / 0 / 0 / 17 |
| Hurt me plenty | 15 / 0 / 1 / 7 | 15 / 2 / 0 / 4 |
| AntiStrat_FINAL | 0 / 2 / 0 / 9 | 0 / 3 / 0 / 22 |
| \u006c\u0061\u006c\2 | 0 / 2 / 0 / 17 | 0 / 1 / 0 / 16 |
| lalalabot | 0 / 4 / 0 / 22 | 0 / 0 / 0 / 8 |
| good night | 0 / 4 / 0 / 21 | 0 / 0 / 0 / 8 |
| sleep tight | 0 / 3 / 0 / 30 | 0 / 0 / 0 / 0 |
| specialmovedestroyer | 0 / 3 / 0 / 19 | 0 / 2 / 0 / 8 |

First timeout or full-column line at or after the OOM line, by whose side it hit (own / opp / no later event):

| Bot | own / opp / none |
|---|---|
| Testimine-M7 | 0 / 2 / 37 |
| Savi5 beater 2.1 | 3 / 7 / 42 |
| Savi5 | 0 / 0 / 30 |
| Savi6 - "Beat this" | 1 / 4 / 37 |
| Testing_v2 | 17 / 1 / 19 |
| BeetaSaag | 1 / 2 / 19 |
| Strange Quark 1 | 0 / 1 / 28 |
| Hurt me plenty | 13 / 1 / 9 |
| AntiStrat_FINAL | 0 / 2 / 9 |
| \u006c\u0061\u006c\2 | 0 / 1 / 18 |
| lalalabot | 0 / 4 / 22 |
| good night | 0 / 5 / 20 |
| sleep tight | 0 / 3 / 30 |
| specialmovedestroyer | 0 / 2 / 20 |

Games with an OOM line followed by a later timeout line: 35 of 205; delay from the OOM line to it, ms min/median/p90/max = 3/300/834/1049.

Games where a timeout line precedes the OOM line: 10.

OOM games / games for every pair of bots (both colours); a bot that exhausts memory shows up as a column and row of high shares:

| # | Bot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Testimine-M7 | . | 0/7 | 2/7 | 3/7 | 0/7 | 0/7 | 3/7 | 2/7 | 3/7 | 1/7 | 5/7 | 7/7 | 7/7 | 6/7 |
| 2 | Savi5 beater 2.1 | 0/7 | . | 4/5 | 3/3 | 4/4 | 5/7 | 6/7 | 6/6 | 0/7 | 6/6 | 7/7 | 1/7 | 6/6 | 4/7 |
| 3 | Savi5 | 2/7 | 4/5 | . |  | 3/5 | 3/7 | 3/6 | 2/7 | 3/7 | 3/7 |  |  | 7/7 |  |
| 4 | Savi6 - "Beat this" | 3/7 | 3/3 |  | . | 5/5 | 5/7 | 7/7 | 4/7 |  |  | 3/7 | 7/7 |  | 5/7 |
| 5 | Testing_v2 | 0/7 | 4/4 | 3/5 | 5/5 | . | 3/4 | 3/3 | 1/4 | 2/5 | 3/4 | 3/3 | 5/5 | 3/3 | 2/5 |
| 6 | BeetaSaag | 0/7 | 5/7 | 3/7 | 5/7 | 3/4 | . | 0/2 | 0/2 | 0/2 | 1/2 | 1/2 | 1/1 | 2/2 | 1/1 |
| 7 | Strange Quark 1 | 3/7 | 6/7 | 3/6 | 7/7 | 3/3 | 0/2 | . | 1/2 | 0/2 | 0/2 | 2/2 | 0/2 | 2/2 | 2/2 |
| 8 | Hurt me plenty | 2/7 | 6/6 | 2/7 | 4/7 | 1/4 | 0/2 | 1/2 | . | 1/2 | 1/2 | 2/2 |  | 2/2 | 1/1 |
| 9 | AntiStrat_FINAL | 3/7 | 0/7 | 3/7 |  | 2/5 | 0/2 | 0/2 | 1/2 | . | 0/2 |  |  | 2/2 |  |
| 10 | \u006c\u0061\u006c\2 | 1/7 | 6/6 | 3/7 |  | 3/4 | 1/2 | 0/2 | 1/2 | 0/2 | . |  | 2/2 | 2/2 |  |
| 11 | lalalabot | 5/7 | 7/7 |  | 3/7 | 3/3 | 1/2 | 2/2 | 2/2 |  |  | . | 2/2 |  | 1/2 |
| 12 | good night | 7/7 | 1/7 |  | 7/7 | 5/5 | 1/1 | 0/2 |  |  | 2/2 | 2/2 | . |  |  |
| 13 | sleep tight | 7/7 | 6/6 | 7/7 |  | 3/3 | 2/2 | 2/2 | 2/2 | 2/2 | 2/2 |  |  | . |  |
| 14 | specialmovedestroyer | 6/7 | 4/7 |  | 5/7 | 2/5 | 1/1 | 2/2 | 1/1 |  |  | 1/2 |  |  | . |

Median OOM size in MB for every pair of bots (the figure is the memory in use when the limit tripped; abrupt jumps give large values, slow growth gives values just above 200):

| # | Bot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Testimine-M7 |  |  | 203 | 200 |  |  | 224 | 207.5 | 215 | 210 | 229 | 292 | 265 | 225 |
| 2 | Savi5 beater 2.1 |  |  | 200 | 205 | 307 | 206 | 211 | 321 |  | 248 | 230 | 306 | 274 | 202 |
| 3 | Savi5 | 203 | 200 |  |  | 202 | 207 | 203 | 206 | 214 | 209 |  |  | 264 |  |
| 4 | Savi6 - "Beat this" | 200 | 205 |  |  | 201 | 206 | 217 | 208.5 |  |  | 240 | 213 |  | 214 |
| 5 | Testing_v2 |  | 307 | 202 | 201 |  | 228 | 208 | 271 | 222 | 200 | 227 | 284 | 262 | 200 |
| 6 | BeetaSaag |  | 206 | 207 | 206 | 228 |  |  |  |  | 211 | 249 | 258 | 276 | 216 |
| 7 | Strange Quark 1 | 224 | 211 | 203 | 217 | 208 |  |  | 236 |  |  | 270.5 |  | 253.5 | 210.5 |
| 8 | Hurt me plenty | 207.5 | 321 | 206 | 208.5 | 271 |  | 236 |  | 231 | 201 | 244.5 |  | 268 | 232 |
| 9 | AntiStrat_FINAL | 215 |  | 214 |  | 222 |  |  | 231 |  |  |  |  | 290.5 |  |
| 10 | \u006c\u0061\u006c\2 | 210 | 248 | 209 |  | 200 | 211 |  | 201 |  |  |  | 263.5 | 264.5 |  |
| 11 | lalalabot | 229 | 230 |  | 240 | 227 | 249 | 270.5 | 244.5 |  |  |  | 276 |  | 203 |
| 12 | good night | 292 | 306 |  | 213 | 284 | 258 |  |  |  | 263.5 | 276 |  |  |  |
| 13 | sleep tight | 265 | 274 | 264 |  | 262 | 276 | 253.5 | 268 | 290.5 | 264.5 |  |  |  |  |
| 14 | specialmovedestroyer | 225 | 202 |  | 214 | 200 | 216 | 210.5 | 232 |  |  | 203 |  |  |  |

## Wall-clock evidence

The exported log lines are only ERROR lines (OOM, timeout, invalid column, full column); they carry millisecond timestamps (707 lines) but there is no per-move or start/end line, so a game's duration can only be bracketed from its first to its last timestamped event.

36 games have two or more paired timeout lines (log events match the skipped turns found by the replay). Between consecutive such events (73 intervals, 643 turns): time per turn in ms, min/median/p90/max = 0/9/53/89, pooled mean 16 ms per turn (10.2 s over 643 turns). First-to-last event span per game, ms min/median/p90/max = 1/187/784/1151, covering turns 2/13/38/52. Consecutive timeouts can be logged only a few ms apart although each is reported as exceeding 100 ms, so the log timestamps are not a reliable clock for single moves; they give an order of magnitude only (games of this many turns are logged within about a second, far below 30 s).

