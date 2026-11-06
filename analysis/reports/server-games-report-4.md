# Server games report

1020 unique games (from 1054 lines in 1 export file(s), 1054 unique ids, 34 dropped as same players and moves). Draws are not stored by the server, so every game here is decided and the W/L tables contain no draws. Plies are recorded moves (blasts included, skipped turns not); turns also count skipped turns.

## Notation and replay check

Replay with the engine rules (gravity, colour-blind six, 3x3 blast, a blast needs a cooldown counter of at least 10, every own turn, skipped ones included, counts toward it): **1020 of 1020 games consistent** (recorded winner is the replay winner, the line appears exactly at win_at, no earlier line, no disc into a full column, no blast before the cooldown).

Board size per game: the "board" field of the export (page = read from the snapshot, inferred = guessed by the exporter from the time), else the first of 8x12, 15x11, 15x22 that replays. A game that fits a smaller board also fits any larger one that contains it (8x12 and 15x11 sit inside 15x22), so the moves prove a size only when they do not replay on the smaller ones (the last three columns).

| Size (rows x cols) | Games | Consistent | On recorded size | On guessed size | Board from page | Board inferred | Replays also on 8x12 | on 15x11 | on 15x22 |
|---|---|---|---|---|---|---|---|---|---|
| 15x11 | 356 | 356 | 356 | 0 | 356 | 0 | 167 | 356 | 356 |
| 15x22 | 323 | 323 | 323 | 0 | 166 | 157 | 102 | 141 | 323 |
| 8x12 | 341 | 341 | 341 | 0 | 0 | 341 | 341 | 223 | 341 |

- `*` after a token marks a blast in that column: 1115 blasts in 411 games; smallest cooldown counter at a blast 10; earliest blast is the own turn #10 (0-based, own turns counted from 0); smallest gap between two blasts of one side 10 own turns. Replaying with skipped turns NOT counting toward the cooldown gives 1011 of 1020 consistent (against 1020 with the opposite rule), so the data favour the above rule.
- A skipped turn (timeout or full column) leaves no token; replay finds 1674 of them as breaks in the F/S alternation. In 982 of 1020 games the breaks equal the logged timeout / column-full lines in count and sides (a break is invisible when both sides skip in a row).

## Per bot

Decided by: games of this bot ending in a normal six / in a blast (either side winning). Blasts used counts every `*` token by this bot. Length is plies per game.

| Bot | Games | W/L | As F W/L | As S W/L | Six / blast games | Its wins six / blast | Blasts used | Mean plies | Median plies |
|---|---|---|---|---|---|---|---|---|---|
| Savi5 beater 2.1 | 229 | 219/10 | 102/5 | 117/5 | 195 / 34 | 188 / 31 | 321 | 56.9 | 34.0 |
| Testing_v2 | 148 | 51/97 | 28/41 | 23/56 | 143 / 5 | 48 / 3 | 71 | 30.6 | 15.0 |
| FunkyMelodyBot | 146 | 25/121 | 17/64 | 8/57 | 146 / 0 | 25 / 0 | 0 | 26.0 | 18.0 |
| Hurt me plenty | 135 | 49/86 | 23/39 | 26/47 | 131 / 4 | 49 / 0 | 0 | 32.8 | 33.0 |
| BeetaSaag | 128 | 52/76 | 25/32 | 27/44 | 106 / 22 | 52 / 0 | 0 | 42.7 | 37.0 |
| Strange Quark 1 | 128 | 34/94 | 19/42 | 15/52 | 120 / 8 | 34 / 0 | 0 | 20.8 | 17.5 |
| specialmovedestroyer | 124 | 60/64 | 30/31 | 30/33 | 105 / 19 | 59 / 1 | 45 | 58.9 | 45.0 |
| HereWeGoAgain | 97 | 29/68 | 15/38 | 14/30 | 94 / 3 | 29 / 0 | 2 | 26.3 | 17.0 |
| lalalalabot | 94 | 54/40 | 28/17 | 26/23 | 78 / 16 | 54 / 0 | 31 | 48.9 | 30.0 |
| Testimine-M7 | 91 | 2/89 | 2/38 | 0/51 | 91 / 0 | 2 / 0 | 35 | 23.9 | 19.0 |
| Back To Basics | 72 | 19/53 | 11/27 | 8/26 | 72 / 0 | 19 / 0 | 41 | 22.9 | 16.0 |
| Savi8 - "The King" | 71 | 71/0 | 38/0 | 33/0 | 58 / 13 | 58 / 13 | 70 | 60.3 | 39.0 |
| Savi beater 2.2 | 67 | 58/9 | 33/4 | 25/5 | 53 / 14 | 45 / 13 | 128 | 65.9 | 42.0 |
| Savi7 - "The Prince" | 59 | 52/7 | 29/3 | 23/4 | 48 / 11 | 43 / 9 | 108 | 68.4 | 43.0 |
| Savi5 | 58 | 53/5 | 27/3 | 26/2 | 58 / 0 | 53 / 0 | 63 | 51.8 | 38.5 |
| Savi 7 - "The Prince | 57 | 56/1 | 32/0 | 24/1 | 40 / 17 | 39 / 17 | 82 | 69.9 | 49.0 |
| Savi6 - "Beat this" | 57 | 55/2 | 30/0 | 25/2 | 56 / 1 | 54 / 1 | 47 | 53.2 | 44.0 |
| HereWeGo | 48 | 13/35 | 10/18 | 3/17 | 48 / 0 | 13 / 0 | 0 | 18.3 | 13.0 |
| AntiStrat_FINAL | 36 | 10/26 | 6/11 | 4/15 | 36 / 0 | 10 / 0 | 34 | 28.9 | 24.0 |
| \u006c\u0061\u006c\2 | 36 | 17/19 | 10/8 | 7/11 | 35 / 1 | 17 / 0 | 0 | 40.6 | 19.0 |
| I'm too young to die | 34 | 0/34 | 0/16 | 0/18 | 34 / 0 | 0 / 0 | 0 | 12.1 | 12.5 |
| lalalabot | 34 | 14/20 | 9/10 | 5/10 | 32 / 2 | 14 / 0 | 5 | 35.9 | 19.5 |
| good night | 33 | 11/22 | 7/11 | 4/11 | 27 / 6 | 11 / 0 | 14 | 40.2 | 33.0 |
| sleep tight | 33 | 9/24 | 8/10 | 1/14 | 33 / 0 | 9 / 0 | 18 | 22.9 | 18.0 |
| Savi6 - "Beat This" | 25 | 7/18 | 5/8 | 2/10 | 25 / 0 | 7 / 0 | 0 | 21.8 | 13.0 |

## Game length

All games: plies min/median/p90/max = 6/27.5/95/341, mean 40.5; turns (skips included) 11/29/98/342. Games ending by a blast: 88 of 1020 (8.6%). Games over 200/300/400 plies: 9/2/0 (over 200/300/400 turns: 9/2/0).

Plies in 20-ply buckets (all games):

| Plies | Games |  |
|---|---|---|
| 1-20 | 419 | ################################# |
| 21-40 | 272 | ###################### |
| 41-60 | 129 | ########### |
| 61-80 | 58 | ##### |
| 81-100 | 50 | #### |
| 101-120 | 35 | ### |
| 121-140 | 23 | ## |
| 141-160 | 13 | ## |
| 161-180 | 9 | # |
| 181-200 | 3 | # |
| 201-220 | 6 | # |
| 221-240 | 0 |  |
| 241-260 | 1 | # |
| 261-280 | 0 |  |
| 281-300 | 0 |  |
| 301-320 | 0 |  |
| 321-340 | 1 | # |
| 341-360 | 1 | # |

Per bot (plies; the over-N columns count games with more than N plies):

| Bot | Games | min/median/p90/max | > 200 | > 300 | > 400 |
|---|---|---|---|---|---|
| Savi5 beater 2.1 | 229 | 7/34/132/341 | 6 | 2 | 0 |
| Testing_v2 | 148 | 6/15/84/130 | 0 | 0 | 0 |
| FunkyMelodyBot | 146 | 8/18/51/109 | 0 | 0 | 0 |
| Hurt me plenty | 135 | 9/33/49/93 | 0 | 0 | 0 |
| BeetaSaag | 128 | 6/37/90/121 | 0 | 0 | 0 |
| Strange Quark 1 | 128 | 6/17.5/34/41 | 0 | 0 | 0 |
| specialmovedestroyer | 124 | 6/45/130/181 | 0 | 0 | 0 |
| HereWeGoAgain | 97 | 6/17/56/100 | 0 | 0 | 0 |
| lalalalabot | 94 | 6/30/129/201 | 1 | 0 | 0 |
| Testimine-M7 | 91 | 11/19/46/71 | 0 | 0 | 0 |
| Back To Basics | 72 | 6/16/43/109 | 0 | 0 | 0 |
| Savi8 - "The King" | 71 | 6/39/127/208 | 3 | 0 | 0 |
| Savi beater 2.2 | 67 | 8/42/155/208 | 3 | 0 | 0 |
| Savi7 - "The Prince" | 59 | 6/43/166/341 | 2 | 2 | 0 |
| Savi5 | 58 | 13/38.5/123/136 | 0 | 0 | 0 |
| Savi 7 - "The Prince | 57 | 7/49/152/242 | 3 | 0 | 0 |
| Savi6 - "Beat this" | 57 | 15/44/113/128 | 0 | 0 | 0 |
| HereWeGo | 48 | 6/13/35/56 | 0 | 0 | 0 |
| AntiStrat_FINAL | 36 | 11/24/53/67 | 0 | 0 | 0 |
| \u006c\u0061\u006c\2 | 36 | 11/19/100/112 | 0 | 0 | 0 |
| I'm too young to die | 34 | 6/12.5/17/30 | 0 | 0 | 0 |
| lalalabot | 34 | 11/19.5/93/112 | 0 | 0 | 0 |
| good night | 33 | 11/33/74/96 | 0 | 0 | 0 |
| sleep tight | 33 | 11/18/35/71 | 0 | 0 | 0 |
| Savi6 - "Beat This" | 25 | 6/13/50/64 | 0 | 0 | 0 |

Our team (No beaches?): 296 games, plies min/median/p90/max = 7/35/134/341, over 200/300/400 plies: 9/2/0.

## First-player advantage

First player (F) won 544 of 1020 (53.3%, normal-approximation 95% interval 50.3-56.4%). By a six: F 499, S 433; by a blast: F 45, S 43. In games without any timeout or full-column event: F 380 of 726 (52.3%). Bots differ in strength, so this mixes colour with pairing; the pairings are colour-balanced only if each ordered pair is played equally often.

## Head to head

Cell = wins of the row bot over the column bot, as wins-losses (all colours).

