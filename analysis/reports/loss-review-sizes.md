# Loss review by board size: hand-eval searches at our moves

Source: `loss-review-sizes-raw.md` (GameReview, single thread, hand evaluation, short = 45 ms, deep = 2000 ms, scores in logit from OUR view, mate scores shown as `loss N` = plies to the forced loss). Only our moves were searched. 'Point of no return' = the first searched ply from which the score stays at or below -1.00 for the rest of the game; 'before end' counts plies including the searched one.

| Size | Opponent | Our side | Plies | Our moves searched | Moves differing from played: deep / short / deep vs short | Deep first <= -1.00 (before end) | Short first <= -1.00 | Deep point of no return | Short point of no return | Deep first forced loss | Short first forced loss | Deep move differs from played at the 3 moves up to the deep point of no return | Game |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 15x11 | Savi8 | S | 201 | 94 | 34 / 26 / 33 | 46 (156 before end) | 50 (152 before end) | 178 (24 before end) | 188 (14 before end) | 188 (14 before end) | 188 (14 before end) | 0 of 3 (none) | 384b1906 |
| 15x11 | Savi8 | F | 112 | 56 | 33 / 20 / 26 | 49 (64 before end) | 31 (82 before end) | 49 (64 before end) | 69 (44 before end) | 107 (6 before end) | 107 (6 before end) | 2 of 3 (47: 6* vs 3, 49: 6 vs 5) | 86d1ed47 |
| 15x11 | Savi8 | F | 144 | 67 | 40 / 13 / 40 | 34 (111 before end) | 38 (107 before end) | 86 (59 before end) | 38 (107 before end) | 143 (2 before end) | 143 (2 before end) | 3 of 3 (80: 8 vs 7, 84: 7 vs 4, 86: 4 vs 7) | 8a6838ab |
| 15x11 | Savi8 | S | 208 | 101 | 40 / 17 / 35 | 65 (144 before end) | 47 (162 before end) | 185 (24 before end) | 191 (18 before end) | 191 (18 before end) | 197 (12 before end) | 1 of 3 (183: 0 vs a) | 7b9d3eaf |
| 15x11 | Savi8 | F | 203 | 101 | 52 / 33 / 55 | 59 (145 before end) | 61 (143 before end) | 73 (131 before end) | 75 (129 before end) | 190 (14 before end) | 194 (10 before end) | 2 of 3 (69: 5 vs 4, 73: 7* vs 4) | 791a6fc2 |
| 15x11 | Savi8 | S | 128 | 62 | 37 / 23 / 35 | 40 (89 before end) | 42 (87 before end) | 44 (85 before end) | 42 (87 before end) | 119 (10 before end) | 121 (8 before end) | 2 of 3 (40: 4* vs 4, 42: 9 vs 4) | 7e073388 |
| 15x11 | Savi8 | F | 157 | 74 | 37 / 14 / 40 | 36 (122 before end) | 36 (122 before end) | 50 (108 before end) | 52 (106 before end) | 150 (8 before end) | 150 (8 before end) | 0 of 3 (none) | b1cb6881 |
| 15x22 | Savi7 | S | 169 | 74 | 40 / 5 / 38 | 74 (96 before end) | 70 (100 before end) | 138 (32 before end) | 166 (4 before end) | 166 (4 before end) | 166 (4 before end) | 3 of 3 (134: 4 vs b*, 136: d* vs d, 138: 4 vs d) | 0afc31d4 |
| 15x11 | Savi7b | F | 142 | 60 | 31 / 16 / 32 | 53 (90 before end) | 53 (90 before end) | 53 (90 before end) | 59 (84 before end) | 131 (12 before end) | 133 (10 before end) | 3 of 3 (49: 1 vs 9, 51: a vs 6, 53: 1 vs 6) | c4ca211c |
| 15x11 | Savi7b | S | 153 | 62 | 32 / 10 / 30 | 34 (120 before end) | 34 (120 before end) | 106 (48 before end) | 40 (114 before end) | 138 (16 before end) | 144 (10 before end) | 3 of 3 (102: 9 vs 7, 104: 9 vs 1, 106: 3 vs 8) | 96321eb5 |
| 15x11 | Savi7b | S | 182 | 81 | 44 / 23 / 37 | 40 (143 before end) | 40 (143 before end) | 149 (34 before end) | 153 (30 before end) | 171 (12 before end) | 175 (8 before end) | 2 of 3 (147: 3 vs 7, 149: a vs 0) | f9b0b6f4 |
| 15x11 | Savi7b | S | 210 | 95 | 48 / 18 / 48 | 35 (176 before end) | 39 (172 before end) | 113 (98 before end) | 123 (88 before end) | 199 (12 before end) | 205 (6 before end) | 1 of 3 (113: 3 vs 6) | 9dd6922e |
| 15x11 | Savi7b | F | 242 | 108 | 47 / 20 / 47 | 51 (192 before end) | 49 (194 before end) | 51 (192 before end) | 55 (188 before end) | 227 (16 before end) | 231 (12 before end) | 2 of 3 (49: 9* vs 6, 51: 6 vs 9) | d921b131 |

