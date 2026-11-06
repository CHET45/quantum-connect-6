# King beater (rc5) vs HWGAbot6.0 (lb d78e3e6a), loss c43411fd, export-15

2026-11-04, data(10).zip = `server-exports/export-15` (new games 18:27-19:24 UTC, 328, all 15x30). We went 29-1 in them:
Savi13 new (lb f8087981) 7-0, HWGAbot6.0 6-1, L4l4Th3D3str0y3r 7-0, lalalalalalala 7-0, TeamVitality 2-0.

The one loss is the same mechanism as the Savi12 losses (`../savi12b/README.md`):
- plies 109-112: F drops 3, 3; we block on column 2 twice (fives on rows 0 and 1, columns 3-7).
- ply 113: blast 1* removes both our blockers on column 2. Two fives now need the same column: we drop 2, they drop 2
  on top and make six at ply 115. Our blast was not ready (last blast at ply 96).
- Opponent to move at ply 109 finds the win in 7 at 80 ms. We to move at ply 108: +4.20 at 4 s, depth 13
  (rc5, `review-c43411fd.md`); the candidate bqfe misses it too (`bqfe-c43411fd.md`).
- Plies 110 and 112 are forced replies (depth 1, static eval +5).

Test position for the blast fix: game c43411fd, us to move at ply 108 (and 110, 112).
