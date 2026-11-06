# Losses and draws — Cristi Schuszter

Source: leaderboard/0f940697-6928-4caa-a8d9-fe33be770acf, exported 2026-11-03. 46 matches on the page in total.
Every game was replayed by the engine rules (8x12 board, 6 in a row, 3x3 blast including the centre, plus gravity; a line after a move counts for the player who moved).

**Result: 43 wins, 3 losses (all against Savi5), 0 draws.**

Notation: `F`/`S` = first/second player, number = column 0-11, `*` = special move (blast). `(skip X)` = the move of X was skipped (100 ms timeout -> column -1).
Row r0 = top of the board, r7 = bottom.

## Match 37: Cristi Schuszter (F) vs Savi5 (S) — Cristi = F
ID `5368c685-ef1f-4963-945a-6c3bb5af40db` · moves in history: 95 · special moves by Cristi: 2, opponent: 3
Logs: OOM (Memory limit exceeded: 295MB) -> SecurityException; then 1 timeout FIRST_PLAYER (Cristi) -> column -1

```
  1: F9 S5 F5 S5 F5 S6 F5 S6 F5 S6
 11: F6 S6 F6 S6 F5 S5 F6 S11 F3 S7
 21: F1 S4 F4 S11 F11 S9 F1 S9 F4 S4
 31: F9 S7 F7 S7 (skip F) S3 F9 S9 F1 S4* F5
 41: S1 F4 S4 F4 S5 F4 S1 F1 S7 F7
 51: S7 F4 S9 F5 S9 F7 S3 F1 S2* F8*
 61: S1 F3 S11 F11 S11 F11 S10 F0 S10 F1
 71: S10 F1 S10 F10 S10 F8 S9 F3 S3 F3
 81: S3 F9 S10 F0 S7 F7 S10 F0 S8 F0
 91: S8 F8* S9* F10 S8
```
(the line number is the move number in the match history; a skipped move takes no number)
```
```
Win: Savi5 (S) with move #95 `S8`; line cells (row,col): [(2, 11), (3, 10), (4, 9), (5, 8), (6, 7), (7, 6)]

Final position:
```
     0  1  2  3  4  5  6  7  8  9 10 11
r0   .  F  .  .  F  F  F  .  .  .  .  .
r1   .  F  .  .  F  S  S  .  .  .  .  F
r2   .  S  .  S  F  F  F  .  .  .  F  S
r3   .  F  .  F  S  S  S  F  .  .  S  F
r4   F  F  .  S  F  F  F  S  .  S  S  S
r5   F  S  .  F  F  S  S  F  S  S  S  F
r6   F  S  .  F  F  F  S  S  S  F  S  S
r7   F  F  .  S  S  S  S  F  F  S  S  S
```

## Match 38: Savi5 (F) vs Cristi Schuszter (S) — Cristi = S
ID `09329215-7fc8-49d9-9bc9-08fb4843c446` · moves in history: 133 · special moves by Cristi: 3, opponent: 4
Logs: 19 warnings 'Memory usage approaching limit' 162-198MB; 2 timeouts SECOND_PLAYER (Cristi) -> column -1

```
  1: F5 S6 F6 S6 F6 S6 F6 S5 F5 S5
 11: F5 S5 F5 S6 F6 S10 F8 S10 F5 S4
 21: F10 S4 F4 S9 F4 S4 F7 S4 F4 S4
 31: F9 S7 F10 (skip S) F9 S1 F1 S1 F1 S7 F7*
 41: S7 F9 S6 F6 S6* F6 S5 F5 S6 F7
 51: S10 F3 S10 F10 S8 F8 S3 F1 S2 F1
 61: S9 F9 S2 F0* S2 F11 S9 F2 S8* F8
 71: S7 F7 S1 F1 S1 F8 S3 F11 S1 F8
 81: S8 F7 S7 F7* (skip S) F8 S8 F11 S6 F3 S3
 91: F3 S9 F11 S0 F0 S9 F0 S2 F3 S6
101: F3 S6 F2* S3 F7 S1 F1 S10 F0 S8
111: F11 S11 F1 S0 F0 S7 F3 S9 F3 S9*
121: F9 S11 F11 S10 F7 S0 F0 S9 F8 S8
131: F10 S2 F2
```
(the line number is the move number in the match history; a skipped move takes no number)
```
```
Win: Savi5 (F) with move #133 `F2`; line cells (row,col): [(0, 0), (1, 1), (2, 2), (3, 3), (4, 4), (5, 5), (6, 6), (7, 7)]

Final position:
```
     0  1  2  3  4  5  6  7  8  9 10 11
r0   F  F  .  F  S  F  S  F  S  S  F  F
r1   S  F  .  F  F  S  S  S  F  F  S  S
r2   F  S  F  S  S  S  S  F  F  S  S  S
r3   S  S  S  F  S  F  F  F  F  S  S  F
r4   F  F  F  F  F  S  S  F  F  S  F  F
r5   F  F  S  S  F  F  F  S  F  F  F  F
r6   F  F  S  S  S  S  F  S  S  F  S  F
r7   S  S  S  F  S  F  S  F  F  S  S  F
```

