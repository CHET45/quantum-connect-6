# King beater (rc5) vs new Savi12 (lb 5a3e4d88), 4-3, export-13

2026-11-04, data(8).zip = `server-exports/export-13`. All 15x30. Tool `GameReview`, weights g6f, short 80 ms, deep 4 s
(rc5, `night/wt-main`, `review-*.md`) and short 80 ms / deep 500 ms our moves only (candidate blastQs+forcedExt,
`night/wt-blast` build of 19:55, `bqfe-*.md`). Runs on E-cores.

All three losses come from Savi12's blast.

| game | we | plies | how it ends | forced loss exists from | rc5 80 ms sees it | rc5 4 s sees it | bqfe 80 ms |
|---|---|---|---|---|---|---|---|
| c0ae70c6 | S | 49 | blast 8* at ply 47 removes our blockers at (9,0),(9,1): bottom five a-e and diagonal a2-e6 both open on column 9; six at ply 49 | ply 44 (opponent to move at 45 finds win in 5 at 80 ms) | ply 48 | ply 48 | ply 48 |
| 168c9417 | F | 292 | blast d* | ply 282 (11 before end) | ply 284 | ply 282 | ply 287 |
| e998dd54 | S | 350 | blast 8* | ply 337 (14 before end) | ply 343 | ply 337 | ply 339 |

- Same asymmetry as 811e622e: with the opponent to move the win is found at 80 ms, with us to move not even at 4 s
  (c0ae70c6 plies 44 and 46).
- The candidate (bqfe) would have lost all three the same way. blastQs fires only when a blast wins at once; here the
  blast breaks a blocker and leaves a double threat (c0ae70c6), or ends a long forced chain (the other two).
- Forced-reply rows (depth 1) report the static eval (+7.29 at e998dd54 ply 341 inside a forced loss in 9; -0.12 at
  c0ae70c6 ply 46 one ply before the losing blast).
- The eval says +5 to +7 for us until the forced loss appears: g6f is overconfident on 15x30.
- Memory-limit lines appear in these games and in wins alike: not the cause.

Next idea: treat "opponent's ready blast leaves us two or more threats" like blastQs (no reductions, one more ply),
and add these three positions to the BlastThreats set. Judge by matches.
