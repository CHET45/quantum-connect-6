# Eval stream results (night 2026-11-04)

Worktree `night/wt-eval`, branch `night-eval`. Data, models and gate logs under `qc-bot/data/night/eval/`.
All gates are fixed-node `FastMatch`, colour-swapped pairs, score of the first-named model with 95% CI over pairs, unless marked real time.

## Facts measured so far

| What | Number | Note |
|---|---|---|
| nps generic vs hand, 15x11 | 336k vs 366k (x0.92) | one thread, machine loaded; `EvalSpeed` |
| nps generic vs hand, 15x22 | 204k vs 250k (x0.82) | |
| nps generic / gen003 vs hand, 8x12 | 687k / 652k vs 759k (x0.91 / x0.86) | |
| Spearman with search value, held-out 15x11 | hand 0.800, 15x11-only table 0.803 | AUC win/loss: value 0.628, hand 0.618, table 0.620 |
| Spearman with search value, held-out 15x22 | hand 0.937, 15x22-only table 0.765 | AUC win/loss: value 0.610, hand 0.605, table 0.632. Hand search values on 15x22 are nearly the static hand eval |

## Gates

| Model | Against | Size, nodes | Pairs | Score | Verdict |
|---|---|---|---|---|---|
| g1-x11: 15x11 data only, 660k records, K auto (1722), lam 0.9 | hand | 15x11, 12k | 60 | 61.3% +-9.5 (+80) | signal |
| g1-x15: 15x22 data only, 330k records, K auto (3444), lam 0.9 | hand | 15x22, 8k | 40 | 50.0% +-9.9 | no gain |
| g1-x11-s25: as g1-x11 with colour-antisymmetric window tables | hand | 15x11, 12k | 60 | 48.3% +-9.1 | worse than free tables, dropped |
| m1: one table set, 15x11 + 15x22 + 8x12 (gen003 labels) + 10x10, K 256 for all, balanced batches, learned per-size scale | hand | 15x11, 12k | 60 | 65.8% +-8.1 (+114) | first mixed model; running on more sizes |

## Fit settings found

- Target scale K 256 on every size gives far better agreement with the search value than the outcome-fitted K: on held-out 15x11, Spearman 0.866 against 0.803; on 15x22, 0.875 against 0.765.
- Sharing tables across sizes helps held-out prediction: 15x11 Spearman 0.878 (mixed) against 0.866 (15x11 only); 15x22 0.915 against 0.875. The learned per-size output scales stay near 1 (0.89 to 1.33), so one table set is in the same units on all sizes.
- lr 1e-3 -> 1e-5 with `--balance` converges by about epoch 16 on the mixed data. Quantization at scale 1024: max 0.13 logit on 15x22.

## Merged night-speed (04af256) at 02:14, merge commit 9cf3668

Everything from here on runs on the merged code (frozen classes `qc-bot/data/night/eval/bin-speed`). Earlier gates at 12k/8k nodes used the old code; their tree is the same by the speed stream's bench signatures.

- Parity on merged code: `GenericEvalTest.parityWithThePythonModel`, model m1, 4000 records each on 15x11, 15x22, 8x12, 10x10: integer sums equal on a loaded board and through the kept per-level sums. Max |int - float|: 0.056, 0.124, 0.014, 0.025 logit. `UploadRulesTest` and `WindowSumTest` pass.
- nps, merged code, one thread on the loaded machine, 100k nodes per position (`EvalSpeed`):

| Size | hand | generic m1 | ratio |
|---|---|---|---|
| 15x11 | 1952k | 1618k | x0.83 |
| 15x22 | 1866k | 1569k | x0.84 |
| 8x12 | 2448k | 1911k | x0.78 |
| 10x10 | 2167k | 1656k | x0.76 |

- Positions or depth: I chose deeper labels, 40k nodes per move instead of 12k/8k (30k on 20x20), and the same for gates. Reason: at these speeds a real 80 ms move searches about 130k nodes, so 12k-node labels and gates would judge the evaluation about 10x below real play, while 40k is about 3x below. Positions per hour stay at or above the old rate. This was not measured against the alternative; I did not find a test that could separate the two within tonight's budget. More data did show a measurable gain (mixing sizes raised held-out Spearman), so if a later generation is short of data I will lower the nodes.

## Class families tested in Python only

- Family 1 (adds distance to the nearest side wall, 54 classes) on the same data as m1: held-out Spearman 15x11 0.854 against 0.878, 15x22 0.899 against 0.915, 8x12 0.856 against 0.871. It overfits at this data size. Not adopted.

## m1 on merged code, 40k nodes (02:24)

