package ch.qc.bot.rules;

/**
 * Game position with incrementally maintained window codes, threat cells and Zobrist hash of the cells.
 * Rules follow design section 1 (R1-R7): gravity drops, colour-blind six-in-a-row, 3x3 blast plus gravity.
 * Side to move and cooldowns are not part of this class.
 */
public final class Board {
  public static final byte EMPTY = 0;
  public static final byte FIRST = 1;
  public static final byte SECOND = 2;

  public final Geometry g;
  private final Tables t;
  private final int rows;
  private final int k;
  private final short[] info;

  private final byte[] cell;
  private final int[] height;
  private final short[] wc;
  /** thr[colour][cell]: windows with K-1 discs of colour and their single empty cell here. */
  private final byte[][] thr;
  private final int[] live3 = new int[3];
  private long hash;
  private int discs;

  private final int[] stamp;
  private int stampGen;
  /** Windows changed by the blast in progress. */
  private final int[] touched;
  /** fall[c]: discs a blast being tested takes out of column c (from the band up); 0 outside {@link #blastWins}. */
  private final int[] fall;

  public Board(Geometry g) {
    this.g = g;
    this.t = g.tables;
    this.rows = g.rows;
    this.k = g.k;
    this.info = g.tables.info;
    cell = new byte[g.cells];
    height = new int[g.cols];
    wc = new short[g.windows];
    thr = new byte[3][g.cells];
    stamp = new int[g.windows];
    touched = new int[g.windows];
    fall = new int[g.cols];
  }

  public void clear() {
    java.util.Arrays.fill(cell, EMPTY);
    rebuild();
  }

  /** Copies a board of the same geometry. */
  public void copyFrom(Board o) {
    copy(o.cell, cell);
    for (int c = 0; c < height.length; c++) {
      height[c] = o.height[c];
    }
    for (int w = 0; w < wc.length; w++) {
      wc[w] = o.wc[w];
    }
    copy(o.thr[1], thr[1]);
    copy(o.thr[2], thr[2]);
    live3[1] = o.live3[1];
    live3[2] = o.live3[2];
    hash = o.hash;
    discs = o.discs;
  }

  private static void copy(byte[] from, byte[] to) {
    for (int i = 0; i < to.length; i++) {
      to[i] = from[i];
    }
  }

  /**
   * Loads cells in this class's index order. Returns false if a column is not compact (a disc above a hole); the
   * board is then not usable.
   */
  public boolean load(byte[] cells) {
    copy(cells, cell);
    return rebuild();
  }

  /** Recomputes all derived state from the cells; false if a column has a disc above a hole. */
  public boolean rebuild() {
    boolean compact = true;
    hash = 0;
    discs = 0;
    for (int c = 0; c < g.cols; c++) {
      int h = 0;
      for (int y = 0; y < rows; y++) {
        int v = cell[c * rows + y];
        if (v == EMPTY) {
          continue;
        }
        if (y != h) {
          compact = false;
          h = y;
        }
        h++;
        discs++;
        hash ^= g.zobrist[v][c * rows + y];
      }
      height[c] = h;
    }
    java.util.Arrays.fill(thr[1], (byte) 0);
    java.util.Arrays.fill(thr[2], (byte) 0);
    live3[1] = live3[2] = 0;
    for (int w = 0; w < g.windows; w++) {
      int code = codeFromCells(w);
      wc[w] = (short) code;
      account(w, code, 1);
    }
    return compact;
  }

  private int codeFromCells(int w) {
    int code = 0;
    int base = w * k;
    for (int i = 0; i < k; i++) {
      code += cell[g.winCells[base + i]] * t.pow3[i];
    }
    return code;
  }

  private void account(int w, int code, int sign) {
    int f = info[code];
    if (f == 0) {
      return;
    }
    int p = f & 15;
    if (p != 0) {
      thr[1][g.winCells[w * k + p - 1]] += (byte) sign;
    }
    p = (f >> 4) & 15;
    if (p != 0) {
      thr[2][g.winCells[w * k + p - 1]] += (byte) sign;
    }
    if ((f & 256) != 0) {
      live3[1] += sign;
    }
    if ((f & 512) != 0) {
      live3[2] += sign;
    }
  }

