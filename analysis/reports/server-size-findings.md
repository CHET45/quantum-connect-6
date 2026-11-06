# Server games by board size: findings (export 06, 2026-11-03)

Data: `analysis/server-exports/export-06-8a0e07e7.zip` (extracted to the agent scratchpad, never run), `data/matches.jsonl`,
1054 game ids, 1020 unique after dropping 34 identical replays (same two bots, same moves). Draws are not exported, so every
W/L figure excludes draws. Tables behind every number: `server-analysis-by-size.md`, `our-losses-by-size.md`,
`loss-review-sizes.md`, `server-games-report-4.md` (Java tool, all sizes mixed).

## 1. Replay, size-aware (task 1)

- Board size per game = the `board` field of `matches.jsonl` (rows x cols; 522 unique games read from the page, 498
  inferred by the exporter from the time: 341 x 8x12 before 20:06, 157 x 15x22 20:31-20:42). The snapshots' own `board`
  field agrees with `matches.jsonl` for all 554 ids that appear in both. Move strings: base-36 column digit.
- Blast, checked against `Board.blast`: the disc is placed at the landing cell (column height h) and vanishes; rows h-1..h+1
  of columns c-1..c+1 are cleared and the discs above fall. So the 3x3 block is centred on the landing cell, NOT on the top
  disc: column c loses exactly its top disc (row h-1), the two neighbours lose up to three discs. After a blast any six of
  either colour touching those columns wins for the mover. Cooldown: a blast needs counter >= 10, every own turn counts
  including skipped ones (gap between two blasts of a side: 10 own turns, earliest blast = 11th own turn). With skipped
  turns not counting, 9 of 1054 games (9 of 1020 unique) stop replaying, so skipped turns do count.
- Result: **1020 of 1020 unique games replay consistently on their recorded size** (recorded winner, line exactly at
  `win_at`, no earlier line, no disc into a full column, no early blast): 8x12 341/341, 15x22 323/323 (166 page + 157
  inferred), 15x11 356/356 (all page). The old hex/8x12-only `ServerGames` reported 610 of 1020 consistent.
- Caveat: consistency does not identify the size. A game that fits a smaller board also fits any larger one that contains
  it (all 8x12 and 15x11 games replay on 15x22; 167 of the 356 15x11 games and 102 of the 323 15x22 games also replay on
  8x12). The size therefore comes from the field, and the 498 inferred sizes rest on timestamps.
- Tools: Java `ServerGames` and `GameReview` (edited in place, not committed) and the Python replayer `qc_replay.py`. The
  Python replayer and `Board.java` give identical boards and win flags on 600 random games (all four sizes, 20% blasts) and
  identical results on all 1020 games (blast-decided games 88, blasts 1115 in 411 games, F wins 544).

## 2. Results by size (task 2)

| Size | Unique games | F win rate | F rate, games without timeout | Decided by blast | Plies min/median/p90/max |
|---|---|---|---|---|---|
| 8x12 | 341 | 54.5% | 55.9% | 15 (4.4%) | 11/31/90/136 |
| 15x22 | 323 | 52.3% | 52.1% | 17 (5.3%) | 6/17/68/341 |
| 15x11 | 356 | 53.1% | 48.1% | 56 (15.7%) | 6/31/118/242 |
| all | 1020 | 53.3% (CI 50.3-56.4) | 52.3% | 88 (8.6%) | 6/27.5/95/341 |

Between bots of no team role (neither Savi nor ours) F wins 58.1% (8x12, n=155), 53.2% (15x22, n=173), 51.0% (15x11, n=104):
a modest first-move edge (borderline at 8x12, within noise on the other two). 15x11 has far more blast endings than the other sizes.

W/L of the leading bots (full per-bot tables and head-to-head matrices per size are in `server-analysis-by-size.md`):

