# Bot competitions for Connect Four and close relatives (other than Kaggle ConnectX), and what won them

Scope note: about 17 tool calls were used. Most CodinGame forum pages were read through the WebFetch summariser, so quotes are the summariser's paraphrase of the posts, not verbatim page text. Several search-result summaries are secondary and are marked "(search summary)". Nothing here is about a Java bot at 100 ms unless marked "[JAVA/100ms]".

## 1. CodinGame: Connect 4, Ultimate Tic-Tac-Toe and similar ~100 ms arenas

### Takeaway
On CodinGame, the Connect 4 community puzzle (9 columns x 7 rows, steal-first-move rule) has top players (darkhorse64, RoboStac) using MCTS with bitboards, a proven-result solver and, in one case, an AlphaZero-style network. The best-documented 100 ms arena is Ultimate Tic-Tac-Toe: every Legend-level post found is MCTS plus bitboard plus solver, not alpha-beta, with 10k-90k playouts per turn. I found no formal post-mortem for the Connect 4 game itself, only forum comments.

### Cited Findings
**Connect 4 (CodinGame community puzzle)**
- The CodinGame page for the game states a 7x9 board (7 rows, 9 columns). Page text was not retrievable; the board size comes from a search summary — [CodinGame Connect 4](https://www.codingame.com/multiplayer/bot-programming/connect-4) (search summary).
- The puzzle was created by AshKetchum and validated by darkhorse64, jacek1 and MSmits — [Forum: Community Puzzle Connect 4](https://forum.codingame.com/t/community-puzzle-connect-4/189435) (search summary).
- Steal rule: at his first turn the second player can make a move or choose to become the first player (jacek). First player's best strategy is to pick the most balanced opening move — [Forum: Community Puzzle Connect 4](https://forum.codingame.com/t/community-puzzle-connect-4/189435).
- darkhorse64 uses MCTS with bitboards, full rollouts to game end, about 2 million games per second, rollouts biased toward winning moves and away from losing ones. His solver backpropagates proven win/loss results and "predicts outcomes 15 to 20 moves before the end" — [Forum: Community Puzzle Connect 4](https://forum.codingame.com/t/community-puzzle-connect-4/189435).
- RoboStac describes MCTS Solver (changes backpropagation once a state is solved, needs no extra knowledge) and says his own bot is AlphaZero-style with a small dense neural network guiding search. He links gamesolver.org for improving minimax bots — [Forum: Community Puzzle Connect 4](https://forum.codingame.com/t/community-puzzle-connect-4/189435).
- Minimax depth data point: Malte42 reached only depth 4 with alpha-beta in Python; darkhorse64 replied C++ could likely reach depth 5 and that a 10x or greater speedup over Python is common but depends on implementation. These are low depths, probably because the poster's evaluation/implementation was naive; treat as an anecdote — [Forum: Community Puzzle Connect 4](https://forum.codingame.com/t/community-puzzle-connect-4/189435).
- The multiplayer time limit cannot be changed; darkhorse64 suggests running bots locally with the psyleague CLI — [Forum: Connect 4, page 2](https://forum.codingame.com/t/community-puzzle-connect-4/189435?page=2).
- There is a related CodinGame playground "6x7 Connect 4: Multiplayer and Singleplayer, with Special Moves". The page only returned a loading placeholder, so author, rules and algorithm are unknown — [Playground 38574](https://www.codingame.com:443/playgrounds/38574).

**Ultimate Tic-Tac-Toe (CodinGame, 1 s first turn, 100 ms later turns)**
- Time limit: "1 second for first round and 0.1 second for subsequent rounds" (brokenelevator); another poster refers to "the 100ms that we have" — [Forum UTTT p.9](https://forum.codingame.com/t/ultimate-tic-tac-toe-puzzle-discussion/22616?page=9), [Forum UTTT p.3](https://forum.codingame.com/t/ultimate-tic-tac-toe-puzzle-discussion/22616?page=3).
- jacek (Java, Legend): MCTS with a tuned exploration constant ("much less than typical sqrt(2)"), tree reuse, MCTS solver, a 1-ply win check in expansion and playouts, bitboards with a lookup table for small-board wins, and precomputed log values for UCT — [Forum UTTT p.8](https://forum.codingame.com/t/ultimate-tic-tac-toe-puzzle-discussion/22616?page=8). [JAVA/100ms]
- luigi_gelato (Java): simple MCTS, roughly 160k simulations in round 1 and 20k+ later; reused tree nodes from a cache of 350k to avoid GC; named the default Java PRNG as a major bottleneck and replaced it with LCG64ShiftRandom from Jenetics. jgall measured no difference between LCG64ShiftRandom and ThreadLocalRandom — [Forum UTTT p.8](https://forum.codingame.com/t/ultimate-tic-tac-toe-puzzle-discussion/22616?page=8). [JAVA/100ms]
- dbf (Java): 30-40k playouts after an opening book; custom RNG, node object pools, no for-each loops or collections; "one of the largest improvements was tuning of MCTS score parameter" — [Forum UTTT p.8](https://forum.codingame.com/t/ultimate-tic-tac-toe-puzzle-discussion/22616?page=8). [JAVA/100ms]
- MrSubZero (C++): about 10k rollouts raised to about 25k with a compiler pragma, reached Legend; about 2 microseconds per simulation and rank ~120 after adding a solver; later 40-43k rollouts at turn 2 versus an 80k goal, and 350-400k from the start position. "Almost all time is spent on simulation", and a memory pool was "almost useless" for him — [Forum UTTT p.8](https://forum.codingame.com/t/ultimate-tic-tac-toe-puzzle-discussion/22616?page=8), [p.9](https://forum.codingame.com/t/ultimate-tic-tac-toe-puzzle-discussion/22616?page=9).
- darkhorse64: recommends targeting 80-90k simulations and adding a solver to reach the top 50; uses a 9-bit bitboard per player to index a precomputed win-check table; says Python has "absolutely 0 chance" to reach Legend — [Forum UTTT p.8](https://forum.codingame.com/t/ultimate-tic-tac-toe-puzzle-discussion/22616?page=8), [p.9](https://forum.codingame.com/t/ultimate-tic-tac-toe-puzzle-discussion/22616?page=9).
- RoboStac (C++): pure MCTS with a pool of 15 million nodes (~32 bytes each), about one node per iteration, rollouts must not use tree nodes — [Forum UTTT p.8](https://forum.codingame.com/t/ultimate-tic-tac-toe-puzzle-discussion/22616?page=8).
- Earlier-era UTTT posts (page 3): UCT needing "at least 30k rollouts" to beat the boss at turn two (Vingte); jft63 at 3k playouts with bitboards, needing about 10x more for Legend; pondering during the opponent's turn gives roughly 1/6 fewer rollouts than own turn (SupSon). On CodinGame "multithreading is useless" because of one core, though AVX is available (Magus) — [Forum UTTT p.3](https://forum.codingame.com/t/ultimate-tic-tac-toe-puzzle-discussion/22616?page=3).
- No post found on pages 3, 8, 9 mentions alpha-beta/minimax used by a top player for UTTT — [Forum UTTT p.8](https://forum.codingame.com/t/ultimate-tic-tac-toe-puzzle-discussion/22616?page=8). Pages 2, 6, 7 and 10 appeared in search results but were not read.

**Other 50-100 ms CodinGame minimax data points**
- Dots and Boxes: a poster's alpha-beta with memoization reached only depth 2-3 and ranked 108th, while a handcrafted solution ranked 14th. Replies note a 50 ms turn limit and 1000 ms first turn, advise C++ ("other languages aren't fast enough for 50ms", one reply says possibly C#/Java), store moves not states in nodes, and start the turn timer just after reading the first input — [Forum: Question about minimax](https://forum.codingame.com/t/question-about-minimax/189821). The 50 ms claim is one poster's opinion for that game.

### Inferences
- At 100 ms with a 1 s first turn, the winning pattern on CodinGame is "make the simulator or evaluator extremely fast (bitboards, lookup tables, no allocation), then spend the budget on search that proves results (solver)". For Connect 4 the equivalent proven-result approach is alpha-beta with bitboards and a transposition table (see section 4), which is a different route to the same end.
- UTTT is MCTS-dominated because its game tree is wide and win evaluation is hard; Connect 4 on 7x6 is solvable by alpha-beta, so UTTT results do not transfer directly to a small-branching game like Quantum Connect (12 columns, plus a special move that adds branching). This is my inference.
- Java reaching Legend in UTTT (jacek, luigi_gelato, dbf) shows Java is not a handicap at 100 ms provided allocation is controlled.

### Gaps
- No post-mortem or ranking list for the CodinGame Connect 4 puzzle itself (who is #1, their language, depth). The forum thread pages read held no such data, and the CodinGame leaderboard page does not render without JavaScript.
- No official CodinGame contest post-mortems for other ~100 ms games were retrieved (searched only UTTT and generic queries). Forum pages 2, 6, 7, 10 of the UTTT thread were not read.
- The playground "6x7 Connect 4 ... with Special Moves" could not be read; it is potentially the closest analogue to a special-move variant.

## 2. TheAIGames / Riddles.io "Four in a Row", Botzone, class tournaments, Battlecode-style events

### Takeaway
I found the game's API docs and several hobby bot repositories but no results page, winner list or post-mortem for TheAIGames / Riddles.io Four in a Row. I found nothing on Botzone, Battlecode or university tournaments specific to Connect Four winners.

### Cited Findings
- Riddles.io hosts a Four In A Row API with a configurable `timebank` (maximum ms in the bot's time bank) and `time_per_move` (added to the time bank each move). Numeric defaults were not retrieved (fetch failed) — [Four In A Row API](https://docs.riddles.io/four-in-a-row/api) (search summary).
- Bot repositories exist for the game, none with results: a Python-like bot with depth-limited search that prints "depth 8" at round 15 ([madest92/four_in_row](https://github.com/madest92/four_in_row)); a Clojure bot ([jipson7/four-in-a-row-bot](https://github.com/jipson7/four-in-a-row-bot)); others are listed in the search results ([dungpt3003/connect-four-ai](https://github.com/dungpt3003/connect-four-ai), [ghooo/four-in-a-row-bot](https://github.com/ghooo/four-in-a-row-bot), [lynxaegon/riddles.io-connect4-bot](https://github.com/lynxaegon/riddles.io-connect4-bot)). The first two READMEs state no ranking. The madest92 depth-8 figure is an unspecified-time output sample, not a benchmark.
- jmerle/ai-bot-workspace is a local runner for Riddles.io bots including Four In A Row, useful for local testing — [ai-bot-workspace](https://github.com/jmerle/ai-bot-workspace) (search summary).

### Inferences
- The time-bank mechanic (budget accumulates per move) is a different time model from Quantum Connect's hard 100 ms; strategies from those bots (spend more on critical moves) do not transfer.

### Gaps
- Winners, rankings, leaderboards and any winner interview for TheAIGames/Riddles.io Four in a Row: not found. Wayback Machine pages were not tried.
- Botzone Connect Four, Battlecode, Stanford/MIT/university Connect Four tournaments with winner write-ups: searches returned no relevant winner write-ups. A Carleton Computer Science Society Connect 4 tournament page (2021-02-24) and a Toronto CSC181 tournament page appeared in results but were not opened — [Carleton CCSS](https://ccss.carleton.ca/events/2020-2021/social/2021-02-24-connect4-tournament/), [Toronto](https://www.dgp.toronto.edu/~rayo/csc181/tournament).

## 3. "Quantum Connect" CERN bot competition (package ch.cern.quantumconnect)

### Takeaway
The only public trace found is a CERN Indico entry, "QuantumConnect: Particle Collision Challenge", on 5 May 2025 at Satakunta University of Applied Sciences (Pori, Finland): participants write Java programs for a Connect 4-style tournament and get 45 minutes to build and submit. No rules, board size, time limit, special-move description, leaderboard or winner was found. The Indico page requires CERN login, so these details come from the search tool's summary of the page, not a direct read.

### Cited Findings
- QuantumConnect competition listed on CERN Indico, 5 May 2025, Satakunta University of Applied Sciences, Pori, Finland; Java programs for a Connect 4-style tournament; 45 minutes to create and submit; winner titled "QuantumConnect Champion"; the entry is part of a 5-9 May 2025 event — [Indico contribution 6172921](https://indico.cern.ch/event/1441614/contributions/6172921) (search summary only; direct fetch redirected to CERN login).
- Full event page also required login — [Indico event 1441614](https://indico.cern.ch/event/1441614/) (fetch redirected to auth).
- Search for "ch.cern.quantumconnect" returned only generic CERN Quantum Technology Initiative pages, no repo or package — [CERN QTI](https://quantum.web.cern.ch/about) (search summary).
- Unrelated things with similar names (do not confuse): "Quantum Connect Four" by Michelle Ding at UT Austin ([PDF](https://cs.utexas.edu/~mding01/docs/MichelleDingQuantumConnectFour.pdf)), itch.io "quantum connect 4" games ([lazarus97](https://lazarus97.itch.io/quantum-connect-4)). Neither was opened.

### Inferences
- A 45-minute build window suggests a short-format event where competing bots were probably simple (heuristic or shallow minimax); a carefully built bitboard alpha-beta bot may already be far above the field. Speculative; no results to confirm.
- The user's engine (8x12 board, 100 ms, special move every few moves) is plausibly an evolved version of that event's engine, but no source confirms the rules.

### Gaps
- Rules, board size, time limit, special-move rules, scoring, participant count, winners, winning technique: all not found. Next step for the team: ask the organisers or open the Indico page while logged in, check CERN GitLab/GitHub for the engine repo, and check whether the Indico entry has attached material.

## 4. Common patterns among winners at very short time limits (depth, JVM advice, time management)

### Takeaway
Across the sources found, winners at 50-100 ms make the core loop allocation-free and bit-parallel, pre-allocate everything on the generous first turn, and measure everything with profiling. For Connect Four-type games, the reference technique is negamax alpha-beta with bitboards, transposition table, centre-first move ordering and an opening book (Pascal Pons's solver). For depth numbers I only have weak anecdotes (depth 4-5, from Python/naive code).

### Cited Findings
**Search technique for Connect Four**
- Pascal Pons's solver uses alpha-beta with a transposition table and opening book, bitboard representation; the repo has TranspositionTable, MoveSorter, OpeningBook, Solver, Position files — [PascalPons/connect4](https://github.com/PascalPons/connect4); technique description from [arXiv 2507.05267](https://arxiv.org/pdf/2507.05267) (search summary). Tutorial: [blog.gamesolver.org](http://blog.gamesolver.org) (certificate error on fetch, not read). No positions-per-second figure was retrieved.
- 7x6 strong-solving paper uses alpha-beta over bitboards with a transposition table of 2^28 entries overwriting on collision — [arXiv 2507.05267](https://arxiv.org/pdf/2507.05267) (search summary).
- GameDev.net advice: a Connect 4 hash key needs only 49 bits; allocate a fixed-size table once at start; Tromp used 2-entry bins prioritised by depth; at the root, iterative deepening makes it easy to put the best move found so far first — [GameDev.net thread](https://gamedev.net/forums/topic/644765-hashing-connect-4-board-for-transposition-tableneed-some-help?page=3) (search summary).

**JVM / Java specifics**
- chouch: avoid `Scanner` (1024-byte buffer; switching removed about 15 ms of input delay at Fall Challenge 2020); pre-allocate objects in a pool at initialisation; `Enum.values()` copies the array each call so keep it out of hot loops — [Forum: [JAVA] CodinGame Tips](https://forum.codingame.com/t/java-codingame-tips/191870). [JAVA/100ms-class]
- wala: switching a beam search from objects to arrays at a 60 ms limit removed many timeouts and allowed a 90-95 ms limit with almost none; oversized caches slow the bot even without OutOfMemoryError — [Forum: [JAVA] CodinGame Tips](https://forum.codingame.com/t/java-codingame-tips/191870). [JAVA/100ms-class]
- RNG micro-benchmark (JMH, wala): SplittableRandom about 246,046 ns/op vs XORShiftRandom about 357,387 ns/op (unit as given in the summary); skyyker claimed about 4x — [Forum: [JAVA] CodinGame Tips](https://forum.codingame.com/t/java-codingame-tips/191870). Irrelevant for deterministic alpha-beta, relevant if rollouts are used.
- Suggested GC mitigation: larger initial heap `-Xms` (100-200 MB) to delay collection; this is a suggestion, not verified — [Forum: [JAVA] JVM memory issues](https://forum.codingame.com/t/java-jvm-memory-issues/1494) (search summary). Not applicable if your harness fixes JVM flags.
- Generic JVM warm-up: code starts in the interpreter, tiered compilation moves hot methods to C1 then C2, and lowering the C1 threshold trades startup for earlier compilation. The sources are general (partly Azul-specific), not game-bot-specific — [Azul](https://www.azul.com/?p=89481), [NLJUG](https://nljug.org/?p=10976) (search summary).

**General rules from top players**
- darkhorse64: "do not allocate/free anything in your real time loop" — [Forum UTTT p.9](https://forum.codingame.com/t/ultimate-tic-tac-toe-puzzle-discussion/22616?page=9).
- Pre-allocate during turn 0 (1000 ms), start the turn timer right after reading the first input, profile — [Forum: Question about minimax](https://forum.codingame.com/t/question-about-minimax/189821).
- Bitboards alone gave luigi_gelato no gain over a 2D array until he also eliminated allocation/random-selection overhead; the lesson is that representation matters less than the hot loop being allocation-free — [Forum UTTT p.8](https://forum.codingame.com/t/ultimate-tic-tac-toe-puzzle-discussion/22616?page=8).
- Time-safety margin: wala's experiment (60 ms vs 90-95 ms) shows allocation-heavy code needed a margin of ~35-40 ms below a 100 ms-class budget, while array code could run at 90-95% of the budget. The thread does not state a general safety rule — [Forum: [JAVA] CodinGame Tips](https://forum.codingame.com/t/java-codingame-tips/191870).

### Inferences
- A sensible bot for a hard 100 ms limit: iterative deepening negamax with a time check every few thousand nodes, abort-and-return the last completed depth, bitboards (an 8x12 board needs 96 bits so two longs or a long plus an int per player; this is my engineering inference, not from a source), a preallocated primitive-array transposition table, centre-first move ordering, and no object allocation in search.
- Because the JVM interpreter is slow on the first calls, a warm-up search (self-play on dummy positions) before the first timed move is probably worth doing if the engine allows work at construction time. Not source-backed; the engine's loading rules matter.
- Given winner data from short-limit arenas is MCTS+solver for wide games but alpha-beta for Connect Four (Pons, solver literature), an alpha-beta core with an MCTS fallback for the special-move branching is a candidate worth testing; no source tests this.

### Gaps
- No source gives depth reached in 100 ms for Connect Four in Java (or any language) on a 7x6 or 8x12 board; the only numbers are depth 4 (Python) and depth 5 (C++ guess) from a CodinGame forum thread.
- No source gives a nodes-per-second figure for a Java bitboard Connect Four search.
- No source on safe time margins specific to a hard-kill 100 ms limit in Java (only the wala 60 ms vs 90-95 ms anecdote).
- Pons's tutorial (blog.gamesolver.org) could not be fetched (certificate error); its benchmarks remain unread.
- No evidence at all about special-move (disc-destroying) Connect Four variants, other than the unread CodinGame playground.
