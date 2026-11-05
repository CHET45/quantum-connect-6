package ch.qc.bot.rules;

import java.util.SplittableRandom;

/**
 * Immutable board geometry for one (rows, cols, K): the list of K-cell windows, which windows pass through
 * each cell and touch each column, and the Zobrist keys.
 *
 * <p>Cell index {@code i = col * rows + y} with {@code y = 0} the bottom row; engine row is {@code rows - 1 - y}.
 */
public final class Geometry {
  /** The line length of the core-2.0 engine; a match may use another ({@link Rules}). */
  public static final int K_ENGINE = Rules.K_DEFAULT;
  public static final int DIR_H = 0;
  public static final int DIR_V = 1;
  public static final int DIR_D1 = 2;
  public static final int DIR_D2 = 3;

  private static final long ZOBRIST_SEED = 0x51C0_7A6E_D00D_2026L;

  public final int rows;
  public final int cols;
  public final int k;
  /** The rules of the match this geometry serves; {@link #k} equals rules.k. */
  public final Rules rules;
  public final int cells;
  public final Tables tables;

  public final int windows;
  /** winCells[w * k + i]: cell of digit i of window w. */
  public final int[] winCells;
  public final byte[] winDir;

  /** Windows through cell i: entries cellStart[i] .. cellStart[i + 1] - 1 of cellWin / cellPos. */
  public final int[] cellStart;
  public final int[] cellWin;
  /** Digit position of the cell inside the window. */
  public final int[] cellPos;
  /** Value of that digit position: 3^cellPos[j]. */
  public final int[] cellPow;

  /** Windows touching column c: entries colStart[c] .. colStart[c + 1] - 1 of colWin. */
  public final int[] colStart;
  public final int[] colWin;

  /**
   * The threat search reads the clock when (nodes & clockMask) == 0. A reading allocates, so not more often than needed;
   * a node costs more with more windows (copies and blasts in a tall, wide board), so the gap must not grow with the board.
   */
  public final int clockMask;

  /** zobrist[colour][cell], colour 1 or 2. */
  public final long[][] zobrist;

  public Geometry(int rows, int cols) {
    this(rows, cols, Rules.DEFAULT);
  }

  /** The default rules with another line length (evaluation tables, tests). */
  public Geometry(int rows, int cols, int k) {
    this(rows, cols, Rules.DEFAULT.withK(k));
  }

  public Geometry(int rows, int cols, Rules rules) {
    this.rows = rows;
    this.cols = cols;
    this.rules = rules;
    this.k = rules.k;
    this.cells = rows * cols;
    this.tables = new Tables(k);

    int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}}; // (dx, dy) for H, V, D1, D2
    int n = 0;
    for (int[] d : dirs) {
      n += count(cols, d[0]) * count(rows, Math.abs(d[1]));
    }
    windows = n;
    clockMask = n <= 1000 ? 63 : 15;
    winCells = new int[n * k];
    winDir = new byte[n];
    int w = 0;
    for (int dir = 0; dir < 4; dir++) {
      int dx = dirs[dir][0];
      int dy = dirs[dir][1];
      for (int x0 = 0; x0 < cols; x0++) {
        for (int y0 = 0; y0 < rows; y0++) {
          int x1 = x0 + dx * (k - 1);
          int y1 = y0 + dy * (k - 1);
          if (x1 < 0 || x1 >= cols || y1 < 0 || y1 >= rows) {
            continue;
          }
          for (int i = 0; i < k; i++) {
            winCells[w * k + i] = (x0 + dx * i) * rows + (y0 + dy * i);
          }
          winDir[w] = (byte) dir;
          w++;
        }
      }
    }

    cellStart = new int[cells + 1];
    for (int j = 0; j < n * k; j++) {
      cellStart[winCells[j] + 1]++;
    }
    for (int i = 0; i < cells; i++) {
      cellStart[i + 1] += cellStart[i];
    }
    cellWin = new int[n * k];
    cellPos = new int[n * k];
    cellPow = new int[n * k];
    int[] fill = cellStart.clone();
    for (int j = 0; j < n * k; j++) {
      int c = winCells[j];
      cellWin[fill[c]] = j / k;
      cellPos[fill[c]] = j % k;
      cellPow[fill[c]] = tables.pow3[j % k];
      fill[c]++;
    }

    colStart = new int[cols + 1];
    int[] lastSeen = new int[n];
    java.util.Arrays.fill(lastSeen, -1);
    int[] tmp = new int[n * k];
    int t = 0;
    for (int c = 0; c < cols; c++) {
      colStart[c] = t;
      for (int i = c * rows; i < (c + 1) * rows; i++) {
        for (int j = cellStart[i]; j < cellStart[i + 1]; j++) {
          int win = cellWin[j];
          if (lastSeen[win] != c) {
            lastSeen[win] = c;
            tmp[t++] = win;
          }
        }
      }
    }
    colStart[cols] = t;
    colWin = java.util.Arrays.copyOf(tmp, t);

    zobrist = new long[3][cells];
    SplittableRandom rnd = new SplittableRandom(ZOBRIST_SEED ^ rules.hashSalt());
    for (int colour = 1; colour <= 2; colour++) {
      for (int i = 0; i < cells; i++) {
        zobrist[colour][i] = rnd.nextLong();
      }
    }
  }

  private int count(int extent, int step) {
    return Math.max(0, extent - step * (k - 1));
  }

  public int index(int col, int y) {
    return col * rows + y;
  }

  public boolean sameSize(int rows, int cols) {
    return this.rows == rows && this.cols == cols;
  }
}