Medians over the reviewed, untruncated games (plies before the end): deep first <= -1.00: 122; short first <= -1.00: 122; deep point of no return: 64; short point of no return: 87; deep forced loss seen: 12; short forced loss seen: 8.

## Savi beater 2.2 (S) vs Savi8 - "The King", game 384b1906-8088-4dd2-a25e-34d1f43c73fc, 15x11

Rows around the deep point of no return (ply 178). `played` = move in the game, short = 45 ms, deep = 2000 ms (scores from our view):

| ply | played | short move | short | deep move | deep | depth |
|---|---|---|---|---|---|---|
| 170 | 3 | 0 | -2.25 | 3 | -1.46 | 14 |
| 172 | 0 | 3 | -1.53 | 3 | -1.14 | 14 |
| 174 | 0 | 3 | -1.29 | 0 | -1.08 | 15 |
| 176 | 3 | 3 | -1.46 | 3 | -0.20 | 18 |
| 178 **<-** | 3 | 3 | -1.29 | 3 | -1.30 | 20 |
| 180 | 8 | 8 | -0.44 | 8 | -1.75 | 19 |
| 182 | 9 | 9 | -0.34 | 9 | -1.75 | 19 |
| 184 | 7* | 7* | -0.05 | 7 | -1.58 | 18 |

## Savi beater 2.2 (F) vs Savi8 - "The King", game 86d1ed47-ba83-4847-b666-acea6c07318f, 15x11

Rows around the deep point of no return (ply 49). `played` = move in the game, short = 45 ms, deep = 2000 ms (scores from our view):

| ply | played | short move | short | deep move | deep | depth |
|---|---|---|---|---|---|---|
| 41 | 4 | 4 | -1.33 | 4 | -0.70 | 10 |
| 43 | 5 | 3 | -0.74 | 3 | -0.70 | 8 |
| 45 | 5 | 3 | -0.72 | 5 | -0.59 | 7 |
| 47 | 6* | 6* | -0.95 | 3 | -0.59 | 7 |
| 49 **<-** | 6 | 6 | -0.16 | 5 | -2.00 | 8 |
| 51 | 4 | 6 | -2.08 | 6 | -2.02 | 9 |
| 53 | 6 | 7 | -2.21 | 6 | -2.89 | 9 |
| 55 | 5 | 5 | -1.64 | 3 | -2.68 | 9 |

## Savi beater 2.2 (F) vs Savi8 - "The King", game 8a6838ab-177d-4c31-8efa-7cb6397e11c1, 15x11

Rows around the deep point of no return (ply 86). `played` = move in the game, short = 45 ms, deep = 2000 ms (scores from our view):

| ply | played | short move | short | deep move | deep | depth |
|---|---|---|---|---|---|---|
| 76 | 6 | 6 | -4.09 | 6 | -3.74 | 9 |
| 78 | 6 | 6 | -3.74 | 6 | -3.76 | 9 |
| 80 | 8 | 8 | -3.61 | 7 | -3.02 | 9 |
| 84 | 7 | a | -3.48 | 4 | -0.65 | 9 |
| 86 **<-** | 4 | 4 | -2.45 | 7 | -1.66 | 8 |
| 88 | 4 | 7 | -1.39 | 7 | -1.16 | 8 |
| 91 | 3* | 3* | -2.04 | 3 | -3.60 | 7 |
| 93 | 3 | 3 | -7.45 | 3 | -7.88 | 9 |

## Savi beater 2.2 (S) vs Savi8 - "The King", game 7b9d3eaf-bd3b-4f14-bc61-8f7841db25ba, 15x11

Rows around the deep point of no return (ply 185). `played` = move in the game, short = 45 ms, deep = 2000 ms (scores from our view):

| ply | played | short move | short | deep move | deep | depth |
|---|---|---|---|---|---|---|
| 177 | 1 | 1 | -1.22 | 1 | -1.82 | 11 |
| 179 | 0 | 0 | -1.85 | 0 | -0.28 | 11 |
| 181 | a | a | -1.82 | a | -0.28 | 13 |
| 183 | 0 | a | -1.20 | a | -0.06 | 14 |
| 185 **<-** | a | a | +0.21 | a | -1.54 | 16 |
| 187 | a | a | -0.19 | a | -1.75 | 17 |
| 189 | 9 | 9 | -0.53 | a | -1.88 | 17 |
| 191 | 9 | a | -1.58 | a | loss 20 | 19 |

