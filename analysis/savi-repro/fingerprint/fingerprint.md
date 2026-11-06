# Savi line: behavioural fingerprint from game logs

> **Correction (coordinator).** This report's blast counter is one too low after a blast, which is where "counter 9" comes from. With the engine's rule (reset to 0 on a blast, then +1 for that turn) the counter at a blast is never below 10 in all 1218 games. So its "ready" shares and "first ready turn" shares are off. Recomputed on 15x11, blasts / ready turns, then share of blasts on the first ready turn: Savi7 8.6% (82/956), 13%; Savi8 6.4% (70/1101), 11%; Savi9 4.6% (62/1356), 13%; 2.1 20.4% (142/695), 39%; 2.2 19.8% (128/645), 32%; 3.1 11.9% (108/910), 21%.


Source: `analysis/savi-repro/games.jsonl` (regenerated copy, 1218 decided games, every game has board and winner; 3 boards: 8x12 = 341 games, 15x11 = 552, 15x22 = 325; some board records are marked `inferred` by the exporter and are taken as given). Scripts: `qclib.py`, `analyze.py` (one Python process, about 5 s). The tables at the end are copied from `tables.md`, which `analyze.py` regenerates.

## Method notes

- Replay: blast geometry copied from `Board.blast` (nothing is placed; columns c-1..c+1 lose rows h-1..h+1 of the discs that exist, h = height of column c; discs above fall; a six of either colour after the move counts for the mover). **Replay failures: 0 of 1218** (every game ends in a six by the recorded winner on its last token, no earlier six, no drop into a full column).
- Lost turns are taken from breaks in the F/S alternation (F starts) and count as own turns for the blast counter. The smallest counter before any blast is 9 (counting lost turns; without them it would be 5, so lost turns do count). "Ready" below therefore means counter >= 9 before the move, i.e. the blast is the 10th own turn since the last blast. No blast happens earlier (0 violations) and none into a full column.
- Version labels merge name variants, but each variant lives on one board, so a (version, board) cell is one name: Savi5 = 8x12; Savi6 "Beat this" = 8x12 (57 games); Savi6 "Beat This" (capital T) = 15x22 only (25 games); "Savi 7 - The Prince" = 15x11 (57); "Savi7 - The Prince" = 15x22 (59); Savi8 and Savi9 = 15x11 only. So Savi7 -> Savi8 -> Savi9 can only be compared on 15x11, Savi5 -> Savi6 only on 8x12, and Savi6 -> Savi7 not at all (the Savi6 15x22 build is a different, weak sample).
- Ours = team "No beaches?" (2.1 on all three boards, 2.2 and 3.1 on 15x11 only). Cells with n < 30 are weak; every table carries n.
- Phases are by ply index (moves already made): 0-20, 21-40, 41+.
- "Repeated position" = board contents + side to move + blast-ready flag, mirror-folded, same version and board, earlier game in `fetchedAt` order. It does not contain the exact counter or time left, so some disagreement may come from state we cannot see.
- Windows: any 6-cell in-board segment through the new disc with no disc of the other colour, empty cells allowed (no gravity check). Drops only; blasts are excluded from the window columns.
- Tactics: "has a win" = a drop makes a six, or (if ready) a blast makes a six (blast tested exactly, with the sound prefilter from `blastMayWin`). "Opponent has one drop win" = exactly one column where the opponent's drop would make a six; it counts as dealt with when, after the mover's move, the opponent has no drop win left. Opponent blast wins are not considered. Positions where the mover has a win are not counted as block positions.
- Weak opponents (strict reading: overall win rate <= 20% and <= 20% in every pairing with >= 3 games): only `I'm too young to die` (0-34). Near-weak (overall <= 20% but one pairing above 20%): `FunkyMelodyBot` (25-149, beats Testing_v2 11-2) and `Testimine-M7` (2-89, beats sleep tight 2-5). The list of all bots is at the end.
- No Savi-vs-Savi games exist in the data, so the "vs Savi line" column is empty. "Other Savi-level bots" do not exist either: no non-Savi, non-ours bot has an overall win rate above 64%.

## What the data shows

