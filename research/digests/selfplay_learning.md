# Self-play value/policy learning — technical digest for Quantum Connect

Scope: AlphaZero family, Descent / Unbounded Minimax (Athénan), Approximate Value Iteration (AVI),
n-tuple TD learning (+ MCTS wrapper), TD(λ) / TDLeaf(λ). The question this digest answers: which learning
algorithm and which training target should we use to train an evaluator on one 8 GB GPU, when the
finished bot runs alpha-beta + threat search in pure Java (standard library only, weights embedded)
at 100 ms per move?

Our game, as stated in the brief: gravity drops on an 8-row × 12-column board (size-generic), six in a
row wins, each player may use a 3×3 "destroy" move at most once every 10 of their own turns (it clears
both colours and gravity is re-applied), and any 6-line on the board after a move is a win for the
player who moved.

Conventions
- Each claim is cited as `[KEY §section]`. The keys are listed in the source key below. Every file is in
  `sources/text/`.
- **[INFERENCE]** marks my own reasoning, extrapolation or estimate. No source states it. Everything not
  marked is attributed to a source.
- Extraction caveat: in several arXiv files the HTML-to-text step stripped the math inside algorithm
  boxes. Only the symbol stream was left. Where I reconstruct pseudo-code from that stream, I say so.
  Figures (learning curves) did not survive extraction either, so any number quoted here comes from text
  or tables.

## Source key

| Key | File | What it is |
|---|---|---|
| MSB | arxiv-2012.10700-minimax-strikes-back.md | Cohen-Solal & Cazenave, *Minimax Strikes Back* (AAMAS 2023): Athénan / Descent vs Polygames |
| AVI | arxiv-2609.09094-surprising-effectiveness-approximate-value.md | Boige, Boumaza, Scherrer 2026, *Surprising Effectiveness of AVI in Self-Play* |
| SPARSE | arxiv-2607.08984-alphazero-sparsely-rewarded-games.md | Kong, Ram, Yu 2026, *AlphaZero in Sparsely Rewarded Games* (AZAL) |
| AGENTS | arxiv-2604.25067-frontier-coding-agents-can.md | Sherwood et al. 2026, coding agents build AlphaZero for Connect Four |
| VIT | arxiv-2408.13871-alphavit-flexible-game-playing.md | Fujita 2024/25, AlphaViT/ViD/VDA |
| SWEEP | arxiv-1903.08129-hyper-parameter-sweep-alphazero.md | Wang et al. 2019, hyper-parameter sweep on AlphaZeroGeneral (6×6 Othello) |
| CX | arxiv-2210.08263-reinforcement-learning-connectx.md | Shah & Gupta 2022, RL for ConnectX (Kaggle) |
| EVO | arxiv-2405.16595-evolutionary-framework-connect-4.md | Taylor & Stella 2024, Q-learning vs minimax vs MCTS on Connect-4 |
| IMB | arxiv-1406.0486-monte-carlo-tree-search.md | Lanctot et al. 2014, MCTS with implicit minimax backups |
| SK | scheiermann-konen-alphazero-ntuple.md | Scheiermann & Konen 2022, MCTS wrapper around TD n-tuple nets, MCTS only at test time |
| FARL | konen-final-adaptation-rl-nplayer.md | Konen & Bagheri, Final Adaptation RL (TD-FARL) for N-player games |
| AZJL | alphazero-jl-connect-four.md | AlphaZero.jl Connect Four tutorial (J. Laurent) |
| OR1–OR6 | web-medium-oracle-alphazero-part1.md, …-connect-four-part2.md, …-part3.md … -part6.md | Oracle Developers "Lessons from AlphaZero" series, parts 1–6 |
| CPW-AZ | cpw-alphazero.md | Chess Programming Wiki, AlphaZero |
| CPW-RL | cpw-reinforcement-learning.md | CPW, Reinforcement Learning |
| CPW-TD | cpw-temporal-difference-learning.md | CPW, Temporal Difference Learning (incl. practitioner quotes) |
| TDL | baxter-tdleaf-lambda.md | Baxter, Tridgell, Weaver 1999, TDLeaf(λ) (chess + backgammon) |
| KC | baxter-knightcap.md | Baxter, Tridgell, Weaver 1998, KnightCap |
| TDG | tesauro-td-gammon-html.md | Tesauro 1995, TD learning and TD-Gammon |

---

## 1. Methods, one subsection per paper

### 1.1 AlphaZero, the reference recipe [CPW-AZ, AVI App. A, OR1, OR4, SPARSE §4/App. B]

**Problem.** Learn a policy and a value from self-play with no domain knowledge, using MCTS as the policy-improvement operator.

**Algorithm (precise).**
- Network: f_θ(s) = (p, v). p is a distribution over moves (illegal moves masked and renormalised) and v ∈ [−1, 1] [CPW-AZ "Network Architecture"; SPARSE App. B.1 eq. 5–7].
- Search: PUCT selection, a* = argmax_a [ Q(s,a) + c_puct · P(a|s) · √N(s) / (1 + N(s,a)) ] [AVI eq. 12; SPARSE eq. 8]. A leaf is expanded on its first visit and the value from the net is backed up, with no rollout [OR1]. Dirichlet noise is mixed into the root prior during self-play: p' = x·p + (1−x)·Dir(α) with x = 0.75 in the paper [OR3].
- Move choice: π_MCTS(a|s) = N(s,a)^{1/τ} / Σ_b N(s,b)^{1/τ} [AVI eq. 10]. τ = 1 for the first 30 moves, then τ → 0 [OR3].
- Targets: every visited state is stored as (s, π_MCTS, z), where z ∈ {−1, 0, +1} is the final game result from the perspective of the side to move [AVI §3.4; OR4].
- Loss: L_AZ = Σ_i (V_θ(s_i) − z_i)² − Σ_i π_MCTS(·|s_i)ᵀ log π_θ(·|s_i) [AVI eq. 11]. Implementations add L2 regularisation (AZJL: `l2_regularization=1e-4`).

**Setup and compute (original).** Body: 19 residual blocks with 256 3×3 filters. Value head: 1×1 convolution, dense layer of 256, then tanh. Training: 700,000 mini-batches of 4,096, with 5,000 first-generation TPUs for self-play and 64 second-generation TPUs for training [CPW-AZ "Network Architecture", "Training"].

**Results.** Beat Stockfish 8 by +28 =72 −0 in 100 games (2017 preprint). In the 2018 Science version it won 155 and lost 6 of 1,000 games against several Stockfish versions [CPW-AZ "Stockfish Match"].

**Limitations (as reported elsewhere).**
- Training is dominated by inference. Oracle needed more than 120 M position evaluations per generation [OR5 "Model Size"].
- Many hyper-parameters are not explained [OR6 "Overview"; SWEEP §1].
- The value target is noisy [OR4].
- "Strong ≠ perfect": see §1.4.

### 1.2 Athénan: Descent and Unbounded Minimax with "tree learning" [MSB] (read carefully)

**Problem.** Zero-knowledge learning for two-player perfect-information games without a policy network. The claim is that it is far more data-efficient than AlphaZero/Polygames [MSB Abstract, §1].

**Components.**
1. **Descent Minimax**, used for self-play during training. It is a best-first minimax that, on each iteration, extends the current best line all the way to a terminal state. Unbounded Minimax and MCTS stop at a leaf instead. One Descent iteration is "a deterministic complete simulation of the rest of the game", so terminal values reach shallower states faster [MSB §2.3.1].
2. **Learning target = partial-tree minimax value, not game outcome.** "The learned target value of a state is not the end-game value but its minimax value in the partial game tree built during the match" [MSB §2.3.1].
3. **Tree learning.** Every state in the partial tree is learned, not only the states on the played line: D ← {(s, v(s)) | s ∈ S} [MSB §2.3.1, Alg. 3]. As a result Athénan produced **296× more learned states** than Polygames (50–700× depending on the game) while doing 2.5× fewer state evaluations [MSB §3.2, Table 4].
4. **No policy head.** No action encoding is needed. All children of an expanded state are batched to the GPU together [MSB §2.3.1].
5. **Play-time search: UBFM_s** (Unbounded Best-First Minimax with "safe decision"). It repeatedly extends the leaves of the principal variation. After the search it plays the most-explored action, not the best-valued one [MSB §2.3.1]. In the earlier work it beat both plain UBFM and αβ in head-to-head play [MSB §2.1].
6. **Optional "reinforcement heuristic".** A terminal evaluation richer than ±1/0. The best general choices were "scoring" and the "depth heuristic", which favours quick wins and slow losses [MSB §2.3.1].

**Descent pseudo-code.** I reconstructed this from the symbol stream of Alg. 1 and Table 1 [MSB §2.1, Table 1]. Values are from the first player's point of view, `best_action` takes the max for player 1 and the min for player 2, f_θ is the learned evaluation of non-terminal leaves, and f_t is the terminal evaluation (game gain or reinforcement heuristic).

```
descent_iter(s, S, T, fθ, ft):
  if terminal(s):
      S ← S ∪ {s};  v(s) ← ft(s)
  else:
      if s ∉ S:                               # first visit: expand all children
          S ← S ∪ {s}
          for a in actions(s):
              if terminal(a(s)):
                  S ← S ∪ {a(s)}; v(s,a) ← ft(a(s)); v(a(s)) ← v(s,a)
              else:
                  v(s,a) ← fθ(a(s))           # batched GPU call over all children
      a_b ← best_action(s)                    # argmax/argmin over v(s,·)
      v(s,a_b) ← descent_iter(a_b(s), S, T, fθ, ft)   # recurse, always down to a terminal
      a_b ← best_action(s)
      v(s) ← v(s,a_b)                         # minimax backup
  return v(s)

descent(s, S, T, fθ, ft, τ):
  t0 ← time();  while time() − t0 < τ: descent_iter(s, S, T, fθ, ft)
  return S, T
```

