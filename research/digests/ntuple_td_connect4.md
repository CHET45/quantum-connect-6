# Digest: N-tuple networks + TD learning for Connect Four (Thill / Bagheri / Konen line)

Purpose: enough exact detail to re-implement the method for "Quantum Connect" (8x12, six in a row, 3x3 "destroy" special move with 10-own-turn cooldown, 100 ms/move, Java stdlib only).
Everything below marked **[INF]** is my own inference or design proposal, not a claim of the sources. Everything else is cited as `[code] section/location`.
Source texts were pdftotext extractions; where a formula or table was scrambled I say so and say how I reconstructed it (see section 8).

## 0. Source key

| code | file in `sources/text/` | what it is |
|---|---|---|
| **T14** | `pdf-p1-thill2014-temporal-difference-learning-with-eligibility.md` | Thill, Bagheri, Koch, Konen, CIG 2014: eligibility traces for Connect-4 n-tuple TD |
| **B14** | `pdf-p1-bagheri2014-temporal-coherence-in-tdlearning-for.md` | Bagheri and Thill, case-study report Nov 2014: TCL, IDBD, TCL-EXP, Q-learning, traces (full parameter tables in App. A/B) |
| **T15** | `pdf-p1-thill2015-temporal-difference-learning-methods-with.md` | Thill, MSc thesis June 2015: step-size adaptation survey (TCL-EXP, IDBD, nl-IDBD, SMD, Autostep, K1, ELK1, alpha-bounds), eligibility-trace variants, Dots-and-Boxes |
| **K15** | `pdf-p1-konen2015-reinforcement-learning-for-board-games.md` | Konen TD tutorial (states vs afterstates, Algorithms 1-3) |
| **G22** | `pdf-p2-konen2022-gbg-technical-report-on-the.md` | GBG framework tech report incl. App. C (n-tuples) and App. G (parameter settings of the best agents) |
| **SK22** | `scheiermann-konen-alphazero-ntuple.md` | Scheiermann and Konen 2022: MCTS wrapper around TD n-tuple agent at test time; Connect-4 numbers |
| **FA21** | `konen-final-adaptation-rl-nplayer.md` | Konen and Bagheri: TD-FARL (final adaptation), Alg. 1/2/7, eligibility "horizon", TCL, App. 0.A.6 parameters |
| **W** | `gbg-readme-wiki.md` | GBG README/wiki (code layout, file sizes, memory flags) |
| **N2** | `gbg-ntuple2-tdntuple3.md` | GBG source: `controllers/TD/ntuple2/*` (NTuple2, NTuple2ValueFunc, NTupleFactory, NextState, TDNTuple3Agt) |
| **N4** | `gbg-ntuple4-tdntuple4.md` | GBG source: `controllers/TD/ntuple4/*` (same, with per-cell position values; replay-buffer option) |
| **C4P** | `gbg-connect4-ntuple-params.md` | GBG source: `XNTupleFuncsC4` (the 70 fixed 8-tuples, cell coding, mirror), `ParNT`, `ParTD`, `StateObserverC4` |

Not in this folder: Thill/Koch/Konen PPSN 2012 (the original Connect-4 n-tuple paper), Bagheri et al. T-CIAIG 2015 (journal version of B14). Their content is only known second-hand through T14/B14/T15.

## 0.1 One-paragraph summary

A **linear sum of look-up-table weights** over many n-tuples of board cells, passed through **tanh**, trained by **self-play TD** on **afterstates**, with **eps-greedy** exploration (eps about 0.1), **two LUT sets (one per player)**, **left-right mirror symmetry**, and a **4-valued cell code that distinguishes empty-reachable from empty-unreachable** (the gravity trick). On 6x7 Connect-4 the best systems (70 or 150 random-walk 8-tuples, eligibility traces or TCL-EXP per-weight step sizes) reach ~93-94 % score against a perfect alpha-beta player (agent always moving first, so the ideal is 100 %) after 0.1-0.5 M self-play games (about 25 CPU-minutes in 2014) and ~2 M games for the asymptote. A later GBG variant (final adaptation, no traces, 6 M games, ~3.4 CPU-hours) reaches the same level (about 91-93 % wins as first player), and adding a shallow MCTS on top of the same weights at test time lifts first-player wins vs the perfect agent from 91 % to 99 % [SK22 Tab. II]. No source reports using the evaluation as an alpha-beta leaf evaluator; the only precedent is the "RL-Minimax agent" listed in T15 App. B and GBG's `MaxN2Wrapper` [G22 3.5.2], both without published results.

---

## 1. Value function and n-tuple representation (exact)

### 1.1 Value function

- Network: **no hidden layer**. `f(w,x(s)) = sum over n-tuples nu, sum over LUT entries i of w[nu][i] * x[nu][i](s)`, where `x` is a 0/1 indicator: exactly one LUT entry per tuple (per symmetric image) is active. [T14 II-A Eq. 1-3; B14 2.4 Eq. 2.30-2.33; T15 3.3 Eq. 3.23-3.25]
- Output nonlinearity: **`V(s) = tanh(f)`** [T14 II-B Eq. 4; T15 3.4.1 Eq. 3.27]. In GBG `NTuple4ValueFunc` "the sigmoid function is always tanh, so that V in [-1,1]" [G22 App. C.3 fn. 22]; best Connect-4 agent: "output sigmoid tanh" [G22 App. G.2; FA21 0.A.6]. K15 Sec. 5 and App. B also discuss linear-without-sigmoid and logistic variants ("Function approximators with no sigmoid on the output neuron are sometimes better"); IDBD/K1/Autostep experiments omit the sigmoid [T15 4.2]. For Rubik's cube GBG uses `sigma = id` [G22 App. G.3].
- Rewards: only at game end, **win +1, draw 0, loss -1** [T14 II-B]. (K15 Sec. 3 uses 1/0.5/0 in its TicTacToe example; ignore.) Value of a terminal state is defined as 0 and the net is never trained to hit the terminal reward directly: the predecessor is trained instead [K15 4.1.2 Eq. 3, 4.3 notes; N2 `TDNTuple3Agt.getScore(so,refer)` returns 0.0 if `so.isGameOver()`, l.2942-2951].
- Code (GBG): `NTuple2ValueFunc.getScoreI(StateObsWithBoardVector, int player)` [N2 l.1523-1542]:
  `score = sum_{i<numTuples} sum_{j<equiv.length} nTuples[0][player][i].getScore(equiv[j].bvec); return hasSigmoid ? tanh(score) : score;`
  Note: **no averaging over the symmetric images** (a commented-out `score /= equiv.length` is marked "DON'T, at least for TTT clearly inferior", l.1538). So with mirror symmetry each state contributes up to 2 x m LUT entries to the sum.
- The value is from the viewpoint of the **player who just moved** (creator of the afterstate) in GBG (see 2.2). In T14/B14 the older formulation uses one signed value (Yellow = +1) and two LUT sets (details of which LUT is read when are not spelled out in the extracted text).

### 1.2 Cell states, "empty reachable" vs "empty unreachable" (the gravity encoding)