1. Openings. Every Savi build opens in the centre column as F (col 5 on 15x11 and 8x12 after folding, col 10 on 15x22; n=30-38 per cell, 100%) and mostly answers in the same column as S. The dominant 15x11 line for Savi7, Savi8 and Savi9 is the same: F5 S5 five times, then F5/F4 with S4 (6-7 repeats each). Our 2.1 and 2.2 play the same first ten plies and deviate at ply 11 with S3 where Savi plays S4/S5. Our 3.1 as F opens col 4 (30 of 35), Savi never does. Our 2.1 on 8x12 as F opens at distance 3 from the edge in 32 of 39 games, Savi5/Savi6 open col 5 in 60 of 60. Distinct 20-ply lines per seat-game: Savi7/8/9 on 15x11 72-77% (41/57, 55/71, 47/63), ours 77-84%.
2. Repeat behaviour. Plies 0-20 on 15x11: a position seen before gets the same reply as the first earlier visit in 90% (Savi7, n=355), 91% (Savi8, n=436), 93% (Savi9, n=386); ours 85%, 85%, 91%. Plies 21-40: Savi7 81% (n=96), Savi8 78% (n=91), Savi9 87% (n=102); ours 64%, 74%, 84%. Never 100%, so the replies are not a fixed table. No sign of opponent dependence: same-reply rate when an earlier visit was against the same opponent vs only against other opponents: Savi9 91% vs 97% (n=324, 62), Savi8 89% vs 89%, Savi7 90% vs 96%.
3. Version carry-over. Early positions of Savi8 found in Savi7: 75% (of 756 moves), with a reply Savi7 also used in 95%. Savi9 in Savi8: 69% (of 659), 90%. Savi6 in Savi5 (8x12): 46% and 89%. 15x22 Savi6 -> Savi7: 32% and 70% (not a like-for-like pair).
4. Blasts. Savi blasts less often than our bots when ready. 15x11 share of ready turns: Savi7 8%, Savi8 6%, Savi9 4% (n=1009, 1165, 1413 ready turns) against ours 19% (2.1), 18% (2.2), 11% (3.1). Blasts per player-game on 15x11: Savi 1.44, 0.99, 0.98 vs ours 2.03, 1.91, 1.66. Counter at blast spreads from 9 to 17; blasting on the first ready turn (counter 9) is 7%, 7%, 11% of Savi blasts (6/82, 5/70, 7/62) vs 32% for ours 2.1 (45/142). Savi blasts late: share of blasts at ply 100 or later is 26%, 33%, 32% (Savi7/8/9). Only Savi7 on 15x22 (n=108 blasts) blasts about as often as ours there (14% vs 18% of ready turns). Savi6 "Beat This" on 15x22 never blasts in 25 games (101 ready turns, weak sample).
5. Blast purpose. Most blasts win nothing at once and save nothing, for both sides. 8x12: Savi5 0 of 63 win at once and Savi6 1 of 47, ours 2.1 11 of 73. 15x11: Savi 17/82, 13/70, 14/62 win at once (21%, 19%, 23%); ours 13/142, 13/128, 24/108 (9%, 10%, 22%). Removing an immediate opponent drop win: 1, 4, 2 for Savi (weak, n<30 each) and 3, 4, 2 for ours. Discs removed per blast (opponent / own): Savi 2.7-3.2 / 0.9-1.7, ours 2.5-3.0 / 1.1-1.5. After a blast that neither wins nor saves, Savi8 won the game in 53 of 53, Savi9 44 of 46, Savi7 60 of 64, ours 61-93 of 82-126 (55-74%); this follows the general win rate (Savi 96-100% on 15x11) and says nothing on the blast itself.
6. Move shape. On 15x11 Savi plays nearer the centre early: mean distance 0.82, 0.82, 0.87 columns in plies 0-20 (n=608-756) vs ours 0.99-1.02; later phases differ by 0.1 or less. Savi stacks on the opponent's last move 43%, 43%, 46% vs ours 40%, and on its own last move 39-40% vs 33%. On 8x12 it reverses (Savi 34% vs ours 37% on the opponent's last move; 27-29% vs 33% own). Window shares (make >=3/4/5, kill >=3/4/5) are within a few points of ours (Savi9 15x11: make 48/27/12%, kill 18/7/2%; ours 2.1: 49/30/14%, 19/9/3%). No clear difference.
7. Tactics. Taking an immediate win: 100% in every Savi and every our cell (n=53-69 per Savi cell, 7 in the Savi6 15x22 cell, includes blast-only wins, 11-15 per cell on 15x11 for Savi, 7-20 for ours). Dealing with a single drop threat: Savi5 30/30, Savi6 (8x12) 39/39, Savi7 (15x11) 42/42, Savi9 54/54, Savi8 52/53 (miss: 55fd4ee3 at ply 29, no timeouts), Savi7 15x22 48/51 (misses c3a9693e@172, 1db63ef0@50, 7b386928@120; in the last two Savi7 had timeouts). Ours: 2.1 67/69 (8x12), 65/67 (15x11), 26/27 (15x22); 2.2 67/70; 3.1 62/69 (six misses in three games at plies 25-28, one of those games has a timeout by our bot). The threat was removed by a blast instead of a drop in 5 of 30 (Savi5), 1 of 39 (Savi6 8x12), 1 of 42 (Savi7 15x11), 4 of 53 (Savi8), 3 of 54 (Savi9). Savi6 "Beat This" 15x22: dealt with 3 of 14, with 8 more positions that had two or more threats (weak sample; its 25 games are short and mostly lost, 7-18).
8. Opponents. Totals W-L: Savi5 53-5, Savi6 62-20, Savi7 108-8, Savi8 71-0, Savi9 61-2. Against our bots: Savi5 1-4 vs 2.1, Savi6 2-8 vs 2.1 (all 0-7 on 15x22 from the capital-T build), Savi7 7-6 vs 2.1, Savi8 7-0 vs 2.2, Savi9 3-2 vs 3.1 (all weak, n<=14). Share of own moves from games against our team: Savi5 20%, Savi6 11% (8x12) and 45% (15x22), Savi7 28% (15x11) and 35% (15x22), Savi8 26%, Savi9 20%. Moves against the strict weak bot: Savi8 90 (4%), others 0; against near-weak bots 0-9%. The remaining 55-83% are against mid-level bots that are neither ours nor weak.
9. Savi7 -> Savi8 -> Savi9 (15x11, n=57, 71, 63 games). Changed: blast share of ready turns 8% -> 6% -> 4%; blasts per game 1.44 -> 0.99 -> 0.98; games with a blast 60% -> 46% -> 52%; same-reply rate for repeated positions in plies 21-40 81% -> 78% -> 87% and in plies 0-20 90% -> 91% -> 93% (Savi9 the most repeatable); stacking on the opponent's last move 43% -> 43% -> 46%; results 56-1 -> 71-0 -> 61-2 (the Savi7 15x11 build is the one that lost 5 of 6 to our 2.1). Not changed: opening lines and first moves, distance from centre, window shares, tactics. Savi5 -> Savi6 on 8x12 (n=58, 57): blasts per game 1.09 -> 0.82, blast share of ready 11% -> 7%, rest equal.

## Could not determine

- Whether the blast threshold is exactly 9 prior own turns (smallest observed) or only a count that happens not to go lower; behaviour on the last empty cell (none seen).
- Time left per move. Repeat positions ignore it, so the 7-22% disagreement in repeated positions may come from time-dependent search, randomness, or the exact blast counter.
- Savi vs Savi behaviour (no such games) and draws (not exported). Whether Savi6 "Beat This" on 15x22 is the same program as Savi6 "Beat this" on 8x12.
- Cells with n < 30: the Savi6 15x22 row, all "removes opponent win" counts, blast-only wins per cell, opponent-split repeat rates in later phases, per-opponent W-L lines.

# Tables