Training loop [MSB §2.4, Alg. 3]:

```
while time left:
  s ← initial_state(); S ← ∅; T ← {}
  while not terminal(s):
      S, T ← descent(s, S, T, fθ, ft, τ)      # S and T persist through the whole match
      a ← action_selection(s, S, T)           # "ordinal law", see below
      s ← a(s)
  D ← D ∪ {(s, v(s)) | s ∈ S}                 # every node of the partial tree
  update(fθ, D)                               # regression of fθ(s) toward v(s)
```

**Training setup (all from [MSB §2.4, §3.1.1]).**
- *Exploration*: the "ordinal law", which picks actions at random according to the rank order of their values. Its exploitation parameter ε′ is redrawn uniformly from [0, 1] at every move, with no annealing. The exact formula is in Cohen-Solal 2020 and is not in this file.
- *Replay*: data from the last 100 matches, with "smooth experience replay" (μ = 100, δ = 3; the definition is in Cohen-Solal 2020).
- *Optimisation*: SGD batch size B = 3,000.
- *Network*: one value-only "R2" network for every game. It is: convolution, 8 residual blocks (each ReLU-conv-ReLU-conv, 3×3, F = 132 filters, with padding), then three dense layers ending in 1 output. About 5·10⁶ parameters; the width D of the dense layers varies by game (Connect6 D = 65, Hex13 D = 155, Othello D = 477) [MSB Tables 2–3].
- *Hardware*: one V100 32 GB with 2–10 CPUs. Athénan is pure Python (TF 1.15) and plays matches sequentially. Polygames is C++ and runs games in parallel [MSB §3.1.2, §3.2].

**Results.**
- *Same resources, 15 days, 8 games, scored against 160-rollout UCT MCTS*: the "gain" ½[(w−l)_A − (w−l)_B] of Athénan over Polygames was **+35.8 %** without the reinforcement heuristic and **+60.75 %** with it. Athénan without it matched Polygames' 15-day level in 2 days (7×); with it, in well under half a day (>30×) [MSB §3.3].
- *Hex 13, 113 days, against Mohex 2.0*: Athénan with the reinforcement heuristic went "rather far beyond" Mohex. Polygames won 0 % at every checkpoint [MSB §3.4].
- *Against tournament Polygames networks* (TCGA 2020 medallists, about 7 days on 100 GPUs): Athénan trained 5 days on 1 GPU won at Breakthrough, Othello 8 and Othello 10 [MSB §3.5]. §5 says "half a GPU"; the paper is inconsistent here.
- *Computer Olympiad*: 5 golds in 2020, 11 in 2021, 5 in 2022 [MSB §5].
- *Ablations from the earlier work*, as summarised here: tree learning beat terminal learning by ≥40 % final win rate on almost all of 9 games, and Descent with tree learning beat MCTS with tree learning by ≥40 % on all 9 [MSB §4]. MCTS without a policy but trained on search values did "much worse" than Descent + tree learning [MSB §3.2].

