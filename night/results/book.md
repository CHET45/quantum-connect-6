# Book stream

Branch `night-book`, worktree `night/wt-book`. Numbers are appended as they are measured.

**Verdict, 12:00: do not ship the book.** Against an imitation of Savi it scores 6.4 +-4.5 points below no book (mean over 15x11, 8x12, 15x22), and where it differs from plain search the difference rests on noise or on a single game. Numbers below. The first-reply test on any size is still running.

## What exists

- `src/main/java/ch/qc/bot/Book.java`: position to column, decoded from `weights/book.qcb` by the Gradle task `embedBook` (no file, no book).
- Hook in `QcBot.choose`: a book hit runs a 30 ms search; a proven result overrides the book, otherwise the book move is played.
- `src/tools/java/ch/qc/tools/BookBuilder.java`: drop-out expansion; `tools/build-book.sh WEIGHTS MINUTES THREADS OUT`.
- `BookMatch` (real protocol, empty board, moves logged), `BookFast` (fixed nodes with jitter, empty board), `BookCheck` (book against the server lines of Savi), `tools/book-report.py` (unique games).

## Key stability

`Book` does not use the Geometry Zobrist keys. Its hash is plain arithmetic on (cell index, colour), pinned in `BookTest.keysAreStable`, so it depends neither on the JDK's `SplittableRandom` nor on `Board`. The Geometry keys are stable as well (same value between constructions and pinned in the same test), but nothing relies on that.

## Log

- 02:14 merged `night-speed` 04af256 into `night-book` at the coordinator's request (no conflicts). Book-off jar of the same code: `qc-bot/dist/night/book-off-a49b0c3.jar`. All book tests compare against a jar with the same search, so the speed gain does not count as a book gain.
- v1 (before the speed merge, 30 min, 2 threads, machine loaded): 15x11 233 entries, 8x12 106, 15x22 154, 2984 bytes. Superseded, kept in `qc-bot/data/night/book/v1/`.
- v2 (after the merge, same budget) running: about 1 position per second on 2 threads.

- v2 finished: 15x11 625 entries, 8x12 449, 15x22 422, 9002 bytes.
- Fixed-node test, v2 against the same code without book, empty board, 40k nodes +-30% per move, hand evaluation, 15x11: **52.4% +-3.7 (600 games, 580 unique)**; as F 56.2%, as S 48.7%. Only 3.0 book moves per game: the engine's own replies leave the book early. Not significant.
- Found while reading v2: backed-up scores were not comparable with plain search scores of siblings. One more ply changes a score by -83 on 15x22 on average (horizon effect), -4 on 15x11, -5 on 8x12. That pushed the 15x22 root to column 5 for no reason. The mean shift is now taken out at every backup.


## After the crash (09:45)

- Merged `night-main` 8f31c96. The hook now reads: time plan's stop, then `min(stop, 30 ms)` on a book hit; stall sensor, blast stop and second-call search unchanged; a book move is passed to the second-call search like any other move. Book-off jar of the same code: `qc-bot/dist/night/book-off-merged.jar`.
- The bot plays the centre on an empty board without search (`Timing.centreOnEmpty`), so the builder fixes the root to the centre.
- The bot's weights are now gen003 on 8x12 and generic v-wd elsewhere; the builder picks them the same way from `weights/`.

- 09:57 new requirement: no dependence on board size or on the time limit. The search behind a book move is now 40% of the call's stop, not 30 ms. On a size without a table `Book.column` returns -1 at once and the bot's path is unchanged (`BookTest.anotherBoardSizeNeverGetsAColumn`). Book time-boxed to about 12:00.
- v4 (shipped weights: gen003 on 8x12, generic v-wd elsewhere; 20 min, 2 threads): 15x11 652 entries, 8x12 317, 15x22 198, 7028 bytes. With the generic tables the horizon shift is small (-6.6, -3.2, -13.4 units).
- Book v4 against the Savi lines in the export, long search about 5M nodes per move, scores in 1/256 logit: differences between the book's move and what our bots played are small, 15x11 +12, +28, -2, +27, -17; 8x12 +10, +12, +62, +44; 15x22 +45, +15, +3, +26, -16. On 15x22 the book leaves lines that went 4-0 and 3-1 for us. Full tables: `night/logs/book-check-v4-*.txt`.

