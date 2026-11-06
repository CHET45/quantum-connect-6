# Export-20: games after the server update (15×30, separate machines)

Data: `analysis/server-exports/export-20/data`. Games of the new engine: 495 records (error lines carry a machine number instead of a time). Machine **2824152**: 104 games, limit **50 ms**. Machine **2825972**: 391 games, limit **75 ms**. For comparison: the old shared engine (15×30 and 15×100, 2026-11-04 10:00 to 2026-11-05 12:32, limit 100 ms). Scripts: `scripts/` (list at the end).

## In brief

1. **rc8 ("Cod Published CHET45") is first: 108-1-3 out of 112** by the site table. savi22 is 93-12-0, test123 is 88-17. Against savi22 we are **7-0**, not 2-5. The export field `winner` (= who moved last) is wrong in 26 "merged" games. By the site labels (`labels`) we won all 7.
2. **rc8 loses no turns on the new machines: 0 in 78 clean games** (on the old engine: 15.6 per 1000 on 15×30, about 65 on 15×100). The three "exceeded … turn skipped" lines in each of our games are our own limit probe (3 measurements in the second call). They are not one line printed three times. A timeout of the second call costs only the blast answer. The column move is already made.
3. **The only loss** (HWGAbot13.0, 43879758): the opponent made a drop threat in column `i` on exactly the move when its blast was charged again. A blast in column 7 won. We lost no turns there.
4. **The "double moves" `FeFeSeSe…` are not double moves. They are two runs of one match written into one list.** First they go strictly step by step (F F S S, one disc per run each), then, when one run has ended, the other goes alone (F S F S). Taken "as is", the 16th disc lands in a 15-row column already at the 16th half-move. This cannot be played on one board. This is a server fault. The bot must not use it. There is nothing to defend against and no need to (rc8 is 9-0 in such records). But the bot must not assume that one instance plays one game.
5. **For rc9:** the bot finds the 50/75 ms limit itself, and the budgets work. On 50 ms, until its 10th own move rc8 searches only about 12.5 ms, later about 30 ms. On 75 ms the figures are 35 and 55 ms. The margin is large and time can probably be added, but this is not checked. The rc9 addition `BLAST_FLOOR` = 40 ms goes through scaling and shrinks to 5/15 ms on these limits, so it changes almost nothing. The rc9 gate must be run at limits of 50 and 75 ms, not 100. Any thread that lives after the call means the loss of all remaining turns.

## 1. Table after the update

Site table (last snapshot 14:03 UTC, 7 games per pair, draws do not reach the export):

| Place | Bot | W | L | D | Points (W+D/2) |
|---|---|---|---|---|---|
| 1 | **Cod Published CHET45 (rc8)** | 108 | 1 | 3 | 97.8% |
| 2 | savi22 - "The King" | 93 | 12 | 0 | 88.6% |
| 3 | test123 | 88 | 17 | 0 | 83.8% |
| 4 | Tank-05-1101 | 85 | 27 | 0 | 75.9% |
| 5 | HumanBot_Test2 | 74 | 38 | 0 | 66.1% |
| 6 | HWGAbot13.0 | 73 | 32 | 7 | 68.3% |
| 7 | SPMoveDestroyer3 | 55 | 50 | 0 | 52.4% |
| 8 | \l\a\l\a\b\o\t | 54 | 51 | 0 | 51.4% |
| 9 | FunkyMelodyBot3 | 52 | 53 | 0 | 49.5% |
| 10 | BombSaag | 43 | 62 | 0 | 41.0% |
| 11 | Hurt me plenty | 35 | 63 | 7 | 38.8% |
| 12 | OJ477 | 27 | 78 | 0 | 25.7% |
| 13 | MimimiTest | 25 | 80 | 0 | 23.8% |
| 14 | DESTROYER OF WORLDS | 23 | 82 | 0 | 21.9% |
| 15 | Strange Quark 1 | 19 | 86 | 0 | 18.1% |
| 16 | Early Bird | 4 | 24 | 0 | 14.3% (joined late, 28 games) |
| 17 | Testing_v2 | 0 | 102 | 3 | 1.4% |

