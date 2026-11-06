# Rapfi — technical digest for re-implementation in Quantum Connect

Sources (all in `sources/text/`, read in full; the infrastructure-only parts of the trainer data-pipeline doc were read but are summarised briefly):

| Short name used below | File |
|---|---|
| PAPER | `rapfi-mixnet-gomoku.md` (arXiv 2503.13178v1, Jin, Duan, Hang 2025) |
| README | `rapfi-repo-readme.md`, `rapfi-networks-readme.md` |
| PB | `rapfi-code-patterns-board.md` (pattern.h/.cpp, board.h/.cpp, wincheck.h, movegen.h/.cpp, candarea.h, bitboard.h, core/types.h, core/pos.h) |
| EV | `rapfi-code-eval.md` (eval.h/.cpp, evaluator.h/.cpp, evalconfig, scoretables.h, mixcommon.h, mix10nnue, mix9svqnnue, weightloader.h, classicalpolicy) |
| AB | `rapfi-code-search-ab.md` (search.cpp, searcher.h, searchstack.h, parameter.h, history, movepick, hashtable, timecontrol, searchcommon.h) |
| TUNE | `rapfi-code-tuning.md` (tuner, tunerobjective, featureencoder, tunerexport, tunemap, optimizer, tunerdetail) |
| TR-DOC | `rapfi-trainer-readme-docs.md` (trainer README, model_inputs.md, data_pipeline.md, example YAML configs, easyrun.sh) |
| TR-MODEL | `rapfi-trainer-model-mix.md` (mixnet.py, mixnet_components.py, mixnet_vq.py, linenet.py, patnet.py, linear.py, input.py, head.py, ops, layers) |
| TR-LINE | `rapfi-trainer-line-encoding.md` (line_encoding.py, line_encoding.cpp) |
| TR-EXPORT | `rapfi-trainer-export-tables.md` (export.py, serialization/mixnet.py, mixnet_vq.py) |
| TR-LOSS | `rapfi-trainer-train-loss.md` (train.py, trainer/supervised.py, loss/supervised.py, loss/utils.py, winrate_model.py, test.py) |

Conventions: "source:" = stated by code/paper. **[Inference]** = my own reasoning, not in the sources. Rapfi code is GPL-3.0 (README "Terms of use"); network weights are CC0 (`rapfi-networks-readme.md`). Re-implementing the ideas is fine; copying code into a distributed jar would pull in GPL obligations.

Version note: the code snapshot (fetched 2026-11-02, master) is newer than the paper. The paper's "Mixnet" corresponds to the trainer's `mix9` model (engine evaluator `mix9svq`, the vector-quantised variant); the code also contains a newer `mix10`, and a classical evaluator with a "v8 four-table policy format" (README networks: latest classical model `classical/model260809.bin`).

---

## 1. Line / pattern encoding

Rapfi has **two independent line encodings**: (a) the classical one, used by the board for patterns, move generation, threat detection and the classical evaluator; (b) the Mixnet one, used only by the neural evaluators. Both are computed from per-direction "bit keys".

### 1.1 Board address space and bit keys (PB core/pos.h, board.h)

- `Pos` packs `((y+5)<<5) | (x+5)`: row stride `FULL_BOARD_SIZE = 32`, `BOARD_BOUNDARY = 5` wall cells on every side, so `MAX_BOARD_SIZE = 32 − 2·5 = 22`; `FULL_BOARD_CELL_COUNT = 1024`. Walking off the board lands on WALL cells, so inner loops need no bounds checks.
- `DIRECTION[] = {RIGHT(+1), DOWN(+32), UP_RIGHT(−31), DOWN_RIGHT(+33)}`.
- Cell color encoding (2 bits): `00 = WALL, 01 = WHITE, 10 = BLACK, 11 = EMPTY`. An empty cell has both bits set; placing a stone of color c clears bit c, so the remaining bit marks the color (`Line::Line`, `Board::get`). `Color` enum `BLACK=0, WHITE=1, WALL=2, EMPTY=3` so `~c = c^1` flips both BLACK↔WHITE and WALL↔EMPTY.
- Four bit-key arrays (`Board` members): `bitKey0[32]` (horizontal, one uint64 per row, bit order RIGHT=MSB…LEFT=LSB), `bitKey1[32]` (vertical, per column), `bitKey2[63]` (main diagonal, indexed `x+y`), `bitKey3[63]` (anti-diagonal, indexed `31−x+y`). Each line holds 32 cells × 2 bits = 64 bits. `setBitKey`/`flipBitKey` touch all four keys with one OR/XOR each.
- `getKeyAt<R>(pos, dir)` = `rotr(bitKey[line], 2·(x − L))` where `L = HalfLineLen` — the window centred on pos ends up in the low bits.

### 1.2 Classical line window and dense index (PB pattern.h `PatternConfig`)

- `HalfLineLen<FREESTYLE> = 4`, `HalfLineLen<STANDARD/RENJU> = 5` (freestyle only needs 4 neighbours for five-in-a-row; standard/renju need 5 to detect overlines). Window = `2H+1` cells, centre implicit (always empty at lookup time).
- Raw key layout: left half bits `[0, 2H)`, centre `[2H, 2H+2)`, right half `[2H+2, 4H+2)`.
- **Dense half code** (`detail::denseHalfCode<H, NearAtLowBits>`): walk the half from the centre outward; a WALL ends the "visible run"; any non-wall after a wall is invalid (−1). Cells are ternary digits (empty=0, black=1, white=2), nearest cell = least significant digit. Code = `(3^v − 1)/2 + digits` where v = visible run length (all shorter-run blocks precede). Hence `DenseHalfCnt = (3^(H+1) − 1)/2`: **121** for H=4, **364** for H=5.
- `DenseKeyCnt = DenseHalfCnt²` = **14,641** (freestyle) / **132,496** (standard, renju). Static-asserted in pattern.h.
- `denseKey(key) = HALF_CODE_LO[lo] + HALF_CODE_HI[hi]`; the LO table is pre-multiplied by `DenseHalfCnt` so the index is one add of two table reads. Half tables: `1<<(2H)` entries (256 / 1024), uint16 (uint32 for the H=5 LO table).
- Tables built at static init (`PatternConfig::init` lambda): `PATTERN2x[14641]`, `PATTERN2xStandard[132496]`, `PATTERN2xRenju[132496]` (1 byte each: `Pattern2x` = 4-bit black pattern + 4-bit white pattern), `DEFENCE*[DenseKeyCnt][2]` (uint8 masks), `PCODE[16][16][16][16]` and `PCODERenjuBlack[...]` (uint16 each = 128 KiB each).

### 1.3 Per-direction line pattern classification (PB pattern.cpp `getPattern`, core/types.h `Pattern`)

16 classes (`PATTERN_NB = 16`), ordered weak→strong, computed **per colour from that colour's perspective** (`Line(key, self)` maps cells to SELF/OPPO/EMPT, with the centre treated as SELF):

`DEAD, OL, B1, F1, B2, F2, F2A, F2B, B3, B3S, F3, F3S, B4, B4S, F4, F5`

Dynamic program with a memo table of size `3^(2H+1)` (base-3 line code):
1. `countLine`: `realLen` = contiguous SELF run through the centre; `fullLen` = span until the first OPPO on each side; `[start,end]` = span bounds.
2. `realLen ≥ 6` and rule checks overline (standard, renju-black) → `OL`; `realLen ≥ 5` → `F5`; `fullLen < 5` → `DEAD`.
3. Otherwise, for each EMPT cell i in the span: re-centre the line on i (`shiftLine`; off-line cells become OPPO), put SELF at the centre and classify recursively; count the resulting patterns `patCnt[]`, remember up to 2 five-points.
4. Ladder: ≥2 five-points → `F4` (renju black: two fives <5 apart in one line → `OL`, i.e. forbidden); exactly 1 five-point → block that point with OPPO and reclassify: ≥B3 → `B4S` (chaining four), else `B4`; ≥2 F4-points → `F3S`; 1 → `F3`; a B4S-point → `B3S`; a B4-point → `B3`; F3/F3S-points ≥4 → `F2B`, ≥3 → `F2A`, ≥1 → `F2`; a B3/B3S-point → `B2`; an F2-class point → `F1`; a B2-point → `B1`; else `DEAD`.

`fillPattern2xLUT<R>` enumerates every dense (lo, hi) pair, rebuilds the raw key with `rawHalfFromDense`, and classifies for both colours.

### 1.4 Four-direction aggregation: pattern code and Pattern4 (PB pattern.h `PCodeP4`, pattern.cpp `fillPatternCodeLUT`, `getPattern4`)

- **PatternCode**: the unordered multiset of the 4 directional patterns. Dense code assigned in lexicographic order of sorted 4-tuples; `PCODE_NB = C(16+4−1, 4) = 3876` (EV scoretables.h `combineNumber(PATTERN_NB,4)`). Every ordering of the same multiset shares the entry (5-comparator sorting network).
- **Fused entry**: `uint16 raw` = low 12 bits pcode (`PCODE_BITS = 12`, mask 0xFFF) + high 4 bits `Pattern4`. One load gives both.
- **Pattern4** (14 values, `PATTERN4_NB = 14`): `NONE, FORBID, L_FLEX2, K_BLOCK3, J_FLEX2_2X, I_BLOCK3_PLUS, H_FLEX3, G_FLEX3_PLUS, F_FLEX3_2X, E_BLOCK4, D_BLOCK4_PLUS, C_BLOCK4_FLEX3, B_FLEX4, A_FIVE`. `getPattern4` first folds B4S→B4 and B3S→B3, then: any F5 → A_FIVE; (renju forbid: OL, ≥2 fours, ≥2 F3 → FORBID); ≥2 B4 or any F4 → B_FLEX4; B4+F3 → C; B4+(B3 or F2-class) → D; B4 → E; 2×F3 → F; F3+(B3 or F2) → G; F3 → H; 2×B3 or B3+F2 → I; ≥2 F2-class → J; B3 → K; F2-class → L; else NONE.
- Board storage per cell: `Pattern2x pattern2xs[1024][4]` (4 KB, AoS so one 32-bit load gets the 4 directions), `uint8 pattern4x2[1024]` (black nibble low, white high).