| # | Bot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 19 | 20 | 21 | 22 | 23 | 24 | 25 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Savi5 beater 2.1 | . | 17-0 | 14-0 | 20-0 | 21-0 | 20-0 | 21-0 | 14-0 | 13-0 | 7-0 | 10-0 |  |  | 5-2 | 4-1 | 1-5 | 1-2 | 7-0 | 7-0 | 6-0 | 4-0 | 7-0 | 7-0 | 6-0 | 7-0 |
| 2 | Testing_v2 | 0-17 | . | 2-11 | 6-4 | 4-2 | 3-2 | 5-4 | 0-8 | 0-6 | 7-0 | 0-2 | 0-7 | 0-7 | 0-5 | 1-4 | 0-7 | 1-4 | 0-5 | 5-0 | 4-0 | 2-0 | 3-0 | 5-0 | 3-0 | 0-2 |
| 3 | FunkyMelodyBot | 0-14 | 11-2 | . | 2-12 | 0-14 | 1-13 | 0-14 | 0-14 | 0-14 |  | 4-10 | 0-7 | 0-7 |  |  |  |  |  |  |  | 7-0 |  |  |  |  |
| 4 | Hurt me plenty | 0-20 | 4-6 | 12-2 | . | 1-3 | 4-0 | 0-5 | 6-0 | 0-4 | 7-0 | 2-0 | 0-7 | 0-6 | 0-7 | 0-7 | 0-7 | 0-7 | 6-0 | 2-0 | 0-2 | 2-0 | 0-2 |  | 1-1 | 2-0 |
| 5 | BeetaSaag | 0-21 | 2-4 | 14-0 | 3-1 | . | 2-0 | 1-4 | 4-0 | 1-2 | 7-0 | 2-0 | 0-7 | 0-7 | 0-6 | 0-7 | 0-7 | 0-7 | 6-0 | 2-0 | 1-1 | 2-0 | 1-1 | 1-0 | 1-1 | 2-0 |
| 6 | Strange Quark 1 | 0-20 | 2-3 | 13-1 | 0-4 | 0-2 | . | 0-4 | 4-2 | 0-4 | 7-0 | 1-1 | 0-7 | 0-7 | 0-7 | 0-6 | 0-7 | 0-7 | 3-2 | 0-2 | 0-2 | 2-0 | 0-2 | 0-2 | 1-1 | 1-1 |
| 7 | specialmovedestroyer | 0-21 | 4-5 | 14-0 | 5-0 | 4-1 | 4-0 | . | 6-0 | 2-2 | 7-0 | 4-0 | 0-7 | 0-7 | 1-6 |  | 0-7 | 0-7 | 4-0 |  |  | 2-0 | 1-1 |  |  | 2-0 |
| 8 | HereWeGoAgain | 0-14 | 8-0 | 14-0 | 0-6 | 0-4 | 2-4 | 0-6 | . | 0-7 |  | 3-2 | 0-6 | 0-7 | 0-7 |  | 0-5 |  |  |  |  | 2-0 |  |  |  |  |
| 9 | lalalalabot | 0-13 | 6-0 | 14-0 | 4-0 | 2-1 | 4-0 | 2-2 | 7-0 | . |  | 3-0 | 0-6 | 2-5 | 1-6 |  | 0-7 |  | 5-0 |  |  | 2-0 |  |  |  | 2-0 |
| 10 | Testimine-M7 | 0-7 | 0-7 |  | 0-7 | 0-7 | 0-7 | 0-7 |  |  | . |  |  |  |  | 0-7 |  | 0-7 |  | 0-7 | 0-7 |  | 0-7 | 0-7 | 2-5 |  |
| 11 | Back To Basics | 0-10 | 2-0 | 10-4 | 0-2 | 0-2 | 1-1 | 0-4 | 2-3 | 0-3 |  | . | 0-3 | 0-5 | 0-6 |  | 0-4 |  | 1-5 |  |  | 2-0 |  |  |  | 1-1 |
| 12 | Savi8 - "The King" |  | 7-0 | 7-0 | 7-0 | 7-0 | 7-0 | 7-0 | 6-0 | 6-0 |  | 3-0 | . | 7-0 |  |  |  |  |  |  |  | 7-0 |  |  |  |  |
| 13 | Savi beater 2.2 |  | 7-0 | 7-0 | 6-0 | 7-0 | 7-0 | 7-0 | 7-0 | 5-2 |  | 5-0 | 0-7 | . |  |  |  |  |  |  |  |  |  |  |  |  |
| 14 | Savi7 - "The Prince" | 2-5 | 5-0 |  | 7-0 | 6-0 | 7-0 | 6-1 | 7-0 | 6-1 |  | 6-0 |  |  | . |  |  |  |  |  |  |  |  |  |  |  |
| 15 | Savi5 | 1-4 | 4-1 |  | 7-0 | 7-0 | 6-0 |  |  |  | 7-0 |  |  |  |  | . |  |  |  | 7-0 | 7-0 |  |  |  | 7-0 |  |
| 16 | Savi 7 - "The Prince | 5-1 | 7-0 |  | 7-0 | 7-0 | 7-0 | 7-0 | 5-0 | 7-0 |  | 4-0 |  |  |  |  | . |  |  |  |  |  |  |  |  |  |
| 17 | Savi6 - "Beat this" | 2-1 | 4-1 |  | 7-0 | 7-0 | 7-0 | 7-0 |  |  | 7-0 |  |  |  |  |  |  | . |  |  |  |  | 7-0 | 7-0 |  |  |
| 18 | HereWeGo | 0-7 | 5-0 |  | 0-6 | 0-6 | 2-3 | 0-4 |  | 0-5 |  | 5-1 |  |  |  |  |  |  | . |  |  |  |  |  |  | 1-3 |
| 19 | AntiStrat_FINAL | 0-7 | 0-5 |  | 0-2 | 0-2 | 2-0 |  |  |  | 7-0 |  |  |  |  | 0-7 |  |  |  | . | 0-2 |  |  |  | 1-1 |  |
| 20 | \u006c\u0061\u006c\2 | 0-6 | 0-4 |  | 2-0 | 1-1 | 2-0 |  |  |  | 7-0 |  |  |  |  | 0-7 |  |  |  | 2-0 | . |  |  | 1-1 | 2-0 |  |
| 21 | I'm too young to die | 0-4 | 0-2 | 0-7 | 0-2 | 0-2 | 0-2 | 0-2 | 0-2 | 0-2 |  | 0-2 | 0-7 |  |  |  |  |  |  |  |  | . |  |  |  |  |
| 22 | lalalabot | 0-7 | 0-3 |  | 2-0 | 1-1 | 2-0 | 1-1 |  |  | 7-0 |  |  |  |  |  |  | 0-7 |  |  |  |  | . | 1-1 |  |  |
| 23 | good night | 0-7 | 0-5 |  |  | 0-1 | 2-0 |  |  |  | 7-0 |  |  |  |  |  |  | 0-7 |  |  | 1-1 |  | 1-1 | . |  |  |
| 24 | sleep tight | 0-6 | 0-3 |  | 1-1 | 1-1 | 1-1 |  |  |  | 5-2 |  |  |  |  | 0-7 |  |  |  | 1-1 | 0-2 |  |  |  | . |  |
| 25 | Savi6 - "Beat This" | 0-7 | 2-0 |  | 0-2 | 0-2 | 1-1 | 0-2 |  | 0-2 |  | 1-1 |  |  |  |  |  |  | 3-1 |  |  |  |  |  |  | . |

## Our team: No beaches?