- 8x12: ours 2.1 76/3 (4-1 against Savi5, 1-2 against Savi6), Savi6 55/2, Savi5 53/5, Testing_v2 45/12, the rest below 40%.
- 15x22: ours 2.1 78/2 (5-2 against Savi7), Savi7 52/7, lalalalabot 32/14, specialmovedestroyer 29/14.
- 15x11: **Savi8 71/0**, Savi7b ("Savi 7 - The Prince", the second id) 56/1, ours 2.1 65/5, ours 2.2 58/9 (0-7 against Savi8,
  2-5 against lalalalabot); against Savi7b our 2.1 is 1-5.

## 3. What the Savi line does (task 3)

(Statistics over all games of each bot; "ours" = both of our versions together where stated.)

- **Openings are not special.** Everybody strong opens in the centre. On 15x11 Savi8 plays 5 on its first move as F in 100%
  and the next moves 5/6; on 8x12 Savi5/6 play 5 (F) and 5/6 (S); on 15x22 Savi7 plays `a` (column 10) as F. Our 2.1 opens
  with column 3 in 82% of its 8x12 games as F (and wins 37-2 as F there). In the first 24 plies the Savi bots play into the column
  the opponent just used 34-50% of the time, we 35-42%.
- **A shared start on 15x11:** 23 of the 356 games begin `F5 S5` six times (the centre column is filled alternately for 12
  plies; 79 begin with at least four such pairs), among them 11 of the 13 games Savi8/Savi7b played against us. After it the
  paths diverge: when we are F and play 13.F3, Savi answers 14.S5: **0-5 for us** (4 x Savi8,
  1 x Savi7b); when we are S and Savi plays 13.F5, we answer 14.S4: **0-5** (3 x Savi8, 2 x Savi7b). Savi as F in the same
  start: F5S5 7-0, F5S4 5-0; Savi as S against 13.F3: 5-0. 10 of our 12 losses on 15x11 to Savi8/Savi7b go through these two
  nodes. The 2000 ms search at ply 13 prefers 5 (Savi's own move) to our 3 (score -0.10 at depth 8; the 45 ms search also
  says 5 in 3 of 4 games).
