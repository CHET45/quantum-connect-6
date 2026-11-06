# Kaggle ConnectX: top approaches, leaderboard results, lessons learned

Evidence-quality warning for the report writer: kaggle.com pages are JavaScript-rendered. WebFetch returned only page titles for every Kaggle URL I tried (competition page, discussion 188305, discussion 509721, notebooks). A real-Chrome attempt on discussion 188305 hung on "page still loading". So every statement about the Kaggle discussion post below comes from a search-engine summary of the page, not from my reading of the page itself. Treat those items as "reported, unverified". I could not open the live leaderboard, so there is no verified top-of-leaderboard rating. The numbers I could verify come from the Kaggle environment config on GitHub, a student paper (arXiv), a PARL pull request, and a few GitHub READMEs.

## Q1. What did the top-ranked ConnectX agents use, and what scores/ratings did each approach reach?

### Takeaway
The only top-tier recipe I found described in detail is a hybrid: a deep alpha-beta search built on a Pascal Pons-style solver, plus a large precomputed opening book, uploaded in the submission. It is reported (unverified) at about rank 1-2 with a score near 1600. Verified numbers for other approaches are lower: AlphaZero-style agents 1282-1368 (rank 5-9 at the time), plain MCTS 1075, depth-5 minimax 873. I found no verified evidence of a pure-RL or small-net agent at the very top.

