# Export-16: five lost games of King beater (15x30)

2026-11-04. Tool `ch.qc.tools.GameReview`, weights g6f, short 80 ms, deep 1.5 s (whole game), 4 s and 20 s (last plies), E-cores only, one java at a time. Scores in our view, plies counted from 1.

**Important: King beater on the server is rc5 (rc6 was never uploaded).** The moves in these games are rc5's. The `rc6` and `super` columns show only what the newer search would play at the same position with its 80 ms stop. GameReview replays the real game, so it cannot play an alternative move and look at what follows. "Avoids" below therefore means the short search picks a different move than rc5 at the losing ply, and "yes (deep move)" means it picks the move that the deep search also prefers. Short searches are time based and vary a little between runs (e.g. a9d31935 ply 352: rc6 short played `1` in one run, `s` in another).

Side findings from the data: every game, including the 163 won ones, has a "Memory limit exceeded" log line (not a cause). 4d74b898 has two first-player timeouts at the very end (after the position was already lost).

## Table

| game | opponent | losing ply (rc5 move) | type | rc6 avoids? | super avoids? |
|---|---|---|---|---|---|
| c8b29d14 (we F, 132 plies) | Savi14 | 117 `f` (after our blast `f*` at 115); forced loss 14 visible at 119 | blast exchange, then Savi14 blast at 128 and a forced finish | different move (`h`), unverified; blast at 115 repeated | different move (`c` = deep move), unverified; blast at 115 repeated |
| 4d74b898 (F, 369) | HWGAbot8.0 | 342 `f` (drift from 338); loss 24-26 visible at 344-346 | eval misjudgement into a 24-26 ply forced drop chain | 342: `r` (different), but 338/340/344 same as rc5 | same as rc6 |
| 3961aa7f (F, 276) | Savi15 | 257 `7`: a forced win in 13 existed and was dropped; position lost from 263 | missed win, then blast exchange (our `7*` 247, Savi15 `b*` 250) and drop chain | no (`7`) | no (`7`) |
| 0ea69312 (F, 276) | Savi15 | 265 `h` (267 `c` is also the deep move); loss 14 visible at 269 | blast combination (Savi15 `9*` at 274, wins 276) after a balanced game | different move (`c`), unverified; does not see the loss at 269 | different move (`1`), unverified; deep search sees loss 14 at 269 |
| a9d31935 (S, 365) | Savi15 | 352 `k`; loss 18 visible at 354 | blast combination: our `b*` 360 answered by Savi15 `c*` 361, drop win 365 | different move (`1` or `s`), unverified | yes (`l` = deep move, deep +5.4 at 4 s) |

## Per game

**c8b29d14 vs Savi14.** Eval +2.2 to +2.5 for 40 plies. Deep (20 s) at 117 sees +2.38 (move `c`), at 119 any move loses in 14. So the losing move is `f` at 117 (or the blast `f*` at 115, where the 20 s search prefers `c`, 4 s still prefers `f*`). The loss shows only at 119, 14 plies ahead; the 80 ms search sees it at 125, 8 plies before the end. Raw: `review-rc6-c8b29d14-d1500.md`, `...-d4000-from95.md`, `...-d20000-from109.md`, `review-super-c8b29d14-d4000-from95.md`.

**4d74b898 vs HWGAbot8.0.** Eval +3.7 at 336 falls with search depth: 4 s deep at 338 says +2.4 (move 4 or 1), at 340 +1.0, at 342 +0.6, and at 344 the 20 s search finds loss 26 (4 s: -0.77). rc5, rc6 and super short all keep playing `t`, `r`, `f` (+3.7 to +1.9) while the deep search wants `4` or `1`. The static eval is wrong by 3 points for 8 plies and the forced loss is 24-26 plies long, far outside the 80 ms horizon. Raw: `review-rc6-4d74b898-*`, `review-super-4d74b898-d4000-from320.md`.

