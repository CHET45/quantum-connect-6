# Search stream results

Branch `night-search`, worktree `night/wt-search`. Started 2026-11-04 01:20.

Tools: `ch.qc.tools.SearchBench` (fixed-depth node counts on 300 self-play positions per size, `qc-bot/data/night/search/bench-*.txt`, 13 plies apart), `FastMatch` (fixed nodes, `-nodeRule` so the whole budget is used as with a clock), `MatchMain` through `rtrun.py` (real time).

## Tree facts (fixed-depth bench, base 3.1)

| Size, depth | Nodes per position | Leaf evals share | First-move cuts | Odd-even swing of the root score |
|---|---|---|---|---|
| 15x22, 5 | 20,300 | 79% | 91.9% | mean abs 188 (d3 to d4), 103 (d3 to d5) |
| 15x11, 7 | 41,000 | 67% | 90.2% | mean abs 157, 113 |
| 8x12, 8 (gen003) | 86,000 | 60% | 95.8% | mean abs 50, 37 |

- Ordering is already close to the limit: 90-96% of cuts come on the first move, and the tree is about 3x the minimal tree on 15x22.
- The hand evaluation swings by 100-190 units between odd and even depths. A reduction by one ply lands on the other parity, so the quick `lmr` form compared scores across the swing. Aspiration windows centred on the previous iteration suffer from it too.

## Bug found

`pvsChild` passed `pv = true` to the first child of every node, null-window nodes included. Those children had TT cutoffs switched off and, since most leaf evaluations came below them, would have been exempt from any non-PV pruning. Fix: `pv` only when the window is wider than one (`pvFix`).

| Size | Nodes vs base at fixed depth | Same score | Same move |
|---|---|---|---|
| 15x22, d5 | 0.976 | 100% | 100% |
| 15x11, d7 | 0.962 | 100% | 100% |
| 8x12, d8 | 0.954 | 100% | 100% |

Verdict: accepted without games, it changes no value at fixed depth. Commit on `night-search`.

## Ordering screens (fixed depth, nodes against base, geometric mean)

| Switch | 15x22 d4 | 15x11 d6 | 8x12 d7 | Verdict |
|---|---|---|---|---|
| history by cell | 0.999 | 0.993 | 0.997 | no effect |
| keep history across moves | 1.000 | 1.000 | 1.000 | bench has a fresh start per position, not measured here |
| history malus | 1.000 | 0.999 | 0.998 | no effect |
| countermove | 1.001 | 1.002 | 0.998 | no effect |
| hand window values in place of squares | 0.991 | 0.991 | 0.993 | under 1% |
| IID at depth 4 | 1.000 | 1.003 | 1.005 | none |
| aspiration +-50 | 1.059 | 1.021 | 1.010 | worse, the parity swing |
| root order by subtree nodes | 0.995 | 0.980 | 0.991 | small |
| ETC at depth 2 | 0.981 | 0.936 | 0.934 | pure, up to 7% |

## Work log

(see below; updated as runs finish)

- 02:15 merged `night-speed` (04af256): nodes per second up 7x (about 1.9 M/s hot). Fixed-depth and fixed-node screens from here on use the merged code. Node budget for screens: 40,000 (about 20 ms hot), `-nodeRule`.
- Lazy move scoring (`lazyOrder`, on): the TT move and killers are tried before any other move is scored. Identical node counts. Speed +15-18% after the merge.
- Leaf value cache (`evalCacheBits`): 22-32% hits and +8% before the merge; after the merge no gain (evaluation is incremental). Off, to be removed.
- ETC at depth 2 after the merge: fewer nodes (0.91-0.95) at fixed depth, identical scores, but at a fixed node budget depth moves only 0.02-0.05. To be decided.
- Earlier fixed-node results on the old code (12,000 nodes, 200 pairs, 15x11): `lmrBlast=2` +42 [+12, +73]; `lmp=5` +29 [-1, +59]. Superseded by the merged code below.

## Merged code, fixed nodes, 40,000 per move, 200 pairs, 15x11 (blast share of all games in brackets)

