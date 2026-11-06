# Status

Coordinator's running note, in case the session stops before the final report. Details per stream: `results/*.md`. Deadline: 2026-11-04, 17:00 or later.

## Best jars so far (updated about 10:00)

- `qc-bot/dist/night/qc-bot-4.0-rc1.jar`: speed work, time plan with a 1 s constructor warm-up, crash fix, generic tables v-wd, gen003 on 8x12. Commit 8f31c96 on `night-main`. Passed `UploadRulesTest` and the key tests.
- `qc-bot/dist/night/qc-bot-4.0-rc1-nowarm.jar`: same code without the warm-up, and with the lower early stops of the no-warm-up plan (50 ms below own turn 10, rising to 80). Use it if the warm-up jar does not start on the server. It has not played a match.

**Do not upload `qc-bot-4.0-rc2b.jar` or `qc-bot-4.0-rc2b-nowarm.jar`:** in them a single late measurement can raise the bot's belief about the call limit, in any game with a stall (found 12:00, fix on `night-time`, not yet measured). rc1 has no such measuring.

## rc1 against 3.1

Real protocol, hot JVM, self-play. Self-play gains usually overstate results against other bots.

| Size | Games | Score | Elo | 95% CI | Timeouts rc1 / 3.1 |
|---|---|---|---|---|---|
| 15x11 | 100 | 74.0% | +182 | +118 to +259 | 0 / 0 |
| 15x22 | 80 | 93.8% | +470 | +357 to +778 | 1 / 6 |
| 8x12 | 100 | 57.0% | +49 | +4 to +95 | 5 / 3 |
| 12x16 (unseen) | 60 | 92.5% | +436 | +320 to +749 | 17 / 32 |

## Known gaps in rc1

- The second-call search almost never runs, probably because of the 75 ms guard. The time stream is fixing it.
- Not yet played on the server. Whether the server accepts the constructor warm-up is unknown.
- Local timeout counts have not predicted the server before.
- Search improvements, new table generations and the book are not in it.

## Server

Closed until 17:00 on 2026-11-04 (user). No upload or server feedback before then, so the jar that goes up at 17:00 is chosen from local evidence only, with a fallback ready.

## Machine

Crash at 03:20 (bugcheck 0x113) after NVIDIA driver errors during GPU training. Rules since then: one GPU process at a time, watchdog `tools/gpu-watch.ps1`, at most 18 threads.

## Day coordinator note, 12:50

- Real-time queue of the old streams stopped at 12:35 (old scripts and rtrun queue, by PID). Background jobs run on the E-cores (mask FF000), real-time tests on the P-cores.
- New: `Crop` (branch `night-huge`, merged into `night-main`). A board dimension above 40 is modelled only around the discs. rc3 and everything before it do no search above 4096 cells (65x65 and up). Series running: `night/tools/huge.sh`, logs in `night/logs/huge/`.
- `lmrBlast=2` on by default in `night-main`; `qc-bot/dist/night/cand-1.jar` = crop + lmrBlast, tables g3, not gated yet.
- A new eval agent owns tables until 15:00 (g4 vs g4w, maybe g5) and stops self-play by 15:00.

## Day coordinator note, 13:30

- `qc-bot/dist/night/qc-bot-4.0-rc4.jar` (commit 27b8f62 on `night-main`) and `qc-bot-4.0-rc4-nowarm.jar`, both copied to OneDrive `qc-bot`: Crop, lmrBlast 2, tables g5w, no sized 8x12 table. Upload to the cloud not confirmed.
- rc4 vs rc3, real protocol, P-cores: 15x11 67.2%, 15x22 70.0%, 8x12 57.8%, 12x16 45.8% (12 games), 7x9 45.8% (12 games); pooled 60.2% of 108 games; 0 crashes; rc4 0 lost turns, rc3 1.
- ServerSim rc4 vs rc3, 15x11, 12 games, 2 pinned CPUs: 10-0-2; lost turns per 1000: rc4 0.8, rc3 0. Machine quieter than the server (late ticks 1.1% against 4%).
- One second-call timeout per game per bot at own turns 14-19, on both rc3 and rc4: the upward limit probe, set off by an opponent's steady whole turns above 115 ms (our own second-call search does that). No lost turn; one log line. Not a defect.
- QcBotRobustnessTest: failed once under load (12x8, one timeout), 3 of 3 on P-cores alone.

## Day coordinator note, 13:46

rc4 vs 3.1, real protocol: 15x11 88.8% (40 games), 15x22 95.8% (24), 8x12 57.5% (40). 0 lost turns, 0 crashes in 176 games of the queue. Latest handoff: `HANDOFF-2026-11-04-day2.md`.