### Cited Findings
- Kaggle ConnectX environment defaults: 6 rows, 7 columns, inarow 4, `actTimeout` 2 s, `remainingOverageTime` 60 s; `agentTimeout` default 60 is marked obsolete and `timeout` 2 is an obsolete copy of `actTimeout` — [kaggle-environments connectx.json](https://raw.githubusercontent.com/Kaggle/kaggle-environments/master/kaggle_environments/envs/connectx/connectx.json)
- Reported top agent (search-engine summary of Kaggle discussion "Connect X" 188305; page itself not readable by me): the author's "best agent" combines a deep alpha-beta search with a 19-ply opening book. Plies 1-14 contain all reachable positions; plies 15-19 contain only losing, tying and close games. The book was generated with a modified version of the C++ scoring code from connect4.gamesolver.org (Pascal Pons). It was compressed with zlib, base64-encoded and pickled to about 1 MB, "well under the 100 MB limit". Forced positions, instant wins and terminal positions were not stored. The same summary places this author at roughly rank 1-2 with a score near 1600 — [Kaggle discussion 188305](https://www.kaggle.com/c/connectx/discussion/188305). The rank/score line appeared in only one of two search summaries, so it is the weakest part of this claim.
- A second search summary of the same post says the author did not think leaning on such a deep book was the best approach, so the book is not the whole agent — [Kaggle discussion 188305](https://www.kaggle.com/c/connectx/discussion/188305). The first summary also said the author avoids the centre opening when playing second to induce mistakes; the second summary found no such statement, so treat the centre-avoidance claim as unconfirmed.
- AlphaZero-style baseline from the PaddlePaddle PARL repo: README claims "about score 1368 in the Kaggle Connect X competition", and a reviewer asked to add "(rank 5)". The PR was closed by its author on 4 June 2020. No MCTS simulation count, network size or compute is stated — [PARL PR #282](https://github.com/PaddlePaddle/PARL/pull/282)
- Student paper "Reinforcement Learning for ConnectX" (submissions as of 5 May 2022): AlphaZero 1282 (9th of 225 teams, ~700 submissions); plain MCTS 1075; MCTS+depth-2-minimax hybrid 980; depth-5 alpha-beta minimax 873; greedy one-step lookahead 268. New submissions start at 600 — [arXiv 2210.08263 (HTML)](https://arxiv.org/html/2210.08263v1)
- Same paper, head-to-head (10 games per pair, W-L-D for the first-named agent): AlphaZero vs Greedy 10-0-0, vs Minimax 10-0-0, vs Hybrid 9-0-1, vs plain MCTS 7-1-2; plain MCTS vs Minimax 8-2-0 and vs Hybrid 8-2-0 — [arXiv 2210.08263 (HTML)](https://arxiv.org/html/2210.08263v1)
- Same paper, details: plain MCTS ran about 20,000 simulations per move with UCB c = sqrt(2); the hybrid ran about 500 simulations per move. AlphaZero padded the board to 12x12 to support variable board sizes and promoted a new net only if it won more than 60% of decided games. The paper gives no hardware or training-time figures — [arXiv 2210.08263 (HTML)](https://arxiv.org/html/2210.08263v1)
- GitHub minimax + alpha-beta + memoization agent (YazanIST): plain minimax depth 3, with alpha-beta depth 4, with memoization "up to depth 5". Self-reported "rank ~21 worldwide"; no numeric score — [YazanIST/ConnectX](https://github.com/YazanIST/ConnectX)
- Another GitHub agent reports a skill rating of 1069.7 at rank 54 (via search summary); a Kaggle notebook "ConnectX Minimax Alpha-Beta Agent" shows 634.0 and "ConnectX with Deep Q-Learning" shows 555.5 — [sidhantagar/Kaggle-ConnectX](https://github.com/sidhantagar/Kaggle-ConnectX), [Minimax notebook](https://www.kaggle.com/code/ameythakur20/connectx-minimax-alpha-beta-agent/output?scriptVersionId=301724835), [DQN notebook](https://www.kaggle.com/code/phunghieu/connectx-with-deep-q-learning). Source for the 1069.7 and the two notebook scores is a search summary only; I did not open these pages.
- Bitboard negamax agent (Tarun995): bitboards, negamax, alpha-beta, principal variation search, aspiration windows (+/-150 after depth 4), transposition table of 2^24 entries, killer moves, history heuristic, Numba JIT, iterative deepening from depth 1. It claims "often looking 15+ moves ahead", but no benchmark, rank or score is given — [Tarun995/connectX-bitboard-agent](https://github.com/Tarun995/connectX-bitboard-agent)
- Learned world-model agent: wins 100% vs random, 100% vs a weak heuristic and 83.3% vs a stronger heuristic. It uses real-rules search with a learned leaf value and an exact endgame solver. No Kaggle rank is given, and its README says the competitive pool "likely includes near-perfect solvers" — [alextoti1/WorldModel-ConnectX](https://huggingface.co/alextoti1/WorldModel-ConnectX)
- Other public notebooks exist as bitboard MCTS and alpha-beta by James McGuigan, but I could not read them (no scores obtained) — [MCTS bitboard + bitsquares](https://www.kaggle.com/code/jamesmcguigan/connectx-mcts-bitboard-bitsquares-heuristic/notebook?scriptVersionId=95652052), [AlphaBeta minimax](https://www.kaggle.com/code/jamesmcguigan/connectx-alphabeta-minimax/log)

### Inferences
- The score ladder (greedy ~270, depth-5 minimax ~870, plain MCTS ~1075, AlphaZero ~1280-1370, solver + book ~1600) suggests exact or near-exact play beats heuristic search, and heuristic search beats naive RL, on 6x7. This ordering is reported across different sources and dates, so it is indicative only.
- Pascal Pons' solver (negamax + alpha-beta + bitboards + transposition table + move ordering) appears to be the shared backbone of the strongest agents; evidence for this is one summarised post, not several.

### Gaps
- No verified current leaderboard (top ratings, number of teams, who is 1st). Kaggle pages could not be rendered.
- No verified full text of discussion 188305 (author, exact rank, score, search depth, per-move node counts).
- No write-up from a rank-1 agent confirmed to be an AlphaZero or MCTS design; the 1282-1368 AlphaZero figures are ranks 5-9 from 2020-2022.
- Rating-scale caveat: scores are TrueSkill-like and drift over time; the 2020 (1368) and 2022 (1282) numbers are not directly comparable.

## Q2. How did agents deal with the per-move time limit (iterative deepening, opening books, book compression)?

### Takeaway
Time limits were handled by (a) iterative deepening with a time check, (b) transposition tables and strong move ordering, (c) precomputed opening books embedded in the submission, (d) JIT/compiled code. The one detailed book report used 19 plies compressed to about 1 MB, which removes search cost for the opening and mid-opening.

### Cited Findings
- Official limit: `actTimeout` = 2 s per move, plus a 60 s overage bank (`remainingOverageTime`) — [kaggle-environments connectx.json](https://raw.githubusercontent.com/Kaggle/kaggle-environments/master/kaggle_environments/envs/connectx/connectx.json)
- Conflict on the limit: the 2022 student paper says "5 seconds per move" and fitted minimax depth 5 to it, with timeouts or illegal moves counted as losses — [arXiv 2210.08263 (HTML)](https://arxiv.org/html/2210.08263v1). The current config says 2 s. The limit may have changed between 2020-2022 and now; I could not confirm the history.
- Book as embedded data: zlib + base64 + pickle, about 1 MB for 19 plies, and the 100 MB submission limit was not a constraint (summarised post, unverified by direct read) — [Kaggle discussion 188305](https://www.kaggle.com/c/connectx/discussion/188305)
- Book content selection: exhaustive up to ply 14, then only "losing, tying and close" positions for plies 15-19; forced/instant-win/terminal positions skipped to save space — [Kaggle discussion 188305](https://www.kaggle.com/c/connectx/discussion/188305)
- Iterative deepening from depth 1, so a finished depth's move is always available; Numba warm-up call is made before the clock starts; a transposition table of 2^24 entries — [Tarun995/connectX-bitboard-agent](https://github.com/Tarun995/connectX-bitboard-agent). The README does not describe how the clock is checked inside the search.
- Plain memoization/hash caching moved a depth-4 alpha-beta agent to depth 5 — [YazanIST/ConnectX](https://github.com/YazanIST/ConnectX)
- Pons' solver code is hard-coded to the 6x7 board in some forks, which matters for porting to other sizes — [vasilije2448/connect4-solver](https://github.com/vasilije2448/connect4-solver) (via search summary)
- For scale: John Tromp's reference database for 7x6 (8-ply) took about 40,000 CPU hours on workstations, and his 9x6 solve needed about 2E13 positions, roughly 2,000 hours on a 1.4 GHz Opteron — [Tromp's Connect-4 page](https://tromp.github.io/c4/c4.html)

### Inferences
- A book is a way to move computation from the move clock to build time; the Kaggle book was built offline with a C++ solver, then shipped as data. For a Java jar the analogue is a resource file inside the jar, loaded once at startup.
- With 2 s on Kaggle versus 100 ms for Quantum Connect (a 20x reduction) and a board of 96 cells versus 42, in-search solving will not reach solver-level depth. A book and cheap, strong move ordering matter relatively more.

### Gaps
- No source that quantifies nodes per second or depth reached per 2 s for the top agents.
- No source on how top agents avoided timeouts in practice (safety margin, overage-bank usage).
- Could not verify whether the 5 s to 2 s change happened or when.

## Q3. Which public notebooks/discussions are most cited, and what win rates vs baselines did they report?

### Takeaway
I could not obtain Kaggle view counts, votes, or citation counts, so I cannot rank notebooks by popularity. The sources I could read give small-sample win rates against other agents, with AlphaZero dominating minimax and MCTS in 10-game matches.

### Cited Findings
- Kaggle discussion 188305 (the opening-book / alpha-beta "best agent" post) — [link](https://www.kaggle.com/c/connectx/discussion/188305). It came up first when searching for "opening book" and "perfect agent", but I have no vote count.
- Kaggle "Intro to Game AI and Reinforcement Learning" course (Alexis Cook): units on a first agent, one-step and N-step lookahead, deep RL, and minimax — [Class Central listing](https://www.classcentral.com/course/intro-to-game-ai-and-reinforcement-learning-74263). I did not read lesson content.
- The Kaggle Book (Packt) devotes a chapter to simulation competitions, with a `connectx` notebook; the page does not state what approach it recommends — [PacktPublishing/The-Kaggle-Book](https://github.com/PacktPublishing/The-Kaggle-Book)
- Win rates (10 games per pair): AlphaZero 10-0-0 vs Greedy and vs Minimax, 9-0-1 vs Hybrid, 7-1-2 vs plain MCTS; plain MCTS 8-2-0 vs Minimax — [arXiv 2210.08263](https://arxiv.org/html/2210.08263v1)
- Outside Kaggle: on a generic Connect-4 comparison, MCTS agents win 100% vs random and a mean 94.75% vs a heuristic, and MCTS wins 69% vs depth-6 alpha-beta minimax — [arXiv 2405.16595](https://arxiv.org/pdf/2405.16595) (search summary only, not opened). A separate study reports base MCTS (10,000 iterations) at about 25% vs a near-perfect alpha-beta agent (search summary, source paper not identified).
- AlphaZero baseline code for ConnectX (PARL) — [PARL PR #282](https://github.com/PaddlePaddle/PARL/pull/282)
- A 2026 arXiv paper uses Pons' solver (commit d6ba50d) as the strength baseline against which coding agents' AlphaZero pipelines for Connect Four are measured, and reports they perform comparably to the external solver — [arXiv 2604.25067](https://arxiv.org/pdf/2604.25067) (title and summary only; I did not read the numbers)

### Inferences
- The two MCTS-vs-minimax results conflict (8-2-0 for MCTS in the Kaggle paper vs 25% for MCTS against near-perfect alpha-beta elsewhere) because of the opponent's depth/strength and whether a heuristic is used. A depth-5 minimax with a weak heuristic is a poor proxy for a real solver.

### Gaps
- No view/vote counts; "most cited" cannot be established.
- I never read "Connect X with bitboards", Pons-port notebooks or "AlphaZero for ConnectX" notebooks on Kaggle; they may exist but were not retrievable.
- No reddit threads found despite searches.
- Win rates against negamax from the top agents were not found.

## Q4. What failed or was overrated (pure RL / DQN, small nets, etc.) and why?

### Takeaway
Evidence for failures is thin and mostly by implication: naive RL (DQN) notebooks sit at the bottom of the score range, hybrid MCTS with too few simulations underperformed plain MCTS, and shallow depth-limited minimax with a hand-made heuristic is far from the top. Tie-breaking toward the centre was reported as a cheap, big gain.

### Cited Findings
- A "ConnectX with Deep Q-Learning" notebook scores 555.5, below the 600 starting score (search summary, notebook not opened) — [phunghieu notebook](https://www.kaggle.com/code/phunghieu/connectx-with-deep-q-learning)
- A minimax alpha-beta notebook scores 634.0 (search summary) — [ameythakur20 notebook](https://www.kaggle.com/code/ameythakur20/connectx-minimax-alpha-beta-agent/output?scriptVersionId=301724835)
- Hybrid MCTS + depth-2 minimax scored 980, below plain MCTS (1075); the authors attribute this to the much lower simulation count (~500 vs ~20,000) — [arXiv 2210.08263](https://arxiv.org/html/2210.08263v1)
- The authors say their AlphaZero used a basic network with hyperparameters not finely tuned, and the paper does not report DQN results — [arXiv 2210.08263](https://arxiv.org/html/2210.08263v1)
- Preferring the column nearest the centre among equal-scored moves "gained about 200 points" (self-reported) — [YazanIST/ConnectX](https://github.com/YazanIST/ConnectX)
- A heuristic that gives -100 for the opponent's 3-in-window but +1 for the agent's own is described by the author as "needs more work" — [YazanIST/ConnectX](https://github.com/YazanIST/ConnectX)
- The world-model agent author states its parity (zugzwang) heuristic is shipped as an opt-in, experimental parameter and "not a solved problem" — [alextoti1/WorldModel-ConnectX](https://huggingface.co/alextoti1/WorldModel-ConnectX)

### Inferences
- The failure pattern is "approximate evaluation + limited depth": heuristics lose to exact solvers because Connect Four is decided by deep zugzwang/parity lines that a window-count heuristic misses.
- The 555 DQN score is one notebook and says little about a tuned RL pipeline.

### Gaps
- No post-mortems from top competitors on what they tried and dropped.
- No evidence on small-net AlphaZero variants beyond the one student project.

## Q5. Data on forced first-player wins on standard 6x7 and the effect on the leaderboard (ties among perfect players, etc.)

### Takeaway
6x7 is a solved first-player win (opening in the centre column). The reported top agent uses a book with tie/close positions; I found no source describing how the leaderboard treated ties between perfect players. For larger boards the solved-status data is sparse, and nothing is known for 8x12.

### Cited Findings
- Connect Four was first solved in 1988 by Victor Allis, independently by James D. Allen, and with perfect play the first player wins by opening in the centre column (search summary of general references) — [Tromp's Connect-4 page](https://tromp.github.io/c4/c4.html) for Allen's priority (announced 1 Oct 1988 on rec.games.programmer, as reported in the search summary)
- Tromp's page: number of reachable 7x6 positions 4,531,985,219,092 (Edelkamp and Kissmann, 2008); 8x8 solved as a second-player win (late 2015, with code and an opening book on GitHub); 9x6 solved in 2005; for 6-wide boards "the winning starting moves are NEVER in the central columns" — [Tromp's Connect-4 page](https://tromp.github.io/c4/c4.html). The page's outcome table lost its alignment in extraction, so I did not map results to individual board sizes. The page content I got has no 12-wide results and no 8xh results beyond 8x8.
- Ranking-score design: Kaggle ratings are TrueSkill-style, start uncertain and converge over dozens of games — [alextoti1/WorldModel-ConnectX](https://huggingface.co/alextoti1/WorldModel-ConnectX); new submissions start at 600 — [arXiv 2210.08263](https://arxiv.org/html/2210.08263v1)
- The reported top agent's book stores "losing, tying and close" positions for plies 15-19, which implies draws are distinguished in the book — [Kaggle discussion 188305](https://www.kaggle.com/c/connectx/discussion/188305) (unverified read).
- The reported top author (search summary) says playing second they avoid the centre opening so that weaker agents may err — [Kaggle discussion 188305](https://www.kaggle.com/c/connectx/discussion/188305); not confirmed by a second summary.

### Inferences
- If several agents play perfectly, the first player always wins and the second always loses, so perfect-vs-perfect ratings are decided by who is first. The mirrored pairing in Kaggle matches would then yield roughly even results among perfect agents, compressing the top of the ladder; I have no data confirming this effect.
- Strength differences at the top would then come from how an agent plays as the second player in a theoretically lost position (choosing the longest-resisting or most error-inducing lines), which fits the reported centre-avoidance trick.
- Quantum Connect has a special move, so the 6x7 theoretical result and the 1988 solutions do not apply to it. Even for plain 8x12 Connect-4 there is no solved-status data in my sources.

### Gaps
- No leaderboard-specific data on ties or the number of perfect solvers.
- No theoretical result for 8x12; Tromp's table as extracted does not cover width 12.
- Pons' gamesolver.org blog could not be fetched (certificate error), so its benchmark and opening-book statistics are not cited here.

## What transfers to Quantum Connect (8x12, 100 ms, special move, Java only) and what does not

Transfers (inference from the findings above):
- Negamax/alpha-beta on bitboards with transposition table, iterative deepening and strong move ordering (centre-first, TT move, killer/history) is the common backbone of the strongest reported agents. Java can do this with `long` bitboards; Numba/C++ are not needed.
- Shipping precomputed knowledge as data in the jar (book compressed to about 1 MB for 19 plies on 6x7) is permitted in the Kaggle setting; whether the Quantum Connect jar size permits the same is a question for the organisers.
- Centre-preference tie-breaking is a cheap gain, reported at about 200 points.
- Hand-written heuristics with shallow depth, and naive DQN, score poorly; do not expect them to be competitive.

Does not transfer:
- Perfect solving. 8x12 has 96 cells (about 2.3x more than 42), and a 100 ms budget is 20x less than 2 s; Tromp's data show that even 9x6 took about 2,000 CPU hours, so a full solve at 8x12 within the clock is not feasible. A deep exact book is also unlikely to be buildable for 8x12.
- 6x7-hard-coded solver code (Pons forks) must be rewritten for 8x12; a 96-bit board needs two `long`s or a different layout, because the standard 7x(6+1)=49-bit bitboard trick does not fit in 64 bits for 12 columns x 9 rows.
- The special move (area destruction, rate-limited) breaks gravity-only invariants: the standard win-check shortcuts, symmetric-position hashing, and any 6x7 book do not carry over, and move ordering and the evaluation need to be redesigned.
- The first-player-wins fact and the Kaggle ladder compression among perfect players have no direct counterpart for the Quantum Connect variant.
- MCTS with 20,000 simulations per move (Kaggle student paper) is not possible at 100 ms in Java on a larger board without a much faster simulator. AlphaZero with a net would need inference to run in pure Java inside the limit, which makes it expensive; the Kaggle results (rank 5-9, 1282-1368) were below the reported solver-plus-book agent (~1600).
