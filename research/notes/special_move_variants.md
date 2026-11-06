# Connect Four variants with special/destructive moves, limited-use powers, and AI handling (for the Quantum Connect bot)

## Q1. Connect Four variants (Pop Out, Pop 10, Five-in-a-Row, Power Up, Blast!, Shots, Bomb, Plus): rules, solved results, AI papers

### Takeaway
Standard Connect Four is solved, but I found only one rigorous result for a removal variant: a 2014 Oulu master's thesis weakly solved Pop Out (7x6, first-player win) and flagged repetition and extreme search depth as the new problems. For the power-chip and bomb variants (Power Up, Blast!, Shots) I found no solving or AI papers, and sources disagree on the Power Up chip rules.

### Cited Findings
- Pop Out: each turn you either drop a disc or remove one of your own discs from the bottom row, and the discs above shift down. — [Wikipedia: Connect Four](https://en.wikipedia.org/wiki/Connect_Four); same rule in [Rarepike variants](https://rarepike.com/four/variants/)
- Rarepike adds that if your pop creates a four-in-a-row for the opponent, the opponent wins, and it labels Pop Out "Not solved". That label is a hobby site; it is contradicted by the Oulu thesis below. — [Rarepike variants](https://rarepike.com/four/variants/); contradicted by [Oulu thesis](https://oulurepo.oulu.fi/handle/10024/39813)
- Pop Out is solved: "Computer analysis of Connect-4 PopOut" (Jukka Pekkala, University of Oulu master's thesis, 2014). Per the abstract as relayed by search (I could not open the full text, the repository returned 403): PopOut is a first-player win on standard 7x6; smaller boards are either first-player wins or draws by repetition; proof-number search weakly solved it with no modifications. — [Oulu repository](https://oulurepo.oulu.fi/handle/10024/39813)
- The same thesis says plain alpha-beta needed changes. Two reasons were reported: the game tree is much deeper because pops make the game not guaranteed to end, and the graph history interaction (GHI) problem appears because positions can repeat. For alpha-beta the first player was forbidden from making non-winning pop moves and any game exceeding a chosen length was scored a first-player loss; under those handicaps the first player still wins. — [Oulu repository](https://oulurepo.oulu.fi/handle/10024/39813) (abstract via search summary, not full text)
- Pop 10: players fill the board bottom-up; on your turn you remove one of your own bottom discs; if that disc was part of a four-in-a-row it is set aside and you go again, else it is re-dropped on top and the turn passes; first to set aside ten wins. Rarepike describes it differently (pop one of the four, or a bottom disc of a full column). The two sources differ in detail. — [Wikipedia](https://en.wikipedia.org/wiki/Connect_Four); [Rarepike variants](https://rarepike.com/four/variants/)
- Five-in-a-Row: Wikipedia says a 6x9 grid with two pre-filled alternating columns added at the sides; Rarepike says larger boards such as 9x6, 8x7, 9x7, 10x7 and calls it "much harder to solve computationally" without claiming a solution. — [Wikipedia](https://en.wikipedia.org/wiki/Connect_Four); [Rarepike variants](https://rarepike.com/four/variants/)
- Power Up (Hasbro), with conflicting descriptions. Wikipedia: "Power Checkers", each usable once per game: anvil (pop out all pieces below it), wall (take a second consecutive non-winning turn), x2 (unrestricted second turn), bomb (pop out an opponent's piece). Rarepike: limited supply (typically 2-3 per type per player); anvil knocks out the opposing disc directly below it and takes its place; wall permanently blocks a slot; bomb clears the entire column (both colours) and is itself removed; a winning four must be all the same colour and cannot mix standard and special discs. I found no "Ninja" chip in any source. Treat the exact semantics as unverified; check the Hasbro instruction PDF for a given edition. — [Wikipedia](https://en.wikipedia.org/wiki/Connect_Four); [Rarepike variants](https://rarepike.com/four/variants/)
- Connect 4 Shots (Hasbro, 2018): coloured balls bounced into the grid. Connect 4 Blast! (Hasbro): players knock out discs by firing Nerf foam darts from blasters. Both are physical-dexterity variants, not discrete-move games. — [Wikipedia](https://en.wikipedia.org/wiki/Connect_Four); [Hasbro instructions (Blast, UK)](https://instructions.hasbro.com/en-gb/instruction/connect-4-blast-game-with-nerf-blasters)
- A fan game on itch.io has a bomb rule very close to ours: "every 8 turns you drop a bomb piece, destroying a column". No AI is described. — [itch.io Connect 4](https://xanderath.itch.io/connect-4)
- Other solved/related results: classic 7x6 is a first-player win (centre column start, win on or before move 41; adjacent columns draw; outer columns lose), solved independently by James Dow Allen and Victor Allis in 1988. — [Wikipedia](https://en.wikipedia.org/wiki/Connect_Four)
- Misère Connect Four (avoid making four) is solved as a second-player win, with generalisations to Connect-k on w x h boards. — [arXiv 2410.05551](https://arxiv.org/abs/2410.05551)
- Infinite Connect-Four was solved as a draw (Yamaguchi et al., 2012). — [Wikipedia](https://en.wikipedia.org/wiki/Connect_Four)
- Hasbro "Connect 4 Plus": I did not find a source that describes it. Not covered.

### Inferences
- Because the only solved destructive variant (Pop Out) needed proof-number search and handicapped alpha-beta, nothing in the literature gives an off-the-shelf evaluation for a "destroy 3x3 around the drop point" move. Our bot has to derive its own.
- Our game differs from Pop Out in an important way: the special move is cooldown-gated, so the game stays finite in practice (the disc count only grows between special moves, as long as each cycle adds more discs than it removes). That removes the repetition problem Pekkala hit, unless the cooldown is short and the bomb frees more cells than the intervening drops fill. This is my reasoning, not a sourced claim.
- Bomb semantics vary even inside one product line (anvil/bomb above), so Quantum Connect's own rules and engine source are the only authoritative reference.

### Gaps
- No access to the full Oulu thesis text, so its search-algorithm details, node counts and evaluation/move-ordering advice are unknown.
- No source found on Pop 10 or Power Up being solved or analysed by AI, no "Ninja" chip, no Hasbro "Connect 4 Plus" description, and no strategy guide for Power Up chips.
- The Blast! results mentioning "explosive discs" came from a third-party PDF that I judged unreliable and did not use.

## Q2. Games with destructive or cooldown-limited moves in AI research: modelling resources/cooldowns and deciding when to spend them

### Takeaway
Pommerman (Bomberman-like) models bombs as ammo that returns when a bomb explodes, and compares MCTS with RHEA, finding MCTS ahead in several settings. Atomic chess is the closest "explosion as a normal move" analogue, but the sources I could reach give rules and engine names only, not search tactics or timing heuristics for spending a limited power.

### Cited Findings
- Pommerman rules: each agent starts with one bomb of ammo; placing a bomb decreases ammo by one and the ammo returns when that bomb explodes; each bomb takes the agent's current blast strength (starts at 3). Ammo and blast strength are part of the agent's observation/state. — [Pommerman README](https://github.com/MultiAgentLearning/playground/blob/master/pommerman/README.md) (as quoted by search; page not fetched directly)
- Pommerman is multi-player and partially observable. A 2019 AIIDE paper compares two statistical forward planning methods, MCTS and the Rolling Horizon Evolutionary Algorithm (RHEA); the abstract says MCTS outperforms RHEA in several game settings, and lists trap-move identification and opponent modelling as future work. The abstract page does not describe how either method treats bombs or ammo. — [Perez-Liebana et al. 2019, summary page](https://gaigresearch.github.io//2019/06/15/perez2019pommerman/); [paper PDF](https://ojs.aaai.org/index.php/AIIDE/article/download/5226/5082) (blocked by bot protection when I tried it)
- Atomic chess: a capture removes the captured piece, the capturing piece, and all non-pawn pieces on neighbouring squares; captures that would explode your own king are illegal; kings may stand next to each other; en passant explosion is centred on the target square behind the pawn. The wiki's engine list names Atomystica, Pulsar, PyChess and Sjaak and cites Droste and Fürnkranz (2008) on learning piece values for chess variants. It has no search or evaluation detail. — [Chessprogramming: Atomic Chess](https://chessprogramming.org/Atomic_Chess)
- A 2025 Chalmers bachelor's thesis built an atomic engine from a deep CNN plus modified MCTS trained by self-play; it reached about 1,729 Elo on Lichess (top 10th percentile of atomic players). I only saw the search snippet; the PDF I tried returned 404. — [Chalmers ODR](https://odr.chalmers.se/bitstreams/30276124-374a-473f-89bc-14ee57bd84ec/download) (as summarised by search)
- Fairy-Stockfish supports atomic as a built-in variant and uses a handcrafted evaluation by default for variants, with NNUE opt-in. — [Fairy-Stockfish](https://github.com/ianfab/Fairy-Stockfish) (as summarised by search; not fetched)
- Pop Out's proof-number search success and the alpha-beta handicap (forbid non-winning pops, cap game length) show one practical way to cope with a destructive move: restrict when it may be used in search. — [Oulu repository](https://oulurepo.oulu.fi/handle/10024/39813)

### Inferences
- The simplest correct model of the cooldown is to put it in the search state: a counter (moves until the special is available, possibly per player) that is decremented on each ply and reset on use. This mirrors Pommerman's ammo field being part of the state. It also has to be part of the hash key (see Q3).
- "When to spend it" falls out of search if the special move is generated as a normal move and evaluated by depth-limited search. The hard part is the horizon: spending it early gives up an option the opponent may not have, so a static term for "special available" (and for "opponent's special available / turns until available") is needed in the evaluation. The literature I reached does not give values for this; they would need tuning, for example by self-play.
- Atomic chess intuition (unverified in sources): since an explosion also removes your own capturing piece, engines treat it as a trade, so the net material swing and the freed lines matter, not only what the opponent loses. The analogous check for us is net threat swing, counting our own discs destroyed.
- MCTS beating RHEA in Pommerman suggests tree search with rollouts is a sensible baseline, but with a 100 ms limit and a small branching factor (about 12 drops plus a few special options), alpha-beta with iterative deepening is likely the better fit. This is my judgement.

### Gaps
- No accessible source on Bomberman AI competitions beyond Pommerman, no Tetris-attack analysis, no "Ultimate" variants, and nothing on Amazons arrows.
- No source on how any engine values a once-per-N-moves power, or on timing heuristics (save versus spend). This is a genuine gap rather than a failure to search for one specific title.
- No atomic-chess search or evaluation specifics (extensions, quiescence, king-adjacency handling) from a primary source.

## Q3. Search with non-monotonic boards: transposition-table hashing, evaluation stability, threat re-evaluation

### Takeaway
Zobrist hashing handles removals naturally (XOR out the piece), but any state that is not on the board (cooldown counters, side to move) must also be hashed, and repeated positions bring the graph history interaction (GHI) problem. Evaluation and threat bookkeeping must be recomputed or updated for the whole affected region after a destroy move, not just at the drop point.

### Cited Findings
- Zobrist hashing updates incrementally using XOR, which is its own inverse; a capture is handled by XOR-ing out the moving piece at its origin, XOR-ing out the captured piece, XOR-ing in the mover at the destination, and XOR-ing the side-to-move key. Unmake applies the same XORs. The page also describes an additive variant with no difference in accuracy or speed. — [Chessprogramming: Zobrist Hashing](https://www.chessprogramming.org/Zobrist_Hashing)
- Castling rights and en passant file are extra Zobrist components in chess, i.e. non-board state goes into the key. — [Chessprogramming: Zobrist Hashing](https://www.chessprogramming.org/Zobrist_Hashing)
- GHI problem: a transposition table caches by position, but a position's value can depend on the path (repetition rules), so table hits can return values wrong for the current path. Campbell (1985) split it into draw-first and draw-last cases; Kishimoto and Müller (AAAI 2004) proposed a practical general solution, and a 2005 Information Sciences follow-up applies it to depth-first proof-number search. Storing full paths per position is too expensive. — [Kishimoto and Müller, AAAI 2004](https://cdn.aaai.org/AAAI/2004/AAAI04-102.pdf); [Information Sciences paper](https://webdocs.cs.ualberta.ca/~mmueller/ps/kishimoto-mueller-infsci-ghi.pdf); [Wikipedia: Transposition table](https://en.wikipedia.org/wiki/Transposition_table); [Hsu lecture slides](https://homepage.iis.sinica.edu.tw/~tshsu/tcg/2024/slides/slide15.pdf)
- Pop Out specifically hits GHI because positions can repeat, and the game is not guaranteed to end. — [Oulu repository](https://oulurepo.oulu.fi/handle/10024/39813)
- A developer on gamedev.net reported hash mismatches for the same Pop Out position reached via different drop/pop sequences (while trying to detect repetition); the thread has no documented fix. — [gamedev.net thread](https://gamedev.net/forums/topic/701335-need-help-on-optimizing-my-ai-program)

### Inferences
- Key design for us: hash = XOR of (cell, colour) keys, plus side to move, plus our cooldown counter and the opponent's cooldown counter. If two positions have the same board but different cooldown values, they have different values, so reusing one table entry for both would be a correctness bug.
- A destroy-and-fall move changes many cells at once. Incremental hashing remains valid, but make/unmake needs to record every changed cell, because the fall shifts whole column segments. A simpler and safer alternative is copy-make on the 8x12 board (96 cells) and recompute the hash for special moves only, since special moves are rare under a cooldown.
- Because of the cooldown, the disc count does not strictly increase, but cooldown phase plus total disc count identifies the stage of the game; this may be usable as the replacement-scheme "age" or to limit GHI exposure. Repetition cannot occur within a cooldown cycle in which each ply adds a disc, so GHI should be a minor risk unless the special move is available very often. This follows from the rules as described in the brief and has not been checked against the engine.
- Evaluation stability: a static evaluation built from counts of open 2/3-in-a-row windows will swing sharply across a destroy move. Search should therefore treat the special move as a forcing/tactical move (extend it or search it at the leaf, quiescence-style) so the leaf is not evaluated in the middle of a large swing. This is an engineering inference; no source tested it.
- Threat re-evaluation: after a destroy move, discs above fall, so a threat can appear in a cell the bomb did not touch (a falling disc completes a line). Win detection must scan the whole board (or all columns that shifted), for both colours, not just lines through the drop point. The brief's "discs above presumably fall" should be confirmed in the engine source.

### Gaps
- I found no source on Zobrist hashing or evaluation for games where many pieces vanish and others fall in one move.
- No source quantifying how often GHI actually occurs in cooldown-limited variants.
- Whether a fall can create a simultaneous win for both players, and how the engine resolves it, is unknown and rule-dependent.

## Q4. Public information about "Quantum Connect" (CERN, ch.cern.quantumconnect): rules, special move, competitions

### Takeaway
I found no public information about a CERN "Quantum Connect" game, the package ch.cern.quantumconnect, the method useSpecialMove, or any competition. All rules details have to come from the project's own engine source and task description.

### Cited Findings
- Searches for "Quantum Connect" with CERN, bot, hackathon, Java and useSpecialMove returned only unrelated results: CERN quantum-computing hackathons (Pan-European Quantum Internet Hackathon 2019, Quantum Futures Hackathon 2019) that do not involve a Connect Four game. — [Indico CERN event 851369](https://indico.cern.ch/event/851369/); [gate42qc hackathon repo](https://github.com/gate42qc/quantum-futures-hackathon-2019)
- Different "Quantum Connect Four" projects exist but are unrelated to a CERN game: a student project where players create superposition pieces on a quantum-computer backend (and a UT Austin paper by Michelle Ding with that title), plus an itch.io game with minimax AI. — [Michelle Ding, UT Austin](https://cs.utexas.edu/~mding01/docs/MichelleDingQuantumConnectFour.pdf); [itch.io quantum-connect-4](https://lazarus97.itch.io/quantum-connect-4)
- A CodeGame/tech.io playground "6x7 Connect 4: Multiplayer and Singleplayer, with Special Moves" has two special moves (Invert, Drill). I could not retrieve its rules (redirected to a login-style page), and I do not know if it relates. — [tech.io playground 38574](https://tech.io/playgrounds/38574)
- A GitHub Connect Four bot starter in Java (`chooseMove(board, you, info)`) has no link to CERN or a special move. — [yruefenacht/connect-four-bot](https://github.com/yruefenacht/connect-four-bot)

### Inferences
- The game is probably an internal or private-competition creation, since no public trace exists. Treat the user's description (8x12 board, connect 4, 100 ms per move, cooldown-gated 3x3-style destroy, discs above fall) as the sole rule source and verify details in the engine code: exact blast area, whether own discs are destroyed, whether the drop disc itself survives, cooldown counting (per player or global), whether a destroy can complete a line, and who wins on a simultaneous line.

### Gaps
- No public rules, forum, repo, or competition results found for Quantum Connect. Absence from a limited web search does not prove it is non-public.

## Q5. Transferable decision rules for using and countering a cooldown-limited destroy move

### Takeaway
No source gives validated rules for this; the points below are inferences from the Pop Out, Pommerman and atomic-chess material combined with general search practice, and are hypotheses to test by self-play, not established results.

### Cited Findings
- Pop Out analysis only tractably handled the destructive move by restricting it in search (no non-winning pops for the first player, game-length cap). — [Oulu repository](https://oulurepo.oulu.fi/handle/10024/39813)
- In Power Up, special pieces are single-use, and their effect (anvil, bomb) is resolved before the win check; per one source, a special piece cannot be mixed with standard discs in a winning line. — [Rarepike variants](https://rarepike.com/four/variants/); [Wikipedia](https://en.wikipedia.org/wiki/Connect_Four)
- Pommerman treats bomb ammo as recoverable state that is observable by agents, and published planners are MCTS/RHEA with a forward model. — [Pommerman README](https://github.com/MultiAgentLearning/playground/blob/master/pommerman/README.md); [Perez-Liebana et al. 2019](https://gaigresearch.github.io//2019/06/15/perez2019pommerman/)

### Inferences
Using our special move:
- Always generate it as a legal move in the search whenever the cooldown allows; never use a fixed "fire when X" rule alone. Let search decide, with the cooldown counters in the state and hash.
- Check first for an immediate win: a destroy move that makes the falling discs complete our four (or removes the discs blocking our line) is the highest-priority candidate.
- Use it defensively when the opponent has a winning threat that normal drops cannot block (for example two simultaneous threats): destroy the threat discs. A defensive special that removes more opposing than own threat material is worth more than holding it.
- Don't spend it for small material gain if the cooldown is long: add an evaluation term for "special ready" so that search prefers keeping it when nothing forcing exists, and a term for turns-until-ready for both sides.
- Prefer targets where the net swing (opponent threat discs removed minus own discs removed, plus new falling-line potential for us) is largest; require the result to not leave a one-move win for the opponent after the discs fall.
- Time it so the cooldown restarts when it is least useful to the opponent, e.g. right before a stretch where the opponent's special will be ready is dangerous, so fire when we can still respond.

Countering the opponent's special:
- When the opponent's special is ready (or will be within the search horizon), treat each of our lines as fragile: model their best destroy on our position in the search, as an opponent move, so we see which of our threats it erases.
- Prefer spreading threats across several columns or rows, since a 3x3-style blast can wipe one cluster; avoid stacking all our potential in one tight area.
- Build threats so that after fall they survive or re-form (vertical stacks collapse down but stay adjacent; consider how the fall rearranges rows).
- Be wary of a position where their destroy move makes their own line fall into place, and keep a spare blocking disc away from their potential bomb target area.
- When their cooldown has just been used, we have a free window: attack with threats that would otherwise have been erased.

Engineering rules for the 100 ms limit:
- Iterative deepening with a hard cutoff around 70-80 ms to leave a margin, special move searched first when it is a forced win/defence (to be tuned).
- Hash the cooldown counters; recompute the hash on special moves.
- Re-scan the whole board for wins after any destroy-and-fall.

### Gaps
- None of these rules has been validated against data or a published source; they need testing by self-play with the real engine, and the actual blast shape and cooldown length from the rules.
