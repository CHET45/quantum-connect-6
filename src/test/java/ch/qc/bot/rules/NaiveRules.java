package ch.qc.bot.rules;

/**
 * Literal re-implementation of the engine's QuantumField rules in engine orientation (row 0 on top), used as
 * the oracle of the differential test. Mirrors produceCollision, produceSpecialCollision, destroySurroundingDiscs,
 * applyGravity and hasWinner as read from the bytecode.
 */
public final class NaiveRules {
  public final int rows;
  public final int cols;
  final int k;
  /** Blast half width and half height: the engine clears rows r-1..r+1 and columns c-1..c+1, i.e. 1 and 1. */
  final int blastW;
  final int blastH;
  final int[][] f;

  public NaiveRules(int rows, int cols, int k) {
    this(rows, cols, k, 1, 1);
  }

  /** The engine generalised to a blast of (2 blastW + 1) columns by (2 blastH + 1) rows. */
  public NaiveRules(int rows, int cols, int k, int blastW, int blastH) {
    this.rows = rows;
    this.cols = cols;
    this.k = k;
    this.blastW = blastW;
    this.blastH = blastH;
    f = new int[rows][cols];
  }

  public NaiveRules copy() {
    NaiveRules n = new NaiveRules(rows, cols, k, blastW, blastH);
    for (int r = 0; r < rows; r++) {
      n.f[r] = f[r].clone();
    }
    return n;
  }

  public boolean drop(int col, int colour) {
    for (int r = rows - 1; r >= 0; r--) {
      if (f[r][col] == 0) {
        f[r][col] = colour;
        return true;
      }
    }
    return false;
  }

  public boolean special(int col, int colour) {
    for (int r = rows - 1; r >= 0; r--) {
      if (f[r][col] == 0) {
        f[r][col] = colour;
        for (int dr = -blastH; dr <= blastH; dr++) {
          for (int dc = -blastW; dc <= blastW; dc++) {
            int rr = r + dr;
            int cc = col + dc;
            if (rr >= 0 && rr < rows && cc >= 0 && cc < cols) {
              f[rr][cc] = 0;
            }
          }
        }
        gravity();
        return true;
      }
    }
    return false;
  }

  private void gravity() {
    for (int c = 0; c < cols; c++) {
      for (int r = rows - 1; r >= 0; r--) {
        if (f[r][c] != 0) {
          continue;
        }
        for (int a = r - 1; a >= 0; a--) {
          if (f[a][c] != 0) {
            f[r][c] = f[a][c];
            f[a][c] = 0;
            break;
          }
        }
      }
    }
  }

  public boolean hasWinner() {
    int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        if (f[r][c] == 0) {
          continue;
        }
        for (int[] d : dirs) {
          int n = 0;
          for (int i = 0; i < k; i++) {
            int rr = r + i * d[0];
            int cc = c + i * d[1];
            if (rr < 0 || rr >= rows || cc < 0 || cc >= cols || f[rr][cc] != f[r][c]) {
              break;
            }
            n++;
          }
          if (n >= k) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public boolean hasLine(int colour) {
    NaiveRules only = copy();
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        if (only.f[r][c] != colour) {
          only.f[r][c] = 0;
        }
      }
    }
    return only.hasWinner();
  }

  /** Builds the engine-oriented grid from cells in Geometry index order. */
  public static NaiveRules fromCells(int rows, int cols, int k, byte[] cells) {
    NaiveRules n = new NaiveRules(rows, cols, k);
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        n.f[r][c] = cells[c * rows + (rows - 1 - r)];
      }
    }
    return n;
  }

  public boolean isFull(int col) {
    return f[0][col] != 0;
  }

  /** Cells in Geometry index order. */
  byte[] cells() {
    byte[] out = new byte[rows * cols];
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        out[c * rows + (rows - 1 - r)] = (byte) f[r][c];
      }
    }
    return out;
  }
}