  public boolean isFull(int col) {
    return height[col] >= rows;
  }

  /** Drops a disc of colour into a non-full column; returns true if it completes a K-line. */
  public boolean make(int col, int colour) {
    int i = col * rows + height[col];
    cell[i] = (byte) colour;
    height[col]++;
    discs++;
    hash ^= g.zobrist[colour][i];
    boolean win = false;
    int target = t.six[colour];
    int[] wins = g.cellWin;
    int[] pow = g.cellPow;
    int end = g.cellStart[i + 1];
    for (int j = g.cellStart[i]; j < end; j++) {
      int w = wins[j];
      int old = wc[w];
      int nw = old + colour * pow[j];
      wc[w] = (short) nw;
      if ((info[old] | info[nw]) != 0) {
        account(w, old, -1);
        account(w, nw, 1);
      }
      if (nw == target) {
        win = true;
      }
    }
    return win;
  }

  /** Takes back the top disc of a column, the exact inverse of {@link #make}. */
  public void unmake(int col) {
    height[col]--;
    int i = col * rows + height[col];
    int colour = cell[i];
    cell[i] = EMPTY;
    discs--;
    hash ^= g.zobrist[colour][i];
    int[] wins = g.cellWin;
    int[] pow = g.cellPow;
    int end = g.cellStart[i + 1];
    for (int j = g.cellStart[i]; j < end; j++) {
      int w = wins[j];
      int old = wc[w];
      int nw = old - colour * pow[j];
      wc[w] = (short) nw;
      if ((info[old] | info[nw]) != 0) {
        account(w, old, -1);
        account(w, nw, 1);
      }
    }
  }

  /**
   * Special move in place (R5-R7): the disc lands at height h, the 3x3 block around it is cleared and the
   * three columns are compacted. A full column is a no-op. Returns true if any K-line of either colour exists
   * in a window touching the three columns afterwards (the mover wins it, R3-R4).
   */
  public boolean blast(int col) {
    int h = height[col];
    if (h >= rows) {
      return false;
    }
    if (stampGen == Integer.MAX_VALUE) {
      java.util.Arrays.fill(stamp, 0);
      stampGen = 0;
    }
    int gen = ++stampGen;
    int n = 0;
    int lo = Math.max(h - 1, 0);
    for (int x = Math.max(col - 1, 0); x <= Math.min(col + 1, g.cols - 1); x++) {
      // the placed disc vanishes with the band, so column col keeps its own height h as the top
      int top = height[x];
      int hi = Math.min(h + 1, top - 1);
      if (lo > hi) {
        continue;
      }
      int cleared = hi - lo + 1;
      int base = x * rows;
      for (int y = lo; y < top; y++) {
        int src = y + cleared;
        int v = src < top ? cell[base + src] : EMPTY;
        if (cell[base + y] != v) {
          n = setCell(base + y, v, gen, n);
        }
      }
      height[x] = top - cleared;
    }
    boolean win = false;
    int six1 = t.six[1];
    int six2 = t.six[2];
    for (int i = 0; i < n; i++) {
      int w = touched[i];
      int code = wc[w];
      account(w, code, 1);
      if (code == six1 || code == six2) {
        win = true;
      }
    }
    return win;
  }

  /**
   * Changes a cell during a blast: its windows get their new codes; a window leaves the threat and live counts when
   * first touched and returns in {@link #blast} once all its cells are final. Returns the new count of touched windows.
   */
  private int setCell(int i, int v, int gen, int n) {
    int old = cell[i];
    if (old != EMPTY) {
      hash ^= g.zobrist[old][i];
      discs--;
    }
    if (v != EMPTY) {
      hash ^= g.zobrist[v][i];
      discs++;
    }
    cell[i] = (byte) v;
    int diff = v - old;
    for (int j = g.cellStart[i]; j < g.cellStart[i + 1]; j++) {
      int w = g.cellWin[j];
      int code = wc[w];
      if (stamp[w] != gen) {
        stamp[w] = gen;
        touched[n++] = w;
        account(w, code, -1);
      }
      wc[w] = (short) (code + diff * g.cellPow[j]);
    }
    return n;
  }