**Why it works (authors' account).**
- AlphaZero learns one summary per search. Tree learning keeps all of the search information [MSB §3.2].
- Naively applying tree learning to AlphaZero's outcome target would give every node in the tree the same label and cause overfitting [MSB §3.2].
- Descent's deep best-first exploration is what makes tree learning safe. With other search algorithms, tree learning can hurt [MSB §4].

**Limitations.**
- Exact formulas (ordinal law, smooth replay, Descent's handling of the transposition table T) and the per-game curves are in the technical appendix and in Cohen-Solal 2020, neither of which is in our sources.
- The baseline opponent (160-rollout MCTS) is weak.
- Strength is measured only relative to other programs; there is no oracle.
- The implementation is Python, so absolute throughput numbers do not carry over [MSB §3.2].

### 1.3 Approximate Value Iteration in self-play [AVI]

**Problem.** How far can plain value learning go without any tree search during training? The study is oracle-evaluated on Connect Four, Hex 7×7 and synthetic "F-Games", and spot-checked on Othello and Go 9×9 [AVI Abstract, §1].

**Algorithm (precise).** Negamax convention, so values are always from the side to move:
- Target: y_θ̄(s) = max_{a∈A(s)} [ R(s,a) − γ · V_θ̄(f(s,a)) ], where θ̄ is frozen during collection [AVI eq. 4].
- Loss: L_AVI(θ) = E_{(s,y)∼D} [(V_θ(s) − y)²] [AVI eq. 5].
- Behaviour: ε-greedy with respect to the same one-step lookahead, G(V)(s) = argmax_a [R − γV(f(s,a))] [AVI eq. 2].
- Storage: a circular replay buffer [AVI §3.3].

Pseudo-code, reconstructed from Alg. 1 [AVI §3.3]:

```
for i = 1..I:
  θ̄ ← θ
  for k = 1..K:                                    # collection, network frozen
    for each of N_envs parallel games with state s:
      q(s,a) = R(s,a) − γ V_θ̄(f(s,a))  for all legal a
      push (s, max_a q(s,a)) into replay buffer D
      a ← ε-greedy over q;  step; reset if terminal
  for g = 1..N_grads:                              # training
    sample batch of B pairs from D;  Adam step on (V_θ(s) − y)²
```

**Setup** [AVI §4.5, App. C.1, Table 3].
- *Collection*: N_envs = 128, K = 128.
- *Optimiser*: Adam, lr 3·10⁻⁴, batch 256, replay buffer 10⁶, 4 epochs per iteration.
- *Exploration*: ε = 0.3, tuned over {0.05, 0.15, 0.3}; ε = 0.15 for Othello and Go.
- *Lookahead*: depth 1.
- *Network* (Connect Four): an MLP over the flattened 6×7×2 one-hot board, with 4 residual blocks of 4 layers × 256 units each, LayerNorm and GeLU.
- *Symmetry*: the mirror symmetry is enforced by averaging V over the original and the reflected board.
- *Training length*: 2.4 M gradient updates for both AVI and AlphaZero, which also shared data volume, batch and code.
- *Wall-clock* (single GPU, mostly a 1080 Ti): Connect Four AVI ≈ 2 h vs AlphaZero ≈ 3 h (S = 32 simulations) to 2 days (S = 512). Hex AVI ≈ 20 h vs 20 h to 4 days [AVI App. C "Indicative wall-clock times"].

**Results.**
- *Value accuracy*: AVI reached lower mean absolute value error than AlphaZero at every MCTS budget S ∈ {32, …, 512}. The gap widened over training, and none of 20 seeds diverged [AVI §5.1].
- *Head-to-head*: greedy 1-ply AVI against AlphaZero with S = 512 scored **−0.09 ± 0.04** on Connect Four and **+0.38 ± 0.09** on Hex 7×7 [AVI Table 2]. AVI needs at most 7 (Connect Four) or 49 (Hex) network calls per move, against 512 for AlphaZero [AVI §5.1].
- *Values inside deeper search*: with the AlphaZero policy held fixed, swapping in the AVI value lowered the error rate at every MCTS budget. In value-only minimax lookahead, AVI's value was "markedly stronger" than AlphaZero's [AVI §5.2].
- *Othello / Go 9×9*: training stayed stable. Greedy AVI remained weaker than MiniZero with 200 simulations, but AVI values improved MiniZero's own MCTS [AVI §5.3].

**Authors' explanation of the stability.** Negamax backups flip the sign of successor errors, so overestimates may alternate instead of accumulating. They label this a hypothesis [AVI §6].

**Limitations.**
- Greedy inference spreads its budget evenly over all moves [AVI §5.3].
- ε-greedy may miss sparse strategies [AVI §6].
- Strong claims rest only on small solved games [AVI §6].
- γ is never given a value in the paper.

### 1.4 AlphaZero in sparsely rewarded games and oracle-auxiliary loss (AZAL) [SPARSE]

**Problem.** Measure how far "superhuman" AlphaZero is from "perfect" AlphaZero on solved games (Connect Four with Pons' solver, Chomp with a Grundy oracle), and test whether oracle supervision closes the gap [SPARSE §1, §3].

**Algorithm.**
- AZAL loss: L = L_policy + L_value + λ_aux · L_aux.
- L_aux(s) = −Σ_a q(a|s) log p_θ(a|s), with the smoothed oracle target q(a|s) ∝ (1[a ∈ B(s)] + ε)·1[a ∈ A(s)], ε = 10⁻³, where B(s) is the oracle's set of optimal moves [SPARSE §4].
- Labels are attached once per iteration, after deduplication, by batched solver queries [SPARSE App. B.3].

**Setup (Connect Four)** [SPARSE App. D, Table 5].
- Network: Conv-ResNet with 9 blocks × 128 channels and a 3-plane input.
- Search: 1,000 MCTS simulations per move.
- Volume: 20 iterations × 700 self-play games.
- Optimisation: batch 128, lr 10⁻³.
- Exploration: constant temperature 1.25, Dirichlet ε = 0.25, α = 0.3.
- λ_aux = 1.0, 3 seeds, last checkpoint evaluated.

**Results** [SPARSE Tables 1–3].
- *Vanilla AlphaZero on Connect Four*: 0/60 perfect self-play traces, oracle-match rate 0.785, first failure at ply 0.867 on average (it usually misses the correct opening move), sampled-state match 0.589.
- *AZAL on Connect Four*: match 0.849, first failure at ply 5.283, sampled-state match 0.768, still 0/60 perfect traces.
- *Chomp 10×11*: AZAL reached 60/60 perfect traces.
- **Self-play target corruption** [Table 3]: in 14.4 % (vanilla) and 17.4 % (AZAL) of Connect Four positions the oracle says the side to move is losing, yet that side went on to win the self-play game. In other words, z-labels are wrong because the self-play opponent fails to convert.
- *Training dynamics*: policy and value losses plateau and keep spiking. The authors read this as the network "chasing search targets generated by an evolving self-play distribution" [SPARSE §5.1].

**Limitations.** Three seeds, small sampled-state sets, only the final checkpoint evaluated, and no comparison with deep-state resampling methods such as Go-Exploit [SPARSE §7].

### 1.5 Coding agents build AlphaZero for Connect Four on consumer hardware [AGENTS]

**Setup.**
- Agents implemented AlphaZero-style MCTS self-play for 7×6 Connect Four within 3 hours on one RTX 5060 Ti, a Ryzen 7 9700X and 32 GB RAM; the sandbox had 16 GB RAM and 12 CPUs [AGENTS §2.1, §2.2.1].
- Moves had to be chosen by MCTS with the trained network, and minimax or αβ were forbidden in the player. 30 s per move was allowed [AGENTS App. A.1, Part 6].

**Results.**
- *Ratings*: in a 49-player round robin rated with Bradley–Terry and anchored so Pons = 2000, the Opus 4.7 runs averaged ≈1938, and 4 of 8 runs matched or exceeded Pons [AGENTS §5.1, §5.3.3].
- *Against the solver*: 7 of 8 Opus 4.7 runs beat Pons when moving first. Pons as second player still won 62 of 96 games against the field [AGENTS §5.1].
- *Networks*: the trained ResNets were small (5×64, 6×96, 5×96) with a mean of about 518 K parameters.
- *Speed*: the mean move time was 1.62 s, against 0.10 s for the Pons solver [AGENTS §5.3.3].

**Relevance.**
- A sub-1 M-parameter network trained for under 3 GPU-hours on a consumer GPU is enough to play Connect Four near-perfectly — but with MCTS at about 1–2 s per move [AGENTS §5.3.3].
- Self-play agents scored *above* the solver when moving second, because they induce mistakes in imperfect opponents, while the solver only maximises the length of a lost game [AGENTS §5.1].

**Limitations.**
- Connect Four is solved, so the measure has a ceiling.
- Possible spec violations (non-MCTS techniques in the players) were not verified.
- n = 8 trials per agent [AGENTS §5.3, §7.3].

### 1.6 AlphaViT / AlphaViD / AlphaVDA [VIT]

**Problem.** One transformer network for several games and board sizes [VIT Abstract].

**Training.**
- Loss: AlphaZero's l = (c_win − v)² − πᵀ log p [VIT App. B eq. 11].
- Self-play: N_self games per iteration (30 for Connect Four), softmax-on-visits move choice with temperature τ for the first T_opening moves (4 for Connect Four, τ = 100), then the greedy move [VIT App. B eq. 10, Table A2].
- Symmetry: 2 symmetries for Connect Four, 8 for Othello and Gomoku [VIT App. B].
- Data and optimiser: replay queue of 100,000 states, 1 epoch, AdamW, lr 10⁻⁴, weight decay 0.01, batch 1,024 [VIT Table A1].
- Search: 200 MCTS simulations for Connect Four [VIT Table A2].
- Hardware: RTX 4060 Ti 16 GB or RTX 3060 12 GB, two GPUs per machine [VIT §4.4].

**Results (Connect Four Elo, 50 round robins)** [VIT Table 3].

| Player | Elo | Notes |
|---|---|---|
| AlphaZero (Res6 × 256 filters) | 2114 | 7.1 M parameters |
| AlphaViT L4 | 1846 | 11.2 M parameters |
| MCTS400 | 1516 | |
| Minimax (depth 3, hand evaluation) | 1012 | |

Other findings:
- The CNN AlphaZero was the most parameter-efficient model. The authors attribute this to the CNN's inductive bias [VIT §7].
- Pre-training on a small board sped up Gomoku and Othello but gave little or no gain for Connect Four [VIT §5.4].
- Single-game Gomoku models declined after their peak, read as overfitting. Multi-game training did not show this decline [VIT §5.2].
- AdamW clearly beat SGD for the AlphaZero baseline [VIT App. G].
- Plain MCTS stopped improving past about 1,600 simulations in Connect Four [VIT Table A3].
- Timing: about 0.65–0.8 s per decision on GPU. FP16 helped training but not inference [VIT App. I].

**Relevance.** Weak. For a size-generic board the paper points to fully convolutional networks with global pooling (KataGo, Polygames) or to transformers [VIT §2, §7; MSB §2.1].

### 1.7 Hyper-parameter sweep on AlphaZeroGeneral [SWEEP]

**Setup.**
- Game and code: 6×6 Othello on AlphaZeroGeneral, with a network of 4 conv + 2 fully connected layers [SWEEP §3.3].
- Method: one parameter varied at a time over {min, default, max} [SWEEP Table 1].
- Default configuration: 100 iterations, 50 episodes, tempThreshold 15, 100 MCTS simulations, c_puct 1.0, retrain length 20 iterations, 10 epochs, batch 64, lr 0.005, dropout 0.3, 40 arena games, update threshold 0.6.
- Cost: the default configuration took 44 h on a GTX 980 Ti [SWEEP Table 2].

**Findings (best value per objective)** [SWEEP Table 3].

| Parameter | Best for Elo | Note |
|---|---|---|
| tempThreshold | 10 | |
| MCTS simulations | 200 | Peaked around iteration 60, then dropped |
| c_puct | 0.5 (or 2.0) | 0.5 gave the lowest loss |
| retrain length | 40 | Length 1 gave the lowest *loss* but overfitted |
| epochs | 15 | |
| batch | 32 | |
| lr | 0.001 | |
| dropout | 0.2 | |
| arena games | insignificant | |
| update threshold | 0.6 | |

- Time-sensitive parameters: iterations, episodes, simulations, retrain length, epochs, batch, arena games. "Time-friendly" ones, cheap to tune: temperature threshold, c_puct, lr, dropout, update threshold [SWEEP Table 2].

**Limitations.** One run per setting, no interaction effects.

### 1.8 RL for ConnectX (Kaggle) [CX]

- Agents compared: greedy, plain MCTS (UCB, c = √2, about 20,000 simulations per move), depth-5 αβ with the heuristic Σ 1000^{i−1}·(own i-in-a-row) − Σ 2000^{i−1}·(opponent's), an MCTS–minimax hybrid (depth-2 minimax rollouts, about 500 simulations, c = 1/√2), and AlphaZero (gated at 0.6 win rate among decisive games). Variable boards were handled by padding the input to 12×12 [CX §4].
- AlphaZero was the best, at Kaggle rank 9 of 225 [CX §5]. With only 10 games per pairing the comparison is weak evidence [CX Table 1].

### 1.9 Evolutionary tournament: Q-learning, minimax, MCTS on Connect-4 [EVO]

- Tabular Q-learning trained on 200 k games (1.29 M unique states) lost 100 % of games against depth-6 αβ-with-move-ordering and against MCTS-Decisive. Unseen states are played randomly [EVO §II, §III, Table I].
- Training half the games against minimax instead of pure self-play gave a Q-agent that won 61 % against the pure self-play one [EVO §II].
- "Decisive moves" (always take a winning move, always block an immediate loss) raised MCTS to 60 % against plain MCTS [EVO §II].
- Odd search depths (3, 5) won 50 % against the heuristic control, while even depths (4, 6) won 100 %. This is an odd/even evaluation imbalance [EVO §III].
- Implementation quality matters: the paper calls an earlier claim that MCTS beats correct minimax "surprising" given that correctly configured minimax plays optimally [EVO §I].

### 1.10 MCTS with implicit minimax backups [IMB]

- Each node keeps a heuristic minimax value v^τ_s, backed up by max over children, next to the Monte Carlo mean. Selection uses Q̂^{IM}(s,a) = (1−α)·r_{s,a}/n_{s,a} + α·v^τ_{s,a} inside UCB [IMB §3, eq. 2].
- α ∈ [0.15, 0.4] is a "safe choice" [IMB §5].
- *Breakthrough*: MCTS(ipp, im 0.4) beat MCTS(ipp) 82.3 % of the time [IMB Table 2].
- *Against αβ*: with the sophisticated evaluation at 1 s, MCTS won only 7.9 % (28.95 % with implicit minimax) [IMB Table 4]. "αβ performs significantly better against MCTS at low time settings" with that evaluation, and MCTS catches up only at 60–120 s [IMB §4.2].
- **Relevance:** this supports αβ, not MCTS, at our 100 ms budget when a good evaluation exists. **[INFERENCE]** The paper's data are at ≥1 s per move; at 100 ms the gap should favour αβ even more.

### 1.11 n-tuple TD learning with an MCTS wrapper at test time only [SK]

**Problem.** Get most of AlphaZero's benefit at very low cost: train a TD n-tuple value network on CPU, then add PUCT-MCTS only during play [SK Abstract, §II-A].

**n-tuple value function.**
- An n-tuple is n board cells, each with m states, indexing a look-up table of mⁿ weights. V(s) = σ(Φ(s)·Θ) [SK §II-C, eq. 3].
- Tuples are generated as random walks of adjacent cells [SK §II-C].
- **Connect Four configuration**: n = 8, m = 4, with cell codes 1/2 for the players, 3 for empty and playable next move, 0 for empty and not yet reachable [SK Fig. 3b]. k = 2·70 tables, 9,175,040 weights of which only 8 % are ever activated [SK Table I].

**Learning.**
- TD-FARL (§1.12), plus Temporal Coherence Learning (TCL): each weight gets its own rate α·α_i, with α_i = |N_i|/A_i, the ratio of the summed signed changes to the summed absolute changes. Optionally α_i = e^{β(x−1)} (the "TCL-EXP" variant) [SK §II-E].
- Exploration is ε-greedy self-play only, with no external data [SK §II-D].
- *Connect Four*: α = 3.7, TCL on, λ = 0, ε 0.1 → 0, 6 M episodes [SK §III-B].
- *Othello*: α = 0.2, λ = 0.5, ε 0.2 → 0.1, 250 k episodes [SK §III-B].

**MCTS wrapper.**
- Uses the AlphaGo Zero PUCT rule. The prior P(s,a) is the softmax of the n-tuple values of the afterstates [SK §II-B, §II-C].
- The tree is reused between moves, and the most-visited move is played [SK §II-B].

**Compute.** Everything ran on one laptop CPU (i7-9850H). Connect Four took 1.4 days for 10 agents, about 3.4 h per agent; Othello took about 1.8 h per agent [SK §IV-D, Table III].

**Results.**
- *Connect Four* [SK Table II]:
  - TCL-base moving first against AB-DL (a near-perfect αβ with opening book that maximises loss distance) scored 91 W / 2 T / 7 L.
  - TCL-wrap with 1,000 iterations scored 99/0/1.
  - As second player against MCTS-10k, TCL-wrap won 97 % against AB-DL's 80 %.
- *Wrapper iterations*: 50–100 iterations already gave >80 % win rate in that test [SK §IV-B].
- *Othello*: TCL-wrap with 10 k iterations beat Edax up to level 7 [SK §IV-A].
- **Move times** [SK Table IV]: TCL-base 0.05 ms per move in Connect Four; TCL-wrap with 1,000 iterations 30 ms; with 10,000 iterations 300 ms (GBG is a Java framework [SK §II]).
- *MCTS inside training*: under equal wall-clock it was worse. With equal episodes, 100 in-loop iterations improved Edax level 8 from 34 % to 66 % at about 30× the compute [SK §IV-E].

**Limitations.**
- Othello stays below Olivaw, which reached Edax level 10.
- A deeper network or MCTS during training may be needed for the last gains [SK §V-C].

### 1.12 Final Adaptation RL (TD-FARL) for N-player games [FARL]

**Algorithm.**
- Player-centred TD: each player's afterstate is linked to *that same player's* previous afterstate, one round back. Target T = r′ + γV(s′), error δ = T − V(s_last[p]) [FARL §2.4–2.5, Alg. 2].
- **FARL step at game end**: every player p other than the one who moved last updates V(s_last[p]) toward their own final reward, δ = r[p] − V(s_last[p]). Terminal states are also pushed toward 0 [FARL §2.7].
- n-tuple update with symmetries S(s′) and m tuples: w ← w + (α / (m·N_S)) · δ · σ′(ν(s′)) for every activated index, each index updated at most once per step. The 1/(m·N_S) normalisation means α = 1 moves a linear V exactly onto the target [FARL App. 0.A.3, eq. 3–4].
- TD(λ) uses a truncated eligibility horizon h = ⌊log_λ 0.1⌋ [FARL App. 0.A.3].
- Afterstates are preferred to (s, a) Q-learning: an action's value is fully determined by its afterstate, so Q/SARSA store the same value repeatedly and need N_a times more weights [FARL §2.2, §2.6].

**Connect Four settings** [FARL App. 0.A.6]: ε 0.1 → 0, α = 3.7, λ = 0, sigmoid output, symmetry used, 70 fixed 8-tuples, TCL-EXP with β = 2.7, 5 M episodes.

**Results** [FARL Table 1].
- *Connect Four*: 49.5 % ± 0.5 against AB and 46.5 % ± 0.5 against AB-DL (50 % is perfect). **Without FARL: 3.5 % and 0.0 %**. The no-FARL agent activated only 0.9 % of its weights against 8 % for TD-FARL: the losing side never receives its terminal reward [FARL §3.2].
- *Other games*: Hex 6×6 81 % against MCTS-10000 (0 % without FARL). Othello was the exception, nearly unaffected by FARL [FARL §3.3].

### 1.13 AlphaZero.jl, Connect Four tutorial [AZJL]

**Configuration (copied from params.jl)** [AZJL "Full Training Configuration"].
- Network: 5 residual blocks × 128 filters, about 1.6 M parameters, with 32-filter policy and value heads.
- Self-play: 5,000 games per iteration with 600 MCTS simulations per move; c_puct 2.0; Dirichlet ε 0.25, α 1.0.
- Temperature: 1.0 up to move 20, falling to 0.3 by move 30.
- Learning: Adam lr 2·10⁻³, L2 10⁻⁴, batch 1,024, position averaging, symmetry augmentation, weighting by `LOG_WEIGHT`.
- Memory: grows from 400 K to 1 M samples by iteration 15. About 175 K new samples are generated per iteration (35 moves × 5,000 games).
- Gating: arena update threshold 0.05.

**Compute.** 15 iterations at 1–2 h each on an i5-9600K with an **8 GB RTX 2070** [AZJL "Setup and Training"]. **[INFERENCE]** That is about 15–30 GPU-hours, the closest documented match to our 8 GB budget.

**Results.** The network alone (no search) ended "significantly stronger" than the depth-5 minimax baseline. Measured against the perfect solver it made few mistakes that a 14-move lookahead would catch, but it was "still imperfect" at long-term strategy and openings [AZJL "Experimental Results"].

**Advice.**
- Adam was more forgiving than SGD with cyclical rates.
- Doubling MCTS simulations from 300 to 600 made learning faster.
- MCTS parameters were tuned by grid search on a trained agent; `prior_temperature` mattered.
- None of the Python AlphaZero implementations the author knew of beat even a depth-2 minimax on one desktop [AZJL footnote 1].

### 1.14 Oracle "Lessons from AlphaZero", parts 1–6 [OR1–OR6]

- **Part 2, supervised upper bound.**
  - Method: train the *same* network on solver labels to get the architecture's ceiling, then compare the self-play-trained network with it. This separates "architecture too weak" from "self-play too weak" [OR2 "Training"].
  - "Strong" labels count only the fastest win or slowest loss as correct. To train AlphaZero in strong mode, terminal values were scaled by game length n as **1.18 − 9n/350**, which lies in [0.1, 1] [OR2 "Strong vs Weak"].
  - Strong mode: supervised network 96.20 %, supervised + MCTS 97.29 %, AlphaZero network 95.70 %, AlphaZero + MCTS 96.95 %.
  - Weak mode: AlphaZero network 98.83 %, AlphaZero + MCTS 99.76 %, against a 58.1 % random baseline [OR2 "Results"].
- **Part 3, parameters.**
  - c_puct ≈ 4.
  - Dirichlet α: the 10/n heuristic gives 2.5; 1.75 did better early on, and the final choice was **α = 1.0**.
  - τ = 1 for the whole game beat dropping it after 30 moves. 1.75 did best, but no better than simply raising c_puct.
  - INT8 quantisation encourages exploration.
  - Deduplicating positions and averaging their targets fixed the over-representation of openings [OR3].
- **Part 4, value target.**
  - z (the game outcome) is noisy: one late blunder labels every earlier position as lost.
  - q (the root MCTS value) avoids that noise but suffers from the horizon effect and is meaningless early in training.
  - For Connect Four, q alone was slightly better than z. **Averaging z and q, or falling linearly from 100 % z at generation 1 to 100 % q at generation 20, was best.** Falloff ended with a slightly lower error than averaging [OR4].
- **Part 5, throughput.**
  - Per generation: 7,168 games, giving 140–220 K positions and needing more than 120 M position evaluations.
  - Model grown from 5×64 to 20×128.
  - Shared evaluation caching cut inference requests by more than 2×; inference requests were batched; parallel MCTS with virtual loss reached >95 % GPU utilisation.
  - TensorRT INT8 gave about 4× throughput. The value-head precision loss was fixed by using **32 filters in the heads** [OR5].
- **Part 6, tuning.**
  - 1cycle learning-rate schedule; c_puct 3–4; α = 1; position averaging (30–50 % of the training window is duplicates); 32 head filters; 2 epochs per generation.
  - "Slow window": the training window grows from 4 to 20 generations by generation 35, so the earliest bad data leaves sooner.
  - Combined effect: about 150 generations cut to about 40, and **77 GPU-hours cut to 21**. Without these and the Part 5 changes it would have been more than 450 GPU-hours [OR6].

### 1.15 TD(λ), TDLeaf(λ) and KnightCap [TDL, KC, CPW-TD]

- **TD(λ) after a game** [TDL eq. 3–4; KC eq. 2–3]:
  - d_t = J̃(x_{t+1}, w) − J̃(x_t, w), with J̃(x_N) := r(x_N).
  - w ← w + α Σ_{t=1}^{N−1} ∇J̃(x_t, w) · Σ_{j=t}^{N−1} λ^{j−t} d_j. The sources print d_t in the inner sum; the index should be j.
  - λ = 0 regresses each prediction toward the next one. λ = 1 is gradient descent on Σ(r(x_N) − J̃(x_t))², which is Monte Carlo outcome learning [TDL eq. 5–6].
- **TDLeaf(λ)** [KC Fig. 3; TDL §4]:
  - Run a depth-d minimax search from each game position x_t and take the principal-variation leaf x_t^l, so J̃_d(x_t) = J̃(x_t^l).
  - Compute d_t = J̃(x_{t+1}^l) − J̃(x_t^l) and update ∇J̃(x_t^l) with the same λ-weighted sum.
  - Ties in the PV make the gradient multi-valued only on a measure-zero set, so one leaf is picked at random.
- **KnightCap details** [KC §4.1].
  - Linear evaluation, leaf scores squashed by tanh(β·J̃), with β set so that 1 pawn ≈ 0.25.
  - λ = 0.7, α = 1.0.
  - **Positive temporal differences zeroed unless KnightCap had predicted the opponent's move**, so the program does not learn to expect blunders.
  - Pawn value fixed for scale.
- **Results.**
  - 1650 → 2150 in 308 FICS games over 3 days. Later 2400–2500 on ICC with opening-book learning and 5,872 features [KC §4.1].
  - Initialising all weights to a pawn's value gave only about +300 in 1,000+ games [KC §4.2].
  - **Self-play for 600 games scored only 11 % against the FICS-trained version** [KC §4.2].
  - In backgammon at 2-ply, TDLeaf brought no gain over TD [TDL §6].
  - KnightCap "routinely gets crushed by faster programs searching more deeply". Better evaluation cannot replace search; the authors suggest "learning to search selectively" as the open problem [KC §4.1].
- **Practitioner notes** [CPW-TD "Quotes"].
  - Beal: piece values settle in about 500 self-play games, piece-square values in about 5,000. The learning rate is "critical". Rarely active terms learn slowly.
  - Hamstra: the "cause–effect problem". Mobility was inflated because it correlated with an extra piece.
  - TD-Leaf is TD(λ) applied to PV leaves. CilkChess used Temporal Coherence Learning [CPW-TD "TDLeaf(λ)"].

### 1.16 TD-Gammon [TDG]

- Setup: an MLP trained by TD(λ) in self-play, choosing moves by 1-ply greedy search over its own output [TDG "Learning Methodology"].
- Raw-board input, 40 hidden units, 200 k games: equal to Neurogammon. Adding hand-crafted features went beyond it [TDG "Results of Training"].
- Why it worked [TDG "Understanding the Learning Process"]:
  - Absolute errors are large but **errors are correlated across sibling positions, so relative ranking is good**.
  - Dice give exploration and make terminal states attractors. Deterministic games "would require some sort of external noise source" (random starts, stochastic move choice).
  - Linear concepts are learnt first.

### 1.17 Background pages [CPW-RL]

CPW-RL gives definitions only (MDP, Q-learning, TD) and a bibliography. It has no quantitative content.

---

## 2. Comparison table

Fit column legend: ++ strong fit, + usable, ~ partial, − poor. **[INFERENCE]** The fit column is my judgement for our constraints: train on one 8 GB GPU, run in pure Java at 100 ms per move inside αβ + threat search.

| Method (source) | Training target | Needs MCTS? | Compute reported | Strength reached | Fit for 8 GB train → Java αβ at 100 ms |
|---|---|---|---|---|---|
| AlphaZero, original [CPW-AZ] | z (MSE) + π_MCTS (CE) | Yes, train and play | 5,000 TPUv1 + 64 TPUv2; 700 k × 4,096 batches | Beat Stockfish 155–6 | − Network too large for Java αβ; MCTS-bound |
| AlphaZero.jl, Connect Four [AZJL] | z + π, position-averaged | Yes | 15 iterations × 1–2 h, RTX 2070 8 GB; 5 k games × 600 simulations per iteration | Network-only beats depth-5 minimax; imperfect openings | ~ Fits training budget; 1.6 M-parameter CNN too slow per node in Java αβ |
| Oracle Connect Four [OR2–OR6] | z→q linear falloff + π | Yes | 21 GPU-h after tuning (77 before); 20×128 ResNet | 95.7 % strong / 98.8 % weak move accuracy (network only) | ~ Lessons transfer; architecture does not |
| Agents' Connect Four [AGENTS] | z + π | Yes | < 3 h, one RTX 5060 Ti | Mean ≈ 1938 BT (Pons = 2000); beat Pons as first player 7/8 | ~ Proves the budget; at 1.6 s per move, MCTS + Python |
| AlphaViT [VIT] | z + π | Yes | Two 12–16 GB consumer GPUs; 1,000–3,000 iterations | Connect Four AZ 2114 vs ViT 1846 Elo | − Transformers 11–20 M parameters |
| Polygames (in [MSB]) | z + π | Yes | 15 d on 1 V100 per run | Lost to Athénan everywhere | − |
| Athénan / Descent [MSB] | Partial-tree minimax value of **every tree node** (MSE) | **No** (Descent in training, UBFM_s in play) | 15 d per run on 1 V100 in Python; 5 d on 1 GPU beat 100-GPU Polygames | Olympiad golds; beyond Mohex 2.0 at Hex 13 | ++ Value-only, minimax-native, data-rich. Our own Java Descent can produce the data |
| AVI [AVI] | 1-ply negamax backup max_a[R − γV(f(s,a))] | **No** | Connect Four ≈ 2 h on a 1080 Ti; 2.4 M updates | Lower value MAE than AZ-512; Connect Four −0.09 vs AZ-512, Hex +0.38 | ++ Value-only, trivially extended to a d-ply αβ target **[INFERENCE]** |
| AZAL [SPARSE] | z + π + λ·CE(oracle best moves) | Yes | 20 × 700 games, 1,000 simulations | Connect Four match 0.785 → 0.849 | + Idea only: use our endgame solver as the oracle **[INFERENCE]** |
| TD-FARL n-tuple [FARL] | TD(0) bootstrap + final adaptation | No | CPU; 5 M episodes in Connect Four | 49.5 % / 46.5 % vs perfect αβ (50 % max) | ++ Pattern tables = our evaluator type; Java (GBG) |
| TCL n-tuple + MCTS wrapper [SK] | as TD-FARL (+TCL) | Test time only | 3.4 h per agent on one laptop core (Connect Four) | 99/0/1 as first player vs AB-DL; Othello beats Edax level 7 | ++ Shows a pattern table + search is near-perfect in Connect Four; αβ in place of MCTS **[INFERENCE]** |
| TDLeaf(λ) / KnightCap [KC, TDL] | TD(λ) on PV-leaf evaluations | No (αβ) | 308 online games (chess) | 1650 → 2150 FICS | + Native to αβ; weak in self-play for deterministic games without noise |
| TD-Gammon [TDG] | TD(λ) | No (1–2 ply) | 200 k+ self-play games | Near world-class backgammon | ~ Relies on dice for exploration |
| Tabular Q-learning [EVO] | Q-learning | No | 200 k games | Lost 100 % to depth-6 αβ | − |
| MCTS + implicit minimax [IMB] | n/a (search) | Yes | — | αβ beats it at short time controls with a good evaluation | − at 100 ms |
| AlphaZeroGeneral sweep [SWEEP] | z + π | Yes | 44 h per run (980 Ti) | 6×6 Othello | Tuning lessons only |
| AlphaZero on ConnectX [CX] | z + π | Yes | Not reported | Kaggle rank 9/225 | Lessons only (12×12 padding) |

---

## 3. Training-target choices for a pattern-table or small-network evaluator inside αβ

What the sources say about each target, and what follows for us.

### 3.1 Game outcome z (Monte Carlo, equivalent to TD(1))
- **How it works.** Every position of a game is labelled with the final result, as in AlphaZero [AVI §3.4; OR4]. TD(1) is gradient descent on Σ(r − J̃(x_t))² [TDL eq. 6].
- **For.**
  - It is unbiased by the evaluator's own errors.
  - It counteracts the horizon effect of search targets [OR4].
  - KnightCap's authors suggest λ = 1 early on, when the evaluation is "completely wrong" [KC §4.2]. Beal & Smith used λ = 0.95 [KC §4.2].
- **Against.**
  - It is high-variance: one late blunder relabels every earlier position [OR4].
  - Self-play opponents fail to convert wins, so 14–17 % of oracle-lost Connect Four positions were labelled as wins [SPARSE Table 3].
  - Learning the outcome over the tree would give every node the same label and overfit [MSB §3.2].
  - AlphaZero's value was less accurate than AVI's bootstrapped value at every budget [AVI §5.1].
- **For us [INFERENCE].** Use z only as a minority term in the target mix, and mostly in early iterations (Oracle falloff). Never use it alone: our self-play will be imperfect, and destroy moves make positions more volatile.

### 3.2 TD(λ) on game positions (TD-FARL)
- **How it works.** Bootstrap on the next own-turn afterstate value. The FARL final step is mandatory: without it Connect Four fell to 3.5 % / 0 % [FARL §3.2].
- **Settings that worked in Connect Four.** λ = 0, ε 0.1 → 0, α = 3.7 with TCL-EXP, 5–6 M episodes, symmetry averaging inside the update [FARL App. 0.A.6; SK §III-B].
- **Strength.** It reaches near-perfect Connect Four with pattern tables and only a 1-ply policy [FARL Table 1]. Adding 1,000 MCTS iterations at play time nearly removes the remaining losses [SK Table II].
- **Weaknesses.**
  - The 1-ply greedy policy learns positions reachable by 1-ply play. That distribution differs from the leaves an αβ search evaluates. This is the gap TDLeaf addresses [KC §1, §3; TDL §7].
  - Plain CPU self-play is slow for large games: Othello at 250 k episodes did not reach the deep-network results [SK §V-C].
- **For us [INFERENCE].** The cheapest way to get a decent first set of weights, with no GPU and no search. The GBG code is the reference design [SK §II; FARL §1].

### 3.3 TDLeaf(λ)
- **How it works.** Apply TD to the *PV leaves* of the αβ search instead of the root positions [KC Fig. 3]. This trains the evaluator on the distribution it actually sees inside αβ, which explains the large gain in chess [TDL §7].
- **Practical tweaks** [KC §4.1]:
  - Squash leaf scores with tanh(β·eval).
  - Zero positive temporal differences unless the opponent's move was predicted.
  - Initialise near good weights.
- **Against.**
  - In self-play the deterministic game shrinks the explored distribution: the self-play version scored only 11 % [KC §4.2].
  - No gain in backgammon [TDL §6].
  - Ties in the PV make the gradient ambiguous, so one leaf is picked at random [KC Fig. 2–3].
- **For us [INFERENCE].** Store the PV leaf (or the quiescence/threat-search leaf) of each self-play search, and fit V(leaf) to the next search's root value. Noise in the openings is needed to avoid narrow self-play.

### 3.4 Descent-style minimax backup with tree learning
- **How it works.** The target is the minimax value of the partial tree, with terminal values fed in by playouts that run to the end of the game, applied to *all* tree nodes [MSB §2.3.1].
- **Evidence.** 296× more training states per unit of time; 7–30× faster learning than Polygames; tree learning better than outcome learning by ≥40 % win rate in the earlier study [MSB §3.2–3.3, §4]. With a reinforcement heuristic that favours fast wins and slow losses, it was better again (+60.75 % against +35.8 %) [MSB §3.3].
- **Caveat from the source.** Tree learning helps *with Descent* specifically, because Descent explores deep and best-first. With other search algorithms it can lower performance [MSB §4].
- **For us [INFERENCE].**
  - αβ with fail-high and fail-low cutoffs gives only bounds at most nodes. Only PV nodes and their exact-window children have exact values.
  - So "tree learning from αβ" means harvesting PV nodes and transposition-table entries flagged EXACT, which is much less data than Descent.
  - The cleanest way to get Descent's benefit is to implement Descent itself in Java *for data generation*. It is about 60 lines (pseudo-code in §1.2) and our pattern evaluation is cheap enough to run thousands of iterations per move. The shipped bot stays αβ.

### 3.5 Search-value distillation: bootstrapping from a deeper search
- **How it works.** AVI is the 1-ply case: y = max_a[R − γV(child)] [AVI eq. 4]. The Oracle "q" target is the MCTS version, the root Q after 800 simulations [OR4]. AVI cites Veness et al. 2009 for bootstrapped learning from minimax search reaching master-level chess with a linear approximator [AVI §2]; that paper is not in our sources.
- **Evidence.**
  - AVI values were more accurate than outcome-trained AlphaZero values and transferred better into deeper search, both minimax and MCTS [AVI §5.1–5.2].
  - Connect Four q alone was slightly better than z, and z→q falloff was best [OR4].
- **Risks.** The horizon effect [OR4]. Early-training targets near 0 are meaningless [OR4]. The deadly triad, though no divergence was observed [AVI §1, §6].
- **For us [INFERENCE].** The natural target is the root value of a depth-d (or fixed-time) αβ + threat search, run with the *current* weights, with t = (1−w_z)·v_search + w_z·z, where w_z falls from about 0.5 to about 0.1 across iterations. This is AVI with a deeper operator, or AlphaZero-style expert iteration with αβ as the expert. Proven wins and losses from the threat search are exact labels (see §3.6). Every exact-valued PV node can be added too, as a "Descent-lite" step.

### 3.6 Solver / oracle labels
- **Evidence.**
  - An oracle auxiliary loss improved Connect Four oracle consistency (match 0.785 → 0.849) and made Chomp 10×11 perfect, but it did not make Connect Four perfect [SPARSE Tables 1–2].
  - The supervised-on-solver upper bound is the best diagnostic for separating "the network cannot represent it" from "self-play did not teach it" [OR2].
  - All the Connect Four papers measure mistake rates against Pons [AVI §4.1; AZJL "Benchmark against a perfect solver"; SPARSE §3.3].
- **For us [INFERENCE].**
  - There is no solver for 8×12 six-in-a-row with destroy moves.
  - Our threat search (forced sequences of 5-threats, accounting for the opponent's available destroy) is a *partial* oracle. Every position it proves won or lost gets an exact label (±1, scaled by distance to mate as in OR2's strong mode). These should override any search or outcome target.
  - Deep offline searches, for example 10–30 s per position on a sample, give a "strong-search oracle" for evaluation sets, playing the role Pons plays in Connect Four papers.

### 3.7 Summary [INFERENCE, synthesised from §3.1–3.6]
The sources agree on four points.
1. Bootstrapped search values beat raw outcomes for value accuracy [AVI; MSB; OR4]. Outcomes help early and against horizon effects [OR4; KC §4.2].
2. Training on the positions the search actually evaluates is better than training on root positions only [KC; TDL §7; MSB tree learning].
3. Terminal rewards should prefer fast wins and slow losses [MSB reinforcement heuristic; OR2 strong mode].
4. Exact labels from a solver are valuable wherever available [SPARSE; OR2].

For a pattern-table evaluator inside αβ, the recommended target is therefore:

target = proven value if the threat solver proves the position, else
 (1 − w_z) · v_αβ(depth d, current weights) + w_z · z_length-scaled,

applied to the root and to exact PV nodes. The training data come from self-play with noisy openings and destroy-move exploration. As a comparison arm, a Java Descent generator produces tree-learned data.

---

## 4. Practical lessons

### 4.1 Exploration
- **Deterministic games need injected noise.** Suggested sources: random starting positions or stochastic move choice [TDG "Conclusion"]. Pure self-play in chess was much worse than online play (11 %) [KC §4.2]. Random starting boards were used in Kalah [IMB §4.1]; Othello learning starts from varied positions ("Choose-Start-01") and is evaluated from all 244 four-ply openings [FARL App. 0.A.6].
- **Values used in practice.**

  | Setting | Value | Source |
  |---|---|---|
  | AVI ε | 0.3 (Connect Four, Hex); 0.15 (larger move sets) | [AVI §4.5, App. C.2] |
  | TD-FARL ε | 0.1 → 0 (Connect Four); 0.2 (Othello, Hex) | [FARL App. 0.A.6] |
  | Descent | ordinal law with ε′ ∼ U[0,1] redrawn each move | [MSB §3.1.1] |
  | AlphaZero temperature | τ = 1 for 30 moves, then 0 | [OR3] |
  | | τ = 1 all game (better in Connect Four) | [OR3] |
  | | temperature threshold 10 best on 6×6 Othello | [SWEEP §5] |
  | | 1.0 → 0.3 between moves 20 and 30 | [AZJL] |
  | | T_opening = 4 with τ = 100 | [VIT Table A2] |
  | Dirichlet α | ≈ 10/n heuristic; α = 1.0 best in Connect Four; ε = 0.25 | [OR3, OR6; AZJL] |

- **Measure diversity.** AZJL's "redundancy" = 1 − u/n (u unique states, n states seen) diagnoses missing randomisation [AZJL "Initial Benchmarks"]. In Oracle's runs 30–50 % of the window was duplicates, and redundancy was high at c_puct = 1 [OR6].
- **Watch for over-deterministic self-play.** More in-training MCTS iterations sometimes *lowered* strength, possibly from too little exploration [SK §IV-E, footnote 7].
- **Sparse critical decisions.** AlphaZero failed at the very first Connect Four move (first failure ≈ ply 0.87) [SPARSE §5.1]. ε-greedy "may miss sparse strategies" [AVI §6].
- **[INFERENCE] For us.** The destroy move is a sparse, high-impact action that is available at most once per 10 own turns. Self-play must deliberately sample positions where destroy is available or recently used, for both sides. Concretely:
  - Force a random destroy with probability p when one is legal.
  - Start some games from mid-game positions with randomised cooldowns.
  - Otherwise the tables never learn the defensive value of holding a destroy in reserve, or the threat value of the opponent's being on cooldown. This is the rare-term problem [CPW-TD "Quotes", Beal].

### 4.2 Replay buffer and data handling
- **Sizes in use.**

  | Source | Buffer / window | Training per iteration |
  |---|---|---|
  | [AVI Table 3] | 1 M (circular) | 4 epochs |
  | [AZJL] | 400 K → 1 M | — |
  | [VIT Table A1] | 100 k states | — |
  | [MSB §2.4] | last 100 matches + smooth replay | — |
  | [SWEEP] | 40 iterations best for Elo | — |
  | [OR6] | 20 generations | 2 epochs |

- **Retire early data quickly.** A slow-growing window (4 → 20 generations) lets the worst early data leave sooner. Oracle saw a jump in performance exactly when the oldest data left the window [OR6 "Slow Window"]. AZJL grows its buffer for the same reason; AVI's appendix explains the reasoning [AVI App. B].
- **Deduplicate and average.** Average the targets of identical positions [OR3, OR6; AZJL `use_position_averaging`; AVI App. B]. Openings are otherwise heavily over-represented [OR3].
- **Gate new networks or not.** Gating thresholds of 0.6 [SWEEP; CX] and 0.05 score margin [AZJL] have been used. AVI and Athénan train continuously without gating [AVI Alg. 1; MSB Alg. 3].

### 4.3 Symmetry augmentation
- **Connect Four: one mirror.** Implemented as averaging V over the original and the mirrored board [AVI App. C.1], as augmentation [VIT App. B; AZJL `use_symmetries`], as random flips at play time for diversity [AZJL `flip_probability`], or inside the n-tuple sum over S(s) [FARL eq. 2].
- **[INFERENCE] For us.** Gravity removes up–down symmetry. Left–right mirroring is valid on 8×12, provided the destroy move is symmetric (a 3×3 block is). Colour symmetry is handled by the negamax side-to-move encoding, but the destroy cooldowns of the two players must be fed in from the mover's point of view.

### 4.4 Overfitting and model size
- A retrain window of 1 iteration gives the lowest loss but worse Elo, i.e. overfitting [SWEEP §5].
- Single-game models declined after their peak [VIT §5.2]. Too many gradient steps per iteration destabilised AVI on F-Games, so it was cut to 1 epoch [AVI App. C.3].
- Bigger networks learnt better in Oracle's Connect Four [OR5]. But CNN inductive bias beats raw parameter count [VIT §7].
- **[INFERENCE] For n-tuple tables.**
  - Most weights are never touched: only 8 % are active in Connect Four [SK Table I].
  - Unvisited entries stay 0, which acts as an implicit prior. Weight decay or TCL keeps rarely seen entries from being driven by a handful of samples.

### 4.5 Evaluation methodology
- **Paired openings.** Play every opening from both sides [AVI §4.3]. Examples: all 406 four-ply Connect Four openings [AVI Table 1]; Othello's 244 four-ply starts [FARL App. 0.A.6].
- **Statistics.** Report confidence intervals: Student-t over seeds [AVI §4.6], percentile bootstrap Elo [VIT App. F], Bradley–Terry anchored to a fixed reference [AGENTS §4.1].
- **Oracle metrics** where possible: value MAE, policy regret, error rate against the oracle, and blunder rate (a guaranteed win turned into a guaranteed loss) [AVI §4.2]. Oracle-match rate, longest consistent chain, first failure ply [SPARSE §4]. Mistake rate by game phase and difficulty bucket [AZJL; AVI App. D.3].
- **Deterministic players repeat games.** Add a small temperature [AZJL]; use 20 differently seeded n-tuple agents and 40 runs against Edax [SK §IV-A]; 4 games per matchup [AGENTS §4].
- **Report network-only and with-search strength separately.** The search adds a lot: Oracle errors fell about 29 % (strong) and about 80 % (weak) [OR2]; TCL-wrap gained about 17 points of total win rate [SK Table II].
- **Use more than one opponent type.** A perfect solver does not exploit an imperfect opponent the way self-play agents do [AGENTS §5.1]. Agents trained against near-perfect self-play can lose to "surprising" MCTS moves [SK §IV-B "Interpretation"]. Odd and even search depths behave differently [EVO §III].

### 4.6 Pitfalls reported
1. **No FARL means collapse** in player-centred TD. Without it the loser's last state never gets its reward [FARL §3.2, §3.5].
2. **Corrupted z** from opponents who fail to convert; oscillating losses [SPARSE §5.1, §7].
3. **Horizon effect** in search-value targets [OR4].
4. **Learning to predict opponent blunders.** KnightCap zeroed positive temporal differences unless it had predicted the move [KC §4.1].
5. **Cause–effect misattribution.** Mobility became inflated; rare features learn slowly [CPW-TD "Quotes"].
6. **Starting point matters**: material-only initialisation gained +500, all-equal initialisation +300 in 1,000+ games [KC §4.2].
7. **Narrow deterministic self-play** [TDG; KC §4.2].
8. **INT8 precision** degrades the value head; 32 head filters fixed it [OR5].
9. **Weak implementations**: Python AlphaZero builds often fail to beat a depth-2 minimax on one desktop [AZJL footnote 1]. One coding agent claimed convergence by reasoning that Connect Four is a draw, which is false [AGENTS §5.3.1].
10. **Hyper-parameters interact**. Scaling a setup down "is not trivial" [AZJL "You can do better!"]. More simulations or iterations can peak and then fall [SWEEP §5].
11. **Policy prior plus weak value**: AlphaZero leans on policy-guided search to make up for an inaccurate value [AVI §5.2]. That matters for us, because we will have no policy head inside αβ.
12. **Search beats evaluation**: KnightCap with learnt weights was still "crushed" by deeper-searching programs [KC §4.1]. **[INFERENCE]** Search speed in Java matters as much as evaluation quality, which is a strong argument for O(1) incremental pattern tables over networks.

---

## 5. Recommended training pipeline for Quantum Connect

Everything in this section is **[INFERENCE]** unless it cites a source. Effort figures assume one developer who already has the Java rules engine.

### 5.0 Design decisions and the reasons for them
- **Evaluator: pattern tables (n-tuple style), optionally with a tiny MLP head. No convolutional network inside αβ.**
  - In Java, a pattern lookup costs nanoseconds. TCL-base takes 0.05 ms per *move decision* in GBG Java Connect Four, and a decision evaluates up to 7 afterstates [SK Table IV].
  - A 5×64 ResNet on 96 cells costs about 5 blocks × 2 convolutions × 96 × (64·64·9) ≈ 35 M multiply-adds per evaluation, roughly 15–40 ms in scalar Java. That allows only a handful of nodes in 100 ms.
  - AVI's 256-wide residual MLP costs about 1 M multiply-adds, around 0.5–1 ms per evaluation: usable for 1–2 ply but not for deep αβ.
  - Evidence that pattern tables suffice: near-perfect Connect Four [FARL Table 1; SK Table II] and master-level linear chess evaluation inside αβ [KC]. The data-efficiency evidence for value-only methods comes from [MSB, AVI].
- **Learning algorithm: expert iteration with our own αβ + threat search as the expert, plus value-only targets (search value mixed with outcome, proven values from the threat solver), seeded by TD-FARL.** Descent-generated tree data is the main experiment to try against it.
  - Rationale: §3.7. MCTS in training is not justified at our budget [SK §IV-E; MSB].
- **Where the GPU helps.**
  1. Fast batch regression of millions of (features → target) rows, with many epochs and proper validation.
  2. Optional larger feature sets and an MLP head trained with Adam and large batches.
  3. A teacher/oracle network that is too slow for Java but can label positions offline. Not required.
  - 8 GB of VRAM is far more than any of this needs. Even 10 M float32 weights are 40 MB.

### 5.1 Step 1: Java engine prerequisites (≈ 3–5 days, if not done)
- Bitboard or array engine with make/unmake, including destroy + gravity + "any 6-line after a move = mover wins". This is unusual: a destroy can create a line for *either* colour, and the mover still wins. The rule needs exhaustive tests.
- Destroy cooldown counters in the state, Zobrist hash including cooldowns.
- αβ/PVS with iterative deepening, a transposition table (bound flags), and killer/history move ordering.
- Threat search: immediate win and block, double threats, and the special treatment that a pending opponent destroy can break a five.
- A fixed-node-count or fixed-depth mode for reproducible data generation.
- **Decisive and anti-decisive moves first.** EVO measured +60 % for decisive moves on top of MCTS [EVO §II]; [IMB §4.2] reports similar effects on playout policies.

### 5.2 Step 2: Feature design for the pattern tables (≈ 2–3 days)
- **Line windows.** All length-6 windows on 8×12: 56 horizontal (8 × 7), 36 vertical (12 × 3), 21 + 21 diagonal (3 × 7 each) = **134 windows**.
- **Cell alphabet.** Following Konen's Connect Four coding [SK Fig. 3b]: own, opponent, empty-playable-now, empty-not-yet-reachable. That is 4 states, so 4⁶ = 4,096 entries per window. The 3-state version has 729 entries.
- **Weight sharing.**
  - Option A: separate tables per window. 134 × 4,096 = 549 k weights, about 1.1 MB as int16.
  - Option B, **size-generic**: share tables by direction × row band (bottom, low, mid, top). About 16 tables × 4,096 = 65 k weights. It can be evaluated on any board size, as the brief requires.
  - Start with B, and check whether A is measurably stronger on 8×12.
- **Extra terms.**
  - Destroy cooldowns for both players (0..9 each, from the mover's view) as a small table, and an interaction "own five-threat × opponent destroy available".
  - Game phase, e.g. bucketed piece count. KnightCap used separate weight sets per stage [KC §4.1].
- **Optional random-walk n-tuples** [SK §II-C]: for example 64 tuples of 7 cells with m = 4, 16 k entries each, about 1 M weights. They catch non-line shapes such as "under-threats" (playable vs not-yet-playable cells).
- **Output.** v = tanh(Σ weights), or a sigmoid giving a win probability, in negamax convention.
- **Symmetry.** Sum over the original and the left–right mirror, normalised by 1/(m·N_S) [FARL eq. 3], or mirror-augment the data.
- **Incremental update in Java.** A drop changes one cell, affecting at most about 21 windows. A destroy changes up to 9 cells and shifts columns, so recompute fully (134 lookups, under 1 µs).

### 5.3 Step 3: Bootstrap with TD-FARL in Java on CPU (≈ 2–3 days to build; hours to overnight to run)
- Port the TD-FARL episode loop: player-centred afterstates, a FARL final step for the loser, terminal states pushed to 0 [FARL Alg. 2, §2.7]. Add TCL-EXP per-weight rates (β = 2.7) [FARL App. 0.A.5–0.A.6].
- Connect Four settings to start from: α ≈ 3.7 with TCL, λ = 0, ε 0.1 → 0, millions of episodes [FARL App. 0.A.6; SK §III-B].
- Add the destroy exploration from §4.1 and random 2–6-ply openings [TDG "Conclusion"].
- **Expected cost.** Connect Four took about 3.4 h per agent per core for 6 M episodes [SK §IV-D]. Our games are longer, the board is 2.3× larger, and there are more windows: assume 3–10× per episode, so overnight on 8–16 cores with parallel episodes and periodic weight merging, or one agent per core with the best kept.
- **Deliverable.** Weights v0, and an αβ bot using v0. Measure v0 against the hand-written evaluation: paired openings, ≥400 games, 95 % confidence interval.

### 5.4 Step 4: Self-play data generation with the αβ expert (≈ 2–3 days to build)
- For each game: a random opening (2–6 plies, plus random cooldowns in about 20 % of games), then moves chosen by αβ at a **fixed node budget** with stochastic selection among moves within δ of the best score, or by ε-greedy (ε ≈ 0.1–0.3 [AVI §4.5]). Forced destroy exploration as in §4.1.
- Logged per searched position:
  - Features or the raw state; side to move; cooldowns; ply.
  - v_root: the αβ root value at the last completed depth.
  - The PV leaf state (for a TDLeaf variant) and every PV node with an exact value (for "Descent-lite").
  - Any proven win or loss from the threat search, with distance.
  - At game end: z, scaled by length as a win value 1 − c·plies [OR2; MSB reinforcement heuristic].
- **Throughput estimate.** At 10–20 ms per move and 60–90 plies per game, about 1–2 s per game per core, so 8 cores give about 15–30 k games/h ≈ 1–2 M positions/h. That exceeds Oracle's 140–220 k positions per generation [OR5] and AZJL's 175 k per iteration [AZJL] by about 10×.
- **Format.** Compact binary (state bitboards + floats), one file per iteration.

### 5.5 Step 5: GPU training (PyTorch, ≈ 2 days to build)
- **Model.** `nn.EmbeddingBag` (sum) over the active table indices, i.e. a sparse linear model identical to the Java evaluator, then tanh. Optionally a 2-layer MLP (32–64 hidden units) over per-direction table sums: Java can afford about 4 k multiply-adds per node.
- **Target.** t = proven value if available, else (1 − w_z)·v_search + w_z·z_scaled.
  - w_z: 0.5 in iteration 1, falling linearly to about 0.1 by iteration 10. This mirrors Oracle's z → q falloff [OR4]. Our v_search is from αβ instead of MCTS.
  - Optional TDLeaf term: fit V(PV leaf of x_t) toward v_root(x_{t+1}) [KC Fig. 3].
- **Loss and optimiser.** MSE on tanh output [AVI eq. 5], Adam lr 1e-3 to 3e-4 [AVI Table 3; AZJL], large batches (4–16 k). Sparse weight decay or L2 10⁻⁴ [AZJL]. 2–4 epochs over the window [OR6; AVI Table 3].
- **Data handling.** Mirror augmentation; position dedup with target averaging [OR3, OR6]; window of the last ~5 iterations growing to ~20 [OR6].
- **Validation.**
  - Held-out positions, especially threat-solver-proven ones: sign accuracy and MAE.
  - A fixed "strong-search oracle" set of 2–5 k positions labelled by deep offline search (10–30 s each). This is our stand-in for AVI's oracle MAE and regret metrics [AVI §4.2] and for Oracle's supervised upper bound [OR2]: train the same model on these labels to learn the representation's ceiling.
- **Cost.** Seconds to minutes per iteration on 8 GB. The GPU is not the bottleneck; CPU self-play is.

### 5.6 Step 6: Export into Java (≈ 1 day)
- **Quantise.** int16 fixed point: scale weights so that the maximum |Σ| fits in int32 accumulation. Check that the quantised network matches the float one (MAE and win-rate parity). Oracle's INT8 experience is the warning [OR5].
- **Embed.** A JVM method is limited to 64 KB of bytecode, so `static final short[] W = {...}` fails for large tables. Generate a Java source file instead, with the weights as Base64 string constants (each literal ≤ 65,535 bytes of modified UTF-8, so split into chunks), and decode once at class initialisation with `java.util.Base64` and `ByteBuffer`. Standard library only. 65 k int16 weights ≈ 175 KB of Base64 (3 chunks); 549 k ≈ 1.5 MB.
- **Check the export.** Generate a test vector of (position → expected eval) in Python. A Java unit test asserts bit-exact equality after quantisation.

### 5.7 Step 7: Iterate and gate (ongoing; each iteration ≈ 1–3 h wall-clock)
Each iteration:
1. Export the weights.
2. Generate self-play with the new bot.
3. Retrain.
4. Run a match of new against previous best (and against a fixed anchor such as v0 or the hand-written evaluation) on a fixed set of paired openings, at the *real* 100 ms time control.
5. Accept if the 95 % CI is above 50 %, or use a lenient threshold [AZJL arena 0.05; SWEEP 0.6].

Further guidance:
- Track redundancy [AZJL], the fraction of games using destroy, game-length distribution, the proven-label fraction and validation MAE.
- Keep an anchored Bradley–Terry ladder of all accepted versions [AGENTS §4.1].
- Expect 10–40 iterations, by analogy with Oracle's 40 generations after tuning [OR6] and AZJL's 15 iterations.

### 5.8 Step 8 (experiment arm): Java Descent data generator (≈ 3–5 days)
- Implement Descent with the pattern evaluation, a transposition table, terminal values from the rules plus a depth-scaled reward, and ordinal-law-style move selection (pseudo-code in §1.2 of this digest; [MSB §2.1, §2.4]). Record (s, v(s)) for every tree node [MSB Alg. 3].
- **A/B test.** Same CPU hours. Data from αβ expert iteration vs data from Descent, the same trainer, evaluated with αβ at 100 ms. Metrics: match score, oracle-set MAE, proven-position sign accuracy.
- The source predicts a large advantage for Descent over outcome learning [MSB §3.3, §4]. Whether it beats αβ-search-value distillation is untested (see §6).
- Optionally also try **UBFM_s** as the play-time search at 100 ms against αβ. The source reports it beat αβ in head-to-head play [MSB §2.1].

### 5.9 Step 9 (optional): small-network variants, only if tables plateau
- Train a small CNN value network (for example 6×64; with value-only targets, AVI-style) on the GPU, as an *offline teacher*. Distill its values into the pattern tables, or use it to label positions. Use it at play time only if a 1–2 ply root re-rank fits inside 100 ms.
- The source basis for the gain from bigger models: [OR5; VIT §5.3]. Keep the Java αβ fast [KC §4.1].

### 5.10 Effort summary [INFERENCE]

| Block | Build effort | Compute |
|---|---|---|
| Engine + αβ + threat search + tests | 3–5 d (if not done) | — |
| Pattern features + incremental Java eval | 2–3 d | — |
| TD-FARL bootstrap | 2–3 d | overnight CPU |
| Self-play generator + logging | 2–3 d | ~1–2 M positions/h on 8 cores |
| PyTorch trainer + validation sets | 2 d | minutes per iteration on 8 GB GPU |
| Exporter + Java loader + parity test | 1 d | — |
| Match harness (paired openings, BT ladder, CIs) | 1–2 d | 400–1,000 games per gate at 100 ms ≈ 1–2 h on 8 cores |
| Descent arm | 3–5 d | same as main loop |
| **Total to first trained bot** | **~2.5–3.5 weeks** | **~1–3 days of iteration** |

---

## 6. Open questions

1. **Search-distilled αβ targets vs Descent tree learning.** Which gives a stronger *αβ* player per CPU-hour? [MSB] compares Descent with Polygames and with MCTS-based tree learning [MSB §4], but not with αβ-value distillation (AVI-style with depth > 1). Is there a Descent-lite that harvests enough exact nodes from αβ?
2. **Play-time search.** At 100 ms, with a learned evaluation and destroy moves (branching factor up to 12 + destroy targets), does UBFM_s beat αβ + threat search, as it did in [MSB §2.1]'s earlier experiments?
3. **Size-generic features.** How much strength do direction/row-band-shared tables give up against per-window tables on 8×12? Does training on smaller boards transfer to larger ones? It did for Gomoku and Othello but not Connect Four [VIT §5.4].
4. **Representing destroy.** Can additive tables capture the interaction "threat × opponent destroy availability", or is a tiny non-linear head needed? What is the right exploration rate for destroy in self-play?
5. **Rules affecting labels.** If a destroy creates an opponent 6-line, the mover still wins. How often does that happen? Does it make the value function non-smooth enough to need explicit tactical features rather than learning? Also unconfirmed: whether overlines (≥7) count, and whether a full board is a draw.
6. **Target mix schedule.** The best w_z falloff and the TD/λ setting for our game are unknown. Oracle's 20-generation falloff [OR4] and KnightCap's λ = 0.7 [KC §4.1] are only starting points.
7. **Oracle quality.** Without a solver, how reliable are deep-search labels as an evaluation oracle? How much do threat-solver-proven labels improve consistency? [SPARSE] shows oracle labels help but do not guarantee perfection.
8. **Deliverable size limits.** Is there a cap on the size of the submitted Java source or jar? That decides between 65 k shared weights and 0.5–1 M+ per-window or n-tuple weights.
9. **Self-play narrowness.** Is opening randomisation plus ε enough for a deterministic game [KC §4.2; TDG], or do we need a population of past versions? Polygames uses a pool of networks to avoid catastrophic forgetting [MSB §2.1].
10. **Unread primary sources.** Several important details live in documents that are not in our corpus: Cohen-Solal 2020/2026 (ordinal law, smooth experience replay, Descent completeness), the [MSB] technical appendix, and Veness et al. 2009 (bootstrapping from game-tree search, cited in [AVI §2]). They are worth fetching before Step 8.
