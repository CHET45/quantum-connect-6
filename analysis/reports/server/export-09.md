# Export 09 (data(3).zip, fetched 2026-11-04 15:41 UTC)

Archive: `server-exports/export-09-832c060d.zip`, unpacked in `server-exports/export-09/`.
527 new games since export-08, all on 15x30 (rows x cols), 6 in a row.

## Savi beater 4.0 (lb ba9671a0)
- 72 games, 71-1. Two batches: 37 games played 11:20-11:30 UTC (13:20-13:30 Riga), 35 games about 15:10-15:30 UTC.
- Both batches are rc4 (user, 2026-11-04). rc5 has not played on the server. The different move in the same position between batches (vs SPMoveDestroyer2/3) is time-dependent search, not a different jar.
- vs Savi9 7-0 (Savi9 beat 3.1 5-2 on the same board), vs Savi12 7-0.
- Lost turns per 1000 own turns: batch 1 5.4 (9/1666), batch 2 1.2 (4/3282), all 2.6. 3.1 on the same board 7.5, Savi9 1.1, Savi12 1.4.
  Batch 2 by band: 0-2 0/105, 3-9 0/245, 10-19 0/350, 20-29 1/333, 30-59 2/870, 60+ 1/1379.
  Lost turns past own turn 60 occur now (earlier versions had none); 15x30 games are long.
- 7 second-call timeouts (blast call only, no lost turn). No memory-limit lines. No crashes.
- Only loss: vs lalalalalabotfixed as S, 371 plies, 0 lost turns, decided by the opponent's blast on the last move (id cf05e266).
- "Invalid column selected: -1" follows every first-call timeout for every bot: it is the engine's record of the lost turn, not a crash.
- Draws are not exported; all pairs with Savi bots show 7 decided games.