Replay check: 1218 games, failures: 0.  Weak: ["I'm too young to die"].  Near-weak: ['FunkyMelodyBot', 'Testimine-M7'].  Ready = counter >= 9 before the move.

### Coverage

| version | board | games | own moves | lost turns |
|---|---|---|---|---|
| Savi5 | 8x12 | 58 | 1525 | 0 |
| Savi6 | 8x12 | 57 | 1539 | 1 |
| ours2.1 | 8x12 | 79 | 2129 | 3 |
| Savi7 | 15x11 | 57 | 2046 | 10 |
| Savi8 | 15x11 | 71 | 2241 | 7 |
| Savi9 | 15x11 | 63 | 2340 | 5 |
| ours2.1 | 15x11 | 70 | 2447 | 11 |
| ours2.2 | 15x11 | 67 | 2253 | 18 |
| ours3.1 | 15x11 | 65 | 2228 | 51 |
| Savi6 | 15x22 | 25 | 277 | 0 |
| Savi7 | 15x22 | 59 | 2047 | 9 |
| ours2.1 | 15x22 | 80 | 2081 | 23 |

### First own move per seat (column folded to distance from the nearer edge, 0 = edge; count after colon)

| version | board | seat | n | top first moves |
|---|---|---|---|---|
| Savi5 | 8x12 | F | 30 | 5:30 |
| Savi5 | 8x12 | S | 28 | 5:28 |
| Savi6 | 8x12 | F | 30 | 5:30 |
| Savi6 | 8x12 | S | 27 | 5:27 |
| ours2.1 | 8x12 | F | 39 | 3:32, 5:7 |
| ours2.1 | 8x12 | S | 40 | 5:29, 3:6, 4:3, 0:1, 2:1 |
| Savi7 | 15x11 | F | 32 | 5:32 |
| Savi7 | 15x11 | S | 25 | 5:20, 4:5 |
| Savi8 | 15x11 | F | 38 | 5:38 |
| Savi8 | 15x11 | S | 33 | 5:26, 4:6, 3:1 |
| Savi9 | 15x11 | F | 36 | 5:36 |
| Savi9 | 15x11 | S | 27 | 5:18, 4:9 |
| ours2.1 | 15x11 | F | 33 | 5:33 |
| ours2.1 | 15x11 | S | 37 | 5:28, 4:6, 3:3 |
| ours2.2 | 15x11 | F | 37 | 5:28, 4:9 |
| ours2.2 | 15x11 | S | 30 | 5:21, 4:5, 3:4 |
| ours3.1 | 15x11 | F | 35 | 4:30, 5:5 |
| ours3.1 | 15x11 | S | 30 | 5:17, 4:13 |
| Savi6 | 15x22 | F | 13 | 10:13 |
| Savi6 | 15x22 | S | 12 | 10:12 |
| Savi7 | 15x22 | F | 32 | 10:32 |
| Savi7 | 15x22 | S | 27 | 10:14, 5:7, 6:5, 7:1 |
| ours2.1 | 15x22 | F | 35 | 10:34, 6:1 |
| ours2.1 | 15x22 | S | 45 | 10:26, 9:9, 5:5, 6:5 |

### Distinct opening lines (mirror-folded, both seats' moves, first 20 plies; 'n' = Savi seat-games)

| version | board | n | distinct 6 plies | distinct 10 | distinct 20 | repeated 20-ply lines |
|---|---|---|---|---|---|---|
| Savi5 | 8x12 | 58 | 30 | 40 | 54 | F5 S5 F5 S5 F6 S7 F6 S6 F4 S6 F4 S5 F4 S4 F7 S6 F7 S7 F3 S7 (x3); F5 S7 F7 S7 F7 S7 F7 S7 F5 S7 F6 S4 F6 S4 F4 S4 F6 S4 F5 S4 (x2) |
| Savi6 | 8x12 | 57 | 26 | 34 | 46 | F5 S5 F5 S6 F5 S6 F5 S6 F5 S6 F6 S4 F4 S4 F6 S4 F4 S7 F5 S5 (x4); F5 S5 F5 S6 F5 S6 F5 S6 F5 S6 F6 S4 F6 S4 F4 S7 F6 S7 F5 S5 (x3) |
| ours2.1 | 8x12 | 79 | 37 | 54 | 70 | F3 S5 F5 S5 F5 S6 F5 S6 F6 S4 F5 S4 F6 S4 F4 S7 F4 S7 F6 S7 (x4); F5 S6 F5 S5 F6 S5 F6 S5 F6 S5 F6 S5 F5 S6 F4 S6 F4 S6 F4 S4 (x2) |
| Savi7 | 15x11 | 57 | 23 | 26 | 41 | F5 S5 F5 S5 F5 S5 F5 S5 F5 S5 F4 S5 F4 S4 F4 S4 F4 S4 F4 S4 (x6); F5 S5 F5 S5 F5 S5 F5 S5 F5 S5 F5 S4 F5 S4 F4 S4 F4 S4 F4 S4 (x6) |
| Savi8 | 15x11 | 71 | 32 | 40 | 55 | F5 S5 F5 S5 F5 S5 F5 S5 F5 S5 F5 S4 F5 S4 F4 S4 F4 S4 F4 S4 (x7); F5 S5 F5 S5 F5 S5 F5 S5 F5 S5 F4 S5 F4 S4 F4 S4 F4 S4 F4 S4 (x4) |
| Savi9 | 15x11 | 63 | 24 | 29 | 47 | F5 S5 F5 S5 F5 S5 F5 S5 F5 S5 F5 S4 F5 S4 F4 S4 F4 S4 F4 S4 (x7); F5 S5 F5 S5 F5 S5 F5 S5 F5 S5 F4 S5 F4 S4 F4 S6 F4 S4 F4 S4 (x4) |
| ours2.1 | 15x11 | 70 | 35 | 43 | 57 | F5 S5 F5 S5 F5 S5 F5 S5 F5 S5 F4 S3 F4 S4 F4 S4 F4 S4 F4 S4 (x6); F5 S5 F5 S5 F5 S5 F5 S5 F5 S5 F5 S4 F4 S4 F4 S4 F4 S4 F4 S4 (x4) |
| ours2.2 | 15x11 | 67 | 36 | 40 | 56 | F5 S5 F5 S5 F5 S5 F5 S5 F5 S5 F4 S3 F4 S4 F4 S4 F4 S4 F4 S4 (x5); F5 S0 F6 S0 F5 S0 F6 S0 F0 S0 F6 S0 F4 S0 F4 S0 F0 S0 F5 S0 (x4) |
| ours3.1 | 15x11 | 65 | 34 | 36 | 50 | F4 S5 F5 S5 F5 S5 F5 S5 F5 S5 F5 S6 F4 S6 F6 S6 F6 S6 F6 S6 (x5); F5 S5 F5 S5 F5 S5 F5 S5 F5 S5 F4 S5 F4 S3 F4 S4 F4 S4 F4 S5 (x5) |
| Savi6 | 15x22 | 25 | 18 | 21 | 25 | no repeat |
| Savi7 | 15x22 | 59 | 28 | 34 | 46 | Fa Sa Fa Sa Fa Sa Fa Sa Fa Sa Fa Sb Fa Sb Fb Sb Fb Sb Fb Sb (x6); F10 S10 F10 S10 F10 S10 F10 S10 F10 S10 Ff Se Ff S10 Ff Sf Ff Sf Ff Sf (x3) |
| ours2.1 | 15x22 | 80 | 48 | 60 | 73 | Fa Sa Fa Sa Fa Sa Fa Sa Fa Sa Fa Sb Fa Sb Fb Sb Fb Sb Fb Sb (x4); F10 S10 F10 S10 F10 S10 F10 S10 F10 S10 Ff Se Ff Sf Ff Sf Ff Sf Ff Sf (x2) |