| Switch against base | Elo [95% CI] | Blast-decided | Note |
|---|---|---|---|
| `lmrMode=2,lmr=4` (reduce late quiet drops by 2, parity kept) | +23 [-9, +55] | 178/400 | |
| `lmrMode=2,lmr=2` | +36 [+4, +69] | 146/400 | |
| `lmrMode=3,lmr=2` (also by 4 for index >= 6, depth >= 5) | +27 [-4, +58] | 168/400 | |
| `lmrMode=2,lmr=1` | +3 [-31, +36] | 184/400 | too much |
| `wd=0` (winning-blast scan at leaves too) | +65 [+32, +99] | 136/400 | see below |
| `wd=0` + `leafBlastThreat` against `wd=0` | +9 [-21, +39] | 133/400 | no clear gain |

`wd=0` undoes a choice of 3.1, made when the scan was about 100x slower. Scanning after the evaluation (`lazyLeafBlast`: a leaf at beta needs no scan, because the scan can only raise its value) keeps node counts identical to the eager scan and costs 3-4% speed instead of 22-25%.

## LMR audit (fixed depth, `wd=0,lmrMode=2,lmr=2`): each reduced move that stayed below alpha is searched again in full

Missed = the full search would have beaten alpha.

| Group | 15x11 d7, missed | 15x22 d5, missed |
|---|---|---|
| all reduced moves | 1.96% | 1.27% |
| same column as the last move | 6.40% | 5.26% |
| own window with 4+ discs | 4.92% | 1.26% |
| own window with 3 discs | 4.75% | 2.38% |
| opponent window with 4+ discs | 5.28% | 6.74% |
| opponent window with 3 discs | 4.01% | 2.75% |
| cell above is an own threat | 0.25% | 0.36% |
| potential >= 40 | 4.39% | 6.35% |
| move index 0-2 / 3-5 / 6-8 / 9+ (d8, d6) | 3.45 / 2.16 / 1.22 / 0.57% | 3.28 / 2.63 / 1.37 / 0.63% |

No clear pattern by depth. So index and those features are what to use.

## Results from before the crash (hand evaluation on large boards, 40,000 nodes, 200 pairs)

| Test | Size | Elo [95% CI] | Blast-decided |
|---|---|---|---|
| `wd=0` against base | 15x22 | +67 [+38, +97] | 235/400 |
| `lmrMode=2,lmr=3` against `wd=0` | 15x11 | +45 [+13, +78] | 129/400 |
| `lmrMode=2,lmr=2,lmrTact=2` against `wd=0` | 15x11 | +69 [+38, +100] | 128/400 |

`lmrTact=2` means no reduction for a drop in the column of the last move or into a window holding 4+ discs of one colour.

## After the crash: merged on night-main (8f31c96), generic tables off 8x12, gen003 on 8x12

Screens use the tables that ship: `weights/eval-generic.qcw`, `weights/eval.qcw` on 8x12. With the generic tables the odd-even swing is much smaller than with the hand evaluation (mean abs 39-59 between d4 and d5, 24-30 between d4 and d6 on 15x11 and 15x22).

Rejected on the bench with these tables too, under 1% of nodes either way at fixed depth: aspiration +-40 and +-100, IID at depth 5, root order by subtree nodes. ETC under LMR: 1-6% fewer nodes, 6-8% slower per node, no net gain. All removed from the code.

### `wd=0` against base on merged code, fixed nodes (40,000), 200 pairs, `-nodeRule`

| Size | Tables | Elo [95% CI] | Blast-decided |
|---|---|---|---|
| 15x11 | generic | +10 [-18, +39] | 117/400 |
| 8x12 | gen003 | +10 [-8, +29] | 16/400 |
| 15x22 | generic | +79 [+50, +109] | 205/400 |

With the shipped tables the gain on 15x11 is far smaller than with the hand evaluation (+65). Most of it is on 15x22, where blasts decide half of the self-play games, so this part may not transfer to the server (blast share there 5%). Speed cost 3-4%.

Also tested: transposition table size at a long budget (1,000,000 nodes, 15x11, 24 positions): mean depth 12.42 / 12.58 / 12.63 / 12.50 for 2^16 / 18 / 20 / 22 entries; at 150,000 nodes 10.95 / 11.05 / 11.08 for 2^16 / 18 / 20. The bot's 2^18 is enough at any budget measured.

