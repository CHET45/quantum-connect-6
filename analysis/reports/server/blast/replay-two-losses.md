# Replay of the two server losses with the rc5 search

2026-11-04. Tool: `ch.qc.tools.GameReview` from `night/wt-main` (560df13 = rc5), weights `generic-g6f.qcw` (what rc5 uses on 15x30), short search 80 ms (the bot's stop), deep search 4 s and 30 s, one pinned P-core each. Raw tables: `review-*.md` (last 80 plies, deep 4 s), `deep30-*.md` (last 18 plies, deep 30 s). Scores are in our view.

## 811e622e: rc5 (F) vs Savi12, 288 plies, lost to Savi12's blast

- Losing move: ply 275, our blast `8*`. The 80 ms search chooses `8*` as on the server; the 4 s and the 30 s searches prefer a quiet move (`n`, `j`) at +4.8.
- Right after it the opponent has a forced win (mate in 9 for the side to move). Our search finds it at 80 ms when the opponent is to move (plies 276, 278, 280, depth 8).
- With us to move it is blind: at plies 277 and 279 the 80 ms and 4 s searches say +4.9; only 30 s finds "loss 12". The 80 ms search first sees the loss at ply 281, 8 plies before the end.
- Reading: the same forced line is found in 80 ms from the opponent's side and needs about 30 s from ours. That points at reductions and pruning of the opponent's replies in our tree (late-move reductions, null move, or the blast-readiness rule not extending), not at eval alone. This is the case the rc5-blast-threat branch should fix.

## cf05e266: rc4 (S) vs lalalalalabotfixed, 371 plies, lost to the opponent's blast

- Losing move: ply 356, rc4 played `8`. The rc5 search plays `6` at 80 ms and at 30 s (+7.5), so rc5 would most likely not have made this move.
- After `8` the forced loss is 15 plies long: the 30 s search sees it at once, the 80 ms search sees it 10 plies before the end.

## Both games

- The eval said +5 to +8 for us until the forced loss appeared: g6f is strongly overconfident on 15x30 (it was not trained on it). Supports g7 with 15x30 data.
- Threat horizon: forced loss existed 13-15 plies before the end; the bot's own budget saw it 8-10 plies before the end.

## Next

Build a set of positions (server and self-play) where the opponent wins by a blast within 1-15 plies and compare our detection with us to move against the opponent to move, at equal nodes. Then try on branch rc5-blast-threat: no reduction of opponent replies and an extension when the opponent's blast becomes ready within a few plies. Judge only by matches.