## Savi beater 2.2 (F) vs Savi8 - "The King", game 791a6fc2-d142-47ec-9bdf-7d25b35d238b, 15x11

Rows around the deep point of no return (ply 73). `played` = move in the game, short = 45 ms, deep = 2000 ms (scores from our view):

| ply | played | short move | short | deep move | deep | depth |
|---|---|---|---|---|---|---|
| 65 | 4 | 4 | -2.61 | 4 | -2.99 | 9 |
| 67 | 5 | 5 | -2.61 | 5 | -0.78 | 9 |
| 69 | 5 | 5 | -2.37 | 4 | -0.64 | 9 |
| 71 | 3 | 3 | -0.99 | 3 | -0.92 | 9 |
| 73 **<-** | 7* | 4 | -0.96 | 4 | -1.09 | 8 |
| 75 | 8 | 6 | -1.11 | 6 | -1.14 | 8 |
| 77 | 4 | 4 | -1.27 | 5 | -1.82 | 9 |
| 79 | 5 | 5 | -1.35 | 5 | -2.44 | 9 |

## Savi beater 2.2 (S) vs Savi8 - "The King", game 7e073388-a1c2-45a6-a273-f519fb97c225, 15x11

Rows around the deep point of no return (ply 44). `played` = move in the game, short = 45 ms, deep = 2000 ms (scores from our view):

| ply | played | short move | short | deep move | deep | depth |
|---|---|---|---|---|---|---|
| 36 | 5 | 4 | -0.78 | 6 | -0.69 | 7 |
| 38 | 4 | 4 | -0.58 | 6 | -0.55 | 6 |
| 40 | 4* | 4* | -0.55 | 4 | -1.09 | 7 |
| 42 | 9 | 4 | -1.15 | 4 | -0.86 | 8 |
| 44 **<-** | 6 | 5 | -1.49 | 6 | -1.64 | 9 |
| 47 | 5 | 5 | -1.72 | 5 | -2.11 | 8 |
| 49 | 8 | 8 | -2.14 | 4 | -2.64 | 9 |
| 51 | 8 | 8 | -1.79 | 5 | -3.37 | 7 |

## Savi beater 2.2 (F) vs Savi8 - "The King", game b1cb6881-ef2d-423e-aed5-e088224fcb36, 15x11

Rows around the deep point of no return (ply 50). `played` = move in the game, short = 45 ms, deep = 2000 ms (scores from our view):

| ply | played | short move | short | deep move | deep | depth |
|---|---|---|---|---|---|---|
| 42 | 9 | 3 | -1.11 | 9 | -1.59 | 8 |
| 44 | 4 | 3 | -0.68 | 3 | -0.94 | 7 |
| 46 | 3 | 9 | -1.18 | 3 | -0.92 | 7 |
| 48 | 6 | 6 | -2.07 | 6 | -0.95 | 7 |
| 50 **<-** | 3 | 3 | -0.89 | 3 | -1.03 | 7 |
| 52 | 2* | 2* | -1.71 | 3 | -1.57 | 8 |
| 54 | 3 | 3 | -1.85 | 3 | -2.22 | 9 |
| 56 | 2 | 2 | -2.27 | 2 | -2.62 | 12 |

## Savi5 beater 2.1 (S) vs Savi7 - "The Prince", game 0afc31d4-bd49-4864-baea-588fccec4e36, 15x22

Rows around the deep point of no return (ply 138). `played` = move in the game, short = 45 ms, deep = 2000 ms (scores from our view):

| ply | played | short move | short | deep move | deep | depth |
|---|---|---|---|---|---|---|
| 130 | c | c | -2.88 | c | -2.16 | 5 |
| 132 | 8 | 8 | -0.63 | 8 | -1.38 | 5 |
| 134 | 4 | 4 | -0.14 | b* | -1.45 | 4 |
| 136 | d* | d* | -1.19 | d | +0.48 | 6 |
| 138 **<-** | 4 | 4 | -1.60 | d | -1.28 | 8 |
| 140 | d | d | -1.72 | e | -1.49 | 8 |
| 142 | c | c | -1.84 | b | -1.79 | 8 |
| 144 | 4 | 4 | -1.79 | c | -1.58 | 8 |

## Savi5 beater 2.1 (F) vs Savi 7 - "The Prince, game c4ca211c-b9ad-4a2a-a284-348f6e03955a, 15x11

Rows around the deep point of no return (ply 53). `played` = move in the game, short = 45 ms, deep = 2000 ms (scores from our view):