Draws: rc8 – Testing_v2 had 3 draws. HWGAbot13.0 – Hurt me plenty had all 7. There are no records of any of them.

### Score by pair (export, site labels; the row bot's wins / games)

| | rc8 | savi22 | test123 | HWGA13 | HumanB2 | Tank | lalabot | Funky3 | Bomb | SPMove3 | EarlyB |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **rc8** | | **7/7** | **7/7** | 6/7 | 7/7 | 7/7 | 5/5 | 7/7 | 7/7 | 7/7 | 6/7* |
| savi22 | 0/7 | | 1/7 | 6/7 | 7/7 | 6/7 | 7/7 | 5/5 | 7/7 | 7/7 | – |
| test123 | 0/7 | 6/7 | | 3/7 | 7/7 | 6/6 | 5/7 | 6/6 | 3/3 | 3/3 | – |
| HWGAbot13.0 | 1/7 | 1/7 | 4/7 | | 0/7 | 4/7 | 4/4 | 7/7 | 7/7 | 7/7 | 7/7 |
| HumanBot_Test2 | 0/7 | 0/7 | 0/7 | 7/7 | | 0/7 | 7/7 | 4/7 | 7/7 | 4/6 | 3/7 |
| Tank-05-1101 | 0/7 | 1/7 | 0/6 | 3/7 | 7/7 | | 2/4 | 3/3 | 4/4 | 3/3 | 3/3 |

\* The seventh game with Early Bird has no label in the export. On the site we have 7 wins against it. Against the others (Mimimi, OJ477, DESTROYER, Strange Quark, Hurt me plenty, Testing_v2) rc8 has 7 of 7 each by the site, except Testing_v2 (4 W, 3 D). Full matrix: `scripts/standings.py`.

Conclusions:
- **The strongest opponent is now test123, not savi22:** test123 beats savi22 6-1. savi22 loses only to us (0-7) and to test123 (1-6).
- HumanBot_Test2 is "rock-paper-scissors": it loses 0-7 to the whole top, but crushes HWGAbot13.0 7-0.
- HWGAbot13.0 (our rc5, reworked by the team WileyQuarks) is the only one that has beaten us.

### rc8 before and after the update (site labels)

| Opponent | old engine | new machines |
|---|---|---|
| FunkyMelodyBot3 | **0/7** | **7/7** |
| HWGAbot13.0 | 7/14 | 6/7 |
| Tank-05-1101 | 10/14 | 7/7 |
| \l\a\l\a\b\o\t | 3/7 | 5/5 |
| savi22 | 6/7 | 7/7 |
| test123 | 6/7 | 7/7 |
| HumanBot_Test2 | 14/14 | 7/7 |

On the old engine rc8 scored 63% on 15×30 and 79% on 15×100. Now it scores 98%. Almost everything we lost earlier was explained by the load on the shared server. Now there are no lost turns and no suppressed blasts.

### Why rc8 loses (replay of all losses on 15×30, `scripts/losses.py`, `scripts/annotate.py`)

| Engine | id | Opponent | We | Half-moves | Our lost turns (own turn no.) | Last move | Opponent's wins before it (drop / blast) | Cause |
|---|---|---|---|---|---|---|---|---|
| **new** | 43879758 | HWGAbot13.0 | F | 178 | – | 7* | – / [7] | drop threat + blast recharged |
| old | 8884e5fb | SPMoveDestroyer3 | S | 30 | 2,4,9,15,17 | 13 | [13] / – | lost turns |
| old | 08bb847f | HWGAbot13.0 | S | 81 | 3,25,35,42,44 | 12* | – / [12] | lost turns |
| old | 4b271aa4 | HWGAbot13.0 | F | 148 | 18 turns | 8* | – / [8] | lost turns |
| old | 0b6ee910 | Tank-05-1101 | F | 242 | – | 13 | [13] / – | single threat not closed |
| old | 64178d4f | Tank-05-1101 | S | 191 | – | 18 | [18] / – | single threat not closed |
| old | 1a7316fe | HWGAbot13.0 | F | 162 | – | 4* | – / [4] | blast |
| old | 47ba35bc | HWGAbot13.0 | S | 485 | – | 17* | – / [17] | blast |

