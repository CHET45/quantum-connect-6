# Chess/Othello/checkers engine techniques transferable to a fast gravity 6-in-a-row bot (Quantum Connect)

Scope note for the report writer: chessprogramming.org pages (the prioritized source) turned out to carry almost NO Elo figures; most pages state techniques qualitatively. Quantified gains that I could actually source are few and are listed explicitly. Where I give a ranking or an estimate that is not backed by a source, it is labelled "unsourced prior" under Inferences. Applicability tags: [DIRECT] = directly applicable, [ADAPT] = needs adaptation, [N/A] = not applicable. Game facts used (from the task brief, not re-verified): 8x12 gravity board, up to 12 drops per turn, 6-in-line wins, special move once per 10 own turns that clears a 3x3 block + gravity (non-monotonic), 100 ms hard limit, forfeited turn on timeout, Java stdlib only.

## 1. Search: iterative deepening, aspiration, PVS/MTD(f), transposition tables, move ordering, pruning, extensions

### Takeaway
The sourced evidence says the big search wins are: iterative deepening + transposition table + history/killer/hash-move ordering (Schaeffer: TT + history account for ~99% of achievable tree-size reduction at depth <= 8); PVS/MTD(f) differ by under ~20% in leaf nodes; null-move pruning and LMR are strong in chess but rest on assumptions (zugzwang-free, quiet/tactical split) that do not hold cleanly in a drop game with a clearing special move.