**3961aa7f vs Savi15.** We had a forced win. Deep 4 s at 255: `6` wins in 15 (rc5 played `6`). At 257 deep (rc6 and super, 4 s and 20 s) says `o` wins in 13; rc5, rc6 short and super short all play `7` (eval +3.9, deep +3.58 afterwards). The win decays: 259 `o` +3.3, 261 `o` +1.9, 263 every move loses in 12-14. The 80 ms searches never see the win (they would need 13 plies). Raw: `review-rc6-3961aa7f-*`, `review-super-3961aa7f-d4000-from235.md`.

**0ea69312 vs Savi15.** Balanced game (-0.5 to +0.4) for 140 plies. Loss is decided between 265 and 267; at 269 the 20 s search says every move loses in 14. super's 4 s deep search sees it at 269, rc6's 4 s search does not. Finish is Savi15's blast `9*` at 274. Raw: `review-*-0ea69312-*`.

**a9d31935 vs Savi15 (we S).** Eval +5 to +7.8 for 100 plies (g6f is overconfident here too). At 352 rc5 played `k`; deep 4 s and 20 s play `l` (+5.4 / +4.9), after `k` every move loses in 18. super short plays `l`, rc6 short plays something else. Our own blast `b*` at 360 is answered by `c*`. Raw: `review-*-a9d31935-*`.

## Savi15 compared with Savi14

- Opening: identical to all other games (our column-e stack; first 26 plies of 3961aa7f and 0ea69312 are the same). Nothing Savi15-specific.
- Blasts: Savi15 uses 1.9 blasts per game against us, Savi14 1.5, Savi12 2.0 (HWGAbot8.0 5.9). Same style: it holds the blast and spends it late. Its winning blast comes at 274 (of 276), 361 (of 365), and in 3961aa7f at 250 right after our 247 blast.
- The three losses share one pattern: we are even or ahead (+0.3, +4, +6) until 10-18 plies before the end, a blast exchange or one late Savi15 blast creates an unstoppable drop chain, and the eval never warned. Games are long (276, 276, 365 plies). Savi15 lost 4 of 7 to us, so it is not stronger in general; our losses to it are late tactical ones.
- Blast exchange: in 3 of 5 losses (c8b29d14, 3961aa7f, a9d31935) our own blast was followed within 1-13 plies by the opponent's blast, then the loss.

## Conclusions

1. The loss is decided 14-26 plies before the end and only appears in a 4-20 s search after the fatal move. 80 ms rc6 or super cannot avoid it by depth; the realistic lever is the eval (g6f gives +3.7 to +7 in positions that lose by force). A g7 fitted on 15x30 data including these Savi15 and HWGAbot8.0 positions is the most likely fix, with the unspent opponent blast as an explicit feature.
2. 3961aa7f is a missed 13-ply win that rc5, rc6 and super all miss at 80 ms. A forced-win search for our side (VCF with blasts, deeper when the eval is above +3) is worth testing; super's VCF did not find it in 80 ms.
3. Treat our own blast as risky while the opponent still holds a blast: verify it with a longer search before playing it (115, 247, 360 in these games). Test as a gated candidate.
4. super is the better base: it sees the 0ea69312 loss one move earlier at 4 s, and in a9d31935 its short move equals the deep move where rc5 lost. It does not fix 3961aa7f or 4d74b898.
5. Five positions are not evidence for a change. Any candidate must be judged by matches against Savi15 and HWGAbot8.0 (15x30), not by this replay.

## Files (this folder)

`review-rc6-<id>-d1500.md` whole games, rc6, deep 1.5 s; `review-<rc6|super>-<id>-d4000-from<N>.md` last plies, deep 4 s (from 95, 320, 235, 245, 330); `review-rc6-<id>-d20000-from<N>.md` deep 20 s (from 109, 336, 253, 259, 344). `.err` files are empty. Runner scripts are in the session scratchpad only.
