package ch.qc.bot;

import ch.qc.bot.rules.Board;
import ch.qc.bot.rules.Geometry;

/**
 * Opening book from the empty board: position key to column, per board size, built by ch.qc.tools.BookBuilder and
 * embedded by the Gradle task embedBook from weights/book.qcb (Base64 string constants in the generated BookBlob). A
 * jar built without that file has an empty book and plays as if this class did not exist.
 *
 * <p>Keys do not use the Geometry Zobrist tables: the hash here is plain arithmetic on (cell, colour), so it cannot
 * depend on a JDK's random generator or on changes to Board. A position and its left-right mirror share an entry:
 * the key is the smaller of the two hashes and the stored column belongs to that orientation. Colour to move follows
 * from the disc count, and cooldowns follow from it as long as no turn was skipped and no blast was played, which
 * {@link #column} checks by requiring discs == plyIndex.
 *
 * <p>Blob, big endian: "QCB1", u8 tables, then per table u8 rows, u8 cols, u8 maxDiscs, u32 count and count entries of
 * a 5-byte key (the top 40 bits of the hash, sorted as unsigned) and a 1-byte column.
 */
public final class Book {
  private static final Book EMPTY = new Book(new Table[0]);

  private static final class Holder {
    static final Book EMBEDDED = fromBase64(BookBlob.CHUNKS);
  }

  private static final class Table {
    final int rows;
    final int cols;
    final int maxDiscs;
    final long[] keys;
    final byte[] columns;

    Table(int rows, int cols, int maxDiscs, long[] keys, byte[] columns) {
      this.rows = rows;
      this.cols = cols;
      this.maxDiscs = maxDiscs;
      this.keys = keys;
      this.columns = columns;
    }
  }

  private final Table[] tables;

  private Book(Table[] tables) {
    this.tables = tables;
  }

  /** The book compiled into this jar; shared, immutable, decoded on first use (call from a constructor). */
  public static Book embedded() {
    return Holder.EMBEDDED;
  }

  /** Decodes a blob; a broken blob gives the empty book, so it can never stop the bot. */
  public static Book fromBytes(byte[] raw) {
    if (raw == null || raw.length < 5 || raw[0] != 'Q' || raw[1] != 'C' || raw[2] != 'B' || raw[3] != '1') {
      return EMPTY;
    }
    int n = raw[4] & 0xFF;
    Table[] t = new Table[n];
    int at = 5;
    for (int i = 0; i < n; i++) {
      if (at + 7 > raw.length) {
        return EMPTY;
      }
      int rows = raw[at] & 0xFF;
      int cols = raw[at + 1] & 0xFF;
      int maxDiscs = raw[at + 2] & 0xFF;
      int count = (raw[at + 3] & 0xFF) << 24 | (raw[at + 4] & 0xFF) << 16 | (raw[at + 5] & 0xFF) << 8
          | (raw[at + 6] & 0xFF);
      at += 7;
      if (count < 0 || at + 6L * count > raw.length) {
        return EMPTY;
      }
      long[] keys = new long[count];
      byte[] columns = new byte[count];
      for (int e = 0; e < count; e++, at += 6) {
        long k = 0;
        for (int b = 0; b < 5; b++) {
          k = k << 8 | (raw[at + b] & 0xFFL);
        }
        keys[e] = k;
        columns[e] = raw[at + 5];
      }
      t[i] = new Table(rows, cols, maxDiscs, keys, columns);
    }
    return new Book(t);
  }