### Repeated positions: same reply as an earlier game (position = board + side to move + blast-ready, mirror-folded, within version and board)

| version | board | plies 0-20 | 21-40 | 41+ |
|---|---|---|---|---|
| Savi5 | 8x12 | 255/605 seen; first-reply same 84%; any-prev same 88%; same-opp 82% (n=214), only-other-opp 83% (n=41) | 24/399 seen; first-reply same 75%; any-prev same 75%; same-opp 75% (n=24), only-other-opp - (n=0) | 1/521 seen; first-reply same 0%; any-prev same 0%; same-opp 0% (n=1), only-other-opp - (n=0) |
| Savi6 | 8x12 | 288/601 seen; first-reply same 84%; any-prev same 92%; same-opp 85% (n=197), only-other-opp 85% (n=91) | 55/450 seen; first-reply same 80%; any-prev same 84%; same-opp 69% (n=29), only-other-opp 88% (n=26) | 9/488 seen; first-reply same 100%; any-prev same 100%; same-opp 100% (n=3), only-other-opp 100% (n=6) |
| ours2.1 | 8x12 | 347/810 seen; first-reply same 78%; any-prev same 90%; same-opp 78% (n=220), only-other-opp 82% (n=127) | 23/521 seen; first-reply same 65%; any-prev same 74%; same-opp 73% (n=15), only-other-opp 62% (n=8) | 1/798 seen; first-reply same 0%; any-prev same 0%; same-opp 0% (n=1), only-other-opp - (n=0) |
| Savi7 | 15x11 | 355/608 seen; first-reply same 90%; any-prev same 94%; same-opp 90% (n=303), only-other-opp 96% (n=52) | 96/404 seen; first-reply same 81%; any-prev same 88%; same-opp 82% (n=80), only-other-opp 88% (n=16) | 18/1034 seen; first-reply same 89%; any-prev same 89%; same-opp 89% (n=18), only-other-opp - (n=0) |
| Savi8 | 15x11 | 436/756 seen; first-reply same 91%; any-prev same 93%; same-opp 89% (n=351), only-other-opp 89% (n=85) | 91/451 seen; first-reply same 78%; any-prev same 82%; same-opp 80% (n=71), only-other-opp 70% (n=20) | 20/1034 seen; first-reply same 90%; any-prev same 90%; same-opp 90% (n=20), only-other-opp - (n=0) |
| Savi9 | 15x11 | 386/659 seen; first-reply same 93%; any-prev same 95%; same-opp 91% (n=324), only-other-opp 97% (n=62) | 102/500 seen; first-reply same 87%; any-prev same 89%; same-opp 85% (n=82), only-other-opp 95% (n=20) | 43/1181 seen; first-reply same 93%; any-prev same 93%; same-opp 87% (n=15), only-other-opp 96% (n=28) |
| ours2.1 | 15x11 | 363/737 seen; first-reply same 85%; any-prev same 91%; same-opp 85% (n=284), only-other-opp 86% (n=79) | 47/450 seen; first-reply same 64%; any-prev same 72%; same-opp 68% (n=37), only-other-opp 40% (n=10) | 6/1260 seen; first-reply same 83%; any-prev same 83%; same-opp 83% (n=6), only-other-opp - (n=0) |
| ours2.2 | 15x11 | 335/704 seen; first-reply same 85%; any-prev same 91%; same-opp 84% (n=276), only-other-opp 73% (n=59) | 50/438 seen; first-reply same 74%; any-prev same 80%; same-opp 66% (n=32), only-other-opp 78% (n=18) | 5/1111 seen; first-reply same 60%; any-prev same 60%; same-opp 0% (n=1), only-other-opp 75% (n=4) |
| ours3.1 | 15x11 | 306/682 seen; first-reply same 91%; any-prev same 96%; same-opp 90% (n=247), only-other-opp 88% (n=59) | 85/431 seen; first-reply same 84%; any-prev same 87%; same-opp 82% (n=67), only-other-opp 94% (n=18) | 2/1115 seen; first-reply same 100%; any-prev same 100%; same-opp - (n=0), only-other-opp 100% (n=2) |
| Savi6 | 15x22 | 51/192 seen; first-reply same 100%; any-prev same 100%; same-opp 100% (n=24), only-other-opp 100% (n=27) | 0/61 seen; first-reply same -; any-prev same -; same-opp - (n=0), only-other-opp - (n=0) | 0/24 seen; first-reply same -; any-prev same -; same-opp - (n=0), only-other-opp - (n=0) |
| Savi7 | 15x22 | 281/587 seen; first-reply same 91%; any-prev same 93%; same-opp 90% (n=236), only-other-opp 98% (n=45) | 36/425 seen; first-reply same 78%; any-prev same 81%; same-opp 81% (n=36), only-other-opp - (n=0) | 6/1035 seen; first-reply same 83%; any-prev same 83%; same-opp 83% (n=6), only-other-opp - (n=0) |
| ours2.1 | 15x22 | 281/775 seen; first-reply same 70%; any-prev same 85%; same-opp 78% (n=199), only-other-opp 76% (n=82) | 10/375 seen; first-reply same 50%; any-prev same 50%; same-opp 50% (n=10), only-other-opp - (n=0) | 0/931 seen; first-reply same -; any-prev same -; same-opp - (n=0), only-other-opp - (n=0) |