Root threat-search caps: the time caps (10 ms, 5 ms) are now 1/8 and 1/16 of the budget of the call and the node caps scale with it, same values as before at 80 ms (commit a6ae689). On average a root threat search uses only about 30 nodes, so these caps bind rarely.

Tie-breaker for `wd=0`, from the coordinator: Savi9's only two server losses in 63 games (a0eb728d, 81eb4b94) both ended with a blast by 3.1, which hints that Savi9 may misjudge blast combinations, so seeing blast wins sooner may be worth more against it than self-play shows. Two games prove nothing alone, so this only decides the case where `wd=0` is neutral at worst across sizes; nothing that measurably loses is accepted because of it.

### LMR (`lmrMode=2,lmr=2,lmrTact=2`) against `wd=0`, fixed nodes (40,000), shipped tables

| Size | Pairs | Elo [95% CI] | Blast-decided |
|---|---|---|---|
| 15x11 | 200 | +64 [+33, +97] | 107/400 |
| 8x12 | 200 | +58 [+37, +79] | 8/400 |
| 15x22 | 200 | +80 [+47, +113] | 209/400 |
| 6x7 | 200 | +4 [-4, +11] | 1/400, 95% draws |
| 20x9 | 150 | +31 [-3, +66] | 81/300 |
| 25x25 | 80 | +26 [-26, +80] | 77/160 |

LMR accepted at fixed nodes, commit 355bff2. The first real-time gate died at startup (missing logging jars on the classpath), so it has to wait for the lock again.

### `wd=0` on the extreme sizes (fixed nodes, 40,000)

| Size | Pairs | Elo [95% CI] | Blast-decided |
|---|---|---|---|
| 6x7 | 200 | -1 [-5, +3] | 0/400 |
| 20x9 | 150 | +7 [-29, +43] | 98/300 |
| 25x25 | 80 | +75 [+29, +123] | 93/160 |

Verdict: accepted, default on (`blastWinMinDepth` 0). Neutral at worst on every size and clearly up on the widest boards.

### LMR parameters, fixed nodes (40,000), 15x11, 200 pairs each, against `lmr=2,lmrTact=2`

| Variant | Elo [95% CI] | Verdict |
|---|---|---|
| `lmr=1,lmrTact=2` | -17 [-49, +15] | no gain |
| `lmr=3,lmrTact=2` | +2 [-26, +30] | same, keep 2 |
| `lmr=2,lmrTact=3` | -20 [-49, +9] | no gain |
| `lmrMode=3` (also by 4 from index 6, depth >= 5) | +7 [-24, +38] | no clear gain, simpler form kept |

### Move quality on real server positions (games with a Savi bot, export 08)

Each configuration picks a move at 150,000 nodes on 200 positions per size. A full-width deep search (1,000,000 nodes, no LMR) then scores each picked move, and loss is against the best of them, in 1/256 logit, clipped at +-1000. Lower is better; differences are against `lmr=0,wd=1` with a 95% interval.

| Size | `lmr=0,wd=1` | `lmr=0,wd=0` | LMR, `wd=1` | LMR, `wd=0` | LMR with `wd=0`, same as deep |
|---|---|---|---|---|---|
| 15x11 | 14.6 | 13.1 (-1.5 +-4.4) | 21.8 (+7.2 +-11.4) | 20.9 (+6.2 +-11.1) | 80.0% (base 75.5%) |
| 15x22 | 13.1 | 12.8 (-0.3 +-0.5) | 9.2 (-3.9 +-8.2) | 9.1 (-4.0 +-8.4) | 83.0% (base 72.0%) |
| 8x12 | 8.5 | 7.9 (-0.6 +-1.2) | 4.8 (-3.7 +-4.5) | 3.9 (-4.6 +-4.2) | 80.5% (base 71.5%) |

This oracle is weak for LMR, because the LMR search at 150,000 nodes already reaches about the oracle's depth. On 15x11 the mean is decided by 3-4 positions out of 200 where the LMR search missed a long forced line (loss 400 or more in 1.5% of positions, base 0.5-1%). It raises the risk of missing a deep forced win on that size, but it is not significant, and the games say +64.

LMR at a long budget: 160,000 nodes, 15x11, 100 pairs: +52 [+11, +95], 51/200 blast-decided.
