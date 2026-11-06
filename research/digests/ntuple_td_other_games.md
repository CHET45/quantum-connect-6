# N-tuple / pattern-weight value functions learned by TD and related methods: Othello, 2048, Go, Backgammon

Digest for re-implementing the best ideas in "Quantum Connect" (gravity drop, 8 rows x 12 columns, SIX in a row, special 3x3-clear move once per 10 own turns, 100 ms/move, Java stdlib only, alpha-beta + threat search + learned pattern/n-tuple evaluation trained offline).

All claims below cite the source file plus section. Line numbers (L...) refer to the `.md` files in `sources/text/ (full texts of the cited works, not published)`. Anything marked **[OWN]** is my own inference and not a claim of the papers. Anything marked **[UNCERTAIN]** is a place where the pdftotext extraction dropped or garbled symbols and I reconstructed the content.

## Source legend

| Tag | File | What it really is |
|---|---|---|
| LUCAS | `pdf-p1-lucas2008-learning-to-play-othello-with.md` | S. Lucas, "Learning to Play Othello with N-Tuple Systems", Australian J. Intelligent Information Processing 4 (2008) |
| SZUBERT | `pdf-p1-krawiec2012-learning-ntuple-networks-for-othello.md` | Despite the filename, this is Szubert, Jaskowski, Krawiec, "On Scalability, Generalization, and Hybridization of Coevolutionary Learning: A Case Study for Othello", IEEE TCIAIG 5(3), 2013 (the GECCO 2011 Krawiec and Szubert paper is its predecessor) |
| JASKSYS | `jaskowski-systematic-ntuple-othello.md` | W. Jaskowski, "Systematic N-tuple Networks for Position Evaluation: Exceeding 90% in the Othello League" (arXiv 1406.1509, 2014) |
| J2048 | `jaskowski-ntuple-td-2048.md` | W. Jaskowski, "Mastering 2048 with Delayed Temporal Coherence Learning, Multi-Stage Weight Promotion, Redundant Encoding and Carousel Shaping" (arXiv 1604.05085, 2016) |
| YEH | `pdf-p3-yeh2016-multistage-temporal-difference-learning-for.md` | Yeh et al., "Multi-stage temporal difference learning for 2048-like games" (IEEE TCIAIG 2016) |
| SILVER | `pdf-p1-silver2007-reinforcement-learning-of-local-shape.md` | Silver, Sutton, Mueller, "Reinforcement Learning of Local Shape in the Game of Go" (IJCAI 2007) |
| TESAURO95 | `pdf-p2-tesauro1995-temporal-difference-learning-and-tdgammon.md` | Tesauro, "Temporal Difference Learning and TD-Gammon", CACM 1995 |
| TESAURO-HTML | `tesauro-td-gammon-html.md` | HTML version of the same article |
| BAXTER99 | `pdf-p2-baxter1999-tdleaflambda-combining-temporal-difference-learning.md` | Baxter, Tridgell, Weaver, "TDLeaf(lambda): Combining Temporal Difference Learning with Game-Tree Search" (1999) |
| BAXTER-TDL | `baxter-tdleaf-lambda.md` | Another version of the TDLeaf(lambda) paper |
| BAXTER-KC | `baxter-knightcap.md` | Baxter et al., "KnightCap: A chess program that learns by combining TD(lambda) with game-tree search" |

Caveat on extraction: for the two-column IEEE PDFs (SZUBERT) the extractor dropped most mathematical symbols (learning rate, epsilon, sigma values, exponents, table I numbers). I could not recover them from the text; I say so where it matters.

---

# 1. One subsection per paper

## 1.1 LUCAS (Othello, TD(0) on a symmetric random-snake n-tuple network)

**Role in our context.** The paper that introduced n-tuple value functions trained by TD into board games. 1-ply play only, so it measures the learner, not the search.

### Representation (LUCAS §3, §3.1, §3.2)
- An n-tuple = n board squares. Each square has 3 values, so the tuple is read as an n-digit base-3 number used as an index into a look-up table (LUT) of weights. Digit coding: **white=0, vacant=1, black=2** (LUCAS §3.1, L296).
- **Symmetric sampling**: each n-tuple is applied at all symmetric images (up to 8 under rotations/reflections); the LUT is **shared** across the 8 images, and the tuple output is the **sum** of the LUT entries at the 8 indices (LUCAS §3.1, L282-L311, Eq. 1: `v(b) = sum_{d in D(b)} l[d]`).
- Whole-board value is the sum over all n-tuples; for training the sum `f(x)` is squashed: `v(x) = tanh(f(x))` (LUCAS §3.1 L315-L319; §4 Eq. 3).
- **Tuple shapes**: "random snakes": pick a random square, take a random walk of 6 steps through the 8 neighbours, keep only distinct squares, so each tuple has 2 to 6 squares. **30** such tuples (LUCAS §3.2, L322-L335). Example tuple {2, 11, 20, 28, 34, 35} (L344). Tuple order is irrelevant provided the same order is used in all symmetric expansions (L345).
- Size: the demonstrated network had **14,772 weights** (LUCAS §5, L540). Lucas calls it "typically around 15,000 weights" (§2.1, L169).
- Speed argument: LUT lookup is constant time, so cost is linear in the number of tuples, independent of table size; "millions of weights can be calculated extremely quickly" (LUCAS §4, L453-L457).
- Sign convention: single value function, positive = good for black, negative = good for white; the LUT entry for the all-white pattern (index 0) is the most negative and all-black (index 26 for a 3-tuple) the most positive (LUCAS §3.1, L297-L305). The paper does not spell out how White picks a move; the sign convention implies **output negation** (white minimises) **[OWN inference; JASKSYS §II-F names the alternatives]**.

### Algorithm (LUCAS §4)
- Gradient-descent TD(0) on afterstates, with the player to move choosing the successor with the highest value (1-ply):
  `w_i <- w_i + alpha * (v(x') - v(x)) * (1 - v(x)^2) * x_i` where `x` is the board before and `x'` after the move (Eq. 2; pdftotext shows it garbled but the Java listing in Fig. 8 confirms it, L429-L442).
- Terminal update: `w_i <- w_i + alpha * (r - v(x)) * (1 - v(x)^2) * x_i`, with `r = +1` black wins, `0` draw, `-1` white wins (L402-L405).
- For an n-tuple LUT the gradient is an indicator, so the update is simply `l[d] += delta` for every index `d` in `D(b)` with `delta = alpha*(tg - op)*(1 - op^2)` (Eq. 4, L451).
- **alpha = 0.001** (Fig. 8 caption, L418-L419).
- Pure self-play. Lucas says TDL "uses all information during the course of a game" whereas coevolution (CEL) only uses win/lose aggregated over games (LUCAS §4, L360-L363).

