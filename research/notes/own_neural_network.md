# Training our own neural network for Quantum Connect (8x12, 100 ms, pure-Java inference)

Scope note: these notes cover learned evaluation and policy options only (AlphaZero-lite, NNUE, distillation, TD/Texel/n-tuple, policy-only). Numbers marked "inference" or "estimate" are my own arithmetic or reasoning and are not quoted from a source. Medium (Oracle "Lessons from AlphaZero") returned HTTP 403 to direct fetch, so those numbers come from search-engine excerpts of the original posts.

## Q1. Options (a)-(e): what evidence exists for each in Connect Four / Othello / Gomoku-like games?

### Takeaway
The strongest cheap evidence is for (d): TD-trained n-tuple networks with a small search wrapper. In Connect Four they train in a few CPU-hours, evaluate in about 0.05 ms, and with a 100-1000-iteration MCTS wrapper (3-30 ms) they beat their alpha-beta and MCTS baselines. AlphaZero-style nets (a) do reach strong Connect Four play, but they cost tens of GPU-hours, still make long-horizon mistakes against a perfect solver, and lose to hybrids that add alpha-beta for tactics. NNUE (b) is the proven way to put a learned eval inside alpha-beta under tight time limits, but its training recipe needs a strong search engine to label data, so it is a second-stage upgrade on top of a classical bot. A policy net with no search (e) is consistently the weakest option.

