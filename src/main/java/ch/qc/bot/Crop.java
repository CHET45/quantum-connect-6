package ch.qc.bot;

/**
 * The part of the board the search works on. A game stays local: on a very large board nearly every column is empty and
 * out of reach of any line for many plies, and a search over all of them spends its time there. So a dimension beyond
 * {@link Timing#cropAbove} is modelled only around the discs, as if it were a smaller board. Up to that size the crop is
 * the whole board and nothing changes. The model always starts at the real floor; only its columns are offset.
 *
 * <p>With gaps ({@link Timing#cropGaps}) the model holds only the columns within {@link #GAP_REACH} of some disc: an empty
 * stretch wider than {@link #GAP_FILL} between two groups of discs is left out, so discs spread over the whole width (an
 * opponent dropping far away) no longer widen the model by the empty columns between them. No window of the winning
 * length holds discs on both sides of such a stretch, so the lines through discs are exact; only lines built entirely
 * from new discs across the cut differ from the real board.
 */
final class Crop {
  /** Free columns (and rows above the highest disc) that must remain: every window through a disc then lies inside. */
  static final int KEEP = 6;
  /** Free columns and rows a new crop leaves, so it need not be rebuilt on the next turn. */
  static final int GROW = 12;
  /** Discs spread wider than this leave some out: the crop then covers the densest part. */
  static final int MAX_COLS = 128;
  /** A densest part moves only for this many more discs, so a spread-out game does not rebuild every turn. */
  private static final int MOVE_FOR_DISCS = 4;
  /** Above this many cells or {@link QcBot#MAX_SEARCH_COLS} columns there is no model at all. */
  static final int MAX_CELLS = 65536;
  /** With gaps: columns kept on each side of a disc when the model is rebuilt (KEEP plus room to grow). */
  static final int GAP_REACH = KEEP + 2;
  /** With gaps: empty stretches up to this wide between kept columns are kept too, so a cut always hides more. */
  static final int GAP_FILL = 4;
  /**
   * With gaps level 2: a group of discs (columns at most WIN-1 apart) where neither side has this many discs is left out
   * while a larger group exists. From this many discs a line of six still needs three more of that side's drops.
   */
  static final int GROUP_MIN = 3;
  private static final int GROUP_SPAN = 5;

  final int realRows;
  final int realCols;
  /** Real column of model column 0. */
  final int col0;
  final int cols;
  final int rows;
  /** Real column of each model column, ascending; null when the model columns are contiguous from col0. */
  private final int[] map;

  private Crop(int realRows, int realCols, int col0, int cols, int rows) {
    this.realRows = realRows;
    this.realCols = realCols;
    this.col0 = col0;
    this.cols = cols;
    this.rows = rows;
    this.map = null;
  }

  private Crop(int realRows, int realCols, int[] map, int rows) {
    this.realRows = realRows;
    this.realCols = realCols;
    this.col0 = map[0];
    this.cols = map.length;
    this.rows = rows;
    this.map = map;
  }

  /** Real column of model column m. */
  int real(int m) {
    return map == null ? col0 + m : map[m];
  }

  /** Model column of a real column, -1 if the model leaves it out. */
  int model(int realCol) {
    if (map == null) {
      int m = realCol - col0;
      return m >= 0 && m < cols ? m : -1;
    }
    int m = java.util.Arrays.binarySearch(map, realCol);
    return m >= 0 ? m : -1;
  }

  /** True if some empty stretch of real columns is left out between model columns. */
  boolean hasGaps() {
    return map != null && map[map.length - 1] - map[0] + 1 != map.length;
  }

  /** The crop before any disc is known, around the centre column the bot opens in; null if it cannot be modelled. */
  static Crop start(int realRows, int realCols, int above) {
    int cols = realCols <= above ? realCols : Math.min(realCols, 2 * GROW + 1);
    int rows = realRows <= above ? realRows : Math.min(realRows, GROW);
    int col0 = realCols <= above ? 0 : Math.max(0, (realCols - 1) / 2 - GROW);
    return checked(realRows, realCols, Math.min(col0, realCols - cols), cols, rows);
  }

  private static Crop checked(int realRows, int realCols, int col0, int cols, int rows) {
    boolean fits = cols <= QcBot.MAX_SEARCH_COLS && (long) rows * cols <= MAX_CELLS;
    return fits ? new Crop(realRows, realCols, col0, cols, rows) : null;
  }