Analysis of the only new loss (43879758): the opponent fires a blast at half-move 157, and at 175 it is ready again. At 175 HWGAbot13.0 plays `i` and threatens a drop in `i`. We close `i` (176), and a blast in column 7 (177) makes a six. This is a fork: "one drop threat + a blast that has charged exactly now". At half-move 174 the opponent had no blast yet, so the fork appeared in one move. The place to look is move 174: did the search see that the opponent's blast would be charged again after one move (own move 91, budget about 30 ms on the 50 ms machine)? Separately, check whether move 176 had a move that closes both threats (through `BlastThreats`).

Edges played no role in our losses: all decisive columns are from 4 to 18.

## 2. Rules of the new engine from the logs (`scripts/engine_rules.py`)

| Line | Meaning | Evidence |
|---|---|---|
| `TimedBotExecutor : Bot X exceeded the 50ms / 75ms time limit - turn skipped` | the bot call did not fit the limit. **The limit applies to each call** (to the first one, the column, and to the second one, the blast). 2824152 is 50 ms, 2825972 is 75 ms | 563 lines with 50, 202 lines with 75, no other values |
| … + immediately `QuantumField : Invalid column selected: -1 - X` | timeout of the **first** call: the turn is lost (visible in `moves` as two opponent moves in a row) | there are exactly as many "-1" lines as visible losses, in 810 of 815 bot×game pairs |
| `exceeded` **without** a "-1" line | timeout of the **second** call: the column move is already made, only the blast answer is lost | rc8 has 234 such lines and 0 losses |
| `Bot X failed - turn skipped` + stack (`ArrayIndexOutOfBoundsException: Index 14 out of bounds for length 12`) | the bot threw an exception; the turn is lost, the game goes on | Hurt me plenty is hard-wired for 12 columns: 151 lost turns in 24 games |
| `Bot X is still running Nms after its turn was abandoned and ignores interruption - skipping its remaining turns` | the bot thread is still alive N ms after the abandoned call, where **N = the limit** (50 or 75). **All remaining turns are lost** | Testing_v2: 32 of 32 games (75 ms), \l\a\l\a\b\o\t: 21 of 24 at 50 ms, HWGAbot13.0: 9 of 27 at 50 ms, HumanBot_Test2: 1 |
| `BotMatchRunner : Memory limit exceeded: N MB used` + `Uncaught exception in match between A and B` + `SecurityException` | exactly one line per game, always the first in the log. N is from 200 to 1900 MB (medians 837 and 927). The game goes on as usual | 494 of 495 games. rc8 scores 98% anyway, so no harm is visible. It looks like a shared JVM counter, not the bot's memory |

**Why rc8's "~1 skip per game" does not give a lost turn.** In every game rc8 has exactly 3 `exceeded` lines and 0 "-1" lines (78 of 78 clean games). This is `CallLimit`: three limit measurements (`samples = new long[3]`) in the second call (`useSpecialMove`) on own moves 1–3. The bot keeps running until the engine gives up and applies the already chosen column, and it measures the limit from that moment. The probe ends as soon as the board changes, so we never have a "still running" line. The lines are not tripled: Testing_v2 has 1 exceeded + 1 abandoned in a game, and for other bots the number of lines equals the number of losses. The claim "every line three times" is wrong, it is our probe.

The probe worked: rc8 found 50 and 75 ms and lost no turns (details in section 5).