### Cited Findings
**(a) AlphaZero-lite (value/policy net + MCTS)**
- Oracle Devs Connect Four AlphaZero: after tuning, training took about 40 model generations instead of about 150, cutting compute from 77 GPU-hours to 21 GPU-hours. At about 12 min per generation the untuned run took more than a day. — [Oracle Devs, Lessons from AlphaZero part 6](https://medium.com/oracledevs/lessons-from-alpha-zero-part-6-hyperparameter-tuning-b1cfcbe4ca9a) (search excerpt; direct fetch 403)
- The first Oracle implementation was so slow that training "would have taken weeks" on one GPU machine. It needed remote batched GPU inference and reworked parallel MCTS to keep the GPU busy. — [Oracle Devs part 5](https://medium.com/oracledevs/lessons-from-alpha-zero-part-5-performance-optimization-664b38dc509e) (search excerpt)
- AlphaZero.jl Connect Four (6x7): 15 iterations x 5,000 self-play games, 600 MCTS simulations per move, 128 parallel workers. Each iteration took 1-2 h on an i5-9600K + RTX 2070, so about 15-30 h in total (that total is the fetch summary's estimate). The ResNet had 5 blocks, 128 filters and about 1.6M parameters. The agent "rarely makes mistakes detectable within 14 moves" but is still imperfect at longer-term strategy. The raw network alone starts at 0 wins and ends up significantly stronger than a depth-5 minimax baseline. — [AlphaZero.jl Connect Four tutorial](https://jonathan-laurent.github.io/AlphaZero.jl/stable/tutorial/connect_four/)
- Vanilla AlphaZero on Connect Four "plays strongly" but "loses the optimal line of play" when checked against an exact solver. Adding oracle-derived policy supervision (AZAL) raises the oracle-match rate and delays the first mistake, but still does not give perfect play. — [Kong, Ram, Yu, arXiv 2607.08984 (2026)](https://arxiv.org/abs/2607.08984)
- Kaggle ConnectX: AlphaZero was the authors' best agent and placed 9th of 225 teams. Kaggle score was 1282, against 1075 for plain MCTS (about 20k simulations per move), 980 for an MCTS-minimax hybrid and 873 for depth-5 minimax. Kaggle allows 5 s per move. The minimax used a simple run-length heuristic (weights 1000/2000), so it is a weak baseline. — [Reinforcement Learning for ConnectX, arXiv 2210.08263](https://ar5iv.labs.arxiv.org/html/2210.08263)
- A Connect4 "Two-Phase-Win" strategy that combines AlphaZero's positional judgement with minimax/alpha-beta for "partial solutions" (tactics) won 58% against plain AlphaZero and did not lose any of 100 games. The authors write that AlphaZero is good at "overall situations" and minimax is adept at finding tactics. — [NTNU, Two-phase win strategy for improving AlphaZero's strength](https://scholar.lib.ntnu.edu.tw/zh/publications/two-phase-win-strategy-for-improving-the-alphazeros-strength-2/) (search excerpt)
- alpha-zero-general reference: 6x6 Othello, 80 iterations x 100 episodes, 25 MCTS simulations, about 3 days on a Tesla K80. — [alpha-zero-general README (via search)](https://github.com/tonyxia2016/alpha-zero-general); hyperparameter sweep on the same codebase: [Wang et al., arXiv 1903.08129](https://arxiv.org/pdf/1903.08129)
- A 2026 paper reports that simple self-play Approximate Value Iteration (AVI) with one-step greedy policies gives more accurate value functions than AlphaZero on Connect Four and Hex 7x7 (checked against ground-truth oracles). Its greedy policies stay competitive with MCTS policies "at lower training and inference cost". A search snippet mentions about 2 GPU-hours for Connect Four, but I could not confirm that figure from the abstract. — [The Surprising Effectiveness of Approximate Value Iteration in Self-Play, arXiv 2609.09094](https://arxiv.org/abs/2609.09094)

**(b) NNUE for alpha-beta**
- NNUE's speed comes from sparse inputs (about 0.1% active in current chess nets) and few feature changes per move (at most 4, usually fewer than 3), so the first-layer "accumulator" is updated by adding and subtracting weight columns instead of being recomputed. A move that changes every feature (a king move in HalfKP) forces a full refresh. — [nnue-pytorch docs/nnue.md](https://raw.githubusercontent.com/official-stockfish/nnue-pytorch/refs/heads/master/docs/nnue.md)
- The nets are shallow (2-4 layers, with most knowledge in the first layer). Stockfish went from `256x2->32->32->1` to `1024x2->8->32->1`. The tutorial's toy example is `A[768]->8->8->1`. — [nnue-pytorch docs](https://raw.githubusercontent.com/official-stockfish/nnue-pytorch/refs/heads/master/docs/nnue.md)
- NNUE originated in Shogi (Yu Nasu, 2018, integrated into YaneuraOu) and was then ported to Stockfish. — [nnue-pytorch docs](https://raw.githubusercontent.com/official-stockfish/nnue-pytorch/refs/heads/master/docs/nnue.md); [chessprogramming.org Stockfish NNUE](https://chessprogramming.org/Stockfish_NNUE)
- Training targets are search evaluations passed through a sigmoid (scale about 400), blended with game results by a parameter lambda. The docs explicitly leave out dataset sizes. — [nnue-pytorch docs](https://raw.githubusercontent.com/official-stockfish/nnue-pytorch/refs/heads/master/docs/nnue.md)

**(c) Supervised distillation from a solver or strong engine**
- AZAL adds oracle (exact solver) policy supervision to AlphaZero on Connect Four. It improves oracle agreement but not to perfect play. — [arXiv 2607.08984](https://arxiv.org/abs/2607.08984)
- NNUE training is itself distillation: the net learns to predict a strong search's eval, mixed with game outcome by lambda. — [nnue-pytorch docs](https://raw.githubusercontent.com/official-stockfish/nnue-pytorch/refs/heads/master/docs/nnue.md)

**(d) Tabular/linear evaluation tuned by TD or Texel**
- Connect Four n-tuple network: 140 tuples (2x70) of 8 cells, 4 values per cell, 9,175,040 weights of which 8% are ever active. It was trained by TD with TCL (adaptive learning rates) for 6,000,000 self-play episodes, and 10 agents took 1.4 days on a single laptop CPU (i7-9850H, no GPU), so about 3.4 h per agent. — [Scheiermann & Konen, AlphaZero-inspired game learning, arXiv 2204.13307](https://ar5iv.labs.arxiv.org/html/2204.13307)
- Move time: TCL-base (n-tuple, 1-ply greedy) takes about 0.05 ms per move. TCL-wrap (n-tuple + MCTS wrapper) takes about 3 ms at 100 iterations, 30 ms at 1,000 and 300 ms at 10,000. — [arXiv 2204.13307](https://ar5iv.labs.arxiv.org/html/2204.13307)
- Connect Four results (100 games, row agent moving first): TCL-wrap beat AB-DL 99/0/1, TCL-base 100/0/0 and MCTS 100/0/0. TCL-base without search beat AB-DL only 91/2/7. As second player, TCL-wrap won 97% against MCTS. Overall win rates were TCL-wrap 66.3%, AB-DL 64.9%, TCL-base 49.0%, MCTS 19.8%. The wrapper passes 80% win rate at 50-100 iterations and approaches 100% at 500 or more. AB/AB-DL are the alpha-beta agents from Thill et al. (CIG 2014). — [arXiv 2204.13307](https://ar5iv.labs.arxiv.org/html/2204.13307)
- Othello, same method: 200 tuples of 7 cells (3.28M weights), 250k episodes, about 1.8 h per agent on CPU. TCL-wrap with 10k MCTS iterations beats Edax up to level 7. Without the wrapper, the n-tuple agent cannot beat Edax level 2. — [arXiv 2204.13307](https://ar5iv.labs.arxiv.org/html/2204.13307)
- Background references: TD n-tuple for Connect-4 in [Thill, Bagheri, Koch, Konen, "Temporal difference learning with eligibility traces for the game Connect-4", CIG 2014] and TCL in [Bagheri et al., IEEE TCIAIG 2015]. Both are cited inside [arXiv 2204.13307](https://ar5iv.labs.arxiv.org/html/2204.13307); I did not fetch them directly.
- Texel's tuning method fits eval weights by logistic regression: minimize the squared error between the game result and sigmoid(K * qsearch score). The author used about 64,000 fast games, about 8.8M positions, for about 400 parameters, and reported cumulative gains of about +99.6 Elo across tuning rounds (32k-game tests). He is unsure how much of that reflects a previously weak eval. — [chessprogramming.org, Texel's Tuning Method](https://www.chessprogramming.org/Texel%27s_Tuning_Method)
- Logistello (Othello) used game-stage-dependent pattern tables (edges, corners, etc.) fitted by regression on labelled positions and combined with Multi-ProbCut alpha-beta. It beat the human world champion Murakami 6-0 in 1997. — [Buro's Logistello page](https://webdocs.cs.ualberta.ca/~mburo/log.html); [chessprogramming.org ProbCut](https://chessprogramming.org/ProbCut); [Buro JAIR 1995](https://jair.org/index.php/jair/article/download/10146/24043/18479)

**(e) Pure policy/value net with no search**
- The AlphaZero.jl network alone beats depth-5 minimax, but the full MCTS agent is far stronger. — [AlphaZero.jl tutorial](https://jonathan-laurent.github.io/AlphaZero.jl/stable/tutorial/connect_four/)
- The n-tuple net with no search (TCL-base) is clearly weaker than the same net with a 1,000-iteration wrapper: it loses 100% of games as first player against TCL-wrap, and in Othello it cannot beat Edax level 2 while TCL-wrap beats level 7. — [arXiv 2204.13307](https://ar5iv.labs.arxiv.org/html/2204.13307)

### Inferences
- For our constraints the evidence ranks the options: (d) n-tuple/pattern eval with TD (or Texel) inside the classical alpha-beta ≈ (b) a tiny NNUE inside alpha-beta > (a) AlphaZero-lite with MCTS > (e) policy-only. (c) distillation is a training method rather than a separate architecture: it is how you would train (b) or (d) using our own deep alpha-beta as the teacher.
- Every strong data point combines a learned eval with search. Search is non-negotiable in a game where a single missed 4-in-a-row ends the game.
- The n-tuple + TD route is the only published pipeline that is both CPU-only and needs hours rather than days. It also inherently uses only table lookups and additions at inference, which is ideal for pure Java.

### Gaps
- No published Gomoku or Connect-Four-variant data on boards as large as 8x12 with a "destroy" move. All Connect Four numbers are for 6x7.
- I could not fetch the Oracle Medium posts directly, so the network size and the exact "accuracy vs solver" percentages of the Oracle runs are unverified (only GPU-hours and generation counts came through excerpts).
- AlphaZero.jl's error rates against the perfect solver are only in plots, so I have no exact numbers.

## Q2. What net size can run N times within 100 ms in pure Java on CPU? Vector API, int8/float tricks, jar size

### Takeaway
A ResNet of the size used in published Connect Four AlphaZero runs (about 1.6M parameters, 128 filters) costs about 0.25 GFLOP per evaluation on an 8x12 board, so pure Java manages only about 1-5 evaluations per move: useless for search. Usable sizes are an MLP with a few hundred thousand weights (about 1-3k evals per move), an NNUE-style net with incremental int16 updates (tens of thousands of evals per move), or n-tuple lookups (hundreds of thousands per move). The Vector API needs `--add-modules jdk.incubator.vector` at both compile and launch time, so treat it as unavailable. Integer arithmetic and element-wise (not reduction) loops are what HotSpot auto-vectorizes reliably.

### Cited Findings
- JDK 25 ships the Vector API as the 10th incubator (JEP 508) in module `jdk.incubator.vector`. It is not final and not a preview feature, and it must be enabled with `--add-modules jdk.incubator.vector` on both `javac` and `java` (the JVM prints an "incubating module" warning). — [JEP 508](https://openjdk.org/jeps/508); [TheServerSide usage example](https://www.theserverside.com/blog/Coffee-Talk-Java-News-Stories-and-Opinions/Solve-the-prefix-sum-problem-with-SIMD-ops-and-Javas-Vector-API/)
- HotSpot C2 auto-vectorization (SuperWord) has historically restricted reductions. Float and double add/mul linear reductions (a dot-product `sum += a[i]*b[i]`) are constrained because Java requires strict left-to-right floating-point evaluation order, and on aarch64 float/double add reductions are excluded from auto-vectorization entirely. Ongoing work adds a cost model that enables reductions only where profitable. — [openjdk/jdk PR 27803](https://github.com/openjdk/jdk/pull/27803); [JDK-8078563](https://bugs.openjdk.org/browse/JDK-8078563)
- C2 still emits AVX instructions for scalar float operations even without SuperWord. — [OpenJDK discuss mailing list](https://mail.openjdk.org/pipermail/discuss/2023-August/006220.html)
- The NNUE quantization recipe: int16 accumulator with feature-transformer weights scaled by 127, ClippedReLU to int8 range 0..127, int8 weights in later layers with int32 accumulation, weight scale 64 (float range about ±1.98, step 1/64), and power-of-two scales so divisions become shifts. Weights must be clamped during training. — [nnue-pytorch docs](https://raw.githubusercontent.com/official-stockfish/nnue-pytorch/refs/heads/master/docs/nnue.md)
- Stockfish's `256x2->32->32->1` HalfKP net has about 10M first-layer parameters, about 20 MB after quantization. — [nnue-pytorch docs](https://raw.githubusercontent.com/official-stockfish/nnue-pytorch/refs/heads/master/docs/nnue.md)
- The Connect Four n-tuple net has 9.18M weights, but only 8% are ever active. Inference takes 0.05 ms per move (1-ply). — [arXiv 2204.13307](https://ar5iv.labs.arxiv.org/html/2204.13307)
- The AlphaZero.jl Connect Four net is 5 residual blocks x 128 filters, 3x3 kernels, about 1.6M parameters. — [AlphaZero.jl tutorial](https://jonathan-laurent.github.io/AlphaZero.jl/stable/tutorial/connect_four/)

### Inferences
These are my own estimates and must be benchmarked on the target JVM.
- **Time budget:** take about 60-70 ms of usable compute from the 100 ms hard limit, keeping about 30% margin for GC, JIT and unknown server speed.
- **Assumed Java throughput:** about 1-3 GFLOP/s single-threaded for scalar float code, and maybe 5-15 GFLOP/s for well-shaped element-wise loops that C2 vectorizes. Multithreading is risky on unknown hardware.
- **Cost of the AlphaZero.jl net on 8x12:** one 3x3 conv with 128 to 128 channels is 147k MACs per cell, times 96 cells = 14M MACs. Ten convs make about 140M MACs, about 0.28 GFLOP per eval, which is about 20-280 ms per eval in Java. That rules it out.
- **"Lite" CNN (4 blocks x 32 filters):** about 9.2k MACs per cell x 96 cells x 8 convs ≈ 7M MACs ≈ 14 MFLOP. That is about 1-14 ms per eval, so only 5-60 evals per move. MCTS needs hundreds or more, so it is still too slow.
- **MLP (288 one-hot inputs -> 256 -> 32 -> 1):** about 82k MACs. Exploiting one-hot inputs (sum only the 96 active rows) brings it to about 25k MACs, roughly 10-30 µs, so about 2-6k evals per move.
- **NNUE-style (96 cells x 3 states x 2 perspectives -> 256 int16 accumulator -> 32 -> 1):** one ordinary disc drop changes 1-2 features, so the update is 1-2 adds of a 256-element short[] row (element-wise, vectorizable, no reduction). The output layer is about 8k int MACs. Roughly 2-5 µs per node, so 10-30k evals per move. This is the realistic way to put a net inside alpha-beta at modest depth.
- **n-tuple:** about 100-140 table lookups per position, which is cheapest of all (0.05 ms per 1-ply move with about 12 children in the paper, i.e. about 4 µs per position).
- **Java coding tricks:** store weights as `short[]`/`byte[]` and accumulate in `int` (integer reductions are associative and so not blocked by the float-ordering rule), lay out accumulator updates as `acc[j] += W[f][j]` loops over j (no reduction), avoid allocation in the search loop, and use flat arrays, not `float[][]`.
- **Vector API:** if the tournament runs `java -jar bot.jar` or loads our class inside its own JVM, we cannot add `--add-modules`, and referencing `jdk.incubator.vector` would fail at class-load time. As far as I know, executable-JAR manifests support `Add-Exports` and `Add-Opens` but not `Add-Modules`. That is unverified, so test it.
- **Jar size:** float32 costs 4 bytes per weight. A 9M-weight n-tuple net is about 37 MB raw. 8x12 needs 4^k entries per k-tuple, so tuple length 6-7 keeps tables at 4-16k entries each, and storing int8/int16 plus deflating inside the jar (most entries are zero, only 8% active) brings it to low MB. A 256-wide NNUE over 576 features is about 150k int16 ≈ 300 KB. Load weights from a classpath resource (`getResourceAsStream`) rather than as Java array literals, because large static initializers hit the 64 KB method-size limit.

### Gaps
- No published benchmark of hand-written NN inference GFLOP/s in plain Java (without Vector API) on recent JDKs. All throughput numbers above are estimates.
- The jar-size limit and launch flags of the Quantum Connect tournament server are unknown and must be asked of the organizers.
- I did not verify which JDK version the server runs, or whether C2 is enabled (some sandboxes use `-XX:TieredStopAtLevel=1`, which would remove most auto-vectorization).

## Q3. Training budget: published numbers for Connect Four runs, CPU-only setups, laptop feasibility, cloud cost

### Takeaway
Published 6x7 Connect Four AlphaZero runs need about 15-80 GPU-hours (21 GPU-h after heavy tuning, 77 GPU-h before). CPU-only TD n-tuple training reaches strong Connect Four play in about 3-4 CPU-hours per agent. On a laptop with Iris Xe, an AlphaZero-lite run for an 8x12 board is not realistic in days. An n-tuple/TD, Texel or NNUE-distillation pipeline driven by our own Java engine is realistic in hours to a couple of days.

### Cited Findings
- Oracle Connect Four AlphaZero: 77 GPU-h (about 150 generations) cut to 21 GPU-h (about 40 generations), about 12 min per generation. The 20-generation training window was 143,360 games, i.e. about 7,000 games per generation. — [Oracle Devs part 6](https://medium.com/oracledevs/lessons-from-alpha-zero-part-6-hyperparameter-tuning-b1cfcbe4ca9a) (search excerpt)
- AlphaZero.jl Connect Four: 15 iterations x 5,000 games x 600 simulations per move, 1-2 h per iteration on a consumer desktop with an RTX 2070, so about 15-30 h. — [AlphaZero.jl tutorial](https://jonathan-laurent.github.io/AlphaZero.jl/stable/tutorial/connect_four/)
- CPU-only: 6M TD self-play episodes for a Connect Four n-tuple agent, 1.4 days for 10 agents on one i7-9850H laptop CPU (about 3.4 h per agent). Othello takes about 1.8 h per agent. Putting MCTS inside training (100 iterations) improved Othello to 66% against Edax level 8 (against 34% without) but took about 2.5 days per agent, about 30x the compute. — [arXiv 2204.13307](https://ar5iv.labs.arxiv.org/html/2204.13307)
- alpha-zero-general 6x6 Othello: about 3 days on a Tesla K80. — [alpha-zero-general (via search)](https://github.com/tonyxia2016/alpha-zero-general)
- AVI self-play is reported as cheaper than AlphaZero to train on Connect Four, with a figure of about 2 GPU-hours from a search snippet that I did not verify. — [arXiv 2609.09094](https://arxiv.org/abs/2609.09094)
- Texel tuning uses about 64k fast games (1s+0.08s), about 8.8M positions, for about 400 parameters. — [chessprogramming.org Texel](https://www.chessprogramming.org/Texel%27s_Tuning_Method)

### Inferences
- **Scaling to 8x12:** 96 cells against 42 means games can last up to 96 plies rather than 42, and each MCTS or NN evaluation costs about 2.3x more per cell. The state space is vastly larger. Expect at least 3-10x the 6x7 AlphaZero budget, so roughly 60-500 GPU-hours. That is an order-of-magnitude guess, not sourced.
- **Laptop:** Iris Xe has no CUDA, and PyTorch on Intel iGPU (XPU/DirectML) is possible but slow and fragile. An AlphaZero-lite run on the laptop CPU would take weeks. In contrast, the TD n-tuple training loop is pure table updates and can be written in Java inside our own engine (no Python at all). The paper's figure of about 3-4 CPU-hours per Connect Four agent suggests about 1 day on the laptop for 8x12, with longer tuples and games.
- **Cloud:** at typical on-demand prices for a T4/L4/A10-class GPU (roughly $0.5-2 per hour; not sourced, check current provider pricing), 20-500 GPU-hours costs about $10-1,000. The real cost is engineering time: self-play has to run in Java (the engine) while training runs in Python, so we would need a bridge or a Java reimplementation of the MCTS-plus-batched-inference loop.
- **NNUE distillation budget:** label 1-10M positions with our alpha-beta at fixed depth (for example depth 8-12 on 8x12) on laptop cores. At about 10-50 ms per position that is about 3-140 CPU-hours, which can run in parallel overnight. Then train a 300 KB net in PyTorch on CPU for minutes to hours (tiny nets don't need a GPU).

### Gaps
- No published training budget for any Connect Four variant on a board larger than 7x6, or with a special move.
- Exact cloud prices were not fetched.
- I found no confirmed figure for the Oracle runs' final error rate against the perfect solver (the posts report it, but the fetch was blocked).

## Q4. Handling the special destroy move, the 8x12 board, input encoding, action space, augmentation

### Takeaway
Encode the board as planes (own discs, opponent discs, and optionally playable cells), plus scalar or one-hot inputs for side to move and each player's special-move cooldown and availability. Give the policy head 2 x 12 = 24 actions (normal drop and destroy drop per column) and mask illegal ones. Horizontal mirroring doubles the data for free if the destroy pattern is left-right symmetric. For NNUE, a destroy move changes many cells, so treat it like a "king move": a full accumulator refresh, which is cheap on 96 cells.

### Cited Findings
- AlphaZero on ConnectX handled variable board sizes by padding everything to 12x12 so the network input stays fixed. — [arXiv 2210.08263](https://ar5iv.labs.arxiv.org/html/2210.08263)
- NNUE assumes few feature changes per move. When a move changes many features (king move in HalfKP), Stockfish does a full accumulator refresh, which is costlier but acceptable if infrequent. — [nnue-pytorch docs](https://raw.githubusercontent.com/official-stockfish/nnue-pytorch/refs/heads/master/docs/nnue.md)
- The n-tuple Connect Four nets use 4 values per cell, i.e. empty / player 1 / player 2 / "reachable empty" (the next playable cell in its column). — [arXiv 2204.13307](https://ar5iv.labs.arxiv.org/html/2204.13307) (4 values per cell stated there; the meaning of the 4th state is from the Thill/Konen line of work as cited in that paper)
- AlphaZero Connect Four training windows contain about 30-50% duplicate positions that carry different targets from different generations, which affects augmentation and deduplication. — [Oracle Devs part 6](https://medium.com/oracledevs/lessons-from-alpha-zero-part-6-hyperparameter-tuning-b1cfcbe4ca9a) (search excerpt)

### Inferences
- **Inputs:** 96 cells x {own, opponent, empty-reachable} one-hot gives 288 binary features, or 384 with an explicit empty plane. Add side to move, our special-move cooldown one-hot (0..k), the opponent's cooldown one-hot, and, if the rules have it, a plane for "special used". Present the board from the perspective of the side to move to halve what the net must learn.
- **Action space:** 24 logits (12 normal + 12 special), with illegal and cooldown-blocked ones masked before the softmax.
- **Special move and gravity:** if discs fall after destruction, a destroy move changes up to a whole column segment per affected column. Recompute the accumulator from scratch: 96 cells x 256 int16 adds ≈ 25k adds ≈ a few µs. It is also tactically sharp (it can break the opponent's threat, or open one), which is another argument for keeping it in the alpha-beta search and not trusting a static eval to judge it.
- **n-tuples:** sample tuples by random walk on the 8x12 grid (as in the paper). Share weights between each tuple and its mirror image (symmetric n-tuples), which halves the weights and doubles the learning signal.
- **Augmentation:** horizontal mirroring (column c to column 11-c) is valid only if the destroy area is left-right symmetric around the drop point. Vertical flips are never valid (gravity). With a 30-50% duplicate rate, deduplicate (average the targets of duplicate positions) before training.
- **Variable board size:** if the tournament can change rows or columns (the brief says 8x12 "by default"), use either a fully convolutional net or n-tuples or NNUE features indexed per board size, or pad to a maximum size as in the ConnectX paper. Otherwise the learned eval silently breaks on a different board.

### Gaps
- The exact destroy-move rules (radius, whether gravity reapplies, cooldown length) were not available to me. The symmetry and encoding advice above assumes a symmetric 3x3-style area.
- No published work encodes a "bomb"/destroy action in a Connect Four net.

## Q5. Known pitfalls: overfitting to self-play, missing tactics without search, latency spikes, JVM JIT warm-up

### Takeaway
The documented pitfalls are: nets that are strong but not exact (they miss long tactical lines), self-play windows full of duplicated or stale data, and training cost dominated by plumbing and not by the net. For deployment, the risks are in the JVM: C2 compiles hot code only after thousands of invocations, and GC pauses. Our bot must use iterative deepening with a hard deadline check and must not allocate in search.

### Cited Findings
- Strong is not perfect: vanilla AlphaZero on Connect Four deviates from the optimal line, and even solver supervision does not fix it fully. — [arXiv 2607.08984](https://arxiv.org/abs/2607.08984)
- The AlphaZero.jl agent rarely errs within 14 moves of the end but is imperfect on longer-term strategy. — [AlphaZero.jl tutorial](https://jonathan-laurent.github.io/AlphaZero.jl/stable/tutorial/connect_four/)
- A minimax/alpha-beta tactical layer on top of AlphaZero won 58% against plain AlphaZero with no losses in 100 games, i.e. the net misses tactics that alpha-beta catches. — [NTNU Two-Phase-Win](https://scholar.lib.ntnu.edu.tw/zh/publications/two-phase-win-strategy-for-improving-the-alphazeros-strength-2/) (search excerpt)
- Self-play data: about 30-50% duplicates in a Connect Four window, with conflicting targets from different generations. The window size (20 generations, about 143k games, in Oracle's first version) was a key tuning knob. — [Oracle Devs part 6](https://medium.com/oracledevs/lessons-from-alpha-zero-part-6-hyperparameter-tuning-b1cfcbe4ca9a) (search excerpt)
- GPU starvation and uneven game lengths made the first Oracle pipeline "weeks" slow. — [Oracle Devs part 5](https://medium.com/oracledevs/lessons-from-alpha-zero-part-5-performance-optimization-664b38dc509e) (search excerpt)
- Texel/regression tuning pitfalls: the model learns correlation not causation, cannot learn knowledge the eval has no features for, finds local optima, and produces odd-looking weights that can cause rare errors. The scoring search must be deterministic (TT/history effects break it). — [chessprogramming.org Texel](https://www.chessprogramming.org/Texel%27s_Tuning_Method)
- NNUE quantization: weights must be clamped in training to fit int8/int16, and the accumulator must not overflow for any feature combination. Small layers near the output add little and add quantization error. — [nnue-pytorch docs](https://raw.githubusercontent.com/official-stockfish/nnue-pytorch/refs/heads/master/docs/nnue.md)
- JIT: OpenJDK benchmarks of reduction loops needed about 1.1-1.4 s of warm-up before steady state (old setup, tiered compilation off). Whether a float dot-product loop gets vectorized depends on C2's reduction rules. — [JDK-8078563](https://bugs.openjdk.org/browse/JDK-8078563); [PR 27803](https://github.com/openjdk/jdk/pull/27803)

### Inferences
- **JIT warm-up:** the first 1-3 moves will run in the interpreter or C1 code, several times slower. Mitigations: (1) run a dummy search (and NN evals) for 50-200 ms during bot construction or class loading, if the framework allows time before the first move; (2) make the search anytime (iterative deepening) so a slow JIT only reduces depth instead of causing a timeout; (3) check `System.nanoTime()` every N nodes against a deadline of about 70-80 ms.
- **GC spikes:** preallocate all search stacks, move lists, accumulator stacks and transposition tables at startup. Use primitive arrays only, and no boxing, streams or lambdas in the hot loop.
- **Overfitting to self-play:** evaluate every new eval against a fixed gauntlet (classical alpha-beta bot, previous best, random-opening suites) with at least several hundred games including both colours. Add random openings or temperature so training covers positions an opponent will actually reach.
- **Eval/search mismatch:** a learned eval trained on quiet positions can be badly wrong on positions with immediate threats. Keep a hard-coded threat check (immediate win or forced block) in front of the eval, and keep threat-space extensions in search.
- **Board-size drift:** if the tournament changes rows or columns or the connect length, a net trained only on 8x12 may be worthless. Keep the handcrafted eval as a fallback.

### Gaps
- No source measures HotSpot warm-up specifically for a game-bot workload with a 100 ms budget. The mitigations above are standard engineering practice, not cited data.

## Q6. Does a learned evaluation beat a well-tuned handcrafted threat-based evaluation in Connect-Four-like games at equal time?

### Takeaway
There is no clean equal-time head-to-head between a learned eval and a strong handcrafted threat-based alpha-beta in Connect Four. The available data shows learned evals with search beating weak minimax baselines (depth 5, simple heuristics), and alpha-beta hybrids beating pure AlphaZero. The honest expected payoff over a well-tuned classical bitboard alpha-beta with a threat-based eval is small to moderate, and comes mainly in the middlegame of a large 8x12 board where search cannot see to the end. Tuning the weights of the existing handcrafted eval (Texel or TD, a few days of work) gives most of the gain for a fraction of the effort.

### Cited Findings
- AlphaZero beat depth-5 minimax 10-0 on ConnectX, but that minimax used a simple run-length heuristic and Python-level depth limits within a 5 s budget. — [arXiv 2210.08263](https://ar5iv.labs.arxiv.org/html/2210.08263)
- The AlphaZero.jl network alone ends "significantly stronger" than depth-5 minimax with a handcrafted heuristic. — [AlphaZero.jl tutorial](https://jonathan-laurent.github.io/AlphaZero.jl/stable/tutorial/connect_four/)
- On 6x7, the n-tuple + MCTS wrapper (TCL-wrap, about 30 ms per move) won 99/0/1 as first player against the alpha-beta agent AB-DL. Without the wrapper, TCL-base won only 91/2/7 as first player. Overall win rates were nearly equal (66.3% vs 64.9%). Because the first player wins under perfect play in 6x7 Connect Four, wins as first player mostly measure the absence of mistakes. — [arXiv 2204.13307](https://ar5iv.labs.arxiv.org/html/2204.13307)
- The hybrid (AlphaZero + alpha-beta for tactics) won 58% against pure AlphaZero with no losses in 100 games. — [NTNU Two-Phase-Win](https://scholar.lib.ntnu.edu.tw/zh/publications/two-phase-win-strategy-for-improving-the-alphazeros-strength-2/) (search excerpt)
- Texel tuning of a handcrafted eval gave about +100 Elo cumulatively in a chess engine. — [chessprogramming.org Texel](https://www.chessprogramming.org/Texel%27s_Tuning_Method)
- Learned patterns plus alpha-beta (Logistello: regression-fitted pattern tables plus Multi-ProbCut) beat the human Othello world champion 6-0 in 1997, which is the historical precedent for "learned eval inside alpha-beta" in a board-pattern game. — [Buro, Logistello](https://webdocs.cs.ualberta.ca/~mburo/log.html); [chessprogramming.org ProbCut](https://chessprogramming.org/ProbCut)

### Inferences
- **Recommended path (effort to payoff):**
  1. Build the classical bot first: bitboards, negamax alpha-beta, transposition table, threat-based eval, move ordering, iterative deepening.
  2. Texel- or TD-tune its eval weights on self-play games (hours of CPU time, about 1 week of work).
  3. Optionally add an n-tuple or pattern eval trained by TD in Java. It is the cheapest learned eval and needs no Python.
  4. Only then try a tiny NNUE distilled from deep-search labels, if profiling shows spare time per node.
  Skip full AlphaZero/MCTS: it costs about 10-100x more effort and compute, needs a Java MCTS plus a Python trainer bridge, and its inference cost in pure Java at 100 ms would cripple it.
- **Expected gain:** step 2 is likely the best gain per hour. Steps 3-4 might add a measurable but modest win-rate edge (single-digit to low double-digit percentage points) against a strong handcrafted bot, and only if the eval is the bottleneck. On 8x12 with 100 ms the search will reach maybe 10-14 plies, which does not reach the end of the game, so the eval does matter more than in 6x7. These numbers are my judgement, not measured.
- **Risk:** a learned eval adds failure modes (overfitting, board-size drift, quantization bugs, jar-size and JIT issues) that a handcrafted eval does not have. Always ship with A/B self-play evidence and a fallback.

### Gaps
- No published equal-time match between a learned eval and a strong handcrafted threat-based alpha-beta (Allis-style threat analysis) in Connect Four or a larger-board variant. Every published baseline I found was a depth-5 minimax or a perfect solver on 6x7.
- I did not find Stockfish's own published NNUE-vs-classical Elo gain in the sources fetched here, so I don't quote a number.
