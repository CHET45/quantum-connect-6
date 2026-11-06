# Export-17: The Emperor (rc7) on 15x100, and a loaded server

2026-11-05. Zip `analysis/server-exports/export-17-98c0e8a4.zip` (sha256 98c0e8a4…), unpacked in `analysis/server-exports/export-17/`. 4453 unique games (export-16: 3132). Scripts in the session scratchpad (`emp.py`, `loss.py`, `lost.py`, `lost_hour.py`, `blastuse.py`, `bylost.py`, replay tool `ServerReplay.java`); every number below comes from `export-17/data/matches.jsonl`.

## Board

The server switched to **15x100** (15 rows, 100 columns, 6 in a row) at 2026-11-05 00:34 UTC; 807 games, the only size since then. Before: 15x30 from 2026-11-04 10:15 to 2026-11-05 00:33.

## The Emperor (lb f419bc4b, team "No beaches?")

- 83 games, all 15x100, fetched 06:00-06:16 UTC: **65-18-0 (78%)**. It does not appear in summary.md's loss table (that table only lists the older team bots), so the losses below come from `matches.jsonl`.
- Own timeout lines 317 (3.8 per game), own lost turns 226 = **65 per 1000 own turns**.
- By opponent (W-L): FunkyMelodyBot3 **1-6**, HumanBot **3-4**, HWGAbot10.1 4-3, lalalalalalala 5-2, Hurt me plenty 3-1, Savi21 6-1, test123 6-1; 7-0 or 6-0 against BeetaSaag, MimimiTest, SPMoveDestroyer3, Strange Quark 1, UnlimitedBots, Testing_v2 (3-0).
- It never met Savi18, Savi20 or HWGAbot9.0.

15x100 standings (all games since 00:34): Savi18 21-1, HWGAbot9.0 84-17 (83%), Savi20 73-18 (80%), King beater (rc5) 76-19 (80%), **The Emperor 65-18 (78%)**, HWGAbot10.1 70-23 (75%), Savi21 81-31 (72%), lalalalalalala 63-27 (70%). The Emperor and King beater are level; rc7's local +60..+100 Elo over rc5 does not show here, but 83 games under a loaded server cannot show it either.

## New opponents since export-16

Savi16, Savi17, Savi18, Savi20, Savi21 (team Savi), HWGAbot9.0 and HWGAbot10.1 (team WileyQuarks), HumanBot (team "lalalabot v2"), FunkyMelodyBot1.2v, test123 (team Tiim566779). King beater's new losses came from HWGAbot9.0 (6), HumanBot (5), FunkyMelodyBot3 (4), Savi20 (3 + 1 draw), Savi17 (3), HWGAbot10.1 (3), Savi16 (2), Savi18, Savi21, lalalalalalala. King beater total now 301-39-1.

Team WileyQuarks = HWGAbot5.0/6.0/8.0/9.0/10.1, HereWeGo, HereWeGoAgain(2.0), QuarkBot-04-1713, Testimine-M7. According to the team it is built on our rc5, modified by another team.

## Finding 1: the server got slow at about 22:00 UTC on 2026-11-04, and lost turns decide games

Timeouts per 1000 plies (all bots): 12-19 on 15x30 during the day, then 72 (22:00), 96 (23:00), 129 (00:00), still on 15x30, and 107-145 on 15x100. The rise starts before the board change, so it is the server (load), not the board.

King beater, own lost turns per 1000 own turns, by hour: 15x30 15:00-21:00 **1-3**, 22:00 41, 23:00 83, 00:00 78; 15x100 00:00 **5** (44-1), 01:00 **158 (15-14)**, 02:00 63 (13-1), 05:00 40 (4-3). The score follows the load.

Lost turns per 1000 own turns on 15x100: HWGAbot10.1 37, Savi21 59, **The Emperor 65**, King beater 86, HWGAbot9.0 126, Savi20 233. The Emperor's rate is flat over the game (own turns 0-2: 48, 3-9: 82, 10-19: 47, 20-29: 64, 30-59: 75, 60+: 60), so the stall and lost-turn adaptation in the time plan does not bring it down.

Games with no own lost turn against games with at least one (15x100): King beater **40-0** against 36-19, The Emperor **20-2** against 45-16, HWGAbot9.0 32-1 against 52-16. (Long games have more lost turns, so this overstates the effect, but not by this much.)