| ply | played | short move | short | deep move | deep | depth |
|---|---|---|---|---|---|---|
| 45 | 4 | 4 | -0.02 | 4 | -0.05 | 9 |
| 47 | 5 | 5 | -0.60 | 5 | -0.48 | 8 |
| 49 | 1 | 1 | -0.05 | 9 | +0.01 | 7 |
| 51 | a | a | -0.10 | 6 | -0.56 | 7 |
| 53 **<-** | 1 | 1 | -1.21 | 6 | -1.28 | 8 |
| 55 | 2 | 2 | -1.05 | 2 | -1.50 | 8 |
| 57 | 2 | 2 | -0.63 | 1 | -1.38 | 8 |
| 59 | 0* | 1 | -1.37 | 1 | -1.24 | 7 |

## Savi5 beater 2.1 (S) vs Savi 7 - "The Prince, game 96321eb5-5c07-404f-9292-55820aaa981d, 15x11

Rows around the deep point of no return (ply 106). `played` = move in the game, short = 45 ms, deep = 2000 ms (scores from our view):

| ply | played | short move | short | deep move | deep | depth |
|---|---|---|---|---|---|---|
| 98 | 4 | 4 | -3.42 | 4 | -3.58 | 10 |
| 100 | 4 | 4 | -3.88 | 4 | -3.47 | 10 |
| 102 | 9 | 9 | -3.74 | 7 | -3.52 | 10 |
| 104 | 9 | 9 | -3.64 | 1 | -0.54 | 9 |
| 106 **<-** | 3 | 3 | -3.61 | 8 | -1.39 | 10 |
| 108 | 1 | 1 | -1.67 | 1 | -1.45 | 10 |
| 110 | 2* | 2* | -1.51 | 2* | -1.48 | 10 |
| 112 | 1 | 1 | -1.45 | 1 | -3.76 | 10 |

## Savi5 beater 2.1 (S) vs Savi 7 - "The Prince, game f9b0b6f4-cb65-4b50-b042-4f4e69ad64ae, 15x11

Rows around the deep point of no return (ply 149). `played` = move in the game, short = 45 ms, deep = 2000 ms (scores from our view):

| ply | played | short move | short | deep move | deep | depth |
|---|---|---|---|---|---|---|
| 141 | 7 | 7 | -2.35 | 7 | -1.89 | 8 |
| 143 | a | 8 | -2.23 | 3 | -1.71 | 8 |
| 145 | 2 | 9 | -1.87 | 2 | -0.99 | 7 |
| 147 | 3 | 0* | -2.52 | 7 | -0.80 | 8 |
| 149 **<-** | a | 9 | -1.68 | 0 | -2.30 | 8 |
| 151 | a | a | -0.64 | 7 | -3.76 | 8 |
| 153 | 8 | a* | -3.98 | 7 | -4.50 | 8 |
| 155 | a | 7 | -3.98 | 7 | -4.30 | 8 |

## Savi5 beater 2.1 (S) vs Savi 7 - "The Prince, game 9dd6922e-e63a-4363-8bd7-259cd5bc3c0e, 15x11

Rows around the deep point of no return (ply 113). `played` = move in the game, short = 45 ms, deep = 2000 ms (scores from our view):

| ply | played | short move | short | deep move | deep | depth |
|---|---|---|---|---|---|---|
| 105 | 7 | 7 | -1.80 | 3 | -1.06 | 9 |
| 107 | 3 | 3 | -0.91 | 8 | -1.22 | 9 |
| 109 | 8 | 8 | -0.94 | 8 | -0.98 | 10 |
| 111 | 6 | 6 | -1.02 | 6 | -0.95 | 10 |
| 113 **<-** | 3 | 3 | -0.96 | 6 | -1.01 | 10 |
| 115 | 8 | 8 | -0.92 | 8 | -1.02 | 8 |
| 117 | 3 | 3 | -0.97 | 3 | -1.22 | 8 |
| 119 | a | a | -0.77 | 3 | -2.09 | 8 |

## Savi5 beater 2.1 (F) vs Savi 7 - "The Prince, game d921b131-24ab-4cf7-9ed3-e9d22a7e6d32, 15x11

Rows around the deep point of no return (ply 51). `played` = move in the game, short = 45 ms, deep = 2000 ms (scores from our view):

