# Top k-in-a-row engines (Gomoku/Renju, Connect6, Connect Four family): results and techniques, with transferability to Quantum Connect

Context: Quantum Connect = gravity drop, 8x12, 4-in-a-row, 100 ms hard limit per move, Java stdlib only, plus a periodic "destroy discs around drop point" special move. Everything under "Inferences" is my own reasoning, not a sourced claim.

## 1. Gomocup: recent winners, techniques, time controls, papers and source

### Takeaway
Rapfi (Haobin Duan et al., open source, GPL-3.0, C++) has dominated Gomocup since 2022-2023 and won every main league in 2024 and 2025 except Caro; it is alpha-beta (PVS) with NNUE-style pattern-based evaluation plus VCF at leaves, and the paper reports about +400 Elo over Katagomo with CPU only. Before the neural era (2012-2018) Yixin (alpha-beta + deep threat-space search + handcrafted evaluation) won everything; the Gomocup "fastgame" league that is closest to our budget is 5 s per move / 120 s per match, still ~50x our 100 ms.

### Cited Findings
**Results (official gomocup.org pages)**
- Gomocup 2025 (26th, June 6-8, 2025): Rapfi 1st in Freestyle-20 (then AlphaGomoku [Kozarzewski], JAX), Freestyle-15 (Rapfi, JAX, AlphaGomoku), Fastgame (Rapfi, AlphaGomoku, JAX), Standard (Rapfi, AlphaGomoku, JAX), Renju (Rapfi, AlphaGomoku, JAX). Caro: AlphaGomoku, JAX, Embryo. — [Gomocup 2025](https://gomocup.org/results/gomocup-result-2025)
- Gomocup 2024 (25th, May 17-19): Rapfi 1st in Freestyle-20 (Barbakan 2nd, AlphaGomoku 3rd), Freestyle-15 (JAX 2nd, AlphaGomoku 3rd), Fastgame (AlphaGomoku 2nd, Barbakan 3rd; Rapfi lost only 4 games), Standard (JAX 2nd, Katagomo 3rd; Rapfi had one loss), Renju (Katagomo 2nd, JAX 3rd). Caro: AlphaGomoku, Embryo, JAX. — [Gomocup 2024](https://gomocup.org/results/gomocup-result-2024)
- Gomocup 2023: Rapfi 1st in Freestyle, Fastgame, Renju; Standard won by AlphaGomoku, then Katagomo, Pentazennn (Rapfi was left unrated after crashes in 27 drawn games exceeded the 10% crash-exclusion threshold). Caro (new league): AlphaGomoku, Embryo, Lavender. — [Gomocup 2023](https://gomocup.org/results/gomocup-result-2023)
- Gomocup 2021: Embryo won Freestyle 1 and Fastgame; Katagomo won Standard 1 and Renju; Rapfi 3rd-4th in freestyle, 3rd in fastgame, 2nd in standard. Barbakan (new) 2nd in freestyle/fastgame. — [Gomocup 2021](https://gomocup.org/results/gomocup-result-2021)
- Historical Freestyle winners (Wikipedia table; official pages are the authority): Pisq 2000-01, Trunkat 2002, Svine 2003-04, Goro 2005-06 and 2009-10, Tito 2007-08 and 2011, Yixin (Sun Kai, China) every year 2012-2018. Yixin also won Fastgame 2013-2017, Standard 2013/2015-2017, Renju 2016-2017. — [Wikipedia: Gomocup](https://en.wikipedia.org/wiki/Gomocup)
- Gomocup 2014 and 2015 official pages name Yixin the winner (consistent with the Wikipedia freestyle table above). Note: Wikipedia's *Standard* table lists Tito for 2014, so a source that says "Tito won 2014" is probably reading the Standard league, not Freestyle. — [Gomocup 2014](https://gomocup.org/results/gomocup-result-2014/), [Gomocup 2015](https://gomocup.org/results/gomocup-result-2015)
- Gomocup tournament calendar (27 editions 2000-2026) — [Gomocup results index](https://gomocup.org/results/)

**Time controls and rules (the "fast-game" league)**
- Fastgame league: 5 s per move, 120 s per match, 20x20 board, five or more stones win, 350 MB memory (2024). Main leagues: 300 s/1000 s; second groups: 30 s/180 s. — [Gomocup 2024](https://gomocup.org/results/gomocup-result-2024), [Gomocup 2025](https://gomocup.org/results/gomocup-result-2025)
- General rules: time limits are announced per tournament and "won't be stricter than 30 seconds for a turn and 3 minutes for a match"; balanced openings are drawn from a set to reduce first-player advantage; Renju openings are prepared by experts. — [Gomocup detail information](https://gomocup.org/detail-information/)
- Freestyle was 20x20 until 2023; from 2024 both 15x15 and 20x20 leagues. Standard = exactly five; Caro = unblocked five. — [Gomocup detail information](https://gomocup.org/detail-information/)

**Rapfi (technique, paper, code)**
- Paper "Rapfi: Distilling Efficient Neural Network for the Game of Gomoku" (Jin, Duan, Hang; arXiv 2503.13178, March 2025): "Mixnet" splits the board into four directional line patterns; a mapping network turns them into features; the mapping is distilled into a lookup codebook over all 397,488 possible line patterns for instant feature retrieval; only features of changed stones are recomputed incrementally (helpful for depth-first search). — [arXiv 2503.13178](https://arxiv.org/html/2503.13178v1)
- Same paper, search: MCTS (PUCT variant) and alpha-beta with Principal Variation Search, futility pruning, late move reduction, singular extension, null-move pruning, transposition table, and VCF search at leaf nodes; parameters tuned with SPSA. — [arXiv 2503.13178](https://arxiv.org/html/2503.13178v1)
- Strength: MixNet beats ResNet baselines by roughly 300-400 Elo under MCTS at fixed move times; combined with alpha-beta, Rapfi is about 400 Elo above Katagomo using CPU only; the large net did not beat medium/small under alpha-beta because of higher codebook update cost. — [arXiv 2503.13178](https://arxiv.org/html/2503.13178v1)
- Training: ~30.8 M positions from Katagomo self-play, distillation from a ResNet 6b128f teacher (75% distill / 25% label loss), int8/int16 quantization with AVX2 (~4x faster than non-vectorized). — [arXiv 2503.13178](https://arxiv.org/html/2503.13178v1)
- Paper says it "ranked first among 520 Gomoku agents on Botzone and won the championship in GomoCup 2024" over 54 competitors. — [arXiv 2503.13178](https://arxiv.org/html/2503.13178v1)
- Repo: dhbloo/rapfi, C++17, CMake, GPL-3.0, alpha-beta search with classical and NNUE evaluation, Piskvork protocol with Yixin-Board extensions; separate NNUE trainer (PyTorch) and network repository. The README does not itself describe VCF or time management. — [GitHub dhbloo/rapfi](https://github.com/dhbloo/rapfi), [wiki index](https://github.com/dhbloo/rapfi/wiki)
- Rapfi engine updates by year: 2023 newer NNUE + SPSA tuning + better search; 2025 new NNUE with smaller weights and lower memory, AVX-VNNI/AVX-512 support. — [Gomocup 2023](https://gomocup.org/results/gomocup-result-2023), [Gomocup 2025](https://gomocup.org/results/gomocup-result-2025)
- A Korean wiki reports the Mix8 to Mix9 NNUE update gave roughly +20 to +55 Elo under renju rules (secondary source, approximate). — [namu.moe Rapfi](https://namu.moe/w/Rapfi)

**Other engines**
- Katagomo: rewritten on newer KataGo (2021), CPU-only build, "strict VCF solver" and nets with VCF input features. — [Gomocup 2021](https://gomocup.org/results/gomocup-result-2021)
- AlphaGomoku (Kozarzewski): NN + search; 2021 added a VCF solver into search from ~1 M training games; 2023 upgraded the VCF solver to alpha-beta, trained on 5 M games; 2024 improved alpha-beta, OpenCL backend, AVX512. — [Gomocup 2021](https://gomocup.org/results/gomocup-result-2021), [Gomocup 2023](https://gomocup.org/results/gomocup-result-2023), [Gomocup 2024](https://gomocup.org/results/gomocup-result-2024)
- Barbakan: slower Monte Carlo search with NN evaluation and a fast "surewin" search (freestyle only). — [Gomocup 2021](https://gomocup.org/results/gomocup-result-2021)
- JAX: MCTS + CNN, self-play from zero, KataGo methods, CPU/CUDA/TensorRT; new in 2023 (12th freestyle), 2nd in Freestyle-15 in 2024. — [Gomocup 2023](https://gomocup.org/results/gomocup-result-2023), [Gomocup 2024](https://gomocup.org/results/gomocup-result-2024)
- Embryo (Fontan): won 2019-2021 freestyle; later updates AVX2, pruning, move-generator fixes. Squirrel (2023, new): "simple C++ AI using alpha-beta pruning, iterative deepening, VCF/VCT search, and a transposition table" — a useful template for a minimal strong engine. — [Gomocup 2023](https://gomocup.org/results/gomocup-result-2023), [Gomocup 2024](https://gomocup.org/results/gomocup-result-2024)
- PentaZen (open source, Renju): "pure alpha-beta search framework" (v0.4.18, Gomocup 2021), pattern header files generated at build time; NNs from 0.5.0. — [GitHub schibir/PentaZen](https://github.com/schibir/PentaZen)
- Yixin (Sun Kai): described by a hobbyist site as "deep threat-space search with sophisticated evaluation functions"; Yixin's own source/paper not found (see Gaps). — [rarepike overview](https://rarepike.com/five/computer-ai/) (low-reliability, no citations on that page)
- Rust "figrid-board" engine docs describe a typical stack: alpha-beta with TT, threat-aware move ordering, killer/history heuristics, late-move pruning, quiescence layer for forcing sequences, optional VCF/VCT at root. — [docs.rs figrid-board](https://docs.rs/figrid-board)

### Inferences
- The consistent recipe across winners from Yixin (2012) to Rapfi (2025): alpha-beta/PVS + TT + a VCF/threat "quiescence" search + a cheap pattern-based incremental evaluation. Neural nets (NNUE-like) gave the last large jump, but only in combination with the alpha-beta framework (Rapfi), so a handcrafted pattern table is the closest cheap analogue for our setting.
- Fastgame (5 s/move) is ~50x our budget, so only the simplest components are relevant (Squirrel-like stack; pattern lookup; VCF-style threat search); NN training or MCTS with a big net is not.

### Gaps
- Yixin's own technical description, source code or paper: not found (closed source; only secondary descriptions).
- Per-year Gomocup results for 2022 and 2026 were not retrieved; 2016-2020 league tables were not opened individually.
- Rapfi source-level details of its VCF implementation (data structures, node limits) were not read; only the paper summary. The GitHub README/wiki lacks them.
- Exact Elo tables from Gomocup pages were not captured.

## 2. Connect6 (ICGA Computer Olympiad) winners; relevance-zone and dependency-based search

### Takeaway
Connect6 Olympiad winners were dominated by Taiwanese and Chinese programs (NCTU6 2006, NCTU6-Lite 2008, X6 2007, Bit 2009, MoreThenFive 2010, MOBILE 6 2013, Kavalan 2018). The distinctive technology is NCTU6's threat-based proof search (RZOP, relevance-zone-oriented proof search, built on Thomsen's lambda search) and dependency-based search (DBS) for threat-space search, which gave a 3-4x average speedup (up to 50x on hard positions). MoreThenFive combined alpha-beta/MTD(f) with VCF endgame search.

### Cited Findings
- Olympiad winners: 2006 (11th, Turin) NCTU6; 2007 (12th) X6; 2008 (13th, Beijing) NCTU6-Lite; 2009 (14th) Bit; 2010 (15th) MoreThenFive; 2013 (17th) MOBILE 6; 2018 (21st) Kavalan; 2011 and 2019 winners not stated on the page. — [Chessprogramming wiki: Connect6](https://chessprogramming.org/Connect6)
- NCTU6 by I-Chen Wu and Hsiu-Chen Chang; NCTU6-Lite credited to Wu and Ping-Hung Lin (ICGA Journal 31(4)). — [Chessprogramming wiki: Connect6](https://chessprogramming.org/Connect6)
- Techniques listed on the wiki: alpha-beta and MTD(f) combined with VCF search (cited for MoreThenFive); MCTS/UCT (two-stage MCTS); proof-number search variants (job-level PNS, stage PN); RZOP search (Wu and Lin, IEEE TCIAIG 2(3), 2010); dependency-based search (Wu et al., CG 2013); bitboard methods (Yen and Yang); TD learning. — [Chessprogramming wiki: Connect6](https://chessprogramming.org/Connect6)
- RZOP: 2010 PhD thesis by Ping-Hung Lin (advisor I-Chen Wu), a threat-based proof search developed from Thomsen's lambda search; solved many Connect6 positions including the Mickey Mouse opening; a refinement (SRZOP) is reported ~2.04x faster over 12 openings; NCTU6 won 8 games and lost none against top Taiwanese players in 2009. — [NYCU repository: RZOP thesis](https://ir.lib.nycu.edu.tw/handle/11536/32262?locale=en), [thesis (Chinese title)](https://ir.lib.nycu.edu.tw/handle/11536/40301)
- Dependency-Based Search for Connect6 (Wu, Kang, Lin, Lin, Wei, Chang; CG 2013): first application of DBS to Connect6 programs; the two-stones-per-move rule makes DBS "extremely difficult" to apply; average speedup 4.12, up to 50 on some hard positions. A 2012 NCTU thesis (Kang) reports 3x average, ~50x for hard positions for NCTU6's TSS. DBS originates in Allis's Gomoku work and helps when many independent or nearly independent TSS areas exist. — [NYCU repository: DBS paper](https://ir.lib.nycu.edu.tw/handle/11536/125019), [thesis](https://ir.lib.nycu.edu.tw/handle/11536/50437?locale=en)

### Inferences
- Connect6 techniques (two stones per turn) are only loosely transferable. The portable idea is RZOP/relevance zone: when a threat sequence wins, record only the cells that mattered, so the proof can be reused (and transposition-matched) across positions that differ outside the zone. In Connect Four this could be used to cache "forced win" results in a table keyed on the relevant columns/cells, but that is speculative and not shown in any Connect Four source I found.

### Gaps
- I could not read the full text of the RZOP and DBS papers (only abstracts), so implementation details (data structures, how the relevance zone is computed) are missing.
- Winners for 2011 and 2019 not stated; no Connect6 Olympiad results after 2019 were checked.
- ICGA Journal articles are paywalled; Kavalan/MOBILE 6/Bit techniques not described in retrieved sources.

## 3. Threat-space search, dependency search, VCF/VCT, pattern tables: how they are implemented efficiently

### Takeaway
Allis's 1994 threat-space search (TSS) solved freestyle Gomoku by searching only the attacker's threat moves (with the defender restricted to replies to threats), combined with proof-number search and a position database. Modern engines reduce this to VCF (continuous fours) and VCT (fours and threes) searches inside or at the leaves of alpha-beta, backed by per-line pattern lookup tables updated incrementally.

### Cited Findings
- Allis, van den Herik, Huntjens, "Go-Moku Solved by New Search Techniques" (AAAI Tech Report FS-93-02, 1993) and "Go-Moku and Threat-Space Search": Victoria used threat-space search and proof-number search; Black wins Go-Moku in 18 moves (35 ply) against best defence; the solution tree held 138,790 positions; Victoria won gold at the 1992 Computer Olympiad. — [Tilburg Univ. record: Go-Moku solved](https://research.tilburguniversity.edu/en/publications/go-moku-solved-by-new-search-techniques/), [Go-Moku and threat-space search](https://research.tilburguniversity.edu/en/publications/go-moku-and-threat-space-search/)
- Full text is available: Allis, "Go Moku" PDF — [renju.se/rif/proof/Go-Moku.pdf](https://www.renju.se/rif/proof/Go-Moku.pdf); Allis PhD thesis "Searching for Solutions in Games and AI" — [Maastricht PDF](https://project.dke.maastrichtuniversity.nl/games/files/phd/SearchingForSolutions.pdf). (I did not open these; they are the pointers for implementation detail.)
- VCF = sequence of continuous fours with no chance for the opponent to counterattack; VCT = continuous threes and fours; both are "very significant strategies for alpha-beta pruning" — [arXiv 1912.05407 (UCT-ADP Gomoku)](https://arxiv.org/pdf/1912.05407)
- Typical modern engine flow: check own VCF/VCT win, then check whether the opponent has one and must be blocked; otherwise heuristic evaluation (threats, centre, connectivity) with alpha-beta, iterative deepening, TT. — [rarepike overview](https://rarepike.com/five/computer-ai/) (low reliability; consistent with engine notes above)
- Efficient implementation evidence from engines: pattern code per line (Rapfi: 397,488 patterns in a codebook, incremental updates only for changed stones) — [arXiv 2503.13178](https://arxiv.org/html/2503.13178v1); Connect6 bitboard knowledge bases and bitboard connection codes (Yen and Yang) — [Chessprogramming wiki: Connect6](https://chessprogramming.org/Connect6); PentaZen precomputes pattern header files at build time — [GitHub PentaZen](https://github.com/schibir/PentaZen); Squirrel is plain alpha-beta + iterative deepening + VCF/VCT + TT — [Gomocup 2023](https://gomocup.org/results/gomocup-result-2023).
- AlphaGomoku upgraded its VCF solver "to alpha-beta search" in 2023 (i.e., VCF is itself searched with alpha-beta, not just DFS). — [Gomocup 2023](https://gomocup.org/results/gomocup-result-2023)

### Inferences
- In 4-in-a-row, "four" = three-with-an-open-completion-cell. The VCF analogue is a search where the attacker only plays moves that create an immediate winning threat (a cell that wins next move) and the defender is forced to the single blocking cell. With gravity, a threat is only "live" if the completion cell is currently playable or becomes playable at the right parity; this makes forced lines even narrower (one defender reply) and shallow-cheap to search.
- Dependency-based search (reuse of independent threat areas) probably has low value on an 8x12 board with 4-in-a-row; it targets 15x15+ boards with many independent regions.

### Gaps
- Did not read Allis's thesis chapters on TSS (dependency stage vs combination stage) or the Rapfi/AlphaGomoku VCF source; no concrete node/second numbers for VCF searches were found.
- No source with benchmark numbers (nodes/s) for pattern-table evaluation in Java.

## 4. Gravity-specific k-in-a-row games (Connect Four family) and bots/papers

### Takeaway
Gravity k-in-a-row has a rich solver literature for k=4 (bitboards, alpha-beta, TT, dynamic move ordering; Tromp, Allis, Pons) but I found no published bots or papers for gravity Gomoku / Connect-5 variants. Wide Connect-4 boards: results exist up to 8x8 and 9x6 (first player generally not guaranteed a win).

### Cited Findings
- 7x6 Connect Four solved independently by James Dow Allen (Oct 1, 1988) and Victor Allis (Oct 16, 1988, knowledge-based with nine strategies); first player wins starting in the centre column; adjacent columns draw; outer columns lose. John Tromp later solved by brute force. — [Wikipedia: Connect Four](https://en.wikipedia.org/wiki/Connect_Four)
- Tromp's solver: exhaustive alpha-beta + transposition table (with smarter replacement strategy) + dynamic move ordering; bitboards encode a position in 49 bits and test a win with 8 shifts/ANDs; 8-ply database cost ~40,000 hours; result grid for widths/heights 4-11; 9x6 finished in 2005 (~2E13 positions, ~2000 h on a 1.4 GHz Opteron); 8x8 solved late 2015 as a second-player win; Fhourstones benchmark is a guide to exhaustive alpha-beta + TT. — [Tromp: Connect Four](https://tromp.github.io/c4/c4.html)
- 7x6 solved with a transposition table of size 2^28 with always-replace, standard bitboards (Böck, arXiv 2507.05267); Pons's solver described as negamax alpha-beta with TT on bitboards. — [arXiv 2507.05267](https://arxiv.org/pdf/2507.05267) (title/abstract level only; PDF body unreadable by my tool), [search summary of related papers](https://docs.rs/connect-four-ai)
- Infinite Connect-Four solved as a draw (Yamaguchi et al., 2012). — [Wikipedia: Connect Four](https://en.wikipedia.org/wiki/Connect_Four)
- Pons's tutorial lives at blog.gamesolver.org (fetch failed on TLS certificate); the gamesolver.org front page only confirms alpha-beta and the score convention (sign = outcome, magnitude = moves to end). — [gamesolver.org](http://www.gamesolver.org/)
- Searches for "gravity gomoku / Connect 5 gravity bots" returned only ordinary Gomoku material. — (no source)

### Inferences
- Tromp's 49-bit bitboard (height+1 bits per column) generalizes to 8 rows x 12 columns: 9 bits x 12 = 108 bits, which does not fit in one long, so use two longs (or `long[2]`, or per-column shorts) in Java. Win tests by shift-and-AND remain 4 shifts (directions) per colour.
- Center-first dynamic move ordering and a TT are the proven core for gravity boards; the pattern-table eval of Gomoku engines is replaced here by threat counting on playable/odd-even cells (known Connect-Four practice, but I did not find a source for the odd/even threat rule in these fetches).

### Gaps
- No bots or papers for gravity Gomoku / "Connect 5" were found. 
- Pons's tutorial text (move ordering by centre, "anticipate losing moves", etc.) could not be fetched; those details are not cited here.
- No published solved result for 4-in-a-row on 8x12 or for a special "explosion" move.

## 5. What is cheap enough for ~100 ms in Java and known to give big gains

### Takeaway
From the evidence: (1) alpha-beta/PVS with iterative deepening and a TT; (2) bitboard move/win generation; (3) incremental pattern-based evaluation (lookup per line pattern); (4) a forced-threat (VCF-like) search at leaves/root; (5) good move ordering (threat-aware, killers/history). The expensive parts (NNUE with AVX, MCTS+ResNet, proof-number search with databases) are not cost-effective at 100 ms in plain Java.

### Cited Findings
- Squirrel, a "simple C++ AI" with alpha-beta, iterative deepening, VCF/VCT and TT, entered Gomocup 2023 (placement not given on the page). — [Gomocup 2023](https://gomocup.org/results/gomocup-result-2023)
- Rapfi's alpha-beta stack (PVS, futility pruning, LMR, null move, singular extension, TT, VCF at leaves, SPSA tuning) is the strongest published combination, and incremental pattern updates are explicitly valuable for depth-first search. — [arXiv 2503.13178](https://arxiv.org/html/2503.13178v1)
- Rapfi's NNUE speed relies on int8/int16 AVX2 SIMD (~4x over non-vectorized). — [arXiv 2503.13178](https://arxiv.org/html/2503.13178v1)
- Evidence that tuning matters: SPSA parameter tuning used by Rapfi; "Mix8 to Mix9 +20 to +55 Elo" is secondary. — [arXiv 2503.13178](https://arxiv.org/html/2503.13178v1), [namu.moe Rapfi](https://namu.moe/w/Rapfi)
- Fastgame league (5 s/move) was won by Rapfi, Yixin (2013-2017), Embryo (2021), showing the same stacks work at short time controls. — [Gomocup 2025](https://gomocup.org/results/gomocup-result-2025), [Wikipedia: Gomocup](https://en.wikipedia.org/wiki/Gomocup), [Gomocup 2021](https://gomocup.org/results/gomocup-result-2021)

### Inferences
- Priority list for Quantum Connect (ranked by expected gain per implementation cost, my judgment):
  1. Immediate win/loss checks and forced-block logic at every node (equivalent of VCF-lite); cheap and decisive.
  2. Negamax alpha-beta + iterative deepening with a hard time check (poll the clock every N nodes), TT keyed by Zobrist hash, centre-out/threat-first move ordering.
  3. Bitboard state (2 longs per side) with incremental threat/pattern counts.
  4. Dedicated threat-sequence search (attacker forcing moves only) at root and as quiescence at depth limit.
  5. Tune evaluation weights offline via self-play (SPSA-like), since Rapfi shows tuning is worth tens of Elo.
- The special "destroy around drop point" move breaks monotonic position growth (discs are removed), so TT entries, incremental pattern counts and the "forced threat" logic need explicit handling: treat the special move as a separate branch, and do not apply gravity-based threat parity pruning across it. No source covers this.
- MCTS/NN approaches (JAX, Barbakan) are not cheap enough for 100 ms in Java without a trained net and would need training infrastructure; skipping them is a judgment, not a sourced result.

### Gaps
- No published benchmarks of Java alpha-beta nodes/s on Connect-Four-like games; JIT warm-up cost within the first 100 ms moves is untested here.
- No source quantifying the Elo gain of VCF vs plain alpha-beta, or of pattern evaluation vs simple scoring.