7 of the Emperor's 18 losses have an own lost turn within the last 6 plies.

**Replays** (rc7 jar through the real engine, our bot asked at every own turn, the recorded moves played; tool `ServerReplay`):

- 3a3a8bd4 (FunkyMelodyBot3, 18 plies): rc7 plays the same moves locally (plies 2-12 identical, eval +2 at depth 7-8). At ply 16 the server lost our turn; locally rc7 plays `42`, which holds. Funky's two moves in a row (41, 46) made a double threat (42 and 47) on row 0, lost at 18.
- 84649c25 and ec851a48 (FunkyMelodyBot3): the same pattern, a lost turn (plies 8 and 28; 7 and 41) shortly before the decisive threat.

FunkyMelodyBot3 lost 7-0 to HWGAbot9.0 and lalalalalalala, 5-2 to Savi20/21; it beat the Emperor 6-1 and King beater 3-4 (all in loaded hours). Its plan works only if the opponent loses a turn.

## Finding 2: wide play (HumanBot) widens the crop to all 100 columns, search falls to depth 3-5, and the eval stays overconfident

HumanBot opens at the edges (columns 0-98). Our `Crop` then covers the whole board (`0+100x15` from about ply 15). In the replays of 89be6860 and 8076149f, rc7's depth falls from 7-10 (25-64 column crop) to 3-5 and its score is +600..+1300 in positions it then loses. HumanBot beat both our bots 4-3 but lost 7-0 to HWGAbot9.0, 6-1 to Savi20 and Savi21.

## Finding 3 (weaker): blasts are held longer under load

Own turns with the blast ready, per blast used: King beater 8.5 on calm 15x30, 9.7 on loaded 15x30, 12.8 on 15x100; The Emperor 15.4. Cause in the code: `QcBot.blastFinish()` = max(limit/10, 60 ms − stall); a high `stall` (thread gaps measured in the search) shrinks it to 10 ms, and a blast is then replaced by the best drop (to avoid the engine crash after a timed-out first call). In 8076149f plies 44-74 the server Emperor dropped in column 51 fifteen times while the local rc7 wants the blast `49*` at every one of those positions (blast ready, score +800..+1300). Other strong bots hold blasts much longer by choice (Savi20/21, HWGAbot10.1: about 42-44 ready turns per blast), so blast use alone does not explain results.

## Is The Emperor rc7?

Taken from the team; the moves cannot confirm it. On 14 random Emperor games (551 own moves) the rc7 jar picks the server move in 57.5% of positions and the rc5 jar in 57.7%: the two engines are too close, and the server's loaded timing changes moves more than the version does. rc7 does replay the Emperor's opening exactly (3a3a8bd4 plies 2-12).

Move agreement (share of positions where the jar picks the server move; samples of 15x100 games, `ServerReplay` quiet mode):

| server bot (moves) | rc5 | rc6 | rc7 |
|---|---|---|---|
| The Emperor, 14 games, 551 moves | 57.7% | 59.5% | 57.5% |
| King beater (= rc5), 10 games, 442 moves | **69.9%** | | 64.0% |
| HWGAbot10.1, 10 games, 541 moves | 25.3% | | 23.5% |
| HWGAbot9.0, 8 games, 260 moves | 27.3% | | 27.3% |
| Savi21, 6 games, 266 moves | 39.1% | | 38.0% |

The method separates rc5 from rc7 on King beater's games (+6 points for the right jar), but not on the Emperor's (all three within ±2 points, about one standard error). The Emperor's games were all played in the most loaded hour, which cuts its search and so changes its moves; that may hide the version. HWGAbot9.0 and 10.1 agree with rc5 less than Savi21 does, so whatever WileyQuarks took from rc5, their bots no longer play like it.

## Conclusions

1. The losses are driven by **lost turns on a loaded server** (since about 22:00 UTC on 2026-11-04), plus weak search on a whole-width crop against edge play. The export-16 conclusion (eval is the next lever) came from five 15x30 games on a calm server; on the current server, timing under load matters more.
2. Next lever: a time plan that keeps lost turns low when the server is slow (react to lost turns and stalls by cutting the stop much harder, and possibly a lower base stop on boards above the crop threshold), tested in ServerSim with CPU load (`load` option) against rc7, judged by lost turns per 1000 and score.
3. Second lever: crop and move generation when play spreads over the whole width (HumanBot pattern): limit the search to the active regions instead of one 100-column model.
