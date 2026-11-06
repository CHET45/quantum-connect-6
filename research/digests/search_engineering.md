# Search engineering for the Quantum Connect bot: bitboards, alpha-beta, threat search, time control, testing

Digest for the "Quantum Connect" engine: gravity drop, 8 rows x 12 columns (size is a constructor parameter), SIX or more in a row in any direction wins, a special move (place the disc, clear the 3x3 block around the landing cell for both colours, re-apply gravity) at most once every 10 own turns, the mover wins if any 6-line of EITHER colour is on the board after the move, draw on a full top row or after 1000 turns, 100 ms per call (`accelerateQuark` = column, then `useSpecialMove` = boolean with its own 100 ms), timeout = loss, Java standard library only. Planned engine: alpha-beta, threat search and a learned pattern-table evaluation.

Citations use `TAG §section`. The tags are listed in the legend below, and all files are in `sources/text/ (full texts of the cited works, not published)`. **[OWN]** marks my own inference, design or derivation, which is not a claim made by any source. All source text was treated as data.

## Source legend

| Tag | File | What it is |
|---|---|---|
| PONS-SRC | `pons-connect4-solver.md` | Pascal Pons' C++ Connect-4 solver (Position.hpp, Solver.cpp, TranspositionTable.hpp, MoveSorter.hpp, OpeningBook.hpp) |
| PONS-n | `pons-blog-0n-*.md` / `pons-blog-1n-*.md` | Pons tutorial parts 1-12 (each part has its benchmark table) |
| TROMP-C4 | `tromp-c4.md` | Tromp, "John's Connect Four Playground" |
| TROMP-FH | `tromp-fhourstones.md` | Tromp, Fhourstones benchmark page |
| TROMP-JAVA | `tromp-connect4-java.md` | Tromp, Connect4.java (Fhourstones 3.0 board logic) |
| HERZBERG | `bitboardc4-design.md` | Herzberg, "Bitboards and Connect Four" (explains Tromp's encoding) |
| CPW-x | `cpw-x.md` | Chess Programming Wiki pages: alpha-beta, minimax, PVS, iterative deepening, aspiration windows, TT, Zobrist, move ordering, history, killer, LMR, reductions, null move, futility, ProbCut, quiescence, singular extensions, proof-number search, time management, bitboards, Connect Four, Connect6, Victor Allis, SPRT, SPSA |
| CG-C4 | `web-codingame-community-puzzle-connect-4.md` | CodinGame multiplayer Connect 4 forum thread |
| CG-C4SOLO | `web-codingame-connect-four-solo-puzzle-1388.md` | CodinGame solo "Connect four" puzzle thread (low relevance) |
| CG-UTTT | `web-codingame-uttt-puzzle-discussion-22616.md` | CodinGame Ultimate Tic Tac Toe thread, posts #1-#98 |
| KG-xxxxxx | `web-kaggle-connectx-xxxxxx.md` | Kaggle ConnectX discussion pages, plus the leaderboard and index pages |
| BOECK | `arxiv-2507.05267-strongly-solving-7-times.md` | Böck 2025, "Strongly Solving 7x6 Connect-Four on Consumer Grade Hardware" |
| MISERE | `arxiv-2410.05551-misere-connect-four-is.md` | Steele & Larremore, "Misère Connect Four is Solved" |
| UCTADP | `arxiv-1912.05407-uct-adp-progressive-bias.md` | Cao & Lin 2019, "UCT-ADP Progressive Bias Algorithm for Solving Gomoku" (VCF/VCT) |
| RAPFI | `rapfi-code-search-ab.md` | Rapfi Gomoku engine: `search.cpp` (search, vcfsearch, vcfdefend), `parameter.h`, `history.cpp`, `movepick.cpp`, `hashtable.cpp`, `timecontrol.cpp` |
| RAPFI-PB | `rapfi-code-patterns-board.md` | Only the `Pattern4` enum and the `DEPTH_QVCF*` constants were looked up there |

Coverage note: the CPW pages were read up to their "See also / Publications / Forum Posts / External Links" tails. Those tails are bibliographies only, and for Connect Four and Victor Allis I also checked them. None of the sources contains the text of Allis' Connect-Four rules (claimeven, baseinverse, vertical, aftereven, ...). CPW-connect-four and CPW-victor-allis only cite the thesis. Allis-style material below is therefore limited to what the sources do contain: odd/even remarks (KG-188305), MISERE's take-even strategy and BOECK's static double-threat pruning. Everything else on that topic is marked [OWN].

---

# 1. Board representation

## 1.1 What the Connect-4 sources do (exact formulas)

**Pons/Tromp column-major layout with a sentinel row.** Each column uses H+1 bits: H cells plus one always-empty bit on top. For 7x6, bit index = col*7 + row, giving 49 bits (PONS-SRC Position.hpp header comment, PONS-06 §Binary representation, TROMP-JAVA comment block, HERZBERG §Using Two Longs). Herzberg explains why the extra row matters: it "hold[s] values of zero and [is] vital to prevent us from finding four in a row crossing 'boarders'" (HERZBERG §Are there Four In A Row?).

**Two encodings of the same board:**
- Tromp: `long color[2]`, one bitboard per colour, plus `byte height[W]` = the bit index of the lowest free cell in each column. Moves are `color[nplies&1] ^= 1L<<height[n]++` and `backmove` is the exact reverse (TROMP-JAVA `makemove`/`backmove`, HERZBERG §Make a Move / §Undo a Move).
- Pons: `current_position` (the stones of the side to move) plus `mask` (all stones). Playing a move is `current_position ^= mask; mask |= move;`, so the bitboards stay relative to the side to move (PONS-SRC `play`). The column move bit is `(mask + bottom_mask_col(col)) & column_mask(col)` (PONS-SRC `playCol`).

**Unique key:** Pons uses `key = current_position + mask` (+ bottom, which is constant). It is "a compact and non ambiguous" encoding on W*(H+1) bits (PONS-SRC Position.hpp comment, PONS-06 §Binary representation). Tromp uses `positioncode = 2*color[0] + color[1] + BOTTOM` (TROMP-JAVA `positioncode`).

**Constants:** Tromp computes `BOTTOM = ALL1 / COL1` ("has bits i*H1 set") and `TOP = BOTTOM << HEIGHT` (TROMP-JAVA). Pons computes `board_mask = bottom_mask * ((1<<HEIGHT)-1)` (PONS-SRC).

**Playable cells (one per non-full column):** `possible = (mask + bottom_mask) & board_mask`. The addition carries up through each column's filled cells into its first empty cell (PONS-SRC `possible()`, PONS-09). A column is playable if `(mask & top_mask_col(col)) == 0` (PONS-06 §Playing a move).

**Win test, 4 in a row, shift doubling:** `m = pos & (pos >> d); if (m & (m >> 2d)) win`, with d = 1 (vertical), H+1 (horizontal), H (one diagonal) and H+2 (the other) (PONS-06 §Checking for alignment, TROMP-JAVA `haswon`). Tromp: "test for a win with 8 shifts/ands" (TROMP-C4). In Fhourstones `haswon` is 25.5% of the profiled time (TROMP-FH gprof table).

**Threat ("winning position") mask:** the empty cells that would complete a line for the given player (PONS-SRC `compute_winning_position`, PONS-09):
```
r  = (p << 1) & (p << 2) & (p << 3);                       // vertical: 3 below
for d in {H+1, H, H+2}:                                     // 3 lateral directions
  q = (p << d) & (p << 2d);   r |= q & (p << 3d);  r |= q & (p >> d);
  q = (p >> d) & (p >> 2d);   r |= q & (p << d);   r |= q & (p >> 3d);
return r & (board_mask ^ mask);
```
(The blog version reuses `p >>= 3d`. The repository version recomputes `q` from fresh right shifts, which avoids losing bits shifted off the top.)

**Move-ordering score** = the number of threats after the move: `popcount(compute_winning_position(current_position | move, mask))` (PONS-SRC `moveScore`, PONS-10).

**"Anticipate losing moves"** (PONS-SRC `possibleNonLosingMoves`, PONS-09):
```
possible = possible(); oppWin = opponent_winning_position();
forced = possible & oppWin;
if (forced) { if (forced & (forced-1)) return 0;   // two immediate opponent wins -> lost
              possible = forced; }                   // must block the single one
return possible & ~(oppWin >> 1);                    // never play directly below an opponent threat
```

**Throughput reference:** the bitboard change alone gave a "6-fold" speed-up with identical node counts: Middle-Medium 3.48 s → 644 ms, ~62.7 M pos/s (PONS-06 §Benchmark).

## 1.2 Layout options for 12x8 (and generic W x H) in Java

**[OWN] The problem:** with a sentinel row, 12 x (8+1) = 108 bits. Row-major with a sentinel column is 8 x 13 = 104 bits. Without sentinels it is 96 bits. None of these fits in one `long`, and Java has no 128-bit integer type (CPW-bitboards §Defining Bitboards notes that Java uses the scalar 64-bit `long`; §Analysis notes that multi-word bitboards cost extra instructions per operation and that "some operations on bitboards are less general, f.i. shifts").

| Option [OWN] | Layout | Pros | Cons |
|---|---|---|---|
| A. Virtual 128-bit pair `(lo, hi)` | Pons column-major, index = c*S + r with S = H+1; bits 0-63 in `lo`, 64-127 in `hi`. For 12x8 column 7 straddles the words (bit 63 = row 0 of col 7) | All Pons/Tromp formulas carry over unchanged; generic for any W*(H+1) <= 128 (12x8 = 108, 14x8 = 126) | Each shift costs about 3 ops (two shifts and an OR); `mask + bottom` needs a manual carry; extracting a column needs a generic 128-bit shift |
| B. Two independent halves (cols 0-6 in `lo`, cols 7-11 in `hi`, each from bit 0) | Each half is a valid Pons board | Per-half ops are 1-op shifts; columns never straddle | Horizontal and diagonal lines cross the seam. A 6-window starting at column s=2..6 spans both halves, so you need a third "seam" word or an extra check → messy and error-prone |
| C. `long[] words` generic | Arbitrary size | Works for any board | Loops in the hottest code; not worth it for 12x8 |
| D. Per-column `int[W]` (9-bit columns) plus derived bitboards | Columns as small ints | The special move becomes trivial (operate on 3 ints) | Win and threat detection need the packed form → rebuilding it costs more than it saves |

**Recommendation [OWN]:** Option A with `final int S, W, H` fields and the shift amounts precomputed in the constructor. Add a separate fast path if W*(H+1) <= 64 (useful for tests on 7x6 against Pons' known results) and reject or fall back to option C above 128 bits. Keep relative-to-side-to-move boards (Pons): `cur` (2 longs) and `mask` (2 longs).

**Java shift pitfalls [OWN]:**
1. Java masks the shift distance (`x << 64 == x`), so the cross-word term `lo >>> (64-k)` is wrong for k = 0. Every shift used for lines here is between 1 and 5*(S+1) = 50 for 12x8, which is fine, but `shr128(k)` used for column extraction must branch on k == 0 and on k >= 64.
2. Always use `>>>`, not `>>`, because `lo` bit 63 (col 7 row 0) can be set. Tromp's Java uses `>>` (TROMP-JAVA `haswon`), which is only safe because 7x6 uses 49 bits.

```java
// [OWN] 128-bit helpers; 0 < k < 64
static long shlHi(long hi, long lo, int k){ return (hi << k) | (lo >>> (64 - k)); }  // lo' = lo << k
static long shrLo(long hi, long lo, int k){ return (lo >>> k) | (hi << (64 - k)); }  // hi' = hi >>> k
```
The JIT handles these well in local variables. Write the hot functions with explicit `lo`/`hi` locals; do not create a `U128` object per operation, because escape analysis is not guaranteed.

## 1.3 Six-in-a-row win detection

**[OWN] Generalising the doubling trick of PONS-06 and HERZBERG to length 6** (3 shifts per direction):
```
m1 = b & (b >>> d)        // runs of 2: bit i set if i, i+d set
m2 = m1 & (m1 >>> 2d)     // runs of 4: i..i+3d
m6 = m2 & (m1 >>> 4d)     // runs of 6: i..i+5d   ("longer counts": any run >= 6 contains a 6-run)
win if m6 != 0, for d in {1, S, S-1, S+1}
```
Sentinel correctness for 12x8 (S = 9): vertical runs stop at the always-zero row 8. The up-right diagonal (d = S+1 = 10) from row 7 lands on row 8 of the next column (zero). The down-right diagonal (d = S-1 = 8) from row 0 lands on row 8 of the same column (zero). Bits >= 108 are never set. This is the same argument as HERZBERG §Are there Four In A Row?. Cost with option A: 4 directions x 3 128-bit shifts ≈ 12 x 3 + ANDs ≈ 60 simple ops.

**[OWN] After a normal move** only the mover can have a new line. A cheaper equivalent test is: "was the landing cell in my threat mask?". Pons does this with `isWinningMove(col) = winning_position() & possible() & column_mask(col)` (PONS-SRC). **After a special move**, check `hasSix(cur) || hasSix(opp)`, because by the rules either colour's line makes the mover win.

## 1.4 Threat masks for length 6

**[OWN] Generalising `compute_winning_position`:** an empty cell x is a threat in direction d if, for some k in 0..5, own stones occupy x-d..x-kd and x+d..x+(5-k)d. Build prefix ANDs:
```
L_0 = ALL; L_j = L_{j-1} & (p << j*d)     // own at x-d ... x-jd   (j = 1..5)
R_0 = ALL; R_j = R_{j-1} & (p >>> j*d)    // own at x+d ... x+jd
threat_d = OR_{k=0..5} (L_k & R_{5-k})
vertical: only L_5 with d=1 (above an empty cell everything is empty)
threats = (threat_1 | threat_S | threat_{S-1} | threat_{S+1}) & boardMask & ~mask
```
This costs about 10 shifts per lateral direction plus 5 for vertical, roughly 35 x 128-bit shifts. Pons' version for 4 is the special case with gap positions k = 0..3 (PONS-SRC). Pons notes the computation is "quite expensive" for ordering: about 3x the per-node cost, but up to 100x fewer nodes (PONS-10 §Benchmark).

Derived masks [OWN]:
- `playableThreats(me) = threats(me) & possible` = immediate wins (Pons `canWinNext` = `winning_position() & possible()`, PONS-SRC).
- `latentThreats(me) = threats(me) & ~possible` = threats needing support (the cell below is empty). These drive zugzwang and parity play (§3.2).
- `stacked(me) = playableThreats(me) & (threats(me) >>> 1)`: a playable threat with another own threat directly above. The opponent's forced block hands you the cell above, so a single stacked pair wins like a double threat. This is the mirror image of Pons' `~(opponent_win >> 1)` rule (PONS-09).

## 1.5 Applying the special move (destroy 3x3 + gravity)

**[OWN] Key fact:** columns stay bottom-contiguous before and after the move, because gravity is re-applied. So for each affected column the transformation is a fixed bit operation on its S-bit field. For landing cell (c, r), set the disc first, then for each column cc in [max(c-1,0), min(c+1,W-1)]:
```
r0 = max(r-1, 0); r1 = min(r+2, H);          // cleared rows [r0, r1)
w  = column bits of board X at cc             // X = each colour board
w' = (w & ((1<<r0)-1)) | ((w >>> r1) << r0)   // keep below, drop everything above by (r1-r0)
```
This is exact. If the column top is below r1, `w >>> r1 = 0`. If it is above r1, all cells in [r0, r1) were occupied (contiguity), so the drop is exactly r1-r0. In column c the just-placed disc at row r is always cleared. Apply the formula to both colour boards and recompute `mask = cur | opp`. Column extraction and insertion in layout A use `shr128(cc*S) & colMask` and the inverse. That is 3 columns x 2 colours x about 10 ops, cheap compared with the win test that follows. A note on JDK features: `Long.compress`/`Long.expand` (PEXT/PDEP intrinsics) exist only in JDK 19+ and are not needed here.

**Undo:** a special move is not reversible from the move alone, because the cleared stones are lost. Use **copy-make** for all moves: preallocated per-ply arrays `long[] curLo, curHi, maskLo, maskHi; byte[] cdMe, cdOpp; long[] hash`. Pons' solver copy-makes too (`Position P2(P); P2.play(next);`, PONS-SRC `negamax`), whereas Tromp uses make/unmake (TROMP-JAVA). Copy-make also makes exception-based time-outs safe (§4.3).

**Draw and terminal tests [OWN]:** top-row full ⇔ `(mask & topRowMask) == topRowMask`. The 1000-turn cap needs a turn counter in the state. The terminal check order after a move is (a) any 6-line → mover wins, then (b) full top row → draw, then (c) turn ≥ 1000 → draw. Confirm the exact order against the engine bytecode.

---

# 2. Core search

## 2.1 Negamax alpha-beta, fail-soft, PVS

- Alpha-beta's minimal tree is b^⌈n/2⌉ + b^⌊n/2⌋ - 1 leaves vs b^n for minimax. This produces the odd/even effect (CPW-alpha-beta §Savings, table). Fail-soft "is generally regarded better than fail-hard ... because it retains more information" (CPW-alpha-beta §Fail soft).
- PVS: search the first move with the full window and the rest with a zero window [alpha, alpha+1], then re-search on fail-high. "With a good move ordering we expect to save about 10%" and "modern chess programs find fail-highs on the first move around 90% of the time" (CPW-PVS §The Idea, §PVS and NegaScout). Use the templated PV/NonPV split ("PVS + ZWS", CPW-PVS §PVS + ZWS) and the fail-soft Diepeveen pseudo-code (CPW-PVS §PVS and Aspiration). In Java, the template becomes a boolean `pvNode` parameter or two methods.
- Pons' solver is a null-window driver over an exact score range: it repeatedly calls `negamax(P, med, med+1)` with a binary search on `med`, biased toward 0, to find the exact score (PONS-SRC `solve`, PONS-08). Pons calls this his "iterative deepening": "Our specific score function allows to limit the depth of our exploration by limiting the [alpha; beta] search window" (PONS-08 §Iterative deepening). **[OWN]** This works in Connect-4 because score = remaining-move count bounds the depth. Use it in our engine only as an **endgame proof mode**: a null window just below the win score answers "forced win?" cheaply. It cannot replace depth-based ID, because special moves remove stones and the 1000-turn cap, not the cell count, bounds game length.
- Pons' "score bound clamp" (`max = (W*H-1-nbMoves)/2` etc., PONS-SRC `negamax`) is **invalid** for us [OWN], because the number of empty cells no longer bounds the remaining plies. Use **mate-distance pruning** instead: `alpha = max(mated_in(ply), alpha); beta = min(mate_in(ply+1), beta); if (alpha >= beta) return alpha;` (RAPFI `search` Step 3).

## 2.2 Iterative deepening and aspiration windows

- ID: "even if one is about to search to a given depth, ... iterative deepening is faster than searching for the given depth immediately", thanks to PV, hash and refutation moves from previous iterations. In an unfinished iteration, "if we make sure that this move is searched first in the next iteration, ... the results from the partial search can be accepted" (CPW-iterative-deepening §How it Works).
- KG-188305 (a top-ranked ConnectX agent): "It is also better to only search even depths (0, 2, 4, etc.)". The same post used exceptions to exit recursion at time-out. **[OWN]** The odd/even effect is real (CPW-alpha-beta table), but with a fixed 100 ms per call, skipping odd depths only matters if the odd iteration is often unfinished. Measure it, do not assume it.
- Aspiration: use "a window around [the previous score]", re-search on fail, and "only widen the bound that fails". Modern engines "start with a rather small aspiration window, and increase the bound that fails in an exponential fashion" (CPW-aspiration-windows §Gradual Widening). Rapfi's concrete scheme (RAPFI `aspirationSearch`, `parameter.h`):
  - Enabled only from depth ≥ `ASPIRATION_DEPTH = 5.3`.
  - Initial delta `AspirationInitialDelta = {20,10,11}` (per rule).
  - Next delta = `prevDelta*(3+|prevValue|/1024)/4 + AspirationAddDelta{8,3,8}`.
  - On fail-low: `beta=(alpha+beta)/2; alpha=value-delta`. On fail-high: `beta=value+delta; failHighCnt++`. The re-search depth is reduced by `failHighCnt/3`.
- **[OWN]** Skip aspiration when |previous score| is in the mate range. The learned eval's scale sets delta, so tune it by SPSA (§5).

## 2.3 Transposition table

**What to store** (CPW-TT §What Information is Stored): key/signature, best move, depth (draft), score plus bound type (exact = PV node, upper = All node, lower = Cut node), age. **Cut-off rule** (CPW-TT §Transposition Table Cutoffs): depth ≥ current and (EXACT, or LOWER ≥ beta, or UPPER ≤ alpha). "In more advanced engines transposition table cutoffs are not performed on PV-Nodes." Rapfi does exactly that (`if (!PvNode && ttHit && ttDepth >= depth && (ttBound & (ttValue >= beta ? BOUND_LOWER : BOUND_UPPER))) return ttValue;`, RAPFI `search` Step 4). The hash move is "the most important part of move ordering": "In Stockfish, 75% of cutoffs produced in a position with hash moves are caused by the hash move" (CPW-TT §Hash Move).

**Layouts and sizes in the sources:**

| Engine | Layout | Size | Replacement |
|---|---|---|---|
| Pons (PONS-SRC TranspositionTable.hpp, PONS-11) | Parallel arrays `K[]` (partial key) and `V[]` (uint8 value), index `key % size` with **size = next prime ≥ 2^24** | 2^24 entries now. The blog version used the smallest prime above 2^23 with 4+1 bytes per entry ≈ 40 MB, instead of 64 MB for 8-byte entries | Always replace ("keep the last entry"). Partial keys are collision-free by the Chinese remainder theorem when bits(key) ≤ bits(stored key) + log2(size): 49-bit keys, 32-bit stored keys, any odd size > 2^17 (PONS-11 §Case of 7x6) |
| Pons, value encoding (PONS-12) | Upper bound stored as `alpha - MIN_SCORE + 1`, lower bound as `score + MAX_SCORE - 2*MIN_SCORE + 2`, 0 = empty | 1 byte | Lower bounds cut nodes by 10-15% but give less time gain, because "pruned nodes are less expensive" (PONS-12) |
| Tromp Fhourstones (TROMP-FH) | 64-bit bitboards, hash = "a single 64-bit modulo" | 64 MB, 8,306,069 entries in the sample run | "twobig" replacement strategy. Tromp later used "a smarter transposition table replacement strategy" for larger boards (TROMP-C4) |
| Böck (BOECK §3.3) | Exploits vertical symmetry | 2^28 entries | Always overwrite |
| Rapfi (RAPFI hashtable.cpp) | 12-byte entry: key32, value16, eval16, pv 1 + bound 2 + best 13 bits, depth8, generation8. **5 entries per 64-byte bucket** (cache line, 4 bytes padding). Bucket index `mulhi64(key, numBuckets)` | Default 16 MB | Store into the matching key, else the entry with the lowest `depth8 - (generation - gen8)`. Do not overwrite the same position unless the bound is exact or `depth + 2 >= oldDepth`. Keep the old best move if the new one is NONE. The probe refreshes the generation. `key32` is stored XOR data for lock-free sharing |

Replacement schemes (CPW-TT §Replacement Strategies): always replace; depth-preferred; **two-tier** (Thompson/Condon: a depth-preferred and an always-replace entry per slot); bucket systems ("overwrites the entry in the bucket with the lowest depth"); aging via the halfmove clock "modulo some power two constant". Collision math: Gillogly's table gives the combined address+check bits needed. For example, 10^6 stored positions at 0.001 collision probability need 49 bits (CPW-TT §Bits Required). Hyatt & Cozzie "concluded that 64-bit signatures are more than sufficient" (CPW-TT §Bits Required). For 64-bit Zobrist keys, expect a collision "after about 2^32 or 4 billion positions" (CPW-Zobrist §Theory).

**Zobrist** (CPW-Zobrist §Initialization, §Runtime): one random number per (piece, square), side to move, and each extra state component (castling and e.p. in chess). Update incrementally by XOR. Use your own PRNG "for reproducibility". "Linear independence ... is much more important than hamming distance" (CPW-Zobrist §Linear Independence).

**[OWN] Recommended TT for our engine:**
- **Key content:** the stones (relative to the side to move, as Pons does), **both cooldown counters** (side-to-move first, then the other side), and optionally a "near-1000-turn" bucket when turn > 1000 - maxPly. With relative stones plus an ordered cooldown pair, the side to move does not need its own bit. If you keep absolute colour boards, add a side-to-move Zobrist bit as in chess (CPW-Zobrist §Initialization). **Important: you cannot infer the side to move from the disc count** once special moves exist, because they remove an arbitrary number of discs of both colours.
- **Hash computation:** recompute from the bitboards at each node rather than updating incrementally. Incremental Zobrist would need XORs for every stone that a special move moves or clears, which is possible via `(old ^ new)` bit iteration over the 3 columns but is fiddly. From-scratch hashing costs about 10 ops, e.g. `h = mix64(curLo*K1 ^ rotl(curHi*K2,23) ^ maskLo*K3 ^ rotl(maskHi*K4,41) ^ ZCOOL[cdMe][cdOpp])` with a splitmix64-style finaliser. Pons' exact key (`cur + mask`, 108 bits) is also available if you want zero type-1 errors, at the cost of storing 2 longs.
- **Storage:** `long[] keys` + `long[] data` (or one interleaved `long[2*N]`). Pack data in 64 bits: score 16 | bound 2 | depth 8 | bestCol 4 | bestSpecial 1 | generation 8 | staticEval 16 = 55 bits. Use the power-of-two index `(int)(h >>> (64-bits))` and verify with the full 64-bit `h`. Use buckets of 2 (two-tier) or 4 (Rapfi-style depth-minus-age) entries. Sizing: 16 bytes/entry, so 2^21 entries = 32 MB and 2^22 = 64 MB. Pick according to the heap limit (open question). At an estimated 10^5-10^6 nodes per move, 2^21 entries comfortably exceed the per-move node count. Increment the generation per root call and keep the table across calls, as Rapfi does ("most todays programs do not [clear], profit from entries of previous searches", CPW-TT §Aging).
- **Mate scores:** store them ply-adjusted (Rapfi `searchValueToStoredValue(value, ply)` / `storedValueToSearchValue`, RAPFI hashtable.cpp).

## 2.4 Move ordering stack

Evidence of value:
- Pons, Middle-Medium (strong solver), node counts: plain alpha-beta 453.6 M → centre-first column order 40.4 M (PONS-04, PONS-05: "more than 10-fold") → +TT 1.73 M (PONS-07) → +ID/null-window 1.70 M (PONS-08) → +skip losing moves 1.14 M (PONS-09) → **+threat-count ordering 48,290** (PONS-10, "divided by up to 100 in most complex cases", per-node cost about ×3) → lower-bound TT 39,900 (PONS-12). Begin-Hard went from unsolvable within 24 h to 5.49 s (65.9 M nodes) with all improvements (PONS-12).
- Pons' static order is `columnOrder[i] = W/2 + (1-2*(i%2))*(i+1)/2`, i.e. centre first. For W = 12 this gives 6,5,7,4,8,3,9,2,10,1,11,0 [OWN arithmetic]. Pons adds moves to an insertion-sort `MoveSorter` in reverse order so that ties keep centre-first (PONS-SRC Solver.cpp, MoveSorter.hpp, PONS-10).
- Böck sorts moves "by their distance to the central board column and by the number of threats they generate" (BOECK §3.3).
- Tromp's dynamic history: "A move causing a cutoff is rewarded as many points as moves previously tried, each of which gets a -1 penalty, thus preserving total weight and avoiding renormalization (uniform penalties were found to work much better than depth dependent ones)" (TROMP-FH).
- Typical chess order (CPW-move-ordering §Typical move ordering): PV move → hash move → good captures → killers → history-sorted quiets → bad captures. Use selection-sort on fetch instead of sorting everything. "The longer it takes to refute a move, the higher is its chance to become best move in the next iteration", so re-order root moves by subtree size (CPW-move-ordering §Root Node Considerations).
- Killers: "typically containing two or three moves per ply", and the slots should hold different moves (CPW-killer-heuristic).
- History: `+= depth*depth` classic. Modern "history gravity" is `h += clamped - h*|clamped|/MAX_HISTORY`, with maluses for all quiets searched before the cut-off move, e.g. `bonus = 300*depth - 250` (CPW-history-heuristic §Update). Continuation/counter-move history is indexed by the previous move (CPW-history-heuristic §Counter Moves History, §Continuation History). Rapfi keeps separate history tables for attack moves (pattern ≥ flex3) and quiet moves, plus a counter-move table, and skips the update when the last opponent move was a forcing "five" (RAPFI history.cpp `updateBestmoveStats`).
- KG-188305 order: centre/"condensed" weights, history heuristic, **odd-even strategy** ("Odd rows are better for the odd player and even rows are better for the even player"), and "if moveIsForced: nextMoves = [onlyForcedMove]".

**[OWN] Recommended order for Quantum Connect (moves are encoded as `col | special<<4`):**
1. Immediate normal win (`playableThreats(me)`). Return immediately; no search needed.
2. Immediate special-move win: any special that leaves a 6-line of either colour. Only if the special is available; test by apply plus `hasSix` for 12 columns, or restrict to columns whose 3x3 block touches a "near-line" (see §3.3).
3. Forced block(s) of the opponent's playable threats (Pons rule). If there are ≥2 and no special is available → return loss (§3.1 caveat).
4. TT/PV move.
5. Moves creating a stacked or double playable threat (they win unless defused).
6. Killers (2 per ply).
7. Remaining normal moves by `threatCount(after) + history[stm][col]` (+ an odd/even-row bonus from the eval's parity feature), tie-broken centre-first.
8. Remaining special moves, ordered by "opponent threat cells destroyed - own threat cells destroyed". They come last because they are rarely best outside tactics, and they are excluded from the "≥2 forced → lost" shortcut.
9. Never generate "below an opponent threat" normal moves (Pons `~(oppWin >> 1)`), except when every move loses.

## 2.5 Reductions, pruning and extensions: what is safe in a zugzwang-heavy gravity game

Gravity games revolve around zugzwang: who must play under whose threat (PONS-09 rule 2; MISERE §III.1 take-even). Every heuristic that assumes "doing something is at least as good as passing" is suspect:
- **Null-move pruning:** the null-move observation fails in zugzwang. "This can be avoided by disabling NMP at pawn endgames, where zugzwangs are prone" (CPW-null-move-pruning §Core Idea). Basic R = 3 or 4 (§Basic Implementation). Verification search gave Hyatt no gain in Crafty (§Zugzwang Verification). Rapfi measured only "~3 elo" from NMP even in zugzwang-free Gomoku (RAPFI `search` Step 9). **[OWN] Verdict: do not use NMP for pruning.** A null move may still be used as a *threat detector* ("what does the opponent threaten if I pass") for move ordering (CPW-NMP §Threat Detection, §Influence on Move Ordering), but with gravity "passing" also changes who must fill odd cells, so do it only as an ordering hint.
- **Quiescence stand-pat** "is based on the Null Move Observation - it assumes that we are not in Zugzwang" (CPW-quiescence-search §Standing Pat). **[OWN]** Stand pat in the threat search is acceptable only because the defender's forced replies are handled explicitly (§3.3). It does not make a zugzwang-blind leaf safe; the evaluation must model parity.
- **LMR** is safe-ish, because it re-searches on fail-high: "can often reduce the effective branching factor to less than 2" (CPW-LMR intro). Common exclusions: depth < 3, tactical moves, check-like moves, moves causing extensions, killers, "tactically threatening moves" (CPW-LMR §Common/Uncommon Conditions). Formula examples: Ethereal `0.7844 + ln(depth)*ln(moves)/2.4696` for quiets; Fruit Reloaded `sqrt(depth-1)+sqrt(moves-1)` (CPW-LMR §Base Reduction). Rapfi's LUT is `Factor*ln(i)` with r = lut[d]*lut[moveCount] (Factor 0.95/0.78/0.83) (RAPFI `initReductionLUT`, `reduction`). **[OWN]** Never reduce: forced blocks, moves that create a playable or stacked threat, special moves at PV nodes, moves into a column whose parity changes who gets a latent threat. With b ≤ 24, reduce gently (≈ 1 ply for move ≥ 4 at depth ≥ 3) and SPRT-test larger values.
- **Futility pruning / razoring:** not "when either alpha or beta are close to the mate value" and not without at least one legal move (CPW-futility-pruning §Conditions). Rapfi's measured gains in Gomoku were large ("Razoring with VCF (~68 elo)", "Futility pruning: child node (~121 elo)", "Move count pruning ... (~107 elo)", RAPFI `search` Steps 7, 8, 12). Rapfi's razoring *drops into the VCF search* instead of returning eval. **[OWN]** Those gains depend on a strong eval and a game without zugzwang. In our game, start without futility, add razoring-to-threat-search first (it is the safest variant because the threat search still sees forced wins), then SPRT each.
- **ProbCut / Multi-ProbCut:** model `v = a*v' + b + e` between a shallow search at d' and a deep one at d; cut if `v' >= (Φ^-1(p)*σ + β - b)/a` (CPW-probcut §The Idea). Example pseudo-code: T = 1.5, d' = 4, D = 8. Crafty regressions: (3,5) middlegame a = 0.998, b = -7.0, σ = 55.8, r = 0.90 (CPW-probcut §ProbCut or MPC in Chess). "Part of the reason why ProbCut is so successful in Othello is that null-move does not work in Othello" (CPW-probcut, Jiang quote). **[OWN]** That makes ProbCut the natural selective technique for a zugzwang game like ours. Fit a, b, σ by game phase (move number / empty cells) on our own data before trying it, and expect a tactical-strength trade-off (Allgeuer's warning in CPW-probcut).
- **Extensions:**
  - Rapfi extends the forced single reply to an opponent "five" by `OPPO5_EXT = 1.1` ("Singular response extension for opponent B4 attack (~77 elo)").
  - Singular extension: requires depth ≥ `SE_DEPTH = 6.85`, the TT move, a TT lower bound and ttDepth ≥ depth - ~2. It searches the excluded-move node at `singularBeta = ttValue - SEScale*depth` with depth*0.55, extends 1 or 2 plies, and multi-cuts if singularBeta ≥ beta ("~52 elo").
  - TT-move extension 0.08-0.24 ply (~12 elo).
  - All of the above: RAPFI `search` Step 13, `parameter.h`; CPW-singular-extensions for the background.
  - **[OWN]** For us: (a) a forced-block reply costs 0 ply (as in KG-188305 "only evaluate that position"); (b) extend moves that create a playable threat by about 1/2 ply (fractional depth as in Rapfi); (c) singular extension only once the base engine is strong, gated by SPRT.
- **Internal iterative reduction (IIR):** reduce depth by about 0.4-0.7 when a PV node has no TT move (RAPFI Step 10, `IIR_REDUCTION_PV = {0.45,0.43,0.37}`, "~3 elo"). This is low priority.

---

# 3. Threat search

## 3.1 Pons: anticipate losing moves (exact, cheap)

The rules (PONS-09) are: "always play a column on which the opponent has a winning position in the bottom of the column; never play under an opponent winning position; if the opponent has more than [one] directly playable winning positions ... we will lose". Code is in §1.1. Effect on the strong solver: Middle-Medium 1.70 M → 1.14 M nodes, Begin-Easy 236,700 → 107,400 (PONS-08 vs PONS-09). After this change `negamax` never needs to test "can I win next move" inside the loop, because it never enters a position where the opponent can (PONS-09 §Implementation). Böck uses the same static rule: "a player cannot prevent two threats at the same time, thus losing the game" (BOECK §3.3).

**[OWN] Mandatory adaptations for Quantum Connect:**
1. **The special move breaks "two threats = lost".** If the side to move has its special available, it may clear stones of the opponent's threatening lines inside a 3x3 block and win the race. It may even create a 6-line of either colour, which is an immediate win for itself. So `forced has ≥2 bits` → loss only if `cd[stm] > 0`. Otherwise, try the special moves before declaring a loss.
2. **The opponent's special move adds threats Pons does not see.** A position where the opponent has 0 playable threats can still be lost in one, if the opponent's special creates a line. Gravity drops discs by 2-3 rows in the neighbouring columns, so the line can be of either colour. The `~(oppWin >> 1)` rule stays valid for normal-move threats. Special threats must be computed by simulation (≤ 12 applications) when `cd[opp] == 0`. **Cost control:** only simulate specials whose 3x3 block lies within 2 cells of an existing ≥3-stone segment (a cheap bitboard prefilter: `near = dilate(m2-type run masks)`).
3. **Immediate-win checks** must cover both normal and special wins of the side to move at each node entry, as Rapfi's `quickWinCheck` does (RAPFI Steps 2-3).

## 3.2 Allis-style threat analysis, parity and zugzwang

What the sources contain:
- The Allen and Allis 1988 solutions were "rule based approaches in combination with knowledge database" (PONS-01). Allis' later work is proof-number search and "Go-Moku Solved and Threat-Space Search" (CPW-victor-allis bibliography, CPW-PNS §Applications: "Victor Allis first used the notion of proof and disproof numbers while analyzing and solving Connect Four, still called conspiracy numbers").
- Odd/even: "Odd rows are better for the odd player and even rows are better for the even player" (KG-188305).
- MISERE's **take-even** strategy: on an even-height board the second player can always answer directly on top of the first player's move. "The board's even height guarantees that any odd-row play by P1 will have an empty even-row space above it" (MISERE §III.1-III.2). On odd heights a "delayed take-even" uses row 0 as a parity buffer (MISERE §III.3).
- Tromp's 6xh observation: "the winning starting moves are NEVER in the central columns" (TROMP-C4), a reminder that centre-first is only a heuristic.

**[OWN] Consequences for our game:**
- **H = 8 is even**, so the follow-up ("play on top") strategy is available to the second player in principle. Whoever needs an odd-row (1-indexed) latent threat versus an even-row one follows the classic Connect-4 parity logic.
- **Special moves reset parity locally.** A special removes 2 cells in the landing column and up to 3 per neighbouring column, changing the empty-cell parity in up to 3 columns. Our eval and threat logic therefore cannot treat parity as fixed: each special in the horizon is a "parity flip" resource.
- **Eval features to learn** (for the pattern-table eval): latent threats by row parity per side; "threat directly above an opponent threat" stacks; number of columns with odd empty count; special availability (cooldowns) of both sides. These are cheap from the §1.4 masks.
- **A full Allis rule system** (claimeven, baseinverse, ...) is out of reach for this game (6-in-a-row, special moves) and not in the sources. Prefer search plus a learned parity-aware eval.

## 3.3 VCF (Rapfi) and its gravity adaptation

**Rapfi's structure** (RAPFI `search.cpp`):
- The main search drops into the threat search at the horizon: `if (depth <= 0) return oppo5 ? vcfdefend(...) : vcfsearch(...);` ("~17 elo"). The same applies after IIR reductions push depth ≤ 0 (Steps 1 and 10).
- **`vcfsearch` (attacker node):**
  1. checkExit (time).
  2. Draw checks.
  3. `quickWinCheck`.
  4. Mate-distance pruning.
  5. TT probe: if ttDepth ≥ depth, it tightens alpha/beta from the stored bound and returns once they cross. This applies at non-PV nodes, and at PV nodes only in helper threads (so the main thread keeps a full PV).
  6. Static eval (or ttValue if its bound allows) = stand pat. If ≥ beta, store a LOWER bound with DEPTH_NONE and return.
  7. **Delta pruning:** `if (!PvNode && bestValue + qvcfDeltaMargin(depth) < alpha) return alpha`, with margin `max(2084 + 62*d, 768)` for freestyle (d ≤ 0).
  8. Iterate VCF moves only (`MovePicker QVCF`: TT move if it makes a ≥ E_BLOCK4 pattern, otherwise generated "four" moves). Each is followed by `-vcfdefend(..., depth-1)`.
  9. Store to the TT with `max(depth, DEPTH_QVCF)`.
- **`vcfdefend` (defender node):** if the opponent has no five-threat (`!oppo5`), return the static eval. Otherwise play **the only defence move** (`lastPattern4(oppo, A_FIVE)`) and call `vcfsearch` **without reducing depth** ("we do not reduce depth for vcf defence move").
- **Candidate generation in QVCF:** `generateNeighbors<VCF | COMB>` in the `RANGE_SQUARE2_LINE4` neighbourhood **of the side's own last move**. Plain block-fours (B4) are allowed only when `depth >= DEPTH_QVCF_FULL (-1)` or when the side's two previous moves were already ≥ D_BLOCK4_PLUS (RAPFI movepick.cpp `MovePicker(QVCF)`, `QVCF_MOVES`; RAPFI-PB `DEPTH_QVCF_FULL = -1`, `DEPTH_QVCF = -2`). This locality is what keeps VCF from exploding.
- **Main-search move-picker stages by threat state** (RAPFI movepick.cpp): if the opponent has a five → only the defend-five move; if the opponent has a flex-four → defend-four moves plus own VCF moves; if the opponent has a B4+F3 combination → defend moves plus own VCF moves; otherwise all moves sorted by history and counter-move.
- **Rapfi pattern vocabulary** (RAPFI-PB `enum Pattern4`): A_FIVE (immediate win) > B_FLEX4 (open four or double four) > C_BLOCK4_FLEX3 > D_BLOCK4_PLUS > E_BLOCK4 > F_FLEX3_2X > G_FLEX3_PLUS > H_FLEX3 > ... It is a per-cell, per-colour classification of what playing that cell creates.
- **UCTADP §IV-C:** VCF = "continuously manufacturing a sleep four attack ... in the case of the opponent do not have opportunity to fight back"; VCT = the same with "alive three" attacks. Before attacking, "detect whether there is a victory situation for enemy or whether it is possible to carry out a counterattack". In UCTADP these are used as move pre-selection (VCF/VCT candidates first). CPW-connect6 mentions "Alpha-Beta / MTD(f) along with VCF search ... to find a path to win in the endgame" in Connect6, our closest k-in-a-row relative (six in a row).

**[OWN] Gravity translation.** In a drop game the forcing unit is the **playable threat** (a threat cell in `possible`). The opponent *must* occupy it now: it is the analogue of a Gomoku "four". A latent threat (cell not yet supported) is not forcing; it is a long-term parity asset (§3.2), which is roughly the analogue of a "three" that needs support.

```
// [OWN] Gravity VCF ("victory by continuous playable threats")
int vcfAttack(ply, alpha, beta, qdepth):
    if (hasImmediateWin(stm, normal or special)) return WIN - ply
    if (playableThreats(opp) != 0) return vcfForcedReply(...)   // we are not free: answer first
    standPat = eval(); if (standPat >= beta) return standPat; alpha = max(alpha, standPat)
    if (qdepth <= -QMAX) return standPat                        // hard cap, e.g. 8-12 attacker moves
    for m in forcingMoves(stm):            // normal moves m with playableThreats(stm) after m != 0
                                           //   (incl. m creating a threat at m+1, the cell it unlocks)
        order: (1) creates >=2 playable or a stacked threat, (2) TT move, (3) threat count
        make m
        v = -vcfDefend(ply+1, -beta, -alpha, qdepth-1)
        unmake; update best/alpha; if (v >= beta) break
    TT store (bound, depth = qdepth)
    return best

int vcfDefend(ply, alpha, beta, qdepth):
    P = playableThreats(attacker)
    if (P == 0) return eval()
    if (hasImmediateWin(stm)) return WIN - ply                  // counter-win beats blocking
    replies = (popcount(P) == 1) ? {block P} : {}               // >=2 -> no normal defence
    if (cd[stm] == 0) replies += specials that leave playableThreats(attacker) == 0
                                 (or that win outright)
    if (replies empty) return LOSS + ply                        // proven
    best = -INF
    for r in replies: make r; v = -vcfAttack(ply+1, -beta, -alpha, qdepth); unmake; ...   // no depth decrement (Rapfi)
    return best
```
Notes [OWN]:
- **Candidate generation is exact and local,** like Rapfi's neighbourhood of the last move. A move can create a playable threat only if, after it, `threats(stm) & possible'` is non-empty. Two cases: the threat sits at the newly unlocked cell `m+1`, or at a playable cell elsewhere that the new stone completes a 5-of-6 pattern for. Precompute `fiveOfSix(stm)` (the threat mask) once per node. A move m is forcing iff `threats(stm | m)` intersects `possible(after m)`. That is at most 12 candidates, each costing one threat-mask computation. Pons' `moveScore` is the same computation (PONS-SRC).
- **Stacked threat = gravity "four-three".** If the block cell x has an attacker threat at x+1 (`P & (T >>> 1)`), the block loses next move. Detect it at generation time and treat it like a double threat.
- **Counter-threat handling** (Rapfi's DEFENDFOUR / DEFENDB4F3 stages include the defender's own VCF moves): a defender's block that *also* creates a defender playable threat flips the initiative. `vcfAttack` must therefore check `playableThreats(opp)` on entry (first line above).
- **Use at the horizon** (replaces chess-style quiescence): `if (depth <= 0) return vcfAttack(...)`. Also use it as **razoring** (Rapfi Step 7: `if (!PvNode && eval + razorMargin(depth) < alpha) return vcfsearch(alpha, alpha+1)`, with `razorMargin` active only for depth < 2.6). Store VCF results in the same TT with negative depths, as Rapfi does with `DEPTH_QVCF = -2`.
- **Proof-number search** (CPW-PNS §Algorithm, pseudo-code for select / expand / update): best-first, proof and disproof numbers, ideal for "is there a forced win" in narrow trees. Allis used it for Connect Four. **[OWN]** It needs the tree in memory, so it is not worth it inside 100 ms. Use it offline (df-pn, CPW-PNS §Depth First) to build tactical test suites and to verify VCF results.

## 3.4 The special move inside the main search

**[OWN] Design:**
- **Move set:** 12 normal moves plus up to 12 specials when `cd[stm] == 0`, so b ≤ 24.
- **Cooldown bounds the extra branching.** A side can use its next special only 10 own turns (20 plies) after the previous one. Any search path shorter than 21 plies therefore contains at most one special per side. The ×2 branching is a one-shot option per side within the horizon, not a factor at every level.
- **State:** the cooldown counters are part of the node state, the TT key and the eval input (special availability is a big positional factor).
- **Ordering:** specials come last except (a) immediate special wins, (b) specials that are the only defence against ≥ 2 playable opponent threats or a stacked threat, and (c) the TT move. Give specials LMR treatment at non-PV nodes, and consider pruning non-tactical specials at depth ≤ 2 (test via SPRT).
- **Root and decision split:** the root search ranks (column, special) pairs. `accelerateQuark` returns the column of the best pair and caches the special flag (§4.4).
- **Evaluation consistency:** after a special, the side to move changes as usual. The eval must treat "special available soon" as a resource. For the opponent, our latent threats near their 3x3 reach are worth less.

---

# 4. Time management for a hard 100 ms deadline in Java

## 4.1 What the sources say

- **Soft and hard bounds:** "one optimium time threshold (soft bound), and one maximum time threshold (hard bound). ... the optimum time threshold is checked on each iteration of iterative deepening, while the maximum time threshold is checked periodically, usually by every set amount of nodes" (CPW-time-management §Soft Bound). Premature termination applies with only one legal move, or before starting a new iteration when elapsed time is above roughly 50% of the allocation (§Premature Termination).
- **Rapfi** (RAPFI timecontrol.cpp, `search`):
  - `maximumTime = min(turnTime, matchTimeLeft/min(matchSpaceMin, movesToGo)) - turnTimeReserved`
  - `optimumTime = maximumTime * advancedStopRatio`
  - The hard check is `elapsed() >= maximum()` (`checkTimeupCondition`), polled through `checkExit()` at every main-thread node.
  - The per-iteration soft check scales the optimum by best-move stability (`timeReduction = 1 + scale*stablePlies`), `bestMoveInstability = 1 + 2*averageBestMoveChanges` and the "falling factor" (score drop, clamped to [0.5, 1.5]), and caps it at `maximum/timeDivisor(depth)`.
  - It stops after a mate is found plus `numIterationAfterMate` iterations, and on a singular root.
- **ID fallback:** "the program always has the option to fall back to the move selected in the last iteration", and partial-iteration results are usable if the previous best is searched first (CPW-iterative-deepening §How it Works).
- **KG-188305:** iterative deepening "until all available time has run out (allowing a small buffer)". Raising an exception from the inner negamax and catching it at the top "worked well for exiting my recursion quickly when the time is almost up".
- **CG-UTTT measurements:** iteration counts vary run to run at the same time budget. One player reported 29K-41K iterations at 98 ms (CG-UTTT #97). Another saw rollouts drop randomly from 16-17k to 10-11k for several turns (#74). The platform gives 1 core, "multithreading is useless", and the process is paused while waiting for input (#52, #48). Pondering yields only about 10% extra because the referee pauses the process shortly after output (#65).

## 4.2 [OWN] Policy for a fixed 100 ms with no time bank

There is no time bank, so unused time is simply lost. The only objectives are **never exceed the deadline** and **use as much of the budget as possible.** So:
- **Hard limit:** measure from method entry with `System.nanoTime()`. Abort the search at `T_hard ≈ 75-85 ms`, chosen after measuring the harness' call overhead and the 99.9th-percentile JVM pauses (§4.5). Poll every 1024 nodes (`(nodes & 1023) == 0`). At ~2-4 Mnps that is a check every 0.25-0.5 ms; a `nanoTime` call costs only tens of nanoseconds, so poll even more often if node rates are lower. Also poll inside VCF nodes, which Rapfi does as well.
- **Soft limit:** do not start iteration d+1 if `elapsed > T_hard / EBF_est` (EBF measured, initially about 2.5-3). Otherwise start it and accept a partial result. Searching the previous best move first (PV/TT move at the root) makes partial results safe: if the first root move finished, any later root move that completed with a better score is a legitimate improvement.
- **Early exit:** a proven win or loss at the root (mate score) stops after one more iteration (Rapfi's `numIterationAfterMate` idea). With a single non-losing root move, return immediately: no benefit from searching, and less risk.
- **Never** allocate, print or log on the hot path. Format debug output after choosing the move, or not at all in submission builds.

## 4.3 Aborting recursion cheaply

- **Option 1:** a `boolean stop` flag. Check it after each child returns: `if (stop) return 0;`. The root ignores results of aborted iterations except fully searched root moves.
- **Option 2 (KG-188305 style):** throw a **preallocated** exception without a stack trace and catch it at the root, e.g. `static final class Abort extends RuntimeException { Abort(){ super(null, null, false, false); } }` (writableStackTrace = false). With **copy-make** state stacks indexed by ply (§1.5) nothing needs unwinding, so this is safe and keeps the hot path free of flag checks. [OWN]

## 4.4 Using the separate `useSpecialMove` call

**[OWN] Protocol design:**
1. `accelerateQuark`: search (column, special) pairs and pick the best pair (c*, s*). Cache `{positionHash, turn, c*, s*, score(c*, normal), score(c*, special)}` in a field, and return c*.
2. `useSpecialMove`: if the cached hash and turn match the current board, you can return s* at once. Better: spend this call's own budget on a **deeper verification of the two siblings** (c*, normal) vs (c*, special), because the column is already committed. The TT is warm from step 1, so the extra 70-80 ms typically buys 1-2 more plies on exactly that decision. If the cache is missing or mismatched (engine restart, different object), run a fresh restricted search over just these two moves.
3. If the special is unavailable (cooldown), return false immediately. Whether the engine calls `useSpecialMove` at all in that case is an open question.
4. **Do not rely on cross-call state for correctness.** Kaggle hosts warned that agents may be "re-set and re-started ... at any point" (KG-126678, Neil Slater/Peter Cnudde exchange). Treat the cache and TT as accelerators only.
5. **Root search for step 1 under the split protocol:** the column decision is made before the special decision. Valuing a column as `max(normal, special)` is correct only if step 2 will make the special choice correctly, and the verification search in step 2 ensures it does.

## 4.5 JIT warm-up and a GC-free design

- **Java speed reference:** Fhourstones on the same i7 975 gives C 10,741 Kpos/s vs Java 6,310 Kpos/s (TROMP-FH table). Tromp: "the Java version is 75% slower than the C one, which I find hard to explain". On another machine the gap was 5,881 (gcc) vs 3,177 (Java). Expect roughly 0.5-0.6× C [OWN, from those rows].
- **Warm-up [OWN]:** HotSpot starts in the interpreter, then C1, then C2 after roughly 10^4 invocations or loop back-edges. The first real call can be several times slower than steady state. If the constructor or an init phase is not time-limited (open question), run a dummy search for a few hundred ms on random positions there. Otherwise make the first call's hard limit more conservative (e.g. 50 ms). Magus on CodinGame pre-allocated 5,000,000 MCTS nodes in the first (longer) turn to avoid allocation later (CG-UTTT #36).
- **GC-free [OWN]:**
  - Allocate TT arrays, per-ply state arrays, move lists (`int[MAX_PLY][24]`), history and killer tables once.
  - No boxing, collections, lambdas, iterators, varargs, `String` or `String.format` in search.
  - Re-use one result object.
  - Verify zero allocation per move in the harness with `com.sun.management.ThreadMXBean.getThreadAllocatedBytes` (test harness only).
  - Keep the TT size well under the unknown heap limit, and allocate it in the constructor so an OutOfMemoryError would surface at start, not mid-game.
- **Code shape [OWN]:** keep hot methods small (the inlining limit is about 325 bytecodes for hot methods), use `final` classes and primitive locals, and avoid megamorphic interface calls in the search. `Long.bitCount` and `Long.numberOfTrailingZeros` are intrinsics. Pons' popcount loop `for(c=0; m; c++) m &= m-1;` (PONS-SRC) is not needed in Java.

---

# 5. Testing

## 5.1 SPRT (CPW-sequential-probability-ratio-test)

- **Hypotheses:** H0 Elo = elo0 vs H1 Elo = elo1, with error rates alpha and beta. Update the LLR after each game and stop at either bound. "Used by virtually all top engines". Give a huge game cap (999999) so the test ends on the LLR, not on a game limit (§Testing Methodology).
- **Bounds table** (§Common Bounds):

  | Usage | Gainer bounds | Non-regression bounds |
  |---|---|---|
  | Stockfish LTC | [0.5, 2.5] | [-1.75, 0.25] |
  | Stockfish STC | [0, 2] | [-1.75, 0.25] |
  | Top 30 engines | [0, 3] | [-3, 1] |
  | Top 200 engines | [0, 5] | [-5, 0] |
  | All other engines | **[0, 10]** | **[-10, 0]** |

  Example command: `-sprt elo0=0 elo1=5 alpha=0.05 beta=0.05`, with `-repeat` (each opening played with both colours) and a balanced opening book (§Performing an SPRT Test). Fast-chess "supports pentanomial statistics, which can make tests finish faster" (§Preparing).
- **Non-regression tests:** use them to *remove* code (e.g. a heuristic that costs complexity) with bounds [-5, 0] (§Non-regression Bounds).

## 5.2 SPSA (CPW-spsa)

Pseudo-code (§Automated Tuning):
```
α = 0.602; γ = 0.101;
for k: a_k = a/(k+1+A)^α;  c_k = c/(k+1)^γ;  Δ_p = ±1 (Rademacher)
       Θ+ = Θ + c_k Δ;  Θ- = Θ - c_k Δ;  Θ += a_k * match(Θ+, Θ-) / (c_k Δ)
```
Stockfish's version plays "a pair of games with Θ+ versus Θ-, the function 'match' returning a ±2 range". The choice of A, a, c, α, γ "is critical". RSPSA (common random numbers, antithetic variates, RPROP) is a refinement (§RSPSA). **[OWN]** SPSA fits search parameters (LMR table factor, aspiration delta, razor margin, special-move ordering penalty) and small sets of eval scalars. The pattern table itself is trained by the learning pipeline (see the n-tuple digest).

## 5.3 [OWN] Self-play harness for the Java engine

- **Same interface as production:** the harness instantiates bots via the real API (`accelerateQuark` then `useSpecialMove`), measures each call like the engine does, and records time-outs as losses. It runs each bot in a **separate JVM process** (ProcessBuilder plus a tiny stdin/stdout adapter) so JIT and GC state are isolated and realistic. A same-JVM mode with fixed node counts is useful for fast deterministic regression runs.
- **Paired openings with colour swap:** generate a pool of K start positions by 4-10 random legal plies (normal moves only, plus a few with early specials). Filter to |eval| below a threshold with a deep offline search, analogous to the "balanced books" in CPW-SPRT §Preparing. Play every opening twice with colours swapped and score the pair, which gives pentanomial outcomes and removes first-move bias. CodinGame scores every pairing this way: "you always play twice: as first player and then as second player" (CG-UTTT #4). Draw-heavy pairs make ranking hard (CG-UTTT #11-#13).
- **Concurrency:** tests at the real 100 ms must not oversubscribe cores (timing noise, §4.1 CG numbers). Use concurrency ≤ physical cores - 1. Run most SPRT tests at fixed nodes or at a scaled time (e.g. 40 ms with proportional limits), then confirm at real time control.
- **Correctness suites, following Pons' methodology** (PONS-02: test sets by game depth × remaining moves; measure accuracy, time and node count):
  1. **Perft-like move-generation counts,** including specials and cooldowns, compared against a naive array implementation (differential testing of win detection, special-move gravity and terminal order).
  2. **Win-in-N tactical suites** (normal-only and special-required), generated by long offline searches or PNS. Track the solve rate within 100 ms.
  3. **Known-results check** on 7x6 connect-4 mode, using the size-generic code path with win length 4 and no specials, against Pons' published benchmark sets and scores (PONS-02) or Tromp's 8-ply database (TROMP-C4).
  4. **Timing distribution:** max, p99 and p99.9 of the call duration over thousands of calls, with GC logging enabled.

---

# 6. Lessons from competitions (CodinGame, Kaggle)

## 6.1 CodinGame Connect 4 (multiplayer, 9 columns; Python minimax depth "9*9*9*9")

- **Pie rule:** the second player may "STEAL" the first move, "so it's good to steal [centre openings]. The strategy for 1st player is then to use the most balanced first move" (CG-C4 #9, jacek).
- **Python minimax:** depth 4 only. C++ is "usually ... a 10x speed increase or more" (CG-C4 #12-#15).
- **darkhorse64 (top bot, MCTS):** "I use full rollouts ... a very fast game engine that plays 2 million games per second". "During rollouts, I avoid losing moves and play winning moves". "I have a solver ... The solver routinely predicts the game outcome 15 to 20 moves before the end" (CG-C4 #17). RoboStac explained this is MCTS-Solver, which backs up proven wins and losses (CG-C4 #19).
- **RoboStac:** an AlphaZero-like bot with "a small dense network" due to CodinGame constraints. The Pons blog "is a good resource for improving a minimax based C4 bot" (CG-C4 #19).
- **[OWN]** The gravity-game lesson that transfers: "avoid losing moves, play winning moves" everywhere (search leaves, rollouts, move generation) is the single highest-value trick. It is the same as Pons' PONS-09 rule.

## 6.2 CodinGame Ultimate Tic-Tac-Toe (100 ms per turn, the best-documented throughput numbers)

- **Throughput needed:** 25k rollouts at turn 2 reached Legend against the Gold boss (Magus #33; confirmed by #38). "The top of legend goes more towards 100k+. You will need C++" (reCurse #76). 300k with heuristics-tuned MCTS (Magus #78). C# around 1.1k-6k rollouts, Python about 300 (#75, #79).
- **Speed techniques:** bitboards, lookup tables for move generation and win checks ("precompute everything"), store small boards as ints, "#pragma" optimisation flags for GCC (#40, #46, #51, #53, #73, #81).
- **Memory:** allocate nodes up front (5,000,000 on the first turn) and keep the tree between turns (Magus #36).
- **Platform:** 1 core; threads are useless; pondering gives about 10% (#48, #52, #65). The source size limit is 100 kB, which limited embedded NN weights (JFB #26). **[OWN]** Check whether our harness has a jar/source size limit before planning a large pattern table.
- **Forced wins in MCTS:** "If there is a winning move I just 'trim' all other children" helped (accorp #93-#95). A win check inside every playout cost 5x performance (Di_Masta #97). **[OWN]** Bitboard immediate-win checks are cheap, so the trade-off favours checking.

## 6.3 Kaggle ConnectX (7x6, connect 4)

- **Top approach** (Taaha Khan, "current score ~1600, rank 1-2", KG-188305):
  - 19-ply opening book (all positions to ply 14; plies 15-19 only losing, tying and close games), built with Pons' solver code, zlib+base64 compressed, about 1 MB.
  - Negamax alpha-beta with two 64-bit bitboards; one main tree "for the scoring in one batch".
  - ID on even depths, exception-based exit, TT with upper/lower/exact bounds.
  - "First Win": accept any proven win and cut off, instead of seeking the fastest.
  - Ordering: centre weights + history + odd-even + forced-move-only.
  - The alpha-beta window narrowed to "looking for only wins when playing first".
  - Later replaced ID by a full 42-ply search with a 10-ply fallback: "returning a move in less than 0.3 seconds in the Kaggle Environment and 0.08 seconds on my local machine".
- **Peter Cnudde** (10th): "just over 300 lines of C. It uses bitboards, alpha/beta with move sorting, and a custom implemented hashmap for the transposition table". "If you can make things fast enough, the code can get much simpler" (KG-188305 comments).
- **Hardware and limits:** "Assume 1 (@400MHz)" CPU. The timeout was bumped to 5 seconds (KG-125322). Submissions were capped at 1 MB early on (KG-124397) and 100 MB later (KG-188305). The current leaderboard top is about 1219 (KG-leaderboard).
- **Cross-call state:** Kaggle may restart agents, so do not rely on state between calls (KG-126678).
- **[OWN] Transferable:** (a) an opening book is feasible only for solved or small games. For 12x8 six-in-a-row with specials, a shallow "book" of deep-searched first replies, computed offline with seconds per position, is plausible: for example ~12 + 144 positions at ply 1-2, stored in under 10 kB. (b) "First win" cut-offs and the forced-move-only rule are free speed.

## 6.4 Reference throughput numbers (for budgeting)

| Source | Engine | Speed | Per 100 ms |
|---|---|---|---|
| PONS-06 | C++ bitboard, centre order, no TT | ~62.7 M pos/s | ~6 M |
| PONS-12 | C++ full solver (TT, threat ordering) | ~12 M pos/s | ~1.2 M |
| TROMP-FH | Fhourstones C (i5 3.2 GHz) / Java (i7 975) | 12.1 M / 6.3 M pos/s | 1.2 M / 0.63 M |
| CG-C4 #17 | MCTS full random-ish rollouts, C++ | 2 M games/s | ~200 k playouts |
| CG-UTTT | MCTS, C++ (Legend) | 25k-300k playouts per 100 ms turn | |

**[OWN] Estimate for us:** two-long boards, a 6-length threat mask (~35 128-bit shifts) and special-move simulation make a node about 3-6x heavier than Pons' 49-bit node, and Java is about 0.6× C++. That suggests **1-3 M nodes/s, i.e. 100-250 k nodes in a 75-85 ms window.** This is a guess to be replaced by measurement (§7.2).

---

# 7. Recommended architecture for our bot [OWN unless cited]

## 7.1 Components in priority order

1. **Rules core (P0):** `Board` with option-A 128-bit relative bitboards (`cur`, `mask`), cooldowns `cd[2]`, turn counter and a `possible` mask. Normal move = Pons `play`. Special = §1.5. Win = §1.3, applied to both colours after specials. Terminal order per bytecode. Copy-make stacks. Size-generic, with a ≤ 64-bit fast path for 7x6 tests.
2. **Test bed (P0):** a naive reference implementation, differential/perft tests, the timing harness (§5.3) and the self-play runner with paired openings plus SPRT. Build it *before* the search; Pons' series shows how far measured iteration takes you (PONS-02 → PONS-12).
3. **Search skeleton (P1):** negamax fail-soft PVS, ID, TT (§2.3), mate-distance pruning, Pons immediate-win/forced-block logic adapted to specials (§3.1), and a hard deadline with abort (§4.2-4.3).
4. **Ordering (P1):**
   - TT move, killers (2 per ply) and history with gravity (indexed `[side][col][special]`; Tromp's "+tried / -1" or the CPW gravity formula).
   - Pons threat-count score (popcount of 6-threats after the move), centre-first ties.
   - Specials last unless tactical (§2.4).
5. **Threat search (P2):** gravity VCF at the horizon (§3.3), plus "razoring into VCF" as a later experiment.
6. **Evaluation (P2, parallel track):** a learned pattern table over 6-cell windows in 4 directions, with parity-aware latent-threat features and cooldown features (see the n-tuple digest), and incremental updates where cheap. Until it exists: `(#playable/latent threats by parity) + (open 6-windows weighted by own-stone count) + centre`.
7. **Special-move protocol (P2):** cache plus a verification search in `useSpecialMove` (§4.4).
8. **Selectivity (P3, each behind SPRT):** aspiration windows; LMR (gentle); extensions (forced reply 0-ply, threat creation +1/2); singular extension; ProbCut (fit a, b, σ first). **No null-move pruning.**
9. **Book (P4):** an offline-searched first 1-2 plies (and pie-rule-like considerations if the engine has any).

## 7.2 What to measure first (before tuning anything)

1. **Engine call timing:** the distribution of real wall time between the engine's start-of-call and our first statement, and after our return. Also GC and JIT pauses on the target JVM and machine. This sets `T_hard`.
2. **Nodes per second of the rules core:** normal make, special make, `hasSix`, threat mask, `possible`. Micro-benchmark each (with care for JIT warm-up and dead-code elimination), then measure full-search NPS.
3. **Effective branching factor and depth reached in 80 ms:** with and without the threat-count ordering, the TT and specials in the move set. This is the Pons-style ablation table, re-done for our game (PONS-04 … PONS-12 format: mean time, mean nodes, K pos/s per test set).
4. **Game statistics from self-play:** game length (does the 1000-turn cap matter?), draw rate, how often specials are played and how often a special is the winning or saving move, and first-player advantage. These decide whether special-move search effort is worth its branching.
5. **TT behaviour:** hit rate and cut-off rate vs size (2^18 … 2^22), and cross-call reuse benefit.
6. **VCF statistics:** fraction of nodes in VCF, average VCF tree size, and the rate of proven wins found per move.
7. **Hash collision sanity:** run with the full 108-bit key stored in debug builds and count 64-bit signature mismatches (expected ~0, CPW-TT §Bits Required).

## 7.3 Per-node pseudo-code skeleton

```java
// [OWN] skeleton; all state in per-ply arrays (copy-make), no allocation
int search(int ply, int depth, int alpha, int beta, boolean pv) {
  if ((++nodes & 1023) == 0 && System.nanoTime() > hardDeadline) throw ABORT;
  // 1. terminal / immediate tactics
  long myWin = playableThreats(ply, ME);                 // normal immediate wins
  if (myWin != 0) return WIN - ply;
  if (cd[ply][ME] == 0 && specialWinExists(ply)) return WIN - ply;   // prefiltered simulation
  if (turn[ply] >= 1000 || topRowFull(ply)) return DRAW;
  alpha = Math.max(alpha, -WIN + ply); beta = Math.min(beta, WIN - ply - 1);
  if (alpha >= beta) return alpha;                       // mate-distance pruning (Rapfi)
  if (depth <= 0) return vcfAttack(ply, alpha, beta, 0); // threat quiescence
  // 2. TT
  long h = hash(ply); int tt = probe(h);
  if (!pv && ttHit && ttDepth >= depth && boundAllowsCut) return ttScore;
  // 3. generate: forced blocks (Pons), else all normal (minus "below opp threat"), + specials if cd==0
  int n = genMoves(ply, moves[ply]);                     // returns 0 if lost (>=2 opp threats, no special)
  if (n == 0) return -WIN + ply + 1;
  scoreAndOrder(ply, n, ttMove);                         // TT, killers, history, threat-count, centre
  // 4. PVS loop with LMR on late quiet normal moves and late specials
  ...
  // 5. store TT (bound by fail-soft best vs original alpha/beta), update killers/history
}
```

---

# 8. Open questions

1. **Cooldown semantics** (bytecode): is the special available at game start? After using it at own turn t, is the next first allowed at own turn t+10 or t+11? Does a failed or blocked special (e.g. a full column) count? This is needed for the TT key and search.
2. **Terminal order:** if a move both fills the top row and creates a line, which wins? Does the 1000-turn cap count plies or "turns" per player? Does a special move that clears the landing column change the "full top row" check?
3. **Call protocol:** is `useSpecialMove` called every turn, even when the special is unavailable? Is the board state passed or must we track it? Can the bot keep state between calls, and are the two calls guaranteed to be on the same object and same turn? Is the opponent's move given to us, or must we diff boards (needed for the cache key)?
4. **Timing details:** what exactly does the 100 ms cover (wall vs CPU time; including JVM safepoints)? Is the constructor or initial setup timed? Is there a total match budget? Does the bot run in the same JVM as the engine (threads allowed? pondering possible during the opponent's call?).
5. **Resources:** heap size (TT sizing), number of cores, JDK version (affects `Math.unsignedMultiplyHigh` (18+) and `Long.compress` (19+)), and the jar/source size limit (affects pattern-table size; CodinGame's limit was 100 kB, CG-UTTT #26).
6. **Size genericity in practice:** which board sizes will actually be played? If W*(H+1) > 128 can occur, the long[] path needs to be fast too.
7. **Strategic weight of the special move:** how often does a special decide the game? This needs self-play statistics (§7.2.4) before investing in special-specific threat logic (opponent special-threat simulation at every node can be expensive).
8. **Zugzwang structure with 6-in-a-row on 8 rows:** do classic odd/even threat rules (KG-188305, MISERE take-even) dominate endgames as in Connect-4? With six-in-a-row, vertical lines need 6 of 8 rows, so horizontal and diagonal threats may matter more. This is an empirical question for the eval.
9. **Even-depth-only ID** (KG-188305) and **aspiration windows:** measure whether they help at about 100-250k nodes per move.
10. **MCTS-Solver as an alternative or complement:** it was very strong in CodinGame Connect 4 (2 M playouts/s, solver 15-20 moves ahead, CG-C4 #17). Worth a timed A/B against alpha-beta once the rules core exists, especially if the eval is weak early on.