### Cited Findings
- Iterative deepening: the search can be aborted and fall back to the move from the last completed iteration; the previous iteration's best move should be searched first in the next one so partial results of the new iteration stay usable. Also ID improves move ordering (PV, hash and refutation moves from earlier iterations) so it can be faster than a direct search to the same depth. [DIRECT] — [Iterative Deepening](https://www.chessprogramming.org/Iterative_Deepening)
- Score drops between iterations are a reason to allocate more time (the first alternative is often a bad move that only delays the loss). — [Iterative Deepening](https://www.chessprogramming.org/Iterative_Deepening)
- Schaeffer (1989): the history heuristic combined with transposition tables significantly outperforms other alpha-beta enhancements in application-generated game trees; for trees up to depth 8 this combination accounts for ~99% of the possible reductions in tree size, other enhancements being insignificant. (Second-hand summary of the abstract; exact per-enhancement numbers not retrieved.) [DIRECT] — [WorldCat record of Schaeffer 1989](https://search.worldcat.org/oclc/8557794501); repeated in [arXiv 1911.03388](https://arxiv.org/pdf/1911.03388)
- Move ordering matters because more than 90% of all fail-high nodes fail high on the first move. Typical order: PV move, hash move, winning captures, killers, history-sorted quiets, losing captures. Sort lazily (selection sort per fetched move); at the root reorder by subtree size from the previous iteration ("the longer it takes to refute a move, the higher its chance to become best next iteration"). Use internal iterative deepening when there is no hash move. [DIRECT] — [Move Ordering](https://www.chessprogramming.org/Move_Ordering)
- History heuristic: basic update is += depth^2 on a quiet beta cutoff, table indexed [from][to] (butterfly) or [piece][to]; modern "history gravity" clamps values to +-MAX_HISTORY and scales updates; maluses are applied to quiets searched before the cutoff move. Counter-move history gained "some Elo points" (Stockfish 7, no number). The heuristic is described as a depth-independent generalization of killer moves. [DIRECT, index by column/cell] — [History Heuristic](https://www.chessprogramming.org/History_Heuristic)
- Transposition table: replacement schemes are always-replace, depth-preferred, two-tier (Thompson/Condon, 1983: one depth-preferred + one always-replace slot), buckets sized to a cache line (overwrite the lowest-depth entry), priority by nodes searched, and aging. The page gives no Elo numbers for any scheme. Stockfish data point: 75% of cutoffs in positions with a hash move are caused by the hash move. Entry = key check, best move, depth, score, bound type (exact/upper/lower), age. [DIRECT] — [Transposition Table](https://www.chessprogramming.org/Transposition_Table)
- A 2025 7x6 Connect-Four solver paper used alpha-beta PVS on bitboards with a 2^28-entry TT that simply overwrites old entries on collision. (Secondary description from a search snippet; the PDF body could not be decoded.) [DIRECT as a simple baseline] — [Böck, arXiv 2507.05267](https://arxiv.org/pdf/2507.05267) (via search-result summary)
- Zobrist hashing: one random number per (piece, square), plus side to move, castling and en-passant file numbers; XOR is self-inverse so make/unmake updates incrementally; extra state is hashed by XORing in a key per state value (castling has 4 keys, often all 16 combos get one). 64-bit keys are the modern standard; Schaeffer reported a poor PRNG seed caused many hash errors. [DIRECT; add cooldown counters as extra keys] — [Zobrist Hashing](https://www.chessprogramming.org/Zobrist_Hashing)
- Aspiration windows: typical window 1/4 to 1/2 pawn either side of the previous score; on fail, widen only the failing bound (Crafty) or exponentially (Stockfish/RobboLito). Benefit is debated (forum thread titles "Are Aspiration Windows Worthless?"), no quantified speedup. [ADAPT / low priority] — [Aspiration Windows](https://www.chessprogramming.org/Aspiration_Windows)
- MTD(f): repeated null-window alpha-beta with memory (TT) converging bounds; efficiency depends on the first guess (exact value => 2 passes). Plaat/Schaeffer/Pijls/de Bruin tested Chinook (checkers), Keyano (Othello), Phoenix (chess): MTD(f) beat aspiration NegaScout on leaf nodes, total nodes and time, but differences between algorithms were "less than 20%" (vs ~100% in earlier simulations), attributed to iterative deepening + memory. [ADAPT / optional; evaluation granularity matters] — [Plaat et al., A New Paradigm for Minimax Search](https://arxiv.org/pdf/1404.1515.pdf); [MTD(f) page](https://www.chessprogramming.org/MTD(f)); summary at [repub.eur.nl/pub/1440](https://repub.eur.nl/pub/1440)
- Null-move pruning: fails in zugzwang ("not moving would be the best move, if it were legal"); standard R = 2..3, disabled in check or with only king+pawns; Hyatt found verified null move "does not help at all in Crafty"; Costalba argues verification has little to do with zugzwang. Page gives no Elo, only "significant Elo" when tuned. A forum post attributes ~40 Elo to Stockfish's null-move-with-verification code (low-quality source, unverified). — [Null Move Pruning](https://www.chessprogramming.org/Null_Move_Pruning); [talkchess post](https://talkchess.com/viewtopic.php?p=841347)
- Late move reductions: search first 1-2 moves at full depth, reduce later ones, re-search at full depth if the reduced search beats alpha; commonly not applied at depth < 3, to tactical moves, killers, PV nodes, or in check; reduce more at expected cut nodes and by low history. Formulas are ~ ln(depth)*ln(moveIndex)/c (e.g. Ethereal quiet: 0.7844 + ln(d)ln(m)/2.4696). LMR became popular in 2005 (Fruit, Glaurung). The wiki says LMR and friends can bring the effective branching factor "to less than 2" but gives no Elo. [ADAPT] — [Late Move Reductions](https://www.chessprogramming.org/Late_Move_Reductions)
- Quiescence search: resolves the horizon effect by searching only "noisy" moves after the nominal depth, with stand-pat as a lower bound (assumes no zugzwang); explosion is controlled by ordering, SEE and delta pruning; checks in qsearch must be limited to the first X plies. No Elo given. [ADAPT: noisy = moves that make/block a 6-line threat or special-move clears] — [Quiescence Search](https://www.chessprogramming.org/Quiescence_Search)
- Existing Connect-Four solvers (Pons) are built on alpha-beta + transposition table + opening book + bitboards, with center-first move ordering; the Pons tutorial itself could not be fetched (certificate error). — [PascalPons/connect4 README](https://github.com/PascalPons/connect4); description via [Böck, arXiv 2507.05267](https://arxiv.org/pdf/2507.05267)

### Inferences
- Priority is the standard chess order: ID + TT (Zobrist, cooldown state hashed) + hash/killer/history/center-first ordering give most of the gain; everything else is second order. (Supported by Schaeffer's ~99% claim, which was measured on application trees of depth <= 8, i.e. not on a 100 ms Java search of a different game.)
- Hash key must include: side to move, each player's special-move cooldown (turns since last use, capped at 10 -> 11 states per player), and probably a turn-counter bucket only if the 1000-turn draw matters near the limit. Because discs vanish, positions can recur, so the history-path (graph-history-interaction) problem exists: do not store draw-by-repetition style scores in the TT (the game has no repetition rule per the brief, only 1000 turns/full board, so mainly the turn limit is path-dependent).
- Null-move pruning: "pass" is not a legal move; gravity-connect games have genuine zugzwang-like parity/odd-even threat structure (known from Connect-4 theory, unsourced here), and the special move makes "do nothing" even less comparable. Treat as [N/A] initially; at best a very conservative experiment guarded by SPRT. Hyatt's negative verified-null result is a caution.
- LMR [ADAPT]: with only 12 (up to ~24 with special) moves and 100 ms, depth will be modest, so LMR benefits are smaller than in chess; use it only after threat-based move classification exists (never reduce moves that create/block 5-in-6 threats, special moves, or forced replies). Expect a small, SPRT-testable gain.
- Quiescence analogue [ADAPT]: instead of captures, extend on forcing sequences: if the side to move has an immediate winning drop/special move, or must answer one, search only the forcing moves (win, block, special that wins/defuses) beyond the horizon; this is the analogue of check extensions and is likely the highest-value "tactical" addition for a k-in-a-row game.
- Aspiration windows / MTD(f) [ADAPT, low]: evaluation granularity is coarse in connect games and MTD(f) re-search cost grows with granularity (the wiki listing of a granularity thread is the only hint; no data). PVS with a TT is the safer default.
- Unsourced prior (general engine-development folklore, treat as hypothesis): killer + history ordering together are typically worth a large factor in nodes; LMR/null move are each worth tens of Elo in chess; aspiration windows only a few Elo.

### Gaps
- No Elo figures found on chessprogramming.org for TT replacement schemes, killers, history, LMR, aspiration, qsearch (the pages are qualitative; the LMR test-results page only links to forum threads).
- Could not retrieve Schaeffer 1989 full text (per-enhancement numbers) or the Pons tutorial (blog.gamesolver.org has a TLS certificate mismatch).
- No source found for Fishtest-style Elo of removing individual features; no source on search behavior specific to non-monotonic games (disc-clearing special moves) or on hashing cooldown counters beyond the generic "XOR in extra state" statement.

## 2. Time management for a hard per-call deadline, JVM pitfalls

### Takeaway
Use a soft limit checked between iterations and a hard limit checked every N nodes, always returning the last completed iteration's best move; for the 100 ms budget keep a generous margin and measure JVM effects (JIT warm-up, GC, clock) empirically since I found no authoritative source on them.

### Cited Findings
- Two thresholds: a soft bound (optimum time) checked each ID iteration and a hard bound (maximum time) checked periodically "usually by every set amount of nodes"; the two-level design exists because aborting mid-iteration wastes work. [DIRECT] — [Time Management](https://www.chessprogramming.org/Time_Management)
- On abort, ID falls back to the previous iteration's best move/PV; one may also decide before starting an iteration (e.g. stop if more than 50% of allotted time is already spent, or only one legal move). [DIRECT] — [Time Management](https://www.chessprogramming.org/Time_Management)
- Basic per-move allocation base/20 + increment/2 is "very competitive"; instability signals (best-move changes, score trend, fail-low) justify extra time; "panic time" for fail-lows has fallen out of use. Not directly relevant to a fixed 100 ms call (no clock bank) except as a model of soft vs. hard limits. [ADAPT] — [Time Management](https://www.chessprogramming.org/Time_Management)
- The page does not discuss lag compensation or explicit safety margins. — [Time Management](https://www.chessprogramming.org/Time_Management)
- Timer choice: nanoTime() is the right clock for elapsed time; currentTimeMillis() is wall-clock and can be coarse/unstable on Windows (older reports 1-15 ms; one report of 1 ms precision on Windows 7 / Java 8). — [JavaMex timing FAQ](https://javamex.com/faq/timing.shtml); [GeeksforGeeks nanoTime vs currentTimeMillis](https://www.geeksforgeeks.org/java-system-nanotime-vs-system-currenttimemillis)

### Inferences
- Design for the 100 ms limit: record System.nanoTime() at entry of the call; hard stop at ~(limit minus margin) with margin covering move-return overhead and GC; check the clock every ~1-4k nodes via a cheap counter; abort by flag/exception (not Thread.interrupt, which is racy), unwinding the board state; always have a legal move (e.g. a precomputed center-first or immediate-win/block move) ready before searching, so a forfeited turn is never the outcome of an unexpected stall.
- Since a timeout only forfeits the turn (not the game), the cost of an overrun is a lost tempo, which in a connect game can be decisive; keep the margin conservative and calibrate against the real engine harness.
- JVM pitfalls (unsourced engineering knowledge, flagged as hypotheses to verify in the harness): the first calls run interpreted/C1 code, so early-move search depth will be shallower; do a self-play warm-up in a static initializer if the jar's initialization is not itself time-limited, and use preallocated arrays (no per-node allocation) plus a fixed-size TT allocated once to avoid GC pauses; avoid streams/boxing in the hot path.
- Search-instability time extension (more time when the score drops or the best move changes) is cheap to add but probably low value at 100 ms; the soft-limit "do not start an iteration if >50% time used" rule is the part worth copying.

### Gaps
- No authoritative source found on JIT warm-up, GC pause behavior or thread interruption in time-limited Java bots (the one search returned only dated timer-resolution material).
- No source for a specific safety margin value; must be measured with the actual engine.

## 3. Evaluation: Texel tuning, SPSA, pattern evaluation, incremental evaluation

### Takeaway
Texel tuning (logistic regression of game results on the static evaluation, tuned by coordinate descent) is the standard cheap way to fit eval weights from self-play data and was worth ~100 Elo cumulatively in its author's first experiments; Logistello shows that game-stage-specific pattern/line weights fitted by regression beat hand-tuned features.

### Cited Findings
- Texel tuning: collect ~64,000 fast self-play games, extract ~8.8 million positions (excluding book positions and mate scores), compute E = mean squared error between game result R (0 / 0.5 / 1) and sigmoid(K * qsearch score); K computed once (1.13 in the reported case), then each integer parameter is perturbed +1/-1 by local search, keeping changes that lower E. About 400 parameters, one gradient ~25 min on 16 cores, local minimum within ~6 h. [DIRECT] — [Texel's Tuning Method](https://www.chessprogramming.org/Texel%27s_Tuning_Method)
- Reported Elo steps in Texel's own tuning sequence (32,000 games at 1+0.08): 24.6, 4.0, 5.8, 2.8, 12.8, 39.4, 10.2 = 99.6 Elo total; the biggest step (39.4) came from changing which positions were used; 10.2 came from separate middlegame/endgame queen PSTs. — [Texel's Tuning Method](https://www.chessprogramming.org/Texel%27s_Tuning_Method)
- Caveats: correlation not causation; positions from the same game are not independent; the engine cannot learn what it does not understand; some tuned values look odd. — [Texel's Tuning Method](https://www.chessprogramming.org/Texel%27s_Tuning_Method)
- Logistello (Othello): evaluation based on disc patterns and features with over a million numerical parameters tuned by linear regression; beat world champion Takeshi Murakami 6-0 in 1997. (ProbCut and opening-book details were not on the fetched Wikipedia page.) [ADAPT: pattern = line segments of the board] — [Wikipedia: Logistello](https://en.wikipedia.org/wiki/Logistello)
- Checkers: Chinook's knowledge was hand-coded (evaluation of 25 weighted components) rather than learned. — [Wikipedia: Chinook](https://en.wikipedia.org/wiki/Chinook_(computer_program)) (via search summary)
- Piece-square-table idea: Rebel uses piece-square tables for ordering non-captures; the Texel page shows PSTs (e.g. queen) are among the tuned parameters. — [History Heuristic](https://www.chessprogramming.org/History_Heuristic); [Texel's Tuning Method](https://www.chessprogramming.org/Texel%27s_Tuning_Method)

### Inferences
- PST analogue [DIRECT]: a per-cell weight table (center columns, row height) plus per-direction line-window scores. A natural pattern evaluation for 6-in-a-row is a lookup table over every 6-cell window (and 5-cell sub-patterns): 3^6 = 729 states per window type for (empty/mine/theirs), summed over all windows, with weights fitted by Texel-style logistic regression on self-play positions (the Logistello recipe, scaled down). This is incrementally updatable per placed/removed disc (only windows through that cell change) — but the special move clears up to 9 cells and shifts columns, so incremental update must handle bulk changes (recompute affected columns, or just recompute windows touching the 3x3 plus all cells above it).
- Features specific to this game beyond the chess analogy: parity/odd-even threats (rows reachable by a drop), cooldown availability of special moves (own and opponent), number of 5-in-a-6-window open threats, and special-move "clear" potential. Tuned weights per cooldown state may matter.
- Texel tuning is feasible here because the Java engine can produce games cheaply; the labelled data and the eval being a linear function of features makes a Gauss-Newton/Adam logistic regression easy to implement in the harness (offline Java or Python, not shipped in the jar).
- SPSA [ADAPT]: useful for tuning search constants (LMR constants, margins, TT sizes, time margins) through games; needs many games per iteration; I did not retrieve a source for it (see Gaps).
- Unsourced prior: for a shallow-depth fast bot, eval quality and threat-aware search likely dominate Elo; the 100 Elo Texel figure is from chess with a mature engine and should not be taken as an expected gain here.

### Gaps
- No source fetched for SPSA (chessprogramming.org SPSA page, Fishtest tuning) or for quantified pattern-vs-handcrafted eval gains in Othello (Buro's papers not retrieved).
- No source on incremental pattern evaluation under bulk-removal moves.

## 4. Othello (Logistello, Edax) and checkers (Chinook) lessons: endgame solvers, opening books

### Takeaway
Both domains show that (a) well-ordered alpha-beta + TT + history is the core, (b) exact endgame solving (Edax-style endgame solver, Chinook endgame databases) and (c) opening books give outsized gains in the late/early phases; for this game the analogue is an exact solver for short forcing sequences and a small precomputed book, bounded by the 100 ms and jar-size limits.

### Cited Findings
- Chinook: parallel iterative alpha-beta with transposition tables and the history heuristic; endgame databases for up to 8 pieces (444 billion positions in one source), later 10-piece (completed May 2003); checkers was solved as a draw in 2007 using a 39-trillion-position endgame database; plus an opening book. [ADAPT: endgame DB is N/A for a non-monotonic 96-cell game] — [Wikipedia: Chinook](https://en.wikipedia.org/wiki/Chinook_(computer_program)); [ChessBase: checkers solved](https://en.chessbase.com/post/500-billion-billion-moves-later-computers-solve-checkers/5) (via search summary)
- MTD(f) was developed and tested on Chinook, Keyano (Othello) and Phoenix; it was best on leaf nodes, total nodes and time, with differences <20%. — [Plaat et al.](https://arxiv.org/pdf/1404.1515.pdf)
- Logistello: >1 million regression-fitted pattern weights; 6-0 vs Murakami (1997). — [Wikipedia: Logistello](https://en.wikipedia.org/wiki/Logistello)
- Connect-4 is the closest solved cousin: strong solvers use alpha-beta + TT + bitboards + opening book; the 7x6 board has been strongly solved on consumer hardware (2025). — [PascalPons/connect4](https://github.com/PascalPons/connect4); [Böck 2025](https://arxiv.org/pdf/2507.05267)

### Inferences
- Endgame-solver analogue [ADAPT]: when few empty cells or when a forced win/loss sequence is short, switch to an exact win/loss search with no eval (Edax-style: solve with a narrow window). In our game the "endgame" is nearly unreachable by filling (full board is a draw), so the useful analogue is a fast forced-win/forced-loss ("threat-space") solver inside the main search and at the root, which is the same idea as Edax's WLD solving.
- Opening book [ADAPT, medium value]: precompute best replies for early positions by running the engine offline at deep depth; store in the jar as a resource (Java stdlib can read it). Value depends on how deterministic the opponent is; low risk, low priority until the search is stable.
- Chinook/Edax endgame databases [N/A]: positions are not monotone and the state space (8x12 board, cooldowns) is far too large.
- Unsourced: Edax speed claims, Edax's ProbCut/MPC usage, fastest-first ordering for Othello endgames — my attempt to fetch the chessprogramming Edax page returned 404, so these are not cited here.

### Gaps
- Edax documentation (search algorithm, speed, endgame solver) and Logistello's ProbCut/book learning not retrieved (404 / not on the Wikipedia page).
- Schaeffer's checkers search-enhancement details (primary papers) not retrieved; only secondary summaries.

## 5. Testing methodology: SPRT, self-play harness, Elo estimation

### Takeaway
Modern engine development gates every change with SPRT on fast self-play games (typically small Elo bounds, alpha=beta=0.05); with a Java engine you must write a small match runner (cutechess-cli/fastchess speak UCI, not our API) that plays paired games with swapped colors and diverse openings.

### Cited Findings
- SPRT is "generally regarded as the most important process in modern engine development"; parameters Elo0, Elo1, alpha, beta; test stops when the LLR crosses either bound. Example settings: gainer test elo0=0, elo1=5, alpha=0.05, beta=0.05, tc=8+0.08; non-regression elo0=-5, elo1=0. Common gainer bounds range from [0,2] to [0,10] (looser for weaker engines). Game counts depend on bounds; no typical count given. — [Sequential Probability Ratio Test](https://www.chessprogramming.org/Sequential_Probability_Ratio_Test)
- Tools: cutechess-cli, fastchess (recommended; pentanomial statistics let tests finish faster), OpenBench (self-hosted distributed testing), Fishtest (Stockfish). — [Sequential Probability Ratio Test](https://www.chessprogramming.org/Sequential_Probability_Ratio_Test)
- Texel's tuning validated each step with 32,000-game matches at 1+0.08 time control. — [Texel's Tuning Method](https://www.chessprogramming.org/Texel%27s_Tuning_Method)
- Parameter-search caveat from a forum anecdote: search parameters can be very insensitive to variations (anecdotal, possibly outdated). — [TalkChess thread](https://talkchess.com/viewtopic.php?p=841347) (via search summary)

### Inferences
- cutechess-cli/fastchess only drive UCI/XBoard engines, so for this Java game build a custom harness: a driver that loads two bot jars (or two configs in one JVM-per-bot process), plays N games using the real engine package ch.cern.quantumconnect, swaps who moves first (pair each opening with swapped sides, pentanomial scoring as fastchess does), starts from randomized opening positions (e.g. 4-6 random plies) to avoid duplicates since bots are deterministic, enforces the same 100 ms per-call limit with the same JVM flags as the real match, and feeds results to an SPRT (LLR with bounds ln(beta/(1-alpha)) and ln((1-beta)/alpha); these standard formulas are not in the fetched page, so verify them against an online SPRT calculator).
- Use 2-5 Elo bounds in early development (large gains) and tighter later; limit CPU contention (run <= cores/2 games in parallel, since the 100 ms limit is wall-clock and parallel load would distort strength). Fixed-node or fixed-depth runs can be used for determinism in regression tests of eval/ordering changes, with timed runs reserved for final checks.
- Estimate Elo from score: Elo = -400 * log10(1/score - 1); report with 95% confidence interval; because discs-clearing special moves create high variance, expect to need thousands of games for 5-Elo-level changes (unsourced estimate).

### Gaps
- No fetched source for LLR bound formulas, expected game counts per bound pair, or for adapting cutechess-cli/fastchess to non-UCI engines.
- No SPSA/Fishtest tuning source retrieved.

## 6. Prioritized list of transferable techniques (the requested deliverable) with applicability

### Takeaway
Rank by (sourced evidence of importance) x (fit to our game). Top tier: ID + safe time control, Zobrist TT with cooldown state, hash/killer/history/center ordering, immediate-win/block detection with forced-move extensions; mid tier: PVS, Texel-tuned pattern eval, SPRT harness, LMR; low tier: aspiration windows, MTD(f), null move, opening book; not applicable: endgame databases.

### Cited Findings
- (Ranking is mostly inference; the supporting citations are in sections 1-5.) Key sourced anchors: Schaeffer's ~99% of tree-size reduction from TT + history ([worldcat](https://search.worldcat.org/oclc/8557794501)); >90% first-move cutoffs ([Move Ordering](https://www.chessprogramming.org/Move_Ordering)); MTD(f) vs NegaScout differences <20% ([Plaat](https://arxiv.org/pdf/1404.1515.pdf)); Texel tuning ~99.6 Elo cumulative in chess ([Texel](https://www.chessprogramming.org/Texel%27s_Tuning_Method)); SPRT as the standard gate ([SPRT](https://www.chessprogramming.org/Sequential_Probability_Ratio_Test)).

### Inferences
Ranked list (order = recommended implementation order; "gain" column is sourced only where stated, otherwise unsourced prior):
1. Iterative deepening with soft/hard time limits and fallback to last completed iteration — [DIRECT]; essential for correctness under 100 ms (sourced behavior; no Elo).
2. Transposition table with Zobrist keys including side to move and both cooldown counters; start with a single large always-replace or depth-preferred table (2-tier later) — [DIRECT]; sourced: TT+history ~99% of reduction (Schaeffer), 75% of cutoffs from hash move (Stockfish).
3. Move ordering: hash move > immediate win > forced block > special move that wins/defuses > killers > history > center-first — [DIRECT]; sourced: >90% first-move cutoffs; center-first is the Connect-4 standard.
4. Immediate-threat detection + forced-reply extension (the "check extension"/qsearch analogue) — [ADAPT]; no Elo source; likely the biggest tactical gain for a 6-in-line game (unsourced prior).
5. Incremental bitboard / pattern evaluation with Texel-tuned weights — [DIRECT/ADAPT]; ~100 Elo cumulative in chess (sourced), expected gain here unknown.
6. PVS (null-window re-search) — [DIRECT]; small gain on top of good ordering (Plaat: algorithm differences <20% leaf nodes).
7. History gravity/counter-move history (cheap add-ons to 3) — [DIRECT]; "some Elo" (Stockfish 7).
8. LMR with tactical exemptions — [ADAPT]; no number; test with SPRT.
9. SPRT self-play harness with paired openings (custom Java runner) — [ADAPT]; prerequisite for all measurement; do it early (move it to step 2 in practice).
10. Aspiration windows, MTD(f) — [ADAPT, low]; debated benefit; MTD(f) beat NegaScout on nodes in Plaat's tests but depends on eval granularity.
11. Opening book from offline deep search — [ADAPT, low-medium].
12. Null-move pruning — [N/A / risky] because of zugzwang-like parity and no legal pass; Hyatt found verification did not help.
13. Endgame databases (Chinook) — [N/A] (non-monotonic state space); the Edax-style exact solver for short forcing lines is the [ADAPT] analogue (see 4).

### Gaps
- Real Elo-per-technique numbers for connect-type games do not exist in the sources I found; all "expected gain" statements for this game are priors and must be measured with the SPRT harness.
