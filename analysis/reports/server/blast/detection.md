# Blast-win detection at the bot's node budget

## Summary of the night 2026-11-04/05 (details in the last sections)

- **Delivered: rc7** (`OneDrive/qc-bot/qc-bot-4.0-rc7.jar`, sha256 06043f17e2b8...). rc7 = rc6 + exact speed-ups of the blast check (+29% nodes per second, same search) + `partial` (take the best move of the interrupted iteration).
- rc7 vs rc6 on 15x30: 57.3% (E-cores) and 59.8% (P-cores), 400 pairs, about +60 Elo. vs rc5 (still on the server): 61.0% (E-cores) and 64.5% (P-cores, +104 [+70, +139]) on 15x30, 63.7% on 100x100. Other sizes: no loss anywhere (15x22 and 12x16 +70, the rest about even). No crashes or timeouts; ServerSim at 50, 100 and 200 ms clean.
- The gain is `partial` (+37 on 15x30 over 400 pairs); the speed-ups alone scored 49.7% of 400 pairs.
- Tried on top of rc7, none shown: VCF with attacker blasts (51.9% of 400), double VCF time (51.9% of 400), half VCF time (47.0%), lmp=5 (51.2%), lmrRoot=4 (46.0%), lmrFrom=3 (48.8%).
- New losses of rc5 in export-16 (3 to Savi15) are decided 14-26 plies before the end, past an 80 ms search; the eval called those positions +2..+7. Review: `analysis/reports/server/export16/README.md`. The likely lever is the evaluation, not search settings.
- Nothing committed; code in `night/wt-blast` (uncommitted, rc7 = its source).

