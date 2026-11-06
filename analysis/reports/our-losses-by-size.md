# Our losses by board size

All losses of team `No beaches?` (versions Savi5 beater 2.1 = 'ours 2.1', Savi beater 2.2 = 'ours 2.2') in matches.jsonl, de-duplicated. 'Our TO' = our timeouts in the game, 'Opp TO' = the opponent's. Finish category: see the definitions in server-analysis-by-size.md section 5.

19 losses.

'Winning cell' = direction of the six (H, V, up/down diagonal) with the column and row (0 = floor) of the last disc; '13-14' = the tokens of plies 13 and 14, i.e. the first moves after a 12-ply stack of the centre column (F5S5 six times) if the game began that way.

| Size | Opponent | Our side | Our version | Plies | Decided by | Our TO | Opp TO | Our blasts | Their blasts | Finish | Winning cell | 13-14 | Game id |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 8x12 | Savi5 | F | ours 2.1 | 136 | six | 0 | 0 | 3 | 4 | single threat not blocked [7] | diag down at 7,5 | - | e9582887 |
| 8x12 | Savi6 | S | ours 2.1 | 115 | six | 0 | 0 | 2 | 1 | loser's move opened the winning cell [a] | H at a,5 | - | 01c00039 |
| 8x12 | Savi6 | F | ours 2.1 | 122 | six | 0 | 0 | 2 | 4 | blocked the single threat, cell above won [6] | H at 6,6 | - | 440af18b |
| 15x22 | Savi7 | S | ours 2.1 | 169 | six | 0 | 0 | 7 | 7 | single threat not blocked [d] | diag up at d,6 | - | 0afc31d4 |
| 15x22 | Savi7 | F | ours 2.1 | 341 | blast | 1 | 0 | 14 | 13 | blast (own colour) | blast in d | - | 0eff6434 |
| 15x11 | Savi7b | F | ours 2.1 | 142 | six | 0 | 0 | 5 | 4 | blocked the single threat, cell above won [3] | H at 3,9 | F3S5 | c4ca211c |
| 15x11 | Savi7b | S | ours 2.1 | 153 | six | 0 | 0 | 6 | 4 | blocked the single threat, cell above won [7] | diag down at 7,5 | F5S4 | 96321eb5 |
| 15x11 | Savi7b | S | ours 2.1 | 182 | blast | 0 | 1 | 6 | 6 | blast (own colour) | blast in 3 | F5S4 | f9b0b6f4 |
| 15x11 | Savi7b | S | ours 2.1 | 210 | blast | 1 | 0 | 7 | 8 | blast (own colour) | blast in 1 | - | 9dd6922e |
| 15x11 | Savi7b | F | ours 2.1 | 242 | six | 0 | 0 | 10 | 5 | loser's move opened the winning cell [8] | diag up at 8,8 | - | d921b131 |
| 15x11 | Savi8 | F | ours 2.2 | 112 | six | 0 | 0 | 4 | 3 | blocked the single threat, cell above won [7] | diag up at 7,7 | F3S5 | 86d1ed47 |
| 15x11 | Savi8 | S | ours 2.2 | 128 | blast | 1 | 0 | 4 | 4 | blast (own colour) | blast in a | F5S4 | 7e073388 |
| 15x11 | Savi8 | F | ours 2.2 | 144 | six | 2 | 0 | 6 | 5 | double threat (>=2 winning columns) [3,4] | diag down at 3,7 | F3S5 | 8a6838ab |
| 15x11 | Savi8 | F | ours 2.2 | 157 | six | 1 | 0 | 6 | 5 | single threat not blocked [8] | diag down at 8,4 | F3S5 | b1cb6881 |
| 15x11 | Savi8 | S | ours 2.2 | 201 | six | 0 | 0 | 6 | 3 | double threat (>=2 winning columns) [2,3] | H+diag up at 2,10 | F5S4 | 384b1906 |
| 15x11 | Savi8 | F | ours 2.2 | 203 | six | 0 | 1 | 7 | 3 | loser's move opened the winning cell [7] | H at 7,11 | F3S5 | 791a6fc2 |
| 15x11 | Savi8 | S | ours 2.2 | 208 | six | 1 | 0 | 8 | 3 | loser's move opened the winning cell [3] | H at 3,2 | F5S4 | 7b9d3eaf |
| 15x11 | lalalala | S | ours 2.2 | 165 | six | 0 | 0 | 5 | 1 | loser's move opened the winning cell [7] | H+diag down at 7,3 | - | 45f300fd |
| 15x11 | lalalala | S | ours 2.2 | 176 | six | 1 | 0 | 6 | 1 | blocked the single threat, cell above won [7] | diag up at 7,2 | - | 62931620 |

