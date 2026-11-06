# Digest: MCTS-minimax hybrids, MCTS-Solver, shallow traps, AlphaZero efficiency

Purpose: decide whether any MCTS component belongs in the Quantum Connect bot (8x12 gravity board, six in a row, 3x3 destroy move once per 10 own turns, 100 ms per move, Java stdlib only; current plan = alpha-beta + threat search + learned pattern evaluation).

Method: every source below was read in full from the extracted text (Browne survey: only the requested sections, rest skimmed). All sources are treated as data. pdftotext garbling is flagged where it matters. Statements marked **[OWN]** are my inference, not a claim of any paper.

## Citation key

| Key | File (folder `sources\text\`) | Notes |
|---|---|---|
| W08 | `pdf-p2-winands2008-montecarlo-tree-search-solver.md` | Winands, Bjornsson, Saito, CG 2008 |
| R10 | `pdf-p2-ramanujan2010-on-adversarial-search-spaces-and.md` | Ramanujan, Sabharwal, Selman, ICAPS 2010. No numbered sections; cited by heading and Table/Figure |
| B14 | `pdf-p2-baier2014-montecarlo-tree-search-and-minimax.md` | Baier, Winands, ECAI-CGW 2014: hybrids WITH evaluation functions (Othello, Breakthrough, Catch the Lion) |
| B15 | `pdf-p2-baier2015-mctsminimax-hybrids-ieee-tciaig-version.md` | Baier, Winands, IEEE TCIAIG: hybrids WITHOUT evaluation functions (Connect-4, Breakthrough, Othello, Catch the Lion) |
| L14 | `arxiv-1406.0486-monte-carlo-tree-search.md` | Lanctot, Winands, Pepels, Sturtevant: implicit minimax backups (arXiv v4) |
| C21 | `pdf-p3-clausen2021-improvements-to-increase-the-efficiency.md` | Clausen et al., ICAART 2021: AlphaZero efficiency, Connect 4 |
| BR12 | `pdf-p3-browne2012-a-survey-of-monte-carlo.md` | Browne et al. survey, IEEE TCIAIG 2012 (sections read: 3.3, 3.4, 3.5, 4.8.7, 5.1.1, 5.2.2, 5.2.5, 5.2.8, 5.3, 5.4, 5.5.1, 6.1.6, 7.2, 7.3) |
| CPW | `cpw-monte-carlo-tree-search.md` | Chess Programming Wiki page; mostly a bibliography |

Section references use the paper's own numbering (for example `B15 §V-C1` = Baier 2015, Section V, subsection C, item 1).

---

## 1. One subsection per paper

### 1.1 W08 - Winands, Bjornsson, Saito 2008: MCTS-Solver (Lines of Action)

**Problem.** In sudden-death games with narrow tactical lines, plain MCTS node values converge too slowly. Example (Fig. 1, LOA): after 5 s MCTS picks a move that is a forced 8-ply loss with 67.2% simulated win rate, over the 7-ply win scoring 48.2%. It picks right only after 60 s. Alpha-beta proves the win in under 1 s [W08 §4].

**Changes vs. MCTS** (backpropagation, selection, final move) [W08 §4.1-4.3]:

1. Backpropagate proven game-theoretic values (infinity / -infinity) in addition to simulation results {1, 0, -1}, negamax style [W08 §4.1].
   - A node is a proven win if ONE child is a proven win for the mover.
   - A node is a proven loss only if ALL children are proven losses.
   - If the selected child is a proven loss but not all siblings are, back up an ordinary -1 (a normal lost-game result), not -infinity [W08 §4.1].
2. Selection [W08 §4.2]:
   - Children proven lost are NEVER selected by the UCT(+PB) formula. The paper argues that pruning them in nodes with random/biased move choice overestimates the parent (Fig. 2: 0.4 vs 0.37), while keeping them risks underestimation.
   - For nodes with fewer visits than a threshold T (T = 50), moves are chosen by the simulation strategy instead of the UCT formula. There a proven-loss child CAN be selected, but the search is not continued below it; the result is just backed up.
   - For every child of a leaf (visit count = 1), check for a direct win for the mover. If one exists, stop and mark the node proven. Otherwise it could take many simulations to find the mate-in-one.
3. Final move: "Secure child" = argmax of `v + A/sqrt(n)` with A = 1. Search stops as soon as the root is proven won [W08 §4.3].

**Selection formula (Eq. 1)** [W08 §3.1]: `k = argmax_i [ v_i + C*sqrt(ln n_p / n_i) + W*P_c/(n_i+1) ]` with progressive bias W = 100 and P_c = transition probability of the move category (277 categories in LOA, learned from expert games as n_played/n_available). The numerical value of C is not stated in the text read. Note: this C multiplies `sqrt(ln n_p/n_i)`. BR12 uses a different convention (see 1.7).

**Playout details** [W08 §3.1]: playouts are capped at 200 moves (scored draw) and cut early using the MIA 4.5 evaluation (win if > +1000, loss if < -1000), evaluated only every 3 plies. One node is added per simulation.

**Pseudo-code** (W08 Fig. 3 in the extracted text has inconsistent sign conventions between prose and code; below is my clean negamax reconstruction **[OWN reconstruction of W08 Fig. 3 + §4.1-4.2]**; status is always from the view of the player to move at that node):

```
int iterate(Node n):                     // returns r in {+1,0,-1} for the mover at n
    if n.status != UNKNOWN: return n.status == WIN ? +1 : -1
    if n.visits == 1 and anyChildWinsImmediately(n):        // W08 4.2 leaf check
        n.status = WIN; return +1
    c = select(n)                        // UCT(+PB) skipping children with c.status == WIN
                                         // (WIN for the child's mover = losing move for us);
                                         // if n.visits < T(=50): pick by simulation policy instead
    n.visits++
    if c.visits == 0 and c.status == UNKNOWN: r = -playout(c); addToTree(c)
    else                                      r = -iterate(c)
    if c.status == LOSS:                  n.status = WIN        // we have a proven winning move
    else if allChildrenExpanded(n) and every child.status == WIN:  n.status = LOSS
    n.update(r)                           // normal average; proven results enter as plain +-1
    return r
```

**Experiments** [W08 §5]: Pentium IV 3.2 GHz; 1,000-game matches from 100 standardized three-ply starts; 5 s per move.

| Match (1000 games, 5 s/move, LOA) | MCTS pts | MCTS-Solver pts | Source |
|---|---|---|---|
| MCTS-Solver vs MCTS | 353.5 | 646.5 (65%, winning ratio 1.83) | W08 §5.1 Table 1 |
| vs MIA 2000 (alpha-beta, ERPS, forward pruning) | 585.5 | 692.0 | W08 §5.2 Table 2 |
| vs MIA 2002 | 394.0 | 543.5 | W08 §5.2 Table 2 |
| vs MIA 2006 | 69.5 | 115.5 | W08 §5.2 Table 2 |

Interpretation as stated: both MC programs beat MIA 2000; only MCTS-Solver beats MIA 2002; both lose to MIA 2006 but Solver loses far less [W08 §5.2]. Draws are not proven ("draws only happen in exceptional cases in LOA", footnote 3) [W08 §4.1]. Uncertainty: Table 2 values are read as points out of 1000 scored by the MC program; the extract does not label the columns explicitly.

---

### 1.2 R10 - Ramanujan, Sabharwal, Selman 2010: shallow traps

**Definition (Def. 1)** [R10 "Traps in Adversarial Search Spaces"]: In a 2-player game, the current player p at state s is **at risk** if there is a move m from s such that, after m, the opponent has a k-move winning strategy. The state after m is a **level-k search trap** for p. A level-k trap is detectable by a minimax search of depth k+1 from the current state [R10 "Identifying and Avoiding Traps"]. Labels used are 1, 3, 5, 7. B14/B15 read "level-k" as "opponent wins within at most k plies" and note every level-k trap is also a level-(k+1) trap [B15 §V-B]. The R10 definition text counts "moves", which is ambiguous, but the labels used (odd numbers) match plies. **[OWN reading]**

**Measured frequency in Chess** [R10 "Existence of Search Traps", Fig. 2]: 200 semi-random games (random move with probability 1/3, GNU Chess heuristic with 2/3) and 200 grandmaster games, played to 15, 31, 47, 63 plies, then checked for traps at levels 1, 3, 5 (7 also for semi-random 15- and 31-ply).
- Semi-random, 31 plies: roughly 7-10% of boards have a trap at levels 1-7.
- Semi-random, 47 plies: as many as 15%.
- Grandmaster games, 47 plies: roughly 12%.
- Go is said to have essentially none until the end of the game, because wins are defined through territory [R10 "Existence of Search Traps"].

**UCT behaviour at a trap** [R10 "Identifying and Avoiding Traps"]:
- Setup: UCT exploration bias 0.4. Compared against minimax of depth 7 with the GNU Chess heuristic. UCT is given equivalent effort measured in nodes against a depth-(k+1) minimax.
- Utilities are in [-1, 1].

**Table 1 (garbled in extraction; my reconstruction of cell positions, uncertain)**: UCT utility estimate for best-by-UCT / best-by-minimax-7 / trap move:

| Trap level | UCT-best | Minimax-best | Trap move |
|---|---|---|---|
| 1 | -0.083 | -0.092 | -0.250 |
| 3 | +0.020 | +0.013 | -0.012 |
| 5 | -0.056 | -0.063 | -0.066 |
| 7 | +0.011 | +0.009 | +0.004 |

The twelve numbers are all present in the extraction but their row assignment is inferred from reading order and the text; the text itself says the level-5 trap action stays "over -0.07" versus the ideal -1 and level-5/7 trap utilities are "very close" to the best action's [R10 "Identifying and Avoiding Traps"]. Treat exact cells as uncertain, the qualitative reading as solid.

**Findings**:
- UCT correctly finds the level-3 trap (explores only to depth 3, converges to -1 when forced to visit the trap state) [R10 Fig. 4, Fig. 5].
- Level 5: UCT spends about 70% of iterations deeper than level 5. Level 7: about 95% deeper than level 7, down to depth about 20 [R10 Fig. 4].
- More samples do not fix it: with 10x the nodes minimax needs (sometimes 50x), the trap-state utility stays above -0.15 for level >= 5. Level-7 traps were found in some runs at about 5x effort, but several runs failed [R10 Fig. 5 and footnote 3].
- Conclusion: UCT cannot separate "really good" from "really bad" moves; "soft traps" (moves that merely lose material) are probably missed as well; this is offered as a major reason minimax beats UCT in Chess while UCT dominates Go [R10 "Identifying and Avoiding Traps", "Conclusion"].

Caveats: only a handful of boards (one per level, Table 1 has four) are examined in detail; the UCT playout policy is not stated in the text read.

---

### 1.3 B14 - Baier & Winands 2014: hybrids with heuristic evaluation (CGW 2014)

**Setup** [B14 §5]: baseline MCTS-Solver with UCB1-Tuned, uniformly random rollouts; 1 second per move; C = 0.7 (Othello, Catch the Lion) and 0.8 (Breakthrough 6x6); draws count 1/2; unenhanced alpha-beta inside hybrids (random move ordering for non-determinism); evaluation = own score minus opponent score, normalised to [0, 1]. Games: Othello (8x8), Breakthrough (6x6), Catch the Lion (3x4). Evaluations [B14 §5.1]: Othello = 10 per stable disc + number of legal moves; Breakthrough = 3 per piece + row values 10, 3, 6, 10, 15, 21 from home row; Catch the Lion = material (Chick 3, Giraffe/Elephant 5, Chicken 6, captured pieces included).

**Game properties** [B14 §5.2], self-play of MCTS-Solver, 1000 games:

| Game | Avg branching | Avg level-3 traps per position (share of legal moves) |
|---|---|---|
| Catch the Lion | about 10 | 3.7 (37%) |
| Breakthrough 6x6 | 15.5 | 2.8 (18%) |
| Othello | 8 | 0.1 (1.2%) |

"Results were comparable for other trap depths."

**Three heuristic-integration families, each with an E (evaluation call) and an M (shallow minimax) variant** [B14 §4]:

| Family | Idea | E variant | M variant (new in B14 except IR-M) | Parameters |
|---|---|---|---|---|
| IR (informed rollouts) | Choose rollout moves with the evaluation | epsilon-greedy: best 1-ply child by eval, random move with prob. epsilon | depth-d alpha-beta for each rollout move | epsilon, d |
| IC (informed cutoffs) | Stop rollout after m random moves and backpropagate the (un-rounded) evaluation | eval at cutoff | depth-d minimax value at cutoff | m, d |
| IP (informed priors) | When a node is created (or after n visits) store eval as virtual wins/losses weighted by w; e.g. eval 0.6 and w = 100 gives 60 wins and 40 losses | eval | depth-d minimax value | n, w, d |

**Results** (win rate vs. MCTS-Solver with uniform random rollouts; 1 s/move; best settings re-tested over 2000 extra games; all differences p < 0.001) [B14 §5.3-5.5]:

| Variant (best setting) | Othello | Breakthrough | Catch the Lion |
|---|---|---|---|
| IR-E (epsilon: 0.05 / 0 / 0.05) | 79.9% | 75.4% | 96.8% |
| IR-M (d = 1 best everywhere) | 73.9% | 65.7% | 96.5% |
| IC-E (m = 0 in all three) | 61.1% | 41.9% (worse than baseline) | 98.1% |
| IC-M (Oth m0 d2; BT m4 d1; CtL m1 d2) | 62.4% | 32.4% (worse) | 99.6% |
| IP-E (Oth n1 w1000; BT n1 w2500; CtL n0 w100) | 56.8% | 86.6% | 71.6% |
| IP-M (Oth n2 w5000 d3; BT n1 w1000 d1; CtL n1 w2500 d5) | 81.7% | 87.8% | 98.0% |

Head-to-head of M vs E variants [B14 §5.3-5.5]:
- IR-M vs IR-E: 37.1% / 35.3% / 47.9% (Oth / BT / CtL). IR-M did not beat IR-E. Depth-1 minimax equals IR-E but pays recursion overhead.
- IC-E vs IC-M: IC-E wins 73.1% / 58.3% / 66.1%.
- IP-M vs IP-E: IP-M wins 76.2% (Othello), 97.6% (CtL), but only 36.4% in Breakthrough. At an equal 1000 rollouts per move, IP-M (n1, w1000, d1) won 91.1% in Breakthrough, so the BT loss is time overhead from branching 15.5.
- Equal-rollout (time ignored) tests: IR-M beat IR-E 78.6% (Oth d2), 63.4% (BT d2), 89.3% (CtL d3) at 1000 rollouts. IC-M beat IC-E 65.7% (Oth, 10000 rollouts, m0 d1), 69.8% (BT, 6000, m0 d2), 86.8% (CtL, 2000, m0 d4). All confirm the time-overhead explanation [B14 §5.3, §5.4].

**Paradox: stronger standalone searchers make weaker rollouts** [B14 §5.3-5.5]:
- Catch the Lion: IR-M d=1 won 50.4% (+-3.1%) vs IR-E at 1000 rollouts; d=2 only 38.0%. Depth-2 minimax beat depth-1 minimax 95.8% as a standalone player, but lost 38.8% vs d=1 when both were used as rollout policies.
- IC-M in CtL: m=0, d=1 won only 2.9% vs IC-E (50,000 rollouts); d=2 34.3% (25,000); d=3 34.8% (6,000).
- Breakthrough IP-M: d=2 83.4% vs d=1 91.1% (both at 1000 rollouts), although depth-2 beats depth-1 80.2% standalone.

**Cross-comparison and combinations** [B14 §5.6-5.7]:
- Strongest single algorithm: IP-M in Othello, IP-E in Breakthrough, IC-E in Catch the Lion.
- Combining node priors with epsilon-greedy rollouts beat the best single algorithm: Othello IP-M+IR-E 55.2% (vs IP-M, p = 0.001); Breakthrough IP-E+IR-E 53.9% (p < 0.05); CtL IP-M+IR-E (n1, w2500, d4) 61.1% vs IC-E.
- Large prior weights win: w of 1000-5000 means rollouts almost never override the prior [B14 §5.5]. **[OWN]** In effect the search becomes heuristic-guided best-first minimax with rollouts as tie-breakers.

**Conclusions** [B14 §6]: sensitivity to the branching factor is the main limit of the hybrids (their Breakthrough weakness); unenhanced alpha-beta (no ordering, no k-best, no killer moves) was used, and enhancements could raise the viable branching factor.

Parameter grids [B14 §5.3-5.5]: IR epsilon in {0, 0.05, 0.1, 0.2, 0.5}; IR-M d in 1..4; IC m in 0..5, d in {1,2,3}; IP-E n in {0,1,2}, w in {50,100,250,500,1000,2500,5000}; IP-M n in {0,1,2,5,10,25}, w as before, d in 1..5.

---

### 1.4 B15 - Baier & Winands (TCIAIG): MCTS-minimax hybrids WITHOUT evaluation functions

This is the most relevant paper for a game where only terminal states are known (win/loss by 6-in-a-row), and it includes Connect-4.

**Three hybrids** [B15 §IV]:

- **MCTS-MR (minimax in rollouts)**: at each rollout step run a depth-d minimax; it can only find forced wins and avoid forced losses within horizon; if nothing is found, play a random move [B15 §IV-A, Fig. 1].
- **MCTS-MS (minimax in selection/expansion)**: trigger: a node reaches v visits (v = 0 means during expansion). Run an INDEPENDENT minimax for each legal move from the node, so a proven loss can be stored for an individual move even if the node itself cannot be proven. If the node's value is proven it is backpropagated; otherwise selection continues. Other trigger ideas (low average return, short average rollout length) were tried in preliminary runs but visit count seemed most promising [B15 §IV-B, Fig. 2]. In Connect-4 the alpha-beta window was set so minimax only detects forced LOSSES (traps), so only even depths d were used [B15 §V-C2].
- **MCTS-MB (minimax in backpropagation)**: MCTS-Solver must fall back to ordinary rollout returns as soon as at least one sibling of a proven-loss move is not proven. At exactly that moment run shallow minimax on the unproven siblings (each searched independently, FULL window so wins and losses are both detected, depth 1-6). If all moves of the parent state are proven losses, backpropagate a proven loss. Triggers only when a terminal was found in the tree, so it costs nothing in terminal-free regions [B15 §IV-C, Fig. 3, §V-C3].

Pseudo-code **[OWN reconstruction from B15 §IV prose and Figs. 1-3]**:

```
// MR: rollout move choice with depth-d minimax, terminal knowledge only
int rolloutMove(State s, int d):
    for each legal m: v[m] = -negamax(s.play(m), d-1)   // +1 win, -1 loss, 0 unknown/other
    if some v[m] == +1: return m                         // forced win within horizon
    safe = { m : v[m] != -1 }                            // avoid forced losses if possible
    return random(safe nonempty ? safe : all moves)

// MS: called from the selection phase when node.visits == v (v=0 -> in expansion)
void minimaxSelection(Node n, int d):
    for each legal move m of n:
        r = -negamax(n.state.play(m), d-1)   // loss-only window in Connect-4: detects only r == -1
        if r == -1: mark child(m) proven LOSS for the mover at n   // child stored individually
        // (win window variant not used in Connect-4)
    if all children proven LOSS:  n = proven LOSS  -> backpropagate
    else if (win found):          n = proven WIN   -> backpropagate

// MB: inside Solver backprop, at the point where a proven-loss child exists but siblings are not all proven
void minimaxBackprop(Node n, int d):
    for each child c of n with c.status == UNKNOWN:       // each searched independently
        c.status = provenValueOf(negamax(c.state, d))     // full window: win or loss or unknown
    if all children proven LOSS: n.status = LOSS          // backpropagate proven loss
    // otherwise back up an ordinary loss/win result as plain MCTS-Solver does
```

**Experimental setup** [B15 §V]: UCB1-Tuned (note: §II says UCT with UCB1, §V says UCB1-Tuned; internal inconsistency), C = 1.3 Connect-4, 0.8 Breakthrough, 0.7 Othello, 0.7 Catch the Lion, tuned once for MCTS-Solver and then held fixed. 1 s per move unless stated; 1000 games for tuning curves, 5000 games for the best setting; draws = 1/2; hardware not stated. Alpha-beta has no enhancements. MCTS-Solver beat a random player 100% of 1000 games in all four domains.

**Game properties** [B15 §V-B, §V-H]:

| Game | Avg branching (self-play, 1 s) | Positions analysed for traps | Baseline sims/s (whole-game average) |
|---|---|---|---|
| Connect-4 7x6 | 6.4 | 36,633 | about 91,000 |
| Breakthrough 6x6 | 15.5 | 28,344 | about 45,100 |
| Othello 8x8 | 8.1 | 60,723 | about 8,700 |
| Catch the Lion 3x4 | 9.2 | 34,187 | about 34,700 |

**Trap density and difficulty** [B15 §V-B, Figs. 4-9]. Density = average number of level-1/3/5/7 traps per position. Difficulty = proportion of 1000 uniformly random rollouts, started from each level-3 to level-7 trap move, that return a non-loss (incorrect) result; no tree built. Ranking on BOTH measures: Catch the Lion > Breakthrough > Connect-4 > Othello. Othello's rollouts judge traps almost perfectly because traps appear only at the very end, when the last move has no alternatives. Connect-4 also differs: trap density for the two players differs significantly (and in early Catch the Lion). Exact values per level were only shown in figures and are not readable in the extraction. The only numbers available are the B14 level-3 figures (section 1.3).

**Results** (win rate vs. MCTS-Solver with random rollouts; 5000 additional games for the best setting; 95% CI where given):

| Hybrid (best setting) | Connect-4 | Breakthrough 6x6 | Othello | Catch the Lion |
|---|---|---|---|---|
| MR | MR-2: **72.1%** (70.8-73.3), about 28,000 sims/s | all MR significantly worse than baseline (best MR-1) | MR-1: 43.7% (42.3-45.1), 6,400/s (worse) | MR-1: **94.8%** (94.1-95.4), 28,700/s |
| MS | MS-2-Visit-1: **53.6%** (52.2-55.0), 83,700/s | MS-2-Visit-2: **67.3%** (66.0-68.6), 33,000/s | MS-2-Visit-50: 50.8% (49.4-52.2), n.s. | MS-4-Visit-2: **76.8%** (75.6-78.0), 14,500/s |
| MB | MB-1: 49.9% (48.5-51.3), n.s. | MB-2: **60.6%** (59.2-62.0), 46,800/s | MB-2: 49.2% (47.8-50.6), n.s. | MB-4: **73.1%** (71.8-74.3), 20,000/s |

Sources: Connect-4 [B15 §V-C1..3], Breakthrough [§V-D1..3], Othello [§V-E1..3], Catch the Lion [§V-F1..3].

Search spaces tested: MR depth 1-4 everywhere; MS d in {2,4} (also 6 in CtL) and v in {0,1,2,5,10,20,50,100,(200,500,1000 in BT/Othello)}; MB depth 1-6.

Speed cost of MR in Connect-4: MR-1 about 69% of baseline sims/s, MR-2 about 31%, MR-3 about 11%, MR-4 about 7% [B15 §V-C1]. In Breakthrough: MR-1 15.8%, MR-2 2.3%, MR-3 0.5%, MR-4 0.15% (the paper says "empty Connect-4 board" in this Breakthrough paragraph, almost certainly a typo) [B15 §V-D1].

Specific observations:
- Connect-4 MR-1 is significantly WORSE than baseline, also at equal iterations (36.3% win rate at 10^5 iterations/move, 1000 games). Hypothesis: depth-1 rollouts (decisive moves only, no anti-decisive) are systematically unbalanced; Fig. 10 gives a toy position where a uniformly random rollout is right with probability 0.75 but a depth-1 minimax rollout only 0.25 [B15 §V-C1].
- MR-2 (decisive + avoidance of immediate-loss moves) is the Connect-4 sweet spot. **[OWN]** MR-2 is functionally what BR12 §5.2.2 calls decisive/anti-decisive rollout moves.
- Catch the Lion: even-odd effect (MR stronger at odd depths); the only domain where MR-3 beat MR-2 [B15 §V-F1].
- Othello with equal iterations: MR-1 47.9% (5000 rollouts), MR-2 54.3% (1000), MR-3 58.2% (250), MR-4 59.6% (100). The positive effect is wiped out by the time overhead [B15 §V-E1].
- Algorithm vs algorithm (2000 games): MS beat MB in Breakthrough and Connect-4; MR was stronger than both in Connect-4 and Catch the Lion, weaker than both in Breakthrough and Othello [B15 §V-G]. Numbers only in Fig. 23.
- Time scaling 250 ms to 5000 ms [B15 §V-G, Figs. 24-27]: performance gaps widen with time in most domains; in Connect-4 MR gains most from extra time; in Breakthrough MS and MB; in Othello nothing helps at any time; in Catch the Lion MR does NOT profit from longer searches (the plain MCTS-Solver scales better there). Numeric curves unreadable. The shortest time tested is 250 ms; 100 ms was never tested.
- Branching factor scan, Breakthrough 6 rows, widths 6, 9, 12, 15, 18, avg branching 15.5, 24.0, 37.5, 43.7, 54.2 (game length rises from about 30 to about 70 moves), hybrids retuned per size, 2000 games each [B15 §V-I, Fig. 31]: MR-1 falls from 30.8% to 10.2%; MR-2 from 20.2% to 0.1%. MS and MB stay effective up to branching of at least 50.
- Combinations of the three hybrids: effects overlap; no significant gain over the best single hybrid (preliminary) [B15 §VI].

**Authors' conclusion** [B15 §VI]: hybrids help most where trap density and difficulty are high; MR works for low branching (up to about 10 moves on average), MS and MB are more robust to branching (tested to about 50). They propose evaluation functions or move ordering (k-best) as ways to extend the hybrids, and note that move ordering is less useful without heuristics since most leaves of embedded searches are equal-valued.

---

### 1.5 L14 - Lanctot, Winands, Pepels, Sturtevant 2014: implicit minimax backups

**Idea** [L14 §3]: keep two separate statistics per node: the usual playout mean `r_s/n_s`, and an implicit minimax value `v_s` built from heuristic evaluations `v0` of leaves and backed up with max/min (negamax) rules. They are mixed only in the selection score:

```
Q_hat_IM(s,a) = (1 - alpha) * r_{s,a} / n_{s,a}  +  alpha * v_{s,a}        (Eq. 2; v in view of tau(s))
a* = argmax_a [ Q_hat_IM(s,a) + C * sqrt( ln n_s / n_{s,a} ) ]               (Eq. 1 with Q replaced)
```

Algorithm 1 (garbled in extraction; reconstructed from text and symbol dump, **[OWN reconstruction]**):

```
Select(s):  A' = argmax_a Q_hat_IM(s,a) + C*sqrt(ln n_s / n_{s,a});  return uniform random a in A'
Update(s, r): r_s += r; n_s += 1; v_s = max_{a} v_{s,a}          // minimax backup, negamax sign flips
Expand(s):   for each child s' : v_{s'} = v0(s')                  // all children added; then one-ply backup
             v_s = max_a v_{s,a}
Simulate(s): if some child not in tree: Expand(s); r = Playout(); Update(...); return r
             else a = Select(s); r = Simulate(T(s,a)); Update(s, r); return r
```

Notes: expanding ALL children is in the listing; a single-child expansion with lazy evaluation was used for LOA. With MCTS-Solver enabled, proven values take precedence in selection, so the combined scheme still converges; before a node is proven, the implicit minimax value acts like a heuristic approximation of Solver for the part of the tree without terminals [L14 §3].

**Setup** [L14 §4]: expand one node per simulation; MCTS-Solver on everywhere ("overhead negligible, never decreases performance"); rewards in {-1, 0, 1}; evaluation scaled to [-1, 1] by a "cache-optimized sigmoid" of a domain-dependent score difference; best-visit-count final move; seats swapped; 1000 games per point; 1 s per move unless stated. The value of the UCT constant C is not given in the text read.

Playout enhancements used (labels): ipp = decisive/anti-decisive + capture preference (4x weight on capturing undefended pieces); fet*x* = stop after x playout moves and return eval; det*x* = dynamic stop returning +-1 if |eval| >= x; ege*e* = epsilon-greedy; np = node priors; bl = (ege0.1, det0.5); bl' = (ipp, fet20, np).

**Results by game** [L14 §4.1-4.3, Tables 2-5]:

Kalah (eval = store stone difference; fet4 best): clear advantage for alpha in [0.1, 0.5] at 1 s [L14 §4.1, Fig. 1].

Breakthrough 8x8 (Table 2; 95% CI; baselines as defined above):

| Player A | Player B | A wins % |
|---|---|---|
| MCTS(ipp) | random playouts | 94.30 +-1.44 |
| MCTS(ege0.1, det0.5) | MCTS(ipp) | 68.80 +-2.88 |
| MCTS(ipp, im0.4) | MCTS(ipp) | 82.30 +-2.37 |
| MCTS(ipp, fet20, im0.4) | MCTS(ipp, fet20) | 87.20 +-2.07 |
| MCTS(bl, im0.4) | MCTS(bl, constant-bias Q^CB) | 67.80 +-2.90 |
| MCTS(bl, im0.4) | MCTS(bl, progressive-bias Q^PB) | 65.50 +-2.95 |
| MCTS(bl, im0.6) | MCTS(bl) | 63.30 +-2.99 |
| MCTS(bl, im0.6, np) | MCTS(bl) | 77.90 +-2.57 |
| MCTS(efMS, bl) | MCTS(efLH, bl') | 40.20 +-3.04 |
| MCTS(efMS, bl, np) | MCTS(efLH, bl') | 78.00 +-2.57 |
| MCTS(efMS, bl, np, im0.4) | MCTS(efLH, bl') | 84.90 +-2.22 |
| MCTS(efMS, bl, np, im0.4) | MCTS(efLH, bl', im0.6) | 53.40 +-2.19 (2000 games) |

Alpha ranges that helped in Breakthrough: [0.1, 0.6] at 1 s and 5 s (no priors); with node priors [0.5, 0.6] at 1 s and [0.1, 0.6] at 5 s; with the stronger Lorentz-Horey eval and bl' [0.5, 0.6] at both times. alpha > 0.6 behaves like greedy best-first minimax [L14 §4.2]. The Q^CB and Q^PB formulas compared against are `(1-alpha)Q + alpha*v0(s')` and `(1-alpha)Q + alpha*v_{s,a}/(n_{s,a}+1)`.

Maximum backpropagation vs im0.4 (Table 3, win rate of MCTS(bl, im0.4), 1 s, T in thousands of visits):

| T (thousand visits) | 0.1 | 0.5 | 1 | 5 | 10 | 20 | 30 |
|---|---|---|---|---|---|---|---|
| win % | 81.9 | 73.1 | 69.1 | 65.2 | 63.6 | 66.2 | 67.0 |

Lines of Action (MC-LOA, MCTS-Solver + PB + optimized alpha-beta playouts, vs MIA-based alpha-beta; 100 opening positions) [L14 §4.3, Table 5, Fig. 4]: without PB, alpha in [0.1, 0.5] helped; with PB the advantage vanishes, alpha in [0.05, 0.3] is "not significantly harmful". All Table 5 rows use alpha = 0.2:

| PB | Matchup | Games | Time | Win % |
|---|---|---|---|---|
| yes | MCTS(im) vs MCTS | 32,000 | 1 s | 50.59 |
| yes | MCTS(im) vs MCTS | 6,000 | 5 s | 50.91 |
| no | MCTS(im) vs MCTS | 1,000 / 6,000 / 2,600 | 1 / 5 / 10 s | 59.90 / 63.10 / 63.80 |
| no | MCTS vs alpha-beta | 2,000 | 5 s | 40.0 |
| no | MCTS(im) vs alpha-beta | 2,000 | 5 s | 51.0 |
| yes | MCTS vs alpha-beta | 20,000 | 5 s | 61.8 |
| yes | MCTS(im) vs alpha-beta | 20,000 | 5 s | 63.3 |

**Comparison with alpha-beta in Breakthrough** (Table 4; alpha-beta = iterative deepening + static move ordering [decisive/anti-decisive, capture of defenceless pieces, other captures, quiet]; win rates are for the MCTS player):

| Eval | MCTS player | 1 s | 5 s | 10 s | 20 s | 30 s | 60 s | 120 s |
|---|---|---|---|---|---|---|---|---|
| efMS | MCTS(bl) | 27.55 | 39.00 | 47.60 | - | - | - | - |
| efMS | MCTS(bl, im0.4) | 45.05 | 61.60 | 61.80 | - | - | - | - |
| efLH | MCTS(bl') | 7.90 | 10.80 | 12.60 | 18.80 | 19.40 | 24.95 | 25.38 |
| efLH | MCTS(bl', im0.6) | 28.95 | 39.30 | 41.20 | 45.80 | 46.20 | 55.60 | 61.54 |

(Games per point: 2000 at 1 s, 1000 at 5 s, 500 at 10-60 s, 130 at 120 s.) Alpha-beta(efMS) vs alpha-beta(efLH): efMS wins 70.40% at 1 s, 53.40% at 5 s, 31.25% at 10 s [L14 §4.2 Table 4]. Authors' observations: MCTS gains versus alpha-beta as time rises, implicit minimax improves MCTS at equal time, and MCTS profits more from a weak heuristic than alpha-beta does; "when using efLH, alpha-beta performs significantly better against MCTS at low time settings" [L14 §4.2].

**Discussion on traps** [L14 §4.4]: the authors' original motivation was trap moves, but they could not construct a Breakthrough position showing implicit-minimax solving a trap problem; in their experience traps are handled by decisive/anti-decisive playout moves, and "even with random playouts, an efficient implementation with MCTS-Solver handles shallow traps". Implicit minimax did not speed up solving a LOA endgame test set. Their explanation: short-term tactical information not captured by long playouts (and vice versa). No improvement in Chinese Checkers and Hearts. Conclusion range: alpha in [0.15, 0.4] "a safe choice" (higher, [0.5, 0.6], in Breakthrough with priors or the alternative baseline) [L14 §5].

---

### 1.6 C21 - Clausen et al. 2021: improving AlphaZero efficiency (Connect 4)

**Scope**: reduce the compute cost of AlphaZero training; cost counted as estimated time of self-play workers on one reference machine (RTX 2070S, saturated), network training cost ignored; full runs take 2-6 h on up to 20 cloud GPUs [C21 §3.1]. Metric: "MCTS accuracy %" of the MCTS+network player against a set of solved Connect-4 positions (Pons solver). Fig. axes span 84-92%.

**AlphaZero formulas** [C21 §2.3]: `a = argmax_a ( Q(s,a) + U(s,a) )`, `U(s,a) = C_puct * P(s,a) * N(s)/(1+N(s,a))` as extracted. A square root over N(s) appears to have been lost in the extraction; the standard AlphaZero form has `sqrt(N(s))` **[uncertainty flagged]**. C_puct "typically in [0.5, 5]"; Dirichlet noise at the root in self-play. The network returns policy and value; here the value head has three outputs (win/draw/loss). Loss: `-c_output * sum_i o_test[i] log o_pred[i] - sum_j m_test[j] log m_pred[j] + c_reg * ||theta||^2` with c_output = 0.01 and c_reg = 0.0001 [C21 §3, Eq. 3].

**Supervised ceiling** [C21 §3.2, Table 1]: 1M positions generated with 50% random and 50% solver-perfect moves (harder than 90/10); no duplicates, no trivial positions, only strongest moves accepted. Version 1 = all positions of each game, version 2 = one random position per game. 800k train / 100k validation / 100k test.

| ResNet blocks N | params | v1 move acc. | v1 win acc. | v2 move acc. | v2 win acc. |
|---|---|---|---|---|---|
| 5 | 1.57e6 | 91.63% | 77.47% | 92.44% | 79.23% |
| 10 | 3.05e6 | 92.37% | 77.87% | 93.00% | 79.67% |
| 20 | 6.01e6 | 92.68% | 78.23% | 93.49% | 79.93% |

(Column labelling "Pr_move / Pr_win" is inferred from the caption; the win figures being lower than move figures is as extracted.)

**Extended baseline (known improvements, adopted as comparison)** [C21 §3.3, §3.4]:

| Improvement | Setting | Measured effect in C21 |
|---|---|---|
| Duplicate-position merging | `z_new = z_old*(1-w) + z_dup*w`; tried w = 0.2, 0.5, 0.8; **0.8 chosen** | chosen from Fig. 1 (curves unreadable) |
| Cyclic LR and momentum | cycles over one iteration; LR annealing factor 1 at iteration 1 falling linearly to 0.4 at iteration 20; max LR 0.08 from iteration 20 | "accelerated the learning" (Fig. 3, no numbers) |
| Slow training window | starts 500,000 examples, grows between iterations 5 and 12 to 2M (iterations 5 to 15 when deduplicated) | "no harmful effect" (Fig. 4) |
| Opponent-reply prediction | extra loss `-w_opp * sum pi_opp log pi_opp_hat`, w_opp = 0.35 | "small advantage for some parts of the run" (Fig. 5) |
| Squeeze-and-excitation blocks | FC 128 to 8 with ReLU/sigmoid in residual blocks | "some gains early, possibly small losses later" (Fig. 6); kept for plausibility |
| Network | base: 3x3x64 initial + N blocks of 3x3x128 convs; policy head 3x3x32, value head 3x3x32 | extended baseline better than plain AlphaZero (Fig. 7, no numbers) |

Literature improvements listed in §2.4 with the numbers the authors quote from elsewhere (not measured in this paper): playout caps (Wu 2020) speed training by 27%; forced playouts + policy-target pruning 20%; two networks of unequal size (10 blocks x128 filters and 5 blocks x64) at least doubling training speed; KL-divergence-based focus on complex positions; "propagation of terminal moves to simplify the search" (Videodr0me, LC0) "moderately improving the playing strength" **[OWN: this is MCTS-Solver-style proof propagation inside an AlphaZero-style search]**; draw-probability value target; mixing game result and node value as value target.

**The three proposed extensions - results** [C21 §4, §5]:

1. **Games as trees** [C21 §4.1]: one process plays all games and asks a distributed MCTS-evaluation service; duplicate evaluations are cached. More efficient than the extended baseline (Fig. 8, no numbers). Resetting lost games to explore alternatives: "no significant improvements". Growing one large MCTS tree and training on its nodes: no significant improvement; diversity of positions spikes then collapses as MCTS settles into one style, even with C_puct of 1.5, 5, 15 (Fig. 9).
2. **Network-internal features as auxiliary targets** [C21 §4.2]: tiny single-filter network trained first (2 h, expensive); its features (policy and game output, future moves 2 and 4) regularise an internal layer of the larger net via an MSE term, no extra parameters. Supervised: only +0.2% absolute test accuracy, within run-to-run variance. Full AlphaZero: extended baseline "mostly remained better" (Fig. 10); growing the network during the run improves efficiency but each step up causes a temporary accuracy drop (Fig. 11).
3. **Hyperparameter evolution** [C21 §4.3]: parameter sets (C_puct, fpu, drawValue) as players in an Elo league, Gaussian mutation. The evolution itself works (a planted artificial parameter was optimised perfectly), but game-win fitness does not correlate with solver accuracy: evolved parameters won more of 1000 matches but played worse against the solver. Novelty search gave marginally more new gameplay than the Bayesian-optimised baseline (Fig. 13). No significant improvements.

**Conclusion of the paper** [C21 §5]: effort "could be decreased slightly, the benefit is mostly only small". Almost every quantitative result is in figures that the extraction does not preserve, so apart from Table 1 and the chosen constants no measured speed-ups are available.

---

### 1.7 BR12 - Browne et al. 2012 survey (sections requested)

**UCT** [BR12 §3.3.1]: child j maximises `UCT = X_j + 2*Cp*sqrt(2 ln n / n_j)`, rewards in [0, 1], unvisited children get the largest value; `Cp = 1/sqrt(2)` satisfies the Hoeffding inequality for rewards in [0, 1], and other reward ranges or enhancements need a different Cp (§3.3.1, footnote). With Cp = 1/sqrt(2) this equals `X_j + 2*sqrt(ln n / n_j)`. **Convention warning [OWN]**: W08, L14 and B14/B15 use `C*sqrt(ln n_p/n_i)` (C about 0.7-1.3), R10 uses 0.4, BR12 uses `2*Cp*sqrt(2 ln n/n_j)`. Do not copy a C value across papers without converting. UCT converges to minimax (failure probability decays polynomially) [BR12 §3.3.2]. Final move: max child, robust child, max-robust, or secure child [BR12 §3.1]. UCB1-Tuned replaces `2 ln n/n_j` with `(ln n / n_j) * min{1/4, V_j(n_j)}`, V_j = sample variance + sqrt(2 ln t / s); no regret bound proven but better in practice [BR12 §5.1.1].

**MCTS vs minimax** [BR12 §3.5]: if no reliable heuristic and non-trivial size, minimax is unsuitable and MCTS applicable; if domain knowledge is available both are viable. Ramanujan et al.: UCT performs poorly with many trap states, iterative-deepening minimax relatively well; traps common in Chess, rare in Go.

**MCTS-Solver** [BR12 §5.4.1]: summary of W08: proven wins/losses backpropagated with +-infinity; above visit threshold T normal UCB selection and forced-loss nodes never selected; below T the simulation policy selects and a forced loss may be chosen. Also: Score-Bounded MCTS (pessimistic/optimistic bounds, alpha-beta-like pruning, "beneficial for Connect Four" in solving smaller boards) [BR12 §5.4.3, §7.3]; MC-PNS proves Go values about twice as fast with a quarter of the nodes [BR12 §5.4.2].

**RAVE / AMAF** [BR12 §5.3]: AMAF updates every action seen in the playout as if played first. alpha-AMAF score = `alpha*A + (1-alpha)*U`. RAVE decays alpha with visits: `alpha = max{0, (V - v(n))/V}` (symbols garbled, formula reconstructed; V = visits after which RAVE is ignored) **[uncertain]**. Cutoff AMAF uses AMAF only for the first k simulations. Killer RAVE (only important moves updated) better for Havannah than plain RAVE; poolRAVE improves Havannah and Go. Helmbold & Parker-Wood: playouts give more evidence about earlier moves; AMAF useful after every playout; combined heuristics can be stronger than any single one. With AMAF/RAVE the UCB exploration constant is reported to work best near zero (Go and Hex programs) [BR12 §5.2.9, §7.1.3]. Known weakness: RAVE can accumulate strong bias against moves that are good early but bad later (Sum of Switches discussion) [BR12 §7.3].

**Progressive bias, priors, widening** [BR12 §5.2.5, §5.2.8, §5.5.1]:
- Progressive bias adds `H_i/(n_i+1)` to the selection score (H_i heuristic value of node i); heuristic evaluation can be delayed until a node has a fixed visit count to save time.
- Search seeding: initialise nodes with "virtual wins" (permanent) or a transient estimate blended in as visits increase (RAVE, progressive bias). Szita: virtual wins helped but needed hand-tuning per action; Gelly & Silver: prior data from a function approximator helped most in Go.
- Progressive unpruning/widening (Chaslot; Coulom): heuristic soft pruning that eventually considers all moves. Teytaud & Teytaud: progressive widening WITHOUT heuristic move ordering had little effect in Havannah; small improvement in MoGo.
- First play urgency (FPU) assigns a fixed value to unvisited nodes to encourage earlier exploitation [BR12 §5.2.1].

**Heuristic playouts** [BR12 §6.1]: rule-based policies must be fast; evaluation-function playouts: for LOA the best strategy used the evaluation first to AVOID bad moves and later switched to greedily picking the best move [BR12 §6.1.6]. Monte Carlo alpha-beta (MC-alpha-beta: a selective 2-ply alpha-beta search used as the default policy) is the strongest known LOA player but needs a reliable heuristic [BR12 §4.8.7]. Drake & Uurtamo: improving the simulation policy is more efficient than improving the tree policy (Go) [BR12 §6.1.5]. Balanced playouts matter and "increasing simulation strength may lead to weaker performance overall" (Wang, Gelly) [BR12 §7.1.4]; simulation balancing is in [BR12 §6.1.7].

**Decisive and anti-decisive moves** [BR12 §5.2.2]: decisive = wins immediately; anti-decisive = prevents the opponent's decisive move next turn. In selection and simulation: if either player has a decisive move, play it; else the normal policy. Teytaud & Teytaud (Havannah): significant gain even after paying the checking cost. Also cited: Yavalath plain UCT with pre-search handling of win/loss moves "played a competent game" [BR12 §7.3]; NoGo (Chou et al.) and Havannah: RAVE and anti-decisive moves improve strength [BR12 §7.1.5, §7.2].

**Applications to connection games** [BR12 §7.2]: "The strongest known connection game agents at competition board sizes are currently all MCTS implementations." Hex: MoHex (UCT + RAVE + inferior-cell pruning + bridge completion in playouts) won the 14th and 15th Computer Olympiads; Raiko's UCT plays Hex/Y/*Star/Renkula. Havannah: 2010 Computer Olympiad, 4 of 5 entries MCTS, the alpha-beta entry came last; RAVE helped, progressive widening did not; decisive/anti-decisive moves, Killer RAVE, contextual MC, LGR+N-grams (Stankiewicz: 77.5% over the unenhanced version) all helped. Caveat **[OWN]**: Hex/Havannah/Y are connection games with a complementary-goal structure (exactly one player wins when the board fills), unlike a gravity k-in-a-row game; the survey itself notes board-filling playouts exploit that property [BR12 §6.1.3].

**Other board-game verdicts (skimmed)** [BR12 §7.3]: Connect Four - only Score-Bounded MCTS for solving small boards; Gomoku (five in a row) - "an excellent test case for UCT" but no specific programs; Othello - UCT weak vs strong programs; Shogi - enhanced UCT at strong-amateur level, "poorly against state of the art traditional search"; Khet - straight UCT won no game against the earlier alpha-beta player; Clobber - UCT 44.5% vs world-champion MILA (80,000 playouts/s, 30 s); Mancala - first known game where both minimax and UCT work well with minimal enhancement, with a benefit from a UCT/minimax hybrid if heuristics exist (Ramanujan & Selman); Chess/Draughts - "no human-competitive MCTS implementations reported" up to 2012.

---

### 1.8 CPW - Chess Programming Wiki: Monte-Carlo Tree Search

Content [CPW intro, "Four Phases", "UCT", "Playouts by NN"]: the four phases; pure Monte-Carlo search; UCT as the fix for a program favouring a losing move with a single refutation; PUCT as UCT with a prior over arms; since AlphaGo/AlphaZero the playouts are replaced by network evaluations and the policy serves as PUCT prior. States that MCTS was unsuccessful for Chess until AlphaZero (Dec 2017). The rest is a bibliography. Titles only (contents NOT verified, not read): candidates for follow-up reading: Yen & Yang 2011 "Two-Stage Monte Carlo Tree Search for Connect6" (Connect6 is a 6-in-a-row family game - the closest relative to ours in the list), Lin et al. 2018 "Some improvements in MCTS algorithms for sudden death games", Cohen-Solal & Cazenave 2020 "Minimax Strikes Back", Weinstein et al. 2013 "Rollout-based game-tree search outprunes traditional alpha-beta", Baier 2017 "A rollout-based search algorithm unifying MCTS and alpha-beta", Lanctot et al. 2013 "Monte Carlo *-Minimax Search", Matsuzaki & Kitamura 2018 "Do evaluation functions really improve MCTS?", Chen et al. 2018 "Exact-Win Strategy for Overcoming AlphaZero", plus CCC threads "Search traps in MCTS and chess" (2017) and "Alpha-Beta as a rollouts algorithm" (2018) [CPW Publications, Forum Posts].

---

## 2. When MCTS beats alpha-beta, and when not

### 2.1 Evidence table

Only head-to-head results are listed as "winner". Many sources compare hybrids to plain MCTS, not to alpha-beta (marked "no alpha-beta comparison").

| Game | Time/move | Matchup | Winner | Margin | Source |
|---|---|---|---|---|---|
| LOA | 5 s | MCTS-Solver (PB, playout eval) vs MIA 2000 (alpha-beta) | MCTS-Solver | 69.2% (plain MCTS 58.55%) | W08 §5.2 Table 2 |
| LOA | 5 s | MCTS-Solver vs MIA 2002 | MCTS-Solver | 54.35% (plain MCTS 39.4% loses) | W08 §5.2 Table 2 |
| LOA | 5 s | MCTS-Solver vs MIA 2006 (strongest eval) | Alpha-beta | MCTS-Solver 11.55% (plain MCTS 6.95%) | W08 §5.2 Table 2 |
| LOA | 5 s | MC-LOA (alpha-beta playouts, PB) vs MIA-based alpha-beta | MCTS | 61.8% (63.3% with implicit minimax alpha = 0.2); 20,000 games | L14 §4.3 Table 5 |
| LOA | 5 s | same without PB | Alpha-beta (plain) / even (im) | plain MCTS 40.0%, with im 51.0%; 2000 games | L14 §4.3 Table 5 |
| Breakthrough 8x8, efMS | 1 s | MCTS(bl) vs alpha-beta (ID, static ordering) | Alpha-beta | MCTS 27.55% | L14 §4.2 Table 4 |
| same | 5 s | same | Alpha-beta | MCTS 39.00% | L14 Table 4 |
| same | 10 s | same | about even | MCTS 47.60% | L14 Table 4 |
| same | 1 / 5 / 10 s | MCTS(bl, im0.4) vs alpha-beta | Alpha-beta at 1 s; MCTS from 5 s | 45.05 / 61.60 / 61.80% | L14 Table 4 |
| Breakthrough 8x8, efLH (stronger eval) | 1 s | MCTS(bl') vs alpha-beta | Alpha-beta | MCTS 7.90% | L14 Table 4 |
| same | 120 s | same | Alpha-beta | MCTS 25.38% (500 games, 130 at 120 s) | L14 Table 4 |
| same | 1 / 5 / 10 / 30 / 60 / 120 s | MCTS(bl', im0.6) vs alpha-beta | Alpha-beta below about 60 s; MCTS above | 28.95 / 39.30 / 41.20 / 46.20 / 55.60 / 61.54% | L14 Table 4 |
| Breakthrough, alpha-beta evals | 1 / 5 / 10 s | alpha-beta efMS vs alpha-beta efLH | efMS at 1 s, efLH at 10 s | efMS 70.40 / 53.40 / 31.25% | L14 Table 4 |
| Chess | n/a | UCT vs minimax at traps | minimax identifies level-k traps at depth k+1; UCT does not at k >= 5 even with 10x nodes | utilities in R10 Table 1 | R10 |
| Chess, Draughts | n/a | MCTS vs traditional | traditional until AlphaZero 2017 | no human-competitive MCTS up to 2012 | BR12 §7.3; CPW intro |
| Shogi | n/a | enhanced UCT vs traditional | traditional | UCT at strong-amateur level | BR12 §7.3 |
| Khet | n/a | straight UCT vs earlier alpha-beta | alpha-beta | UCT won no game | BR12 §7.3 |
| Clobber | 30 s | UCT vs MILA (champion) | MILA | UCT 44.5% | BR12 §7.3 |
| Othello | n/a | Monthello (UCT) vs strong programs | strong programs | cannot compete; strongest Othello programs already beat humans before MCTS | BR12 §7.3 |
| Havannah | Olympiad 2010 | 4 MCTS entries vs 1 alpha-beta entry | MCTS | alpha-beta entry last | BR12 §7.2 |
| Hex | Olympiad | MoHex (UCT+RAVE) | MCTS | world champion at 14th and 15th Olympiads | BR12 §7.2 |
| Mancala | n/a | UCT vs minimax | both strong | hybrid helps with heuristics | BR12 §7.3 |
| Kalah | n/a | UCT with minimax-style backups vs minimax | trade-off depends on search time | no numbers in sources read | L14 §1.1 (cites Ramanujan & Selman 2011) |
| Connect-4 7x6 | 250 ms to 5 s | hybrids vs plain MCTS-Solver only | hybrid (MR-2) | 72.1%; no alpha-beta comparison | B15 §V-C |
| Breakthrough 6x6, Catch the Lion | 1 s | hybrids vs plain MCTS-Solver only | hybrids | 60.6-94.8% (see 1.4); no alpha-beta comparison | B15 §V |
| Othello 8x8 | 1 s | hybrids vs plain MCTS-Solver | no effect (MR-1 worse, 43.7%) | MS 50.8%, MB 49.2% | B15 §V-E |

Gaps: none of the sources gives an MCTS-vs-alpha-beta match in Connect-4, Gomoku or any k-in-a-row gravity game at sub-second budgets. The only Connect-4 sources are (a) hybrids vs plain MCTS (B15) and (b) AlphaZero vs a solver-labelled test set (C21). Pons' solver is described in C21 §3 only as "strong solvers exist"; that the solver is alpha-beta based is background knowledge I am adding **[OWN, not in the sources]**.

### 2.2 Conditions that the sources associate with each side

- **Favours alpha-beta / full-width minimax**: a stable, accurate evaluation and a low branching factor (Clune, via B15 §III and B14 §3); high density and difficulty of shallow traps (R10; B15 §V-B); LOA with the best evaluation (W08 Table 2, MIA 2006); short time per move relative to MCTS scaling (L14 Table 4 shows MCTS's share rising monotonically with time, at 1 s alpha-beta wins on both evals); stronger evaluations favour alpha-beta more (L14 §4.2: efLH makes MCTS worse relative to alpha-beta at low time).
- **Favours MCTS**: no good evaluation (BR12 §3.4.1, §3.5); high branching factor (Go 250, Havannah); delayed-reward games with few traps (Go, Othello-like, Hex/Havannah) (BR12 §7); longer time per move (L14 Table 4); a weak or noisy heuristic (L14 §4.2 "MCTS benefits more from weak heuristic information").
- **Both fine**: Mancala (BR12 §7.3).
- **Hybrids help MCTS where**: trap density and difficulty are high (Catch the Lion > Breakthrough > Connect-4 > Othello) [B15 §V-H]; MR needs low branching (about 10 or less), MS and MB tolerate about 50 [B15 §VI].
- **Hybrids with heuristic priors/cutoffs help when** the evaluation is stable enough to lean on (Othello, where evaluation is less volatile, IP-M best; Catch the Lion, where the evaluation is volatile because of capture exchanges, IC-E best and IP-E weak) [B14 §5.5].

---

## 3. Implications for a 100 ms Java bot (trap-heavy gravity 6-in-a-row with a destroy move)

Everything in this section is **[OWN inference]** built on the cited evidence unless a citation is attached.

### 3.1 What our game looks like against the variables the sources identify

| Variable | Quantum Connect (as given) | Where it sits in the sources | Consequence |
|---|---|---|---|
| Branching | about 12 column drops; on destroy turns plus the destroy targets (up to the number of 3x3 placements; engine must confirm; potentially 60+) | Connect-4 6.4; Breakthrough 15.5 (MR collapses); hybrids MS/MB OK to about 50 [B15 §V-H, §V-I] | Non-destroy nodes are in the MR danger zone; destroy turns are an outlier of high branching. Needs k-best/ordering of destroy moves in any search |
| Terminal density / traps | wins only by 6-in-line, but the destroy move can create a 6-line for the mover in one move (gravity re-applied) | Hybrids benefit most where terminals and traps are dense [B15 §V-H] | Likely denser, sharper tactics than Connect-4 because of destroy; but six-in-a-row threats form slower than four-in-a-row, so ordinary traps are probably deeper. Measure, do not assume |
| Time | 100 ms | tested budgets: 250 ms to 5 s [B15 §V-G]; 1 s [B14, L14] | We are below every tested budget |
| Simulations available | unknown; scale argument below | sims/s in sources 8.7k to 91k per second [B15 §V] | likely a few thousand playouts per move |
| Evaluation | learned pattern evaluation | alpha-beta gains more from a good eval than MCTS (L14 §4.2) | favours alpha-beta |
| Game length | up to about 96 plies plus destroys, random playouts long | Breakthrough/Othello long playouts slow MR a lot [B15 §V-D1] | rollout-based variants lose speed |

Order-of-magnitude scale argument: at 91,000 sims/s (Connect-4 baseline) 100 ms is about 9,100 simulations; at Othello's 8,700 sims/s about 870. The smallest budget B15 tested (250 ms) gives about 22,800 (Connect-4), 11,300 (Breakthrough), 2,200 (Othello), 8,700 (Catch the Lion) simulations. A 96-cell board with six-in-a-row has longer playouts than 42-cell Connect-4, so I expect a few thousand simulations at 100 ms even with efficient bitboard code, and a Java JIT warm-up penalty in the first moves. This is an estimate, to be replaced by a measurement of our own playout speed.

### 3.2 Is a hybrid worth it?

**Verdict: no, not as the primary engine. Keep alpha-beta + threat search + learned evaluation. Confidence: medium.** The reasons:

1. Every positive MCTS-hybrid result is relative to plain MCTS, never relative to alpha-beta at comparable budgets [B14, B15]. The reasons hybrids exist (MCTS misses shallow traps [R10]; MCTS needs minimax to see them [B15 §III]) are reasons a full-width alpha-beta searcher does not have the problem in the first place: depth k+1 finds every level-k trap exactly [R10 "Identifying and Avoiding Traps"].
2. At the shortest budgets measured, alpha-beta beats MCTS even in a game (Breakthrough) where MCTS-with-implicit-minimax later overtakes: 1 s: MCTS 27.55%-45.05% (efMS) and 7.9%-28.95% (efLH) [L14 Table 4]. Our budget is ten times shorter, and we plan to have an evaluation (L14: stronger evaluation makes alpha-beta stronger faster).
3. The one hybrid result that is the best single-algorithm result in B14 depends on evaluation priors with weights of 1000-5000 (rollouts almost never override them) [B14 §5.5], which turns the search into heuristic-driven best-first minimax. We would be rebuilding alpha-beta with worse efficiency.
4. The paradox results warn that making a component stronger as a standalone searcher does not make it better inside MCTS (CtL: depth 2 beats depth 1 95.8% standalone, loses 38.8% as rollout policy) [B14 §5.3]. Nothing guarantees our alpha-beta/threat module would transfer well into MCTS, and tuning would cost a lot of games at 100 ms.
5. The destroy move adds large branching on a subset of nodes; MR collapses with branching (30.8% to 10.2%, MR-2 to 0.1%) [B15 §V-I]. Only MS/MB are branching-robust, but they were tested only at >= 250 ms.

### 3.3 What to take from the MCTS literature anyway (component verdicts)

| Component | Verdict | Reason (source) |
|---|---|---|
| Decisive and anti-decisive move check (win-in-1 and block-in-1, including destroy moves that create a 6-line for either side) at every node and at the root | **Adopt** (probably already in the threat search) | Cheapest trap protection; large gains in MCTS rollouts [BR12 §5.2.2]; MR-2 in Connect-4 gave 72.1% over random-rollout MCTS-Solver [B15 §V-C1]; L14 §4.4: "traps are effectively handled" by it. In alpha-beta it is a move-ordering and extension rule |
| Proven-value propagation (Solver idea) | **Already in alpha-beta**; add early termination when the root is proven | MCTS-Solver exists to imitate alpha-beta's exact terminal values [W08 §4]; LC0 uses terminal propagation [C21 §2.4] |
| Secure-child final move rule | **Skip** | needed only because Solver values jump; alpha-beta returns a PV |
| Random-rollout MCTS as the engine | **Skip** | see 3.2 |
| MCTS-MR (minimax in rollouts) | **Skip** | branching-sensitive; slows sims by 3x at depth 2 even in Connect-4 [B15 §V-C1] |
| MCTS-MS / MCTS-MB | **Only inside an experimental MCTS engine** (see 3.4) | branching-robust but untested below 250 ms |
| Implicit minimax backups (alpha about 0.2-0.4) | **Only inside the experimental engine** | help MCTS with an evaluation [L14]; irrelevant to alpha-beta |
| Node priors / progressive bias from the learned eval | **Reinterpret as move ordering** in alpha-beta (policy-like ordering from the pattern model; k-best at destroy nodes) | MCTS-IP gains come from heuristic move preference [B14 §5.5]; B15 §VI suggests k-best to tame branching |
| RAVE / AMAF | **Skip** | moves are not order-independent here **[OWN]** (gravity changes available cells; the destroy move re-applies gravity). Success in Hex/Havannah relies on position-independent cells and complementary goals [BR12 §6.1.3, §7.2] |
| Progressive widening | **Treat as destroy-move ordering/pruning idea only** | no effect without heuristic ordering (Havannah) [BR12 §7.2, §5.5.1] |
| Dirichlet noise / PUCT / network | **Skip** (no GPU, stdlib only, 100 ms; a policy net would need to be tiny) | CPW: PUCT needs a policy prior [CPW "UCT", "Playouts by NN"] |
| AlphaZero training-efficiency tricks (position de-duplication with w = 0.8, slow training window, cyclic LR) | **Consider for training the learned evaluation** | these were the improvements the authors kept; effects "small" and reported only in figures [C21 §3.3, §5]. Our setting differs: no solver exists for 8x12/6/destroy so the accuracy-vs-solver metric is unavailable |
| Trap-density measurement protocol (B15 §V-B) | **Adopt as a diagnostic** | gives a quantitative way to compare our game with Connect-4/Breakthrough/Othello |

### 3.4 If you still want to test an MCTS candidate: exact first variant

Run as a controlled experiment, not as a plan of record. Pick the variant that fits 100 ms: NO random rollouts, because rollouts are what make the sample count collapse and what the heavy-prior results found unnecessary.

**Variant "UCT-EV"** (eval-leaf UCT, all components with published support):

- MCTS-Solver with the W08 leaf check (all children of a leaf checked for an immediate win, including destroy moves) [W08 §4.2].
- Leaf evaluation = our learned pattern evaluation, squashed to [-1, 1] (sigmoid/tanh) for L14 conventions or [0, 1] for B14 conventions. This corresponds to MCTS-IC-E with m = 0 (no rollout moves), the best IC setting in all three B14 games and the best individual player in Catch the Lion (98.1%) and strong in Othello (61.1%), but weak where the evaluation is poor (Breakthrough, 41.9%) [B14 §5.4]. Quality of the learned evaluation is therefore the decisive factor.
- Implicit minimax backups with **alpha = 0.25 to start** (sweep 0.15, 0.25, 0.4; the safe range reported is [0.15, 0.4], higher values only with priors) [L14 §5]. Expand all children with `v = v0(child)` and do the one-ply backup [L14 §3].
- Optional node prior from the evaluation as virtual wins (B14 weights: 1000-5000 were best with random rollouts; with no rollouts the prior is effectively the value, so skip this and rely on `v0`).
- C: B14/B15 used 0.7-1.3 with UCB1-Tuned and rollouts [B15 §V]; for eval-leaf search there is no published value. Start at 0.7 and sweep {0.3, 0.5, 0.7, 1.0} **[OWN, no evidence]**.
- Decisive/anti-decisive move shortcut in the tree at every node [BR12 §5.2.2].
- Expand all children at once; transposition table keyed on board + both destroy-cooldown counters (the cooldown is part of the state, so MCTS nodes and any hash must include it).

**Variant "UCT-EV + MS"** (add second, only if UCT-EV is within about 5 points of alpha-beta): MCTS-MS-2-Visit-1 (depth 2 loss-only window, trigger at first revisit), which was the Connect-4 optimum (53.6%), then MS-4-Visit-2 (Catch the Lion optimum, 76.8%; depth 4 corresponds to level-3 traps **[OWN reading: MS depth d detects traps of level d-1 for the mover]**). These parameters were tuned with rollouts and at >= 250 ms, so they are starting points only.

**Go/no-go gate** **[OWN]**: pit the variant against the production alpha-beta bot at exactly 100 ms, same hardware, at least 1000 games with swapped seats (B14/B15/L14 all use 1000+ games and half-point draws; 95% CIs are about +-3% at 1000 games [L14 Table 2]). Ship or hybridise only if the variant scores at least 50% against the production bot. Otherwise drop MCTS.

**Diagnostics to run first (cheap)**: (a) replicate B15 §V-B on our engine: 1000 self-play games, at every position count level-1/3/5/7 trap moves; (b) 1000 random rollouts from each trap move to get "rollout error rate". If level-3 traps are as rare as Othello (1.2% of legal moves) a hybrid has nothing to fix; if closer to Breakthrough (18%) or Catch the Lion (37%), tactics dominate and exact search (alpha-beta) is even more important [B14 §5.2].

### 3.5 Concrete improvements to alpha-beta suggested by this literature

**[OWN]**
1. Treat the destroy move like MCTS treats wide nodes: order destroy candidates by the learned pattern model plus immediate-win tests, and consider k-best pruning at destroy nodes. The B15 authors point to k-best as the path to viability at high branching [B15 §VI].
2. Make cooldown state first-class in hashing and evaluation (an unavailable destroy for 9 of 10 turns is a different position from an available one).
3. Early exit when a forced win/loss is proven (Solver's "stop when root proven"; W08 §4.3).
4. If something Monte-Carlo-like is wanted at 100 ms, use it as a tie-break or tuning tool offline (self-play data for the evaluation), not at move time.

---

## 4. Open questions

1. **Connect-4-class games at sub-second budgets, MCTS vs alpha-beta.** None of the sources reports it. Is hybrid MCTS ever better than a plain alpha-beta + threat search on a k-in-a-row gravity game at 100 ms? Needs an experiment on our own engine.
2. **Behaviour below 250 ms.** B15's shortest tested budget is 250 ms; curves are in figures that the extraction does not preserve (Figs. 24-27). Whether MS/MB gains persist at about 2.5x fewer simulations is unknown.
3. **Trap statistics of Quantum Connect.** Density and rollout-difficulty of level-3..7 traps in our game, with and without destroy-move sequences, are not known. Six-in-a-row may push typical traps deeper than level 7.
4. **Does the destroy move invalidate MCTS assumptions?** Gravity re-application means the destroy result depends on the whole column state; AMAF-style statistics and many independence assumptions break. Branching and terminal-creating tactics on destroy turns are unmeasured.
5. **Conflicting evidence on whether MCTS-Solver alone handles shallow traps.** L14 §4.4 says it does (with decisive/anti-decisive playouts); B15 reports 60-77% hybrid gains over MCTS-Solver in Breakthrough/Catch the Lion with random playouts. The likely reconciliation is playout quality (L14 used improved playouts), but this is not tested in the sources.
6. **Evaluation quality threshold.** B14 shows IC-E collapses when the evaluation is poor (Breakthrough 41.9%); L14 shows alpha-beta gains more from a better evaluation. At what accuracy does eval-driven UCT stop being worse than alpha-beta? Unknown for the learned pattern evaluation.
7. **Why do stronger embedded searches make MCTS weaker?** Documented in B14 and B15 (policy-strength paradox) but unexplained; limits predictability of any hybrid tuning.
8. **Draw handling.** W08 does not prove draws; Quantum Connect may allow draws (full board or other). Score-bounded MCTS exists [BR12 §5.4.3] but was not evaluated here.
9. **Clausen et al.** Most quantitative effects are in unreadable figures; the cost reductions of their extensions and of the extended baseline are not available as numbers, and they used a solver-labelled test set that we cannot construct for this game.
10. **Extraction uncertainty to re-check against the PDFs** if a decision hinges on it: R10 Table 1 cell assignment; L14 Algorithm 1 listing; BR12 RAVE alpha formula; B15 Figs. 8, 9, 23-27, 31 numeric values; C21 Eq. 2 square root; B15 inconsistency UCB1 (§II) vs UCB1-Tuned (§V); B15 "empty Connect-4 board" sentence inside the Breakthrough section.
11. **Unread follow-ups listed by CPW** with the closest subject (Connect6 two-stage MCTS, sudden-death-game MCTS improvements, "Minimax Strikes Back", rollout-based search unifying MCTS and alpha-beta) were not read here.