### 1.5 Incremental update per move (PB board.cpp `Board::move`, `Board::undo`)

Per ply a `StateInfo` copy (candidate-journal anchor, candidate bounding box, last move, `lastFlex4AttackMove[2]`, `lastPattern4Move[2][3]` for C/B/A classes, `p4Count[2][14]` histogram of empty cells per Pattern4, `valueBlack`) plus a `MoveRestore` record.

`move(pos)`:
1. Evaluator `beforeMove` hook; Zobrist XOR; `flipBitKey`; clear empty bit.
2. Pre-rotate the 4 bit keys to the i = −L cell: `rotr(bitKey, 2·(x − 2L))`. Walk `i = −L … +L` (skip 0), shifting each key right by 2 bits per step (4 bits across the centre).
3. For each direction, if the cell is empty (code 11 at bit offset 2L): `newP2x = lookupPattern(key)`. If unchanged, skip (only refresh `lastPattern4`). Otherwise save `{old Pattern2x, old pattern4 pair}` into `restore.cells[restoreIdx++]`, set bit `stepIdx*4+dir` in a 64-bit `touchedMask` (max 40 restore cells: 2·5 cells × 4 dirs).
4. New pcode: PCODE rows are order-independent, so index with the 3 unchanged directions first and the changed one last — old and new entries are two loads from the same 16-entry row. `deltaValueBlack += EVALS[new] − EVALS[old]`; update `p4Count` and `pattern4x2`.
5. After the walk: subtract the placed cell's own value and p4 contribution (its patterns stay **frozen** at their empty-cell values: "freeze invariant", used by forbidden-point checks and defence reconstruction).
6. Mark candidate neighbourhood (stencil OR into `candidatesBB`, 0→1 transitions journaled for undo); record `lastFlex4AttackMove` on 0→nonzero B_FLEX4 transitions; evaluator `afterMove` hook.

`undo()`: rewind candidate journal by XOR, flip bit key back, then restore exactly the cells listed in `touchedMask` (slot → step, dir → position) — no table lookups. Move types `NORMAL / NO_EVALUATOR / NO_EVAL / NO_EVAL_MULTI` let probes skip evaluation work.

Memory (classical, per board): pattern arrays 5 KB, bit keys ~1.3 KB, `stateInfos` and `moveRestores` sized `1 + 2·cells`, candidate journal 1024 entries. Global tables: freestyle 14.6 KB + 29 KB defence; standard/renju 132 KB + 265 KB each; 2×128 KB PCODE.

### 1.6 Defence table (PB pattern.cpp `fillDefenceLUT`)

For each dense key and attacker, if the attacker's pattern ≥ F3: for each empty cell in the window, put a defender stone (reads as a wall to the attacker) and check whether the attacker's pattern drops below F3; such cells form an 8-bit mask, centred (`(mask >> (H−4)) & 0xff`, outermost cells are never defences). Used by `findF3LineDefence` to list exact defences against a flex three.

### 1.7 Mixnet line encoding (TR-LINE line_encoding.cpp `LineEncodingEncoder`; EV mixcommon.h `initIndexTable`, mix10nnue `Accumulator::move`)

- Line length `L = 11` (paper §3.2 eq. 1: `L^{(m,n)}_{i,j} = {s(i+mk, j+nk) | −5 ≤ k ≤ 5}`, "enabling it to capture the connection features of the surrounding five stones"). **Includes the centre cell** (unlike the classical pattern, features exist for occupied cells too).
- Per-cell digits: empty 0, self 1, oppo 2 (`state==0b01 → 1, 0b10 → 2`), digit of key index k weighted by `3^(L−1−k)`. Wall handling: a "border encoding" offset added in front, depending on (left, right) distances to the wall, each in [0, L/2]: no walls → 0; only left wall → `2·3^L + …`; right wall → `1·3^L + …` (exact digit construction in `get_boarder_encoding`, identical in engine `get_border_encoding`). Cells beyond a wall contribute 0.
- Total code space `max_encoding(L) + 1` with `max_encoding(L) = 2·3^L + Σ_{i=0..L/2} 2·3^i + Σ_{i=L/2+2..L−1} 3^i` → **442,503** for L=11 (`mixnet::ShapeNum = 442503`; `NUM_LINE_ENCODINGS` in mixnet_vq.py). Not all slots are reachable; `get_encoding_usage_flags` marks the reachable ones. The paper counts the reachable ones as `N = Σ_{i=0..5}Σ_{j=0..5} 3^{i+1+j} = 397,488` (§3.3).
- Fast encoder: 4 chunk LUTs of 4096 entries (6 cells × 2 bits per chunk) + `boarder_encodings[10][10]`. Max encodable length 17 compressed, 15 raw.
- Engine incremental rule (mix10nnue `Accumulator::move`): a stone of colour c at distance d ∈ [−5, 5] from a cell, in direction dir, changes that cell's shape code by `(c+1)·3^(d+5)`. So **a single stone changes ≤ 4 × 11 = 44 directional codes** (paper §3.4: "at most 4×11 directional features are affected"). The per-cell initial (empty board) codes come from `initIndexTable()` using the border distances `min(x, 5)` etc. per direction.
- Rapfi evaluates from both perspectives: the WHITE accumulator sees a colour-flipped board (`applyMoveCache` maps BLACK↔WHITE), so "self" is always the side to move of that accumulator.

---

## 2. Classical evaluator

### 2.1 Tables (EV scoretables.h)

- `Eval EVALS[RULE_NB+1][PCODE_NB]` (int16, 4 tables: freestyle, standard, renju-black, renju-white; `tableIndex(r, c) = r + (r==RENJU ? c : 0)`).
- `Eval EVALS_THREAT[RULE_NB+1][THREAT_NB]`, `THREAT_NB = 2^11 = 2048`.
- `MoveScorePair P4SCORES[RULE_NB+1][PCODE_NB]` = `{Score byOwner, Score byOpponent}` (int16 each) for move ordering.
- `POLICY_CROSS[RULE_NB+1][6 contexts][14][14]` (classicalpolicy.h) residual move scores.

### 2.2 Value composition (EV eval.cpp `evaluate<R>`, `makeThreatMask`, `composeClassicalValue`)

1. **Basic** (incremental, see §1.5): `valueBlack = Σ_{empty cells} EVALS[t(B)][pcodeBlack] − EVALS[t(W)][pcodeWhite]` (`getValueBlack`); `evaluateBasic(st, self) = ±valueBlack`.
2. **Threat**: 11-bit mask from presence of Pattern4 counts:
   bit0 oppo A_FIVE, bit1 self B_FLEX4, bit2 oppo B_FLEX4, bit3 self (D or C), bit4 self E_BLOCK4, bit5 self (G or F), bit6 self H_FLEX3, bit7 oppo (D or C), bit8 oppo E, bit9 oppo (G or F), bit10 oppo H. Value = `EVALS_THREAT[t(R,self)][mask]`.
3. **Blend** (int64 sum then `/256`): `eval = (wCur·basic(st0) + wPrev·basic(st1) + wThreat·threat(st0)) / 256`, where `st1` is the previous ply's state evaluated from the *current* side to move (smooths the odd/even oscillation). `ClassicalValueBlendWeights` = freestyle `{149,106,267}`, standard `{155,116,268}`, renju-black `{137,122,263}`, renju-white `{138,125,261}`.
4. Clamp to `[VALUE_EVAL_MIN, VALUE_EVAL_MAX] = [−6000, 6000]`; optional **readout**: odd monotone piecewise-linear map on `|v|/ScalingFactor` with x-knots `{0, 0.5, 1, 2, 4, 8}`, y-knots from config, slope 1 beyond 8 (`refreshClassicalValueReadoutCaches`, cached for every integer value).
5. Value ↔ win rate: `winRate = 1/(1 + exp(−v/ScalingFactor))`, `ScalingFactor = 200` (`valueToWinRate`, `winRateToValue`). Mate encoding `VALUE_MATE = 30000`, `mate_in(ply) = 30000 − ply`, TT stores mate scores relative to the node (`searchValueToStoredValue`).

### 2.3 Lazy switching to the neural evaluator (EV eval.cpp `evaluate<R>`, `computeClassicalEvalMargin`)

If an NN evaluator is loaded, the classical eval is computed first; the NN is called **only if** `classicalEval ∈ [alpha − margin, beta + margin]`, with
`margin = marginScale · exp(−(x²)^marginWinLossExponent)`, `x = marginWinLossScale · 2·(winRate(classicalEval) − 0.5)`.
Defaults (evalconfig.h): `marginWinLossScale {1.22, 0.86, 1.24}`, `marginWinLossExponent {3.04, 3.44, 2.56}`, `marginScale {351.0, 379.2, 389.4}` (freestyle, standard, renju). The margin is tabulated for every integer eval. NN value → `ValueType(win, loss, draw)` softmax → `value = winRateToValue((win − loss + 1)/2)`; optional draw-ratio adjustment (`drawRatio`, `drawBlackWinRate`).