Losses per size and opponent:

| Size | Opponent | Our losses | Our games vs it | Loss rate |
|---|---|---|---|---|
| 8x12 | Savi6 | 2 | 3 | 66.7% |
| 8x12 | Savi5 | 1 | 5 | 20.0% |
| 15x22 | Savi7 | 2 | 7 | 28.6% |
| 15x11 | Savi8 | 7 | 7 | 100.0% |
| 15x11 | Savi7b | 5 | 6 | 83.3% |
| 15x11 | lalalala | 2 | 14 | 14.3% |

## Last 14 plies of the losses to Savi8 and Savi7 (15x11 and 15x22)

Per ply: mover, column played (`*` = blast), and the immediate winning columns each side had before the move (`W` = the mover's, `T` = the opponent's, base-36 columns). A non-empty `T` list that the mover ignores is a lost game; two or more columns in `T` cannot be blocked.

### 15x22 ours 2.1 (S, Savi7), 169 plies, six - game 0afc31d4-bd49-4864-baea-588fccec4e36

| Ply | Mover | Move | W (mover wins at) | T (opponent wins at) |
|---|---|---|---|---|
| 156 | us | c | - | - |
| 157 | them | f | - | - |
| 158 | us | 6 | - | - |
| 159 | them | f | - | - |
| 160 | us | 4 | - | - |
| 161 | them | 4 | - | - |
| 162 | us | 8 | - | - |
| 163 | them | 8 | - | - |
| 164 | us | 3* | - | - |
| 165 | them | d | - | - |
| 166 | us | d | - | d |
| 167 | them | d* | - | d |
| 168 | us | c | - | d |
| 169 | them | d | d | - |

Final position (top row first, X = F, O = S):

```
..........X....X......
......O..OO....X......
......O..OO....X......
....X.X..XX.O..O......
....O.O..OO.O..X......
....O.O..OX.X..X......
....X.X..XOXO..O......
....O.O.XXXXX..X......
....X.X.OOXOOX.X......
....O.O.OXXOXX.X......
....O.X.OOOXOO.O......
....X.O.XOXXXX.X......
....X.X.OXOXOX.O......
....O.OXXOXXXO.X......
....O.OOOXOOOXOO......
0123456789abcdefghijkl
```

### 15x22 ours 2.1 (F, Savi7), 341 plies, blast - game 0eff6434-8158-419f-800e-3cf29fa0a10a

| Ply | Mover | Move | W (mover wins at) | T (opponent wins at) |
|---|---|---|---|---|
| 328 | us | 6 | - | - |
| 329 | them | h | - | - |
| 330 | us | h | - | - |
| 331 | them | i | - | - |
| 332 | us | 3* | - | - |
| 333 | them | 3 | - | 3 |
| 334 | us | 4 | - | - |
| 335 | them | h | - | - |
| 336 | us | 3 | - | - |
| 337 | them | i | - | - |
| 338 | us | h | - | - |
| 339 | them | i | - | - |
| 340 | us | i | - | i |
| 341 | them | d* | - | i |

Final position (top row first, X = F, O = S):