  boolean isFor(int rows, int cols) {
    return realRows == rows && realCols == cols;
  }

  boolean whole() {
    return cols == realCols && rows == realRows;
  }

  /**
   * This crop if it still suits the position in {@code cells} (real board, {@link Snapshot#cells} order), else the one
   * to use now; null if the position cannot be modelled.
   */
  Crop refit(byte[] cells, int above) {
    return refit(cells, above, 0);
  }

  /** As {@link #refit(byte[], int)}; gaps 1 or 2 ({@link Timing#cropGaps}) leave out columns of a cropped dimension. */
  Crop refit(byte[] cells, int above, boolean gaps) {
    return refit(cells, above, gaps ? 1 : 0);
  }

  Crop refit(byte[] cells, int above, int gaps) {
    if (gaps > 0 && realCols > above) {
      return refitGaps(cells, above, gaps >= 2);
    }
    boolean cropCols = realCols > above;
    boolean cropRows = realRows > above;
    if (!cropCols && !cropRows) {
      return this;
    }
    int lo = -1;
    int hi = -1;
    int top = 0;
    for (int c = 0; c < realCols; c++) {
      int at = c * realRows;
      if (cells[at] != 0) {
        lo = lo < 0 ? c : lo;
        hi = c;
        int h = 1;
        while (h < realRows && cells[at + h] != 0) {
          h++;
        }
        top = Math.max(top, h);
      }
    }
    if (lo < 0) {
      return this;
    }
    int newRows = cropRows ? fitRows(top) : realRows;
    int newCol0 = col0;
    int newCols = cols;
    if (cropCols) {
      int wantLo = Math.max(0, lo - GROW);
      int wantHi = Math.min(realCols - 1, hi + GROW);
      int want = wantHi - wantLo + 1;
      if (want > MAX_COLS) {
        newCols = MAX_COLS;
        newCol0 = densest(cells);
      } else {
        boolean loOk = col0 == 0 || lo - KEEP >= col0;
        boolean hiOk = col0 + cols == realCols || hi + KEEP < col0 + cols;
        // also when the discs are gone from most of the crop, as after a new game: a smaller one searches better
        if (!loOk || !hiOk || cols > MAX_COLS || 2 * want <= cols) {
          newCol0 = wantLo;
          newCols = want;
        }
      }
    }
    if (newRows == rows && newCol0 == col0 && newCols == cols) {
      return this;
    }
    return checked(realRows, realCols, newCol0, newCols, newRows);
  }

  private Crop refitGaps(byte[] cells, int above, boolean dropSmall) {
    boolean cropRows = realRows > above;
    boolean[] occupied = new boolean[realCols];
    int top = 0;
    int discs = 0;
    for (int c = 0; c < realCols; c++) {
      int at = c * realRows;
      int h = 0;
      while (h < realRows && cells[at + h] != 0) {
        h++;
      }
      occupied[c] = h > 0;
      discs += h > 0 ? 1 : 0;
      top = Math.max(top, h);
    }
    if (discs == 0) {
      return this;
    }
    if (dropSmall) {
      dropSmallGroups(cells, occupied);
    }
    int newRows = cropRows ? fitRows(top) : realRows;
    int[] want = gapMap(occupied, realCols);
    if (want.length > MAX_COLS) {
      // too spread even with gaps: the densest stretch, as without them
      return refit(cells, above, 0);
    }
    boolean keep = covers(occupied) && 2 * want.length > cols;
    if (keep && newRows == rows) {
      return this;
    }
    if (keep) {
      return map == null ? checked(realRows, realCols, col0, cols, newRows) : checkedMap(map, newRows);
    }
    if (isContiguous(want)) {
      return checked(realRows, realCols, want[0], want.length, newRows);
    }
    return checkedMap(want, newRows);
  }

