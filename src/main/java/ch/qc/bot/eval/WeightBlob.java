package ch.qc.bot.eval;

/**
 * The learned tables as written by training/qctrain/export.py (design 3.7), after inflating the zlib stream of
 * the .qcw file: big-endian, "QCW1", i32 version, rows, cols, k, i64 specHash, i32 scale, stages, cdContexts,
 * classes, codes, t1, t2, t3, then int16 W[stage][cd][class * codes + code], T1[cd][t1], T2[cd][t2], T3[t3].
 * A blob with rows = cols = 0 holds the size-independent tables of {@link GenericSpec} (its spec hash is
 * {@link GenericSpec#hash}). A blob with one stage holds stage-independent window tables (large boards, where per-stage tables would cost
 * too much memory). The bot gets these bytes already inflated (the build does it, see EvalBlob), because uploaded bots may not use
 * java.util.zip or java.nio; parsing is by hand and reports problems by returning null, as bots may not throw.
 */
public final class WeightBlob {
  public final int rows;
  public final int cols;
  public final int k;
  public final long specHash;
  public final int stages;
  public final int cdContexts;
  public final int classes;
  public final int codes;
  public final short[] w;
  public final short[] t1;
  public final short[] t2;
  public final short[] t3;

  private WeightBlob(int[] header, long specHash, short[] w, short[] t1, short[] t2, short[] t3) {
    rows = header[0];
    cols = header[1];
    k = header[2];
    this.specHash = specHash;
    stages = header[4];
    cdContexts = header[5];
    classes = header[6];
    codes = header[7];
    this.w = w;
    this.t1 = t1;
    this.t2 = t2;
    this.t3 = t3;
  }

  private static final int HEADER_BYTES = 4 + 4 + 3 * 4 + 8 + 8 * 4;

  /** Parses an inflated blob; returns null on any format problem. */
  public static WeightBlob parse(byte[] raw) {
    if (raw == null || raw.length < HEADER_BYTES || raw[0] != 'Q' || raw[1] != 'C' || raw[2] != 'W'
        || raw[3] != '1' || i32(raw, 4) != 1) {
      return null;
    }
    // rows, cols, k, (unused), stages, cdContexts, classes, codes
    int[] h = new int[8];
    h[0] = i32(raw, 8);
    h[1] = i32(raw, 12);
    h[2] = i32(raw, 16);
    long specHash = ((long) i32(raw, 20) << 32) | (i32(raw, 24) & 0xFFFFFFFFL);
    int scale = i32(raw, 28);
    h[4] = i32(raw, 32);
    h[5] = i32(raw, 36);
    h[6] = i32(raw, 40);
    h[7] = i32(raw, 44);
    int n1 = i32(raw, 48);
    int n2 = i32(raw, 52);
    int n3 = i32(raw, 56);
    boolean generic = h[0] == 0 && h[1] == 0;
    if (scale != 256 || h[2] < 1 || h[2] > 9 || (!generic && (h[0] < 1 || h[1] < 1))
        || (generic && (h[6] != GenericSpec.CLASSES || specHash != GenericSpec.hash(h[2])))
        || (h[4] != Features.STAGES && h[4] != 1)
        || h[5] != Features.CD_CONTEXTS || n1 != Features.T1_SIZE || n2 != Features.T2_SIZE
        || n3 != Features.T3_SIZE || h[6] < 1 || h[7] < 1 || (long) h[6] * h[7] > 1L << 24) {
      return null;
    }
    int nw = h[4] * h[5] * h[6] * h[7];
    long expected = HEADER_BYTES + 2L * (nw + (long) h[5] * n1 + (long) h[5] * n2 + n3);
    if (raw.length != expected) {
      return null;
    }
    int[] pos = {HEADER_BYTES};
    short[] w = shorts(raw, pos, nw);
    short[] t1 = shorts(raw, pos, h[5] * n1);
    short[] t2 = shorts(raw, pos, h[5] * n2);
    short[] t3 = shorts(raw, pos, n3);
    return new WeightBlob(h, specHash, w, t1, t2, t3);
  }

  private static int i32(byte[] b, int at) {
    return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8 | (b[at + 3] & 0xFF);
  }

  private static short[] shorts(byte[] b, int[] pos, int n) {
    short[] s = new short[n];
    int at = pos[0];
    for (int i = 0; i < n; i++, at += 2) {
      s[i] = (short) ((b[at] & 0xFF) << 8 | (b[at + 1] & 0xFF));
    }
    pos[0] = at;
    return s;
  }

  /** True for tables of the size-independent family ({@link GenericSpec}): rows and cols are 0. */
  public boolean generic() {
    return rows == 0 && cols == 0;
  }

  /** True if the blob was trained for this feature spec. */
  public boolean matches(FeatureSpec spec) {
    return spec.specHash == specHash && spec.g.rows == rows && spec.g.cols == cols && spec.g.k == k
        && spec.classes == classes && spec.codes == codes;
  }
}
