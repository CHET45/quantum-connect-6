# Connect-Four threat theory and Rapfi VCF search, adapted to Quantum Connect

Game ("Quantum Connect"): gravity drop, 8 rows x 12 columns, SIX in a row (any direction, longer counts) wins; once per 10 own turns a special move (disc placed, 3x3 block centred on it cleared for both colours, gravity re-applied; any 6-line after a move wins for the mover, even the opponent's line); 100 ms per move; Java standard library only.

## Source legend and conventions

| Tag | File (folder `sources/text/`) |
|---|---|
| MIT | `pdf-p3-mit2010-mit-es268-connect-four-notes.md` (read in full; lecture notes summarising Allis 1988) |
| ALLIS | `pdf-p2-allis1994-searching-for-solutions-in-games.md` (only Connect-Four / Qubic / threat-sequence parts read) |
| CPW4 | `cpw-connect-four.md` |
| CPW6 | `cpw-connect6.md` |
| AB | `rapfi-code-search-ab.md` (search.cpp, movepick, hashtable, parameter.h ...) |
| PB | `rapfi-code-patterns-board.md` (pattern, board, wincheck, movegen, types ...) |

Citations are `[TAG Lnn-mm]` = line numbers of that .md file (verified in this session) plus the section name where useful. **[OWN]** = my own reasoning or inference, not stated in a source. **[UNCERTAIN]** = the source text is garbled (pdftotext) or ambiguous.

Global caveats:
- Every figure in MIT (Allis Figs 3.1, 3.9, 4.2, 4.3 and the rule-compatibility table of Allis section 7.4) is missing from the text; only captions remain [MIT L97-98, L115, L172-181, L334-335]. Statements that depend on a figure are flagged.
- The ALLIS file (Allis' 1994 PhD thesis) does NOT contain the knowledge rules: grep for "claimeven" and "zugzwang" finds nothing. It only gives Connect-Four statistics and the Qubic threat-sequence machinery. The rule definitions come solely from MIT.
- CPW4 contains only history and bibliography, no theory [CPW4 L19-21, L31-55].
- The 7x6 theory is stated for width 7, height 6, line length 4. Section 3 re-derives what carries over to 12x8, line length 6.

---

## 1. Connect-Four knowledge rules (Allis 1988 / VICTOR)

### 1.1 Basic vocabulary [MIT L87-116]

- Board numbering: columns a-g, rows 1-6 bottom to top; the middle bottom square is d1 [MIT L89-91].
- **Threat**: defined as "a square that if taken by opponent forms a game-winning group of four" [MIT L93-95]. [UNCERTAIN: the extracted wording says "taken by opponent"; the standard meaning, and the one used in the rest of the notes, is the empty square that completes a group for the threat's OWNER. The example "White has threats at b1 and f1" is a figure we cannot see.] Used in this digest as: a threat of player X = an empty square that completes a winning group of X.
- A threat is only carried out if the opponent is forced to play the square below it, or lets you play below it [MIT L100-104, L118-121].
- **Useless threat**: a threat that can never be carried out. Example in the notes: White threatens b2..b6 and f2..f6, Black threatens b2, b6, f2, f6; because b2 and f2 are shared (both players have a threat on the same square) and lower squares fill first, every threat above them is useless [MIT L107-113; picture missing].

### 1.2 Odd/even threats [MIT L118-156]

- Parity of a threat = row number (d1 is an odd threat) [MIT L123-124].
- Default square ownership if the board is simply filled: White (first player) gets the odd rows, Black the even rows. Reason given: play alternates and the number of squares is even, so Black must end with row 6, White with row 5, and so on down [MIT L124-135; they note exceptions are possible].
- Resulting outcome table (same column / different columns) [MIT L141-153]:

| White threat | Black threat | Result per MIT |
|---|---|---|
| odd | even | White wins. Same column: the LOWER threat wins; different columns: White's is stronger [MIT L141-145] |
| even | even | Black wins: no column has an odd number of free squares, both get their normal squares, Black refutes White's threat [MIT L146-150] |
| even | odd | Draw [MIT L151]. (Also: if only the last column remains with White even / Black odd, draw [MIT L137-139]) |
| odd | odd | Usually neither works, depends on other threats [MIT L152-153] |

- Caveat: when neutralising one threat, check that doing so does not create another [MIT L155-156].

### 1.3 Zugzwang [MIT L158-189]

- Definition: a player is forced to move although he would rather not move [MIT L158-160].
- "Controlling the zugzwang" = being able to steer how odd and even squares are divided between the players [MIT L162-164].
- Worked example (Allis p.25-26, figures missing): all columns have an even number of discs, White to move. White can only take odd squares, so it never fills a column; Black plays **follow-up** (answers every White move in the same column). The position then reaches a state where White must play b1 or f1, Black follows and wins with a group on row 2 [MIT L166-189].
- Takeaway: Black wanted only even squares there; if it had wanted odd squares it could simply have stopped following up and played elsewhere [MIT L185-189].
- **[OWN]** Follow-up is a pairing strategy: pair rows (1,2), (3,4), (5,6) in every column; it is legal exactly when every column has an even number of empty squares after the follower's move. It gives the follower every upper square of a pair for free.

### 1.4 The nine rules [MIT L191-304]

All rules are stated "valid for the player that controls the Zugzwang, assumed to be Black in the following examples" [MIT L196-203]. Each rule has Required (precondition on squares) and Solutions (the opponent groups it neutralises, i.e. cannot be completed by the opponent). "Group" = a line of 4 squares; the position has 69 of them on 7x6 [MIT L313-317].

| Rule | Required | Solutions (groups neutralised) | Notes |
|---|---|---|---|
| **Claimeven** | Two squares directly above each other, both empty; the upper square is even. The text literally says "the upper square must be empty" [MIT L204-208] | All groups containing the upper square [MIT L209-210] | [UNCERTAIN] "empty" is almost surely a typo for "even": the summary line says "all empty even squares which are not directly playable" [MIT L204-206]. [OWN] Implemented by follow-up: the controller answers on the upper square. |
| **Baseinverse** | Two directly playable squares [MIT L212-215] | All groups containing both squares [MIT L216-217] | Rests on "a player cannot play two directly playable squares in one turn" [MIT L212-213]. [OWN] Whichever the opponent takes, the controller takes the other. |
| **Vertical** | Two squares directly above each other, both empty, the upper square is ODD [MIT L219-224] | All groups containing both squares [MIT L226] | Rests on "cannot play two men in one column in one turn; after one move in the column the square above is immediately playable" [MIT L219-222]. [OWN] This is the complementary alignment to claimeven (lower even, upper odd): the opponent can never obtain both squares. |
| **Aftereven** | A group the controller can complete using only squares from a set of claimevens [MIT L228-236] | All groups having at least one square in each aftereven column above the empty aftereven group in that column, plus all groups solved by the claimevens [MIT L235-238] | Side effect of claimeven: the controller will eventually fill his own group. This is the only OFFENSIVE rule in the list (it builds the controller's win rather than only refuting). Wording of Solutions is garbled ("column,s") [UNCERTAIN]. |
| **Lowinverse** | Two different columns, each with two squares directly above each other, all four empty, the upper squares odd [MIT L242-246] | All groups containing both upper squares, plus all groups solved by verticals [MIT L248-249] | Motivation line: "the sum of two odd numbers is even" [MIT L242]. Further rationale not given [UNCERTAIN]. |
| **Highinverse** | Two columns with 3 empty squares each, the upper square even [MIT L251-253] | Groups containing the two upper squares; groups containing the two middle squares; vertical groups containing the two highest squares of one of the columns [MIT L254-258]. If the lower square of the first column is directly playable: groups containing it and the upper square of the second; symmetric for the second column [MIT L260-266] | Same principle as lowinverse [MIT L251]. |
| **Baseclaim** | Three directly playable squares plus the square above the second one; that non-playable square is even [MIT L268-272] | Groups containing the first playable square and the square above the second; groups containing the second and third playable squares [MIT L273-275] | "Combination of two baseinverses and a claimeven" [MIT L268]. The notes print "Solutions:Solutions:" and "basinverses" typos. |
| **Before** | A group without opponent discs (the Before group); none of its empty squares in the top row [MIT L277-285] | All groups containing all successors (squares directly above) of the empty squares of the Before group; groups solved by the verticals and claimevens that are part of the Before [MIT L287-290] | "Combination of claimevens and verticals" [MIT L277]. [OWN] The top-row exclusion exists because those squares have no successor above them. |
| **Specialbefore** | A group without opponent discs; a directly playable square in another column; none of its empty squares in the top row; one empty square of the Before group playable [MIT L292-299] | Groups containing all successors of the empty squares plus the extra playable square; groups containing the two playable squares; groups solved by one of the claimevens or verticals [MIT L301-304] | A variant of Before. The text says "Before group" in the last requirement, presumably meaning the Specialbefore group [UNCERTAIN]. |

### 1.5 How the rules are combined (matching / graph step) [MIT L306-365]

1. VICTOR takes a position (either colour) and finds ALL instances of the nine rules; each instance is checked against all 69 groups; an instance that solves at least one group is kept, stored with (a) the list of groups it solves and (b) the other instances usable for the same problem [MIT L311-318].
2. Compatibility graph: solutions become nodes of an UNDIRECTED graph; two nodes are linked iff they CANNOT be used simultaneously; stored as an adjacency matrix [MIT L319-325]. A table of pairwise compatibility between rule types (Allis section 7.4, p.50) exists but is a figure, not recoverable [MIT L330-335].
3. Problems (the opponent's threat groups) are added as nodes; a solution is linked to a problem iff it solves it; problems are not linked to each other [MIT L325-328].
4. Goal: choose an INDEPENDENT subset C of the solution set S (no two conflicting) such that every problem in P is a neighbour of some member of C. The notes remark it is "potentially NP-complete" [MIT L337-341].
5. Allis' recursive algorithm FindChosenSet(P, S) [MIT L341-364]: if P is empty, success ("Eureka": C found); else pick the problem with the FEWEST neighbouring solutions ("MostDifficultNode"), branch over each neighbouring solution, remove the chosen solution's conflicts from S and the solved problem from P, recurse. (Fail-first ordering. The extracted pseudocode shows a loop variable "ChosenNeighbour" not bound in the for header; garbled but intent clear [UNCERTAIN].)

If such a C exists, the controller of zugzwang is guaranteed not to lose (every opponent group is refuted); combined with a won-group the position is won. This is "knowledge-based" evaluation, used as a leaf prover inside a search.

### 1.6 Search integration and results

- The actual solution combined these rules with transposition tables and conventional search; Allis used conspiracy-number search, a close relative of proof-number search [MIT L366-372]. Node values: -1 (Black can at least draw), 1 (White wins), 0 (undecided); conspiracy number (x, y): x for "becomes 1" is always 1, y for "becomes -1" counts unevaluated children, infinite if some child is already 1 [MIT L373-386]. Policy: expand nodes with LOW conspiracy numbers [MIT L388-398].
- Numbers: knowledge-based solution initially ~350 CPU hours; adding one knowledge rule and switching to pn-search brought it under 25 CPU hours; Allen's brute force alpha-beta needed ~300 CPU hours for 1.d1, Tromp's 8-ply database ~40,000 hours [ALLIS L7400-7409]. VICTOR plus Tromp's 8-ply database decides almost any position in minutes and also explains why a position is won [ALLIS L7413-7419].
- Most games are decided between moves 37 and 42, i.e. while the last column is being filled [ALLIS L7380-7381] (consistent with parity/zugzwang deciding the endgame).
- ALLIS says the same family of tools covers Connect-Four: "applying knowledge rules to determine the game-theoretic value of positions has proved to be successful" [ALLIS L8142-8143]; pn-search was applied to connect-four [ALLIS L2895-2905]; unpublished experiments found pn-search's node selection better than cn-search's [ALLIS L2934-2937].
- Value of the game (7x6): first player wins by playing the middle column; any other first move draws or loses [MIT L21-24; ALLIS L7390-7391 gives the value as a first-player win].

### 1.7 Which player benefits from odd vs even threats, and why

- White (first player) benefits from ODD-row threats, Black (second player) from EVEN-row threats, because on a board with an even number of rows the second player can play follow-up and thereby obtain every even square; White's only way to alter that is a threat on an odd square which Black cannot refute by claimeven [MIT L124-135, L141-153, L162-189].
- Black needs only an even threat to become the "controller" and win the zugzwang battle; White's odd threat in a different column beats Black's even threat (White's is the stronger), in the same column the lower one wins [MIT L141-145, L146-150].
- Opposite-parity threats (White even, Black odd) are "wrong-side" and lead to a draw; same-parity odd/odd cancel out [MIT L151-153].
- **[OWN]** The reason in one line: whoever owns the parity class of the threat's row gets to move into the square when all other columns are full; Section 3.1 derives this exactly and generalises it to a board where discs can be cleared.

---

## 2. Rapfi's VCF implementation

Rapfi is a Gomoku/Renju engine (`Rapfi/search/ab/search.cpp` is its alpha-beta searcher). VCF = "victory by continuous four": the attacker plays only moves that create a four (a one-move win threat), so the defender has exactly one non-losing reply; the tree collapses to a single-agent search. The same idea is used by Connect6 programs: "Alpha-Beta / MTD(f) along with VCF search (Victory by Continuous Four) to find a path to win in the endgame" [CPW6 L21].

### 2.0 Lineage: Allis' threat sequences (the theory behind VCF)

- Threat = attacker move after which the defender cannot win immediately and has at most one move that stops the attacker winning next move; "double threat" = zero such moves; threat sequence (a1 d1 ... an dn) = every ai a single threat and every di the unique reply; winning threat sequence = threat sequence + double threat [ALLIS L4686-4712].
- Because every defender reply is implied, attacker move + reply merge into one "meta-move" and the adversarial search becomes single-agent search [ALLIS L4743-4758]; the principle applies to games where "a restricted search concentrates on sequences of threatening moves only" [ALLIS L4437-4443].
- Allis lists three pitfalls that the forced replies can cause: a **defender four** (forced replies accidentally complete a group for the defender), **closed defender three** (ordering issue when merging), **open defender three** (the defender gets a counter-threat; attacker must answer it at once) [ALLIS L4839-4949]. These are exactly the "defender gets a threat for free from his forced reply" cases a gravity VCF must handle (Section 3.6).

### 2.1 Data structures

All in PB unless noted.

1. **Cell encoding and line keys.** 2 bits per cell: `00 wall, 01 white, 10 black, 11 empty`; placing colour c clears bit c [PB L432-451]. Four arrays of 64-bit line keys `bitKey0..3` (rows, columns, two diagonals); the window around a cell is extracted by a rotate: `rotr(bitKey0[y], 2*(x-2L))` etc. [PB L1690-1695]. Window half-length H = `HalfLineLen` = 4 (freestyle: five wins) or 5 (standard/renju: must see overlines) [PB L77-80]. Walls (board edge) are folded into the key.
2. **Dense key tables.** A window key (centre cell implicit/empty) maps to a dense index via two half-code lookups: sizes 121x121 = 14641 (freestyle) and 364x364 = 132496 (standard/renju) [PB L82-94, L251-274]. The dense index selects `Pattern2x`.
3. **`Pattern` (16 single-direction line classes)** `DEAD, OL, B1, F1, B2, F2, F2A, F2B, B3, B3S, F3, F3S, B4, B4S, F4, F5` ordered by threat strength; F5 = "this cell completes five" [PB L3547-3566]. Classified by a memoised recursion: put one more stone on each empty cell of the window and look at the resulting class (>=2 five-points = open four F4; exactly 1 = block four B4, or B4S if the remaining line still holds another four; and so on down to B1/F1) [PB L517-625, rule list L525-544]. All classifications are precomputed into lookup tables at start-up [PB L665-684, L407-420].
4. **`Pattern2x`**: 4 bits per colour = both colours' pattern of ONE direction at ONE cell in one byte; the board stores 4 of them per cell [PB L55-73, L1707-1710].
5. **`Pattern4` (14 aggregate classes per cell and colour)**: `NONE, FORBID, L_FLEX2, K_BLOCK3, J_FLEX2_2X, I_BLOCK3_PLUS, H_FLEX3, G_FLEX3_PLUS, F_FLEX3_2X, E_BLOCK4, D_BLOCK4_PLUS, C_BLOCK4_FLEX3, B_FLEX4, A_FIVE` [PB L3568-3586]. It answers: "what happens if this colour plays this empty cell?": A_FIVE = cell completes five, B_FLEX4 = creates an unstoppable four (open four or double four), C = block four + open three, D = block four + extra potential, E = plain block four, F..H = threes, etc. Computed by `getPattern4` from the four direction patterns (counts how many of each class) [PB L686-751].
6. **`PCodeP4` fused table**: the four per-direction patterns (order-independent) index `PCODE[16][16][16][16]` whose 16-bit entry holds a 12-bit pattern code (for the eval) and the 4-bit Pattern4 in the spare high bits, so one load gives both [PB L178-217, L396].
7. **Per-cell and per-ply state.** `pattern4x2[cell]` = packed (black p4, white p4) bytes [PB L1710, L1763]. `StateInfo` (one per ply) holds `p4Count[side][14]` (number of empty cells currently at each Pattern4), `lastPattern4Move[side][3]` (most recent empty cell that reached C_BLOCK4_FLEX3 / B_FLEX4 / A_FIVE), `lastFlex4AttackMove`, candidate-area box [PB L923-953]. Bitboards: `emptyBB`, `candidatesBB` (empty cells near stones, rewound by a journal in undo) [PB L895-921, L1794-1798, L1828-1833].
8. **Defence tables** `DEFENCE[denseKey][attacker]` = bitmask of cells that defend a line (used by the defence generators, not by the plain VCF path) [PB L219-235].

### 2.2 Threat detection per cell (incremental)

`Board::move` [PB L1665-1810] updates only what a stone can change: the cells within L steps of the stone along the 4 directions (walk i = -L..-1, +1..+L, the key is shifted 2 bits per step, double shift across the centre) [PB L1682-1774]. Per touched empty cell and direction: look up the new `Pattern2x`; if equal to the old one, skip (only refresh `lastPattern4Move` for tactical cells) [PB L1712-1721]; otherwise save the old pattern for undo (`restore.cells`, `touchedMask`), re-read the fused PCODE entry using the other three directions as the row, adjust `p4Count` (old --, new ++), store the new p4 pair, and call `setLastPattern4` [PB L1723-1766]. Then the placed cell's own p4 counts are removed and the side flips [PB L1777-1788]. `undo` restores exactly the recorded cells [PB L1860-1870].

Consequence: `board.p4Count(side, A_FIVE)` etc. are O(1) reads; "which cell?" is an O(1) read of `lastPattern4(side, p4)` with a fallback scan if stale [PB L2579-2585].

### 2.3 Static win check `quickWinCheck` [PB L2214-2299]

Called at every search and VCF node before any move loop [AB L1262-1269, L2079-2086]. Returns a mate score without search when:
- self has an A_FIVE cell: `mate_in(ply+1)`;
- opponent has exactly one A_FIVE: no verdict (must defend); two or more: `mated_in(ply+2)`;
- self has a B_FLEX4 cell: `mate_in(ply+3)`;
- self has a C_BLOCK4_FLEX3 cell and the opponent has no block-four reply of his own: `mate_in(ply+5)`; otherwise each such candidate is played on the board and the forced reply is tested statically;
- self has F_FLEX3_2X (double open three) and the opponent has no B_FLEX4 / block-four counterplay: `mate_in(ply+5)`.
A deliberately absent rule (opponent has several flex fours) is documented as unsafe because counts cannot tell shared lines from separate ones [PB L2247-2250].

### 2.4 Move generation for forcing moves

- `GenType` flags: `WINNING` (A_FIVE/B_FLEX4), `VCF` (four-making moves), `VCT` (three-making), `VC2`, `TRIVIAL`, `COMB` (require a combination, not a single line), `DEFEND_FIVE/FOUR/B4F3` [PB L2344-2366].
- **VCF filter** `basicPatternFilter`: plain VCF accepts any empty cell whose p4 for the side to move is >= `E_BLOCK4` (i.e. playing it creates at least a four); `VCF|COMB` accepts only p4 >= `D_BLOCK4_PLUS` (a four plus extra potential); Renju adds forbidden-point exceptions [PB L2491-2509].
- **Pre-check**: if no cell of the side has E/D/C block-four patterns (read from `p4Count`) the generator returns immediately [PB L2539-2556].
- **QVCF candidate region**: `generateNeighbors<VCF[|COMB]>` scans only the offsets `RANGE_SQUARE2_LINE4` around the side's LAST ACTUAL MOVE: the 5x5 square plus the 8 rays up to distance 4 [PB L4064-4077, L4112-4114; AB L3795-3806]. [OWN] That is 24 + 16 = 40 cells. Rationale (stated in the doc-comment): "only searches continuous VCF moves to avoid search explosion" [AB L2034-2035]; a continuing four normally touches the previous one.
- **Gate `allowPlainB4InVCF`**: plain block fours are allowed only while `depth >= DEPTH_QVCF_FULL (-1)` or when BOTH of the side's previous two moves were already >= D_BLOCK4_PLUS; otherwise only COMB moves [AB L3522-3525; consts PB L3510-3512; inputs `(ss-2)->moveP4[self], (ss-4)->moveP4[self]` at AB L2180-2182]. Since `depth` drops by 1 per attacker move starting at 0, the first two attacker moves may be plain fours.
- Order: `scoreAllMoves<CLASSICAL_ONLY>` then `fastPartialSort` (insertion sort for small lists) [AB L3808-3809, L3361-3389]; TT move tried first if it still has p4 >= E_BLOCK4 for self [AB L3546-3549].
- **Defender's reply**: the unique cell `stateInfo().lastPattern4(oppo, A_FIVE)` [AB L2336]; the same cell serves the attacker when HE must block a five (stage `DEFENDFIVE`) [AB L3542-3545, L3754-3765; PB L3166-3189].

### 2.5 The attacker/defender recursion

`vcfsearch` (attacker node, side to move may also be forced to block) [AB L2034-2296]:
1. count the node; mark selDepth; poll the clock on the main thread [AB L2054-2069];
2. draw if board full / max moves; static eval at `ply >= MAX_PLY` [AB L2071-2077];
3. `quickWinCheck` -> return mate score [AB L2079-2086]; mate-distance pruning [AB L2088-2092];
4. TT probe; early cutoff if `ttDepth >= depth` (and not a PV node on the main thread); bounds narrow alpha/beta [AB L2094-2113];
5. static evaluation (TT eval if present, else `Evaluation::evaluate`, or minus the parent's eval after a null move); **stand pat**: if `eval >= beta` return it (store LOWER with DEPTH_NONE); in PV nodes raise alpha to the static eval, "since we can stop anywhere in the move limited search" [AB L2115-2151];
6. **delta pruning** at non-PV nodes: if `eval + qvcfDeltaMargin(depth) < alpha`, return alpha (store UPPER) [AB L2153-2167];
7. loop over the QVCF MovePicker: record `moveP4` of the move for both colours, `board.move`, `value = -vcfdefend(..., depth - 1)`, `board.undo`; abort with VALUE_NONE if the engine is terminating; standard best/alpha/beta update, PV update [AB L2177-2262];
8. store in TT with `depth = max(depth, DEPTH_QVCF)`, bound LOWER/EXACT/UPPER [AB L2264-2292].

`vcfdefend` (defender node) [AB L2298-2367]:
1. count node; **if the opponent has NO A_FIVE, return the static evaluation immediately**, i.e. the chain ends as soon as an attacker move did not create a five-threat [AB L2322-2325]; (otherwise draw/max-ply checks);
2. exactly one move: `lastPattern4(oppo, A_FIVE)`; Renju special-case if that cell is a forbidden point for black (black loses in two) [AB L2335-2342];
3. prefetch the TT line of the child (`zobristKeyAfter(move)`), make the move, `value = -vcfsearch(..., depth)` with **no depth reduction for the defence move**, undo, update PV [AB L2344-2363].
Node return type is a single score (mate scores for wins), not a boolean: the VCF doubles as a quiescence search.

### 2.6 Depth limits and bounding (summary)

| Mechanism | Value / rule | Source |
|---|---|---|
| Explicit VCF depth cap | none; only `ply < MAX_PLY = 256`, board full, `maxMoves` | AB L2071-2077, L2635 |
| `depth` argument | starts at 0 (called with depth <= 0), `-1` per attacker move, unchanged for defender | AB L2037, L2219, L2356 |
| Plain-four gate | `depth >= -1` or two previous own moves >= D_BLOCK4_PLUS | AB L3522-3525 |
| Candidate region | 40 cells around own last move | PB L4114 |
| Delta margin | `max(bias + scale*depth, 768)`; bias {2084, 1817, 2154}, scale {62, 66, 53} for {freestyle, standard, renju} | AB L2805-2812 |
| Stand-pat | return static eval if >= beta; raise alpha in PV | AB L2134-2151 |
| TT depth stored | `max(depth, DEPTH_QVCF = -2)`; stand-pat entries use DEPTH_NONE (-3) | AB L2291, L2144; PB L3510-3512 |

### 2.7 Transposition table use

- Single shared TT with the main search (`TT.probe / TT.store`, key = Zobrist of the position) [AB L2094-2102, L2285-2292]. Entry: value16, eval16, pv/bound/best-move word, depth8 (stored relative to `DEPTH_LOWER_BOUND = -20`, which is what lets negative VCF depths be stored), generation8, 32-bit key check XOR-ed with the data [AB L4254-4307; PB L3514-3517].
- Replacement: pick the bucket entry with the lowest `depth - relativeAge`; an existing entry for the same position is not overwritten by a non-EXACT bound with `depth + 2 < old depth` [AB L4263-4287]. So a deep main-search entry survives shallow VCF probes.
- Probe rule at VCF nodes: accept if `ttDepth >= depth` where depth <= 0: any stored entry (including a main-search one) is usable; a miss defaults to `DEPTH_LOWER_BOUND` [AB L2101-2113].
- Mate scores are converted ply-relative on store/probe (`searchValueToStoredValue`, `storedValueToSearchValue`) [AB L4240, L4299].

### 2.8 How alpha-beta calls the VCF

- **Depth-zero dive**: at the top of `search()`, `if (depth <= 0.0f) return oppo5 ? vcfdefend(...) : vcfsearch(...)` ("~17 elo") [AB L1229-1237].
- **Razoring with VCF**: at non-PV nodes, if `eval + razorMargin(depth) < alpha` (and no short mate is being searched for), return `vcfsearch<NonPV>(alpha, alpha+1)` instead of the plain razor ("~68 elo") [AB L1344-1349; margin function L2712-2720, only for depth < 2.6].
- **After reductions**: IIR/TT-based reductions may push `depth <= 0`; then `vcfsearch` is called with the node type [AB L1390-1402].
- **Forced-reply plumbing**: if the opponent has a five-threat, early pruning is skipped and the move loop sees only the defence move; opponent four (`oppo4`) disables null-move pruning [AB L1331-1338, L1359]. Futility pruning is suppressed if the side made block-fours with its previous two moves [AB L1355].
- **Mate bookkeeping**: `quickWinCheck`, mate-distance pruning, root `numIterationAfterMate` [AB L1262-1274, L474-483].

### 2.9 Performance notes

- Stated in source comments: dive at depth 0 about +17 Elo; VCF-razoring about +68 Elo [AB L1233, L1344]. No nodes-per-second figures are in these files (not measured here).
- Design choices that make it cheap [OWN reading of the code]: O(1) detection via `p4Count`; O(1) forced reply via `lastPattern4`; branching of 1 at defender nodes and typically a handful at attacker nodes; per-move cost proportional to 8L cells with early-out when a pattern is unchanged; fused PCODE tables avoid dependent loads [PB L1732-1747]; TT prefetch before making the forced reply [AB L2351]; fixed-size arrays, no allocation in the node functions.
- The VCF is a QUIESCENCE tool (heavily pruned, returns scores, stand-pat) and not a complete prover: the neighbourhood restriction, the plain-four gate and the delta margin each discard some real VCF wins. [OWN] For our game the opposite trade-off is feasible: the board has only 12 playable cells, so a complete forcing-move search is cheap.

---

## 3. Adaptation to Quantum Connect [OWN throughout; every formula below is my derivation, not in any source]

Notation. Rows 1..8 from the bottom (row0 = row-1 in code), columns 0..11. `H_c` = discs in column c, `D` = discs on board, `E = 96 - D` empties, `S` = side to move. A threat of X = empty cell completing a 6-window (a line of 6 cells in a row, column or diagonal) for X; every cell of the 6-window is X's except that one cell. There are 134 windows: 8 rows x 7 + 12 columns x 3 + 2 diagonals x 7 x 3 = 56 + 36 + 42. "Longer counts" needs no extra rule: any longer line contains a 6-window.

### 3.1 Parity for height 8 and six-in-a-row

**Which rows are odd.** Rows 1, 3, 5, 7 are odd; 2, 4, 6, 8 are even. Height 8 is even exactly like height 6 in the 7x6 theory, so the qualitative theory of Section 1 carries over unchanged: with no special moves, the first player gets odd rows and the second player even rows (follow-up pairs rows (1,2), (3,4), (5,6), (7,8) in every column). Parity depends on the column height and on who moves first, NOT on line length (4 vs 6) and NOT on the number of columns (7 vs 12).

**Exact ownership rule (no special moves left).** Let T be an empty cell at row r with u = 8 - r empty cells above it in its column and d empty cells below it (d = r - 1 - H_c). Model of the waiting game: neither side wants to play below T; all other columns fill first. Then N_out = E - (d + 1 + u) moves are made elsewhere; T is the (N_out + d + 1)-th move counted from S, and S makes the odd-numbered moves, so
 S gets T  iff  N_out + d + 1 is odd  iff  (E + r) is odd  iff  (D + r) is odd.
(u = 8 - r is fixed by the board top, so only E matters.) Therefore:
- D even: the side to move owns the odd rows; D odd: the side to move owns the even rows.
- Equivalent: `oddOwner = (D even) ? sideToMove : otherSide`. In an ordinary game the first player is `oddOwner` all game long (each normal move changes D by 1 and flips the side).
- Check against the Allis example: Black threats b2, f2; White to move; D even so White owns odd rows; row 2 is even, so Black gets b2/f2 [MIT L166-189, L107-113].
- Cross-check by columns: let O = number of columns with an ODD number of empty cells. O even: side to move owns the odd rows; O odd: side to move owns even rows. (E and D have the parity of O.) A column with odd e_c has its lowest empty cell on an even row: an unpaired "free waiting move".

**Natural vs unnatural threats (generalised MIT table).** Call a threat of X at row r NATURAL if X owns the parity class of r (`oddOwner == X` for odd r, the other side for even r). Replace White by `oddOwner`, Black by the other side in the table of [MIT L141-153]:

| oddOwner threat | other side's threat | Expected result |
|---|---|---|
| odd (natural) | even (natural) | oddOwner wins; same column: the lower threat wins |
| even (unnatural) | even (natural) | other side wins |
| even (unnatural) | odd (unnatural) | draw-ish: neither realised |
| odd (natural) | odd (unnatural) | usually unresolved, decided by other threats (MIT: "neither works") |

This is the MIT table relabelled; the underlying claims are MIT's. A natural threat with nothing else to refute it wins the waiting game; an unnatural threat is realised only if ownership changes (Section 3.3) or by search.

**Six-in-a-row geometry that matters for rows.**
- A horizontal window lies in ONE row, so a horizontal threat cell has the same row as the window (classic parity threat). With 12 columns each row has 7 windows. Rows 7-8 need 6 discs in one top row: very late, treat as negligible except as column-top fillers.
- A vertical window needs 6 consecutive discs in a column of 8: rows 1-6, 2-7, 3-8. A vertical 5-in-a-column is ALWAYS an immediate threat (the cell above is playable), so verticals are pure "fours" with no parity content.
- A diagonal window spans 6 consecutive rows, start row 1, 2 or 3; its single empty cell can be at any of the six rows, so diagonal threats come in both parities; they need support cells below them on other columns, which is why diagonal threats are the usual source of "needs support" threats.

### 3.2 Threat classes: playable-now vs needing support

Define for a threat cell T of X: `d(T)` = number of empty cells below it in its column (0 = playable now).

| Class | Condition | Meaning | Rapfi analogue |
|---|---|---|---|
| I immediate | d = 0 | X wins if it is X's turn; otherwise the opponent must occupy T (forced reply) | `A_FIVE` cell [PB L3584] |
| I2 double immediate | two distinct d = 0 cells, or a stacked pair with d = 0 (3.3) | cannot be parried by a normal move | `B_FLEX4` [PB L3583] |
| II one-step | d = 1 | the cell b below T is the key: if the opponent plays b, X wins; if X plays b, the opponent MUST play T. So X's move b is a FORCING move (tempo-neutral exchange that spends the threat) | block-four move `E_BLOCK4` |
| III support, natural | d >= 2, X owns the row parity | wins the waiting game when nothing else interferes | (no analogue: no gravity in Gomoku) |
| IV support, unnatural | d >= 2, opponent owns the parity | realised only if parity flips; still poisons the cell below T for the opponent (he cannot play it), i.e. removes one of his waiting moves | |
| V stacked | two threats of X at vertically adjacent cells | see 3.3 | `C_BLOCK4_FLEX3`-like combination |

Rules of thumb inherited from MIT: threats above a cell that is a threat of BOTH players are useless; in one column the lower threat is decisive [MIT L107-113, L141-145]. Bit-level: `shared = thr[0] & thr[1]` and every cell above a shared cell in its column is useless for both sides.

Forcing moves in gravity (needed for VCF, Section 3.6): a move p of X is forcing iff after it X has at least one playable winning cell. This happens in exactly two ways: (i) p lies on a 6-window that now has 5 X discs and the missing cell is playable (p itself placed on the line; the missing cell is either `above(p)` or the lowest cell of another column); (ii) `above(p)` is a threat of X (p merely unlocks it: class II). Unlike Gomoku, the forced reply cell may not be adjacent to p, and the attacker's own move can unlock a cell for the DEFENDER (`above(p)` may be the defender's winning cell): always test the defender's immediate wins after the attacker's move.

### 3.3 The stacked-threat win (two vertically adjacent threat cells)

Let X own T1 = (c, r) and T2 = (c, r+1), d = d(T1) empties below T1.
- **d = 0**: wins whoever is to move. X to move: play T1. Defender to move: he must occupy T1, which makes T2 playable and X wins. (Check first that the defender has no immediate win and that his T1 stone does not give him a win.)
- **d = 1** (cell b below T1 playable): X to move plays b; the defender must take T1 (forced), X takes T2. Forced win in 3 plies. Defender to move: he cannot play b; X still wins on its turn unless the defender has a faster forcing sequence.
- **d >= 2**: any defender move in column c is harmful to him (it speeds up X), so the column is X's private reservoir: X needs d own moves in it (the d-th is b), the defender gets d - 1 free moves elsewhere. Win unless the defender can build, in d - 1 free moves, a forcing win or a threat that X must answer more than tempo-neutrally. Forcing moves are tempo-neutral, so only genuinely free moves matter. This also breaks the zugzwang battle: X owns extra waiting moves in that column, the defender owns none, so the defender runs out of safe moves first regardless of row parity.
- The special move is the defender's only structural escape (3.4).
- Mixed stack: X at T1 and Y at T1+1: when X plays b, Y takes T1, X must then block T2; a tempo-neutral exchange, no win. Shared T1: cells above are useless (3.2).
Bit test (byte-per-column layout, row0 = bit 0 of each byte): `stack = thr[X] & (thr[X] >>> 1) & 0x7F7F7F7F7F7F7F7FL` gives the lower cell of every stacked pair (per 64-bit word).

### 3.4 What the special move does to threats and parity

Let the special move be played in column c with H_c = h before the drop. The disc lands on row h+1 (1-based); the block is columns c-1..c+1, rows h..h+2 (clipped at rows 1 and 8 and at the side columns). Assumption [UNCERTAIN, see Section 4]: the placed disc itself is inside the cleared block and therefore vanishes.

- **Own column c**: loses its top disc (row h) and the placed disc; row h+2 is empty; nothing is above. New height = h - 1 (h >= 1) or 0 (h = 0).
- **Neighbour column c' (height H')**: loses the k occupied cells inside rows h..h+2, k in 0..3 (k <= 2 if h = 0 because row 0 does not exist). If H' >= h+3 then k = 3 (k = 2 if h = 0) and EVERY disc above the block falls by exactly k = 3 rows (odd shift) while their colours stay; else the column just loses k top discs.
- **Cleared count** R = 1 (placed) + [h >= 1] + k_left + k_right. Net disc change = 1 - R. In the typical middle case (h >= 1, both neighbours tall) R = 1+1+3+3 = 8 (net -7); at an edge column R = 1+1+3 = 5.
- **Global parity ownership flips iff R is odd.** Proof: a normal move changes D by +1 and keeps `oddOwner`; a special move changes D by 1 - R and also flips the side, so it keeps `oddOwner` iff 1 - R has the parity of +1, i.e. iff R is even. So edge-column blasts (R = 5) flip, typical mid-board blasts (R = 8) do not. R is computable from the 12 column heights alone, in O(1) per column.
- **Per-column parity.** In a neighbour column with k = 3 the number of empties below any empty cell rises by 3, so d flips parity there. This is the "threat above the blast flips parity" effect, but it does NOT add a second flip: the waiting-game formula depends only on E (the global count) and the fixed u = 8 - r, so the per-column flips are already accounted for inside the single global rule above.
- **Geometry.** Empty threat cells keep their grid position; discs move. Lines built from discs above the block in the neighbour columns drop by 3 rows (row parity of those discs flips) while discs in other columns stay, so a horizontal or diagonal structure spanning c-1..c+1 above the block is torn apart; a vertical stack above the block falls intact; the discs below and above the block in a neighbour column become adjacent, creating NEW alignments (possibly 6-lines of either colour). Any 6-line present after the move wins for the mover, so a blast can never lose by creating a line, but it must be scanned for wins for BOTH colours.
- **Exposure test (when can a structure be hit by one blast?).** A disc at (c', r) is hit by a blast in an adjacent column n iff `H_n <= r <= H_n + 2`; by a blast in c' itself iff it is the top disc (r = H_c'). So a cell is safe against any single blast, now and later (provided no other blast lowers the neighbours), once both neighbours satisfy `H_n > r` and `r < H_c'`; structures buried below all neighbours' tops are immune. Low neighbour columns ("valleys") are the danger.
- **Support removal.** A blast beside a column lowers its stack by k, turning a playable (d = 0) threat into d = k; this is the cheapest defence against an immediate threat when the special move is available.

Consequences. (1) Parity/zugzwang plans are reliable only while the opponent's special move is on cooldown for the whole remaining fill-up, or when every disc and support of the plan is unexposed. (2) Whoever has the special move available can both destroy local structure and, in edge columns, flip `oddOwner`. (3) Because a blast can lower a column by up to 3 and so make a hitherto "playable" cell unplayable, all VCF defender nodes must consider special replies while the defender's special is available (3.6).

### 3.5 Cooldown bookkeeping

Per side keep `turnsSinceSpecial`; availability after k further own turns is `turnsSinceSpecial + k >= 10` (exact off-by-one: Section 4). A VCF line of m attacker moves gives the defender m replies, so a defender special is possible in the line iff it becomes available within m of his turns; this is the exact switch between "sound" and "unsound" VCF (3.6).

### 3.6 Concrete gravity-VCF design in Java (stdlib only, budget 10-20 ms)

**Design choice [OWN].** Rapfi stores a Pattern4 per cell and updates cells within L of each stone [PB L1682-1774]. On a 96-cell gravity board the equivalent that is both simpler and faster is a per-WINDOW counter table (134 windows) from which the set of threat cells is derived in O(1); the threat cells, occupancy and playable cells are kept as 96-bit masks in two longs. A window-shift detector (`b & (b>>s) & ...`) is only needed for rebuilds and tests.

**Layout.** `cell = column*8 + row0` (row0 = 0 bottom). Word 0 = columns 0-7 (cells 0-63), word 1 = columns 8-11 (cells 64-95, 32 bits). Each column is one byte, so a column's discs are the low `H_c` bits of its byte. Per colour two longs: `bb[colour*2 + (cell>>6)]`.

```java
static final long ROW0 = 0x0101010101010101L, NOT_ROW0 = 0xFEFEFEFEFEFEFEFEL,
                  NOT_TOP = 0x7F7F7F7F7F7F7F7FL;
long playable(int word) {                       // lowest empty cell of each non-full column
  long occ = bb[word] | bb[2 + word];
  long p = (((occ << 1) & NOT_ROW0) | ROW0) & ~occ;     // empty col -> bit0; height h -> bit h; full -> 0
  return word == 0 ? p : p & 0xFFFFFFFFL;
}
```
Static tables built once: `WIN[134][6]` cells, `WIN_SUM[w]`, `CELL_WINS[96][]` (window ids through a cell, 4 directions: horizontal, vertical, two diagonals; windows lie fully inside the board).

**State (no allocation in the search).**
```java
byte[][] cnt = new byte[2][134];  // own discs in window, per colour
int[][]  sum = new int[2][134];   // sum of cell indices of own discs in window
byte[][] thr = new byte[2][96];   // #windows (5 own, 0 enemy) whose single missing cell is this cell
long[]   thrMask = new long[4];   // bit set iff thr>0 (same layout as bb)
int[] h = new int[12]; byte[] grid = new byte[96]; long key; int discs;
```
Invariant: window w is a threat window of X iff `cnt[X][w]==5 && cnt[1-X][w]==0`; its missing (empty) cell is `WIN_SUM[w] - sum[X][w]` (no scan needed).

**Normal move, O(windows through the cell) (about 12 on average, at most 21):**
```java
boolean place(int me, int column) {            // returns true if the move completes a 6-window
  int cell = column*8 + h[column]++, o = me ^ 1; boolean win = false;
  bb[me*2 + (cell>>6)] |= 1L << (cell & 63);  grid[cell] = (byte)(me+1);
  for (int w : CELL_WINS[cell]) {
    int m = cnt[me][w], q = cnt[o][w];
    if (q == 5 && m == 0) dec(o, cell);                      // enemy threat window blocked by my disc
    if (q == 0) {
      if (m == 4) inc(me, WIN_SUM[w] - (sum[me][w] + cell)); // new 5-of-6: its empty cell becomes my threat
      else if (m == 5) { dec(me, cell); win = true; }        // I fill my own threat: 6 in a row
    }
    cnt[me][w] = (byte)(m + 1); sum[me][w] += cell;
  }
  discs++; key ^= ZOB[me][cell]; return win;
}
```
`unplace` is the exact inverse (decrement counts, then undo the same `inc`/`dec` pairs). `inc/dec` update `thr[X][cell]` and flip the bit in `thrMask` on 0<->1 transitions. Complexity is what Rapfi's `Board::move` achieves with its "unchanged pattern -> skip" early-out [PB L1712-1721].

**Special move.** Rare, so do the simple and safe thing: copy `grid`+`h` to a snapshot slot (108 bytes), apply the placement, clear the block, compact the three columns, then `rebuild()` all counters from `grid` (134 windows x 6 cells, about 1-2 microseconds); scan for any window with `cnt[c][w]==6` for EITHER colour, then the mover wins. Undo = restore snapshot + `rebuild()`. Precompute `R`, `deltaD` and `flipsOwnership = (R&1)==1` per column from the heights for the evaluation (3.4).

**O(1) queries (Rapfi analogues in brackets).**
- Immediate wins of X: `(thrMask[2X] & playable(0)) | (thrMask[2X+1] & playable(1))`; count with `Long.bitCount`; the forced reply cell = `Long.numberOfTrailingZeros` [`p4Count(A_FIVE)`, `lastPattern4` PB L2579-2585].
- Static quick win (`quickWinCheck` analogue, PB L2214-2299): X has an immediate win -> win in 1; opponent has >= 2 distinct immediate winning cells -> loss (unless a special move can rescue); X has a stacked pair with d <= 1 -> forced win (3.3).

**Forcing-move generation at an attacker node** (at most 12 columns; replaces Rapfi's `generate<VCF>`/`generateNeighbors` PB L3096-3138):
```java
long cand = 0;
for each non-full column c with playable cell p:
   if (thrBit(A, above(p))) cand |= bit(c);                       // class II: p unlocks my threat
   else for (int w : CELL_WINS[p])                                // p would make 5 of 6
       if (cnt[A][w]==4 && cnt[D][w]==0) {
           int q = WIN_SUM[w] - sum[A][w] - p;                    // the other empty cell
           if (q == p+1 || isPlayableNow(q)) { cand |= bit(c); break; }
       }
```
This is a necessary condition; the survivors are made and tested (`A` has >= 1 playable winning cell after the move). No neighbourhood restriction like Rapfi's 40-cell box is needed: 12 columns is already tiny.

**Search skeleton (attacker node `A`, boolean prover, depth in attacker moves):**
```
int attack(int depth):                                  // returns WIN or UNKNOWN
  if timeUp or nodes>cap -> abort
  if immediateWins(A)>0 -> WIN
  if specialAvail(A,0) and someBlastMakesAnyLine() -> WIN              // either colour's line wins
  n = immediateWins(D)
  if n >= 2 -> (only a special move can save A) try blasts else UNKNOWN
  if n == 1 -> A must block cell q (or use its special): place(A, col(q));
               k = immediateWins(A); r = (k>0 && immediateWins(D)==0) ? defend(depth, k) : UNKNOWN; undo
               return r                                    // a block that creates no threat ends the VCF
  if depth == 0 -> UNKNOWN;  TT probe (key, depth, cooldown bits)
  for column in orderedCandidates():                     // ordering: 2 new wins > stacked > most windows
     place(A,column); k = immediateWins(A)
     if k>0 && immediateWins(D)==0:                      // my disc may unlock D's winning cell: test it
         if k>=2 and not defenderSpecialPossible -> undo; return WIN
         r = defend(depth-1, k)
     undo; if r==WIN -> TT store; return WIN
  TT store UNKNOWN(depth); return UNKNOWN

int defend(int depth, int k):                           // D to move; AND over D's options
  options = { block the single winning cell }  U  { every special move, if D's special is available }
  for each option: apply; if D now has any 6-line (own or A's) -> option refutes (return UNKNOWN);
      else r = attack(depth); undo; if r != WIN return UNKNOWN
  return WIN
```
Notes: (1) the defender's block may complete his own window or create an immediate threat for him; both are caught because `attack()` first tests `immediateWins(D)` and `place` returns the win flag (Allis' "defender four / open defender three" pitfalls, ALLIS L4839-4949). (2) The forced reply is a single cell, so, as in Rapfi, the defender node has branching 1 when no special is available [AB L2335-2363]. (3) Rapfi returns scores and stands pat [AB L2134-2151]; a boolean prover is cleaner for us because the engine only needs "forced win found / not found", and wins can be converted to mate scores `MATE - plies`.

**Special-move policy.**
| Situation | Treatment |
|---|---|
| defender special unavailable for the whole line (`turnsSinceSpecial + m < 10` for m defender replies) | exact: no special options at defender nodes; VCF is sound |
| defender special available | enumerate all <= 12 blasts at each defender node (each costs ~2 microseconds); prune by the exposure test of 3.4 only if you accept incompleteness |
| attacker special available | at an attacker node test the <= 12 blasts for an immediate 6-line (win); optionally treat a blast that leaves an immediate threat as a forcing move |
| leaf/razor calls inside alpha-beta | mode H: ignore defender specials, report "tentative win" with a large but non-mate score |
Availability is a function of ply inside the line, so it must be part of the TT key (include `min(cooldownA,10)`, `min(cooldownD,10)` or the per-ply flags).

**Transposition table.** Own small table, `long[] keys` + `int[] data`, 2^18 entries (about 3 MB), always-replace or depth-preferred; store (result, remaining depth). A proven WIN is valid for any depth >= the stored one; an UNKNOWN with remaining depth d is reusable for any depth <= d. Rapfi shares its main TT and stores VCF nodes at `max(depth, -2)` [AB L2285-2292, L4254-4307]; sharing is optional here.

**Depth, iteration and budget [estimates unmeasured; benchmark before trusting].**
| Call site | Attacker depth cap | Node cap | Time |
|---|---|---|---|
| root, own VCF (iterative deepening 1,2,3,...) | 12 | ~20k | <= 10 ms |
| root, opponent VCF if we pass (threat detection; then restrict our moves to those that stop it) | 8 | ~8k | <= 5 ms |
| leaf dive at alpha-beta depth 0 (Rapfi: [AB L1233-1237]) | 3 | 100-300 | none |
| razoring replacement (Rapfi: [AB L1344-1349]) | 3 | ~300 | none |
Throughput guess: place+unplace about 0.2-0.4 microseconds after JIT warm-up; an attacker node with <= 12 candidate tests and a TT probe about 1-3 microseconds; so 10 ms is roughly 3k-10k attacker nodes, enough because candidate sets are 0-3 wide (a forcing move needs a 5-of-6 or an unlockable threat). Check `System.nanoTime()` every 1024 nodes; abort returns UNKNOWN.

**Static evaluation hook (cheap, from the same masks).** For X in {0,1}: count immediate cells (`thrMask & playable`); class-II cells (`thrMask & ((playable << 1) & NOT_ROW0)`, i.e. threat cells sitting directly above a playable cell); natural vs unnatural threats using `oddOwner` (3.1) and `thr` row parity, stacked pairs (3.3 bit test), useless cells above `thr[0]&thr[1]`; discount parity-based terms when the opponent's special is available or the threat's discs are exposed (3.4); add a small bonus when the side to move has a blast with odd R that flips ownership in its favour. Weights: tune by self-play (not in sources).

**Validation plan [OWN].** (a) Randomised test: after random play, `place/unplace` sequences must leave `cnt/sum/thr/thrMask` equal to a fresh `rebuild()`. (b) Exhaustive minimax on a reduced board (e.g. 6x4 with line length 4) to confirm the ownership rule of 3.1, the stacked-threat results of 3.3 and VCF soundness. (c) Compare boolean VCF against full alpha-beta with specials disabled on random midgame positions: every VCF win must be a real win.

---

## 4. Open questions

Rules of the game (need an authoritative answer; each changes formulas above):
1. **Does the placed disc vanish in the blast?** "3x3 block centred on it cleared" implies yes (assumed in 3.4). If the disc survives, R loses its leading 1, the ownership flip condition (R odd) inverts, and the own column keeps one more disc.
2. **Cooldown semantics.** "At most once every 10 own turns": is the earliest reuse the 10th or the 11th own turn after the last use? Can it be used on own turn 1? Does the special move count as one of the 10 turns? This sets the exact soundness window of VCF (3.5).
3. **Blast legality and clipping.** Can a special move be played in a full column (no drop possible)? Is the block clipped at the floor and side walls exactly as assumed (rows 0 and 9 do not exist, so a disc dropped on an empty column clears only rows 1-2 of its neighbours)? Are all columns compacted simultaneously, and may the compaction itself create the winning line?
4. **Line win after a special move.** Confirm: if after a blast BOTH colours have a 6-line, the mover wins; and a blast that completes only the opponent's line also wins for the mover (the statement says so; counter-intuitive, so worth a unit test).
5. **Who moves first** and does the first player get any handicap? Needed to fix `oddOwner` at D = 0 (assumed: side to move owns odd rows when D is even).

Theory gaps:
6. The ALLIS file does not contain the nine rules (grep: no "claimeven" / "zugzwang"); the MIT text has typos ("upper square must be empty" for claimeven) and misses the figures and the rule-compatibility table of Allis section 7.4. Exact formal definitions, proofs and the conflict relation need Allis' 1988 thesis or Uiterwijk, van den Herik, Allis 1989 (listed in [CPW4 L35-36] but not in the corpus).
7. The odd/even outcome table (3.1) is MIT's relabelled, valid for the 7x6 case; I have not verified that it carries unchanged to 12 columns and 6-in-a-row. The waiting-game ownership rule (3.1) is my derivation and assumes both sides avoid the column; it ignores forcing moves and other threats. Needs exhaustive-search confirmation on reduced boards.
8. The stacked-threat analysis for d >= 2 (3.3) is a tempo-counting argument; the claim that the defender cannot profit from d - 1 free moves is not proven (depends on his counterplay).
9. How much of the static rule machinery (claimeven, baseinverse, vertical, before) is worth implementing, given that any blast breaks the follow-up pairing in the three affected columns? Possible niche: certify a draw/loss-avoidance only when both specials are on cooldown for the rest of the game. Needs a count of remaining plies versus cooldown.
10. Formal treatment of "unnatural threat poisons the cell below it" (it removes opponent waiting moves; Allis' before/specialbefore rules seem to cover this in 4-in-a-row).

Engineering:
11. All throughput and node-budget numbers in 3.6 are guesses (no measurement; Rapfi files give no nps either). Needed: a Java micro-benchmark of `place/unplace`, attacker-node cost and typical branching of forcing moves with six-in-a-row (probably narrower than Gomoku fours because a 5-of-6 plus a playable completion cell is rare).
12. Defender special replies at every defender node cost up to 12 rebuilds; is a safe filter (exposure test of 3.4 plus "can this blast create a line anywhere") good enough, or should leaf calls simply run in mode H and rely on the root's exact check?
13. TT: separate VCF table vs shared with the main search as in Rapfi [AB L2285-2292]; the key must include cooldown state.
14. Rapfi constants (delta margins 2084/62/768, razor margins, plain-four gate depth -1) are tuned for Gomoku evaluation scales and have no direct counterpart here; they would have to be re-tuned if a stand-pat quiescence variant is built instead of the boolean prover.
15. Evaluation weights for natural/unnatural/stacked threats and the blast-exposure/flip features must be tuned (self-play); nothing in the sources gives values.