### 2.4 Classical move scores (policy) (EV classicalpolicy.h, PB board.cpp `Board::score`)

`base = (wSA·selfAttack + wSD·selfDefense + wOA·opponentAttack + wOD·opponentDefense) / 256` where for side `self` at a cell: `selfAttack = P4SCORES[self][pcode_self].byOwner`, `selfDefense = P4SCORES[oppo][pcode_oppo].byOpponent`, `opponentAttack = P4SCORES[oppo][pcode_oppo].byOwner`, `opponentDefense = P4SCORES[self][pcode_self].byOpponent`. Weights `ClassicalPolicyBlendWeights` freestyle `{235, 264, −25, 121}`, standard `{292, 265, 14, 16}`, renju-B `{271, 247, 17, 6}`, renju-W `{289, 272, 16, 40}`. Plus `POLICY_CROSS[context][selfP4][oppoP4]` where the context (`classifyPolicyContext`) is one of OPPONENT_FLEX_FOUR, OPPONENT_BLOCK4_FLEX3, MUTUAL_RACE, SELF_ATTACKING, OPPONENT_ATTACKING, QUIET (QUIET row not stored). Result saturates to int16.

### 2.5 How the classical tables are tuned (TUNE)

- **Parameters** (`Tuner::initParams`): every entry of `EVALS[r]` and `EVALS_THREAT[r]` (value objective) and of `P4SCORES[r]` (policy objective) for the selected rules. Move scores are stored as `param` with `score = param·moveScoreScale + moveScoreBias` (defaults 24, 24), clamped to `[−999, 999]`; in "Current" semantics equal scores are **tied** into one parameter (`addTiedMoveScoreParams`).
- **Model form**: the evaluation is linear in the parameters: each prepared sample stores a sparse list of `(index, coeff)` terms with `CoeffScale = 256` (`computeLinearEval = Σ coeff·param / 256`). The sample-compilation code (`appendTuneSample`) is not in the provided sources; `EvalInfo` (eval.h) holds `pcodeCount[side][3876]` for the current and previous ply plus the threat mask, which is consistent with terms = blend weight × pcode counts + threat entry. **[Inference]** exact coefficients are the §2.2 blend.
- **Value loss** (`lossFunction`): BCE of `sigmoid(K·eval)` against game result (`result/2` ∈ {0, 0.5, 1}), computed stably, minus the target entropy, halved. Gradient `(sigmoid(K·eval) − target)/2 · K · coeff`.
- **K (inverse scaling factor)** (`searchOptimalInvScalingFactor`): grid search over K ∈ [1/100, 1/400] (`scalingFactorMin 100`, `Max 400`), `nIterations = 10`, `nStepsPerIteration = 10`, each iteration re-centres around the best K with a step shrunk by `2/nSteps`. Optional recalibration every `recomputeInterval` epochs.
- **Policy loss** (`tunerobjective.cpp computeMoveScoreLoss`): softmax cross-entropy over the candidate moves of the position; each candidate's logit = `(param[i0] + param[i1]) · logitFactor`, `logitFactor = moveScoreScale / 32` (Current) or 1 (Bootstrap). Target = one-hot best move, or soft multi-PV target: `softmax(winRate(eval_i)/T)` over the best move and extra PVs (`featureencoder.cpp encodePolicyTarget`, needs `multiPVPolicyTemperature > 0`).
- **Optimiser**: Adam, β1 0.9, β2 0.999, ε 1e−8, decoupled weight decay inside the update (`p −= lr·(m̂/(√v̂+ε) + wd·p)`), defaults `lr = 0.01`, `weightDecay 0`, schedule constant or exponential `lr0·(lrEnd/lr0)^progress` (`scheduledLearningRate`). `batchSize = 8192`, last partial batch dropped; gradients averaged over the batch, computed in 64 logical partitions.
- **Data**: positions with `(board, rule, result, bestMove, bestEval, payload)` (payload = dense policy array, or extra PVs). Filters: `minPly 1`, `minPlyBeforeFull 50`, board size 5…22, per-rule selection; optional equal weighting per board size (`BoardSizeWeighting::EqualBoard`, weight = total/(activeBoards·count)). Curriculum phases with separate corpora, objectives and LR are supported. The dataset generator itself is not in the sources.
- **Export**: floats are quantised into the live int16 tables (`installParams`); optional projection rescales quiet-move score dispersion (IQR around the median) to a reference (`projectMoveScoreScale`).
- **Search hyper-parameters** are tuned separately with SPSA via a `TUNE()` macro registry (`tunemap.h`; PAPER A.4: "parameter tuning via the Simultaneous Perturbation Stochastic Approximation (SPSA) algorithm").
- The trainer also has PyTorch equivalents of the classical table: `LinearModel` (TR-MODEL linear.py) = embedding `pcode → (policy, win, draw)` summed over empty cells, and `LinearThreatModel` adds a 2048-entry threat-mask table — i.e. the classical evaluator expressed as a trainable embedding-sum model (older `pcode_dim = 2380` for 14 patterns).

---

## 3. Mixnet

### 3.1 Architecture (PAPER §3; TR-MODEL mixnet.py `Mix9Net`, mixnet_components.py `Mapping`, `QuantizedHeadMixin`)

Input: `x ∈ {0,1}^{2×H×W}` (own stones, opponent stones; input type `basicns`, no side-to-move plane).

**Mapping network** (`Mapping`, one per direction family — `mapping1` for horizontal+vertical, `mapping2` for the two diagonals; paper: `M_hv`, `M_di`):
1. `DirectionalConvLayer(2 → M)`: one learned 3-tap kernel applied along the given direction (weights shape `(M, in, 3)`, zero padding, so off-board = zero activations), SiLU.
2. `(line_length//2 − 1) = 4` × `DirectionalConvResBlock(M)`: dir-conv 3-tap → SiLU → 1×1 conv → SiLU → +residual.
3. `Conv0dResBlock(M)`: 1×1 → SiLU → 1×1 → SiLU → +residual.
4. `final_conv`: 1×1 `M → C`.
5 directional 3-tap layers ⇒ receptive field exactly 11 cells along the line (paper §3.3), so the output at a cell is a pure function of its 11-cell line code ⇒ exportable losslessly to a table.

**Feature aggregation** (`Mix9Net.get_feature`):
`f = clamp(mapping, −16, 511/32)` → fake-quant int16 scale 32 → **mean over 4 directions** (= int16 sum at scale 128) → ReLU (paper eq. 2: `f = ReLU(M_hv(L^h)+M_hv(L^v)+M_di(L^d1)+M_di(L^d2))`).
Depth-wise 3×3 conv on the first `D = dim_dwconv` channels (input ×4, weights int16 scale 65536 ⇒ range ±0.5, bias scale 128, ReLU, `quant="pixel-dwconv-floor"`), remaining `C − D` channels pass through (paper eq. 4: dwconv on the first half). Output feature map `F'` int16 scale 128.

**Heads** (`QuantizedHeadMixin`):
- Global sum `Σ_{cells} F'` → `/256` floor (srai 8), int32 scale 128.
- **Policy (dynamic point-wise conv)**: `MLP(global) = Linear(C → 2P) ReLU → Linear(2P → 16·P + 16)` gives a per-position 16×P weight matrix (int16 scale 128²) and 16 biases (int32 scale 128³); `policy = Conv1x1(16 → 1, float)( ReLU(W_dyn · F'[:P] + b_dyn) )`. `policy_middle_dim = 16`.
- **Value (value grouping + star blocks)**: split board into 3×3 regions (`split_3x3_regions`: row cut points `h1 = H//3 + (H%3==2)`, `h2 = 2·(H//3) + (H%3>0)`, same for W); region sums `/32` floor (srai 5). Each region → StarBlock (shared weights by symmetry class: `value_corner` ×4, `value_edge` ×4, `value_center` ×1) → `V`; 2×2 quadrants = quantised average of the 4 adjacent regions (`quantized_avg4`, paper eq. 5); `value_quad` StarBlock on each quadrant; concat `[global (C), 4×V]` → `Linear(C+4V → V) ReLU → Linear(V → V) ReLU → Linear(V → 3)` = win/loss/draw logits.
- **StarBlock** (layers/star.py): `x1 = ReLU(Linear(in → 2·out))`, `x2 = Linear(in → 2·out)` (no act), both int8 scale 128; `x[i] = x1[2i]·x2[2i] + x1[2i+1]·x2[2i+1]` (pairwise dot, "star operation", Ma et al. 2024), int8; `down = ReLU(Linear(out → out))`.
- Ablation flags: `no_star_block` (StarBlock → single Linear), `no_value_group` (only global sum → 3-layer MLP), `no_dynamic_pwconv` (fixed 1×1 conv P→16), `one_mapping`.

Sizes (PAPER §4.2): Small `M=64, C=32, P=16, V=32`; Medium and Large double the channels each step. Medium = the example config `mix9: dim_middle 128, dim_feature 64, dim_policy 32, dim_value 64, dim_dwconv 32` (TR-DOC mix9net-f15.yaml). Constraints: `dim_policy ≤ dim_dwconv ≤ dim_feature`, all multiples of 8 (serializer asserts).

