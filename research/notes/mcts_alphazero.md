# MCTS variants and AlphaZero-style approaches for Connect Four (and similar games) vs alpha-beta

Context for the report writer: target is "Quantum Connect" (Java, std-lib only, gravity drop, 8x12 board, connect-4, 100 ms hard limit per move, a periodic "special move" that destroys discs around the drop point). Source-quality notes are inline. Several numbers come only from search-engine snippets (marked "snippet only"), because the primary page could not be fetched; treat those as lower confidence.

## 1. MCTS/UCT enhancements that matter for Connect-Four-like games, and MCTS vs alpha-beta in Connect Four (incl. shallow traps)

### Takeaway
Plain UCT with random playouts is tactically weak in Connect Four: the game is full of shallow traps (a forced loss within a few plies), which a selective sampling search misses and full-width alpha-beta does not. The fixes that measurably help are the "tactical" ones: MCTS-Solver (propagate proven wins and losses), shallow minimax inside rollouts (2 ply was best, +23 points win rate), decisive and anti-decisive move rules, and implicit minimax backups when a heuristic evaluation exists. With those added, MCTS is competitive, but there is no clean published result showing MCTS beating a well-tuned alpha-beta in Connect Four at sub-second time controls.

### Cited Findings
**Shallow traps (the core weakness)**
- Ramanujan, Sabharwal & Selman, "On Adversarial Search Spaces and Sampling-Based Planning", ICAPS 2010. A level-k search trap is a position where the opponent has a forced win within k plies. Such shallow traps are rare in Go but frequent in Chess, even in grandmaster games. UCT tends to miss traps that full-width minimax detects, which helps explain why minimax still wins in Chess while UCT wins in Go. — [ICAPS 2010 (AAAI OJS)](https://ojs.aaai.org/index.php/ICAPS/article/view/13437); [PDF](https://cdn.aaai.org/ojs/13437/13437-40-16955-1-2-20201228.pdf)
- Ramanujan's 2012 Cornell dissertation (Chess, Mancala): trap distribution differs strongly between games and largely determines UCT's performance. Systematic biases in a heuristic can make UCT "freeze" prematurely onto suboptimal lines. — [Cornell eCommons](https://ecommons.cornell.edu/entities/publication/25e34c67-f78e-4e5a-b294-6fc05fff66c5)
- Baier & Winands, "Monte-Carlo Tree Search and Minimax Hybrids" (Maastricht). Setup: 1000 self-play games of MCTS-Solver at 50,000 rollouts per move. At each turn they checked whether a level-3 trap existed for the player to move. Shallow traps occur throughout both Connect-4 (7x6) and Breakthrough. In Connect-4 the trap density differs significantly between the two players. — [Baier & Winands PDF](https://dke.maastrichtuniversity.nl/m.winands/documents/paper%2049.pdf)

**MCTS-minimax hybrids in Connect-4 (Baier & Winands)**
Conditions: 7x6 board, 1 s per move, MCTS-Solver baseline, alpha-beta with no evaluation function (it only detects terminal wins and losses), 1000 to 5000 games per condition. — [PDF](https://dke.maastrichtuniversity.nl/m.winands/documents/paper%2049.pdf)
- Baseline MCTS-Solver speed: about **91,000 simulations/s**, averaged over a whole game. The paper does not state language or hardware.
- **Minimax in rollouts (MCTS-MR-d), i.e. "heavy" playouts**
  - Speed relative to the baseline on the start position: MR-1 about 65%, MR-2 about 18%, MR-3 about 6%, MR-4 about 2%.
  - **MR-2 was the best condition: 73.2% win rate vs the baseline over 2000 games (p<0.001)**, at about 30,000 simulations/s.
  - MR-1 was *worse* than the baseline: 35.7% over 1000 games, even with equal iterations (100k).
- **Minimax at selection/expansion (MCTS-MS-d-Visit-v)**
  - The alpha-beta window was set so that the search only detects forced losses (traps).
  - Best condition: MS-2-Visit-2, with **53.4%** over 5000 games, at about 83,000 sims/s.
- **Minimax in backpropagation (MCTS-MB-d)**
  - Best condition: MB-2, with **52.1%** over 5000 games (p<0.05), at about 90,000 sims/s.
- In Breakthrough, minimax in rollouts was much more expensive than in Connect-4. MR-1 ran at about 7.3% of baseline speed and MR-2 at about 1.3%. Cheap move generation is what makes rollout minimax viable in Connect-4.

**MCTS-Solver (Winands, Björnsson & Saito 2008)**
- MCTS-Solver changes backpropagation and selection so that proven wins and losses propagate as game-theoretic values. Given enough time it can prove a position's value.
- In Lines of Action, a program using MCTS-Solver beat plain MCTS with a **65% winning score**. It also did much better than plain MCTS against several versions of the world-class alpha-beta program MIA.
- The journal version is Winands, Björnsson & Saito, IEEE TCIAIG 2(4), 2010.
- Sources: [Maastricht CRIS](https://cris.maastrichtuniversity.nl/en/publications/monte-carlo-tree-search-solver/); [TCIAIG 2010 PDF](https://staff.ru.is/yngvi/pdf/WinandsB10a.pdf)

**Implicit minimax backups (Lanctot, Winands, Pepels & Sturtevant 2014, arXiv:1406.0486)**
How it works: each node stores two values, the playout win rate and a minimax-backed-up heuristic evaluation. Selection uses a mix weighted by α. Unless stated otherwise, the results below are at 1 s per move. — [ar5iv HTML](https://ar5iv.arxiv.org/html/1406.0486); [arXiv](https://arxiv.org/abs/1406.0486)
- Breakthrough, MCTS-IM vs plain MCTS:
  - im0.6 alone: **63.3%**
  - With node priors: 77.9%
  - Implicit-minimax variants on improved-playout baselines: up to **87.2%**
- Breakthrough, against alpha-beta (efMS evaluator):

  | Time per move | Plain MCTS | MCTS-IM (α=0.4) |
  |---|---|---|
  | 1 s | 27.6% | **45.1%** |
  | 5 s | 39.0% | **61.6%** |
  | 10 s | 47.6% | **61.8%** |

- Lines of Action, against the alpha-beta program MIA at 5 s: implicit minimax raised MCTS from 40.0% to **51.0%**.
- Across all tested cases, MCTS's score against alpha-beta rose with thinking time. Recommended α: 0.15 to 0.4.

**Decisive and anti-decisive moves**
- Teytaud & Teytaud, "On the Huge Benefit of Decisive Moves in Monte-Carlo Tree Search Algorithms", IEEE CIG 2010. — [Olivier Teytaud, chessprogramming wiki](https://chessprogramming.org/Olivier_Teytaud)
  - The rule: in rollouts and in the tree, play an immediately winning move if one exists, and otherwise block the opponent's immediate win.
  - I could not retrieve the paper's own numbers.
- A 2024 secondary source (AlphaViT) says decisive-move heuristics "substantially" improve MCTS, often by "one to two orders of magnitude" in strength. — [AlphaViT, Fujita, arXiv:2408.13871](https://arxiv.org/pdf/2408.13871)

**Head-to-head numbers: MCTS vs alpha-beta in Connect Four**
- Scheiermann & Konen (arXiv:2204.13307, published in IEEE Transactions on Games 2023 per citing papers) ran a Connect-4 tournament.
  - Plain MCTS (UCT, random playouts, 10,000 iterations) had an overall won-game rate of only **19.8%**.
  - The tournament included the near-perfect alpha-beta agents AB and AB-DL, which use an opening book.
  - As second player against MCTS, AB-DL won 80% of games.
  - Source: [arXiv HTML v3](https://arxiv.org/html/2204.13307v3)
- **Citation warning:** AlphaViT (Fujita, arXiv:2408.13871) cites this as "UCT with 10000 random simulations achieved only a 19.8% win rate against a depth-8 α–β opponent". The original paper does not report a depth-8 alpha-beta match. **19.8% is the overall tournament won-game rate.** Do not repeat the "depth-8" wording. — [AlphaViT](https://arxiv.org/pdf/2408.13871) vs [Scheiermann & Konen HTML](https://arxiv.org/html/2204.13307v3)
- A KTH bachelor thesis (2021) on **3D** Connect-4 found alpha-beta "clearly" stronger than MCTS at sub-second thinking times. It is snippet only and a different game variant. — [DiVA PDF](https://www.diva-portal.org/smash/get/diva2:1597267/FULLTEXT01.pdf)
- A hobby repo (Python bitboards, snippet only) claims MCTS with 2000 rollouts per move plays roughly as well as a 4–6 ply alpha-beta with a crude threat-counting evaluation. — [bushesarebetter/connect-four-ai](https://github.com/bushesarebetter/connect-four-ai)
- An MDPI 2026 review (snippet only, not verified) cites an optimized minimax reaching a 79% win rate vs MCTS at search depth 5. — [MDPI Symmetry 18(2):293](https://www.mdpi.com/2073-8994/18/2/293)

### Inferences
- For Connect-Four-like games, the evidence ranks MCTS enhancements roughly like this:
  1. Solver backups plus decisive/anti-decisive move checks. These are cheap with bitboards, and the trap problem is the dominant failure mode.
  2. Shallow (2-ply) win/loss-only minimax in rollouts or at expansion. MR-2 gave the largest single gain, +23 points.
  3. Implicit minimax backups, if we have a heuristic evaluation (threat counting, odd/even threats).
- RAVE/AMAF is less clearly useful here. Connect-4 move value depends heavily on *when* a column is played (height and parity), which violates AMAF's "move value is independent of timing" assumption. This is my inference; I found no Connect-4 RAVE number.
- The "special move" (destroy discs around the drop point) breaks the monotone board-filling that solvers and threat analysis rely on. It also multiplies tactical possibilities, which means more shallow traps. That favours full-width search (alpha-beta) or hybrids over plain sampling.
- With 100 ms per move, Quantum Connect sits in the regime where published comparisons favour alpha-beta. Implicit-minimax results show MCTS gaining on alpha-beta only as time per move grows (1 s → 10 s → 120 s).

### Gaps
- No primary-source numbers retrieved for RAVE/AMAF in Connect-4, for tree reuse between moves, or for Teytaud & Teytaud's own decisive-move effect sizes.
- No peer-reviewed head-to-head of *enhanced* MCTS (solver + decisive moves + MR-2) vs a *tuned* alpha-beta (transposition table, move ordering, threat evaluation) on standard Connect-4 at ~100 ms. That would need an in-house experiment.
- The Baier & Winands PDF does not state its venue, implementation language or hardware. A 2015 journal version exists ("MCTS-Minimax Hybrids", IEEE TCIAIG, cited as Baier & Winands 2015 by AlphaViT), but its fetch timed out.

## 2. AlphaZero on Connect Four: Oracle series, alpha-zero-general, AlphaZero.jl, academic replications — accuracy vs solver and compute

### Takeaway
AlphaZero-style training reaches near-perfect Connect-4 play, but it needs GPU training: hours to days on 1–20 GPUs, and tens of thousands of self-play games. Inference needs about 600 network-evaluated MCTS simulations per move. None of that fits "Java standard library only, 100 ms per move" unless the network is tiny and hand-coded. A CPU-only alternative exists: n-tuple networks trained with TD learning (Scheiermann & Konen) plus an MCTS wrapper at test time, trained on a laptop CPU.

### Cited Findings
**Oracle "Lessons from AlphaZero" series (Aditya Prasad et al., Oracle Developers on Medium, 2018)**
The posts returned HTTP 403, so everything below is from search snippets and secondary sources. Part URLs:
- [Part 1: Connect Four](https://medium.com/oracledevs/lessons-from-alphazero-connect-four-e4a0ae82af68)
- [Part 3: Parameter Tweaking](https://medium.com/oracledevs/lessons-from-alphazero-part-3-parameter-tweaking-4dceb78ed1e5)
- [Part 4: Improving the Training Target](https://medium.com/oracledevs/lessons-from-alphazero-part-4-improving-the-training-target-6efba2e71628)
- [Part 5: Performance Optimization](https://medium.com/oracledevs/lessons-from-alpha-zero-part-5-performance-optimization-664b38dc509e)
- [Part 6: Hyperparameter Tuning](https://medium.com/oracledevs/lessons-from-alpha-zero-part-6-hyperparameter-tuning-b1cfcbe4ca9a)

Reported results (snippet only):
- Accuracy against a perfect solver:
  - With MCTS, scored on "any optimal move", the agent erred about **once in 400 positions**.
  - The raw network, required to find the *fastest* win or slowest loss, erred about **once in 25**.
  - MCTS cut errors on the "weak" test set by about 80%. On the "strong" set it cut them by about 29%, from roughly 4% to roughly 3%.
- Compute: early runs needed about **150 generations at about 12 min per generation**, more than a day. Later optimizations cut this to about **40 generations**.
- Connect-4-specific tricks: deduplicating and merging repeated positions, a 1cycle learning-rate schedule, and an improved training target (part 4).
- Part 5 states that Connect Four was chosen to allow a solid AlphaZero implementation "while utilizing more modest resources".

**Clausen, Reichhuber, Thomsen & Tomforde, ICAART 2021 ("Improvements to Increase the Efficiency of the AlphaZero Algorithm: A Case Study in the Game 'Connect 4'")** — [SciTePress PDF](https://www.scitepress.org/Papers/2021/102459/102459.pdf)
- Builds on Prasad (2019, the Oracle work). Uses the Pons perfect solver to create test positions.
- Accuracy measures follow Young (2019):
  - The "strong" test set counts only fastest-win or slowest-loss moves as correct.
  - The "weak" set counts any move with the same game-theoretic outcome.
- Hard dataset: 50% random moves, 50% solver moves. A **supervised** 20-block ResNet (6.0M params) reached **92.7–93.5% move accuracy** and about 78–80% outcome accuracy. 5 blocks (1.6M params) reached 91.6–92.4%.
- Full AlphaZero training runs took **2–6 hours using up to 20 cloud GPUs** for self-play. Cost is reported as estimated hours on one RTX 2070S.
- Proposed improvements and their reported speedups (from the related-work section): 27% less training for one prior method, and a 20% speed increase for another.

**AlphaZero.jl Connect Four tutorial (Jonathan Laurent)** — [AlphaZero.jl docs](https://jonathan-laurent.github.io/AlphaZero.jl/stable/tutorial/connect_four/)
- Hardware: Core i5-9600K CPU plus one **RTX 2070 (8 GB)**.
- Schedule: **15 iterations × 5,000 self-play games = 75,000 games**, at **600 MCTS simulations per move**.
- Each iteration takes about 1–2 hours, which I estimate at roughly 15–30 hours total. The page does not state a total.
- Network: ResNet with 5 blocks × 128 filters, about **1.6M parameters**.
- The setup is described as a downscaled Oracle configuration: Oracle's network was about 10× larger and generated about 2× the data per iteration.
- Raising simulations from 300 to 600 sped up learning.
- Results against the Pons-solver benchmark appear only as a figure. The text says the agent makes few mistakes detectable within 14-move lookahead but remains imperfect on long-term strategy, and that both the full agent and the network-only player end up beating the minimax baseline.

**Scheiermann & Konen (2022/2023), "AlphaZero-Inspired Game Learning: Faster Training by Using MCTS Only at Test Time"** — [arXiv HTML v3](https://arxiv.org/html/2204.13307v3); [abs](https://arxiv.org/abs/2204.13307)
- Method: n-tuple network trained with TD learning (TCL). There is **no MCTS during training**; an MCTS wrapper is added only at test time.
- Compute: 10 Connect-4 agents trained in **1.4 days total (6M training episodes)** on one **Intel i7-9850H laptop CPU, no GPU**. That is about 3.4 h per agent (my arithmetic).
- Results with TCL moving first:
  - TCL-wrap (1000 MCTS iterations) vs near-perfect AB-DL: **99 wins / 0 ties / 1 loss** in 100 games.
  - TCL-base (no MCTS) vs AB-DL: 91/2/7.
- Note: Connect-4 is a proven first-player win, so these are first-player results against a near-perfect defender.

**AlphaViT / AlphaViD / AlphaVDA (Fujita, arXiv:2408.13871)** — [arXiv PDF](https://arxiv.org/pdf/2408.13871)
- Vision-transformer AlphaZero agents for several games and board sizes, including Connect 4 on a "large board configuration".
- Training hardware: a single PC with two RTX 4060 Ti 16 GB GPUs. No cloud was used.
- 200–400 MCTS simulations per move.
- Also reports **plain-MCTS Elo scaling** for Connect 4; see section 3.

**Related projects**
- AZFour, a web app powered by an AlphaZero Connect Four model, deployed with GraphPipe (Oracle-related). — [Medium: AZFour](https://medium.com/@sleepsonthefloor/azfour-a-connect-four-webapp-powered-by-the-alphazero-algorithm-d0c82d6f3ae9)
- The Pons perfect solver is the standard oracle for ground-truth evaluation in these papers. — [Clausen et al.](https://www.scitepress.org/Papers/2021/102459/102459.pdf)

### Inferences
- Rough compute envelope to "near-perfect" (once in a few hundred errors with search) on **7x6** Connect-4:
  - about 1 day on one consumer GPU (Oracle early runs; AlphaZero.jl)
  - or a few hours on about 20 GPUs (Clausen et al.)
  - about 10^4–10^5 self-play games
- An **8x12** board has about 2.3× more cells and a branching factor up to 12 instead of 7. Expect the cost to grow by at least an order of magnitude, and the destroy "special move" further enlarges the state space. This is extrapolation.
- Deploying an AlphaZero net under our constraints means hand-written Java forward passes with no libraries. A 1.6M-parameter ResNet is about 1–3 GFLOP-scale work per evaluation (my estimate), so at most a handful to a few hundred evaluations fit in 100 ms on a CPU. AlphaZero-style play wants about 600 evaluations per move.
- The Scheiermann & Konen route fits our constraints much better:
  - n-tuple value functions are lookup tables, cheap in plain Java
  - training is CPU-only on a laptop
  - an MCTS or alpha-beta wrapper at test time adds tactics
- Best-fit learned component for Quantum Connect: an n-tuple or small linear evaluation trained offline by TD or self-play, used as the evaluation inside alpha-beta or as a prior and value inside MCTS. A deep AlphaZero network is not a good fit.

### Gaps
- Could not fetch the Oracle posts directly (403), so exact hardware, total GPU-hours and final error rates are unconfirmed beyond snippets.
- Not found:
  - suragnair/alpha-zero-general Connect4 results (no published accuracy vs solver located)
  - Leela-style or KataGo-style Connect-4 projects with solver-accuracy numbers
  - KataGo-improvement ablations on Connect-4
- AlphaZero.jl's numeric error rates exist only in a figure.

## 3. Playouts per 100 ms in Java/C++ with bitboards, and how strength scales with playouts

### Takeaway
The best-documented figure is about 91,000 MCTS-Solver simulations per second on 7x6 Connect-4 (Baier & Winands), which is about 9,000 per 100 ms. Heavier playouts trade speed for strength: 2-ply rollout minimax drops to about 30k/s but wins 73%. Plain-MCTS Elo on Connect-4 rises steeply up to about 1,600 simulations and then flattens. More plain playouts are therefore not the path to strength; tactics are.

### Cited Findings
- Baier & Winands report MCTS-Solver simulations per second on Connect-4 (7x6), 1 s per move, language and hardware unstated. — [PDF](https://dke.maastrichtuniversity.nl/m.winands/documents/paper%2049.pdf)

  | Variant | Simulations/s |
  |---|---|
  | Baseline | **about 91,000** |
  | MCTS-MB-2 | about 90,000 |
  | MCTS-MS-2-Visit-2 | about 83,000 |
  | MCTS-MR-2 | about 30,000 |

  - In Breakthrough (6x6) the baseline ran at about 61,000 sims/s.
- Python figures (no Java or C++ benchmark found):
  - Numba-accelerated Python Connect-4: about **415,000 random rollouts/s** (29.4M rollouts in 70.9 s). Snippet only. — [zaforf Connect 4 MCTS](https://zaforf.github.io/isp/pages/connect4/)
  - A plain-Python engine report: about 143,000 sims/s for the baseline, and 49k–67k for tactical-rollout variants. The authors note that cost ratios are specific to Python and that a compiled rollout kernel changes the trade-off. Snippet only. — [andrewcukierwar/board-game-ai-lab REPORT.md](https://github.com/andrewcukierwar/board-game-ai-lab/blob/main/docs/search-mcts-v3/REPORT.md)
- Plain-MCTS strength scaling on Connect 4 ("large board configuration", Fujita, AlphaViT Appendix H, Table A3). — [arXiv PDF](https://arxiv.org/pdf/2408.13871)

  | Simulations | Elo |
  |---|---|
  | 10 | 1048 |
  | 100 | 1317 |
  | 200 | 1431 |
  | 400 | 1510 |
  | 800 | 1574 |
  | 1600 | 1677 |
  | 3200 | 1674 |
  | 6400 | 1708 |

  - The author notes diminishing returns past 1600, which matches Baier & Winands (2015): more MCTS time does not keep improving Connect-4 play.
- Plain UCT with 10,000 random-playout iterations still had only a 19.8% overall won-game rate in a Connect-4 tournament that included near-perfect alpha-beta agents. — [Scheiermann & Konen](https://arxiv.org/html/2204.13307v3)

### Inferences
- Throughput for Quantum Connect in Java with long bitboards:
  - Starting point: about 91k sims/s, i.e. about 9k per 100 ms, on 7x6 (2013-era hardware, unknown language).
  - A modern CPU with a tight bitboard implementation can plausibly do several times that on 7x6.
  - An 8x12 board has about 2.3× longer random games (96 cells vs 42), and the board no longer fits in one 64-bit long. With two longs, or a column-height layout across two words, rollouts get slower.
  - Ballpark: **about 3k–20k light playouts per 100 ms**, or about 1k–5k with 2-ply tactical rollouts. These are estimates, not measurements, and should be benchmarked.
- Java-specific overheads inside a hard 100 ms limit:
  - JIT warm-up on the first moves
  - GC pauses if nodes are allocated per iteration (use preallocated node arrays)
  - `System.nanoTime` checks
  - Keep a safety margin of about 10–20 ms.
- The Elo curve flattens around 1.6k sims, but enhanced MCTS keeps gaining (MR-2 beat the baseline with 3× fewer sims). The budget should go into tactical knowledge, not raw playout count.
- The 100 ms budget is therefore enough for MCTS near its plain-playout plateau, but that plateau is weak against alpha-beta in Connect-4.

### Gaps
- No published Java or C++ bitboard Connect-4 rollout benchmark was found. We need our own microbenchmark.
- Board size for the AlphaViT "large board configuration" was not extracted.
- No data on 8x12 or 12-column Connect-4 variants.

## 4. Related games (Hex, Gomoku, Othello, Breakthrough, Amazons, LOA): where MCTS beats alpha-beta and where it does not

### Takeaway
MCTS wins where traps are rare or deep and good evaluation functions are hard to write; Go is the archetype. It reaches parity or better against alpha-beta in Breakthrough and Lines of Action once given solver backups, implicit minimax or more time. Alpha-beta stays stronger in trap-dense, tactically sharp games with good evaluations: Chess, Checkers, short-time-control Connect-4. Connect-4 sits on the tactical side.

### Cited Findings
- Ramanujan et al. 2010: UCT outperforms minimax in Go and Kriegspiel, while minimax still prevails in Chess, a difference linked to shallow-trap density. — [ICAPS 2010](https://ojs.aaai.org/index.php/ICAPS/article/view/13437)
- Baier & Winands: MCTS has been applied successfully in Go, Amazons, Lines of Action, General Game Playing and planning. Alpha-beta remains superior in games such as Chess and Checkers. — [PDF](https://dke.maastrichtuniversity.nl/m.winands/documents/paper%2049.pdf)
- **Breakthrough** (Lanctot et al. 2014) — [ar5iv](https://ar5iv.arxiv.org/html/1406.0486)
  - Plain MCTS vs alpha-beta: 27.6% at 1 s, 47.6% at 10 s.
  - MCTS with implicit minimax vs alpha-beta: 45.1% at 1 s, 61.8% at 10 s.
  - With a stronger alpha-beta evaluator (efLH), MCTS-IM needed about 60–120 s per move to pass 50% (55.6% at 60 s, 61.5% at 120 s). Its baseline managed only 7.9% at 1 s.
- **Lines of Action**
  - MCTS-IM reached 51.0% vs the world-class alpha-beta program MIA at 5 s, up from 40.0% for plain MCTS. With progressive bias, MCTS scored 61.8%, and 63.3% with implicit minimax. — [ar5iv](https://ar5iv.arxiv.org/html/1406.0486)
  - MCTS-Solver was the enabling enhancement. — [Winands et al. 2008](https://cris.maastrichtuniversity.nl/en/publications/monte-carlo-tree-search-solver/)
- **Othello** (snippet only): a later Winands-group paper found that shallow alpha-beta inside MCTS rollouts did not help. — [Baier & Winands, MCTS-minimax hybrids with heuristic evaluation functions](https://dke.maastrichtuniversity.nl/m.winands/documents/mctshybrids.pdf)
- **Othello** (learned evaluation): Scheiermann & Konen's CPU-trained n-tuple agent plus MCTS wrapper beat Edax, a strong alpha-beta Othello engine, at some levels. Snippet from the abstract; exact Edax level not extracted. — [arXiv abs](https://arxiv.org/abs/2204.13307)
- **Amazons**: Browne et al. 2012 survey snippets report UCT being preferred over flat Monte Carlo and Amazons MCTS programs being compared to earlier alpha-beta programs. Verdict not extracted. — [Browne et al. 2012 survey PDF](http://www.incompleteideas.net/609%20dropbox/other%20readings%20and%20resources/MCTS-survey.pdf)
- **Hex**: MCTS Hex work uses rollout rules such as always answering bridge-intrusion moves. — [Cazenave, Monte Carlo Hex](https://www.lamsade.dauphine.fr/~cazenave/papers/hex.pdf)
- **Octagon Theory**: decisive moves, move groups and limited simulation length turned a losing MCTS agent into the best-performing one. — [SciTePress 2014](https://www.scitepress.org/Papers/2014/47576/)
- **Gomoku**: a 2025 paper (Rapfi) works on distilled neural evaluation for Gomoku. Search snippets did not give an MCTS-vs-alpha-beta verdict. — [Rapfi, arXiv:2503.13178](https://arxiv.org/pdf/2503.13178)
- **Minimax revisited**: "Minimax Strikes Back" (arXiv:2012.10700) is a counterpoint arguing minimax-family search with learned evaluations is competitive with AlphaZero-style MCTS. Not read in detail. — [arXiv:2012.10700](https://arxiv.org/pdf/2012.10700)

### Inferences
- Whether MCTS beats alpha-beta depends on three things:
  1. **trap density at shallow depth** (Connect-4: high; Go: low)
  2. **availability of a good static evaluation** (Connect-4: decent threat or parity heuristics exist)
  3. **time per move** (MCTS gains with more time; at about 100 ms alpha-beta is favoured)
- All three point toward alpha-beta for Quantum Connect, with the "special move" adding further tactical sharpness.
- Recommended architecture, given the evidence:
  - a **bitboard alpha-beta** (iterative deepening, transposition table, killer/history ordering, threat-based evaluation, immediate-win/block checks)
  - an optional **hybrid** using MCTS-Solver with decisive/anti-decisive moves and 2-ply loss detection in rollouts. Build it only if self-play experiments show alpha-beta's evaluation misjudges long-term structure on the 8x12 board.
- Any learned component should be an offline-trained n-tuple or linear evaluation, not a deep AlphaZero network.

### Gaps
- No primary sources retrieved for:
  - Hex: MoHex's MCTS dominance in Computer Olympiad Hex
  - Gomoku/Renju: threat-space-search and alpha-beta dominance
  - Othello: alpha-beta engines such as Edax/Logistello vs MCTS
  - Amazons: MCTS programs such as Invader winning Olympiads
- These are widely cited in the field but were not verified in this session, so they should not be stated as sourced facts.
- No quantitative per-game table from Browne et al. 2012 was extracted.