```
.....XX.XXOO...XX..OX.
.....OO.XXXO...OX..XX.
....XXO.OOOO...XX..OX.
....OOX.OXOXX.XXX..XX.
....OOO.OOXOO.XOO..XO.
....XXX.OXOXX.OOO..OX.
....XOO.XOXOO.XXX..XO.
....XXO.OOXXX.OOX..OO.
...XXXX.XOXOX.OOX..XX.
...OOOO.XOOOO.OXOX.OO.
...XXXO.OOXXX.OOXO.OO.
...XOOX.XXOOO.XOOXXXO.
...XXOO.XOXXX.OOOOOOXO
...XOOOOOXOOX.XXOXOOXX
..OXXXOXXOXXO.OXXOOXXX
0123456789abcdefghijkl
```

### 15x11 ours 2.1 (F, Savi7b), 142 plies, six - game c4ca211c-b9ad-4a2a-a284-348f6e03955a

| Ply | Mover | Move | W (mover wins at) | T (opponent wins at) |
|---|---|---|---|---|
| 129 | us | 8* | - | - |
| 130 | them | 3 | - | - |
| 131 | us | a | - | - |
| 132 | them | a | - | - |
| 133 | us | 9 | - | - |
| 134 | them | 3 | - | - |
| 135 | us | 3 | - | 3 |
| 136 | them | 3 | - | - |
| 137 | us | 9 | - | - |
| 138 | them | 3 | - | - |
| 139 | us | 3 | - | 3 |
| 140 | them | 3 | - | - |
| 141 | us | 3 | - | 3 |
| 142 | them | 3 | 3 | - |

Final position (top row first, X = F, O = S):

```
..X.OXO....
.OX.OOO....
.XO.XXX....
.OO.OXO....
.XX.XXXX...
.XOOOOOO...
.OOXOOOX...
.OXOXXXO...
.XOXOOOO...
.OXOXXOX...
.XOOOOXO..O
.OOXXXOX..X
.XXOOOXO..X
.OXOXOXO.XX
.XOXXXOX.XX
0123456789a
```

### 15x11 ours 2.1 (S, Savi7b), 153 plies, six - game 96321eb5-5c07-404f-9292-55820aaa981d

| Ply | Mover | Move | W (mover wins at) | T (opponent wins at) |
|---|---|---|---|---|
| 140 | us | a | - | - |
| 141 | them | 7* | - | - |
| 142 | us | a | - | - |
| 143 | them | a | - | - |
| 144 | us | 6 | - | - |
| 145 | them | 7 | - | - |
| 146 | us | 6 | - | - |
| 147 | them | 7 | - | - |
| 148 | us | 9 | - | - |
| 149 | them | 9 | - | 9 |
| 150 | us | 9 | - | - |
| 151 | them | 7 | - | - |
| 152 | us | 7 | - | 7 |
| 153 | them | 7 | 7 | - |

Final position (top row first, X = F, O = S):

```
....OO.....
....OOO....
....XXO....
....OOX....
..X.OXX....
.XOXXOO..O.
.OOOXOX..X.
.OOOOXO..O.
.OXXXXX..O.
.XXOOOXX.O.
.OXXXXXOXO.
.XXOOOOXXOX
.XXXXXOXXXO
.XOOOOXXOOO
XOXOOXOOXOO
0123456789a
```

### 15x11 ours 2.1 (S, Savi7b), 182 plies, blast - game f9b0b6f4-cb65-4b50-b042-4f4e69ad64ae

| Ply | Mover | Move | W (mover wins at) | T (opponent wins at) |
|---|---|---|---|---|
| 169 | us | 8 | - | - |
| 170 | them | 9 | - | - |
| 171 | us | 9 | - | - |
| 172 | them | a | - | - |
| 173 | us | a | - | - |
| 174 | them | 8 | - | - |
| 175 | us | 7 | - | - |
| 176 | them | 7 | - | - |
| 177 | us | 8 | - | - |
| 178 | them | 8 | - | - |
| 179 | us | 6 | - | - |
| 180 | them | 3 | - | - |
| 181 | us | 3 | - | 3 |
| 182 | them | 3* | - | - |

Final position (top row first, X = F, O = S):

