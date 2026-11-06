# Server games by board size (export 06, replayed with qc_replay.py)

Source: `analysis/server-exports/export-06-8a0e07e7.zip` (`data/matches.jsonl`, 1054 ids, 1020 unique after removing identical replays). Draws are not exported, so all W/L figures exclude draws. Plies = move tokens (blasts count, skipped turns do not). Team Savi = Savi5, Savi6 (two ids), Savi7 (two ids), Savi8; ours = Savi5 beater 2.1 / Savi beater 2.2. Short names: Savi6b = Savi6 "Beat This", Savi7b = "Savi 7 - The Prince" (second id).

## 1. Replay check by board size

Rules mirrored from `Board.java`: gravity; a normal drop wins with a six of the mover's colour through the new disc; a blast (`*`) is played at the landing cell (column height h), the placed disc vanishes, rows h-1..h+1 of columns c-1..c+1 are cleared (in column c only the top disc at h-1) and the discs above fall; any six of EITHER colour touching those three columns then wins for the mover. A blast needs a per-side cooldown counter >= 10 (every own turn counts, skipped ones included).

Blast centre: the 3x3 block is centred on the cell where the blasting disc would have landed (just above the column's top disc), not on the top disc. Column c therefore loses exactly its top disc, the neighbouring columns lose their discs in rows h-1, h, h+1.

| Size (rows x cols) | Games in file (ids) | Unique games | Replay consistent | On the recorded size | Size read from the page | Size inferred by exporter |
|---|---|---|---|---|---|---|
| 8x12 | 341 | 341 | 341 | 341 | 0 | 341 |
| 15x22 | 325 | 323 | 323 | 323 | 166 | 157 |
| 15x11 | 388 | 356 | 356 | 356 | 356 | 0 |
| all | 1054 | 1020 | 1020 | 1020 | 522 | 498 |

1020 of 1020 unique games replay consistently on their recorded board (recorded winner = replay winner, line exactly at `win_at`, no earlier line, no disc into a full column, no blast before the cooldown); 34 further ids are identical replays (same two bots, same moves) and were dropped. The old hex/8x12-only tool reported 410 of these 1020 unique games as inconsistent.

## 2. Results by board size

### 8x12

341 unique games. First player (F) won 186 (54.5%); in the 272 games with no timeout or full-column event F won 152 (55.9%). Decided by a blast: 15 (4.4%), by a normal six: 326. Plies min/median/p90/max = 11/31/90/136, mean 40.0.

Game length (plies):

| Plies | Games | Share |  |
|---|---|---|---|
| 1-40 | 208 | 61.0% | ######################## |
| 41-80 | 92 | 27.0% | ########### |
| 81-120 | 32 | 9.4% | #### |
| 121-160 | 9 | 2.6% | # |
| 161-200 | 0 | 0.0% |  |
| 201-240 | 0 | 0.0% |  |
| 241-280 | 0 | 0.0% |  |
| 281-320 | 0 | 0.0% |  |
| 321+ | 0 | 0.0% |  |

First-player win rate by pairing of team roles (savi = team Savi, ours = No beaches?, other):

| Pairing | Games | F won | F win rate |
|---|---|---|---|
| other (F) vs other (S) | 155 | 90 | 58.1% |
| other (F) vs ours (S) | 36 | 0 | 0.0% |
| other (F) vs savi (S) | 51 | 2 | 3.9% |
| ours (F) vs other (S) | 35 | 35 | 100.0% |
| ours (F) vs savi (S) | 4 | 2 | 50.0% |
| savi (F) vs other (S) | 56 | 56 | 100.0% |
| savi (F) vs ours (S) | 4 | 1 | 25.0% |

Per bot (W/L; its wins by six/blast; plies of its wins and its losses as min/median/p90/max; own timeouts):

| Bot | Games | W/L | Win % | As F W/L | As S W/L | Wins six/blast | Plies in wins | Plies in losses | Own timeouts |
|---|---|---|---|---|---|---|---|---|---|
| TestimineM7 | 91 | 2/89 | 2.2% | 2/38 | 0/51 | 2/0 | 35/43/51/51 | 11/19/46/71 | 0 |
| ours 2.1 | 79 | 76/3 | 96.2% | 37/2 | 39/1 | 65/11 | 13/42/99/132 | 115/122/136/136 | 3 |
| Savi5 | 58 | 53/5 | 91.4% | 27/3 | 26/2 | 53/0 | 13/36/98/136 | 104/123/132/132 | 0 |
| Savi6 | 57 | 55/2 | 96.5% | 30/0 | 25/2 | 54/1 | 15/42/93/128 | 109/111.5/114/114 | 1 |
| Testing_v2 | 57 | 45/12 | 78.9% | 24/5 | 21/7 | 42/3 | 12/44/112/123 | 68/114/129/130 | 50 |
| BeetaSaag | 46 | 17/29 | 37.0% | 9/12 | 8/17 | 17/0 | 11/14/96/96 | 11/45/57/63 | 0 |
| StrangeQ | 46 | 8/38 | 17.4% | 5/18 | 3/20 | 8/0 | 11/24/35/35 | 11/18/32/36 | 0 |
| HurtMe | 44 | 13/31 | 29.5% | 6/15 | 7/16 | 13/0 | 13/33/51/64 | 23/38/47/93 | 88 |
| AntiStrat | 36 | 10/26 | 27.8% | 6/11 | 4/15 | 10/0 | 11/13/30/53 | 11/28/53/67 | 0 |
| \u006c\u0061.. | 36 | 17/19 | 47.2% | 10/8 | 7/11 | 17/0 | 11/12/30/39 | 17/80/112/112 | 0 |
| lalala | 34 | 14/20 | 41.2% | 9/10 | 5/10 | 14/0 | 11/13/24/26 | 18/33.5/94/112 | 0 |
| goodN | 33 | 11/22 | 33.3% | 7/11 | 4/11 | 11/0 | 11/12/19/19 | 11/52/84/96 | 0 |
| sleepT | 33 | 9/24 | 27.3% | 8/10 | 1/14 | 9/0 | 11/11/71/71 | 11/23.5/35/51 | 0 |
| SpecialMD | 32 | 11/21 | 34.4% | 6/12 | 5/9 | 11/0 | 11/14/21/93 | 11/53/78/96 | 0 |

Head to head, row bot wins-losses against column bot (both colours; bots with >= 12 games here):

| # | Bot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | TestimineM7 | . | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 0-7 | 2-5 | 0-7 |
| 2 | ours 2.1 | 7-0 | . | 4-1 | 1-2 | 4-0 | 7-0 | 7-0 | 6-0 | 7-0 | 6-0 | 7-0 | 7-0 | 6-0 | 7-0 |
| 3 | Savi5 | 7-0 | 1-4 | . |  | 4-1 | 7-0 | 6-0 | 7-0 | 7-0 | 7-0 |  |  | 7-0 |  |
| 4 | Savi6 | 7-0 | 2-1 |  | . | 4-1 | 7-0 | 7-0 | 7-0 |  |  | 7-0 | 7-0 |  | 7-0 |
| 5 | Testing_v2 | 7-0 | 0-4 | 1-4 | 1-4 | . | 4-0 | 3-0 | 4-0 | 5-0 | 4-0 | 3-0 | 5-0 | 3-0 | 5-0 |
| 6 | BeetaSaag | 7-0 | 0-7 | 0-7 | 0-7 | 0-4 | . | 2-0 | 1-1 | 2-0 | 1-1 | 1-1 | 1-0 | 1-1 | 1-0 |
| 7 | StrangeQ | 7-0 | 0-7 | 0-6 | 0-7 | 0-3 | 0-2 | . | 0-2 | 0-2 | 0-2 | 0-2 | 0-2 | 1-1 | 0-2 |
| 8 | HurtMe | 7-0 | 0-6 | 0-7 | 0-7 | 0-4 | 1-1 | 2-0 | . | 2-0 | 0-2 | 0-2 |  | 1-1 | 0-1 |
| 9 | AntiStrat | 7-0 | 0-7 | 0-7 |  | 0-5 | 0-2 | 2-0 | 0-2 | . | 0-2 |  |  | 1-1 |  |
| 10 | \u006c\u0061.. | 7-0 | 0-6 | 0-7 |  | 0-4 | 1-1 | 2-0 | 2-0 | 2-0 | . |  | 1-1 | 2-0 |  |
| 11 | lalala | 7-0 | 0-7 |  | 0-7 | 0-3 | 1-1 | 2-0 | 2-0 |  |  | . | 1-1 |  | 1-1 |
| 12 | goodN | 7-0 | 0-7 |  | 0-7 | 0-5 | 0-1 | 2-0 |  |  | 1-1 | 1-1 | . |  |  |
| 13 | sleepT | 5-2 | 0-6 | 0-7 |  | 0-3 | 1-1 | 1-1 | 1-1 | 1-1 | 0-2 |  |  | . |  |
| 14 | SpecialMD | 7-0 | 0-7 |  | 0-7 | 0-5 | 0-1 | 2-0 | 1-0 |  |  | 1-1 |  |  | . |

### 15x22

323 unique games. First player (F) won 169 (52.3%); in the 240 games with no timeout or full-column event F won 125 (52.1%). Decided by a blast: 17 (5.3%), by a normal six: 306. Plies min/median/p90/max = 6/17/68/341, mean 31.8.

Game length (plies):

| Plies | Games | Share |  |
|---|---|---|---|
| 1-40 | 256 | 79.3% | ################################ |
| 41-80 | 42 | 13.0% | ##### |
| 81-120 | 12 | 3.7% | # |
| 121-160 | 6 | 1.9% | # |
| 161-200 | 5 | 1.5% | # |
| 201-240 | 0 | 0.0% |  |
| 241-280 | 0 | 0.0% |  |
| 281-320 | 0 | 0.0% |  |
| 321+ | 2 | 0.6% |  |

First-player win rate by pairing of team roles (savi = team Savi, ours = No beaches?, other):

| Pairing | Games | F won | F win rate |
|---|---|---|---|
| other (F) vs other (S) | 173 | 92 | 53.2% |
| other (F) vs ours (S) | 37 | 0 | 0.0% |
| other (F) vs savi (S) | 33 | 9 | 27.3% |
| ours (F) vs other (S) | 29 | 29 | 100.0% |
| ours (F) vs savi (S) | 6 | 5 | 83.3% |
| savi (F) vs other (S) | 37 | 33 | 89.2% |
| savi (F) vs ours (S) | 8 | 1 | 12.5% |

Per bot (W/L; its wins by six/blast; plies of its wins and its losses as min/median/p90/max; own timeouts):

| Bot | Games | W/L | Win % | As F W/L | As S W/L | Wins six/blast | Plies in wins | Plies in losses | Own timeouts |
|---|---|---|---|---|---|---|---|---|---|
| ours 2.1 | 80 | 78/2 | 97.5% | 34/1 | 44/1 | 71/7 | 7/30/111/340 | 169/255/341/341 | 117 |
| Funky | 62 | 9/53 | 14.5% | 7/29 | 2/24 | 9/0 | 18/27/109/109 | 11/13/43/107 | 0 |
| HereWeGoAg | 59 | 19/40 | 32.2% | 11/24 | 8/16 | 19/0 | 6/11/16/17 | 11/17.5/43/98 | 86 |
| Savi7 | 59 | 52/7 | 88.1% | 29/3 | 23/4 | 43/9 | 6/39.5/113/341 | 52/161/340/340 | 10 |
| HurtMe | 49 | 27/22 | 55.1% | 13/9 | 14/13 | 27/0 | 13/16/39/54 | 9/29.5/42/76 | 256 |
| HereWeGo | 48 | 13/35 | 27.1% | 10/18 | 3/17 | 13/0 | 6/11/13/13 | 11/16/37/56 | 6 |
| Testing_v2 | 46 | 0/46 | 0.0% | 0/20 | 0/26 | 0/0 | - | 6/7/20/42 | 555 |
| lalalala | 46 | 32/14 | 69.6% | 16/6 | 16/8 | 32/0 | 6/14/31/52 | 13/65.5/148/156 | 0 |
| StrangeQ | 44 | 18/26 | 40.9% | 10/10 | 8/16 | 18/0 | 6/11/12/14 | 11/16/33/37 | 0 |
| BackToBas | 43 | 13/30 | 30.2% | 8/13 | 5/17 | 13/0 | 6/11/74/107 | 11/13/24/109 | 0 |
| SpecialMD | 43 | 29/14 | 67.4% | 14/6 | 15/8 | 28/1 | 6/14/109/122 | 11/79/144/174 | 0 |
| BeetaSaag | 42 | 26/16 | 61.9% | 12/7 | 14/9 | 26/0 | 6/13/38/45 | 11/48/109/116 | 12 |
| Savi6b | 25 | 7/18 | 28.0% | 5/8 | 2/10 | 7/0 | 6/11/12/12 | 11/19.5/51/64 | 0 |

Head to head, row bot wins-losses against column bot (both colours; bots with >= 12 games here):

| # | Bot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | ours 2.1 | . | 7-0 | 7-0 | 5-2 | 7-0 | 7-0 | 6-0 | 6-0 | 7-0 | 5-0 | 7-0 | 7-0 | 7-0 |
| 2 | Funky | 0-7 | . | 0-7 |  | 2-5 |  | 6-0 | 0-7 | 0-7 | 1-6 | 0-7 | 0-7 |  |
| 3 | HereWeGoAg | 0-7 | 7-0 | . | 0-7 | 0-6 |  | 7-0 | 0-6 | 2-4 | 3-2 | 0-4 | 0-4 |  |
| 4 | Savi7 | 2-5 |  | 7-0 | . | 7-0 |  | 5-0 | 6-1 | 7-0 | 6-0 | 6-1 | 6-0 |  |
| 5 | HurtMe | 0-7 | 5-2 | 6-0 | 0-7 | . | 6-0 | 4-0 | 0-2 | 2-0 | 2-0 | 0-2 | 0-2 | 2-0 |
| 6 | HereWeGo | 0-7 |  |  |  | 0-6 | . | 5-0 | 0-5 | 2-3 | 5-1 | 0-4 | 0-6 | 1-3 |
| 7 | Testing_v2 | 0-6 | 0-6 | 0-7 | 0-5 | 0-4 | 0-5 | . | 0-3 | 0-2 | 0-2 | 0-2 | 0-2 | 0-2 |
| 8 | lalalala | 0-6 | 7-0 | 6-0 | 1-6 | 2-0 | 5-0 | 3-0 | . | 2-0 | 2-0 | 1-1 | 1-1 | 2-0 |
| 9 | StrangeQ | 0-7 | 7-0 | 4-2 | 0-7 | 0-2 | 3-2 | 2-0 | 0-2 | . | 1-1 | 0-2 |  | 1-1 |
| 10 | BackToBas | 0-5 | 6-1 | 2-3 | 0-6 | 0-2 | 1-5 | 2-0 | 0-2 | 1-1 | . | 0-2 | 0-2 | 1-1 |
| 11 | SpecialMD | 0-7 | 7-0 | 4-0 | 1-6 | 2-0 | 4-0 | 2-0 | 1-1 | 2-0 | 2-0 | . | 2-0 | 2-0 |
| 12 | BeetaSaag | 0-7 | 7-0 | 4-0 | 0-6 | 2-0 | 6-0 | 2-0 | 1-1 |  | 2-0 | 0-2 | . | 2-0 |
| 13 | Savi6b | 0-7 |  |  |  | 0-2 | 3-1 | 2-0 | 0-2 | 1-1 | 1-1 | 0-2 | 0-2 | . |

### 15x11

356 unique games. First player (F) won 189 (53.1%); in the 214 games with no timeout or full-column event F won 103 (48.1%). Decided by a blast: 56 (15.7%), by a normal six: 300. Plies min/median/p90/max = 6/31/118/242, mean 48.8.

Game length (plies):

| Plies | Games | Share |  |
|---|---|---|---|
| 1-40 | 219 | 61.5% | ######################### |
| 41-80 | 59 | 16.6% | ####### |
| 81-120 | 43 | 12.1% | ##### |
| 121-160 | 21 | 5.9% | ## |
| 161-200 | 7 | 2.0% | # |
| 201-240 | 6 | 1.7% | # |
| 241-280 | 1 | 0.3% |  |
| 281-320 | 0 | 0.0% |  |
| 321+ | 0 | 0.0% |  |

First-player win rate by pairing of team roles (savi = team Savi, ours = No beaches?, other):

| Pairing | Games | F won | F win rate |
|---|---|---|---|
| other (F) vs other (S) | 104 | 53 | 51.0% |
| other (F) vs ours (S) | 61 | 2 | 3.3% |
| other (F) vs savi (S) | 51 | 0 | 0.0% |
| ours (F) vs other (S) | 63 | 63 | 100.0% |
| ours (F) vs savi (S) | 7 | 1 | 14.3% |
| savi (F) vs other (S) | 64 | 64 | 100.0% |
| savi (F) vs ours (S) | 6 | 6 | 100.0% |

Per bot (W/L; its wins by six/blast; plies of its wins and its losses as min/median/p90/max; own timeouts):

| Bot | Games | W/L | Win % | As F W/L | As S W/L | Wins six/blast | Plies in wins | Plies in losses | Own timeouts |
|---|---|---|---|---|---|---|---|---|---|
| Funky | 84 | 16/68 | 19.0% | 10/35 | 6/33 | 16/0 | 8/19/67/79 | 11/24/51/77 | 0 |
| Savi8 | 71 | 71/0 | 100.0% | 38/0 | 33/0 | 58/13 | 6/39/127/208 | - | 10 |
| ours 2.1 | 70 | 65/5 | 92.9% | 31/2 | 34/3 | 52/13 | 7/36/142/209 | 142/182/242/242 | 15 |
| ours 2.2 | 67 | 58/9 | 86.6% | 33/4 | 25/5 | 45/13 | 8/34.5/112/155 | 112/165/208/208 | 21 |
| Savi7b | 57 | 56/1 | 98.2% | 32/0 | 24/1 | 39/17 | 7/45/142/242 | 209/209/209/209 | 31 |
| SpecialMD | 49 | 20/29 | 40.8% | 10/13 | 10/16 | 20/0 | 6/12/45/91 | 11/118/161/181 | 0 |
| lalalala | 48 | 22/26 | 45.8% | 12/11 | 10/15 | 22/0 | 6/13/38/176 | 17/93/152/201 | 0 |
| Testing_v2 | 45 | 6/39 | 13.3% | 4/16 | 2/23 | 6/0 | 9/34/77/77 | 6/11/16/19 | 503 |
| HurtMe | 42 | 9/33 | 21.4% | 4/15 | 5/18 | 9/0 | 13/15/61/61 | 24/36/55/64 | 55 |
| BeetaSaag | 40 | 9/31 | 22.5% | 4/13 | 5/18 | 9/0 | 6/18/48/48 | 11/82/95/121 | 0 |
| HereWeGoAg | 38 | 10/28 | 26.3% | 4/14 | 6/14 | 10/0 | 6/11.5/16/17 | 11/39/78/100 | 0 |
| StrangeQ | 38 | 8/30 | 21.1% | 4/14 | 4/16 | 8/0 | 6/11.5/17/17 | 11/31/34/41 | 0 |
| TooYoung | 34 | 0/34 | 0.0% | 0/16 | 0/18 | 0/0 | - | 6/12.5/17/30 | 409 |
| BackToBas | 29 | 6/23 | 20.7% | 3/14 | 3/9 | 6/0 | 6/25.5/43/43 | 11/20/64/79 | 0 |

Head to head, row bot wins-losses against column bot (both colours; bots with >= 12 games here):

| # | Bot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Funky | . | 0-7 | 0-7 | 0-7 |  | 0-7 | 0-7 | 5-2 | 0-7 | 0-7 | 0-7 | 1-6 | 7-0 | 3-4 |
| 2 | Savi8 | 7-0 | . |  | 7-0 |  | 7-0 | 6-0 | 7-0 | 7-0 | 7-0 | 6-0 | 7-0 | 7-0 | 3-0 |
| 3 | ours 2.1 | 7-0 |  | . |  | 1-5 | 7-0 | 7-0 | 7-0 | 7-0 | 7-0 | 7-0 | 6-0 | 4-0 | 5-0 |
| 4 | ours 2.2 | 7-0 | 0-7 |  | . |  | 7-0 | 5-2 | 7-0 | 6-0 | 7-0 | 7-0 | 7-0 |  | 5-0 |
| 5 | Savi7b |  |  | 5-1 |  | . | 7-0 | 7-0 | 7-0 | 7-0 | 7-0 | 5-0 | 7-0 |  | 4-0 |
| 6 | SpecialMD | 7-0 | 0-7 | 0-7 | 0-7 | 0-7 | . | 1-1 | 2-0 | 2-0 | 2-0 | 2-0 |  | 2-0 | 2-0 |
| 7 | lalalala | 7-0 | 0-6 | 0-7 | 2-5 | 0-7 | 1-1 | . | 3-0 | 2-0 | 1-0 | 1-0 | 2-0 | 2-0 | 1-0 |
| 8 | Testing_v2 | 2-5 | 0-7 | 0-7 | 0-7 | 0-7 | 0-2 | 0-3 | . | 2-0 |  | 0-1 |  | 2-0 |  |
| 9 | HurtMe | 7-0 | 0-7 | 0-7 | 0-6 | 0-7 | 0-2 | 0-2 | 0-2 | . |  |  |  | 2-0 |  |
| 10 | BeetaSaag | 7-0 | 0-7 | 0-7 | 0-7 | 0-7 | 0-2 | 0-1 |  |  | . |  |  | 2-0 |  |
| 11 | HereWeGoAg | 7-0 | 0-6 | 0-7 | 0-7 | 0-5 | 0-2 | 0-1 | 1-0 |  |  | . |  | 2-0 |  |
| 12 | StrangeQ | 6-1 | 0-7 | 0-6 | 0-7 | 0-7 |  | 0-2 |  |  |  |  | . | 2-0 |  |
| 13 | TooYoung | 0-7 | 0-7 | 0-4 |  |  | 0-2 | 0-2 | 0-2 | 0-2 | 0-2 | 0-2 | 0-2 | . | 0-2 |
| 14 | BackToBas | 4-3 | 0-3 | 0-5 | 0-5 | 0-4 | 0-2 | 0-1 |  |  |  |  |  | 2-0 | . |

## 3. Blast usage

A blast is available from the 11th own turn on (cooldown counter 10; skipped turns count) and again 10 own turns after each blast, so n own turns allow at most 1 + floor((n-11)/10) blasts (0 if n < 11). 'Used/avail' compares the blasts played with that maximum. 'At counter 10' = share of blasts played the first moment they were allowed. 'Wins' = blasts that ended the game; 'threat' = non-winning blasts after which the blaster has an immediate winning drop; 'defence' = blasts after which the opponent's immediate winning drops are gone (opponent had one before). Colour of the winning line: own = only the blaster's colour, opp = only the opponent's (a colour-blind win), both.

### 8x12

| Bot | Games | Blasts | Per game | Used/avail | At counter 10 | Median own turn of 1st blast | Median own turn (all) | Wins | Threat after | Defence | Win colour own/opp/both | Discs removed own / opp per blast | Blasts removing more opp than own | Blasts removing >= 6 discs | Blasts that won or left a threat |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TestimineM7 | 91 | 35 | 0.38 | 61% | 14% | 12 | 12 | 0 | 0 | 1 | 0/0/0 | 1.5 / 1.3 | 37% | 3% | 0% |
| ours 2.1 | 79 | 73 | 0.92 | 44% | 18% | 15 | 26 | 11 | 15 | 4 | 4/7/0 | 1.2 / 3.0 | 71% | 18% | 36% |
| Savi5 | 58 | 63 | 1.09 | 53% | 11% | 14 | 22 | 0 | 9 | 5 | 0/0/0 | 1.1 / 2.7 | 76% | 13% | 14% |
| Savi6 | 57 | 47 | 0.82 | 38% | 23% | 16 | 21 | 1 | 13 | 1 | 1/0/0 | 0.9 / 2.7 | 83% | 13% | 30% |
| Testing_v2 | 57 | 70 | 1.23 | 50% | 27% | 17 | 26.5 | 3 | 10 | 2 | 3/0/0 | 1.0 / 3.4 | 90% | 33% | 19% |
| BeetaSaag | 46 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |
| StrangeQ | 46 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |
| HurtMe | 44 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |
| AntiStrat | 36 | 34 | 0.94 | 100% | 100% | 10 | 10 | 0 | 1 | 2 | 0/0/0 | 1.4 / 1.2 | 24% | 6% | 3% |
| \u006c\u0061.. | 36 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |
| lalala | 34 | 5 | 0.15 | 13% | 0% | 12 | 12 | 0 | 0 | 5 | 0/0/0 | 1.0 / 2.6 | 60% | 0% | 0% |
| goodN | 33 | 14 | 0.42 | 29% | 0% | 28 | 28.5 | 0 | 1 | 13 | 0/0/0 | 1.9 / 2.6 | 57% | 43% | 7% |
| sleepT | 33 | 18 | 0.55 | 100% | 100% | 10 | 10 | 0 | 1 | 0 | 0/0/0 | 0.7 / 0.7 | 22% | 6% | 6% |
| SpecialMD | 32 | 20 | 0.62 | 38% | 0% | 23 | 23.5 | 0 | 0 | 20 | 0/0/0 | 1.4 / 2.8 | 60% | 15% | 0% |

### 15x22

| Bot | Games | Blasts | Per game | Used/avail | At counter 10 | Median own turn of 1st blast | Median own turn (all) | Wins | Threat after | Defence | Win colour own/opp/both | Discs removed own / opp per blast | Blasts removing more opp than own | Blasts removing >= 6 discs | Blasts that won or left a threat |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| ours 2.1 | 80 | 106 | 1.32 | 63% | 42% | 12 | 33.5 | 7 | 4 | 4 | 2/5/0 | 1.1 / 2.7 | 77% | 14% | 10% |
| Funky | 62 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |
| HereWeGoAg | 59 | 2 | 0.03 | 6% | 0% | 18 | 18 | 0 | 0 | 0 | 0/0/0 | 3.0 / 1.0 | 0% | 0% | 0% |
| Savi7 | 59 | 108 | 1.83 | 63% | 27% | 16 | 36.5 | 9 | 10 | 4 | 5/4/0 | 1.2 / 2.8 | 82% | 19% | 18% |
| HurtMe | 49 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |
| HereWeGo | 48 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |
| Testing_v2 | 46 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |
| lalalala | 46 | 9 | 0.20 | 16% | 0% | 35 | 38 | 0 | 0 | 9 | 0/0/0 | 0.7 / 2.1 | 67% | 0% | 0% |
| StrangeQ | 44 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |
| BackToBas | 43 | 21 | 0.49 | 100% | 100% | 10 | 20 | 0 | 1 | 1 | 0/0/0 | 1.7 / 1.6 | 33% | 10% | 5% |
| SpecialMD | 43 | 13 | 0.30 | 17% | 0% | 43 | 47 | 1 | 1 | 12 | 0/1/0 | 1.2 / 3.3 | 85% | 23% | 15% |
| BeetaSaag | 42 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |
| Savi6b | 25 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |

### 15x11

| Bot | Games | Blasts | Per game | Used/avail | At counter 10 | Median own turn of 1st blast | Median own turn (all) | Wins | Threat after | Defence | Win colour own/opp/both | Discs removed own / opp per blast | Blasts removing more opp than own | Blasts removing >= 6 discs | Blasts that won or left a threat |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Funky | 84 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |
| Savi8 | 71 | 70 | 0.99 | 37% | 11% | 17 | 37 | 13 | 14 | 4 | 5/7/1 | 1.3 / 3.2 | 79% | 24% | 39% |
| ours 2.1 | 70 | 142 | 2.03 | 68% | 39% | 12 | 32 | 13 | 15 | 3 | 3/10/0 | 1.2 / 2.6 | 77% | 15% | 20% |
| ours 2.2 | 67 | 128 | 1.91 | 67% | 32% | 13 | 33 | 13 | 9 | 4 | 3/10/0 | 1.2 / 2.5 | 71% | 8% | 17% |
| Savi7b | 57 | 82 | 1.44 | 47% | 13% | 15.5 | 32 | 17 | 4 | 1 | 10/7/0 | 1.5 / 2.8 | 70% | 23% | 26% |
| SpecialMD | 49 | 12 | 0.24 | 7% | 0% | 59.5 | 59.5 | 0 | 1 | 12 | 0/0/0 | 1.3 / 2.9 | 75% | 8% | 8% |
| lalalala | 48 | 22 | 0.46 | 18% | 0% | 27.5 | 33 | 0 | 1 | 20 | 0/0/0 | 1.4 / 3.3 | 86% | 50% | 5% |
| Testing_v2 | 45 | 1 | 0.02 | 3% | 0% | 46 | 46 | 0 | 0 | 0 | 0/0/0 | 0.0 / 5.0 | 100% | 0% | 0% |
| HurtMe | 42 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |
| BeetaSaag | 40 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |
| HereWeGoAg | 38 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |
| StrangeQ | 38 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |
| TooYoung | 34 | 0 | 0.00 | 0% | - | - | - | 0 | 0 | 0 | 0/0/0 | - | - | - | - |
| BackToBas | 29 | 20 | 0.69 | 100% | 100% | 10 | 10 | 0 | 2 | 1 | 0/0/0 | 2.7 / 1.9 | 35% | 15% | 10% |

## 4. Openings and habits of the Savi line and the other leading bots

Columns are base-36 digits; centre columns: 8x12 = 5/6, 15x22 = a/b (10/11), 15x11 = 5. 'Own move i' = the column of the bot's i-th own move (as F these are plies 1, 3, 5, as S plies 2, 4, 6).

### 8x12

| Bot | Side | Games | Own move 1 | Own move 2 | Own move 3 |
|---|---|---|---|---|---|
| Savi6 | F | 30 | 5 100% | 5 53%, 6 47% | 5 63%, 6 37% |
| Savi6 | S | 27 | 5 59%, 6 41% | 5 63%, 6 33%, 7 4% | 6 48%, 5 33%, 7 11% |
| Savi5 | F | 30 | 5 100% | 6 43%, 5 40%, 7 13% | 5 43%, 6 43%, 7 10% |
| Savi5 | S | 28 | 5 86%, 6 14% | 5 39%, 6 29%, 7 29% | 7 32%, 5 29%, 4 18% |
| ours 2.1 | F | 39 | 3 82%, 5 18% | 5 41%, 3 23%, 6 21% | 5 44%, 3 23%, 6 15% |
| ours 2.1 | S | 40 | 5 42%, 6 30%, 3 12% | 5 60%, 6 20%, 7 8% | 5 45%, 6 30%, 7 8% |
| Testing_v2 | F | 29 | 5 100% | 5 86%, 7 10%, 6 3% | 5 76%, 7 14%, 6 10% |
| Testing_v2 | S | 28 | 5 79%, 6 18%, 3 4% | 5 64%, 6 25%, 7 7% | 6 36%, 5 25%, 7 21% |
| SpecialMD | F | 18 | 5 100% | 5 100% | 6 72%, 5 28% |
| SpecialMD | S | 14 | 5 100% | 5 57%, 6 43% | 6 64%, 5 36% |

### 15x22

| Bot | Side | Games | Own move 1 | Own move 2 | Own move 3 |
|---|---|---|---|---|---|
| Savi7 | F | 32 | a 100% | a 41%, b 16%, 6 16% | a 56%, 6 19%, 5 12% |
| Savi7 | S | 27 | a 52%, 5 26%, 6 19% | 5 26%, a 22%, 6 19% | 5 30%, a 22%, 7 11% |
| ours 2.1 | F | 35 | a 97%, 6 3% | a 49%, b 23%, c 11% | a 23%, c 23%, b 20% |
| ours 2.1 | S | 45 | a 44%, 9 20%, b 13% | a 27%, b 22%, 6 16% | a 36%, 5 22%, 0 9% |
| Testing_v2 | F | 20 | 9 10%, d 10%, e 5% | 9 5%, 4 5% | 3 5% |
| Testing_v2 | S | 26 | d 15%, 7 12%, 9 8% | 5 4% | b 4% |
| lalalala | F | 22 | 5 100% | 6 55%, 5 23%, 3 9% | 7 32%, 1 23%, 5 18% |
| lalalala | S | 24 | 5 42%, a 33%, 6 17% | a 33%, 6 25%, 5 25% | 5 38%, a 25%, c 12% |
| SpecialMD | F | 20 | a 100% | a 100% | a 100% |
| SpecialMD | S | 23 | a 100% | a 100% | a 100% |

### 15x11

| Bot | Side | Games | Own move 1 | Own move 2 | Own move 3 |
|---|---|---|---|---|---|
| Savi8 | F | 38 | 5 100% | 5 61%, 6 34%, 4 5% | 5 47%, 6 34%, 4 16% |
| Savi8 | S | 33 | 5 79%, 6 18%, 3 3% | 5 52%, 6 33%, 4 12% | 5 45%, 6 24%, 4 21% |
| Savi7b | F | 32 | 5 100% | 5 50%, 6 41%, 4 9% | 5 56%, 6 41%, 4 3% |
| Savi7b | S | 25 | 5 80%, 6 20% | 5 56%, 6 44% | 5 36%, 4 24%, 6 24% |
| ours 2.1 | F | 33 | 5 100% | 5 55%, 6 21%, 4 21% | 5 67%, 6 21%, 4 12% |
| ours 2.1 | S | 37 | 5 76%, 6 16%, 3 8% | 6 43%, 5 32%, 4 19% | 5 46%, 6 19%, 0 11% |
| ours 2.2 | F | 37 | 5 76%, 4 24% | 5 59%, 6 24%, 4 14% | 5 70%, 6 16%, 4 11% |
| ours 2.2 | S | 30 | 5 70%, 6 13%, 3 7% | 5 43%, 6 43%, 4 3% | 5 43%, 6 17%, 4 13% |
| Testing_v2 | F | 20 | 8 45%, 7 15%, 3 5% | 5 10%, 3 5%, 7 5% | 2 10%, 9 5%, 5 5% |
| Testing_v2 | S | 25 | 8 44%, 3 8%, 6 4% | 3 4%, 5 4% | 9 4%, 5 4% |
| lalalala | F | 23 | 5 100% | 4 83%, 5 13%, 6 4% | 0 57%, 6 26%, 5 9% |
| lalalala | S | 25 | 5 76%, 6 12%, 4 8% | 5 60%, 4 20%, 6 16% | 5 64%, 6 16%, 4 12% |
| SpecialMD | F | 23 | 5 100% | 5 100% | 5 100% |
| SpecialMD | S | 26 | 5 100% | 5 100% | 5 100% |

### Stacking and centre habits (first 24 plies)

'Same col as opp' = share of the bot's own moves that go into the column the opponent just played in; 'centre' = share of own moves within one column of the centre; 'mean height' = average stack height at the end over all columns of the decisive position (fill of the board).

| Size | Bot | Own moves | Same col as opp's last move | Within 1 of centre |
|---|---|---|---|---|
| 8x12 | Savi6 | 673 | 38% | 84% |
| 8x12 | Savi5 | 678 | 34% | 83% |
| 8x12 | ours 2.1 | 891 | 36% | 72% |
| 8x12 | Testing_v2 | 631 | 37% | 83% |
| 8x12 | SpecialMD | 338 | 14% | 89% |
| 15x22 | Savi7 | 656 | 50% | 47% |
| 15x22 | ours 2.1 | 847 | 35% | 58% |
| 15x22 | lalalala | 429 | 36% | 24% |
| 15x22 | SpecialMD | 388 | 37% | 97% |
| 15x11 | Savi8 | 828 | 41% | 82% |
| 15x11 | Savi7b | 670 | 46% | 81% |
| 15x11 | ours 2.1 | 808 | 41% | 73% |
| 15x11 | ours 2.2 | 781 | 42% | 78% |
| 15x11 | Testing_v2 | 60 | 3% | 32% |
| 15x11 | lalalala | 497 | 43% | 59% |
| 15x11 | SpecialMD | 498 | 52% | 100% |

### Board shape at ply 30

Discs of each bot after 30 plies (games that lasted that long): 'central' = discs within one column of the centre, 'floor' = discs on row 0, 'rows 0-1' = discs on the two lowest rows, per bot-game (blasts have already thinned the board).

| Size | Bot | Bot-games | Discs | Central | Floor | Rows 0-1 |
|---|---|---|---|---|---|---|
| 8x12 | TestimineM7 | 27 | 12.4 | 37% | 49% | 71% |
| 8x12 | ours 2.1 | 52 | 14.5 | 72% | 15% | 30% |
| 8x12 | Savi5 | 40 | 14.4 | 77% | 18% | 35% |
| 8x12 | Savi6 | 45 | 14.8 | 81% | 15% | 30% |
| 8x12 | Testing_v2 | 44 | 13.8 | 79% | 17% | 37% |
| 8x12 | BeetaSaag | 27 | 13.8 | 81% | 28% | 54% |
| 8x12 | HurtMe | 31 | 13.7 | 78% | 29% | 50% |
| 15x22 | ours 2.1 | 42 | 13.9 | 54% | 25% | 47% |
| 15x22 | Savi7 | 43 | 14.3 | 43% | 18% | 30% |
| 15x11 | Funky | 28 | 15.7 | 32% | 39% | 63% |
| 15x11 | Savi8 | 47 | 14.5 | 82% | 13% | 25% |
| 15x11 | ours 2.1 | 45 | 13.8 | 72% | 18% | 35% |
| 15x11 | ours 2.2 | 45 | 13.9 | 77% | 16% | 33% |
| 15x11 | Savi7b | 44 | 14.2 | 82% | 13% | 25% |
| 15x11 | SpecialMD | 33 | 14.0 | 100% | 14% | 24% |
| 15x11 | lalalala | 32 | 13.9 | 59% | 17% | 35% |
| 15x11 | HurtMe | 30 | 14.3 | 52% | 30% | 53% |
| 15x11 | BeetaSaag | 31 | 13.7 | 100% | 14% | 25% |

### Common 12-ply start on 15x11 and where it branches

Many strong 15x11 games begin with the centre column filled alternately (F5 S5 six times, 12 plies). The table groups the games that began that way by the next pair of tokens (plies 13-14) and shows the result for the bot group shown (W-L of that group in that colour).

| Group | Colour | Plies 13-14 | Wins | Losses |
|---|---|---|---|---|
| ours | F | F3S3 | 4 | 0 |
| ours | F | F3S5 | 0 | 5 |
| ours | F | F5S5 | 1 | 0 |
| ours | F | F7S7 | 1 | 0 |
| ours | S | F5S4 | 0 | 5 |
| savi | F | F5S4 | 5 | 0 |
| savi | F | F5S5 | 7 | 0 |
| savi | S | F3S5 | 5 | 0 |
| savi | S | F5S5 | 0 | 1 |

## 5. How games end and how fast the Savi bots win

Finish categories are read from the position the loser faced on its last move: 'double threat' = the winner had >= 2 distinct immediately winning columns; 'blocked the single threat, cell above won' = the loser dropped on the only winning column and the cell above it won (stacked threat); 'single threat not blocked'; 'loser's move opened the winning cell' = no immediate threat existed, the loser's drop gave the cell above to the winner; 'loser skipped its last turn' = the winner moved twice in a row (timeout/full column).

### 8x12

| Winner group | Games won | How it ended | Games | Share |
|---|---|---|---|---|
| Savi winners | 108 | single threat not blocked | 38 | 35.2% |
| Savi winners | 108 | loser's move opened the winning cell | 30 | 27.8% |
| Savi winners | 108 | blocked the single threat, cell above won | 18 | 16.7% |
| Savi winners | 108 | double threat (>=2 winning columns) | 17 | 15.7% |
| Savi winners | 108 | loser skipped its last turn | 2 | 1.9% |
| Savi winners | 108 | after loser's blast | 2 | 1.9% |
| Savi winners | 108 | blast (own colour) | 1 | 0.9% |
| Our winners | 76 | single threat not blocked | 19 | 25.0% |
| Our winners | 76 | loser's move opened the winning cell | 18 | 23.7% |
| Our winners | 76 | double threat (>=2 winning columns) | 18 | 23.7% |
| Our winners | 76 | blocked the single threat, cell above won | 8 | 10.5% |
| Our winners | 76 | blast (opponent colour only) | 7 | 9.2% |
| Our winners | 76 | blast (own colour) | 4 | 5.3% |
| Our winners | 76 | loser skipped its last turn | 1 | 1.3% |
| Our winners | 76 | after loser's blast | 1 | 1.3% |
| Other winners | 157 | single threat not blocked | 88 | 56.1% |
| Other winners | 157 | double threat (>=2 winning columns) | 35 | 22.3% |
| Other winners | 157 | loser's move opened the winning cell | 17 | 10.8% |
| Other winners | 157 | blocked the single threat, cell above won | 13 | 8.3% |
| Other winners | 157 | blast (own colour) | 3 | 1.9% |
| Other winners | 157 | after loser's blast | 1 | 0.6% |

Winning six by direction (H horizontal, V vertical, diag) and height of the last disc (row index, 0 = floor):

| Winner | Six wins | H | V | diag | mixed (2+ lines) | Mean row of last disc | Median row |
|---|---|---|---|---|---|---|---|
| Savi | 107 | 52.3% | 22.4% | 25.2% | 0.0% | 3.2 | 3 |
| Ours | 65 | 56.9% | 20.0% | 21.5% | 1.5% | 3.2 | 3 |
| Other | 154 | 44.2% | 50.0% | 5.8% | 0.0% | 3.5 | 5 |

Blasts in the last 8 plies of games decided by a normal six ('threat' = the winner's blast left it an immediate winning drop):

| Winner | Six wins | Winner blasted | of which blast left a threat | Loser blasted | No blast in last 8 plies |
|---|---|---|---|---|---|
| Savi | 107 | 18.7% | 15.0% | 22.4% | 62.6% |
| Ours | 65 | 16.9% | 9.2% | 26.2% | 64.6% |
| Other | 154 | 2.6% | 1.9% | 13.0% | 85.7% |

### 15x22

| Winner group | Games won | How it ended | Games | Share |
|---|---|---|---|---|
| Savi winners | 59 | single threat not blocked | 22 | 37.3% |
| Savi winners | 59 | double threat (>=2 winning columns) | 21 | 35.6% |
| Savi winners | 59 | loser skipped its last turn | 5 | 8.5% |
| Savi winners | 59 | blast (own colour) | 5 | 8.5% |
| Savi winners | 59 | blast (opponent colour only) | 4 | 6.8% |
| Savi winners | 59 | loser's move opened the winning cell | 1 | 1.7% |
| Savi winners | 59 | blocked the single threat, cell above won | 1 | 1.7% |
| Our winners | 78 | double threat (>=2 winning columns) | 48 | 61.5% |
| Our winners | 78 | loser's move opened the winning cell | 10 | 12.8% |
| Our winners | 78 | single threat not blocked | 6 | 7.7% |
| Our winners | 78 | blast (opponent colour only) | 5 | 6.4% |
| Our winners | 78 | loser skipped its last turn | 4 | 5.1% |
| Our winners | 78 | blocked the single threat, cell above won | 3 | 3.8% |
| Our winners | 78 | blast (own colour) | 2 | 2.6% |
| Other winners | 186 | single threat not blocked | 130 | 69.9% |
| Other winners | 186 | loser skipped its last turn | 27 | 14.5% |
| Other winners | 186 | double threat (>=2 winning columns) | 25 | 13.4% |
| Other winners | 186 | blocked the single threat, cell above won | 1 | 0.5% |
| Other winners | 186 | blast (opponent colour only) | 1 | 0.5% |
| Other winners | 186 | after loser's blast | 1 | 0.5% |
| Other winners | 186 | loser's move opened the winning cell | 1 | 0.5% |

Winning six by direction (H horizontal, V vertical, diag) and height of the last disc (row index, 0 = floor):

| Winner | Six wins | H | V | diag | mixed (2+ lines) | Mean row of last disc | Median row |
|---|---|---|---|---|---|---|---|
| Savi | 50 | 64.0% | 30.0% | 6.0% | 0.0% | 2.2 | 0 |
| Ours | 71 | 87.3% | 4.2% | 8.5% | 0.0% | 1.0 | 0 |
| Other | 185 | 25.4% | 74.6% | 0.0% | 0.0% | 4.2 | 5 |

Blasts in the last 8 plies of games decided by a normal six ('threat' = the winner's blast left it an immediate winning drop):

| Winner | Six wins | Winner blasted | of which blast left a threat | Loser blasted | No blast in last 8 plies |
|---|---|---|---|---|---|
| Savi | 50 | 12.0% | 2.0% | 22.0% | 68.0% |
| Ours | 71 | 8.5% | 1.4% | 11.3% | 81.7% |
| Other | 185 | 1.1% | 0.0% | 3.2% | 95.7% |

### 15x11

| Winner group | Games won | How it ended | Games | Share |
|---|---|---|---|---|
| Savi winners | 127 | double threat (>=2 winning columns) | 32 | 25.2% |
| Savi winners | 127 | single threat not blocked | 30 | 23.6% |
| Savi winners | 127 | loser's move opened the winning cell | 17 | 13.4% |
| Savi winners | 127 | blast (own colour) | 15 | 11.8% |
| Savi winners | 127 | blast (opponent colour only) | 14 | 11.0% |
| Savi winners | 127 | loser skipped its last turn | 11 | 8.7% |
| Savi winners | 127 | blocked the single threat, cell above won | 7 | 5.5% |
| Savi winners | 127 | blast (both colours) | 1 | 0.8% |
| Our winners | 123 | single threat not blocked | 36 | 29.3% |
| Our winners | 123 | double threat (>=2 winning columns) | 36 | 29.3% |
| Our winners | 123 | blast (opponent colour only) | 20 | 16.3% |
| Our winners | 123 | loser's move opened the winning cell | 15 | 12.2% |
| Our winners | 123 | loser skipped its last turn | 8 | 6.5% |
| Our winners | 123 | blast (own colour) | 6 | 4.9% |
| Our winners | 123 | blocked the single threat, cell above won | 2 | 1.6% |
| Other winners | 106 | single threat not blocked | 56 | 52.8% |
| Other winners | 106 | loser skipped its last turn | 31 | 29.2% |
| Other winners | 106 | double threat (>=2 winning columns) | 13 | 12.3% |
| Other winners | 106 | loser's move opened the winning cell | 5 | 4.7% |
| Other winners | 106 | blocked the single threat, cell above won | 1 | 0.9% |

Winning six by direction (H horizontal, V vertical, diag) and height of the last disc (row index, 0 = floor):

| Winner | Six wins | H | V | diag | mixed (2+ lines) | Mean row of last disc | Median row |
|---|---|---|---|---|---|---|---|
| Savi | 97 | 42.3% | 29.9% | 26.8% | 1.0% | 3.7 | 5 |
| Ours | 97 | 57.7% | 28.9% | 13.4% | 0.0% | 3.2 | 1 |
| Other | 106 | 35.8% | 60.4% | 2.8% | 0.9% | 4.1 | 5 |

Blasts in the last 8 plies of games decided by a normal six ('threat' = the winner's blast left it an immediate winning drop):

| Winner | Six wins | Winner blasted | of which blast left a threat | Loser blasted | No blast in last 8 plies |
|---|---|---|---|---|---|
| Savi | 97 | 13.4% | 8.2% | 12.4% | 76.3% |
| Ours | 97 | 11.3% | 3.1% | 10.3% | 80.4% |
| Other | 106 | 3.8% | 1.9% | 2.8% | 93.4% |

### Speed of the Savi bots (plies of their wins, min/median/p90/max)

'Clean wins' = wins in games with no timeout or full-column event at all (a bot that times out hands the game over in a few plies).

| Size | Bot | Opponents | W/L | Plies in wins | Mean | Clean wins: n and plies | Plies in losses |
|---|---|---|---|---|---|---|---|
| 8x12 | Savi6 | vs other bots | 53/1 | 15/42/84/128 | 49 | 43: 15/40/74/115 | 114/114/114/114 |
| 8x12 | Savi6 | vs ours | 2/1 | 115/118.5/122/122 | 118 | 2: 115/118.5/122/122 | 109/109/109/109 |
| 8x12 | Savi5 | vs other bots | 52/1 | 13/35.5/80/130 | 44 | 43: 13/33/71/100 | 123/123/123/123 |
| 8x12 | Savi5 | vs ours | 1/4 | 136/136/136/136 | 136 | 1: 136/136/136/136 | 104/116.5/132/132 |
| 15x22 | Savi7 | vs other bots | 50/2 | 6/39/89/174 | 49 | 35: 11/41/101/174 | 52/87/122/122 |
| 15x22 | Savi7 | vs ours | 2/5 | 169/255/341/341 | 255 | 1: 169/169/169/169 | 66/166/340/340 |
| 15x11 | Savi8 | vs other bots | 64/0 | 6/36.5/107/161 | 49 | 38: 14/42/107/127 | - |
| 15x11 | Savi8 | vs ours | 7/0 | 112/157/208/208 | 165 | 2: 112/156.5/201/201 | - |
| 15x11 | Savi7b | vs other bots | 51/0 | 7/38/104/152 | 56 | 30: 14/45/102/152 | - |
| 15x11 | Savi7b | vs ours | 5/1 | 142/182/242/242 | 186 | 3: 142/153/242/242 | 209/209/209/209 |

## 6. Timeouts

Own timeouts = letters of the game's server log for that side (a timed-out turn leaves no move token and the bot is skipped). Games with a timeout = games where the bot timed out at least once; 'max in a game' shows the server-stall games (a turn after a stall times out again and again). A rate per own turn is not given: skipped turns of both sides in a row leave no trace in the move string.

### 8x12

| Bot | Team | Games | Own timeouts | Games with a timeout | Share of games | Timeouts per game | Max in a game |
|---|---|---|---|---|---|---|---|
| Testimine-M7 | WileyQuarks | 91 | 0 | 0 | 0.0% | 0.00 | 0 |
| Savi5 beater 2.1 | No beaches? | 79 | 3 | 3 | 3.8% | 0.04 | 1 |
| Savi5 | Savi | 58 | 0 | 0 | 0.0% | 0.00 | 0 |
| Savi6 - "Beat this" | Savi | 57 | 1 | 1 | 1.8% | 0.02 | 1 |
| Testing_v2 | LinMacWin | 57 | 50 | 34 | 59.6% | 0.88 | 4 |
| BeetaSaag | Saag1 | 46 | 0 | 0 | 0.0% | 0.00 | 0 |
| Strange Quark 1 | Strange Quark | 46 | 0 | 0 | 0.0% | 0.00 | 0 |
| Hurt me plenty | cristi | 44 | 88 | 31 | 70.5% | 2.00 | 9 |
| AntiStrat_FINAL | Shalomtehnikud | 36 | 0 | 0 | 0.0% | 0.00 | 0 |
| \u006c\u0061\u006c\2 | \u006c\u0061\u006c\u0061\u006c\u0061\u0062\u006f\u0074 | 36 | 0 | 0 | 0.0% | 0.00 | 0 |
| lalalabot | \u006c\u0061\u006c\u0061\u006c\u0061\u0062\u006f\u0074 | 34 | 0 | 0 | 0.0% | 0.00 | 0 |
| good night | KolmApelsini | 33 | 0 | 0 | 0.0% | 0.00 | 0 |
| sleep tight | KolmApelsini | 33 | 0 | 0 | 0.0% | 0.00 | 0 |
| specialmovedestroyer | KolmApelsini | 32 | 0 | 0 | 0.0% | 0.00 | 0 |

### 15x22

| Bot | Team | Games | Own timeouts | Games with a timeout | Share of games | Timeouts per game | Max in a game |
|---|---|---|---|---|---|---|---|
| Savi5 beater 2.1 | No beaches? | 80 | 117 | 12 | 15.0% | 1.46 | 79 |
| FunkyMelodyBot | FunkyMelody | 62 | 0 | 0 | 0.0% | 0.00 | 0 |
| HereWeGoAgain | WileyQuarks | 59 | 86 | 8 | 13.6% | 1.46 | 22 |
| Savi7 - "The Prince" | Savi | 59 | 10 | 8 | 13.6% | 0.17 | 3 |
| Hurt me plenty | cristi | 49 | 256 | 26 | 53.1% | 5.22 | 86 |
| HereWeGo | WileyQuarks | 48 | 6 | 3 | 6.2% | 0.12 | 2 |
| Testing_v2 | LinMacWin | 46 | 555 | 46 | 100.0% | 12.07 | 33 |
| lalalalabot | \u006c\u0061\u006c\u0061\u006c\u0061\u0062\u006f\u0074 | 46 | 0 | 0 | 0.0% | 0.00 | 0 |
| Strange Quark 1 | Strange Quark | 44 | 0 | 0 | 0.0% | 0.00 | 0 |
| Back To Basics | Shalomtehnikud | 43 | 0 | 0 | 0.0% | 0.00 | 0 |
| specialmovedestroyer | KolmApelsini | 43 | 0 | 0 | 0.0% | 0.00 | 0 |
| BeetaSaag | Saag1 | 42 | 12 | 2 | 4.8% | 0.29 | 6 |
| Savi6 - "Beat This" | Savi | 25 | 0 | 0 | 0.0% | 0.00 | 0 |

### 15x11

| Bot | Team | Games | Own timeouts | Games with a timeout | Share of games | Timeouts per game | Max in a game |
|---|---|---|---|---|---|---|---|
| FunkyMelodyBot | FunkyMelody | 84 | 0 | 0 | 0.0% | 0.00 | 0 |
| Savi8 - "The King" | Savi | 71 | 10 | 9 | 12.7% | 0.14 | 2 |
| Savi5 beater 2.1 | No beaches? | 70 | 15 | 13 | 18.6% | 0.21 | 3 |
| Savi beater 2.2 | No beaches? | 67 | 21 | 18 | 26.9% | 0.31 | 2 |
| Savi 7 - "The Prince | Savi | 57 | 31 | 13 | 22.8% | 0.54 | 10 |
| specialmovedestroyer | KolmApelsini | 49 | 0 | 0 | 0.0% | 0.00 | 0 |
| lalalalabot | \u006c\u0061\u006c\u0061\u006c\u0061\u0062\u006f\u0074 | 48 | 0 | 0 | 0.0% | 0.00 | 0 |
| Testing_v2 | LinMacWin | 45 | 503 | 45 | 100.0% | 11.18 | 36 |
| Hurt me plenty | cristi | 42 | 55 | 27 | 64.3% | 1.31 | 5 |
| BeetaSaag | Saag1 | 40 | 0 | 0 | 0.0% | 0.00 | 0 |
| HereWeGoAgain | WileyQuarks | 38 | 0 | 0 | 0.0% | 0.00 | 0 |
| Strange Quark 1 | Strange Quark | 38 | 0 | 0 | 0.0% | 0.00 | 0 |
| I'm too young to die | cristi | 34 | 409 | 34 | 100.0% | 12.03 | 29 |
| Back To Basics | Shalomtehnikud | 29 | 0 | 0 | 0.0% | 0.00 | 0 |

### Our versions, timeouts and results by size

'Stall games' = games in which BOTH bots timed out (server stall rather than a slow bot). The next column splits the games with a timeout by how many we had: 1 / 2-3 / 4 or more.

| Version | Size | Games | Own timeouts | Games with a timeout | Share | 1 / 2-3 / 4+ per game | Stall games | Lost games with our timeout | Max in a game |
|---|---|---|---|---|---|---|---|---|---|
| Savi5 beater 2.1 | 8x12 | 79 | 3 | 3 | 3.8% | 3 / 0 / 0 | 1 | 0 of 3 | 1 |
| Savi5 beater 2.1 | 15x22 | 80 | 117 | 12 | 15.0% | 8 / 2 / 2 | 6 | 1 of 2 | 79 |
| Savi5 beater 2.1 | 15x11 | 70 | 15 | 13 | 18.6% | 12 / 1 / 0 | 5 | 1 of 5 | 3 |
| Savi beater 2.2 | 15x11 | 67 | 21 | 18 | 26.9% | 15 / 3 / 0 | 2 | 5 of 9 | 2 |

Visible skipped turns of ours by game phase (replay breaks in the F/S alternation, turn index counting skipped turns):

| Version | Size | All | turn 0-5 | 6-12 | 13-18 | 19-29 | 30-59 | 60+ |
|---|---|---|---|---|---|---|---|---|
| Savi5 beater 2.1 | 8x12 | 3 | 0 | 0 | 1 | 2 | 0 | 0 |
| Savi5 beater 2.1 | 15x22 | 23 | 6 | 0 | 7 | 3 | 6 | 1 |
| Savi5 beater 2.1 | 15x11 | 11 | 1 | 0 | 5 | 2 | 3 | 0 |
| Savi beater 2.2 | 15x11 | 18 | 0 | 0 | 11 | 2 | 3 | 2 |

Both versions on 15x11 time out most at turns 13-18, i.e. right after the 12-ply stack of the centre column in which most of these games begin (the first move off the stack is turn 12/13). A turn index of 14 is the first player's 8th move.