- P = 4 states per sampling point: **0 = empty and not reachable, 1 = Yellow (first player), 2 = Red, 3 = empty and reachable**. "Reachable" = an empty cell that can be occupied in the next move. Reason: three in a row with a *reachable* empty neighbour is a direct threat, with an unreachable one an indirect threat. [T14 II-A; T15 3.3 (p.26); SK22 Fig. 3 caption]
- GBG code: `XNTupleFuncsC4.getBoardVector` [C4P l.287-313]: board stored column-major, `cell = col*ROWCOUNT + row` (rows 0..5 bottom to top). Empty cell becomes 3 if `row==0` or the cell below holds a disc (value 1 or 2), else stays 0. A comment documents a past bug where the test read `board[i][j-1]` instead of the already-updated `bvec` [C4P l.303]; the check as written tests only "cell below is a disc", which is equivalent for a gravity-consistent board.
- GBG also supports P = 3 (plain empty) but marks it "*not* recommended" [C4P `getNumPositionValues`, l.245-248; `getBoardVector` doc l.276-285]. The sources give no measured P=3 vs P=4 comparison for Connect-4 (T14/B14 only say the distinction matters, citing the PPSN 2012 paper).
- The board vector is built from the **afterstate** (the board after the mover's disc landed), so the "3" cells are exactly the opponent's legal landing cells. [inferred from N2 `adaptAgentV`/`getNextAction2`, which evaluate `NewSO` after `advance`]
- Non-realisable tuple states: for tuples that contain a cell and the cell below it, the upper cell cannot be 1, 2 or 3 if the lower cell is 0 or 3 [T14 fn. 4]. Consequently only **~7 % (T14 II-A) / 8 % (SK22 Tab. I)** of the addressable weights are ever non-zero: standard system 600 000-700 000 active of 9 175 000; the 150x8 system 1.3-1.5 M active [T14 fn. 5].

### 1.3 Index computation

`i = k_nu = sum_{j=0}^{n-1} s[tau_nu_j] * P^j` [T14 Eq. 1; T15 Eq. 3.23]. GBG: `NTuple2.getIndex(int[] board)`: `index += P*board[nTuple[i]]; P*=posVals` [N2 l.198-206]; `NTuple4.getIndex` uses a running product of per-cell `posVals` [N4 l.207-216]. LUT length `P^n` (`new double[Math.pow(posVals,len)]`, N2 l.143). Worked example in T14 Fig. 1 / T15 Fig. 3.5: cells read 1-2-1-1 give k = 1*4^0 + 2*4^1 + 1*4^2 + 1*4^3 = 89, mirrored 3-0-1-2 gives 147.

### 1.4 How many tuples, how long, how chosen

| item | value | source |
|---|---|---|
| "standard" system | **70 tuples x length 8**, 2 LUT sets, 2x70x4^8 = **9 175 040** weights | T14 II-A; T15 4.2; SK22 Tab. I |
| "M" system | **150 x 8** (about 20 M addressable, 1.3-1.5 M active); learning ~1.5x faster, loss rate 7.0 % to 5.5 % | T14 III-C; T15 5.1.4; B14 4.6.3 |
| 70 x 7 | used in a step-size sensitivity test, worse than 70x8 (TDL max ~0.8 vs ~0.9 asymptotic score over alpha range, read off Fig. 4.5, qualitative) | B14 4.2.3 |
| why 70x8 | "good compromise between computation time and accuracy"; "several n-tuple architectures tried" | T14 fn. 3; T15 4.2 |
| GBG best agent | `FIXEDNTUPLEMODE=1`: **70 specific 8-tuples** (hard-coded list) | G22 App. G.2; FA21 0.A.6; C4P `fixedNTuples` l.368-451 |
| how generated | **random walk on the board** from a random start cell, "no cell visited twice", step to one of the **8 neighbours** (Thill) | T15 3.4.2; T14 III-A ("created by random walks on the board [1]") |
| fixed or per-run | the same fixed 70x8 set was reused in all Connect-4 experiments; for Dots-and-Boxes a new random set per run (to show robustness) | T15 4.2; T15 5.2.1 |
| overlap | a cell may be in many tuples; a tuple never contains a cell twice; two tuples may coincide | G22 8.9, App. C.2; N2 factory comments |
| quality of tuple choice | **open**: "more systematic approach to create relevant n-tuples could improve" (e.g., judge by |weights|) | B14 5.2; T15 6.2 |

Two generators in GBG [N2 `NTupleFactory`, l.971-1151; N4 `NTuple4Factory` same]: `generateRandomWalkNTuples` = **grow a connected cell set**: start with one random cell; repeat: pick a random cell already in the set, remove already-chosen cells from its `adjacencySet`, if anything is left add a random one (else retry). This yields "bifurcations and cross shapes", not only snakes [SK22 Fig. 2 caption]. `generateRandomPointNTuples` = uniformly random distinct cells, no adjacency (G22 C.2: "often not advantageous").
Detail that matters when re-implementing: `XNTupleFuncsC4.adjacencySet` returns the **4-neighbourhood** [C4P l.478-507], whereas Thill's text says 8 neighbours [T15 3.4.2]. The 70 hard-coded GBG tuples do contain diagonal-only links (e.g. `{0,6,7,12,13,14,19,21}`: cells 14=(col2,row2) and 19=(col3,row1)/21=(col3,row3) touch only diagonally), i.e. they were produced by the **8-neighbour** walk. If you want to mimic the published tuple statistics, use 8-neighbours.

### 1.5 Two LUT sets (one per player)

- "Important observation: the values for certain sampled subparts of the board are typically different depending on which player's turn it is. Therefore we introduced two LUTs per player" - this "for the first time led to satisfying results" for the full game (before, only truncated 10/12-move games worked) [B14 4.1; T14 II-A; T15 3.4.3].
- T15 3.4.3 contrasts it with *board inversion* (colour-swap and negate; suitable for impartial games like Dots-and-Boxes) and states that for Connect-4 "this was the only approach that led to good results; the strategies for Yellow and Red are rather different".
- GBG: `NTuple2[numOutputs][numPlayers][numTuples] nTuples` [N2 l.1357-1358]; `numOutputs==1` for TD, `numActions` for SARSA/Q. Cost: doubles memory.

### 1.6 Symmetry

- Connect-4 has **one** symmetry, left-right mirror. For every state, both index sets (original and mirrored) are activated: `x_i = 1 if i in {k, k_mirror}` [T14 II-A Eq. 2; B14 2.4 Eq. 2.34]. GBG: `XNTupleFuncsC4.symmetryVectors` returns `{board, flip(board)}`, `getNumSymmetries()=2` [C4P l.261-335]; `flip` reverses column order keeping the within-column order [C4P l.526-535].
- GBG detail ("inhibition of multiple changes"): when both images hit the same LUT index, the **sum** counts the weight twice but the **update** touches it once per tuple per state (`indexList.contains`, `clearIndices()` called per tuple per state) [N2 `NTuple2.updateNew` l.362-389; FA21 0.A.4]. Reason: guarantees that with alpha=1 and a linear net the new value equals the target.
- Per-update step scale: `alphaM = ALPHA / (numTuples * equiv.length)` [N2 `NTuple2ValueFunc.update` l.1836; FA21 0.A.3 Eq. 3]. With m=70, N_S=2 this divides alpha by 140.

### 1.7 Weight initialisation

- C4GPF (Thill): uniform in [-xi/2, xi/2], xi = 0.001 [T14 III-A; T15 4.2; B14 3.2].
- GBG: **zeros** (`RANDINITWEIGHTS = false`), which also lets the code count "active" weights as `lut != 0` [N2 l.2606; `activeWeights()` l.2241-2267].

---

## 2. Learning algorithms and update equations

### 2.1 Plain TD(0) self-play (older "C4GPF" scheme: chain of consecutive plies)

[T14 II-B Eq. 5-6, Alg. 1; K15 Alg. 2; T15 3.4.1 Alg. 1]
- Per ply t: `Vold = V(s_t)`; with prob. eps choose a **random** legal move, else the **greedy afterstate** maximising `p * (R(s_{t+1}) or V(s_{t+1}))` (p=+1 Yellow, -1 Red; V is a Yellow-perspective score).
- `delta_t = R(s_{t+1}) + gamma*V(s_{t+1}) - V(s_t)`, **gamma = 1**, `V(terminal) = 0`.
- Update `w <- w + alpha * delta_t * grad_w V(s_t)`; for tanh+linear LUT `grad = (1 - V(s_t)^2) * x(s_t)` [T14 Eq. 6]. Only the weights active in `s_t` (<= 2m LUT cells) change.
- **No update after a random move** (the value of the successor of a random move is a bad predictor), *except* when the move is terminal [T14 Alg. 1 l.373; K15 Alg. 2 and notes; T15 3.4.1]. Training is done "online", per ply.
- Training is from the empty board only; eval benchmark also from the empty board. Training and benchmark never use the perfect agent in training.

### 2.2 The newer GBG formulation: player-centred afterstate TD + FARL ("TD-FARL")

[FA21 2.2-2.7, Alg. 2, Alg. 7, 0.A.3-0.A.4; code N2 `TDNTuple3Agt`, N4 `TDNTuple4Agt`]

Idea: each player has its own value `V_p` of **its own afterstates**, and each afterstate is trained toward the player's **next afterstate one round (2 plies) later**; the opponent's moves are folded into the "environment reaction". Every player *maximises* its own `V_p`, so there are no min/max cases and it works for any N.

Training loop per episode (`TDNTuple3Agt.trainAgent` l.3271-3350; same in `TDNTuple4Agt.trainAgent` N4 l.3204-3288):
1. `sLast[n]=null` for all players; (for a non-empty start state `sLast[prev player] = so.precedingAfterstate()`).
2. Repeat: `a_t = acting_pa.getNextAction2(s_t, random=true, ...)` (eps-greedy, below); `ns = new NextState(this, s_t, a_t)` (afterstate and next state; identical for deterministic games); `randLast[cur] = a_t.isRandomAction()` **set before adapting** (a 2019 bug fix, comment l.3308-3312); `R = reward tuple of next state for all players`; `adaptAgentV(cur, R, ns)`; `sLast[cur] = afterstate`, `rLast[cur] = R[cur]`.
3. `adaptAgentV` [N2 l.3067-3159]:
   `v_next = (s_next.isGameOver()) ? 0 : V_cur(afterstate)`;
   if `sLast[cur] != null`: `r_next = (R[cur] - rLast[cur]) (+ step reward)`; `target = r_next + gamma*v_next`; `vLast = V_cur(sLast[cur])`;
   **if the move that produced the afterstate was random and `learnFromRM==false`: no update** (and `clearEligList` if eligibility type is RESET); else `updateWeightsTD(sLast[cur], cur, vLast, target)` (delta = target - vLast, `e = 1 - vLast^2`).
4. After the terminal move, `finalAdaptAgents` [N2 l.3176-3241], two parts:
   - **Part 1 (this is "FARL")**: for every player `n != cur` with `sLast[n] != null`: `target = R[n] - rLast[n]` (the terminal delta reward, e.g. -1 for the loser, 0 for a draw); adapt `V_n(sLast[n])` toward it.
   - **Part 2**: for the player who made the final move: adapt the value of the **terminal afterstate** toward **0** (skipped if the episode ended by length cap).
5. `finishUpdateWeights()`: `ALPHA *= alphaChangeRatio` (exponential schedule, ratio `(alfin/alpha)^(1/maxGames)`) and `m_epsilon -= (eps0 - epsFinal)/maxGames` (linear) [N2 `NTupleBase.setTDParams` l.806-817, `finishUpdateWeights` l.751-766].

Evidence that FARL part 1 is **essential** in this scheme: without it the ConnectFour agent collapses (win rate vs perfect AB 3.5 % -> 49.5 % with FARL, vs AB-DL 0.0 % -> 46.5 %; 8 % active weights with FARL, **0.9 % without**), because the side that loses never gets the negative reward propagated into its pre-loss afterstate, so it never learns to avoid precursors of threats. Part 2 alone-off degrades by <= 1 %. [FA21 3.2 and fn. 7, Tab. 1, 3.5]. The older ply-chain scheme (2.1) did not need FARL because the loser's previous state is connected to the next ply [FA21 3.5]. Othello is the exception (FARL indifferent) [FA21 3.3].

### 2.3 TD(lambda) and eligibility traces

**Thill's version (explicit sparse traces)** [T14 II-C, Alg. 1; T15 3.2.2, 3.4.1, 5.1.4]
- `e_t = lambda*gamma*e_{t-1} + grad_w V(s_t)`, `e_0 = grad V(s_0)`, update `w += alpha*delta_t*e_t`. gamma=1.
- Naive trace vector would be 9 M long. Observation: sparse. Upper bound of non-zero traces per game 42 plies x 70 tuples x 2 (mirror) = 5 880; **measured mean ~1 800** (3 600 for 150 tuples). Implemented as a **`TreeMap`** (self-balancing BST) holding only active traces, reset after each episode; memory reduced by a factor ~5 000 [T14 II-C; T15 3.4.1/5.1.4].
- Variants (notation `[et]`, `[res]`, `[rep]`, `[rr]`): **[res]** = zero all traces after a random move; **[rep]** = *replacing* traces: on activation set `e_i = grad_i` instead of accumulating (the OCR shows the condition as `x_i = 0 and REP`; logically it must be `x_i != 0`); **[rr]** = both. [T14 II-C Alg. 1, Fig. 2; B14 2.3.3]
- Outcome of variants (see section 4): all ~1.5-2x faster than no traces; `[rr]` fastest to 80 %; plain `[et]` (no reset, no replace) gives the best asymptotic strength (+~1-2 points) and "this improvement was lost when adding reset or replace".
- Best lambda: **0.4-0.7** for `[et]`; 0.5 for TDL/TCL `[et]`; 0.6 for `[res]`/`[rep]`; 0.8 for `[rr]` (0.5 for nl-IDBD `[rr]`, 0.7 for SMD `[rr]`). **lambda >= 0.9 breaks down completely**, attributed to exploratory-move rewards being projected too far back; confirmed because `[res]` with lambda=0.9 behaves like `[et]` with 0.8 [T14 III-C; T15 5.1.4 Fig. 5.8 and "Resetting and Replacing" section].
- Sparse-activation argument for why traces help: each position activates only 140 of ~650 000 active weights (0.02 %); traces let each weight learn in a greater share of the time steps [T14 IV].

**Jaskowski's "finite horizon" version (GBG)** [FA21 2.3, 0.A.3; N2 `NTuple2ValueFunc.update`/`setHorizon` l.1826-1875, 1958-1964]
- Instead of a trace vector, keep the last `h` afterstates **per player** (`eList[player]`, a `LinkedList<EligStates>` storing the symmetric board vectors and the tanh derivative `e` of each state at the time it was visited) and at each step apply the current `delta` to all of them: `w_i[Ind(q)] += alpha/(m*N_S) * delta_t * lambda^(t-k) * sigma'(nu(s'_k)) * tcFactor`, `k = t, t-1, ..., max(t-h,0)`.
- `h = 1 + (int)(log(horizonCut)/log(lambda))` (so only terms with `lambda^age >= horizonCut` survive); `horizonCut` default 0.1, **0.01 for ConnectFour in `ParTD.setParamDefaults`** [C4P l.1710-1716]; horizon is 1 when lambda=0.
- Eligibility modes implemented: `STANDARD` (do nothing on random move) and `RESET` (clear the list); REPLACE/RES+REP are commented out "may later contain" [N2 `NTupleAgt.EligType`, l.2482]. Cost is O(h) per update, independent of episode length.
- Caveat: the horizon list stores past `sigma'` values but re-indexes with the *current* LUTs (indices are computed from stored board vectors) - this is the standard way to approximate traces and not exactly equal to them.
- **The best-so-far GBG Connect-4 agents use lambda = 0** (no traces) [G22 App. G.2; FA21 0.A.6]; lambda=0.5 is used for Othello. The sources never explain why lambda=0 was kept for Connect-4 despite T14's finding that traces halve the training time (open question, section 7).

### 2.4 Temporal coherence learning (TCL) and TCL-EXP (per-weight step size)

[B14 2.5, 4.2, 4.4; T15 3.5.1, Alg. 2; N2 `NTuple2.updateNew`/`setTcFactor` l.362-415]
- Each weight `i` has two counters `N_i` (sum of recommended weight changes) and `A_i` (sum of absolute RWC), initialised equal to a small constant (`TC-Init`, 1e-4 in all GBG settings, so the initial ratio is 1).
- Per update: `r_i = delta_t * e_i` (**[r] accumulation**; "TCL[delta]" accumulates `delta_t` only); `alpha_i = g(|N_i|/A_i)`; **`w_i += alpha * alpha_i * r_i`** (weights updated *first*, with the previous counters); then `A_i += |r_i|; N_i += r_i`. [B14 Alg. 3; T15 Alg. 2; N2 l.368-377]
- Transfer function `g`: original TCL `g(x) = x`; **TCL-EXP: `g(x) = exp(beta*(x-1))`, beta = 2.7** (optimum beta in [2,3], scanned 1..7). So the multiplier lies in [e^-2.7 = 0.067, 1]. [B14 4.4 Eq. 4.1-4.3; T15 3.5.1 Eq. 3.37]
- The key (and surprising) findings:
  - Original TCL did **not** speed up learning; TCL[r] was the *worst* of all step-size methods (worse than hand-tuned TDL), requires a constant (not decaying) global alpha (0.02-0.05), and has the same sensitivity to alpha as TDL (just shifted); breaks down for alpha_init > 0.1. [B14 4.2.1-4.2.5, 5.1; T15 5.1.2]
  - Update episodes (accumulate RWC over L moves) hurt (already L=3); swapping update order (counters first) hurt. [B14 4.2.6-4.2.7]
  - **Geometric (exponential) step-size change is what matters**: a piecewise-linear transfer function with the same endpoints and slope was clearly worse than TCL-EXP. [B14 4.4; T15 5.1.2]
- GBG scales alpha by `1/(m*N_S)` (section 1.6) and `tcFactor` multiplies this; the TCL accumulators `N`,`A` are updated even for index-inhibited duplicates (call is before the `indexList.contains` test) [N2 l.371-384].
- Memory cost: 3 extra doubles per weight (`tcN`, `tcA`, `tcFactorArray`), transient (not saved) [N2 l.82-84].

### 2.5 IDBD, nl-IDBD, nl-IDBD(lambda), SMD(lambda), Autostep, K1, ELK1, alpha-bounds, RProp

(only the linear/tanh variants that were *run* on Connect-4; all with per-weight step sizes `alpha_i = exp(beta_i)`.)
- **IDBD (Sutton 1992)**, linear unit: `beta_i += theta*delta*x_i*h_i; alpha_i = exp(beta_i); w_i += alpha_i*delta*x_i; h_i = h_i*[1 - alpha_i x_i^2]^+ + alpha_i*delta*x_i`. Memory 3n. Needs no global alpha, but needs `beta_init` (best -5.8, i.e. alpha_i = 0.003) and `theta` (0.1 in the text of B14 4.3; Table B.3 prints 0.01 - inconsistent, see section 8). Good beta_init range [-7.5,-5.5]; breaks down for beta_init > -4.6. [B14 2.6, 4.3; T15 3.5.2 Alg. 3]
- **nl-IDBD (Koop)**: tanh/logistic output + cross-entropy loss: `h_i <- [1 - alpha_i*V(1-V)*x_i^2]^+ h_i + alpha_i*delta*x_i` (logistic sigmoid). **nl-IDBD(lambda)** (derived by Thill): replace `x_i` by the trace `e_i`: `beta_i += theta*delta*e_i*h_i; alpha_i = exp(beta_i); w_i += alpha_i*delta*e_i; h_i = [1 - alpha_i*V(1-V)*x_i*e_i]^+ h_i + alpha_i*delta*e_i; e_i = lambda*gamma*e_i + x_i` (A.3). [T15 3.5.2 Eq. 3.45-3.46, Alg. 4]
- **SMD(lambda)** (Schraudolph, cross-entropy, logistic output): `alpha_i = alpha_i*max(1/2, 1 + theta*delta*e_i*h_i); w_i += alpha_i*delta*e_i; h_i = mu*h_i - mu*alpha_i*V(1-V)*e_i*(sum_j h_j x_j) + alpha_i*delta*e_i; e_i = lambda*gamma*e_i + x_i` (exact Hessian term, sum over active inputs once per step). Memory 4n. [T15 A.4 Eq. A.48-A.49, Alg. 5]
- **Autostep** (Mahmood et al.): described in T15 only as "much less dependence on the meta step size", memory 4n, parameters alpha_init, mu, tau, linear only. Its update equations are **not** in the sources; from my memory of Mahmood 2012 (**[INF], verify before use**): normalise the IDBD meta-gradient per weight by a running max `v_i`, then rescale `alpha` so `sum_i alpha_i x_i^2 <= 1`. Empirically: insensitive to mu from 1e-2 down to 1e-7 [T15 5.1.3, Fig. 5.4], robust to alpha_init on Dots-and-Boxes [T15 5.2.1], but only 490 000 games to 80 % on Connect-4.
- **K1/ELK1/alpha-bounds**: K1 (NLMS-based IDBD) and ELK1 (nonlinear K1) reach 80 % in ~390-490 k games; alpha-bounds (scalar step size) 500-560 k, and its original linear form fails (6 of 20 runs never pass 80 %, median 1.9 M) [T15 5.1.3]. 
- **RProp** (all four variants) failed completely on the full game; learning rates fell to the lower bound 1e-5 because the per-sample TD error signs are too noisy [T15 5.1.3, "Final comparison"].
- Overall verdict [T15 6.1]: "none of the examined algorithms can free the user from tuning"; geometric step size + nonlinear output are the helpful combination; tuning-free is **not** achieved.

### 2.6 Q-learning variant (action-value n-tuples)

One LUT per action (7 per tuple); update only the LUT of the chosen action; trained with the eps-greedy exploratory moves too. Result: ~**2 M games to 80 %** versus ~0.6 M for state-value TD, i.e. **6-7x slower**, and *worse with larger eps* (best eps = 0.1 constant, 0.05 slightly faster initially) [B14 4.5, Fig. 4.17]. In GBG's afterstate form it is also bigger (factor N_a) and cannot exploit afterstate sharing [FA21 2.6].

### 2.7 What worked best, and by how much (Connect-4, median games to reach 80 % score vs perfect player unless stated)

| step | before -> after | factor | source |
|---|---|---|---|
| two LUT sets (per player) | not learning the full game -> learns (3.7-4 M games to 80 %) | enabling | B14 4.1 |
| tune eps: sigmoid 0.6 -> 0.1 changed to constant 0.1 (and alpha tuned) | 1 565 000 -> ~560-670 000 | **~2.3-2.8x** | T15 5.1.1, 6.1; B14 4.2.4 |
| TCL-EXP (beta=2.7), no traces | 560 000 (tuned TDL) -> 385 000 (some text variants: 430 000/500 000) | ~1.5x | T15 5.1.2; T14 Tab. I; B14 4.4 |
| SMD (fastest step-size method) | -> 320 000; nl-IDBD 345-350 k | ~1.7x vs tuned TDL | T15 5.1.3, Tab. 5.1 |
| eligibility traces | e.g. TCL-EXP 385 k -> 230 k `[et]`; -> 175 k `[rr]` | **1.5-2x** | T14 Tab. I; T15 Tab. 5.2 |
| bigger system 150x8 | 175 k -> 115 k (`[rr]`), 230 k -> 145 k (`[et]`) | ~1.5x | T14 Tab. I |
| all of the above vs original | 1 565 000 -> 115 000 | **13x** | T14 IV; T15 5.1.4 |
| FARL in the GBG scheme | collapse (3.5 %) -> 49.5 % (of max 50 %) | enabling | FA21 Tab. 1 |
| MCTS wrapper at test time | first-player wins vs perfect AB-DL 91 % -> 99 % | +8 points | SK22 Tab. II |

---

## 3. Hyperparameters and training budget

### 3.1 Definitions common to the papers

- Self-play only; env gives only the final +1/0/-1. Game starts from the empty board (GBG: `ChooseStart-01=false` for ConnectFour [G22 App. G intro]).
- gamma = 1 throughout. Exploration is eps-greedy: with prob. eps pick a **uniformly random legal move**; otherwise the greedy afterstate, ties broken randomly [N2 `getNextAction2` l.2828-2838].
- Evaluation protocol (all of T14/B14/T15): tournament of 200 matches (B14: 50) against a perfect-play Minimax agent **which always moves second** (as first player the agent would otherwise lose every game); Minimax randomises its first 12 plies among moves that delay its defeat by >= 12 extra plies, then plays the longest loss; score win 1 / draw 0.5 / loss 0. "Asymptotic success rate" = mean score over the last 200 k-500 k training games; "time-to-learn" = smoothed score crosses 80 % (T14 captions: *last* crossing; 90 %: *first* crossing; T15 4.2.1 text says first crossing for both). [T14 III-B; B14 3.3; T15 4.2.1]

### 3.2 Table: settings used

| setting | alpha | alpha decay | eps | lambda / traces | n-tuples | LUT sets | extras | games | source |
|---|---|---|---|---|---|---|---|---|---|
| "Former TDL" (PPSN 2012) | 0.004 -> 0.002 (exp. decay) | exponential | **sigmoid-in-time 0.6 -> 0.1**, inflection at 1.0 M | 0 | 70x8 | 2 | tanh, init +-0.0005 | 5-10 M runs | B14 App. B.1 `TD2`; T15 5.1.1 |
| "Tuned TDL" (std TDL) | 0.004 -> 0.002 | exponential | **constant 0.1** | 0 | 70x8 | 2 | tanh | 2 M | T14 Tab. I (TDL-STD); T15 4.2 |
| TCL[r]/TCL[delta] | 0.04 constant | none | 0.2 -> 0.1 (ip 0.3 M) | 0 | 70x8 | 2 | TC init (see B14) | 5-10 M | B14 B.2 `TC8,TC9` |
| **TCL-EXP** | **0.05 constant** | none | **0.1 const** | 0 | 70x8 | 2 | beta=2.7, [r] | 2 M | T14 Tab. I; T15 4.2 |
| TCL-EXP `[et]` | 0.05 | none | 0.1 | **0.5** | 70x8 | 2 | traces in TreeMap | 2 M | T14 Tab. I |
| TCL-EXP `[res]`/`[rep]` | 0.05 | none | 0.1 | 0.6 | 70x8 | 2 | | 2 M | T14 Tab. I |
| TCL-EXP `[rr]` | 0.05 | none | 0.1 | **0.8** | 70x8 | 2 | | 2 M | T14 Tab. I |
| **TCL-M `[et]`/`[rr]`** | **0.02** | none | 0.1 | 0.5 / 0.8 | **150x8** | 2 | | 2 M | T14 Tab. I |
| TDL `[et]`/`[rr]` | 0.004 -> 0.002 | exp. | 0.1 | 0.5 / 0.8 | 70x8 | 2 | | 2 M | T14 Tab. I |
| IDBD | beta_init=-5.8 (alpha_i=0.003), theta=0.1 (or 0.01, inconsistent) | adaptive | 0.2 -> 0.1 (ip 0.3 M) | 0 | 70x8 | 2 | **no sigmoid** (linear) | 2-5 M | B14 4.3, B.3 |
| GBG TD-FARL + TCL-EXP (best) | **alpha = 3.7** (scaled by 1/(m N_S) = 1/140, effective ~0.026 before tcFactor); ParTD default 5.0 | const. (alpha_final = alpha) | **linear 0.1 -> 0.0** | **0** | 70 fixed 8-tuples | 2 | beta=2.7, TC-Init=1e-4, [r] accumulation, symmetry, LearnFromRM active (G22; not listed in FA21), tanh | **6 M** (FA21: 5 M, eval every 100 k) | G22 App. G.2; SK22 III-B; FA21 0.A.6; C4P `ParTD.setParamDefaults` |
| GBG Othello (for comparison) | 0.2 | | 0.2 -> 0.1 | 0.5, horizon cut 0.1 | **100 random-walk 7-tuples** | 2 | TCL-EXP, ChooseStart-01, LearnFromRM | 250 k | G22 G.1 |
| Q-learning n-tuple | 0.004 -> 0.002 | exp | 0.05-0.5 const, best 0.1 | 0 | 70x8 x 7 actions | | | ~2 M to 80 % | B14 4.5 |

Note the two alpha conventions: T14/B14 use a raw per-weight alpha (0.05 for 70 tuples, 0.02 for 150: roughly 1/m scaling, 0.05*70 = 3.5, 0.02*150 = 3.0); GBG divides by `m*N_S` internally and uses alpha ~ 3.7-5. Both amount to an effective per-weight step of ~0.02-0.05 multiplied by `(1 - V^2)` and the TCL factor.

### 3.3 Budget

| run | games | hardware | wall/CPU time | source |
|---|---|---|---|---|
| TDL-STD to 80 % | 565 000 | one core of an Intel Core i7-3770 3.4 GHz, 8 GB | 68.0 min (includes the periodic minimax evaluations) | T14 Tab. I |
| TCL-EXP `[res]` to 80 % | 220 000 | same | 28.5 min | T14 Tab. I |
| TCL `[rr]` to 80 % | 175 000 | same | 26.1 min | T14 Tab. I |
| TCL-M `[rr]` (150x8) to 80 % | 115 000 | same | 25.5 min | T14 Tab. I |
| step-size survey (Tab. 5.1) | 320-890 k per run | one core of an i7-3632QM 2.2 GHz | 27.7-67.2 min; 102 min for the 1.565 M "Former TDL" | T15 Tab. 5.1 |
| 2 M-game run | 2 M | | not given; eval included | T14/T15 |
| GBG best agent | 6 M | single core of an Intel i7-9850H 2.6 GHz | "1.4 d for n_ag = 10 agents" = **~3.4 h per agent** (derived: 1.4 d x 24 h / 10) = ~17 000 moves/s if ~35 plies/game; GBG states 10 000-90 000 moves/s for TD agents on one core | SK22 Tab. III and fn. 6; G22 1.3 |
| trained agent on disk | | | `*.agt.zip` ~6 MB for the C4 agent (9.2 M weights, ~8 % non-zero); 2048 agent 93 MB | W Code-Guide |
| per-move play time | | | TCL-base ConnectFour 0.05 +- 0.02 ms; MCTS wrapper 100 iterations 3 ms, 1000 it 30 ms, 10 000 it 300 ms | SK22 Tab. IV |

The time unit that mattered was evaluation: the minimax benchmark every 10 000 games dominated run time in Dots-and-Boxes (500 k games = 4-5 days, down to 15 h after fixing evaluation) [T15 5.2.2].

---

## 4. Results

### 4.1 Against the perfect-play agent (Connect-4, 6x7)

| system | score vs perfect minimax (agent first), asymptotic (mean over last 0.5 M of 2 M games) | time to 80 % / 90 % (median of 20 runs) | source |
|---|---|---|---|
| Former TDL (PPSN 2012) | ~90 % | 1 565 000 / - | T14 I; T15 5.1.1 |
| tuned TDL | 90.6 % | 565 000 / 925 000 | T15 Tab. 5.2 (reconstructed) |
| TDL `[et]` | 92.3 % | 370 000 / 640 000 | " |
| TDL `[rr]` | 90.6 % | 305 000 / 710 000 | " |
| TCL-EXP | 92.4 % | 385 000 / 820 000 | " |
| TCL-EXP `[et]` | 93.3 % | 230 000 / 450 000 | " |
| TCL-EXP `[res]` | 92.3 % | 220 000 / 435 000 | " |
| TCL-EXP `[rep]` | 92.1 % | 215 000 / 405 000 | " |
| TCL-EXP `[rr]` | 90.7 % | 175 000 / 410 000 | " |
| TCL-M (150x8) | 93.2 % | 250 000 / 510 000 | " |
| **TCL-M `[et]`** | **94.4 % (best)** | 145 000 / 275 000 | " |
| **TCL-M `[rr]`** | 93.1 % | **115 000** / 265 000 | " |
| nl-IDBD / `[et]` / `[rr]` | 92.8 / 93.0 / 93.1 % | 350 k / 220 k / 210 k (80 %) | " |
| SMD / `[et]` / `[rr]` | 92.4 / 92.3 / 91.4 % | 320 k / 220 k / 195 k (80 %) | " |

Notes: this table is reconstructed from the scrambled Tab. 5.2 in T15 by row order and cross-checked against T14 Tab. I (the time columns match exactly for the rows both give) and T15 5.1.4 text ("TCL-M[et] 94.4 %, the highest"). The 80 %/90 % pairs and ASR columns are internally consistent but were not machine-aligned; treat ASR digits to +-0.1.
- Lost-game rate of the best systems: **7.0 % (TCL[et]) -> 5.5 % (TCL-M[et])** [T14 III-C]. All of these figures are with the agent as Yellow (first). The agent as Red against the perfect player was never measured (cannot win) [B14 3.3].
- Step-size algorithms w/o traces, asymptote after 2 M / 10 M games: ~89.4-92.8 % / 90.4-93.5 % (T15 Tab. 5.1, garbled; plain TDL "approaches the same strength after 10 M games" [T15 6.1]).
- GBG TD-FARL (no traces, TCL-EXP, 6 M episodes): win rate over both roles 49.5 % vs AB and 46.5 % vs AB-DL (max 50 %) [FA21 Tab. 1], i.e. about 93 % wins as first player. In the SK22 tournament (100 games each pairing, AB-DL = perfect agent that prolongs lost games): TCL-base as first player vs AB-DL **91 W / 2 T / 7 L**; as second player 0 wins (theoretically lost); overall won-games rate 49.0 %. [SK22 Tab. II]
- With MCTS wrapper (1000 iterations, c_puct = 1): first player vs AB-DL **99/0/1**; as second player it still never beats AB-DL (0/0/100, theoretically lost) but wins 97 % of its games as second player against the *imperfect* plain-MCTS opponent (the 66.3 % vs 64.9 % overall ranking over AB-DL comes from that). Even 50-100 iterations give > 80 % (first-player win rate vs AB-DL), >= 500 iterations ~100 % [SK22 IV-B, Tab. II, Fig. 6]. Cost: 30 ms/move at 1000 iterations.
- Comparison reported: ConnectZero (CNN, AlphaZero-style) starting (theoretical win) lost 80 % of 10 episodes to AB playing second and 100 % to TCL-wrap playing second [SK22 IV-B; FA21 3.4 reports AB winning all].

### 4.2 Learning curves (qualitative shape)

Success rate vs games (per 10 000-game evaluation, 20 runs, smoothed fits): ~0 for the first 50-100 k games, rapid rise to ~80 % within 0.1-0.5 M, then a slow tail 80 % -> 90 % taking another ~0.2-0.5 M games and the asymptote 90-94 % at 2 M [T14 Fig. 4-8; T15 Fig. 5.2/5.6/5.10]. TDL-STD has occasional outlier runs (one at 0.79 asymptotic, one that never reaches 80 % in 2 M). Run-to-run spread is non-trivial; hence 20 runs. No numerical series are in the extracted text (figures only).

### 4.3 Effect of each enhancement (summary)

See 2.7. Additional quantified statements:
- Larger exploration is bad: initial eps < 0.2 was better *even early*; final eps < 0.1 reduced the asymptote (hypothesised overtraining/forgetting) [B14 4.2.4]; eps = 0.1 const slightly beats 0.2 -> 0.1 [B14 4.4.1].
- TCL sensitivity: breakdown for global alpha_init > 0.1; TDL and TCL good-alpha ranges have similar width, just shifted [B14 4.2.3, 5.1].
- Traces: lambda in [0.4, 0.7] best; lambda >= 0.9 collapses [T14 Fig. 8; T15 Fig. 5.8].
- Dots-and-Boxes (T15 5.2): the same framework needed *intermediate rewards* and *board inversion*, needed n-tuples covering ~2/3 of the board (length 16 on 3x3 worked, 16 on 4x4 = 40 % coverage failed), i.e. n-tuples are not universal: negative evidence for games where long chains matter [T15 6.1 Q4].

---

## 5. GBG implementation details worth copying

### 5.1 File / class / method map

| role | class (path under `src/`) | notes |
|---|---|---|
| single tuple + LUT + TCL | `controllers/TD/ntuple2/NTuple2` (N2), `ntuple4/NTuple4` (N4) | fields: `int[] nTuple`, `double[] lut`, transient `tcN,tcA,tcFactorArray`, transient `LinkedList indexList`; methods `getIndex`, `getScore`, `updateNew(board, alphaM, delta, e)`, `setTcFactor`, `clearIndices` |
| set of tuples, per-player, update loop | `NTuple2ValueFunc` / `NTuple4ValueFunc`: `NTuple2[numOutputs][numPlayers][numTuples] nTuples`, `getScoreI`, `update(...)`, `updateWeightsTD`, `setHorizon`, `finishUpdateWeights` | `LinkedList<EligStates>[] eList` per player; `EligStates{BoardVector[] equiv; double sigDeriv}` |
| tuple generation | `NTupleFactory` / `NTuple4Factory.makeNTupleSet` (fixed / random walk / random points) | `generateRandomWalkNTuples`, `pickElement`, `checkNtuples` |
| game glue | `games/XNTupleFuncs` interface; `games/CFour/XNTupleFuncsC4` | methods: `getNumCells`, `getNumPositionValues`, `getPositionValuesVector` (NTuple4), `getNumPlayers`, `getNumSymmetries`, `getBoardVector`, `symmetryVectors`, `symmetryActions` (SARSA only), `fixedNTuples(mode)`, `adjacencySet` |
| agent / training loop | `TDNTuple3Agt` (N2) / `TDNTuple4Agt` (N4): `getNextAction2`, `getScore(so, refer)`, `getScoreTuple`, `trainAgent`, `adaptAgentV`, `finalAdaptAgents`, `collectReward` | `sLast[]`, `randLast[]`, `rLast` as transient state |
| next-state helper | `NextState` / `NextState4` | holds `refer` (s), `afterState` (s'), `nextSO` (s''), reward tuple; for deterministic games s'=s'' |
| base class with schedules | `NTupleBase` / `NTuple4Base`: `setTDParams` (alpha ratio, eps delta), `finishUpdateWeights`, `normalize2` | `m_epsilon` decreases linearly per episode |
| params | `params/ParTD` (alpha, alfin, epsil, epfin, gamma, lambda, horCut, eligMode, sigmoid), `params/ParNT` (tc, tcInit, tcTransfer, tcAccumul, tcBeta, tuple number/length, randomness, randomWalk, fixedNtupleMode, useSymmetry, nSym, afterState) | `ParTD.setParamDefaults` for ConnectFour: alpha=alfin=5.0, horCut=0.01, eps_final=0, lambda=0, gamma=1, sigmoid=true [C4P l.1710-1740]; `ParNT.setParamDefaults("ConnectFour")`: `setTc(true)`, `setRandomness(false)` (= fixed 70 tuples), useSymmetry=true [C4P l.1268-1271]. Defaults for TC: TC-EXP, beta=2.7, [r] accumulation, TC-Init 1e-4 (the class constant `DEFAULT_TC_INIT=0.001` is overridden to 0.0001 in `setParamDefaults`) |
| game state | `games/CFour/StateObserverC4` | note: wins are detected only when reached via `advance` ("Wins are only detected if they are reached via advance", l.562-570); bitboard-free; board `int[7][6]`; `getGameScore` from perspective of player to move |
| reference opponents | `AlphaBetaAgent` (AB, AB-DL = distant losses) for Connect-4 [G22 Tab. 1; Thill 2015 code], plus 12-ply Huffman DB and Tromp's 8-ply DB [T15 4.1.1] | only used for evaluation, never in training |
| wrappers | `MaxN2Wrapper` (n-ply tree search with the wrapped agent's `getScore` at leaves), `MCTSWrapperAgent` (PUCT) [G22 3.5.2] | see 5.5 |

### 5.2 Training loop in condensed form (my reconstruction of N2/N4 `trainAgent`, `adaptAgentV`, `finalAdaptAgents`, `update`) **[INF as to naming, logic from code]**

```java
// V_p(s') : value for player p of afterstate s'   (p = player who just moved)
double V(int p, int[] cells4state) {
  double nu = 0;
  for (int t = 0; t < m; t++) {
    nu += lut[p][t][ idx(t, cells4state) ];
    nu += lut[p][t][ idx(t, mirror(cells4state)) ];     // N_S = 2, not averaged
  }
  return Math.tanh(nu);
}

void trainEpisode(State s0) {
  State s = s0; State[] sLast = {null, null}; double[] rLast = {0,0}; boolean[] randLast = {false,false};
  while (true) {
    int p = s.player();
    boolean rnd = rng.nextDouble() < eps;
    Move a = rnd ? randomLegal(s) : argmax_a ( r_delta(s,a) + gamma * (after(s,a).over ? 0 : V(p, after(s,a))) );  // ties random
    State sa = after(s, a);
    double[] R = rewardTuple(sa);                        // +1 winner, -1 loser, 0 otherwise
    randLast[p] = rnd;                                   // BEFORE adapting (bug fix 2019)
    if (sLast[p] != null && !(rnd && !learnFromRM)) {
        double vNext  = sa.over ? 0.0 : V(p, sa);
        double target = (R[p] - rLast[p]) + gamma * vNext;
        update(sLast[p], p, target);                     // delta = target - V(sLast), e = 1 - V^2
    } else if (sLast[p] != null && eligType == RESET) eList[p].clear();
    sLast[p] = sa; rLast[p] = R[p];
    if (sa.over) {                                       // final adaptation
        for (int n = 0; n < 2; n++) {
            if (n != p) { if (sLast[n] != null) update(sLast[n], n, R[n] - rLast[n]); }
            else        { update(sa, p, 0.0); }          // skip if length-capped
        }
        break;
    }
    s = sa;
  }
  alpha *= alphaRatio;  eps -= (eps0 - epsFinal) / maxGames;
}

void update(State sOld, int p, double target) {
  double v = V(p, sOld), delta = target - v, e = 1 - v*v;           // tanh derivative
  eList[p].addFirst(new Elig(sOld, e)); if (eList[p].size() > horizon) eList[p].removeLast();
  double lam = 1.0;
  for (Elig el : eList[p]) {
    for (int t = 0; t < m; t++) {
      Set<Integer> done = new HashSet<>();                          // inhibition per (state, tuple)
      for (int[] q : new int[][]{ el.cells, el.mirror }) {
        int i = idx(t, q);
        double tc  = Math.exp(beta * (Math.abs(N[p][t][i]) / A[p][t][i] - 1.0));
        double rW  = delta * el.e * lam;                            // recommended weight change
        double dW  = alpha / (m * 2) * rW * tc;
        N[p][t][i] += rW;  A[p][t][i] += Math.abs(rW);              // N,A init = 1e-4
        if (done.add(i)) lut[p][t][i] += dW;
      }
    }
    lam *= lambda;
  }
}
```
This is exactly the logic in N2 `NTuple2.updateNew` + `NTuple2ValueFunc.update` + `TDNTuple3Agt`, minus bookkeeping.

### 5.3 Afterstate handling

- Why afterstates: "For positional games (TicTacToe, ConnectFour, Hex) the value of taking action a in state s depends only on the resulting afterstate s'. Several (s,a) pairs may lead to the same afterstate"; learning V(afterstate) rather than Q(s,a) reduces complexity [FA21 2.2, 2.6; K15 Sec. 2]. `NextState(parent, so, actBest)`: with `AFTERSTATE=true` it calls `advanceDeterministic` then `advanceNondeterministic`; for deterministic games (`getAFTERSTATE()==false` default) one `advance` and `afterState == nextSO` [N2 l.2334-2354].
- Move selection scans all legal actions, applies each to a copy of the state and compares `rtilde + gamma*V(s')` where `rtilde` is the delta reward [N2 `getNextAction2` l.2756-2832]. Value of a game-over afterstate comes from the reward (win = +1 ranks above any V < 1), and `getScore` returns 0 for it.

### 5.4 Value by player and 2-player conversion

For 2 players `getScoreTuple(so)` returns `sc[creator] = V_creator(so)`, `sc[toMove] = -sc[creator]` [N2 l.2966-3004]: the net is only trained from the creator's perspective, so a position must be evaluated with the **creator's LUT** and negated for the other side. (A bug fixed 2019-03-10: asking the LUT of the player to move returned untrained values.) This is exactly what an alpha-beta leaf evaluator needs.

### 5.5 Wrappers at test time (the "search on top" precedent)

- **MCTS wrapper** [SK22 II-B, Alg. 1; G22 3.5.2, App. G]: UCT/PUCT tree where the wrapped agent supplies (i) leaf value `V` = the TD n-tuple value of the node state and (ii) priors `P(s,a) = softmax_a( V(s') )` over children; selection `argmax W/N + c_puct * P * sqrt(eps + sum N)/(1+N)`; action = most visited child; **tree reuse** between moves (`UseLastMCTS`). Settings used: c_puct = 1.0, d_max = -1 (unlimited), `UseSoftMax = true`, `UseLastMCTS = true` (SK22: 1000 iterations for ConnectFour, 10 000 for Othello). No rollouts. Not used in training.
- **MaxN2 / Expectimax wrapper**: n-ply Max-N tree (alpha-beta not stated; "depths > 2-3 with caution"), the inner agent's value at the leaf. [G22 3.5.2, App. B] Not benchmarked in these sources. C4GPF's "RL-Minimax agent" is the same idea [T15 App. B]. 
- MCTS *inside* training (Othello experiment): not worth it - under equal CPU budget (2 h) or at 12 000 episodes it is much worse; with equal episodes (250 000) +small gain for 30x compute [SK22 IV-E].

### 5.6 Other infrastructure worth copying

- **Evaluator** abstraction with several modes; must not influence training [G22 3.10, 8.10]. Perfect reference player (alpha-beta + opening books) used only for measuring [G22 1.3].
- Parallelism: read-only evaluation (`getNextAction2`) is thread-safe, training is not [G22 App. D]. Experiments in T15 ran 20 independent seeds per setting, multi-threaded via XML parameter files and CSV output [T15 4.1].
- Persistence: serialise LUTs (zeros compress away: C4 agent ~6 MB zipped); TCL arrays are transient and not saved [N2 l.82-84; W].
- `-Xmx12096M` recommended for big nets [W Install].
- Evaluator in training uses move-by-move comparison against a perfect agent on a 200-game tournament; cost-dominating.

---

## 6. Adaptation to Quantum Connect (all **[INF]** unless cited)

### 6.1 Direct carry-over (low risk)

1. Value function = `tanh(sum of LUT weights)` over random-walk (8-neighbour) tuples of cells with 4-valued cell code {empty-unreachable, mine, theirs, empty-reachable}; two LUT sets (creator side), left-right mirror symmetry (the board is 8 rows x 12 cols, the 3x3 destroy move and gravity are mirror-symmetric, and six-in-a-row lines mirror onto lines; I assume the game has no left/right asymmetric rule). 
2. Self-play TD on **afterstates** with the creator's LUT; TD target one round ahead (own previous afterstate); FARL final adaptation (loser/loser's previous afterstate toward -1, winner's terminal afterstate toward 0); eps-greedy eps 0.1 -> 0.0 linear; constant alpha with TCL-EXP (beta 2.7) or a hand-tuned decaying alpha; lambda = 0 initially, then try the Jaskowski horizon with lambda 0.5-0.7.
3. Because the source line trains and evaluates the *1-ply greedy* policy, the learned `V` is "value if both sides keep playing the 1-ply greedy policy (with eps noise)". For a leaf evaluator inside alpha-beta that is a defensible, slightly optimistic-for-shallow-play estimate; mixed experience from the MCTS-at-test-time result suggests that search on top of such a V adds a lot (91 % -> 99 % first-player wins) [SK22].

### 6.2 What changes for 8x12, six in a row

- Cells: 96. Lines: horizontal 8x7 = 56, vertical 12x3 = 36, two diagonal directions 3x7 = 21 each = 42; total **134 length-6 windows** (Connect-4: 69).
- **Tuple length**: a straight 6-window needs n >= 6 to be a complete pattern, so tuples of length 6-8 can express a full line plus context. With only random-walk blobs, few tuples align with a full line; **[INF] add systematic line tuples** (all 134 windows, or all 6-windows extended by 1-2 neighbour cells) next to random walks, and use the tuple count scaled with area: Connect-4 had 70 tuples for 42 cells (1.67 per cell); same ratio gives ~160 tuples for 96 cells. T14's 150x8 for 42 cells (3.6 per cell) was 1.5x faster/stronger; ~200-350 tuples is a plausible upper range for 96 cells.
- Expect the proportion of realisable states to stay near 5-10 % for n=8 (columns must be gravity-consistent; same rule after the destroy move since gravity is re-applied) [INF; same mechanism as T14 fn. 4].
- Episode length: up to 96 plies (more with destroy moves), roughly 2.3x Connect-4's 42. Credit assignment is harder; the horizon cut/lambda matter more. T14 found lambda in [0.4,0.7] best and collapse at 0.9 for 42 plies; expect an equal or lower optimum.
- Self-play cost: ~2.3x plies per game x larger tuple set; start budget estimate (**[INF], very rough**): 1.5-3x more games than Connect-4 for the same relative strength => 1-10 M games; at ~3.4 CPU-h per 6 M (Connect-4, GBG) that is a few CPU-hours per 100-tuple run in a lean implementation; trivial on a PC. GPU not needed for this method (CPU-bound, sparse updates; per-update touches <= 2m cells).

### 6.3 Special move, cooldown state, hash of the position

- The "destroy" move (disc placed, 3x3 block cleared including both colours, gravity re-applied) leaves a normal gravity-consistent board, so the same 4-valued encoding applies to the afterstate. The afterstate depends on (board, side that moved, **cooldown counters of both players**) - the cooldown state *must* be part of the value function inputs because it changes who can threaten what. The sources have no analogue. Options **[INF]**:
  (a) extra bias LUT(s) indexed by (own cooldown bucket 0..10, opp cooldown bucket 0..10) per creator: 121 entries x 2, cheap, probably necessary;
  (b) extend some tuples with 1-2 extra pseudo-cells holding the cooldown buckets (multiplies those tuples by 4-11x);
  (c) separate LUT sets per (self-ready, opp-ready) boolean pair (4x memory).
  Start with (a); try (b) for tuples sitting near columns where the 3x3 clear matters. 
- Bucket idea: 0 = ready, 1-3 = soon ready, 4-10 = far; the availability within the next 1-2 own turns is the tactically relevant signal.
- **Exploration**: eps-random picks a uniformly random *legal action*; if special moves are ~12 extra actions, random exploration would use them ~50 % of exploratory moves, far more than good play; **[INF]** sample the exploratory move type with a prior (e.g., 80 % normal drops) or keep eps small (0.05-0.1) and rely on greedy selection so special moves are tried when V suggests it.
- `learnFromRM`: decide per source: C4 GBG agent uses it [G22 G.2]; T14 skips updates after random moves. With cooldown, a random wasted special move changes the future; the safer default is T14's rule (skip).
- Win detection: GBG notes win detection only works for states reached via `advance` [C4P]. Our rule "any 6-line present after a move is a win for the mover" including after a destroy move: implement as "check whole board after each move", not just lines through the landing cell.
- Reward: terminal +1/0/-1 as in all sources; step reward 0. If special moves create long forcing sequences, longer episodes will need the length cap + `epiLengthStop` logic (no final-adapt-to-zero on a capped episode) [N2 l.3219].

### 6.4 Using the learned V as alpha-beta leaf evaluation

- Return `V_creator(leaf) ` negated if the side to move differs: leaf value for side-to-move `= -tanh(nu_creator)`; keep `nu` (pre-tanh sum) as a float and scale to an int centipawn-like scale for TT/aspiration (tanh is monotone, search ordering only needs the order; mate scores outside range). **Never** let the learned V decide forced tactics: do exact win/loss/forced-block detection (threat search, the planned part) first; V only for quiet leaves. n-tuples trained by 1-ply self-play with eps=0.1 do not see 3-4 ply tactics reliably (the 7 % lost games vs a perfect player show it).
- **Incremental evaluation**: each ordinary move changes two cells (landing cell and the newly reachable cell above); a destroy move changes up to 9 cells plus the columns that shift down (gravity) - recompute all affected tuple indices (or all) for those. Keep per tuple `idx_orig[t]`, `idx_mirror[t]`, precomputed lists `tuplesOfCell[c]` with the cell's weight `P^j` inside the tuple; update `idx += (newState-oldState)*P^j`. The evaluation is then `sum(lut[t][idx[t]] + lut[t][idxM[t]])` ~ 2m table reads.
- Throughput guess: GBG's whole (unoptimised, object-heavy) evaluation of 7 children = 0.05 ms [SK22 Tab. IV], i.e. ~7 us per child with m = 70 (140 lookups). A lean int-array implementation with incremental indices should be 1-3 us per leaf for m ~ 150-250 (300-500 lookups, LUTs mostly in L2/L3) => 300 k - 1 M evaluations per second per core => 30 k - 100 k per 100 ms move. That bounds the usable depth for the evaluation-heavy part of the search; the exact-threat part runs on bitboards. (**[INF]**, measure.)
- Test-time MCTS wrapper (SK22) is the in-sources alternative to alpha-beta; with 100 ms and ~5-10 us per expansion one would get ~10 k simulations; the paper shows >80 % at 50-100 iterations and ~100 % at >= 500 for Connect-4.
- Training with search in the loop (TDLeaf style, search values as targets) is **not** part of this line of work: Scheiermann and Konen find MCTS-in-training barely pays off [SK22 IV-E]. The folder holds `baxter-tdleaf-lambda.md` and `pdf-p2-baxter1999-...` for that route; not digested here.

### 6.5 Table sizes (4-state cells; "weights" = LUT entries; two LUT sets = x2)

Entries per tuple per player: n=6: 4 096; n=7: 16 384; n=8: 65 536; n=9: 262 144; n=10: 1 048 576.

| tuples m (2 LUT sets) | n=6 | n=7 | n=8 | n=9 | n=10 |
|---|---|---|---|---|---|
| m=100 weights | 0.82 M | 3.28 M | 13.1 M | 52.4 M | 210 M |
| float32 bytes | 3.3 MB | 13.1 MB | 52 MB | 210 MB | 840 MB |
| int16 bytes (fixed point) | 1.6 MB | 6.6 MB | 26 MB | 105 MB | 420 MB |
| m=200, n=8: | | | 26.2 M weights; 105 MB (f32), 52 MB (int16) | | |

Check values: 100 7-tuples 2 players = 3 276 800 [SK22 Tab. I]; 70 8-tuples 2 players = 9 175 040 [T14, SK22].
- Training-time memory in GBG style: `lut` (double) + `tcN` + `tcA` + `tcFactorArray` = 4 doubles = 32 bytes/weight: m=100, n=8 -> 420 MB dense; fine for a PC with a 12 GB heap [W]. m=200, n=9 would need 6.7 GB dense; then use a sparse/hash LUT (only 5-10 % entries become non-zero) or float32 + drop `tcFactorArray` (recompute from N,A).
- Inference size: store only trained values; GBG's zero-initialised design plus deflate means a jar-embedded `int16[]` compresses to roughly the non-zero fraction (C4 agent 9.2 M weights zip = 6 MB even in Java-serialised double form [W]). For the jar: quantise per tuple-LUT to int16 with a per-LUT scale (the sum is then done in int, tanh via a lookup table or `Math.tanh` once per leaf) and deflate. **[INF]** Verify the jar size limit you have; if tight, n=7 with more tuples is far cheaper than n=8 and loses less than halving the tuple count (T15: longer tuples matter for Dots-and-Boxes; for Connect-4 only 7 vs 8 was tested, 70x7 < 70x8).
- Cache behaviour: n=8 LUT per tuple/player = 65 536 entries x 2 B = 128 KB int16 (L2-sized); m=200 => 25 MB total hot set, spills L2 but most accesses hit a small realisable subset.

### 6.6 Suggested concrete first experiment **[INF]**

1. Board encoder with 4-valued cells + mirror; tuple set = 134 line-window tuples extended to length 8 by random-walk + 100 random-walk 8-tuples (8-neighbour).
2. TD-FARL loop as in 5.2, lambda=0, TCL-EXP beta=2.7, alpha(raw)=3.7, eps 0.1 -> 0.0, `learnFromRM=false`, cooldown-bucket bias LUT, 5 M games, evaluate by round-robin vs an exact-threat alpha-beta with a material-free heuristic and vs previous checkpoints (no perfect player exists).
3. Ablate: lambda 0.5 horizon; eps 0.05; tuple count; special-move exploration prior; mirror on/off.
4. Plug the weights into alpha-beta leaf eval; compare "V only" vs "alpha-beta d=N + V" at fixed 100 ms.

---

## 7. Open questions

1. **Which LUT / what perspective in the older ply-chain scheme** (T14/B14)? Text says two LUTs "one for each player" but does not state whether the LUT is chosen by player-to-move or by the player who moved, or whether V is Yellow-perspective with minimisation for Red. GBG's code resolves it for the newer scheme only (creator's LUT, own-perspective value, negate for the opponent).
2. **Why lambda = 0 and 6 M episodes in the final GBG Connect-4 recipe** when T14 reports halved training time with traces? Not explained. Possibly robustness/simplicity or a different bottleneck (alpha scaling 1/(mN_S), FARL). No sources compare TD-FARL with lambda>0 on Connect-4 (Othello uses 0.5).
3. **Scale of alpha**: T14 alpha=0.05 (raw) vs GBG alpha=3.7 with `1/(m N_S)` scaling and `(1-V^2)` factor. Equivalent order of magnitude but not an exact map; GBG also double-counts mirror contributions in the sum while updating each index once.
4. **P = 3 vs P = 4** has no published ablation in these sources; the claim "empty-reachable matters" rests on the PPSN 2012 paper (not provided).
5. **Tuple generation quality**: no study of 4- vs 8-neighbour walks, tuple count vs length trade-off beyond 70x7 vs 70x8 and 70 vs 150, no selection criterion. Dots-and-Boxes showed n-tuples can fail when coverage ~ 2/3 of the board is needed - unclear for 6-in-a-row on 8x12 where long lines span 6 of 12 columns.
6. **Does a TD-learned V trained by 1-ply self-play work as a leaf evaluator for deeper alpha-beta?** Only MCTS-at-test-time (SK22) and an untested "RL-Minimax"/`MaxN2Wrapper` exist; no data on evaluation consistency across depths.
7. **Agents as second player**: never measured against a perfect first player (always lost); asymptotic numbers are from the first-player side only.
8. **Nonstationarity from eps decay to 0**: B14 says final eps < 0.1 hurt asymptote ("overtraining/forgetting"), yet GBG's best agent uses 0.1 -> 0.0. Not reconciled.
9. **Cooldown and special moves**: no source has a game with limited-use moves; every design in 6.3 is untested.
10. **Sample efficiency numbers are on a 42-ply game with a perfect-play test bench**; our game has no solver, so progress metrics need a different protocol.

---

## 8. Reliability notes: scrambled extractions and source conflicts

- **T15 Tab. 5.1 and Tab. 5.2** (pdftotext columns interleaved). I re-aligned by row order and checked against text/other tables; rows/times are consistent (T14 Tab. I rows match to the digit), ASR digits slightly less certain. IDBD/nl-IDBD theta and Autostep/K1 meta-parameter entries in Tab. 5.1 ("3.0, 3.1, 6.0, 5.0", "1e-4") could not be mapped reliably; not reported above except Autostep insensitive in 1e-7..1e-2 [T15 5.1.3].
- **Tuned-TDL time to 80 %**: 670 000 (T15 5.1.1; B14 4.7) vs 560 000/565 000 (T15 Tab. 5.1/5.2, 5.1.2; T14 Tab. I). Different tuning/measurement; use ~560-670 k.
- **TCL-EXP time to 80 %**: 385 000 (T15), 430 000 (T14 Tab. I TCL-STD), ~500 000-550 000 (B14 4.4/4.7 text). Reported as 385 k.
- **IDBD theta**: 0.1 (B14 4.3 text) vs 0.01 (B14 Tab. B.3).
- **Neighbourhood for random walks**: 8 (T15 3.4.2) vs 4 (`XNTupleFuncsC4.adjacencySet`).
- **Replace condition** in T14/T15 Alg. 1 printed as `x_i(s_{t+1}) = 0` in the extraction; must be `!= 0` logically.
- **Eps default** in `ParTD` 0.3 (`DEFAULT_EPSIL`) vs 0.1 documented in `TDParams`; best agent uses 0.1 -> 0.0 [G22 App. G.2]. `ParTD.setParamDefaults` for TD-Ntuple-3/4 + ConnectFour doesn't set epsilon, so a freshly constructed agent would have the class default; the cited configurations are the authority.
- **Episodes**: 6 M (G22 App. G.2, SK22) vs 5 M (FA21 0.A.6) for the same alpha=3.7 agent.
- **LearnFromRM** for ConnectFour: active in G22 App. G.2, not mentioned in FA21 0.A.6.
- **Reward scale**: K15 uses 0/0.5/1; T14/T15/B14/GBG use -1/0/+1.
- `agents/theNtuple.txt` in `gbg-connect4-ntuple-params.md` is a printed random-walk set of **100 7-tuples with cell indices 0..39** - not a Connect-4 (42-cell) set; ignore.
- B14 Fig. 4.15 caption refers to "Fig. 5" in its own text; harmless.
- No numeric learning-curve data in text form; all curves are figures (not extractable).