**mix10** (newer, TR-MODEL `Mix10Net`, engine `mix10nnue.cpp`): `C=64, D=32, V=64` (engine constants `FeatureDim 64, FeatDWConvDim 32, ValueDim 64`), two value heads and two policy heads:
- small value: `Linear(C→V) ReLU → Linear(V→V) ReLU` = shared small feature → `Linear(V→3)`.
- large value: `gate = Linear(V → 2C)` from the small feature (no act); each region sum `/32` int8 duplicated and dotted pairwise with the gate (multiplicative modulation); corner/edge/center plain Linear `C→V`; avg4 quadrants; `value_quad Linear(V→V)`; concat `[small feature, 4 quads]` (5V) → `Linear(5V→V) → Linear(V→V)` = large feature → `Linear(V→3)`.
- policy small: dynamic pwconv `max(D/2,16)=16 in → max(D/4,16)=16 out`; policy large: two dynamic layers `32 → 16 → 16` generated from the large feature (`policy_large_pwconv_weight_0/1/2`).
- Training adds aux losses on the small heads (`value_small`, `policy_small`, `policy_small_reg`). The engine uses only the large heads (`evaluateValueLarge`, `evaluatePolicyLarge`); `AccLevel` is plumbing for future small/large switching.

### 3.2 Quantisation (PAPER A.3; trainer fake-quant, engine integer pipeline)

- PAPER: codebook features clamped to [−16, 16], int16 with scale 32; aggregation and dwconv in int16, final feature-map sums accumulated in int32; policy dynamic conv in int16 matmul, final point-wise conv in float; value head and the linear layers that generate dynamic weights use int8 weights with int32 accumulation. AVX2 SIMD gives "roughly 4x" over non-vectorised code.
- Trainer QAT: `fake_quant` at every stage with the same scales and floor rounding (comments in `Mix9Net.get_feature/forward`: "int16, scale=32, [-16,16]", "srai 8", "srai 5"); `LinearBlock(quant=True)`: input int8 scale 128 (floor), weight int8 scale 128, bias int32 scale 128·128. Weight clipping after each step: linear weights `[−1, 127/128]`, dwconv weights `[−32768/65536, 32767/65536]`, mix8 PReLU `[−1,1]` (`weight_clipping` properties).
- Export (TR-EXPORT `Mix9NetSerializer`): mapping features `round(clip(f, −16, 511/32)·32)` → int16 in [−512, 511] → **10-bit packed** into a little-endian uint64 stream (`write_feature_map_compressed`); dwconv weights `·65536` (int16), bias `·128` clipped to ±64, overflow check `Σ|w|/2·16·4·128 < 32767`; linear weights `round(w·128)` int8, biases `round(b·128·128)` int32; policy output float `w/128³`. File = LZ4 frame with header `{magic 0xacd8cc6a = crc32("gomoku network weight version 1"), arch_hash, rule_mask, boardsize_mask, desc_len, desc}` (TR-DOC README "Serialized Weight Format").

### 3.3 Distilling the mapping network into the codebook (PAPER §3.3; TR-EXPORT `_export_feature_map`)

For each line length `l = 11, 10, …, 1`: generate all `3^l` base-3 lines (`generate_base3_permutation`), feed them as a `[1, 2, N, l]` plane (one row of length l, so zero padding = board edge) through `mapping{1,2}(…, dirs=[0])`, read the feature at every position `0..l−1`, compute its line encoding with `transform_lines_to_line_encoding(lines, 11)` and store into `feature_map[442503, C]`. Positions near the ends of short lines produce the wall-bordered codes, so every reachable code gets exactly its network output: the export is **lossless** (up to the int16 rounding). Unreachable codes stay 0. The paper describes the same: "enumerating all N patterns as a plane of shape 2×1×(i+1+j) … recording the feature outputs at (1, i+1)".

**VQ variants** (TR-MODEL mixnet_vq.py, TR-EXPORT mixnet_vq.py, engine `mix9svq`): to shrink the table, line features are vector-quantised during training (`ProductVectorQuantize`, codebook 16384 or 65536 entries, k-means init, EMA codebook update, straight-through/rotation trick, `vq_lambda 1.0`, computed on de-duplicated unique line codes per batch). Export: `codebook[2][K][C]` (10-bit packed) + `mapping_index[2][442503]` uint16. Engine `mix9svq::Weight`: `int16 codebook[2][65536][64]` + `uint16 mapping_index[2][442503]`. `mix10vq` example config: `dim_middle 192, dim_feature 96, D 32, V 64, codebook_size 16384, num_codebooks 1, kmeans_init, ema_update`, initialised from a trained mix10 checkpoint (`load_from`). A further variant, `mix10vq_dictionary`, learns centroids directly with a fixed assignment table (no mapping network).

### 3.4 Incremental update at inference (PAPER §3.4; EV mixcommon.h `MixAccumulatorState`, mix10nnue.cpp `Accumulator`)

Per accumulator (one per perspective):
- `indexTable[row][4]` = shape code per direction; `mapSum[row][C]` int16 = Σ over 4 directions of `mapping[dir/2][shape]`; `mapConv[row][D]` int16 = dwconv output over an **outer board (H+2)×(W+2)** (so the 3×3 conv needs no edge checks), initialised to the dwconv bias; `valueSumTable[version]` = int32 `global[C]` and `group[3][3][C]` sums, plus cached head features.
- **Versioned copy-on-write**: each `move()` appends changed rows to `mapSum`/`mapConv` and writes a new per-version index table (`versionInnerIndexTable[v·H·W + cell]`, `versionOuterIndexTable[...]`, `versionChangeNumTable[v]`); **undo is `currentVersion--`**. Capacity bounded by `MaxInnerChanges[size]`, `MaxOuterChanges[size]` (e.g. size 15: 6,521 / 19,857 rows). Board size ≤ 22 (`assert`).
- `move(color, x, y)`:
  1. For dir ∈ 4, dist ∈ [−5, 5] in-board: `newShape = oldShape + (color+1)·3^(dist+5)` → up to 44 `OnePointChange`s.
  2. For each change: `mapSum_new = mapSum_old − mapping[old] + mapping[new]` (prefetch next rows); `old/new = ReLU(…)`; for the first D channels, ×4 and add `mulhi(convW, new) − mulhi(convW, old)` into the 9 `mapConv` cells around; for channels D..C, add `new − old` to `global` and to the cell's `group[gy][gx]` (int32).
  3. For the window of `mapConv` cells around the move (`x−5 … x+7`, `y−5 … y+7` in outer coordinates), add the ReLU delta of mapConv to global/group sums.
  4. Bump version; mark head caches invalid.
- **Lazy move cache** (`queueMoveCache`/`applyMoveCache`): board move/undo hooks only enqueue `{oldColor, newColor, x, y}`; a move followed by its own undo cancels out; the accumulator is synced only when `evaluateValue/evaluatePolicy` is called (and only for the side to move). With the lazy classical/NN switch (§2.3) many nodes never touch the accumulator.
- Head evaluation: mix9svq value = crelu(global, /256) → group crelu /32 → 9 star blocks → avg4 → 4 quad star blocks → 3 linear layers → `/(128·128)` floats → softmax. Policy: computed only for cells with `computeFlag` set (the generated moves): `ReLU(W_dyn·mapConv[cell][0:32] + b)` (int32) → float dot with `policy_output_weight[16]` + bias.

### 3.5 Cost numbers (PAPER Table 1)

| Model | CB params (k) | FF params (k) | kFLOPs (forward) | Storage (MiB) | MCTS playouts/s | α-β nodes/s |
|---|---|---|---|---|---|---|
| Mixnet small | 14,160 | 37 | 51 | 28.4 | 47,575 | 428K |
| Mixnet medium | 28,320 | 146 | 131 | 54.7 | 36,823 | 257K |
| Mixnet large | 56,640 | 580 | 381 | 111 | 18,401 | 104K |
| Resnet 4b64f | 0 | 298 | 67,089 | 1.13 | 1,064 | 1,758 |
| Resnet 6b96f | 0 | 1,001 | 225,396 | 3.81 | 297 | 484 |
| Resnet 10b128f | 0 | 2,960 | 666,403 | 11.2 | 95 | 163 |
| Resnet 15b192f | 0 | 9,976 | 2,245,493 | 38.0 | 28 | 51 |
| Resnet 20b256f | 0 | 23,631 | 5,318,727 | 90.0 | 11 | 32 |

(CB params small = 442,503 × 32. Hardware: CPU only, AVX2; exact CPU and thread count not stated.) Engine table memory: mix10 `int16 mapping[2][442503][64]` = 113 MB in RAM; mix9svq `codebook[2][65536][64]` (16 MB) + indices (1.8 MB).

### 3.6 How the policy drives move ordering and pruning (AB movepick.cpp `scoreAllMoves`, search.cpp step 12/15)

- If an evaluator exists, `MovePicker` sets compute flags for the generated moves only, calls `evaluatePolicy`, and uses `score = policy·32 (+bias)` (`PolicyBuffer::ScoreScale = 32`) instead of the classical score; then adds main-history and counter-move bonuses (§5.6) and partial-sorts.
- `hasPolicy` enables **policy-based LMR** (`policyReduction`: `r += clamp(bias − scale·score·0.1/32, 0, max)`, freestyle bias 2.53, scale 3.60, max 5.18; "~59 elo") and **policy pruning** at shallow depth (`score < 372 − 48·depth` pruned, freestyle; "~10 elo").
- Root can use normalised (softmax) policy; QVCF move ordering is always classical.

---

## 4. Training pipeline

### 4.1 Data