  /**
   * Sound prefilter: a blast in col can only create a line if some disc falls, which needs a neighbour at
   * least three discs taller than col (design 2.4). False means no blast in col can win for either side.
   */
  public boolean blastMayWin(int col) {
    int h = height[col];
    if (h >= rows) {
      return false;
    }
    return (col > 0 && height[col - 1] >= h + 3) || (col + 1 < g.cols && height[col + 1] >= h + 3);
  }

  /**
   * What {@link #blast} would return in col, without changing or copying anything; the position must hold no line.
   * A new line must contain a disc that falls, and only neighbours at least three discs taller than col have one, so
   * the test walks the four lines through each falling disc on the board as it would be after the blast.
   */
  public boolean blastWins(int col) {
    if (!blastMayWin(col)) {
      return false;
    }
    int h = height[col];
    int lo = Math.max(h - 1, 0);
    int from = Math.max(col - 1, 0);
    int to = Math.min(col + 1, g.cols - 1);
    for (int n = from; n <= to; n++) {
      fall[n] = Math.max(0, Math.min(h + 1, height[n] - 1) - lo + 1);
    }
    boolean win = (col > 0 && fallingLine(col - 1, lo)) || (col + 1 < g.cols && fallingLine(col + 1, lo));
    for (int n = from; n <= to; n++) {
      fall[n] = 0;
    }
    return win;
  }

  /** True if a disc falling in column n completes a line, with fall[] set up for the tested blast. */
  private boolean fallingLine(int n, int lo) {
    int base = n * rows;
    int drop = fall[n];
    int end = height[n] - drop;
    for (int y = lo; y < end; y++) {
      int v = cell[base + y + drop];
      if (run(n, y, 1, 0, v, lo) + run(n, y, -1, 0, v, lo) >= k - 1
          || run(n, y, 0, 1, v, lo) + run(n, y, 0, -1, v, lo) >= k - 1
          || run(n, y, 1, 1, v, lo) + run(n, y, -1, -1, v, lo) >= k - 1
          || run(n, y, 1, -1, v, lo) + run(n, y, -1, 1, v, lo) >= k - 1) {
        return true;
      }
    }
    return false;
  }

  /** Discs of colour v next to (x, y) in direction (dx, dy) on the board after the tested blast, at most k - 1. */
  private int run(int x, int y, int dx, int dy, int v, int lo) {
    int n = 0;
    while (n < k - 1) {
      x += dx;
      y += dy;
      if (x < 0 || x >= g.cols || y < 0 || y >= rows) {
        break;
      }
      int real = y >= lo ? y + fall[x] : y;
      if (real >= rows || cell[x * rows + real] != v) {
        break;
      }
      n++;
    }
    return n;
  }

  /** Full scan for a K-line of the given colour (1 or 2), or of either colour when colour is 0. */
  public boolean hasLine(int colour) {
    for (int w = 0; w < g.windows; w++) {
      int code = wc[w];
      if ((colour != 2 && code == t.six[1]) || (colour != 1 && code == t.six[2])) {
        return true;
      }
    }
    return false;
  }

  public boolean topRowFull() {
    for (int c = 0; c < g.cols; c++) {
      if (height[c] < rows) {
        return false;
      }
    }
    return true;
  }

  public int cell(int col, int y) {
    return cell[col * rows + y];
  }

  public int height(int col) {
    return height[col];
  }

  public int discs() {
    return discs;
  }

  public long hash() {
    return hash;
  }

  public int windowCode(int w) {
    return wc[w];
  }

  public int threats(int colour, int cellIndex) {
    return thr[colour][cellIndex];
  }

  public int live3(int colour) {
    return live3[colour];
  }

  /** Copy of the cells in this class's index order. */
  public byte[] cells() {
    return cell.clone();
  }

  /** True if every derived field equals a rebuild from the cells (test helper). */
  public boolean consistent() {
    Board fresh = new Board(g);
    fresh.load(cell);
    return fresh.hash == hash
        && fresh.discs == discs
        && java.util.Arrays.equals(fresh.height, height)
        && java.util.Arrays.equals(fresh.wc, wc)
        && java.util.Arrays.equals(fresh.thr[1], thr[1])
        && java.util.Arrays.equals(fresh.thr[2], thr[2])
        && fresh.live3[1] == live3[1]
        && fresh.live3[2] == live3[2];
  }
}