Machine **2640679** (12:xx, 36 games, before 14:00) uses the old line format (`Operation timed out: exceeded 100ms`) with a machine number. The limit is 100 ms.

### Lost turns per 1000 own turns by band (clean records; games where the bot was abandoned are counted separately)

| Bot | 50 ms machine: games / abandoned / all | 0-2 | 3-9 | 10-19 | 20-29 | 30-59 | 60+ | 75 ms machine: games / abandoned / all | old engine 15×30: all |
|---|---|---|---|---|---|---|---|---|---|
| **rc8** | 32 / 0 / **0.0** | 0 | 0 | 0 | 0 | 0 | 0 | 46 / 0 / **0.0** | 15.6 |
| savi22 | – | | | | | | | 46 / 0 / 7.6¹ | 101.8 (3 games) |
| test123 | – | | | | | | | 76 / 0 / 0.4 | 70.4 (2) |
| HWGAbot13.0 | 27 / **9** / **40.6** | 0 | 0 | **171** | 0 | 33 | 31 | 54 / 0 / 0.0 | 27.5 |
| HumanBot_Test2 | 49 / 0 / 0.8 | | | | | | | 62 / 1 / 0.6 | 6.1 |
| Tank-05-1101 | 32 / 0 / 0.0 | | | | | | | 31 / 0 / 0.0 | 65.3 |
| \l\a\l\a\b\o\t | 24 / **21** / 441.6 | | | | | | | 22 / 0 / 0.0 | 181.2 |
| Hurt me plenty | 16 / 0 / 334.3 | | | | | | | 8 / 0 / 217.7 | 294.3 |
| OJ477 | 19 / 0 / 0.0 | | | | | | | 24 / 0 / 49.3¹ | – |
| Testing_v2 | – | | | | | | | 32 / **32** / – | 492.6 |

¹ Almost all of it is one savi22 – OJ477 record with 15 "losses" for each side. It is a merge that my detector did not catch (both runs gave a legal record).

The others (BombSaag, FunkyMelodyBot3, MimimiTest, SPMoveDestroyer3, Strange Quark 1, DESTROYER, Early Bird) have 0.0.

**Conclusion:** separate machines without load removed lost turns for almost all bots. What remains: bots that do not adapt to 50 ms (HWGAbot13.0 = rc5, \l\a\l\a\b\o\t), bots that crash (Hurt me plenty), and bots that cannot stop their thread (Testing_v2).

## 3. "Double moves" are merged records (`scripts/merged.py`, `merged_facts.py`, `merged_time.py`, `merged_search.py`, `old_problems.py`)

**What is seen.** 56 records, all on machine 2825972 (14% of its games). There are none on 2824152. There were 17 more of the same kind on the old engine since 01:00 on 2026-11-05 (8 of them are Savi20/savi22).

| Feature | Value |
|---|---|
| Structure of the runs | `2222…2222` (F F S S in a cycle), then `1111…` (F S F S). On average 83% of half-moves are in the "step by step" phase |
| Legality "as is" | illegal in all 56: usually at the 16th half-move the 16th disc goes into column `e` (15 rows). The variant "one board, overflow ignored" also fails: a six is made long before the end of the record |
| Start | both halves have the same first moves (`FeFeSeSe…` = both runs play `e`), then they diverge (`FdFe…`). It is the same pair with the same colours, twice |
| Length | 24–778 half-moves, about twice as long as a normal game of the same pair |
| Timeout lines | for rc8 a merged record also has exactly 3 probe lines, not 6. So it is the log of one run, or one bot instance for both runs |
| Who | savi22 in 50 of 56: its whole series against rc8, Tank, HWGAbot13.0, \l\a\l\a\b\o\t, OJ477, DESTROYER, Hurt me plenty, and part of its series with SPMoveDestroyer3 and test123. Plus 2 games Early Bird – rc8. In savi22's history this is **one continuous stretch**. Its series with HumanBot_Test2, BombSaag, Funky, Mimimi, Strange Quark, Testing_v2 are clean |
| Field `winner` | = the last mover; in 26 merged records it does not match the site label. The labels (`labels`) are the official result |