```
.O...X....O
.O...O....X
.O...OO...X
.O..XOX..OX
.O..OOX..XO
.X..XOO..OX
.O..OXX..XO
.O..XXO..XO
.XO.XXXX.OO
XOX.OXXO.XO
XOO.XXXXXOO
OXX.OOOXOXX
XOO.XXOXXOO
XXXXOOXOOXO
OXOOOXOXOXO
0123456789a
```

### 15x11 ours 2.1 (S, Savi7b), 210 plies, blast - game 9dd6922e-e63a-4363-8bd7-259cd5bc3c0e

| Ply | Mover | Move | W (mover wins at) | T (opponent wins at) |
|---|---|---|---|---|
| 197 | us | 2 | - | - |
| 198 | them | 2 | - | - |
| 199 | us | 2 | - | - |
| 200 | them | 2 | - | - |
| 201 | us | 2 | - | - |
| 202 | them | 2 | - | - |
| 203 | us | 2 | - | - |
| 204 | them | 1 | - | - |
| 205 | us | 9 | - | - |
| 206 | them | 1 | - | - |
| 207 | us | a | - | - |
| 208 | them | 1 | - | - |
| 209 | us | 1 | - | 1 |
| 210 | them | 1* | - | - |

Final position (top row first, X = F, O = S):

```
...OXXX.X..
...XOOO.O..
...OOXX.O..
...XOOX.X..
..OOXXX.X..
O.XXOXX.O.O
O.OXOXO.X.O
O.XOXXX.O.X
X.OXOOX.O.O
O.XOXXO.X.O
OXXOOOX.OOX
XXOXXXXOXXO
XXXXXOXXOXX
OXXXOXXOOOO
XOXOXXXOXOX
0123456789a
```

### 15x11 ours 2.1 (F, Savi7b), 242 plies, six - game d921b131-24ab-4cf7-9ed3-e9d22a7e6d32

| Ply | Mover | Move | W (mover wins at) | T (opponent wins at) |
|---|---|---|---|---|
| 229 | us | a | - | - |
| 230 | them | 0 | - | - |
| 231 | us | 0 | - | 0 |
| 232 | them | 0 | - | - |
| 233 | us | 8* | - | - |
| 234 | them | 8 | - | - |
| 235 | us | 7 | - | - |
| 236 | them | 9 | - | - |
| 237 | us | 7 | - | - |
| 238 | them | 7 | - | - |
| 239 | us | 9 | - | - |
| 240 | them | 9 | - | - |
| 241 | us | 8 | - | - |
| 242 | them | 8 | 8 | - |

Final position (top row first, X = F, O = S):

```
OXXXOXXO.OX
XOXOXXOX.XO
OOOXXOXX.OX
OXOXOOXX.XO
XOOOXOOO.XX
OXXXOXXX.OO
XOOXXOOOOXO
OOOOXXXOXOO
XXXXXOOXOOO
OXOOOOXOOXX
XOOXOXXOXOX
OOXXOOOXOOO
XXOOOXXXXOO
XOXXXOXXXOX
OOXOXXOXOOX
0123456789a
```

### 15x11 ours 2.2 (F, Savi8), 112 plies, six - game 86d1ed47-ba83-4847-b666-acea6c07318f

| Ply | Mover | Move | W (mover wins at) | T (opponent wins at) |
|---|---|---|---|---|
| 99 | us | 8 | - | - |
| 100 | them | 6 | - | - |
| 101 | us | 6 | - | - |
| 102 | them | 3 | - | - |
| 103 | us | 8 | - | - |
| 104 | them | 8 | - | - |
| 105 | us | 8 | - | - |
| 106 | them | 8 | - | - |
| 107 | us | 3 | - | - |
| 108 | them | 7* | - | - |
| 109 | us | 5 | - | - |
| 110 | them | 7 | - | - |
| 111 | us | 7 | - | 7 |
| 112 | them | 7 | 7 | - |

Final position (top row first, X = F, O = S):

