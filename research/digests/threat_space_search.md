# Digest: Allis' threat-space search, dependency-based search, proof-number search

Target game: "Quantum Connect" (8 rows x 12 columns, gravity, six in a row incl. overlines, once-per-10-own-turns 3x3 "destroy" special move, 100 ms/move, Java stdlib only).

## Sources and citation key

| Key | File (folder `sources\text\`) | Notes |
|---|---|---|
| **A93** | `pdf-p2-allis1993-gomoku-and-threatspace-search-gomoku.md` (770 lines) | Allis, van den Herik, Huntjens, "Go-Moku and Threat-Space Search" (1993/94). Read in full. Cited by section (`§`) and md line (`L`). |
| **T** | `pdf-p2-allis1994-searching-for-solutions-in-games.md` (9117 lines) | Allis, PhD thesis 1994. Read only ch. 2 (pn-search, L920-3082, results L2326-2900 skimmed), ch. 3 (db-search, L3083-4455), ch. 5 (Go-Moku, L5560-7009). Cited by section, printed page (`p.`) and md line. Ch. 1, 4 (Qubic), 6 deliberately not read, except the terminology note at L4548-4551. |
| **CPW** | `cpw-proof-number-search.md` (356 lines) | Chess Programming Wiki page, pseudo-code from Kishimoto et al. 2012. |

Conventions: **[OWN]** = my reasoning/design, not in the sources. **[GARBLED]** = pdftotext damaged the text; what I reconstructed and how sure I am is stated. In all T/A93 text, math glyphs are lost (set symbols, arrows, the infinity sign, the attacker/defender/empty marks in attributes). The infinity sign comes out as the digit "1" or "OO" (e.g. T L1081 "proof number 1" for a false terminal node really means infinity, confirmed by T L1513 "proof number 0 and disproof number infinity" and CPW L39).

Terminology note (T §4.1 p.97, L4548-4551; §5.1 p.122, L5600-5602): the thesis drops the name "threat-space search" and uses only **db-search** for what A93 calls TSS in Qubic and Go-Moku; "both names were suggested by Barney Pell". So TSS (A93) = the Go-Moku/Qubic *instance* of db-search (T ch. 3 framework, T ch. 5 instance).

---

## 1. Threat-space search (TSS)

### 1.1 Go-Moku threat vocabulary (A93 §3.1, L125-131; T §5.2.2 p.125, L5717-5761)

Board 15x15, five in a row wins (free-style: overline also wins; "standard": exactly five). Threat types, each a pattern inside one line:

| Name | Pattern (attacker = A) | Defender replies | Wins in |
|---|---|---|---|
| five | 5 consecutive A | - | already won |
| **four** | line of 5 squares, 4 are A, 5th empty | exactly 1 (the empty square) | 1 move |
| **straight four** | line of 6 squares, 4 centre squares A, both outer squares empty | none (two winning squares) | 1 move, unstoppable |
| **three** | (c) line of 7 squares, 3 centre A, other 4 empty -> 2 replies (both squares adjacent to the stones); or (d) line of 6 squares, 3 consecutive of the 4 centre squares A, other 3 empty -> 3 replies | 2 or 3 | 2 moves (makes straight four) |
| **broken three** | line of 6 squares, 3 non-consecutive of the 4 centre squares A, other 3 empty | 3 | 2 moves |

A93 L96-106 / T Fig 5.1 show these as diagrams; the diagram glyphs are lost [GARBLED] but the verbal definitions above are intact in both sources and agree. The 2/3-reply counts are stated explicitly (A93 L89-94, T L5740-5761).

More complicated threats (double-four, four-three, double-three *next move* patterns, T Fig 5.2) are **deliberately excluded** from the search as threats (T §5.2.2 p.126, L5763-5780), for three reasons: many replies do not fit the single-agent transformation; single-line threats are cheaper to recognise; the catalogue is open-ended.

A winning move needs a **double threat**: two fours, a four plus a three, two threes, or a straight four (A93 L108-112; T L5807-5810). T Def 5.1 (L5925-5931) treats five and straight four as "double threats" (no reply), four/three/broken three as "single threats". Rationale (T L5970-5977): a double three/four/four-three is recognised one move later as a straight four / five once the defender has answered one half.

### 1.2 Threat sequences and the human model (A93 §3.2, L132-197; T §5.2.3 p.128, L5826-5885)

- A *threat sequence* = alternating attacker threats and (forced) defender replies; a *winning threat sequence* ends in a double threat. Typical expert depth 5-20 ply (A93 L142-144, T L5835-5836).
- Observed expert method (6 steps in A93, 4 in T): pick a board region by "feeling"; consider threats, mostly ignoring defender moves; once a double threat is found, check whether the defender can refute (several replies -> does the same sequence work in all?; can he insert fours?); if only a few variations fail, look for other sequences; in practice the attacker's moves are often the same in all variations; searching one side first shrinks the space (A93 L150-197).
- A winning threat *tree* vs *sequence*: A93 prefers "tree" where the attacker's moves depend on the replies (A93 L164-178); T Def: tree where every variation reaches a double threat (L5809-5812). TSS searches only the special case where one **sequence** wins against all replies at once.
- Motivating failure of alpha-beta (A93 §3.4, Diagrams 3a/3b, L198-221, L256-264): a position with 16 independent fours gives about 8! x 2^8 ~ 10,000,000 move orders (over 6,000 variations even with transposition tables), none winning; humans see at once that the loose threats are irrelevant.

### 1.3 Formal TSS definitions (A93 §4, L265-303)

Six notions (verbatim sense, with the A93 example for diagram 3b, L278-292):

1. **Gain square** of a threat = the square the attacker plays.
2. **Cost squares** = the square(s) the defender plays in response (= the reply set).
3. **Rest squares** = the squares of the threat's line that "contain a threat possibility", gain square excepted. Example: playing e15 gives a four, gain e15, cost d15, rest a15, b15, c15. [GARBLED/uncertain: the wording is loose; from the example the rest squares are the attacker's other line squares that the threat needs, i.e. the stones already on the board or placed by earlier threats.]
4. Threat A is **dependent** on threat B if a rest square of A is the gain square of B.
5. **Dependency tree** of threat A: root A, children of node J = threats dependent on J.
6. Two dependency trees P, Q are **in conflict** if some threat A in P and B in Q have (1) gain(A) = cost square of B, or (2) vice versa, or (3) a cost square of A is also a cost square of B.

Two search principles (A93 L305-315):

- **P1** A threat that is *independent* of threat B may not occur in B's search tree (only dependent threats are children). This is what removes the permutations of unrelated threats (Diagram 3a: 16 fours become 16 leaves).
- **P2** The search tree contains only the attacker's threats; each is "answered by all directly defensive moves" simultaneously. After a potential winning threat sequence is found it is checked against counterattack.

Key reduction idea (A93 L278-292): instead of choosing among the 2-3 replies, let the defender play **all** replies at once; if the attacker still wins, the defender's choice was irrelevant. Justification in free-style (T L5991-5995): extra stones never hurt in free-style Go-Moku, so winning against the multi-stone reply implies winning against any single-stone choice. Incomplete: rare positions need the attacker to vary moves with the reply (T L5997-6000; A93 L288-290).

### 1.4 Algorithm (prose in A93 L405-419; reconstruction below is [OWN])

A93 gives no pseudo-code for TSS; the general description (A93 L405-419): "From a given position a search tree of dependent threats is developed. Whenever a branch ends in a straight four or five it is examined whether the potential winning sequence can be refuted. If none of the variations leads to a win for the attacker, combinations are made between all independent nodes, with the condition that the gain squares involved lie on a single line and close to each other. This process is repeated until a winning threat sequence has been found, or no new potentially beneficial combinations can be created." A93 summarises: TSS "linearizes part of the search process".

[OWN] reconstruction as pseudo-code (the actual db-search loop is in section 2):

```
TSS(pos, attacker):
  roots  = all threats available in pos (types four/three/broken three, gain square g, reply set R)
  tree   = roots                      # level-1 nodes, each carries board = pos + g(attacker) + R(defender)
  repeat
    # dependency stage
    for node in frontier(tree):
       for threat t on board(node) whose rest squares include gain(node):   # P1: dependent only
           child = apply(t) to board(node)   (attacker gets gain, defender gets all of R(t))
           if t is straight four or five: candidate = path(child); if survives(candidate): return WIN
           add child
    # combination stage
    for each pair/triple (n1,n2,..) of INDEPENDENT nodes in tree:
        if gain squares lie on one line, close enough for a five,
           and dependency trees pairwise NOT in conflict (cond. 1-3 above):
              add combination node (board = merge of both boards)
  until win found or no new nodes
  return NO_WIN_FOUND                  # incomplete: may miss real wins
survives(seq): defender cannot (a) win by his own threats interjected, or
               (b) occupy a square of the threat lines by interjected forcing moves   (T Def 5.5)
```

Worked examples (A93 §4): Diagram 3b: tree of **24 nodes** finds both potential wins; 18 independent threats = 16 fours in the corners + 2 threes near i7-i12, 16 of them are terminals because their gain squares enable no new threat (L354-378). Diagram 4: four initial fours (h10, h9, j10, i9); of 6 pairs, h9-h10 and i9-j10 rejected by conflict, h9-j10 not on a line, the remaining three combos give a straight four at f12 from {h10, i9} (L361-402).

### 1.5 Why defender moves can be ignored and the correctness conditions (T §5.3.1, 5.3.3, 5.3.4; A93 §6)

Two levels of defence (T L6002-6023):
- **Local defence**: reply to each threat on its own squares - eliminated by the meta-move "attacker move + whole reply set" (T L6025-6034), which turns the adversary space into a **single-agent** space. This is what TSS/db-search searches.
- **Global defence**: any other defender move (interjected threats). Checked only *after* a potential win is found.

T Def 5.5 (L5949-5960): a *winning* threat sequence is a potential one for which the defender cannot (1) interject a sequence of threats that the attacker must answer and that leads to a win for the defender, or (2) interject threats leading to occupation of a square related to an earlier attacker threat a_i before the defender has played its reply d_i.

**Threat categories** (T L6219-6235): cat 0 = five; cat 1 = straight four and four; cat 2 = three and broken three. "Each threat in category i threatens to win in exactly i moves". Against a category-i threat only threats of category j < i can be interjected: against a four only a five; against a three also fours and straight fours.

**Global-defence check** (T L6237-6358): run the same db-search *for the defender* after every attacker move, with: goal set extended by the squares of earlier attacker threats and replies; any defender potential-win sequence found is accepted as refutation without being checked itself; only threats of category below the attacker's current one. Properties: it "may erroneously decide that a defensive strategy exists" (rejecting a true win) "but will never overlook" one, so a sequence accepted as winning is guaranteed (T L6244-6286). After a cat-2 move, search cat 1 for the defender; after a cat-1 move search cat 0, **from every position reachable by defender fours interjected earlier** (T L6353-6358, Fig 5.5 example with 28 related squares).

Module parameters (T L6295-6304): position, attacker, goal squares, defensive-check flag, maximum category.

**Efficacy loss** (A93 L433-448, T L6360-6378): TSS only returns guaranteed wins but misses some wins: in 3 of 12 test positions (2, 10, 11) Victoria-TSS found nothing, "the reason being our reduction of the search space"; the tournament version's TSS+PN-search finds all three.

Heuristics that raise efficiency at small efficacy cost (T §5.3.4 p.139-141, L6360-6463; all switched off inside global-defence searches):
- **Global refutation**: stop after T = 10 potential wins have been investigated at a level (T L6387-6417).
- **Category reduction**: if the defender has a threat of category c1 in node N, the attacker's descendants of N are limited to categories < c1 (T L6426-6441).
- **Restricted threes**: use the 3-reply three only if the 2-reply three (T2) is not applicable (T L6443-6459).

Standard (exactly five) Go-Moku needs extension-square bookkeeping and costs about 20% more nodes (T §5.3.5 p.141-143; T L6870-6873). Not needed for our free-style six (overline wins) [OWN note: our rules are the free-style analogue].

### 1.6 Numbers reported for TSS

- Average **0.1 CPU-s** per call on an IBM RS6000 to decide "winning threat sequence exists" (A93 L475-477, L578-579).
- 12 Polygon test positions: Victoria-TSS searched fewer nodes than Polygon-3ply in all 9 jointly solved positions, and fewer than Polygon-7ply in all but position 9; ratio vs 3-ply from **3.2x (pos 1) to 87x (pos 8)**, vs 7-ply from 0.8 (pos 9) to 17.2 (pos 7); TSS ran about **5,000 nodes/s vs Polygon's 500** on the same hardware, "ten times faster since it is not hemmed by a burden of knowledge" (A93 L444-469). Caveats given by the authors: test positions have almost no defender counter-threats; Polygon uses transpositions and global move generation, TSS generates moves locally around the parent's gain square (A93 L434-453). **[GARBLED]** Table 4 (A93 L405-429) is interleaved by the two-column layout; per-position node counts cannot be reconstructed reliably, so only the ratios stated in the prose are used.
- TSS as a heuristic evaluation is "admissible" because a found win is a proven win (A93 L479-484).
- Success of the first (Qubic) version: A93 L575-577; Go-Moku Black win found via database then TSS in all of Victoria's games as Black at the 4th Computer Olympiad (A93 L563-568).

---

## 2. Dependency-based search (db-search)

Source: T ch. 3 (p.65-94, L3083-4455). It is a **single-agent** search; the chapter's pitch is that a *better state representation* shrinks the space, while pn-search (ch. 2) is a better *traversal* (T §3.1 p.65, L3085-3091).

### 2.1 Idea: structured states and order-independence (T §3.1, L3093-3225)

STRIPS-like representation: a state is a set of attributes; an operator f = (pre, del, add) with del subset of pre; applicable in S iff pre subset of S; f(S) = (S minus del) union add (T Def 3.2, L3387-3391). From the structure one can *derive* when two operators are independent (order irrelevant) without domain code. Toy illustration: 10 independent rewrite rules r0..r9 plus goal rule r10 needing all of them: atomic states = 1024 (+1) states, the reduced space = 11 (+1) (L3143-3225). Double-letter puzzle (DLP, L3241-3292): rewriting `aa -> e|b`, `bb -> a|c`, ... on strings over {a..e}; solutions = theorems of length 1; used as the running example and benchmark.

### 2.2 Formal framework (T §3.3, L3294-4036)

- **Paths** = operator sequences applicable to an initial state (Def 3.5-3.6). Two paths are *equivalent* if one is a permutation of the other (Def 3.8). Conventional DFS/BFS traverse all of U_p (all paths); transposition tables only collapse paths reaching the *same state* (L3526-3562).
- **Key operator** = last operator of a path; a **key class** = an equivalence class whose paths all share the same key operator; U_k = set of all key classes (Def 3.10-3.11, L3578-3598). Intuition: only the *dependency-closed set of operators needed for a given last operator* is kept, in any order.
- Three conditions (L3601-3628):
  - **Monotonous**: for a path (f1..fn), the add sets are pairwise disjoint and disjoint from the initial state (an attribute, once deleted, is never recreated) (Def 3.12). The formula is **[GARBLED]** by pdftotext but the verbal statement (L3571-3573) is clear.
  - **Singular goal**: every goal state is a single attribute (Def 3.13). Can be forced by adding a special goal attribute G with operators mapping any goal state to G (T L6187-6189).
  - **Non-redundant**: no path extends a solution (Def 3.14).
- **Theorem 3.1** (L3686-3699): under these three conditions U_k is **complete** (every solution lies in a key class; a class is all-solutions or none). Lemma 3.1: equivalent monotonous paths produce the same state; Lemma 3.2: a class containing a solution is a key class.
- **Relations** (Def 3.15, L3731-3738; the arrow glyphs are lost **[GARBLED]**, definitions from the prose): f1 *supports* f2 (f2 *depends on* f1) iff add(f1) intersect pre(f2) is non-empty; f1 *precedes* f2 iff pre(f1) intersect del(f2) is non-empty (f1 must run before f2 deletes what f1 needs). *Parents* of f in path P = operators that support or precede f; *ancestors* = transitive closure; a parent is *relevant* if it is not an ancestor of another parent (Def 3.16-3.17).
- **Merge** of paths/classes = all permutations of the union of their operators, each operator once (Def 3.18, L3780-3795).
- **Meta-operator F(N, f)** (Def 3.19, L3830-3849): N = a set of key classes C1..Cn whose merge is non-empty; f is *valid* in N iff f extends a path of the merge and the key operator of every Ci supports or precedes f; F(N, f) is applicable iff f is valid in N and in no proper subset of N. Result: the class [P+(f)]. F(empty, f) = {(f)} when f applies to the initial state. Informally: f needs attributes produced by each class in N, the classes can be combined without conflict, and N is minimal.
- **Soundness** (Thm 3.2, L3913-3924): every application of F creates a key class. **Completeness** (Thm 3.3, L3981-4002): every key class is created by some F(N, f) (induction using relevant parents). Hence F traverses *exactly* U_k (L4022-4036).

### 2.3 The algorithm: dependency and combination stages (T §3.4-3.5, L4039-4316)

Trying all subsets N is exponential (L4048-4052), so db-search organises the application of F in **levels**, each = two stages (L4059-4084):

1. **Dependency stage**: apply F only with |N| = 1 (extend a single node by every operator that depends on it); newly created singleton-eligible nodes are processed in the same stage, until nothing new.
2. **Combination stage**: build *combination nodes* for sets N with |N| > 1 for which at least one operator f makes F(N, f) applicable. These are only preparatory (merge of the classes, no operator applied yet); f is applied in the *next level's dependency stage*. Nodes created during a combination stage may not be members of an N in the same stage. At level k only combinations including at least one node created in dependency stage k need testing (L4176-4184).

Node types: Root, Dependency, Combination. Termination: when a combination stage adds nothing (example DLP `aaccadd`: 3 dependency levels, third combination level empty; both single-letter theorems a and d found, L4186-4224).

Pseudo-code from T Tables 3.2-3.6 (L4189-4297; condensed; all operators read exactly as printed, minor glyph losses only in "<>"):

```
procedure DbSearch()
  CreateRoot(root); level := 1
  while ResourcesAvailable() and TreeSizeIncreased() do
      AddDependencyStage(root)      # Table 3.3: walk child/sibling pointers;
      AddCombinationStage(root)     #   for node with node.level+1 = level and type in {Root, Combination}
      level := level + 1            #       AddDependentChildren(node)
  od

procedure AddDependentChildren(node)                       # Table 3.4
  for operator in LegalOperators(node) do
     if Applicable(operator, node) then          # = F(N,f) applicable, i.e. f depends on node's key
        LinkNewChildToGraph(node, operator)
        AddDependentChildren(node.newChild)      # recursion = same-stage chaining
  od

procedure AddCombinationStage(node)                        # Table 3.5
  if node.type = Dependency and node.level = level then FindAllCombinationNodes(node, root)
  recurse over node.child, node.sibling

procedure FindAllCombinationNodes(partner, node)           # Table 3.6 (pairs only)
  if NotInConflict(partner, node) then
     if node.type = Dependency then
        combination := Combine(partner, node)
        operators   := DependingOn(combination)
        if operators != nil then AddCombinationNode(node, combination)   # avoids useless combination nodes
  recurse over node.child, node.sibling
```

Complexity warnings: Table 3.6 only does pairs (enough for DLP and Qubic); Go-Moku needs combinations of up to **four** nodes; finding c-combinations in a graph of N nodes is O(N^c); domain-specific pruning is advised (L4299-4308).

### 2.4 Numbers (T §3.6, L4316-4433)

DLP benchmark, 2000 random axioms (100 per length 1..20). DFS without transposition table (dfs-) capped at 1,000,000 nodes, DFS with TT (dfs+) capped at 100,000; the caps cut off 129 (dfs-, 24 of those missing a solution) and 26 (dfs+, one missing) runs; db-search and the domain algorithm "triangle" always ran to completion. At length 20: |U_p| (what dfs- searches) averages **more than 1200x** |U_k|; dfs+ (TT gives a factor ~20) still visits **more than 60x** more than db-search. Hardest instance for db-search: 3,934 nodes. db-search beat triangle up to length 18, but the authors do not claim superiority (combination stage is costlier per node). Fig 3.5 is a log2 plot; no numbers extractable.

### 2.5 Applicability and conditions in games (T §3.7, L4435-4455; §5.3.2 L6180-6191; §5.3.4)

- Applies to a game when the **opponent is restricted to a single (forced) reply**, so the adversary space becomes single-agent (L4437-4443).
- In Qubic/Go-Moku the three conditions "do not hold fully": U_k "may neither be sound nor complete"; still useful when U_p is intractable; "further research is necessary" (L4445-4455). In Go-Moku the framework is monotone and non-redundant paths can be enforced, and U_g (any five or straight four) can be made singular, but completeness is unclaimed because global defence is outside the framework (L6180-6191).
- Go-Moku instance (T §5.3.2, L6036-6178): attributes S(square, {attacker, defender, empty}), |U| = 675 on 15x15; lines G5 = 572, G6 = 500, G7 = 432 (I re-derived these counts and they are right); operators: five FI (572), straight four SF (500), four FO (572), broken three BT (500), three-with-2-replies T2 (432), three-with-3-replies T3 (500) = **3,076 operators, 23,596 applications**. Each operator's pre/del/add: e.g. FO: pre = 4 attacker + 1 empty, del = {empty at s4, s5}, add = {attacker at s4 (gain), defender at s5 (cost)} **[GARBLED]** (the owner marks are lost, but the structure "delete empties, add gain+cost" is consistent with the single-agent meta-move).
- Mapping TSS -> db-search [OWN reading]: "dependent" (A93 Def 4) = "supports" (a rest square of A is added by B); "conflict" (A93 Def 6) = the merge of two key classes is empty (an attribute would be added twice or one class deletes what the other needs); the combination stage = linking independent nodes whose gain squares align.

---

## 3. Proof-number search (pn-search, PNS)

Sources: T ch. 2 (p.13-64, L763-3082) for the algorithm; T §5.4-5.5 (L6536-7009) and A93 §6-7 for Go-Moku; CPW for the modern pseudo-code and variant list.

### 3.1 Model and numbers

- **AND/OR tree**; node value true/false/unknown; internal AND = false if any child false, else unknown if any unknown, else true; OR dual. Tree solved = root true (*proved*) or false (*disproved*) (T §2.2.1, L930-981). Frontier = unexpanded or evaluated-unknown leaf. **Immediate evaluation** (children evaluated when created; used in all of Allis' game applications) vs delayed (evaluate on selection); immediate gives more information per unit of memory (T L1360-1401).
- Two assumptions: the value distribution of a frontier node is unknown and the same everywhere (T L1012-1016); so only the *position in the tree* matters.
- **Proof set / disproof set**; **proof number (pn)** = cardinality of the smallest proof set, **disproof number (dn)** likewise (Def 2.1-2.4, L1030-1047). Computation (T Table 2.3, L1372-1395; CPW L65-87; infinity glyph restored):

| Node | pn | dn |
|---|---|---|
| frontier, unknown | 1 | 1 |
| terminal true | 0 | infinity |
| terminal false | infinity | 0 |
| internal AND | sum over children pn | min over children dn |
| internal OR | min over children pn | sum over children dn |

  T's Table 2.3 prints "1" where infinity belongs **[GARBLED]**; confirmed by T L1511-1513, L1079-1081 and CPW.
- **Most-proving node** (Def 2.5, L1180-1183): a frontier node that, if proved, reduces the root's pn by 1 and, if disproved, reduces its dn by 1. Lemma (L1189-1215, by induction): every pair (smallest proof set, smallest disproof set) intersects, so there is **no conflict between trying to prove and to disprove**; one selection serves both.
- **Selection** (Table 2.2, L1320-1347): descend from the root; at an OR node take the child whose pn equals the node's pn (min pn), at an AND node the child whose dn equals the node's dn; ties -> leftmost (children are not kept sorted; "leftmost equal" halves the average selection cost vs taking the true minimum, L1407-1415).
- **Main loop** (Table 2.1, L1276-1292) plus Tables 2.4-2.5 in T:

```
ProofNumberSearch(root):
  Evaluate(root); SetProofAndDisproofNumbers(root)
  while root.proof != 0 and root.disproof != 0 and ResourcesAvailable():
      n := SelectMostProving(root)
      DevelopNode(n)           # GenerateAllChildren; for each child: Evaluate, SetProofAndDisproofNumbers
      UpdateAncestors(n)       # recompute pn/dn up to the root
  value := true if root.proof = 0; false if root.disproof = 0; else unknown
```

  CPW/Kishimoto 2012 variant (CPW L49-151): same, but `expandNode` **stops evaluating children as soon as one child decides the node** (AND: a child with dn = 0; OR: a child with pn = 0) - a cheap shortcut that T's DevelopNode (evaluate all children) does not use; `updateAncestors` returns the first ancestor whose numbers did not change.
- **Current-node enhancement** (Def 2.6, Tables 2.6-2.7, L1604-1713): stop updating when an ancestor's (pn, dn) are unchanged, and start the next selection from that ancestor ("current node"), because it lies on the next most-proving path too. Cuts node traversals per iteration from O(depth) to roughly constant; should be in every implementation (L1628-1630). An even faster "instant" selection by caching the most-proving node per subtree was rejected as memory-hungry (L1632-1638).

### 3.2 Memory handling (T §2.3.1, L1480-1586)

- Memory is **linear in tree size** (the bottleneck), unlike depth-first alpha-beta.
- **Delete solved subtrees**: a solved node is never on the most-proving path and its (pn, dn) either solve the parent or do not matter (true child: pn 0, dn infinity); so drop them when summing (L1490-1526). On awari this halves working memory on solved problems (T L2880-2886, Table 2.12).
- **Pn2-search** (with Stef Keetman, then unpublished): first-level pn1 calls, as the "evaluation" of a node J, a second-level pn-search with node bound N = current pn1 tree size; if unsolved, the (pn, dn) of J are initialised from the pn2 root and the pn2 tree is discarded. Properties as stated (L1564-1573): a pn1 tree of size N has searched about (1/2) N^2 nodes **[GARBLED: "1 N2 / 2"]**, memory about 2N nodes, small code change. Experiments on awari and draughts: pn2 investigates on average **3x as many nodes** as plain pn for the same problem, a constant factor; thus with a memory limit of 10^6 nodes pn2 can usefully investigate about 10^12 (extrapolation, L1574-1586). Caching the M last pn2 trees (Schaeffer) is suggested.

### 3.3 Using domain knowledge: initial (pn, dn) (T §2.3.3, L1640-1920)

pn/dn are lower bounds on effort; replace "1" by a better effort measure:

1. **Evaluations as effort**: an OR node with n children gets pn = 1, dn = n; an AND node pn = n, dn = 1. Give-away chess ("Prove-away", 30 self-play games, 25,000-node cap): factor ~2.2 fewer node developments, ~8 fewer nodes, average branching of developed nodes 2.9 vs 10.5, ~20% slower per node, overall CPU gain > 6 (L1766-1822). Table 2.8 itself is **[GARBLED]** (interleaved columns); the numbers quoted are from the prose.
2. **Domain effort**: awari "stones still to be captured" as pn/dn (L1838-1852); on 337 awari positions it won about a factor 2 over standard init.
3. **Depth-related**: init pn = dn = f(depth); larger values deeper -> broader/shallower trees. Motivation: a chess mating problem where aimless single-piece maneuvers produced a mate in 114 although a mate in 4 existed (L1862-1870). For Go-Moku: init to the number of full moves from the root, i.e. **1 + floor(d/2)** at depth d (T L6774-6777), "ensures that forcing lines are not searched too deeply before sufficient alternatives have been tried" (L6779-6781); it was used in the solving run.
- Caveat (L1900-1920): non-standard init violates both principles (indistinguishable frontier nodes; pn/dn as lower bounds); othello needed many tuning experiments. "We lack a proper understanding" of the effects.

### 3.4 Transpositions (T §2.3.4, L1922-2083)

- Pn-search is defined on trees; with shared subtrees the sums over-count, so pn/dn become upper bounds. Correct algorithms exist (Schijf 1993) but are slow/memory-hungry.
- **DAGs** (games where every move is irreversible, "a conversion": connect-four, qubic, go-moku, where the stone count strictly increases): practical solution = compute pn/dn from children as if a tree, but on update recurse to **all parents**. It is knowingly wrong (Fig 2.4: root gets (2,2) instead of (1,1) and the wrong node is selected) but "has advantages similar to those of standard pn-search" in connect-four, qubic, go-moku (L1985-1998).
- **DCGs** (repetitions): the same node can have different pn/dn depending on its path (GHI-type problem); practical fixes (L2064-2080): only positions created after a conversion move may have several parents, or at most two nodes per position (first occurrence / own ancestor, the latter initialised to the repetition value); then a proof of the repetition value is "not fully reliable" but a proof of the *opposite* value is correct. Needs "further investigation".

### 3.5 Variants and relatives named in the sources

- Direct ancestor **conspiracy-number search** (McAllester, mid-80s): number of nodes that must change value to change the root value; uses a heuristic evaluation, pn-search does not; pn selection beat cn selection in unpublished connect-four tests (T §2.5.1, L2912-2965). **"?-cn search"** (the prefix glyph is lost [GARBLED], presumably alpha-beta-cn), a hybrid of SSS* and pn-search (L2997-3007); **SSS***, **B***, **A*** comparisons (L2967-3077). **mst*** (add 1 to pn/dn at each internal node -> finds a minimal solution tree), future work (L3069-3078).
- CPW list (L153-175): **Pn2**, **PN* / C*** (Seo; iterative-deepening depth-first df-pn style), **PDS**, **PDS-PN** (depth-first first level, best-first second), **parallel PNS** (Kishimoto-Kotani 1999, Kaneko 2010). CPW's bibliography also lists Connect6-specific pn work (Wu & Lin relevance-zone search, job-level PNS) at L251-267 - titles only, not read.
- T §2.5.4: pn-search selects on cheapest *remaining* effort, so it does not guarantee the smallest solution tree (L3062-3067).

### 3.6 Results

**Awari comparison** (T §2.4, L2326-2900). 337 "final" test positions (from 1599 unique positions of 22 games; 496 with <= 17 stones excluded as DB lookups, 764 unsolvable by all four within 500,000 nodes excluded). Four algorithms: alpha-beta (iterative deepening), alpha-beta with TT (250k entries), basic pn, stones pn. Total nodes: alpha-beta **2,437,035,522** (avg 7,231,559; factor 128.8 vs best), alpha-beta+TT **1,285,839,816** (3,815,548; 68.0), basic pn **28,214,875** (83,723; 1.5), stones pn **18,918,032** (56,136; 1.0) (Table 2.12, L2598-2603). Average maximum pn-tree in memory 42,767 (basic) / 25,505 (stones) - **[GARBLED: row assignment is my best reading of an interleaved table]**. On the hard positions pn-search beats alpha-beta+TT in > 85% of cases; the advantage grows with problem size; alpha-beta ran ~10,000 nodes/s on a SPARCstation 1+ and the others about half that (L2404-2412). Conclusion: pn-search wins because awari trees are **non-uniform** (T §2.4.8, L2871-2898); hard positions took at most ~1.5 million pn nodes.

**Go-Moku solved** (T §5.5, L6783-7009; A93 §7, L489-553):
- Victoria = pn-search with db-search (TSS) as the evaluation function. Black = OR, White = AND; leaf value true if black (the player to move at an OR node) has a winning threat sequence; draw and white win both = false (T §5.4.1, L6543-6561).
- Evaluation also at AND nodes (db-search for White -> false).
- Game tree split by hand into several hundred subproblems (full tree did not fit memory); hand-chosen black moves = several hundred, <1% of the tens of thousands of black moves in the solution (L6817-6830). Database of solved subtrees was reused as a transposition table: searches of several 100,000 nodes shrank to a few thousand when hitting it (L6809-6815).
- Hardware: 11 SPARCstations (A93 says 10), 64-128 MB, 16-28 MIPS, 150+ CPU-h per day available overnight, pn trees up to 1 million nodes in RAM (T L6842-6858; A93 L515-523).
- **Free-style Go-Moku: 11.9 CPU-days, 5.3 million pn nodes; standard Go-Moku: 15.1 CPU-days, 6.3 million nodes** (T L6859-6868; A93 Table 5 gives 1.1 and 1.3 million CPU-seconds, about 12.7 and 15.0 days, with the same node totals - the thesis excludes killed/test runs; small discrepancy, not an error). About **5 nodes/s**; 10-20 independent db-searches per internal node, 50-100 db-searches per CPU-second, **50-130 million db-searches** in total (L6870-6881). Standard variant = 20% larger tree.
- **Solution tree** 138,790 (free-style) / 153,284 (standard) database records, depth **35 ply**; 1 in 40 created nodes is in the solution (L6883-6889; per-depth histogram Table 5.1, L6892-6912, mostly 5,000-21,000 nodes at plies 4-11).
- Verification (T §5.5.4, L6963-7009; A93 L505-513, L538-539): the program has almost 20,000 lines of C; a checker module walks the final DB (black positions: at least one successor present; white positions: every legal move has either a winning threat sequence or a successor in DB). It found missing records (machine crashes) and a bug in the related-squares generator; db-search itself is the only part not independently checked (so tested "meticulously", especially the extension-square counterplay). Checking took about 1 CPU-day per variant.

### 3.7 Go-Moku-specific pn-search enhancements (T §5.4.2, L6563-6781)

These are the pieces relevant for our offline analysis:

1. **Transpositions as DAG** + 8 board symmetries.
2. **Restrict the prover (OR node) to the N = 10 most promising children** after first filtering to moves that answer the opponent's existing threats; sound for *proofs* (a proof in the restricted tree is a proof in the full tree) but a disproof is then only "not proved". Move-order score: 4 per two-reply three, 3 per three-reply three, 2 per broken three, 2 per open two, 1 per broken two; **no points for fours** (a four only matters if it answers a threat or yields a winning sequence, and those are caught by db-search at evaluation) (L6579-6619).
3. **Related squares for the defender (AND nodes)**: an empty square is related to a winning threat sequence if a defender stone there would stop it. Exact set is expensive; a superset S is used = squares on the threats' lines + squares that could serve counter-threats in the global-defence searches (R subset of S, "without proof", checked empirically in 5.5.4). Restrict White to S. **Iterated**: play one square s of S; if db-search then finds *another* winning sequence with related set S1, only S intersect S1 remain; Fig 5.8: 35 related squares -> 19 (L6636-6737).
4. **Null move test**: a position is an *implicit threat* if db-search finds a winning sequence after a null move for the opponent; used for the attacker's move choice and the related-squares logic (L6653-6697, L6739-6744).
5. **Implicit-threat heuristic**: an OR node that is not an implicit threat for black evaluates to false (except while < 9 stones); black can keep every move an implicit threat from about move 11 on (L6746-6757).
6. **Depth-based pn/dn initialisation** (above).

### 3.8 Operating points to remember

Go-Moku branching factor > 200 (A93 L494; T L6581); 5 pn nodes/s was acceptable because every internal node costs 10-20 independent TSS calls (T L6877-6879, excluding the global-defence searches, which is why this 50-100 calls/s on a 16-28 MIPS SPARC is faster than the 0.1 s/call on an RS6000 that A93 quotes for the full module including defence checks).

---

## 4. Adaptation to gravity six-in-a-row [OWN]

Everything in this section is my reasoning, built on the mechanisms in sections 1-3. Nothing here is validated by experiment. Source anchors are given where an idea is borrowed.

### 4.1 Rule assumptions I rely on (please confirm; see section 5)

- Board 8 rows x 12 columns, gravity, six or more in a line wins (free-style, so the "extension square" bookkeeping of T §5.3.5 is not needed).
- Normal move: drop a disc on top of a column. Special move: drop a disc, then clear the 3x3 block centred on **the cell where it landed** (so the landing cell, i.e. the placed disc itself, is cleared too; at most 8 neighbours plus the centre, some are empty or off-board), then gravity re-applied; **after any move, any six-line of either colour on the board means the mover wins**.
- Each side may use the special once per 10 of its own turns; the counter is part of the state.
- Derived facts (computed by me): there are **134** six-windows (56 horizontal, 36 vertical, 21+21 diagonal), versus 500 on Allis' 15x15 board; a cell lies in at most 15 windows (average 8.4). Branching factor is at most 12 normal + 12 special = 24 (Go-Moku: > 200).

### 4.2 Threat taxonomy (the six-in-a-row analogue of Allis' table in 1.1)

I define threats through **windows** (6-cell lines) instead of hand-drawn patterns, the way T's G5/G6/G7 sets do (T L6047-6054). Let a window be "live for A" if it has no defender disc.

| Name (mine) | Allis analogue | Pattern | Immediate? | Defender replies | Category (T L6219) |
|---|---|---|---|---|---|
| **six** | five | 6 A discs | game over | - | 0 |
| **F5** ("five-threat") | four | live window with 5 A discs + 1 empty cell g | needs g playable | exactly 1: g | 1 |
| **O5** (double gain) | straight four | two different empty cells g1, g2, each completing some window (e.g. 5 in a row with both ends empty) | wins if both (or a stack, see 4.3) are playable | none | 1 |
| **O4** ("open four") | three | 4 consecutive A with room for a 5-run having both ends free after one more move; e.g. 7-cell line `. X X X X . .`-shaped | no | 2 to 4 (end squares and the cells beyond them) | 2 |
| **B4** (broken four) | broken three | 4 A discs in a 7-cell line with one gap, enough room | no | 3 to 4 | 2 |

[OWN] Counting rule for replies: do not tabulate by hand. The reply set R(move) = every empty square s such that, if the defender occupied s, the attacker would no longer have the follow-up (F5 for the O4 case, O5 for the B4 case); compute by one-ply lookahead on the window counters. That gives T's "cost squares" automatically, including the odd extra replies near the board edge.

As in Allis, "category i threatens to win in exactly i moves", and the global-defence rule carries over unchanged: against a category-i threat the defender may interject only threats of category < i (T L6224-6230). With only F5/O5 (category 1) that rule collapses to "the defender may interject a six", which is a one-line check; this is the main reason to start with a pure-F5 search (4.5).

### 4.3 What gravity changes

1. **Only F5 threats whose gain square g is *playable now* are forcing.** If g sits above an empty cell, the defender cannot block it and the attacker cannot play it: it is a *latent* threat. Consequences:
   - The cell p directly below g becomes *poisoned for the defender* (if he plays p, the attacker takes g and wins) - a hard constraint that removes a legal move from the defender's list (a free move-ordering/pruning rule and a zugzwang source).
   - If the attacker plays p himself the defender blocks g, so p is also bad for him unless p creates another threat. This is the classic connect-four parity logic; with 8 rows (even) and the special move around, I would **not** hard-code parity rules (the special move deletes discs and shifts column heights by arbitrary amounts, so any parity argument must be re-derived per state). Use it only as an evaluation feature.
2. **Playability is a precondition attribute.** In the db-search framework (section 2) a threat move on cell e is applicable only if the cell below e is occupied; so "supports" (add(f1) intersect pre(f2)) automatically includes *support* squares: a move or a forced reply that fills the cell below e supports every threat that plays e. Dependency in gravity = shares a rest square **or** fills a support cell. This is a clean extension of T Def 3.15 and A93 Def 4 that I believe needs no change to the algorithm, only to the Applicable() test. [OWN]
3. **The forced reply changes playability for the attacker.** After the attacker plays e1 threatening g, the defender's forced reply at g makes the cell g+1 playable, which can be exactly the gain square of the next threat ("stacked" threats). Therefore the defender's reply must really be placed on the board (as in A93's cost squares) - cheap here, since a single-reply F5 has exactly one reply. This is the same single-agent linearisation as Allis' "sequence of fours" (T L5816-5818: "the whole sequence of moves is forced"), now with the board physically updated.
4. **Stacked gains win.** If after the attacker's move two gain cells exist in the *same column*, one directly above the other (g and g+1, both completing windows), then g is playable, the defender must take g, and the attacker then takes g+1. Equivalent to O5. More generally, a position is a win-by-threat when the set of *simultaneously reachable* gains has size >= 2 (distinct playable cells, or a playable cell with a gain right above it), before specials are considered. [OWN]
5. **Latent threats as a resource.** Allis' pn-search move ordering gave no points for fours and relied on db-search at the evaluation (T L6610-6615). In gravity, quiet moves that *create latent threats* (g not yet playable) do matter, since they create poisoned cells without forcing; include them in the ordering score (e.g. 3 points per latent F5, 2 per latent O4 end). [OWN, untested]

### 4.4 The special move: universal defence, and an attack

**Universal defence.** Suppose A makes an F5 with playable gain cell g and the defender D has the special available. [OWN derivation] Every window contains, next to g, at least one neighbour cell on its line (left/right, diagonal, or the cell below for a vertical window), and that neighbour is an A disc. A special at the column of g lands exactly on g, so its 3x3 block (rows h-1..h+1, columns c-1..c+1) contains that neighbour. Hence **a special move at column(g) always removes at least one disc of the threatened window**; it is legal whenever g is playable. Gravity may let discs from above refill the cleared cells, so the destruction is only *nominal* until simulated, but typically the threat is dead and D has spent one move for one move. Consequences:

- A forced-win claim made by F5 sequences is exact only if **D's special is unavailable** (counter < 10). When D holds it, every F5 has a reply set = {block g} + up to 12 specials (the legal ones), not 1 reply. That is the AND-node generalisation of Allis' single reply. T's single-agent trick (T §5.3.1) survives only in the "D on cooldown" case.
- A **robust** attack under an available special is a double threat that no single special kills: gains g1, g2 with disjoint windows and columns more than 2 apart (block spans 3 columns), re-verified by simulating all specials. Everything else gets refuted by the special.
- Timing: the right moment to launch a VCF is when the opponent has just spent his special (cooldown window up to 10 own turns). Conversely, a side that uses the special defensively opens a 10-turn vulnerability window. Both are search-state features, so the two cooldown counters must be in the TT key and in the position hash.
- **Special as an attack.** (i) After any special move the mover wins if *any* six exists (either colour), including sixes made only by gravity shifting columns that lost 0-3 cells. So each node where the side to move has the special needs a **12-way "special win scan"** (simulate each column; check all windows). (ii) For the same reason a quiet move is unsafe if it lets the opponent (special available) shake the board into a six of *either* colour. (iii) The special can remove a defender's block (cost square) on a latent threat, turning latent threats into immediate ones; this is a dependency link the framework would model as the special "deleting" a defender-owned attribute, which breaks monotonicity (below).
- **Framework conditions broken by the special** [OWN]: T's db-search completeness needs a *monotonous* path (an attribute deleted is never recreated; T Def 3.12). A special move deletes and gravity recreates "empty" attributes, so key-class completeness (Thm 3.1) does not hold across special moves. Keep specials out of the dependency framework (use them only as explicit AND-children / OR-children of the node), and use db-search ideas only for the special-free F5 core.

### 4.5 A compact VCF-style search for the 100 ms budget

Goal: `vcf(me, depth)` = does `me` win by continuous F5 threats, the defender's only options being block / immediate win / (if available) special? Exact in this restricted sense because the F5 reply set is fully enumerated, so Allis' separate "global-defence" verification (T §5.3.3) is unnecessary: the defender can answer a five-threat only by blocking, by winning at once, or by a special; none of these needs a second search for counter-threat sequences. This is the same reason T Fig 5.3a "fours only" sequences are fully forced.

**Data (Java, no allocation in the hot loop):**
- `byte[96] cell`, `int[12] height`, window tables `int[134][6] winCell`, `int[96][] cellWin` (<= 15 each), per-window counters `byte[134] cntA, cntB` updated incrementally on place/remove (<= 15 updates per disc); a window is "live for X" if the other colour's counter is 0. Six detected when a counter reaches 6.
- Zobrist keys for 96 cells x 2 colours, plus the two cooldown counters and side to move; TT of 2^20 entries (`long[]` keys + `byte[]` results, ~9 MB), storing proven win/loss and the failing depth.
- Moves are ints: column 0..11, plus a special bit.

**Node (sketch, condensed):**

```
boolean vcf(me, d):
  if myWinNow(me)  return true                   # live window with 5 of mine whose empty cell is playable,
                                                 # or (special avail.) 12-way special-win scan hits
  opp = other(me)
  G = distinct playable gains of opp (live windows, 5 of opp, empty cell playable)
  if |G| >= 2 and no single move of mine (incl. special) removes both:  return false
  if |G| == 1:  cand = { block at G.cell }                # forced; may itself create a threat
  else          cand = threatMoves(me)                    # live windows with 4 of mine and 2 empties e1,e2:
                                                          # play e1 if playable and e2 playable afterwards
                                                          # (e2 playable now, or e2 == cell above e1)
  if d == 0 or cand empty: return false
  for m in order(cand):                                   # order: >=2 gains first, forced blocks, stack setups, rest
     play(me, m); g = distinct playable gains of me
     ok = (g != empty)                                    # else not a threat; skip unless m was a forced block
     if ok:
        D = ( |g| >= 2 ? {} : {block g} )  ∪  ( opp.special available ? legalSpecials : {} )
        for o in D: ok &= (!winsAt(opp,o)) && (play(opp,o); r = vcf(me, d-1); undo; r)
        if |g| >= 2 and no special saves: ok = true       # stacked/double threat
     undo
     if ok: record principal move; return true
  return false
```

- **Dependency principle (A93 P1)**: optionally restrict `cand` after the first threat to moves whose window contains the previous gain/reply cell or a support cell (the cell above a reply). A93 needed it to kill 8!-type permutations (A93 L204-210); with a TT on 134 windows I would first measure without it, then enable it as a flag. The **combination stage** (A93 L383-402, T §3.4) corresponds to allowing *independent* threats at the root only, or when no dependent threat exists - an option, not a first-version requirement.
- **Budget control**: global node limit and `System.nanoTime()` check every 256 nodes; on expiry return UNKNOWN (never "win"). An UNKNOWN is not stored as a loss.
- **When to call it inside alpha-beta** (my proposal; all untested):
  1. At the root: win-in-one scan (normal + 12 specials); if the opponent has a playable F5, the only moves are the blocks (or an own win); then `vcf(me)`; then **null-move VCF for the opponent** (A93 L500-516, T L6653-6697): if he has a VCF after a null move, restrict candidate moves to the **related set** (below) and treat everything else as losing.
  2. In the tree, on every node with a live 4-window for the side to move (cheap precondition from the counters); give it a small node budget (a few hundred nodes) at leaf/depth <= 1 as a replacement for quiescence, and a mate-like score on success.
  3. Threat extension: if the side to move faces a playable immediate gain, the position has one legal non-losing move (block / win / special); do not charge a ply for it.
- **Related-squares analogue for gravity** (T §5.4.2 / L6640-6709) [OWN]: after a VCF is found, the defender move set can be restricted to (a) moves in *columns that contain any cell of the sequence* (line cells, replies, or support cells), since a disc anywhere else cannot change which cells are playable for the sequence; (b) specials whose 3-column span intersects those columns; (c) columns where the defender owns a live window with >= 4 discs (counter-threat interjections, i.e. T's global defence). The superset property is the thing to unit-test: brute-force all 24 defender replies on random positions and verify that no reply outside the set breaks the VCF (the checker idea of T L6989-6998).
- **Move ordering and pruning for latent threats**: rule out (or demote) normal moves into poisoned cells (cell below an opponent latent F5) at all search nodes.
- **Speed expectations**: A93 quotes TSS at ~5,000 nodes/s (1993, RS6000-class; A93 L452-453, "ten times faster than Polygon") and 0.1 s per decision; I have no measured number for Java on this board. The window-counter design should be far faster per node, but plan the budget by benchmarking, not by assumption.

### 4.6 Proof-number search for offline opening analysis

Use PNS offline (no 100 ms limit) for: proving that a chosen opening line is a forced win for a side, verifying a book, or finding the shortest "trap" lines. Mapping to T §5.4.1: OR = side we want to prove a win for; AND = other side; value true = that side wins; false = draw or other side wins (two-valued, T L6546-6551; awari did the same with draw = loss, L2467-2469).

- **Leaf evaluation** = the section 4.5 `vcf` (both sides): true if the OR side has a VCF (in the right cooldown mode), false if the AND side has one; else unknown. This is exactly Victoria's structure (T §5.4.1), and the VCF result is a proof, so the pn values at its leaves are exact.
- **Branching is small** (<= 24 vs > 200): the "restrict the prover to the N = 10 best children" (T L6591-6600) is probably unnecessary, or can be applied only to specials. Use T's ordering idea (score by threat-creating potential) as a tie-break, plus latent-threat points (4.3).
- **Initial (pn, dn)**: first try "evaluations as effort" (OR: pn 1, dn = #children; AND: pn = #children, dn 1; T L1717-1722) because branching varies a lot with special availability; test T's depth penalty 1 + floor(d/2) (T L6774-6777) if forcing lines run too deep. Both are heuristics that violate the PNS assumptions (T L1900-1920), so measure.
- **Transpositions are a DCG problem here.** T's DAG shortcut is justified only because every Go-Moku/Qubic/Connect-Four move is a conversion (stone count strictly increases; T L1953-1959). The special move removes up to 9 discs, so positions can repeat; the cooldown counters in the key make cycles long (>= roughly 20 plies) but not impossible. Use T's practical rule (T L2064-2080): give a repeated position on the current path the value *false* (= "not a win for the prover"). Then a **proof (true) is reliable**, a disproof (false) is not - acceptable if we only want proven wins. [OWN deduction from T L2076-2080.]
- **Memory**: node arrays in `int[]` (pn, dn, firstChild, nChildren, parent ~ 20-24 B per node), custom open-addressing hash from Zobrist to node index, delete-solved-subtrees on (halves memory, T L2880-2886), current-node optimisation (T Table 2.6-2.7). If the tree outgrows RAM, use PN2 (memory ~ 2N for N effective nodes, ~3x more work, T L1564-1579) or df-pn with a TT (CPW L161-163; not described in detail in the sources).
- **Persistence**: store every solved node (key, side, cooldowns, best move) in a database and probe it as a TT in later runs; T L6809-6815 reports 100,000+-node searches collapsing to a few thousand nodes by hitting the database early.
- **Splitting and verification**: solve opening subtrees independently and merge (T §5.5.2); then write a checker that walks the final book (every our-turn position has a stored winning move, every opponent-turn position has every legal reply either covered or a VCF), as in T L6989-6998. Because PNS and the VCF share the board code, test the VCF against a plain alpha-beta on small random positions - T's checker did not independently verify db-search either (T L6994-6996).

---

## 5. Open questions

**Rules to pin down before any code (they change the search):**
1. Cooldown semantics: "at most once every 10 own turns" - is the special available again on the 10th own turn after use (9 normal turns in between) or the 11th? Does the counter start at available or at 0? One off-by-one here shifts every "defender on cooldown" decision.
2. Is the placed disc really cleared with the block (my reading of "centred on it"), and is the special legal in a column of height 7 (landing cell in the top row; block clipped at the board edge)? Is it legal on an empty board?
3. If gravity after a special produces sixes for both colours, the mover wins (as stated) - and is gravity applied once, or iterated until stable? (Columns are independent, so once should suffice, but confirm.)
4. Draw conditions: full board? move cap? Any rule against repetition? (Matters for the PNS repetition value, 4.6.)
5. Confirm that the "any six present after a move wins for the mover" rule only matters after a special (a normal move adds one disc of the mover's own colour).

**Where the sources do not answer the gravity case:**
6. Do the db-search completeness conditions (monotone, non-redundant, singular goal; T §3.3.3) hold for the special-free F5 game with playability as a precondition attribute? My argument (4.3) says dependency extends naturally; I have not proved soundness/completeness. T itself says that for Qubic/Go-Moku "further research is necessary" (T L4450-4455). Practical alternative: plain DFS with a transposition table and test whether the permutation blow-up (A93's 6,000-variation example) really occurs on 134 windows.
7. Are category-2 threats (O4/B4, the analogues of threes) worth adding? Allis' Go-Moku engine needs them; but in gravity their end squares may be unplayable, the defender's reply set is larger, the special destroys them cheaply, and the global-defence search must be run for every threat (T §5.3.3). I would not add them before the F5 search is measured.
8. How often does an available special actually refute a VCF in real play? If strong opponents spend the special early, the "defender on cooldown" window is common and the single-reply linearisation pays; if they hoard it, the AND-node version (block + ≤12 specials) is the real search and its cost matters. Needs self-play statistics.
9. Gravity-only win by column shift (special-win scan, 4.4): how rare are positions where it fires? The 12-way scan costs 12 simulations per node when the special is available; it may need to be restricted to nodes that pass a cheap prefilter.
10. Parity: with the special moving column heights arbitrarily, are there any usable parity invariants for the 8-row board? I assumed not.
11. Is the first player's value (win/draw/loss) known or plausible? PNS on Go-Moku succeeded because a strong evaluator (TSS) made many nodes terminal and the first player wins; for our game no strong prior exists, so the proof effort for the empty-board root is unknown.

**Source/reading gaps and discrepancies:**
12. **Not read** (outside the requested scope): T ch. 4 (Qubic), notably §4.3 "db-search framework for qubic" and §4.3.3 enhancements, where the search space of threat sequences is searched completely and the reply sets are small - probably the closest analogue to a gravity game; T ch. 6 (connect-four remarks); the PN papers cited in CPW (df-pn, Kishimoto et al. 2012). Recommend reading T L4658-4967 next.
13. Text damage to re-check against the PDF if exactness matters: A93 Table 4 (per-position node counts), T Table 2.8 (give-away chess) and the last column of Table 2.12, T Def 3.12's formula, the owner marks (attacker/defender/empty) in T §5.3.2's operators, and the exact meaning of "rest squares" (A93 Def 3).
14. Small inconsistencies between sources, none material: A93 says 10 SPARCstations, T says 11; A93 Table 5 CPU seconds (1.1 and 1.3 million = 12.7 and 15.0 days) vs T's 11.9 and 15.1 days (T excludes killed/test runs); the 4th Olympiad result in A93 used standard Go-Moku, the demonstration examples use free-style; CPW's `expandNode` stops evaluating children early whereas T's DevelopNode evaluates all.
15. All Go-Moku run times are for 1993 hardware (16-28 MIPS); the node counts (5.3 and 6.3 million pn nodes, 138,790 and 153,284 solution records) are hardware-independent and are the figures to quote.
