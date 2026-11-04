package ch.qc.bot;

/**
 * The part of the board the search works on. A game stays local: on a very large board nearly every column is empty and
 * out of reach of any line for many plies, and a search over all of them spends its time there. So a dimension beyond
 * {@link Timing#cropAbove} is modelled only around the discs, as if it were a smaller board. Up to that size the crop is
 * the whole board and nothing changes. The model always starts at the real floor; only its columns are offset.
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

  final int realRows;
  final int realCols;
  /** Real column of model column 0. */
  final int col0;
  final int cols;
  final int rows;

  private Crop(int realRows, int realCols, int col0, int cols, int rows) {
    this.realRows = realRows;
    this.realCols = realCols;
    this.col0 = col0;
    this.cols = cols;
    this.rows = rows;
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
      int from = (col0 + m) * realRows;
      int to = m * rows;
      for (int y = 0; y < rows; y++) {
        out[to + y] = real[from + y];
      }
    }
    return out;
  }
}
