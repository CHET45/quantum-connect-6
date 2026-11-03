package ch.qc.bot.search;

/**
 * Lockless transposition table (design 2.6): two-entry buckets of (key ^ data, data) longs, so a torn write by
 * a concurrent zombie search is rejected on probe. Slot 0 keeps the deeper entry, slot 1 is always replaced.
 *
 * <pre>
 * data bits  0-15 score + 32768   16-17 bound   18-25 depth   26-35 move + 1 (col | special << 9)   36-43 generation
 * </pre>
 */
public final class TranspositionTable {
  public static final int LOWER = 1;
  public static final int UPPER = 2;
  public static final int EXACT = 3;

  private final long[] keys;
  private final long[] data;
  private final int mask;
  private int generation;

  public TranspositionTable(int log2Entries) {
    keys = new long[1 << log2Entries];
    data = new long[1 << log2Entries];
    mask = (1 << log2Entries) - 1;
  }

  /** Size chosen from the heap: at most an eighth of it, between 2^18 and 2^21 entries (4 to 32 MB). */
  public static TranspositionTable forHeap() {
    long budget = Runtime.getRuntime().maxMemory() / 8 / 16;
    int log2 = 18;
    while (log2 < 21 && (1L << (log2 + 1)) <= budget) {
      log2++;
    }
    try {
      return new TranspositionTable(log2);
    } catch (OutOfMemoryError e) {
      return new TranspositionTable(16);
    }
  }

  public void newSearch() {
    generation = (generation + 1) & 0xFF;
  }

  /** Entry data for the key, or 0 when absent. */
  public long probe(long key) {
    int i = (int) key & mask & ~1;
    long d0 = data[i];
    if ((keys[i] ^ d0) == key && d0 != 0) {
      return d0;
    }
    long d1 = data[i + 1];
    if ((keys[i + 1] ^ d1) == key && d1 != 0) {
      return d1;
    }
    return 0;
  }

  public void store(long key, int depth, int score, int bound, int move) {
    int i = (int) key & mask & ~1;
    int packedMove = move < 0 ? 0 : (Tactics.column(move) | (Tactics.isSpecial(move) ? 1 << 9 : 0)) + 1;
    long d = (score + 32768L) | ((long) bound << 16) | ((long) Math.max(0, Math.min(255, depth)) << 18)
        | ((long) packedMove << 26) | ((long) generation << 36);
    long old = data[i];
    boolean sameKey = (keys[i] ^ old) == key;
    if (old == 0 || sameKey || depth(old) <= depth || generation(old) != generation) {
      data[i] = d;
      keys[i] = key ^ d;
    } else {
      data[i + 1] = d;
      keys[i + 1] = key ^ d;
    }
  }

  public static int score(long d) {
    return (int) (d & 0xFFFF) - 32768;
  }

  public static int bound(long d) {
    return (int) (d >>> 16) & 3;
  }

  public static int depth(long d) {
    return (int) (d >>> 18) & 0xFF;
  }

  /** Stored move in {@link Tactics} encoding, or {@link Tactics#NONE}. */
  public static int move(long d) {
    int p = (int) (d >>> 26) & 0x3FF;
    if (p == 0) {
      return Tactics.NONE;
    }
    p--;
    return (p & 0x1FF) | ((p & (1 << 9)) != 0 ? Tactics.SPECIAL : 0);
  }

  private static int generation(long d) {
    return (int) (d >>> 36) & 0xFF;
  }
}