### Carry-over of early (ply<=20) positions from older version X to newer Y (Y position seen in X; reply is one X also used)

| board | pair | Y early moves | position also in X | reply also used by X | distinct 8-ply lines of Y also in X |
|---|---|---|---|---|---|
| 8x12 | Savi5 -> Savi6 | 601 | 277 (46%) | 247 (89%) | 11/32 |
| 15x11 | Savi7 -> Savi8 | 756 | 570 (75%) | 539 (95%) | 15/36 |
| 15x11 | Savi8 -> Savi9 | 659 | 455 (69%) | 409 (90%) | 12/27 |
| 15x22 | Savi6 -> Savi7 | 587 | 188 (32%) | 131 (70%) | 2/31 |

### Blasts: counts, own-turn counter when blasting, readiness use, ply of blast (starting ply of 20-ply bucket)

| version | board | player-games | blasts | per game | games with >=1 | ready turns | blast share of ready | counter at blast | ply buckets |
|---|---|---|---|---|---|---|---|---|---|
| Savi5 | 8x12 | 58 | 63 | 1.09 | 59% | 559 | 11% | 9:6, 10:7, 11:5, 12:5, 13:9, 14:10, 15:3, 16:5 | 20-39:28, 40-59:12, 60-79:10, 80-99:8, 100+:5 |
| Savi6 | 8x12 | 57 | 47 | 0.82 | 51% | 699 | 7% | 9:6, 10:6, 11:4, 12:5, 13:4, 14:3, 16:4, 17:1 | 20-39:23, 40-59:12, 60-79:4, 80-99:6, 100+:2 |
| ours2.1 | 8x12 | 79 | 73 | 0.92 | 59% | 1000 | 7% | 9:6, 10:9, 11:4, 12:5, 13:7, 14:5, 15:4, 16:4 | 20-39:29, 40-59:12, 60-79:14, 80-99:9, 100+:9 |
| Savi7 | 15x11 | 57 | 82 | 1.44 | 60% | 1009 | 8% | 9:6, 10:5, 11:7, 12:6, 13:3, 14:6, 15:9, 16:3 | 0-19:2, 20-39:25, 40-59:11, 60-79:10, 80-99:13, 100+:21 |
| Savi8 | 15x11 | 71 | 70 | 0.99 | 46% | 1165 | 6% | 9:5, 10:4, 11:3, 12:7, 13:3, 14:1, 15:7, 16:4 | 0-19:1, 20-39:21, 40-59:8, 60-79:9, 80-99:8, 100+:23 |
| Savi9 | 15x11 | 63 | 62 | 0.98 | 52% | 1413 | 4% | 9:7, 10:3, 11:2, 12:1, 13:6, 14:1, 15:9, 16:1 | 20-39:16, 40-59:9, 60-79:9, 80-99:8, 100+:20 |
| ours2.1 | 15x11 | 70 | 142 | 2.03 | 64% | 757 | 19% | 9:45, 10:22, 11:13, 12:17, 13:11, 14:3, 15:6, 16:4 | 0-19:2, 20-39:39, 40-59:24, 60-79:22, 80-99:16, 100+:39 |
| ours2.2 | 15x11 | 67 | 128 | 1.91 | 58% | 709 | 18% | 9:35, 10:18, 11:8, 12:22, 13:9, 14:5, 15:9, 16:3 | 0-19:1, 20-39:37, 40-59:19, 60-79:22, 80-99:16, 100+:33 |
| ours3.1 | 15x11 | 65 | 108 | 1.66 | 62% | 972 | 11% | 9:21, 10:12, 11:10, 12:4, 13:9, 14:4, 15:10, 16:5 | 0-19:1, 20-39:22, 40-59:19, 60-79:19, 80-99:10, 100+:37 |
| Savi6 | 15x22 | 25 | 0 | 0.00 | 0% | 101 | 0% |  |  |
| Savi7 | 15x22 | 59 | 108 | 1.83 | 59% | 747 | 14% | 9:27, 10:9, 11:7, 12:11, 13:5, 14:17, 15:4, 16:7 | 20-39:28, 40-59:16, 60-79:13, 80-99:9, 100+:42 |
| ours2.1 | 15x22 | 80 | 106 | 1.32 | 44% | 596 | 18% | 9:36, 10:16, 11:9, 12:9, 13:7, 14:10, 15:4, 16:2 | 0-19:1, 20-39:31, 40-59:16, 60-79:12, 80-99:8, 100+:38 |

### Blast purpose: wins at once / removes the opponent's immediate drop win / neither; mean discs removed per blast (own, opponent); game won after a 'neither' blast

