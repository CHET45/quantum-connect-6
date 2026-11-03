package ch.qc.bot.eval;

import ch.qc.bot.rules.Geometry;

/**
 * Window family that does not depend on the board size, so one set of tables serves every board: a window's class
 * is its direction group (horizontal, vertical, diagonal; the two diagonals are mirror images and share a class)
 * and the row of its lowest cell, capped at {@link #BANDS} - 1. Codes are read with the digits in rising-row order
 * (a down-sloping diagonal is read backwards), and a horizontal window, its own mirror image within its class,
 * uses the smaller of its code and the reversed code. {@link #featMap} gives the parameter index
 * {@code class * codes + canonical code} per (window, code) in the layout of {@link FeatureSpec}, so the training
 * code handles both families alike; {@link GenericEval} computes the same index without the per-window table.
 */
public final class GenericSpec {
  public static final int BANDS = 6;
  public static final int GROUPS = 3;
  public static final int CLASSES = GROUPS * BANDS;
  /** Canonical-code kinds: digits as they are, reversed, or the smaller of both. */
  static final int AS_IS = 0;
  static final int REVERSED = 1;
  static final int SYMMETRIC = 2;

  private GenericSpec() {}

  /** Class of window w of g. */
  public static int windowClass(Geometry g, int w) {
    int dir = g.winDir[w];
    int group = dir == 0 ? 0 : dir == 1 ? 1 : 2;
    int lowest = g.rows;
    for (int i = 0; i < g.k; i++) {
      lowest = Math.min(lowest, g.winCells[w * g.k + i] % g.rows);
    }
    return group * BANDS + Math.min(lowest, BANDS - 1);
  }

  /** Canonical-code kind of window w: horizontal windows are symmetric, down-sloping diagonals reversed. */
  public static int kind(Geometry g, int w) {
    int dir = g.winDir[w];
    return dir == 0 ? SYMMETRIC : dir == 3 ? REVERSED : AS_IS;
  }

  /** canon[kind][code] for windows of k cells. */
  public static int[][] canonTables(int k) {
    int codes = 1;
    for (int i = 0; i < k; i++) {
      codes *= 3;
    }
    int[][] canon = new int[3][codes];
    for (int code = 0; code < codes; code++) {
      int rev = 0;
      for (int i = 0, c = code; i < k; i++, c /= 3) {
        rev = rev * 3 + c % 3;
      }
      canon[AS_IS][code] = code;
      canon[REVERSED][code] = rev;
      canon[SYMMETRIC][code] = Math.min(code, rev);
    }
    return canon;
  }

  /** featMap[w * codes + code] = class * codes + canonical code, as in {@link FeatureSpec#featMap}. */
  public static int[] featMap(Geometry g) {
    int[][] canon = canonTables(g.k);
    int codes = canon[0].length;
    int[] map = new int[g.windows * codes];
    for (int w = 0; w < g.windows; w++) {
      int base = windowClass(g, w) * codes;
      int[] c = canon[kind(g, w)];
      for (int code = 0; code < codes; code++) {
        map[w * codes + code] = base + c[code];
      }
    }
    return map;
  }

  /** Identifies the family and the table layout; written into generic blobs instead of a board's spec hash. */
  public static long hash(int k) {
    long h = 0x47454E45524943L; // "GENERIC"
    h = 31 * h + k;
    h = 31 * h + BANDS;
    h = 31 * h + GROUPS;
    h = 31 * h + Features.STAGES;
    h = 31 * h + Features.CD_CONTEXTS;
    h = 31 * h + Features.T1_SIZE;
    h = 31 * h + Features.T2_SIZE;
    h = 31 * h + Features.T3_SIZE;
    return h;
  }

  /** JSON for the training code, in the format of {@link FeatureSpec#toJson} plus the generic hash. */
  public static String toJson(Geometry g) {
    FeatureSpec spec = new FeatureSpec(g);
    int[] map = featMap(g);
    StringBuilder sb = new StringBuilder(map.length * 7);
    sb.append("{\"version\":1,\"generic\":true,\"rows\":").append(g.rows).append(",\"cols\":").append(g.cols)
        .append(",\"k\":").append(g.k).append(",\"codes\":").append(spec.codes)
        .append(",\"windows\":").append(g.windows).append(",\"classes\":").append(CLASSES)
        .append(",\"stages\":").append(Features.STAGES).append(",\"cdContexts\":").append(Features.CD_CONTEXTS)
        .append(",\"t1\":").append(Features.T1_SIZE).append(",\"t2\":").append(Features.T2_SIZE)
        .append(",\"t3\":").append(Features.T3_SIZE)
        .append(",\"specHash\":\"").append(Long.toHexString(spec.specHash))
        .append("\",\"genericHash\":\"").append(Long.toHexString(hash(g.k)))
        .append("\",\"winCells\":[");
    for (int i = 0; i < g.winCells.length; i++) {
      sb.append(i == 0 ? "" : ",").append(g.winCells[i]);
    }
    sb.append("],\"featMap\":[");
    for (int i = 0; i < map.length; i++) {
      sb.append(i == 0 ? "" : ",").append(map[i]);
    }
    return sb.append("]}").toString();
  }
}