**Cause.** The server starts one match twice at the same time and writes the moves of both runs into one list. The strict step by step (F F S S) says that the runs go move for move, not independently. The fault is limited to a time window and to the series of a specific bot, so it is the server and not the bots: there is no savi between Early Bird and rc8. Why it is almost always savi is not visible from the data. Probably its series fell into this part of the schedule.

**Which run the site counts** cannot be said for sure. Splitting a record into two legal games worked only for 14 short records. There the site label matches the six of the run that ended first in 11 of 14, and matches the surviving one in 12 of 14. The splits are ambiguous, so there is no conclusion.

**Can it be used, or must we defend.** It cannot be used: there is no double move on any board, and each run is an honest game. The only defence is robustness. If the two runs share one bot instance (3 probe lines instead of 6 point to this), the bot alternately sees two different boards and a history with two own moves per round. rc8 reads the board anew in each call, and `TurnTracker` checks the continuity of the history, so rc8 won all 9 merged records. For rc9 this property must not be broken: no "I remember the board from the last call" without checking against the field.

For analysis: **never count lost turns or the winner from merged records.** This is where "savi22 lost 3824 turns" and "we are 2-5 against savi22" came from.

Side note on the old engine at 11:xx: 12 records are marked 15×30 but move in columns 49–75 (that is 15×100). The size in the export is wrong in places.

## 4. Style and tactics of the opponents (`scripts/style.py`, `scripts/gap_time.py`, `scripts/lost_turns.py`)

Clean records of the new machines. "Edge" means columns 0–2 and 27–29. "Far" means 8+ columns from the centre.

| Bot | Games / W | Blasts per 100 moves | First blast (median, own move) | Edge | Far | Wins by blast | Wins ≤30 half-moves | Half-moves in wins / losses | First move as F | Reply as S |
|---|---|---|---|---|---|---|---|---|---|---|
| **rc8** | 78 / 76 | 5.6 | 11.5 | 2% | 19% | 33/76 | 15 | 71 / 116 | 14 (36/36) | 14 (42/42) |
| savi22 | 46 / 42 | 2.5 | 18 | 5% | 31% | 6/42 | 15 | 44 / 290 | 14 (25/25) | 14:8, 1:5, 7:3 |
| test123 | 76 / 63 | 3.2 | **41** | 4% | 29% | 14/63 | 15 | 55 / 213 | 14 (40/40) | 14:14, 18:9, 10:6 |
| HWGAbot13.0 | 81 / 60 | 3.7 | 14 | 3% | 20% | 20/60 | 25 | 46 / 137 | 14 | 14 |
| HumanBot_Test2 | 111 / 74 | 2.2 | 22 | **18%** | **48%** | **33/74** | **28** | 34 / 69 | spread (20, 25, 10…) | spread |
| Tank-05-1101 | 63 / 44 | 6.2 | 13 | 2% | 17% | 4/44 | 12 | 46 / 148 | 14 | 14:19, 0:5, 5:5 |
| \l\a\l\a\b\o\t | 46 / 19 | 0.7 | **73** | 11% | 41% | 7/19 | 7 | 76 / 23 | 5:10, 14:5 | 12, 14, 15 |
| FunkyMelodyBot3 | 46 / 12 | 4.5 | 19 | 4% | 38% | 0/12 | 9 | 16 / 62 | **7** (27/27) | **22**:12, 18:3, 10:2 |
| SPMoveDestroyer3 | 38 / 7 | 1.6 | 41 | 0% | 7% | 0/7 | 5 | 14 / 117 | 14 | 14 |
| Early Bird | 22 / 4 | **8.4** | 10 | 1% | 5% | 0/4 | 4 | 11 / 70 | 14 | 14 |
| BombSaag | 53 / 12 | 7.5 | 10 | 0% | 94% | 1/12 | 10 | 13 / 40 | 5 | 5 |
| OJ477 | 43 / 8 | 3.7 | 13 | 26% | 59% | 0/8 | 8 | 7 / 30 | 0 | 0 |
| DESTROYER OF WORLDS | 43 / 7 | 0 | – | 0% | 100% | 0 | 7 | 11 / 29 | 5 | 5 |
| Strange Quark 1 | 36 / 2 | 0 | – | 100% | 100% | 0 | 2 | 11 / 30 | 0 | 0 |
| MimimiTest | 40 / 2 | 0 | – | 0% | 0% | 0 | 2 | 11 / 38 | 14 | 14 |