296 games, 277 wins, 19 losses. Opponent column shows our bot's version in brackets; blasts are ours/theirs; our skipped turns are numbered by turn (skipped turns included, 1-based) and, when the log pairs up, their cause; the last column is the OOM line of the game, if any (not tagged with a bot).

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
| 80 | Hurt me plenty (Savi5 beater 2.1) | F | W | 23 | 27 | six | 0/0 | turn 17 (timeout) | 3 | - |
| 81 | BeetaSaag (Savi5 beater 2.1) | S | W | 32 | 32 | six | 0/0 | - | 0 | 201 MB |
| 82 | BeetaSaag (Savi5 beater 2.1) | F | W | 46 | 47 | six | 1/0 | turn 17 (timeout) | 0 | 207 MB |
| 83 | BeetaSaag (Savi5 beater 2.1) | S | W | 62 | 62 | six | 2/0 | - | 0 | 227 MB |
| 84 | BeetaSaag (Savi5 beater 2.1) | F | W | 19 | 19 | six | 0/0 | - | 0 | 200 MB |
| 85 | BeetaSaag (Savi5 beater 2.1) | S | W | 68 | 68 | blast | 2/0 | - | 0 | 230 MB |
| 86 | Testing_v2 (Savi5 beater 2.1) | F | W | 7 | 11 | six | 0/0 | - | 4 | 237 MB |
| 87 | Testing_v2 (Savi5 beater 2.1) | S | W | 12 | 22 | six | 0/0 | - | 10 | 201 MB |
| 88 | Testing_v2 (Savi5 beater 2.1) | F | W | 10 | 17 | six | 0/0 | - | 7 | 220 MB |
| 89 | Testing_v2 (Savi5 beater 2.1) | S | W | 13 | 26 | six | 0/0 | - | 13 | 253 MB |
| 90 | Testing_v2 (Savi5 beater 2.1) | F | W | 9 | 15 | six | 0/0 | - | 6 | 211 MB |
| 91 | Testing_v2 (Savi5 beater 2.1) | S | W | 7 | 12 | six | 0/0 | - | 5 | 247 MB |
| 92 | Hurt me plenty (Savi5 beater 2.1) | F | W | 76 | 103 | six | 1/0 | turn 1, turn 3, turn 17, turn 27, turn 33, turn 35, turn 37, turn 55, turn 59, turn 61 | 17 | 200 MB |
| 93 | Hurt me plenty (Savi5 beater 2.1) | S | W | 17 | 22 | blast | 1/0 | turn 2, turn 6, turn 16, turn 18, turn 20 | 0 | - |
| 94 | Hurt me plenty (Savi5 beater 2.1) | F | W | 9 | 11 | six | 0/0 | - | 2 | 201 MB |
| 95 | Hurt me plenty (Savi5 beater 2.1) | S | W | 42 | 42 | six | 1/0 | - | 0 | - |
| 96 | Hurt me plenty (Savi5 beater 2.1) | F | W | 10 | 13 | six | 0/0 | - | 3 | - |
| 97 | Hurt me plenty (Savi5 beater 2.1) | S | W | 23 | 24 | six | 0/0 | - | 1 | 205 MB |
| 98 | BeetaSaag (Savi5 beater 2.1) | F | W | 19 | 19 | six | 0/0 | - | 0 | - |
| 99 | BeetaSaag (Savi5 beater 2.1) | F | W | 29 | 29 | blast | 1/0 | - | 0 | 200 MB |
| 100 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 16 | 16 | six | 0/0 | - | 0 | 238 MB |
| 101 | Strange Quark 1 (Savi5 beater 2.1) | F | W | 33 | 33 | blast | 1/0 | - | 0 | 268 MB |
| 102 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 22 | 22 | six | 0/0 | - | 0 | 220 MB |
| 103 | Strange Quark 1 (Savi5 beater 2.1) | F | W | 21 | 21 | six | 0/0 | - | 0 | 251 MB |
| 104 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 130 | 130 | six | 5/2 | - | 0 | 278 MB |
| 105 | Back To Basics (Savi5 beater 2.1) | S | W | 19 | 20 | six | 0/0 | turn 2 (timeout) | 0 | 324 MB |
| 106 | Back To Basics (Savi5 beater 2.1) | F | W | 19 | 19 | six | 0/0 | - | 0 | 270 MB |
| 107 | Back To Basics (Savi5 beater 2.1) | S | W | 20 | 20 | six | 0/0 | - | 0 | 303 MB |
| 108 | Back To Basics (Savi5 beater 2.1) | S | W | 18 | 18 | six | 0/0 | - | 0 | 355 MB |
| 109 | HereWeGo (Savi5 beater 2.1) | S | W | 30 | 30 | six | 1/0 | - | 0 | 302 MB |
| 110 | HereWeGo (Savi5 beater 2.1) | S | W | 28 | 28 | six | 0/0 | - | 0 | 288 MB |
| 111 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 30 | 30 | six | 0/0 | - | 0 | 290 MB |
| 112 | Strange Quark 1 (Savi5 beater 2.1) | F | W | 15 | 15 | six | 0/0 | - | 0 | 284 MB |
| 113 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 16 | 16 | six | 0/0 | - | 0 | 317 MB |
| 114 | specialmovedestroyer (Savi5 beater 2.1) | F | W | 19 | 19 | six | 0/0 | - | 0 | 282 MB |
| 115 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 144 | 144 | six | 2/1 | - | 0 | 314 MB |
| 116 | specialmovedestroyer (Savi5 beater 2.1) | F | W | 19 | 19 | six | 0/0 | - | 0 | 249 MB |
| 117 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 42 | 42 | six | 1/1 | - | 0 | 283 MB |
| 118 | specialmovedestroyer (Savi5 beater 2.1) | F | W | 111 | 111 | blast | 5/1 | - | 0 | 331 MB |
| 119 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 134 | 134 | six | 4/1 | - | 0 | 331 MB |
| 120 | Back To Basics (Savi5 beater 2.1) | S | W | 24 | 24 | six | 0/1 | - | 0 | 290 MB |
| 121 | HereWeGo (Savi5 beater 2.1) | F | W | 31 | 31 | six | 0/0 | - | 0 | 340 MB |
| 122 | HereWeGo (Savi5 beater 2.1) | F | W | 21 | 21 | six | 0/0 | - | 0 | - |
| 123 | HereWeGo (Savi5 beater 2.1) | S | W | 34 | 34 | six | 1/0 | - | 0 | - |
| 124 | HereWeGo (Savi5 beater 2.1) | F | W | 35 | 35 | six | 0/0 | - | 0 | - |
| 125 | HereWeGo (Savi5 beater 2.1) | S | W | 56 | 56 | six | 1/0 | - | 0 | - |
| 126 | lalalalabot (Savi5 beater 2.1) | S | W | 79 | 80 | six | 2/1 | turn 14 (timeout) | 0 | - |
| 127 | lalalalabot (Savi5 beater 2.1) | S | W | 90 | 90 | six | 2/1 | - | 0 | - |
| 128 | lalalalabot (Savi5 beater 2.1) | S | W | 14 | 14 | six | 0/0 | - | 0 | - |
| 129 | lalalalabot (Savi5 beater 2.1) | S | W | 92 | 92 | six | 4/1 | - | 0 | - |
| 130 | lalalalabot (Savi5 beater 2.1) | F | W | 13 | 13 | six | 0/0 | - | 0 | - |
| 131 | lalalalabot (Savi5 beater 2.1) | F | W | 17 | 17 | six | 0/0 | - | 0 | - |
| 132 | Savi6 - "Beat This" (Savi5 beater 2.1) | S | W | 50 | 52 | six | 1/0 | turn 2 (timeout), turn 14 (timeout) | 0 | 205 MB |
| 133 | Savi6 - "Beat This" (Savi5 beater 2.1) | F | W | 13 | 13 | six | 0/0 | - | 0 | - |
| 134 | Savi6 - "Beat This" (Savi5 beater 2.1) | S | W | 64 | 64 | six | 2/0 | - | 0 | 205 MB |
| 135 | Savi6 - "Beat This" (Savi5 beater 2.1) | F | W | 23 | 23 | six | 0/0 | - | 0 | - |
| 136 | Savi6 - "Beat This" (Savi5 beater 2.1) | S | W | 32 | 32 | six | 0/0 | - | 0 | 208 MB |
| 137 | Savi6 - "Beat This" (Savi5 beater 2.1) | F | W | 33 | 33 | six | 1/0 | - | 0 | 230 MB |
| 138 | Savi6 - "Beat This" (Savi5 beater 2.1) | S | W | 38 | 38 | six | 1/0 | - | 0 | 218 MB |
| 139 | Savi7 - "The Prince" (Savi5 beater 2.1) | S | W | 174 | 174 | six | 7/6 | - | 0 | 200 MB |
| 140 | Savi7 - "The Prince" (Savi5 beater 2.1) | S | W | 166 | 166 | six | 5/6 | - | 0 | 426 MB |
| 141 | Savi7 - "The Prince" (Savi5 beater 2.1) | F | W | 161 | 161 | blast | 6/6 | - | 0 | 435 MB |
| 142 | Savi7 - "The Prince" (Savi5 beater 2.1) | S | L | 169 | 169 | six | 7/7 | - | 0 | 441 MB |
| 143 | Savi7 - "The Prince" (Savi5 beater 2.1) | F | L | 341 | 342 | blast | 14/13 | turn 21 (timeout) | 0 | 447 MB |
| 144 | Savi7 - "The Prince" (Savi5 beater 2.1) | F | W | 340 | 341 | blast | 15/13 | turn 57 (timeout) | 0 | 201 MB |
| 145 | Savi7 - "The Prince" (Savi5 beater 2.1) | S | W | 66 | 66 | six | 2/2 | - | 0 | 200 MB |
| 146 | HereWeGoAgain (Savi5 beater 2.1) | S | W | 22 | 26 | six | 0/0 | - | 4 | 332 MB |
| 147 | HereWeGoAgain (Savi5 beater 2.1) | F | W | 23 | 23 | six | 0/0 | - | 0 | 200 MB |
| 148 | HereWeGoAgain (Savi5 beater 2.1) | S | W | 98 | 98 | six | 3/0 | - | 0 | 236 MB |
| 149 | HereWeGoAgain (Savi5 beater 2.1) | F | W | 31 | 31 | six | 1/0 | - | 0 | 343 MB |
| 150 | HereWeGoAgain (Savi5 beater 2.1) | S | W | 30 | 30 | six | 0/0 | - | 0 | 202 MB |
| 151 | HereWeGoAgain (Savi5 beater 2.1) | F | W | 43 | 43 | six | 1/0 | - | 0 | 228 MB |
| 152 | HereWeGoAgain (Savi5 beater 2.1) | S | W | 24 | 24 | six | 0/0 | - | 0 | 274 MB |
| 153 | FunkyMelodyBot (Savi5 beater 2.1) | S | W | 14 | 14 | six | 0/0 | - | 0 | - |
| 154 | FunkyMelodyBot (Savi5 beater 2.1) | S | W | 54 | 54 | six | 1/0 | - | 0 | 202 MB |
| 155 | FunkyMelodyBot (Savi5 beater 2.1) | F | W | 31 | 31 | six | 0/0 | - | 0 | 235 MB |
| 156 | FunkyMelodyBot (Savi5 beater 2.1) | F | W | 11 | 11 | six | 0/0 | - | 0 | - |
| 157 | FunkyMelodyBot (Savi5 beater 2.1) | F | W | 35 | 35 | six | 0/0 | - | 0 | - |
| 158 | FunkyMelodyBot (Savi5 beater 2.1) | S | W | 32 | 32 | six | 0/0 | - | 0 | 272 MB |
| 159 | FunkyMelodyBot (Savi5 beater 2.1) | S | W | 34 | 34 | six | 1/0 | - | 0 | - |
| 160 | Hurt me plenty (Savi5 beater 2.1) | S | W | 40 | 40 | six | 0/0 | - | 0 | 296 MB |
| 161 | Hurt me plenty (Savi5 beater 2.1) | F | W | 27 | 29 | six | 0/0 | - | 2 | 200 MB |
| 162 | BeetaSaag (Savi5 beater 2.1) | F | W | 78 | 79 | six | 2/0 | turn 31 (timeout) | 0 | 215 MB |
| 163 | BeetaSaag (Savi5 beater 2.1) | S | W | 75 | 76 | blast | 2/0 | turn 16 (timeout) | 0 | 290 MB |
| 164 | BeetaSaag (Savi5 beater 2.1) | F | W | 32 | 33 | six | 1/0 | turn 17 (timeout) | 0 | 200 MB |
| 165 | BeetaSaag (Savi5 beater 2.1) | F | W | 121 | 121 | six | 5/0 | - | 0 | 331 MB |
| 166 | BeetaSaag (Savi5 beater 2.1) | S | W | 86 | 86 | six | 3/0 | - | 0 | 271 MB |
| 167 | BeetaSaag (Savi5 beater 2.1) | F | W | 69 | 69 | blast | 2/0 | - | 0 | 204 MB |
| 168 | Testing_v2 (Savi5 beater 2.1) | F | W | 7 | 13 | six | 0/0 | - | 6 | 262 MB |
| 169 | Testing_v2 (Savi5 beater 2.1) | S | W | 11 | 20 | six | 0/0 | - | 9 | 298 MB |
| 170 | Testing_v2 (Savi5 beater 2.1) | F | W | 15 | 29 | six | 0/0 | - | 14 | 341 MB |
| 171 | Hurt me plenty (Savi5 beater 2.1) | F | W | 36 | 39 | six | 1/0 | turn 19 (timeout) | 2 | 202 MB |
| 172 | Hurt me plenty (Savi5 beater 2.1) | S | W | 45 | 46 | six | 1/0 | - | 1 | 252 MB |
| 173 | Hurt me plenty (Savi5 beater 2.1) | F | W | 41 | 41 | six | 1/0 | - | 0 | 303 MB |
| 174 | Hurt me plenty (Savi5 beater 2.1) | S | W | 64 | 66 | six | 2/0 | turn 28 (timeout) | 1 | 202 MB |
| 175 | Hurt me plenty (Savi5 beater 2.1) | F | W | 41 | 45 | six | 1/0 | turn 23 (timeout) | 3 | 244 MB |
| 176 | BeetaSaag (Savi5 beater 2.1) | S | W | 87 | 88 | six | 1/0 | turn 42 (timeout) | 0 | 239 MB |
| 177 | Testing_v2 (Savi5 beater 2.1) | S | W | 9 | 18 | six | 0/0 | - | 9 | 228 MB |
| 178 | Testing_v2 (Savi5 beater 2.1) | S | W | 10 | 18 | six | 0/0 | - | 8 | 390 MB |
| 179 | Testing_v2 (Savi5 beater 2.1) | F | W | 10 | 17 | six | 0/0 | - | 7 | 236 MB |
| 180 | Strange Quark 1 (Savi5 beater 2.1) | F | W | 33 | 33 | six | 0/0 | - | 0 | 255 MB |
| 181 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 26 | 26 | six | 0/0 | - | 0 | 304 MB |
| 182 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 28 | 28 | six | 0/0 | - | 0 | 396 MB |
| 183 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 34 | 34 | six | 0/0 | - | 0 | 315 MB |
| 184 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 181 | 182 | blast | 7/1 | turn 48 (timeout) | 0 | 326 MB |
| 185 | specialmovedestroyer (Savi5 beater 2.1) | F | W | 118 | 119 | six | 5/1 | turn 15 (timeout) | 0 | 351 MB |
| 186 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 128 | 128 | six | 4/1 | - | 0 | 202 MB |
| 187 | specialmovedestroyer (Savi5 beater 2.1) | F | W | 115 | 115 | six | 4/1 | - | 0 | 203 MB |
| 188 | Back To Basics (Savi5 beater 2.1) | S | W | 16 | 16 | six | 0/0 | - | 0 | 201 MB |
| 189 | Back To Basics (Savi5 beater 2.1) | F | W | 17 | 17 | six | 0/0 | - | 0 | 223 MB |
| 190 | Back To Basics (Savi5 beater 2.1) | S | W | 20 | 20 | six | 0/0 | - | 0 | 207 MB |
| 191 | Testing_v2 (Savi5 beater 2.1) | F | W | 11 | 19 | six | 0/0 | - | 8 | 301 MB |
| 192 | Strange Quark 1 (Savi5 beater 2.1) | S | W | 24 | 24 | six | 0/0 | - | 0 | 392 MB |
| 193 | Strange Quark 1 (Savi5 beater 2.1) | F | W | 31 | 31 | six | 0/0 | - | 0 | 268 MB |
| 194 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 164 | 164 | six | 5/1 | - | 0 | 201 MB |
| 195 | specialmovedestroyer (Savi5 beater 2.1) | F | W | 149 | 149 | blast | 4/0 | - | 0 | 200 MB |
| 196 | specialmovedestroyer (Savi5 beater 2.1) | S | W | 142 | 142 | blast | 4/0 | - | 0 | 246 MB |
| 197 | Back To Basics (Savi5 beater 2.1) | S | W | 16 | 16 | six | 0/0 | - | 0 | - |
| 198 | Back To Basics (Savi5 beater 2.1) | S | W | 24 | 24 | six | 0/1 | - | 0 | 259 MB |
| 199 | lalalalabot (Savi5 beater 2.1) | S | W | 132 | 132 | blast | 6/0 | - | 0 | 201 MB |
| 200 | lalalalabot (Savi5 beater 2.1) | S | W | 80 | 80 | blast | 3/1 | - | 0 | 203 MB |
| 201 | lalalalabot (Savi5 beater 2.1) | F | W | 187 | 187 | six | 4/1 | - | 0 | 258 MB |
| 202 | lalalalabot (Savi5 beater 2.1) | S | W | 92 | 92 | blast | 4/0 | - | 0 | 274 MB |
| 203 | lalalalabot (Savi5 beater 2.1) | F | W | 201 | 201 | blast | 7/2 | - | 0 | 250 MB |
| 204 | lalalalabot (Savi5 beater 2.1) | S | W | 82 | 82 | blast | 3/0 | - | 0 | 289 MB |
| 205 | HereWeGoAgain (Savi5 beater 2.1) | S | W | 33 | 34 | six | 1/0 | turn 18 (timeout) | 0 | 275 MB |
| 206 | HereWeGoAgain (Savi5 beater 2.1) | F | W | 47 | 47 | six | 1/0 | - | 0 | 208 MB |
| 207 | HereWeGoAgain (Savi5 beater 2.1) | S | W | 72 | 72 | six | 2/0 | - | 0 | 205 MB |
| 208 | HereWeGoAgain (Savi5 beater 2.1) | S | W | 58 | 58 | six | 2/0 | - | 0 | 303 MB |
| 209 | lalalalabot (Savi5 beater 2.1) | F | W | 55 | 55 | blast | 2/0 | - | 0 | 260 MB |
| 210 | HereWeGoAgain (Savi5 beater 2.1) | S | W | 28 | 28 | blast | 1/0 | - | 0 | 241 MB |
| 211 | HereWeGoAgain (Savi5 beater 2.1) | F | W | 27 | 27 | blast | 1/0 | - | 0 | 285 MB |
| 212 | HereWeGoAgain (Savi5 beater 2.1) | F | W | 31 | 31 | six | 1/0 | - | 0 | 256 MB |
| 213 | FunkyMelodyBot (Savi5 beater 2.1) | S | W | 54 | 54 | six | 2/0 | - | 0 | 228 MB |
| 214 | FunkyMelodyBot (Savi5 beater 2.1) | F | W | 33 | 33 | six | 1/0 | - | 0 | 277 MB |
| 215 | FunkyMelodyBot (Savi5 beater 2.1) | F | W | 39 | 39 | six | 1/0 | - | 0 | 215 MB |
| 216 | FunkyMelodyBot (Savi5 beater 2.1) | S | W | 28 | 28 | six | 1/0 | - | 0 | 259 MB |
| 217 | FunkyMelodyBot (Savi5 beater 2.1) | F | W | 41 | 41 | six | 1/0 | - | 0 | 296 MB |
| 218 | FunkyMelodyBot (Savi5 beater 2.1) | S | W | 28 | 28 | six | 1/0 | - | 0 | 206 MB |
| 219 | I'm too young to die (Savi5 beater 2.1) | S | W | 15 | 30 | six | 0/0 | - | 15 | 345 MB |
| 220 | I'm too young to die (Savi5 beater 2.1) | F | W | 15 | 29 | six | 0/0 | - | 14 | 243 MB |
| 221 | I'm too young to die (Savi5 beater 2.1) | F | W | 14 | 27 | six | 0/0 | - | 13 | 338 MB |
| 222 | I'm too young to die (Savi5 beater 2.1) | F | W | 15 | 29 | six | 0/0 | - | 14 | 291 MB |
| 223 | FunkyMelodyBot (Savi5 beater 2.1) | S | W | 24 | 24 | six | 0/0 | - | 0 | 316 MB |
| 224 | Savi 7 - "The Prince (Savi5 beater 2.1) | S | L | 210 | 211 | blast | 7/8 | turn 2 (timeout) | 0 | - |
| 225 | Savi 7 - "The Prince (Savi5 beater 2.1) | F | W | 209 | 209 | six | 8/4 | - | 0 | - |
| 226 | Savi 7 - "The Prince (Savi5 beater 2.1) | S | L | 153 | 153 | six | 6/4 | - | 0 | - |
| 227 | Savi 7 - "The Prince (Savi5 beater 2.1) | S | L | 182 | 183 | blast | 6/6 | - | 1 | - |
| 228 | Savi 7 - "The Prince (Savi5 beater 2.1) | F | L | 242 | 242 | six | 10/5 | - | 0 | - |
| 229 | Savi 7 - "The Prince (Savi5 beater 2.1) | F | L | 142 | 142 | six | 5/4 | - | 0 | - |
| 230 | BeetaSaag (Savi beater 2.2) | S | W | 88 | 88 | six | 3/0 | - | 0 | - |
| 231 | BeetaSaag (Savi beater 2.2) | S | W | 54 | 54 | six | 1/0 | - | 0 | - |
| 232 | BeetaSaag (Savi beater 2.2) | F | W | 65 | 65 | blast | 3/0 | - | 0 | - |
| 233 | BeetaSaag (Savi beater 2.2) | F | W | 103 | 103 | blast | 4/0 | - | 0 | - |
| 234 | BeetaSaag (Savi beater 2.2) | F | W | 92 | 93 | blast | 3/0 | turn 15 (timeout) | 0 | - |
| 235 | BeetaSaag (Savi beater 2.2) | F | W | 90 | 91 | blast | 3/0 | turn 15 (timeout) | 0 | - |
| 236 | BeetaSaag (Savi beater 2.2) | S | W | 76 | 76 | six | 2/0 | - | 0 | - |
| 237 | Hurt me plenty (Savi beater 2.2) | S | W | 34 | 34 | six | 0/0 | - | 0 | - |
| 238 | Hurt me plenty (Savi beater 2.2) | F | W | 33 | 33 | six | 0/0 | - | 0 | - |
| 239 | Hurt me plenty (Savi beater 2.2) | F | W | 42 | 43 | six | 1/0 | - | 1 | - |
| 240 | Hurt me plenty (Savi beater 2.2) | S | W | 36 | 36 | six | 0/0 | - | 0 | - |
| 241 | Hurt me plenty (Savi beater 2.2) | F | W | 27 | 29 | six | 0/0 | - | 2 | - |
| 242 | Hurt me plenty (Savi beater 2.2) | S | W | 48 | 50 | six | 1/0 | - | 2 | - |
| 243 | Testing_v2 (Savi beater 2.2) | F | W | 15 | 27 | six | 0/0 | - | 12 | - |
| 244 | Strange Quark 1 (Savi beater 2.2) | F | W | 31 | 31 | six | 0/0 | - | 0 | - |
| 245 | Strange Quark 1 (Savi beater 2.2) | S | W | 28 | 28 | blast | 1/0 | - | 0 | - |
| 246 | Strange Quark 1 (Savi beater 2.2) | F | W | 33 | 33 | six | 0/0 | - | 0 | - |
| 247 | Strange Quark 1 (Savi beater 2.2) | S | W | 32 | 32 | six | 0/0 | - | 0 | - |
| 248 | Strange Quark 1 (Savi beater 2.2) | S | W | 28 | 28 | six | 0/0 | - | 0 | - |
| 249 | Testing_v2 (Savi beater 2.2) | F | W | 14 | 25 | six | 0/0 | - | 11 | - |
| 250 | Testing_v2 (Savi beater 2.2) | S | W | 16 | 30 | six | 0/0 | - | 14 | - |
| 251 | Testing_v2 (Savi beater 2.2) | S | W | 14 | 26 | six | 0/0 | - | 12 | - |
| 252 | Testing_v2 (Savi beater 2.2) | F | W | 11 | 19 | six | 0/0 | - | 8 | - |
| 253 | Testing_v2 (Savi beater 2.2) | S | W | 14 | 26 | six | 0/0 | - | 12 | - |
| 254 | Testing_v2 (Savi beater 2.2) | F | W | 8 | 15 | six | 0/0 | - | 7 | - |
| 255 | Strange Quark 1 (Savi beater 2.2) | F | W | 31 | 31 | blast | 1/0 | - | 0 | - |
| 256 | Strange Quark 1 (Savi beater 2.2) | F | W | 29 | 29 | six | 0/0 | - | 0 | - |
| 257 | FunkyMelodyBot (Savi beater 2.2) | F | W | 42 | 43 | six | 1/0 | turn 15 (timeout) | 0 | 283 MB |
| 258 | FunkyMelodyBot (Savi beater 2.2) | S | W | 48 | 48 | six | 0/0 | - | 0 | 265 MB |
| 259 | FunkyMelodyBot (Savi beater 2.2) | F | W | 19 | 19 | six | 0/0 | - | 0 | 303 MB |
| 260 | specialmovedestroyer (Savi beater 2.2) | S | W | 146 | 146 | blast | 5/1 | - | 0 | 201 MB |
| 261 | Back To Basics (Savi beater 2.2) | F | W | 17 | 17 | six | 0/0 | - | 0 | 227 MB |
| 262 | lalalalabot (Savi beater 2.2) | F | W | 17 | 17 | six | 0/0 | - | 0 | 251 MB |
| 263 | FunkyMelodyBot (Savi beater 2.2) | F | W | 25 | 25 | six | 0/0 | - | 0 | 236 MB |
| 264 | specialmovedestroyer (Savi beater 2.2) | F | W | 155 | 155 | six | 6/1 | - | 0 | - |
| 265 | specialmovedestroyer (Savi beater 2.2) | S | W | 112 | 112 | six | 4/0 | - | 0 | - |
| 266 | specialmovedestroyer (Savi beater 2.2) | F | W | 127 | 127 | blast | 6/1 | - | 0 | - |
| 267 | specialmovedestroyer (Savi beater 2.2) | S | W | 116 | 116 | six | 4/1 | - | 0 | 201 MB |
| 268 | specialmovedestroyer (Savi beater 2.2) | F | W | 112 | 113 | blast | 4/0 | turn 15 (timeout) | 0 | 200 MB |
| 269 | specialmovedestroyer (Savi beater 2.2) | F | W | 85 | 87 | blast | 3/0 | turn 29 (timeout), turn 59 (timeout) | 0 | 205 MB |
| 270 | Back To Basics (Savi beater 2.2) | S | W | 24 | 24 | six | 0/1 | - | 0 | 200 MB |
| 271 | Back To Basics (Savi beater 2.2) | F | W | 25 | 25 | six | 0/1 | - | 0 | 209 MB |
| 272 | Back To Basics (Savi beater 2.2) | S | W | 30 | 30 | six | 0/1 | - | 0 | 207 MB |
| 273 | Back To Basics (Savi beater 2.2) | S | W | 24 | 24 | six | 0/1 | - | 0 | 207 MB |
| 274 | lalalalabot (Savi beater 2.2) | F | W | 19 | 19 | six | 0/0 | - | 0 | 200 MB |
| 275 | lalalalabot (Savi beater 2.2) | S | W | 106 | 106 | blast | 5/1 | - | 0 | 229 MB |
| 276 | lalalalabot (Savi beater 2.2) | F | W | 129 | 131 | blast | 4/0 | turn 15 (timeout), turn 91 (timeout) | 0 | 234 MB |
| 277 | lalalalabot (Savi beater 2.2) | S | L | 176 | 177 | six | 6/1 | turn 14 (timeout) | 0 | 208 MB |
| 278 | lalalalabot (Savi beater 2.2) | F | W | 52 | 53 | six | 1/1 | turn 17 (timeout) | 0 | 230 MB |
| 279 | lalalalabot (Savi beater 2.2) | S | L | 165 | 165 | six | 5/1 | - | 0 | 216 MB |
| 280 | HereWeGoAgain (Savi beater 2.2) | F | W | 40 | 41 | six | 1/0 | turn 15 (timeout) | 0 | 222 MB |
| 281 | HereWeGoAgain (Savi beater 2.2) | S | W | 61 | 62 | six | 2/0 | turn 16 (timeout) | 0 | 264 MB |
| 282 | HereWeGoAgain (Savi beater 2.2) | F | W | 37 | 37 | six | 1/0 | - | 0 | 257 MB |
| 283 | HereWeGoAgain (Savi beater 2.2) | S | W | 24 | 24 | blast | 1/0 | - | 0 | 247 MB |
| 284 | HereWeGoAgain (Savi beater 2.2) | F | W | 19 | 19 | six | 0/0 | - | 0 | 233 MB |
| 285 | HereWeGoAgain (Savi beater 2.2) | S | W | 52 | 52 | six | 1/0 | - | 0 | 267 MB |
| 286 | HereWeGoAgain (Savi beater 2.2) | F | W | 22 | 23 | six | 0/0 | turn 15 (timeout) | 0 | 261 MB |
| 287 | FunkyMelodyBot (Savi beater 2.2) | S | W | 42 | 42 | six | 1/0 | - | 0 | 266 MB |
| 288 | FunkyMelodyBot (Savi beater 2.2) | F | W | 35 | 35 | six | 1/0 | - | 0 | 253 MB |
| 289 | FunkyMelodyBot (Savi beater 2.2) | S | W | 66 | 66 | six | 2/0 | - | 0 | 291 MB |
| 290 | Savi8 - "The King" (Savi beater 2.2) | S | L | 201 | 201 | six | 6/3 | - | 0 | 328 MB |
| 291 | Savi8 - "The King" (Savi beater 2.2) | F | L | 112 | 112 | six | 4/3 | - | 0 | 285 MB |
| 292 | Savi8 - "The King" (Savi beater 2.2) | F | L | 144 | 146 | six | 6/5 | turn 17 (timeout), turn 91 (timeout) | 0 | 325 MB |
| 293 | Savi8 - "The King" (Savi beater 2.2) | S | L | 208 | 209 | six | 8/3 | turn 28 (timeout) | 0 | 353 MB |
| 294 | Savi8 - "The King" (Savi beater 2.2) | F | L | 203 | 204 | six | 7/3 | - | 1 | 342 MB |
| 295 | Savi8 - "The King" (Savi beater 2.2) | S | L | 128 | 129 | blast | 4/4 | turn 46 (timeout) | 0 | - |
| 296 | Savi8 - "The King" (Savi beater 2.2) | F | L | 157 | 158 | six | 6/5 | turn 35 (timeout) | 0 | - |

