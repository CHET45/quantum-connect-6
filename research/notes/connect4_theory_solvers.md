# Connect Four / connect-k theory and classical solver techniques (for an 8x12, 100 ms, destroy-move bot)

Scope: game-theoretic results for Connect Four and related k-in-a-row games, and the solver techniques that matter for a fast, Java-only bot on an 8-row x 12-column gravity board with a periodic "destroy" special move. Every factual claim has a source; my own reasoning is kept under "Inferences".

## 1. Allis (1988) knowledge-based solution, Allen's independent solution, Tromp's database and other board sizes

### Takeaway
Standard 7x6 Connect Four is a first-player win if the first move is in the centre column. James D. Allen solved it on Oct 1 1988 and Victor Allis independently on Oct 16 1988. Allis used 9 provable "zugzwang/parity" rules plus conspiracy-number search. John Tromp later confirmed and extended the result by brute force and solved many other sizes up to 9x6 (2005) and 8x8 (2015). No published result covers a board with 12 columns or 8x12. In every solved case where both width and height are even and width >= 6, the second player wins. An 8x12 board has that shape, but its value is unknown.

### Cited Findings
**Who solved it, and when**
- Allen announced the first weak solution on rec.games.programmer on Oct 1 1988. Allis announced an independent solution 15 days later — [Tromp, John's Connect Four Playground](https://tromp.github.io/c4/c4.html); [Wikipedia: Connect Four](https://en.wikipedia.org/wiki/Connect_Four)
- Allis's thesis is "A Knowledge-Based Approach of Connect Four: The Game is Over, White to Move Wins", M.Sc. thesis, Report IR-163, Vrije Universiteit Amsterdam, 1988. A companion paper by Uiterwijk, van den Herik and Allis appeared in 1989 in *Heuristic Programming in AI 1*, and Allen's "A note on the Computer Solution of Connect Four" appeared in the same volume. Allen also wrote "Expert Play in Connect-Four" (1990) and *The Complete Book of Connect 4* (1990; 2nd ed. 2010) — [Chessprogramming wiki: Connect Four](https://www.chessprogramming.org/Connect_Four)
- Value of the opening move: the first player wins by starting in the centre column, on or before the 41st move. Starting in a column next to the centre is a theoretical draw. Starting in columns 1/7 loses (second player wins on move 40), and starting in columns 2/6 also loses (on move 42) — [Wikipedia: Connect Four](https://en.wikipedia.org/wiki/Connect_Four)

**Allis's terminology and the zugzwang / odd-even theory** (from MIT ES.268 notes, which reproduce figures from Allis's thesis with permission)
- A *threat* is a square that would complete a group of four. A threat can only be carried out if the opponent is forced to play the square below it. A *useless threat* is one above a square where both players have a threat, so it can never be realised — [MIT OCW ES.268, Connect Four notes (2010)](https://ocw.mit.edu/courses/es-268-the-mathematics-in-toys-and-games-spring-2010/94a5d1da64c57cbc5fdf135d985445a9_MITES_268S10_Ses5_ConFour.pdf)
- *Odd and even threats* are classified by row number (row 1 is the bottom and is odd). If the board fills up normally, White (first player) gets the odd squares and Black the even squares. The notes give these rules of thumb:
  - White has an odd threat and Black an even threat: White wins.
  - Both have only even threats: Black refutes and wins.
  - White even, Black odd: draw.
  - Both odd: usually neither works, and the result depends on other threats.
  - Same column: the lower threat wins.
  - Source: [MIT OCW ES.268](https://ocw.mit.edu/courses/es-268-the-mathematics-in-toys-and-games-spring-2010/94a5d1da64c57cbc5fdf135d985445a9_MITES_268S10_Ses5_ConFour.pdf)
- *Zugzwang control* means being able to dictate how the odd and even squares are split between the players. In the example, every column holds an even number of men, so Black plays "follow-up" (answers in the same column) and claims all the even squares — [MIT OCW ES.268](https://ocw.mit.edu/courses/es-268-the-mathematics-in-toys-and-games-spring-2010/94a5d1da64c57cbc5fdf135d985445a9_MITES_268S10_Ses5_ConFour.pdf)

**Allis's nine rules.** Each is stated for the player who controls the zugzwang. The "solutions" of a rule are the opponent's groups that the rule refutes. Source for all nine: [MIT OCW ES.268](https://ocw.mit.edu/courses/es-268-the-mathematics-in-toys-and-games-spring-2010/94a5d1da64c57cbc5fdf135d985445a9_MITES_268S10_Ses5_ConFour.pdf)
  1. **Claimeven.** The controller takes every empty even square that is not directly playable by letting the opponent play the odd squares. Needs two empty squares one above the other, with the upper one even. Refutes every group containing the upper square.
  2. **Baseinverse.** A player cannot play two directly playable squares in one turn. Needs two directly playable squares. Refutes every group containing both.
  3. **Vertical.** A player cannot play two men in the same column in one turn. Needs two empty squares one above the other, with the upper one odd. Refutes groups containing both.
  4. **Aftereven.** A side effect of claimevens. If the controller can complete a group using only claimeven squares, he will eventually complete it. Refutes the opponent groups that have a square in every aftereven column above the empty aftereven-group square.
  5. **Lowinverse.** Based on odd + odd = even. Needs two columns, each with two empty stacked squares whose upper square is odd. Refutes groups containing both upper squares, plus those solved by the verticals.
  6. **Highinverse.** Needs two columns, each with three empty squares, whose upper square is even. Refutes groups through the two upper squares, through the two middle squares, and certain vertical and cross groups.
  7. **Baseclaim.** Two baseinverses plus a claimeven. Needs three directly playable squares and the square above the second one, which must be even.
  8. **Before.** Claimevens plus verticals. Needs a group with no opponent men (the "Before group") whose empty squares are not in the top row. Refutes groups containing all the successors of its empty squares.
  9. **Specialbefore.** A Before group plus one directly playable square in another column.
- **How VICTOR combines the rules.** It finds every instance of the nine rules and checks them against all 69 possible winning groups on 7x6. Two rule applications are "adjacent" in a conflict graph when they cannot be used together (Allis §7.4 gives the compatibility table). The solver then looks for an independent set of solutions that covers every opponent group (problem). It uses a recursive FindChosenSet that always branches on the problem with the fewest candidate solutions. The notes say this is potentially NP-complete — [MIT OCW ES.268](https://ocw.mit.edu/courses/es-268-the-mathematics-in-toys-and-games-spring-2010/94a5d1da64c57cbc5fdf135d985445a9_MITES_268S10_Ses5_ConFour.pdf)
- **Search around the rules.** The full solution combined this rule-based evaluator with stored position tables and conspiracy-number search, a relative of proof-number search. Node values were "Black at least draws", "White wins" and "undecided" — [MIT OCW ES.268](https://ocw.mit.edu/courses/es-268-the-mathematics-in-toys-and-games-spring-2010/94a5d1da64c57cbc5fdf135d985445a9_MITES_268S10_Ses5_ConFour.pdf)

**Tromp's brute-force work**
- Tromp built a database of all 8-ply positions with their theoretical values. It took about 40,000 hours on Sun and SGI workstations at CWI. The 67,557 unfinished, unforced 8-ply positions were published as the UCI "Connect-4" data set (Feb 4 1995). His applet plays perfectly from the 8-ply database, read from a 12 KB compressed file — [Tromp](https://tromp.github.io/c4/c4.html); [Wikipedia](https://en.wikipedia.org/wiki/Connect_Four)
- Tromp's solver is also the Fhourstones integer benchmark, which the page calls a guide to exhaustive alpha-beta with a transposition table and dynamic move ordering. His board code tests for a win with 8 shifts/ANDs and encodes a 7x6 position in 49 bits — [Tromp](https://tromp.github.io/c4/c4.html); [Fhourstones](https://tromp.github.io/c4/fhour.html)
- **Board-size results** ("+" first-player win, "=" draw, "−" second-player win), transcribed cell by cell from the raw HTML table — [Tromp](https://tromp.github.io/c4/c4.html):

  | height \ width | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |
  |---|---|---|---|---|---|---|---|---|
  | 11 | = | | | | | | | |
  | 10 | = | = | | | | | | |
  | 9 | = | = | + | | | | | |
  | 8 | = | = | − | + | − | | | |
  | 7 | = | = | + | = | + | | | |
  | 6 | = | = | − | **+** (standard 7x6) | − | − | | |
  | 5 | = | = | = | = | + | + | + | |
  | 4 | = | = | − | = | − | − | − | − |

  - So 8x7 (w8, h7) is a first-player win and 9x6 is a second-player win. 9x6 finished in Nov 2005 after about 2×10^13 positions and about 2000 hours on a 1.4 GHz Opteron 840. 8x8 (w8, h8) was solved as a second-player win in late 2015 (code and opening book at github.com/tromp/fhourstones88). 7x8 (w7, h8) is a first-player win.
  - Note: an automated summary of this page mislabelled 9x6 as "+". The raw HTML is left-aligned from width 4, and on it 9x6 is "−".
- On 6xh boards the winning first moves are never in the central columns — [Tromp](https://tromp.github.io/c4/c4.html)
- **Number of positions.** Standard 7x6 has 4,531,985,219,092 legal positions (OEIS A212693). Edelkamp & Kissmann had already obtained this count in 2008 ("Symbolic Classification of General Two-Player Games") with BDDs. Tromp also lists counts for smaller boards, for example 8x4 = 54,233,186,631 — [Tromp](https://tromp.github.io/c4/c4.html); [Wikipedia](https://en.wikipedia.org/wiki/Connect_Four)
- **Strong solution (2025).** A full win/draw/loss look-up table for 7x6 was computed and published on Zenodo by Markus Böck in 2025 — [Wikipedia, citing Böck 2025, doi:10.5281/zenodo.14582823](https://en.wikipedia.org/wiki/Connect_Four)
- **Infinite variants.** Infinite Connect-Four is a draw (Yamaguchi, Yamaguchi, Tanaka, Kaneko, ACG 2011). Cylinder-Infinite Connect-Four is a draw for every width except 2, 6 and 11 (CG 2013). The first player has a cannot-lose strategy for widths 2 and 6 (Yamaguchi & Neller, ACG 2015) — [Chessprogramming wiki](https://www.chessprogramming.org/Connect_Four); [Wikipedia](https://en.wikipedia.org/wiki/Connect_Four)

### Inferences
- **What the solved sizes suggest for 8x12.** In every solved board where both width and height are even and width >= 6, the second player wins: 6x4, 8x4, 10x4, 6x6, 8x6, 6x8, 8x8. 12x8 is even-by-even, so plain gravity Connect-4 on 8x12 *might* favour the second player through zugzwang. This is an extrapolation, not a result. With odd height (5 or 7) the first player tends to win on wide boards: 8x5, 9x5, 10x5, 8x7 and 6x7 are all "+".
- **Parity carries over to height 8.** The odd/even threat theory and all of Allis's rules assume columns that fill from the bottom. With height 8 (even, like 6), each column has 4 odd and 4 even squares and the total of 96 cells is even. The zugzwang logic is therefore unchanged: under follow-up play the first player gets the odd rows and the second player the even rows. The same "odd threat for player 1, even threat for player 2" evaluation applies.
- **No single centre column.** With 12 columns the centre-first idea becomes "columns 6 and 7 (1-based) first".
- **Many more groups to cover.** 8x12 has 72 horizontal, 60 vertical and 90 diagonal groups, so 222 four-in-a-row groups against 69 on 7x6. Allis-style rule covering scales badly: it is an exact-cover / independent-set search over many more rule instances.
- **The destroy move breaks the rules.** Allis's rules are only valid when discs are permanent and squares fill in order. The destroy move breaks both assumptions. Claimeven, baseinverse and the others can be overturned whenever a destroy is available to either side, so they should only be trusted while neither side can destroy within the horizon.

### Gaps
- I could not retrieve Allis's thesis PDF; the Wikipedia link to uni-trier.de failed to download. The rule definitions above come from MIT OCW notes that reproduce the thesis with permission, not from the thesis itself. The exact compatibility table (Allis §7.4) and VICTOR's runtimes were not captured.
- I did not obtain the text of Allen's "Expert Play in Connect-Four"; its link is on Tromp's page.
- I found no published game-theoretic result for any board with 12 columns, and none for 8 rows beyond widths 4 to 8.
- I did not verify whether Tromp's table has been extended after 2015. The page shows nothing newer.

## 2. Pascal Pons' Connect 4 solver tutorial: engineering and performance numbers

### Takeaway
Pons's 12-part tutorial goes from plain minimax to a strong solver. The steps are negamax, alpha-beta, centre-first ordering, bitboards, a transposition table, iterative deepening with null windows, pruning of losing moves, threat-count move ordering, a compact TT, and lower-bound TT entries. The final strong solver handles the hardest 7x6 test set (Begin-Hard) in about 5.5 s and 66 M nodes per position, at roughly 12 M nodes/s in C++. The two largest gains are move ordering, worth 10x to 100x in nodes, and the transposition table. Bitboards are worth about 6x in speed with identical nodes.

### Cited Findings
**Score convention and test set**
- Positions are written as the sequence of columns played. A win scores 22 minus the number of stones the winner has played at the end, so earlier wins score higher. A draw scores 0 and a loss is the negative — [Pons, Part 2](https://blog.gamesolver.org/solving-connect-four/02-test-protocol/)
- The benchmark has 6 test sets of 1000 positions each, combining game stage (moves <= 14, 14 to 28, > 28) with distance to the end (< 14, 14 to 28, >= 28 remaining): End-Easy, Middle-Easy, Middle-Medium, Begin-Easy, Begin-Medium, Begin-Hard — [Pons, Part 2](https://blog.gamesolver.org/solving-connect-four/02-test-protocol/)

**Reference: Tromp's Fhourstones as a weak solver** (mean time / mean nodes / K pos/s) — [Pons, Part 2](https://blog.gamesolver.org/solving-connect-four/02-test-protocol/)

| Test set | Time | Nodes | K pos/s |
|---|---|---|---|
| End-Easy | 3.69 µs | 39.8 | ~10,800 |
| Middle-Easy | 176 µs | 2,101 | ~11,900 |
| Middle-Medium | 2.41 ms | 28,725 | ~11,900 |
| Begin-Easy | 209 ms | 2,456,180 | ~11,750 |
| Begin-Medium | 111 ms | 1,296,900 | ~11,750 |
| Begin-Hard | 7.95 s | 93,425,600 | ~11,750 |

**Each step and what it bought**
- **Minimax and alpha-beta.** Plain minimax only solved End-Easy within 24 h. Alpha-beta cut nodes sharply but still could not do Begin-Hard — [Pons, Part 3](https://blog.gamesolver.org/solving-connect-four/03-minmax/); [Part 4](https://blog.gamesolver.org/solving-connect-four/04-alphabeta/)
- **Centre-first column order.** Exploring the centre columns first cut nodes and time by more than 10x, with bigger gains on deeper positions. Middle-Easy took 189 ms and 2.08 M nodes as a strong solver — [Pons, Part 5](https://blog.gamesolver.org/solving-connect-four/05-move-exploration-order/)
- **Bitboard.**
  - Layout: W×(H+1) bits, i.e. H bits per column plus a sentinel bit, so 49 bits on 7x6.
  - Two bitmaps: the current player's stones and a mask of occupied cells. The key is `position + mask`. A move flips the player bitmap (XOR with the mask) and adds a bit to the mask.
  - A win is tested with shift/AND in 4 directions.
  - Result: same nodes, about 6x faster. Middle-Easy dropped to 33 ms at about 62.5 M pos/s; Middle-Medium took 644 ms for 40.4 M nodes.
  - Source: [Pons, Part 6](https://blog.gamesolver.org/solving-connect-four/06-bitboard/)
- **Transposition table.**
  - Design: a fixed-size hash table that always overwrites (no collision handling), storing only upper bounds. Entries are a 56-bit key plus an 8-bit value, 64 MB in total.
  - Result: Middle-Medium went from 644 ms / 40.4 M nodes to 52 ms / 1.73 M nodes as a strong solver.
  - Source: [Pons, Part 7](https://blog.gamesolver.org/solving-connect-four/07-transposition-table/)
- **Iterative deepening and null window.**
  - Because the score is bounded by the number of moves left, a narrow [alpha, beta] window limits search depth implicitly.
  - `solve()` runs a binary search of null-window [a, a+1] probes, alternating between the positive and the negative side, so short wins and losses are found first.
  - This mainly helps the strong solver on easy and medium sets and does almost nothing for the weak [-1, 1] solver.
  - Source: [Pons, Part 8](https://blog.gamesolver.org/solving-connect-four/08-iterative-deepening/)
- **Anticipating losing moves.**
  - `possibleNonLosingMoves()` applies three rules: play a column where the opponent has a winning square at the bottom; never play directly under an opponent's winning square; if the opponent has two or more directly playable wins, the position is lost.
  - The opponent's winning squares (open 3-alignments) are computed with bitboard shifts.
  - Result: Middle-Medium dropped to 32 ms / 1.135 M nodes (strong) and Begin-Easy to 3.5 ms / 107 K nodes (strong).
  - Source: [Pons, Part 9](https://blog.gamesolver.org/solving-connect-four/09-anticipate-losing-moves/)
- **Better move ordering.**
  - Each move is scored by the number of winning squares (open threes) the player has after it, using a popcount fast on sparse bitboards; ties keep centre-first order.
  - Nodes fell by up to 100x on complex cases. Per-node cost rose about 3x.
  - Strong-solver results: Begin-Hard 6.45 s / 71.4 M nodes, Begin-Medium 126 ms / 1.45 M, Begin-Easy 296 µs / 3,692, Middle-Medium 4.1 ms / 48 K, at about 11 to 12.5 M pos/s.
  - Weak-solver result: Begin-Hard 3.73 s / 41.5 M nodes.
  - Source: [Pons, Part 10](https://blog.gamesolver.org/solving-connect-four/10-better-move-ordering/)
- **Optimised transposition table.**
  - The 49-bit key is stored as 32 bits plus an 8-bit value, which is valid because the table size is an odd number above 2^17 (Chinese-remainder argument). The table has 8,388,617 entries (the smallest prime above 2^23), about 40 MB.
  - Result: a little under 10% faster.
  - Source: [Pons, Part 11](https://blog.gamesolver.org/solving-connect-four/11-optimized-transposition-table/)
- **Lower-bound TT entries.**
  - Lower bounds from beta cutoffs are also stored, flagged by an offset in the same table.
  - Result: 10 to 15% fewer nodes. Final strong solver: End-Easy 4.6 µs / 51 nodes; Middle-Easy 37 µs / 450; Middle-Medium 3.2 ms / 39.9 K; Begin-Easy 255 µs / 3.3 K; Begin-Medium 96.6 ms / 1.2 M; Begin-Hard 5.49 s / 65.9 M at about 12 M pos/s. Final weak solver: Begin-Hard 3.15 s / 38.1 M.
  - Source: [Pons, Part 12](https://blog.gamesolver.org/solving-connect-four/12-lower-bound-transposition-table/)
- An opening book is mentioned in the series and the solver code is on GitHub (PascalPons/connect4). I did not capture opening-book numbers.

### Inferences
- **The bitboard does not fit in one `long`.** The W×(H+1) layout needs 12×9 = 108 bits on 8x12, more than one Java `long`. Options:
  - Two `long`s, each holding 6 columns × 9 bits = 54 bits. Horizontal and diagonal shifts then cross the word boundary and need a carry or merge step.
  - A row-major layout with a padding bit per row: 8 rows × 13 bits = 104 bits, still two words.
  - Either way, win detection, threat maps and `possibleNonLosingMoves` all have to work on two words. That is still far cheaper than array scans.
- **What to port into 100 ms.** Pons's techniques that need no full solve all apply directly at fixed depth: negamax with alpha-beta, centre-first ordering, threat-count ordering, the non-losing-move filter, a TT with lower and upper bounds, and iterative deepening.
- **Exact solving is only feasible near the end.** Even 7x6 Begin-Hard takes seconds in C++. 8x12 has 96 cells and a vastly larger state space: an upper bound of 511^12 ≈ 3×10^32 column configurations, against about 4.5×10^12 legal 7x6 positions. Exact solving fits in 100 ms only when roughly 14 or fewer moves remain (End-Easy-like positions: microseconds on 7x6). Earlier, the bot must rely on depth-limited search plus a heuristic.
- **Pons's score is not well-defined with destroys.** The score definition (22 minus stones played) and the "window bounds depth" trick assume the game ends within a fixed number of plies. A destroy move removes stones, so neither holds. The bot needs its own mate-distance scoring based on ply from the root, plus a draw or move-limit rule.

### Gaps
- I did not retrieve exact numbers for Parts 3 and 4 (the minimax and alpha-beta tables) beyond the qualitative statements.
- I did not verify the opening-book details on GitHub.

## 3. Threat-based evaluation heuristics for fixed shallow depth

### Takeaway
The academic and solver literature agrees that what matters in Connect Four is parity-aware threat analysis, not plain counts of 2s and 3s. Odd threats are worth more to the first player and even threats to the second, threats in the same column are ranked by height, and threats above an opponent threat are useless. I found no controlled academic study comparing shallow-depth evaluation functions. The evidence is Allis's theory, Pons's empirical finding that threat count is an excellent move-ordering key, and anecdotal practitioner reports.

### Cited Findings
- **Parity rules.** White wins with an odd threat against Black's even threat. With even threats on both sides, Black wins. With White even and Black odd, the game is drawn. Within a column, the lower threat decides — [MIT OCW ES.268](https://ocw.mit.edu/courses/es-268-the-mathematics-in-toys-and-games-spring-2010/94a5d1da64c57cbc5fdf135d985445a9_MITES_268S10_Ses5_ConFour.pdf)
- **Useless threats.** Threats lying above a square where both players have a threat can never be realised and should be ignored — [MIT OCW ES.268](https://ocw.mit.edu/courses/es-268-the-mathematics-in-toys-and-games-spring-2010/94a5d1da64c57cbc5fdf135d985445a9_MITES_268S10_Ses5_ConFour.pdf)
- **Threat count orders moves well.** Counting the "winning squares" (open threes) a move creates gives up to 100x node reduction as a move-ordering key — [Pons, Part 10](https://blog.gamesolver.org/solving-connect-four/10-better-move-ordering/)
- **Immediate tactics as a filter.** Never play under an opponent's threat square, and recognise that two or more playable opponent threats are a forced loss. Pons uses these as an exact pruning rule — [Pons, Part 9](https://blog.gamesolver.org/solving-connect-four/09-anticipate-losing-moves/)
- **Practitioner reports (anecdotal, not peer-reviewed).**
  - Simple threat-counting evaluators cannot tell a shared threat square favouring player 1 (odd) from one favouring player 2 (even).
  - One player-1 odd threat outweighs any number of player-2 even threats.
  - Engines with naive evaluation showed "no remarkable improvement" going from depth 10 to 12, while adding a "good threat" (parity) term improved play markedly.
  - Source: [GameDev.net forum thread 644496](https://gamedev.net/forums/topic/644496-connect-4-evaluation-functionneed-some-guide/); [GameDev.net thread 667963](https://gamedev.net/forums/topic/667963-helpevaluationfunctionconnect4?page=1)
- **Learned evaluators.** Learned n-tuple evaluation functions for Connect-4 trained by TD learning are studied by Thill (2015, TH Köln) and Runarsson & Lucas (IEEE CIG 2015). They are listed as an alternative line of work, and I did not extract numbers — [Chessprogramming wiki](https://www.chessprogramming.org/Connect_Four)
- **Threat-space search in Victoria.** Victoria assigned points for patterns of two or three stones in a row and examined the N = 10 best-scored moves for hidden threats under a null move — [Allis, van den Herik & Huntjens, "Go-Moku and Threat-Space Search"](https://www.renju.se/rif/proof/Go-Moku.pdf)

### Inferences
Suggested evaluation for an 8x12 gravity board, from the cited theory:
- **Per open group.** For each of the 222 groups containing no opponent discs, score the number of own discs: 3 counts much more than 2, which counts much more than 1.
- **Threat squares by parity.** Count threats (empty completing squares) separately by row parity: odd-row threats for the first player and even-row threats for the second player get a large bonus.
- **Same-column threats.** Within a column only the lowest threat matters; drop threats above an opponent threat or above a shared square.
- **Immediate threats.** Two or more directly playable threats, or stacked threats (two threats in consecutive squares of one column), should be scored as near-wins.
- **Centre bonus.** Add a small bonus for central-column occupancy.
- **Turn tempo.** Add a term for side to move.
- **Destroy changes parity.** Parity terms should be damped when the opponent has a destroy move available. A destroy changes how many discs are in the affected columns and can flip which squares each player will be forced to take.

### Gaps
- I found no peer-reviewed benchmark comparing Connect-Four evaluation functions at fixed shallow depth. Claims about which heuristic is "strongest" rest on theory and practitioner reports.

## 4. m,n,k-games, Connect(m,n,k,p,q), threat-space search and proof-number search: what transfers

### Takeaway
k-in-a-row theory supplies the right tools, but the full-board results do not transfer directly to a gravity game:
- Strategy stealing shows the second player cannot win the gravity-free m,n,k-game. Gravity breaks that argument, which is why Tromp's table has second-player wins.
- Pairing strategies prove draws.
- Threat-space search plus proof-number search solved Go-Moku: a 35-ply win, 138,790-node solution tree.
- Wu & Huang defined Connect(m,n,k,p,q) and Connect6.

The pieces that transfer are the threat-sequence search (with gravity: a threat is only playable once the square below is filled), the null-move hidden-threat idea, and proof-number search for endgame proofs.

### Cited Findings
**m,n,k-game results**
- By strategy stealing, the second player cannot have a winning strategy in any m,n,k-game. If a game is a draw, then larger k or smaller boards are also draws; if a win, then larger boards or smaller k are also wins (Uiterwijk & van den Herik 2000; van den Herik et al. 2002) — [Wikipedia: m,n,k-game](https://en.wikipedia.org/wiki/M,n,k-game)
- **k = 4 (no gravity):**
  - (5,5,4) is a draw and (6,5,4) is a first-player win (Vogt 2025).
  - (m,4,4) is a draw for m <= 8 and a win for m >= 9 (Uiterwijk 2019).
  - k >= 9 is a draw by pairing strategy, and k = 8 is a draw on an infinite board.
  - (7,7,5) and (8,8,5) are draws (Hsu, Ko, Hsueh & Wu 2018), and (15,15,5) is a first-player win (Allis).
  - Source: [Wikipedia: m,n,k-game](https://en.wikipedia.org/wiki/M,n,k-game)
- **Connect(m,n,k,p,q).** Wu & Huang (2005, *Advances in Computer Games 11*, LNCS 4250, pp. 180–194) defined the family: k in a row, p stones per turn, q stones on the first move. Connect6 is Connect(m,n,6,2,1) and free-style Gomoku is Connect(15,15,5,1,1). They argued that p = 2q is necessary for fairness and proposed a threat-based strategy for playing these games, with null-move search used to solve a small instance. Hsieh & Tsai (TCS 2007) showed that no one wins from an empty board when k >= 4p + 7 and p >= q, and that the games are PSPACE-complete for suitable parameters — [NYCU repository: Wu & Huang](https://ir.lib.nycu.edu.tw/handle/11536/17080); [NYCU: fairness and complexity](https://ir.lib.nycu.edu.tw/handle/11536/10232)

**Threat-space search** (Allis, van den Herik & Huntjens 1993) — [Go-Moku and Threat-Space Search](https://www.renju.se/rif/proof/Go-Moku.pdf); [AAAI FS-93-02](https://aaai.org/?p=69164)
- **Definitions.**
  - Gain square: the square the attacker plays.
  - Cost squares: the defender's replies.
  - Rest squares: the squares completing the threat pattern.
  - Threat A depends on threat B if a rest square of A is the gain square of B.
  - Dependency trees are in conflict if a gain square of one is a cost square of the other, or they share cost squares.
- **Key trick.** The defender is allowed to play *all* cost squares at once, so the search covers attacker threats only. If a winning sequence survives, the defender's choices were irrelevant. The trade-off is that some wins are missed, and proof-number search is called as a fallback.
- **Performance.** Victoria TSS ran at 5,000 nodes/s against 500 for the knowledge-heavy Polygon. Polygon at 3 ply searched 3.2x to 87x more nodes on the test positions. TSS finds a winning threat sequence, if one exists, in about 0.1 CPU-seconds on average (IBM RS6000).
- **Admissibility.** TSS used as a first evaluation is admissible: it only reports wins it has proved.
- **Hidden threats.** At internal nodes with a branching factor above 200, the defender's moves were restricted to "hidden threats": moves that, after a null move, lead to a winning threat sequence. This cuts the defence to a few dozen moves.
- **Go-Moku solution.**
  - Non-restricted variant: 1.1 M CPU-seconds, 5.3 M positions investigated, solution tree of 138,790 nodes, depth 35.
  - Variant B: 1.3 M CPU-seconds, 6.3 M positions, 153,284-node tree.
  - Hardware: 10 SUN SPARCstation 2 machines running overnight, about 1000 CPU-hours a week.
  - TSS had earlier been applied to Qubic (Allis & Schoo 1992).
- **Proof-number search** is described in Allis, van der Meulen & van den Herik 1994 (*Artificial Intelligence*), cited inside the TSS paper. It is the standard solver for game trees with uneven branching.
- **PN search on a removal variant.** Pekkala (2014, University of Oulu) reports that PopOut on 7x6 is a first-player win, weakly solved with *unmodified* proof-number search. Smaller boards are first-player wins or draws by repetition — [Oulu repository record](https://oulurepo.oulu.fi/handle/10024/39813) (search snippet only; the page returned 403 when fetched directly)

### Inferences
- **TSS needs gravity changes.** In gravity Connect-4 a threat is "live" only when the square below it is filled, and forcing moves are mostly immediate wins and blocks in playable squares. A cheap TSS-like routine is therefore: search only moves that create a playable immediate threat (the opponent's reply is forced), with dependency through stacked squares. That is a forced-win "VCF" search worth running before alpha-beta in the 100 ms budget, and it is cheap on bitboards.
- **Null-move hidden threats transfer.** Victoria's restriction of defender moves to hidden threats under a null move is a direct template for pruning quiet moves at the root of a selective search.
- **Proof-number search** suits offline analysis, such as an opening book for a fixed 8x12 start or endgame verification, more than a 100 ms move budget.
- **m,n,k pairing and strategy-stealing results do not apply.** Gravity plus zugzwang makes second-player wins possible, as Tromp's table shows. The destroy move changes the rules again.

### Gaps
- I did not obtain the full text of Wu & Huang 2005, so I have no details of their threat-based strategy beyond the abstract.
- I found no publication applying threat-space search to Connect Four specifically.

## 5. Variants with piece removal (PopOut, Power-Up) and how the first-player advantage changes with board size

### Takeaway
Removal variants keep the first-player advantage on 7x6 (PopOut: first-player win by PN search), but repetition draws become possible. Commercial "Power Up" checkers include an anvil that removes all discs beneath it, the closest published analogue to a destroy move. I found no academic analysis of it. Board size changes the first-player advantage non-monotonically. On solved boards it depends strongly on parity (even×even boards with width >= 6 are all second-player wins), and the infinite board is a draw.

### Cited Findings
- **PopOut rules.** A player may drop a disc, or remove one of their own discs from the bottom row, after which the discs above drop one space. The first to connect four wins — [Wikipedia: Connect Four, PopOut](https://en.wikipedia.org/wiki/Connect_Four)
- **PopOut result.** PopOut on 7x6 is a first-player win, solved with proof-number search, and smaller boards are first-player wins or draws by repetition (Pekkala 2014, "Computer analysis of Connect-4 PopOut", University of Oulu) — [Oulu repository](https://oulurepo.oulu.fi/handle/10024/39813) (from search snippet; full text not accessed)
- **Power Up.** Special "Power Checkers" can each be used once per game. The anvil checker pops out all pieces below it and sits on the bottom row; another lets a player take a second consecutive turn — [Wikipedia: Connect Four, Power Up](https://en.wikipedia.org/wiki/Connect_Four)
- **Board size.** Results by size are non-monotonic: 7x6 is "+", 8x6 and 9x6 are "−", 8x7 is "+", 8x8 is "−", 7x7 is "=", and narrow boards of width 4 or 5 are draws at every solved height — [Tromp](https://tromp.github.io/c4/c4.html). Infinite Connect-Four is a draw (Yamaguchi et al. 2011) — [Wikipedia](https://en.wikipedia.org/wiki/Connect_Four)

### Inferences
- **What the destroy move does to the game.** It is closest to PopOut and the Power Up anvil, both of which make the game non-monotonic: squares can empty again. Consequences for the bot:
  1. **Game length is unbounded.** Add repetition detection or a ply cap, and use mate-distance scoring from the root.
  2. **The TT key must include destroy state.** The board alone is not enough: include the destroy cooldown counters for both players, or positions with different cooldowns will alias. Zobrist hashing over the two bitboard words plus small cooldown indices is the natural Java choice.
  3. **Parity rules only hold while destroy is unavailable.** Allis-style zugzwang rules and parity evaluation are sound only while neither player can destroy within the horizon. Every destroy resets the odd/even ownership of the squares it empties.
  4. **Defence must consider destroys.** A destroy can delete an opponent's three-in-a-row group, so the "non-losing moves" filter must also consider destroy replies. A position with two opponent threats is no longer automatically lost if a destroy can break both.
  5. **The destroy is also an attack.** It can let discs above fall, depending on the engine's rules, which can create new alignments.
  6. **Branching grows.** Each turn has up to 12 drops plus up to 12 destroys, which roughly doubles branching on turns where destroy is available. Order destroys after tactical drops unless they remove a threat.
- **Who is favoured on 8x12.** The evidence on parity and wide even-height boards suggests that, without destroys, the second player may hold the zugzwang on 8x12. The periodic destroy move probably weakens zugzwang-based advantages and favours tactical, threat-creating play. This is a hypothesis to test by self-play, not an established result.

### Gaps
- I found no academic work on Connect-Four variants with area-destroying or bomb moves, or on Power Up's anvil checker.
- I could not read the full text of Pekkala 2014 (HTTP 403), so I have no node counts or times for the PopOut solve.
- I found no study of first-player advantage specifically on 12-column boards or 8x12.