## Against an imitation of Savi (fixed nodes, 40k +-30%, shipped weights, empty board)

`BookFast --b-imitates Savi`: B plays what any Savi version played in the export wherever it has been in the position (mirror included, chosen by how often it played each move) and searches elsewhere. A plays with or without the book. No Savi code exists, so everything after the known moves is our own engine on both sides.

15x11, book v4 against no book, 300 games each: **41.2% against 49.3%, difference -8.2 +-7.1 points. As F 33.3% against 52.7%, -19.3 +-9.8; as S 49.0% against 46.0%, +3.0 +-10.2.** The no-book arm opened by search here, not in the centre as the bot does; a fair arm with `--centre` is running. The book follows one line of 40 plies in all 150 games as F (`55555555555555 4 66666 3646`, 11 book moves, known Savi moves at every step, most from a single game) and it scores 33%.

Rollouts (100 games per move, 40k nodes, equal engines) at the two nodes where our bots went 0-5 against Savi: at ply 12 after the stack, 13.F3 46.5%, 13.F5 (the book) 62.5%; at ply 13, 14.S4 41.0%, 14.S3 (the book) 41.5%. So the known bad 13.F3 is rightly avoided, but plain search with the shipped tables also avoids it. The long search picks that line's later move F4 at ply 14 and rollouts say that is bad. A long search with these tables is not a good enough judge of an opening line.

Real protocol, book v4 against no book, same code, loaded machine: 15x11 51.7% +-11.9 (60 games, 60 unique); 8x12 51.8% +-7.7 (57 games, 57 unique). Own turns 0-9 of the book side, lost turns: 0 of 600 on 15x11, 1 of 570 on 8x12. Three of 60 games on 8x12 crashed the engine (a special after a timed-out first call); which bot asked was not recorded. The on and off jars have the same guard, this is not a book matter, but it still happens under load.

## First moves on any size (fixed nodes, generic tables, equal engines, only the start forced)

F's score by the offset of the first move from the centre (20k nodes +-30%, 60 games per cell; 15x11 centre arm cut short):

| size | search plays | 0 | 1 | 2 | 3 |
|---|---|---|---|---|---|
| 6x7 | -3 | 46.7 | 50.0 | 46.7 | 45.0 |
| 7x9 | +0 | 50.0 | 50.8 | 51.7 | 50.8 |
| 8x12 | +0 | 55.8 | 45.0 | 60.8 | 49.2 |
| 9x20 | -8 | 52.5 | 61.7 | 69.2 | 35.8 |
| 10x10 | +0 | 42.5 | 45.8 | 50.8 | 43.3 |
| 12x16 | +3 | 31.7 | 44.2 | 56.7 | 50.0 |
| 15x11 | -3 | | 41.7 | 59.2 | 53.3 |
| 20x9 | +0 | 59.2 | 55.0 | 59.2 | 50.8 |
| pooled | | 48.3 +-3.3 (420) | 49.3 +-3.3 (480) | 56.8 +-3.2 (480) | 47.3 +-3.2 (480) |

