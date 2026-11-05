# Final day, 2026-11-06: from the announcement to the jar

At 9:00 the organisers announce the board, the time rules and the line length that wins. The leaderboard goes dark at
about 10:00 and uploads close at 12:00. This page is the whole procedure; the base is final-base (rc8 code + dynamic
rules + g6f tables, branch rc9-dynrules in `night/wt-dyn`), which at k = 6 plays exactly like rc8.

## The switches

All announced values live in one file, `src/main/java/ch/qc/bot/Announced.java`:

| Switch | Meaning | core-2.0 value |
|---|---|---|
| `K` | connected quarks needed to win, 2..9 | 6 |
| `BLAST_W`, `BLAST_H` | blast half width / half height (1, 1 = 3x3; 2, 2 = 5x5; 0, 0 = landing cell only) | 1, 1 |
| `COOLDOWN` | turns between specials | 10 |
| `CALL_LIMIT_MS` | time limit of one engine call; the bot still measures a shorter one itself | 100 |
| `MATCH_SECONDS` | wall-clock time after which a running game is a draw | 30 |
| `WARMUP_MS` | search in the constructor; 0 if the constructor is timed tightly | 1000 |

Nothing else needs editing. The board size is read from the engine; boards wider than 40 are cropped as before.
With the core-2.0 values the jar is byte-for-byte the final-base logic (checked by bytecode diff and AnnouncedTest).

What each switch reaches: `K`, `BLAST_*`, `COOLDOWN` → `EngineRules.ANNOUNCED` → every model the bot builds
(`Geometry.rules`), the cooldown tracker, the blast tests. `CALL_LIMIT_MS` → `CallLimit.ASSUMED_NANOS` (the limit
assumed before measuring, and the end of the second-call probe); all budgets stay written at 100 ms and scale through
`CallLimit.scaled`. `MATCH_SECONDS` → `MatchClock.MATCH_NANOS`. `WARMUP_MS` → `Timing.CONSTRUCTOR_WARMUP_NANOS`.

Tables exist only for k = 6. Any other k plays with the hand evaluation (measured 79-98% vs the 3-ply fallback at fixed
nodes, `docs/dynamic-rules.md`), and a blast other than 3x3 uses the slower generic blast test (a third to half of the
nodes at 100 ms).

## One command

From `C:\Users\Chtean45m\Documents\quantum-connect` in PowerShell:

```
& night\tools\final-build.ps1 -K 5 -Blast 3x3 -Cooldown 10 -LimitMs 100 -MatchSec 30 -Rows 15 -Cols 30
```

It writes `Announced.java`, builds the jar, runs every test (upload rules, conformance, AnnouncedTest, ...), plays a
fixed-node sanity match under the announced rules on the announced board (bot vs its 3-ply fallback; it stops if the
bot misses a win in one or blunders in one ply), stages `qc-bot/dist/night/final/qc-bot-final-<tag>.jar` and prints its
sha256. About 5 minutes. Add `-Deliver` to copy the jar to `C:\Users\Chtean45m\OneDrive\qc-bot\` and check the copy's
hash. Leave out any switch that was not announced (it keeps the core-2.0 value). `-Blast` takes the full block, `WxH`,
odd sides. A board without an opening suite gets random two-ply openings.

Tests that play inside the core-2.0 engine (QcBotRobustnessTest, MatchClockTest, a few TimePlanTest cases) are skipped
when the announced values differ from core-2.0, because that engine plays other rules (`CoreEngine` in the tests);
the script lists what it skipped.

## Real-time gate

`MatchMain` runs the real core-2.0 engine, so a real-time gate is only meaningful while the announced rules are k 6,
3x3, cooldown 10 at 100 ms (for example when only the board changes):

```
bash night/tools/finalgate.sh 1 RxC 50 <openings-RxC.txt> 123 -s1
bash night/tools/finalgate.sh 2 RxC 50 <openings-RxC.txt> 456 -s2
```

(edit the `--a` jar in the script to the staged jar; 50 pairs per slot take about 20 minutes on 15x30). Look at
score, timeouts (must be 0) and the per-side `accelerate p50`.

## If the organisers ship a new engine jar

1. Put it in `night/wt-dyn/libs/` under the name the build expects (or adjust `build.gradle`), keep the old one aside.
2. If it has instance getters for the rules, wire them in `EngineRules.read` (the one place), e.g.
   `return Rules.of(field.getConnectedQuarksRequiredToWin(), r, r, field.getMovesBetweenSpecialMoves());` and keep
   `ANNOUNCED` as the fallback. Never read a `static final` constant of the engine: javac inlines it at our compile time.
3. Build with the script; ConformanceTest and the differential tests then run against the new engine. With the new
   engine at the announced rules, the CoreEngine-guarded tests can be run by setting the announced values back to
   whatever the new engine plays.
4. Check `UploadRulesTest` still passes (no System, io/nio/net, reflection, exceptions).

## Checklist 9:00-12:00

1. Read the announcement; write down k, blast, cooldown, call limit, game clock, board size(s), and whether rules vary
   per match (if they vary and there is no getter, pick the most likely value and say so).
2. Run `final-build.ps1` with those values. Fix nothing by hand in the bot unless a test fails.
3. If the rules are core-2.0, run the real-time gate on the announced board.
4. Run again with `-Deliver`; give Aleksandrs the file name and sha256. He uploads before 12:00.
5. Commit `Announced.java` with the announced values in rc9-dynrules.