  /** Clears the columns of groups where neither side has GROUP_MIN discs, if some group has; tops stay as they are. */
  private void dropSmallGroups(byte[] cells, boolean[] occupied) {
    int n = occupied.length;
    int[] start = new int[n];
    int[] end = new int[n];
    boolean[] big = new boolean[n];
    int groups = 0;
    boolean anyBig = false;
    int c = 0;
    while (c < n) {
      if (!occupied[c]) {
        c++;
        continue;
      }
      int lo = c;
      int hi = c;
      int last = c;
      int first = 0;
      int second = 0;
      for (int x = c; x < n && x - last <= GROUP_SPAN; x++) {
        if (occupied[x]) {
          last = x;
          hi = x;
          for (int y = 0; y < realRows; y++) {
            byte v = cells[x * realRows + y];
            if (v == 0) {
              break;
            }
            if (v == 1) {
              first++;
            } else {
              second++;
            }
          }
        }
      }
      start[groups] = lo;
      end[groups] = hi;
      big[groups] = Math.max(first, second) >= GROUP_MIN;
      anyBig |= big[groups];
      groups++;
      c = hi + 1;
    }
    if (!anyBig) {
      return;
    }
    for (int g = 0; g < groups; g++) {
      if (!big[g]) {
        for (int x = start[g]; x <= end[g]; x++) {
          occupied[x] = false;
        }
      }
    }
  }

  private Crop checkedMap(int[] m, int newRows) {
    boolean fits = m.length <= QcBot.MAX_SEARCH_COLS && (long) newRows * m.length <= MAX_CELLS;
    return fits ? new Crop(realRows, realCols, m, newRows) : null;
  }

  private static boolean isContiguous(int[] m) {
    return m[m.length - 1] - m[0] + 1 == m.length;
  }

  /** True if every real column within KEEP of a disc is in this model, next to its real neighbours. */
  private boolean covers(boolean[] occupied) {
    for (int c = 0; c < realCols; c++) {
      if (!occupied[c]) {
        continue;
      }
      int lo = Math.max(0, c - KEEP);
      int hi = Math.min(realCols - 1, c + KEEP);
      int m = model(lo);
      if (m < 0 || m + (hi - lo) >= cols || real(m + (hi - lo)) != hi) {
        return false;
      }
    }
    return true;
  }

  /** The columns within GAP_REACH of a disc, with empty stretches up to GAP_FILL wide between them filled in. */
  static int[] gapMap(boolean[] occupied, int realCols) {
    boolean[] keep = new boolean[realCols];
    for (int c = 0; c < realCols; c++) {
      if (occupied[c]) {
        for (int x = Math.max(0, c - GAP_REACH); x <= Math.min(realCols - 1, c + GAP_REACH); x++) {
          keep[x] = true;
        }
      }
    }
    int last = -1;
    int n = 0;
    for (int c = 0; c < realCols; c++) {
      if (keep[c]) {
        if (last >= 0 && c - last - 1 <= GAP_FILL) {
          for (int x = last + 1; x < c; x++) {
            keep[x] = true;
            n++;
          }
        }
        last = c;
        n++;
      }
    }
    int[] m = new int[n];
    int i = 0;
    for (int c = 0; c < realCols; c++) {
      if (keep[c]) {
        m[i++] = c;
      }
    }
    return m;
  }

  private int fitRows(int top) {
    if (top + KEEP <= rows && 2 * (top + GROW) > rows) {
      return rows;
    }
    if (rows == realRows && top + KEEP >= realRows) {
      return rows;
    }
    return Math.min(realRows, top + GROW);
  }

  /** First column of the MAX_COLS columns holding the most discs; stays where it is unless clearly better. */
  private int densest(byte[] cells) {
    int[] sum = new int[realCols + 1];
    for (int c = 0; c < realCols; c++) {
      int at = c * realRows;
      int h = 0;
      while (h < realRows && cells[at + h] != 0) {
        h++;
      }
      sum[c + 1] = sum[c] + h;
    }
    int best = 0;
    int bestDiscs = -1;
    for (int c = 0; c + MAX_COLS <= realCols; c++) {
      int discs = sum[c + MAX_COLS] - sum[c];
      if (discs > bestDiscs) {
        bestDiscs = discs;
        best = c;
      }
    }
    if (cols == MAX_COLS && sum[col0 + MAX_COLS] - sum[col0] + MOVE_FOR_DISCS > bestDiscs) {
      return col0;
    }
    return best;
  }

  /** The cells of this crop, in the index order of a {@link ch.qc.bot.rules.Geometry} of its size. */
  byte[] cut(byte[] real) {
    if (whole()) {
      return real;
    }
    byte[] out = new byte[rows * cols];
    for (int m = 0; m < cols; m++) {
      int from = real(m) * realRows;
      int to = m * rows;
      for (int y = 0; y < rows; y++) {
        out[to + y] = real[from + y];
      }
    }
    return out;
  }
}
