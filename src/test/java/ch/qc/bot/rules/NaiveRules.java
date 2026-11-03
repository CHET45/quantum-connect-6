package ch.qc.bot.rules;

/**
 * Literal re-implementation of the engine's QuantumField rules in engine orientation (row 0 on top), used as
 * the oracle of the differential test. Mirrors produceCollision, produceSpecialCollision, destroySurroundingDiscs,
 * applyGravity and hasWinner as read from the bytecode.
 */
final class NaiveRules {
  final int rows;
  final int cols;
  final int k;
  final int[][] f;

  NaiveRules(int rows, int cols, int k) {
    this.rows = rows;
    this.cols = cols;
    this.k = k;
    f = new int[rows][cols];
  }

  NaiveRules copy() {
    NaiveRules n = new NaiveRules(rows, cols, k);
    for (int r = 0; r < rows; r++) {
      n.f[r] = f[r].clone();
    }
    return n;
  }

  boolean drop(int col, int colour) {
    for (int r = rows - 1; r >= 0; r--) {
      if (f[r][col] == 0) {
        f[r][col] = colour;
        return true;
      }
    }
    return false;
  }

  boolean special(int col, int colour) {
    for (int r = rows - 1; r >= 0; r--) {
      if (f[r][col] == 0) {
        f[r][col] = colour;
        for (int dr = -1; dr <= 1; dr++) {
          for (int dc = -1; dc <= 1; dc++) {
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

  boolean hasWinner() {
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

  boolean hasLine(int colour) {
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