```
....O......
....XX.....
....XO.....
...XOX.....
...OOX.....
...XOO.....
...OXOX....
...XXXOO...
...XOOOX...
..OXOOOOO..
..XXOXXOX..
..OOXOOXX..
..OOOXXXO..
..XXXOXXX..
O.OXXXOXO..
0123456789a
```

### 15x11 ours 2.2 (S, Savi8), 128 plies, blast - game 7e073388-a1c2-45a6-a273-f519fb97c225

| Ply | Mover | Move | W (mover wins at) | T (opponent wins at) |
|---|---|---|---|---|
| 115 | us | 6 | - | - |
| 116 | them | 6 | - | - |
| 117 | us | 3 | - | - |
| 118 | them | 3 | - | - |
| 119 | us | 3 | - | - |
| 120 | them | 3 | - | - |
| 121 | us | 2 | - | - |
| 122 | them | 2 | - | - |
| 123 | us | 7 | - | - |
| 124 | them | 7 | - | 7 |
| 125 | us | 7 | - | 7 |
| 126 | them | 7 | - | - |
| 127 | us | 7 | - | - |
| 128 | them | a* | - | - |

Final position (top row first, X = F, O = S):

```
....OXX....
...XXXO....
...OXXX....
...XOOO....
...OOXO....
...XOXXOX..
...XXOXXO..
...XXXXOXO.
...OOOOXOO.
...OXXXOOX.
...XOOOOXX.
..XOXXXXXX.
..OXOOXOOO.
..OOXXOOOO.
..OXOXOOOX.
0123456789a
```

### 15x11 ours 2.2 (F, Savi8), 144 plies, six - game 8a6838ab-177d-4c31-8efa-7cb6397e11c1

| Ply | Mover | Move | W (mover wins at) | T (opponent wins at) |
|---|---|---|---|---|
| 131 | us | 9 | - | - |
| 132 | them | 4 | - | - |
| 133 | us | 4 | - | 4 |
| 134 | them | 9 | - | - |
| 135 | us | 4 | - | - |
| 136 | them | 0 | - | - |
| 137 | us | 0 | - | - |
| 138 | them | 9 | - | - |
| 139 | us | 9* | - | - |
| 140 | them | 3 | - | - |
| 141 | us | 3 | - | 3 |
| 142 | them | 3* | - | - |
| 143 | us | 4 | - | 3,4 |
| 144 | them | 3 | 3 | - |

Final position (top row first, X = F, O = S):

```
.....OXO...
.....XXO...
.....OOX...
.....OXO...
.....OXO...
.....XXXO..
.....OOXX..
...OXOOOX..
...OOOXXO..
...XOOOXOOO
...OXXOOXXX
.X.XXXXOOOO
.O.OXOOXOOX
XOXXOXXOOXX
OOXXOOOXXXX
0123456789a
```

### 15x11 ours 2.2 (F, Savi8), 157 plies, six - game b1cb6881-ef2d-423e-aed5-e088224fcb36

| Ply | Mover | Move | W (mover wins at) | T (opponent wins at) |
|---|---|---|---|---|
| 144 | us | 8 | - | - |
| 145 | them | a | - | - |
| 146 | us | 1 | - | - |
| 147 | them | 1 | - | 1 |
| 148 | us | 1 | - | - |
| 149 | them | a | - | - |
| 150 | us | 0 | - | - |
| 151 | them | a | - | - |
| 152 | us | 1 | - | - |
| 153 | them | 8 | - | - |
| 154 | us | 8 | - | 8 |
| 155 | them | 8* | - | - |
| 156 | us | 9 | - | 8 |
| 157 | them | 8 | 8 | - |

Final position (top row first, X = F, O = S):

```
...XXXX....
...XXOO....
...OOXO....
...XXOX....
...OXXO....
...XOOX....
...OOXO....
.X.OOOX..X.
.X.XOXOO.O.
.O.XXOOO.OO
.XOOOXOXOXO
.XOXXOXXOOO
.XOXOXXOXOX
.XOOXOXXOXO
XXXOXXOXXOX
0123456789a
```