Two columns off the centre stands out, but it is one arm of four picked after looking: a replication with new seeds at 40k nodes is running (centre, two off, the search's own choice).


## Book v4 against the imitation of Savi, fair arm (both open in the centre)

15x11: book 41.2% against no book 50.0%, 300 games each, **difference -8.8 +-7.1 points. As F 33.3% against 54.0%, -20.7 +-9.7; as S 49.0% against 46.0%, +3.0 +-10.2.**

Why, read from the listing: after 14 plies of stacking, the book plays 15.F4 (1-0 in one real game, a bonus of +55 units) and not 15.F5 (7-1, bonus -11 because mostly Savi played it). The long search scores them -4 and -8. So a single game overturned a tie, and that line scores 33% in 150 games, while 15.F5 scores 54% in 150 games. One round of learning from these games (v5, `--self-lines`) did not flip it, so its test was stopped.

Plain search of 150k nodes with the shipped tables against the known Savi replies plays the same moves as the book for a long way: 15x11 as F identical up to ply 14 and as S up to ply 13 (so it avoids the known bad 13.F3 and plays 14.S5 where the book plays 14.S3), 8x12 as F up to ply 10 and as S for all 16 known plies, 15x22 as F up to ply 8 and as S up to ply 11. Where they differ, the long search separates the moves by 0.1 logit or less. In short: where the book agrees with search it adds nothing, and where it disagrees it rests on noise or on one game.


## Final table, book v4 against no book

Imitation of Savi (`BookFast --b-imitates Savi --centre`), 40k nodes +-30%, shipped weights, same seeds in both arms, games are mostly unique:

| Size | With book | Without | Difference | As F | As S | Book moves per game |
|---|---|---|---|---|---|---|
| 15x11 | 41.2% (300) | 50.0% (300) | -8.8 +-7.1 | -20.7 +-9.7 | +3.0 +-10.2 | 11.0 |
| 8x12 | 48.5% (200) | 47.2% (200) | +1.3 +-6.0 | -4.0 +-8.7 | +6.5 +-8.1 | 8.2 |
| 15x22 | 53.0% (200) | 64.8% (200) | -11.7 +-9.6 | -6.5 +-13.4 | -17.0 +-13.6 | 6.5 |
| mean | | | **-6.4 +-4.5** | | | |

Real protocol, book v4 jar against the same code without book, 5 game threads on the loaded machine (unique games = games):

| Size | Score of the book side | Games | Book moves per game | Lost turns, own turns 0-9, on / off | Lost turns, all, on / off | Engine crashes |
|---|---|---|---|---|---|---|
| 15x11 | 51.7% +-11.9 | 60 | 8.0 | 0 / 5 of 600 | 57 / 91 | 0 |
| 8x12 | 51.8% +-7.7 | 57 | 6.4 | 1 / 6 of 570 | 72 / 81 | 3 of 60 |
| 15x22 | 45.3% +-13.5 | 53 | not counted | 12 / 20 of 530 | 388 / 367 | 7 of 60 |

The one thing the book does measurably is fewer lost turns in the first ten own turns, 13 against 31 of 1700: its 40% stop is short there. The time plan can get that without a book, on any size, by a lower stop early.

Earlier, v2 with hand tables against the same code without book, fixed nodes, self-play: 52.4% +-3.7 (600 games, 580 unique), 3.0 book moves per game.

**First move, replication with new seeds (40k nodes, 60 games per cell, generic tables):** centre 49.1 +-3.8 (480), two off the centre 48.1 +-3.5 (480). The 20k result (48.3 against 56.8) did not replicate, so it was noise from picking the best of four arms. Where the search's own first choice differs from the centre (8x12, 9x20, 12x16, 15x11; 240 games each way) it scores 47.7 against 52.5 for the centre on the same sizes, not significant. So: **no first-move rule beats the centre on the sizes tested, and the centre without a search (`Timing.centreOnEmpty`) loses nothing against search.** F's edge in self-play at these nodes is about zero.

| size | search plays (offset) | centre | one off | two off | search's choice if further |
|---|---|---|---|---|---|
| 7x9 | 0 | 34.2 | | 43.3 | |
| 8x12 | -1 | 48.3 | 45.8 | 40.0 | |
| 9x20 | -5 | 48.3 | | 55.0 | 44.2 |
| 10x10 | 0 | 61.7 | | 56.7 | |
| 12x16 | -3 | 57.5 | | 50.8 | 46.7 |
| 15x11 | -1 | 55.8 | 54.2 | 50.0 | |
| 15x22 | +1 (same as centre by mirror) | 46.7 | | 38.3 | |
| 20x9 | 0 | 40.0 | | 50.8 | |

Server games give the same answer: net of the two bots' ratings, F's result by first-move offset is within a few points of zero on all three sizes (`suites/book-lines.txt`, ratings fitted on the same games, so this is a weak check).

