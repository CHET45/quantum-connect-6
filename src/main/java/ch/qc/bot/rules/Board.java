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
  /** Keys of remembered window sums are below this; see {@link #rememberSum}. */
  public static final int SUM_KEYS = 32;
  private static final int SUM_SLOTS = 16 * SUM_KEYS;

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
  /** colThreats[col]: the thr values of both colours added up over the column. */
  private final int[] colThreats;
  private final int[] live3 = new int[3];
  private long hash;
  private int discs;

  private final int[] stamp;
  private int stampGen;
  /** Windows changed by the blast in progress. */
  private final int[] touched;
  /**
   * The drop stack: level 0 is the position as last set (load, copy, blast), each drop made since is one level.
   * levelCell[l]: the dropped cell; levelLog[l]: where logCode holds the old code of each window through that cell;
   * levelTag[l]: unique for this drop, so remembered sums need no clearing when the level is taken back.
   */
  private final int[] levelCell;
  private final int[] levelLog;
  private final long[] levelTag;
  private final short[] logCode;
  private int level;
  private long lastTag;
  /** Window sums of one evaluation, per (level, key); allocated when an evaluation first asks. */
  private Object sumOwner;
  private long[] sumTag;
  private int[] sumValue;


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
    colThreats = new int[g.cols];
    levelCell = new int[g.cells + 2];
    levelLog = new int[g.cells + 2];
    levelTag = new long[g.cells + 2];
    logCode = new short[g.cellWin.length];
    stamp = new int[g.windows];
    touched = new int[g.windows];
    resetLevels();
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
    for (int c = 0; c < colThreats.length; c++) {
      colThreats[c] = o.colThreats[c];
    }
    resetLevels();
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
    java.util.Arrays.fill(colThreats, 0);
    resetLevels();
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
      int cellIndex = g.winCells[w * k + p - 1];
      thr[1][cellIndex] += (byte) sign;
      colThreats[cellIndex / rows] += sign;
    }
    p = (f >> 4) & 15;
    if (p != 0) {
      int cellIndex = g.winCells[w * k + p - 1];
      thr[2][cellIndex] += (byte) sign;
      colThreats[cellIndex / rows] += sign;
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
    int l = ++level;
    levelCell[l] = i;
    levelTag[l] = ++lastTag;
    int log = levelLog[l];
    for (int j = g.cellStart[i]; j < end; j++) {
      int w = wins[j];
      int old = wc[w];
      logCode[log++] = (short) old;
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
    levelLog[l + 1] = log;
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
    if (level > 0 && levelCell[level] == i) {
      level--;
    } else {
      resetLevels();
    }
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
    resetLevels();
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
   * A new line must contain a disc that falls, and only a neighbour at least three discs taller than col has one.
   */
  public boolean blastWins(int col) {
    int h = height[col];
    if (h >= rows) {
      return false;
    }
    return (col > 0 && height[col - 1] >= h + 3 && fallingLine(col - 1, -1, h))
        || (col + 1 < g.cols && height[col + 1] >= h + 3 && fallingLine(col + 1, 1, h));
  }

  /**
   * True if a blast at height h in the column next to n completes a line through a disc falling in n; away is the
   * direction from that column to n. After the blast that column is empty from lo up, so a line can only reach it
   * below lo. That leaves, for a fallen disc at height y: the vertical line where the fallen discs meet the discs
   * below them, the horizontal and the two diagonal lines starting at the disc and running away from the blast, all
   * on cells the blast does not change, and for y = lo the rising diagonal that also continues below lo on the other side.
   */
  private boolean fallingLine(int n, int away, int h) {
    int lo = Math.max(h - 1, 0);
    int drop = h + 2 - lo;
    int base = n * rows;
    int end = height[n] - drop;
    int need = k - 1;
    int v = cell[base + lo + drop];
    if (lo > 0 && cell[base + lo - 1] == v) {
      int same = 1;
      for (int y = lo - 1; y >= 0 && cell[base + y] == v; y--) {
        same++;
      }
      for (int i = base + lo + drop + 1; i < base + rows && cell[i] == v; i++) {
        same++;
      }
      if (same >= k) {
        return true;
      }
    }
    int far = n + away * need;
    boolean room = far >= 0 && far < g.cols;
    for (int y = lo; y < end; y++) {
      v = cell[base + y + drop];
      if (room && (lineRun(n + away, y, away, 0, v) >= need || lineRun(n + away, y - 1, away, -1, v) >= need)) {
        return true;
      }
      if (y == lo) {
        if (lineRun(n + away, y + 1, away, 1, v) + lineRun(n - away, y - 1, -away, -1, v) >= need) {
          return true;
        }
      } else if (room && lineRun(n + away, y + 1, away, 1, v) >= need) {
        return true;
      }
    }
    return false;
  }

  /** Discs of colour v from (x, y) on in direction (dx, dy), on cells a tested blast does not change, at most k - 1. */
  private int lineRun(int x, int y, int dx, int dy, int v) {
    int n = 0;
    while (n < k - 1 && x >= 0 && x < g.cols && y >= 0 && y < rows && cell[x * rows + y] == v) {
      n++;
      x += dx;
      y += dy;
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

  /** Threat windows of both colours whose empty cell is in this column; 0 for most columns. */
  public int columnThreats(int col) {
    return colThreats[col];
  }

  public int live3(int colour) {
    return live3[colour];
  }

  private void resetLevels() {
    level = 0;
    levelLog[1] = 0;
    levelTag[0] = ++lastTag;
  }

  /** Current height of the drop stack; 0 right after load, copy or blast. */
  public int level() {
    return level;
  }

  /** Cell dropped at a level of the stack, level at least 1. */
  public int levelCell(int l) {
    return levelCell[l];
  }

  /** Colour dropped at a level of the stack, level at least 1. */
  public int levelColour(int l) {
    return cell[levelCell[l]];
  }

  /** Index of the first logged window code of a level, level at least 1. */
  public int levelLog(int l) {
    return levelLog[l];
  }

  /** A logged window code: the code before the drop of the n-th window through its cell at levelLog + n. */
  public int loggedCode(int index) {
    return logCode[index];
  }

  /** The evaluation whose window sums this board remembers. */
  public Object sumOwner() {
    return sumOwner;
  }

  /** Forgets all remembered sums and remembers those of this evaluation from now on. */
  public void bindSums(Object owner) {
    sumOwner = owner;
    if (sumTag == null) {
      sumTag = new long[SUM_SLOTS];
      sumValue = new int[SUM_SLOTS];
    }
    java.util.Arrays.fill(sumTag, 0);
  }

  /** True if a sum is remembered for this level of the stack (as it stands now) and this key. */
  public boolean sumKnown(int l, int key) {
    return sumTag[(l * SUM_KEYS + key) & (SUM_SLOTS - 1)] == levelTag[l];
  }

  public int sum(int l, int key) {
    return sumValue[(l * SUM_KEYS + key) & (SUM_SLOTS - 1)];
  }

  public void rememberSum(int l, int key, int value) {
    int slot = (l * SUM_KEYS + key) & (SUM_SLOTS - 1);
    sumTag[slot] = levelTag[l];
    sumValue[slot] = value;
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
        && java.util.Arrays.equals(fresh.colThreats, colThreats)
        && fresh.live3[1] == live3[1]
        && fresh.live3[2] == live3[2];
  }
}
