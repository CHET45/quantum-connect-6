# rc8: gap crop (and an optional wide opening) on boards wider than 40

2026-11-05. Code in `night/wt-blast` (uncommitted, on top of rc7; rc7's source saved as `night/patches/rc7-wt-blast.diff` + `rc7-wt-blast-untracked.tar`). Why: export-17/18 (`analysis/reports/server/export17/README.md`, `export18/README.md`).

## What changed

- `Crop` can map model columns to any ascending set of real columns (`map`, `real(m)`, `model(c)`), not only a contiguous stretch. `QcBot` turns moves into real columns through that map (and the second-call search back), instead of one offset.
- `Timing.cropGaps` (spec `gap=N`, default in rc8 **2**), only for a board with more than 40 columns:
  - 1: the model keeps the columns within 8 of some disc; empty stretches wider than 4 between them are left out. No window of six holds discs on both sides of a cut, so lines through discs are exact.
  - 2: also leaves out groups of discs (columns at most 5 apart) where neither side has 3 discs, while a larger group exists. From 3 discs a six still needs 3 more drops of that side, so the group is modelled in time.
  - The model is rebuilt only when a disc comes within 6 of a cut or an edge of the model (a rebuild costs 1-5 ms).
- `Timing.wideTurns` (spec `wide=N`, default 0 in rc8, **2 in rc8-wide**): on a board wider than 40, up to N own turns from the second on, when the search's move is a quiet drop (|score| < 150, no blast), drop instead next to the edge farthest from all discs (column 1 or cols−2, at least 20 columns from every disc).
- `Timing.lostCap*` (spec `lc=ONE@TWO`): a stop cap after own lost turns; default off, not tested.
- Boards with at most 40 columns take none of these paths: rc8 plays exactly like rc7 there.

Tests: `CropTest` (map both ways, cut, rebuild rules, level 2 groups, legal wide games with gap 1, gap 2 and wide), plus the rc7 set (UploadRules, TacticsSuite, MateSuite, TimePlan, BoardSizes, BoardDifferential, Vcf, Robustness, TurnTracker, Conformance).

## Gates (real protocol, 80 ms bots, P-cores unless said; 15x100 from 64 server openings: 10-20-ply prefixes of export-18 games of King beater, The Emperor and Data Collector, `night/wt-main/suites/openings-15x100.txt`)

| A | B | size | result |
|---|---|---|---|
| gap=1 | rc7 | 15x100 | 54.0% of 150 pairs, +28 [−11, +68]; repeat (seed 123) **62.2%** of 150, +86 [+48, +127]; together about 58%, +57 |
| gap=2 | rc7 | 15x100 | **63.7%** of 120 pairs, +98 [+53, +147] |
| gap=2 | gap=1 | 15x100 | 47.7% of 150 pairs, −16 [−58, +25] (no difference in self-play, where nobody scatters discs) |
| gap=2 | rc7 | 100x100 / 50x50 / 15x64 (random 4-ply openings, CPUs 2-7) | 60.8% of 30 pairs, +77 [−5, +167] / 57.5% of 40, +53 [−29, +140] / 56.3% of 40, +44 [−34, +126]; about 58% of 110 pairs together |
| gap=2, wide=2 | rc7 | 15x100 | **80.0%** of 120 pairs, +241 [+187, +308], SPRT H1 |
| gap=2, wide=2 | gap=2 | 15x100 (E-cores) | 45.2% of 150 pairs, −34 [−73, +5]: the two far drops cost about 34 Elo against a bot that leaves single far discs out |

Final jars (default constructor, as on the server):

| A | B | size | result |
|---|---|---|---|
| rc8 | rc7 | 15x100 server openings | **64.5%** of 100 pairs, +104 [+56, +156] |
| rc8-wide | rc7 | 15x100 server openings | **83.8%** of 60 pairs, +285 [+214, +383], SPRT H1 |
| rc8 | rc7 | 15x30 (E-cores) | 45.0% of 60 pairs, −35 [−97, +25]: noise; only Crop, QcBot and Timing classes differ from rc7, and on boards up to 40 columns their paths are rc7's (code checked) |

No crashes, invalid columns or invalid specials in any gate; timeouts 0-11 per 300 games on both sides.

Jars: `qc-bot/dist/night/qc-bot-4.0-rc8.jar` (sha256 bf4a1288…) and `qc-bot-4.0-rc8-wide.jar` (ee133f59…), both copied to OneDrive `qc-bot`.

## Time plan under load (rejected)

ServerSim with an imitated container CPU limit (see export18 README): stops of 45 ms, no second-call search, or both, all score 34-47% against rc7 in 16 games and do not lower engine timeouts. Under a CPU limit the frozen periods take calls of any length.

## Recommendation

- **rc8** (gap=2) as the main bot: +57..+98 Elo over rc7 on 15x100 from server openings, neutral or better on 50x50, 15x64, 100x100, identical on boards up to 40 columns.
- **rc8-wide** (gap=2, wide=2) only as a second bot on the server: it beats rc7 80% (bots with one contiguous crop, i.e. rc5/rc7 and anything built on them), but costs about 34 Elo against a bot that does not widen its model for single far discs. Whether Savi or WileyQuarks' bots are of the first or second kind is unknown; HumanBot's scatter play did not hurt them (HWGAbot9.0 7-0, Savi20/21 6-1 against HumanBot), so the second is more likely. Decide by the server: upload both, compare after about 80 games each.