| Against | Size | Pairs | Score | Note |
|---|---|---|---|---|
| hand | 15x22 | 60 | 74.2% +-7.9 (+183) | |
| hand | 15x11 | 60 | 60.8% +-9.0 (+76) | |
| hand | 12x16 | 50 | 78.0% +-7.6 (+220) | no 12x16 data in the fit |
| hand | 10x10 | 50 | 67.0% +-7.2 (+123) | |
| hand | 7x9 | 50 | 53.0% +-4.8 (+21) | no 7x9 data in the fit; 78% full-board draws |
| hand | pooled | 270 | 66.7% +-3.5 (+120) | |
| gen003 | 8x12 | 60 | 42.5% +-6.1 (-53) | gen003 stays on 8x12 |

Real time, `eval-m1-speed.jar` vs `speed-04af256.jar` (same code, hand on these sizes):

| Size | Games | Score | Timeouts A / B |
|---|---|---|---|
| 15x11 | 100 | 62.5% +-10.0 (+89) | 51 / 54 |
| 15x22 | 80 | 58.8% +-10.5 (+61) | 16 / 21 |

## Regularisation matters more than anything else tried so far

The fit's decoupled weight decay was far too weak (lr * wd per step with wd 1e-4 and 1e-3 does almost nothing).

| Model | Against | Size | Pairs | Score |
|---|---|---|---|---|
| v-wd: m1's data, `--wd-base 1 --wd-delta 10` | m1 | 15x11, 40k | 100 | 63.5% +-6.7 (+96) |

Held-out Spearman v-wd vs m1: 15x11 0.884 vs 0.878, 15x22 0.908 vs 0.915, 8x12 0.880 vs 0.871, 10x10 0.859. So a small Spearman change on held-out data hides a large strength change, and it even moved the wrong way on 15x22. A further sweep is running.

v-wd against m1, rest of the gate (40k nodes): 15x22 62.5% +-7.9 (+89, 80 pairs), 12x16 59.0% +-9.9 (+63, 50 pairs, no 12x16 data in either fit), pooled with 15x11 62.2% +-4.5 (+86, 230 pairs).

Real time, m1 vs hand on 12x16: 63.3% +-10.4 (+95), 60 games, timeouts 20 / 22, on a size not in the fit. One of those games ended in an engine crash, counted as a loss for m1 (a special after a timed-out first call; the code involved is in `QcBot`, not the tables; reported to the coordinator for the time stream).

Self-play moved to BelowNormal at 02:38 on the coordinator's instruction, using v-wd tables. The two old hand jobs were stopped by their owner.

Fine-tuning m1 on 15x11 data alone (m1-ft11, lr 2e-4 -> 2e-5): 46.8% +-6.1 against m1 on 15x11, 100 pairs at 40k. No gain from a per-size table at this data size. Dropped for now.

## Variants against v-wd (40k nodes)

| Model | Size | Pairs | Score | Verdict |
|---|---|---|---|---|
| w3: wd 3 / 30 | 15x11 | 100 | 53.0% +-6.4 | same |
| w3: wd 3 / 30 | 15x22 | 80 | 48.1% +-8.3 | same; decay is flat around 1 / 10, kept 1 / 10 |
| s-live: windows holding both colours forced to zero (blob 48 kB) | 15x11 | 100 | 37.5% +-6.4 (-89) | rejected, although held-out Spearman was equal. Dead windows carry real value. Gate stopped after this size |

Spearman misled twice now, in both directions. Only matches decide.

## Restart after the 03:20 machine crash (09:45)

- Shards checked with `qctrain.checkdata`: every record in gen1, hand0, x11, x15 and it03-partial is in range; nothing was lost beyond the unfinished chunks. The partial gen1 data is used.
- The gates of v-wd scaled by 1.5, s-lam10, s-nostage and the first gen1 fit (g2a) were lost in the crash and are rerun where still useful.
- Night-main 8f31c96 merged into night-eval (76f4c48). Frozen classes `qc-bot/data/night/eval/bin-main`.
- GPU: one process at a time through `qctrain.budget` (a lock; falls back to the CPU if `night/logs/gpu-stop` exists). Self-play: `qctrain.selfplayd`, Below Normal, threads per chunk = what fits under the machine limit of 18, at most 10; pauses for gates.
- Unseen sizes kept out of every fit for the any-size test: 9x14, 11x18, 12x12, 8x8, 6x10, 25x12.

## Size independence (user requirement, 10:00)

Nothing in the blob or in `GenericEval` knows a list of sizes. The blob has rows = cols = 0. The learned per-size output scale exists only during fitting. K exists only for building labels; `genfit` now derives it from the spread of the labels. Tables multiplied by 1.5 against plain v-wd on 15x11: 48.4% +-6.9 (80 pairs), so the absolute scale that the per-size output scale was hiding does not matter there.