| ply | played | short move | short | deep move | deep | depth |
|---|---|---|---|---|---|---|
| 43 | 6 | 6 | +0.24 | 2 | -0.36 | 6 |
| 45 | 9 | 9 | -0.61 | 4 | -0.40 | 7 |
| 47 | 7 | 6* | -0.78 | 7 | -0.65 | 7 |
| 49 | 9* | 9* | -1.01 | 6 | -0.66 | 7 |
| 51 **<-** | 6 | 7 | -0.96 | 9 | -1.41 | 9 |
| 53 | 6 | 6 | -0.82 | 6 | -2.08 | 9 |
| 55 | 6 | 6 | -1.80 | 6 | -2.46 | 9 |
| 57 | 6 | 4 | -2.08 | 4 | -2.75 | 9 |

## Short search versus deep search (score gap)

Gap = short (45 ms) score minus deep (2000 ms) score at the same position, our view, positions where neither search reports a forced result and the deep search really ran (depth >= 3). Positive = the 45 ms search is more optimistic than the 2 s search.

| Game | Opponent | Rows | Mean gap, whole game | Mean gap, last 30 plies | Mean gap, last 60 plies | Deep depth median | Rows with gap >= +1.00 |
|---|---|---|---|---|---|---|---|
| 384b1906 | Savi8 | 87 | +0.11 | +0.34 | +0.46 | 9 | 9 |
| 86d1ed47 | Savi8 | 53 | +0.00 | -0.15 | -0.00 | 9 | 5 |
| 8a6838ab | Savi8 | 66 | +0.08 | +0.33 | +0.20 | 9 | 5 |
| 7b9d3eaf | Savi8 | 92 | +0.11 | +0.07 | +0.07 | 9 | 6 |
| 791a6fc2 | Savi8 | 95 | -0.01 | +0.10 | +0.24 | 9 | 11 |
| 7e073388 | Savi8 | 58 | +0.13 | +0.53 | +0.13 | 8 | 6 |
| b1cb6881 | Savi8 | 71 | +0.25 | +0.22 | +0.58 | 9 | 8 |
| 0afc31d4 | Savi7 | 72 | +0.17 | +0.64 | +0.24 | 6 | 8 |
| c4ca211c | Savi7b | 55 | -0.00 | +0.20 | +0.20 | 9 | 5 |
| 96321eb5 | Savi7b | 55 | -0.08 | -0.50 | -0.11 | 9 | 4 |
| f9b0b6f4 | Savi7b | 76 | +0.45 | +0.03 | +0.07 | 9 | 9 |
| 9dd6922e | Savi7b | 89 | +0.18 | -0.04 | +0.26 | 9 | 8 |
| d921b131 | Savi7b | 100 | -0.19 | -0.11 | -0.24 | 9 | 5 |

All reviewed games: mean gap +0.09 over 969 rows; last 60 plies +0.16 over 306 rows; last 30 plies +0.17 over 118 rows.

## Our blasts in the reviewed games