| version | board | blasts | win now | removes opp win | neither | own removed | opp removed | 'neither' blasts then won game | blast with counter<9 | blast in full column |
|---|---|---|---|---|---|---|---|---|---|---|
| Savi5 | 8x12 | 63 | 0 | 5 | 58 | 1.1 | 2.7 | 45/58 (78%) | 0 | 0 |
| Savi6 | 8x12 | 47 | 1 | 1 | 45 | 0.9 | 2.7 | 42/45 (93%) | 0 | 0 |
| ours2.1 | 8x12 | 73 | 11 | 4 | 58 | 1.2 | 3.0 | 51/58 (88%) | 0 | 0 |
| Savi7 | 15x11 | 82 | 17 | 1 | 64 | 1.5 | 2.8 | 60/64 (94%) | 0 | 0 |
| Savi8 | 15x11 | 70 | 13 | 4 | 53 | 1.3 | 3.2 | 53/53 (100%) | 0 | 0 |
| Savi9 | 15x11 | 62 | 14 | 2 | 46 | 1.7 | 3.1 | 44/46 (96%) | 0 | 0 |
| ours2.1 | 15x11 | 142 | 13 | 3 | 126 | 1.2 | 2.6 | 93/126 (74%) | 0 | 0 |
| ours2.2 | 15x11 | 128 | 13 | 4 | 111 | 1.2 | 2.5 | 61/111 (55%) | 0 | 0 |
| ours3.1 | 15x11 | 108 | 24 | 2 | 82 | 1.5 | 2.9 | 60/82 (73%) | 0 | 0 |
| Savi6 | 15x22 | 0 | 0 | 0 | 0 | - | - | 0/0 (-) | 0 | 0 |
| Savi7 | 15x22 | 108 | 9 | 4 | 95 | 1.2 | 2.8 | 58/95 (61%) | 0 | 0 |
| ours2.1 | 15x22 | 106 | 7 | 4 | 95 | 1.1 | 2.7 | 74/95 (78%) | 0 | 0 |

### Column distance from centre by ply phase (mean, columns), and stacking share (same column as opponent's last / own last move)

| version | board | 0-20 | 21-40 | 41+ | on opp last | on own last |
|---|---|---|---|---|---|---|
| Savi5 | 8x12 | 1.21 (n=605) | 2.20 (n=399) | 3.46 (n=521) | 34% (n=1494) | 27% (n=1467) |
| Savi6 | 8x12 | 1.08 (n=601) | 2.01 (n=450) | 3.25 (n=488) | 34% (n=1508) | 29% (n=1482) |
| ours2.1 | 8x12 | 1.38 (n=810) | 2.02 (n=521) | 3.27 (n=798) | 37% (n=2087) | 33% (n=2050) |
| Savi7 | 15x11 | 0.82 (n=608) | 1.36 (n=404) | 2.58 (n=1034) | 43% (n=1940) | 39% (n=1989) |
| Savi8 | 15x11 | 0.82 (n=756) | 1.43 (n=451) | 2.60 (n=1034) | 43% (n=2054) | 39% (n=2170) |
| Savi9 | 15x11 | 0.87 (n=659) | 1.56 (n=500) | 2.60 (n=1181) | 46% (n=2303) | 40% (n=2277) |
| ours2.1 | 15x11 | 1.00 (n=737) | 1.56 (n=450) | 2.66 (n=1260) | 40% (n=2297) | 33% (n=2377) |
| ours2.2 | 15x11 | 0.99 (n=704) | 1.55 (n=438) | 2.55 (n=1111) | 40% (n=2140) | 33% (n=2186) |
| ours3.1 | 15x11 | 1.02 (n=682) | 1.49 (n=431) | 2.72 (n=1115) | 40% (n=2149) | 33% (n=2163) |
| Savi6 | 15x22 | 0.50 (n=192) | 0.61 (n=61) | 1.33 (n=24) | 32% (n=252) | 92% (n=252) |
| Savi7 | 15x22 | 2.95 (n=587) | 3.69 (n=425) | 3.66 (n=1035) | 37% (n=1987) | 30% (n=1988) |
| ours2.1 | 15x22 | 2.29 (n=775) | 2.99 (n=375) | 3.70 (n=931) | 36% (n=2000) | 27% (n=2001) |

### Drop moves only: share making a window (6 cells, no opponent disc, through the new disc) with >=3/4/5 own discs; share killing an opponent window (no own disc before) with >=3/4/5 opponent discs

| version | board | drops | make>=3 | make>=4 | make>=5 | kill>=3 | kill>=4 | kill>=5 |
|---|---|---|---|---|---|---|---|---|
| Savi5 | 8x12 | 1462 | 49% | 29% | 13% | 17% | 8% | 2% |
| Savi6 | 8x12 | 1492 | 53% | 33% | 16% | 15% | 8% | 3% |
| ours2.1 | 8x12 | 2056 | 50% | 32% | 16% | 19% | 10% | 3% |
| Savi7 | 15x11 | 1964 | 46% | 26% | 11% | 16% | 7% | 2% |
| Savi8 | 15x11 | 2171 | 48% | 28% | 13% | 15% | 7% | 2% |
| Savi9 | 15x11 | 2278 | 48% | 27% | 12% | 18% | 7% | 2% |
| ours2.1 | 15x11 | 2305 | 49% | 30% | 14% | 19% | 9% | 3% |
| ours2.2 | 15x11 | 2125 | 47% | 27% | 12% | 19% | 10% | 3% |
| ours3.1 | 15x11 | 2120 | 45% | 25% | 11% | 16% | 7% | 3% |
| Savi6 | 15x22 | 277 | 32% | 18% | 10% | 4% | 3% | 1% |
| Savi7 | 15x22 | 1939 | 52% | 27% | 12% | 18% | 8% | 2% |
| ours2.1 | 15x22 | 1975 | 56% | 32% | 14% | 18% | 7% | 1% |

### Immediate win available (drop / blast-only): taken?