2026-11-04, branch `rc5-blast-threat` (worktree `night/wt-blast`, from 560df13). Tool `ch.qc.tools.BlastThreats` (new). Data: every 15x30 game of export-09 and export-10 that ended with a winning blast (108 games), positions 1-12 plies before that blast (1296). Each spec searches at 350k nodes (rc5's nodes per call on the server, 15x30), the reference is the bot's spec at 3M nodes; a position counts as forced when the reference finds a mate score. Weights g6f. Raw: `bt1.md`, `bt2.md`, `bt*-rows.tsv`.

## Result

| spec at 350k nodes | winner to move: wins found | loser to move: losses found | mean depth |
|---|---|---|---|
| rc5 (bot) | 201 of 232 (87%) | 152 of 164 (93%) | 7.86 |
| lmr=0 | 189 (81%) | 152 (93%) | - |
| lmrBlast=0 | 197 (85%) | 151 (92%) | - |
| blastGenDepth=1 | 199 (86%) | 151 (92%) | - |
| +blastQs | 209 (90%) | 159 (97%) | 7.81 |
| +blastQs,+blastExt | 209 (90%) | 158 (96%) | 7.79 |
| +forcedExt | 216 (93%) | 157 (96%) | 7.56 |
| +forcedExt,+blastQs | 228 (98%) | 165 (101%) | 7.50 |
| reference (bot, 3M) | 232 | 164 | 10.09 |

At 8-11 plies before the end: rc5 42 and 12, forcedExt+blastQs 55 and 19, reference 58 and 20.

## The two switches

- `blastQs` (new): when the opponent's blast is ready and some blast wins now, the side to move must break it this ply. Such a node is treated like a single drop threat: a leaf gets one more ply instead of a static eval, and no drop is reduced, razored or pruned there. Before, a leaf in that state was scored by the eval as if nothing were wrong. Cost at equal nodes: -0.05 ply.
- `forcedExt` (existing experiment switch since adafe81, never gated): a node with exactly one opponent drop threat is extended by one ply. Cost at equal nodes: -0.3 ply.
- `blastExt` (new, also extends inner nodes in the blast state): adds nothing; not used.

## Gates (real protocol, 80 ms bots, 5 games at a time on the P-cores, g7 self-play running on the E-cores)

Candidate jars (only `Searcher*.class` differ from rc5): `qc-bot/dist/night/cand-rc5-bq.jar` (blastQs) and `cand-rc5-bqfe.jar` (blastQs + forcedExt).

| A | B | board | pairs | score A | Elo [95%] | logs |
|---|---|---|---|---|---|---|
| bq | rc5 | 15x30 | 30 | 53.3% | +23 [-56, +104] | `night/logs/bq-rc5/` |
| bqfe | rc5 | 15x30 | 30 | 65.0% | +108 [+18, +213] | `bqfe-rc5/` |
| bqfe | rc5 | 15x30 | 100 (seed 71) | 57.0% | +49 [+3, +96] | `bqfe-rc5-s71/` |
| bqfe | rc5 | 15x11 / 15x22 / 8x12 / 12x16 / 20x9 | 50 / 40 / 50 / 30 / 30 | 49.5 / 51.9 / 47.5 / 54.2 / 52.5% (50.6% over 200) | each interval spans 0 | `bqfe-rc5-sizes/` |
| bqfe | rc4 | 15x30 | 50 (seed 73) | 56.0% | +42 [-27, +114] | `bqfe-rc4/` |

bqfe vs rc5 on 15x30 over 130 pairs: 58.8%. Crashes 0 everywhere. Timeouts: 12 vs 12 in the 100-pair run (both sides equally, machine load), otherwise 0-1. bqfe takes more of its 80 ms on 15x30 (p50 77 ms vs rc5 69-72 ms; p99 equal, about 87 ms).

Reading: a gain on 15x30 (the server's board), neutral elsewhere. `gate.sh` now takes `SEED=` (default 53, as before).

Robustness of bqfe against rc5:
- ServerSim 15x30, 100 ms limit, 14 games: 7-4-3, 60.7%; engine timeouts 0, crashes 0; second-call timeouts 14 vs 14 (the limit probe). Nodes per call lower for bqfe (30-59: 265k vs 336k, 60+: 146k vs 177k): the blast scan costs about 20% of the speed. `night/logs/sim/bqfe-15x30.*`.
- 100x100 real protocol, 10 pairs: 62.5%, timeouts 0. `night/logs/bqfe-h100.out`.

## Delivered

`qc-bot/dist/night/qc-bot-4.0-rc6.jar` = bqfe (sha256 b9722c2d...), rebuilt from `night/wt-blast` with `blastQs = true` and `forcedExt = true` as defaults and checked byte-identical to the gated `cand-rc5-bqfe.jar`; copied to `C:\Users\Chtean45m\OneDrive\qc-bot\`. Not committed.

## Second set: export-13 (with the new Savi12 losses) and losing-move avoidance

`BlastThreats --blast-within 12`: every 15x30 game of export-13 where the winner blasted in its last 12 plies (297 games, 3564 positions; includes c0ae70c6, 168c9417, e998dd54 from `analysis/reports/server/savi12b/`). Reference: rc6 at 3M nodes. New metric: positions where the loser to move was not lost (reference) but the move played gave the winner a forced win (290); does the spec play the same move, another losing move, or a move the reference cannot refute ("saved")? Raw `bt3.md`, `bt4.md`.

| spec at 350k | wins found (winner to move) | losses found (loser to move) | losing moves saved of 290 | mean depth |
|---|---|---|---|---|
| rc5 | 563 of 688 (82%) | 440 of 537 (82%) | 153 | 7.85 |
| rc6 | 635 (92%) | 479 (89%) | 164 | 7.51 |
| rc6 -forcedExt | 584 (85%) | 456 (85%) | 156 | - |
| rc6 lmrBlast=0 | 618 (90%) | 477 (89%) | 164 | - |
| rc6 blastGenDepth=1 | 637 (93%) | 481 (90%) | 172 | 7.25 |
| rc6 +blastDbl | 592 (86%) | 459 (85%) | - | 6.58 |
| rc6 +blastDbl, blastGenDepth=1 | 593 (86%) | 461 (86%) | - | 6.37 |

- rc6's gain over rc5 holds on this larger, independent set.
- `blastDbl` (new, proposal (a) of the savi12b review: at depth <= 1 an opponent blast that clears one of our discs and leaves two threats, stacked threats included, makes the node forced) costs about a ply through the trial blasts and finds less. Rejected; the code stays, off.
- c0ae70c6: no spec, not even the reference, finds a forced loss before ply 46 with us to move; all find the win after it. The losing move is ours at ply 46, which is what the losing-move metric measures.
- `blastGenDepth=1` (blasts generated as moves at every depth, not from 3) is the only further gain: candidate `qc-bot/dist/night/cand-rc6-gen1.jar`. Real protocol vs rc6, 15x30: 100 pairs 52.5% (+17 [-30, +66]); more pairs running (`night/logs/gen1-rc6-s82/`).

## g7f tables

Fresh fit, 20 epochs, 163 sets, 8.29M records (gen1-gen7, 15x30 in SEEN), GPU 19:56-20:37, no watchdog events. Spec files for the seven new sizes made with `FeatureSpecDump --generic` from `bin-rc` (15x22 regenerated byte-identical; 15x30 specHash equals the self-play data's). New opening suite `night/wt-eval/suites/openings-15x30.txt` (OpeningGen, 400 lines, 60 ms, seed 15).

Fixed nodes against g6f (bin-rc search): pooled 52.9% +-2.5 (+20, 600 pairs); 12x16 63.0%, 20x9 60.0% (both hold-outs), 8x12 53.8%, others 50-52%. On 15x30: 48.5% (100 pairs, 30k), 50.5% (200, 30k), 52.0% (100, 150k): no gain on the server's board. Real protocol `cand-rc6-g7f.jar` vs rc6 running (`night/logs/g7f-rc6/`).

## Is the eval blind when both blasts are held? (calibration check)

Tool `ch.qc.tools.EvalCalib` (new): static eval of every position (ply >= 10) of the 966 15x30 games of export-13, with the outcome and blast features. Raw `calib-g6f.tsv`, `calib-g7f.tsv`. The eval is not a probability (the per-size scale exists only in the fit), so: AUC (scale-free) per group, and the bias against one global scale fitted on all positions (a = 0.72 per logit for g6f).

| group (g6f) | positions | AUC | predicted | actual | bias | favoured side wins when \|eval\| > 4 |
|---|---|---|---|---|---|---|
| all | 66417 | 0.856 | 0.516 | 0.507 | -0.010 | 92.3% |
| none charged | 10052 | 0.863 | 0.516 | 0.508 | -0.008 | 90.4% |
| only the mover charged | 11672 | 0.821 | 0.411 | 0.281 | -0.130 | 88.8% |
| only the opponent charged | 12141 | 0.817 | 0.620 | 0.716 | +0.095 | 88.9% |
| both charged | 32552 | 0.859 | 0.516 | 0.510 | -0.006 | 94.0% |
| both charged, 11-30 plies left | 5722 | 0.923 | 0.516 | 0.511 | -0.005 | 98.0% |

Strong against strong only (both names match Savi|beater; 52 games, 13017 positions): both charged AUC 0.918, bias -0.006, 93.7%; only the mover charged bias -0.081, only the opponent charged +0.052. g7f: the same pattern (both charged AUC 0.861, one side charged bias -0.106 / +0.111).

Reading:
- "Both charged, board full" is where the eval is best, not worst. More such positions from held-blast self-play would train what it already does well. The trap self-play idea is not supported by this data.
- The eval does misjudge positions where only one side is charged: the side that blasted recently does better than the eval says. Part of this is selection (a bot blasts when its search sees a gain the static eval does not), so it is not clearly an eval defect.
- A column at least three taller than its neighbour exists in almost every 15x30 position (32294 of 32552 with both charged), so it carries no information as a feature.
- The losses with +5..+7 on the board are the 2-8% tail at high confidence: long forced chains, a search-depth problem.

## c43411fd (King beater vs HWGAbot6.0, export-15; review in `analysis/reports/server/hwga6/`)

Losing move: ours at ply 108 (`e`). Even the reference at 20M nodes (depth 13) does not see a loss before it, while every spec sees the win right after it. Who plays `e` at ply 108 at a fixed node budget (`c434-n*.md`):

| nodes | rc5 settings | rc6 |
|---|---|---|
| 60k | `e` (loses) | `e` (loses) |
| 100k-250k | `e` (loses) | another move, not refuted by the reference |
| 350k | another move | another move |

rc6 avoids this loss from 100k nodes on (its server budget at that stage is about 265k) without seeing the loss: the blast switches change its move choice. blastGenDepth=1, lmrBlast=0 and blastDbl change nothing here. Plies 110 and 112 have a single legal reply, so the root stops after depth 1 and prints the static eval; this does not affect the move.

Why the opponent-to-move search finds such wins at once: the root VCF of the side to move. VCF plays only threat drops and the attacker's immediately winning blast; a blast that breaks a block and leaves threats (c0ae70c6, c43411fd) is not in it. Candidate: let the attacker's VCF play a blast that leaves it a playable threat, so the root's check of our moves against the opponent's VCF (`dropMovesLosingToVcf`) catches these lines.

Open: the blast scan's speed cost (an incremental check could win back the ~20%); 500 ms and 50 ms limits not run.

## Night 2026-11-04/05: VCF with block-breaking blasts, and rc6's speed

Code in `night/wt-blast` (uncommitted): `Vcf.attackerBlasts`, search option `vcfBlast` (0 off, 1 root threat searches only, 2 every threat search), anyBlastWins cache in `Searcher`.

### VCF with attacker blasts

With the option on, the VCF attacker may also play a ready blast that does not win at once but leaves it a playable threat and the defender none (no playable threat, no winning blast of its own). Defender replies are unchanged (block, own win, own blast if ready). The results use their own table keys.

Proof check (`VcfTest.longProofsWithAttackerBlastsSurviveEveryDefence`, independent checker in which the attacker may also blast and the defender tries every legal move): 50,000 random positions, 5,781 proofs, no false proof; 43 proofs exist only through an attacker blast.

BlastThreats on export-13 (`bt5.md`, `bt5-rows.tsv`; same 3,564 positions, 350k nodes, reference rc6 at 3M without the option):

| spec | detection winner | detection loser | losing moves saved | mean depth |
|---|---|---|---|---|
| rc6 | 635 (92%) | 479 (89%) | 164 | 5.96 |
| vcfBlast=1 | 636 (92%) | 489 (91%) | 165 | 5.85 |
| vcfBlast=2 | 636 (92%) | 489 (91%) | 165 | 5.85 |

The gain is small: the side to move sees its own loss 10 times more often, one more losing move is avoided, at -0.1 ply. Level 2 adds nothing over level 1.

### Where rc6's speed went

SearchBench, 300 positions from 15x30 server games (export-13, `qc-bot/data/night/search/server-15x30.txt`), 100k nodes, one thread, g6f. All runs of one table were measured at the same time on two pinned P-cores; first spec of a JVM is a cold-JIT warm-up and is left out.

| configuration | knps |
|---|---|
| rc6 (blastQs + forcedExt) | 2,680 |
| rc5 settings (-blastQs, -forcedExt) | 3,030-3,340 |
| forcedExt only (-blastQs) | 3,460 |
| blastQs only (-forcedExt) | 2,630 |

The cost is blastQs, not forcedExt. JFR profile of rc6: `Board.anyBlastWins` is 30% of all time (the evaluation 18%); 21% of the time is the call for `oppBlast` at every node where the opponent's blast is ready.

Fixes, both leaving the search identical (same nodes, depth, move and score on all 300 positions):
- compute anyBlastWins once per node (the side-to-move check and oppBlast used the same board): +2-3%.
- cache anyBlastWins by board hash per search context (2^15 entries, 256 KB; 2^17 is no faster): rc6 2,697 -> 3,039 knps, +13%. anyBlastWins falls to 20% of the time; the rest are first visits of new leaves.

Jars: `qc-bot/dist/night/cand-rc6-fast.jar` (rc6 + the cache), `cand-rc6-super.jar` (rc6 + the cache + vcfBlast=1). Both differ from rc6 only in `Searcher*.class` and `Vcf.class`.
- third fix, also identical: when a node was reached by a drop in column c from a node where no blast won, only the columns within K of c can have changed (a column's blast test reads only the columns within K of it), so the scan checks those (`Board.anyBlastWinsNear`, checked against the full scan in `BoardDifferentialTest`): 3,290 -> 3,470 knps. All three together: rc6 2,680 -> ~3,470 knps (+29%), above the rc5 settings' 3,340-3,470 measured in the same runs.

### Gates (real protocol, 80 ms, 15x30)

| A | B | pairs | score of A | Elo [95%] |
|---|---|---|---|---|
| super (rc6 + hash cache + vcfBlast=1) | rc6 | 100 | 48.5% | -10 [-60, +38] |
| fast (rc6 + hash cache) | rc6 | 200 | 50.0% | 0 [-34, +34] |

Logs `night/logs/super-rc6.out`, `fast-rc6.out`. The +13% speed does not show in 200 pairs (a share of one more ply is small); vcfBlast=1 shows no gain either, so it stays off by default. `cand-rc6-fast2.jar` = rc6 + all three speed fixes, vcfBlast off.
| fast2 (rc6 + all three speed fixes) | rc6 | 200 | 49.4% | -4 [-38, +29] |
| fast2 + partial | fast2 | 200 | 55.8% | +40 [+7, +74] |

`partial` (Searcher.Options, never gated before): when the clock stops an iteration, take the best move among those already searched at the new depth (the first of them is the previous best, re-searched), not only a move that beat the previous iteration's score. The bot nearly always stops on the clock, so this changes most moves. Jar `cand-fast2-partial.jar` (differs from fast2 only in `Searcher$Options.class`). Confirmation run with another seed and other sizes follow.
| fast2 | rc5 (on the server) | 200, E-cores | 61.6% | +82 [+50, +116] |

E-core lane: `night/tools/gate-e.sh` = gate.sh on the E-cores (mask FF000, 4 games at a time, lock slot 3); slower cores for both jars, so its budget is like a slower server.
| fast2 + partial | fast2 | 200 (seed 96) | 54.8% | +33 [0, +67] |

partial on 15x30 over 400 pairs: 55.3%, about +37 Elo. Other sizes (E-cores, small samples): 15x11 46.5% (50 pairs), 15x22 50.6% (40), 8x12 47.5% (50); more pairs running on the P-cores.
E-cores, partial vs fast2: 12x16 55.0% (30 pairs), 20x9 60.0% (30); all five sizes together 50.9% of 200 pairs.
P-cores, partial vs fast2: 15x11 46.3% (100 pairs; with the E-core 50 pairs 46.4% of 150, about -25 Elo, CI about -60..+10), 8x12 49.8% (100), 15x22 59.2% (60). Logs `night/logs/partial-sizes.out`, `e-partial-sizes.out`.
ServerSim 15x30, 100 ms, fast2+partial vs rc6 (`night/logs/sim/partial-15x30.*`): 20 games 7-9-4 (45%, too few to judge); no timeouts on either side; nodes per call 336k vs 291k (own turns 30-59), 185k vs 163k (60+); the one second-call timeout per game at own turns 10-19 is the known limit probe and equal for both.
| fast2 + partial | rc6 | 200, E-cores | 57.3% | +51 [+14, +88] |
15x11 recheck (P-cores, seed 101): 49.0% of 150 pairs; 15x11 over 300 pairs 47.7%, about -16 Elo (CI about -40..+10). Kept: strongly positive on 15x30, neutral on the other sizes together.
| fast2 + partial | rc5 | 200, E-cores | 61.0% | +78 [+41, +116] |

100x100 (`huge.sh`, fixed opening seed 71, so the runs share openings), fast2+partial vs rc6: 10 pairs 42.5%, 30 pairs 45.0%; no timeouts. Split into speed and partial follows (`huge-e.sh`, `huge-s.sh` take SEED).
100x100, 60 more pairs (P-cores, seed 71): 54.2%; all 100 pairs 50.3%, neutral.

### rc7 delivered (2026-11-05 02:39)

rc7 = rc6 + anyBlastWins once per node + hash cache + near-drop scan (search identical, ~+29% nodes per second on 15x30) + `partial` on. vcfBlast stays off (code in, default 0). Built from `night/wt-blast` source (uncommitted) with the full test suite passing; its classes are identical to `cand-fast2-partial.jar`, which played the gates. `qc-bot/dist/night/qc-bot-4.0-rc7.jar` and `C:\Users\Chtean45m\OneDrive\qc-bot\qc-bot-4.0-rc7.jar`, sha256 06043f17e2b8...

| test | result |
|---|---|
| vs rc6, 15x30, E-cores, 200 pairs | 57.3%, +51 [+14, +88] |
| vs fast2 (rc7 minus partial), 15x30, P-cores, 400 pairs | 55.3%, about +37 |
| vs rc5 (on the server), 15x30, E-cores, 200 pairs | 61.0%, +78 [+41, +116] |
| vs fast2, other sizes (15x11, 15x22, 8x12, 12x16, 20x9), 610 pairs | about 50%; 15x11 alone 47.7% of 300 |
| vs rc6, 100x100, 100 pairs | 50.3% |
| ServerSim 15x30 100 ms vs rc6 | no timeouts; 12-16% more nodes per call |

Crashes 0 and timeouts 0 in every gate above.

### After rc7

| A | B | pairs | score of A | Elo [95%] |
|---|---|---|---|---|
| fast2 | rc6, 100x100, E-cores | 40 | 44.4% | -39 [-104, +23] (search identical: noise or a huge-board effect; rc7 itself 50.3% of 100 there) |
| rc7 with half VCF shares (root 1/16, opp 1/32) | rc7 | 200 | 47.0% | -21 [-56, +13] |
| rc7 + vcfBlast=1 | rc7 | 200, E-cores | 53.8% | +26 [-7, +60] |
| rc7 with double VCF shares (root 1/4, opp 1/8) | rc7 | 200 | 54.4% | +30 [-2, +63] |
| rc7 + vcfBlast=1 | rc7 | 200, E-cores, seed 109 | 50.0% | 0 [-35, +35] |

vcfBlast on rc7 over 400 pairs: 51.9%, about +13 (CI about +-24); with the earlier 48.5% of 100 vs rc6, no gain shown. Stays off.
| rc7 with double VCF shares | rc7 | 200, seed 110 | 49.3% | -5 [-38, +27] |

Double VCF shares over 400 pairs: 51.9%, about +13, not shown; rc7 keeps 1/8 and 1/16.
| rc7 | rc5, E-cores | 15x11 60 / 15x22 40 / 8x12 60 | 52.5% / 62.5% / 48.3% | +17 / +89 / -12 |
| rc7 | rc6, P-cores | 200 | 59.8% | +69 [+33, +106] |
| rc7 | rc5, 100x100, E-cores | 40 | 63.7% | +98 [+27, +179] |
| rc7 + lmp=5 | rc7 | 200 | 51.2% | +9 [-25, +43] |
ServerSim 15x30 at 50 ms, rc7 vs rc6 (`night/logs/sim/rc7-15x30-l50.*`): 12-8-0 (60%), no call over 90 ms, no timeouts, limit read as 50 by both.
| rc7 + lmrRoot=4 | rc7 | 200, E-cores | 46.0% | -28 [-62, +6] |
ServerSim 15x30 at 200 ms, rc7 vs rc6: 10-3-7 (67.5%; 7 draws by the 30 s match clock), no engine timeouts; limit read as 200 in 14 games, 100 in 6 (either bot), as before.
| rc7 | rc6, unseen sizes, E-cores | 10x10 40 / 7x9 40 / 6x7 40 / 20x20 30 | 50.6% / 50.0% / 50.6% / 55.0% | 0 crashes |
| rc7 | rc6, 15x11, E-cores | 100 | 49.8% | -2 [-46, +43] |
| rc7 with lmrFrom=3 | rc7 | 200 | 48.8% | -9 [-44, +26] |
| rc7 | rc6, E-cores | 15x22 50 / 12x16 40 / 20x9 40 / 8x12 50 | 60.0% / 60.0% / 49.4% / 48.5% | +70 / +70 / -4 / -10, 0 crashes |
| rc7 | rc5, P-cores | 200 | 64.5% | +104 [+70, +139], SPRT H1; timeouts 2 vs 3 |