For every searched move of ours that was a blast (`*`): did the 2000 ms search choose the same blast, a quiet drop in the same column, or something else; and the change of the deep score from this move to our next searched move (after the opponent's reply), compared with our quiet moves. Rows with depth <= 2 and no mate (forced-move shortcut) are ignored.

Blasts searched: 82. 2000 ms search: same blast 20, quiet drop in the same column 13, other move 49. 45 ms search: same blast 69, quiet drop in the same column 2, other move 11.

- Deep score change from our blast move to our next searched move: mean -0.79, median -0.42 over 72 moves (mate-range scores excluded).
- Deep score change from our blast, deep agrees move to our next searched move: mean -0.36, median -0.17 over 17 moves (mate-range scores excluded).
- Deep score change from our blast, deep disagrees move to our next searched move: mean -0.93, median -0.59 over 55 moves (mate-range scores excluded).
- Deep score change from our quiet move to our next searched move: mean -0.01, median -0.02 over 838 moves (mate-range scores excluded).
- Deep score change from our quiet, deep agrees move to our next searched move: mean +0.11, median +0.01 over 422 moves (mate-range scores excluded).
- Deep score change from our quiet, deep disagrees move to our next searched move: mean -0.13, median -0.08 over 416 moves (mate-range scores excluded).

| Game | Opponent | Ply | Played | Deep move | Deep score then | Short move | Deep score at our next move |
|---|---|---|---|---|---|---|---|
| 384b1906 | Savi8 | 36 | 7* | 3 | -0.18 | 7* | -0.39 |
| 384b1906 | Savi8 | 86 | 3* | 3 | -1.88 | 3* | -1.96 |
| 384b1906 | Savi8 | 106 | 3* | 3* | -1.48 | 3* | -1.54 |
| 384b1906 | Savi8 | 126 | 3* | 0 | -1.25 | 3* | -1.09 |
| 384b1906 | Savi8 | 158 | 3* | 3 | -1.03 | 3* | -2.35 |
| 384b1906 | Savi8 | 184 | 7* | 7 | -1.58 | 7* | - |
| 86d1ed47 | Savi8 | 25 | 4* | 4* | +0.16 | 4* | -0.62 |
| 86d1ed47 | Savi8 | 47 | 6* | 3 | -0.59 | 6* | -2.00 |
| 86d1ed47 | Savi8 | 75 | 5* | 3 | -1.84 | 3* | -4.57 |
| 86d1ed47 | Savi8 | 97 | 2* | 1* | -4.35 | 2* | -6.05 |
| 8a6838ab | Savi8 | 24 | 6* | 6 | -0.10 | 4 | -0.26 |
| 8a6838ab | Savi8 | 44 | 8* | 7* | -1.16 | 8* | -1.75 |
| 8a6838ab | Savi8 | 66 | 7* | 7 | -2.01 | 7* | -3.04 |
| 8a6838ab | Savi8 | 91 | 3* | 3 | -3.60 | 3* | -7.88 |
| 8a6838ab | Savi8 | 119 | 4* | 4* | -4.94 | 4* | -4.68 |
| 8a6838ab | Savi8 | 139 | 9* | 9 | -2.57 | 9* | - |
| 7b9d3eaf | Savi8 | 29 | 4* | 4* | -0.55 | 4* | -0.55 |
| 7b9d3eaf | Savi8 | 55 | 4* | 6 | -0.75 | 4* | -0.81 |
| 7b9d3eaf | Savi8 | 75 | 8* | 4 | -1.14 | 8* | -2.16 |
| 7b9d3eaf | Savi8 | 97 | a* | a* | -1.76 | a* | -1.87 |
| 7b9d3eaf | Savi8 | 117 | 0* | 0* | -0.24 | 0* | -1.91 |
| 7b9d3eaf | Savi8 | 143 | 7* | 7* | -0.58 | 7* | -1.46 |
| 7b9d3eaf | Savi8 | 167 | 0* | 2 | -1.27 | 0* | -1.71 |
| 7b9d3eaf | Savi8 | 197 | a* | a* | loss 12 | a* | -10.09 |
| 791a6fc2 | Savi8 | 21 | 6* | 4 | +0.07 | 6* | -0.24 |
| 791a6fc2 | Savi8 | 53 | 1* | 4 | -0.48 | 4 | -0.86 |
| 791a6fc2 | Savi8 | 73 | 7* | 4 | -1.09 | 4 | -1.14 |
| 791a6fc2 | Savi8 | 93 | 5* | 5 | -3.96 | 5* | -4.16 |
| 791a6fc2 | Savi8 | 127 | 9* | 3 | -4.71 | 9* | -6.27 |
| 791a6fc2 | Savi8 | 165 | 0* | 0* | -4.97 | 0 | -5.14 |
| 791a6fc2 | Savi8 | 186 | 0* | 0* | -7.05 | 0* | -5.34 |
| 7e073388 | Savi8 | 40 | 4* | 4 | -1.09 | 4* | -0.86 |
| 7e073388 | Savi8 | 61 | 6* | 4 | -3.46 | 6* | -3.78 |
| 7e073388 | Savi8 | 91 | 3* | 8 | -4.16 | 3* | -4.51 |
| 7e073388 | Savi8 | 111 | 7* | 7 | -3.15 | 7* | -3.51 |
| b1cb6881 | Savi8 | 31 | 2* | 6 | +0.19 | 2* | -0.55 |
| b1cb6881 | Savi8 | 52 | 2* | 3 | -1.57 | 2* | -2.22 |
| b1cb6881 | Savi8 | 72 | 3* | 3* | -2.55 | 3* | -3.00 |
| b1cb6881 | Savi8 | 92 | 4* | 4* | -3.48 | 4* | -2.88 |
| b1cb6881 | Savi8 | 122 | 1* | 0 | -4.83 | 1* | -10.82 |
| b1cb6881 | Savi8 | 142 | 8* | 8* | -4.35 | 8* | -10.69 |
| 0afc31d4 | Savi7 | 24 | 9* | f | -0.09 | 9* | -0.19 |
| 0afc31d4 | Savi7 | 46 | 4* | 4* | +0.02 | 4* | -0.37 |
| 0afc31d4 | Savi7 | 70 | b* | 9 | -0.68 | b* | -0.93 |
| 0afc31d4 | Savi7 | 90 | 3* | c | -2.42 | 3* | -3.32 |
| 0afc31d4 | Savi7 | 112 | c* | 7* | -3.78 | c* | -3.38 |
| 0afc31d4 | Savi7 | 136 | d* | d | +0.48 | d* | -1.28 |
| 0afc31d4 | Savi7 | 164 | 3* | 8* | -1.50 | g | -10.10 |
| c4ca211c | Savi7b | 35 | 4* | 2 | -0.31 | 4 | -0.48 |
| c4ca211c | Savi7b | 59 | 0* | 1 | -1.24 | 1 | -3.38 |
| c4ca211c | Savi7b | 79 | 6* | 3 | -3.72 | 6* | -5.09 |
| c4ca211c | Savi7b | 109 | 9* | 1* | -6.08 | 9* | -7.96 |
| c4ca211c | Savi7b | 129 | 8* | 1 | -5.59 | 8* | -10.09 |
| 96321eb5 | Savi7b | 22 | 4* | 5 | -0.48 | 4* | -0.40 |
| 96321eb5 | Savi7b | 44 | 6* | 7 | -1.05 | 3* | -1.71 |
| 96321eb5 | Savi7b | 64 | 3* | 2 | -1.15 | 3* | -2.02 |
| 96321eb5 | Savi7b | 90 | 3* | 3 | -2.35 | 1 | -3.29 |
| 96321eb5 | Savi7b | 110 | 2* | 2* | -1.48 | 2* | -3.76 |
| 96321eb5 | Savi7b | 136 | 9* | 8 | -2.96 | 8 | -10.09 |
| f9b0b6f4 | Savi7b | 38 | 4* | 6* | -0.88 | 4* | -1.16 |
| f9b0b6f4 | Savi7b | 57 | 2* | a | -0.79 | 2* | -1.63 |
| f9b0b6f4 | Savi7b | 81 | 6* | 6 | -2.17 | 6* | -6.36 |
| f9b0b6f4 | Savi7b | 101 | 3* | 2 | -4.07 | 3* | -7.16 |
| f9b0b6f4 | Savi7b | 123 | 3* | 3* | -2.61 | 3* | -3.06 |
| f9b0b6f4 | Savi7b | 163 | 8* | 7 | -3.46 | 8* | -3.86 |
| 9dd6922e | Savi7b | 21 | 4* | 6 | -0.25 | 4* | -0.41 |
| 9dd6922e | Savi7b | 45 | 7* | 7* | -1.46 | 7* | -1.57 |
| 9dd6922e | Savi7b | 67 | 3* | 6 | -1.86 | 3* | -2.21 |
| 9dd6922e | Savi7b | 87 | 3* | 6 | -0.44 | 3* | -1.14 |
| 9dd6922e | Savi7b | 123 | 2* | 3 | -2.86 | 2* | -5.65 |
| 9dd6922e | Savi7b | 143 | 9* | 9* | -6.96 | 0 | -6.99 |
| 9dd6922e | Savi7b | 169 | 1* | 1* | -7.02 | 1* | -8.34 |
| d921b131 | Savi7b | 27 | 8* | 3 | -0.04 | 8* | -0.31 |
| d921b131 | Savi7b | 49 | 9* | 6 | -0.66 | 9* | -1.41 |
| d921b131 | Savi7b | 73 | 2* | 5 | -1.81 | 2* | -4.30 |
| d921b131 | Savi7b | 93 | a* | 4 | -2.97 | a* | -5.93 |
| d921b131 | Savi7b | 115 | 2* | 9 | -5.54 | 2* | - |
| d921b131 | Savi7b | 135 | 4* | 1 | -5.84 | 4* | -6.17 |
| d921b131 | Savi7b | 155 | 8* | a | -3.23 | a | -3.43 |
| d921b131 | Savi7b | 175 | 0* | a | -2.70 | 0* | -3.89 |
| d921b131 | Savi7b | 207 | 0* | 1* | -1.83 | 0* | -1.80 |
| d921b131 | Savi7b | 233 | 8* | 8* | loss 10 | 8* | -10.09 |

## Deep-score trajectory (every 10th searched ply)

- 15x11 Savi beater 2.2 vs Savi8 - "The King" (384b1906): 2:+0.05, 12:+0.10, 22:-0.17, 32:-0.24, 42:-0.78, 52:-1.36, 62:-1.48, 72:-1.09, 82:-1.50, 92:-1.84, 102:-1.67, 112:-1.29, 122:-1.30, 132:-1.36, 142:-0.67, 152:-0.95, 162:-2.25, 172:-1.14, 182:-1.75, 192:loss 10
- 15x11 Savi beater 2.2 vs Savi8 - "The King" (86d1ed47): 1:+0.18, 11:+0.27, 21:+0.21, 31:-0.42, 41:-0.70, 51:-2.02, 61:-2.64, 71:-3.04, 81:-7.82, 91:-4.79, 101:-6.68, 111:loss 2
- 15x11 Savi beater 2.2 vs Savi8 - "The King" (8a6838ab): 1:+0.18, 11:+0.25, 22:-0.15, 32:-0.61, 42:-0.84, 52:-2.16, 62:-1.42, 72:-3.98, 82:-4.21, 93:-7.88, 103:-7.51, 113:-5.32, 123:-4.78, 133:-3.46, 143:loss 2
- 15x11 Savi beater 2.2 vs Savi8 - "The King" (7b9d3eaf): 2:-0.21, 12:+0.10, 22:-0.28, 33:-0.30, 43:-0.73, 53:-0.68, 63:-0.41, 73:-0.32, 83:-2.43, 93:-0.58, 103:-1.46, 113:-0.84, 123:-3.13, 133:-1.16, 143:-0.58, 153:-3.95, 163:-1.36, 173:-1.22, 183:-0.06, 193:loss 16, 203:loss 6
- 15x11 Savi beater 2.2 vs Savi8 - "The King" (791a6fc2): 1:+0.18, 11:+0.27, 21:+0.07, 31:-0.58, 41:-0.48, 51:-0.38, 61:-2.28, 71:-0.92, 81:-2.69, 91:-3.89, 101:-6.10, 111:-3.94, 121:-4.83, 131:-6.34, 141:-3.39, 151:-3.69, 161:-3.96, 171:-5.89, 180:-5.86, 190:loss 14, 200:-1.86
- 15x11 Savi beater 2.2 vs Savi8 - "The King" (7e073388): 2:-0.21, 12:+0.10, 22:-0.11, 32:-0.77, 42:-0.86, 53:-3.82, 63:-3.78, 73:-4.56, 83:-3.34, 93:-4.51, 103:-7.84, 113:-3.51, 123:loss 6
- 15x11 Savi beater 2.2 vs Savi8 - "The King" (b1cb6881): 1:+0.18, 11:+0.27, 21:+0.21, 31:+0.19, 42:-1.59, 52:-1.57, 62:-2.18, 72:-2.55, 82:-4.33, 92:-3.48, 102:-6.06, 112:-4.50, 122:-4.83, 132:-11.40, 142:-4.35, 152:loss 6
- 15x22 Savi5 beater 2.1 vs Savi7 - "The Prince" (0afc31d4): 22:-0.32, 32:-0.29, 42:+0.06, 52:-0.43, 62:-0.38, 72:-0.93, 82:-1.20, 92:-3.32, 102:-2.94, 112:-3.78, 122:-3.15, 132:-1.38, 142:-1.79, 152:-2.88, 162:-1.08
- 15x11 Savi5 beater 2.1 vs Savi 7 - "The Prince (c4ca211c): 21:+0.21, 31:+0.09, 41:-0.20, 51:-0.56, 61:-3.38, 71:-6.88, 81:-5.09, 91:-5.53, 101:-4.82, 111:-7.96, 121:-7.13, 131:loss 12, 141:loss 2
- 15x11 Savi5 beater 2.1 vs Savi 7 - "The Prince (96321eb5): 22:-0.48, 32:-0.88, 42:-0.71, 52:-1.70, 62:-0.86, 72:-3.34, 82:-3.43, 92:-3.29, 102:-3.52, 112:-3.76, 122:-1.71, 132:-2.36, 142:loss 12, 152:loss 2
- 15x11 Savi5 beater 2.1 vs Savi 7 - "The Prince (f9b0b6f4): 22:-0.05, 32:-0.13, 42:-1.11, 51:-0.99, 61:-2.04, 71:-2.00, 81:-2.17, 91:-6.47, 101:-4.07, 111:-7.33, 121:-2.61, 131:-3.48, 141:-1.89, 151:-3.76, 161:-4.45, 171:loss 12, 181:loss 2
- 15x11 Savi5 beater 2.1 vs Savi 7 - "The Prince (9dd6922e): 21:-0.25, 31:-0.65, 41:-0.97, 51:-1.69, 61:-2.19, 71:-2.30, 81:-1.35, 91:-1.70, 101:-1.96, 111:-0.95, 121:-2.37, 131:-6.29, 141:-6.34, 151:-8.45, 161:-6.80, 171:-8.34, 181:-8.44, 191:-8.40, 201:loss 8
- 15x11 Savi5 beater 2.1 vs Savi 7 - "The Prince (d921b131): 21:-0.38, 31:-0.30, 41:-0.46, 51:-1.41, 61:-2.88, 71:-1.61, 81:-4.54, 91:-1.79, 101:-6.16, 111:-5.41, 121:-8.98, 131:-5.47, 141:-7.39, 151:-4.05, 161:-3.64, 171:-2.46, 181:-3.96, 191:-1.54, 201:-2.16, 211:-2.64, 221:-2.81, 231:loss 12, 241:loss 2