Time per move comes only from the old engine: the new machines do not write the time, so the gap method does not work on them. Gap = the other side's failed move (about 100 ms) + the bot's whole move. Medians on 15×100: savi22 200 (so it uses about 100 ms itself, the whole limit), rc8 202, HWGAbot9/10/12 160–200, test123 106 (about 5 ms), SPMoveDestroyer3 106, FunkyMelodyBot3 107, HumanBot_Test2 92 (about 0, instant). Under load this is noisy (`scripts/gap_time.py`).

Portraits:
- **savi22**: centre (14), full time, does not spend the blast early (median 18). Almost no losses on the new machines. It lost 7 of 7 to us and 6 of 7 to test123. We see nothing to exploit. It is simply weaker than we and test123.
- **test123**: 82%, the second strongest against savi22. It holds the blast for a very long time (first one at median own move 41), games are long (losses are 213 half-moves). It is fast (about 5 ms per move by the old data) and loses no turns. **Expect a late blast:** it almost always has a charged blast in the endgame.
- **HWGAbot13.0**: our rc5. It blasts early and accurately (20 of 60 wins are blasts; it caught us with the fork "drop + recharged blast"). **Weakness: on the 50 ms machine it loses turns (171 per 1000 on moves 10–19), and in 9 of 27 games it is abandoned entirely.** On 75 ms it is clean.
- **HumanBot_Test2**: scattered opening, much edge play (18%) and far columns (48%), fast wins by blast (33 of 74 wins, 28 wins in ≤30 half-moves). The strongest bots hold it (0-7), and it beats HWGAbot13.0 7-0. The width crop of rc5 in HWGA seems unable to handle edge play.
- **Tank-05-1101**: centre, many blasts (6.2), no longer loses turns (65 per 1000 before). It lost to us 7 of 7 (4 of 14 before).
- **\l\a\l\a\b\o\t**: almost never uses the blast (first one at move 73). **On 50 ms its thread does not honour the interrupt: abandoned in 21 of 24 games.** Free wins.
- **FunkyMelodyBot3**: opening 7/22, far lines along the bottom row. On the old engine it beat us 7-0. Now it lost 0-7 and dropped from 51–64% to 26% overall. Its former wins rested on our turns lost under load.
- **Early Bird**: new, blasts as soon as recharged (8.4 per 100, first at move 10), 17%.
- Weak: BombSaag and DESTROYER (column 5), OJ477 and Strange Quark (column 0, edge), MimimiTest (14, no blasts), Hurt me plenty (crashes: built for 12 columns), Testing_v2 (always abandoned).

Draws and 30 s: they do not reach the export. rc8 – Testing_v2: 3 draws out of 7. In the site history we have 4 wins: 1 when Testing_v2 moved first, 3 when we moved first. So the draws are most likely in games where Testing_v2 is first and gets abandoned on the first move, and we move alone on an empty board. Other bots win in this situation. Hypothesis: `TurnTracker.beginTurn` resets the counters (`reset()`) when it sees one disc on the board and a history of length ≤1. This fires on our second move if the opponent has never moved, and breaks the count of own moves and the blast recharge. Whether this explains the draw is not proven. **Check locally:** a game where the first player makes no move at all. HWGAbot13.0 – Hurt me plenty: 7 draws out of 7. The rc5 family has a similar trouble with an opponent that crashes on every move.

