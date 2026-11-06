# Export-19: rc8 on the server as "Cod Published CHET45"

2026-11-05. Zip `analysis/server-exports/export-19-b27c11aa.zip`, unpacked in `export-19/`; 4971 unique games, 412 new since export-18. Scripts: session scratchpad `bot.py` (W/L by opponent, own lost turns by own-turn band, losses with lost turns in the last 6 plies).

## Cod Published CHET45 (lb 7cf3f313, team "No beaches?") = rc8 (the team)

Plays as rc8 (gap crop), not rc8-wide: no early drops at the edges. rc8-wide does not appear in this export.

All 15x100, 11:12-11:33 UTC: **56-17 (76.7%)**. The Emperor (rc7), 06:00-06:15: 65-18 (78.3%); Data Collector (rc5), 06:18-06:59: 64-27-1 (69.6%). Different hours and opponents; with ~75 games each the gap of ±7 points is noise. The server data cannot separate rc8 from rc7 yet.

| opponent | rc8 | Emperor (rc7) | Data Collector (rc5) |
|---|---|---|---|
| FunkyMelodyBot3 | **0-7** | 1-6 | 2-5 |
| HWGAbot12.0 (new) | 1-2 | — | — |
| Strange Quark 1 | 5-2 | 6-0 | 1-5 |
| savi22 | 5-2 | — | 2-3-1 |
| Tank-05-1101 (new) | 4-2 | — | — |
| test123 | 6-1 | 6-1 | 6-1 |
| BombSaag | 6-1 | — | 6-1 |
| HumanBot_Test2, MimimiTest, SPMoveDestroyer3, Testing_v2, Hurt me plenty, Early Bird | 29-0 | | |

Window from 11:00 UTC on 15x100: FunkyMelodyBot3 13-1, Tank-05-1101 34-9 (79%), rc8 56-17 (77%), HWGAbot12.0 37-12 (76%).

## Lost turns

Own lost turns 64.8 per 1000 own turns (Emperor 64.6, Data Collector 91.9): the server is as loaded as in the morning. By own-turn band: 0-2 114, 3-9 80, 10-19 78, 20-29 50, 30-59 52, 60+ 52. 10 of 17 losses have an own lost turn in the last 6 plies (Emperor 7 of 18). Two losses with long stalls: 5a32013b vs savi22 (42 lost turns in 259 plies), e5e9cfda vs BombSaag (16 in 69).

## FunkyMelodyBot3

Same lb (ac9ca783) since 01:04 UTC. It beats our family and its forks: King beater 4-3, Emperor 6-1, Data Collector 5-2, rc8 7-0, HWGAbot12.0 6-1, HWGAbot10.1 4-3; but lost 0-7 to HWGAbot9.0 and lalalalalalala, 1-6 to Savi20, 2-5 to Savi21 and savi22. It builds horizontal lines on the bottom row, often 25+ columns away from the main fight (01681da6: 74-79 while we play 48-57).

The three shortest losses (60c18b32 13 plies, 778f2d02 12, 01681da6 22) end with our lost turn on the move that had to block: Funky's bottom-row three plus a far disc becomes a double threat while we skip the turn. The four longer losses (54-115 plies) are not reviewed yet: whether rc8 misjudges the far bottom-row line or only loses turns needs a GameReview pass (15x100, rc8's crop is not in GameReview's Searcher path).

## Funky losses replayed (2026-11-05 afternoon)

Tool: `JarReplay` (session scratchpad `jarreplay/`, compiled against `night/wt-blast` classes): drives the real rc8 jar through the engine's `executeWithTimeout` at 100 ms on every own turn of a server game, applies the server's moves (lost turns as column -1), and optionally runs a full-board 3 s search (`Weights.forGeometry`, TT 22) on the same position. Deep searches between bot calls make the bot see 3 s opponent turns and shrink its own stop to ~15 ms or probe a longer limit (timeouts in 74f8a28a): only the runs without deep search (`--last 0`, twice) count for rc8's own choices.