- **Blasts.** Per game Savi8 blasts 0.99 times (37% of the blasts the cooldown allows), Savi7b 1.44, Savi7 1.83, Savi5 1.09,
  Savi6 0.82; we blast 2.0 times per game on 15x11 (68% of the allowed ones; 0.92 on 8x12, 1.32 on 15x22). Savi waits: only
  11-13% of Savi8/Savi7b blasts are played on the first possible turn (ours 32-39%), the median own turn of the first blast is
  17 for Savi8 and 15.5 for Savi7b (ours 12-13). Savi blasts hit harder: Savi8 removes 3.2 opponent and 1.3 own discs per blast, 24% of its blasts remove
  six or more discs (ours 2.5-2.6 / 1.2, 8-15%). **39% of Savi8's blasts win the game or leave an immediate winning drop**
  (13 wins + 14 threats of 70) against 20% / 17% for our 2.1 / 2.2 (13+15 of 142, 13+9 of 128). Savi7b: 26%, Savi7 on
  15x22: 18% (ours 10%). On 8x12 our 2.1 converts best (36%, Savi6 30%, Savi5 14%). Savi8 wins 13 of its 71 games by a blast (18%), Savi7b 17 of 56 (30%); the
  winning line contained opponent discs in 8 of Savi8's 13 (own colour only 5) and 7 of Savi7b's 17 (own only 10), so the
  colour-blind rule is used, but we use it more (ours: 10 of 13 blast wins on 15x11 for each version used the opponent's colour only).
- **Speed.** Against bots outside the top group the Savi bots win in a median of 33-45 plies (clean games, no timeouts; p90
  71-107); our versions win in a median of 30-42 plies (all wins). Against us the games last much longer: Savi8 on 15x11 112-208 plies (median
  157, 7 wins), Savi7b 142-242 (median 182, 5 wins), Savi7 on 15x22 169 and 341, Savi5/6 on 8x12 115-136.
- **How their sixes arrive** (six wins, share of 127 Savi wins on 15x11): double threat 25%, single threat not blocked 24%,
  our move opened the winning cell 13%, blast 23%, we skipped the last turn 9%, stacked threat (we block, the cell above
  wins) 6%. Their winning lines are mixed: horizontal 42%, vertical 30%, diagonal 27% (the other bots win vertically
  50-75%, i.e. with stacked columns; we win horizontally on the floor on 15x22, 87%).

## 4. Our losses (task 4)

19 losses in the export (all versions, no duplicates): 8x12 three (Savi5, Savi6 x2, all with 0 timeouts of ours),
15x22 two (Savi7: 169 plies S, 341 plies F by blast), 15x11 fourteen (Savi8 x7, Savi7b x5, lalalalabot x2). The full table
with side, plies, blast/six, timeouts, finish category and the last plies is `our-losses-by-size.md`. Our timeouts in the
15x11 losses: Savi8 games 2.2 had 0, 2, 1, 0, 1, 0, 1 timeouts; four of the seven contain a skipped turn at turn 16, 27,
34, 45 (and 90), none of them in the last 50 plies.

### Search review of 13 losses (hand evaluation, one thread, about 22 minutes)

`GameReview` (made size-aware) searched our moves with 45 ms and 2000 ms, scores from our view (digest and per-game rows:
`loss-review-sizes.md`, raw output `loss-review-sizes-raw.md` / `-raw2.md`). Reviewed: all 7 losses to Savi8 on 15x11 (every
ply of ours), the 15x22 loss to Savi7 (S, 169 plies, from ply 21) and 5 losses to Savi7b on 15x11 (from ply 21), 1035 searched
moves of ours in all (forced-move positions, depth 1-2, are not counted). Not reviewed: the 341-ply loss to Savi7 on 15x22, the two losses to lalalalabot, the three on 8x12.

- **There is no single blunder; the position declines from the opening on.** The deep score is about +0.2..0 until ply 21-31,
  then falls: first at -1.00 or worse at ply 34-74 (median 46), and it stays there from ply 44-185 on (median ply 86, a
  median 64 plies before the end; in 8 of 13 games more than 50 plies before the end). The 45 ms search shows the same curve
  (median first -1.00 at 122 plies before the end for both), so the middle game is not a horizon problem of the short
  search; it is the evaluation of both.
- **The 2 s search sees the forced loss only a little earlier than the 45 ms search**: a median of 12 plies before the end
  (range 2-18) against 8 (2-14); equal in 5 games, 2-6 plies earlier in 8. Median depth of the 2 s search is 9 (6 on 15x22). The
  short search is not systematically more optimistic: mean score gap short minus deep +0.09 over 969 positions (+0.17 in the
  last 30 plies); 1.00 or more in about 9% of positions.
- **The deep search would have played another move at about half of our turns** (515 of 1035; the 45 ms search differs from
  the move played in 238, 23%, it does not reproduce the live bot exactly), mostly between near-equal moves. In 11 of 13
  games at least one of the three searched moves up to the point of no return differs.
- **Blasts are where the short search is wrong most.** Of 82 blasts of ours in these games the 2 s search plays the same blast
  20 times (24%), a quiet drop in the same column 13 (16%) and a different move 49 (60%); the 45 ms search repeats the blast
  69 times (84%). The deep score changes by -0.79 on average (median -0.42) from a blast to our next searched move, against
  -0.01 after our quiet moves; after the 55 blasts the 2 s search would not have played the change is -0.93, after the 17 it
  would have played -0.36. The first blast (own turn 11-13) is a different move for the 2 s search in 11 of 13 games (a quiet
  drop or another blast). 32-39% of our 15x11 blasts are played on the first turn the cooldown allows (Savi8: 11%). Examples
  where the decline starts at a blast the 2 s search would not play: 86d1ed47 ply 47 (6*, deep 3: -0.59, then -2.00 at ply 49),
  b1cb6881 ply 31 and 52 (2*, deep 6 and 3: +0.19, -0.55 at the next move, -1.57 and -2.22 for the second), 791a6fc2 ply 73
  (7*, deep 4, the deep point of no return), 7e073388 ply 40 (4*, deep the quiet 4).
- **Timeouts are not the reason in the games without any.** Two of the seven Savi8 losses (86d1ed47, 384b1906) have no
  timeout and show the same decline (+0.21 at ply 21 to -2.02 at ply 51; -0.17 at ply 22 to -1.36 at ply 52). In b1cb6881
  the skipped turn 34 is followed by the fall from +0.19 (ply 31) to -1.59 (ply 42), and in 7e073388 the skipped turn 45
  coincides with the deep point of no return (ply 44).
- **The two opening nodes of section 3** (13.F3 S5 as F; 13.F5 14.S4 as S) cover 10 of the 13 reviewed games (all 7 Savi8
  losses, 3 of the 5 Savi7b losses). Where it could be checked (the 4 Savi8 games with us as F, reviewed from ply 1) the 2 s
  search prefers 13.F5, Savi's own move, to our 13.F3 (-0.10 at depth 8).

## 5. Our timeouts per version and size (task 5)

| Version | Size | Games | Own timeouts | Games with one | Share | Stall games (both bots timed out) | Lost games with a timeout |
|---|---|---|---|---|---|---|---|
| 2.1 (45 ms) | 8x12 | 79 | 3 | 3 | 3.8% | 1 | 0 of 3 |
| 2.1 (45 ms) | 15x22 | 80 | 117 | 12 | 15.0% | 6 | 1 of 2 |
| 2.1 (45 ms) | 15x11 | 70 | 15 | 13 | 18.6% | 5 | 1 of 5 |
| 2.2 (62 ms) | 15x11 | 67 | 21 | 18 | 26.9% | 2 | 5 of 9 |

On 15x22 117 timeouts sit in 12 games; one game has 79 (server stall at 20:33). The other bots: Testing_v2 (555 timeouts in 46
games on 15x22, 503 on 15x11) and Hurt me plenty (256 on 15x22) are worse; Savi7b 31 timeouts in 57 games, Savi8 10 in 71.
Timing: our visible skipped turns on 15x11 cluster at turns 13-18 (16 of 29; 2.2: 11 of 18, seven of them exactly at turn 14,
the first player's 8th move), which is the first move off the 12-ply centre stack. A scratch probe replayed the 29 positions
before each of our 15x11 skips and searched them 10 times each at 45/62 ms with the same hand evaluation: wall time was at
most 72 ms (budget 62) and 61 ms (budget 45) with a warm JVM, so the search itself does not explain a 100 ms overrun there.

## 6. Not verified

- The cause of the timeouts (JIT or GC at the memory limit, server-side overhead per move on larger boards, stalls). Only the
  clustering and the search-time probe above are measured.
- Draws are invisible in the data. The first-player rates and all W/L are among decided games.
- The hand evaluation's absolute scores (-1.00 etc.) are not calibrated; "point of no return" is defined on them. The
  learned 8x12 tables were not used (no tables exist for 15x11/15x22, and the reviewed games are on those sizes).
- Which games were reviewed: see `loss-review-sizes.md`; the 341-ply 15x22 loss to Savi7 by blast, the two 15x11 losses to
  lalalalabot and the three 8x12 losses were not searched.
- Bot identity is by name. "Savi 7 - The Prince" (8df129b8, played only on 15x11) and "Savi7 - The Prince" (c664dbeb, only on
  15x22) are different ids, so Savi7's 15x22 and 15x11 results are not the same bot.

## 7. Files

- `qc-bot-safe/src/tools/java/ch/qc/tools/ServerGames.java`, `GameReview.java` (modified, uncommitted).
- `analysis/reports/`: `qc_replay.py`, `qc_analysis.py`, `summarise_review.py`, `server-analysis-by-size.md`,
  `our-losses-by-size.md`, `loss-review-sizes.md`, `loss-review-sizes-raw.md`, `loss-review-sizes-raw2.md`,
  `server-games-report-4.md`, this file.