Our own timeout lines and the OOM line of the same game:

- Game #4 vs Hurt me plenty: our timeout line at turn 17; OOM line 311 MB, 696 ms before the timeout.
- Game #56 vs good night: our timeout line at turn 28; OOM line 306 MB, 747 ms before the timeout.
- Game #67 vs lalalabot: our timeout line at turn 20; OOM line 235 MB, 495 ms before the timeout.
- Game #80 vs Hurt me plenty: our timeout line at turn 17; OOM line absent.
- Game #82 vs BeetaSaag: our timeout line at turn 17; OOM line 207 MB, 358 ms after the timeout.
- Game #87 vs Testing_v2: our timeout line; OOM line 201 MB, 1480 ms before the timeout.
- Game #89 vs Testing_v2: our timeout line; OOM line 253 MB, 2076 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 8104 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 8001 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 7800 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 7403 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 7302 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 6999 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 6803 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 6618 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 6602 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 6500 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 6400 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 6399 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 6196 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 5804 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 5598 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 5502 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 5204 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 5100 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 5004 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 4900 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 4700 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 4502 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 4403 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 4299 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 4000 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3700 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3618 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3404 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3403 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3303 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3204 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2904 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2799 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2503 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2402 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2303 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2200 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2102 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2003 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1803 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1617 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1403 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1302 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1201 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1199 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1001 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 904 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 702 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 618 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 400 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 203 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 18 ms after the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 296 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 300 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 398 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 500 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 599 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 801 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1096 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1098 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1383 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1597 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1782 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1897 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 1999 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2196 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2197 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2498 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2696 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2797 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 2904 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3098 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3198 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3300 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3582 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3800 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3897 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 3998 ms before the timeout.
- Game #92 vs Hurt me plenty: our timeout line; OOM line 200 MB, 4197 ms before the timeout.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #93 vs Hurt me plenty: our timeout line; OOM line absent.
- Game #105 vs Back To Basics: our timeout line at turn 2; OOM line 324 MB, 1397 ms before the timeout.
- Game #126 vs lalalalabot: our timeout line at turn 14; OOM line absent.
- Game #132 vs Savi6 - "Beat This": our timeout line at turn 2; OOM line 205 MB, 1113 ms before the timeout.
- Game #132 vs Savi6 - "Beat This": our timeout line at turn 14; OOM line 205 MB, 1521 ms before the timeout.
- Game #143 vs Savi7 - "The Prince": our timeout line at turn 21; OOM line 447 MB, 1222 ms before the timeout.
- Game #144 vs Savi7 - "The Prince": our timeout line at turn 57; OOM line 201 MB, 2453 ms before the timeout.
- Game #146 vs HereWeGoAgain: our timeout line; OOM line 332 MB, 5000 ms before the timeout.
- Game #146 vs HereWeGoAgain: our timeout line; OOM line 332 MB, 6000 ms before the timeout.
- Game #162 vs BeetaSaag: our timeout line at turn 31; OOM line 215 MB, 857 ms before the timeout.
- Game #163 vs BeetaSaag: our timeout line at turn 16; OOM line 290 MB, 646 ms before the timeout.
- Game #164 vs BeetaSaag: our timeout line at turn 17; OOM line 200 MB, 630 ms before the timeout.
- Game #170 vs Testing_v2: our timeout line; OOM line 341 MB, 2197 ms before the timeout.
- Game #170 vs Testing_v2: our timeout line; OOM line 341 MB, 2795 ms before the timeout.
- Game #170 vs Testing_v2: our timeout line; OOM line 341 MB, 2997 ms before the timeout.
- Game #171 vs Hurt me plenty: our timeout line at turn 19; OOM line 202 MB, 1802 ms before the timeout.
- Game #174 vs Hurt me plenty: our timeout line at turn 28; OOM line 202 MB, 480 ms before the timeout.
- Game #175 vs Hurt me plenty: our timeout line at turn 23; OOM line 244 MB, 882 ms before the timeout.
- Game #176 vs BeetaSaag: our timeout line at turn 42; OOM line 239 MB, 1278 ms before the timeout.
- Game #177 vs Testing_v2: our timeout line; OOM line 228 MB, 1797 ms before the timeout.
- Game #184 vs specialmovedestroyer: our timeout line at turn 48; OOM line 326 MB, 2028 ms before the timeout.
- Game #185 vs specialmovedestroyer: our timeout line at turn 15; OOM line 351 MB, 670 ms before the timeout.
- Game #205 vs HereWeGoAgain: our timeout line at turn 18; OOM line 275 MB, 1999 ms before the timeout.
- Game #224 vs Savi 7 - "The Prince: our timeout line at turn 2; OOM line absent.
- Game #234 vs BeetaSaag: our timeout line at turn 15; OOM line absent.
- Game #235 vs BeetaSaag: our timeout line at turn 15; OOM line absent.
- Game #236 vs BeetaSaag: our timeout line; OOM line absent.
- Game #253 vs Testing_v2: our timeout line; OOM line absent.
- Game #254 vs Testing_v2: our timeout line; OOM line absent.
- Game #257 vs FunkyMelodyBot: our timeout line at turn 15; OOM line 283 MB, 759 ms before the timeout.
- Game #268 vs specialmovedestroyer: our timeout line at turn 15; OOM line 200 MB, 90 ms before the timeout.
- Game #269 vs specialmovedestroyer: our timeout line at turn 29; OOM line 205 MB, 1000 ms before the timeout.
- Game #269 vs specialmovedestroyer: our timeout line at turn 59; OOM line 205 MB, 1898 ms before the timeout.
- Game #276 vs lalalalabot: our timeout line at turn 15; OOM line 234 MB, 737 ms before the timeout.
- Game #276 vs lalalalabot: our timeout line at turn 91; OOM line 234 MB, 2638 ms before the timeout.
- Game #277 vs lalalalabot: our timeout line at turn 14; OOM line 208 MB, 351 ms before the timeout.
- Game #278 vs lalalalabot: our timeout line at turn 17; OOM line 230 MB, 757 ms before the timeout.
- Game #280 vs HereWeGoAgain: our timeout line at turn 15; OOM line 222 MB, 757 ms before the timeout.
- Game #281 vs HereWeGoAgain: our timeout line at turn 16; OOM line 264 MB, 700 ms before the timeout.
- Game #286 vs HereWeGoAgain: our timeout line at turn 15; OOM line 261 MB, 665 ms before the timeout.
- Game #292 vs Savi8 - "The King": our timeout line at turn 17; OOM line 325 MB, 1148 ms before the timeout.
- Game #292 vs Savi8 - "The King": our timeout line at turn 91; OOM line 325 MB, 5448 ms before the timeout.
- Game #293 vs Savi8 - "The King": our timeout line at turn 28; OOM line 353 MB, 1832 ms before the timeout.
- Game #295 vs Savi8 - "The King": our timeout line at turn 46; OOM line absent.
- Game #296 vs Savi8 - "The King": our timeout line at turn 35; OOM line absent.

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

