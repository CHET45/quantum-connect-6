# How to beat everyone — analysis and plan (2026-11-04 00:40)

Inputs: research bundle (reports, 9 notes, 10 digests), design and milestone logs, all server exports (1,020 unique games replayed on their true sizes, `reports/server-size-findings.md`), 13 lost games re-searched at 2 s, cold-JVM probes, and the independent clean-context session (real-time matches, per-move time of other bots).

## The actual problem

"Beat everyone" means **beat the Savi line head to head**. Everyone else is already beaten: on 8x12 we are 76/3 and on 15x22 78/2. On 15x11 we are 65/5 (2.1), Savi8 71/0, and we are **0-7 vs Savi8, 1-5 vs Savi7b**. 14 of our 19 losses are on 15x11. Savi ships a new version about every hour, so the target moves.

The server rotates board sizes (8x12 → 15x22 → 15x11). All our training went into 8x12, the size where we were already first.

## Why we lose (ranked by evidence)

| # | Cause | Evidence | Size of the lever |
|---|---|---|---|
| 1 | **We use half the clock** | Savi8 96–99 ms per turn, Savi7 90–98, ours 46–74, with the same lost-turn rate (3–5 per 1000 turns). `elapsed*3 > hard` stops deepening at 1/3 of the budget | ms=90 + no early stop: **+108 Elo** on 15x11 (65% ±10, 40 pairs, hot JVM) |
| 2 | **Hand evaluation off 8x12** | The deep score falls steadily from ply 21–31 with no single blunder; a 2 s search is no better in the middle game. Learned vs hand on 8x12: **+222 Elo** (gen003) | Largest, unproven off 8x12 |
| 3 | ~~Our blasts are bad~~ **Rejected by test** | Re-analysis suggested it (we blast 2.0/game, Savi8 1.0; the 2 s hand-eval search agrees with 24% of our blasts). But a root margin against blasts loses at fixed nodes on 15x11: margin 200 **−120 Elo** [−158, −85], margin 100 **−153** [−190, −119]. Blasts decide 42–64% of games between our bots on large boards. Hand-eval re-analysis is a poor judge; matches decide | Blast bonus and deeper blast generation under test |
| 4 | **Slow nodes** | `blastWins` at leaves: 332 vs 503 knps on 15x11 (+30 Elo, n.s.). Full window sum per eval (815 windows on 15x22). No LMR or any selectivity at branching 11–44 | Rapfi-type pruning figures are large but unverified here |
| 5 | **Cold JIT** | Fresh JVM vs hot: 1.5–2x fewer nodes in mid-game; one 110 ms call at own turn 10 under load. All our gates ran hot | Equal to 1.5–2x time if the server is cold every game (unknown) |
| 6 | **One opening line** | 12-ply centre stack on 15x11: we are 0-10 after it, Savi as F 12-0. Our 2 s search prefers Savi's own 13.F5 | 10 of 12 losses to Savi8/7b pass through it; Savi can change it any time |

Not causes: memory lines (rate follows the time window, 457 of 640 games with them have no timeouts), draws (terminal contempt does nothing; draws are not predictable before the endgame).

Correction to an earlier claim: "62 ms causes more timeouts" is not established. On 15x11, 2.1 lost a turn in 13 of 70 games, 2.2 in 18 of 67, and the windows differ. Savi runs at twice our time with the same rate, so most lost turns are not budget-driven.

## Options considered

- **More 8x12 generations** (what we were doing): +101, +89, +52 Elo per generation, on a size where we are already first. Paused.
- **Tables per size**: a known pipeline, but it does not survive a new size and 15x22 costs 19 MB. Kept as a fallback for 15x11 if generic tables are weak.
- **Size-independent learned tables + search fixes**: chosen. The research ranks it first (Rapfi; direction × row-band tables), memory is tiny, and it works on a size nobody has seen yet.
- **Exploit Savi specifically**: a book against one line helps for an hour. Only as a small add-on, built from deep search, not from their moves.
- **NN or MCTS in Java at 100 ms**: the research says no (1.8k vs 428k nodes/s).

## Plan

Every step is gated by a colour-swapped match against the previous version. Search changes use real time, eval changes fixed nodes first, then real time. All three sizes.

1. **3.1, time and speed: done, sent 00:52** (`dist/qc-bot-gen003-safe-80ms.jar`, commit c81c42d): hard stop 80 ms, no 1/3 rule, no `blastWins` at depth ≤ 0, clock polling by window count, tables built in the constructor, match clock that counts the plies the board can still need. Real time vs 3.0, 100 pairs each: **15x11 +186 [+135, +246], 8x12 +92 [+60, +127], 15x22 +70 [+24, +119]**. Check on the server: lost turns per 1000 must stay near Savi's 3–5.
2. **3.2, search levers:** blast bonus and blast generation depth, LMR for wide boards, each at fixed nodes first.
3. **4.0, generic tables (morning):** 15x11 + 15x22 + 8x12 data → fit (fix the learning rate and quantization) → gate vs hand per size → 2–3 generations on the mixed sizes. Keep gen003 on 8x12 unless generic beats it.
4. **Then:** LMR and SPSA for wide boards, running window sums (15x22), JIT warm-up in the constructor, a shallow deep-search book per size, cold-JVM mode in the gates.

## What would prove this wrong

- Server lost turns rise well above 5 per 1000 at 80 ms → back off in steps.
- Generic tables do not beat hand on 15x11 by a clear margin → per-size tables for 15x11.
- Savi8 still wins 7-0 after 3.1 and 4.0 → look at what its search does that ours does not, by re-searching its moves.

## Needed from the organisers (via the user)

Board sizes and their mix in the final; points for win/draw/loss and games per pair; whether versions freeze before the final; what the 200–350 MB memory lines mean. None of these block the plan.