  static Book fromBase64(String[] chunks) {
    int chars = 0;
    for (String c : chunks) {
      chars += c.length();
    }
    if (chars == 0 || chars % 4 != 0) {
      return EMPTY;
    }
    int pad = 0;
    String last = chunks[chunks.length - 1];
    for (int i = last.length() - 1; i >= 0 && last.charAt(i) == '='; i--) {
      pad++;
    }
    byte[] out = new byte[chars / 4 * 3 - pad];
    int o = 0;
    int acc = 0;
    int bits = 0;
    for (String c : chunks) {
      for (int i = 0; i < c.length(); i++) {
        char ch = c.charAt(i);
        if (ch == '=') {
          continue;
        }
        int v = ch >= 'A' && ch <= 'Z' ? ch - 'A'
            : ch >= 'a' && ch <= 'z' ? ch - 'a' + 26
            : ch >= '0' && ch <= '9' ? ch - '0' + 52
            : ch == '+' ? 62 : ch == '/' ? 63 : -1;
        if (v < 0) {
          return EMPTY;
        }
        acc = (acc << 6 | v) & 0xFFFF;
        bits += 6;
        if (bits >= 8) {
          bits -= 8;
          if (o < out.length) {
            out[o++] = (byte) (acc >> bits);
          }
        }
      }
    }
    return fromBytes(out);
  }

  /** Entries for this board size, 0 if there is no table for it. */
  public int size(int rows, int cols) {
    Table t = table(rows, cols);
    return t == null ? 0 : t.keys.length;
  }

  private Table table(int rows, int cols) {
    for (Table t : tables) {
      if (t.rows == rows && t.cols == cols) {
        return t;
      }
    }
    return null;
  }

  /**
   * The book column for colour me to move, or -1 when the position is not in the book, a turn was skipped or a blast
   * played (discs differ from the ply index), the column is full, or the drop would allow an immediate win.
   *
   * @param plyIndex engine iteration index of this turn (both players, from 0)
   * @param cdOpp engine cooldown counter of the opponent before its next turn
   */
  public int column(Board b, int plyIndex, int me, int cdOpp) {
    Table t = table(b.g.rows, b.g.cols);
    int discs = b.discs();
    if (t == null || discs != plyIndex || discs > t.maxDiscs || me != (discs % 2 == 0 ? Board.FIRST : Board.SECOND)) {
      return -1;
    }
    long plain = hash(b, false);
    long mirror = hash(b, true);
    boolean flip = Long.compareUnsigned(mirror, plain) < 0;
    int i = find(t.keys, (flip ? mirror : plain) >>> 24);
    if (i < 0) {
      return -1;
    }
    int col = flip ? t.cols - 1 - t.columns[i] : t.columns[i];
    if (col < 0 || col >= t.cols || b.isFull(col)) {
      return -1;
    }
    return safe(b, col, me, cdOpp) ? col : -1;
  }

  private static int find(long[] keys, long key) {
    int lo = 0;
    int hi = keys.length - 1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      long k = keys[mid];
      if (k == key) {
        return mid;
      }
      if (k < key) {
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    return -1;
  }

  /** False if, after me drops in col, the opponent wins at once by a drop or by a blast it may play. */
  static boolean safe(Board b, int col, int me, int cdOpp) {
    Geometry g = b.g;
    int opp = 3 - me;
    for (int c = 0; c < g.cols; c++) {
      int y = b.height(c) + (c == col ? 1 : 0);
      if (y < g.rows && b.threats(opp, g.index(c, y)) > 0) {
        return false;
      }
    }
    if (cdOpp < TurnTracker.COOLDOWN) {
      return true;
    }
    Board after = new Board(g);
    after.copyFrom(b);
    after.make(col, me);
    Board probe = new Board(g);
    for (int c = 0; c < g.cols; c++) {
      if (!after.isFull(c) && after.blastMayWin(c)) {
        probe.copyFrom(after);
        if (probe.blast(c)) {
          return false;
        }
      }
    }
    return true;
  }

  /** Hash of the position, or of its left-right mirror. */
  public static long hash(Board b, boolean mirror) {
    Geometry g = b.g;
    long h = 0;
    for (int c = 0; c < g.cols; c++) {
      int top = b.height(c);
      int at = (mirror ? g.cols - 1 - c : c) * g.rows;
      for (int y = 0; y < top; y++) {
        h ^= cellKey(at + y, b.cell(c, y));
      }
    }
    return h;
  }

  private static long cellKey(int index, int colour) {
    long z = (index * 3L + colour) * 0x9E3779B97F4A7C15L + 0x51C07A6ED00D2026L;
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }
}