## Match 39: Savi5 (F) vs Cristi Schuszter (S) — Cristi = S
ID `bcf33874-57c4-4afb-9a5e-05843b533493` · moves in history: 124 · special moves by Cristi: 3, opponent: 4
Logs: 17 warnings 162-197MB; OOM at 200MB; 1 timeout SECOND_PLAYER (Cristi) -> column -1

```
  1: F5 S5 F5 S5 F5 S5 F5 S3 F5 S3
 11: F3 S3 F3 S3 F3 S3 F8 S8 F8 (skip S) F6
 21: S6 F6 S9 F9 S6 F4* S6 F4 S7 F9
 31: S2 F7 S7 F7 S2 F5 S4 F4 S2 F6
 41: S4 F2 S7 F9 S7 F1* S7 F8 S8* F8
 51: S7 F9 S8 F3 S9 F6 S6 F5 S3 F4
 61: S8 F8 S8 F0 S1 F2* S9 F7 S4* F7
 71: S9 F5 S7 F5 S5 F9 S1 F1 S3 F3
 81: S3 F3 S2 F4 S4 F4 S4 F2 S2 F11
 91: S0 F0 S10 F10 S2 F10 S11 F11 S2 F11
101: S1 F0 S0 F2 S1 F0 S1 F2 S11 F11
111: S0* F1 S0 F0* S0 F1 S11 F0 S11 F1
121: S0 F1 S10 F10
```
(the line number is the move number in the match history; a skipped move takes no number)
```
```
Win: Savi5 (F) with move #124 `F10`; line cells (row,col): [(2, 11), (3, 10), (4, 9), (5, 8), (6, 7), (7, 6)]

Final position:
```
     0  1  2  3  4  5  6  7  8  9 10 11
r0   S  F  F  F  S  S  S  S  S  F  .  S
r1   F  F  F  S  F  F  F  F  F  S  .  S
r2   S  F  S  F  S  F  F  F  S  S  .  F
r3   S  S  S  S  F  F  S  S  S  S  F  S
r4   F  S  S  F  S  S  S  S  F  F  S  F
r5   F  F  F  S  F  F  F  S  F  F  F  F
r6   S  S  S  F  S  S  S  F  S  F  F  S
r7   F  S  S  F  F  F  F  S  F  S  S  F
```

## Errors in all 46 matches

Raw lines from "Output Logs" (the "Error Logs" section is empty everywhere). The OOM line does not say whose bot it is; timeouts name the side (F/S).
Columns: number on the leaderboard page · Cristi's role · "Memory usage approaching limit" warnings (count, range) · 100 ms timeouts · OOM ("Memory limit exceeded") · result.

| No. | Cristi | Memory warnings | Timeouts | OOM, MB | Result |
|---|---|---|---|---|---|
| 0 | F | | | 219 | W |
| 1 | S | 2 × 178-198 | | 203 | W |
| 2 | F | | | 216 | W |
| 3 | S | 12 × 161-194 | | 200 | W |
| 4 | F | | | 211 | W |
| 5 | S | | | | W |
| 6 | F | 9 × 161-195 | | | W |
| 7 | F | | | 201 | W |
| 8 | S | | | 239 | W |
| 9 | F | 2 × 162-165 | | | W |
| 10 | S | 5 × 171-198 | **S** | 201 | W |
| 11 | F | | **F** | 221 | W |
| 12 | S | | | 258 | W |
| 13 | F | 5 × 166-183 | | | W |
| 14 | F | 1 × 199 | S | 211 | W |
| 15 | F | | S | 286 | W |
| 16 | F | | | 238 | W |
| 17 | S | | | 347 | W |
| 18 | F | 1 × 190 | | 203 | W |
| 19 | S | | FF | 246 | W |
| 20 | F | | S | 306 | W |
| 21 | S | | FF | 221 | W |
| 22 | S | | F | 205 | W |
| 23 | F | | SSSSS + «Column is full - SECOND_PLAYER» | 262 | W |
| 24 | F | 1 × 193 | | 213 | W |
| 25 | S | | **S** | 237 | W |
| 26 | F | | **F** | 281 | W |
| 27 | S | | | 341 | W |
| 28 | S | | | 281 | W |
| 29 | F | | | 322 | W |
| 30 | F | | | 235 | W |
| 31 | S | | **S** | 286 | W |
| 32 | F | | **F** | 344 | W |
| 33 | S | | | 256 | W |
| 34 | F | | **FF** | 298 | W |
| 35 | F | | **F** | 297 | W |
| 36 | S | | | 326 | W |
| 37 | F | | **F** | 295 | **L** |
| 38 | S | 19 × 162-198 | **SS** | | **L** |
| 39 | S | 17 × 163-197 | **S** | 200 | **L** |
| 40 | S | 2 × 161-164 | | | W |
| 41 | F | 7 × 170-197 | | 200 | W |
| 42 | S | | | 213 | W |
| 43 | F | 6 × 165-176 | | | W |
| 44 | S | 2 × 185-198 | | 206 | W |
| 45 | F | 2 × 163-167 | **F** | | W |

Bold marks timeouts of Cristi's bot: 12 matches, 15 skipped moves. In all three losses Cristi skipped a move.
