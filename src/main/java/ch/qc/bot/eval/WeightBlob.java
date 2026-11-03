package ch.qc.bot.eval;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * The learned tables as written by training/qctrain/export.py (design 3.7): zlib-deflated, big-endian, "QCW1", i32
 * version, rows, cols, k, i64 specHash, i32 scale, stages, cdContexts, classes, codes, t1, t2, t3, then int16
 * W[stage][cd][class * codes + code], T1[cd][t1], T2[cd][t2], T3[t3].
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

  private WeightBlob(ByteBuffer bb) {
    byte[] magic = new byte[4];
    bb.get(magic);
    if (magic[0] != 'Q' || magic[1] != 'C' || magic[2] != 'W' || magic[3] != '1') {
      throw new IllegalArgumentException("not a QCW1 blob");
    }
    int version = bb.getInt();
    if (version != 1) {
      throw new IllegalArgumentException("unsupported blob version " + version);
    }
    rows = bb.getInt();
    cols = bb.getInt();
    k = bb.getInt();
    specHash = bb.getLong();
    int scale = bb.getInt();
    if (scale != 256) {
      throw new IllegalArgumentException("unsupported scale " + scale);
    }
    stages = bb.getInt();
    cdContexts = bb.getInt();
    classes = bb.getInt();
    codes = bb.getInt();
    int n1 = bb.getInt();
    int n2 = bb.getInt();
    int n3 = bb.getInt();
    if (stages != Features.STAGES || cdContexts != Features.CD_CONTEXTS || n1 != Features.T1_SIZE
        || n2 != Features.T2_SIZE || n3 != Features.T3_SIZE) {
      throw new IllegalArgumentException("blob layout does not match the feature definitions");
    }
    w = shorts(bb, stages * cdContexts * classes * codes);
    t1 = shorts(bb, cdContexts * n1);
    t2 = shorts(bb, cdContexts * n2);
    t3 = shorts(bb, n3);
    if (bb.hasRemaining()) {
      throw new IllegalArgumentException("trailing bytes in the blob");
    }
  }

  private static short[] shorts(ByteBuffer bb, int n) {
    short[] s = new short[n];
    bb.asShortBuffer().get(s);
    bb.position(bb.position() + 2 * n);
    return s;
  }

  /** Parses a deflated blob; throws IllegalArgumentException on any format problem. */
  public static WeightBlob parse(byte[] deflated) {
    Inflater inf = new Inflater();
    try {
      inf.setInput(deflated);
      ByteArrayOutputStream out = new ByteArrayOutputStream(deflated.length * 4);
      byte[] buf = new byte[1 << 16];
      while (!inf.finished()) {
        int n = inf.inflate(buf);
        if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
          throw new IllegalArgumentException("truncated blob");
        }
        out.write(buf, 0, n);
      }
      return new WeightBlob(ByteBuffer.wrap(out.toByteArray()));
    } catch (DataFormatException | java.nio.BufferUnderflowException e) {
      throw new IllegalArgumentException("corrupt blob", e);
    } finally {
      inf.end();
    }
  }

  /** True if the blob was trained for this feature spec. */
  public boolean matches(FeatureSpec spec) {
    return spec.specHash == specHash && spec.g.rows == rows && spec.g.cols == cols && spec.g.k == k
        && spec.classes == classes && spec.codes == codes;
  }
}