### 15x11 ours 2.2 (S, Savi8), 201 plies, six - game 384b1906-8088-4dd2-a25e-34d1f43c73fc

| Ply | Mover | Move | W (mover wins at) | T (opponent wins at) |
|---|---|---|---|---|
| 188 | us | 6 | - | - |
| 189 | them | 3 | - | - |
| 190 | us | 6 | - | - |
| 191 | them | 3 | - | - |
| 192 | us | 7 | - | - |
| 193 | them | 7 | - | - |
| 194 | us | 8 | - | - |
| 195 | them | 8 | - | - |
| 196 | us | 8 | - | - |
| 197 | them | 2 | - | - |
| 198 | us | 2 | - | 2 |
| 199 | them | 2* | - | 2 |
| 200 | us | 3 | - | 2,3 |
| 201 | them | 2 | 2 | - |

Final position (top row first, X = F, O = S):

```
X...OOOX..X
O...OXOOO.O
X..OXXXXXOX
OX.XOOXOOOO
XXXXXXOXXXX
OXXXOOXOOXX
XOOOXXOXXOX
OXOXOOXXOXO
XOXOOXOOOXO
OXOXXOXXXOX
XOOOOXOOXXO
XXXXXOXOOXO
OOOOOXOXXOX
OOXXXOXOXXO
XXOXXXOXOOO
0123456789a
```

### 15x11 ours 2.2 (F, Savi8), 203 plies, six - game 791a6fc2-d142-47ec-9bdf-7d25b35d238b

| Ply | Mover | Move | W (mover wins at) | T (opponent wins at) |
|---|---|---|---|---|
| 190 | us | 1 | - | - |
| 191 | them | 1 | - | - |
| 192 | us | 0 | - | - |
| 193 | them | 1 | - | - |
| 194 | us | 0 | - | - |
| 195 | them | 0 | - | - |
| 196 | us | 9 | - | - |
| 197 | them | 9 | - | - |
| 198 | us | 9 | - | - |
| 199 | them | 7 | - | - |
| 200 | us | 7 | - | 7 |
| 201 | them | 7 | - | - |
| 202 | us | 7 | - | - |
| 203 | them | 7 | 7 | - |

Final position (top row first, X = F, O = S):

```
OO.OXOX.OXO
XO.XOXO.XOX
XX.OOXO.XXO
XX.OXOOOOOO
OX.XOXXXOXX
XO.XXXOOOOX
XX.OOOOXOOX
XO.OXXOOXXO
OO.XXOOOXOO
OO.OOOXXOOO
OX.XXOOXXOX
OOOXOXXOOXX
XXXXOXOOOXX
XXOOXOXOXXO
XXOXXXOOXOX
0123456789a
```

### 15x11 ours 2.2 (S, Savi8), 208 plies, six - game 7b9d3eaf-bd3b-4f14-bc61-8f7841db25ba

| Ply | Mover | Move | W (mover wins at) | T (opponent wins at) |
|---|---|---|---|---|
| 195 | us | 9 | - | - |
| 196 | them | 9 | - | - |
| 197 | us | a* | - | - |
| 198 | them | a | - | - |
| 199 | us | 9 | - | - |
| 200 | them | 9 | - | - |
| 201 | us | 9 | - | - |
| 202 | them | a* | - | - |
| 203 | us | a | - | - |
| 204 | them | 9 | - | - |
| 205 | us | 9 | - | - |
| 206 | them | 9 | - | - |
| 207 | us | 3 | - | - |
| 208 | them | 3 | 3 | - |

Final position (top row first, X = F, O = S):

```
XXO.OXXOXX.
OXO.XOOXOO.
OOO.OXOOXX.
XOX.XOXXXO.
OXO.OXXOOX.
XOX.XOOXOO.
XXO.OXOXOX.
XOO.XOXOXOO
OOX.OXOXOOX
XXO.XOXXOXO
XXX.XXOOOOX
OOO.OOXXXOO
XXXXXXOOOXX
OXXOOOXXXXO
OOXOOXXXOOX
0123456789a
```