## 5. What this means for the next jar (rc9)

1. **Leave the limit probe as it is.** It found 50 and 75 ms, gave 0 lost turns in 78 games and 0 "still running". Three `exceeded` lines per game are harmless (timeout of the second call on moves 1–3, the blast is not charged there anyway).
2. **The budget is now very cautious.** `CallLimit.scaled` keeps an absolute reserve from the limit: 40 ms until the 10th own move, 20 ms later. Hence the first call takes about 12.5 ms (floor 25% of the limit) and 30 ms on the 50 ms machine, 35 and 55 ms on the 75 ms machine. Blast window: `safeFinish` = 60 ms turns into 20/35 ms. Over 78 games there was not one first-call timeout and there is no load, so the reserve is probably excessive, especially in the first 10 moves at 50 ms. **Not checked:** before changing the reserve, run ServerSim with limits of 50 and 75 ms and count timeouts. Do not enable it in rc9 without such a check.
3. **`BLAST_FLOOR` = 40 ms from rc9 hardly works on the new limits:** `limit.scaled(40 ms, 10%)` gives 5 ms at 50 and 15 ms at 75. The window is set by `safeFinish` (20/35 ms). `BLAST_DESPERATE` (blast up to 0.8 of the limit, 40/60 ms) works as before. **The rc9-versus-rc8 gate is needed at limits of 50 and 75 ms:** a result at 100 ms does not carry over to the server.
4. **No threads that live after the call.** One more limit (50/75 ms) after an abandoned call, and all remaining turns are lost. The search in the second call (ponder/probe) must finish inside the call. rc8 does this.
5. **Exceptions are now caught** (`failed - turn skipped`) and cost a turn, not the game. The upload rules stay the same. Do not rely on this.
6. **Robustness to merges:** do not rely on instance state between calls without checking against the field (section 3).
7. **Opponent weaknesses that can be used:**
   - against HWGAbot13.0 and test123: a defence against the fork "drop threat + blast recharges on the next move" (our only loss). Heuristic or test: positions where the opponent will have a blast again in 1–2 moves;
   - test123 saves its blast until move 40+: in the endgame, count its blast as always charged;
   - HumanBot_Test2 and OJ477 play at the edge: the crop must keep the edges (rc8 handles this, 7-0);
   - on 50 ms HWGAbot13.0, \l\a\l\a\b\o\t and Testing_v2 lose turns by themselves. Nothing to do, just do not lose our own.
8. **Check before upload:** the behaviour of rc8/rc9 when the opponent does not move from the first move (3 draws with Testing_v2).

## Scripts (`analysis/reports/export-20/scripts/`)

| File | What it does |
|---|---|
| `lib.py` | loading, move parsing, machine/epoch of a game, board with blast |
| `standings.py` | table by site labels, pair matrix, games where the field `winner` is wrong |
| `engine_rules.py` | "-1" lines against visible losses, second-call timeouts, rc8 probe, memory lines |
| `lost_turns.py` | lost turns per 1000 by band: 50/75 ms machines and the old engine |
| `merged.py`, `merged_facts.py`, `merged_time.py`, `merged_search.py` | merged records: features, results, order in the history, search for a split into two games |
| `old_problems.py` | merges and wrong board size on the old engine |
| `losses.py`, `annotate.py` | replay of rc8 losses: opponent's wins before the last move, blasts, lost turns |
| `style.py` | bot style, draws from the site history |
| `gap_time.py` | move time by the gap method (old engine only) |
| `deinterleave.py`, `mixed_test.py` | first (failed, too broad) attempt at splitting; kept for history |

Run: `cd analysis/reports/export-20/scripts && PYTHONIOENCODING=utf-8 python <script>.py`.