- PAPER §4.1: dataset generated by **Katagomo** (KataGo fork for Gomoku) in an AlphaZero-like self-play process "over several weeks", **≈30.8 M positions**, each `(B, V_t, π_t)`: `B ∈ {0,1}^{2×H×W}` (current player's stones, opponent's), `V_t = (p_w, p_l, p_d)` value target, `π_t` normalised policy target. A.2: both targets "derived from the self-play data generated by KataGo".
- Trainer accepts KataGo npz (raw or processed with keys `bf, gf, vt, pt`) and `.binpack(.lz4)`/`.bin` produced by **c-gomoku-cli sampling mode** (Rapfi self-play/match tool) (TR-DOC README, configs). How many positions were used for the released Rapfi nets is not stated.
- Rapfi's own feature encoder for exported training data (TUNE featureencoder.cpp `encodeEntryFeatures`): packed bit planes `[in-board, self, opponent]`, optional sparse inputs (8 line patterns, 2 Pattern4, 2 pattern codes per cell), global input = side to move (black −1, white +1), value target = one-hot WLD from the game result or a soft (W, L, D) override, policy target uint16-quantised (dense, multi-PV softmax, or one-hot). `WinrateModel` (TR-LOSS utils/winrate_model.py): `eval_to_wld`: `win = sigmoid(eval/200)`, `loss = sigmoid(−eval/200)`, `draw = max(1 − win − loss, 0)`, mate threshold 29000.

### 4.2 Targets and loss (PAPER A.2; TR-LOSS loss/supervised.py)

- PAPER: `L = L_p + L_v + λ Σ w_i²` with `L_p = −Σ_m π(m) log π̂(m)` over H×W moves and `L_v = −Σ_{n=1..3} V_t(n) log V̂(n)`.
- Code: `loss_type` "V+P" with V, P ∈ {KL, CE, MSE} (default `KL+KL`; soft-label CE/KL on the 3 WLD logits and on the flattened policy logits). Weights `value_lambda = 2r/(r+1)`, `policy_lambda = 2/(r+1)` with `r = value_policy_ratio` (default 1; mix10vq example 4). Policy regulariser `policy_reg_lambda · (mean policy logit)²` (0.001 in all example configs). Optional focal γ, forbidden-point masking, masked boards.
- **Knowledge distillation**: PAPER: teacher ResNet-6b128f trained to convergence on the same data; loss = 75 % distillation labels + 25 % true labels. Code: `total = α·kd_loss·T² + (1−α)·real_loss` (`compute_supervised_losses`), teacher logits softened by `softmax(t/T)`, student `/T`; example configs `kd_alpha 0.8, kd_T 1.0`, teacher `resnet, num_blocks 10, dim_feature 128, head v0, input basic-nostm`.
- Teacher ResNet (PAPER A.1): 3×3 conv (2 → f) + ReLU, b residual blocks (two 3×3 f×f convs + ReLU, skip), value path avgpool → linear → 3, policy path 1×1 conv; inference through ONNX Runtime.

### 4.3 Optimiser, schedule, batch, augmentation (PAPER §4.2; TR-DOC configs; TR-LOSS train.py)

| Item | Paper | Trainer example configs |
|---|---|---|
| Optimiser | Adam lr 0.001, β (0.9, 0.999), ε 1e−8 | `optim_type adamw` (train.py default), lr 0.001, `weight_decay 1e−7` (train.py default 1e−2), `clip_grad_norm 1.0` |
| Batch / iterations | 128 × 600 k | mix9: 2048 × 2,000,000; mix10vq: 8192 × 2,000,000 |
| LR schedule | — | `step`: ×0.9 every 50,000 iterations |
| EMA | — | decay 0.9999 saved alongside |
| Precision / compile | — | `easyrun.sh`: bf16 mixed precision, torch.compile inductor `max-autotune` |
| Augmentation | — | `apply_symmetry: true` (dihedral; only 4 rectangle-preserving transforms when H≠W, `isRectangleTransform`), shuffle reservoir 32,768–262,144 |
| QAT | implied by A.3 | fake-quant throughout model forward (§3.2) |
| Multi-size | — | `iterative_multi` balanced mixing, `batch_by_boardsize: true` (f15 + f20 example) |

GPU hours: **not reported** anywhere in the sources.

### 4.4 Export and deployment

`export.py --export_type bin-lz4 --export_args "{rule: freestyle, board_size: 15}"` → serializer per model type (§3.2–3.3). Rules and board sizes are stamped into the header; the engine rejects mismatching arch hash, rule or board size (EV `loadWeightPair`). Separate black/white weight files are supported (`weight_file_black/white`; example `mix9net-r15-black.yaml` trains with `filter_stm: -1`).

---

## 5. Search (α-β, AB files)

### 5.1 Driver

- Iterative deepening `rootDepth = startDepth … maxDepth` (`ABSearcher::search`); Lazy-SMP threads skip depths that more than half the other threads are already searching (`pickNextDepth`); final move chosen by voting `(value − minValue + 15)·completedDepth` with mate preference (`pickBestThread`).
- Aspiration windows from depth `ASPIRATION_DEPTH = 5.3`: initial δ = 20/10/11 (freestyle/standard/renju), on fail `δ' = δ·(3 + |v|/1024)/4 + {8,3,8}`; fail-low lowers alpha and sets `beta = (alpha+beta)/2`; repeated fail-highs reduce depth by `failHighCnt/3`.
- Depth is a **float** (fractional extensions/reductions). `DEPTH_QVCF_FULL = −1`, `DEPTH_QVCF = −2`. `MAX_PLY 256`, `MAX_DEPTH 200`.
- Stops early after `numIterationAfterMate` iterations once a mate is found, or `numIterationAfterSingularRoot` if only one root move does not lose.

### 5.2 Node procedure `search<Rule, NT>` (search.cpp, step numbers as in code; Elo figures are the code comments)