### Loss: Savi5 beater 2.1 (S) vs Savi7 - "The Prince" (F)

Match id `0afc31d4-bd49-4864-baea-588fccec4e36`. Decided at ply 169 (turn 169, last token `Fd`, by a six, win_at 168). Skipped turns: none.

Moves:

```
FaSbFaSaFbSbFaSaFaSaFaSbFbSbFbSbFaSbFaSaFbSbF5S9*Fa*SaFbS8FcS6FbS7F9S9F9S9F8S8F7S9FaSbFaSaF5S4*F9S9F8*S8FbS9F4SbFaSaFbSfFfSfFfSfF9S9FfScFfScFc*Sb*F9ScF9S9FbS9F9SbFaS9FfSfF4S6F6SaFbS9FbS3*Fc*SbFfS6F6S6F6S6F4SbFfSaFbSfF4ScF6S4FcScFcSc*Fc*S4F4S6FcScFcScFcS6F6S6F4ScFcScFbScF8S8FdS4Fd*Sd*FcS4FdSdFdScF4S4FcScFcS8F4S4FdSdFaSeFfScFfS6FfS4F4S8F8S3*FdSdFd*ScFd
```

Position after the deciding move (top row first, F = X, S = O):

```
..........X....X......
......O..OO....X......
......O..OO....X......
....X.X..XX.O..O......
....O.O..OO.O..X......
....O.O..OX.X..X......
....X.X..XOXO..O......
....O.O.XXXXX..X......
....X.X.OOXOOX.X......
....O.O.OXXOXX.X......
....O.X.OOOXOO.O......
....X.O.XOXXXX.X......
....X.X.OXOXOX.O......
....O.OXXOXXXO.X......
....O.OOOXOOOXOO......
0123456789abcdefghijkl
```

### Loss: Savi5 beater 2.1 (F) vs Savi7 - "The Prince" (S)

Match id `0eff6434-8158-419f-800e-3cf29fa0a10a`. Decided at ply 341 (turn 342, last token `Sd*`, by a blast, win_at 340). Skipped turns: [Skip[side=1, turn=20, kind=T]].

Moves:

```
FaSaFaSaFaSaFaSaFaSaFaSbFaSbFbSbFbSbFbSbSbFbSgFbSb*Fc*SbF9S8FbSeFaSbF9S9FbS9F9S9F9SaF9SaF9S9*F9*S9F9S9F9S9FeS9FfSbFfSfFgSfFfSfFfSgFgSgFgSgFgSfFgSeFeSgFeSe*Ff*SfFfSfFgSfFeSgFfSgFgSfFfSfFiSbFkSfFaS9F7*S8FkSgFaSaFiSeFeSh*FkSgFgSgFcSkFcSbFcSeFeSeFe*SfFcSeFcScFfSd*FcSeFcScFcScFcSeFcSeFeScFcS9F8*ScF9SeFeS8*FfSgFcS5F5S5F8S8F4SfF8S8F8S8F8*S6F6S9F8S8F8S8*F8S9F8S8FaSaFeS9F9S9F4S5F4S5F5S5F5S4F8S7*F8S8F4*S5F8S4F5S5F9S8F3SaFaS9FfSgF3ShFhSh*FgS5Fh*SgFgSkFjSkFkSkFgSkFkSkFkS5F5ShFgS5*F5S5Fh*SgF5S4F4S4F4S4F9S4F4S4F5S8F4SgF4S5F4SaF9S8FkS7F7S6F6S7*F6S8F8S7F3*SjFkSjFjSjFgSjFgS7F4S4FjS7F7SjFkS7F7S6*F6*S6FjSjFjS6F3S2FjSjFlS4FlSlF3S7F5S5F3SdF8S5F5S5F5S6F6S6F3S3Fh*S6F6S6FjS6FgShFgSjF6S6F6S6FhS6F6ShFhSiF3*S3F4ShF3SiFhSiFiSd*
```

Position after the deciding move (top row first, F = X, S = O):

```
.....XX.XXOO...XX..OX.
.....OO.XXXO...OX..XX.
....XXO.OOOO...XX..OX.
....OOX.OXOXX.XXX..XX.
....OOO.OOXOO.XOO..XO.
....XXX.OXOXX.OOO..OX.
....XOO.XOXOO.XXX..XO.
....XXO.OOXXX.OOX..OO.
...XXXX.XOXOX.OOX..XX.
...OOOO.XOOOO.OXOX.OO.
...XXXO.OOXXX.OOXO.OO.
...XOOX.XXOOO.XOOXXXO.
...XXOO.XOXXX.OOOOOOXO
...XOOOOOXOOX.XXOXOOXX
..OXXXOXXOXXO.OXXOOXXX
0123456789abcdefghijkl
```

### Loss: Savi5 beater 2.1 (S) vs Savi 7 - "The Prince (F)

Match id `9dd6922e-e63a-4363-8bd7-259cd5bc3c0e`. Decided at ply 210 (turn 211, last token `F1*`, by a blast, win_at 209). Skipped turns: [Skip[side=2, turn=1, kind=T]].

Moves:

```
F5F5S5F5S5F5S5F5S5F5S5F4S6F5S4F5S4F4S4F4S4*F4*S6F4S4F4S5F5S5F4S4F4S4F5S8F5S4F6S6F6S8F7*S2F8S7*F8S7F5S8F8S7F8S8F8S8F6S4F5S5F7S7F6S4F7S7F6S3*F4S4F8S8F8S8F8S8F0S4F6*S6F2S2F1S1F5S6F6S3*F6S3F4S5F5S4F6S6F3S2F6S7F6S6F6S3F7*S7F6S3F6S8F6S6F3S3F8S8F8S3FaSaF6SaF8S2*F2S9F3S0FaS0F0S0F3S9F9S3F3*S3F3S4F0S4F4S9*F9S8F0S8F8S0F3S0F2S2F3S3F2*S3FaSaFaSaF2S2F3S3F3S1F1S1*F0SaF2S1FaS0F2*S0F0S0F1SaF2S0F3S3F3S0F2S2F2S2F9S3F3S3F2S2F2S2F2S2F2S2F1S9F1SaF1S1F1*
```

Position after the deciding move (top row first, F = X, S = O):

```
...OXXX.X..
...XOOO.O..
...OOXX.O..
...XOOX.X..
..OOXXX.X..
O.XXOXX.O.O
O.OXOXO.X.O
O.XOXXX.O.X
X.OXOOX.O.O
O.XOXXO.X.O
OXXOOOX.OOX
XXOXXXXOXXO
XXXXXOXXOXX
OXXXOXXOOOO
XOXOXXXOXOX
0123456789a
```

### Loss: Savi5 beater 2.1 (S) vs Savi 7 - "The Prince (F)

Match id `96321eb5-5c07-404f-9292-55820aaa981d`. Decided at ply 153 (turn 153, last token `F7`, by a six, win_at 152). Skipped turns: none.

Moves:

```
F5S5F5S5F5S5F5S5F5S5F5S5F5S4F5S4F4S4F4S4F4*S4*F5S5F5S6F4S6F4S4F4S6F6S6F6S4F4S4F6S6F4S5F6S6*F7*S5F5S5F5S9F4S5F4S5F4S3F2S3F3S2F8S6F6S3*F6S3F4S4F6S6F3S1F3S1F3S3F6S6F6S4F6S8F8S1F8S8F3S3F8S3*F1S1F4S3F3S1F1S4F0*S4F2S9F9S9F1S3F1S1F1S2*F2S1F8S7F8S1F2S9F2S1F1S7F0S3F2S9F2S2F8S9F9S2F3SaF8S9*F8S2F2SaF7*SaFaS6F7S6F7S9F9S9F7S7F7
```

Position after the deciding move (top row first, F = X, S = O):

```
....OO.....
....OOO....
....XXO....
....OOX....
..X.OXX....
.XOXXOO..O.
.OOOXOX..X.
.OOOOXO..O.
.OXXXXX..O.
.XXOOOXX.O.
.OXXXXXOXO.
.XXOOOOXXOX
.XXXXXOXXXO
.XOOOOXXOOO
XOXOOXOOXOO
0123456789a
```

### Loss: Savi5 beater 2.1 (S) vs Savi 7 - "The Prince (F)

Match id `f9b0b6f4-cb65-4b50-b042-4f4e69ad64ae`. Decided at ply 182 (turn 183, last token `F3*`, by a blast, win_at 181). Skipped turns: [Skip[side=1, turn=50, kind=T]].

Moves:

```
F5S5F5S5F5S5F5S5F5S5F5S5F5S4F4S4F5S4F4S6F5S2F4S4F4S4F4S4F6S6F4S3F3S6F6S6F6*S4*F5S5F4S4F5S4F5S2F2S0F2S2S2F2S2F4S2F2S2*F2SaF6S6F6*S5F0S5F6S6F1S2F2S2F1S1F6S6F1S1F2S1F6S6*F1S6F6S5F3*S5F4S5F5S1F4SaF4S6F3S2F3S1F1S3*F2S1F0S4F4S4F6S1F7S1F3*SaF4S1FaS1F4S4F2S2F2S3*F4S4F7S7F4S2F6SaF9S9F9S0F9SaF9S9F7S7F9SaF2S2F7S3F7SaF7SaFaS8F0SaFaS9F9S9F9S7F7*S8*F9S9FaS8F0S8F9S9FaSaF8S7F7S8F8S6F3S3F3*
```

Position after the deciding move (top row first, F = X, S = O):

```
.O...X....O
.O...O....X
.O...OO...X
.O..XOX..OX
.O..OOX..XO
.X..XOO..OX
.O..OXX..XO
.O..XXO..XO
.XO.XXXX.OO
XOX.OXXO.XO
XOO.XXXXXOO
OXX.OOOXOXX
XOO.XXOXXOO
XXXXOOXOOXO
OXOOOXOXOXO
0123456789a
```

### Loss: Savi5 beater 2.1 (F) vs Savi 7 - "The Prince (S)

Match id `d921b131-24ab-4cf7-9ed3-e9d22a7e6d32`. Decided at ply 242 (turn 242, last token `S8`, by a six, win_at 241). Skipped turns: none.

Moves:

```
F5S5F5S5F4S3F5S5F7S5F5S5F5S5F5S7F7S9F3S3F3S3F3S3F3S9F8*S3F3S3F3S5F7S5F9S6F7S3F4S3F6S5F6S6F9S4F7S9F9*S9F6S7F6S6F6S6F6S6*F6S6F6S7F5S7F7S4F7S7F7S3F7S5F2*S5F3S9F6S6F7S9F9S9F9S9F9S4F6S8F3S7F9S9Fa*S9F8S8*F8S9F2S7F7S7FaS1FaSaF2S2F2S2F1SaFaS9F2*S9F9S9F9S4F4S7F3S3FaS8*F7S7F1S7F1S1F4S4F4*S5F5S1F3S8F5SaF4S4F4S4F4SaF0S1F3SaF9SaF8*S9FaS1F1S1F1S2F1S2F2S1F1S3F3S1F4S4F0S2F0*S1F8S1F1S0*F1S1F8S0F7S9F9S9F9S8F8S2F2S2F9S2F0S2F2S1F0S8F0S8F0S0*F0*S0F1S1F0S0F0S0F0S0F0S0F2SaFaS7F1S1F7S1F1SaFaS0F0S0F8*S8F7S9F7S7F9S9F8S8
```

Position after the deciding move (top row first, F = X, S = O):

```
OXXXOXXO.OX
XOXOXXOX.XO
OOOXXOXX.OX
OXOXOOXX.XO
XOOOXOOO.XX
OXXXOXXX.OO
XOOXXOOOOXO
OOOOXXXOXOO
XXXXXOOXOOO
OXOOOOXOOXX
XOOXOXXOXOX
OOXXOOOXOOO
XXOOOXXXXOO
XOXXXOXXXOX
OOXOXXOXOOX
0123456789a
```

### Loss: Savi5 beater 2.1 (F) vs Savi 7 - "The Prince (S)

Match id `c4ca211c-b9ad-4a2a-a284-348f6e03955a`. Decided at ply 142 (turn 142, last token `S3`, by a six, win_at 141). Skipped turns: none.

Moves:

```
F5S5F5S5F5S5F5S5F5S5F5S5F3S5F4S6F6S2F4S4F4S4F4S4F4S3F7S4F3S4F2S4F1S2*F4*S5F4S5F2S5F5S4F2S2F4S4F5S4F1S1FaS1F1S2F2S2F2S1F0*S2F1S1F6S6*F1S5F6S2F2S2F5S5FaS6F6S6F6S1F6*S6F6S5F1S1F5S1F5S6*F6S6F5S2F7S7F7S6F6S6F6S6F2S5F5S7F2S6F9S9F9*S9FaS7F7S7F7S7FaS7F7S0*F1S7F1S1F7S8F1S1F8*S3FaSaF9S3F3S3F9S3F3S3F3S3
```

Position after the deciding move (top row first, F = X, S = O):

```
..X.OXO....
.OX.OOO....
.XO.XXX....
.OO.OXO....
.XX.XXXX...
.XOOOOOO...
.OOXOOOX...
.OXOXXXO...
.XOXOOOO...
.OXOXXOX...
.XOOOOXO..O
.OOXXXOX..X
.XXOOOXO..X
.OXOXOXO.XX
.XOXXXOX.XX
0123456789a
```

### Loss: Savi beater 2.2 (S) vs lalalalabot (F)

Match id `62931620-5574-49fc-a226-ff94dc330d5f`. Decided at ply 176 (turn 177, last token `F7`, by a six, win_at 175). Skipped turns: [Skip[side=2, turn=13, kind=T]].

Moves:

```
F5S5F4S6F0S4F1S6F2S3F4S3F5F6S2F1S3F5S5F3S0*F4S2F6S2F4S4F8S3F3S2F6S6F5S5F6S2F2S0F4S0F5S6F6S8F8S8FaS4F4S5F8S3F3S6F5S6F8SaF8SaF8S8F4S3F3S4*F3S8F8S8F5S0F4S0F0S2F2S2F2S4F5S6F5S6F5S5F5S2*F2S6F6S8F2SaFaS8F8S2F4S4F4S4F0S3F3S3F0S0F0S0F0S0F0Sa*FaS9F9S9F9S3F9S9F9S9FaS9FaSaF9S9F9S9FaS9F9SaF0S0F2S2*F2S3F2S3FaSaFaS1F1*SaF0SaF0S2F0S2F2S1F1S7*F1S1F1S6F1S8F1S1F1S1F1S1F7S7F7
```

Position after the deciding move (top row first, F = X, S = O):

```
X.XOOX...XO
X.OOXOO.OOO
X.OOOXX.XOX
OOXXXXO.OXO
XXXOOXO.OOX
XOOXXXO.OXO
OXXOXOO.XOX
XOXXOXO.OOO
OXOOXOX.OXX
XXXXOXO.XOX
OXOOXOX.XXX
XOXXXXO.XXO
XXOOXXXXXOO
XXOOOOXOOXO
OOOOXXXXXOX
0123456789a
```

### Loss: Savi beater 2.2 (S) vs lalalalabot (F)

Match id `45f300fd-859a-4f9f-9fe3-5783c9c47092`. Decided at ply 165 (turn 165, last token `F7`, by a six, win_at 164). Skipped turns: none.

Moves:

```
F5S5F4S6F0S4F1S6F2S3F4S3F5S2F1S3F3S2F5S6F4S0*F6S2F5S7F7S2F5S5F6S2F2S8F4S4F4S3F3S5F6S5F6S6F5S8*F6S0F6S4F6S0F4S7F7S8F6S3F3S6F4S5F4S4F5S8*F4S5FaS6F5S6F2S2F2SaF2S5F2S2F3S3F2S2F4S4F0S0F0S0F0SaFaSaFaSaFaSaFaSaFaSaF0S0F0S0F8S8F8S8F8S8F8S8*F8S8F8S8F9S9F9S8F8S8F9S9F9S0F9S9F9S9F0S9F9S9F0S0F3S3F3SaF3S3F8S8*F8S8F9S9FaS1F1*S2F2S2F0S0F0S7F7S1F1S7F7
```

Position after the deciding move (top row first, F = X, S = O):

```
X.OOOOO.OOX
O.XXXXO.XXO
X.OXXOO.OOO
O.OOOXX.XXX
X.XXXOX.OOO
X.OOXXX.OOX
O.XXXOX.XXO
O.XXOOO.OOX
X.XOXOX.XXO
O.OXOXX.OXX
X.XOXXX.XOO
X.XXXXXXOXX
O.OOXXOOXXO
XXOOOOOXOOO
OOOOXXOOXXX
0123456789a
```

### Loss: Savi beater 2.2 (S) vs Savi8 - "The King" (F)

Match id `384b1906-8088-4dd2-a25e-34d1f43c73fc`. Decided at ply 201 (turn 201, last token `F2`, by a six, win_at 200). Skipped turns: none.

Moves:

```
F5S5F5S5F5S5F5S5F5S5F5S5F5S4F5S4F4S4F4S4F4S5F4S4F4S4F4S4F4S4F7SaF8SaF6S7*FaS6F6S6F6S6F6S6F6S6F6S6F6SaF6S6F6SaFaS9F9S9F9SaF9S9F7S9F9S9F9S7F9SaF9S8F7S7F8S9F9S7F8S8F8S3F8S8F8*S3*F4S4F7S8F8S7F7S7F1S7F7S4FaS1F7S7F7S7FaS3*F4S1F1S1F1S1F1S1F1S1F1S4FaS9F9SaFaSaF0S3*F4S0F3S2F2S4F9S9F2S2F2S0F0S2F2S2F0S0F0S0F0S0F0S1F1S2F2S2F1S2F3S3*F4S4FaS4F3S3F3S3F3S3F3S3F3S0F0S0F0S3F3S3F8*S8F7S9F7S7*F8S8F8S6F3S6F3S7F7S8F8S8F2S2F2*S3F2
```

Position after the deciding move (top row first, F = X, S = O):

```
X...OOOX..X
O...OXOOO.O
X..OXXXXXOX
OX.XOOXOOOO
XXXXXXOXXXX
OXXXOOXOOXX
XOOOXXOXXOX
OXOXOOXXOXO
XOXOOXOOOXO
OXOXXOXXXOX
XOOOOXOOXXO
XXXXXOXOOXO
OOOOOXOXXOX
OOXXXOXOXXO
XXOXXXOXOOO
0123456789a
```

### Loss: Savi beater 2.2 (F) vs Savi8 - "The King" (S)

Match id `86d1ed47-ba83-4847-b666-acea6c07318f`. Decided at ply 112 (turn 112, last token `S7`, by a six, win_at 111). Skipped turns: none.

Moves:

```
F5S5F5S5F5S5F5S5F5S5F5S5F3S5F4S6F6S2F4S4F4S4F3S4F4*S3F7S4F6S4F6S6*F4S6F5S5F6S6F6S6F4S4F5S4F5S4F6*S5F6S6F4S6F6S5F5S4F5S6F4S5F7S3F3S3F7S3F6S3F7S6F3S8F3S6F5*S7F3S3F2S2F4S0F7S7*F7S5F4S4F8S8F8S6F3S2F2S2F2*S2F8S6F6S3F8S8F8S8F3S7*F5S7F7S7
```

Position after the deciding move (top row first, F = X, S = O):

```
....O......
....XX.....
....XO.....
...XOX.....
...OOX.....
...XOO.....
...OXOX....
...XXXOO...
...XOOOX...
..OXOOOOO..
..XXOXXOX..
..OOXOOXX..
..OOOXXXO..
..XXXOXXX..
O.OXXXOXO..
0123456789a
```

### Loss: Savi beater 2.2 (F) vs Savi8 - "The King" (S)

Match id `8a6838ab-177d-4c31-8efa-7cb6397e11c1`. Decided at ply 144 (turn 146, last token `S3`, by a six, win_at 143). Skipped turns: [Skip[side=1, turn=16, kind=T], Skip[side=1, turn=90, kind=T]].

Moves:

```
F5S5F5S5F5S5F5S5F5S5F5S5F3S5F4S6S5F6S6F6S4*F5S6F6*S5F4S6F4S5F4S4F9S9F7S7F7S7FaS6F6S6F8S8F8*S8F6S6F8S6F8S6F6S5F6S7F7S7F5S5F9S7F7S8F7S7F7*S7F4S4F7S4F4S8F9S9*F6S6F6S8F8S9FaS9F7S7F4S8F4S8S8F3*S4F3S3*FaS7F8S5F6S1F3S1F3S4F4S3F3S8F8S3F3S1F1SaFaS3F3S3*F4*S5F7S7F2S7F8SaF2S8F5S5F9S4F4S9F4S0F0S9F9*S3F3S3*F4S3
```

Position after the deciding move (top row first, F = X, S = O):

```
.....OXO...
.....XXO...
.....OOX...
.....OXO...
.....OXO...
.....XXXO..
.....OOXX..
...OXOOOX..
...OOOXXO..
...XOOOXOOO
...OXXOOXXX
.X.XXXXOOOO
.O.OXOOXOOX
XOXXOXXOOXX
OOXXOOOXXXX
0123456789a
```

### Loss: Savi beater 2.2 (S) vs Savi8 - "The King" (F)

Match id `7b9d3eaf-bd3b-4f14-bc61-8f7841db25ba`. Decided at ply 208 (turn 209, last token `F3`, by a six, win_at 207). Skipped turns: [Skip[side=2, turn=27, kind=T]].

Moves:

```
F5S5F5S5F5S5F5S5F5S5F5S5F5S4F5S4F4S4F4S5F4S4F4S4F4S4F4F4S4*F5S4F6S9F5S9F5S1F6S6F1S9F9S0F4S9F9S1F6S6F6S6F6S6F1S4*F6S6F1S9F9S6F1S1F6S9F6S5F6SaF5S8F7S7F1S8*F1S1F1S1F1S1F1S9F9S8F8S8F8S8F9S8F7S8F7S7F7Sa*F8S8F8S8F8S8FaS0Fa*S7F7S9F8S8F0S3F4S4FaS0*F0S0F7S7F7S7F0S1F1S1F2S9F9S9F7*S6F6S8F6S8F2S7F2S2F2S7*F7S6F8S7F8S8F7S7F7S7F9S2F0S0F2S2F8S2F2S6F0S2F2S0*F0S2F0S2F0S2F6S0F0S1F1S0F1SaFaS0F0SaFaSaF9S9FaS9F9S9FaS9F9Sa*FaS9F9S9Fa*SaF9S9F9S3F3
```

Position after the deciding move (top row first, F = X, S = O):

```
XXO.OXXOXX.
OXO.XOOXOO.
OOO.OXOOXX.
XOX.XOXXXO.
OXO.OXXOOX.
XOX.XOOXOO.
XXO.OXOXOX.
XOO.XOXOXOO
OOX.OXOXOOX
XXO.XOXXOXO
XXX.XXOOOOX
OOO.OOXXXOO
XXXXXXOOOXX
OXXOOOXXXXO
OOXOOXXXOOX
0123456789a
```

### Loss: Savi beater 2.2 (F) vs Savi8 - "The King" (S)

Match id `791a6fc2-d142-47ec-9bdf-7d25b35d238b`. Decided at ply 203 (turn 204, last token `S7`, by a six, win_at 202). Skipped turns: [Skip[side=2, turn=173, kind=T]].

Moves:

```
F5S5F5S5F5S5F5S5F5S5F5S5F3S5F4S6F6S2F6S6F6*S6F6S6F6S6F4S4F6S4F4S6F4S2F1S4*F4S4F4S4F4S6F5S6F8S6F6S1F4S4F4S2F1*S8F8S9F8S3F3S3F7S3*F3S4F4S5F5S6F5S4F3S3F7*S6F8S6F4S5F5S6F3S3F8S3F3S7F8S7F8S8F9S5F5*S6F6S8F8S8F8S4F3S9F8S8FaSaF5S5F4S3F1S7F1S7F7S1F8S8F7S7F8S2F1S1F2S2F9*S3F3S8F9S1F0S1F1S1F0S3F0S0F1S0FaS0FaS1F9S0F0S9F1S8F0S8F0S0FaSaF1S9F1S9F9S9F0*SaF9S9*F8S0F8SaFaFaS8FaSaF0S1F1SaF1S0FaSaF0*S9F9S9F1S1F0S1F0S0F9S9F9S7F7S7F7S7
```

Position after the deciding move (top row first, F = X, S = O):

```
OO.OXOX.OXO
XO.XOXO.XOX
XX.OOXO.XXO
XX.OXOOOOOO
OX.XOXXXOXX
XO.XXXOOOOX
XX.OOOOXOOX
XO.OXXOOXXO
OO.XXOOOXOO
OO.OOOXXOOO
OX.XXOOXXOX
OOOXOXXOOXX
XXXXOXOOOXX
XXOOXOXOXXO
XXOXXXOOXOX
0123456789a
```

### Loss: Savi beater 2.2 (S) vs Savi8 - "The King" (F)

Match id `7e073388-a1c2-45a6-a273-f519fb97c225`. Decided at ply 128 (turn 129, last token `Fa*`, by a blast, win_at 127). Skipped turns: [Skip[side=2, turn=45, kind=T]].

Moves:

```
F5S5F5S5F5S5F5S5F5S5F5S5F5S4F5S6F4S4F5S6F6*S6F6S6F6S4F4S4F4S6F6S4F4S6F6S5F5S4F4S4*F4S9F4S6F6F6S5F4S8F4S8F5S9F3S9F4S9F3*S4F6S6*F5S4F5S4F5S7F3S3F3S3F3S3F9S9F6S8F8S8F8S6F8S9F9S6F8S3F3S8F3S3*F4S8F8S7F3S7F7S2F4S8F8S4F9S2F9S9Fa*S9F3S7*F7S7F6S6F6S3F3S3F3S2F2S7F7S7F7S7Fa*
```

Position after the deciding move (top row first, F = X, S = O):

```
....OXX....
...XXXO....
...OXXX....
...XOOO....
...OOXO....
...XOXXOX..
...XXOXXO..
...XXXXOXO.
...OOOOXOO.
...OXXXOOX.
...XOOOOXX.
..XOXXXXXX.
..OXOOXOOO.
..OOXXOOOO.
..OXOXOOOX.
0123456789a
```

### Loss: Savi beater 2.2 (F) vs Savi8 - "The King" (S)

Match id `b1cb6881-ef2d-423e-aed5-e088224fcb36`. Decided at ply 157 (turn 158, last token `S8`, by a six, win_at 156). Skipped turns: [Skip[side=1, turn=34, kind=T]].

Moves:

```
F5S5F5S5F5S5F5S5F5S5F5S5F3S5F4S6F6S2F4S4F4S4F4S3F6S5F3S3F6S6F2*S6F3S4S4F7S4F4S4F4S9F9S5F4S4F3S6F6S4F3S3F2*S3F3S2*F2S6F6S6F6S6F3S3F3S6F6S9F9S2F3S3F3*S3F9S4F9S3F9S9F8S3*F7S7F7S3F3S9F9S9F4S4F4*S9F4S4F5S3F4S5F7S7F3S3F5S2F1S2F1Sa*F4S9F9S2F1S1F8S9F9S9FaSaF1*S7F7S7F7S7F3S8*F3S2F1S2FaS7F7S8F8S9F7S7F8*S7F8SaF1S1F1SaF0SaF1S8F8S8*F9S8
```

Position after the deciding move (top row first, F = X, S = O):

```
...XXXX....
...XXOO....
...OOXO....
...XXOX....
...OXXO....
...XOOX....
...OOXO....
.X.OOOX..X.
.X.XOXOO.O.
.O.XXOOO.OO
.XOOOXOXOXO
.XOXXOXXOOO
.XOXOXXOXOX
.XOOXOXXOXO
XXXOXXOXXOX
0123456789a
```

## Timeouts and out-of-memory

Timeouts are the per-side letters of the log (an own timeout also gives an 'Invalid column selected: -1' line). OOM lines (`Memory limit exceeded: N MB used`, then an uncaught SecurityException) are not tagged with a side or bot, at most one per game. Warn = games with memory-warning lines in the log (both bots).

| Bot | Games | Own timeouts | Games with own timeout | Own full-column turns | Games with an OOM line | OOM MB min/median/p90/max | Games with warn lines |
|---|---|---|---|---|---|---|---|
| Savi5 beater 2.1 | 229 | 135 | 28 | 0 | 174 (76.0%) | 200/247/331/447 | 70 (max 199 MB) |
| Testing_v2 | 148 | 1108 | 125 | 0 | 95 (64.2%) | 200/238/358/416 | 33 (max 199 MB) |
| FunkyMelodyBot | 146 | 0 | 0 | 0 | 111 (76.0%) | 200/255/316/346 | 18 (max 199 MB) |
| Hurt me plenty | 135 | 399 | 84 | 6 | 72 (53.3%) | 200/266.5/369/392 | 27 (max 199 MB) |
| BeetaSaag | 128 | 12 | 2 | 7 | 72 (56.3%) | 200/239/364/398 | 32 (max 199 MB) |
| Strange Quark 1 | 128 | 0 | 0 | 0 | 77 (60.2%) | 200/238/304/396 | 31 (max 199 MB) |
| specialmovedestroyer | 124 | 0 | 0 | 0 | 84 (67.7%) | 200/243/351/406 | 39 (max 199 MB) |
| HereWeGoAgain | 97 | 86 | 8 | 0 | 74 (76.3%) | 200/258/334/358 | 17 (max 199 MB) |
| lalalalabot | 94 | 0 | 0 | 0 | 49 (52.1%) | 200/247/336/363 | 29 (max 199 MB) |
| Testimine-M7 | 91 | 0 | 0 | 0 | 39 (42.9%) | 200/230/300/331 | 22 (max 199 MB) |
| Back To Basics | 72 | 0 | 0 | 0 | 47 (65.3%) | 200/261/337/365 | 14 (max 199 MB) |
| Savi8 - "The King" | 71 | 10 | 9 | 0 | 5 (7.0%) | 285/328/353/353 | 2 (max 183 MB) |
| Savi beater 2.2 | 67 | 21 | 18 | 0 | 35 (52.2%) | 200/236/325/353 | 17 (max 199 MB) |
| Savi7 - "The Prince" | 59 | 10 | 8 | 0 | 50 (84.7%) | 200/277.5/405/447 | 16 (max 199 MB) |
| Savi5 | 58 | 0 | 0 | 0 | 30 (51.7%) | 200/206.5/264/302 | 24 (max 199 MB) |
| Savi 7 - "The Prince | 57 | 31 | 13 | 0 | 1 (1.8%) | 263/263/263/263 | 15 (max 171 MB) |
| Savi6 - "Beat this" | 57 | 1 | 1 | 0 | 42 (73.7%) | 200/213/240/277 | 25 (max 199 MB) |
| HereWeGo | 48 | 6 | 3 | 0 | 23 (47.9%) | 205/324/366/370 | 8 (max 198 MB) |
| AntiStrat_FINAL | 36 | 0 | 0 | 0 | 11 (30.6%) | 200/227/286/295 | 14 (max 198 MB) |
| \u006c\u0061\u006c\2 | 36 | 0 | 0 | 0 | 19 (52.8%) | 200/216/269/271 | 13 (max 199 MB) |
| I'm too young to die | 34 | 409 | 34 | 0 | 27 (79.4%) | 224/303/350/363 | 0 |
| lalalabot | 34 | 0 | 0 | 0 | 26 (76.5%) | 200/236/277/280 | 4 (max 199 MB) |
| good night | 33 | 0 | 0 | 0 | 25 (75.8%) | 200/273/318/331 | 2 (max 199 MB) |
| sleep tight | 33 | 0 | 0 | 0 | 33 (100.0%) | 223/265/294/302 | 0 |
| Savi6 - "Beat This" | 25 | 0 | 0 | 0 | 13 (52.0%) | 201/218/230/250 | 14 (max 197 MB) |

617 of 1020 games (60.5%) contain an OOM line.

Which side timed out in games with / without an OOM line, seen from each bot (own = only this bot's side, opp = only its opponent's, both, none):

| Bot | OOM games: own / opp / both / none | Other games: own / opp / both / none |
|---|---|---|
| Savi5 beater 2.1 | 14 / 24 / 10 / 126 | 2 / 2 / 2 / 49 |
| Testing_v2 | 63 / 1 / 18 / 13 | 31 / 1 / 13 / 8 |
| FunkyMelodyBot | 0 / 24 / 0 / 87 | 0 / 9 / 0 / 26 |
| Hurt me plenty | 31 / 2 / 9 / 30 | 39 / 2 / 5 / 17 |
| BeetaSaag | 0 / 10 / 0 / 62 | 0 / 11 / 2 / 43 |
| Strange Quark 1 | 0 / 5 / 0 / 72 | 0 / 2 / 0 / 49 |
| specialmovedestroyer | 0 / 16 / 0 / 68 | 0 / 6 / 0 / 34 |
| HereWeGoAgain | 0 / 9 / 6 / 59 | 1 / 0 / 1 / 21 |
| lalalalabot | 0 / 10 / 0 / 39 | 0 / 10 / 0 / 35 |
| Testimine-M7 | 0 / 2 / 0 / 37 | 0 / 4 / 0 / 48 |
| Back To Basics | 0 / 5 / 0 / 42 | 0 / 1 / 0 / 24 |
| Savi8 - "The King" | 1 / 2 / 0 / 2 | 6 / 20 / 2 / 38 |
| Savi beater 2.2 | 11 / 1 / 0 / 23 | 5 / 8 / 2 / 17 |
| Savi7 - "The Prince" | 6 / 11 / 1 / 32 | 1 / 0 / 0 / 8 |
| Savi5 | 0 / 3 / 0 / 27 | 0 / 7 / 0 / 21 |
| Savi 7 - "The Prince | 0 / 0 / 1 / 0 | 9 / 10 / 3 / 34 |
| Savi6 - "Beat this" | 1 / 7 / 0 / 34 | 0 / 3 / 0 / 12 |
| HereWeGo | 0 / 3 / 3 / 17 | 0 / 0 / 0 / 25 |
| AntiStrat_FINAL | 0 / 2 / 0 / 9 | 0 / 3 / 0 / 22 |
| \u006c\u0061\u006c\2 | 0 / 2 / 0 / 17 | 0 / 1 / 0 / 16 |
| I'm too young to die | 25 / 0 / 2 / 0 | 7 / 0 / 0 / 0 |
| lalalabot | 0 / 4 / 0 / 22 | 0 / 0 / 0 / 8 |
| good night | 0 / 4 / 0 / 21 | 0 / 0 / 0 / 8 |
| sleep tight | 0 / 3 / 0 / 30 | 0 / 0 / 0 / 0 |
| Savi6 - "Beat This" | 0 / 2 / 0 / 11 | 0 / 1 / 0 / 11 |

First timeout or full-column line at or after the OOM line, by whose side it hit (own / opp / no later event):

| Bot | own / opp / none |
|---|---|
| Savi5 beater 2.1 | 16 / 31 / 127 |
| Testing_v2 | 69 / 7 / 19 |
| FunkyMelodyBot | 0 / 24 / 87 |
| Hurt me plenty | 33 / 7 / 32 |
| BeetaSaag | 1 / 9 / 62 |
| Strange Quark 1 | 0 / 5 / 72 |
| specialmovedestroyer | 0 / 15 / 69 |
| HereWeGoAgain | 3 / 12 / 59 |
| lalalalabot | 0 / 10 / 39 |
| Testimine-M7 | 0 / 2 / 37 |
| Back To Basics | 0 / 5 / 42 |
| Savi8 - "The King" | 1 / 2 / 2 |
| Savi beater 2.2 | 11 / 1 / 23 |
| Savi7 - "The Prince" | 6 / 12 / 32 |
| Savi5 | 0 / 0 / 30 |
| Savi 7 - "The Prince | 0 / 1 / 0 |
| Savi6 - "Beat this" | 1 / 4 / 37 |
| HereWeGo | 2 / 4 / 17 |
| AntiStrat_FINAL | 0 / 2 / 9 |
| \u006c\u0061\u006c\2 | 0 / 1 / 18 |
| I'm too young to die | 26 / 1 / 0 |
| lalalabot | 0 / 4 / 22 |
| good night | 0 / 5 / 20 |
| sleep tight | 0 / 3 / 30 |
| Savi6 - "Beat This" | 0 / 2 / 11 |

Games with an OOM line followed by a later timeout line: 169 of 617; delay from the OOM line to it, ms min/median/p90/max = 2/201/1133/10505.

Games where a timeout line precedes the OOM line: 20.

OOM games / games for every pair of bots (both colours); a bot that exhausts memory shows up as a column and row of high shares:

| # | Bot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 19 | 20 | 21 | 22 | 23 | 24 | 25 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Savi5 beater 2.1 | . | 17/17 | 10/14 | 16/20 | 18/21 | 19/20 | 18/21 | 14/14 | 7/13 | 0/7 | 9/10 |  |  | 7/7 | 4/5 | 0/6 | 3/3 | 3/7 | 0/7 | 6/6 | 4/4 | 7/7 | 1/7 | 6/6 | 5/7 |
| 2 | Testing_v2 | 17/17 | . | 11/13 | 4/10 | 3/6 | 5/5 | 6/9 | 7/8 | 3/6 | 0/7 | 2/2 | 0/7 | 0/7 | 5/5 | 3/5 | 0/7 | 5/5 | 5/5 | 2/5 | 3/4 | 2/2 | 3/3 | 5/5 | 3/3 | 1/2 |
| 3 | FunkyMelodyBot | 10/14 | 11/13 | . | 8/14 | 11/14 | 13/14 | 10/14 | 12/14 | 10/14 |  | 12/14 | 0/7 | 7/7 |  |  |  |  |  |  |  | 7/7 |  |  |  |  |
| 4 | Hurt me plenty | 16/20 | 4/10 | 8/14 | . | 0/4 | 3/4 | 5/5 | 3/6 | 1/4 | 2/7 | 2/2 | 0/7 | 0/6 | 7/7 | 2/7 | 1/7 | 4/7 | 6/6 | 1/2 | 1/2 | 2/2 | 2/2 |  | 2/2 | 0/2 |
| 5 | BeetaSaag | 18/21 | 3/6 | 11/14 | 0/4 | . | 0/2 | 5/5 | 3/4 | 1/3 | 0/7 | 2/2 | 0/7 | 0/7 | 6/6 | 3/7 | 0/7 | 5/7 | 6/6 | 0/2 | 1/2 | 2/2 | 1/2 | 1/1 | 2/2 | 2/2 |
| 6 | Strange Quark 1 | 19/20 | 5/5 | 13/14 | 3/4 | 0/2 | . | 4/4 | 5/6 | 2/4 | 3/7 | 2/2 | 0/7 | 0/7 | 5/7 | 3/6 | 0/7 | 7/7 | 0/5 | 0/2 | 0/2 | 2/2 | 2/2 | 0/2 | 2/2 | 0/2 |
| 7 | specialmovedestroyer | 18/21 | 6/9 | 10/14 | 5/5 | 5/5 | 4/4 | . | 5/6 | 2/4 | 6/7 | 4/4 | 0/7 | 4/7 | 6/7 |  | 0/7 | 5/7 | 0/4 |  |  | 2/2 | 1/2 |  |  | 1/2 |
| 8 | HereWeGoAgain | 14/14 | 7/8 | 12/14 | 3/6 | 3/4 | 5/6 | 5/6 | . | 6/7 |  | 5/5 | 0/6 | 7/7 | 5/7 |  | 0/5 |  |  |  |  | 2/2 |  |  |  |  |
| 9 | lalalalabot | 7/13 | 3/6 | 10/14 | 1/4 | 1/3 | 2/4 | 2/4 | 6/7 | . |  | 0/3 | 0/6 | 7/7 | 7/7 |  | 0/7 |  | 0/5 |  |  | 2/2 |  |  |  | 1/2 |
| 10 | Testimine-M7 | 0/7 | 0/7 |  | 2/7 | 0/7 | 3/7 | 6/7 |  |  | . |  |  |  |  | 2/7 |  | 3/7 |  | 3/7 | 1/7 |  | 5/7 | 7/7 | 7/7 |  |
| 11 | Back To Basics | 9/10 | 2/2 | 12/14 | 2/2 | 2/2 | 2/2 | 4/4 | 5/5 | 0/3 |  | . | 0/3 | 5/5 | 2/6 |  | 0/4 |  | 0/6 |  |  | 2/2 |  |  |  | 0/2 |
| 12 | Savi8 - "The King" |  | 0/7 | 0/7 | 0/7 | 0/7 | 0/7 | 0/7 | 0/6 | 0/6 |  | 0/3 | . | 5/7 |  |  |  |  |  |  |  | 0/7 |  |  |  |  |
| 13 | Savi beater 2.2 |  | 0/7 | 7/7 | 0/6 | 0/7 | 0/7 | 4/7 | 7/7 | 7/7 |  | 5/5 | 5/7 | . |  |  |  |  |  |  |  |  |  |  |  |  |
| 14 | Savi7 - "The Prince" | 7/7 | 5/5 |  | 7/7 | 6/6 | 5/7 | 6/7 | 5/7 | 7/7 |  | 2/6 |  |  | . |  |  |  |  |  |  |  |  |  |  |  |
| 15 | Savi5 | 4/5 | 3/5 |  | 2/7 | 3/7 | 3/6 |  |  |  | 2/7 |  |  |  |  | . |  |  |  | 3/7 | 3/7 |  |  |  | 7/7 |  |
| 16 | Savi 7 - "The Prince | 0/6 | 0/7 |  | 1/7 | 0/7 | 0/7 | 0/7 | 0/5 | 0/7 |  | 0/4 |  |  |  |  | . |  |  |  |  |  |  |  |  |  |
| 17 | Savi6 - "Beat this" | 3/3 | 5/5 |  | 4/7 | 5/7 | 7/7 | 5/7 |  |  | 3/7 |  |  |  |  |  |  | . |  |  |  |  | 3/7 | 7/7 |  |  |
| 18 | HereWeGo | 3/7 | 5/5 |  | 6/6 | 6/6 | 0/5 | 0/4 |  | 0/5 |  | 0/6 |  |  |  |  |  |  | . |  |  |  |  |  |  | 3/4 |
| 19 | AntiStrat_FINAL | 0/7 | 2/5 |  | 1/2 | 0/2 | 0/2 |  |  |  | 3/7 |  |  |  |  | 3/7 |  |  |  | . | 0/2 |  |  |  | 2/2 |  |
| 20 | \u006c\u0061\u006c\2 | 6/6 | 3/4 |  | 1/2 | 1/2 | 0/2 |  |  |  | 1/7 |  |  |  |  | 3/7 |  |  |  | 0/2 | . |  |  | 2/2 | 2/2 |  |
| 21 | I'm too young to die | 4/4 | 2/2 | 7/7 | 2/2 | 2/2 | 2/2 | 2/2 | 2/2 | 2/2 |  | 2/2 | 0/7 |  |  |  |  |  |  |  |  | . |  |  |  |  |
| 22 | lalalabot | 7/7 | 3/3 |  | 2/2 | 1/2 | 2/2 | 1/2 |  |  | 5/7 |  |  |  |  |  |  | 3/7 |  |  |  |  | . | 2/2 |  |  |
| 23 | good night | 1/7 | 5/5 |  |  | 1/1 | 0/2 |  |  |  | 7/7 |  |  |  |  |  |  | 7/7 |  |  | 2/2 |  | 2/2 | . |  |  |
| 24 | sleep tight | 6/6 | 3/3 |  | 2/2 | 2/2 | 2/2 |  |  |  | 7/7 |  |  |  |  | 7/7 |  |  |  | 2/2 | 2/2 |  |  |  | . |  |
| 25 | Savi6 - "Beat This" | 5/7 | 1/2 |  | 0/2 | 2/2 | 0/2 | 1/2 |  | 1/2 |  | 0/2 |  |  |  |  |  |  | 3/4 |  |  |  |  |  |  | . |

Median OOM size in MB for every pair of bots (the figure is the memory in use when the limit tripped; abrupt jumps give large values, slow growth gives values just above 200):

| # | Bot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 19 | 20 | 21 | 22 | 23 | 24 | 25 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Savi5 beater 2.1 |  | 262 | 247 | 273 | 207.5 | 255 | 247.5 | 248.5 | 258 |  | 270 |  |  | 426 | 200 |  | 205 | 302 |  | 248 | 314.5 | 230 | 306 | 274 | 208 |
| 2 | Testing_v2 | 262 |  | 206 | 271 | 228 | 209 | 255 | 248 | 205 |  | 330 |  |  | 388 | 202 |  | 201 | 341 | 222 | 200 | 297 | 227 | 284 | 262 | 201 |
| 3 | FunkyMelodyBot | 247 | 206 |  | 263.5 | 244 | 241 | 267 | 253.5 | 274 |  | 246 |  | 266 |  |  |  |  |  |  |  | 302 |  |  |  |  |
| 4 | Hurt me plenty | 273 | 271 | 263.5 |  |  | 238 | 290 | 208 | 203 | 207.5 | 303 |  |  | 377 | 206 | 263 | 208.5 | 332 | 231 | 201 | 317 | 244.5 |  | 268 |  |
| 5 | BeetaSaag | 207.5 | 228 | 244 |  |  |  | 290 | 257 | 218 |  | 337.5 |  |  | 385.5 | 207 |  | 206 | 332.5 |  | 211 | 340.5 | 249 | 258 | 276 | 224.5 |
| 6 | Strange Quark 1 | 255 | 209 | 241 | 238 |  |  | 237 | 259 | 245.5 | 224 | 305.5 |  |  | 230 | 203 |  | 217 |  |  |  | 261 | 270.5 |  | 253.5 |  |
| 7 | specialmovedestroyer | 247.5 | 255 | 267 | 290 | 290 | 237 |  | 334 | 220 | 225 | 267 |  | 201 | 208 |  |  | 214 |  |  |  | 350 | 203 |  |  | 207 |
| 8 | HereWeGoAgain | 248.5 | 248 | 253.5 | 208 | 257 | 259 | 334 |  | 263 |  | 266 |  | 257 | 276 |  |  |  |  |  |  | 302 |  |  |  |  |
| 9 | lalalalabot | 258 | 205 | 274 | 203 | 218 | 245.5 | 220 | 263 |  |  |  |  | 229 | 220 |  |  |  |  |  |  | 356.5 |  |  |  | 218 |
| 10 | Testimine-M7 |  |  |  | 207.5 |  | 224 | 225 |  |  |  |  |  |  |  | 203 |  | 200 |  | 215 | 210 |  | 229 | 292 | 265 |  |
| 11 | Back To Basics | 270 | 330 | 246 | 303 | 337.5 | 305.5 | 267 | 266 |  |  |  |  | 207 | 212 |  |  |  |  |  |  | 267.5 |  |  |  |  |
| 12 | Savi8 - "The King" |  |  |  |  |  |  |  |  |  |  |  |  | 328 |  |  |  |  |  |  |  |  |  |  |  |  |
| 13 | Savi beater 2.2 |  |  | 266 |  |  |  | 201 | 257 | 229 |  | 207 | 328 |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 14 | Savi7 - "The Prince" | 426 | 388 |  | 377 | 385.5 | 230 | 208 | 276 | 220 |  | 212 |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 15 | Savi5 | 200 | 202 |  | 206 | 207 | 203 |  |  |  | 203 |  |  |  |  |  |  |  |  | 214 | 209 |  |  |  | 264 |  |
| 16 | Savi 7 - "The Prince |  |  |  | 263 |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 17 | Savi6 - "Beat this" | 205 | 201 |  | 208.5 | 206 | 217 | 214 |  |  | 200 |  |  |  |  |  |  |  |  |  |  |  | 240 | 213 |  |  |
| 18 | HereWeGo | 302 | 341 |  | 332 | 332.5 |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | 226 |
| 19 | AntiStrat_FINAL |  | 222 |  | 231 |  |  |  |  |  | 215 |  |  |  |  | 214 |  |  |  |  |  |  |  |  | 290.5 |  |
| 20 | \u006c\u0061\u006c\2 | 248 | 200 |  | 201 | 211 |  |  |  |  | 210 |  |  |  |  | 209 |  |  |  |  |  |  |  | 263.5 | 264.5 |  |
| 21 | I'm too young to die | 314.5 | 297 | 302 | 317 | 340.5 | 261 | 350 | 302 | 356.5 |  | 267.5 |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 22 | lalalabot | 230 | 227 |  | 244.5 | 249 | 270.5 | 203 |  |  | 229 |  |  |  |  |  |  | 240 |  |  |  |  |  | 276 |  |  |
| 23 | good night | 306 | 284 |  |  | 258 |  |  |  |  | 292 |  |  |  |  |  |  | 213 |  |  | 263.5 |  | 276 |  |  |  |
| 24 | sleep tight | 274 | 262 |  | 268 | 276 | 253.5 |  |  |  | 265 |  |  |  |  | 264 |  |  |  | 290.5 | 264.5 |  |  |  |  |  |
| 25 | Savi6 - "Beat This" | 208 | 201 |  |  | 224.5 |  | 207 |  | 218 |  |  |  |  |  |  |  |  | 226 |  |  |  |  |  |  |  |

## Wall-clock evidence

The exported log lines are only ERROR lines (OOM, timeout, invalid column, full column); they carry millisecond timestamps (5705 lines) but there is no per-move or start/end line, so a game's duration can only be bracketed from its first to its last timestamped event.

164 games have two or more paired timeout lines (log events match the skipped turns found by the replay). Between consecutive such events (1086 intervals, 3290 turns): time per turn in ms, min/median/p90/max = 0/50/100/284, pooled mean 40 ms per turn (131.9 s over 3290 turns). First-to-last event span per game, ms min/median/p90/max = 1/496/1832/6568, covering turns 2/14/42/90. Consecutive timeouts can be logged only a few ms apart although each is reported as exceeding 100 ms, so the log timestamps are not a reliable clock for single moves; they give an order of magnitude only (games of this many turns are logged within about a second, far below 30 s).

