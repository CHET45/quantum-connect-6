# Digest: pattern-table evaluation fitting (Logistello / GLEM), evaluation tuning (Texel, SPSA, ordinal correlation, TreeStrap) and NNUE

Purpose: re-implement the best learned-evaluation pipeline for "Quantum Connect" (8x12 gravity board, six in a row wins, special 3x3-clear move at most once per 10 own turns, 100 ms/move, Java standard library only, hand-written integer inference, weights embedded in a jar, offline training in PyTorch on an 8 GB GPU).

Conventions used throughout:
- Every claim carries a citation `[alias, section/page]`. The alias table below maps each alias to the exact file name in `sources\text\`.
- `[OWN]` marks my own inference, arithmetic, or design proposal (not a statement of any source). `[DERIVED]` marks arithmetic on source numbers. `[UNCERTAIN]` marks garbled or ambiguous source text, or a reconstruction.
- Win-percentage-to-Elo conversions are mine: Elo = 400*log10(p/(1-p)) [DERIVED].

## 0. Source status, aliases and a warning about text quality

| alias | file | status |
|---|---|---|
| B94 | pdf-p1-buro1994-an-evaluation-function-for-othello.md | .md is a pdftotext glyph cipher (unreadable). Read from sibling PDF `sources\pdf\p1-buro1994-...pdf`, cited by PDF page. |
| B95 | pdf-p2-buro1995-probcut-an-effective-selective-extension.md | .md unreadable. Read from sibling PDF, cited by section/page. |
| B97 | pdf-p1-buro1997-experiments-with-multiprobcut-and-a.md | .md unreadable. Read from sibling PDF, cited by section/page. |
| B98 | pdf-p1-buro1998-from-simple-features-to-sophisticated.md | .md readable; garbled formulas and Table 1 cross-checked against the PDF. |
| B99 | pdf-p3-buro1999-how-machines-have-learned-to.md | .md readable (two interleaved columns; whole IEEE "Trends and Controversies" issue; Buro's essay is pp.12-14, Schaeffer's p.10-11). |
| B02 | pdf-p1-buro2002-improving-heuristic-minimax-search-by.md | .md unreadable. Read from sibling PDF, cited by page. |
| B03 | pdf-p3-buro2003-the-evolution-of-strong-othello.md | .md unreadable (Type3 fonts). Read from sibling PDF, cited by PDF page. |
| GOM | pdf-p2-gomboc2003-evaluation-function-tuning-via-ordinal.md | readable; Greek letters dropped, formulas reconstructed. |
| VEN | pdf-p2-veness2009-bootstrapping-from-game-tree-search.md | readable; some symbols dropped. |
| LOGH / LOGT / LOGP | buro-logistello-home.md / buro-logistello-tour.md / buro-logistello-publications.md | readable web pages. |
| BH, CPWM | buro-home.md, cpw-michael-buro.md | readable; little technical content. |
| TEX | cpw-texel-s-tuning-method.md | readable, but ALL formulas are image placeholders (not fetched). |
| AT | cpw-automated-tuning.md | readable; mostly taxonomy and bibliography. |
| SPSA | cpw-spsa.md | readable. |
| SL, EV, PR | cpw-supervised-learning.md, cpw-evaluation.md, cpw-pattern-recognition.md | readable; thin on numbers. |
| PC | cpw-probcut.md | readable (read as a secondary source for ProbCut). |
| SFD | stockfish-nnue-pytorch-nnue-doc.md | readable, 2982 lines; a newer revision (includes Full_Threats, PP_3Wide). Explicitly excludes datasets, hyperparameters and a training tutorial [SFD, Preface]. |
| NNUE | cpw-nnue.md | readable; formulas/images missing. |
| SFN | cpw-stockfish-nnue.md | readable. |

Important consequences:
1. Five of the nine Buro/Gomboc-era files cannot be read as text. Everything attributed to B94, B95, B97, B02, B03 was read visually from the rendered PDFs by the readers who produced the working notes; numbers read from tables are exact, numbers read off plots are flagged. A re-check of key B98/B99 numbers directly in the readable .md files matched (250 iterations, 30 h, Pentium II/333, eleven million positions, 13 stages, 88,000 configurations, about 310 thousand hash entries, "1.2 million", "74%", "72%", "80%", "6-0").
2. Nothing in any source concerns Connect-style gravity games, six-in-a-row, special clear moves, Java, or a 100 ms budget. All transfer statements are [OWN].
3. The sources give no Elo for Logistello; strength is reported as win percentages in 70-434 game matches.

---

## 1. Logistello and GLEM (detailed)

### 1.1 Generations of the evaluation function (what changed and what it was worth)

| generation | features and combination | how table values are obtained | reported effect |
|---|---|---|---|
| IAGO (Rosenbloom 1982) | hand-crafted formula e(p) = C1*EdgeStab + 36*InternalStab + C2*CurMobility + 99*PotMobility; constants guessed [B03 p.3] | none (guessed) | baseline |
| BILL (Lee and Mahajan 1990) | table-indexed line features combined by a quadratic discriminant, one covariance/mean set per game stage, 60 stages [B03 pp.3-4] | probabilistic minimax tables | beats IAGO 100% with 20% of the thinking time [B03 p.4] |
| Logistello-1 (1993-94) | pattern tables plus line-approximated mobility and potential mobility, combined by stage-dependent logistic regression [B94 pp.1-5; LOGH Development History; B03 pp.4-5] | each table entry estimated independently as a conditional mean (counts) | dominated computer Othello until Hannibal in 1996 [B03 p.5]; "entirely table based... an order of magnitude faster" than BILL/IAGO [B03 p.5] |
| Logistello-2 (1997) | same patterns minus mobility, plus parity; plain sum of table lookups [B97 §§3-6; B03 p.5] | all configuration weights fitted JOINTLY by large sparse linear regression | strength gain over Logistello-1 equal to about 2 extra plies, "a speed-up factor of about ten" [B97 §6 Table 1; B02 Table 1; B03 p.5] |
| GLEM (Buro 1998) | general framework: weighted sum of Boolean conjunctions of atomic relations, optional increasing link function [B98 §2] | steepest descent least squares, 11M positions, about 1.5M weights [B98 §6] | formalizes Logistello-2; adds automated configuration generation |

Why joint fitting won: independent estimates ignore correlations between overlapping patterns (for example a corner disc's value is overestimated because several patterns cover the corner), and the old system compensated with large weights on mobility features that line patterns could already represent [B03 p.5; B97 §6; B02 p.9]. The 1997 function even dropped mobility features and was still much stronger, with the same patterns and the same example set [B97 §6]. This is the single most important design lesson: fit overlapping pattern tables jointly, never by independent averaging [B03 p.5].

### 1.2 The 1994/95 pattern set (Logistello-1) [B94 p.2 Fig. 2, pp.3-5]

A pattern is any set of squares; an instance assigns {empty, black, white} to each square; the value of an instance is the conditional expectation of a random variable (game outcome win=+0.5/draw=0/loss=-0.5, or final disc difference) given that instance [B94 p.2].

Twelve shapes in Fig. 2 (3^n entries each, base 3 indexing) [B94 p.2, p.3 "3^8 = 6561"]:

| shape | squares | configs 3^n [DERIVED] |
|---|---|---|
| diagonal 5, 6, 7 | 5, 6, 7 | 243 / 729 / 2187 |
| main diagonal | 8 | 6561 |
| edge (row 1) | 8 | 6561 |
| horizontals 2, 3, 4 | 8 each | 6561 each |
| 2x4 corner | 8 | 6561 |
| 2x5 corner | 10 | 59049 |
| edge+2X (edge plus b2, g2) | 10 | 59049 |
| 3x4 corner | 12 | 531441 |

- Length-4 diagonals exist in the mobility approximation (only diagonals shorter than 4 are dropped) [B94 pp.1-2, p.5] [UNCERTAIN whether they carry a pattern table in the 1995 set].
- Non-pattern features: mobility approximated as a sum of 34 line-table values (8 rows + 8 columns + 18 diagonals), plus potential mobility (adjacent opponent-disc/empty pairs) also summed over lines [B94 pp.1-2]. Early versions spent about 50% of their time on exact legal-move counting, which is why it was replaced [B94 p.1]. The exact mobility is only slightly better than its approximation in the opening, and not better in the endgame [B94 p.2, Fig. 4].
- Side to move is ignored in the tables; reasons given: the right to move can only be exploited in one region, averaging both cases is more robust, it gives more examples per instance, and colour symmetry cuts table size by roughly 50%. Early experiments showed no advantage for distinguishing it [B94 pp.2-3].
- Symmetry sharing: the paper says only that pattern and colour symmetry are exploited [B94 pp.2, 4]. The number of symmetric board copies per table is not stated there. From the "at most five line- and 2x4-corner-indices change per square" remark [B94 p.5] and the later formula (section 1.3) the copies are rotations/reflections [OWN reading].
- Stages: defined by disc count, separate tables and weights per stage; the stage count is not given in B94; B03 says the Logistello-1 tables used 13 stages [B94 p.5; B03 p.4]. The 10- and 12-square patterns use ONE table for all stages because there were too few examples [B94 p.4].
- Training data: about 60,000 games of early KITTY and Logistello versions plus about 20,000 Logistello games generated while extending the book; about three million positions, each labelled win/draw/loss by negamaxing the final game results through the tree built from all games [B94 p.1, p.4 "about 80,000 games"]. Positions from search trees were rejected because the side to move is overwhelmingly favoured in the majority of visited positions, which produces a spurious side-to-move bonus and oscillating evaluations in iterative deepening [B94 p.3]. Footnote 5 mentions a new idea weighting training positions to favour even positions and using searched positions [B94 p.3].
- Held-out split: the example set was divided into two parts; table estimation used the first, discordance was measured on the second to avoid overfitting. The ratio is not stated [B94 p.4].
- Table value estimator (counts, NOT regression) [B94 p.4]:
  - `V(p) = (Y(p) + 0.5) / (N(p) + 1.0) - 0.5`, N = positions containing instance p, Y = wins for Black plus half the draws. The constants 0.5 and 1.0 give instances never seen a neutral value 0.
  - For the large patterns (10 and 12 squares): `V(p) = alpha*(Y(p)+0.5)/(N(p)+1.0) + (1-alpha)*(Y(sub)+0.5)/(N(sub)+1.0) - 0.5`, `alpha = min{1, N(p)/40}`, where sub is a smaller sub-pattern marked by dots in Fig. 2 (the 2x4 corner for 2x5, the plain edge for edge+2X, the 3x3 corner for 3x4; read from the small figure) [B94 p.4] [UNCERTAIN on the 2x5 and 3x4 sub-patterns].
  - Smoothing between adjacent phases and extrapolation to phases where an instance is rare (for example corner occupation in the opening) are mentioned but the algorithm is not described [B94 p.4].
- Combination: stage-dependent logistic regression (P(win | features) = 1/(1+exp(-sum w_i f_i))) over all pattern values and the mobility approximations. Compared in tournaments (30 min per game, 100 nearly even book openings) against a quadratic discriminant and Fisher's linear discriminant; logistic regression gave the strongest program (scores not printed) [B94 pp.4-5]. The solver for logistic regression is "iteratively solving nonlinear equation systems" and is a one-time offline task [B97 §2; B03 p.5]; no runtime is given.
- Quality measure: "discordance" = fraction of mis-ordered pairs among all pairs of positions with different outcomes, measured on the held-out set [B94 p.1 fn.2, p.4]. Figure 4 (read off the plot, +-1 point [UNCERTAIN]): the full logistic combination is at about 8-9% mid-game and about 5% late, versus 10-30% for single features; the new 2x4-corner pattern beat the traditional edge pattern [B94 p.3-4, Fig. 4].
- Table combination for speed: because diagonal/row/column indices are used three times (mobility, potential mobility, pattern value), all three weighted contributions are precomputed into ONE table per stage so each line costs one lookup and no multiplication; this sped up the search by about 75% at the cost of memory [B94 pp.5-6]. This is exactly "fold the weights into the tables", the same trick we need for integer inference.
- Incremental update: board as 10x10 byte array; per line a base-3 index; after a move, `for (i=0;i<5;i++) *(p->Index[i]) OP= p->OFFSET[i]` with OFFSET a flip- or place-offset array; undoing by negated offsets is faster than copying the board with its indices [B94 p.5]. For the 10/12-square patterns it was faster to compute them from the 2x4-corner and edge indices at the leaves than to maintain them incrementally [B94 p.5].

### 1.3 The 1997 evaluation function (Logistello-2) [B97 §§3-6; B02 pp.6-8; B03 p.5]

Eleven pattern types; the sum has 46 table lookups per position plus one parity term [B97 §5 (46 table indices); B02 p.7-8 formula; B03 p.5; B99 Schaeffer p.11 "11 patterns that, with reflections and rotations, come to 46"]:

| pattern | squares | configs 3^n [DERIVED] | instances per position in the formula |
|---|---|---|---|
| diag4 | 4 | 81 | 4 |
| diag5 | 5 | 243 | 4 |
| diag6 | 6 | 729 | 4 |
| diag7 | 7 | 2187 | 4 |
| diag8 | 8 | 6561 | 2 |
| hor./vert. 2 | 8 | 6561 | 4 |
| hor./vert. 3 | 8 | 6561 | 4 |
| hor./vert. 4 | 8 | 6561 | 4 |
| edge+2X | 10 | 59049 | 4 |
| 2x5 corner | 10 | 59049 | 8 |
| 3x3 corner | 9 | 19683 | 4 |

- The formula (verbatim structure) [B02 p.7-8; B03 p.5]: `f(p) = ( [f_d4,s,1 + ... + f_d4,s,4] + ... + [f_2x5,s,1 + ... + f_2x5,s,8] + [f_3x3,s,1 + ... + f_3x3,s,4] + f_parity,s )(p)` with `s = stage(p)`. Each f_{x,s,i} is one table lookup into the table of pattern x at stage s; all occurrences (rotations/reflections) share that one table, so symmetric configurations share weights [B02 p.8; B98 §6].
- The first eight patterns deal with features that can be approximated on lines (mobility-like); the last three cover edge tactics and corner access/parity [B97 §4; B02 p.6]. Mobility features were omitted entirely [B97 §6; B02 p.9]. Pattern SHAPES were hand-chosen "by many experiments which considered the univariate accuracy of many more patterns and the combined pattern evaluation speed"; automatic pattern generation is future work [B02 §2.2 p.7]. Length-3 patterns were "not expressive enough" [B97 §4].
- Parity feature: number of empty squares modulo 2, stage-dependent [B97 §5; B02 p.7; B03 p.5].
- Game stages: 13, `stage(p) = max{0, floor((#discs(p) - 13)/4)}`, i.e. 13-16, 17-20, ..., 61-64 discs [B97 §5]. (The older function had twelve stages from 12 discs [B97 §2]; B03 says 13 for the 1994 tables, so the stage count differs between sources [UNCERTAIN].) No run-time interpolation between stages is described: evaluation uses the single table set of stage(p) [B97 §5; B03 p.5].
- Parameter count: "about 110,000 variables" for the proposed linear model "when taking symmetries into account" [B97 §4] [UNCERTAIN whether per stage; sum of 3^n over the table types is 167,265 raw per stage, so 110,000 is plausibly per stage after mirror symmetry, about 1.4-1.5M for 13 stages [DERIVED]]; B98 states about 1.5 million weights [B98 §6]; B02, B03 and B99 state "over 1.2 million" [B02 p.8; B03 p.5; B99 Buro p.13; B99 Schaeffer p.11]. The sources do not reconcile 1.2M vs 1.5M.
- Weight storage: 16-bit integers; one 10-square table for all 13 stages costs 3^10 * 2 bytes * 13, about 1.5 MB [B98 §6]. Evaluation speed with this function: about 160k nodes/s mid-game on a Pentium Pro 200 (1997) [B02 p.8; LOGH Program overview], about 270k nodes/s on a Pentium II/333 [B98 §6].

Training data and labels (1997) [B97 §5]:
- The same set as before: about three million positions from about 60,000 KITTY/Logistello games plus 20,000 further games; labels obtained by negamaxing the final game results through the game tree (exact for late positions, approximate early), target = game-theoretic final disc difference r(p) in [-64,64] from the side to move [B97 §§3-5].
- Later versions: "Several million training positions labeled with either the true mini-max value or an approximation were generated from self-played games" to fit about 1.2M weights [B02 p.8; B03 p.5]; GLEM run used about eleven million scored positions [B98 §2, §6]. The site notes (Jul 1997) that adding book-correction positions and generating rare positions to fill table gaps improved the evaluation, and that "17 million training positions" existed but are not distributed [LOGH Recent Improvements, README].

Fitting method and solver (1997 paper) [B97 §4-5], reconstructed from the PDF:
- Model `f(p) = w_0 + sum_i sum_j w_i^(j) f_i^(j)(p)` with indicator variables for each feature value; occurrence-count trick shares ONE weight among the symmetric occurrences (for example the four corners) [B97 §3, p.3].
- Loss: mean squared error `E(w) = (1/N) sum_k Delta_k(w)^2`, `Delta_k(w) = r(p_k) - sum_i w_i f_i(p_k)`; n about 110,000 variables makes matrix inversion impractical, so steepest descent ("backpropagation"); conjugate gradient is named as possibly faster [B97 §4].
- Sparse update: one pass over the data accumulates `s_i = sum_k Delta_k f_i(p_k)` touching only the non-zero features of each position, so cost is proportional to the number of active configurations, not to the number of variables [B97 §4].
- Modifications actually used: start at `w^(0) = 0`; update `w_i += 2*beta*s_i*min{1, N_i/50} / N_i` with N_i = number of examples in which feature i is non-zero. Dividing by N_i instead of N equalizes convergence speed between frequent and rare configurations; `min{1,N_i/50}` is a "muting" factor that damps rarely seen configurations (their estimates have high variance and cause search errors). Because E is convex the iteration converges, but it is deliberately stopped early so the muting remains effective. beta = 1 in the reported run [B97 §4-5].
- Stage smoothing: each example contributes to its own stage and to the stages s+-1 and s+-2 (five stages) [B97 §5]. Missing values: if a configuration never occurs in stage s, take the nearest earlier and later stages that do contain it and linearly interpolate between their estimates; with no example in one direction, use 0 as the end-point parameter [B97 §5].
- Cost: each iteration about 4 minutes of CPU on a Pentium Pro 200 (reads about 3M compressed positions, about 54 MB, from disk, and computes 46 indices per position); stopped after 300 iterations, about 20 hours, at max weight change 0.07 and mean 0.008 [B97 §5-6].
- Quality: Fig. 3 plots mean absolute error (disc units) per stage against iteration; curves flatten after about 100-150 iterations; plateaus (read off the plot, [UNCERTAIN]) fall from about 10 discs at 17-20 discs on the board to about 3-4 at 57-64 discs [B97 §5]. No held-out error and no regularization other than muting and interpolation are described [B97 §5].

Evidence of strength gain (same patterns, same examples) [B97 §6 Table 1; identical rounded values in B02 Table 1]:
- Table: winning % of a player using the PREVIOUS evaluation at depth d (+0), d+1, d+2 against the NEW evaluation at depth d (140 games each, 70 nearly even 14-disc openings, perfect solve 16 plies before the end).
  - +0: 33.6 32.1 31.8 35.0 34.3 30.7 26.1 26.4 31.8 for d = 1..9.
  - +1: 58.9 56.1 46.4 56.8 50.4 41.4 44.3 40.4 37.9.
  - +2: 82.5 74.6 70.0 62.1 61.4 59.3 47.9 50.7 53.6.
  Reading: at equal depth the old function scores only 26-35% (about -90 to -180 Elo [DERIVED]); it needs about +2 plies to draw level at d >= 7; the authors summarize it as a speed-up factor of about 10 that "is otherwise only achievable by parallelization" [B97 §6; B02 p.8-9].

### 1.4 GLEM: generalized linear evaluation model [B98 §§2-7; restated in B02 §2.1]

Definitions [B98 §2]:
- Atomic features A with integer values; relations `(f(.) = k)`; a configuration is a conjunction of relations; `val(c(p)) = 1` if all relations hold, else 0.
- Model: `e(p) = g( sum_{i=1..n} w_i * val(c_i(p)) )` with weights w_i and an increasing differentiable link function g (identity, `2C/(1+exp(-x)) - C`, or the logistic function) [B98 §2, §5.1]. Because val is 0/1, evaluation needs no multiplications; because g is monotone, search can compare raw sums and skip g [B98 §2; B02 p.4].
- Weights minimize `E(w) = (1/N) sum_k (r_k - g(sum_i w_i h_{i,k}))^2`, `h_{i,k} = val(c_i(p_k))` [B98 §5.1]. Gradient (garbled in the .md, reconstructed): `dE/dw_i = -(2/N) sum_k g'(.) * eps_k * h_{i,k}`; with the logistic link `g' = g(1-g)` [B98 §5.1] [UNCERTAIN on sign glyphs only].
- Honest caveat: the model itself is not new; with a complete atomic set it can represent any function. Its value is the hierarchy of sub-models obtained by restricting the atomic set, the number of configurations or their structure [B98 §2]. B02 calls it two-level feed-forward networks with binary inputs, "an expressiveness matched only by large neural networks" [B02 §2.1-2.2].
- Othello atoms are the squares themselves: values {0,1,2} = white disc, empty, black disc [B98 Fig. 7; B02 Fig. 2]. In chess one would need relations such as "A attacks B" [B98 §4.1].

Automatic generation of configurations (GenConf) [B98 §4.2]:
- Inputs: atomic features A, training set E, minimal match count n. Start with all single relations matching at least n positions; repeatedly specialize every new configuration by adding a relation (using a total order to avoid duplicates); keep those matching at least n positions; stop when none are left. Primary guard against overfitting is the match-count threshold n.
- Run time O(|C| * |R_A|^2 * |E|) [UNCERTAIN exponent placement]. Speed-ups: precomputed bit sequences per relation (match count = AND plus popcount, with the `x & (x-1)` loop because bits become sparse); a sequential statistical test (MatchHeuristic) that reads the training set in chunks and decides early using a confidence parameter t [B98 §4.2, Fig. 2].
- Reduction options: raise n, prune by significance (correlation with the score), limit configuration size, restrict to subsets of A [B98 §4.4]; B02 adds that inclusion depends on match count and correlation with the outcome [B02 p.7].
- Reality: Logistello's pattern SHAPES were hand-chosen and only the configurations inside them were data-driven; GLEM automation for Othello was research, not the shipped route [B02 p.7; B98 §6].

Patterns as flat tables [B98 §4.4]:
- `pattern[f1..fm]` = all most-specific configurations; a flat table indexed by feature values (example `3*f1 + f2`); match counting and validity checking by incrementing a counter per index; incremental index updates after moves; fast but narrower scope.
- Sparse (large) patterns: for patterns with more than about 10-12 squares a flat 3^n table is too big; configurations are split into two index groups (i1, i2); address `offset[i1] + i2` into a hash table with a stored "lock" i1 to reject invalid pairs; offsets assigned greedily, most frequent i1 first, first-fit. Tested 16-square patterns: about 88,000 valid configurations (n = 75, about 11M positions) fit in hash tables of about 310 thousand entries [B98 §4.4, §6, Fig. 8].
- Outcome of large patterns [B98 §6 Table 1, 434 games, 10-10 min]: versions with large patterns beat the baseline 55.8% and 55.5% (patterns A or B alone, 11% slower), 53.7% (A+B) and 54.3% (A+B+C) because of the slowdown (nodes ratio 0.89 to 0.80); at reduced time (6-10, 7-10, 8-10 minutes) the A version scores 46.4%, 48.5%, 52.2%. Binary-search access to sparse tables caused about 45% slowdown and wiped out the gain; only fast hashing plus just one extra pattern gave the best result [B98 §6]. [DERIVED: +5.8% win rate is about +40 Elo.]

Training data advice (the most reusable part) [B98 §3]:
- No theory of training-set generation exists. Simple scheme: label every position of a game database with the game result; include blunder games because high-quality play hides major features by keeping them balanced.
- Better: generate positions by searches from played-game positions (save a random subset of searched positions), score them by deeper search (more reliable than the static evaluation), and fit stages backward: endgames can often be solved exactly, fit the last stage first, then label the previous stage's positions by search with the newly fitted evaluator, and so on.
- Add balanced positions to tune minor features.
- Side-to-move bias: randomly sampled positions from minimax searches are biased toward the player to move (most searched moves lose), producing an artificial side-to-move bonus and unstable odd/even depth behaviour. Cure: also add the principal-variation successor positions, labelled with the negated search result.
- Target encoding: for a probability-style output use won=0.9, draw=0.5, lost=0.1 (1.0/0.0 can make an optimal weight vector non-existent with a sigmoid link); for score games use the final score with the identity link [B98 §5.1].

Fitting [B98 §5]:
- Plain steepest descent; one pass over the data per iteration; optional momentum term; conjugate gradient is mentioned as faster for correlated features but not used because "the basic algorithm works sufficiently well in practice and is easier to implement" [B98 §5, §5.1 fn.4]. No Gauss-Newton, Levenberg-Marquardt, Gauss-Seidel or explicit L2 regularization appears in any source [B98 §5; B02].
- Rare features: dividing each weight's gradient sum by the number of non-zero h_{i,k} (instead of N) lets rare configurations reach their level; stage-dependent training: fit each stage also from adjacent stages, or per-stage count rules (count high, for example 20: fit normally; count low, for example 4: weight 0; in between: merge adjacent stages, share one weight, or use `w(k) = a*k + b` over an ordered stage code with a, b updated inside gradient descent) [B98 §5.1-5.2].
- Othello run: start from w = 0; about 30 hours on a Pentium II/333 to reach acceptable accuracy after 250 iterations with about 11M positions and about 1.5M weights [B98 §6]. Schaeffer reports "roughly a month of computation" for the whole self-play plus regression loop [B99 Schaeffer p.11].

Why regression worked, in the authors' words [B99 Buro p.13; B03 p.5, pp.7-8]:
- Fitting even a large number of weights from training positions is not hard (unlike choosing features); many simple binary features with a linear combination model non-linear effects directly; joint regression handles correlations; the table-based evaluation is an order of magnitude faster than BILL/IAGO, so search gets deeper.
- Scope: direct pattern learning works when patterns come naturally from the raw board (8x8 Othello, 8x8 Lines of Action, backgammon); chess and Go need an extra layer of atomic features; for 10x10 Othello "the described pattern learning technique requires refinement due to the need for larger patterns" [B03 pp.7-8]. This warning matters for our 8x12 board [OWN].

### 1.5 Opening book and other Logistello components (brief)
- Book learning (pioneered in Logistello): build a tree from played lines, label leaves by results, evaluate the best unplayed move at each interior node and add it, propagate by minimax, expand principal leaves [B99 Buro p.14; B02 §4 pp.14-15; B03 p.7]. No data sizes or gains are given [B02 §4]. The site mentions about 23k games in the book and a skeleton of about 37K lines that are at least 24-ply WLD-correct [LOGH]. Not needed for our 100 ms game unless we want deterministic openings [OWN].
- Hardware/search context: NegaScout with corner quiescence and multi-ProbCut, hash table 2^21 entries, iterative deepening, endgame WLD from 26-22 empties [LOGH Program overview]. Logistello beat world champion Takeshi Murakami 6-0 in August 1997 [B03 pp.1-2; B02 p.2; LOGT; CPWM].

### 1.6 ProbCut and Multi-ProbCut regressions (Logistello's search-side learned parameters)

ProbCut [B95 §§2-3]:
- Model: `v = a*v' + b + e`, e ~ N(0, sigma^2), where v' is the value of a depth-d' search and v of a depth-d search of the same position. Fail-high cut when `v' >= (Phi^-1(p)*sigma + beta - b)/a`; fail-low cut when `v' <= (-Phi^-1(p)*sigma + alpha - b)/a`; implemented as zero-window shallow searches around `bound = round((+t*sigma + beta - b)/a)` [B95 §2 p.3, Fig. 2 code; B97 §7; PC Pseudo Code].
- Parameters: d' = 4, d = 8, t = 1.5 (p about 0.93); a, b, sigma fitted per disc-count stage on about 2,000 (v', v) pairs; examples: 28 discs a=1.036, b=-0.009, sigma=0.542, r^2=0.971; 44 discs a=0.956, b=-0.067, sigma=0.884, r^2=0.963 [B95 §3, Figs. 3-4]. The evaluation output was then log-odds with resolution 0.0001 (value 2 = P 0.88) [B95 p.4 fn.3].
- Threshold selection by match play (35 balanced openings, both colours, 30 min): win % for t = 1.20/1.35/1.50/1.65/1.80/2.00 is 62.9/65.7/74.2/70.0/70.7/60.0 [B95 Table 1].
- Effort and strength: 12-ply searches over 50 book positions: speed-up (nodes) 6.6 (12 discs), 6.0 (20), 6.4 (28), 5.4 (36), 4.3 (44), with the same move chosen 84-100% of the time [B95 Table 2]; selective at 30 min vs full-width at 60 min scored 64.3% [B95 §3 p.6].
- Stated caveats: needs a relatively stable evaluation or a quiescence search; fixed (d', d) makes the branching factor creep back up with depth [B95 §4].

Multi-ProbCut (MPC) [B97 §§7-10]:
- Generalizations: cuts at several heights recursively, several check searches of increasing depth, stage-dependent thresholds. Not adopted: replacing the linear opinion-change model by one with extra features ("quickly computable features are hard to find, especially with quiescence search") [B97 §8].
- Structure: parameter table `param[stage][height][try]` (a, b, sigma, check depth d, threshold t); check searches call plain alpha-beta (zero window) so no recursion in the check, while the main search recurses into MPC at every height up to 13; known inexactness (shallow brute-force predicts deep MPC value) is ignored [B97 §8 Fig. 7].
- Regressions: by disc number (stage), height h in 2..13 and check depth d < h on "thousands of example positions" computed by brute force up to depth 13; no a/b/sigma table is printed [B97 §9]. Check depths: h = 3..13 uses d1 = 1,2,1,2,3,4,3,4,3,4,5; second check d2 = 5,6,5 only at heights 9, 10, 11 [B97 §9 (Table 2 on p.8)].
- Thresholds by iterated tournaments, last stage first: only two stage classes, t = 1.0 below 36 discs and t = 1.4 from 36 discs [B97 §9]; ProbCut(4,8) with the new evaluation again best at t = 1.5 [B97 §10].
- Results (2 x 70 games, Pentium Pro 200): ProbCut vs brute force 70%, MPC vs brute force 80%, MPC vs ProbCut 72%, MPC vs MPC with a single threshold 56% [B97 Table 3]; at depth 10+ MPC is stronger than brute force even at 1:8 time odds; with equal time it looks 5-7 plies deeper in selected lines; new evaluation plus MPC together "more than ten" speed-up [B97 §§10-11]. [DERIVED: 80% is about +240 Elo; 72% about +165 Elo.]
- Chess evidence: Crafty regressions (about 2700 positions): (3,5) middlegame a=0.998 b=-7.0 sigma=55.8 r=0.90; (4,8) middlegame a=1.02 b=2.36 sigma=82 r=0.82 [PC "ProbCut or MPC in Chess"]. Mixed outcomes: MPC Crafty beat plain Crafty vs Yace, but Hyatt and Allgeuer reported unchanged or worse strength, "tactical strength is severely reduced" [PC]. Jiang's explanation: null-move pruning already prunes similar positions in chess and chess searches make more mistakes (larger sigma) [PC].
- Relevance to us: six-in-a-row with forced threats resembles the chess-like regime (tactical, sharp), not the quiet-evaluation Othello regime [OWN]. Treat ProbCut as an optional later experiment, only with a threat-aware quiescence step, and always validate by match play [B95 §4; PC].

### 1.7 What the Logistello sources do NOT say (re-implementation gaps)
- Exact stage boundaries beyond the formula above; any run-time interpolation between stages [B97 §5; B03 p.5].
- Final train/validation error values for the 1997/98 regression, and any train/test split for the GLEM fit (neither B97 §5 nor B98 §6 describes one; only B94 p.4 uses a held-out set, for discordance).
- Learning-rate schedules other than beta = 1; momentum value; convergence criteria; regularization other than muting and interpolation.
- How the final Logistello evaluation unit relates to win probability (Logistello-1 output was log-odds [B95 fn.3]; the 1997 function is a disc-difference regression with no stated conversion [B97 §3-5]).
- Which fraction of the 1997 positions were exactly solved vs search-labelled; position counts per stage.
- Any gravity-board, k-in-a-row, or special-move treatment [all].

---

## 2. Tuning methods

### 2.1 Texel's tuning method [TEX]

What it is: Peter Osterlund's method (Texel 1.03+), described as "a concrete instance of a logistic regression"; Ballicora proposed a similar idea in 2009 (Gaviota) [TEX intro; AT Logistic Regression]. AT lists the lineage: Buro 1995 (Othello), Paterek (Gosu), Ballicora 2009, Amir Ban 2012 (Junior), Begue (named it in Jan 2014) [AT Logistic Regression].

Data [TEX Method]:
- 64000 fast games (1 s + 0.08 s/move) between current and previous versions; all positions except those inside the opening book and those where the engine found a mate score; about 8.8 million positions as FEN, about 140 positions per game [TEX Method, Concerns].
- Label R_i = game result of the whole game, 0 = black win, 0.5 = draw, 1 = white win (White-relative); score q_i = White-relative quiescence-search score of the position (qScore must be deterministic; transposition tables or history heuristics inside qScore may break this).

Loss (the page shows images only; standard form reconstructed [UNCERTAIN, not in source]):
- `E = (1/N) * sum_i ( R_i - sigmoid(q_i) )^2`, `sigmoid(s) = 1 / (1 + 10^(-K*s/400))` with s in centipawns. The page links a plot of "log-linear 1 / (1 + 10^(-s/4)), s = -10..10", which has the same base-10 shape [TEX footnote 12]. The exact scaling of K is therefore [UNCERTAIN]. Others describe the same thing as a sigmoid-mapped MSE [SL Value Adaption].

K: "Compute the K that minimizes E. K is never changed again by the algorithm." Osterlund's K = 1.13 best matched his then-current evaluation and was never changed; "it is just an arbitrary scaling constant" [TEX Method, Results]. How K is minimized numerically is not stated [OWN: a 1-D line search].

Optimizer: "The exact optimization method is not that important": local search one parameter at a time, Gauss-Newton, conjugate gradient, or a hybrid; for integer parameters difference quotients replace the gradient [TEX Method]. Pseudocode in the source is coordinate descent: for each parameter try +1, else -1, accept if E decreases, loop until a full pass yields no improvement [TEX Pseudo Code]. He suggests a few Gauss-Newton iterations first, then local search for small corrections [TEX Future improvements].

Cost: about 400 parameters; computing the gradient about 25 minutes on a 16-core machine; a local minimum "usually" within 6 hours; the method optimizes "several hundreds" of parameters simultaneously; his guess is that CLOP would need "at least ten years" for 400 parameters (an opinion) [TEX Advantages].

Measured gains (32000 games at 1+0.08): `24.6 + 4.0 + 5.8 + 2.8 + 12.8 + 39.4 + 10.2 = 99.6 Elo` over successive stages [TEX Results]:
- 24.6 first exposure of most evaluation parameters; 4.0, 5.8, 2.8 exposing more parameters; 12.8 regenerating positions from recent games; 39.4 from NOT filtering out positions where the q-search score deviated much from the in-game search score (his reasoning: q-search has to handle them in real games anyway); 10.2 splitting the queen piece-square table into middlegame and endgame.
- He doubts the gain generalizes (starting evaluation may have been poor) [TEX Results].

Concerns recorded [TEX Concerns, Future improvements]: correlation is not causation (cannot learn what the engine does not understand, for example it may never win KBNK); positions within one game are not independent (accepted, "64000 independent events is more than enough to estimate 400 parameters"); learned odd values for rare contexts (for example white queen on b7/g7 in the middlegame -128 cp, king on b8 +200 cp, knight on a8 -223 cp) show that sparse parameters absorb confounders.

Not in the sources: data sizes or Elo for Grant, Zurichess, Arasan, Ethereal and other variants (titles only), lambda blending of search score and result, regularization, mini-batching details [TEX External Links; AT; see section 6].

### 2.2 SPSA [SPSA]

- Algorithm (pseudocode quoted in the source): `alpha = 0.602; gamma = 0.101; for k: a_k = a/(k+1+A)^alpha; c_k = c/(k+1)^gamma; Delta_p = +-1 (Rademacher, probability 1/2); Theta+ = Theta + c_k*Delta; Theta- = Theta - c_k*Delta; Theta += a_k * match(Theta+, Theta-) / (c_k * Delta)` [SPSA Automated Tuning]. Two objective measurements per iteration regardless of dimension (FDSA needs p+1) [SPSA intro; sentence slightly garbled]. Spall recommends alpha = 0.602 and gamma = 0.101 as the lowest values that theoretically guarantee convergence [SPSA Automated Tuning]. [OWN: classical gradient estimate is g_k = (y(Theta+) - y(Theta-)) / (2 c_k Delta); the pseudocode folds the outcome difference into `match`.]
- Engine use: Stockfish's tuner (Kiiski 2011) measures the objective once per iteration by playing ONE game pair Theta+ vs Theta-, `match` returning a value in a +-2 range [SPSA Automated Tuning] [UNCERTAIN how the +-2 is formed; likely two game results summed].
- Page states that A, a, c, alpha, gamma are "critical to the performance" but gives no values for A, a, c, no per-parameter scaling advice, no game counts, no Elo gains [SPSA]. RSPSA (RPROP + SPSA; Kocsis, Szepesvari, Winands 2005) discusses common random numbers and antithetic variates and was applied to Poker and Lines of Action (no numbers) [SPSA RSPSA].
- Advantages/disadvantages (taxonomy): works with all engine parameters including search and takes search-eval interaction into account; time complexity grows with the number of weights [AT Mathematical Optimization].

### 2.3 Ordinal-correlation tuning (Gomboc, Marsland, Buro) [GOM]

- Objective: maximize Kendall's tau-a between machine assessments (search scores) and human assessments of positions, not move ranks. Human labels are the 7 Chess Informant symbols (white winning, clear advantage, edge, equal, and mirrored) [GOM §1, Table 1, fn.1]. `tau = (S+ - S-) / (n(n-1)/2)` with S+ concordant pairs, S- discordant pairs, n positions; extra y-pairs and duplicates cap attainable values [GOM §3, Table 2]; worked check S+=162, S-=83, n=25 gives 0.2633 [GOM Table 3] [DERIVED: (162-83)/300 = 0.2633].
- Efficient computation: dynamic programming over a 7-row table of human classes by unique machine values, linear in unique machine values (Spearman cannot be done this way) [GOM §3].
- Data: 649,698 positions from Chess Informant volumes 1-85; 32,768-position subset used for tuning; engine Crafty 19.1 modified to floating-point evaluation (2-3 times slower); 16,384 nodes searched per position; nodes, not depth, as the effort unit; with 1,024 nodes results were mixed [GOM §4, §5.1, §5.4].
- Optimizer: per weight a bracket V_high = 1.01 * V_current and V_low symmetric (minimum distance near zero); evaluate tau at three points; if monotone, interpolate with a bounded step; if the current point is a maximum, take the apex of the inverse parabola; update all weights each iteration (1 + 2*11 = 23 weight vectors per iteration) [GOM §5.2]. About 2 minutes per weight vector (32,768 x 16,384 nodes); best case 30 iterations per hour with enough parallelism [GOM §5.5].
- Tau versus depth (n = 649,698): depth 1: 0.2136; 2: 0.3704; 3: 0.4093; 4: 0.4975; 5: 0.5186; 6: 0.5656; 7: 0.5753, with odd-even effects [GOM Table 4]. Interpreted as tau tracking decision quality.
- Results with 11 tuned weights (king-safety scales, pawn scales, bishop, knight, rook terms) [GOM §6.2]:
  - From all weights initialised to 50: score vs default Crafty (5 min games) Nunn-30: 5.83% (iteration 0), 12.5, 40.8, 56.7 (300), 55.0, 51.7, 49.2 (600); Dailey-200: 2.38, 6.88, 35.0, 51.0, 50.1, 44.6, 49.5. Best by iteration 300-400; most tau gain came from bishop and knight values (both about 285) [GOM Tables 6-7].
  - From the default weights: no improvement (Nunn-30 50.8, 52.5, 45.0, 46.7, 34.2, 42.5 at iterations 25..150); king-safety scale drifted to about 4x default and the asymmetry factor below -100; holding those two fixed gave no significant difference from defaults [GOM §6.2.2, Table 8].
  - Headline: a handicapped program was restored to "grandmaster strength" but never beat the hand-tuned defaults [GOM §7].
- Limits: needs expert-annotated positions or a stronger oracle; search effort per candidate vector; only 11 weights tried; small n = 32,768 raises overfitting risk; Deep Thought's team found maximizing agreement with grandmaster moves "not really the same thing" as playing stronger [GOM §2, §7].
- Relevance to us [OWN]: for a pattern-table model with about 10^5-10^6 parameters this method (per-weight bracketing, hundreds of search-based evaluations per iteration) does not scale; the same ordinal idea survives only as an evaluation metric (discordance/pairwise ranking error, as in B94 p.1) on held-out data.

### 2.4 TreeStrap (Veness, Silver, Uther, Blair) [VEN]

- Idea: regress the heuristic toward the value of a deep search at EVERY node of the search tree; a depth-k search with the updated heuristic then matches depth k+D with the old one [VEN §1]. Differences from Samuel/TD-Leaf: updates all tree nodes rather than one; target is the search value from the current root, not a later position, which makes it less dependent on opponent strength [VEN Abstract, §1]. KnightCap needed expert-initialised piece values and could not exceed weak-amateur level from zero [VEN §1, §6.1].
- Backups [VEN Table 1, reconstructed]: TD-Root `H(s_t) <- V^D_{s_{t+1}}(s_{t+1})`; TD-Leaf: gradient at the principal leaf; RootStrap `H(s_t) <- V^D_{s_t}(s_t)`; TreeStrap(minimax) `H(s) <- V^D_{s_t}(s)` for all s in the tree; TreeStrap(alpha-beta) `H(s) <- [b(s), a(s)]` bounds for all s in the alpha-beta tree [UNCERTAIN on typography].
- Updates (linear heuristic H(s) = phi(s)^T theta) [VEN §3, §4]:
  - RootStrap: `delta_t = V(s_t) - H(s_t); d_theta = eta*delta_t*phi(s_t)`.
  - TreeStrap(minimax): `delta_t(s) = V^D_{s_t}(s) - H(s); d_theta = eta * sum_{s in tree} delta_t(s) phi(s)`.
  - TreeStrap(alpha-beta), one-sided: `delta^a = a(s) - H(s)` if H(s) > a(s) (else 0); `delta^b = b(s) - H(s)` if H(s) < b(s) (else 0); `d_theta = eta * sum_s (delta^a + delta^b) phi(s)`. Exact values (no cutoff) reduce to the minimax update. Bounds are read from the transposition table by `DeltaFromTransTbl(s, d)`, which recurses over successors and uses only entries searched to depth at least d [VEN §4.1, Algorithm 2] [UNCERTAIN: recursion argument may be dropped by a typo; the paper does not say how repeated visits are handled].
- Setup (chess engine Meep): 1812 features, mostly binary, about 100 active per position (material, piece-square tables, pawn structure, mobility, king safety); weights start as small random numbers; terminal values -9999/0/+9999; evaluation clipped to +-9900; training searches kept simple (alpha-beta with transposition table, killers, history; quiescence; no null-move, no weight-dependent pruning); self-play at 1 min + 1 s per game; plain SGD with step sizes 1.0e-5 (TD-Leaf, RootStrap(ab)), 1.0e-6 (TreeStrap minimax), 5.0e-7 (TreeStrap ab); minimum depth parameter d = 1; no RMSprop, momentum or Adam anywhere in the paper [VEN §5, §6.0.1].
- Results (about 16,000 games, BayesElo with the untrained player fixed at 250, 95% CI) [VEN Table 2]: TreeStrap(ab) 2157 +-31; TreeStrap(minimax) 1807 +-32; RootStrap(ab) 1362 +-59; TD-Leaf 1068 +-36; untrained 250. TreeStrap learns effective weights in about a thousand training games [VEN §6.1]. Online ICC blitz: self-play training 1950-2197, training against Shredder 2154-2338; the learned evaluation was about 3x slower than the original hand-crafted one, at about 350K nodes/s on one 1.8 GHz Opteron [VEN §6.2, Table 3]. Claimed first algorithm to reach master level from random weights by self-play [VEN §7].
- Caveat: the only truth signal is terminal values and search depth; no game result is used [VEN §5-6] [OWN: for a game with frequent forced wins within the horizon this self-bootstrapping may behave well, but that is untested here].
- Relevance to us [OWN]: the tree-wide regression is mathematically a sparse linear regression with search values as labels; for pattern tables (gradient of the sum of looked-up entries is a 0/1 vector) it applies directly, and its "all nodes + bounds" labels offer a cheap way to multiply training data per search. It needs searches stored with bounds (transposition-table dump), which complicates a Java pipeline; start with root and PV-leaf labels.

### 2.5 Other tuning/learning remarks in the CPW pages
- AT is a taxonomy: black-box optimizers (CLOP, genetic algorithms, PBIL, simulated annealing, SPSA) are listed with "works with all engine parameters including search" and "time complexity issues with increasing number of weights"; reinforcement learning (Samuel, TD(lambda), TD-Leaf, RootStrap, TreeStrap); supervised learning (move adaptation and value adaptation: "can modify any number of weights simultaneously - constant time complexity" but needs labelled data and "works not optimal when combined with search"); regression (logistic model of win percentage against advantage; MSE or cross-entropy; "using cross-entropy error function instead of sum of squares leads to faster training and improved generalization") [AT Playing Strength, Mathematical Optimization, Reinforcement Learning, Supervised Learning, Regression]. No numbers for CLOP, Adam, Bayesian optimisation, GA/PBIL.
- Strength measurement needs "up to several tens of thousands" of games for close opponents; SPRT is used to terminate matches early [AT Playing Strength].
- Althofer's "telescoping" theorem: for linear evaluations with many terms a small subset with the right weights is almost as good [AT Parameter].
- SL: value adaptation uses game outcomes {-1,0,+1} or {0,1/2,1} through a sigmoid and MSE; move adaptation (Marsland 1985) uses the rank of the desired move [SL Value/Move Adaption]. EV and PR add no numerical content relevant to pattern tables (EV warns that tuning needs independent features, contrasted with Texel's claim that the method removes the need for orthogonal terms [EV Linear vs. Nonlinear; TEX Advantages]).
- The Logistello publication list names "Features are More Important than Weights" (IJCNN'99) and the Gomboc/Buro/Marsland concordance paper [LOGP Honors, Tech reports]; contents are not in the files.

---

## 3. NNUE (implementation level)

All numbers from SFD unless marked; SFD "does not contain datasets, optimizers, hyperparameters, a log of experimental results, or a training tutorial" [SFD Preface]. Items not covered are listed at the end of this section.

### 3.1 Principles and history
- NNUE = "Efficiently Updatable Neural Network". Three principles: few non-zero inputs (current architectures have sparsity about 0.1%); inputs change little between evaluations; the net is simple enough for low-precision integer inference [SFD Basics / What is NNUE?]. Principle 1 is the reason large nets stay fast; principle 2 (incremental update) is "optional" but measurably helpful; principle 3 enables CPU inference "without batching or accelerators"; target is millions of evaluations per second per thread [SFD What is NNUE?].
- Floating point "is not an option for maximum engine strength"; quantization error accumulation is negligible for such shallow nets [SFD Quantization 101].
- Typical depth is 2-4 layers because most knowledge is in the first layer; further layers add little and "may even be harmful if quantization is employed due to error accumulation" [SFD What layers are useful; Further layers].
- History: invented for shogi by Yu Nasu, integrated into YaneuraOu in May 2018; ported to Stockfish by Hisayori Noda (Nodchip) in June 2019; merged into Stockfish master in August 2020; Stockfish 12 released September 2, 2020 [SFD What is NNUE?; SFN intro].
- Measured gains (the only ones in the sources): at least +80 Elo on Fishtest in August 2020 over classical evaluation despite roughly halved search speed; the hybrid patch (NNUE only on balanced material) gave up to +10% speed and +20 Elo; NNUE added "around 100 Elo" at that time; Isozaki predicted +100 [SFN intro, Hybrid; NNUE Stockfish NNUE]. No Elo is given for architecture changes, feature-set changes, factorizer, quantization or loss settings [SFN Elo gain; SFD].

### 3.2 Feature sets and index formulas
- Simplest set "A": 64 x 6 x 2 = 768 binary inputs, input passed as a list of active indices (all values 1). `index = side*64*6 + pieceType*64 + square`; perspective flip: `side = 1 - side; square ^= 0b111000` [SFD A simple input feature set; NNUE Input Layer, Perspective]. Updates: quiet move changes 2 inputs, capture 3, castling 4 [NNUE Efficient Updates].
- HalfKP: (own king square, piece square, piece type excluding king, piece colour): 64*64*5*2 = 40960 inputs; `p_idx = piece_type*2 + piece_color; halfkp_idx = piece_square + (p_idx + king_square*10)*64` [SFD HalfKP]. The real network has 41,024 = 64*(64*10+1) inputs per half (extra 64 unused entries, a shogi relic), 16-bit weights, 256 outputs per half, 256*41,024 = 10,502,144 weights [SFN NNUE Structure / HalfKP; SFD HalfKP]. About 10 million first-layer parameters, "about 20MB after quantization" for 256x2->32->32->1 [SFD Consideration of networks size / Feature set]. At most 30 active rows per perspective [SFD Quantization / Feature Transformer]. King moves force a refresh of that perspective's accumulator [SFD HalfKP].
- Two perspectives, each with its own feature list; combined as concatenation (stm first for tempo, output relative to side to move), or interleaved [SFD How to combine multiple accumulator perspectives]. Shared weights require oriented boards (Stockfish uses a 180-degree rotation inherited from shogi, `xor 63`, "arguably incorrect for chess, but worked surprisingly well"); separate weights per perspective are possible and simpler [SFD Which set of weights to use for each perspective?; SFN HalfKP].
- HalfKA (kings included), HalfKAv2 (11 piece buckets instead of 12 per king square, 11*64*64 = 45056 per side, 8% smaller), HalfKAv2_hm (horizontal mirroring so the own king is on files e-h; halves the feature transformer; strength difference "hardly measurable"; training data effectively 1-2x more useful) [SFD HalfKAv2, HalfKAv2_hm; SFN HalfKA; NNUE Horizontal Mirroring]. Full_Threats and PP_3Wide are newer add-ons (attack pairs and pawn-pair features, i8 weights) [SFD Full_Threats, PP_3Wide].
- Factorization: HalfKP specializes P-features per king square, so rarely seen rows learn slowly; add "virtual" denser features (for example the 640 plain (square, type, colour) features) during training only; after training coalesce: `W[real] += W[virtual]` for every king square, valid because the first layer is linear [SFD Feature factorization, Virtual feature coalescing]. It "seems to only be relevant in the early stages" and can be removed later [SFD Real effect of the factorizer]. Clipping with virtual layers: clip real weights to `[min - virtual, max - virtual]` [SFD Accounting for virtual layers].

### 3.3 Architecture
- Generic template: `FeatureSet[N] -> M*2 -> K -> 1`: Linear N->M, ClippedReLU on the 2M concatenation, Linear 2M->K, ClippedReLU, Linear K->1 [SFD Example network]. First Stockfish net: `HalfKP[41024] -> 256x2 -> 32 -> 32 -> 1` [SFD Simple HalfKP Stockfish architecture]. SF14: HalfKAv2 11x64x64, 2x520 feature transformer, eight `512x2->16->32->1` sub-networks chosen by `(piece_count - 1) / 4` [SFN HalfKA; SFD Multiple subnetworks]. The PSQT/skip-connection mechanics are cited but not documented in the files [NNUE; SFN].
- Layer stacks: evaluate all stacks as one wide linear layer and gather the chosen outputs (`nn.Linear(2*L1, L2*count)`, index offset trick); suggested to initialise stacks identically; discriminator `(piece_count - 1)/4` = 8 buckets [SFD Multiple subnetworks]. CPW's simple output buckets: `bucket = (popcount(occupancy) - 2) / DIVISOR` with `OUTPUT_BUCKETS = 8; DIVISOR = (32 + OUTPUT_BUCKETS - 1)/OUTPUT_BUCKETS` [NNUE Output Buckets].
- Activations: ClippedReLU `min(max(x,0),1)`; SCReLU `clamp(x,0,1)^2` ("most prevalent and produces the strongest network", not auto-vectorizable); pairwise/product pooling halves the width (divide by 128 instead of 127 in the integer version, "has to be accounted for in the trainer") [NNUE Output Layer table, Pairwise Multiplication; SFD Pooling layers]. Quantmoid4 (approximate sigmoid(4x) in integers) exists but is not needed for simple nets [SFD Quantmoid4].
- Basic non-Stockfish NNUE: 768 -> HL (x2 perspectives) -> 1 with `int16 accumulator_weights[768][HL]`, `accumulator_biases[HL]`, `output_weights[2*HL]`, `output_bias` [NNUE Basic NNUE]. "Larger hidden layers are usually preferred as Elo gain from speedup tends to scale inversely with time and computational power"; top engines use accumulators of 1024+ [NNUE Accumulator]. Multi-layer nets are stronger but at 10 s + 0.1 s the extra compute can lose [NNUE Multiple Layers].

### 3.4 Accumulator and incremental update
- Accumulator = bias + sum of weight columns of active features (first-layer pre-activation), one per perspective. Refresh: `acc[i] = bias[i]; for a in active: acc[i] += W[a][i]`. Update: for removed features `acc -= W[r]`, for added `acc += W[a]` [SFD Accumulator, Refreshing/Updating]. Weights stored feature-major (column contiguous) so adding a column is one contiguous pass [SFD Layer parameters].
- Floating-point accumulators drift ("error accumulates with each move"): do not undo by subtracting; store accumulators per ply on the search stack. With integer accumulators updates are exact, but the quantization scheme "must be chosen such that no combination of possible active features can exceed the maximum value" [SFD Accumulator].
- Refresh triggers: king move (HalfKP/KA), crossing the mirror line, crossing a king-bucket boundary; threats only when the king crosses the d-e files [SFD HalfKP, Full_Threats; NNUE Horizontal Mirroring, King Input Buckets]. Copy-make ply-indexed accumulator arrays, lazy updates (mark dirty, update at evaluation, useful because many nodes need no evaluation), fused add/sub, and "Finny" accumulator refresh tables are the standard implementation refinements [NNUE Copy-Make, Lazy Updates, Fused Updates, Accumulator Refresh Table].
- int16 accumulator SIMD: keep all registers live and store once; 16 int16 per AVX2 register; beyond 256 neurons Stockfish needs multiple passes over the feature list [SFD Optimized implementation / Feature Transformer, First set of hidden neurons].

### 3.5 Quantization (Stockfish scheme and the CPW scheme)

Stockfish scheme [SFD Quantization; The math of quantization]:
- Hidden activations are int8 in 0..127 (s_A = 127). Feature-transformer weights and biases are multiplied by 127 and stored as int16; accumulators are int16 (0..30 rows), converted to int8 by the ClippedReLU (`packs_epi16` saturating at 127 then max with 0).
- Dense layers: int8 weights with scale s_W = 64 (max float weight 127/64 = 1.984375, step 1/64 = 0.015625), int8 inputs, int32 accumulation, int32 biases; after the layer `>> log2_weight_scale` (= 6), then clamp to 0..127 and narrow to int8 [SFD Linear layer, `_mm_srai_epi32(outval, log2_weight_scale)` and the ClippedReLU layer].
- Scaling derivations: hidden layer: scale weights by s_W, biases by s_A*s_W, divide the layer output by s_W. Output layer: scale biases by s_W*s_O and weights by s_W*s_O/s_A, divide the output by s_W (s_O = evaluation output scale, applied in training and inference) [SFD The math of quantization / Linear layer].
- Training clamps: hidden layer weights to [-127/64, 127/64]; output layer weights to [-127*127/9600, +127*127/9600]; feature transformer weights and all biases unclamped [SFD Inside the optimizer, Range]. [DERIVED: 9600 = s_W*s_O; consistent with output divisor FV_SCALE = 16 and an internal scale of 600; the source does not state s_O. The CPW description of the original net: output divided by FV_SCALE = 16 [SFN NNUE Structure / HalfKP].]
- Ronald de Man's description of the original net: clip 16-bit elements to 0..127 (512 int8, stm half first), 32x512 int8 matrix with int32 bias, divide by 64 and clip, 32x32, divide by 64 and clip, 1x32, output / 16 [SFN NNUE Structure / HalfKP].

CPW "bullet-style" scheme (common in 2024) [NNUE Basic NNUE, Output Layer, Quantization, Lizard SCReLU]:
- `SCALE = 400, QA = 255, QB = 64`; feature-transformer weights and biases scaled by QA (int16), output weights scaled by QB (int16), output bias scaled by QA*QB; final `eval = (sum + bias) * SCALE / (QA*QB)`. With SCReLU the activation carries QA*QA, so divide by QA first (`eval /= QA`); the Lizard trick computes `(v*w)*v` instead of `(v*v)*w` and clips output weights to [-1.98, 1.98] so `v*w` fits int16; accumulate with `madd_epi16` into int32. Training passes the output through a sigmoid; the target is `lerp(game result in {0,0.5,1}, sigmoid(eval/eval_scale))` [NNUE Output Layer, Lizard SCReLU].

Integer-dtype summary: FT int16 accumulation (wrap-around adds); dense layers int8 x int8 -> int32; the sources do not provide a numerical saturation analysis for `maddubs` pair sums [SFD Quantization; see section 6].

Optimized dense/sparse layer kernels (AVX2, VNNI, blocked sparse inputs with at most 15% density after the first ClippedReLU) are documented in SFD but are SIMD-specific and do not transfer to plain Java [SFD Optimized implementation; Linear layer with sparse input].

### 3.6 Training (what the sources actually give)
- Model definition in PyTorch: `ft = nn.Linear(NUM_FEATURES, M)` applied to the white and black feature vectors, concatenated in stm order via `accumulator = stm*cat([w,b]) + (1-stm)*cat([b,w])`, `clamp(.,0,1)` activations, then two further linear layers [SFD Model specification].
- Data feeding: native (C++) batch preparation, whole batches built by worker threads, example batch 8192; features as a sparse COO tensor with value 1 (density about 0.1%), or faster as a padded `[batch, max_active]` int32 index matrix (pad -1) consumed by a CUDA kernel (CuPy RawKernel) where one block handles one position and the backward pass uses `atomicAdd` into weight and bias gradients; initialization `uniform(-sqrt(1/num_inputs), +sqrt(1/num_inputs))` like `nn.Linear` [SFD Preparing the inputs, Training batch structure, Feature transformer (CUDA)]. PyTorch's own sparse matmul "is not great" for this [SFD Feature transformer (CUDA)].
- Loss [SFD Converting the evaluation from CP-space to WDL-space; Using results along the evaluation; Mean Squared Error; Cross entropy]:
  - `wdl = sigmoid(cp / scaling_factor)` with `scaling_factor = 410` ("depends on the engine and maybe even on the data"); elsewhere "Stockfish uses values around 400".
  - Blend of search score and game result with `lambda_`: `wdl_target = lambda_*wdl_eval + (1 - lambda_)*game_result`, or interpolate the two losses; game result in {0, 0.5, 1}; the source gives no recommended lambda.
  - MSE `loss = (sigmoid(model/scaling) - wdl_target)^2`; "in practice the exponent can be > 2... Stockfish networks had good training results with an exponent of 2.6".
  - Cross entropy with `eps = 1e-12`; training directly in cp space "can lead to large gradients".
- Optimizer: `ranger.Ranger(train_params, lr=LR, betas=(.9, 0.999), eps=1.0e-7)` with per-parameter-group `min_weight/max_weight` clamps applied after each step (`p_data_fp32.clamp_(min_weight, max_weight)`); learning rate value, schedule, epochs and GPU time are not given [SFD Inside the optimizer].
- Trainer ecosystem named (no details): bullet (Rust), nnue-pytorch, marlinflow, Grapheus, NNTrainer, Starway, and "Cortex" (a Java NNUE trainer) [NNUE Notable Trainers, External Links].
- Stockfish nets: volunteer-built; Leela collaboration supplied "billions of positions"; "some high score networks can be built with the effort of one or a few people within a few days" [SFN Network, Strong Points].

### 3.7 Export for integer inference
- The sources document the arithmetic of export but not a file format: multiply weights/biases by the layer scales above, round to the integer type (int16 for the feature transformer, int8 for dense weights in the Stockfish scheme, int16 for all weights in the bullet scheme), coalesce factorized features before export, and store the first-layer weights feature-major [SFD Virtual feature coalescing, Layer parameters, The math of quantization; NNUE Quantization]. The `.nnue` binary layout, header, hashes and compression are not described [see section 6].

### 3.8 What NNUE sources do NOT give
- Data generation recipe (search depth, positions count, filtering, adjudication), binpack format, validation-set size, epochs, learning-rate schedule, GPU hours, rounding procedure at export, dead-ReLU diagnostics, lambda choice [SFD Preface; section 6 of this digest].
- Anything on Gomoku/Othello/Connect-N or non-8x8 boards; the only non-chess games are shogi (origin) and forum titles on draughts/checkers [NNUE Forum Posts].

---

## 4. Comparison table

Ranking logic: "integer-friendly Java inference" means the deployed artifact is table lookups and adds or small int matrices that can be hand-written in plain Java. "Expected gain" quotes only what the sources measured; conversions to Elo are [DERIVED], and gains are NOT transferable to our game without testing [OWN].

| method | data needed | compute (training) | integer-friendly Java inference? | expected gain (as reported) |
|---|---|---|---|---|
| Pattern tables, independent conditional-mean estimates (Logistello-1) [B94] | about 3M positions labelled by negamaxed game results | trivial counting pass; logistic regression for stage weights | yes (tables folded into one lookup per line; 75% faster search [B94 pp.5-6]) | baseline; ~10x weaker than joint fitting [B03 p.5] |
| Pattern tables, joint sparse linear regression (Logistello-2/GLEM) [B97, B98] | 3M-11M positions (labels: solved/search value or backed-up result) | steepest descent: 300 iterations/20 h (3M pos., PPro 200) or 250 iterations/30 h (11M pos., 1.5M weights, PII/333); minutes on a modern GPU [OWN] | yes: sum of table lookups, 16-bit weights, no multiplications [B98 §2, §6] | ~2 plies / ~10x speed-up vs independent estimates (old eval scores 26-35% at equal depth) [B97 Table 1; B02 Table 1] |
| GLEM large sparse patterns (16 squares) [B98] | 11M positions, n = 75 match threshold | hours; hash-table build | yes, but hash/lock access costs speed | +5.8% win rate (about +40 Elo [DERIVED]) with 11% fewer nodes/s; combined patterns did not add [B98 Table 1] |
| Texel tuning [TEX] | about 8.8M game positions, results 0/0.5/1 plus qsearch scores | about 25 min per gradient on 16 cores, up to 6 h to converge (400 parameters) | yes (tunes integer eval terms) | +99.6 Elo cumulative in chess over 7 stages, +39.4 of it from not filtering positions; may be inflated by a weak start [TEX Results] |
| SPSA [SPSA] | none (plays games) | one game pair per iteration; thousands of iterations [OWN] | yes (tunes scalars) | none quantified in the sources |
| Ordinal correlation (tau) [GOM] | 32,768 annotated positions (649,698 available) | 2 min per vector at 16,384 nodes/position; 23 vectors per iteration | yes (tunes eval weights) | restored a handicapped Crafty to default strength; no gain over defaults [GOM §6-7] |
| TreeStrap [VEN] | none beyond self-play (search values); 1812 features | SGD per move on all tree nodes; about a thousand games to learn useful weights | yes for linear/pattern evaluations; needs search bounds from the TT | 2157 Elo vs 250 (untrained), TD-Leaf 1068, RootStrap 1362 (relative scale); ICC blitz 1950-2338 [VEN Table 2-3] |
| ProbCut / MPC regressions [B95, B97] | thousands of (shallow, deep) value pairs per stage/height | linear regressions, minutes; threshold tournaments | yes (a, b, sigma per stage; a few ints/floats) | ProbCut 74% (about +180 Elo [DERIVED]); MPC 80% vs brute force at equal time (about +240) in Othello; unclear or negative in chess [B97 Table 3; PC] |
| NNUE (HalfKP-style, 2-4 layers) [SFD, SFN, NNUE] | "billions" of positions for Stockfish; engine-dependent otherwise | GPU, days; PyTorch/CUDA sparse kernels | yes with int16 accumulator + int8/int16 dense layers; SIMD parts not available in plain Java, scalar loops work | +80 Elo on Fishtest vs classical eval despite about half the speed; ~100 Elo [SFN] |

---

## 5. Recommended pipeline for our game

Everything in this section is [OWN] unless it cites a source. It combines Logistello/GLEM (pattern tables fitted jointly), the Texel/NNUE loss and label ideas, and an NNUE-style export. The design goal is a model that is a plain sum of integer table lookups, because that is exactly the family with the best documented return (Logistello-2) and the cheapest, simplest Java inference.

### 5.1 Model structure (stage 1: pattern-table evaluation, GLEM-style)

Evaluation of a position from the side to move: `e(p) = stageBias[s] + sum over pattern instances of T[s][family][configuration index]` (+ a few scalar features), integer weights, 32-bit sum. Same shape as Logistello's formula [B02 p.7-8; B03 p.5]. No link function at inference (monotone; search compares raw sums) [B98 §2].

Pattern candidates for an 8x12 six-in-a-row board [OWN]:
1. Six-windows in all four directions: horizontal 8 rows x 7 starts = 56; vertical 12 columns x 3 starts = 36; two diagonal directions 21 each = 42; total 134 windows, 3^6 = 729 configurations each (the natural counterpart of Logistello's line patterns). Per stage, unshared, 134 x 729 = 97,686 entries. Share tables across translations and mirror images (Logistello shares one table per pattern type across rotations/reflections [B02 p.8]) but keep the ROW index in the table identity for horizontal and diagonal windows because gravity makes the row meaningful; compare coarse and fine sharing by held-out loss.
2. Longer lines (7 and 8 squares, 3^7 = 2187 and 3^8 = 6561 entries) and full-row context if the held-out loss improves; Logistello's best patterns were 8-10 squares and 2x4/2x5 areas [B94 p.4; B97 §4]. Avoid flat tables above about 3^10 entries; if bigger shapes help, use the sparse offset+hash scheme [B98 §4.4] or add them as an additional family with a back-off to a smaller sub-pattern (B94's `alpha = min{1, N/40}`) or L2 shrink toward the sub-model.
3. Cell states: 3 states (empty, own, opponent). Experiment: split "empty" into playable now vs not yet playable (column height equals this row) which gives 4^6 = 4096 per window; this injects the gravity/parity information that plain ternary windows lack [OWN, untested].
4. Extra scalar features (Logistello added a parity term [B02 p.7]): own/opponent special-move cooldown state (available now, or turns until available 0..10), number of own/opponent windows that one special move could complete, move parity (number of empty cells modulo 2, the Connect-4 zugzwang analogue). Rule details should be taken from the engine code, not from here.
5. Handle the special move conservatively: the 3x3 clear plus gravity re-application changes many windows, so recompute the whole sum after it instead of updating incrementally (rare event).

Stage definition: stones on board (ply). Logistello used 13 stages of 4 discs [B97 §5]. Start with about 8-12 stages of 8-12 stones for 96 cells and tune on held-out loss. For the smoothness Logistello obtained at TRAINING time, train each stage also on neighbouring stages (+-2 stages in B97, "adjacent types" in B98 §5.2) and linearly interpolate empty entries [B97 §5]; at inference, optionally blend two adjacent stage tables linearly in ply (not done by Logistello; low cost, [OWN]).

### 5.2 Data generation (in the Java engine)

1. Bootstrap generation 0: a hand evaluation (counts of open windows by length, weighted) or random-rollout labels; Logistello itself was bootstrapped from earlier programs' games (KITTY and early Logistello) [B94 p.1]. Texel likewise reused games already played for engine testing [TEX Advantages].
2. Self-play games with the real engine at a fixed depth or node budget (for example the depth the 100 ms budget reaches; measure), multi-threaded, with opening diversity: random first plies and occasional epsilon moves. Logistello's opening book and play-level diversity are not described in detail, so the exact scheme is a design choice [B98 §3 only says to use games with at least one good player, including blunders].
3. Per game store, for every position at least every second ply: board (or move list), side to move, special-move cooldown state, the engine's search value at that position (searched at fixed depth, mate/win scores kept separate), principal-variation successor (see below), final game result. Deduplicate identical positions and back up results across the merged position DAG (Logistello negamaxed final results through the tree of all games [B94 p.1; B97 §5]; transpositions are common in Connect-style play [OWN]).
4. Do not filter "noisy" positions: Texel's biggest single gain (+39.4 Elo) came from keeping positions where the quiescence and in-game search scores disagreed [TEX Results]. Exclude only positions already inside the forced/won tail (Texel excluded positions where the engine found a mate score [TEX Method]), but record the exact game-theoretic result for those so the final stages can be fitted from exact labels (B98 §3's "solve endgames, fit last stage first, label earlier stages with the freshly fitted evaluator" [B98 §3]).
5. Side-to-move bias: if positions are sampled inside search trees, add the PV successor labelled with the negated search value [B98 §3]; if positions are sampled from played moves only (the Logistello 1994 approach [B94 p.3]), the issue is milder but the set under-covers patterns that only appear in search; mix both.
6. Volume: Logistello used about 3M positions for roughly 10^5-10^6 parameters and 11M for 1.5M weights (about 7 positions per weight) [B97 §5; B98 §6]. Our model with about 10^5 to 10^6 weights suggests at least 10-30M distinct positions [OWN, rule of thumb from those ratios]. Target counts per table entry matter more than totals: log how many entries have fewer than 20 samples (B98 §5.2's "count about 20 fit normally, about 4 treat as zero" thresholds).
7. Hold out whole GAMES (not random positions) for validation, because 140 positions per game are strongly correlated (the issue Osterlund discusses [TEX Concerns]) [OWN].

### 5.3 Labels and loss

- Label y in [0,1] from the mover's view: `y = lambda * sigmoid(searchScore / K) + (1 - lambda) * result` with result in {0, 0.5, 1}, exactly the NNUE/Texel pattern [SFD Using results along the evaluation; TEX Method]. The sources give no recommended lambda; tune it on the strength test (start values around 0.5-1 as a hypothesis [OWN]). Logistello used final results (1994/97) and later "true minimax value or an approximation" [B94 p.1; B02 p.8].
- Model output `q = sum of looked-up weights` (in float during training); loss `mean((sigmoid(q/S) - y)^2)` (Texel/Stockfish style) with optional exponent (Stockfish used 2.6 [SFD Mean Squared Error]) or cross-entropy [SFD Cross entropy]. Fit the sigmoid scale S (equivalent to Texel's K) once on the starting model and freeze it, as Texel does [TEX Results]; Stockfish's 410/400 is chess-specific and must be re-fitted [SFD Converting the evaluation].
- Logistello's own loss was plain squared error on the disc-difference target with the identity link [B97 §4] or squared error through a logistic link with 0.9/0.5/0.1 targets [B98 §5.1]. A logistic link plus count-weighted loss is closest to the logistic-regression variant Texel names [AT Logistic Regression].
- Regularization for rare configurations, in the order of evidence: (i) a count-based damping like B97's `min{1, N_i/50}/N_i` on the update, (ii) L2 toward zero or toward a smoothed neighbouring stage (`stage +-1,2` sharing, B97 §5), (iii) merging back-off to a smaller sub-pattern (B94 p.4), (iv) hard rule for very low counts: weight 0 (B98 §5.2). Early stopping on the held-out loss matters because the muting only works if iteration is stopped early [B97 §4].

### 5.4 Fitting on the GPU (PyTorch) or CPU

- Implementation: an `nn.EmbeddingBag(mode="sum")` (or a gather-and-sum) over a flat parameter vector, with one active index per pattern instance per position (about 134-200 per position) plus bias and scalar features. This is the PyTorch analogue of SFD's sparse-index feature transformer; the NNUE trainer's custom CUDA kernel is unnecessary at this size [SFD Feature transformer (CUDA); OWN]. Optimizer: Adam/Ranger-class with per-group clamps to the target integer range after every step (SFD does this for quantization [SFD Inside the optimizer]); Logistello used plain steepest descent with per-weight count normalization, which should be reproduced as a baseline [B97 §4].
- Alternative exact route for the squared-error / identity-link variant: sparse least squares on the normal equations by conjugate gradient or LSQR (mentioned but not used by Logistello [B97 §4; B98 §5]) [OWN]. GPU memory is not a constraint (about 10^6 parameters).
- Convergence monitoring: held-out MSE and pairwise discordance per stage (Buro's quality metric [B94 p.1, p.4]); report weights with highest magnitude and low count for sanity checking (the Texel "cursed parameter" check [TEX Future improvements]).
- Iterate: generation k+1 data is produced with the generation-k evaluation (Schaeffer describes Logistello's "self-play and linear regression... incrementally" [B99 Schaeffer p.11]).

### 5.5 Validation of strength

- Judge each generation by matches, not by loss alone: both colours, a fixed set of diverse starting positions, Buro-style 70-140 game matches for coarse decisions [B97 Table 3] and SPRT for fine ones [AT Playing Strength]. A model that wins at equal time is better; also record nodes/s so speed losses are visible (Logistello's larger patterns gained knowledge but lost 11% speed [B98 Table 1]).

### 5.6 Optional refinements, in order of expected value [OWN ranking]

1. Search-side regression (ProbCut-style) per stage once the evaluation is stable; the evidence is strong for Othello, mixed for chess; a six-in-a-row game with forced-win threats resembles chess more, so add a threat-aware quiescence and test whether strength drops [B95 §4; PC].
2. SPSA over a handful of scalars (evaluation scale, special-move bonus, search margins) with game pairs as the noisy objective; use the gain sequences from the source (alpha 0.602, gamma 0.101) and choose a, A, c yourself [SPSA].
3. A small NNUE-style net as a second stage if pattern tables plateau (see 5.8).

### 5.7 Export of integer tables into the Java bot

1. Fold everything into the tables (Logistello folded three weighted contributions per line into one table so evaluation needs no multiplication [B94 pp.5-6]). Choose an integer unit: for example 1 unit = 1/16 centi-stone-equivalent; scale such that `S_int = round(S * unit)`. Round each float weight, clip to int16, store as `short[]` per (stage, family). Logistello stored 16-bit weights [B98 §6].
2. Overflow rule: sum at most ~200 entries of |w| <= 32767 in a 32-bit int: safe. Check max |sum| against the engine's win-score threshold so a heuristic score can never be mistaken for a proven win [OWN].
3. File: binary resource in the jar (`DataInputStream`, big-endian shorts), header with magic, version, board size, pattern-spec hash, stage boundaries, evaluation scale; the loader verifies the hash. Pattern definitions live in code (generated from the same spec the Python trainer reads) so shape/index conventions cannot diverge [OWN].
4. Parity test: Java integer evaluation vs PyTorch float evaluation on 100k stored positions; max absolute difference must equal the rounding bound (SFD-style quantization tests; the sources show the arithmetic but no test harness [SFD The math of quantization; OWN]).
5. Size-generic code: derive window lists from `rows` and `cols` at load time; for sizes other than 8x12 the tables are retrained, not reused [OWN]. Six-windows keep 3^6 constant, so only the number of instances changes with board size.

### 5.8 If/when to move to an NNUE-style network [OWN, using SFD/NNUE constants]

- Inputs: 96 cells x 2 colours = 192 one-hot features per perspective (or colour-relative "us/them" encoding with one accumulator). No king, no captures: one feature turns on per move, nothing turns off, so the accumulator update is one column add and undo is a pop or a column subtract (exact in integer arithmetic) [follows from the update rules in SFD Accumulator]. Left-right mirror symmetry could halve the weights, as HalfKAv2_hm does for chess [SFD HalfKAv2_hm].
- Architecture to try: `192 -> H (32-64) x2 -> 16 -> 1` with ClippedReLU or SCReLU, output bucket by stone count (`(stones - 1)/12` style, the analogue of `(piece_count-1)/4` [SFD Multiple subnetworks]).
- Quantization: use the Stockfish scheme (s_A = 127, s_W = 64, shift 6, dense weights clamped to +-127/64, output weights clamped to +-127*127/(s_W*s_O)) or the bullet scheme (QA = 255, QB = 64) [SFD Quantization; NNUE Quantization]. The int16 accumulator bound is `active_features * max|w_q| + |bias_q| <= 32767`; with up to 96 active features and QA = 255 this forces trained FT weights far below 1.0; clamp FT weights during training (Stockfish does not need to, with at most 30 active) [OWN, from the overflow rule in SFD Accumulator].
- Java cost estimate: accumulator update is H adds per move; the dense layers are 2H*16 + 16 multiply-adds per evaluation in scalar code; SIMD material in SFD does not apply. Measure against the 100 ms budget before committing [OWN].
- The evidence that a small NNUE beats a well-fitted pattern table on a game like this is not in the sources [see 6].

---

## 6. Open questions

Source gaps (cannot be answered from the provided texts):
1. Exact stage boundaries and run-time stage interpolation in Logistello; whether the shipped evaluation blended adjacent stages (B97 §5 describes training-time smoothing only).
2. Regression error values on held-out data for the 1997/98 fits; convergence criteria; learning-rate schedules beyond beta = 1.
3. How Logistello-2's disc-difference output was converted to win probability for search/ProbCut thresholds (B95 used log-odds; B97 used disc units).
4. Reconciliation of weight counts (about 1.2M in B02/B03/B99 vs about 1.5M in B98 vs 110,000 variables in B97) and 12 vs 13 stages across versions.
5. Exact Texel loss/sigmoid formula (images missing) and how K is minimized; any data sizes or Elo for Grant/Zurichess/Arasan variants; any lambda blend results for Texel-style tuning.
6. SPSA parameter values a, A, c; number of games per run; gains.
7. NNUE: data-generation recipe, binpack and .nnue formats, lr schedule, epochs, GPU time, rounding at export, PSQT mechanics, `maddubs` saturation analysis, Elo for architecture/quantization/factorizer choices.
8. Any result on Connect-N, gravity boards, k-in-a-row or special-move games for any of these methods.

Design questions for our game:
9. Do 6-windows with row-specific tables capture enough gravity/zugzwang structure, or is a "playable-empty" fourth state, column-height features or a separate parity feature needed? Which sharing scheme (per row, per row band, global) minimizes held-out loss?
10. How to represent the special move (cooldown, availability, 3x3 clear that can create or destroy lines for both players) in the evaluation, and whether a quiescence/threat extension must include special-move tactics for labels to be stable.
11. Label design: which lambda between game result and search value, which search depth/node budget for labels, how many positions per game, and how to treat positions close to forced wins (exclude, label with exact result, or weight down).
12. How many stages are needed on 96 cells, and does stage-neighbour smoothing at training time suffice or is inference-time blending needed?
13. Is ProbCut/MPC safe in a threat-heavy game at 100 ms, given mixed chess evidence (Hyatt/Allgeuer) versus the large Othello gains?
14. Does a small NNUE (192 inputs) outperform the pattern-table sum enough to repay the extra Java inference cost under a 100 ms budget (accumulator width, quantization range with up to 96 active inputs, scalar speed)?
15. Is the sum-of-tables model already a "linear NNUE" such that factorization-style training (virtual shared features) helps rare configurations (the factorizer idea from SFD vs B94's sub-pattern back-off)?
16. Is TreeStrap-style all-node labelling worth the engineering (needs TT bounds), compared with root plus PV-leaf labels?