### Setup and results (LUCAS §2.2, §5)
- All evaluation at 1-ply with 10% forced random moves ("epsilon-Othello"), measured against the standard heuristic weighted piece counter (WPC) from Yoshioka et al. (LUCAS §2.2, §5).
- Learning curve (Fig. 9, from the 2007 reference): every 25 self-play games, 100 games vs the heuristic (50 per colour). The axis goes to roughly 82% (numbers read off the axis labels, L465-L477 **[UNCERTAIN]**, I cannot see the curve).
- **Table 1, vs the CEC 2006 champion (MLP, 200 games)**: 250 self-play games: 89 W / 5 D / 106 L; **500: 135/6/59**; 750: 142/5/53; 1000: 136/2/62; 1250: 142/5/53 (LUCAS §5, L494-L507). Text: after 500 self-play games the champion is beaten in "nearly 70%" of games.
- Qualitative: the n-tuple player has a worse piece difference in the midgame and only dominates in the last fifth of the game; it won 9 of 10 games against the heuristic in the shown sample (§5, L534-L540).
- Comparison with other representations: spatial/evolved MLP of Chong et al.: 5,900 weights evolved over ~100,000 games (§2.1, L166-L171). Lucas states n-tuple networks "learn faster and better" than WPC and MLPs (abstract, §6).
- Enforcing symmetry in the plain WPC increased learning speed (author's unpublished experiments) (LUCAS §2, L113-L120).
- TDL vs co-evolution (prior work, Runarsson and Lucas): TDL learns faster, CEL with careful tuning (parent-offspring weight averaging against noise) eventually learns better (LUCAS §4, L365-L373). Lucas's own CEL attempt on n-tuples was "less successful", maybe too few games (L370-L372).
- Rationale offered: n-tuple systems "factorise well"; the values in one LUT entry are largely independent of others; TD can adjust them directly with the within-game signal, evolution cannot (LUCAS §6, L555-L562).

### Key lessons
1. Symmetric weight sharing + TD(0) + tanh squashing + a few dozen random tuples is enough to beat strong hand-built MLPs within ~500 self-play games at 1-ply.
2. Tiny learning rate (0.001) is used with the tanh derivative; because each of ~30 tuples times up to 8 symmetric images adds the same delta, effective step on the output is large (**[OWN]** `alpha` is per LUT entry, not scaled by number of active entries; compare J2048, which divides by `m`).
3. Not stated in the paper: whether random exploratory moves were used in training, initial weights (J2048/SZUBERT say TD does best when initialised to 0, see 1.2).

---

## 1.2 SZUBERT (Othello, TDL vs EL vs CEL vs ETDL/CTDL hybrids, three network sizes)

**File caveat.** The file is the 2013 TCIAIG journal paper (`SZUBERT`) rather than the 2012 conference one.

### Representation (SZUBERT §II-C, §IV-A)
- Same as Lucas: random snake-shaped n-tuples, ternary digits (**0 white, 2 black, 1 empty**, L134-L138), **8-fold symmetric sampling** with shared LUTs and summed outputs (Eq. 1), outputs squeezed with tanh for TD (L206-L210).
- Three sizes: **7 x 4-tuples (567 weights)**, **9 x 5 (2,187)**, **12 x 6 (8,748)** (SZUBERT §IV-A, L336-L339). That is `m * 3^n`: 7*81, 9*243, 12*729.
- Weight init: **TD learns faster when 0-initialised** (citing earlier work), evolutionary methods start from random weights (range stripped by extraction) (SZUBERT §IV-A, L391-L398).
- Topology search (their addition): tuples keep the same number and size, but input assignment (which squares) is mutated (topology mutation = each input replaced by one from its neighbourhood with some probability) and crossed over (swap whole tuples with their LUTs) (SZUBERT §IV-B, L442-L458).

### Algorithms (SZUBERT §III, §IV-B, §IV-C)
- **TDL**: self-play TD(0) as in Lucas; weights of the LUT entries addressed by the tuples are changed (L211-L219). **A random move is forced with probability epsilon (epsilon-greedy) and no weight update follows a random move** (L253-L259). Numeric alpha and epsilon are stripped by pdftotext **[UNCERTAIN]**.
- **EL** (evolutionary): generational, population 50, fitness = points in 50 randomised games vs the WPC heuristic, tournament selection (size 5), topology crossover, weight and topology mutation (§IV-C, L473-L487).
- **CEL** (coevolution): population 50, round-robin tournament, win/draw/loss = 3/1/0 points, two games per pair swapping colours (L392-L397).
- **ETDL / CTDL** (hybrids, "Lamarckian"): same as EL / CEL but weight mutation is replaced by a TD phase: **5000 TD self-play games per generation, spread over the population, i.e. 100 games per individual** (L399-L404). The population thus searches topology + opponents, TD searches weights ("coevolutionary gradient search").
- Budget: every run stopped at **3,000,000 games** in total; 24 repetitions; performance sampled 50 times per run (every ~60,000 games) (§IV, L326-L329, L427-L428).

### Performance measures (SZUBERT §IV-D, §V)
1. % points vs the WPC heuristic over 1000 games (500 per colour), with epsilon-random moves forced for both players because players and game are deterministic.
2. Generational round-robin tournament between teams of the 24 best-of-run players of each method (no randomisation needed).
3. Round-robin against the top 14 strategies of the Othello League (3/1/0 points, each pair twice).

### Results
- **Scalability** (§V-A1, Fig. 3, L498-L520): only **TDL** significantly improves when moving 7x4 -> 9x5 -> 12x6. EL and CEL get worse with larger networks. For 12x6, CEL "gains barely a few percent" in the whole run. Authors blame the Gaussian weight-mutation operator in the high-dimensional weight space; checked with several sigmas, no sigma helps, and with sigma=0 (topology-only evolution) larger networks do help (L522-L550).
- **Vs WPC heuristic** (§V-A2, Fig. 5): 7x4: CTDL beats TDL and CEL in the long run; TDL learns rapidly, CEL slower but ends similar. 12x6: TDL and CTDL both between **65% and 70%**, "ceiling effect"; Manning's Nash-memory approach gives 66-68% for 12x6 (L582-L590). EL/ETDL score higher (~80% for ETDL 12x6) because they are *trained against that very opponent* (L685-L686).
- **Round-robin** (§V-B): CTDL clearly beats TDL, CEL and ETDL, advantage grows with time; ETDL "overfits" to the WPC opponent; EL wins about 10% of games (L625-L636). CEL 7x4 beats CEL with larger networks, but with TDL as the weight operator (CTDL) larger networks *do* help (L638-L649).
- **League** (§V-C): CTDL scores 5-10% more than ETDL vs the league pool; ranks of CTDL players are more often top-3 than ETDL (L664-L679). The best ETDL 12x6 player reached **87.1%** vs WPC (89.5 points in the 100-game online league), taking the league lead under the name epTDLmpx_12x6 (L685-L691). (JASKSYS Table III lists the same player at 0.871 with 3,240 weights, which differs from the 8,748 of a 12x6 network. **[UNCERTAIN]**: probably duplicated indices or tuples shorter than 6 after random walks; paper does not say.)
- **Topology** (§V-D): evolved tuples cluster around corners and abandon central squares, mirroring the WPC weight pattern (L696-L722).

### Key lessons
1. **TD is the only method here that scales with the number of weights** (567 -> 8,748); weight mutation does not.
2. Hybridising (coevolution over topology, TD over weights, opponents from the population) gave the most *general* player; evolving against a fixed heuristic gives the best number against that heuristic and worse head-to-head results elsewhere ("1-ply overfitting to the evaluator opponent").
3. Never trust a single fixed opponent benchmark: it saturates (ceiling around 65-70% for random-snake 12x6 vs this opponent).
4. TD-based training with no update on forced-random moves is the recommended recipe; zero initialisation.

---

## 1.3 JASKSYS (Othello, systematic short straight n-tuples, evolution strategy)

### Representation (JASKSYS §II-D, §II-E)
- Same formula: `f(b) = sum_i LUT_i[index(b_loc_i1 ... b_loc_in)]`, `index(v) = sum_j v_j * c^(j-1)`, c=3 with **white=0, empty=1, black=2** (§II-D, L94-L104). 8-fold symmetric sampling.
- Two architecture families with **equal weight counts** (Table II, L140-L146):

  | systematic "all-n" | weights | random snake "rand-m x n" | weights |
  |---|---|---|---|
  | all-2 (32 tuples of size 2) | **288** | rand-10x3 | 270 |
  | all-3 (24 tuples of size 3) | 648 | rand-8x4 | 648 |
  | all-4 (21 tuples of size 4) | 1,701 | rand-7x5 | 1,701 |
  | all-1 (10 tuples of size 1) | 30 | (not paired) | |

- "all-n" = **every possible straight (horizontal, vertical, diagonal) n-tuple on the board**, collapsed to symmetry classes; each class gets one LUT of 3^n entries shared over its up-to-8 images (§II-E2, L118-L120). Thus all-2 = 32 symmetry classes of adjacent pairs, each of 9 weights = 288. (The listing in Fig. 7 confirms: all tuples are adjacent pairs such as {6 7}, {7 15}, {28 35}, with "2 8"/"2 4"/"2 2" = number of images, L267-L395.)
- Logistello (reference): 11 hand-made n-tuples with n between 3 and 10 (§II-E3, L124). Manning: 12x6 from Random-Forest-style inputs.
- Playing both sides (§II-F, L128-L136): (a) doubled function (two sets of weights), (b) output negation (black maximises, white minimises), (c) **board inversion** (learn only the "black" view; for white flip all discs, pick the best black move). All use a single set of weights in (b)/(c).

### Training (JASKSYS §III-A)
- NOT TD: a **(10+90) evolution strategy**, 5,000 generations, initial weights U[-0.1, 0.1], Gaussian mutation sigma = 1.0, fitness = Othello-League measure over 1000 double games (win=1, draw=0.5; epsilon=0.1 random moves) vs the Standard WPC Heuristic. 10^10 games per run, 28 h on a 6-core CPU; the Java implementation did about 1000 games/s per core. 10 runs per architecture, final evaluation with 50,000 double games, Mann-Whitney test with Holm correction (L152-L160).

### Results (JASKSYS §III-B..E, Table III, Table IV)
Means over 10 runs (Table IV, L251-L261), score vs SWH at epsilon=0.1:

| architecture | mean |
|---|---|
| all-2, board inversion | **0.9401** (best run 0.9578) |
| all-3 inv | 0.9379 |
| all-4 inv | 0.9122 |
| rand-10x3 inv | 0.8681 |
| rand-8x4 inv | 0.8454 |
| rand-7x5 inv | 0.8012 |
| rand-8x4 output negation | 0.7698 |
| all-1 inv | 0.7362 |
| all-1 output negation | 0.5946 |

- Systematic beats random at equal weights in every pair; **the worst all-2 run beats the best rand-10x3 run** (§III-C, L187). Variance also lower for all-* (no random-structure noise).
- Longer tuples do not help: all-3 equals all-2, all-4 is significantly worse and learns more slowly (§III-D, L193-L199). Authors note this is surprising since capturing in Othello needs at least three discs in a line (§V, L243).
- **Board inversion beats output negation**, and gives lower variance (§III-B, L166-L168; table values above: 0.8454 vs 0.7698; 0.7362 vs 0.5946).
- Best all-2 player: **0.9592 +/- 0.0012** with 288 weights (§III-E, L205), versus 0.8710 for epTDLmpx_12x6 and 0.9149 for the league-compatible output-negation "wj-1-2-3-tuples" (all straight 1-, 2-, 3-tuples, **966 weights**; 0.94 in the 100-game league run, 0.9149 +/- 0.0017 over 50,000 double games) (Table III, L207-L223).
- Caution: that player was evolved against SWH, so it is not general: vs random WPC opponents the text reports 0.9584 +/- 0.0012 (L227; the caption of Fig. 7 gives the same 0.9584 as "league performance", so there is an inconsistency in the paper **[UNCERTAIN]**) and the author notes >0.99 against random opponents is achievable with less effort via coevolution (L227). He also suspects ~0.96 is a ceiling for 1-ply with 10% random moves (L225).
- Weight magnitudes in Fig. 7 (about -200 to +285) show the evolved LUT scale for an *un-squashed* linear output (L267-L395).

### Key lessons
1. **Replace random long snakes with ALL short straight tuples** (lengths 2 to 3). Fewer weights, less variance, faster learning, better final play.
2. Weight count is not the only factor; architecture matters (all-4 with 1,701 weights beats rand-10x3 with 270) (§IV, L233-L235).
3. The author asks (§V, L245) whether this carries to TD and other games (e.g. Connect Four). The 2048 paper below answers with systematic tuples + TD.

---

## 1.4 J2048 (2048, afterstate TD with systematic n-tuples; TC, delayed TD, stages, weight promotion, carousel, redundant encoding)

2048 is single-player and stochastic, but the machinery (afterstate n-tuple TD, learning-rate adaptation, game-phase staging, extra redundant features, search coupling) transfers.

### Representation (J2048 §IV-B, §IV-D)
- `V(s) = sum_i V_i[index_i(s)]`, `index_i(s) = sum_j s[loc_ij] * c^(j-1)`; **cell encoding: 0 = empty, otherwise log2(tile)**; limited to **c = 16** values (L127-L131, L144). Evaluation `O(m)` memory lookups (L140).
- Weights are the **afterstate** values `V(s')`: state right after the player's move, before the random tile; the policy is greedy on `r + V(s')` (L176-L187). **Afterstate** learning saves up to 30 successor evaluations per move.
- Baseline network "**42-33**": four 6-tuples of two shapes named "33" and "42" (Fig. 3c; the cell geometry is only in the figure, not in the extracted text, so I cannot state it; the names suggest row lengths 3+3 and 4+2 **[OWN guess]**) = `4 * 16^6 = 67,108,864` weights, with 8-fold symmetric sampling (shared tables) (§IV-B2, L156-L158). Larger: "421-43" two shapes of 7-tuples, `5 * 16^7 = 1,342,177,280` weights (L608).

### TD(lambda) with limited horizon (J2048 §IV-C, §IV-D, Alg. 1)
- `delta_t = r_{t+1} + V(s'_{t+1}) - V(s'_t)`; terminal: `delta_t = -V(s'_t)` (L169, L187).
- Update: for `k = t downto max(t-h,1)` and each tuple `i`: `V_i[s'_k] += (alpha/m) * delta_t * lambda^(t-k)` (Alg. 1 line 33; garbled but reconstructable from L307, **[UNCERTAIN]** only in typesetting). Eligibility traces over the whole weight vector are infeasible (hundreds of millions of weights), so traces are replaced by a finite **update horizon** `h = ceil(log_lambda 0.1) - 1` (keep terms with `lambda^(t-k) >= 0.1`); for lambda=0.5, h=3 (L189, L385).
- Learning rate scaled by `1/m` so alpha=1.0 is "the maximum sensible value": it zeroes the error for the visited afterstate (L191).
- **No exploration at all**: epsilon-greedy, softmax and other variants "significantly inhibit" learning in 2048; the stochastic game explores enough (L193). **[OWN]** In a deterministic game like ours this probably does NOT carry over; Szubert/Lucas used epsilon-random moves for Othello.

### Temporal coherence TC(lambda) (J2048 §V-B, Alg. 2)
- Per-weight adaptive rate. Two extra tables with the *same architecture as V*: `E` (sum of signed errors) and `A` (sum of absolute errors). `alpha_i = |E_i[s']| / A_i[s']` if `A_i != 0`, else 1. Update: `V_i[s'] += beta * (alpha_i/m) * delta * lambda^(t-k)`, `E_i[s'] += delta`, `A_i[s'] += |delta|` (L369-L373). The rate stays near 1 while errors for that weight have consistent sign and shrinks when the signs oscillate. `beta` in [0,1] is the meta-rate.
- Compared with Autostep (Mahmood), which failed (non-stationary RL data): TC best (§V-B3, L389).
- Table I (1-ply / 3-ply expectimax average score, 5 runs, L395-L420), TD(0.5): alpha=0.01: 41,085 / 99,015; 0.1: 102,130 / 175,301; 0.5: 130,266 / 196,138; **1.0: 141,456 / 237,026**. TC(0.5): beta=0.1: 200,588 / 292,558; 0.5: 244,905 / 335,076; **1.0: 250,393 / 333,010**. Autostep best mu=0.1: 171,396 / 246,740.
- **TC nearly doubles TD at the same budget** (141k -> 250k at 1-ply).
- Cost: TC takes ~1.5x TD time (Autostep ~2x) because of the E, A tables.

### Delayed TD(lambda) (J2048 §V-C, Alg. 3, Alg. 4)
- Store the last `h+1` states and errors; delay the update of state `s'_{t-h}` by `h` steps and apply once the *decayed cumulative error* `Delta_{t-h} = sum_{k=0..h} lambda^k * delta_{t-h+k}`: `V_i[s'_{t-h}] += (alpha/m) Delta`. Each table cell is touched once rather than `h+1` times; the error buffer fits in cache while the big LUTs do not (L431-L433). At episode end a `Finally` flush applies the remaining deferred updates.
- Result (Table II, L531-L538): no significant change in score (TC(0.5): 248,770 vs 250,393 delayed) but **3x faster** (1.16 vs 0.39 days); TD(0.5): 0.26 vs 0.23 days.

### Multi-stage weights with promotion (J2048 §V-D, Tables III)
- Game split into `2^g` stages by the largest tiles on board (stage length l = 2^(16-g) in tile value; g=4: switches at 4096, 8192, ...); each stage has its own full n-tuple network, so parameters grow `2^g`-fold (g=4: 1,073,741,824 weights for 42-33; extraction shows "6^16" but arithmetic `2^4*4*16^6` fits) (L546-L548).
- **Plain stages hurt**: each stage learns from scratch; 2^4 stages without promotion: 1-ply 226,352 vs 251,033 for 1 stage (Table III, L576-L577).
- **Weight promotion**: when a table entry of stage `k` is first read it is initialised from the same entry in stage `k-1`; implemented by testing "value == 0" so no extra memory (L583-L596). With promotion: 2^3 stages 273,510 / 363,379; **2^4 stages 267,544 / 400,124** (best at 3-ply); 2^5 stages 250,051 / 355,413 (overkill) (Table III, L575-L580). So 2^3 is better at 1-ply, 2^4 at 3-ply.
- Equal-parameter alternative (one big 421-43 network, 1.34e9 weights): best at 1-ply (284,657) but poor at 3-ply (326,267), suffering "1-ply overfitting" (L581, L608). Multi-stage nets seem immune to that, though none has converged and needs ~4x more actions (L610-L612).
- E and A tables are also stage-specific.

### 1-ply overfitting, carousel shaping (J2048 §V-E1, Alg. 5)
- Observation: better 1-ply score can make 3-ply play worse (the evaluation is trained under 1-ply play, but used in search). Late game positions are rarely visited.
- **Carousel shaping**: keep the last 1000 "initial states" of each stage (first state seen after entering the stage). Episodes cycle: stage 1 starts from the real initial state, then the next episode starts from a random stored initial state of stage 2, then stage 3, ... and restarts at stage 1 when the stage set is empty or all stages are done (L622-L624). So training time is spread evenly over stages. Wu et al. instead trained stage by stage and froze earlier ones (L626).
- Effect (Table IV, L684-L690): consistent small loss at 1-ply, large gain at 3-ply: 42-33: 265,435 / 393,289 -> with CS 258,616 / 432,701.

### Redundant encoding (J2048 §V-E2)
- Add **smaller tuples that are sub-patterns of the larger ones** (4-tuples "4" line and "22" square, then 3-tuples). They do not add expressiveness but speed up generalisation. Results (Table IV): 42-33-4-22 with CS 256,569 / 457,754; 42-33-4-22-3 without CS 319,433 / 469,779; **with CS 311,426 / 491,398** (12 tuples, 5.47 learning-days) (L684-L694). After training the small tables can be **folded into the big ones** so the final player is as fast as non-redundant (L696).

### Overall ladder (Table VII, L749-L756), 1-ply / 3-ply:
TD(0.5) 141k / 237k -> TC(0.5) 249k / 333k -> delayed-TC 250k / 336k -> + 2^4 stages & promotion 268k / 400k -> + redundant encoding 319k / 470k -> + carousel 311k / 491k.

### Final strength (J2048 §VI, Tables V and VI)
- Expectimax 1-ply 324,710 (258,371 moves/s); 3-ply 511,759 (1,484 moves/s); 100 ms/move 589,355; 1000 ms/move **609,104**, reaching 32,768 in 70% of games (L714-L724). Time-limited iterative deepening is more effective than fixed depth (5-ply 545,833 at 16 moves/s vs 100 ms 589,355) (L706).
- Earlier work: 1-ply TD(0) 100k; Wu et al. multi-stage 3-ply 328,946; Yeh et al. 443,526 (L69, L728-L735).
- Training scale: 10^10 actions budget per run (~10^7 episodes), 24 threads with **lock-free optimistic parallelism** (no locks on the shared LUTs; no adverse effect) -> 1-7 days per run (L319-L331).

### Key lessons
1. Afterstate value, `alpha/m` scaling, tiny integer-friendly tuple tables.
2. Adaptive per-weight rates (TC) are the biggest single gain after the basic setup.
3. Stages only pay off with promotion (and more stages cost generalisation).
4. Training distribution shaping (carousel) and redundant small tuples both mainly help when the evaluator is used inside **search** (3-ply), not at 1-ply.
5. The author warns that n-tuple nets "learn by heart" and lack global features, e.g. number of empty cells (L764-L766).

---

## 1.5 YEH (2048 and Threes, multi-stage TD without weight promotion; second source on stages)

### Representation (YEH §II-D, §IV-A)
- Afterstate n-tuple networks, linear sum of feature weights `V(s) = phi(s) . theta`; TD(0) gradient is `phi(s)`, update `Delta theta = alpha * delta_t * phi(s_t)` (Eq. 5, L146). Three evaluation modes from Szubert and Jaskowski (Q(s,a), V(s) of the state to play, V(s') of the afterstate); afterstates "clearly outperformed" the other two (§II-C, L162-L172).
- Their tuples (Fig. 4b): four **6-tuples, "knife-shaped"**, covering all of the original 1x4 lines, with 8 symmetric images sharing weights, 16 values per cell including 32768, `4 x 16^6` features (about triple the roughly 23 million of the earlier network) (§II-D, L226-L235).
- **Extra global features**: "large-tile features" indexed by the tuple `(n_2048, n_4096, n_8k, n_16k, n_32k)` = how many of each large tile are on board, to represent the difficulty created by blocking tiles (§IV-A, L281-L289). Of all combinations tried, tuples + large-tile features was best for both average and maximum score (Figs. 5-6; no numbers in the text).
- Later improvements (§IV-E, L596-L612): (a) add features: number of empty tiles, number of distinct tiles, number of mergeable pairs, pairs of neighbouring v and 2v tiles; (b) cut alpha from 0.0025 to **0.00025** once saturated; (c) a truncated TD(lambda), **lambda = 0.5 with up to 5-step returns**, weights 0.5, 0.25, 0.125, 0.0625, 0.0625 (Eq. 6), done **offline after each game** without eligibility traces (sparse features make traces awkward and games last thousands of moves). Each of (a), (b), (c) improved first-stage learning incrementally (Fig. 17, no numbers).

### Multi-stage TD (MS-TD) (YEH §III, §IV-B)
- 3-stage scheme with milestones `T_16k` = first time a 16384-tile exists and `T_16+8k` = first time both 16384 and 8192 exist.
  1. Stage 1: ordinary TD(0) from the opening until saturated ("average score per 1,000 games stops improving"), e.g. **5 million games**.
  2. Keep playing with frozen Stage-1 weights and **collect 100,000 boards at `T_16k`** (with the scores so far).
  3. Stage 2: train a *new* weight set, **initialised to zero** (the text says "feature weights are all initialized to be zero", L234-L235, so there is no promotion here), starting from those collected boards, used round-robin, 5 million games. Same for Stage 3 from boards collected at `T_16+8k`.
  4. When playing: Stage-1 weights before `T_16k`, Stage-2 between, Stage-3 after `T_16+8k` (§III, L240-L244).
- Rationale: large blocking tiles change how hard the game is, so weights should reflect that difficulty (L246-L257); "survival phenomenon": once 16384 is created, the smaller large tiles usually vanish (§II-A, L117-L130).
- Compared with J2048: **no weight promotion**, stage boundaries are event-based (first appearance of a milestone), and later stages train from *saved start boards* of the previous stage (J2048's carousel generalises this; J2048 §V-E1, L626 discusses the difference).

### Results (YEH §IV-B..E, Tables 1-3)
Table 1 (plain TD, 15 million training games, 10,000 test games) vs Table 2 (MS-TD, 5 million games per stage), expectimax 1/2/3 ply (L388-L438):

| | 1-ply | 2-ply | 3-ply |
|---|---|---|---|
| TD, avg score | 159,565 | 290,381 | 321,496 |
| MS-TD, avg score | 143,958 | 301,280 | 350,394 |
| TD, rate of 32768 | 0.00% | 0.00% | 0.00% |
| MS-TD, rate of 32768 | 0.06% | 5.50% | **13.78%** |
| TD, max score | 371,595 | 374,675 | 374,458 |
| MS-TD, max score | 431,847 | 554,090 | 599,645 |
| TD speed (moves/s) | 280,882 | 13,861 | 1,256 |
| MS-TD speed | 213,729 | 13,884 | 1,257 |

(Table 2 was heavily garbled by pdftotext; I reassembled the columns from the two-column text and the prose at L382-L390. **[UNCERTAIN]** for the speed and 2-ply cells.)
- At 1-ply the average score is *lower* with MS-TD, and the 16384 rate is lower (26.33% vs 17.87% at 1-ply), because Stage 1 saw only 5M instead of 15M games; the gain is at search depth and in the tail (large-tile rates) (L391-L398).
- Four alternative splits (Table 3, 3-ply, 10,000 games, L556-L573): an extra split at `T_8k` did not help (14.15% for 32768); at `T_16+8+4k` 15.30%; five stages (adding `T_16+8+4+2k`) **18.31%**, best for 32768 and max score (614,204); six stages 16.82% but best avg score (364,438). "The benefit of splitting extra stages was diminishing" (L586-L592).
- Tuned system (5 stages + (a)(b)(c)), 3-ply, 10,000 games: **32768 reached in 31.75%**, average 443,526, max 793,835, 500 moves/s, one game reached 65536 (L592-L600). Comparison: nneonneo/xificurk hand-feature + CMA-ES program: 28.25% (2,000 games), 432,557, about 4 moves/s, i.e. 125 times slower (L602-L608).
- Threes (same network plus hint-tile features), 3-stage split at `T_1536`, `T_3072`: 6144-tile rate 0.45% -> **7.83%** at 3-ply; with the tuning 8.91%, avg 234,490 (Tables 4-5, L615-L732). Extra stages did not beat the simple 3-stage in search (Table 6).

### Key lessons
1. Stage-specific weight sets help at search depth even without promotion, but only if each stage is trained on states the stage really sees (here: start boards collected from the previous stage).
2. The metric matters: average score and tail-event rates diverge (same message as SZUBERT's "ceiling effect" and J2048's "1-ply overfitting").
3. Event-based stage boundaries (first appearance of a milestone) are easy to apply identically at training and play time.

---

## 1.6 SILVER (Go 9x9: millions of local-shape features, linear + sigmoid, TD(0), online cascade)

### Representation (SILVER §4, §5, Fig. 3)
- **Local shape feature** = a binary feature that is 1 iff a rectangular template of the board (each intersection black / white / empty) matches exactly. All templates of a given size are enumerated into a "shape set" (L87-L119).
- **Weight sharing** (L103-L113): all rotations and reflections share a weight (8-fold); colour symmetry by inverting stone colours when evaluating a White move. Two classes: **location dependent** (rotation/reflection sharing only) and **location independent** (additionally translation invariant: one weight per template wherever it sits).
- Template sizes 1x1 up to 5x5. Counts for 5x5 Go are in Fig. 3 (L189-L203). **[UNCERTAIN]** pdftotext shifted the rows by one; the consistent reading is: number of positions `n_i` where a template fits on 5x5 = 25, 40, 16, 24, 9, 12, 4, 4, 1 for 1x1, 2x1, 2x2, 3x2, 3x3, 4x3, 4x4, 5x4, 5x5. Weights per set (location independent) = 3, 9, 81, 729, 19,683 for 1x1, 2x1, 2x2, 3x2, 3x3 (these equal `3^cells`, which supports the reading); the location-dependent sets are 4x larger for 2x2 to 3x3 (324, 2,916, 78,732); 4x3 and larger are **hashed**.
- Up to 3x3 all weights are stored; from 4x3 up, **hashed weight sharing**: a Zobrist hash of the shape into `h` bins (h = 100,000 in the experiments), colliding shapes share one weight (L119-L128).
- Partial order: a smaller template set is strictly more general than a larger one; location independent is more general than location dependent (L130-L136).
- Value: `V(s) = sigma( sum_j sum_k phi_{j,k}(s) theta_{j,k} )`, sigma = logistic, V = win probability; **all weights initialised to zero** (Eq. 1, L137-L148). Cost `O(sum_j m*n_j)`: only matched features are touched.

### Learning (SILVER §5-§9)
- Reward r = 1 win, 0 loss at game end. Moves by epsilon-greedy 1-ply search over V (epsilon = 0.1 in training, 0 in test), ties broken randomly.
- TD(0): `delta = r + V(s_{t+1}) - V(s_t)`; `Delta theta_{j,k} = (alpha / (m * n_j)) * delta * phi_{j,k} * V(s_t) * (1 - V(s_t))` (Eqs. 2-3; the `m n_j` normalisation gives every shape set an equal share of credit) (L159-L168). `alpha = 0.1`.
- **Co-adaptive self-play**: two agents each with their own weights playing each other (two gradients, a robust comparison) (§6, L174-L181). Test: 100 games vs the Average Liberty Player (ALP) every 500 training games and 1,000 at the end; 50 runs of 100,000 training games per experiment (L186-L196).
- **Board growing** (§7): train on 5x5, enlarge whenever both agents reach 90% vs ALP; weights of features that exist on the smaller board are copied (aligned to the corner), new central features start at 0 (L255-L257). Rationale: training time grows as `O(k^4)` on `k x k` Go (L236).
- **Online cascade** (§8): features are linearly redundant. For each shape set `S_i` compute its own TD error `delta_i` from a value `V_i` that only uses sets as general or more general than `S_i`, and update set `i` with its own error (Eqs. 4-6, L209-L233). General features get the best representation they can alone; specific ones learn only residual corrections, giving a canonical, interpretable weight vector that transfers across board sizes.
- **Generalised templates** (§9): liberty templates attach to each stone an external-liberty count (0, 1, >1); hashed.

### Results (SILVER §6, §10, Fig. 4, Fig. 5)
- 5x5, individual shape sets: **2x2 best** (25% wins vs ALP after 1,000 games, above 80% later); smaller sets lack power, larger ones are too numerous to learn in the budget; location independent sets learn faster, especially small ones (L215-L224).
- Combined shape sets (each experiment uses all sets as general or more general): **above 90%** wins; learning time slows roughly linearly with the number of sets (L226-L232).
- Final 9x9 agent: all shapes 1x1 to 3x3, location independent and dependent, plus liberty features, **about 1.5 million weights**, cascade + board growing (L266-L270; abstract: "over a million features").
- CGOS 9x9 Elo: **Linear-B (shape features) +1070** (71 games), **Linear-L (shape + liberty) +1140** (28 games), **Linear-S (shape features + full-width iterative-deepening alpha-beta) +1210** (43 games). References: ALP +850, Influence Player +700, random -170, best programs +1860 (Fig. 5, L283-L288). So search on the same linear evaluator added about +140 Elo and liberty features about +70.
- Interpretability: the 1x1 to 3x3 weights show recognisable Go shapes; weakness is the purely local view (§11).

### Key lessons
1. A huge number of tiny, exactly matching binary features trained by TD(0) from zero weights works; small translation-invariant templates are "surprisingly effective", 2x2 the best single size on 5x5.
2. Hierarchical redundancy (general + specific features) with a per-level error (cascade) gives faster learning and transferable weights; same spirit as J2048's redundant encoding but with a principled update.
3. Normalise the step by the number of active features (`m * n_j`), as J2048 does with `alpha/m`.
4. Board-size curriculum works because the features are local.

---

## 1.7 TESAURO95 and TESAURO-HTML (Backgammon, TD-Gammon: MLP, TD(lambda), self-play)

**Both files are the same article. Neither extraction contains the TD(lambda) formula, Figure 1 (network), or Tables 1-3** (TESAURO95 L226-L236; TESAURO-HTML L88-L94 show "the formula for the weight change is as follows:" followed by nothing). I therefore cannot quote alpha, lambda, number of input units, or the hidden-unit count of the released versions from these files, and I do not add numbers from memory.

### What the texts do say
- **Value function**: a multilayer perceptron with sigmoid units mapping a board encoding to a **4-component output** (White or Black wins normally or by gammon; triple "backgammons" ignored as too rare) (TESAURO95, "TD-Gammon's Learning Methodology", L216-L225).
- **Algorithm**: TD(lambda) with gradient (backprop) applied **at every ply**; at the end a final reward `z` replaces `Y[t+1]` in the error (L243-L245). Lambda controls the exponentially decaying feedback of an error to earlier positions: 0 = no feedback beyond the current step, 1 = undecayed (L237-L241).
- **Move selection / self-play**: the network scores every legal move for both sides and the side to move takes the maximum expected outcome; training is **pure self-play from random initial weights**, although early games last hundreds to thousands of plies against 50-60 for humans (L255-L266).
- **Encoding**: first experiments used only raw checker counts per location (zero knowledge); later versions **add Neurogammon's hand-crafted features**, giving "higher overall performance" (L246-L253).
- **Results**: raw-encoding network with **40 hidden units trained for 200,000 games** reached about Neurogammon strength; elementary tactics in a few thousand games, subtler concepts after tens of thousands (L268-L285). With hand features it "greatly surpassed" Neurogammon. Version 1.0: 51 games vs Robertie, Magriel, Davis, net loss 13 points (about -0.25/game). Version 2.0 (more training and **2-ply search**): 38 exhibition games, net loss 7 points. Version 2.1: 40 games vs Robertie, lost by 1 point; "strong master level" (L296-L330). 2-ply runs in real time; 3-ply expected to remove endgame errors (Conclusion, L507-L511).
- **Why it works (Tesauro's account)**, "Understanding the Learning Process" (L402-L492):
  - *Relative vs absolute accuracy*: equity estimates can be off by 0.1 or more, but errors on similar candidate positions are highly correlated and cancel in the ranking.
  - *Stochasticity*: dice give exploration and force termination, and make the true value function smooth; in deterministic games self-play may explore a narrow region or cycle forever; pathologies were seen in checkers and Go.
  - *Linear concepts first*: early learning captures what a function linear in the raw inputs can express, already better than beginners; context-dependent concepts come later.
- **Deterministic games need noise**: TD there "would require some sort of external noise source", for example randomised initial positions or probabilistic move choice; noise mattered in Isabell's Othello and Schraudolph's Go (Conclusion, L558-L563).
- **Raw-board linear evaluators are risky in chess/Go-like games**: "might not give very good play at all" without a suitable recoding of the raw board (L550-L556). That is the case for tuple/shape recodings.
- Untested ideas: decreasing lambda during training; "lesson-and-practice" (sessions against experts mixed with self-play); growing the network during training (L520-L531).

### Key lessons for us
1. Self-play TD(lambda) from zero knowledge can reach master level when the game supplies exploration; for a deterministic game like ours exploration must be injected.
2. Spend compute offline on evaluation learning and keep online search light: "massive computing power for extensive off-line training of a sophisticated evaluation function" (L535-L539).
3. What matters for move choice is relative accuracy among sibling positions (also what alpha-beta needs).

---

## 1.8 BAXTER99, BAXTER-TDL, BAXTER-KC (TDLeaf(lambda) and KnightCap chess)

The three files are versions of one body of work: BAXTER99 and BAXTER-TDL are "TDLeaf(lambda): Combining Temporal Difference Learning with Game-Tree Search" (arXiv cs/9901001; BAXTER-TDL is the HTML rendering with equations intact); BAXTER-KC is the companion paper "KnightCap: A chess program that learns by combining TD(lambda) with game-tree search" (arXiv cs/9901002) which has the full algorithm box and more experimental detail. The TDLeaf update appears as Eqs. 9-11 / Figure 3 in BAXTER-KC (L136) and as the single sentence at BAXTER-TDL §4 end (L143) / BAXTER99 §4 (the extraction jumps from Eq. 9 to prose).

### TD(lambda) for games (BAXTER-TDL §3, Eqs. 3-6; BAXTER-KC §2, Eqs. 2-5)
- Temporal difference `d_t = J(x_{t+1}, w) - J(x_t, w)`, terminal `J(x_N) = r(x_N)`. Batch update at the end of the game: `w := w + alpha * sum_{t=1}^{N-1} grad J(x_t, w) * sum_{j=t}^{N-1} lambda^(j-t) d_j`. **The papers print `d_t` inside the inner sum (BAXTER-TDL Eq. 4, BAXTER-KC Eq. 3), which must be a typo for `d_j`**, otherwise lambda would not matter **[UNCERTAIN: my correction of a printed formula]**. lambda=0 is one-step TD, lambda=1 is regression to the outcome; alpha "typically annealed towards zero". Tesauro's backgammon results are insensitive to lambda (around 0.7); Beal and Smith saw more sensitivity in chess and suggested tuning lambda dynamically (BAXTER-TDL §3, L108).

### TD-directed(lambda), TD on root search values, TDLeaf(lambda) (BAXTER-TDL §4; BAXTER-KC §3)
- `J_d(x, w)` = value of `x` from a depth-d minimax search using `J` at the leaves.
- **TD-directed(lambda)**: minimax *chooses the moves*, but temporal differences are computed from the plain evaluations of the successive game positions (Eq. 3).
- **TD on the root search value**: `d_t = J_d(x_{t+1}) - J_d(x_t)` with the gradient of `J_d` (Eqs. 8-9). `J_d` is differentiable almost everywhere in `w` because it equals `J` at the principal-variation (PV) leaf; ties (non-unique PV) are a measure-zero event (BAXTER-TDL §4, L139-L141).
- **TDLeaf(lambda)** (BAXTER-KC Figure 3, L136): for every game position `x_i` run the depth-d search; let `x_i^l` be the **leaf of the PV** (random choice among ties); `J_d(x_i) = J(x_i^l)`; `d_t = J(x_{t+1}^l) - J(x_t^l)`; `w := w + alpha * sum_t grad J(x_t^l, w) * sum_j lambda^(j-t) d_j`. "The only extra requirement ... is that the leaf nodes of the principal variations be stored throughout the game" (BAXTER99 §7). For an n-tuple table the gradient at the PV leaf is just the indicator of the table entries that fire at that leaf **[OWN]**.
- KnightCap's practical modifications (BAXTER-KC §4.1, L146-L158): squash leaf values `v = tanh(beta * J(x^l))` with beta chosen so that `tanh = 0.25` equals a one-pawn advantage; outcome +1/0/-1; **set positive temporal differences to zero** (an increase may only reflect an opponent blunder) unless KnightCap had predicted the opponent's move (later variant: only if the opponent was rated below KnightCap); keep the pawn weight fixed so weights read as multiples of a pawn (letting it float changed it by less than 7% in 1,764 games).

### Experiments (BAXTER99 §5-§6; BAXTER-KC §4)
- Chess: **linear** evaluation; BAXTER-KC says the current version has **5,872 features, 1,468 in each of four stages (opening, middle, ending, mating)**, many in groups of 64 (one per square) (L160); search 5-10 ply MTD(f) alpha-beta (BAXTER99 §7). Start: all weights zero except material (1, 4, 4, 6, 12 for P, N, B, R, Q). Learning **lambda = 0.7, alpha = 1.0**, one update per game (for part of one run every four games).
- Result: blitz rating on FICS **1650 -> 2110 (BAXTER99 §5) / 2150 (BAXTER-KC abstract and §4.1) in 308 games, about 3 days**; BAXTER-KC adds 2400-2500 on ICC (peak 2575) after adding opening-book learning, with the caveat that it "routinely gets crushed by faster programs searching more deeply". TD-directed(lambda) gave only +200 over 300 games (BAXTER99 §5, L285-L287). Rating SD about 50. The experiment was repeated twice with similar results; one repeat needed nearly twice as many games (limited hash memory).
- Initial conditions: starting with all weights = 1 pawn: 1260 -> about 1540 after more than 1000 games, "much slower" (BAXTER99 §5; BAXTER-KC says 1250 -> 1550). Starting near good weights matters. Learning from hand-coded weights: peak 2632 vs 2575 from material only (noisy); learned weights within 50-100 rating points of hand-tuned (BAXTER-KC §4.1 end).
- On-line vs self-play: 600 games of self-play lost a 100-game match to the FICS-trained version 11 to 89 (BAXTER99 §5; BAXTER-KC says "scored only 11%"). Reasons: deterministic play makes similar games; self-play positions unlike real opponents; on-line play against opponents of similar strength supplies both exploration and positive/negative signals (BAXTER-KC §4.2).
- Backgammon (BAXTER99 §6): from the FIBS weights (a 270,000-game TD(lambda) network) train 50,000 more games with TD-directed or TDLeaf, then 1,600-game matches at 2-ply vs the unmodified weights: **no statistically significant change**. For reference 2-ply beat 1-ply by 0.25 points/game and the 2-ply move differed from the 1-ply move in 24% of positions.
- Conclusion (BAXTER99 §7, L367-L375): "significant improvement training on the leaf nodes in chess" (linear eval, 5-10 ply), attributed to the "substantially different distribution over leaf nodes compared to root nodes"; no gain in backgammon, "the optimal network to use in 1-ply search is close to the optimal network for 2-ply search".
- Beal and Smith independently reported TDLeaf-style learning of material values with self-play plus some random move choice using lambda = 0.95 (BAXTER-KC §4.2 end).

### Key lessons
1. If the evaluation is used at the leaves of a deep search, train it on **PV leaves**. Cheap: store the leaf's feature indices per move.
2. Learning against diverse real opponents beat self-play by a wide margin in a deterministic game.
3. Start from sensible weights; bound outputs (tanh) so small evaluation noise does not create large TD errors; consider zeroing apparently lucky positive errors.
4. KnightCap's evaluation has stage-specific weights learned by the same update, but no ablation is reported.

---

# 2. Cross-paper lessons

All numbers are those reported in the cited sections; comparisons across papers are only indicative because games, opponents and budgets differ.

## 2.1 Measured gains, largest first (same-paper comparisons only)

| # | Change | Setting | Before -> after | Source |
|---|---|---|---|---|
| 1 | Temporal-coherence per-weight rate (TC, beta=1) instead of fixed-rate TD | 2048, 10^10 actions | 1-ply 141,456 -> 250,393 (+77%); 3-ply 237,026 -> 333,010 (+40%) | J2048 Table I, Table II, Table VII |
| 2 | Choosing the TD learning rate | 2048, TD(0.5) | alpha 0.01 -> 1.0: 41,085 -> 141,456 (1-ply) | J2048 Table I(a) |
| 3 | Stages **with** weight promotion + redundant encoding + carousel, on top of delayed-TC | 2048 | 3-ply 335,580 -> 491,398 (+46%); 1-ply 250,393 -> 311,426 | J2048 Table VII |
| 4 | MS-TD (3 stages, event-based, saved start boards) | 2048, 3-ply | rate of 32768-tile 0% -> 13.78%; avg 321,496 -> 350,394 | YEH Tables 1-2 |
| 5 | Systematic short straight tuples instead of random snakes (equal weight count) | Othello, ES | all-2 vs rand-10x3: 0.940 vs 0.868; all-3 vs rand-8x4: 0.938 vs 0.845; all-4 vs rand-7x5: 0.912 vs 0.801 | JASKSYS Table II, IV |
| 6 | Board inversion instead of output negation | Othello, ES | rand-8x4: 0.845 vs 0.770; all-1: 0.736 vs 0.595 | JASKSYS Table IV, §III-B |
| 7 | Redundant small tuples (12 vs 5 tuples) | 2048 | 3-ply 400,124 -> 469,779 (+17%); 1-ply 267,544 -> 319,433 | J2048 Table VII |
| 8 | Weight promotion | 2048, 2^4 stages | 1-ply 226,352 -> 267,544 (+18%); 3-ply 318,534 -> 400,124 (+26%) | J2048 Table III |
| 9 | Carousel shaping | 2048 | 3-ply 469,779 -> 491,398 (+5%), 1-ply 319,433 -> 311,426 (-2.5%) | J2048 Table IV, VII |
| 10 | Search depth on the same evaluator | 2048 | 1-ply 324,710; 3-ply 511,759; 100 ms 589,355; 1 s 609,104 | J2048 Table V |
| 11 | TDLeaf(lambda) on PV leaves vs TD-directed | chess | +460 to +500 Elo in 308 games vs +200 in 300 games | BAXTER99 §5; BAXTER-KC §4.1 |
| 12 | On-line play vs real opponents instead of self-play | chess (deterministic) | self-play version lost a 100-game match 11:89 | BAXTER99 §5 |
| 13 | Search on top of a linear shape evaluator | Go 9x9 | +1070 -> +1210 Elo (+140) | SILVER Fig. 5 |
| 14 | Combining all shape sets instead of the best single set | Go 5x5 | best single (2x2) > 80% -> combined > 90% | SILVER §6 |
| 15 | Hybrid coevolution(topology) + TD(weights) | Othello 12x6 | TD alone and CTDL both ~65-70% vs WPC, but CTDL wins the head-to-head tournaments | SZUBERT §V |

## 2.2 Tuple design: systematic vs random, long vs short

- **Systematic beats random at equal weights** (items 5 above). The best Othello network in the sources has 32 tuples of size 2 = 288 weights (JASKSYS §III-E). Evidence is from evolution strategies against a fixed opponent, not TD, and the authors say it "remains to be seen" whether it carries over to TD (JASKSYS §V). The 2048 work uses systematic tuples with TD throughout (J2048 §IV-B2), and Go uses exhaustive enumeration of templates (SILVER §4), so all three TD-based high-performing systems in the set use systematic shapes. The only TD-trained random-snake results are Lucas (30 snakes, 14,772 weights; beats the CEC 2006 champion in about 68-70% of games after 500 self-play games, LUCAS Table 1) and Szubert (TD on 7x4, 9x5, 12x6; 65-70% vs the WPC benchmark for 12x6, SZUBERT §V-A2).
- **Short tuples are enough in Othello**: all-2 equals all-3 and beats all-4 (JASKSYS §III-D); all-4 is slower to learn although it is better than any random network. More weights hurt evolution, but TD is not hurt by size: only TD improves when going 567 -> 8,748 weights (SZUBERT §V-A1) and 2048 networks have up to 1.3 billion weights (J2048 §V-D3).
- **Long tuples are needed where the pattern is the unit of meaning**: 2048 uses 6- and 7-tuples (16^6 and 16^7 entries, J2048 §IV-B2, §V-D3) and Go finds 2x2 best alone but needs 1x1-3x3 together (SILVER §6). Adding the *smaller sub-tuples as redundant features* gave a large gain in 2048 (item 7) and the cascade idea in Go does the same in a principled way.
- **Symmetric weight sharing** (8-fold in Othello, 2048, Go): consistently used, reported to speed learning (LUCAS §2, §3; J2048 §IV-B2: "facilitates generalization ... without the expense of increasing the size of the model").
- **Cell encodings used**: Othello ternary white=0, empty=1, black=2 (LUCAS §3.1; JASKSYS §II-D); 2048 empty=0, else log2(tile), capped at 16 values (J2048 §IV-B1); Go black/white/empty per intersection, colour symmetry by inverting colours for the side to move (SILVER §4); extra non-board features: large-tile counts (YEH §IV-A), liberty counts per stone (SILVER §9), empty-tile / mergeable-pair counts (YEH §IV-E). J2048 states the n-tuple net lacks *global* features such as the number of empty cells (J2048 §VII).
- **Learned patterns match human knowledge**: evolved Othello tuples gather around corners (SZUBERT §V-D); Go weights are interpretable (SILVER §11).

## 2.3 Learning rules, rates, and tricks

- **Basic rule is TD(0) on afterstates / successor positions with LUT gradient = indicator** (LUCAS Eq. 4; J2048 Alg. 1; SILVER Eq. 3). Squashing: tanh (LUCAS, SZUBERT, KnightCap), logistic (SILVER), none (2048 scores).
- **Step-size convention differs and matters**: Lucas adds `alpha*(tg-op)*(1-op^2)` with alpha=0.001 to *every* active entry (LUCAS Fig. 8); J2048 divides by the number of tuples (`alpha/m`) so alpha=1 zeroes the error of the visited afterstate (J2048 §IV-D); Silver divides by `m*n_j` with alpha=0.1 (SILVER Eq. 3); Yeh uses 0.0025 per weight then 0.00025 (YEH §IV-A, §IV-E). **[OWN]** Converting to "total change of the evaluation per update" (per-weight rate x number of active entries): Lucas ~0.001 x up to 240 = up to ~0.24; Yeh ~0.0025 x 32 = ~0.08; Silver 0.1; J2048 up to 1.0 (best for 2048, where each entry is visited rarely). The sensible overall range is therefore about 0.05 to 1.
- **Adaptive per-weight rates**: TC(lambda) with two shadow tables (signed and absolute error sums), `alpha_i = |E|/A` (J2048 §V-B1, Alg. 2) beat tuned fixed-rate TD by far (item 1) and beat Autostep (J2048 §V-B3). Bagheri et al. report adaptive methods beat standard TD on Connect Four but differ little between each other (J2048 §V-B, citing the Connect 4 study).
- **TD(lambda)**: eligibility traces over huge LUTs are impractical; use finite horizon (`h = ceil(log_lambda 0.1) - 1`, lambda=0.5, h=3) (J2048 §IV-D) or truncated multi-step returns computed offline after each game (YEH §IV-E, lambda=0.5, 5 steps). **Delayed update** (apply the decayed cumulative error h steps late) gives the same accuracy and 3x speed for TC (J2048 Table II).
- **Weight init**: zero for TD (SZUBERT §IV-A; SILVER §5; YEH §III); random only for evolutionary methods.
- **Exploration**: forced random moves with no update after the random move (SZUBERT §III-A), epsilon=0.1 (SILVER §6; LUCAS league protocol), none in 2048 (J2048 §IV-D: any non-greedy move hurt). Tesauro: deterministic games need external noise; KnightCap: self-play in a deterministic engine collapses to similar games and is far weaker than playing diverse opponents (TESAURO95 Conclusion; BAXTER99 §5).
- **Parallel training**: lock-free shared tables across 24 threads, no observed harm, 24x speedup (J2048 §V-A3).
- **Curricula**: board growing (SILVER §7), carousel over stages (J2048 §V-E1), saved stage start boards (YEH §III), hand-crafted initial weights (BAXTER-KC §4.1).
- **Output scale**: bounding the output keeps TD errors small (tanh with beta so that 0.25 = one pawn; BAXTER-KC §4.1).
- **Which player's view**: single weight set with board inversion (always evaluate as if the mover were "black") is better than output negation and better than separate weights for each colour (JASKSYS §II-F, §III-B; Silver also inverts colours, SILVER §4).

## 2.4 TD vs (co)evolution

- **Speed**: TD learned fast (LUCAS: beat the CEC champion within 500 games; SZUBERT: TD "learns rapidly, CEL advances slower") (LUCAS §5; SZUBERT §V-A2). Earlier work cited by Lucas: TD faster, CEL eventually better with parent-offspring averaging; on n-tuples CEL "less successful" (LUCAS §4).
- **Scalability**: only TD benefits from larger tables; Gaussian weight mutation fails above a few hundred weights (SZUBERT §V-A1, Fig. 4). JASKSYS also finds more weights worse for ES (§IV) and still needs 10^10 games for 288 weights (§III-A1).
- **Against a fixed heuristic** ES / EL gets the highest score vs that heuristic (JASKSYS 0.9592 with 288 weights; SZUBERT ETDL 87.1% best) but this is partly overfitting to that opponent: ETDL loses head-to-head to CTDL and EL wins only ~10% of games in the round-robin (SZUBERT §V-B, §VI); JASKSYS likewise notes the all-2 player is "not good in general" (§III-E).
- **Hybrid** (topology/opponent selection by coevolution, weights by TD, 100 TD games per individual per generation): best generalisation (SZUBERT §V-B, §V-C), and benefits from larger networks, which pure CEL does not.
- **Practical reading [OWN]**: TD (or TDLeaf) for weights; use a *population or archive of past snapshots* only as opponent/diversity source, never as the weight optimiser; do not make a single heuristic engine the fitness function.

## 2.5 Multi-stage tables (game-phase dependent weights)

- **Where used**: Logistello (cited in J2048 §V-D1); KnightCap 4 stages with 1,468 features each (BAXTER-KC §4.1); 2048 by Wu/Yeh (hand-picked stages) and J2048 (2^g stages).
- **Plain stages hurt, promotion fixes it**: 2^4 stages without promotion 226,352 (1-ply) vs 1-stage 251,033; with promotion 267,544 (J2048 Table III). 2^3 best at 1-ply, 2^4 best at 3-ply, 2^5 too many. The multi-stage model needs about 4x more actions to converge (J2048 §V-D3).
- **Alternative to promotion**: train each later stage from saved start positions collected at the stage boundary, weights from zero (YEH §III). Yeh reports big tail-event gains at 3-ply but a lower 1-ply score.
- **Stage selector**: largest tiles on board (J2048 §V-D1), first appearance of milestone tiles (YEH §III), and for chess a fixed set of phases (BAXTER-KC). Number of stages: 3 (Yeh, simple) to 5 best; 6 gave best average but fewer big-tile wins (YEH Table 3).
- **Interplay with search**: stage benefits appear at depth, not at 1-ply (J2048 Table III: 1-stage 1-ply 251,033 vs 2^4+WP 267,544 is only +7%, while 3-ply is +35% vs 296,207 -> 400,124).

## 2.6 TD-Leaf vs plain TD when the evaluator lives inside search

- **Evidence for training on search leaves**: KnightCap, linear eval, 5-10 ply: TDLeaf(lambda) 1650 -> 2110/2150 in 308 games; TD-directed only +200 in 300 games; the authors attribute the gain to the different distribution of leaf vs root positions (BAXTER99 §5, §7).
- **Evidence for no difference**: backgammon with a hidden-layer network at 2-ply, starting from a converged 1-ply TD network, no significant change after 50,000 games of TDLeaf or TD-directed training (BAXTER99 §6).
- **Indirect evidence in 2048** that eval trained for 1-ply is mis-calibrated for deeper search: 1-stage nets and the big 421-43 net "overfit" to 1-ply (3-ply score falls while 1-ply rises), while stages + carousel + redundant tuples improve 3-ply but not 1-ply (J2048 §V-D3, §V-E).
- **Caveat on KnightCap**: the same paper credits on-line training against many opponents, good initial weights, and a hand-built evaluator; it has not isolated TDLeaf from those (BAXTER99 §5).
- **Practical reading [OWN]**: for a deterministic game with a search-heavy engine, train the final stage with TDLeaf-style updates on PV leaves obtained from the production-like search, but bootstrap with cheap 1-ply TD self-play first.

## 2.7 Things that did not work or have limits

- Autostep (sophisticated adaptive step size) was worse than simple TC in 2048, perhaps because of non-stationary targets (J2048 §V-B3).
- Any exploration in 2048 (J2048 §IV-D); an extra stage split at `T_8k` (YEH Table 3); 2^5 stages (J2048 Table III).
- Large tuples (all-4) learned more slowly than all-2 in Othello (JASKSYS §III-D).
- Weight mutation at high dimension (SZUBERT §V-A1).
- Fixed-opponent benchmark saturation, "ceiling effect" (SZUBERT §V-A2).
- Very local linear evaluators miss global structure (Go: SILVER §11; 2048: J2048 §VII; chess tactics: BAXTER-KC §4.1).

---

# 3. Adaptation to Quantum Connect

Everything in this section is **[OWN]** inference unless it cites a source. I do not have the engine; sizes assume 8 rows x 12 columns, 6-in-a-row, cells in {empty, mine, theirs}.

## 3.1 What differs from the sources

1. Gravity: a legal drop is only into the lowest empty cell of a column. Empty cells therefore have an extra property that no source game has: **playable now** vs **supported later** (and distance to playable).
2. Symmetry group: only left-right mirror (gravity kills vertical flips and rotations). Weight sharing is 2-fold, not 8-fold, so tables need more data than Othello/2048/Go ones for the same number of tuples.
3. Lines of length 6 make the win condition a 6-window pattern; tuples of exactly the winning-window size are natural (in Othello, JASKSYS found 2-tuples sufficient because flips are line-based and local; here the *goal* is a 6-line, so I expect longer tuples to matter more than in Othello. This is a hypothesis; the sources give no Connect-4/6 result).
4. Deterministic game: exploration must be injected (TESAURO95 Conclusion; SZUBERT §III-A; BAXTER99 §5), unlike 2048.
5. The special move (every 10th own turn at most) changes up to 9 cells and shifts columns; it makes the cooldown a **state component** and gives the opponent a tactical defence (clear my threat) that static pattern values must anticipate.
6. Terminal states are tactical; the learned evaluator should score *quiet* positions, and threats should be resolved by the threat search (cf. KnightCap's weakness vs deeper searchers, BAXTER-KC §4.1; SILVER's +140 Elo from adding search, SILVER Fig. 5).

## 3.2 Perspective and cell encoding

- **One weight set, board inversion**: encode cells as relative to the side to move: 0 = empty, 1 = mine (side to move), 2 = theirs; value is "good for the side to move" (JASKSYS §II-F, §III-B: inversion > negation by 7.5 points mean, and lower variance). Empty = 0 matches J2048's convention (all-empty pattern = index 0, and "value == 0 means not yet accessed" promotion trick, J2048 §V-D2).
- **Tempo information lost by inversion**: in Connect-4-like games who moves first (parity of row/columns left) determines which threat rows are good. Keep one bit "mover is the player who moved first" either in the stage/context index or as a tiny extra table; check by ablation. (Mark: parity theory is weakened by special-move clears that change column heights by up to 3 cells; I could not verify with the engine.)
- **Four-state cell** (recommended experiment): split `empty` into `E0` playable now (cell directly above the stack) and `E1` supported later (some empty cell below it). Index base 4. The extra bit is free to compute from column heights and fixes the biggest blind spot of pure window patterns on a gravity board: whether the missing cell of a near-complete line can actually be played. Optionally `E2` = two or more empty cells below (5 states).

## 3.3 Tuple families (systematic, as in JASKSYS / J2048 / SILVER)

Counts for R=8, C=12, L=6, all derived by me.

| Family | Instances | Mirror classes | Entries per class (3 / 4 / 5 states) | Weights (3-state / 4-state) |
|---|---|---|---|---|
| Horizontal 6-windows | 8 rows x 7 starts = 56 | 8 x 4 = 32 | 729 / 4,096 / 15,625 | 23,328 / 131,072 |
| Vertical 6-windows | 12 cols x 3 starts = 36 | 6 x 3 = 18 | 729 / 4,096 | 13,122 / 73,728 |
| Diagonal 6-windows (both directions) | 3 x 7 x 2 = 42 | 21 | 729 / 4,096 | 15,309 / 86,016 |
| **All 6-windows** | **134** | **71** | | **51,759 / 290,816** |
| 7-windows (optional, sees open ends of a 5) | 48 + 24 + 2x(2x6)=24 -> 96 | about 50 | 2,187 / 16,384 | about 110k / 800k |
| 2x2 blocks (local shape, SILVER) | 7 x 11 = 77 | about 42 | 81 / 256 | about 3.4k / 10.8k |
| 3x3 blocks (matches the special-move clear area) | 6 x 10 = 60 | 30 | 19,683 / 262,144 | about 590k / 7.9M |
| Adjacent pairs and 3-in-line (redundant, folded in later) | 326 pairs, 272 triples | about 170 / 140 | 9 / 27 (3-state), 16 / 64 (4-state) | about 5.3k / 12k |
| Column height profile (adjacent 3 columns) | 10 | 5 | 9^3 = 729 | about 3.7k |

Notes:
- Vertical windows only have 127 reachable patterns of 729 (a gravity stack: k filled cells from the bottom with 2^k colourings, k=0..6, summing to 127), so most of that table is dead weight; it can be stored compactly.
- Location dependence: windows are tiny enough to keep location-dependent tables (rows matter under gravity; central columns matter). Following SILVER §8 and J2048 §V-E2, also add the **translation-invariant parent tables** (one 729-entry table per direction per start-row shared across all column offsets) as redundant features; at the end fold them into the specific tables.
- Folding redundant tables is exact only if each small-tuple instance is assigned to exactly one containing big-tuple instance (otherwise it is counted several times). J2048 §V-E3 says the redundant tables "can be integrated" afterwards; the assignment rule is my addition.
- 3x3 blocks: the special move clears a 3x3 block around the landing cell, so a table indexed by the 9 cells (4-state or 3-state) can learn "how much material is at stake in this region" and "what remains after the clear"; this is an [OWN] hypothesis. Use only in the final stage or shared across rows/columns because of size.
- Shapes from SILVER: the single best set was 2x2 and the best combination 1x1-3x3 (both ~1.5M weights in total for 9x9 Go). A combination of 6-windows (goal-shaped) with 2x2 and optional 3x3 blocks is the analogue.

## 3.4 Phase staging and weight promotion

- **Stage variable**: number of discs on the board (monotone except after a special move, which removes at most 9 cells and is available once per 10 own turns). Alternatives: total plies played, or maximum column height. Logistello-style Othello stages and KnightCap phases support disc/phase based staging (J2048 §V-D1; BAXTER-KC §4.1); the 2048 stage selector is "largest tiles", a monotone progress measure, which disc count is for us except for clears.
- **How many**: sources say 3 (YEH simple), 2^3 and 2^4 (J2048), 5 (YEH best). Start with **4 stages** (for example discs 0-23, 24-47, 48-71, 72+ of 96; thresholds to be set from the observed game-length distribution). Do not exceed 6.
- **Promotion is mandatory**: initialise a stage table entry from the previous stage on first access (zero-test trick, J2048 §V-D2), otherwise stages hurt (J2048 Table III). Because zero is also the TD init value for unvisited entries, store a float and treat exactly `0.0f` as "not yet set"; keep a separate bit-set if exact zeros occur.
- **Boundary jumps in alpha-beta**: parent and child may use different stage tables; the evaluation difference across a boundary is bias in the search. Mitigation: promotion keeps adjacent stages close; optionally blend the two nearest stages linearly by disc count at a small cost (two lookups). Not tested in any source.
- **Stage-aware training distribution**: carousel shaping (J2048 §V-E1): keep a buffer of the last 1,000 positions where each stage was entered, and start episodes from them cyclically. Needed for us because late stages are reached rarely when games are decided early; also a stronger version is to restart from random mid-game positions.
- **Number of weights**: 71 classes x 729 x 4 stages = 207k (3-state) or 1.16M (4-state), times cooldown contexts below.

## 3.5 Cooldown (special-move) state

- **State to track**: for each player, `turns_since_special` in 0..10 (or "available" flag if >= 10). Treated as part of the position everywhere (TD target, search, tables).
- **Features** (all small): `(my_special_available, opp_special_available)` as a 2-bit context that selects among **4 weight sets** for the whole network or for the threat-related tables only (mimics multi-stage tables; use promotion from the parent context on first access); plus a table indexed by `(my_countdown bucket, opp_countdown bucket)` of about 11 x 11 entries per stage as a bias.
- **Why contexts and not just a bias**: a window pattern that is a near-win is worth less if the opponent can clear it next move, and more if they cannot. A pure bias cannot represent that interaction; context-selected tables (or an extra input to the 6-window tables) can.
- **Size**: 4 contexts x 4 stages x 71 classes x 729 = 828k weights (3-state), 4.6M weights (4-state); as int16 about 1.7 MB or 9 MB.
- **Alternative if data is thin**: keep a single shared pattern table and add a small second table that depends on `(context, number of my 4/5-windows, number of their 4/5-windows)`.
- **Exploration of specials**: when generating training games make sure the special move is selected often enough (random moves must include it); otherwise tables for the post-special regions never get trained.

## 3.6 Learning recipe (staged, matching the sources' best practice)

1. **Phase A: bootstrapping TD** (cheap, all sources): self-play, 1-ply greedy on afterstate values, epsilon about 0.1 random moves (SILVER §6; LUCAS league), no update after a random move (SZUBERT §III-A), weights from zero, many random opening plies, mix in opponents from a pool of old snapshots (SZUBERT hybrid; BAXTER-KC on diversity). Sign handling with one weight set in the negamax form: `delta = r - V(s_t)` at the end, otherwise `delta = -V(s_{t+1}) - V(s_t)` where `V` is from the view of the side to move **[OWN]**.
2. **Learning-rate control**: per-weight TC with beta 1.0 (`E`, `A` shadow tables) and update scaled by `1/(number of active entries)` (J2048 §V-B). Use TD(lambda) with lambda=0.5 and horizon 3, and the **delayed update** buffer (J2048 §V-C). Expect TC to double memory and cost about 1.5x (J2048 §V-B3).
3. **Staging with promotion** and carousel (J2048 §V-D, §V-E1) once the single-stage network is trained to saturation, because staged networks need roughly 4x more training (J2048 §V-D3).
4. **Redundant encoding** during training (pairs, triples, translation-invariant parent tables), folded afterwards (J2048 §V-E2-3).
5. **Phase B: TDLeaf(lambda)** with the production search (alpha-beta + threat search, depth/time reduced): store the PV leaf's active table indices per root, compute `d_t` between successive PV leaf values **in a fixed player's view** (the sign flips each ply under inversion) **[OWN]**, apply the update at the leaf indices (BAXTER-KC Fig. 3). Use tanh with beta such that a typical one-disc advantage is about 0.25, outcome +1/0/-1, and consider zeroing positive errors that follow an opponent blunder (BAXTER-KC §4.1). The evidence for TDLeaf over plain TD is from one chess experiment (see 2.6); treat as a hypothesis to test by head-to-head matches at equal time per move.
6. **Evaluate by matches**, not only against a fixed heuristic: round-robin of snapshots/hybrids and against the previous generation under the real 100 ms limit (SZUBERT §V-B/C; the ceiling effect and overfit-to-benchmark warnings).
7. **Compute budget (indicative)**: the sources need 5x10^2 games (Lucas), 3x10^6 (Szubert), 5x10^6 per stage (Yeh), 10^7 episodes = 10^10 actions (J2048, 1-7 days on 24 cores), 10^10 games (JASKSYS ES). For an 8x12 board with games of about 40-90 plies I would plan for about 10^6-10^7 self-play games for phase A and 10^5-10^6 searched games for phase B. The 8 GB GPU can host batched self-play with embedding-style gathers on the tables (lookup cost dominated by memory access, J2048 §V-C; lock-free races harmless, J2048 §V-A3) **[OWN, untested]**.
8. **Optional phase C [OWN, not in the sources]**: dump (position, search value or outcome) pairs from phase B and re-fit the tables by minibatch regression on the GPU (the model is linear in one-hot features); useful if TD noise limits quality. Logistello-style regression fits of pattern weights are mentioned in LUCAS §2.1 and SILVER §1.

## 3.7 Inference in Java (stdlib only) [OWN]

- Precompute for each window instance: its 6 cell indices, its table id, and the stride vector (powers of 3 or 4). Evaluation is a loop over the 134 window instances plus extras: 6 reads + 1 table lookup each (J2048 §IV-B1: cost O(m) memory accesses).
- Store tables as `short[]` fixed point (scale chosen from the trained range; JASKSYS Fig. 7 shows evolved Othello weights of magnitude up to about 285 before squashing), serialised in a single resource file in the jar, loaded with `DataInputStream` + `Inflater` (stdlib).
- Incremental update on a normal drop: only the up to 4 x 6 = 24 windows through the dropped cell change; for the special move (up to 9 cleared cells plus gravity shifts in up to 3 columns) recompute all windows. For the 4-state encoding, the "playable" status of the cell above a changed cell also changes (one extra cell per drop).
- Estimated cost: about 134 x 7 = ~940 array reads for a full evaluation; incremental about 24 x 7 = ~170. At Java speeds I expect on the order of 10^6 full evaluations per second per core, so at 100 ms per move the evaluator is not the bottleneck for a search of a few hundred thousand nodes. This is an order-of-magnitude guess; for reference J2048 reports 258,371 moves/s at 1-ply with 12 tuples x 8 symmetric images and 20,524 moves/s at 2-ply (J2048 Table V).
- Mirror: to apply left-right sharing, precompute for every window instance the table id of its mirror class and, if needed, the reversed cell order so that both orientations read the same table consistently (LUCAS §3.2: same relative ordering across symmetric expansions).

## 3.8 Size summary (my estimates)

| Configuration | Weights | int16 size |
|---|---|---|
| 134 six-windows, 3-state, 1 stage, no contexts | 51,759 | 0.1 MB |
| same, 4 stages x 4 cooldown contexts | 828,144 | 1.7 MB |
| same with 4-state cells | 4.65M | 9.3 MB |
| + 2x2 and translation-shared parents, 4 stages x 4 contexts | about +60k | negligible |
| + 3x3 blocks, 3-state, 30 classes, 2 coarse stages only | about 1.2M | 2.4 MB |
| 7-windows, 4-state, 4 stages x 4 contexts | about 12.6M | 25 MB |

All of these fit comfortably into a jar. Training memory is about 3x (weights + TC `E` and `A`), still far below 8 GB; the GPU is useful for throughput, not for capacity.

---

# 4. Open questions

1. **Does systematic beat random for TD?** JASKSYS compares tuple sets only under ES; 2048 uses only systematic tuples; no source trains both with TD at equal weights (JASKSYS §V). For a 6-in-a-row connection game, which window family (exact 6-windows, plus 7/8 windows, plus 2x2/3x3 blocks) is best is unknown.
2. **Is board inversion also better under TD** (JASKSYS tested ES only), and how much does it lose in a gravity game where the first-move parity matters?
3. **Does the 4-state (playable / supported later) cell encoding help**, or do 6-windows + column heights already carry it? No source studies gravity boards; Thill et al.'s Connect Four n-tuple work is only cited (JASKSYS §II-E1) and not in the provided texts.
4. **Stage definition without a monotone progress measure**: disc count is not monotone after a special move; is a cooldown-aware or move-number stage better? Stage-boundary discontinuities inside alpha-beta are untested in all sources.
5. **TDLeaf vs plain TD for n-tuple LUTs**: the only positive evidence is chess with a linear evaluator and on-line opponents; backgammon showed no gain. Whether it helps a depth-limited alpha-beta with a one-hot LUT evaluator is unproven; cost of PV storage is trivial but the training search is expensive.
6. **Sign and perspective bookkeeping of TD errors under board inversion** (negamax TD) is not discussed in any source; my formula in 3.6 should be verified on a toy game.
7. **Exploration in a deterministic game with a special move**: epsilon-greedy vs softmax vs random openings vs opponent pools; SZUBERT, SILVER and TESAURO95 give only the generic advice. No source quantifies the benefit of exploration alone.
8. **Learning rate for a small-table, 134-active-entries network**: the sources span 0.08-1.0 total step; TC makes this less critical (J2048 Table I) but adds time. The best beta for non-stochastic games is unknown.
9. **Size of training needed**: no source trains a connection game of this size; my 10^6-10^7 estimate is extrapolated.
10. **Interaction between the learned evaluation and the threat search**: nothing in the sources covers threat-space or proof-number search combined with a learned evaluator. KnightCap and Go show search adds strength, but do not address the feedback between an evaluator trained on search leaves and a separate tactical module.
11. **Can the special-move vulnerability be learned by a 3x3 block table**, or is it better as hand-built features (for example number of discs of each colour in each 3x3 area after clear)? Unstudied.
12. **Details missing from the provided texts**: SZUBERT's numeric alpha, epsilon, sigma and Table I; the TD-Gammon equation, network size, lambda, alpha, game counts of the released versions, and Tables 1-3 (both Tesauro files); the geometry of 2048 tuple shapes (figures); Silver Figure 3 row alignment; Lucas's training-time exploration. A direct look at the original PDFs is needed for these.

## Discrepancies found inside the source set

- `pdf-p1-krawiec2012-...` is actually the 2013 TCIAIG paper by Szubert, Jaskowski, Krawiec (extraction title in the file; first lines of the text).
- KnightCap: final rating 2110 (BAXTER99 §5) vs 2150 (BAXTER-KC abstract, §4.1) after 308 games; self-play result "11:89 points" (BAXTER99) vs "11%" (BAXTER-KC).
- The same Othello League player `epTDLmpx_12x6` is listed with 3,240 weights (JASKSYS Table III) while a 12x6 network should have 8,748 (SZUBERT §IV-A); not explained.
- JASKSYS: 0.9592 (text, §III-E) vs 0.9584 (Fig. 7 caption and the "random WPC opponents" sentence) for the best all-2 network.
- TD(lambda) formula in both Baxter papers prints `d_t` inside the inner sum where `d_j` is intended.