1. `depth ≤ 0` → `vcfsearch` (or `vcfdefend` if the opponent has a five-threat) (~17 elo).
2. Draw if board full or `maxMoves` reached; `quickWinCheck` (§5.4); mate-distance pruning.
4. TT probe (key XOR `LCHash(skipMove)` inside singular searches); non-PV cutoff if `ttDepth ≥ depth` and bound matches.
5. Optional opening database probe.
6. Static eval; if the opponent just made a four/five threat, reuse `−(ss−1)->staticEval` and (five) jump to the move loop.
7. **Razoring with VCF** (~68 elo): non-PV, `depth < 2.6`, `eval + max(0.17d² + 15d + 11, 0) < alpha` → return `vcfsearch(alpha, alpha+1)` (freestyle constants).
8. **Futility (child)** (~121 elo): non-PV, `eval − max((68 − 16·[cutNode∧!ttHit])·(d − 1 − improving), 0) ≥ beta` → return eval (not when own last two moves were fours).
9. **Null move** (~3 elo): non-PV, no opponent four, `d ≥ 6`, `staticEval ≥ beta + (459 − min(24d, 182))`, `R = 3.70 + 0.141d`; verification search when beta is a mate score.
10. IIR (~3): PV without ttMove `−0.45`; PV with ttMove `−clamp((d − ttDepth)·0.19, 0, 3.6)` (~32 elo); IID at `d ≥ 14.5` without ttMove (~1).
11. Move loop with `MovePicker` (§5.5). Per move: `moveP4[B/W]`, traits `important` (self ≥ J_FLEX2_2X or oppo ≥ H_FLEX3), `trivial` (no pattern for either side), `distract` (far from both sides' last moves).
12. Shallow pruning (only once a non-losing move exists): **move-count pruning** `moveCount ≥ (3 + d^1.30)/(2 − improving)` (~107 elo); trivial moves at `d < 4.4` (~2); policy pruning (~10); distracting non-blocking defences when the opponent has a four (~2).
13. Extensions: forced reply to opponent five-threat `+1.1` (~77 elo); **singular extension** (~52 elo) at `d ≥ 6.85`: `singularBeta = ttValue − (0.85 + formerPv)·d`, reduced search at `d − (0.55d − formerPv)`, `+2` if `value < singularBeta − (29 − min(0.71d, 4))` (double-extension budget 10.8), multicut `return beta` if `singularBeta ≥ beta`, `−1.48` if `ttValue ≥ beta`; tt-move extension `+0.16`/`+0.08` (PV/non-PV), + near-own-four bonus 0.27/0.05 (~12); fail-high reduction (~50/~8).
15. **LMR** (`depth ≥ 2`, `moveCount > 1 + Root`): `r = lut[d]·lut[moveCount]`, `lut[i] = 0.95·(ln i + 0.1·ln threads)`; PV: `max(r − δ/rootDelta, 0)`; non-PV: `+1` if not improving and r > 1. Adjustments: policy reduction (~59), complexity `ln(1 + Σ p4 counts L..D)` × {0.092, 0.033, 0.025, 0.0053} by move type (~2), `−0.98` if ttPv (~10), `+0.91` at odd ply when not improving root alpha (~0), `+1.8` at cut nodes for non-killers (~5), useless defence `+2.07/+0.93` (~6), continuous attack `−0.41` (~5), history `−statScore/(12266 + 4380·[d>5.3])` with `statScore = hAttack + hQuiet·778/1024 − 3323` (~9). Re-search deeper if `value > alpha + 22 + 10·Δ` (+1 more if `> alpha + 343 + 27·Δ`), shallower if `value < best + newDepth` (~13).
16–18. Full-depth zero-window then PV re-search; after an alpha improvement `depth −= 1.05` for `2 < d < 12`, `|v| < 2000` (~26).
19. No legal move → mate score; save TT (§5.7); update histories.

### 5.3 VCF / threat search (search.cpp `vcfsearch`, `vcfdefend`; movepick QVCF stage)

- `vcfsearch` (attacker node, quiescence-like): TT probe (cut for all node types except main-thread PV); **stand pat** with static eval (`≥ beta` → return); delta pruning `eval + max(2084 + 62d, 768) < alpha` (freestyle); moves = only VCF moves (own block-four or better) restricted to the neighbourhood of the attacker's own last move (`RANGE_SQUARE2_LINE4`: 5×5 square + 8 lines to distance 4); plain B4 only if `depth ≥ DEPTH_QVCF_FULL` or the last two own moves were ≥ D_BLOCK4_PLUS, otherwise combination fours only (≥ D_BLOCK4_PLUS); ordered by classical score; each move → `vcfdefend` at `depth − 1`.
- `vcfdefend`: if the opponent has no five-threat → static eval; otherwise the single defence is the opponent's five-point (`lastPattern4(oppo, A_FIVE)`) — played without depth reduction, then `vcfsearch`. Renju: a forbidden defence loses.
- PAPER A.4: "apply a VCF search at each leaf node similar to the quiescence search … only considers moves from the attacker side that only allow one singular response from the defender".

### 5.4 Static win detection (PB wincheck.h `quickWinCheck`)

From `p4Count` only: own A_FIVE → `mate_in(ply+1)`; opponent ≥2 A_FIVE → `mated_in(ply+2)` (1 → continue, forced); own B_FLEX4 → `mate_in(ply+3)`; own C_BLOCK4_FLEX3 and opponent has no block-four → `mate_in(ply+5)` (else probe each C cell: play it and check the opponent's forced block does not also defend); own F_FLEX3_2X and opponent has no four of any kind → `mate_in(ply+5)`.

### 5.5 Move generation and ordering (PB movegen.cpp; AB movepick.cpp)

- Candidates = empty cells inside a candidate range around every stone (`CandidateRange` SQUARE2 5×5, SQUARE2_LINE3, SQUARE3, SQUARE3_LINE4, SQUARE4, FULL_BOARD), kept as a bitboard plus bounding box.
- Generators by GenType: WINNING (A_FIVE/B_FLEX4), VCF (≥E_BLOCK4; COMB → ≥D), VCT (≥H; COMB ≥G), VC2 (≥L; COMB ≥J), TRIVIAL; **defence generators**: DEFEND_FIVE (single cell), DEFEND_FOUR (exact defences of a flex four, reconstructed from the last flex-four attack move: completion square + line extension squares, or double-B3 case), DEFEND_B4F3 (defences of block-four+flex-three: the C cell, F3 line defences from the DEFENCE table, the B4 completion square and own counter-B3 squares); own VCF moves are appended separately.
- MovePicker stages: `MAIN_TT → MAIN_MOVES` (all candidates scored by policy or classical + history + counter-move), `DEFENDFIVE_*`, `DEFENDFOUR_*`, `DEFENDB4F3_*` (restricted move sets when the opponent threatens), `QVCF_*`. TT move first; partial sort (insertion sort if ≤ MAX_MOVES/4).

### 5.6 Histories (AB history.h/.cpp, search/history.h)

`MainHistory[2][1024][ATTACK|QUIET]` int16 with bounded update `v += bonus − v·|bonus|/10692`; `statBonus = min(29d² + 99d − 164, 8927)` (freestyle). Best move with own ≥H_FLEX3 → attack history bonus, others → malus; quiet best move (and no opponent four) → quiet bonus + killer; counter-move table `[2][MAX_MOVES] → (move, Pattern4)`, bonus 38 if the stored reply's pattern ≤ current. Ordering bonus = `hist·weight/65536` with weights attack 492, quiet 265 (freestyle).

### 5.7 Transposition table (AB hashtable.cpp)

12-byte entries: `key32` (low 32 bits of Zobrist XOR the 2 data words — lockless check), `value16`, `eval16`, `pvBoundBest16` (pv bit 15, bound bits 13–14, best move+1 in the low 10 bits), `depth8` (depth − (−20)), `generation8`. 5 entries per 64-byte bucket (+4 B padding); bucket index `mulhi64(key, numBuckets)`; replace the entry with the lowest `depth − relativeAge`; a same-key non-exact entry is kept if new depth + 2 < old depth; default 16 MB.

### 5.8 Time management (AB timecontrol.cpp)

`maximum = min(turnTime, matchLeft / min(matchSpaceMin, movesLeft)) − turnTimeReserved`; `optimum = maximum·advancedStopRatio` (if match time is tight, also `≤ maximum/averageBranchFactor·moveImportance(ply)` with `moveImportance = (1+e^{(ply−50)/8})^{−0.08} + 0.1·e^{−0.04·ply}`). Per iteration (`checkStop`): `turnTime = min(optimum·(1 + 2·avgBestMoveChanges)·reduction·fallingFactor, maximum/timeDivisor(depth))` with `fallingFactor = clamp(scale·(prevBest − best) + bias, 0.5, 1.5)`, `reduction = prevReduction^p / (1 + k·bestMoveStablePly)`. Hard stop at `maximum` (`checkTimeupCondition`). The `TimeCfg` constants are in `searchconfig.h`, **not included** in the sources.

### 5.9 MCTS (PAPER A.4, for comparison)

PUCT `a = argmax Q + c(s)·√ΣN/(1+N_a)·P` with `c = 1.0 + 0.4·ln(1 + ΣN/500)`; FPU `Q_parent − 0.1·√(Σ explored P)`; graph search (DAG), LCB root selection, delayed policy evaluation.

---

## 6. Reported results and ablations (PAPER §5)

**Table 2** — supervised losses and Elo at fixed nodes (MCTS playouts p, α-β depth d), 400 games, relative to Mixnet small:

| Model | Train V | Train P | Val V | Val P | 1p | 4p | 16p | 64p | 1d | 3d | 5d | 7d |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Mixnet Small | 0.7925 | 1.278 | 0.7836 | 1.277 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| Mixnet Medium | 0.7791 | 1.213 | 0.7715 | 1.214 | 64 | 64 | 68 | 90 | 50 | 54 | 49 | 82 |
| Mixnet Large | 0.7688 | 1.165 | 0.7632 | 1.166 | 101 | 121 | 119 | 138 | 77 | 84 | 89 | 102 |
| Resnet 4b64f | 0.8004 | 1.188 | 0.7928 | 1.184 | 106 | 97 | 64 | 37 | 31 | 30 | 25 | 48 |
| Resnet 6b96f | 0.7727 | 1.035 | 0.7645 | 1.031 | 247 | 212 | 208 | 214 | 123 | 154 | 129 | 155 |
| Resnet 10b128f | 0.7534 | 0.9246 | 0.744 | 0.9215 | 354 | 320 | 336 | 361 | 198 | 251 | 247 | 270 |
| Resnet 15b192f | 0.7329 | 0.8376 | 0.7303 | 0.8384 | 475 | 391 | 442 | 505 | 228 | 320 | 338 | 352 |
| Resnet 20b256f | 0.7264 | 0.7979 | 0.7181 | 0.7969 | 516 | 451 | 507 | 566 | 283 | 370 | 404 | 415 |

Reading (PAPER §5.1): Mixnet's value loss ≈ ResNet-6b96f, its policy loss only approaches ResNet-4b64f at the Large size (small receptive field hurts policy); per node, Mixnet sits between 4b64f and 6b96f.

**Fixed time** (§5.2–5.3, Figures 5–6, numbers only in text): with MCTS all Mixnets beat the ResNets by **300–400 Elo** (85–92 % win rate), gap narrowing slightly with time but present at 20 s/move. With α-β, Mixnet is **≈ +400 Elo over Katagomo** (CPU version); in α-β the Large Mixnet is *not* better than Small/Medium (higher codebook update cost eats the incremental-update advantage). Rapfi: #1 of 520 agents on Botzone; GomoCup 2024 champion (54 competitors).

**Table 3** — ablations, Elo at equal time (columns compare vertically only):

| Config | Train V | Train P | MCTS 2s | 5s | 10s | 20s | α-β 2s | 5s | 10s | 20s |
|---|---|---|---|---|---|---|---|---|---|---|
| Small (full) | 0.7925 | 1.278 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| Small w/o star block | 0.7995 | 1.294 | −14 | −15 | −30 | −4 | 5 | −6 | −1 | −7 |
| Small w/o value group | 0.8083 | 1.287 | −72 | −82 | −84 | −82 | −70 | −77 | −70 | −81 |
| Small w/o dynamic conv | 0.7903 | 1.465 | −143 | −144 | −150 | −145 | 2 | −1 | 3 | −8 |
| Medium (full) | 0.7791 | 1.213 | 54 | 60 | 50 | 62 | 11 | 10 | 20 | 9 |
| Medium w/o star block | 0.7839 | 1.223 | 26 | 29 | 19 | 30 | 2 | −4 | 5 | −2 |
| Medium w/o value group | 0.7957 | 1.219 | 2 | 2 | −6 | 14 | −43 | −47 | −42 | −61 |
| Medium w/o dynamic conv | 0.7801 | 1.431 | −118 | −116 | −120 | −113 | −24 | −32 | −12 | −21 |
| Large (full) | 0.7688 | 1.165 | 57 | 74 | 77 | 84 | −82 | −58 | −45 | −38 |
| Large w/o star block | 0.7733 | 1.160 | 90 | 99 | 96 | 112 | −9 | −11 | −3 | −8 |
| Large w/o value group | 0.7844 | 1.161 | 28 | 41 | 33 | 48 | −24 | −34 | −42 | −29 |
| Large w/o dynamic conv | 0.7712 | 1.387 | −112 | −97 | −99 | −88 | −100 | −92 | −66 | −61 |

Takeaways: for **α-β the value-group head matters most** (−40…−80 Elo without it), dynamic policy conv matters a lot for MCTS but little for α-β, the star block is roughly neutral (slower), and the **medium size is the α-β sweet spot**.

**Search-feature Elo** (code comments, AB search.cpp, same engine, conditions unstated): futility 121, move-count pruning 107, OPPO5 forced-reply extension 77, razoring-with-VCF 68, policy LMR 59, singular extension 52, fail-high reduction 50(+8), IIR-tt 32, alpha-improvement reduction 26, VCF at leaves 17, LMR re-search extension 13, tt-move extension 12, policy pruning 10, ttPv LMR 10, history LMR 9, useless defence 6, cut-node 5, continuous attack 5, null move 3, IIR 3, complexity 2, trivial pruning 2, distract pruning 2, IID 1, no-alpha 0.

---

## 7. Adaptation to Quantum Connect **[Inference — everything in this section is my reasoning unless it quotes a source]**

Game recap (from the task): gravity drop, default 8 rows × 12 columns (constructor parameter; Main uses 25×25), six or more in a row wins (so overlines win, like Rapfi FREESTYLE semantics), special move at most once per 10 own turns (place, clear the 3×3 block centred on the landing cell including the new disc, re-apply gravity), after any move any 6-line on the board means the mover wins (whatever its colour), draw on full top row or 1000 turns, 100 ms per call.

### 7.1 Structural facts that change the design

1. **The board is always gravity-compact**: after a normal move and after destroy+gravity, every column is a contiguous stack from the bottom. The whole state is `heights[W]` + stack contents + per-player special cool-downs + side to move + turn number.
2. **Exactly one playable cell per non-full column**: ≤ W normal moves and ≤ W special moves per node (24 on 8×12, 50 on 25×25). Branching is far smaller than Gomoku's 50–200 candidates, so Rapfi's candidate-range machinery is unnecessary; full-width generation is cheap.
3. **Threat cells are not automatically forcing**: a cell that would complete six is only an immediate threat if it is playable now (deficit 0). Higher threat cells are latent (Connect-4 style). Playing directly under an opponent's threat cell hands them the win ("poisoned" column). Stacked threats (own threat cell directly above another own threat cell or above the opponent's forced block) win. Row parity matters (on an even-height board the classic Allis rule: first player wants odd-row threats, second player even-row threats) — but the special move changes column heights by up to 3 in three columns and so **breaks parity/zugzwang** for whoever still has it available.
4. **Only horizontal and diagonal lines are long**: on 8×12 the vertical and diagonal lines are ≤ 8 cells, horizontals 12. Vertical lines are degenerate (the stack is contiguous, everything above the top is empty).
5. **No 90° symmetry**: gravity breaks rotations. Valid symmetry: left–right mirror only (Rapfi's `FLIP_Y`, one of its `isRectangleTransform`s). Mirror maps horizontal→horizontal, vertical→vertical, main diagonal↔anti-diagonal. So use three direction families (H, V, D) instead of Rapfi's two (H+V, D).
6. **Destroy moves are non-local and non-monotone**: up to 8 discs vanish (landing disc + top disc of the landing column + up to 3 in each neighbour column), whole column segments shift down. Rapfi's "freeze invariant" (an occupied cell keeps its patterns forever) and its O(1) stone-local incremental update no longer hold for these moves.
7. **Win check after destroy** must scan all lines through every shifted cell for both colours (mover wins on any 6-line).

### 7.2 Window length for six-in-a-row

- Classical patterns (centre implicit, empty centre): to see every 6-window through the centre you need ±5 → **H = 5, 11-cell window** (Rapfi uses H = win−1 = 4 for freestyle five). Overline needs no special handling because longer lines win (realLen ≥ 6 → win), so the DP is Rapfi's freestyle DP with 5→6 and an extra rung (e.g. `DEAD, B1, F1, B2, F2, F2A, F2B, B3, F3, F3S, B4, B4S, F4, B5, B5S, F5, W6` — 17 classes, one more than fits a nibble; drop B1 or merge F2A/F2B to stay at 16 so Pattern2x stays one byte).
- Mixnet-style learned lines (centre included): Rapfi uses ±win length (±5 for five). The analogue for six is ±6 = 13 cells, but that table is 9× bigger (below). ±5 (11 cells) plus the 3×3 dwconv still sees a ±6 neighbourhood indirectly; I would start with 11.

### 7.3 Cell states and table sizes

| Encoding | States per neighbour cell | Window | Entries (dense) | Bytes | Fits jar/RAM? |
|---|---|---|---|---|---|
| A. Rapfi classical, centre implicit | empty/own/opp + wall | H=5 | 364² = **132,496** (identical to `PATTERN2xStandard`) | 132 KB (Pattern2x) + 265 KB defence | yes; can be built at startup |
| B. + playability | empty-playable / empty-floating / own / opp + wall | H=5 | per half Σ_{v=0..5}4^v = 1,365 → 1365² = **1,863,225** | 1.9 MB | yes; build at startup or ship |
| C. + deficit parity | playable / floating-odd / floating-even / B / W + wall | H=5 | per half Σ5^v = 3,906 → **15.3 M** | 15 MB | RAM ok, DP over 5^11 states too slow at startup → ship precomputed |
| D. Mixnet 3-state (Rapfi) | E/B/W + wall, centre incl. | L=11 | **442,503** slots (397,488 reachable) | C=32 int16: 28.3 MB per mapping ×3 families = 85 MB; 10-bit packed 53 MB | too big for a comfortable jar; use VQ |
| E. Mixnet 3-state | | L=13 | 3,984,714 slots (3·1093² = 3,584,547 reachable) | ×9 of D | no (unless VQ: index 8 MB/family) |
| F. Mixnet 4-state | E-play/E-float/B/W + wall | L=11 | 4·1365² = 7,452,900 | VQ index uint16: 14.9 MB per family | marginal |
| G. Mixnet 4-state | | L=9 | 4·341² = 465,124 | ~ like D | yes with VQ |
| H. VQ'd D | | L=11 | index 442,503×2 B = 0.9 MB + codebook 4096×32×2 B = 0.26 MB per family | ≈ 3.5 MB for 3 families | yes |

Recommendation: **lines use 3 states (encoding A for the classical patterns, D/H for any learned net), and playability is a per-cell context**, not part of the line code. Reasons: the playability of a cell is a function of `(row − heights[col])` only, so it is cheap per-cell side information; keeping it out of the line keeps all tables at Rapfi's proven sizes; and the critical gravity facts (is the win cell playable, what is under it, row parity) are per-cell and per-column, not per-line. The exception is a cheap 1-bit extension where it clearly pays: the vertical direction can be ignored for "floating" altogether because vertical lines are fully determined by the stack.

Per-cell context for an empty cell c (both colours): `deficit = row − heights[col]` bucketed {0, 1, ≥2}, row parity, and "is the cell below c an opponent immediate-win cell" (poisoned). Evaluation index = (direction-aware pattern code) × context:
- Direction-aware pcode: Rapfi's unordered 4-multiset is wrong under gravity (vertical is special). Use `(V pattern) × (H pattern) × unordered{D1, D2}` = 16·16·C(17,2) = 16·16·136 = **34,816** codes (mirror symmetry makes D1/D2 interchangeable), or a cheaper `V × unordered{H, D1, D2}` = 16·C(18,3) = **13,056**.
- × context 3 deficit × 2 parity = 6 → 209 k (or 78 k) int16 entries per colour table ≈ 0.4 MB. Trainable exactly like Rapfi's EVALS (§2.5).

### 7.4 What must be recomputed after each move type

- **Normal move** at (col, h): stone cell changes (empty → colour) **and** the cell above becomes playable (context change only, if lines are 3-state). Update: Rapfi's walk over ±5 cells × 4 directions for the stone (≤ 40 cells), plus re-evaluate the context of the cell above and of the two cells whose "poisoned" flag may change. Undo by restore records as in `Board::undo`.
- **Destroy move**: changed cells = columns col−1..col+1 from row `h−1` (landing row minus one) to the old column tops, i.e. ≤ 3·H cells. Simplest correct approach: **copy-make**: snapshot the board arrays (a few hundred bytes for 8×12, ~5 KB for 25×25) and **recompute all patterns from scratch** — 96 cells × 4 directions = 384 table lookups on 8×12 (2,500 on 25×25), i.e. comparable to ~10 incremental moves. Do the same for any NN accumulator (full refresh; Rapfi's `Accumulator::clear` shows the procedure). Then run a full-board 6-line check for both colours (mover wins on either).
- **Zobrist** must include special cool-down state for both players (10 values each) and side to move; include the turn counter only within ~2·search depth of the 1000-turn limit (or treat it as a draw-distance bound like Rapfi's `maxMoves`).

### 7.5 Threat search adapted to gravity

Keep Rapfi's two-function scheme (`vcfsearch` attacker node with stand-pat, `vcfdefend` single-reply node), with these changes:
- **Forcing attacker move** = a move after which the attacker has ≥ 1 *playable* win cell. Defender replies: (a) block that cell; (b) if the defender has an immediate win, it plays it (Rapfi checks this via `quickWinCheck` first); (c) **any special move the defender still has** (it may clear the threat or even create a six for either colour — which wins for the defender). (c) makes the defence set ≤ 1 + W instead of 1; prune specials to those whose 3×3 block intersects the threat line (others cannot remove the threat; they can only win outright, which a cheap "does this special create any six" pre-check finds).
- **Immediate-win rules for `quickWinCheck`**: own playable win cell → win in 1; opponent has ≥ 2 distinct playable win cells and we have no win and no rescuing special → lose in 2; own win cell directly above the cell the opponent is forced to block (or directly above another own playable win cell) → win in 3; a column where the opponent's win cell sits directly above our only legal non-losing move → zugzwang candidate.
- **Latent threats** (non-playable win cells) feed the evaluator, not the threat search: per column, the lowest win cell of each side and its row parity (Allis's odd/even rules) into a small learned table, analogous to Rapfi's 2048-entry `EVALS_THREAT`; include "special move available" bits for both sides because a special can reset parity.
- **Move restriction while threatened** (Rapfi's DEFEND_* generators): if the opponent has a playable win cell, legal useful moves are: block it, own immediate win, specials. Never play under an opponent win cell (prune, except to win).
- Rapfi restricts VCF moves to the attacker's neighbourhood (`RANGE_SQUARE2_LINE4`); with ≤ W candidates it is cheaper to test all columns with a 1-ply pattern check.

### 7.6 Search settings that transfer / need retuning

- Transfer as-is: PVS + aspiration, float depth, TT layout (12-byte entries, 5 per 64 B bucket; in Java use two `long[]` arrays or one `long[]` with packed fields), killer + main history with the bounded update formula, counter-move, null move (careful: zugzwang is real in gravity games — disable null move when few columns are left or when the side to move has no special, or verify), singular extension, LMR with log×log table, mate-distance pruning, lazy NN evaluation inside an alpha–beta margin (§2.3), iterative deepening with best-move-stability time control.
- Retune (SPSA, as Rapfi does, `tunemap.h`): move-count pruning (Rapfi's `3 + d^1.3` assumes many candidates), futility/razor margins (eval scale), LMR factor; `TRIVIAL_PRUN`/distract logic is Gomoku-specific — replace with "poisoned column" pruning.
- Time: one 100 ms budget per call with a timeout = loss. Use a hard stop at ~70–80 ms measured with `System.nanoTime()`, check the clock every ~1–2 k nodes, keep the best completed iteration. Rapfi's `checkStop` formula can be reused with `turnTime = 75 ms`. Pre-allocate everything (no allocation in search; GC pauses are the main risk). The two calls (`accelerateQuark`, `useSpecialMove`) should share one search result: search both move kinds in one tree at the first call, cache `(column, special?)`, and answer the second call from the cache (or keep searching on the cached tree if the second call comes first — the call order is an open question).
- Expected speed (guess, not measured): the classical evaluator in Java, with its table-lookup incremental update, should allow roughly 10^4–10^5 nodes per 100 ms on 8×12. The paper's Mixnet-small α-β speed is 428 K nodes/s with AVX2, and the paper credits SIMD with ~4×; scalar Java (no AVX; `jdk.incubator.vector` needs a JVM flag and is probably not allowed) would then be ~100 K nodes/s, i.e. ~10^4 NN-evaluated nodes per 100 ms. That favours classical-first with lazy NN calls (§2.3).

### 7.7 Making the evaluator board-size generic

- Classical: naturally generic — sum over empty cells, walls encoded in the line code. Only issue: the absolute magnitude of Σ grows with area; Rapfi tunes per board-size range and supports `EqualBoard` weighting (§2.5). Train on a mix of sizes (8×12 primary; some 6×7, 10×14, 25×25) or normalise by playable-cell count.
- Line encodings: if the Java board uses Rapfi's padded rows with 2-bit cells, a 25-wide row + 2×5 walls = 35 cells = 70 bits > 64; either compute keys by gathering 10 neighbours from a padded `byte[]` (10 reads; cheap) or split into two longs. Rapfi's own layout caps the board at 22.
- Mixnet heads: value grouping into 3×3 regions is defined for any H×W (`split_3x3_regions`); global/group sums scale with area (Rapfi uses fixed shifts `>>8`, `>>5`) → either train with multiple sizes (`iterative_multi` + `batch_by_boardsize`, as the f15+f20 config does) or replace sums by means. The weight header records supported sizes; do the same.
- Data augmentation: mirror only (×2).

### 7.8 Training data generation with our own Java engine

1. **Bootstrap engine**: rule-based patterns (encoding A + per-cell context), hand-set monotone table values (pattern strength), α-β + gravity threat search. Validate by self-play vs a plain alpha-beta.
2. **Self-play harness** in the same jar (headless, multi-threaded, one game per thread): random openings (first 2–8 plies random, including occasional random special moves so destroy positions are well represented), fixed node budget per move (e.g. 10–50 k nodes, faster than tournament), temperature/multi-PV sampling for the first N plies (Rapfi's multi-PV target: softmax(winRate(eval)/T), §2.5). Record per position: board (W·H 2-bit cells ≈ 24 bytes for 8×12), heights, side to move, both cool-downs, ply, search value, best move incl. special flag, top-k move values, final result. Store mirrored copies only virtually (augment in the trainer).
3. **Fit the classical tables** with the Rapfi tuner recipe (BCE on `sigmoid(K·eval)` vs result, K grid search, Adam lr 0.01, batch 8192, policy softmax over legal moves with per-cell scores). Equivalent and easier: a PyTorch embedding-sum model like the trainer's `LinearModel`/`LinearThreatModel`, then round to int16.
4. **Iterate**: new tables → new self-play generation (5–20 M positions per generation), 3–6 generations.
5. **Optional Mixnet stage**: train a small ResNet teacher on the accumulated data, then a Mixnet (3 direction-family mappings, C=32, D=16–32, V=32, value groups on, star blocks optional) with KD α≈0.75–0.8, QAT as in §3.2, export to VQ tables (codebook 4–16 k), hand-written int16/int8 Java inference, evaluator called lazily.

### 7.9 Realistic training effort on an 8 GB GPU

- Self-play is the bottleneck, on CPU: a game of ~40–80 plies at ~20 k nodes/move in Java (~20–50 ms/move) takes ~2–4 s per thread; 8 threads ≈ 7–14 k games/h ≈ 0.5–1 M positions/h → 10 M positions in roughly half a day to a day. Paper scale (30.8 M positions) ≈ 1.5–3 days of one 8-core PC.
- Classical table fitting: a sparse linear model over ≤ 0.5 M parameters; tens of millions of samples train in minutes (CPU or GPU).
- Teacher ResNet 6b64f on 8×12: ~85 MFLOP forward per sample (12 convs × 96 cells × 9·64·64·2), ~250 MFLOP per training sample; at an effective 5–10 TFLOPS in bf16 on an 8 GB card that is ~20–40 k samples/s → 20 M positions × 10 epochs ≈ 1.5–3 h.
- Mixnet small mapping on 8×12: per cell per direction ≈ 76 k MACs (3·2·64 + 4·(3·64·64 + 64·64) + 2·64·64 + 64·32), ×4 directions ×96 cells ≈ 29 M MACs ≈ 60 MFLOP forward, ~180 MFLOP per training sample → tens of thousands of samples/s. Paper-scale training (128 × 600 k = 77 M samples) ≈ 1 h; trainer-config scale (2048 × 2 M = 4.1 B samples) ≈ 1–2 days. A run of 200–500 M samples (a few GPU-hours) is the realistic target. Memory: 8 GB is ample at batch 2048 for 8×12 inputs.
- Export (enumerating 442,503 line codes through the mapping) takes seconds.

---

## 8. Open questions / not answered by the sources

1. Hardware for the paper's Table 1 speeds and Fig. 5/6 matches (CPU model, threads, AVX2 only?), and the exact time controls behind Fig. 6. Figures 5 and 6 are images; only their text summary is available.
2. Storage column of Table 1 (28.4 MiB for small) does not exactly match 442,503×32×int16 (27.0 MiB) or two mappings (54 MiB) or 10-bit packing; how the paper counted it is unclear.
3. Training compute (GPU type, GPU-hours) for any Mixnet, and the size/provenance of the data behind the released Rapfi networks (Katagomo data vs c-gomoku-cli self-play) are not stated.
4. The tuner's sample compilation (`appendTuneSample`), dataset formats (`dataset.h`, `tunecorpus.h`) and therefore the exact linear features of the classical evaluator are not in the provided files (EvalInfo suggests pcode counts at two plies + threat mask).
5. `searchconfig.h` (`TimeCfg` constants, `numIterationAfterMate`, `maxSearchDepth`, aspiration flag), `GeneralCfg.defaultCandidateRange`, and `simdops.h` (exact `crelu`, `dot2`, `mulhi` semantics) are not included.
6. How the released networks were trained for multiple board sizes in practice (the f15+f20 config exists; which sizes the published weights support is not stated).
7. The paper's knowledge-distillation ratio (75/25) differs from the example configs (α = 0.8) and its optimiser (Adam, batch 128, 600 k iterations) differs from the configs (AdamW, batch 2048, 2 M iterations, step LR) — which one produced the competition nets is unknown.
8. Game-specific unknowns (for us, not Rapfi): exact order and semantics of the two 100 ms calls (`accelerateQuark` vs `useSpecialMove`), whether constructor/initialisation time is limited (decides whether 1.9 MB pattern tables can be built at startup or must ship in the jar), number of CPU cores/threads allowed, JVM heap and jar size limits, which board sizes are actually played, whether a special move into a full column or a column near the top is legal, and the precise definition of "draw at full top row".
9. Whether Connect-4 parity theory holds well enough with special moves for a learned latent-threat table to matter — needs measurement in self-play.