- **Lost turns decide 4 of 7:** 60c18b32, 778f2d02 (deep +0.8..+1.3 before our lost turn, forced loss after it), 01681da6 (lost turn while Funky's 74-79 needed one block), 912cae1a (two own lost turns in a row at the end).
- **Blasts not played on the server:** in all 5 longer games, at the first turn with the blast charged (own turn ~10) rc8 locally asks for a blast (48*, 48*, 48*, 66*, 88*), the same with stops of 10 ms and 25 ms; the server rc8 played a drop in another column each time. A blast lost to a second-call timeout would leave a drop in the same column, so on the server rc8 itself switched to its best drop: the rule in `QcBot.accelerateQuark` (`inTime = elapsed <= blastFinish()`, `blastFinish = max(limit/10, 60 ms - stall)` with warm-up timing; `stall` = the longest clock-poll gap lately, +30 ms per lost turn, up to 60 ms). Under the server's load the window shrinks to 10-30 ms. Blasts per 100 own turns after turn 10 on 15x100: rc8 2.9 (11 UTC), rc7 4.9 (06 UTC), rc5 3.6-6.7. 14d879f1 was lost on a turn where only a blast (68*/69*) stopped Funky's open five 66-70; the server played 64.
- **Funky's plan:** bottom-row triples with gaps all over the width (`OOO.OOO`, 38-40 + 43-45, 74-76, 66-70); each needs one block, and the full-board search scores our side +2..+6 until a few plies before the loss: the evaluation does not see these lines coming, the blast is the answer in hand.
- 74f8a28a: the 3 s full-board search finds a forced win (58) at plies 106 and 108; rc8 played 57/56 on the server and does not find it locally either. One case, not verified further.

Next: confirm the blast loss under imitated load (ServerSim `--cfs`, count blasts per game rc8 vs no load), then a candidate that keeps the blast window at ~85 ms and relies on `secondCallInTime` alone for the crash guard, gated against rc8 under the same load.

## rc9: blast window under load (2026-11-05, 14:15-15:10)

Code: worktree `night/wt-bomb`, branch `rc8-bomb` (commit ca0734a = rc8 source as shipped, copied from wt-blast; next commit = rc9). rc8's uncommitted wt-blast state is also saved as `night/patches/rc8-wt-blast.diff` + `rc8-wt-blast-untracked.tar`. Jar `qc-bot/dist/night/qc-bot-4.0-rc9.jar` (sha256 54605fa9…).

Changes (both `Timing` one-line switches, spec keys `bd`, `bf`):
- `BLAST_DESPERATE = 1`: a blast the search wants is kept past the blast window (up to 80 ms of the bot's own clock) when the opponent wins at once in two or more columns of the searched model. A crash is only possible if the first call timed out unseen, and then the game is lost anyway.
- `BLAST_FLOOR = 40 ms`: the window `max(floor, 60 ms - stall)` no longer drops below 40 ms (was 10).

Why the window: the comment in `Timing` explains that a late thread start is invisible to the bot, so `secondCallInTime` alone does not rule out a blast after a timed-out first call (engine crash). Under the imitated CPU limit (quota 120, old `cal-q120`/`load-*` runs) the engine-side overhead around a call (outer - in) exceeds 40 ms in 3-4% of calls, never without load.

Evidence:
- rc8 self-play ServerSim 15x100, 8 games each: no load 7.7 blasts per 100 own turns after turn 10, quota 120 6.7 (-13%), no crash path. Blasts are delayed under load rather than lost.
- JarReplay of the 5 long Funky losses: on the 55 turns where normal rc8 wants a blast, the server move equals normal rc8 2 times and rc8 with a closed window (`b=55@1`) 12 times (and that one never blasts); all turns 65 vs 56 of 164.
- 14d879f1 ply 85 (Funky's open five): rc8 with a closed window drops 68 and loses; the candidate with `bd=1` and rc9 with defaults blast 68*.
- ServerSim 15x100, quota 120, one busy thread, 32 games vs rc8: `bd=1` 51.6%, crash path cand 0 / rc8 2, blasts 216 / 217; `bd=1,bf=40` 53.1%, crash path 0 / 0, blasts 233 / 215. Score differences are noise (±9 points); self-play rarely reaches only-blast-saves positions.
- ServerSim 15x30, quota 120, 16 games, rc9 vs rc8: 46.9% (noise), crash path 0, blasts 128 / 123.

rc9 copied to OneDrive `qc-bot` (sha256 54605fa9…). It does not fix lost turns (4 of 7 Funky losses) or the evaluation's blindness to Funky's gapped bottom-row lines; those remain open.