| version | board | positions | drop/blast-only | taken | missed (game@ply) |
|---|---|---|---|---|---|
| Savi5 | 8x12 | 53 | 53/0 | 53 (100%) | - |
| Savi6 | 8x12 | 55 | 54/1 | 55 (100%) | - |
| ours2.1 | 8x12 | 76 | 65/11 | 76 (100%) | - |
| Savi7 | 15x11 | 56 | 39/17 | 56 (100%) | - |
| Savi8 | 15x11 | 71 | 58/13 | 71 (100%) | - |
| Savi9 | 15x11 | 61 | 47/14 | 61 (100%) | - |
| ours2.1 | 15x11 | 65 | 52/13 | 65 (100%) | - |
| ours2.2 | 15x11 | 58 | 45/13 | 58 (100%) | - |
| ours3.1 | 15x11 | 59 | 35/24 | 59 (100%) | - |
| Savi6 | 15x22 | 7 | 7/0 | 7 (100%) | - |
| Savi7 | 15x22 | 52 | 43/9 | 52 (100%) | - |
| ours2.1 | 15x22 | 78 | 71/7 | 78 (100%) | - |

### Opponent has exactly one immediate drop win and mover has none: dealt with? (drop in that column / blast / other)

| version | board | positions | drop/blast/other | threat gone after move | positions with 2+ opp wins (not scored) | failures (game@ply) |
|---|---|---|---|---|---|---|
| Savi5 | 8x12 | 30 | 25/5/0 | 30 (100%) | 0 | - |
| Savi6 | 8x12 | 39 | 38/1/0 | 39 (100%) | 0 | - |
| ours2.1 | 8x12 | 69 | 63/4/0 | 67 (97%) | 0 | e9582887@134, 440af18b@120 |
| Savi7 | 15x11 | 42 | 41/1/0 | 42 (100%) | 0 | - |
| Savi8 | 15x11 | 53 | 48/4/0 | 52 (98%) | 0 | 55fd4ee3@29 |
| Savi9 | 15x11 | 53 | 51/2/0 | 53 (100%) | 0 | - |
| ours2.1 | 15x11 | 67 | 62/3/0 | 65 (97%) | 0 | 96321eb5@151, c4ca211c@140 |
| ours2.2 | 15x11 | 70 | 64/3/0 | 67 (96%) | 3 | 62931620@174, 86d1ed47@110, b1cb6881@155 |
| ours3.1 | 15x11 | 69 | 60/2/0 | 62 (90%) | 0 | 147174c3@25, 147174c3@27, bb907d77@25, ecf0d7b1@25, ecf0d7b1@27, ecf0d7b1@28 |
| Savi6 | 15x22 | 14 | 3/0/0 | 3 (21%) | 8 | 056aa25a@49, 08ca405f@14, 1cf12218@9, 28a240bb@9, 3b0cd408@62, 5f5f8466@9 |
| Savi7 | 15x22 | 51 | 45/3/0 | 48 (94%) | 3 | c3a9693e@172, 1db63ef0@50, 7b386928@120 |
| ours2.1 | 15x22 | 27 | 23/3/0 | 26 (96%) | 1 | 0afc31d4@167 |

### Opponents per version (W-L of that version, all boards)

| version | W-L | opponents (W-L) |
|---|---|---|
| Savi5 | 53-5 | Hurt me plenty 7-0; Testimine-M7 7-0; AntiStrat_FINAL 7-0; sleep tight 7-0; BeetaSaag 7-0; \u006c\u0061\u006c\2 7-0; Strange Quark 1 6-0; Testing_v2 4-1; Savi5 beater 2.1 1-4 |
| Savi6 | 62-20 | Savi5 beater 2.1 2-8; Hurt me plenty 7-2; BeetaSaag 7-2; Strange Quark 1 8-1; specialmovedestroyer 7-2; Testimine-M7 7-0; Testing_v2 6-1; good night 7-0; lalalabot 7-0; HereWeGo 3-1; Back To Basics 1-1; lalalalabot 0-2 |
| Savi7 | 108-8 | Hurt me plenty 14-0; Strange Quark 1 14-0; specialmovedestroyer 13-1; lalalalabot 13-1; Savi5 beater 2.1 7-6; BeetaSaag 13-0; Testing_v2 12-0; HereWeGoAgain 12-0; Back To Basics 10-0 |
| Savi8 | 71-0 | Testing_v2 7-0; Hurt me plenty 7-0; BeetaSaag 7-0; Strange Quark 1 7-0; specialmovedestroyer 7-0; FunkyMelodyBot 7-0; I'm too young to die 7-0; Savi beater 2.2 7-0; HereWeGoAgain 6-0; lalalalabot 6-0; Back To Basics 3-0 |
| Savi9 | 61-2 | BeetaSaag 7-0; FunkyMelodyBot 7-0; lalalaimprovedbot 7-0; specialmovedestroyer 7-0; HereWeGoAgain2.0 7-0; Hurt me plenty 7-0; Strange Quark 1 6-0; Testing_v2 5-0; Savi beater 3.1 3-2; Back To Basics 5-0 |
| ours2.1 | 219-10 | BeetaSaag 21-0; specialmovedestroyer 21-0; Hurt me plenty 20-0; Strange Quark 1 20-0; Testing_v2 17-0; HereWeGoAgain 14-0; FunkyMelodyBot 14-0; lalalalabot 13-0; Back To Basics 10-0; AntiStrat_FINAL 7-0; Testimine-M7 7-0; good night 7-0; lalalabot 7-0; HereWeGo 7-0; Savi6 - "Beat This" 7-0; Savi7 - "The Prince" 5-2; sleep tight 6-0; \u006c\u0061\u006c\2 6-0; Savi 7 - "The Prince 1-5; Savi5 4-1; I'm too young to die 4-0; Savi6 - "Beat this" 1-2 |
| ours2.2 | 58-9 | BeetaSaag 7-0; Strange Quark 1 7-0; Testing_v2 7-0; HereWeGoAgain 7-0; FunkyMelodyBot 7-0; lalalalabot 5-2; specialmovedestroyer 7-0; Savi8 - "The King" 0-7; Hurt me plenty 6-0; Back To Basics 5-0 |
| ours3.1 | 59-6 | BeetaSaag 7-0; Back To Basics 7-0; Hurt me plenty 6-1; HereWeGoAgain2.0 7-0; FunkyMelodyBot 7-0; specialmovedestroyer 7-0; lalalaimprovedbot 5-2; Testing_v2 6-0; Savi9 - "The King" 2-3; Strange Quark 1 5-0 |