What does know sizes: `Weights.forGeometry` prefers the sized gen003 on 8x12 (kept only while it wins, reported per generation); `QcBot.PREPARED_SIZES` (time stream's file).

Feature check over sizes (`qctrain.featcheck`, gen1 data):

| Size | Plies | Stage 0/1/2/3 % | One-above threats at cap % | Latent at cap % (natural, unnatural) | Valleys at cap % |
|---|---|---|---|---|---|
| 7x9 | 86 | 13 28 32 26 | 0.5 | 0.2, 0.1 | 75 |
| 9x14 | 154 | 21 34 29 17 | 7.5 | 1.4, 1.3 | 95 |
| 10x10 | 130 | 16 29 31 23 | 3.2 | 0.9, 0.7 | 90 |
| 8x12 | 131 | 16 28 31 24 | 3.6 | 0.5, 0.5 | 88 |
| 12x16 | 192 | 32 41 20 7 | 8.1 | 2.8, 3.1 | 97 |
| 15x11 | 181 | 26 36 26 12 | 6.2 | 3.5, 3.3 | 96 |
| 15x22 | 197 | 61 35 4 0 | 6.5 | 4.3, 4.4 | 98 |
| 20x20 | 184 | 68 30 2 0 | 5.8 | 5.2, 4.2 | 97 |

Reading: threat buckets are fine at every size. The valley bucket is nearly constant everywhere above 7x9 (harmless, no information). The stage is the size-dependent one: on wide boards two of the four stages are nearly unused. Tests running: one stage only, stages by average pile height (identical to today's on 8x12), 9 row bands.

## External validation

Server export 08, 1184 unique games. `ServerEval`. AUC of "the side to move wins", position weights equal per game within a phase; strong = both players match `Savi|beater`.

| Size, group | hand | m1 | v-wd | Note |
|---|---|---|---|---|
| 15x11 all | 0.907 | 0.867 | 0.870 | 392 games. Most are weak bots; positions far from self-play |
| 15x11 strong | 0.860 | 0.891 | 0.900 | |
| 15x11 strong, phases 1-4 | 0.708 0.829 0.940 0.921 | 0.705 0.901 0.948 0.934 | 0.713 0.900 0.948 0.948 | |
| 15x22 all | 0.822 | 0.758 | 0.757 | 172 games |
| 15x22 strong | 0.610 | 0.489 | 0.466 | only 14 games, no power |
| 8x12 all | 0.888 | 0.915 | 0.915 | |
| 8x12 strong | 0.621 | 0.743 | 0.752 | |

Hand predicts weak-bot games better on large boards: those games reach lopsided positions that self-play avoids. A warning, but matches do not confirm it as a weakness:

| Gate from positions cut out of real server games (16-44 plies, with a Savi or beater bot), 40k nodes | Pairs | Score |
|---|---|---|
| v-wd vs hand, 15x22 | 60 | 69.2% +-8.1 (+140) |
| v-wd vs hand, 15x11 | 60 | 64.6% +-8.5 (+104) |

Move agreement with the Savi line itself (3000-node search, bots named Savi5 to Savi9, excluding ours): 15x22 hand 44.3% v-wd 45.7% (2055 moves); 15x11 hand 50.8% v-wd 56.4% (6045); 8x12 hand 53.9% v-wd 54.3% (2816). Careful: gen003, not v-wd, plays 8x12 in the jar.

## Hold-out baseline: v-wd against hand, sizes it never saw (40k nodes, 25x25 at 30k)

| Size | Pairs | Score | Draws (full board) |
|---|---|---|---|
| 12x16 | 50 | 79.5% +-7.2 (+235) | 3% |
| 9x20 | 50 | 84.0% +-6.5 (+288) | 4% |
| 20x9 | 50 | 65.5% +-9.1 (+111) | 7% |
| 25x25 | 30 | 68.3% +-12.9 (+134) | 0% |
| 7x9 | 50 | 57.0% +-4.9 (+49) | 72% |
| 6x7 | 30 | 53.3% +-3.9 (+23) | 93% |
| pooled | 260 | 69.0% +-3.4 (+139) | |

Tables multiplied by 1.5 against plain v-wd: 15x11 48.4% +-6.9, 15x22 53.3% +-8.7, pooled 50.5% +-5.4. The absolute scale does not matter within this range.

nps on the merged code, hand / v-wd (ratio), one thread, 100k nodes per position: 25x25 1754k / 1299k (x0.74), 9x20 2159k / 1882k (x0.87), 20x9 2164k / 1631k (x0.75), 6x7 3172k / 2791k (x0.88), 15x11 2053k / 1545k (x0.75), 15x22 2122k / 1535k (x0.72), 8x12 2507k / 1854k (x0.74). A table with one stage is not faster (15x11 x0.75, 15x22 x0.72, 8x12 x0.73).

g2a (interim fit, first with gen1 labels; its data includes 7x9, 12x16 and 20x20, so these are not hold-outs for it):

| Against | Size | Pairs | Score |
|---|---|---|---|
| v-wd | 15x11 | 100 | 58.2% +-6.4 (+58) |
| gen003 | 8x12 | 80 | 50.3% +-5.9 (+2) |

The generic table has about caught the sized gen003 on 8x12 at fixed nodes.

## GenericEval direct tables (10:24)

One array per (context, side to move) indexed by slot + raw window code, built once at load (2.5 MB in memory with 18 classes). Same integer sums: `GenericEvalTest` including parity on m1 passes on 4 sizes, `WindowSumTest` and `UploadRulesTest` pass. nps, alternating rounds on a loaded machine, ratio to hand: 15x11 x0.72 -> x0.85, 9x20 x0.87 -> x0.96, 25x25 x0.69 -> x0.76, 15x22 x0.83 -> x0.83, 8x12 x0.88 -> x0.88. Noisy, but not worse anywhere. Adopted, no change in play at fixed nodes.

## Variants against v-wd at 40k nodes (so far)

| Variant | 15x11 | 15x22 | Other |
|---|---|---|---|
| One stage (t-nostage) | 45.9% +-6.2 (80 pairs) | 55.0% +-9.5 (60) | |
| Pile-height stage (t-height) | 49.1% +-7.1 (80) | running | |
| 9 row bands (t-bands9) | 49.4% +-6.9 (80) | running | |

g2a against v-wd: 15x11 58.2% +-6.4 (100 pairs), 15x22 58.4% +-7.1 (80), 9x20 55.0% +-10.5 (50, unseen by both), 20x9 52.0% +-9.6 (50, unseen by both), pooled 56.6% +-4.0 (280 pairs, +46). Labels went to g2a at 10:29.

Variant results, all against v-wd at 40k nodes, same data as v-wd:

| Variant | 15x11 | 15x22 | 20x9 | 9x20 | 25x25 | Pooled | Verdict |
|---|---|---|---|---|---|---|---|
| One stage | 45.9 +-6.2 | 55.0 +-9.5 | 52.5 +-7.8 | 58.8 +-10.8 | | 51.9 +-4.2 (220 pairs) | no measured difference; still to check on small boards |
| Stage by pile height | 49.1 +-7.1 | 51.7 +-9.0 | 52.5 +-10.0 | 55.0 +-10.6 | | 51.5 +-4.4 (220) | no measured difference |
| 9 row bands | 49.4 +-6.9 | running | 45.5 +-9.0 | | 45.0 +-13.6 | | no gain on tall boards, where it should have helped |

So the row-band cap and the stage definition are not what limits play on extreme shapes. Default stays (4 fill stages, 6 bands) unless the small-board check says otherwise.

Filtering training records to the positions the evaluation sees in search (no immediate threat for either side) is not worth testing: 97.7% of records already qualify on every size checked.

## Generation g3 (accepted 11:02): `qc-bot/data/night/eval/generic-g3.qcw`

First fit with the wide size mix and the six hold-out sizes out of it. Data: 16 sets, 3.1M records. Sizes 6x9, 7x11, 7x20, 8x12, 8x18, 9x14, 10x10, 12x12, 15x11, 15x22, 18x10, 20x8, 20x20; hand labels on 10x10, 15x11, 15x22, labels of v-wd elsewhere, gen003 labels on 8x12. wd 1 / 10, K by label spread (1.8), 20 epochs. Quantization max 0.115 logit (20x20). Parity on 5 sizes passes on the current code. Blob 170 kB.

g3 against v-wd, 40k nodes (25x25 at 30k):

| Size | Status | Pairs | Score |
|---|---|---|---|
| 15x11 | trained | 100 | 64.5% +-6.2 (+104) |
| 15x22 | trained | 80 | 65.6% +-7.9 (+112) |
| 8x12 | trained | 60 | 46.2% +-6.7 (-26) |
| 12x16 | hold-out | 50 | 45.5% +-10.4 (-31) |
| 9x20 | hold-out | 50 | 52.0% +-9.6 (+14) |
| 20x9 | hold-out | 50 | 59.5% +-10.2 (+67) |
| 25x25 | hold-out | 30 | 53.3% +-13.2 (+23) |
| 7x9 | hold-out | 50 | 49.0% +-3.4 (-7) |
| 6x7 | hold-out | 30 | 50.8% +-2.9 (+6) |
| hold-outs pooled | | 260 | about 51.6%: no gain, no loss |

g3 against gen003 on 8x12, 40k nodes, 100 pairs: 51.5% +-5.2. No measurable difference at fixed nodes: the generic table has caught the sized one there.

## GPU incident and CPU fits (11:12 to 11:32)

- 11:12:11 driver errors while one GPU fit (g3-nohand) ran and the user's Ollama also computed on the GPU. The watchdog stopped the fit; g3-nohand and the queued g3-srv were lost.
- My mistake: the first CPU fallback set the device variable to an empty string, which does not hide the GPU on Windows. A "CPU timing fit" ran on the GPU from 11:16:42 until I noticed `nvidia-smi` listing it at 11:18:22 and killed it at about 11:18:40; at that moment the driver logged id 153 (11:18:41). Possibly overlapping with Ollama for part of that time. Reported to the coordinator. GPU off since.
- Fixed in 1655f28: fits take the device from `QC_FIT_DEVICE`; `qctrain.budget` uses the GPU only if `gpu-stop` is absent and `nvidia-smi` lists no compute program. `nvidia-smi` lists nothing during forced-CPU fits.
- Checkpoint and resume: a fit killed with `kill -9` after 2 of 5 epochs, restarted, ended with the tables of an uninterrupted fit (max difference 1.5e-6, same validation loss). CPU only.
- CPU epoch: 145 s and 159 s on 8 threads, Below Normal, 33 sets, 3.5M records, about 430 batches; about 5x the GPU's time.

40 epochs instead of 20 (g3-e40) against g3, 40k nodes: 15x11 56.2% +-6.5, 15x22 49.6% +-8.3, 12x16 45.6% +-10.8, 20x9 55.0% +-11.0, pooled 52.3% +-4.3 (220 pairs). No clear gain.

## Jars for the gen003 question

Same code (night-eval e8aea6f, which contains night-main cc1a91f), generic tables g3 in both; the only difference is the sized 8x12 table:
- `qc-bot/dist/night/eval-e8aea6f-g3-sized.jar` (with gen003, 2.05 MB)
- `qc-bot/dist/night/eval-e8aea6f-g3-nosized.jar` (without, 1.29 MB; 8x12 uses g3)

On e8aea6f: parity on 5 sizes passes for g3, and `UploadRulesTest` and `BoardSizesTest` pass.

## g3 against hand at other node budgets (time-limit independence), merged code with LMR

| Nodes | 15x11 | 9x20 (hold-out) | 15x22 |
|---|---|---|---|
| 10k | 76.9% +-8.7 (+209, 40 pairs) | 93.1% +-5.3 (+453, 40) | 85.0% +-8.3 (+301, 30) |
| 150k | 70.0% +-13.1 (+147, 20) | 76.2% +-11.5 (+203, 20) | 66.7% +-20.7 (+120, 15) |

Pooled 80.5% +-4.2 (165 pairs). The gain stays at about the node count of a real 100 ms move, and it is larger at low node counts. Few pairs at 150k.

## Warm-start candidate g4w against g3 (40k nodes; 25x25 at 30k; merged code with LMR)

42 sets, 3.5M records, including all new wide sizes; warm from g3, 10 epochs, 250 batches each, lr 3e-4 to 1e-5, on the CPU (about 30 minutes).

| Size | Status | Pairs | Score |
|---|---|---|---|
| 15x11 | trained | 100 | 54.5% +-6.4 (+31) |
| 15x22 | trained | 80 | 57.5% +-8.4 (+53) |
| 8x12 | trained | 60 | 50.4% +-5.1 (+3) |
| 12x16 | hold-out | 50 | 56.5% +-8.3 (+45) |
| 9x20 | hold-out | 50 | 55.5% +-9.0 (+38) |
| 20x9 | hold-out | 50 | 53.5% +-9.9 (+24) |
| 25x25 | hold-out | 30 | 66.7% +-11.8 (+120) |
| 7x9 | hold-out | 50 | 53.0% +-4.6 (+21) |
| 6x7 | hold-out | 30 | 52.5% +-2.7 (+17) |
| hold-outs pooled | | 260 | 55.8% +-3.5 (+41) |
| all but 8x12 pooled | | 440 | 55.8% +-2.9 (+41) |

g4w against gen003 on 8x12: 50.7% +-4.9 (100 pairs). The 8x12 line of the first gate was overwritten in its log by a tag clash, fixed; the numbers above come from the monitor and the game files.

## Takeover at 12:45 (new eval agent; the old one was stopped at 12:26)

g4 is a fresh fit (20 epochs, lr 1e-3 to 1e-5, GPU, 12 min), not warm-started, on 42 sets; g4w was a warm fit on 34 sets. Both have the hold-out sizes out.

g4 against g4w, 40k nodes (25x25 at 30k), gatectl run that finished at 12:38:

| Size | Status | Pairs | Score |
|---|---|---|---|
| 15x11 | trained | 100 | 55.0% +-6.5 (+35) |
| 15x22 | trained | 80 | 56.6% +-7.4 (+46) |
| 12x16 | hold-out | 50 | 57.0% +-9.7 (+49) |
| 9x20 | hold-out | 50 | 51.5% +-10.1 (+10) |
| 20x9 | hold-out | 50 | 50.5% +-8.0 (+3) |
| 25x25 | hold-out | 30 | 55.0% +-10.9 (+35) |
| pooled | | 360 | 54.5% +-3.4 (+31) |

Its lower bound clears 50%, so g4 replaces g4w as the current best, pending 8x12, 7x9 and 6x7 (running). Published as `qc-bot/data/night/eval/generic-g4.qcw`. From 12:47 self-play plays with g4 (prev g4w) and writes to `gen5/`.

All jobs from here run on the E-cores only (mask 0xFF000), via `scripts/gate-e.ps1` and `scripts/fit-e.ps1`. `qctrain.gatectl --pause` is no longer used because it kills by a process filter; gates run at Normal priority inside the E-cores, so they go ahead of self-play (Below Normal).

## gen003 question, first number

g4 against gen003 (`weights/eval.qcw`, same bytes as `qc-bot/data/models/gen003/eval.qcw`) on 8x12, 40k nodes, 300 pairs: 54.5% +-2.7 (+31). {six 256, blast 28, full 316}. The generic table is now ahead of the sized one at fixed nodes, with the lower bound above 50%.

g4 against g4w on the remaining sizes, 40k nodes:

| Size | Status | Pairs | Score |
|---|---|---|---|
| 8x12 | trained | 200 | 56.6% +-3.3 (+46) |
| 7x9 | hold-out | 100 | 51.2% +-2.5 (+9) |
| 6x7 | hold-out | 60 | 48.3% +-2.8 (-12) |
| pooled | | 360 | 53.8% +-2.1 (+26) |

g4 is the current best. On 6x7, 90% of games are full-board draws, so this is noise around 50%. First candidates: g5w (warm from g4, 10 epochs, 250 batches, lr 3e-4 to 1e-5, GPU, 3 min 5 s, on 78 sets including the g4w self-play in `gen4/`) and g5f (fresh, 20 epochs, same data).

Trap: sizes given to `gate-e.ps1` must be quoted, or PowerShell turns the commas into an array and the gate fails with a ValueError.

## g5w against g4, 40k nodes (25x25 at 30k), new openings (seed 1)

| Size | Status | Pairs | Score |
|---|---|---|---|
| 15x11 | trained | 200 | 53.4% +-4.5 (+23) |
| 15x22 | trained | 120 | 58.1% +-6.2 (+57) |
| 8x12 | trained | 200 | 48.8% +-3.0 (-9) |
| 12x16 | hold-out | 100 | 52.8% +-6.5 (+19) |
| 9x20 | hold-out | 100 | 54.0% +-5.5 (+28) |
| 20x9 | hold-out | 100 | 59.2% +-6.5 (+65) |
| 25x25 | hold-out | 60 | 55.0% +-8.3 (+35) |
| 7x9 | hold-out | 100 | 47.0% +-3.0 (-21) |
| 6x7 | hold-out | 60 | 50.4% +-2.5 (+3) |
| hold-outs pooled | | 520 | 53.1% +-2.4 (+22) |
| trained pooled | | 520 | 52.7% +-2.6 (+19) |
| all pooled | | 1040 | 52.9% +-1.7 (+20) |

The pooled lower bound clears 50%. The gain comes from the large boards; 8x12 and 7x9 lean slightly negative. Self-play moved to g5w at 13:03 (writes to `gen6/`, prev g4).

## g5f (fresh fit, 20 epochs, 81 sets, 4.2M records, GPU, 15 min) against g5w, 40k nodes (25x25 at 30k), new openings (seed 201)

| Size | Status | Pairs | Score |
|---|---|---|---|
| 15x11 | trained | 200 | 57.0% +-4.7 (+49) |
| 15x22 | trained | 120 | 47.5% +-6.2 (-17) |
| 8x12 | trained | 200 | 51.7% +-3.1 (+12) |
| 12x16 | hold-out | 100 | 51.5% +-6.9 (+10) |
| 9x20 | hold-out | 100 | 52.5% +-6.5 (+17) |
| 20x9 | hold-out | 100 | 47.5% +-6.2 (-17) |
| 25x25 | hold-out | 60 | 46.7% +-8.7 (-23) |
| 7x9 | hold-out | 100 | 52.0% +-2.6 (+14) |
| 6x7 | hold-out | 60 | 52.5% +-2.2 (+17) |
| hold-outs pooled | | 520 | 50.6% +-2.5 (+4) |
| trained pooled | | 520 | 52.8% +-2.6 (+19) |
| all pooled | | 1040 | 51.7% +-1.8 (+12) |

The pooled lower bound is 49.9%, so this does not clear 50% on its own: g5f and g5w are about equal, with g5f ahead on 15x11 and the small boards and behind on the widest boards. Self-play moved to g5f (prev g5w) at 13:18, still writing to `gen6/`. No gates until the next fit, so self-play gets the E-cores.

## Generation 6 (13:48 to 13:54)

`gen6/` holds 75 chunks of self-play with g5w, then g5f. Self-play (pid 19328 and its child) was stopped by PID at 13:54 once g6f had its data list. Candidates: g6w (warm from g5f, 10 epochs, 400 batches, lr 3e-4 to 1e-5, 119 sets, 5.6M records, GPU, 5 min) and g6f (fresh, 20 epochs, all data). GPU memory 4.6 GB, 70 C, no driver errors.

## g6w against g5f, 40k nodes (25x25 at 30k), new openings (seed 101)

| Size | Status | Pairs | Score |
|---|---|---|---|
| 15x11 | trained | 200 | 49.4% +-4.3 (-4) |
| 15x22 | trained | 120 | 55.6% +-6.6 (+39) |
| 8x12 | trained | 200 | 48.0% +-3.2 (-14) |
| 12x16 | hold-out | 100 | 61.3% +-6.5 (+80) |
| 9x20 | hold-out | 100 | 58.5% +-6.4 (+60) |
| 20x9 | hold-out | 100 | 53.8% +-6.2 (+26) |
| 25x25 | hold-out | 60 | 49.2% +-8.9 (-6) |
| 7x9 | hold-out | 100 | 48.5% +-2.8 (-10) |
| 6x7 | hold-out | 60 | 50.8% +-2.0 (+6) |
| hold-outs pooled | | 520 | 54.2% +-2.5 (+29) |
| trained pooled | | 520 | 50.3% +-2.6 (+2) |
| all pooled | | 1040 | 52.3% +-1.8 (+16) |

The pooled lower bound clears 50%, so g6w is the current best, published as `generic-g6w.qcw`. Both warm fits gained on the mid and large boards and leaned slightly negative on 8x12 and 7x9; the fresh fit g5f did the reverse. So far nothing measured is a clear loss.

## g6w at other measures

g6w against gen003 on 8x12, 40k nodes, 400 pairs: 54.1% +-2.5 (+29).

g6w against g3 (the tables in rc3), 150k nodes, about what a real move searches:

| Size | Status | Pairs | Score |
|---|---|---|---|
| 15x11 | trained | 60 | 70.0% +-7.9 (+147) |
| 15x22 | trained | 40 | 73.8% +-9.9 (+179) |
| 8x12 | trained | 100 | 51.5% +-4.8 (+10) |
| 12x16 | hold-out | 30 | 61.7% +-11.2 (+83) |
| 20x9 | hold-out | 30 | 61.7% +-8.0 (+83) |
| pooled | | 260 | 61.5% +-3.5 (+82) |

The gain over the tables in rc3 holds at real-play node counts on 15x11 and 15x22. On 8x12 there is no measurable difference from g3 at 150k.

g6w against gen003 on 8x12, 150k nodes, 200 pairs: 55.9% +-3.5 (+41). With the 40k result (54.1% +-2.5), the sized table is behind the generic one at both node counts.

## g6f and g6a (14:15)

g6f: fresh fit, 20 epochs, 119 sets, 5.6M records, GPU, 20 min. This was the last GPU job; the watchdog stayed quiet. g6a: the mean of the parameters of g6w and g6f, exported on the CPU by `scripts/avg.py` (re-exporting g6w alone gives the same bytes as its own blob). Warm and fresh fits have been strong on different sizes, so the mean is worth one gate. No new fit is planned: self-play has stopped.

## g6f against g6w, 40k nodes (25x25 at 30k), new openings (seed 51)

| Size | Status | Pairs | Score |
|---|---|---|---|
| 15x11 | trained | 200 | 59.4% +-4.6 (+66) |
| 15x22 | trained | 120 | 50.0% +-6.2 (0) |
| 8x12 | trained | 200 | 53.4% +-3.1 (+23) |
| 12x16 | hold-out | 100 | 54.5% +-7.0 (+31) |
| 9x20 | hold-out | 100 | 52.8% +-6.6 (+19) |
| 20x9 | hold-out | 100 | 58.5% +-6.8 (+60) |
| 25x25 | hold-out | 60 | 47.5% +-9.4 (-17) |
| 7x9 | hold-out | 100 | 52.2% +-2.7 (+16) |
| 6x7 | hold-out | 60 | 48.8% +-1.8 (-9) |
| hold-outs pooled | | 520 | 53.0% +-2.6 (+21) |
| trained pooled | | 520 | 54.9% +-2.6 (+34) |
| all pooled | | 1040 | 54.0% +-1.8 (+28) |

The lower bound is 52.2%. g6f is the current best, published as `generic-g6f.qcw`. Three results now show a fresh fit on the new data beating the warm fit on the same or nearly the same data: g4 over g4w, g5f over g5w (not significant), g6f over g6w.

g6a (mean of g6w and g6f) against g6w, 40k nodes, same openings as the g6f line: 15x11 59.6% +-4.2, 15x22 54.0% +-6.1, 8x12 52.2% +-3.1, 12x16 51.7% +-6.4, 9x20 48.2% +-6.4, 20x9 56.2% +-6.2, 25x25 48.3% +-9.3, 7x9 53.0% +-3.1, 6x7 51.2% +-1.8; pooled 53.6% +-1.7 (+25, 1040 pairs), hold-outs 51.7% +-2.4, trained 55.5% +-2.5. That is the same as g6f's 54.0% +-1.8, so averaging adds nothing measurable. Dropped; g6f stays.

## Final result of the eval stream (14:45)

**Ship `qc-bot/data/night/eval/generic-g6f.qcw`** as `weights/eval-generic.qcw` (175075 bytes, sha256 c6a433f4...). It is a fresh fit, 20 epochs, not warm-started: on this data each fresh fit beat or matched the warm one. Hold-out sizes stayed out of every fit.

Parity on g6f: 4000 records each on 15x11, 15x22, 8x12, 20x20, 7x20, integer sums equal; max |int - float| 0.174 logit (20x20). `GenericEvalTest` and `UploadRulesTest` pass on night-eval.

g6f against g3 (the tables in rc3), direct:

| Size | Status | Nodes | Pairs | Score |
|---|---|---|---|---|
| 15x11 | trained | 150k | 60 | 72.5% +-7.2 (+168) |
| 15x22 | trained | 150k | 40 | 72.5% +-9.9 (+168) |
| 8x12 | trained | 150k | 100 | 55.0% +-4.7 (+35) |
| 12x16 | hold-out | 150k | 30 | 72.5% +-10.6 (+168) |
| 20x9 | hold-out | 150k | 30 | 67.5% +-11.3 (+127) |
| 9x20 | hold-out | 40k | 100 | 71.0% +-6.2 (+156) |
| 25x25 | hold-out | 30k | 60 | 55.8% +-9.3 (+41) |
| 7x9 | hold-out | 40k | 100 | 53.0% +-2.8 (+21) |
| 6x7 | hold-out | 40k | 60 | 51.2% +-2.2 (+9) |
| hold-outs pooled | | | 380 | 60.6% +-2.8 (+75) |
| all pooled | | | 580 | 61.7% +-2.3 (+83) |

Line of accepted generations, all 40k nodes, all sizes: g4 over g4w 54.1% +-2.0; g5w over g4 52.9% +-1.7; g5f over g5w 51.7% +-1.8; g6w over g5f 52.3% +-1.8; g6f over g6w 54.0% +-1.8. g6f was not played directly against g4 or g4w.

**gen003 verdict:** the sized table is no longer better on 8x12 at fixed nodes. g6f against gen003: 55.4% +-2.5 (+38, 400 pairs, 40k nodes), 57.5% +-3.2 (+53, 200 pairs, 150k nodes). Delete it unless the real-protocol gate says otherwise.

Not verified: real time; any opponent outside our own self-play line, so expect smaller gains against other bots; g6f's nps (same table layout as g3); the gates ran on frozen classes `bin-rc` (night-main cc1a91f with LMR), not the final merged search, Crop included; `QcBotRobustnessTest` on 6x7 and 7x6 without the sized table.

GPU: four fits (g5w, g5f, g6w, g6f), one at a time through `qctrain.budget`, all on the GPU, no driver events since 11:45, `gpu-stop` absent. Self-play and all jobs of this stream have stopped.