### Own moves by opponent class

| version | board | moves | vs our team | vs Savi line | vs weak | vs near-weak | vs other |
|---|---|---|---|---|---|---|---|
| Savi5 | 8x12 | 1525 | 302 (20%) | 0 (0%) | 0 (0%) | 136 (9%) | 1087 (71%) |
| Savi6 | 8x12 | 1539 | 173 (11%) | 0 (0%) | 0 (0%) | 93 (6%) | 1273 (83%) |
| Savi6 | 15x22 | 277 | 126 (45%) | 0 (0%) | 0 (0%) | 0 (0%) | 151 (55%) |
| Savi7 | 15x11 | 2046 | 570 (28%) | 0 (0%) | 0 (0%) | 0 (0%) | 1476 (72%) |
| Savi7 | 15x22 | 2047 | 709 (35%) | 0 (0%) | 0 (0%) | 0 (0%) | 1338 (65%) |
| Savi8 | 15x11 | 2241 | 580 (26%) | 0 (0%) | 90 (4%) | 135 (6%) | 1436 (64%) |
| Savi9 | 15x11 | 2340 | 467 (20%) | 0 (0%) | 0 (0%) | 107 (5%) | 1766 (75%) |
| ours2.1 | 8x12 | 2129 | 0 (0%) | 476 (22%) | 0 (0%) | 97 (5%) | 1556 (73%) |
| ours2.1 | 15x11 | 2447 | 0 (0%) | 568 (23%) | 59 (2%) | 125 (5%) | 1695 (69%) |
| ours2.1 | 15x22 | 2081 | 0 (0%) | 835 (40%) | 0 (0%) | 107 (5%) | 1139 (55%) |
| ours2.2 | 15x11 | 2253 | 0 (0%) | 573 (25%) | 0 (0%) | 140 (6%) | 1540 (68%) |
| ours3.1 | 15x11 | 2228 | 0 (0%) | 458 (21%) | 0 (0%) | 89 (4%) | 1681 (75%) |

### Weak-bot test: all bots with >=10 games, overall win rate and whether every pairing with >=3 games has <=20% wins (weak = overall <=20% and every such pairing <=20%)

| bot | team | W | L | win% | every pairing<=20% | weak |
|---|---|---|---|---|---|---|
| I'm too young to die | cristi | 0 | 34 | 0% | True | True |
| Testimine-M7 | WileyQuarks | 2 | 89 | 2% | False | False |
| FunkyMelodyBot | FunkyMelody | 25 | 149 | 14% | False | False |
| Back To Basics | Shalomtehnikud | 24 | 75 | 24% | False | False |
| Strange Quark 1 | Strange Quark | 39 | 118 | 25% | False | False |
| sleep tight | KolmApelsini | 9 | 24 | 27% | False | False |
| HereWeGo | WileyQuarks | 13 | 35 | 27% | False | False |
| AntiStrat_FINAL | Shalomtehnikud | 10 | 26 | 28% | False | False |
| Savi6 - "Beat This" | Savi | 7 | 18 | 28% | False | False |
| Testing_v2 | LinMacWin | 51 | 120 | 30% | False | False |
| HereWeGoAgain | WileyQuarks | 32 | 75 | 30% | False | False |
| HereWeGoAgain2.0 | WileyQuarks | 11 | 24 | 31% | False | False |
| good night | KolmApelsini | 11 | 22 | 33% | False | False |
| Hurt me plenty | cristi | 58 | 103 | 36% | False | False |
| lalalabot | \u006c\u0061\u006c\u0061\u006c\u0061\u0062\u006f\u0074 | 14 | 20 | 41% | False | False |
| BeetaSaag | Saag1 | 67 | 92 | 42% | False | False |
| specialmovedestroyer | KolmApelsini | 65 | 79 | 45% | False | False |
| \u006c\u0061\u006c\2 | \u006c\u0061\u006c\u0061\u006c\u0061\u0062\u006f\u0074 | 17 | 19 | 47% | False | False |
| lalalalabot | \u006c\u0061\u006c\u0061\u006c\u0061\u0062\u006f\u0074 | 56 | 41 | 58% | False | False |
| lalalaimprovedbot | \u006c\u0061\u006c\u0061\u006c\u0061\u0062\u006f\u0074 | 23 | 13 | 64% | False | False |
| Savi beater 2.2 | No beaches? | 58 | 9 | 87% | False | False |
| Savi7 - "The Prince" | Savi | 52 | 7 | 88% | False | False |
| Savi5 | Savi | 53 | 5 | 91% | False | False |
| Savi beater 3.1 | No beaches? | 59 | 6 | 91% | False | False |
| Savi5 beater 2.1 | No beaches? | 219 | 10 | 96% | False | False |
| Savi6 - "Beat this" | Savi | 55 | 2 | 96% | False | False |
| Savi9 - "The King" | Savi | 61 | 2 | 97% | False | False |
| Savi 7 - "The Prince | Savi | 56 | 1 | 98% | False | False |
| Savi8 - "The King" | Savi | 71 | 0 | 100% | False | False |

### Results per version and board

| version | board | games | W-L |
|---|---|---|---|
| Savi5 | 8x12 | 58 | 53-5 |
| Savi6 | 8x12 | 57 | 55-2 |
| Savi6 | 15x22 | 25 | 7-18 |
| Savi7 | 15x11 | 57 | 56-1 |
| Savi7 | 15x22 | 59 | 52-7 |
| Savi8 | 15x11 | 71 | 71-0 |
| Savi9 | 15x11 | 63 | 61-2 |
| ours2.1 | 8x12 | 79 | 76-3 |
| ours2.1 | 15x11 | 70 | 65-5 |
| ours2.1 | 15x22 | 80 | 78-2 |
| ours2.2 | 15x11 | 67 | 58-9 |
| ours3.1 | 15x11 | 65 | 59-6 |